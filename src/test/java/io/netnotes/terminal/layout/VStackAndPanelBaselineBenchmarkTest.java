package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.LayoutOverflowStrategy;
import io.netnotes.terminal.components.panels.TerminalPanel;
import io.netnotes.terminal.components.panels.TerminalPanel.AlignItems;
import io.netnotes.terminal.components.panels.TerminalPanel.FlexDirection;
import io.netnotes.terminal.components.panels.TerminalVStack;
import io.netnotes.terminal.components.text.TerminalLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.netnotes.terminal.layout.TerminalLayoutTestHarness.STATE_LAYOUT_IDLE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Baseline benchmark comparing TerminalVStack and TerminalPanel.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * TEST LAYOUT
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Part 1 — VStack baseline: simple vertical stacking.
 * Part 2 — Panel baseline:  same children, horizontal layout.
 *
 * All tests use only min values.  The step‑dispatch pattern is identical
 * to the existing benchmark tests (TerminalLayoutPanelBenchmarkTest).
 */
public class VStackAndPanelBaselineBenchmarkTest {

    private static final int W = 80;
    private static final int H = 24;

    private TerminalLayoutTestHarness harness;
    private TerminalPanel rootPanel;

    @BeforeEach
    void setup() {
        rootPanel = new TerminalPanel("root");
        rootPanel.setWidthPreference(SizePreference.FILL);
        rootPanel.setHeightPreference(SizePreference.FIT_CONTENT);
        harness = new TerminalLayoutTestHarness(W, H);
        harness.attach(rootPanel);   // blocks until first stable layout
    }

    @AfterEach
    void cleanup() { /* GC handles harness */ }

