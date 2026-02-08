package io.netnotes.terminal.layout;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TerminalLayoutContextPool - Singleton pool for LayoutContext instances
 * 
 * POOLING STRATEGY:
 * - Singleton pattern (shared across all TerminalLayoutManagers)
 * - Auto-grows when pool is empty (creates new instances)
 * - Auto-shrinks when pool exceeds target size (discards excess)
 * - Thread-safe concurrent access
 * 
 * LIFECYCLE:
 * 1. LayoutManager obtains context from pool
 * 2. Context initialized with LayoutNode
 * 3. Context used during layout calculation
 * 4. LayoutManager recycles context back to pool
 * 
 * AUTO-SIZING:
 * - Soft max: 256 instances (default)
 * - Hard max: 512 instances (prevents unbounded growth)
 * - Auto-shrink: Trims to soft max when exceeded
 * - Grow on demand: Creates new when pool empty
 * 
 * THREAD SAFETY:
 * - ConcurrentLinkedQueue for lock-free access
 * - AtomicInteger for pool size tracking
 * - Safe concurrent access from multiple threads
 */
public class TerminalLayoutContextPool {
    
    // Singleton instance
    private static volatile TerminalLayoutContextPool instance;
    
    // Pool configuration
    private static final int SOFT_MAX_SIZE = 256;  // Target size for auto-shrink
    private static final int HARD_MAX_SIZE = 512;  // Absolute maximum
    private static final int SHRINK_THRESHOLD = SOFT_MAX_SIZE + 64; // When to trigger shrink
    
    // Pool
    private final ConcurrentLinkedQueue<TerminalLayoutContext> pool = new ConcurrentLinkedQueue<>();
    
    // Size tracking
    private final AtomicInteger poolSize = new AtomicInteger(0);
    
    // Statistics (for diagnostics)
    private final AtomicInteger totalCreated = new AtomicInteger(0);
    private final AtomicInteger totalRecycled = new AtomicInteger(0);
    
    /**
     * Private constructor for singleton
     */
    private TerminalLayoutContextPool() {
        // Warm up pool with initial instances
        warmupPool(32);
    }
    
    /**
     * Get singleton instance
     * Thread-safe double-checked locking
     */
    public static TerminalLayoutContextPool getInstance() {
        if (instance == null) {
            synchronized (TerminalLayoutContextPool.class) {
                if (instance == null) {
                    instance = new TerminalLayoutContextPool();
                }
            }
        }
        return instance;
    }
    
    /**
     * Warm up pool with initial instances
     * Called during initialization to avoid first-use allocation spike
     */
    private void warmupPool(int count) {
        for (int i = 0; i < count; i++) {
            TerminalLayoutContext context = new TerminalLayoutContext();
            pool.offer(context);
            poolSize.incrementAndGet();
        }
    }
    
    /**
     * Obtain a LayoutContext from pool
     * Creates new instance if pool is empty (auto-grow)
     * 
     * @return Clean LayoutContext instance ready for initialization
     */
    public TerminalLayoutContext obtain() {
        TerminalLayoutContext context = pool.poll();
        
        if (context == null) {
            // Auto-grow: create new instance
            context = new TerminalLayoutContext();
            totalCreated.incrementAndGet();
        } else {
            poolSize.decrementAndGet();
        }
        
        // Ensure clean state (reset happens in recycle, but be safe)
        context.reset();
        
        return context;
    }
    
    /**
     * Recycle LayoutContext back to pool
     * Called after layout calculation completes
     * 
     * @param context Context to recycle
     */
    public void recycle(TerminalLayoutContext context) {
        if (context == null) return;
        
        // Clean before recycling
        context.reset();
        
        int currentSize = poolSize.get();
        
        // Auto-shrink: discard if beyond hard max
        if (currentSize >= HARD_MAX_SIZE) {
            // Let GC handle it
            return;
        }
        
        // Add to pool
        if (pool.offer(context)) {
            poolSize.incrementAndGet();
            totalRecycled.incrementAndGet();
            
            // Trigger shrink if needed
            if (currentSize > SHRINK_THRESHOLD) {
                trimPool();
            }
        }
    }
    
    /**
     * Trim pool to soft max size
     * Removes excess contexts to prevent unbounded growth
     */
    private void trimPool() {
        while (poolSize.get() > SOFT_MAX_SIZE) {
            TerminalLayoutContext context = pool.poll();
            if (context == null) break;
            poolSize.decrementAndGet();
            // Let GC collect the excess context
        }
    }
    
    /**
     * Get pool statistics for diagnostics
     */
    public PoolStats getStats() {
        return new PoolStats(
            poolSize.get(),
            totalCreated.get(),
            totalRecycled.get()
        );
    }
    
    /**
     * Clear the pool (for testing/cleanup)
     * WARNING: Only call when no layout operations in progress
     */
    public void clear() {
        pool.clear();
        poolSize.set(0);
    }
    
    /**
     * Reset statistics counters
     */
    public void resetStats() {
        totalCreated.set(0);
        totalRecycled.set(0);
    }
    
    /**
     * Pool statistics snapshot
     */
    public static class PoolStats {
        public final int poolSize;
        public final int totalCreated;
        public final int totalRecycled;
        
        PoolStats(int poolSize, int totalCreated, int totalRecycled) {
            this.poolSize = poolSize;
            this.totalCreated = totalCreated;
            this.totalRecycled = totalRecycled;
        }
        
        @Override
        public String toString() {
            return String.format(
                "ContextPool[size=%d/%d, reuse=%.1f%%]",
                poolSize, totalCreated,
                getReuseRate()
            );
        }
        
        private double getReuseRate() {
            if (totalCreated == 0) return 0.0;
            return (totalRecycled * 100.0) / totalCreated;
        }
    }
}