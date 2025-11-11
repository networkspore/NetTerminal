package io.netnotes.gui.nvg.input;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.netnotes.engine.state.BitFlagStateMachine;

/**
 * InputRouter - Routes input events to handlers based on ContextPath
 * 
 * Features:
 * - Hierarchical routing (events can bubble up from leaf to root)
 * - Focus management (focused context gets priority)
 * - Multi-modal support (multiple contexts active simultaneously)
 * - Event filtering and transformation
 * 
 * Thread-safe for registration and routing.
 */
public class InputRouter {
    
    // Routing state flags
    public static final int ROUTING_ACTIVE = 0;
    public static final int CAPTURE_MODE = 1;     // Single handler captures all
    public static final int BUBBLE_MODE = 2;      // Events bubble through hierarchy
    public static final int RECORDING = 3;        // Record all routed events
    public static final int REPLAYING = 4;        // Replay recorded session
    
    // Map of context paths to handler lists (multiple handlers per path)
    private final Map<ContextPath, List<InputHandler>> handlers;
    
    // Current focus path (gets first shot at events)
    private volatile ContextPath focusPath;
    
    // Active contexts (can receive events even when not focused)
    private final Set<ContextPath> activeContexts;
    
    // State machine for routing decisions
    private final BitFlagStateMachine routingState;
    
    // Global handlers (receive all events regardless of context)
    private final List<InputHandler> globalHandlers;
    
    public InputRouter() {
        this.handlers = new ConcurrentHashMap<>();
        this.activeContexts = ConcurrentHashMap.newKeySet();
        this.globalHandlers = new CopyOnWriteArrayList<>();
        this.routingState = new BitFlagStateMachine("InputRouter");
        
        // Start in bubble mode
        routingState.setFlag(ROUTING_ACTIVE);
        routingState.setFlag(BUBBLE_MODE);
    }
    
    /**
     * Register a handler at a specific context path
     * Multiple handlers can be registered at the same path
     */
    public void registerHandler(ContextPath path, InputHandler handler) {
        if (path == null || handler == null) {
            throw new IllegalArgumentException("Path and handler cannot be null");
        }
        
        handlers.computeIfAbsent(path, k -> new CopyOnWriteArrayList<>())
                .add(handler);
    }
    
    /**
     * Unregister a handler from a context path
     */
    public void unregisterHandler(ContextPath path, InputHandler handler) {
        List<InputHandler> pathHandlers = handlers.get(path);
        if (pathHandlers != null) {
            pathHandlers.remove(handler);
            if (pathHandlers.isEmpty()) {
                handlers.remove(path);
            }
        }
    }
    
    /**
     * Register a global handler (receives all events)
     */
    public void registerGlobalHandler(InputHandler handler) {
        globalHandlers.add(handler);
    }
    
    /**
     * Unregister a global handler
     */
    public void unregisterGlobalHandler(InputHandler handler) {
        globalHandlers.remove(handler);
    }
    
    /**
     * Route an event through the context hierarchy
     * 
     * Routing priority:
     * 1. Global handlers (if any)
     * 2. Focused context (if set)
     * 3. Active contexts
     * 4. Parent contexts (bubble up)
     */
    public void routeEvent(RawEvent event) {
        if (!routingState.hasFlag(ROUTING_ACTIVE)) {
            return; // Router is disabled
        }
        
        // Try global handlers first
        if (!globalHandlers.isEmpty()) {
            for (InputHandler handler : globalHandlers) {
                try {
                    if (handler.handleInput(event, ContextPath.ROOT)) {
                        return; // Event consumed by global handler
                    }
                } catch (Exception e) {
                    System.err.println("Error in global handler: " + e.getMessage());
                }
            }
        }
        
        // Try focused context if set
        if (focusPath != null) {
            if (routeToContext(event, focusPath)) {
                return; // Event consumed by focused context
            }
        }
        
        // Try active contexts in priority order
        List<ContextPath> sortedContexts = getSortedActiveContexts();
        for (ContextPath context : sortedContexts) {
            if (context.equals(focusPath)) {
                continue; // Already tried focused context
            }
            
            if (routeToContext(event, context)) {
                return; // Event consumed
            }
        }
        
        // If bubble mode is enabled and we have a focused context, try parents
        if (routingState.hasFlag(BUBBLE_MODE) && focusPath != null) {
            ContextPath parent = focusPath.parent();
            while (parent != null) {
                if (routeToContext(event, parent)) {
                    return; // Event consumed by parent
                }
                parent = parent.parent();
            }
        }
        
        // Event not consumed - log for debugging
        // System.out.println("Event not consumed: " + event);
    }
    
