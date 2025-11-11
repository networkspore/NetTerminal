package io.netnotes.gui.nvg.core;

import io.netnotes.engine.state.BitFlagStateMachine;
import java.math.BigInteger;

/**
 * StateMachineScopes - Organized state flags using scoped long constants
 * 
 * Each scope uses a different bit range (64 bits per scope).
 * The BitFlagStateMachine's BigInteger backend allows unlimited scopes.
 * 
 * Benefits:
 * - Clean, readable long constants
 * - No bit collision between scopes
 * - Easy to add new scopes
 * - Backward compatible with existing code
 */
public class StateMachineScopes {
    
    // ========== INPUT SCOPE (bits 0-63) ==========
    public static class Input {
        private static final int SCOPE_OFFSET = 0;
        
        // Modifier keys
        public static final long MOD_SHIFT       = flag(0);
        public static final long MOD_CONTROL     = flag(1);
        public static final long MOD_ALT         = flag(2);
        public static final long MOD_SUPER       = flag(3);
        public static final long MOD_CAPS_LOCK   = flag(4);
        public static final long MOD_NUM_LOCK    = flag(5);
        
        // Mouse buttons
        public static final long MOUSE_BUTTON_1  = flag(8);
        public static final long MOUSE_BUTTON_2  = flag(9);
        public static final long MOUSE_BUTTON_3  = flag(10);
        
        // Focus state
        public static final long FOCUS_KEYBOARD  = flag(16);
        public static final long FOCUS_MOUSE     = flag(17);
        public static final long FOCUS_WINDOW    = flag(18);
        
        // Input mode
        public static final long MODE_RAW        = flag(24);
        public static final long MODE_FILTERED   = flag(25);
        public static final long MODE_SECURE     = flag(26);
        
        private static long flag(int bit) {
            return 1L << (SCOPE_OFFSET + bit);
        }
        
        public static BigInteger toBigInteger(long flags) {
            return BigInteger.valueOf(flags).shiftLeft(SCOPE_OFFSET);
        }
    }
    
    // ========== PROCESS SCOPE (bits 64-127) ==========
    public static class Process {
        private static final int SCOPE_OFFSET = 64;
        
        // Process state
        public static final long FOREGROUND      = flag(0);
        public static final long BACKGROUND      = flag(1);
        public static final long SUSPENDED       = flag(2);
        public static final long KILLED          = flag(3);
        public static final long WAITING_INPUT   = flag(4);
        
        // Process type
        public static final long IS_SHELL        = flag(8);
        public static final long IS_CHILD        = flag(9);
        public static final long IS_DAEMON       = flag(10);
        
        // Capabilities
        public static final long CAN_FORK        = flag(16);
        public static final long CAN_EXEC        = flag(17);
        public static final long HAS_TTY         = flag(18);
        
        private static long flag(int bit) {
            return 1L << bit;
        }
        
        public static BigInteger toBigInteger(long flags) {
            return BigInteger.valueOf(flags).shiftLeft(SCOPE_OFFSET);
        }
    }
    
    // ========== CONTAINER SCOPE (bits 128-191) ==========
    public static class Container {
        private static final int SCOPE_OFFSET = 128;
        
        // Container state
        public static final long ACTIVE          = flag(0);
        public static final long ENABLED         = flag(1);
        public static final long RECORDING       = flag(2);
        public static final long PLAYBACK        = flag(3);
        public static final long SHUTTING_DOWN   = flag(4);
        
        // Input routing
        public static final long BUBBLE_MODE     = flag(8);
        public static final long CAPTURE_MODE    = flag(9);
        public static final long DIRECT_MODE     = flag(10);
        
        private static long flag(int bit) {
            return 1L << bit;
        }
        
        public static BigInteger toBigInteger(long flags) {
            return BigInteger.valueOf(flags).shiftLeft(SCOPE_OFFSET);
        }
    }
    
    // ========== RENDERING SCOPE (bits 192-255) ==========
    public static class Rendering {
        private static final int SCOPE_OFFSET = 192;
        
        // Render state
        public static final long DIRTY           = flag(0);
        public static final long RENDERING       = flag(1);
        public static final long VSYNC_ENABLED   = flag(2);
        
        // UI state
        public static final long SHOW_CURSOR     = flag(8);
        public static final long SHOW_FPS        = flag(9);
        public static final long SHOW_DEBUG      = flag(10);
        
        private static long flag(int bit) {
            return 1L << bit;
        }
        
        public static BigInteger toBigInteger(long flags) {
            return BigInteger.valueOf(flags).shiftLeft(SCOPE_OFFSET);
        }
    }
    
    // ========== NETWORK SCOPE (bits 256-319) ==========
    public static class Network {
        private static final int SCOPE_OFFSET = 256;
        
        // Connection state
        public static final long CONNECTED       = flag(0);
        public static final long AUTHENTICATED   = flag(1);
        public static final long ENCRYPTED       = flag(2);
        
        // Protocol state
        public static final long HANDSHAKE_DONE  = flag(8);
        public static final long SYNC_ENABLED    = flag(9);
        
        private static long flag(int bit) {
            return 1L << bit;
        }
        
        public static BigInteger toBigInteger(long flags) {
            return BigInteger.valueOf(flags).shiftLeft(SCOPE_OFFSET);
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Combine flags from multiple scopes into a single BigInteger
     */
    public static BigInteger combine(
            long inputFlags,
            long processFlags,
            long containerFlags,
            long renderFlags,
            long networkFlags) {
        
        return Input.toBigInteger(inputFlags)
            .or(Process.toBigInteger(processFlags))
            .or(Container.toBigInteger(containerFlags))
            .or(Rendering.toBigInteger(renderFlags))
            .or(Network.toBigInteger(networkFlags));
    }
    
    /**
     * Extract scoped flags from BigInteger state
     */
    public static long extractInputFlags(BigInteger state) {
        return state.shiftRight(Input.SCOPE_OFFSET).longValue();
    }
    
    public static long extractProcessFlags(BigInteger state) {
        return state.shiftRight(Process.SCOPE_OFFSET).longValue();
    }
    
    public static long extractContainerFlags(BigInteger state) {
        return state.shiftRight(Container.SCOPE_OFFSET).longValue();
    }
    
    public static long extractRenderFlags(BigInteger state) {
        return state.shiftRight(Rendering.SCOPE_OFFSET).longValue();
    }
    
    public static long extractNetworkFlags(BigInteger state) {
        return state.shiftRight(Network.SCOPE_OFFSET).longValue();
    }
    
    /**
     * Set scoped flags in state machine
     */
    public static void setInputFlags(BitFlagStateMachine sm, long flags) {
        sm.setState(sm.getState().or(Input.toBigInteger(flags)));
    }
    
    public static void setProcessFlags(BitFlagStateMachine sm, long flags) {
        sm.setState(sm.getState().or(Process.toBigInteger(flags)));
    }
    
    public static void setContainerFlags(BitFlagStateMachine sm, long flags) {
        sm.setState(sm.getState().or(Container.toBigInteger(flags)));
    }
    
    /**
     * Check scoped flags
     */
    public static boolean hasInputFlag(BitFlagStateMachine sm, long flag) {
        return sm.hasFlag(Input.toBigInteger(flag));
    }
    
    public static boolean hasProcessFlag(BitFlagStateMachine sm, long flag) {
        return sm.hasFlag(Process.toBigInteger(flag));
    }
    
    public static boolean hasContainerFlag(BitFlagStateMachine sm, long flag) {
        return sm.hasFlag(Container.toBigInteger(flag));
    }
}