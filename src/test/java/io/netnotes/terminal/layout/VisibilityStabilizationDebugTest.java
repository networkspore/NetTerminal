package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.components.panels.TerminalPanel;
import io.netnotes.terminal.components.panels.TerminalVStack;
import io.netnotes.terminal.components.text.TerminalLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Debug tests for visibility and content sizing behavior.
 *
 * Canonical behavior: a FIT_CONTENT-height VStack never auto-hides children
 * on overflow (fitContentOverride in layoutAllChildren). Instead, all
 * non-force-hidden children stay visible and the VStack reports its full
 * content size so the parent can decide whether to grow.
 */
public class VisibilityStabilizationDebugTest {

    private TerminalLayoutTestHarness harness;
    private TerminalPanel rootPanel;

    @BeforeEach
    void setup() {
        rootPanel = new TerminalPanel("root");
        harness = new TerminalLayoutTestHarness(80, 24);
        harness.attach(rootPanel);
        assertTrue(harness.waitForLayoutComplete());
    }

    @Test
    void debugFitContentVStackOverflowNeverHidesChildren() {
        TerminalVStack container = new TerminalVStack("container");
        container.setHeightPreference(SizePreference.FIT_CONTENT);

        // 5 labels × 3 height = total 15
        for (int i = 0; i < 5; i++) {
            container.addChild(createLabel("label-" + i, 3));
        }

        rootPanel.addChild(container);
        assertTrue(harness.waitForLayoutComplete());
        debugState("Initial layout (plenty of space)");

        assertEquals(15, container.getHeight(), "All 5 children: 5 × 3 = 15");

        // Shrink to only fit 2 labels — region changes but content hasn't changed.
        // For a FIT_CONTENT VStack, getHeight() reflects the layout height which
        // is based on allocated region. Content size is unchanged.
        harness.setAllocatedRegion(0, 0, 80, 7);
        assertTrue(harness.waitForLayoutComplete());
        debugState("After shrink to height 7 (overflow, no auto-hide)");

        // Children still visible — FIT_CONTENT VStack never auto-hides.
        for (int i = 0; i < container.getChildren().size(); i++) {
            assertFalse(container.getChildren().get(i).isHidden(),
                "Child " + i + " must NOT be auto-hidden");
        }

        // Expand back — no stale hidden state.
        harness.setAllocatedRegion(0, 0, 80, 24);
        assertTrue(harness.waitForLayoutComplete());
        debugState("After expand back to 24");

        assertEquals(15, container.getHeight(), "Still 15 after restore");
    }

    @Test
    void debugContentSizedChildVisibilityToggle() {
        TerminalVStack parent = new TerminalVStack("parent");
        parent.setHeightPreference(SizePreference.FIT_CONTENT);

        TerminalVStack staticChild = new TerminalVStack("staticChild");
        staticChild.setHeightPreference(SizePreference.STATIC);
        staticChild.setMinHeight(2);
        staticChild.addChild(createLabel("static-label", 2));

        TerminalVStack contentChild = new TerminalVStack("contentChild");
        contentChild.setHeightPreference(SizePreference.FIT_CONTENT);
        contentChild.addChild(createLabel("content", 5));

        parent.addChild(staticChild);
        parent.addChild(contentChild);
        rootPanel.addChild(parent);

        assertTrue(harness.waitForLayoutComplete());
        debugState("Initial: both children visible");

        // Force-hide content child — parent should shrink.
        contentChild.hide();
        assertTrue(harness.waitForLayoutComplete());
        debugState("After force-hiding content child");

        assertTrue(contentChild.isHidden(), "contentChild is force-hidden");
        assertEquals(2, parent.getHeight(), "Parent = 2 (only staticChild)");

        // Unhide — multi-pass stabilization.
        contentChild.show();
        assertTrue(harness.waitForLayoutComplete());
        debugState("After unhiding content child");

        assertFalse(contentChild.isHidden(), "contentChild visible after unhide");
        assertEquals(5, contentChild.getHeight(), "contentChild height = 5");
        assertEquals(7, parent.getHeight(), "Parent re-measured: 2 + 5 = 7");
    }

    private void debugState(String label) {
        System.out.println("\n=== " + label + " ===");
    }

    private TerminalLabel createLabel(String name, int height) {
        TerminalLabel label = new TerminalLabel(name);
        label.setMinHeight(height);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        return label;
    }
}
