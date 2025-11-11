package io.netnotes.gui.nvg.core;


/**
 * Base Process class - all processes extend this
 */
public abstract class Process {
    private int pid;
    private volatile boolean alive = true;
    private volatile boolean killed = false;
    
    /**
     * Get input handler for this process
     */
    public abstract ProcessInputHandler getInputHandler();
    
    /**
     * Execute the process (runs in background thread)
     */
    public abstract void execute();
    
    /**
     * Check if process is alive
     */
    public boolean isAlive() {
        return alive;
    }
    
    /**
     * Check if process was killed
     */
    protected boolean isKilled() {
        return killed;
    }
    
    /**
     * Kill this process
     */
    public void kill() {
        killed = true;
        alive = false;
    }
    
    /**
     * Mark process as completed
     */
    protected void complete() {
        alive = false;
    }
    
    // Package-private setters
    void setPid(int pid) {
        this.pid = pid;
    }
    
    public int getPid() {
        return pid;
    }
}