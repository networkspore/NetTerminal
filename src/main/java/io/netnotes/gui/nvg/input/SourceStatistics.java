package io.netnotes.gui.nvg.input;

import java.util.concurrent.atomic.AtomicLong;

/**
 * SourceStatistics - Tracks performance metrics for an input source
 */
public final class SourceStatistics {
    private final AtomicLong eventCount = new AtomicLong(0);
    private final AtomicLong droppedEvents = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeNanos = new AtomicLong(0);
    private final AtomicLong maxProcessingTimeNanos = new AtomicLong(0);
    private final AtomicLong minProcessingTimeNanos = new AtomicLong(Long.MAX_VALUE);
    
    private volatile long startTimeMillis = System.currentTimeMillis();
    private volatile long lastEventTimeMillis = 0;
    private volatile long lastResetTimeMillis = System.currentTimeMillis();
    
    // Sliding window for events per second calculation
    private static final int WINDOW_SIZE = 10;
    private final long[] eventTimestamps = new long[WINDOW_SIZE];
    private final long[] eventCounts = new long[WINDOW_SIZE];
    private volatile int windowIndex = 0;
    
    public SourceStatistics() {
        // Initialize window
        for (int i = 0; i < WINDOW_SIZE; i++) {
            eventTimestamps[i] = 0;
            eventCounts[i] = 0;
        }
    }
    
    /**
     * Record an event
     */
    public void recordEvent() {
        recordEvent(0);
    }
    
    /**
     * Record an event with processing time
     */
    public void recordEvent(long processingTimeNanos) {
        long count = eventCount.incrementAndGet();
        lastEventTimeMillis = System.currentTimeMillis();
        
        if (processingTimeNanos > 0) {
            totalProcessingTimeNanos.addAndGet(processingTimeNanos);
            
            // Update max
            long currentMax;
            do {
                currentMax = maxProcessingTimeNanos.get();
                if (processingTimeNanos <= currentMax) break;
            } while (!maxProcessingTimeNanos.compareAndSet(currentMax, processingTimeNanos));
            
            // Update min
            long currentMin;
            do {
                currentMin = minProcessingTimeNanos.get();
                if (processingTimeNanos >= currentMin) break;
            } while (!minProcessingTimeNanos.compareAndSet(currentMin, processingTimeNanos));
        }
        
        // Update sliding window
        updateWindow(lastEventTimeMillis, count);
    }
    
    /**
     * Record a dropped event
     */
    public void recordDroppedEvent() {
        droppedEvents.incrementAndGet();
    }
    
    /**
     * Update the sliding window for rate calculation
     */
    private synchronized void updateWindow(long timestamp, long count) {
        // Move to next slot if enough time has passed (1 second)
        if (timestamp - eventTimestamps[windowIndex] >= 1000) {
            windowIndex = (windowIndex + 1) % WINDOW_SIZE;
            eventTimestamps[windowIndex] = timestamp;
            eventCounts[windowIndex] = count;
        } else {
            // Update current slot
            eventCounts[windowIndex] = count;
        }
    }
    
    /**
     * Get total event count
     */
    public long getEventCount() {
        return eventCount.get();
    }
    
    /**
     * Get dropped event count
     */
    public long getDroppedEvents() {
        return droppedEvents.get();
    }
    
    /**
     * Get drop rate as percentage
     */
    public double getDropRate() {
        long total = eventCount.get() + droppedEvents.get();
        return total > 0 ? (droppedEvents.get() * 100.0 / total) : 0.0;
    }
    
    /**
     * Get average processing time in nanoseconds
     */
    public double getAverageProcessingTime() {
        long count = eventCount.get();
        return count > 0 ? (double) totalProcessingTimeNanos.get() / count : 0.0;
    }
    
    /**
     * Get max processing time in nanoseconds
     */
    public long getMaxProcessingTime() {
        long max = maxProcessingTimeNanos.get();
        return max < Long.MAX_VALUE ? max : 0;
    }
    
    /**
     * Get min processing time in nanoseconds
     */
    public long getMinProcessingTime() {
        long min = minProcessingTimeNanos.get();
        return min < Long.MAX_VALUE ? min : 0;
    }
    
    /**
     * Get events per second (over sliding window)
     */
    public double getEventsPerSecond() {
        long now = System.currentTimeMillis();
        long oldestTimestamp = Long.MAX_VALUE;
        long oldestCount = 0;
        long newestCount = 0;
        
        synchronized (this) {
            // Find oldest and newest valid samples
            for (int i = 0; i < WINDOW_SIZE; i++) {
                long timestamp = eventTimestamps[i];
                if (timestamp > 0 && now - timestamp < (WINDOW_SIZE * 1000)) {
                    if (timestamp < oldestTimestamp) {
                        oldestTimestamp = timestamp;
                        oldestCount = eventCounts[i];
                    }
                    newestCount = Math.max(newestCount, eventCounts[i]);
                }
            }
        }
        
        if (oldestTimestamp == Long.MAX_VALUE) {
            return 0.0;
        }
        
        long timeSpanMillis = now - oldestTimestamp;
        if (timeSpanMillis <= 0) {
            return 0.0;
        }
        
        long eventDelta = newestCount - oldestCount;
        return (eventDelta * 1000.0) / timeSpanMillis;
    }
    
    /**
     * Get time since last event in milliseconds
     */
    public long getTimeSinceLastEvent() {
        if (lastEventTimeMillis == 0) {
            return -1;
        }
        return System.currentTimeMillis() - lastEventTimeMillis;
    }
    
    /**
     * Get uptime in milliseconds
     */
    public long getUptimeMillis() {
        return System.currentTimeMillis() - startTimeMillis;
    }
    
    /**
     * Get time since last reset in milliseconds
     */
    public long getTimeSinceReset() {
        return System.currentTimeMillis() - lastResetTimeMillis;
    }
    
    /**
     * Reset statistics
     */
    public void reset() {
        eventCount.set(0);
        droppedEvents.set(0);
        totalProcessingTimeNanos.set(0);
        maxProcessingTimeNanos.set(0);
        minProcessingTimeNanos.set(Long.MAX_VALUE);
        lastResetTimeMillis = System.currentTimeMillis();
        
        synchronized (this) {
            for (int i = 0; i < WINDOW_SIZE; i++) {
                eventTimestamps[i] = 0;
                eventCounts[i] = 0;
            }
            windowIndex = 0;
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "SourceStatistics{events=%d, dropped=%d (%.2f%%), rate=%.1f/s, avgTime=%.2fμs, maxTime=%.2fμs, uptime=%dms}",
            getEventCount(),
            getDroppedEvents(),
            getDropRate(),
            getEventsPerSecond(),
            getAverageProcessingTime() / 1000.0,
            getMaxProcessingTime() / 1000.0,
            getUptimeMillis()
        );
    }
}