package io.netnotes.app.console;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.events.KeyCharEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.io.input.events.KeyUpEvent;
import io.netnotes.engine.noteBytes.NoteBytes;

/**
 * ConsoleEventFactory - Creates RoutedEvents from console input
 * 
 * Factory for creating typed RoutedEvent objects from raw console input.
 * Maps console key codes to engine's event types with modifier flags.
 * 
 * Event Types:
 * - KeyDownEvent: Key press with modifiers
 * - KeyUpEvent: Key release with modifiers
 * - KeyCharEvent: Character input with modifiers
 * 
 * State Flags:
 * - MOD_SHIFT (0x0001): Shift key
 * - MOD_CONTROL (0x0002): Control key (Ctrl)
 * - MOD_ALT (0x0004): Alt key (Meta/Option)
 * - MOD_SUPER (0x0008): Windows/Command key (not detectable in terminal)
 * - MOD_CAPS_LOCK (0x0010): Caps Lock (not detectable in terminal)
 * - MOD_NUM_LOCK (0x0020): Num Lock (not detectable in terminal)
 * - MOD_SCROLL_LOCK (0x0040): Scroll Lock (not detectable in terminal)
 */
class ConsoleEventFactory {
    
    /**
     * Create key down event with modifier flags
     * 
     * @param sourcePath Source input device path
     * @param key Virtual key code (VK_* constants)
     * @param scancode Hardware scancode (0 for console)
     * @param stateFlags Modifier flags (MOD_SHIFT | MOD_CONTROL | etc)
     * @return KeyDownEvent
     */
    static KeyDownEvent createKeyDown(ContextPath sourcePath, int key, int scancode, int stateFlags) {
        return new KeyDownEvent(
            sourcePath,
            new NoteBytes(key),
            new NoteBytes(scancode),
            stateFlags
        );
    }
    
    /**
     * Create key up event with modifier flags
     * 
     * @param sourcePath Source input device path
     * @param key Virtual key code (VK_* constants)
     * @param scancode Hardware scancode (0 for console)
     * @param stateFlags Modifier flags (MOD_SHIFT | MOD_CONTROL | etc)
     * @return KeyUpEvent
     */
    static KeyUpEvent createKeyUp(ContextPath sourcePath, int key, int scancode, int stateFlags) {
        return new KeyUpEvent(
            sourcePath,
            new NoteBytes(key),
            new NoteBytes(scancode),
            stateFlags
        );
    }
    
    /**
     * Create character event with modifier flags
     * 
     * @param sourcePath Source input device path
     * @param codepoint Unicode code point
     * @param stateFlags Modifier flags (MOD_SHIFT | MOD_CONTROL | etc)
     * @return KeyCharEvent
     */
    static KeyCharEvent createKeyChar(ContextPath sourcePath, int codepoint, int stateFlags) {
        return new KeyCharEvent(
            sourcePath,
            new NoteBytes(codepoint),
            stateFlags
        );
    }
}