package io.netnotes.gui.fx.uiNode.input;


/**
 * Parser for source-specific binary events.
 * Each InputSource provides its own parser.
 */
public interface InputPacketDecoder {
    
    /**
     * Get the packet size this parser expects
     */
    int getPacketSize();
    
    /**
     * Check if this parser can handle the given packet
     */
    boolean canParse(byte[] packet);
}