    // ═══════════════════════════════════════════════════════════════════
    // PART 1 — TERMINAL VSTACK BASELINE
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void vstackSumsChildMinHeights() {
        TerminalVStack vstack = new TerminalVStack("vstack");
        TerminalLabel c1 = createLabel("c1", 3);
        TerminalLabel c2 = createLabel("c2", 4);
        TerminalLabel c3 = createLabel("c3", 2);

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        assertEquals(9, vstack.getHeight(), "VStack height = 3+4+2 = 9");
                        assertEquals(W, vstack.getWidth(), "Width fills root");
                        assertFalse(c1.isHidden());
                        assertFalse(c2.isHidden());
                        assertFalse(c3.isHidden());
                        gate.open();
                    }
                }
            } catch (Throwable t) { gate.fail(t); }
        });

        vstack.addChild(c1);
        vstack.addChild(c2);
        vstack.addChild(c3);
        rootPanel.addChild(vstack);
        harness.triggerRender();
        gate.awaitDone();
    }

    @Test
    void vstackFillChildrenShareRemainingHeight() {
        TerminalVStack vstack = new TerminalVStack("vstack");
        vstack.setHeightPreference(SizePreference.FIT_CONTENT); // explicit

        TerminalLabel c1 = new TerminalLabel("c1");
        c1.setMinHeight(2);
        c1.setHeightPreference(SizePreference.FILL);
        TerminalLabel c2 = new TerminalLabel("c2");
        c2.setMinHeight(2);
        c2.setHeightPreference(SizePreference.FILL);

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        // FIT_CONTENT stack does not allocate "remaining parent height" to FILL
                        // children; FILL falls back to min height in content-measure mode.
                        assertEquals(2, c1.getHeight(), "c1 resolves to min height");
                        assertEquals(2, c2.getHeight(), "c2 resolves to min height");
                        assertEquals(4, vstack.getHeight(), "VStack height = 2 + 2");
                        gate.open();
                    }
                }
            } catch (Throwable t) { gate.fail(t); }
        });

        vstack.addChild(c1);
        vstack.addChild(c2);
        rootPanel.addChild(vstack);
        harness.triggerRender();
        gate.awaitDone();
    }

    // (Add more VStack baselines as needed, e.g. STATIC, PERCENT, etc.)

    // ═══════════════════════════════════════════════════════════════════
    // PART 2 — TERMINAL PANEL BASELINE (same tests, horizontal axis)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void panelSumsChildMinWidths() {
        // Panel defaults to horizontal, FIT_CONTENT sizing
        TerminalPanel panel = new TerminalPanel("panel");
        panel.setWidthPreference(SizePreference.FIT_CONTENT);
        panel.setHeightPreference(SizePreference.FIT_CONTENT);

        TerminalLabel c1 = createLabel("c1", 3);
        c1.setMinWidth(5);  // override width for this panel test
        TerminalLabel c2 = createLabel("c2", 4);
        c2.setMinWidth(7);
        TerminalLabel c3 = createLabel("c3", 2);
        c3.setMinWidth(3);

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        assertEquals(5+7+3, panel.getWidth(), "Panel width = 5+7+3 = 15");
                        // Height is max child height (4), independent of width
                        assertEquals(4, panel.getHeight(), "Panel height = max child height = 4");
                        gate.open();
                    }
                }
            } catch (Throwable t) { gate.fail(t); }
        });

        panel.addChild(c1);
        panel.addChild(c2);
        panel.addChild(c3);
        rootPanel.addChild(panel);
        harness.triggerRender();
        gate.awaitDone();
    }

    @Test
    void panelFillChildrenShareRemainingWidth() {
        TerminalPanel panel = new TerminalPanel("panel");
        TerminalLabel c1 = new TerminalLabel("c1");
        c1.setMinWidth(2);
        c1.setWidthPreference(SizePreference.FILL);
        TerminalLabel c2 = new TerminalLabel("c2");
        c2.setMinWidth(2);
        c2.setWidthPreference(SizePreference.FILL);

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        // Default panel sizing is STATIC(0x0), so there is no width budget
                        // unless the panel itself is configured with FILL/FIT_CONTENT/STATIC size.
                        assertEquals(0, c1.getWidth(), "c1 width is clipped by 0-width parent");
                        assertEquals(0, c2.getWidth(), "c2 width is clipped by 0-width parent");
                        assertEquals(0, panel.getWidth(), "Panel keeps default STATIC width of 0");
                        gate.open();
                    }
                }
            } catch (Throwable t) { gate.fail(t); }
        });

        panel.addChild(c1);
        panel.addChild(c2);
        rootPanel.addChild(panel);
        harness.triggerRender();
        gate.awaitDone();
    }

    @Test
    void panelHorizontalFitContentAddsSpacingToWidth() {
        TerminalPanel panel = new TerminalPanel("panel");
        panel.setWidthPreference(SizePreference.FIT_CONTENT);
        panel.setHeightPreference(SizePreference.FIT_CONTENT);
        panel.setSpacing(2);

        TerminalLabel c1 = new TerminalLabel("c1", "AAAA");
        TerminalLabel c2 = new TerminalLabel("c2", "BBB");

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        assertEquals(9, panel.getWidth(), "4 + 2 + 3 = 9");
                        assertEquals(1, panel.getHeight(), "max child height = 1");
                        assertEquals(0, c1.getX());
                        assertEquals(6, c2.getX(), "c2 starts after c1(4) + spacing(2)");
                        gate.open();
                    }
                }
            } catch (Throwable t) { gate.fail(t); }
        });

        panel.addChild(c1);
        panel.addChild(c2);
        rootPanel.addChild(panel);
        harness.triggerRender();
        gate.awaitDone();
    }

    @Test
    void panelVerticalAxisStacksChildrenWithSpacing() {
        TerminalPanel panel = new TerminalPanel("panel");
        panel.setDirection(FlexDirection.ROW);
        panel.setWidthPreference(SizePreference.FIT_CONTENT);
        panel.setHeightPreference(SizePreference.FIT_CONTENT);
        panel.setSpacing(1);

        TerminalLabel c1 = new TerminalLabel("c1", "AAAAA");
        TerminalLabel c2 = new TerminalLabel("c2", "BB");

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        assertEquals(5, panel.getWidth(), "max child width = 5");
                        assertEquals(3, panel.getHeight(), "1 + spacing(1) + 1 = 3");
                        assertEquals(0, c1.getY());
                        assertEquals(2, c2.getY(), "c2 starts after c1(1) + spacing(1)");
                        gate.open();
                    }
                }
            } catch (Throwable t) { gate.fail(t); }
        });

        panel.addChild(c1);
        panel.addChild(c2);
        rootPanel.addChild(panel);
        harness.triggerRender();
        gate.awaitDone();
    }

    @Test
    void panelCrossAlignmentEndPinsChildToBottom() {
        TerminalPanel panel = new TerminalPanel("panel");
        panel.setWidthPreference(SizePreference.FIT_CONTENT);
        panel.setHeightPreference(SizePreference.STATIC);
        panel.setAlignItems(AlignItems.END);
        panel.setRegion(0, 0, 0, 5);

        TerminalLabel child = new TerminalLabel("child", "abc");

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        assertEquals(5, panel.getHeight());
                        assertEquals(4, child.getY(), "height(5) - childHeight(1) = 4");
                        gate.open();
                    }
                }
            } catch (Throwable t) { gate.fail(t); }
        });

        panel.addChild(child);
        rootPanel.addChild(panel);
        harness.triggerRender();
        gate.awaitDone();
    }

    @Test
    void panelCrossAlignmentStretchExpandsChildHeight() {
        TerminalPanel panel = new TerminalPanel("panel");
        panel.setWidthPreference(SizePreference.FIT_CONTENT);
        panel.setHeightPreference(SizePreference.STATIC);
        panel.setAlignItems(AlignItems.STRETCH);
        panel.setRegion(0, 0, 0, 5);

        TerminalLabel child = new TerminalLabel("child", "abc");

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        assertEquals(5, panel.getHeight());
                        assertEquals(5, child.getHeight(), "STRETCH uses full cross-axis height");
                        assertEquals(0, child.getY());
                        gate.open();
                    }
                }
            } catch (Throwable t) { gate.fail(t); }
        });

        panel.addChild(child);
        rootPanel.addChild(panel);
        harness.triggerRender();
        gate.awaitDone();
    }

    @Test
    void panelDistributeEqualSplitsPrimarySpaceEvenly() {
        TerminalPanel panel = new TerminalPanel("panel");
        panel.setWidthPreference(SizePreference.STATIC);
        panel.setHeightPreference(SizePreference.STATIC);
        panel.setOverflowStrategy(LayoutOverflowStrategy.DISTRIBUTE_EQUAL);
        panel.setRegion(0, 0, 10, 1);

        TerminalLabel c1 = new TerminalLabel("c1", "A");
        TerminalLabel c2 = new TerminalLabel("c2", "B");

        TestGate gate = new TestGate();
        int[] step = {0};

        harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                switch (step[0]++) {
                    case 0 -> {
                        assertEquals(10, panel.getWidth());
                        assertEquals(5, c1.getWidth(), "equal share of 10 among 2 children");
                        assertEquals(5, c2.getWidth(), "equal share of 10 among 2 children");
                        assertEquals(0, c1.getX());
                        assertEquals(5, c2.getX());
                        gate.open();
                    }
                }
            } catch (Throwable t) { gate.fail(t); }
        });

        panel.addChild(c1);
        panel.addChild(c2);
        rootPanel.addChild(panel);
        harness.triggerRender();
        gate.awaitDone();
    }

    // (Add mirrored tests for STATIC, PERCENT, etc.)

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private TerminalLabel createLabel(String name, int minHeight) {
        TerminalLabel label = new TerminalLabel(name);
        label.setMinHeight(minHeight);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        return label;
    }

    // ── TestGate (identical to your existing implementation) ──────────
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
                    throw new AssertionError(
                        "Test timed out: STATE_LAYOUT_IDLE never reached final step.");
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
