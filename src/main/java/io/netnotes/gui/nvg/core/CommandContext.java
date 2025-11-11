package io.netnotes.gui.nvg.core;

import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.gui.nvg.resources.FontManager;

/**
 * CommandContext - Execution context for processes
 * Provides access to shared resources and services
 */
public class CommandContext {
    private final ProcessContainer container;
    private final BitFlagStateMachine stateMachine;
    private final ContextPath contextPath;
    private final long vg; // NanoVG context
    private final FontManager fontManager;
    
    // Working directory for file operations
    private String workingDirectory = "/";
    
    public CommandContext(ProcessContainer container, 
                         BitFlagStateMachine stateMachine,
                         ContextPath contextPath,
                         long vg,
                         FontManager fontManager) {
        this.container = container;
        this.stateMachine = stateMachine;
        this.contextPath = contextPath;
        this.vg = vg;
        this.fontManager = fontManager;
    }
    
    /**
     * Get the process container
     */
    public ProcessContainer getContainer() {
        return container;
    }
    
    /**
     * Get the state machine
     */
    public BitFlagStateMachine getStateMachine() {
        return stateMachine;
    }
    
    /**
     * Get the context path
     */
    public ContextPath getContextPath() {
        return contextPath;
    }
    
    /**
     * Get NanoVG context for rendering
     */
    public long getVg() {
        return vg;
    }
    
    /**
     * Get font manager
     */
    public FontManager getFontManager() {
        return fontManager;
    }
    
    /**
     * Get working directory
     */
    public String getWorkingDirectory() {
        return workingDirectory;
    }
    
    /**
     * Set working directory
     */
    public void setWorkingDirectory(String path) {
        this.workingDirectory = path;
    }
    
    /**
     * Start a new process
     */
    public int startProcess(Process process) {
        return container.startProcess(process);
    }
    
    /**
     * Kill a process
     */
    public boolean killProcess(int pid) {
        return container.killProcess(pid);
    }
    
    /**
     * Get current foreground process
     */
    public Process getForegroundProcess() {
        return container.getProcessManager().getForegroundProcess();
    }
    
    /**
     * Create a child context (for nested processes)
     */
    public CommandContext createChildContext(ContextPath childPath) {
        return new CommandContext(
            container,
            stateMachine,
            childPath,
            vg,
            fontManager
        );
    }
}
