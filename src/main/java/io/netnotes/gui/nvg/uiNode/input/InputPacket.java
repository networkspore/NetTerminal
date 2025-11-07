package io.netnotes.gui.nvg.uiNode.input;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteInteger;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.utils.AtomicSequence;

/**
 * InputPacket - Binary format for input events
 * Integrates with NoteBytes messaging system
 */
public final class InputPacket {
    
    /**
     * Event Types - organized by category
     */
    public static final class Types {
        // Mouse events (0-31)
        public static final NoteBytesReadOnly TYPE_MOUSE_MOVE_ABSOLUTE    = new NoteBytesReadOnly((short) 0);
        public static final NoteBytesReadOnly TYPE_MOUSE_MOVE_RELATIVE    = new NoteBytesReadOnly((short) 1);
        public static final NoteBytesReadOnly TYPE_MOUSE_BUTTON_DOWN      = new NoteBytesReadOnly((short) 2);
        public static final NoteBytesReadOnly TYPE_MOUSE_BUTTON_UP        = new NoteBytesReadOnly((short) 3);
        public static final NoteBytesReadOnly TYPE_MOUSE_CLICK            = new NoteBytesReadOnly((short) 4);
        public static final NoteBytesReadOnly TYPE_MOUSE_DOUBLE_CLICK     = new NoteBytesReadOnly((short) 5);
        public static final NoteBytesReadOnly TYPE_SCROLL                 = new NoteBytesReadOnly((short) 6);
        public static final NoteBytesReadOnly TYPE_MOUSE_ENTER            = new NoteBytesReadOnly((short) 7);
        public static final NoteBytesReadOnly TYPE_MOUSE_EXIT             = new NoteBytesReadOnly((short) 8);
        public static final NoteBytesReadOnly TYPE_MOUSE_DRAG_START       = new NoteBytesReadOnly((short) 9);
        public static final NoteBytesReadOnly TYPE_MOUSE_DRAG             = new NoteBytesReadOnly((short) 10);
        public static final NoteBytesReadOnly TYPE_MOUSE_DRAG_END         = new NoteBytesReadOnly((short) 11);
        
        // Keyboard events (32-63)
        public static final NoteBytesReadOnly TYPE_KEY_DOWN               = new NoteBytesReadOnly((short) 32);
        public static final NoteBytesReadOnly TYPE_KEY_UP                 = new NoteBytesReadOnly((short) 33);
        public static final NoteBytesReadOnly TYPE_KEY_REPEAT             = new NoteBytesReadOnly((short) 34);
        public static final NoteBytesReadOnly TYPE_KEY_CHAR               = new NoteBytesReadOnly((short) 35);
        public static final NoteBytesReadOnly TYPE_KEY_CHAR_MODS          = new NoteBytesReadOnly((short) 36);
        
        // Focus events (64-95)
        public static final NoteBytesReadOnly TYPE_FOCUS_GAINED           = new NoteBytesReadOnly((short) 64);
        public static final NoteBytesReadOnly TYPE_FOCUS_LOST             = new NoteBytesReadOnly((short) 65);
        
        // Window events (96-127)
        public static final NoteBytesReadOnly TYPE_WINDOW_RESIZE          = new NoteBytesReadOnly((short) 96);
        public static final NoteBytesReadOnly TYPE_WINDOW_MOVE            = new NoteBytesReadOnly((short) 97);
        public static final NoteBytesReadOnly TYPE_WINDOW_CLOSE           = new NoteBytesReadOnly((short) 98);
        public static final NoteBytesReadOnly TYPE_FRAMEBUFFER_RESIZE     = new NoteBytesReadOnly((short) 99);
    }
    
    /**
     * State Flags - modifier keys and button states
     */
    public static final class StateFlags {
        // Modifier keys (bits 0-7)
        public static final int MOD_SHIFT       = 0x0001;
        public static final int MOD_CONTROL     = 0x0002;
        public static final int MOD_ALT         = 0x0004;
        public static final int MOD_SUPER       = 0x0008;  // Windows/Command key
        public static final int MOD_CAPS_LOCK   = 0x0010;
        public static final int MOD_NUM_LOCK    = 0x0020;
        
        // Mouse buttons (bits 8-15)
        public static final int MOUSE_BUTTON_1  = 0x0100;  // Left
        public static final int MOUSE_BUTTON_2  = 0x0200;  // Right
        public static final int MOUSE_BUTTON_3  = 0x0400;  // Middle
        public static final int MOUSE_BUTTON_4  = 0x0800;
        public static final int MOUSE_BUTTON_5  = 0x1000;
        
        // Event state flags (bits 16-23)
        public static final int STATE_CONSUMED  = 0x010000;  // Event has been handled
        public static final int STATE_BUBBLING  = 0x020000;  // Event is bubbling up
        public static final int STATE_CAPTURING = 0x040000;  // Event is in capture phase
        public static final int STATE_SYNTHETIC = 0x080000;  // Generated, not from OS
        
        public static boolean hasFlag(int state, int flag) {
            return (state & flag) != 0;
        }
        
        public static int setFlag(int state, int flag) {
            return state | flag;
        }
        
        public static int clearFlag(int state, int flag) {
            return state & ~flag;
        }
    }
    
    /**
     * Factory for creating input packets
     */
    public static class Factory {
        public static final NoteBytesReadOnly TYPE_KEY          = new NoteBytesReadOnly("typ");
        public static final NoteBytesReadOnly SEQUENCE_KEY      = new NoteBytesReadOnly("seq");
        public static final NoteBytesReadOnly STATE_FLAGS_KEY   = new NoteBytesReadOnly("stF");
        public static final NoteBytesReadOnly PAYLOAD_KEY       = new NoteBytesReadOnly("pld");
        
