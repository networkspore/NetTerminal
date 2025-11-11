package io.netnotes.gui.nvg.core;

/**
 * Base class for child processes spawned by CommandCenter
 */
public abstract class ChildProcess extends Process {
    protected final CommandCenter shell;
    
    public ChildProcess(CommandCenter shell) {
        this.shell = shell;
    }
    
    protected void output(String message) {
        shell.output(message);
    }
    
    protected void error(String message) {
        shell.error(message);
    }
}