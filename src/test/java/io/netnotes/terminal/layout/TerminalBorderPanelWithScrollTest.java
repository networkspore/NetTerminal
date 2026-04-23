package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.BorderPanel;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.components.panels.TerminalBorderPanel;
import io.netnotes.terminal.components.panels.TerminalScrollPanel;
import io.netnotes.terminal.components.panels.TerminalStackPanel;
import io.netnotes.terminal.components.text.ScrollableTextViewer;

import org.junit.jupiter.api.Nested;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TerminalBorderPanel with TerminalScrollPanel integration.
 * Demonstrates common patterns like scrolling content in different regions.
 */
public class TerminalBorderPanelWithScrollTest {

    private static final int W = 80;
    private static final int H = 24;

    private TerminalLayoutTestHarness harness;
    private TerminalBorderPanel borderPanel;

    @BeforeEach
    void setup() {
        borderPanel = new TerminalBorderPanel("border");
        harness = new TerminalLayoutTestHarness(W, H);
        harness.attach(borderPanel);
        assertTrue(harness.waitForLayoutComplete(), "Initial layout must complete");
    }

    @Nested
    class ScrollInCenterRegion {
        @Test
        void scrollPanelInCenterFillsAvailableSpace() {
            TerminalScrollPanel scroll = new TerminalScrollPanel("center-scroll");
            scroll.setWidthPreference(SizePreference.FILL);
            scroll.setHeightPreference(SizePreference.FILL);

            borderPanel.addToPanel(BorderPanel.CENTER, scroll);

            assertTrue(harness.waitForLayoutComplete(), "Layout after adding scroll must complete");

            TerminalStackPanel center = borderPanel.getRegionStack(BorderPanel.CENTER);
            assertEquals(0, center.getX());
            assertEquals(0, center.getY());
            assertEquals(W, center.getWidth());
            assertEquals(H, center.getHeight());
        }

        @Test
        void scrollPanelShowsContentWhenAdded() {
            TerminalScrollPanel scroll = new TerminalScrollPanel("content");
            scroll.setWidthPreference(SizePreference.FILL);
            scroll.setHeightPreference(SizePreference.FILL);

            // Add a scrollable text viewer
            ScrollableTextViewer text = new ScrollableTextViewer("long-text");
            text.addLines("Line 1", "Line 2", "Line 3", "Line 4", "Line 5", "Line 6", "Line 7", "Line 8", "Line 9", "Line 10");

            borderPanel.addToPanel(BorderPanel.CENTER, scroll);
            scroll.addContent(text);

            assertTrue(harness.waitForLayoutComplete(), "Layout after adding scroll and text must complete");

            TerminalStackPanel center = borderPanel.getRegionStack(BorderPanel.CENTER);
            assertFalse(center.isHidden(), "Center stack should be visible when content is added");
        }
    }

    private static ScrollableTextViewer fitContentViewer(String name, String... lines) {
        ScrollableTextViewer viewer = new ScrollableTextViewer(name, false, null);
        viewer.setWidthPreference(SizePreference.FIT_CONTENT);
        viewer.setHeightPreference(SizePreference.FIT_CONTENT);
        viewer.addLines(lines);
        return viewer;
    }

    @Nested
    class ScrollInSideRegions {
        @Test
        void scrollPanelInLeftRegionUsesAvailableWidth() {
            TerminalScrollPanel scroll = new TerminalScrollPanel("left-scroll");
            scroll.setWidthPreference(SizePreference.FIT_CONTENT);
            scroll.setHeightPreference(SizePreference.FILL);
            scroll.setScrollMode(TerminalScrollPanel.ScrollMode.FIXED_SIZE);
            scroll.setVerticalScrollEnabled(false);

            ScrollableTextViewer text = fitContentViewer(
                "long-left-text",
                "0123456789",
                "abcdefghij",
                "klmnopqrst"
            );

            borderPanel.addToPanel(BorderPanel.LEFT, scroll);
            scroll.addContent(text);
            assertTrue(harness.waitForLayoutComplete(), "Layout after adding left scroll must complete");

            TerminalStackPanel left = borderPanel.getRegionStack(BorderPanel.LEFT);
            assertEquals(0, left.getX());
            assertEquals(0, left.getY());
            assertEquals(10, left.getWidth(), "LEFT FIT_CONTENT width should match content width");
            assertEquals(H, left.getHeight(), "LEFT FILL height should consume full available height");

            TerminalStackPanel center = borderPanel.getRegionStack(BorderPanel.CENTER);
            assertEquals(10, center.getX());
            assertEquals(W - 10, center.getWidth());
            assertEquals(H, center.getHeight());
        }

