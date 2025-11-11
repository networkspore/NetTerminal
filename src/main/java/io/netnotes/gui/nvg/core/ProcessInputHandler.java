package io.netnotes.gui.nvg.core;

import io.netnotes.engine.io.RawEvent;

/**
 * Input handler interface for processes
 */
interface ProcessInputHandler {
    /** What level of input access does this process need? */
    InputMode getInputMode();
    
    /** Handle filtered input (complete line) - only called if mode == FILTERED */
    default void handleLine(String line) {}
    
    /** Handle raw GLFW events - called if mode == RAW_EVENTS or DIRECT_GLFW */
    default void handleRawEvent(RawEvent event) {}

}
