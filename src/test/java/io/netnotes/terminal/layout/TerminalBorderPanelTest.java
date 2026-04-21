package io.netnotes.terminal.layout;

import io.netnotes.terminal.components.TerminalLabel;
import io.netnotes.terminal.components.panels.TerminalBorderPanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TerminalBorderPanelTest extends TerminalLayoutTestBase {
    private TerminalBorderPanel borderPanel;

    @BeforeEach
    void setup() {
        super.setupTestBase();
        borderPanel = new TerminalBorderPanel(
            new TerminalLabel("North"),
            new TerminalLabel("South"),
            new TerminalLabel("East"),
            new TerminalLabel("West"),
            new TerminalLabel("Center")
        );
        rootRegion.add(borderPanel);
    }

    @Test
    void testInitialBounds() {
        assertEquals(0, borderPanel.getX());
        assertEquals(0, borderPanel.getY());
        assertEquals(testWidth, borderPanel.getWidth());
        assertEquals(testHeight, borderPanel.getHeight());
    }

    @Test
    void testInsetsCalculation() {
        var insets = borderPanel.getInsets();
        assertEquals(0, insets.getTop());    // Border panel doesn't add its own border
        assertEquals(0, insets.getBottom());
        assertEquals(0, insets.getLeft());
        assertEquals(0, insets.getRight());
    }

    @Test
    void testInnerRegionBounds() {
        var center = borderPanel.getCenter();
        var insets = borderPanel.getInsets();
        assertEquals(insets.getLeft(), center.getX());
        assertEquals(insets.getTop(), center.getY());
        assertEquals(testWidth - insets.getLeft() - insets.getRight(), center.getWidth());
        assertEquals(testHeight - insets.getTop() - insets.getBottom(), center.getHeight());
    }
}