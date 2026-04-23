package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.BorderPanel;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.components.panels.TerminalBorderPanel;
import io.netnotes.terminal.components.panels.TerminalStackPanel;
import io.netnotes.terminal.components.text.TerminalLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test to trace measurement flow
 */
public class TerminalBorderPanelDebugTest {

    private static final int W = 80;
    private static final int H = 24;

    private TerminalLayoutTestHarness harness;
    private TerminalBorderPanel panel;

    @BeforeEach
    void setup() {
        panel = new TerminalBorderPanel("bp");
        harness = new TerminalLayoutTestHarness(W, H);
        harness.attach(panel);
        assertTrue(harness.waitForLayoutComplete(), "Initial layout must complete");
    }

    @Test
    void debug_simple_label_in_top() {
        TerminalLabel label = new TerminalLabel("header");
        label.setMinHeight(3);
        label.setHeightPreference(SizePreference.FIT_CONTENT);

        panel.addToPanel(BorderPanel.TOP, label);
        assertTrue(harness.waitForLayoutComplete(), "Layout after adding label must complete");

        TerminalStackPanel topStack = panel.getRegionStack(BorderPanel.TOP);

        System.out.println("\n=== After layout ===");
        System.out.println("topStack.getRegion() = " + topStack.getRegion());
        System.out.println("topStack.getHeight() = " + topStack.getHeight());
        System.out.println("topStack.getContent().getRegion() = " + topStack.getContent().getRegion());
        System.out.println("topStack.getContent().getMinHeight() = " + ((TerminalLabel)topStack.getContent()).getMinHeight());
        System.out.println("topStack.getContent().getHeightPreference() = " + ((TerminalLabel)topStack.getContent()).getHeightPreference());

        // The key insight: if the child has FIT_CONTENT and the stack has FIT_CONTENT,
        // the stack should look at the child's LAYOUT CONTEXT measured bounds, not the child's region
        assertEquals(3, topStack.getHeight(), "TOP should have height 3");
    }
}