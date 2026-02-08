package io.netnotes.terminal;
import io.netnotes.engine.ui.ThreadLocalSpatialRegionPool;

/**
 * Thread-local pool for TerminalRectangle objects
 * 
 * Provides efficient allocation-free region management for 2D terminal rendering.
 * Each thread maintains its own pool with adaptive sizing.
 */
public class TerminalRectanglePool extends ThreadLocalSpatialRegionPool<TerminalRectangle> {
    
    // Singleton instance for shared use across all terminal renderables
    private static final TerminalRectanglePool INSTANCE = new TerminalRectanglePool();
    
    /**
     * Get the shared pool instance
     */
    public static TerminalRectanglePool getInstance() {
        return INSTANCE;
    }
    
    /**
     * Private constructor - use getInstance() for shared pool
     */
    private TerminalRectanglePool() {
        super();
    }
    
    /**
     * Create a new pool instance (for custom pools)
     */
    public static TerminalRectanglePool createPool() {
        return new TerminalRectanglePool();
    }
    
    @Override
    protected TerminalRectangle createNew() {
        return new TerminalRectangle(0, 0, 0, 0);
    }
}