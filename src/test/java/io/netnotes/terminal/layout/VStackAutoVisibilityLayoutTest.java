package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.components.panels.TerminalPanel;
import io.netnotes.terminal.components.panels.TerminalVStack;
import io.netnotes.terminal.components.text.TerminalLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.netnotes.terminal.layout.TerminalLayoutTestHarness.STATE_LAYOUT_IDLE;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for TerminalVStack visibility behavior.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * SYSTEM CONTRACT — CANONICAL BEHAVIOR
 * ═══════════════════════════════════════════════════════════════════════
 *
 * FIT_CONTENT HEIGHT — NO AUTO-HIDE:
 *   When a VStack has FIT_CONTENT height and allocatedWidth > 0, the
 *   fitContentOverride flag (TerminalVStack:358-370) PREVENTS auto-hiding
 *   children on overflow. All non-force-hidden children are forced visible
 *   in layoutAllChildren so the parent can measure the true content size
 *   and decide whether to grow.
 *
 * FORCE-HIDE (hide()):
 *   hide() sets isHiddenDesired. If not forced, canUnhide() still
 *   returns false (isHiddenDesired=true). The child is excluded from
 *   measureContent and layoutAllChildren won't force it visible.
 *
 * UNHIDE (show()):
 *   applyNode detects becameVisible → markLayoutDirtySubtree() writes to
 *   NEXT-PASS dirty set. Pass 1 commits visibility; Pass 2 lays out the
 *   child subtree; parent re-measures via dirtyAffectedAncestors.
 *   Result: MINIMUM TWO PASSES to stabilize.
 *
 * CONTENT MEASUREMENT GATE:
 *   measureContent uses canUnhide(child) to gate inclusion. Force-hidden
 *   children are excluded; non-hidden children are measured. FIT_CONTENT
 *   parents receive accurate content sizes.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * TEST EXECUTION PATTERN
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Each test registers a persistent step-dispatch handler on STATE_LAYOUT_IDLE
 * BEFORE triggering any layout changes. The handler uses an int[] step counter
 * to advance through numbered cases. Each case runs assertions and queues the
 * next action by calling fire-and-forget harness methods (setAllocatedRegion,
 * triggerRender) or component mutations (show, hide). The final case opens a
 * TestGate so the JUnit thread can exit.
 *
 *   int[] step = {0};
 *   harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
 *       try {
 *           switch (step[0]++) {
 *               case 0 -> { /* assert initial state; trigger next change *\/ }
 *               case 1 -> { /* assert after change; gate.open() *\/ }
 *           }
 *       } catch (Throwable t) { gate.fail(t); }
 *   });
 *   rootPanel.addChild(myComponent);
 *   harness.triggerRender();
 *   gate.awaitDone();
 *
 * STATE_LAYOUT_IDLE is only entered once ALL queued layout passes have
 * settled. Intermediate passes move between ACTIVE and PENDING, so the
 * step counter advances exactly once per fully-stable idle — the handler
 * never observes a false idle between passes.
 *
 * All assertions inside step callbacks execute on the UI thread with
 * stable, committed geometry. No separate thread-switch is needed.
 */
public class VStackAutoVisibilityLayoutTest {

    private static final int W = 80;
    private static final int H = 24;

    private TerminalLayoutTestHarness harness;
    private TerminalPanel rootPanel;

    @BeforeEach
    void setup() {
        rootPanel = new TerminalPanel("root");
        harness   = new TerminalLayoutTestHarness(W, H);
        // attach() blocks until the harness reaches STATE_LAYOUT_IDLE for the
        // first time. After this returns, isIdle() is true and every test can
        // immediately register its step-dispatch handler.
        harness.attach(rootPanel);
    }

    @AfterEach
    void cleanup() {
        // harness cleanup handled by GC / test runner
    }

    // =========================================================================
    // TEST 1 — FIT_CONTENT VStack never auto-hides children on overflow
    // =========================================================================

    /**
     * A FIT_CONTENT VStack with children that would overflow the allocated
     * region must NOT auto-hide those children. The fitContentOverride path
     * in layoutAllChildren forces all non-force-hidden children visible.
     */
    @Test
    void fitContentVStackNeverAutoHidesChildren() {
        TerminalVStack vstack = new TerminalVStack("vstack");
        vstack.setHeightPreference(SizePreference.FIT_CONTENT);
        for (int i = 0; i < 5; i++) vstack.addChild(createLabel("label-" + i, 3));

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        // Initial layout settled — all 5 children stacked.
                        assertEquals(15, vstack.getHeight(),
                            "All 5 children visible: 5 × 3 = 15");

                        // Shrink allocated region below content height.
                        harness.setAllocatedRegion(0, 0, W, 7); // → triggers step 1
                    }
                    case 1 -> {
                        // FIT_CONTENT overrides auto-hide regardless of overflow.
                        for (int i = 0; i < vstack.getChildren().size(); i++) {
                            assertFalse(vstack.getChildren().get(i).isHidden(),
                                "Child " + i + " must NOT be auto-hidden (FIT_CONTENT VStack)");
                        }

                        // Restore region.
                        harness.setAllocatedRegion(0, 0, W, H); // → triggers step 2
                    }
                    case 2 -> {
                        // All children still visible; height unchanged.
                        assertEquals(15, vstack.getHeight(),
                            "All 5 children: 5 × 3 = 15");
                        for (int i = 0; i < vstack.getChildren().size(); i++) {
                            assertFalse(vstack.getChildren().get(i).isHidden(),
                                "Child " + i + " visible after restore");
                        }
                        gate.open();
                    }
                }
            } catch (Throwable t) {
                gate.fail(t);
            }
        });

        rootPanel.addChild(vstack);
        harness.triggerRender();
        gate.awaitDone();
    }

    // =========================================================================
    // TEST 2 — force-hide (hide()) is respected in FIT_CONTENT VStack
    // =========================================================================

    /**
     * hide() on a child makes canUnhide() return false.
     * measureContent excludes it; layoutAllChildren won't force it visible.
     */
    @Test
    void forceHiddenChildExcludedFromMeasurement() {
        TerminalVStack vstack      = new TerminalVStack("vstack");
        TerminalLabel visibleLabel = createLabel("visible", 3);
        TerminalLabel hiddenLabel  = createLabel("hidden", 4);

        vstack.setHeightPreference(SizePreference.FIT_CONTENT);
        vstack.addChild(visibleLabel);
        vstack.addChild(hiddenLabel);
        hiddenLabel.hide();

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        // Initial layout: force-hidden child excluded from measurement.
                        assertTrue(hiddenLabel.isHidden(),   "hiddenLabel should be hidden");
                        assertFalse(visibleLabel.isHidden(), "visibleLabel should be visible");
                        assertEquals(3, vstack.getHeight(),
                            "VStack measures only non-hidden children: 3");

                        hiddenLabel.show(); // → triggers ≥2 passes; step 1 fires after all settle
                    }
                    case 1 -> {
                        // After multi-pass stabilization: both children measured.
                        assertFalse(hiddenLabel.isHidden(), "hiddenLabel visible after show()");
                        assertEquals(7, vstack.getHeight(),
                            "VStack now includes both children: 3 + 4 = 7");
                        gate.open();
                    }
                }
            } catch (Throwable t) {
                gate.fail(t);
            }
        });

        rootPanel.addChild(vstack);
        harness.triggerRender();
        gate.awaitDone();
    }

    // =========================================================================
    // TEST 3 — two-pass resolution for unhide (show())
    // =========================================================================

    /**
     * Explicit unhide via show() requires ≥2 passes:
     *   Pass 1: visibility flip committed
     *   Pass 2: child laid out, parent re-measures
     *
     * Because STATE_LAYOUT_IDLE is only entered after all passes drain, step 1
     * sees the fully-stabilized geometry without any special handling.
     */
    @Test
    void unhideRequiresTwoPasses() {
        TerminalVStack container = new TerminalVStack("container");
        TerminalVStack child     = new TerminalVStack("child");

        container.setHeightPreference(SizePreference.FIT_CONTENT);
        child.setHeightPreference(SizePreference.FIT_CONTENT);
        child.addChild(createLabel("childLabel", 5));
        container.addChild(child);
        child.hide();

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        // Initial layout with child hidden.
                        assertTrue(child.isHidden(),
                            "Child starts hidden");
                        assertEquals(0, container.getHeight(),
                            "Container height 0 with hidden child");

                        child.show(); // → triggers ≥2 passes; step 1 fires after all settle
                    }
                    case 1 -> {
                        // All passes complete: child laid out, parent re-measured.
                        assertFalse(child.isHidden(),          "Child visible after unhide");
                        assertEquals(5, child.getHeight(),     "Child height = 5");
                        assertEquals(5, container.getHeight(), "Container re-measured to 5");
                        gate.open();
                    }
                }
            } catch (Throwable t) {
                gate.fail(t);
            }
        });

        rootPanel.addChild(container);
        harness.triggerRender();
        gate.awaitDone();
    }

    // =========================================================================
    // TEST 4 — multiple force-hidden children, then unhide all
    // =========================================================================

    @Test
    void multipleForceHiddenChildrenRestoreCorrectly() {
        TerminalVStack vstack = new TerminalVStack("vstack");
        vstack.setHeightPreference(SizePreference.FIT_CONTENT);

        TerminalLabel[] labels = new TerminalLabel[5];
        for (int i = 0; i < 5; i++) {
            labels[i] = createLabel("label-" + i, 2);
            vstack.addChild(labels[i]);
        }
        labels[2].hide();
        labels[3].hide();
        labels[4].hide();

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        // Initial layout: only 2 of 5 children visible.
                        assertEquals(4, vstack.getHeight(),
                            "Only 2 visible children: 2 × 2 = 4");

                        // Unhide all three force-hidden children at once.
                        for (int i = 2; i < 5; i++) labels[i].show(); // → triggers step 1
                    }
                    case 1 -> {
                        // All 5 children now measured and visible.
                        assertEquals(10, vstack.getHeight(),
                            "All 5 children: 5 × 2 = 10");
                        for (int i = 0; i < 5; i++) {
                            assertFalse(labels[i].isHidden(),
                                "Child " + i + " should be visible");
                        }
                        gate.open();
                    }
                }
            } catch (Throwable t) {
                gate.fail(t);
            }
        });

        rootPanel.addChild(vstack);
        harness.triggerRender();
        gate.awaitDone();
    }

    // =========================================================================
    // TEST 5 — FIT_CONTENT parent with content-sized child VStack
    // =========================================================================

    @Test
    void fitContentParentWithContentSizedChild() {
        TerminalVStack parent = new TerminalVStack("parent");
        TerminalVStack child  = new TerminalVStack("child");

        parent.setHeightPreference(SizePreference.FIT_CONTENT);
        child.setHeightPreference(SizePreference.FIT_CONTENT);
        child.addChild(createLabel("grandchild1", 3));
        child.addChild(createLabel("grandchild2", 3));
        parent.addChild(child);

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        // Initial layout: parent grows to fit child content.
                        assertEquals(6, child.getHeight(),  "Child: 2 × 3 = 6");
                        assertEquals(6, parent.getHeight(), "Parent: 6");

                        child.hide(); // → triggers step 1
                    }
                    case 1 -> {
                        // Hidden child excluded; parent collapses to 0.
                        assertTrue(child.isHidden(),        "Child is hidden");
                        assertEquals(0, parent.getHeight(), "Parent shrinks to 0");

                        child.show(); // → triggers step 2 (multi-pass)
                    }
                    case 2 -> {
                        // Child re-shown; both heights restored.
                        assertFalse(child.isHidden(),       "Child visible again");
                        assertEquals(6, child.getHeight(),  "Child: 2 × 3 = 6");
                        assertEquals(6, parent.getHeight(), "Parent: 6");
                        gate.open();
                    }
                }
            } catch (Throwable t) {
                gate.fail(t);
            }
        });

        rootPanel.addChild(parent);
        harness.triggerRender();
        gate.awaitDone();
    }

    // =========================================================================
    // TEST 6 — rapid resize does not leave stale state
    // =========================================================================

    @Test
    void rapidResizeStabilizes() {
        TerminalVStack vstack = new TerminalVStack("vstack");
        TerminalLabel label1  = createLabel("label1", 3);
        TerminalLabel label2  = createLabel("label2", 3);
        TerminalLabel label3  = createLabel("label3", 3);

        vstack.setHeightPreference(SizePreference.FIT_CONTENT);
        vstack.addChild(label1);
        vstack.addChild(label2);
        vstack.addChild(label3);

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        // Initial layout settled.
                        assertEquals(9, vstack.getHeight(),
                            "All children visible: 3 × 3 = 9");

                        // Fire several resizes without waiting between them. The
                        // layout system debounces and will produce a single idle
                        // entry reflecting the final committed region (H).
                        harness.setAllocatedRegion(0, 0, W, 4);
                        harness.setAllocatedRegion(0, 0, W, 2);
                        harness.setAllocatedRegion(0, 0, W, 6);
                        harness.setAllocatedRegion(0, 0, W, H); // → triggers step 1
                    }
                    case 1 -> {
                        // Layout settled on final region; no stale state.
                        assertEquals(9, vstack.getHeight(),
                            "Geometry matches final resize (all visible): 3 × 3 = 9");
                        assertFalse(label1.isHidden(), "label1 visible");
                        assertFalse(label2.isHidden(), "label2 visible");
                        assertFalse(label3.isHidden(), "label3 visible");
                        gate.open();
                    }
                }
            } catch (Throwable t) {
                gate.fail(t);
            }
        });

        rootPanel.addChild(vstack);
        harness.triggerRender();
        gate.awaitDone();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private TerminalLabel createLabel(String name, int minHeight) {
        TerminalLabel label = new TerminalLabel(name);
        label.setMinHeight(minHeight);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        return label;
    }

    // =========================================================================
    // TestGate — single JUnit completion barrier per test
    // =========================================================================

    /**
     * Coordinates between UI-thread step callbacks and the JUnit test thread.
     *
     * UI thread calls open() when all steps pass, or fail(t) on the first
     * assertion error. The JUnit thread calls awaitDone() once at the end of
     * the test method. Any assertion error captured on the UI thread is
     * re-thrown on the JUnit thread so the test fails with the correct message
     * and stack trace.
     *
     * This is the ONLY synchronization point per test. There are no intermediate
     * waits — the state machine drives all step sequencing.
     */
    static final class TestGate {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile Throwable failure;

        /** Call from the final step callback when all assertions have passed. */
        void open() {
            latch.countDown();
        }

        /** Call from any step callback when an assertion (or other error) is caught. */
        void fail(Throwable t) {
            failure = t;
            latch.countDown();
        }

        /** Block the JUnit thread until the step chain completes or times out. */
        void awaitDone() {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError(
                        "Test timed out: STATE_LAYOUT_IDLE never reached final step. " +
                        "Check that triggerRender() was called and all step actions " +
                        "cause a layout pass.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Test interrupted", e);
            }
            if (failure instanceof AssertionError a) throw a;
            if (failure != null) throw new AssertionError("Test step failed", failure);
        }
    }
}