package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.components.panels.TerminalPanel;
import io.netnotes.terminal.components.panels.TerminalVStack;
import io.netnotes.terminal.components.text.TerminalLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.netnotes.terminal.layout.TerminalLayoutTestHarness.STATE_LAYOUT_IDLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Debug tests for visibility and content measurement behavior.
 *
 * Canonical behavior:
 * - FIT_CONTENT VStack never auto-hides children on overflow.
 * - Force-hide via hide() is respected.
 * - Unhide requires multi-pass stabilization.
 */
public class VisibilityMeasurementDebugTest {

    private TerminalLayoutTestHarness harness;
    private TerminalPanel rootPanel;

    @BeforeEach
    void setup() {
        rootPanel = new TerminalPanel("root");
        harness = new TerminalLayoutTestHarness(80, 24);
        harness.attach(rootPanel); // blocks until first idle
    }

    @Test
    void simpleVisibleVStackGetsLaidOut() {
        TerminalVStack visibleStack = new TerminalVStack("visibleStack");
        visibleStack.setHeightPreference(SizePreference.FIT_CONTENT);

        TerminalLabel label = new TerminalLabel("label1");
        label.setMinHeight(3);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        visibleStack.addChild(label);

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                if (step[0]++ == 0) {
                    assertEquals(3, visibleStack.getHeight(), "Visible stack should have height 3");
                    gate.open();
                }
            } catch (Throwable t) {
                gate.fail(t);
            }
        });

        rootPanel.addChild(visibleStack);
        harness.triggerRender();
        gate.awaitDone();
    }

    @Test
    void simpleHiddenVStackVisibilityToggle() {
        TerminalVStack stack = new TerminalVStack("stack");
        stack.setHeightPreference(SizePreference.FIT_CONTENT);

        TerminalLabel label = new TerminalLabel("label");
        label.setMinHeight(3);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        stack.addChild(label);

        stack.hide(); // start hidden

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        assertEquals(0, stack.getHeight(), "Hidden stack height 0");
                        stack.show(); // trigger multi-pass
                    }
                    case 1 -> {
                        assertEquals(3, stack.getHeight(), "Stack height 3 after becoming visible");
                        gate.open();
                    }
                }
            } catch (Throwable t) {
                gate.fail(t);
            }
        });

        rootPanel.addChild(stack);
        harness.triggerRender();
        gate.awaitDone();
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

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        // Initial layout: child hidden.
                        assertEquals(0, parent.getHeight(), "Parent height 0 (child hidden)");
                        child.show(); // multi-pass
                    }
                    case 1 -> {
                        assertEquals(4, child.getHeight(), "Child height = 4 after unhide");
                        assertEquals(4, parent.getHeight(), "Parent re-measured to 4");
                        gate.open();
                    }
                }
            } catch (Throwable t) {
                gate.fail(t);
            }
        });

        rootPanel.addChild(parent);
        harness.triggerRender();
        gate.awaitDone();
    }

    // ── TestGate ─────────────────────────────────────────────────────────
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
                    throw new AssertionError("Test timed out: STATE_LAYOUT_IDLE never reached final step");
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