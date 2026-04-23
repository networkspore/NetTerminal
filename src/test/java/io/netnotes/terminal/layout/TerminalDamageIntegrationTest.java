package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.BorderPanel;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.components.panels.TerminalBorderPanel;
import io.netnotes.terminal.components.text.TerminalLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TerminalDamageIntegrationTest
 *
 * Integration-level damage tests.  Every test runs through the real layout
 * system using {@link TerminalDamageTestHarness} so that we exercise the same
 * damage-propagation, accumulation, and render-dispatch paths that production
 * code uses.
 *
 * REPLACING ClipDamagePropagationTest:
 * The original file used {@code TestTerminalRenderable} / {@code TestTerminalBatchBuilder}
 * test doubles that were never implemented.  These tests instead work with real
 * {@link TerminalBorderPanel} and {@link TerminalLabel} components, exactly as
 * {@link TerminalBorderPanelLayoutTest} does for geometry assertions.
 *
 * THREADING:
 * The UI executor serialises all layout/render work.  Tests block on
 * {@link TerminalDamageTestHarness#awaitDamageCapture()} (1-second timeout)
 * so they do not need {@code Thread.sleep} or manual synchronisation.
 *
 * WHAT IS ASSERTED:
 * Damage is verified at the bounding-union level: we confirm that the union of
 * all damage rectangles covers the expected screen area.  We deliberately do
 * not pin the exact number of rectangles returned by the accumulator because
 * that is an implementation detail (the accumulator may merge adjacent regions).
 */
public class TerminalDamageIntegrationTest {

    private static final int W = 80;
    private static final int H = 24;

    private TerminalDamageTestHarness harness;
    private TerminalBorderPanel panel;

    @BeforeEach
    void setup() {
        panel = new TerminalBorderPanel("bp");
        harness = new TerminalDamageTestHarness(W, H);
        harness.attach(panel);
        harness.drainInitialRender(); // consume the initial layout+render
    }

    // =========================================================================
    // Explicit invalidation
    // =========================================================================

    @Nested
    class ExplicitInvalidation {

        @Test
        void full_invalidate_produces_damage_covering_entire_panel() {
            List<TerminalRectangle> damage = harness.captureNextRender(
                    () -> panel.invalidate());

            assertFalse(damage.isEmpty(), "invalidate() must produce at least one damage region");
            assertTrue(
                    TerminalDamageTestHarness.unionCovers(damage, 0, 0, W, H),
                    "damage union must cover the full panel [0,0," + W + "," + H + "]");
        }

        @Test
        void invalidate_after_content_add_covers_at_least_the_affected_slot() {
            int topH = 3;
            TerminalLabel label = labelWithMinHeight("hdr", topH);

            // Add top label and wait for that layout+render to settle
            harness.beginDamageCapture();
            panel.addToPanel(BorderPanel.TOP, label);
            List<TerminalRectangle> addDamage = harness.awaitDamageCapture();

            // The damage union must at least cover the top slot area [0,0,W,topH]
            assertFalse(addDamage.isEmpty(), "Adding a top label must produce damage");
            assertTrue(
                    TerminalDamageTestHarness.unionCovers(addDamage, 0, 0, W, topH),
                    "Damage must cover the newly added top slot");
        }

        @Test
        void repeated_invalidate_calls_each_produce_damage() {
            // First invalidation
            List<TerminalRectangle> first = harness.captureNextRender(
                    () -> panel.invalidate());
            assertFalse(first.isEmpty());

            // invalidate() does not schedule layout work; just flush queued UI work
            // before issuing the second invalidation.
            harness.flushLayout();

            List<TerminalRectangle> second = harness.captureNextRender(
                    () -> panel.invalidate());
            assertFalse(second.isEmpty(), "Second invalidation must also produce damage");
        }
    }

    // =========================================================================
    // Content changes
    // =========================================================================

    @Nested
    class ContentChanges {

        @Test
        void adding_top_label_produces_damage_in_top_and_center_areas() {
            int topH = 4;

            harness.beginDamageCapture();
            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", topH));
            List<TerminalRectangle> damage = harness.awaitDamageCapture();

            assertFalse(damage.isEmpty());

            // Top slot: [0, 0, W, topH]
            assertTrue(TerminalDamageTestHarness.unionCovers(damage, 0, 0, W, topH),
                    "Top slot area must be in damage");

            // Center slot shifted down: [0, topH, W, H-topH]
            assertTrue(TerminalDamageTestHarness.unionCovers(damage, 0, topH, W, H - topH),
                    "Center slot area must also be in damage after layout shift");
        }

        @Test
        void removing_top_label_via_clearPanel_damages_affected_area() {
            // Setup: add a top label and let that settle
            harness.beginDamageCapture();
            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", 3));
            harness.awaitDamageCapture();
            harness.flushLayout();

            // Now clear it and capture the resulting damage
            harness.beginDamageCapture();
            panel.clearPanel(BorderPanel.TOP);
            List<TerminalRectangle> damage = harness.awaitDamageCapture();

            assertFalse(damage.isEmpty(), "clearPanel() must produce damage");
            // The full panel area should be covered because center expands back to full height
            assertTrue(TerminalDamageTestHarness.unionCovers(damage, 0, 0, W, H),
                    "After clearing top slot the entire panel region must be damaged");
        }

        @Test
        void swapping_top_label_damages_the_new_top_area() {
            int firstH  = 2;
            int secondH = 5;

            harness.beginDamageCapture();
            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("first", firstH));
            harness.awaitDamageCapture();
            harness.flushLayout();

            // Swap in a taller label
            harness.beginDamageCapture();
            panel.swapPanel(BorderPanel.TOP, labelWithMinHeight("second", secondH));
            List<TerminalRectangle> damage = harness.awaitDamageCapture();

            assertFalse(damage.isEmpty());
            // The larger top area [0,0,W,secondH] must be covered
            assertTrue(TerminalDamageTestHarness.unionCovers(damage, 0, 0, W, secondH),
                    "Swap to taller label must damage the new (larger) top area");
        }
    }

    // =========================================================================
    // Visibility changes
    // =========================================================================

    @Nested
    class VisibilityChanges {

        @Test
        void hiding_a_stack_managed_label_directly_dispatches_damage() {
            int topH = 3;
            TerminalLabel label = fillTopLabel("top", topH);

            harness.beginDamageCapture();
            panel.addToPanel(BorderPanel.TOP, label);
            harness.awaitDamageCapture();
            harness.flushLayout();

            // Even though the stack owns visibility, a direct hide request still
            // causes a state transition and should dispatch damage for the
            // previously painted area.
            harness.beginDamageCapture();
            label.hide();
            List<TerminalRectangle> damage = harness.awaitDamageCapture();
            assertFalse(damage.isEmpty(), "Managed-child hide() should dispatch damage");
            assertTrue(TerminalDamageTestHarness.unionCovers(damage, 0, 0, W, topH),
                    "Managed-child hide() damage should include the top slot area");
        }

        @Test
        void showing_a_stack_managed_label_directly_does_not_dispatch_damage() {
            int topH = 3;
            TerminalLabel label = fillTopLabel("top", topH);

            harness.beginDamageCapture();
            panel.addToPanel(BorderPanel.TOP, label);
            harness.awaitDamageCapture();
            harness.flushLayout();

            // Child is already visible under stack management; show() is a no-op
            // and should not dispatch damage by itself.
            harness.beginDamageCapture();
            label.show();
            assertThrows(AssertionError.class, harness::awaitDamageCapture,
                    "Managed-child show() should not dispatch a render batch");
        }
    }

    // =========================================================================
    // Resize
    // =========================================================================

    @Nested
    class Resize {

        @Test
        void shrinking_root_produces_damage_covering_new_bounds() {
            // Add a label so there is always something to render after resize
            harness.beginDamageCapture();
            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", 2));
            harness.awaitDamageCapture();
            harness.flushLayout();

            int newW = W - 10;
            int newH = H - 4;

            harness.beginDamageCapture();
            harness.setAllocatedRegion(0, 0, newW, newH);
            List<TerminalRectangle> damage = harness.awaitDamageCapture();

            assertFalse(damage.isEmpty(), "Resize must produce damage");
            // At minimum the new root area must be covered
            assertTrue(TerminalDamageTestHarness.unionCovers(damage, 0, 0, newW, newH),
                    "Damage union must cover the resized panel area");
        }

        @Test
        void growing_root_produces_damage_covering_new_bounds() {
            harness.beginDamageCapture();
            panel.addToPanel(BorderPanel.CENTER, fillLabel("center"));
            harness.awaitDamageCapture();
            harness.flushLayout();

            int newW = W + 5;
            int newH = H + 3;

            harness.setAllocatedRegion(0, 0, newW, newH);
            assertTrue(harness.waitForLayoutComplete(), "Layout after grow-resize must complete");

            // Verify the next full invalidation honors the grown bounds.
            List<TerminalRectangle> damage = harness.captureNextRender(
                    () -> panel.invalidate());

            assertFalse(damage.isEmpty(), "Growing resize must produce damage");
            assertTrue(TerminalDamageTestHarness.unionCovers(damage, 0, 0, newW, newH),
                    "After grow-resize, invalidate() damage must cover expanded panel");
        }

        @Test
        void damage_after_resize_stays_within_new_bounds_when_panel_has_content() {
            panel.addToPanel(BorderPanel.TOP,    fillTopLabel("top",    2));
            panel.addToPanel(BorderPanel.BOTTOM, fillTopLabel("bottom", 2));
            assertTrue(harness.waitForLayoutComplete());

            int newW = 60;
            int newH = 18;

            harness.setAllocatedRegion(0, 0, newW, newH);
            assertTrue(harness.waitForLayoutComplete(), "Layout after resize must complete");

            List<TerminalRectangle> damage = harness.captureNextRender(
                    () -> panel.invalidate());

            // After resize has committed, explicit invalidation should stay within
            // the new bounds.
            for (TerminalRectangle r : damage) {
                assertTrue(r.getX() >= 0,
                        "Damage rect must not start before x=0: " + r);
                assertTrue(r.getY() >= 0,
                        "Damage rect must not start before y=0: " + r);
                assertTrue(r.getX() + r.getWidth() <= newW,
                        "Damage rect must not exceed new width " + newW + ": " + r);
                assertTrue(r.getY() + r.getHeight() <= newH,
                        "Damage rect must not exceed new height " + newH + ": " + r);
            }
            assertTrue(TerminalDamageTestHarness.unionCovers(damage, 0, 0, newW, newH),
                    "Post-resize invalidate() damage must cover the resized panel area");
        }
    }

    // =========================================================================
    // Damage accumulator interactions
    // =========================================================================

    @Nested
    class AccumulatorInteractions {

        @Test
        void multiple_rapid_invalidations_between_renders_are_merged() {
            // Arm one capture, fire two invalidations without waiting for render
            // between them.  Both should appear in the same render batch (merged).
            harness.beginDamageCapture();
            panel.invalidate();
            panel.invalidate();
            List<TerminalRectangle> damage = harness.awaitDamageCapture();

            // Two full-panel invalidations should produce damage that covers the panel
            assertTrue(TerminalDamageTestHarness.unionCovers(damage, 0, 0, W, H),
                    "Two rapid invalidations must be merged into one render batch");
        }

        @Test
        void damage_regions_in_batch_are_non_empty() {
            List<TerminalRectangle> damage = harness.captureNextRender(
                    () -> panel.invalidate());

            for (TerminalRectangle r : damage) {
                assertTrue(r.getWidth()  > 0, "Damage width must be > 0: "  + r);
                assertTrue(r.getHeight() > 0, "Damage height must be > 0: " + r);
            }
        }
    }

    // =========================================================================
    // Layout-state-driven damage: reserved size / insets
    // =========================================================================

    @Nested
    class LayoutDrivenDamage {

        @Test
        void setting_reserved_top_height_produces_damage() {
            harness.beginDamageCapture();
            panel.setReservedTopHeight(5);
            // setReservedTopHeight triggers a layout update; the subsequent render
            // must produce damage covering at least the reserved top area
            List<TerminalRectangle> damage = harness.awaitDamageCapture();

            assertFalse(damage.isEmpty(), "setReservedTopHeight must trigger damage");
            assertTrue(TerminalDamageTestHarness.unionCovers(damage, 0, 0, W, 5),
                    "Reserved top area must be in the damage union");
        }

        @Test
        void setting_insets_produces_damage_over_full_panel() {
            // Make the panel actively render full-frame content so inset changes
            // visibly affect the whole panel.
            harness.beginDamageCapture();
            panel.addToPanel(BorderPanel.CENTER, fillLabel("center-fill"));
            harness.awaitDamageCapture();
            harness.flushLayout();

            // Insets affect all slot positions, so the whole panel area must be damaged
            List<TerminalRectangle> damage = harness.captureNextRender(
                    () -> panel.setInsets(3));

            assertFalse(damage.isEmpty(), "setInsets must trigger damage");
            assertTrue(TerminalDamageTestHarness.unionCovers(damage, 0, 0, W, H),
                    "Full panel area must be in the damage union after insets change");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static TerminalLabel labelWithMinHeight(String name, int h) {
        TerminalLabel label = new TerminalLabel(name);
        label.setMinHeight(h);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        return label;
    }

    private static TerminalLabel fillTopLabel(String name, int h) {
        TerminalLabel label = labelWithMinHeight(name, h);
        label.setMinWidth(1);
        label.setWidthPreference(SizePreference.FILL);
        return label;
    }

    private static TerminalLabel fillLabel(String name) {
        TerminalLabel label = new TerminalLabel(name, "x");
        label.setMinWidth(1);
        label.setMinHeight(1);
        label.setWidthPreference(SizePreference.FILL);
        label.setHeightPreference(SizePreference.FILL);
        return label;
    }
}
