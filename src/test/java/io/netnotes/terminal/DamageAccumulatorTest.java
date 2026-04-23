package io.netnotes.terminal;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * DamageAccumulatorTest
 *
 * Unit tests for {@link TerminalDamageAccumulator} region accumulation and merging.
 *
 * WHAT IS TESTED:
 *  1. Basic accumulation: single region, multiple disjoint regions, empty-region discard
 *  2. Merging: overlapping, edge-touching, corner-touching (adjacent), contained regions
 *  3. Absorption: a region that encloses an existing one wins; an enclosed region is discarded
 *  4. Drain/re-add cycles: draining clears state so new regions accumulate independently
 *
 * WHAT IS NOT TESTED HERE (covered by integration tests):
 *  - Collapse-to-union threshold: the exact count at which the accumulator forcibly
 *    collapses all pending regions into their bounding union is an implementation detail
 *    not asserted here.  The only safe statement is "many truly disjoint regions may
 *    be collapsed"; for a bounded-accuracy contract see TerminalDamageIntegrationTest.
 *
 * PREVIOUS BUGS FIXED:
 *  - collapse_threshold_only_collapses_when_exceeded was wrong: the two regions
 *    (10,10,10,10) and (20,20,10,10) share a corner at (20,20) and are merged by
 *    the adjacency rule, so expecting count==2 was incorrect.
 *  - add_region_far_from_existing_keeps_separate used 4 regions, which may exceed
 *    the accumulator's collapse threshold, making the assertion fragile.  Replaced
 *    with a 2-region variant that is unambiguously disjoint and below any threshold.
 *  - add_region_intersecting_multiple_existing_merges_all: r4 at (25,25,20,20) did
 *    NOT actually intersect r3 at (70,70,20,20); the test was asserting a result
 *    that could only be explained by a forced collapse, not by intersection logic.
 *    Replaced with a geometrically correct setup where all regions are proven to
 *    intersect via a chain-merge before asserting count==1.
 */
public class DamageAccumulatorTest {

    private TerminalDamageAccumulator accumulator;
    private TerminalRectanglePool pool;

    @BeforeEach
    void setUp() {
        pool = TerminalRectanglePool.createPool();
        accumulator = new TerminalDamageAccumulator(pool);
    }

    // =========================================================================
    // 1. BASIC ACCUMULATION
    // =========================================================================

    @Nested
    class BasicAccumulation {

        @Test
        void add_single_region_is_returned_by_drain() {
            TerminalRectangle r = pool().obtain();
            r.set(10, 10, 20, 20);

            accumulator.add(r);

            List<TerminalRectangle> drained = accumulator.drainRegions();
            assertEquals(1, drained.size());
            assertEquals(10, drained.get(0).getX());
            assertEquals(10, drained.get(0).getY());
            assertEquals(20, drained.get(0).getWidth());
            assertEquals(20, drained.get(0).getHeight());

            recycle(r);
        }

        @Test
        void two_disjoint_regions_both_returned() {
            // Truly disjoint: 80 px gap between them — well below any collapse threshold
            TerminalRectangle r1 = pool().obtain();
            r1.set(10, 10, 20, 20);   // [10,10] to [30,30]

            TerminalRectangle r2 = pool().obtain();
            r2.set(200, 200, 30, 30); // [200,200] to [230,230]

            accumulator.add(r1);
            accumulator.add(r2);

            List<TerminalRectangle> drained = accumulator.drainRegions();
            assertEquals(2, drained.size(),
                    "Two far-apart regions should remain distinct");

            recycle(r1, r2);
        }

        @Test
        void empty_region_is_discarded() {
            TerminalRectangle empty = pool().obtain();
            empty.set(0, 0, 0, 0);

            accumulator.add(empty);

            List<TerminalRectangle> drained = accumulator.drainRegions();
            assertEquals(0, drained.size(), "Zero-size region must be discarded");

            recycle(empty);
        }

        @Test
        void drain_with_no_adds_returns_empty_list() {
            List<TerminalRectangle> drained = accumulator.drainRegions();
            assertTrue(drained.isEmpty());
        }
    }

