package io.netnotes.gui.core.binaryEvents;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe event queue that batches events for processing.
 * Decouples event collection from state processing.
 */
public class BinaryEventQueue {
    
    private final ConcurrentLinkedQueue<BinaryEvent> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    
    private final int maxBatchSize;
    
    public BinaryEventQueue() {
        this(100); // Default batch size
    }
    
    public BinaryEventQueue(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }
    
    /**
     * Add event to queue (called from UI thread)
     */
    public void enqueue(BinaryEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        queue.offer(event);
        pendingCount.incrementAndGet();
    }
    
    /**
     * Add multiple events atomically
     */
    public void enqueueBatch(List<BinaryEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        
        for (BinaryEvent event : events) {
            queue.offer(event);
        }
        pendingCount.addAndGet(events.size());
    }
    
    /**
     * Drain up to maxBatchSize events for processing.
     * Returns empty list if no events available.
     */
    public List<BinaryEvent> drainBatch() {
        List<BinaryEvent> batch = new ArrayList<>();
        
        int count = 0;
        while (count < maxBatchSize) {
            BinaryEvent event = queue.poll();
            if (event == null) {
                break;
            }
            batch.add(event);
            count++;
        }
        
        pendingCount.addAndGet(-count);
        return batch;
    }
    
    /**
     * Drain all pending events (use with caution)
     */
    public List<BinaryEvent> drainAll() {
        List<BinaryEvent> batch = new ArrayList<>();
        
        BinaryEvent event;
        while ((event = queue.poll()) != null) {
            batch.add(event);
        }
        
        pendingCount.set(0);
        return batch;
    }
    
    /**
     * Check if events are pending
     */
    public boolean hasPending() {
        return pendingCount.get() > 0;
    }
    
    /**
     * Get approximate count of pending events
     */
    public int getPendingCount() {
        return pendingCount.get();
    }
    
    /**
     * Clear all pending events
     */
    public void clear() {
        queue.clear();
        pendingCount.set(0);
    }
}