package io.netnotes.gui.nvg.input;

import java.math.BigInteger;

/**
 * SourceState - Bit position constants for source state flags.
 * Uses bit positions instead of enums for universal state representation
 * that can be serialized across network boundaries and coordinated with
 * high-level state machines.
 */
public final class SourceState {
    
    // Source lifecycle states (bits 0-7)
    public static final int REGISTERED_BIT = 0;    // Source exists in registry
    public static final int ACTIVE_BIT = 1;        // Source is actively producing events
    public static final int PAUSED_BIT = 2;        // Temporarily stopped by user/system
    public static final int THROTTLED_BIT = 3;     // Rate limited due to excessive events
    public static final int ERROR_BIT = 4;         // Error state, needs attention
    public static final int DISCONNECTED_BIT = 5;  // Hardware/connection lost
    public static final int INITIALIZING_BIT = 6;  // Starting up, not ready
    public static final int SHUTTING_DOWN_BIT = 7; // Graceful shutdown in progress
    
    // Source capabilities/features enabled (bits 8-15)
    public static final int MOUSE_ENABLED_BIT = 8;
    public static final int KEYBOARD_ENABLED_BIT = 9;
    public static final int TOUCH_ENABLED_BIT = 10;
    public static final int SCROLL_ENABLED_BIT = 11;
    public static final int GAMEPAD_ENABLED_BIT = 12;
    
    // Source operation modes (bits 16-23)
    public static final int HIGH_PRECISION_MODE_BIT = 16;
    public static final int RAW_INPUT_MODE_BIT = 17;
    public static final int FILTERED_MODE_BIT = 18;
    public static final int RECORDING_BIT = 19;
    public static final int PLAYBACK_BIT = 20;
    
    // Source health/diagnostics (bits 24-31)
    public static final int BUFFER_FULL_BIT = 24;
    public static final int DROPPED_EVENTS_BIT = 25;
    public static final int LATENCY_WARNING_BIT = 26;
    public static final int SYNC_ERROR_BIT = 27;
    
    private SourceState() {
        // Utility class, no instances
    }
    
    /**
     * Convert bit position to int flag
     */
    public static int flag(int bitPosition) {
        return 1 << bitPosition;
    }
    
    /**
     * Convert bit position to BigInteger bit for high-level state coordination
     */
    public static BigInteger toBigIntegerBit(int bitPosition) {
        return BigInteger.ONE.shiftLeft(bitPosition);
    }
    
    /**
     * Check if a state has a specific flag set
     */
    public static boolean hasFlag(int state, int bitPosition) {
        return (state & flag(bitPosition)) != 0;
    }
    
    /**
     * Set a flag in a state value
     */
    public static int setFlag(int state, int bitPosition) {
        return state | flag(bitPosition);
    }
    
    /**
     * Clear a flag in a state value
     */
    public static int clearFlag(int state, int bitPosition) {
        return state & ~flag(bitPosition);
    }
    
    /**
     * Get human-readable state description
     */
    public static String describe(int state) {
        StringBuilder sb = new StringBuilder();
        
        if (hasFlag(state, REGISTERED_BIT)) sb.append("REGISTERED ");
        if (hasFlag(state, ACTIVE_BIT)) sb.append("ACTIVE ");
        if (hasFlag(state, PAUSED_BIT)) sb.append("PAUSED ");
        if (hasFlag(state, THROTTLED_BIT)) sb.append("THROTTLED ");
        if (hasFlag(state, ERROR_BIT)) sb.append("ERROR ");
        if (hasFlag(state, DISCONNECTED_BIT)) sb.append("DISCONNECTED ");
        if (hasFlag(state, INITIALIZING_BIT)) sb.append("INITIALIZING ");
        if (hasFlag(state, SHUTTING_DOWN_BIT)) sb.append("SHUTTING_DOWN ");
        
        if (hasFlag(state, MOUSE_ENABLED_BIT)) sb.append("MOUSE ");
        if (hasFlag(state, KEYBOARD_ENABLED_BIT)) sb.append("KEYBOARD ");
        if (hasFlag(state, TOUCH_ENABLED_BIT)) sb.append("TOUCH ");
        if (hasFlag(state, SCROLL_ENABLED_BIT)) sb.append("SCROLL ");
        
        if (hasFlag(state, HIGH_PRECISION_MODE_BIT)) sb.append("HIGH_PREC ");
        if (hasFlag(state, RECORDING_BIT)) sb.append("RECORDING ");
        if (hasFlag(state, PLAYBACK_BIT)) sb.append("PLAYBACK ");
        
        if (hasFlag(state, BUFFER_FULL_BIT)) sb.append("BUFFER_FULL ");
        if (hasFlag(state, DROPPED_EVENTS_BIT)) sb.append("DROPPED_EVENTS ");
        if (hasFlag(state, LATENCY_WARNING_BIT)) sb.append("LATENCY_WARN ");
        if (hasFlag(state, SYNC_ERROR_BIT)) sb.append("SYNC_ERROR ");
        
        return sb.length() > 0 ? sb.toString().trim() : "NONE";
    }
}