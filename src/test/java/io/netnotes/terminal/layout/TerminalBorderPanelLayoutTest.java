package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.BorderPanel;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.components.panels.TerminalBorderPanel;
import io.netnotes.terminal.components.panels.TerminalStackPanel;
import io.netnotes.terminal.components.text.TerminalLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.netnotes.terminal.layout.TerminalLayoutTestHarness.STATE_LAYOUT_IDLE;
import static org.junit.jupiter.api.Assertions.*;

public class TerminalBorderPanelLayoutTest {

    private static final int W = 80;
    private static final int H = 24;

    private TerminalLayoutTestHarness harness;
    private TerminalBorderPanel panel;

    @BeforeEach
    void setup() {
        panel = new TerminalBorderPanel("bp");
        harness = new TerminalLayoutTestHarness(W, H);
        harness.attach(panel); // blocks until first idle
    }

    private TerminalStackPanel stack(BorderPanel region) {
        return panel.getRegionStack(region);
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

    // ── Helper to run a single-step layout action and assert geometry.
    //     The action sets up the panel, then we trigger layout and assert
    //     in the IDLE callback.
    private void assertAfterLayout(String description,
                                   Runnable setupAction,
                                   Runnable assertions) {
        TestGate gate = new TestGate();
        int[] step = {0};
        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                if (step[0]++ == 0) {
                    assertions.run();
                    gate.open();
                }
            } catch (Throwable t) {
                gate.fail(t);
            }
        });

        setupAction.run();
        harness.triggerRender();
        gate.awaitDone();
        // cleanup: handler will remain but won't fire again because we don't trigger
    }

    @Nested
    class EmptyPanelLayout {
        @Test
        void panelFillsRoot() {
            assertAfterLayout("empty panel",
                    () -> {}, // nothing added
                    () -> {
                        assertEquals(0, panel.getX());
                        assertEquals(0, panel.getY());
                        assertEquals(W, panel.getWidth());
                        assertEquals(H, panel.getHeight());
                    });
        }

        @Test
        void centerStackFillsEntireAreaWhenNoSlotsOccupied() {
            assertAfterLayout("center fills root",
                    () -> {},
                    () -> {
                        TerminalStackPanel center = stack(BorderPanel.CENTER);
                        assertEquals(0, center.getX());
                        assertEquals(0, center.getY());
                        assertEquals(W, center.getWidth());
                        assertEquals(H, center.getHeight());
                    });
        }
    }

    @Nested
    class TopSlotLayout {
        @Test
        void topSlotReducesCenterHeight() {
            int labelHeight = 3;
            TerminalLabel topLabel = labelWithMinHeight("header", labelHeight);
            assertAfterLayout("add top label",
                    () -> panel.addToPanel(BorderPanel.TOP, topLabel),
                    () -> {
                        TerminalStackPanel top = stack(BorderPanel.TOP);
                        TerminalStackPanel center = stack(BorderPanel.CENTER);
                        assertEquals(0, top.getY());
                        assertEquals(labelHeight, top.getHeight());
                        assertEquals(labelHeight, center.getY());
                        assertEquals(H - labelHeight, center.getHeight());
                    });
        }

        @Test
        void topStackSpansFullWidth() {
            assertAfterLayout("top label full width",
                    () -> panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("h", 1)),
                    () -> {
                        assertEquals(0, stack(BorderPanel.TOP).getX());
                        assertEquals(W, stack(BorderPanel.TOP).getWidth());
                    });
        }
    }

    @Nested
    class BottomSlotLayout {
        @Test
        void bottomSlotAnchorsToBottomEdge() {
            int labelHeight = 2;
            assertAfterLayout("add bottom",
                    () -> panel.addToPanel(BorderPanel.BOTTOM, labelWithMinHeight("footer", labelHeight)),
                    () -> {
                        TerminalStackPanel bottom = stack(BorderPanel.BOTTOM);
                        assertEquals(H - labelHeight, bottom.getY());
                        assertEquals(labelHeight, bottom.getHeight());
                        assertEquals(W, bottom.getWidth());
                    });
        }

        @Test
        void topAndBottomLeaveCorrectMiddleHeight() {
            int topH = 2, bottomH = 3;
            assertAfterLayout("top+bottom",
                    () -> {
                        panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", topH));
                        panel.addToPanel(BorderPanel.BOTTOM, labelWithMinHeight("bottom", bottomH));
                    },
                    () -> {
                        int expectedCenterH = H - topH - bottomH;
                        assertEquals(expectedCenterH, stack(BorderPanel.CENTER).getHeight());
                        assertEquals(topH, stack(BorderPanel.CENTER).getY());
                    });
        }
    }

    @Nested
    class SideSlotLayout {
        @Test
        void leftSlotStartsAtLeftEdge() {
            int labelW = 10;
            assertAfterLayout("left",
                    () -> panel.addToPanel(BorderPanel.LEFT, labelWithMinWidth("nav", labelW)),
                    () -> {
                        TerminalStackPanel left = stack(BorderPanel.LEFT);
                        assertEquals(0, left.getX());
                        assertEquals(labelW, left.getWidth());
                    });
        }

        @Test
        void rightSlotAnchorsToRightEdge() {
            int labelW = 8;
            assertAfterLayout("right",
                    () -> panel.addToPanel(BorderPanel.RIGHT, labelWithMinWidth("sidebar", labelW)),
                    () -> {
                        TerminalStackPanel right = stack(BorderPanel.RIGHT);
                        assertEquals(W - labelW, right.getX());
                        assertEquals(labelW, right.getWidth());
                    });
        }

        @Test
        void leftAndRightReduceCenterWidth() {
            int leftW = 10, rightW = 5;
            assertAfterLayout("left+right",
                    () -> {
                        panel.addToPanel(BorderPanel.LEFT, labelWithMinWidth("nav", leftW));
                        panel.addToPanel(BorderPanel.RIGHT, labelWithMinWidth("sidebar", rightW));
                    },
                    () -> {
                        int expectedCenterW = W - leftW - rightW;
                        TerminalStackPanel center = stack(BorderPanel.CENTER);
                        assertEquals(leftW, center.getX());
                        assertEquals(expectedCenterW, center.getWidth());
                    });
        }

        @Test
        void sideStacksOccupyMiddleRowOnly() {
            int topH = 2, leftW = 10;
            assertAfterLayout("top+left",
                    () -> {
                        panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", topH));
                        panel.addToPanel(BorderPanel.LEFT, labelWithMinWidth("nav", leftW));
                    },
                    () -> {
                        assertEquals(topH, stack(BorderPanel.LEFT).getY());
                        assertEquals(H - topH, stack(BorderPanel.LEFT).getHeight());
                    });
        }
    }

    @Nested
    class ReservedSizeLayout {
        @Test
        void reservedTopHeightAppliesWhenSlotIsEmpty() {
            assertAfterLayout("reserved top empty",
                    () -> panel.setReservedTopHeight(4),
                    () -> {
                        assertEquals(4, stack(BorderPanel.TOP).getHeight());
                        assertEquals(4, stack(BorderPanel.CENTER).getY());
                        assertEquals(H - 4, stack(BorderPanel.CENTER).getHeight());
                    });
        }

        @Test
        void reservedTopHeightIgnoredWhenSlotHasContent() {
            int contentH = 2;
            assertAfterLayout("reserved with content",
                    () -> {
                        panel.setReservedTopHeight(10);
                        panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", contentH));
                    },
                    () -> {
                        assertEquals(contentH, stack(BorderPanel.TOP).getHeight());
                        assertEquals(contentH, stack(BorderPanel.CENTER).getY());
                    });
        }
    }

    @Nested
    class SwapAndClearLayout {
        @Test
        void swappingTopContentUsesNewChildHeight() {
            TerminalLabel first = labelWithMinHeight("first", 2);
            TerminalLabel second = labelWithMinHeight("second", 5);
            assertAfterLayout("add first",
                    () -> panel.addToPanel(BorderPanel.TOP, first),
                    () -> {
                        // first is inserted, now swap
                        panel.swapPanel(BorderPanel.TOP, second);
                        // we need another idle to see the result, so we chain:
                    });

            // Chain another idle step
            TestGate gate = new TestGate();
            harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
                try {
                    assertEquals(5, stack(BorderPanel.TOP).getHeight());
                    assertEquals(5, stack(BorderPanel.CENTER).getY());
                    assertEquals(H - 5, stack(BorderPanel.CENTER).getHeight());
                    gate.open();
                } catch (Throwable t) { gate.fail(t); }
            });
            harness.triggerRender(); // the swap triggers layout automatically, but we ensure
            gate.awaitDone();
        }

        @Test
        void clearingTopSlotCollapsesCenterBack() {
            assertAfterLayout("add top",
                    () -> panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", 4)),
                    () -> panel.clearPanel(BorderPanel.TOP));

            TestGate gate = new TestGate();
            harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
                try {
                    assertEquals(0, stack(BorderPanel.CENTER).getY());
                    assertEquals(H, stack(BorderPanel.CENTER).getHeight());
                    gate.open();
                } catch (Throwable t) { gate.fail(t); }
            });
            harness.triggerRender();
            gate.awaitDone();
        }
    }

    @Nested
    class InsetsLayout {
        @Test
        void insetsReduceAvailableAreaForAllSlots() {
            int pad = 2;
            assertAfterLayout("insets",
                    () -> {
                        panel.setInsets(pad);
                        panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", 1));
                        panel.addToPanel(BorderPanel.CENTER, new TerminalLabel("center"));
                    },
                    () -> {
                        assertEquals(pad, stack(BorderPanel.TOP).getX());
                        assertEquals(pad, stack(BorderPanel.TOP).getY());
                        assertEquals(W - 2*pad, stack(BorderPanel.TOP).getWidth());
                    });
        }
    }

    @Nested
    class StackVisibilityLayout {
        @Test
        void emptyStackIsHiddenAfterLayout() {
            // after attach, center stack is hidden (no content)
            assertTrue(stack(BorderPanel.CENTER).isHidden());
        }

        @Test
        void stackWithContentIsVisibleAfterLayout() {
            assertAfterLayout("add center",
                    () -> panel.addToPanel(BorderPanel.CENTER, new TerminalLabel("c")),
                    () -> assertFalse(stack(BorderPanel.CENTER).isHidden()));
        }
    }

    @Nested
    class ResizingTests {
        @Test
        void resizingRootRedistributesCenterHeight() {
            int topH = 3;
            assertAfterLayout("add top",
                    () -> panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", topH)),
                    () -> {
                        harness.setAllocatedRegion(0, 0, W, H - 2); // shrink
                    });

            TestGate gate = new TestGate();
            harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
                try {
                    int newCenterH = stack(BorderPanel.CENTER).getHeight();
                    assertEquals((H - 2) - topH, newCenterH);
                    gate.open();
                } catch (Throwable t) { gate.fail(t); }
            });
            gate.awaitDone();
        }

        @Test
        void resizingRootRedistributesCenterWidth() {
            int leftW = 10;
            assertAfterLayout("add left",
                    () -> panel.addToPanel(BorderPanel.LEFT, labelWithMinWidth("left", leftW)),
                    () -> harness.setAllocatedRegion(0, 0, W - 5, H));

            TestGate gate = new TestGate();
            harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
                try {
                    assertEquals((W - 5) - leftW, stack(BorderPanel.CENTER).getWidth());
                    gate.open();
                } catch (Throwable t) { gate.fail(t); }
            });
            gate.awaitDone();
        }

        @Test
        void resizingWithAllSlotsRedistributesAll() {
            int topH = 2, bottomH = 3, leftW = 8, rightW = 6;
            assertAfterLayout("all slots",
                    () -> {
                        panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", topH));
                        panel.addToPanel(BorderPanel.BOTTOM, labelWithMinHeight("bottom", bottomH));
                        panel.addToPanel(BorderPanel.LEFT, labelWithMinWidth("left", leftW));
                        panel.addToPanel(BorderPanel.RIGHT, labelWithMinWidth("right", rightW));
                    },
                    () -> harness.setAllocatedRegion(0, 0, W - 6, H - 4));

            TestGate gate = new TestGate();
            harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
                try {
                    assertEquals((H - 4) - topH - bottomH, stack(BorderPanel.CENTER).getHeight());
                    assertEquals((W - 6) - leftW - rightW, stack(BorderPanel.CENTER).getWidth());
                    gate.open();
                } catch (Throwable t) { gate.fail(t); }
            });
            gate.awaitDone();
        }

        @Test
        void resizingDownReclaimsEmptyReservedSpace() {
            assertAfterLayout("reserved+content",
                    () -> {
                        panel.setReservedTopHeight(10);
                        panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", 3));
                    },
                    () -> harness.setAllocatedRegion(0, 0, W, H - 5));

            TestGate gate = new TestGate();
            harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
                try {
                    assertEquals(3, stack(BorderPanel.TOP).getHeight());
                    gate.open();
                } catch (Throwable t) { gate.fail(t); }
            });
            gate.awaitDone();
        }

        @Test
        void resizingUpExpandsAvailableSpace() {
            assertAfterLayout("top",
                    () -> panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", 2)),
                    () -> harness.setAllocatedRegion(0, 0, W, H + 5));

            TestGate gate = new TestGate();
            harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
                try {
                    assertEquals((H + 5) - 2, stack(BorderPanel.CENTER).getHeight());
                    gate.open();
                } catch (Throwable t) { gate.fail(t); }
            });
            gate.awaitDone();
        }

        @Test
        void resizingPreservesInsetsPadding() {
            int pad = 2;
            assertAfterLayout("insets+top",
                    () -> {
                        panel.setInsets(pad);
                        panel.addToPanel(BorderPanel.TOP, labelWithMinHeight("top", 2));
                        panel.addToPanel(BorderPanel.CENTER, new TerminalLabel("center"));
                    },
                    () -> harness.setAllocatedRegion(0, 0, W - 4, H - 4));

            TestGate gate = new TestGate();
            harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
                try {
                    assertEquals(pad, stack(BorderPanel.TOP).getX());
                    assertEquals((W - 4) - 2*pad, stack(BorderPanel.TOP).getWidth());
                    gate.open();
                } catch (Throwable t) { gate.fail(t); }
            });
            gate.awaitDone();
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
}