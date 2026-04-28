package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.components.panels.TerminalPanel;
import io.netnotes.terminal.components.panels.TerminalVStack;
import io.netnotes.terminal.components.text.TerminalLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.netnotes.terminal.layout.TerminalLayoutTestHarness.STATE_LAYOUT_IDLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for content-sized VStacks with visibility toggling.
 *
 * Canonical behavior:
 * - FIT_CONTENT VStack never auto-hides children on overflow (fitContentOverride).
 * - Force-hide via hide() is respected; child excluded from measurement.
 * - Unhide via show() requires ≥2 passes to stabilize (visibility flip
 *   commits in pass 1; child layout + parent re-measure in pass 2+).
 * - Parent VStacks re-measure correctly after a child becomes visible.
 */
public class VisibilityContentSizeLayoutTest {

    private static final int W = 80;
    private static final int H = 24;

    private TerminalLayoutTestHarness harness;
    private TerminalPanel rootPanel;

    @BeforeEach
    void setup() {
        rootPanel = new TerminalPanel("root");
        harness = new TerminalLayoutTestHarness(W, H);
        harness.attach(rootPanel); // blocks until first layout idle
    }

    @AfterEach
    void cleanup() {
        // harness cleanup handled by GC / test runner
    }

    @Nested
    class NestedVStackVisibility {

        @Test
        void nestedVStackBecomesVisibleAfterParentMeasure() {
            TerminalVStack parentVStack = new TerminalVStack("parent");
            parentVStack.setHeightPreference(SizePreference.FIT_CONTENT);

            TerminalLabel fixedLabel = new TerminalLabel("fixed");
            fixedLabel.setMinHeight(3);
            fixedLabel.setHeightPreference(SizePreference.FIT_CONTENT);
            parentVStack.addChild(fixedLabel);

            TerminalVStack nestedVStack = new TerminalVStack("nested");
            nestedVStack.setHeightPreference(SizePreference.FIT_CONTENT);

            for (int i = 0; i < 2; i++) {
                TerminalLabel label = new TerminalLabel("label-" + i);
                label.setMinHeight(2);
                label.setHeightPreference(SizePreference.FIT_CONTENT);
                nestedVStack.addChild(label);
            }

            // Start with nested stack force-hidden.
            nestedVStack.hide();
            parentVStack.addChild(nestedVStack);

            TestGate gate = new TestGate();
            int[] step = {0};

            harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
                try {
                    switch (step[0]++) {
                        case 0 -> {
                            // Initial layout: only fixedLabel visible.
                            assertEquals(3, parentVStack.getHeight(), "Parent = fixedLabel height (3)");
                            assertTrue(nestedVStack.isHidden(), "Nested stack is force-hidden");

                            nestedVStack.show(); // → triggers multi-pass stabilization
                        }
                        case 1 -> {
                            // After unhide: nested stack laid out, parent re-measured.
                            assertFalse(nestedVStack.isHidden(), "Nested stack visible after unhide");
                            assertEquals(4, nestedVStack.getHeight(), "Nested: 2 labels × 2 = 4");
                            assertEquals(7, parentVStack.getHeight(), "Parent: 3 + 4 = 7");
                            gate.open();
                        }
                    }
                } catch (Throwable t) {
                    gate.fail(t);
                }
            });