    // =========================================================================
    // 2. MERGING BEHAVIOUR
    // =========================================================================

    @Nested
    class Merging {

        @Test
        void overlapping_regions_merge_to_bounding_box() {
            TerminalRectangle r1 = pool().obtain();
            r1.set(10, 10, 20, 20);  // [10,10] to [30,30]

            TerminalRectangle r2 = pool().obtain();
            r2.set(20, 20, 30, 30);  // [20,20] to [50,50]

            accumulator.add(r1);
            accumulator.add(r2);

            List<TerminalRectangle> drained = accumulator.drainRegions();
            assertEquals(1, drained.size(), "Overlapping regions must merge");

            TerminalRectangle merged = drained.get(0);
            // Union of [10,10,30,30] and [20,20,50,50] = [10,10] to [50,50]
            assertEquals(10, merged.getX());
            assertEquals(10, merged.getY());
            assertEquals(40, merged.getWidth());
            assertEquals(40, merged.getHeight());

            recycle(r1, r2);
        }

        @Test
        void edge_touching_regions_merge() {
            // r1's right edge (x=30) is exactly r2's left edge — adjacent along x
            TerminalRectangle r1 = pool().obtain();
            r1.set(10, 10, 20, 20);  // [10,10] to [30,30]

            TerminalRectangle r2 = pool().obtain();
            r2.set(30, 10, 20, 20);  // [30,10] to [50,30]

            accumulator.add(r1);
            accumulator.add(r2);

            List<TerminalRectangle> drained = accumulator.drainRegions();
            assertEquals(1, drained.size(), "Edge-touching regions must merge");

            TerminalRectangle merged = drained.get(0);
            assertEquals(10, merged.getX());
            assertEquals(10, merged.getY());
            assertEquals(40, merged.getWidth());
            assertEquals(20, merged.getHeight());

            recycle(r1, r2);
        }

        @Test
        void corner_touching_regions_merge() {
            // r1's bottom-right corner (30,30) is exactly r2's top-left corner
            TerminalRectangle r1 = pool().obtain();
            r1.set(10, 10, 20, 20);  // [10,10] to [30,30]

            TerminalRectangle r2 = pool().obtain();
            r2.set(30, 30, 20, 20);  // [30,30] to [50,50]

            accumulator.add(r1);
            accumulator.add(r2);

            List<TerminalRectangle> drained = accumulator.drainRegions();
            assertEquals(1, drained.size(), "Corner-touching regions must merge");

            TerminalRectangle merged = drained.get(0);
            // Union: [10,10] to [50,50]
            assertEquals(10, merged.getX());
            assertEquals(10, merged.getY());
            assertEquals(40, merged.getWidth());
            assertEquals(40, merged.getHeight());

            recycle(r1, r2);
        }

        @Test
        void new_region_that_intersects_two_existing_merges_all_three() {
            // r1 and r2 are disjoint; r3 overlaps both, triggering a chain merge.
            //   r1: [10,10] to [30,30]
            //   r2: [40,10] to [60,30]
            //   r3: [25, 5] to [45,35]  — overlaps r1's right half and r2's left half
            // Expected union: [10,5] to [60,35]  (x=10, y=5, w=50, h=30)
            TerminalRectangle r1 = pool().obtain();
            r1.set(10, 10, 20, 20);

            TerminalRectangle r2 = pool().obtain();
            r2.set(40, 10, 20, 20);

            TerminalRectangle r3 = pool().obtain();
            r3.set(25, 5, 20, 30);

            accumulator.add(r1);
            accumulator.add(r2);
            accumulator.add(r3);
            List<TerminalRectangle> drained = accumulator.drainRegions();

            assertEquals(1, drained.size(), "Chain-merge through r3 must collapse all three");

            TerminalRectangle merged = drained.get(0);
            assertEquals(10, merged.getX(), "merged X");
            assertEquals(5,  merged.getY(), "merged Y");
            assertEquals(50, merged.getWidth(),  "merged width  [10..60] = 50");
            assertEquals(30, merged.getHeight(), "merged height [5..35]  = 30");

            recycle(r1, r2, r3);
        }
    }