        private static final int BASE_BODY_SIZE = 2;
        
        private final NoteBytesReadOnly m_sourceId;
        
        public Factory(int sourceId) {
            this.m_sourceId = new NoteBytesReadOnly(sourceId);
        }
        
        // Mouse event creators
        
        public byte[] createMouseMove(double x, double y, int stateFlags) {
            return of(Types.TYPE_MOUSE_MOVE_ABSOLUTE, 
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(x),
                new NoteBytesReadOnly(y));
        }
        
        public byte[] createMouseMoveRelative(double dx, double dy, int stateFlags) {
            return of(Types.TYPE_MOUSE_MOVE_RELATIVE,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(dx),
                new NoteBytesReadOnly(dy));
        }
        
        public byte[] createMouseButtonDown(int button, double x, double y, int stateFlags) {
            return of(Types.TYPE_MOUSE_BUTTON_DOWN,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(button),
                new NoteBytesReadOnly(x),
                new NoteBytesReadOnly(y));
        }
        
        public byte[] createMouseButtonUp(int button, double x, double y, int stateFlags) {
            return of(Types.TYPE_MOUSE_BUTTON_UP,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(button),
                new NoteBytesReadOnly(x),
                new NoteBytesReadOnly(y));
        }
        
        public byte[] createMouseClick(int button, double x, double y, int clickCount, int stateFlags) {
            return of(Types.TYPE_MOUSE_CLICK,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(button),
                new NoteBytesReadOnly(x),
                new NoteBytesReadOnly(y),
                new NoteBytesReadOnly(clickCount));
        }
        
        public byte[] createScroll(double xOffset, double yOffset, double mouseX, double mouseY, int stateFlags) {
            return of(Types.TYPE_SCROLL,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(xOffset),
                new NoteBytesReadOnly(yOffset),
                new NoteBytesReadOnly(mouseX),
                new NoteBytesReadOnly(mouseY));
        }
        
        // Keyboard event creators
        
        public byte[] createKeyDown(int key, int scancode, int stateFlags) {
            return of(Types.TYPE_KEY_DOWN,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(key),
                new NoteBytesReadOnly(scancode));
        }
        
        public byte[] createKeyUp(int key, int scancode, int stateFlags) {
            return of(Types.TYPE_KEY_UP,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(key),
                new NoteBytesReadOnly(scancode));
        }
        
        public byte[] createKeyRepeat(int key, int scancode, int stateFlags) {
            return of(Types.TYPE_KEY_REPEAT,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(key),
                new NoteBytesReadOnly(scancode));
        }
        
        public byte[] createKeyChar(int codepoint, int stateFlags) {
            return of(Types.TYPE_KEY_CHAR,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(codepoint));
        }
        
        public byte[] createKeyCharMods(int codepoint, int stateFlags) {
            return of(Types.TYPE_KEY_CHAR_MODS,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(codepoint));
        }
        
        // Focus event creators
        
        public byte[] createFocusGained() {
            return of(Types.TYPE_FOCUS_GAINED,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                0);
        }
        
        public byte[] createFocusLost() {
            return of(Types.TYPE_FOCUS_LOST,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                0);
        }
        
        // Window event creators
        
        public byte[] createFramebufferResize(int width, int height) {
            return of(Types.TYPE_FRAMEBUFFER_RESIZE,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                0,
                new NoteBytesReadOnly(width),
                new NoteBytesReadOnly(height));
        }
        
        // Core factory method
        
        public CompletableFuture<byte[]> ofAsync(NoteBytesReadOnly type) {
            return CompletableFuture.supplyAsync(() -> 
                of(type, new NoteBytesReadOnly(AtomicSequence.getNextSequence())));
        }
        
        public byte[] of(NoteBytesReadOnly type, NoteBytesReadOnly atomicSequence, NoteBytesReadOnly... payload) {
            return of(type, atomicSequence, 0, payload);
        }
        
        /**
         * Create an input packet
         * @param type InputPacket Type
         * @param atomicSequence Sequence number
         * @param stateFlags State flags (modifiers, buttons, etc.)
         * @param payload Main payload
         * @return Binary packet
         */
        public byte[] of(NoteBytesReadOnly type, NoteBytesReadOnly atomicSequence, int stateFlags, NoteBytesReadOnly... payload) {
            if (type == null) {
                throw new IllegalStateException("Type required");
            }
            
            boolean hasStateFlags = stateFlags != 0;
            boolean hasPayload = payload != null && payload.length > 0;
            
            int size = BASE_BODY_SIZE + (hasStateFlags ? 1 : 0) + (hasPayload ? 1 : 0);
            
            NoteBytesPair[] pairs = new NoteBytesPair[size];
            pairs[0] = new NoteBytesPair(TYPE_KEY, type);
            pairs[1] = new NoteBytesPair(SEQUENCE_KEY, atomicSequence);
            
            int index = BASE_BODY_SIZE;
            
            if (hasStateFlags) {
                pairs[index++] = new NoteBytesPair(STATE_FLAGS_KEY, new NoteInteger(stateFlags));
            }
            if (hasPayload) {
                pairs[index] = new NoteBytesPair(PAYLOAD_KEY, new NoteBytesArray(payload));
            }
            
            NoteBytesObject body = new NoteBytesObject(pairs);
            NoteBytesObject packet = new NoteBytesObject(new NoteBytesPair(m_sourceId, body));
            
            return packet.getBytes();
        }
    }
}