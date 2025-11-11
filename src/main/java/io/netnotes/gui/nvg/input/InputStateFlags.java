package io.netnotes.gui.nvg.input;

/**
 * InputStateFlags - Bit flag definitions for input state management
 * 
 * These flags integrate with BitFlagStateMachine to track input state.
 * Organized into functional groups with clear bit ranges.
 */
public class InputStateFlags {
    
    // ========== MODIFIER KEYS (bits 0-7) ==========
    public static final long MOD_SHIFT       = 1L << 0;  // 0x0000_0000_0000_0001
    public static final long MOD_CONTROL     = 1L << 1;  // 0x0000_0000_0000_0002
    public static final long MOD_ALT         = 1L << 2;  // 0x0000_0000_0000_0004
    public static final long MOD_SUPER       = 1L << 3;  // 0x0000_0000_0000_0008
    public static final long MOD_CAPS_LOCK   = 1L << 4;  // 0x0000_0000_0000_0010
    public static final long MOD_NUM_LOCK    = 1L << 5;  // 0x0000_0000_0000_0020
    public static final long MOD_SCROLL_LOCK = 1L << 6;  // 0x0000_0000_0000_0040
    
    // Modifier mask
    public static final long MOD_MASK = 0x0000_0000_0000_00FFL;
    
    // ========== MOUSE BUTTONS (bits 8-15) ==========
    public static final long MOUSE_BUTTON_1  = 1L << 8;   // 0x0000_0000_0000_0100 (Left)
    public static final long MOUSE_BUTTON_2  = 1L << 9;   // 0x0000_0000_0000_0200 (Right)
    public static final long MOUSE_BUTTON_3  = 1L << 10;  // 0x0000_0000_0000_0400 (Middle)
    public static final long MOUSE_BUTTON_4  = 1L << 11;  // 0x0000_0000_0000_0800
    public static final long MOUSE_BUTTON_5  = 1L << 12;  // 0x0000_0000_0000_1000
    public static final long MOUSE_BUTTON_6  = 1L << 13;  // 0x0000_0000_0000_2000
    public static final long MOUSE_BUTTON_7  = 1L << 14;  // 0x0000_0000_0000_4000
    public static final long MOUSE_BUTTON_8  = 1L << 15;  // 0x0000_0000_0000_8000
    
    // Mouse button mask
    public static final long MOUSE_BUTTON_MASK = 0x0000_0000_0000_FF00L;
    
    // ========== INPUT FOCUS STATE (bits 16-23) ==========
    public static final long FOCUS_WINDOW    = 1L << 16;  // Window has focus
    public static final long FOCUS_KEYBOARD  = 1L << 17;  // Keyboard focus
    public static final long FOCUS_MOUSE     = 1L << 18;  // Mouse focus
    public static final long FOCUS_CAPTURED  = 1L << 19;  // Input captured (modal)
    
    // Focus mask
    public static final long FOCUS_MASK = 0x0000_0000_00FF_0000L;
    
    // ========== INPUT MODE STATE (bits 24-31) ==========
    public static final long MODE_RAW_EVENTS = 1L << 24;  // Raw event mode
    public static final long MODE_FILTERED   = 1L << 25;  // Filtered line mode
    public static final long MODE_DIRECT_GLFW = 1L << 26; // Direct GLFW mode
    public static final long MODE_SECURE     = 1L << 27;  // Secure input mode
    
    // Mode mask
    public static final long MODE_MASK = 0x0000_0000_FF00_0000L;
    
    // ========== PROCESS STATE (bits 32-39) ==========
    public static final long PROCESS_FOREGROUND = 1L << 32;  // Process is foreground
    public static final long PROCESS_BACKGROUND = 1L << 33;  // Process is background
    public static final long PROCESS_SUSPENDED  = 1L << 34;  // Process is suspended
    public static final long PROCESS_KILLED     = 1L << 35;  // Process is killed
    
    // Process mask
    public static final long PROCESS_MASK = 0x0000_00FF_0000_0000L;
    
    // ========== EVENT STATE (bits 40-47) ==========
    public static final long EVENT_CONSUMED  = 1L << 40;  // Event has been handled
    public static final long EVENT_BUBBLING  = 1L << 41;  // Event is bubbling up
    public static final long EVENT_CAPTURING = 1L << 42;  // Event is in capture phase
    public static final long EVENT_SYNTHETIC = 1L << 43;  // Generated, not from OS
    public static final long EVENT_RECORDED  = 1L << 44;  // Event was recorded
    public static final long EVENT_REPLAYING = 1L << 45;  // Event is from playback
    
