package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.LayoutOverflowStrategy;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.components.panels.TerminalPanel;
import io.netnotes.terminal.components.panels.TerminalStackPanel;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.netnotes.terminal.layout.TerminalLayoutTestHarness.STATE_LAYOUT_IDLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverflowClipPolicyRenderTest {

    private static final int W = 20;
    private static final int H = 6;

    @Test
    void clipStrategyClipsChildToContainerBounds() {
        ClipProbe probe = new ClipProbe("probe");
        TerminalPanel root = new TerminalPanel("root");
        TerminalStackPanel stack = createStack("clip-stack", LayoutOverflowStrategy.CLIP, probe);

        TerminalLayoutTestHarness harness = new TerminalLayoutTestHarness(W, H);
        harness.attach(root);

        step(harness,
            () -> {
                root.addChild(stack);
                probe.invalidate();
                harness.triggerRender();
            },
            () -> {
                assertTrue(probe.renderCount > 0, "probe should render at least once");
                assertEquals(4, probe.lastClipWidth, "CLIP should constrain child rendering to stack width");
            });
    }

    @Test
    void overflowStrategyLetsChildUseParentClip() {
        ClipProbe probe = new ClipProbe("probe");
        TerminalPanel root = new TerminalPanel("root");
        TerminalStackPanel stack = createStack("overflow-stack", LayoutOverflowStrategy.OVERFLOW, probe);

        TerminalLayoutTestHarness harness = new TerminalLayoutTestHarness(W, H);
        harness.attach(root);

        step(harness,
            () -> {
                root.addChild(stack);
                probe.invalidate();
                harness.triggerRender();
            },
            () -> {
                assertTrue(probe.renderCount > 0, "probe should render at least once");
                assertEquals(8, probe.lastClipWidth, "OVERFLOW should pass through the parent clip to children");
            });
    }

    private static TerminalStackPanel createStack(
        String name,
        LayoutOverflowStrategy overflowStrategy,
        ClipProbe probe
    ) {
        TerminalStackPanel stack = new TerminalStackPanel(name);
        stack.setWidthPreference(SizePreference.STATIC);
        stack.setHeightPreference(SizePreference.FILL);
        stack.setWidth(4);
        stack.setOverflowStrategy(overflowStrategy);

        probe.setWidthPreference(SizePreference.STATIC);
        probe.setHeightPreference(SizePreference.STATIC);
        probe.setWidth(8);
        probe.setHeight(1);
        stack.addToStack(probe);
        return stack;
    }

    private static void step(TerminalLayoutTestHarness harness, Runnable action, Runnable assertions) {
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
        action.run();
        gate.awaitDone();
    }

    private static final class ClipProbe extends TerminalRegion {
        private volatile int lastClipWidth = -1;
        private volatile int renderCount = 0;

        private ClipProbe(String name) {
            super(name);
        }

        @Override
        protected void renderSelf(TerminalBatchBuilder batch) {
            TerminalRectangle clip = batch.getCurrentClipRegion();
            renderCount++;
            lastClipWidth = clip != null ? clip.getWidth() : -1;
        }
    }

    private static final class TestGate {
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
                    throw new AssertionError("Test timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted", e);
            }
            if (failure instanceof AssertionError a) throw a;
            if (failure != null) throw new AssertionError("Step failed", failure);
        }
    }
}
