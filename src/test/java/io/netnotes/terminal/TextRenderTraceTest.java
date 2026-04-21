package io.netnotes.terminal;

import io.netnotes.debug.RendererTraceEvent;
import io.netnotes.debug.RendererTraceRecorder;
import io.netnotes.engine.ui.renderer.RenderPhase;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.components.text.TerminalLabel;
import io.netnotes.terminal.layout.TerminalFloatingLayoutManager;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TextRenderTraceTest - Specifically trace why text might not be rendering.
 *
 * Scenario: Border renders but text doesn't
 * Possible causes this test will detect:
 * 1. Text renderSelf() is never called
 * 2. Text's printAt() is rejected by clip region
 * 3. Text has empty/null content
 * 4. No damage region for text area
 */
public class TextRenderTraceTest {

    private RendererTraceRecorder traceRecorder;

    private static final class LayoutHarness implements AutoCloseable {
        private static final String CONTAINER_NAME = "text-render-trace-test";

        private final TerminalRectanglePool pool = TerminalRectanglePool.getInstance();
        private final TerminalFloatingLayoutManager floatingManager =
            new TerminalFloatingLayoutManager(CONTAINER_NAME, pool);
        private final TerminalLayoutManager layoutManager =
            new TerminalLayoutManager(CONTAINER_NAME, floatingManager);

        void registerAndLayout(TerminalRenderable root) {
            awaitUiExecutor(root);
            layoutManager.registerRenderable(root, null);
            awaitUiExecutor(root);
            layoutManager.markLayoutDirtyImmediate(root);
            awaitLayoutIdle(root);
        }

        TerminalBatchBuilder render(TerminalRenderable root) {
            TerminalBatchBuilder batch = new TerminalBatchBuilder(pool);
            root.toBatch(batch);
            return batch;
        }

        TerminalRectanglePool pool() {
            return pool;
        }

        private void awaitLayoutIdle(TerminalRenderable root) {
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (System.nanoTime() < deadline) {
                    awaitUiExecutor(root);
                    if (!layoutManager.hasPendingLayout()
                        && root.getRenderPhase() != RenderPhase.COLLECTING) {
                        return;
                    }
                    Thread.sleep(5);
                }
                throw new AssertionError("Timed out waiting for layout idle");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for layout idle", e);
            } catch (Exception e) {
                throw new AssertionError("Failed while waiting for layout idle", e);
            }
        }