    /**
     * Route event to a specific context
     * @return true if event was consumed
     */
    private boolean routeToContext(RawEvent event, ContextPath context) {
        List<InputHandler> pathHandlers = handlers.get(context);
        if (pathHandlers == null || pathHandlers.isEmpty()) {
            return false;
        }
        
        for (InputHandler handler : pathHandlers) {
            try {
                if (handler.handleInput(event, context)) {
                    return true; // Event consumed
                }
            } catch (Exception e) {
                System.err.println("Error in handler at " + context + ": " + e.getMessage());
            }
        }
        
        return false;
    }
    
    /**
     * Get active contexts sorted by depth (deepest first)
     */
    private List<ContextPath> getSortedActiveContexts() {
        List<ContextPath> contexts = new ArrayList<>(activeContexts);
        contexts.sort((a, b) -> Integer.compare(b.depth(), a.depth()));
        return contexts;
    }
    
    /**
     * Set focus to a specific context (gets priority for events)
     */
    public void setFocus(ContextPath path) {
        this.focusPath = path;
        if (path != null) {
            activateContext(path); // Focused context is automatically active
        }
    }
    
    /**
     * Get the currently focused context
     */
    public ContextPath getFocus() {
        return focusPath;
    }
    
    /**
     * Clear focus (no context has priority)
     */
    public void clearFocus() {
        this.focusPath = null;
    }
    
    /**
     * Activate a context (can receive events even when not focused)
     */
    public void activateContext(ContextPath path) {
        if (path != null) {
            activeContexts.add(path);
        }
    }
    
    /**
     * Deactivate a context (stops receiving events)
     */
    public void deactivateContext(ContextPath path) {
        activeContexts.remove(path);
        if (path != null && path.equals(focusPath)) {
            focusPath = null; // Clear focus if deactivating focused context
        }
    }
    
    /**
     * Check if a context is active
     */
    public boolean isContextActive(ContextPath path) {
        return activeContexts.contains(path);
    }
    
    /**
     * Get all active contexts
     */
    public Set<ContextPath> getActiveContexts() {
        return new HashSet<>(activeContexts);
    }
    
    /**
     * Get all registered context paths
     */
    public Set<ContextPath> getRegisteredContexts() {
        return new HashSet<>(handlers.keySet());
    }
    
    /**
     * Enable/disable capture mode (single focused handler captures all)
     */
    public void setCaptureMode(boolean enabled) {
        if (enabled) {
            routingState.setFlag(CAPTURE_MODE);
            routingState.clearFlag(BUBBLE_MODE);
        } else {
            routingState.clearFlag(CAPTURE_MODE);
        }
    }
    
    /**
     * Enable/disable bubble mode (events bubble up hierarchy)
     */
    public void setBubbleMode(boolean enabled) {
        if (enabled) {
            routingState.setFlag(BUBBLE_MODE);
            routingState.clearFlag(CAPTURE_MODE);
        } else {
            routingState.clearFlag(BUBBLE_MODE);
        }
    }
    
    /**
     * Enable/disable the entire router
     */
    public void setActive(boolean active) {
        if (active) {
            routingState.setFlag(ROUTING_ACTIVE);
        } else {
            routingState.clearFlag(ROUTING_ACTIVE);
        }
    }
    
    /**
     * Get routing statistics
     */
    public RouterStats getStats() {
        return new RouterStats(
            handlers.size(),
            activeContexts.size(),
            focusPath != null ? focusPath.toString() : "none",
            globalHandlers.size()
        );
    }
    
    /**
     * Routing statistics
     */
    public record RouterStats(
        int registeredContexts,
        int activeContexts,
        String focusedContext,
        int globalHandlers
    ) {
        @Override
        public String toString() {
            return String.format("Router[contexts=%d, active=%d, focus=%s, global=%d]",
                registeredContexts, activeContexts, focusedContext, globalHandlers);
        }
    }
    
    /**
     * Clear all handlers and reset state
     */
    public void reset() {
        handlers.clear();
        activeContexts.clear();
        globalHandlers.clear();
        focusPath = null;
        routingState.clearAllStates();
        routingState.setFlag(ROUTING_ACTIVE);
        routingState.setFlag(BUBBLE_MODE);
    }
}