            rootPanel.addChild(parentVStack);
            harness.triggerRender();
            gate.awaitDone();
        }

        @Test
        void multipleNestedVStacksBecomeVisible() {
            TerminalVStack rootStack = new TerminalVStack("root");
            rootStack.setHeightPreference(SizePreference.FIT_CONTENT);

            TerminalLabel header = new TerminalLabel("header");
            header.setMinHeight(2);
            header.setHeightPreference(SizePreference.FIT_CONTENT);
            rootStack.addChild(header);

            TerminalVStack section1 = createContentStack("section1", 3);
            TerminalVStack section2 = createContentStack("section2", 4);
            section1.hide();
            section2.hide();

            rootStack.addChild(section1);
            rootStack.addChild(section2);

            TestGate gate = new TestGate();
            int[] step = {0};

            harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
                try {
                    switch (step[0]++) {
                        case 0 -> {
                            assertEquals(2, rootStack.getHeight(), "Only header visible: 2");

                            section1.show();
                            section2.show(); // → triggers multi-pass
                        }
                        case 1 -> {
                            assertFalse(section1.isHidden());
                            assertFalse(section2.isHidden());
                            assertEquals(9, rootStack.getHeight(), "2 + 3 + 4 = 9");
                            gate.open();
                        }
                    }
                } catch (Throwable t) {
                    gate.fail(t);
                }
            });

            rootPanel.addChild(rootStack);
            harness.triggerRender();
            gate.awaitDone();
        }

        @Test
        void deeplyNestedVisibilityChain() {
            TerminalVStack deepParent = new TerminalVStack("deepParent");
            deepParent.setHeightPreference(SizePreference.FIT_CONTENT);

            TerminalVStack level1 = createContentStack("level1", 3);
            TerminalVStack level2 = createContentStack("level2", 2);
            TerminalVStack level3 = createContentStack("level3", 2);

            level2.addChild(level3);
            level1.addChild(level2);
            deepParent.addChild(level1);

            // Force-hide all levels.
            deepParent.hide();
            level1.hide();
            level2.hide();
            level3.hide();

            TestGate gate = new TestGate();
            int[] step = {0};

            harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
                try {
                    switch (step[0]++) {
                        case 0 -> {
                            // All hidden.
                            assertTrue(deepParent.isHidden());
                            // Unhide top-down step by step. Each show() will cause
                            // a new idle transition, so we sequence through separate cases.
                            deepParent.show(); // → step 1
                        }
                        case 1 -> {
                            level1.show(); // → step 2
                        }
                        case 2 -> {
                            level2.show(); // → step 3
                        }
                        case 3 -> {
                            level3.show(); // → step 4 (final)
                        }
                        case 4 -> {
                            assertFalse(level3.isHidden());
                            assertFalse(level2.isHidden());
                            assertFalse(level1.isHidden());
                            assertFalse(deepParent.isHidden());

                            assertEquals(2, level3.getHeight());
                            assertEquals(4, level2.getHeight());
                            assertEquals(7, level1.getHeight());
                            assertEquals(7, deepParent.getHeight());
                            gate.open();
                        }
                    }
                } catch (Throwable t) {
                    gate.fail(t);
                }
            });

            rootPanel.addChild(deepParent);
            harness.triggerRender();
            gate.awaitDone();
        }
    }

    @Nested
    class VisibilityFlipContentRace {

        @Test
        void visibilityFlipWithContentSizedChildren() {
            TerminalVStack container = new TerminalVStack("container");
            container.setHeightPreference(SizePreference.FIT_CONTENT);

            TerminalLabel child1 = createFixedLabel("child1", 3);
            TerminalLabel child2 = createFixedLabel("child2", 3);
            child2.hide();

            container.addChild(child1);
            container.addChild(child2);

            TestGate gate = new TestGate();
            int[] step = {0};

            harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
                try {
                    switch (step[0]++) {
                        case 0 -> {
                            assertEquals(3, container.getHeight(), "Only child1 visible: 3");
                            child1.hide();
                            child2.show(); // toggle → multi-pass
                        }
                        case 1 -> {
                            assertEquals(3, container.getHeight(), "Now child2 visible: 3");
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

        @Test
        void contentDependentVisibilityRequiresTwoPasses() {
            TerminalVStack parent = new TerminalVStack("parent");
            parent.setHeightPreference(SizePreference.FIT_CONTENT);

            TerminalVStack child = new TerminalVStack("child");
            child.setHeightPreference(SizePreference.FIT_CONTENT);
            child.hide();

            TerminalLabel label1 = createFixedLabel("label1", 2);
            TerminalLabel label2 = createFixedLabel("label2", 2);
            child.addChild(label1);
            child.addChild(label2);

            parent.addChild(child);

            TestGate gate = new TestGate();
            int[] step = {0};

            harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
                try {
                    switch (step[0]++) {
                        case 0 -> {
                            assertEquals(0, parent.getHeight(), "Parent height 0 with hidden child");
                            child.show(); // → multi-pass
                        }
                        case 1 -> {
                            assertEquals(4, child.getHeight(), "Child: 2 labels × 2 = 4");
                            assertEquals(4, parent.getHeight(), "Parent re-measured: 4");
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
    }

    private TerminalVStack createContentStack(String name, int height) {
        TerminalVStack stack = new TerminalVStack(name);
        stack.setHeightPreference(SizePreference.FIT_CONTENT);
        TerminalLabel label = new TerminalLabel(name + "-label");
        label.setMinHeight(height);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        stack.addChild(label);
        return stack;
    }

    private TerminalLabel createFixedLabel(String name, int height) {
        TerminalLabel label = new TerminalLabel(name);
        label.setMinHeight(height);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        return label;
    }

    // ── TestGate ─────────────────────────────────────────────────────────
    static final class TestGate {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile Throwable failure;

        void open() { latch.countDown(); }

        void fail(Throwable t) {
            failure = t;
            latch.countDown();
        }

        void awaitDone() {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Test timed out: STATE_LAYOUT_IDLE never reached final step");
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