package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.components.panels.TerminalPanel;
import io.netnotes.terminal.components.panels.TerminalVStack;
import io.netnotes.terminal.components.text.TerminalLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        harness.attach(rootPanel);
        assertTrue(harness.waitForLayoutComplete(), "Initial layout pass must complete");
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
            rootPanel.addChild(parentVStack);

            assertTrue(harness.waitForLayoutComplete(), "Initial layout");
            assertEquals(3, parentVStack.getHeight(), "Parent = fixedLabel height (3)");
            assertTrue(nestedVStack.isHidden(), "Nested stack is force-hidden");

            // Unhide — multi-pass stabilization.
            nestedVStack.show();
            assertTrue(harness.waitForLayoutComplete(), "After unhiding nested");

            assertFalse(nestedVStack.isHidden(), "Nested stack visible after unhide");
            assertEquals(4, nestedVStack.getHeight(), "Nested: 2 labels × 2 = 4");
            assertEquals(7, parentVStack.getHeight(), "Parent: 3 + 4 = 7");
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
            rootPanel.addChild(rootStack);

            assertTrue(harness.waitForLayoutComplete(), "Initial layout");
            assertEquals(2, rootStack.getHeight(), "Only header visible: 2");

            section1.show();
            section2.show();
            assertTrue(harness.waitForLayoutComplete(), "After unhiding both");

            assertFalse(section1.isHidden());
            assertFalse(section2.isHidden());
            assertEquals(9, rootStack.getHeight(), "2 + 3 + 4 = 9");
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

            rootPanel.addChild(deepParent);
            assertTrue(harness.waitForLayoutComplete(), "All hidden");

            // Unhide top-down.
            deepParent.show();
            assertTrue(harness.waitForLayoutComplete(), "Unhide deepParent");

            level1.show();
            assertTrue(harness.waitForLayoutComplete(), "Unhide level1");

            level2.show();
            assertTrue(harness.waitForLayoutComplete(), "Unhide level2");

            level3.show();
            assertTrue(harness.waitForLayoutComplete(), "Unhide level3");

            assertEquals(2, level3.getHeight());
            assertEquals(4, level2.getHeight());
            assertEquals(7, level1.getHeight());
            assertEquals(7, deepParent.getHeight());
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
            rootPanel.addChild(container);

            assertTrue(harness.waitForLayoutComplete(), "Initial layout");
            assertEquals(3, container.getHeight(), "Only child1 visible: 3");

            // Toggle: hide child1, unhide child2.
            child1.hide();
            child2.show();
            assertTrue(harness.waitForLayoutComplete(), "After toggle");

            assertEquals(3, container.getHeight(), "Now child2 visible: 3");
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
            rootPanel.addChild(parent);
            assertTrue(harness.waitForLayoutComplete(), "Initial layout");

            assertEquals(0, parent.getHeight(), "Parent height 0 with hidden child");

            child.show();
            assertTrue(harness.waitForLayoutComplete(), "After unhide");

            assertEquals(4, child.getHeight(), "Child: 2 labels × 2 = 4");
            assertEquals(4, parent.getHeight(), "Parent re-measured: 4");
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
}