        private void awaitUiExecutor(TerminalRenderable root) {
            try {
                root.getUIExecutor().submit(() -> null).get(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while draining UI executor", e);
            } catch (Exception e) {
                throw new AssertionError("Failed while draining UI executor", e);
            }
        }

        @Override
        public void close() {
            layoutManager.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        traceRecorder = RendererTraceRecorder.getInstance();
        traceRecorder.setEnabled(true);
        traceRecorder.setCaptureStackTraces(true);
        traceRecorder.clear();
    }

    @AfterEach
    void tearDown() {
        if (traceRecorder.hasDroppedDamage()) {
            System.out.println("\n=== DROPPED DAMAGE REPORT ===");
            traceRecorder.getDroppedDamageReports().forEach(r -> System.out.println(r.describe()));
        }
        traceRecorder.setEnabled(false);
        traceRecorder.clear();
    }

    /**
     * Test: Simple text label with valid region should emit print commands
     */
    @Test
    void simpleText_shouldEmitPrintCommands() {
        TerminalLabel label = new TerminalLabel("test-text", "Hello World");
        label.setRegion(new TerminalRectangle(0, 0, 20, 1));

        try (LayoutHarness harness = new LayoutHarness()) {
            harness.registerAndLayout(label);
            traceRecorder.clear();

            TerminalBatchBuilder batch = harness.render(label);
            var printEvents = traceRecorder.getEvents(RendererTraceEvent.Type.BATCH_COMMAND_ADDED);
            var textAttempt = traceRecorder.getEvents(RendererTraceEvent.Type.TEXT_RENDER_ATTEMPT);

            assertTrue(batch.getCommandCount() > 0,
                "Batch should have commands for text phase=" + label.getRenderPhase()
                    + " started=" + label.isStarted()
                    + " needsRender=" + label.needsRender());
            assertTrue(textAttempt.stream()
                    .anyMatch(e -> "true".equals(e.getAttribute("actuallyRendered", "false"))),
                "Text render attempt should be marked as rendered");

            assertTrue(printEvents.stream().anyMatch(e ->
                    "print".equals(e.getAttribute("command"))
                    && "Hello World".startsWith(e.getAttribute("text", ""))
            ), "Expected at least one print command event for label text");
        }
    }

    /**
     * Test: Text outside clip region should be rejected
     */
    @Test
    void textOutsideClip_shouldBeRejected() {
        TerminalLabel label = new TerminalLabel("test-text", "Hello World");
        label.setRegion(new TerminalRectangle(0, 0, 20, 1));

        try (LayoutHarness harness = new LayoutHarness()) {
            harness.registerAndLayout(label);
            traceRecorder.clear();

            TerminalBatchBuilder batch = new TerminalBatchBuilder(harness.pool());
            TerminalRectangle clip = harness.pool().obtain();
            clip.set(100, 100, 10, 10);

            try {
                label.toBatch(batch, clip);
                assertEquals(0, batch.getCommandCount(),
                    "Clip fully outside label should produce no batch commands");
                assertTrue(traceRecorder.getEvents(RendererTraceEvent.Type.TEXT_RENDER_ATTEMPT).isEmpty(),
                    "No text render attempt expected when clip excludes the label entirely");
            } finally {
                harness.pool().recycle(clip);
            }
        }
    }

    @Test
    void nestedLayout_positionsPrintCommandsInAbsoluteSpace() {
        TerminalRegion root = new TerminalRegion("root");
        root.setRegion(new TerminalRectangle(5, 3, 40, 10));

        TerminalLabel child = new TerminalLabel("child", "ABC");
        child.setRegion(new TerminalRectangle(2, 1, 10, 1));
        root.addChild(child, ctx -> {
            TerminalRectangle requested = child.getRequestedRegion();
            try {
                return TerminalLayoutData.getBuilder().setRegion(requested).build();
            } finally {
                child.getRegionPool().recycle(requested);
            }
        });

        try (LayoutHarness harness = new LayoutHarness()) {
            harness.registerAndLayout(root);
            traceRecorder.clear();

            TerminalBatchBuilder batch = harness.render(root);
            assertTrue(batch.getCommandCount() > 0, "Nested render should emit batch commands");

            var printEvents = traceRecorder.getEvents(RendererTraceEvent.Type.BATCH_COMMAND_ADDED);
            assertTrue(printEvents.stream().anyMatch(e ->
                    "print".equals(e.getAttribute("command"))
                    && "ABC".equals(e.getAttribute("text"))
                    && "7".equals(e.getAttribute("x"))
                    && "4".equals(e.getAttribute("y"))
            ), "Child text should render at absolute coordinates root(5,3)+child(2,1) => (7,4)");
        }
    }

    /**
     * Test: Empty text should not render
     */
    @Test
    void emptyText_shouldNotRender() {
        TerminalLabel label = new TerminalLabel("test-text", "");
        label.setRegion(new TerminalRectangle(0, 0, 20, 1));

        try (LayoutHarness harness = new LayoutHarness()) {
            harness.registerAndLayout(label);
            traceRecorder.clear();

            TerminalBatchBuilder batch = harness.render(label);
            var textAttempt = traceRecorder.getEvents(RendererTraceEvent.Type.TEXT_RENDER_ATTEMPT);

            assertFalse(textAttempt.isEmpty(), "Should track render attempt");
            assertTrue(textAttempt.stream()
                    .anyMatch(e -> "false".equals(e.getAttribute("actuallyRendered", "true"))),
                "Empty text should not actually render");

            assertEquals(0, batch.getCommandCount(), "Empty text should produce no commands");
        }
    }
}