    // Event mask
    public static final long EVENT_MASK = 0x0000_FF00_0000_0000L;
    
    // ========== CONTAINER STATE (bits 48-55) ==========
    public static final long CONTAINER_ACTIVE   = 1L << 48;  // Container is active
    public static final long CONTAINER_ENABLED  = 1L << 49;  // Input enabled
    public static final long CONTAINER_RECORDING = 1L << 50; // Recording input
    public static final long CONTAINER_PLAYBACK = 1L << 51;  // Playing back input
    
    // Container mask
    public static final long CONTAINER_MASK = 0x00FF_0000_0000_0000L;
    
    // ========== Helper Methods ==========
    
    /**
     * Convert InputPacket.StateFlags (int) to InputStateFlags (long)
     */
    public static long fromInputPacketFlags(int packetFlags) {
        long flags = 0L;
        
        // Modifiers
        if ((packetFlags & InputPacket.StateFlags.MOD_SHIFT) != 0)
            flags |= MOD_SHIFT;
        if ((packetFlags & InputPacket.StateFlags.MOD_CONTROL) != 0)
            flags |= MOD_CONTROL;
        if ((packetFlags & InputPacket.StateFlags.MOD_ALT) != 0)
            flags |= MOD_ALT;
        if ((packetFlags & InputPacket.StateFlags.MOD_SUPER) != 0)
            flags |= MOD_SUPER;
        if ((packetFlags & InputPacket.StateFlags.MOD_CAPS_LOCK) != 0)
            flags |= MOD_CAPS_LOCK;
        if ((packetFlags & InputPacket.StateFlags.MOD_NUM_LOCK) != 0)
            flags |= MOD_NUM_LOCK;
        
        // Mouse buttons
        if ((packetFlags & InputPacket.StateFlags.MOUSE_BUTTON_1) != 0)
            flags |= MOUSE_BUTTON_1;
        if ((packetFlags & InputPacket.StateFlags.MOUSE_BUTTON_2) != 0)
            flags |= MOUSE_BUTTON_2;
        if ((packetFlags & InputPacket.StateFlags.MOUSE_BUTTON_3) != 0)
            flags |= MOUSE_BUTTON_3;
        if ((packetFlags & InputPacket.StateFlags.MOUSE_BUTTON_4) != 0)
            flags |= MOUSE_BUTTON_4;
        if ((packetFlags & InputPacket.StateFlags.MOUSE_BUTTON_5) != 0)
            flags |= MOUSE_BUTTON_5;
        
        // Event state
        if ((packetFlags & InputPacket.StateFlags.STATE_CONSUMED) != 0)
            flags |= EVENT_CONSUMED;
        if ((packetFlags & InputPacket.StateFlags.STATE_BUBBLING) != 0)
            flags |= EVENT_BUBBLING;
        if ((packetFlags & InputPacket.StateFlags.STATE_CAPTURING) != 0)
            flags |= EVENT_CAPTURING;
        if ((packetFlags & InputPacket.StateFlags.STATE_SYNTHETIC) != 0)
            flags |= EVENT_SYNTHETIC;
        
        return flags;
    }
    
    /**
     * Check if any flag in mask is set
     */
    public static boolean hasAnyFlag(long state, long mask) {
        return (state & mask) != 0;
    }
    
    /**
     * Check if all flags in mask are set
     */
    public static boolean hasAllFlags(long state, long mask) {
        return (state & mask) == mask;
    }
    
    /**
     * Get modifier flags only
     */
    public static long getModifiers(long state) {
        return state & MOD_MASK;
    }
    
    /**
     * Get mouse button flags only
     */
    public static long getMouseButtons(long state) {
        return state & MOUSE_BUTTON_MASK;
    }
    
    /**
     * Get focus flags only
     */
    public static long getFocusFlags(long state) {
        return state & FOCUS_MASK;
    }
    
    /**
     * Get mode flags only
     */
    public static long getModeFlags(long state) {
        return state & MODE_MASK;
    }
    
    /**
     * Get process flags only
     */
    public static long getProcessFlags(long state) {
        return state & PROCESS_MASK;
    }
    
    /**
     * Get event flags only
     */
    public static long getEventFlags(long state) {
        return state & EVENT_MASK;
    }
    
    /**
     * Get container flags only
     */
    public static long getContainerFlags(long state) {
        return state & CONTAINER_MASK;
    }
}