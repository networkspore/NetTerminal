package io.netnotes.terminal.layout;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TerminalLayoutDataPool - Singleton pool for LayoutData and Builder instances
 * 
 * POOLING STRATEGY:
 * - Pools both TerminalLayoutData instances and TerminalLayoutDataBuilder instances
 * - Auto-grows when pool is empty (creates new instances)
 * - Auto-shrinks when pool exceeds target size (discards excess)
 * - Thread-safe singleton pattern
 * 
 * USAGE IN LAYOUT CALLBACKS:
 * <pre>
 * public TerminalLayoutData calculate(TerminalLayoutContext context) {
 *     // Get a clean builder from pool
 *     TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder();
 *     
 *     // Configure layout
 *     builder.setX(10)
 *            .setY(5)
 *            .setWidth(20)
 *            .setHeight(10)
 *            .setHidden(false);
 *     
 *     // Build returns pooled LayoutData, builder goes back to pool
 *     return builder.build();
 * }
 * 
 * // After applyLayoutData() completes, LayoutManager recycles:
 * layoutData.recycle(); // Returns to pool
 * </pre>
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
public class TerminalLayoutDataPool {
    
    // Singleton instance
    private static volatile TerminalLayoutDataPool instance;
    
    // Pool configuration
    private static final int SOFT_MAX_SIZE = 256;  // Target size for auto-shrink
    private static final int HARD_MAX_SIZE = 512;  // Absolute maximum
    private static final int SHRINK_THRESHOLD = SOFT_MAX_SIZE + 64; // When to trigger shrink
    
    // Pools
    private final ConcurrentLinkedQueue<TerminalLayoutData> dataPool = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TerminalLayoutData.TerminalLayoutDataBuilder> builderPool = new ConcurrentLinkedQueue<>();
    
    // Size tracking
    private final AtomicInteger dataPoolSize = new AtomicInteger(0);
    private final AtomicInteger builderPoolSize = new AtomicInteger(0);
    
    // Statistics (for diagnostics)
    private final AtomicInteger totalDataCreated = new AtomicInteger(0);
    private final AtomicInteger totalBuilderCreated = new AtomicInteger(0);
    private final AtomicInteger totalDataRecycled = new AtomicInteger(0);
    private final AtomicInteger totalBuilderRecycled = new AtomicInteger(0);
    
    /**
     * Private constructor for singleton
     */
    private TerminalLayoutDataPool() {
        // Warm up pool with initial instances
        warmupPool(32); // Start with 32 of each
    }
    
