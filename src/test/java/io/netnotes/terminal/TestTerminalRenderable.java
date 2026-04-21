package io.netnotes.terminal;


import io.netnotes.engine.ui.renderer.RenderPhase;
import io.netnotes.terminal.components.text.TerminalLabel;

/**
 * TestTerminalRenderable - A wrapper that tracks rendering behavior for tests.
 *
 * Instead of trying to override protected methods (which causes compilation
 * issues with the complex generic hierarchy), this class uses composition
 * and the RendererTraceRecorder to track behavior.
 */
public class TestTerminalRenderable extends TerminalLabel {

    private int invalidateCount = 0;
    private long lastInvalidateTime = 0;

    // Simulate layout manager state
    //TODO: fields not used
    private volatile boolean simulateLayoutExecuting = false;
    private volatile boolean simulateInCurrentPass = false;

    public TestTerminalRenderable(String name) {
        super(name, "");
    }

    // Test control methods
    public void setSimulateLayoutExecuting(boolean executing) {
        this.simulateLayoutExecuting = executing;
    }

    public void setSimulateInCurrentPass(boolean inPass) {
        this.simulateInCurrentPass = inPass;
    }

    // Track invalidate calls (this is a public method in TerminalRenderable)
    @Override
    public void invalidate(TerminalRectangle damageRegion) {
        invalidateCount++;
        lastInvalidateTime = System.nanoTime();
        super.invalidate(damageRegion);
    }

    // Query methods for assertions
    public int getInvalidateCount() { return invalidateCount; }
    public long getLastInvalidateTime() { return lastInvalidateTime; }

    /**
     * Get the current render phase
     */
    public RenderPhase getCurrentRenderPhase() {
        return getRenderPhase();
    }

    /**
     * Check if needs render
     */
    public boolean checkNeedsRender() {
        return needsRender();
    }

    /**
     * Reset tracking counters
     */
    public void resetTracking() {
        invalidateCount = 0;
        lastInvalidateTime = 0;
    }

    /**
     * Get tracking state for debugging
     */
    public String getTrackingState() {
        return String.format("inv=%d, phase=%s, needsRender=%s",
            invalidateCount, getRenderPhase(), needsRender());
    }
}
