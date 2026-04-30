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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.netnotes.terminal.layout.TerminalLayoutTestHarness.STATE_LAYOUT_IDLE;
import static org.junit.jupiter.api.Assertions.*;

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
    }

    // ── TestGate ─────────────────────────────────────────────────────────
    static final class TestGate {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile Throwable failure;
        void open() { latch.countDown(); }
        void fail(Throwable t) { failure = t; latch.countDown(); }
        void awaitDone() {
            try {
                if (!latch.await(5, TimeUnit.SECONDS))
                    throw new AssertionError("Test timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted", e);
            }
            if (failure instanceof AssertionError a) throw a;
            if (failure != null) throw new AssertionError("Step failed", failure);
        }
    }

    // Helper to perform a single action + assertion step
    private void step(Runnable action, Runnable assertions) {
        TestGate gate = new TestGate();
        int[] step = {0};
        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        action.run();
                        harness.triggerRender();
                    }
                    case 1 -> {
                        assertions.run();
                        gate.open();
                    }
                }
            } catch (Throwable t) { gate.fail(t); }
        });
        harness.triggerRender();
        gate.awaitDone();
    }

    @Nested
    class ScrollInCenterRegion {
        @Test
        void scrollPanelInCenterFillsAvailableSpace() {
            TerminalScrollPanel scroll = new TerminalScrollPanel("center-scroll");
            scroll.setWidthPreference(SizePreference.FILL);
            scroll.setHeightPreference(SizePreference.FILL);

            step(() -> borderPanel.addToPanel(BorderPanel.CENTER, scroll),
                 () -> {
                     TerminalStackPanel center = borderPanel.getRegionStack(BorderPanel.CENTER);
                     assertEquals(0, center.getX());
                     assertEquals(0, center.getY());
                     assertEquals(W, center.getWidth());
                     assertEquals(H, center.getHeight());
                 });
        }

        @Test
        void scrollPanelShowsContentWhenAdded() {
            TerminalScrollPanel scroll = new TerminalScrollPanel("content");
            scroll.setWidthPreference(SizePreference.FILL);
            scroll.setHeightPreference(SizePreference.FILL);
            ScrollableTextViewer text = new ScrollableTextViewer("long-text");
            text.addLines("Line 1", "Line 2", "Line 3", "Line 4", "Line 5");

            step(() -> {
                     borderPanel.addToPanel(BorderPanel.CENTER, scroll);
                     scroll.addContent(text);
                 },
                 () -> {
                     TerminalStackPanel center = borderPanel.getRegionStack(BorderPanel.CENTER);
                     assertFalse(center.isHidden());
                 });
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
            ScrollableTextViewer text = fitContentViewer("long-left-text", "0123456789", "abcdefghij");

            step(() -> {
                     borderPanel.addToPanel(BorderPanel.LEFT, scroll);
                     scroll.addContent(text);
                 },
                 () -> {
                     TerminalStackPanel left = borderPanel.getRegionStack(BorderPanel.LEFT);
                     assertEquals(0, left.getX());
                     assertEquals(0, left.getY());
                     assertEquals(10, left.getWidth()); // content width
                     assertEquals(H, left.getHeight());
                     TerminalStackPanel center = borderPanel.getRegionStack(BorderPanel.CENTER);
                     assertEquals(10, center.getX());
                     assertEquals(W - 10, center.getWidth());
                     assertEquals(H, center.getHeight());
                 });
        }

        @Test
        void scrollPanelInTopRegionUsesAvailableHeight() {
            TerminalScrollPanel scroll = new TerminalScrollPanel("top-scroll");
            scroll.setWidthPreference(SizePreference.FILL);
            scroll.setHeightPreference(SizePreference.FIT_CONTENT);
            scroll.setScrollMode(TerminalScrollPanel.ScrollMode.FIXED_SIZE);
            scroll.setVerticalScrollEnabled(false);
            ScrollableTextViewer text = fitContentViewer("top", "Line 1", "Line 2", "Line 3");

            step(() -> {
                     borderPanel.addToPanel(BorderPanel.TOP, scroll);
                     scroll.addContent(text);
                 },
                 () -> {
                     TerminalStackPanel top = borderPanel.getRegionStack(BorderPanel.TOP);
                     assertEquals(0, top.getX());
                     assertEquals(0, top.getY());
                     assertEquals(W, top.getWidth());
                     assertEquals(3, top.getHeight());
                     TerminalStackPanel center = borderPanel.getRegionStack(BorderPanel.CENTER);
                     assertEquals(3, center.getY());
                     assertEquals(H - 3, center.getHeight());
                 });
        }
    }

    @Nested
    class MultipleScrollPanels {
        @Test
        void multipleScrollPanelsInDifferentRegions() {
            TerminalScrollPanel topScroll = new TerminalScrollPanel("top-scroll");
            topScroll.setWidthPreference(SizePreference.FILL);
            topScroll.setHeightPreference(SizePreference.FIT_CONTENT);
            topScroll.setScrollMode(TerminalScrollPanel.ScrollMode.FIXED_SIZE);
            topScroll.setVerticalScrollEnabled(false);
            topScroll.addContent(fitContentViewer("top-text", "H1", "H2"));

            TerminalScrollPanel centerScroll = new TerminalScrollPanel("center-scroll");
            centerScroll.setWidthPreference(SizePreference.FILL);
            centerScroll.setHeightPreference(SizePreference.FILL);
            centerScroll.addContent(fitContentViewer("center", "Main"));

            TerminalScrollPanel bottomScroll = new TerminalScrollPanel("bottom-scroll");
            bottomScroll.setWidthPreference(SizePreference.FILL);
            bottomScroll.setHeightPreference(SizePreference.FIT_CONTENT);
            bottomScroll.setScrollMode(TerminalScrollPanel.ScrollMode.FIXED_SIZE);
            bottomScroll.setVerticalScrollEnabled(false);
            bottomScroll.addContent(fitContentViewer("bottom", "F1", "F2"));

            step(() -> {
                     borderPanel.addToPanel(BorderPanel.TOP, topScroll);
                     borderPanel.addToPanel(BorderPanel.CENTER, centerScroll);
                     borderPanel.addToPanel(BorderPanel.BOTTOM, bottomScroll);
                 },
                 () -> {
                     assertNotNull(borderPanel.getRegionStack(BorderPanel.TOP).getContent());
                     assertNotNull(borderPanel.getRegionStack(BorderPanel.CENTER).getContent());
                     assertNotNull(borderPanel.getRegionStack(BorderPanel.BOTTOM).getContent());
                 });
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

            step(() -> borderPanel.addToPanel(BorderPanel.CENTER, scroll),
                 () -> {
                     TerminalStackPanel center = borderPanel.getRegionStack(BorderPanel.CENTER);
                     assertEquals(pad, center.getX());
                     assertEquals(pad, center.getY());
                     assertEquals(W - 2*pad, center.getWidth());
                     assertEquals(H - 2*pad, center.getHeight());
                 });
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

            step(() -> borderPanel.addToPanel(BorderPanel.TOP, scroll),
                 () -> assertEquals(1, borderPanel.getRegionStack(BorderPanel.TOP).getHeight(),
                                    "Content height wins over reserved when slot has content"));
        }
    }
}