    /**
     * Get singleton instance
     * Thread-safe double-checked locking
     */
    public static TerminalLayoutDataPool getInstance() {
        if (instance == null) {
            synchronized (TerminalLayoutDataPool.class) {
                if (instance == null) {
                    instance = new TerminalLayoutDataPool();
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
            TerminalLayoutData data = new TerminalLayoutData();
            dataPool.offer(data);
            dataPoolSize.incrementAndGet();
            
            TerminalLayoutData.TerminalLayoutDataBuilder builder = new TerminalLayoutData.TerminalLayoutDataBuilder();
            builderPool.offer(builder);
            builderPoolSize.incrementAndGet();
        }
    }
    
    // ===== BUILDER POOL =====
    
    /**
     * Obtain a clean builder from pool
     * Creates new instance if pool is empty (auto-grow)
     * 
     * This is the entry point for layout callbacks:
     * TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder();
     * 
     * @return Clean builder ready for use
     */
    public TerminalLayoutData.TerminalLayoutDataBuilder obtainBuilder() {
        TerminalLayoutData.TerminalLayoutDataBuilder builder = builderPool.poll();
        
        if (builder == null) {
            // Auto-grow: create new instance
            builder = new TerminalLayoutData.TerminalLayoutDataBuilder();
            totalBuilderCreated.incrementAndGet();
            builder.reset();
        } else {
            builderPoolSize.decrementAndGet();
        }
        
        // Ensure clean state
       
        
        return builder;
    }
    
    /**
     * Recycle builder back to pool
     * Called automatically by builder.build()
     * 
     * @param builder Builder to recycle
     */
    public void recycleBuilder(TerminalLayoutData.TerminalLayoutDataBuilder builder) {
        if (builder == null) return;
        
        // Clean before recycling
        builder.reset();
        
        int currentSize = builderPoolSize.get();
        
        // Auto-shrink: discard if beyond hard max
        if (currentSize >= HARD_MAX_SIZE) {
            // Let GC handle it
            return;
        }
        
        // Add to pool
        if (builderPool.offer(builder)) {
            builderPoolSize.incrementAndGet();
            totalBuilderRecycled.incrementAndGet();
            
            // Trigger shrink if needed
            if (currentSize > SHRINK_THRESHOLD) {
                trimBuilderPool();
            }
        }
    }
    
    /**
     * Trim builder pool to soft max size
     * Removes excess builders to prevent unbounded growth
     */
    private void trimBuilderPool() {
        while (builderPoolSize.get() > SOFT_MAX_SIZE) {
            TerminalLayoutData.TerminalLayoutDataBuilder builder = builderPool.poll();
            if (builder == null) break;
            builderPoolSize.decrementAndGet();
            // Let GC collect the excess builder
        }
    }
    
    // ===== DATA POOL =====
    
    /**
     * Obtain a clean LayoutData from pool
     * Creates new instance if pool is empty (auto-grow)
     * Called by builder.build()
     * 
     * @return Clean LayoutData ready for initialization
     */
    public TerminalLayoutData obtainData() {
        TerminalLayoutData data = dataPool.poll();
        
        if (data == null) {
            // Auto-grow: create new instance
            data = new TerminalLayoutData();
            totalDataCreated.incrementAndGet();
        } else {
            dataPoolSize.decrementAndGet();
        }
        
        // Ensure clean state
        data.reset();
        
        return data;
    }
    
    /**
     * Recycle LayoutData back to pool
     * Called after applyLayoutData() completes
     * 
     * @param data LayoutData to recycle
     */
    public void recycleData(TerminalLayoutData data) {
        if (data == null) return;
        
        // Clean before recycling (also recycles contained TerminalRectangle)
        data.recycleRegion();
        data.reset();
        
        int currentSize = dataPoolSize.get();
        
        // Auto-shrink: discard if beyond hard max
        if (currentSize >= HARD_MAX_SIZE) {
            // Let GC handle it
            return;
        }
        
        // Add to pool
        if (dataPool.offer(data)) {
            dataPoolSize.incrementAndGet();
            totalDataRecycled.incrementAndGet();
            
            // Trigger shrink if needed
            if (currentSize > SHRINK_THRESHOLD) {
                trimDataPool();
            }
        }
    }
    
    /**
     * Trim data pool to soft max size
     * Removes excess data instances to prevent unbounded growth
     */
    private void trimDataPool() {
        while (dataPoolSize.get() > SOFT_MAX_SIZE) {
            TerminalLayoutData data = dataPool.poll();
            if (data == null) break;
            dataPoolSize.decrementAndGet();
            // Let GC collect the excess data
        }
    }
    
    // ===== DIAGNOSTICS =====
    
    /**
     * Get pool statistics for diagnostics
     */
    public PoolStats getStats() {
        return new PoolStats(
            dataPoolSize.get(),
            builderPoolSize.get(),
            totalDataCreated.get(),
            totalBuilderCreated.get(),
            totalDataRecycled.get(),
            totalBuilderRecycled.get()
        );
    }
    
    /**
     * Clear all pools (for testing/cleanup)
     * WARNING: Only call when no layout operations in progress
     */
    public void clear() {
        dataPool.clear();
        builderPool.clear();
        dataPoolSize.set(0);
        builderPoolSize.set(0);
    }
    
    /**
     * Reset statistics counters
     */
    public void resetStats() {
        totalDataCreated.set(0);
        totalBuilderCreated.set(0);
        totalDataRecycled.set(0);
        totalBuilderRecycled.set(0);
    }
    
    /**
     * Pool statistics snapshot
     */
    public static class PoolStats {
        public final int dataPoolSize;
        public final int builderPoolSize;
        public final int totalDataCreated;
        public final int totalBuilderCreated;
        public final int totalDataRecycled;
        public final int totalBuilderRecycled;
        
        PoolStats(int dataPoolSize, int builderPoolSize,
                  int totalDataCreated, int totalBuilderCreated,
                  int totalDataRecycled, int totalBuilderRecycled) {
            this.dataPoolSize = dataPoolSize;
            this.builderPoolSize = builderPoolSize;
            this.totalDataCreated = totalDataCreated;
            this.totalBuilderCreated = totalBuilderCreated;
            this.totalDataRecycled = totalDataRecycled;
            this.totalBuilderRecycled = totalBuilderRecycled;
        }
        
        @Override
        public String toString() {
            return String.format(
                "PoolStats[data=%d/%d, builder=%d/%d, dataReuse=%.1f%%, builderReuse=%.1f%%]",
                dataPoolSize, totalDataCreated,
                builderPoolSize, totalBuilderCreated,
                getReuseRate(totalDataRecycled, totalDataCreated),
                getReuseRate(totalBuilderRecycled, totalBuilderCreated)
            );
        }
        
        private double getReuseRate(int recycled, int created) {
            if (created == 0) return 0.0;
            return (recycled * 100.0) / created;
        }
    }
}