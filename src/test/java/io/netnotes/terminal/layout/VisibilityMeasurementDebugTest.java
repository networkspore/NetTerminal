package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.components.panels.TerminalPanel;
import io.netnotes.terminal.components.panels.TerminalVStack;
import io.netnotes.terminal.components.text.TerminalLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Debug tests for visibility and content measurement behavior.
 *
 * Canonical behavior:
 * - FIT_CONTENT VStack never auto-hides children on overflow.
 * - For exposed via show() requires multi-pass stabilization.
 * - readDimension uses measured content bounds when available, falls back
 *   to requested region.
 */
public class VisibilityMeasurementDebugTest {

    private TerminalLayoutTestHarness harness;
    private TerminalPanel rootPanel;

    @BeforeEach
    void setup() {
        rootPanel = new TerminalPanel("root");
        harness = new TerminalLayoutTestHarness(80, 24);
        harness.attach(rootPanel);
        assertTrue(harness.waitForLayoutComplete(), "Initial layout pass must complete");
    }

    @Test
    void simpleVisibleVStackGetsLaidOut() {
        TerminalVStack visibleStack = new TerminalVStack("visibleStack");
        visibleStack.setHeightPreference(SizePreference.FIT_CONTENT);

        TerminalLabel label = new TerminalLabel("label1");
        label.setMinHeight(3);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        visibleStack.addChild(label);

        rootPanel.addChild(visibleStack);
        assertTrue(harness.waitForLayoutComplete());

        System.out.println("DEBUG: visibleStack height = " + visibleStack.getHeight());
        System.out.println("DEBUG: label height = " + label.getHeight());
        assertEquals(3, visibleStack.getHeight(), "Visible stack should have height 3");
    }

    @Test
    void simpleHiddenVStackVisibilityToggle() {
        TerminalVStack stack = new TerminalVStack("stack");
        stack.setHeightPreference(SizePreference.FIT_CONTENT);

        TerminalLabel label = new TerminalLabel("label");
        label.setMinHeight(3);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        stack.addChild(label);

        // Start hidden.
        stack.hide();
        rootPanel.addChild(stack);
        assertTrue(harness.waitForLayoutComplete());

        assertEquals(0, stack.getHeight(), "Hidden stack should have height 0");

        // Unhide — multi-pass stabilization.
        stack.show();
        assertTrue(harness.waitForLayoutComplete());

        System.out.println("DEBUG: After unhide - stack height = " + stack.getHeight());
        System.out.println("DEBUG: After unhide - label height = " + label.getHeight());
        assertEquals(3, stack.getHeight(), "Stack should have height 3 after becoming visible");
    }

    @Test
    void probeReadDimensionBehavior() {
        TerminalVStack parent = new TerminalVStack("parent");
        parent.setHeightPreference(SizePreference.FIT_CONTENT);

        TerminalVStack child = new TerminalVStack("child");
        child.setHeightPreference(SizePreference.FIT_CONTENT);
        child.hide();

        TerminalLabel label = new TerminalLabel("childLabel");
        label.setMinHeight(4);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        child.addChild(label);

        parent.addChild(child);
        rootPanel.addChild(parent);
        assertTrue(harness.waitForLayoutComplete());

        System.out.println("=== BEFORE UNHIDE ===");
        System.out.println("child isHidden: " + child.isHidden());
        System.out.println("label isHidden: " + label.isHidden());
        System.out.println("parent.getHeight(): " + parent.getHeight());

        // Unhide child — multi-pass stabilization.
        child.show();
        assertTrue(harness.waitForLayoutComplete());

        System.out.println("\n=== AFTER UNHIDE ===");
        System.out.println("child isHidden: " + child.isHidden());
        System.out.println("label isHidden: " + label.isHidden());
        System.out.println("child.getHeight(): " + child.getHeight());
        System.out.println("parent.getHeight(): " + parent.getHeight());

        assertEquals(4, child.getHeight(), "Child height = 4 after unhide");
        assertEquals(4, parent.getHeight(), "Parent re-measured to 4");
    }
}
