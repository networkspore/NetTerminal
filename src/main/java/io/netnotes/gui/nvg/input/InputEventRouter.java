package io.netnotes.gui.nvg.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.gui.nvg.input.events.InputRecord;

/**
 * InputEventRouter - Routes events to appropriate handlers based on:
 * - Event type (mouse, keyboard, etc.)
 * - Source ID
 * - Context path
 * - Custom predicates
 */
public class InputEventRouter {
    private final Map<NoteBytesReadOnly, List<InputEventHandler>> m_handlersByType;
    private final Map<NoteBytesReadOnly, List<InputEventHandler>> m_handlersBySource;
    private final Map<String, List<InputEventHandler>> m_handlersByPath;
    private final List<ConditionalHandler> m_conditionalHandlers;
    private final List<InputEventHandler> m_globalHandlers;
    private final InputSourceRegistry m_registry;
    
    // Statistics
    private long m_eventsRouted = 0;
    private long m_handlersInvoked = 0;
    private long m_routingErrors = 0;
    
    public InputEventRouter() {
        this.m_handlersByType = new ConcurrentHashMap<>();
        this.m_handlersBySource = new ConcurrentHashMap<>();
        this.m_handlersByPath = new ConcurrentHashMap<>();
        this.m_conditionalHandlers = new ArrayList<>();
        this.m_globalHandlers = new ArrayList<>();
        this.m_registry = InputSourceRegistry.getInstance();
    }
    
    /**
     * Register a handler for a specific event type
     */
    public void registerHandler(NoteBytesReadOnly eventType, InputEventHandler handler) {
        m_handlersByType.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
    }
    
    /**
     * Register a handler for a specific source
     */
    public void registerHandlerForSource(NoteBytesReadOnly sourceId, InputEventHandler handler) {
        m_handlersBySource.computeIfAbsent(sourceId, k -> new ArrayList<>()).add(handler);
    }
    
    /**
     * Register a handler for a context path (will match all sources under that path)
     */
    public void registerHandlerForPath(String contextPath, InputEventHandler handler) {
        m_handlersByPath.computeIfAbsent(contextPath, k -> new ArrayList<>()).add(handler);
    }
    
    /**
     * Register a conditional handler (evaluated for every event)
     */
    public void registerConditionalHandler(Predicate<InputRecord> condition, InputEventHandler handler) {
        m_conditionalHandlers.add(new ConditionalHandler(condition, handler));
    }
    
    /**
     * Register a global handler (receives all events)
     */
    public void registerGlobalHandler(InputEventHandler handler) {
        m_globalHandlers.add(handler);
    }
    
