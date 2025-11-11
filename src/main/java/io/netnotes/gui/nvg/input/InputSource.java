package io.netnotes.gui.nvg.input;

/**
 * Input source with paired event parser.
 * Each source knows how to parse its own binary event format.
 */
public interface InputSource {
    
    /**
     * Start emitting events to the output stream
     */
    void start();
    
    /**
     * Stop emitting events
     */
    void stop();
    
    
    /**
     * Get source capabilities
     */
    InputSourceCapabilities getCapabilities();
    
    /**
     * Get unique source ID
     */
    int getSourceId();
}