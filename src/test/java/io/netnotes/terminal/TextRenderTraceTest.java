package io.netnotes.terminal;

import io.netnotes.debug.RendererTraceEvent;
import io.netnotes.debug.RendererTraceRecorder;
import io.netnotes.terminal.components.text.TerminalLabel;
import io.netnotes.terminal.layout.TerminalLayoutTestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TextRenderTraceTest - Specifically trace why text might not be rendering.
 */
public class TextRenderTraceTest {

    private RendererTraceRecorder traceRecorder;
    private TerminalLayoutTestHarness harness;

    @BeforeEach
    void setUp() {
        traceRecorder = RendererTraceRecorder.getInstance();
        traceRecorder.setEnabled(true);
        traceRecorder.setCaptureStackTraces(true);
        traceRecorder.clear();
        harness = new TerminalLayoutTestHarness(80, 24);
    }

    @AfterEach
    void tearDown() {
        if (traceRecorder.hasDroppedDamage()) {
            System.out.println("\n=== DROPPED DAMAGE REPORT ===");
            try {
                traceRecorder.dumpTimeline(System.out);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        traceRecorder.setEnabled(false);
    }

    @Test
    void simpleText_shouldEmitPrintCommands() throws Exception {
        TerminalLabel label = new TerminalLabel("test-text", "Hello World");
        harness.attach(label);

        // Wait for initial layout
        CountDownLatch latch1 = new CountDownLatch(1);
        harness.getStateMachine().onStateAdded(TerminalLayoutTestHarness.STATE_LAYOUT_IDLE, (old, now, bit) -> {
            latch1.countDown();
        });
        harness.triggerRender();
        assertTrue(latch1.await(5, TimeUnit.SECONDS), "Timed out waiting for initial layout");

        // Now set region on UI thread
        harness.runOnUiThreadAndWait(() -> label.setRegion(new TerminalRectangle(0, 0, 20, 1)));

        // Wait for second layout and verify
        CountDownLatch latch2 = new CountDownLatch(1);
        harness.getStateMachine().onStateAdded(TerminalLayoutTestHarness.STATE_LAYOUT_IDLE, (old, now, bit) -> {
            try {
                TerminalBatchBuilder batch = new TerminalBatchBuilder(TerminalRectanglePool.getInstance());
                label.toBatch(batch);

                var printEvents = traceRecorder.getEvents(RendererTraceEvent.Type.BATCH_COMMAND_ADDED);
                var textAttempt = traceRecorder.getEvents(RendererTraceEvent.Type.TEXT_RENDER_ATTEMPT);

                assertTrue(batch.getCommandCount() > 0, "Batch should have commands");
                assertTrue(textAttempt.stream()
                        .anyMatch(e -> "true".equals(e.getAttribute("actuallyRendered", "false"))),
                    "Text render attempt should be marked as rendered");

                assertTrue(printEvents.stream().anyMatch(e ->
                        "print".equals(e.getAttribute("command"))
                                && "Hello World".startsWith(e.getAttribute("text", ""))
                ), "Expected at least one print command event for label text");

            } catch (Throwable t) {
                fail("Test failed: " + t.getMessage());
            } finally {
                latch2.countDown();
            }
        });
        harness.triggerRender();
        assertTrue(latch2.await(5, TimeUnit.SECONDS), "Timed out waiting for second layout");
    }
}