    /**
     * Route an InputRecord to appropriate handlers
     */
    public void routeEvent(InputRecord event) {
        if (event == null) {
            m_routingErrors++;
            return;
        }
        
        m_eventsRouted++;
        
        try {
            NoteBytesReadOnly sourceId = event.sourceId();
            NoteBytesReadOnly eventType = event.type();
            
            // Get source info for context path routing
            SourceInfo sourceInfo = m_registry.getSourceInfo(sourceId);
            
            // Route to type-specific handlers
            List<InputEventHandler> typeHandlers = m_handlersByType.get(eventType);
            if (typeHandlers != null) {
                for (InputEventHandler handler : typeHandlers) {
                    invokeHandler(handler, event);
                }
            }
            
            // Route to source-specific handlers
            List<InputEventHandler> sourceHandlers = m_handlersBySource.get(sourceId);
            if (sourceHandlers != null) {
                for (InputEventHandler handler : sourceHandlers) {
                    invokeHandler(handler, event);
                }
            }
            
            // Route to context path handlers
            if (sourceInfo != null && sourceInfo.contextPath != null) {
                routeByContextPath(sourceInfo.contextPath.toString(), event);
            }
            
            // Route to conditional handlers
            for (ConditionalHandler conditional : m_conditionalHandlers) {
                if (conditional.condition.test(event)) {
                    invokeHandler(conditional.handler, event);
                }
            }
            
            // Route to global handlers
            for (InputEventHandler handler : m_globalHandlers) {
                invokeHandler(handler, event);
            }
            
        } catch (Exception e) {
            m_routingErrors++;
            System.err.println("InputEventRouter: Error routing event: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Route an event from raw bytes (for backward compatibility)
     */
    public void routeEvent(byte[] eventBytes) {
        try {
            // Parse the packet: [sourceId][body]
            // Body contains: [eventType][timestamp][stateFlags][payload]
            
            NoteBytesReadOnly sourceId  = NoteBytesReadOnly.readNote(eventBytes, 0);

            if (sourceId.byteLength() < 4) {
                m_routingErrors++;
                System.err.println("InputEventRouter: Invalid packet structure");
                return;
            }

            NoteBytesReadOnly body = NoteBytesReadOnly.readNote(eventBytes, 9);
            
            // Create InputRecord from the components
            InputRecord event = InputRecord.fromNoteBytes(sourceId, body);
            
            // Route the structured event
            routeEvent(event);
            
        } catch (Exception e) {
            m_routingErrors++;
            System.err.println("InputEventRouter: Error parsing event bytes: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Route to handlers registered for context paths
     * Matches the most specific path first
     */
    private void routeByContextPath(String eventPath, InputRecord event) {
        // Sort paths by specificity (longer = more specific)
        List<String> matchingPaths = new ArrayList<>();
        for (String registeredPath : m_handlersByPath.keySet()) {
            if (eventPath.startsWith(registeredPath)) {
                matchingPaths.add(registeredPath);
            }
        }
        
        // Sort by length descending (most specific first)
        matchingPaths.sort((a, b) -> Integer.compare(b.length(), a.length()));
        
        // Invoke handlers in order of specificity
        for (String path : matchingPaths) {
            List<InputEventHandler> handlers = m_handlersByPath.get(path);
            if (handlers != null) {
                for (InputEventHandler handler : handlers) {
                    invokeHandler(handler, event);
                }
            }
        }
    }
    
    /**
     * Safely invoke a handler with error handling
     */
    private void invokeHandler(InputEventHandler handler, InputRecord event) {
        try {
            handler.handleEvent(event);
            m_handlersInvoked++;
        } catch (Exception e) {
            m_routingErrors++;
            System.err.println("InputEventRouter: Handler threw exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Remove a handler from all registrations
     */
    public void unregisterHandler(InputEventHandler handler) {
        m_handlersByType.values().forEach(list -> list.remove(handler));
        m_handlersBySource.values().forEach(list -> list.remove(handler));
        m_handlersByPath.values().forEach(list -> list.remove(handler));
        m_conditionalHandlers.removeIf(ch -> ch.handler == handler);
        m_globalHandlers.remove(handler);
    }
    
    /**
     * Remove all handlers for a specific event type
     */
    public void clearHandlersForType(NoteBytesReadOnly eventType) {
        m_handlersByType.remove(eventType);
    }
    
    /**
     * Remove all handlers for a specific source
     */
    public void clearHandlersForSource(NoteBytesReadOnly sourceId) {
        m_handlersBySource.remove(sourceId);
    }
    
    /**
     * Remove all handlers for a context path
     */
    public void clearHandlersForPath(String contextPath) {
        m_handlersByPath.remove(contextPath);
    }
    
    /**
     * Clear all handlers
     */
    public void clearAllHandlers() {
        m_handlersByType.clear();
        m_handlersBySource.clear();
        m_handlersByPath.clear();
        m_conditionalHandlers.clear();
        m_globalHandlers.clear();
    }
    
    /**
     * Get routing statistics
     */
    public RouterStats getStats() {
        return new RouterStats(
            m_eventsRouted,
            m_handlersInvoked,
            m_routingErrors,
            m_handlersByType.size(),
            m_handlersBySource.size(),
            m_handlersByPath.size(),
            m_conditionalHandlers.size(),
            m_globalHandlers.size()
        );
    }
    
    /**
     * Reset statistics
     */
    public void resetStats() {
        m_eventsRouted = 0;
        m_handlersInvoked = 0;
        m_routingErrors = 0;
    }
    
    /**
     * Handler interface for processing input events
     */
    @FunctionalInterface
    public interface InputEventHandler {
        void handleEvent(InputRecord event);
    }
    
    /**
     * Conditional handler wrapper
     */
    private static class ConditionalHandler {
        final Predicate<InputRecord> condition;
        final InputEventHandler handler;
        
        ConditionalHandler(Predicate<InputRecord> condition, InputEventHandler handler) {
            this.condition = condition;
            this.handler = handler;
        }
    }
    
    /**
     * Router statistics
     */
    public record RouterStats(
        long eventsRouted,
        long handlersInvoked,
        long routingErrors,
        int typeHandlerCount,
        int sourceHandlerCount,
        int pathHandlerCount,
        int conditionalHandlerCount,
        int globalHandlerCount
    ) {
        public double averageHandlersPerEvent() {
            return eventsRouted > 0 ? (double) handlersInvoked / eventsRouted : 0.0;
        }
        
        public double errorRate() {
            return eventsRouted > 0 ? (double) routingErrors / eventsRouted : 0.0;
        }
    }
}