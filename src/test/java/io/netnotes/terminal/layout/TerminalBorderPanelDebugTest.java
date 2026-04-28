package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.BorderPanel;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.components.panels.TerminalBorderPanel;
import io.netnotes.terminal.components.panels.TerminalStackPanel;
import io.netnotes.terminal.components.text.TerminalLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.netnotes.terminal.layout.TerminalLayoutTestHarness.STATE_LAYOUT_IDLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TerminalBorderPanelDebugTest {

    private static final int W = 80;
    private static final int H = 24;

    private TerminalLayoutTestHarness harness;
    private TerminalBorderPanel panel;

    @BeforeEach
    void setup() {
        panel = new TerminalBorderPanel("bp");
        harness = new TerminalLayoutTestHarness(W, H);
        harness.attach(panel); // blocks until first layout idle
    }

    @Test
    void debug_simple_label_in_top() {
        TerminalLabel label = new TerminalLabel("header");
        label.setMinHeight(3);
        label.setHeightPreference(SizePreference.FIT_CONTENT);

        panel.addToPanel(BorderPanel.TOP, label);

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                if (step[0]++ == 0) {
                    TerminalStackPanel topStack = panel.getRegionStack(BorderPanel.TOP);
                    assertEquals(3, topStack.getHeight(), "TOP should have height 3");
                    gate.open();
                }
            } catch (Throwable t) {
                gate.fail(t);
            }
        });

        harness.triggerRender();
        gate.awaitDone();
    }

    // ── TestGate (same as in previous refactors) ─────────────────────────
    static final class TestGate {
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
                    throw new AssertionError("Test timed out: STATE_LAYOUT_IDLE not reached");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Test interrupted", e);
            }
            if (failure instanceof AssertionError a) throw a;
            if (failure != null) throw new AssertionError("Test step failed", failure);
        }
    }
}