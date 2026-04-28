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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Debug tests for visibility and content sizing behavior.
 *
 * Canonical behavior: a FIT_CONTENT-height VStack never auto-hides children
 * on overflow (fitContentOverride in layoutAllChildren). Instead, all
 * non-force-hidden children stay visible and the VStack reports its full
 * content size so the parent can decide whether to grow.
 */
public class VisibilityStabilizationDebugTest {

    private TerminalLayoutTestHarness harness;
    private TerminalPanel rootPanel;

    @BeforeEach
    void setup() {
        rootPanel = new TerminalPanel("root");
        harness = new TerminalLayoutTestHarness(80, 24);
        harness.attach(rootPanel); // blocks until first idle
    }

    @Test
    void debugFitContentVStackOverflowNeverHidesChildren() {
        TerminalVStack container = new TerminalVStack("container");
        container.setHeightPreference(SizePreference.FIT_CONTENT);

        for (int i = 0; i < 5; i++) {
            container.addChild(createLabel("label-" + i, 3));
        }

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        // Initial layout – plenty of space.
                        assertEquals(15, container.getHeight(), "All 5 children: 5 × 3 = 15");
                        harness.setAllocatedRegion(0, 0, 80, 7); // shrink region
                    }
                    case 1 -> {
                        // After shrink: children still visible, FIT_CONTENT disables auto-hide.
                        for (int i = 0; i < container.getChildren().size(); i++) {
                            assertFalse(container.getChildren().get(i).isHidden(),
                                "Child " + i + " must NOT be auto-hidden");
                        }
                        harness.setAllocatedRegion(0, 0, 80, 24); // restore
                    }
                    case 2 -> {
                        assertEquals(15, container.getHeight(), "Still 15 after restore");
                        gate.open();
                    }
                }
            } catch (Throwable t) {
                gate.fail(t);
            }
        });

        rootPanel.addChild(container);
        harness.triggerRender();
        gate.awaitDone();
    }

    @Test
    void debugContentSizedChildVisibilityToggle() {
        TerminalVStack parent = new TerminalVStack("parent");
        parent.setHeightPreference(SizePreference.FIT_CONTENT);

        TerminalVStack staticChild = new TerminalVStack("staticChild");
        staticChild.setHeightPreference(SizePreference.STATIC);
        staticChild.setMinHeight(2);
        staticChild.addChild(createLabel("static-label", 2));

        TerminalVStack contentChild = new TerminalVStack("contentChild");
        contentChild.setHeightPreference(SizePreference.FIT_CONTENT);
        contentChild.addChild(createLabel("content", 5));

        parent.addChild(staticChild);
        parent.addChild(contentChild);

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        // Both children visible.
                        assertEquals(7, parent.getHeight(), "Parent: 2 + 5 = 7");
                        contentChild.hide(); // force-hide
                    }
                    case 1 -> {
                        // After hiding content child.
                        assertTrue(contentChild.isHidden(), "contentChild is force-hidden");
                        assertEquals(2, parent.getHeight(), "Parent = 2 (only staticChild)");
                        contentChild.show(); // unhide, multi-pass
                    }
                    case 2 -> {
                        // After unhide.
                        assertFalse(contentChild.isHidden(), "contentChild visible after unhide");
                        assertEquals(5, contentChild.getHeight(), "contentChild height = 5");
                        assertEquals(7, parent.getHeight(), "Parent re-measured: 2 + 5 = 7");
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

    private TerminalLabel createLabel(String name, int height) {
        TerminalLabel label = new TerminalLabel(name);
        label.setMinHeight(height);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        return label;
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