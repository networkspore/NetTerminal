package io.netnotes.gui.nvg.core;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RawEvent;

/**
 * ProcessInputRouter - Routes input to the appropriate process
 * 
 * This is different from InputRouter (context-based routing) - this
 * routes events to running processes based on foreground/background state.
 */
class ProcessInputRouter {
    private final ProcessManager processManager;
    
    public ProcessInputRouter(ProcessManager processManager) {
        this.processManager = processManager;
    }
    
    /**
     * Route input event to the foreground process
     * 
     * @param event The input event
     * @param sourcePath The context path where input originated
     * @return true if event was handled
     */
    public boolean routeInput(RawEvent event, ContextPath sourcePath) {
        Process fg = processManager.getForegroundProcess();
        if (fg == null) return false;
        
        ProcessInputHandler handler = fg.getInputHandler();
        if (handler == null) return false;
        
        InputMode mode = handler.getInputMode();
        
        switch (mode) {
            case SECURE:
                // Secure input bypasses this router entirely
                // (handled by SecureInputClient directly)
                return false;
                
            case RAW_EVENTS:
            case DIRECT_GLFW:
                handler.handleRawEvent(event);
                return true;
                
            case FILTERED:
                // In filtered mode, processes handle raw events and
                // do their own filtering/line editing
                handler.handleRawEvent(event);
                return true;
        }
        
        return false;
    }
}