package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.BorderPanel;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.components.panels.TerminalBorderPanel;
import io.netnotes.terminal.components.panels.TerminalStackPanel;
import io.netnotes.terminal.components.text.TerminalLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout-driven tests for TerminalBorderPanel.
 *
 * These tests assert committed geometry (x/y/width/height) after full layout
 * drain, using TerminalLayoutTestHarness to mirror root attach behavior.
 */
public class TerminalBorderPanelLayoutTest {

    private static final int W = 80;
    private static final int H = 24;

    private TerminalLayoutTestHarness harness;
    private TerminalBorderPanel panel;

    @BeforeEach
    void setup() {

        panel = new TerminalBorderPanel("bp");
        harness = new TerminalLayoutTestHarness(W, H);
        harness.attach(panel);
        assertTrue(harness.waitForLayoutComplete(), "Initial layout pass must complete");
    }

    private TerminalStackPanel stack(BorderPanel region) {
        return panel.getRegionStack(region);
    }

    @Nested
    class EmptyPanelLayout {
        @Test
        void panelFillsRoot() {
            assertEquals(0, panel.getX());
            assertEquals(0, panel.getY());
            assertEquals(W, panel.getWidth());
            assertEquals(H, panel.getHeight());
        }

        @Test
        void centerStackFillsEntireAreaWhenNoSlotsOccupied() {
       
            TerminalStackPanel center = stack(BorderPanel.CENTER);
            assertEquals(0, center.getX());
            assertEquals(0, center.getY());
            assertEquals(W, center.getWidth());
            assertEquals(H, center.getHeight());
        }
    }

    @Nested
    class TopSlotLayout {
        @Test
        void topSlotReducesCenterHeight() {
            int labelHeight = 3;
            TerminalLabel topLabel = new TerminalLabel("header");
            topLabel.setMinHeight(labelHeight);
            topLabel.setHeightPreference(SizePreference.FIT_CONTENT);

            panel.addToPanel(BorderPanel.TOP, topLabel);
            assertTrue(harness.waitForLayoutComplete(), "Layout after addToPanel must complete");

            TerminalStackPanel top = stack(BorderPanel.TOP);
            TerminalStackPanel center = stack(BorderPanel.CENTER);
            assertEquals(0, top.getY());
            assertEquals(labelHeight, top.getHeight());
            assertEquals(labelHeight, center.getY());
            assertEquals(H - labelHeight, center.getHeight());
        }

