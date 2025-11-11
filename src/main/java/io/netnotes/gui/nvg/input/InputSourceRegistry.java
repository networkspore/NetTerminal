package io.netnotes.gui.nvg.input;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * InputSourceRegistry - Singleton registry for managing input sources.
 * Provides source identity, capabilities, state, and context path navigation.
 */
public class InputSourceRegistry {
    private static final InputSourceRegistry INSTANCE = new InputSourceRegistry();
    
    private final AtomicInteger nextSourceId = new AtomicInteger(1);
    private final ConcurrentHashMap<NoteBytesReadOnly, SourceInfo> sourceById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NoteBytesReadOnly> sourceByName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NoteBytesReadOnly> sourceByPath = new ConcurrentHashMap<>();
    
    // Optional: Link to high-level state machine (e.g., CommandCenter)
    private volatile Supplier<BigInteger> appStateMachineSupplier;
    
    // Bit position allocator for coordinating with high-level state
    private final AtomicInteger nextGlobalBitPosition = new AtomicInteger(100); // Start after app states
    
    private InputSourceRegistry() {}
    
    public static InputSourceRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * Link to CommandCenter's state machine for coordination
     */
    public void linkToStateMachine(Supplier<BigInteger> stateMachineSupplier) {
        this.appStateMachineSupplier = stateMachineSupplier;
    }
    
    /**
     * Register a source with full options
     */
    public NoteBytesReadOnly registerSource(
            String name,
            InputSourceCapabilities capabilities,
            ContextPath contextPath,
            boolean allocateGlobalBit) {
        
        if (sourceByName.containsKey(name)) {
            throw new IllegalStateException("Source already registered: " + name);
        }
        
        NoteBytesReadOnly id = new NoteBytesReadOnly(nextSourceId.getAndIncrement());
        // Allocate bit position in global state if requested
        Integer globalBitPos = null;
        if (allocateGlobalBit && appStateMachineSupplier != null) {
            globalBitPos = nextGlobalBitPosition.getAndIncrement();
        }
        
        SourceInfo info = new SourceInfo(
            id,
            name,
            capabilities,
            contextPath,
            appStateMachineSupplier,
            globalBitPos
        );
        
        sourceById.put(id, info);
        sourceByName.put(name, id);
        
        if (contextPath != null) {
            sourceByPath.put(contextPath.toString(), id);
        }
        
        return id;
    }
    
    /**
     * Register a source with minimal options
     */
    public NoteBytesReadOnly registerSource(String name, InputSourceCapabilities capabilities) {
        return registerSource(name, capabilities, null, false);
    }
    
    /**
     * Register a source with context path
     */
    public NoteBytesReadOnly registerSource(String name, InputSourceCapabilities capabilities, ContextPath contextPath) {
        return registerSource(name, capabilities, contextPath, false);
    }
    
    /**
     * Unregister a source by ID
     */
    public void unregisterSource(NoteBytesReadOnly id) {
        SourceInfo info = sourceById.remove(id);
        if (info != null) {
            sourceByName.remove(info.name);
            if (info.contextPath != null) {
                sourceByPath.remove(info.contextPath.toString());
            }
        }
    }
    
    /**
     * Unregister a source by name
     */
    public void unregisterSource(String name) {
        NoteBytesReadOnly id = sourceByName.get(name);
        if (id != null) {
            unregisterSource(id);
        }
    }
    
    /**
     * Get source info by ID
     */
    public SourceInfo getSourceInfo(NoteBytesReadOnly id) {
        return sourceById.get(id);
    }
    
    /**
     * Get source info by name
     */
    public SourceInfo getSourceInfo(String name) {
        NoteBytesReadOnly id = sourceByName.get(name);
        return id != null ? sourceById.get(id) : null;
    }
    
    /**
     * Get source ID by name
     */
    public NoteBytesReadOnly getSourceId(String name) {
        return sourceByName.get(name);
    }
    
