package io.netnotes.gui;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Global frame rate monitor for coordinating timing across all real-time components
 */
public class FrameRateMonitor {
    private static final FrameRateMonitor INSTANCE = new FrameRateMonitor();
    
    private static final int SAMPLE_SIZE = 30;
    private final long[] frameTimes = new long[SAMPLE_SIZE];
    private final double[] layoutCosts = new double[SAMPLE_SIZE];
    private int frameIndex = 0;
    private long lastFrameTime = System.nanoTime();
    private AtomicReference<Double> averageFrameTime = new AtomicReference<Double>(16.0); // Start at 60fps assumption
    private AtomicReference<Double>  averageLayoutCost = new AtomicReference<Double>(0.0);
    
    private FrameRateMonitor() {}
    
    public static FrameRateMonitor getInstance() {
        return INSTANCE;
    }
    
    /**
     * Record a frame completion with layout cost information.
     * @param layoutCostMs Time spent in layout calculation in milliseconds
     */
    public void recordFrame(double layoutCostMs) {
        long now = System.nanoTime();
        long elapsed = now - lastFrameTime;
        lastFrameTime = now;
        
        frameTimes[frameIndex] = elapsed;
        layoutCosts[frameIndex] = layoutCostMs;
        frameIndex = (frameIndex + 1) % SAMPLE_SIZE;
        
        // Calculate rolling averages
        long timeSum = 0;
        double costSum = 0;
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            timeSum += frameTimes[i];
            costSum += layoutCosts[i];
        }
        averageFrameTime.set((timeSum / (double) SAMPLE_SIZE) / 1_000_000.0); // Convert to ms
        averageLayoutCost.set(costSum / SAMPLE_SIZE);
    }
    
    /**
     * Record a frame completion without layout cost (for other components).
     */
    public void recordFrame() {
        recordFrame(0.0);
    }
    
    /**
     * Get the average frame time in milliseconds
     */
    public double getAverageFrameTime() {
        return averageFrameTime.get();
    }
    
    /**
     * Get the average layout cost in milliseconds
     */
    public double getAverageLayoutCost() {
        return averageLayoutCost.get();
    }
    
    /**
     * Get the percentage of frame time spent in layout
     */
    public double getLayoutPercentage() {
        double avgFrameTime = getAverageFrameTime();
        double avgLayoutCost = getAverageLayoutCost();
        return avgFrameTime > 0 ? (avgLayoutCost / avgFrameTime) * 100.0 : 0.0;
    }
    
    /**
     * Get the current FPS
     */
    public double getFPS() {
        double avgFrameTime = getAverageFrameTime();
        return 1000.0 / avgFrameTime;
    }
    
    /**
     * Check if we're under performance pressure
     * @return true if FPS is below 30
     */
    public boolean isUnderPressure() {
        return getFPS() < 30;
    }
    
    /**
     * Check if layout is the bottleneck
     * @return true if layout takes more than 50% of frame time
     */
    public boolean isLayoutBottleneck() {
        return getLayoutPercentage() > 50.0;
    }
    
    /**
     * Get recommended delay for debouncing based on current performance
     * @return delay in milliseconds
     */
    public long getRecommendedDebounceDelay() {
        double fps = getFPS();
        
        // If layout is the bottleneck, be more aggressive with batching
        if (isLayoutBottleneck()) {
            return 100;
        }
        
        if (fps > 50) {
            return 16; // ~60fps target
        } else if (fps > 30) {
            return 33; // ~30fps target
        } else {
            return 60; // Under pressure, batch more aggressively
        }
    }
}