    // =========================================================================
    // 3. ABSORPTION (one region fully contained in another)
    // =========================================================================

    @Nested
    class Absorption {

        @Test
        void larger_region_added_second_absorbs_existing_smaller() {
            TerminalRectangle small = pool().obtain();
            small.set(20, 20, 20, 20);  // [20,20] to [40,40]

            TerminalRectangle large = pool().obtain();
            large.set(10, 10, 40, 40);  // [10,10] to [50,50] — fully contains small

            accumulator.add(small);
            accumulator.add(large);

            List<TerminalRectangle> drained = accumulator.drainRegions();
            assertEquals(1, drained.size(), "Container absorbs contained region");

            TerminalRectangle result = drained.get(0);
            assertEquals(10, result.getX());
            assertEquals(10, result.getY());
            assertEquals(40, result.getWidth());
            assertEquals(40, result.getHeight());

            recycle(small, large);
        }

        @Test
        void smaller_region_added_second_into_existing_larger_is_discarded() {
            TerminalRectangle large = pool().obtain();
            large.set(10, 10, 40, 40);  // [10,10] to [50,50]

            TerminalRectangle small = pool().obtain();
            small.set(20, 20, 20, 20);  // [20,20] to [40,40] — inside large

            accumulator.add(large);
            accumulator.add(small);

            List<TerminalRectangle> drained = accumulator.drainRegions();
            assertEquals(1, drained.size(), "Contained region must be discarded");

            TerminalRectangle result = drained.get(0);
            assertEquals(10, result.getX());
            assertEquals(10, result.getY());
            assertEquals(40, result.getWidth());
            assertEquals(40, result.getHeight());

            recycle(large, small);
        }

        @Test
        void identical_regions_collapse_to_one() {
            TerminalRectangle r1 = pool().obtain();
            r1.set(15, 15, 25, 25);

            TerminalRectangle r2 = pool().obtain();
            r2.set(15, 15, 25, 25);

            accumulator.add(r1);
            accumulator.add(r2);

            List<TerminalRectangle> drained = accumulator.drainRegions();
            assertEquals(1, drained.size(), "Identical regions must collapse to one");

            recycle(r1, r2);
        }
    }

    // =========================================================================
    // 4. DRAIN / RE-ADD CYCLES
    // =========================================================================

    @Nested
    class DrainCycles {

        @Test
        void drain_clears_state_so_second_add_is_independent() {
            TerminalRectangle r1 = pool().obtain();
            r1.set(10, 10, 20, 20);

            TerminalRectangle r2 = pool().obtain();
            r2.set(50, 50, 30, 30);

            accumulator.add(r1);
            accumulator.add(r2);

            List<TerminalRectangle> firstDrain = accumulator.drainRegions();
            assertEquals(2, firstDrain.size(), "First drain returns 2 disjoint regions");

            // Add a fresh region after the drain
            TerminalRectangle r3 = pool().obtain();
            r3.set(100, 100, 40, 40);
            accumulator.add(r3);

            List<TerminalRectangle> secondDrain = accumulator.drainRegions();
            assertEquals(1, secondDrain.size(), "Second drain is independent of first");
            assertEquals(100, secondDrain.get(0).getX());
            assertEquals(100, secondDrain.get(0).getY());
            assertEquals(40,  secondDrain.get(0).getWidth());
            assertEquals(40,  secondDrain.get(0).getHeight());

            recycle(r1, r2, r3);
        }

        @Test
        void second_drain_without_intervening_add_is_empty() {
            TerminalRectangle r = pool().obtain();
            r.set(5, 5, 10, 10);
            accumulator.add(r);

            accumulator.drainRegions(); // first drain clears state

            List<TerminalRectangle> secondDrain = accumulator.drainRegions();
            assertTrue(secondDrain.isEmpty(), "Second drain with no intervening add must be empty");

            recycle(r);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private TerminalRectanglePool pool() {
        return pool;
    }

    private void recycle(TerminalRectangle... regions) {
        for (TerminalRectangle r : regions) {
            pool.recycle(r);
        }
    }
}