        @Test
        void scrollPanelInTopRegionUsesAvailableHeight() {
            TerminalScrollPanel scroll = new TerminalScrollPanel("top-scroll");
            scroll.setWidthPreference(SizePreference.FILL);
            scroll.setHeightPreference(SizePreference.FIT_CONTENT);
            scroll.setScrollMode(TerminalScrollPanel.ScrollMode.FIXED_SIZE);
            scroll.setVerticalScrollEnabled(false);

            ScrollableTextViewer text = fitContentViewer(
                "long-top-text",
                "Line 1",
                "Line 2",
                "Line 3",
                "Line 4",
                "Line 5",
                "Line 6"
            );

            borderPanel.addToPanel(BorderPanel.TOP, scroll);
            scroll.addContent(text);

            assertTrue(harness.waitForLayoutComplete(), "Layout after adding top scroll must complete");

            TerminalStackPanel top = borderPanel.getRegionStack(BorderPanel.TOP);
            assertEquals(0, top.getX());
            assertEquals(0, top.getY());
            assertEquals(W, top.getWidth());
            assertEquals(6, top.getHeight()); // Height from content

            TerminalStackPanel center = borderPanel.getRegionStack(BorderPanel.CENTER);
            assertEquals(6, center.getY());
            assertEquals(H - 6, center.getHeight());
        }
    }

    @Nested
    class MultipleScrollPanels {
        @Test
        void multipleScrollPanelsInDifferentRegions() {
            // Top scroll panel
            TerminalScrollPanel topScroll = new TerminalScrollPanel("top-scroll");
            topScroll.setWidthPreference(SizePreference.FILL);
            topScroll.setHeightPreference(SizePreference.FIT_CONTENT);
            ScrollableTextViewer topText = new ScrollableTextViewer("top-text");
            topText.addLines("Header 1", "Header 2", "Header 3");
            topScroll.addContent(topText);

            // Center scroll panel
            TerminalScrollPanel centerScroll = new TerminalScrollPanel("center-scroll");
            centerScroll.setWidthPreference(SizePreference.FILL);
            centerScroll.setHeightPreference(SizePreference.FILL);
            ScrollableTextViewer centerText = new ScrollableTextViewer("center-text");
            centerText.addLines("Main content", "More content", "Even more");
            centerScroll.addContent(centerText);

            // Bottom scroll panel
            TerminalScrollPanel bottomScroll = new TerminalScrollPanel("bottom-scroll");
            bottomScroll.setWidthPreference(SizePreference.FILL);
            bottomScroll.setHeightPreference(SizePreference.FIT_CONTENT);
            ScrollableTextViewer bottomText = new ScrollableTextViewer("bottom-text");
            bottomText.addLines("Footer 1", "Footer 2", "Footer 3");
            bottomScroll.addContent(bottomText);

            borderPanel.addToPanel(BorderPanel.TOP, topScroll);
            borderPanel.addToPanel(BorderPanel.CENTER, centerScroll);
            borderPanel.addToPanel(BorderPanel.BOTTOM, bottomScroll);

            assertTrue(harness.waitForLayoutComplete(), "Layout after adding multiple scrolls must complete");

            // Verify all regions have content
            assertTrue(borderPanel.getRegionStack(BorderPanel.TOP).getContent() != null);
            assertTrue(borderPanel.getRegionStack(BorderPanel.CENTER).getContent() != null);
            assertTrue(borderPanel.getRegionStack(BorderPanel.BOTTOM).getContent() != null);
        }
    }

    @Nested
    class ScrollWithInsets {
        @Test
        void scrollPanelRespectsInsets() {
            int pad = 2;
            borderPanel.setInsets(pad);

            TerminalScrollPanel scroll = new TerminalScrollPanel("center-scroll");
            scroll.setWidthPreference(SizePreference.FILL);
            scroll.setHeightPreference(SizePreference.FILL);

            borderPanel.addToPanel(BorderPanel.CENTER, scroll);

            assertTrue(harness.waitForLayoutComplete(), "Layout with insets must complete");

            TerminalStackPanel center = borderPanel.getRegionStack(BorderPanel.CENTER);
            assertEquals(pad, center.getX());
            assertEquals(pad, center.getY());
            assertEquals(W - (2 * pad), center.getWidth());
            assertEquals(H - (2 * pad), center.getHeight());
        }
    }

    @Nested
    class ReservedSizesWithScroll {
        @Test
        void reservedTopHeightWithScrollPanel() {
            int reservedH = 5;
            borderPanel.setReservedTopHeight(reservedH);

            TerminalScrollPanel scroll = new TerminalScrollPanel("top-scroll");
            scroll.setWidthPreference(SizePreference.FILL);
            scroll.setHeightPreference(SizePreference.FIT_CONTENT);
            scroll.setScrollMode(TerminalScrollPanel.ScrollMode.FIXED_SIZE);
            scroll.setVerticalScrollEnabled(false);

            ScrollableTextViewer text = fitContentViewer("top-text", "Short");
            scroll.addContent(text);

            borderPanel.addToPanel(BorderPanel.TOP, scroll);

            assertTrue(harness.waitForLayoutComplete(), "Layout with reserved height and scroll must complete");

            TerminalStackPanel top = borderPanel.getRegionStack(BorderPanel.TOP);
            assertEquals(1, top.getHeight(), "Content height should win when TOP slot has content");
        }
    }

   

}