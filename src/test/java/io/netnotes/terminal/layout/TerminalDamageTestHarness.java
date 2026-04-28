package io.netnotes.terminal.layout;

import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * TerminalDamageTestHarness
 *
 * Lightweight extension of {@link TerminalLayoutTestHarness} that captures
 * the damage rectangles dispatched during each render cycle.
 *
 * Fully uses the state‑machine and latch‑based synchronisation; no
 * {@code CompletableFuture} or {@code waitForLayoutComplete}.
 */
public class TerminalDamageTestHarness extends TerminalLayoutTestHarness {

    private volatile List<TerminalRectangle> lastCapturedDamage = List.of();
    private volatile CountDownLatch damageLatch;
    private volatile List<TerminalRectangle> capturedDamage;

    public TerminalDamageTestHarness(int width, int height) {
        super(width, height);
    }

    @Override
    protected NoteBytesObject buildBatchCommand(
            TerminalBatchBuilder batch,
            List<TerminalRectangle> damage) {

        List<TerminalRectangle> snapshot = damage.stream()
                .map(r -> new TerminalRectangle(r.getX(), r.getY(),
                                                r.getWidth(), r.getHeight()))
                .collect(Collectors.toList());
        lastCapturedDamage = snapshot;

        CountDownLatch latch = damageLatch;
        if (latch != null) {
            capturedDamage = new ArrayList<>(snapshot);
            latch.countDown();
        }

        return super.buildBatchCommand(batch, damage);
    }

    // ── public API ───────────────────────────────────────────────────────

    /**
     * Arm the damage capture. Next render batch will release the latch.
     */
    public void beginDamageCapture() {
        damageLatch = new CountDownLatch(1);
        capturedDamage = null;
    }

    /**
     * Block until the next render batch completes and return its damage.
     */
    public List<TerminalRectangle> awaitDamageCapture() {
        CountDownLatch latch = damageLatch;
        if (latch == null) throw new IllegalStateException("Call beginDamageCapture() first");
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("No render dispatched within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted", e);
        } finally {
            damageLatch = null;
        }
        return capturedDamage == null ? List.of() : capturedDamage;
    }

    public List<TerminalRectangle> captureNextRender(Runnable trigger) {
        beginDamageCapture();
        trigger.run();
        return awaitDamageCapture();
    }

    public List<TerminalRectangle> getLastCapturedDamage() {
        return lastCapturedDamage;
    }

    // No drainInitialRender needed – attach() already waits for idle.
    // If a test needs to discard the very first render, it can call
    // captureNextRender(() -> {}) after attach.

    // ── Geometry helpers ─────────────────────────────────────────────────

    public static TerminalRectangle unionOf(List<TerminalRectangle> regions) {
        if (regions.isEmpty()) return null;
        int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE;
        int x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE;
        for (TerminalRectangle r : regions) {
            x1 = Math.min(x1, r.getX());
            y1 = Math.min(y1, r.getY());
            x2 = Math.max(x2, r.getX() + r.getWidth());
            y2 = Math.max(y2, r.getY() + r.getHeight());
        }
        return new TerminalRectangle(x1, y1, x2 - x1, y2 - y1);
    }

    public static boolean anyContains(List<TerminalRectangle> regions, int x, int y) {
        for (TerminalRectangle r : regions) {
            if (x >= r.getX() && x < r.getX() + r.getWidth()
                    && y >= r.getY() && y < r.getY() + r.getHeight())
                return true;
        }
        return false;
    }

    public static boolean unionCovers(List<TerminalRectangle> regions,
                                      int x, int y, int w, int h) {
        TerminalRectangle u = unionOf(regions);
        if (u == null) return false;
        return u.getX() <= x && u.getY() <= y
                && u.getX() + u.getWidth()  >= x + w
                && u.getY() + u.getHeight() >= y + h;
    }
}