    /**
     * Get all registered sources
     */
    public Collection<SourceInfo> getAllSources() {
        return sourceById.values();
    }
    
    /**
     * Get active source IDs as Set<NoteBytes> for InputPacketReader
     */
    public boolean containsSourceId(NoteBytes sourceId) {
        return sourceById.containsKey(sourceId);
    }
    
    /**
     * Get sources by context path prefix
     */
    public List<SourceInfo> getSourcesByPath(String pathPrefix) {
        List<SourceInfo> sources = new ArrayList<>();
        for (Map.Entry<String, NoteBytesReadOnly> entry : sourceByPath.entrySet()) {
            if (entry.getKey().startsWith(pathPrefix)) {
                SourceInfo info = sourceById.get(entry.getValue());
                if (info != null) {
                    sources.add(info);
                }
            }
        }
        return sources;
    }
    
    /**
     * Get sources by capability predicate
     */
    public List<SourceInfo> getSourcesByCapability(
            java.util.function.Predicate<InputSourceCapabilities> predicate) {
        return sourceById.values().stream()
            .filter(info -> predicate.test(info.capabilities))
            .collect(Collectors.toList());
    }
    
    /**
     * Get sources by state flag
     */
    public List<SourceInfo> getSourcesByState(int bitPosition) {
        return sourceById.values().stream()
            .filter(info -> info.hasLocalFlag(bitPosition))
            .collect(Collectors.toList());
    }
    
    // State control methods
    
    /**
     * Set source state flag
     */
    public void setSourceState(NoteBytesReadOnly id, int bitPosition, boolean value) {
        SourceInfo info = sourceById.get(id);
        if (info != null) {
            info.setLocalFlag(bitPosition, value);
        }
    }
    
    /**
     * Activate a source
     */
    public void activateSource(NoteBytesReadOnly id) {
        SourceInfo info = sourceById.get(id);
        if (info != null) {
            info.activate();
        }
    }
    
    /**
     * Activate a source by name
     */
    public void activateSource(String name) {
        NoteBytesReadOnly id = sourceByName.get(name);
        if (id != null) {
            activateSource(id);
        }
    }
    
    /**
     * Pause a source
     */
    public void pauseSource(NoteBytesReadOnly id) {
        SourceInfo info = sourceById.get(id);
        if (info != null) {
            info.pause();
        }
    }
    
    /**
     * Pause a source by name
     */
    public void pauseSource(String name) {
        NoteBytesReadOnly id = sourceByName.get(name);
        if (id != null) {
            pauseSource(id);
        }
    }
    
    /**
     * Resume a source
     */
    public void resumeSource(NoteBytesReadOnly id) {
        SourceInfo info = sourceById.get(id);
        if (info != null) {
            info.resume();
        }
    }
    
    /**
     * Resume a source by name
     */
    public void resumeSource(String name) {
        NoteBytesReadOnly id = sourceByName.get(name);
        if (id != null) {
            resumeSource(id);
        }
    }
    
    /**
     * Get statistics for a source
     */
    public SourceStatistics getStatistics(NoteBytesReadOnly id) {
        SourceInfo info = sourceById.get(id);
        return info != null ? info.statistics : null;
    }
    
    /**
     * Get statistics for a source by name
     */
    public SourceStatistics getStatistics(String name) {
        NoteBytesReadOnly id = sourceByName.get(name);
        return id != null ? getStatistics(id) : null;
    }
    
    /**
     * Get registry summary
     */
    public String getSummary() {
        int total = sourceById.size();
        int active = getSourcesByState(SourceState.ACTIVE_BIT).size();
        int paused = getSourcesByState(SourceState.PAUSED_BIT).size();
        int error = getSourcesByState(SourceState.ERROR_BIT).size();
        
        return String.format("Sources: %d total, %d active, %d paused, %d error",
            total, active, paused, error);
    }
}