        @Test
        void topStackSpansFullWidth() {
            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("h", 1));
            assertTrue(harness.waitForLayoutComplete(), "Layout after addToPanel must complete");
            assertEquals(0, stack(BorderPanel.TOP).getX());
            assertEquals(W, stack(BorderPanel.TOP).getWidth());
        }
    }

    @Nested
    class BottomSlotLayout {
        @Test
        void bottomSlotAnchorsToBottomEdge() {
            int labelHeight = 2;
            panel.addToPanel(BorderPanel.BOTTOM, labelWithMinHeight("footer", labelHeight));
            assertTrue(harness.waitForLayoutComplete(), "Layout after addToPanel must complete");

            TerminalStackPanel bottom = stack(BorderPanel.BOTTOM);
            assertEquals(H - labelHeight, bottom.getY());
            assertEquals(labelHeight, bottom.getHeight());
            assertEquals(W, bottom.getWidth());
        }

        @Test
        void topAndBottomLeaveCorrectMiddleHeight() {
            int topH = 2;
            int bottomH = 3;
            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", topH));
            panel.addToPanel(BorderPanel.BOTTOM, labelWithMinHeight("bottom", bottomH));
            assertTrue(harness.waitForLayoutComplete(), "Layout after addToPanel must complete");

            int expectedCenterH = H - topH - bottomH;
            assertEquals(expectedCenterH, stack(BorderPanel.CENTER).getHeight());
            assertEquals(topH, stack(BorderPanel.CENTER).getY());
        }
    }

    @Nested
    class SideSlotLayout {
        @Test
        void leftSlotStartsAtLeftEdge() {
            int labelW = 10;
            panel.addToPanel(BorderPanel.LEFT, labelWithMinWidth("nav", labelW));
            assertTrue(harness.waitForLayoutComplete(), "Layout after addToPanel must complete");

            TerminalStackPanel left = stack(BorderPanel.LEFT);
            assertEquals(0, left.getX());
            assertEquals(labelW, left.getWidth());
        }

        @Test
        void rightSlotAnchorsToRightEdge() {
            int labelW = 8;
            panel.addToPanel(BorderPanel.RIGHT, labelWithMinWidth("sidebar", labelW));
            assertTrue(harness.waitForLayoutComplete(), "Layout after addToPanel must complete");

            TerminalStackPanel right = stack(BorderPanel.RIGHT);
            assertEquals(W - labelW, right.getX());
            assertEquals(labelW, right.getWidth());
        }

        @Test
        void leftAndRightReduceCenterWidth() {
            int leftW = 10;
            int rightW = 5;
            panel.addToPanel(BorderPanel.LEFT, labelWithMinWidth("nav", leftW));
            panel.addToPanel(BorderPanel.RIGHT, labelWithMinWidth("sidebar", rightW));
            assertTrue(harness.waitForLayoutComplete(), "Layout after addToPanel must complete");

            int expectedCenterW = W - leftW - rightW;
            TerminalStackPanel center = stack(BorderPanel.CENTER);
            assertEquals(leftW, center.getX());
            assertEquals(expectedCenterW, center.getWidth());
        }

        @Test
        void sideStacksOccupyMiddleRowOnly() {
            int topH = 2;
            int leftW = 10;
            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", topH));
            panel.addToPanel(BorderPanel.LEFT, labelWithMinWidth("nav", leftW));
            assertTrue(harness.waitForLayoutComplete(), "Layout after addToPanel must complete");

            assertEquals(topH, stack(BorderPanel.LEFT).getY());
            assertEquals(H - topH, stack(BorderPanel.LEFT).getHeight());
        }
    }

    @Nested
    class ReservedSizeLayout {
        @Test
        void reservedTopHeightAppliesWhenSlotIsEmpty() {
            panel.setReservedTopHeight(4);
            assertTrue(harness.waitForLayoutComplete(), "Layout after setReservedTopHeight must complete");

            assertEquals(4, stack(BorderPanel.TOP).getHeight());
            assertEquals(4, stack(BorderPanel.CENTER).getY());
            assertEquals(H - 4, stack(BorderPanel.CENTER).getHeight());
        }

        @Test
        void reservedTopHeightIgnoredWhenSlotHasContent() {
            int contentH = 2;
            panel.setReservedTopHeight(10);
            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", contentH));
            assertTrue(harness.waitForLayoutComplete(), "Layout after setReservedTopHeight and addToPanel must complete");

            assertEquals(contentH, stack(BorderPanel.TOP).getHeight());
            assertEquals(contentH, stack(BorderPanel.CENTER).getY());
        }
    }

    @Nested
    class SwapAndClearLayout {
        @Test
        void swappingTopContentUsesNewChildHeight() {
            TerminalLabel first = labelWithMinHeight("first", 2);
            TerminalLabel second = labelWithMinHeight("second", 5);

            panel.addToPanel(BorderPanel.TOP, first);
            assertTrue(harness.waitForLayoutComplete(), "Layout after addToPanel must complete");

            panel.swapPanel(BorderPanel.TOP, second);
            assertTrue(harness.waitForLayoutComplete(), "Layout after swapPanel must complete");

            assertEquals(5, stack(BorderPanel.TOP).getHeight());
            assertEquals(5, stack(BorderPanel.CENTER).getY());
            assertEquals(H - 5, stack(BorderPanel.CENTER).getHeight());
        }

        @Test
        void clearingTopSlotCollapsesCenterBack() {
            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", 4));
            assertTrue(harness.waitForLayoutComplete(), "Layout after addToPanel must complete");

            panel.clearPanel(BorderPanel.TOP);
            assertTrue(harness.waitForLayoutComplete(), "Layout after clearPanel must complete");

            assertEquals(0, stack(BorderPanel.CENTER).getY());
            assertEquals(H, stack(BorderPanel.CENTER).getHeight());
        }
    }

    @Nested
    class InsetsLayout {
        @Test
        void insetsReduceAvailableAreaForAllSlots() {
            int pad = 2;
            panel.setInsets(pad);

            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", 1));
            panel.addToPanel(BorderPanel.CENTER, new TerminalLabel("center"));
            assertTrue(harness.waitForLayoutComplete(), "Layout after setInsets and addToPanel must complete");

            assertEquals(pad, stack(BorderPanel.TOP).getX());
            assertEquals(pad, stack(BorderPanel.TOP).getY());
            assertEquals(W - (2 * pad), stack(BorderPanel.TOP).getWidth());
        }
    }

    @Nested
    class StackVisibilityLayout {
        @Test
        void emptyStackIsHiddenAfterLayout() {
            assertTrue(stack(BorderPanel.CENTER).isHidden());
        }

        @Test
        void stackWithContentIsVisibleAfterLayout() {
            panel.addToPanel(BorderPanel.CENTER, new TerminalLabel("c"));
            assertTrue(harness.waitForLayoutComplete(), "Layout after addToPanel must complete");

            assertFalse(stack(BorderPanel.CENTER).isHidden());
        }
    }

    private static TerminalLabel labelWithMinHeight(String name, int h) {
        TerminalLabel label = new TerminalLabel(name);
        label.setMinHeight(h);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        return label;
    }

    private static TerminalLabel labelWithMinWidth(String name, int w) {
        TerminalLabel label = new TerminalLabel(name);
        label.setMinWidth(w);
        label.setWidthPreference(SizePreference.FIT_CONTENT);
        return label;
    }

    @Nested
    class ResizingTests {
        @Test
        void resizingRootRedistributesCenterHeight() {
            int topH = 3;
            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", topH));
            assertTrue(harness.waitForLayoutComplete());

            int originalCenterH = stack(BorderPanel.CENTER).getHeight();
            assertEquals(H - topH, originalCenterH);

            // Resize root to smaller height
            int newH = H - 2;
            harness.setAllocatedRegion(0,0,W,newH);
            assertTrue(harness.waitForLayoutComplete());

            int newCenterH = stack(BorderPanel.CENTER).getHeight();
            assertEquals(newH - topH, newCenterH);
        }

        @Test
        void resizingRootRedistributesCenterWidth() {
            int leftW = 10;
            panel.addToPanel(BorderPanel.LEFT, labelWithMinWidth("left", leftW));
            assertTrue(harness.waitForLayoutComplete());

            int originalCenterW = stack(BorderPanel.CENTER).getWidth();
            assertEquals(W - leftW, originalCenterW);

            // Resize root to smaller width
            int newW = W - 5;
            harness.setAllocatedRegion(0,0,newW,H);
            assertTrue(harness.waitForLayoutComplete());

            int newCenterW = stack(BorderPanel.CENTER).getWidth();
            assertEquals(newW - leftW, newCenterW);
        }

        @Test
        void resizingWithAllSlotsRedistributesAll() {
            int topH = 2;
            int bottomH = 3;
            int leftW = 8;
            int rightW = 6;

            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", topH));
            panel.addToPanel(BorderPanel.BOTTOM, labelWithMinHeight("bottom", bottomH));
            panel.addToPanel(BorderPanel.LEFT, labelWithMinWidth("left", leftW));
            panel.addToPanel(BorderPanel.RIGHT, labelWithMinWidth("right", rightW));
            assertTrue(harness.waitForLayoutComplete());

            int originalCenterH = stack(BorderPanel.CENTER).getHeight();
            int originalCenterW = stack(BorderPanel.CENTER).getWidth();

            // Resize root to smaller dimensions
            int newH = H - 4;
            int newW = W - 6;
            harness.setAllocatedRegion(0,0, newW, newH);
            assertTrue(harness.waitForLayoutComplete());

            int newCenterH = stack(BorderPanel.CENTER).getHeight();
            int newCenterW = stack(BorderPanel.CENTER).getWidth();

            assertEquals(newH - topH - bottomH, newCenterH);
            assertEquals(newW - leftW - rightW, newCenterW);
        }

        @Test
        void resizingDownReclaimsEmptyReservedSpace() {
            panel.setReservedTopHeight(10);
            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", 3));
            assertTrue(harness.waitForLayoutComplete());

            // Top should have content height, not reserved height
            assertEquals(3, stack(BorderPanel.TOP).getHeight());

            // Resize root down
            int newH = H - 5;
            harness.setAllocatedRegion(0,0,W,newH);
            assertTrue(harness.waitForLayoutComplete());

            // Top should still have content height
            assertEquals(3, stack(BorderPanel.TOP).getHeight());
        }

        @Test
        void resizingUpExpandsAvailableSpace() {
            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", 2));
            assertTrue(harness.waitForLayoutComplete());

            int originalCenterH = stack(BorderPanel.CENTER).getHeight();

            // Resize root up
            int newH = H + 5;
            harness.setAllocatedRegion(0,0,W,newH);
            assertTrue(harness.waitForLayoutComplete());

            int newCenterH = stack(BorderPanel.CENTER).getHeight();
            assertEquals(newH - 2, newCenterH);
        }

        @Test
        void resizingPreservesInsetsPadding() {
            int pad = 2;
            panel.setInsets(pad);

            panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", 2));
            panel.addToPanel(BorderPanel.CENTER, new TerminalLabel("center"));
            assertTrue(harness.waitForLayoutComplete());

            int originalTopX = stack(BorderPanel.TOP).getX();
            int originalTopW = stack(BorderPanel.TOP).getWidth();

            // Resize root
            int newH = H - 4;
            int newW = W - 4;
            harness.setAllocatedRegion(0, 0, newW, newH);
            assertTrue(harness.waitForLayoutComplete());

            // Insets should still be applied
            assertEquals(pad, stack(BorderPanel.TOP).getX());
            assertEquals(newW - (2 * pad), stack(BorderPanel.TOP).getWidth());
        }
    }
}
