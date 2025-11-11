package io.netnotes.gui.nvg.input;

import java.math.BigInteger;
import java.util.Objects;
import java.util.function.Supplier;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * SourceInfo - Complete metadata for an input source
 * Combines identity, capabilities, state, statistics, and context
 */
public final class SourceInfo {
    public final NoteBytesReadOnly id;
    public final String name;
    public final InputSourceCapabilities capabilities;
    public final ContextPath contextPath;
    public final SourceStatistics statistics;
    public final Integer highLevelBitPosition; // For coordination with app state machine
    
    // Local source state (independent from global app state)
    private volatile int localState;
    
    // Optional link to high-level state machine
    private final Supplier<BigInteger> appStateMachineSupplier;
    
    /**
     * Create a new SourceInfo
     */
    public SourceInfo(
            NoteBytesReadOnly id,
            String name,
            InputSourceCapabilities capabilities,
            ContextPath contextPath,
            Supplier<BigInteger> appStateMachineSupplier,
            Integer highLevelBitPosition) {
        
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities cannot be null");
        this.contextPath = contextPath; // Can be null
        this.appStateMachineSupplier = appStateMachineSupplier;
        this.highLevelBitPosition = highLevelBitPosition;
        this.statistics = new SourceStatistics();
        
        // Initialize with REGISTERED state
        this.localState = SourceState.setFlag(0, SourceState.REGISTERED_BIT);
    }
    
    // State management
    
    /**
     * Get the local state flags
     */
    public int getLocalState() {
        return localState;
    }
    
    /**
     * Check if a local state flag is set
     */
    public boolean hasLocalFlag(int bitPosition) {
        return SourceState.hasFlag(localState, bitPosition);
    }
    
    /**
     * Set a local state flag
     */
    public void setLocalFlag(int bitPosition, boolean value) {
        if (value) {
            localState = SourceState.setFlag(localState, bitPosition);
        } else {
            localState = SourceState.clearFlag(localState, bitPosition);
        }
    }
    
    /**
     * Set the entire local state
     */
    public void setLocalState(int state) {
        this.localState = state;
    }
    
    /**
     * Get the high-level state machine state (if linked)
     */
    public BigInteger getHighLevelState() {
        if (appStateMachineSupplier != null) {
            return appStateMachineSupplier.get();
        }
        return null;
    }
    
    /**
     * Check if the high-level state bit is set (if allocated)
     */
    public boolean isHighLevelBitSet() {
        if (highLevelBitPosition != null && appStateMachineSupplier != null) {
            BigInteger state = appStateMachineSupplier.get();
            if (state != null) {
                return state.testBit(highLevelBitPosition);
            }
        }
        return false;
    }
    
    /**
     * Get human-readable state description
     */
    public String getStateDescription() {
        return SourceState.describe(localState);
    }
    
    // Convenience methods for common state transitions
    
    /**
     * Activate this source
     */
    public void activate() {
        setLocalFlag(SourceState.ACTIVE_BIT, true);
        setLocalFlag(SourceState.PAUSED_BIT, false);
        setLocalFlag(SourceState.ERROR_BIT, false);
        setLocalFlag(SourceState.INITIALIZING_BIT, false);
    }
    
    /**
     * Pause this source
     */
    public void pause() {
        setLocalFlag(SourceState.ACTIVE_BIT, false);
        setLocalFlag(SourceState.PAUSED_BIT, true);
    }
    
    /**
     * Resume this source
     */
    public void resume() {
        setLocalFlag(SourceState.PAUSED_BIT, false);
        setLocalFlag(SourceState.ACTIVE_BIT, true);
    }
    
    /**
     * Mark this source as having an error
     */
    public void setError(boolean hasError) {
        setLocalFlag(SourceState.ERROR_BIT, hasError);
        if (hasError) {
            setLocalFlag(SourceState.ACTIVE_BIT, false);
        }
    }
    
    /**
     * Mark this source as disconnected
     */
    public void setDisconnected(boolean disconnected) {
        setLocalFlag(SourceState.DISCONNECTED_BIT, disconnected);
        if (disconnected) {
            setLocalFlag(SourceState.ACTIVE_BIT, false);
        }
    }
    
    /**
     * Mark this source as initializing
     */
    public void setInitializing(boolean initializing) {
        setLocalFlag(SourceState.INITIALIZING_BIT, initializing);
        if (initializing) {
            setLocalFlag(SourceState.ACTIVE_BIT, false);
        }
    }
    
    /**
     * Mark this source as shutting down
     */
    public void setShuttingDown(boolean shuttingDown) {
        setLocalFlag(SourceState.SHUTTING_DOWN_BIT, shuttingDown);
        if (shuttingDown) {
            setLocalFlag(SourceState.ACTIVE_BIT, false);
        }
    }
    
    /**
     * Enable/disable throttling
     */
    public void setThrottled(boolean throttled) {
        setLocalFlag(SourceState.THROTTLED_BIT, throttled);
    }
    
    /**
     * Check if source is in an operational state (not error, not disconnected, not shutting down)
     */
    public boolean isOperational() {
        return !hasLocalFlag(SourceState.ERROR_BIT) &&
               !hasLocalFlag(SourceState.DISCONNECTED_BIT) &&
               !hasLocalFlag(SourceState.SHUTTING_DOWN_BIT);
    }
    
    /**
     * Check if source can produce events
     */
    public boolean canProduceEvents() {
        return hasLocalFlag(SourceState.ACTIVE_BIT) &&
               !hasLocalFlag(SourceState.PAUSED_BIT) &&
               isOperational();
    }
    
    // Context path queries
    
    /**
     * Check if this source is under a given context path
     */
    public boolean isUnderPath(String pathPrefix) {
        if (contextPath == null) {
            return false;
        }
        return contextPath.startsWith(pathPrefix);
    }
    
    /**
     * Check if this source is under a given context path
     */
    public boolean isUnderPath(ContextPath path) {
        if (contextPath == null) {
            return false;
        }
        return contextPath.startsWith(path);
    }
    
    /**
     * Get the relative path from a base path
     */
    public ContextPath getRelativePath(ContextPath basePath) {
        if (contextPath == null) {
            return null;
        }
        return basePath.relativize(contextPath);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SourceInfo{");
        sb.append("id=").append(id);
        sb.append(", name='").append(name).append('\'');
        sb.append(", state=").append(getStateDescription());
        if (contextPath != null) {
            sb.append(", path=").append(contextPath);
        }
        sb.append(", capabilities=").append(capabilities.name);
        sb.append('}');
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SourceInfo)) return false;
        SourceInfo other = (SourceInfo) obj;
        return id.equals(other.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}