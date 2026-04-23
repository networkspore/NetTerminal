package io.netnotes.terminal.layout;

import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * TerminalDamageTestHarness
 *
 * Extends {@link TerminalLayoutTestHarness} to capture the damage rectangles
 * that are dispatched during each render cycle, making them available for
 * assertions in damage-propagation tests.
 *
 * HOW IT WORKS:
 * The base class calls {@code buildBatchCommand(batch, damage)} once per render
 * cycle, passing the list of damage regions that were accumulated for that frame.
 * Those regions are recycled into the pool immediately after the call returns.
 * This subclass snapshots them (by copying) before delegating to super, then
 * surfaces the snapshot through {@link #waitForRenderAndGetDamage()} /
 * {@link #beginDamageCapture()} + {@link #awaitDamageCapture()}.
 *
 * USAGE PATTERN:
 * <pre>
 *   // Setup - drain the initial layout+render pass so we start clean
 *   harness.attach(panel);
 *   harness.drainInitialRender();   // waits for layout + discards first render's damage
 *
 *   // Test - arm capture BEFORE triggering the action to avoid a race
 *   harness.beginDamageCapture();
 *   panel.invalidate();             // or addToPanel, clearPanel, hide, etc.
 *   List<TerminalRectangle> damage = harness.awaitDamageCapture();
 *   // assert on damage ...
 * </pre>
 */
public class TerminalDamageTestHarness extends TerminalLayoutTestHarness {

    /** Snapshot from the most recently completed render cycle. */
    private volatile List<TerminalRectangle> lastCapturedDamage = List.of();

    /**
     * Set by {@link #beginDamageCapture()} before the triggering action,
     * completed inside {@link #buildBatchCommand} when the render fires.
     * Volatile so both threads see the reference update.
     */
    private volatile CompletableFuture<List<TerminalRectangle>> renderFuture;

    public TerminalDamageTestHarness(int width, int height) {
        super(width, height);
    }

    // -------------------------------------------------------------------------
    // Core override — snapshot damage before super recycles the pool objects
    // -------------------------------------------------------------------------

    @Override
    protected NoteBytesObject buildBatchCommand(
            TerminalBatchBuilder batch,
            List<TerminalRectangle> damage) {

        // Copy before super() recycles via regionPool
        List<TerminalRectangle> snapshot = damage.stream()
                .map(r -> new TerminalRectangle(r.getX(), r.getY(),
                                                r.getWidth(), r.getHeight()))
                .collect(Collectors.toList());
        lastCapturedDamage = snapshot;

        NoteBytesObject result = super.buildBatchCommand(batch, damage);

        // Signal any waiting test thread
        CompletableFuture<List<TerminalRectangle>> f = renderFuture;
        if (f != null && !f.isDone()) {
            f.complete(new ArrayList<>(snapshot));
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Public API for tests
    // -------------------------------------------------------------------------

    /**
     * Arms the damage capture latch.  Call this BEFORE the action that should
     * produce a render (e.g. {@code invalidate()}, {@code addToPanel()}, …),
     * then call {@link #awaitDamageCapture()} to block until the render fires
     * and retrieve the damage.
     *
     * Pairing beginDamageCapture + awaitDamageCapture avoids a race between
     * the triggering action scheduling a render on the UI executor and the test
     * thread setting up the future: the future is in place before the action is
     * dispatched.
     */
    public void beginDamageCapture() {
        renderFuture = new CompletableFuture<>();
    }

    /**
     * Blocks until the next render cycle completes and returns the damage
     * regions that were submitted to the batch for that cycle.
     *
     * Must be preceded by a call to {@link #beginDamageCapture()}.
     *
     * @return snapshot of damage rectangles in absolute screen coordinates
     * @throws AssertionError if no render fires within 1 second
     */
    public List<TerminalRectangle> awaitDamageCapture() {
        CompletableFuture<List<TerminalRectangle>> f = renderFuture;
        if (f == null) {
            throw new IllegalStateException(
                    "Call beginDamageCapture() before awaitDamageCapture()");
        }
        try {
            return f.get(1, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError(
                    "No render was dispatched within the timeout. "
                    + "Did the action actually produce damage?");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for render", e);
        } catch (Exception e) {
            throw new AssertionError("Failed while waiting for render", e);
        } finally {
            renderFuture = null;
        }
    }

    /**
     * Convenience shorthand: arm capture, trigger an action via the supplied
     * runnable, then block until the render fires and return the damage.
     *
     * <pre>
     *   List<TerminalRectangle> damage = harness.captureNextRender(
     *       () -> panel.invalidate()
     *   );
     * </pre>
     */
    public List<TerminalRectangle> captureNextRender(Runnable trigger) {
        beginDamageCapture();
        trigger.run();
        return awaitDamageCapture();
    }

    /**
     * Drains the initial layout-and-render pass that occurs immediately after
     * {@link #attach}.  Call this in {@code @BeforeEach} after {@code attach()}
     * so that subsequent test actions start from a clean (no-pending-damage) state.
     *
     * If the initial pass produces no render (e.g. an all-hidden panel), this
     * returns quietly after layout completes.
     */
    public void drainInitialRender() {
        // Arm the render future BEFORE waiting for layout, because the render
        // is typically scheduled by the layout pass itself and may fire
        // concurrently before waitForLayoutComplete() returns.
        renderFuture = new CompletableFuture<>();
        boolean ok = waitForLayoutComplete();
        if (!ok) throw new AssertionError("Initial layout did not complete");
        try {
            // Short timeout — if no render fires the panel had nothing to draw
            renderFuture.get(500, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException ignored) {
            // All-hidden panel — nothing was rendered, that's fine
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted during initial drain", e);
        } catch (Exception e) {
            throw new AssertionError("Error during initial drain", e);
        } finally {
            renderFuture = null;
        }
    }

    /**
     * Returns the damage snapshot from the most recently completed render cycle.
     * Returns an empty list if no render has fired yet.
     */
    public List<TerminalRectangle> getLastCapturedDamage() {
        return lastCapturedDamage;
    }

    // -------------------------------------------------------------------------
    // Geometry helpers for assertions
    // -------------------------------------------------------------------------

    /**
     * Returns the union bounding box of all captured damage rectangles, or
     * {@code null} if the list is empty.
     */
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

    /**
     * Returns {@code true} if any damage rectangle in {@code regions} fully
     * contains the given point (x, y).
     */
    public static boolean anyContains(List<TerminalRectangle> regions, int x, int y) {
        for (TerminalRectangle r : regions) {
            if (x >= r.getX() && x < r.getX() + r.getWidth()
                    && y >= r.getY() && y < r.getY() + r.getHeight()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the union of all damage rectangles covers the
     * entire supplied bounding box (left, top, right, bottom — exclusive).
     */
    public static boolean unionCovers(List<TerminalRectangle> regions,
                                      int x, int y, int w, int h) {
        TerminalRectangle u = unionOf(regions);
        if (u == null) return false;
        return u.getX() <= x && u.getY() <= y
                && u.getX() + u.getWidth()  >= x + w
                && u.getY() + u.getHeight() >= y + h;
    }
}