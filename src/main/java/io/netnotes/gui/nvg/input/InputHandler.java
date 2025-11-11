package io.netnotes.gui.nvg.input;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * InputHandler - Handles input events at a specific context
 * 
 * Functional interface for processing input events.
 * Handlers are registered with InputRouter at specific ContextPaths.
 * 
 * Example handlers:
 * - CommandCenter: processes keyboard input for commands
 * - Canvas: processes mouse/keyboard for drawing
 * - Node UI: processes interaction with nodes
 * - Network terminal: processes remote input
 */
@FunctionalInterface
public interface InputHandler {
    
    /**
     * Handle an input event
     * 
     * @param event The raw input event to process
     * @param sourcePath The context path where this handler is registered
     * @return true if the event was consumed (stop propagation),
     *         false to allow the event to continue bubbling
     * 
     * @throws Exception if processing fails (router will catch and log)
     */
    boolean handleInput(RawEvent event, ContextPath sourcePath) throws Exception;
    
    /**
     * Create a handler that always consumes events
     */
    static InputHandler consuming(InputHandler delegate) {
        return (event, path) -> {
            delegate.handleInput(event, path);
            return true; // Always consume
        };
    }
    
    /**
     * Create a handler that never consumes events (pass-through)
     */
    static InputHandler passthrough(InputHandler delegate) {
        return (event, path) -> {
            delegate.handleInput(event, path);
            return false; // Never consume
        };
    }
    
    /**
     * Create a handler that filters events by type
     */
    static InputHandler filtered(NoteBytesReadOnly typeFilter, 
                                InputHandler delegate) {
        return (event, path) -> {
            if (event.getType().equals(typeFilter)) {
                return delegate.handleInput(event, path);
            }
            return false; // Pass through non-matching events
        };
    }
    
    /**
     * Chain multiple handlers (first to consume wins)
     */
    static InputHandler chain(InputHandler... handlers) {
        return (event, path) -> {
            for (InputHandler handler : handlers) {
                if (handler.handleInput(event, path)) {
                    return true; // First handler to consume stops the chain
                }
            }
            return false; // None consumed
        };
    }
    
    /**
     * Create a conditional handler
     */
    static InputHandler conditional(
            java.util.function.Predicate<RawEvent> condition,
            InputHandler thenHandler,
            InputHandler elseHandler) {
        return (event, path) -> {
            if (condition.test(event)) {
                return thenHandler.handleInput(event, path);
            } else if (elseHandler != null) {
                return elseHandler.handleInput(event, path);
            }
            return false;
        };
    }
    
    /**
     * Create a logging handler (wraps another handler)
     */
    static InputHandler logging(String name, InputHandler delegate) {
        return (event, path) -> {
            System.out.println("[" + name + "] Handling: " + event + " at " + path);
            boolean consumed = delegate.handleInput(event, path);
            System.out.println("[" + name + "] " + (consumed ? "Consumed" : "Passed through"));
            return consumed;
        };
    }
}