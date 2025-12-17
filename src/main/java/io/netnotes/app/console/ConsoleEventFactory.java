package io.netnotes.app.console;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.Keyboard.KeyCode;
import io.netnotes.engine.io.input.events.KeyCharEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.io.input.events.KeyUpEvent;
import io.netnotes.engine.noteBytes.NoteBytes;

/**
 * ConsoleEventFactory - Convert JLine terminal input to HID keyboard events
 * 
 * Maps terminal input codes to USB HID keyboard usage codes and produces
 * proper KeyDown/KeyUp events with modifier flags and KeyChar events.
 */
class ConsoleEventFactory {
    
    // Modifier flags (matching your system)
    static final int MOD_SHIFT = 0x0001;
    static final int MOD_CONTROL = 0x0002;
    static final int MOD_ALT = 0x0004;
    
    // ===== ASCII TO HID MAPPING =====
    
    /**
     * Convert printable ASCII character to HID keycode + modifiers
     * Returns [hidCode, modifiers]
     */
    static int[] asciiToHid(int ascii) {
        // Lowercase letters
        if (ascii >= 'a' && ascii <= 'z') {
            return new int[]{KeyCode.A + (ascii - 'a'), 0};
        }
        
        // Uppercase letters
        if (ascii >= 'A' && ascii <= 'Z') {
            return new int[]{KeyCode.A + (ascii - 'A'), MOD_SHIFT};
        }
        
        // Digits
        if (ascii >= '0' && ascii <= '9') {
            int digit = ascii - '0';
            return new int[]{
                digit == 0 ? KeyCode.DIGIT_0 : KeyCode.DIGIT_1 + digit - 1,
                0
            };
        }
        
        // Symbols - unshifted
        return switch (ascii) {
            case ' ' -> new int[]{KeyCode.SPACE, 0};
            case '-' -> new int[]{KeyCode.MINUS, 0};
            case '=' -> new int[]{KeyCode.EQUALS, 0};
            case '[' -> new int[]{KeyCode.LEFT_BRACKET, 0};
            case ']' -> new int[]{KeyCode.RIGHT_BRACKET, 0};
            case '\\' -> new int[]{KeyCode.BACKSLASH, 0};
            case ';' -> new int[]{KeyCode.SEMICOLON, 0};
            case '\'' -> new int[]{KeyCode.APOSTROPHE, 0};
            case '`' -> new int[]{KeyCode.GRAVE, 0};
            case ',' -> new int[]{KeyCode.COMMA, 0};
            case '.' -> new int[]{KeyCode.PERIOD, 0};
            case '/' -> new int[]{KeyCode.SLASH, 0};
            
            // Symbols - shifted
            case '!' -> new int[]{KeyCode.DIGIT_1, MOD_SHIFT};
            case '@' -> new int[]{KeyCode.DIGIT_2, MOD_SHIFT};
            case '#' -> new int[]{KeyCode.DIGIT_3, MOD_SHIFT};
            case '$' -> new int[]{KeyCode.DIGIT_4, MOD_SHIFT};
            case '%' -> new int[]{KeyCode.DIGIT_5, MOD_SHIFT};
            case '^' -> new int[]{KeyCode.DIGIT_6, MOD_SHIFT};
            case '&' -> new int[]{KeyCode.DIGIT_7, MOD_SHIFT};
            case '*' -> new int[]{KeyCode.DIGIT_8, MOD_SHIFT};
            case '(' -> new int[]{KeyCode.DIGIT_9, MOD_SHIFT};
            case ')' -> new int[]{KeyCode.DIGIT_0, MOD_SHIFT};
            case '_' -> new int[]{KeyCode.MINUS, MOD_SHIFT};
            case '+' -> new int[]{KeyCode.EQUALS, MOD_SHIFT};
            case '{' -> new int[]{KeyCode.LEFT_BRACKET, MOD_SHIFT};
            case '}' -> new int[]{KeyCode.RIGHT_BRACKET, MOD_SHIFT};
            case '|' -> new int[]{KeyCode.BACKSLASH, MOD_SHIFT};
            case ':' -> new int[]{KeyCode.SEMICOLON, MOD_SHIFT};
            case '"' -> new int[]{KeyCode.APOSTROPHE, MOD_SHIFT};
            case '~' -> new int[]{KeyCode.GRAVE, MOD_SHIFT};
            case '<' -> new int[]{KeyCode.COMMA, MOD_SHIFT};
            case '>' -> new int[]{KeyCode.PERIOD, MOD_SHIFT};
            case '?' -> new int[]{KeyCode.SLASH, MOD_SHIFT};
            
            default -> new int[]{KeyCode.NONE, 0};
        };
    }
    
    /**
     * Map ANSI CSI sequence letter to HID keycode
     */
    static int csiLetterToHid(char letter) {
        return switch (letter) {
            case 'A' -> KeyCode.UP;
            case 'B' -> KeyCode.DOWN;
            case 'C' -> KeyCode.RIGHT;
            case 'D' -> KeyCode.LEFT;
            case 'H' -> KeyCode.HOME;
            case 'F' -> KeyCode.END;
            default -> KeyCode.NONE;
        };
    }
    
    /**
     * Map tilde-terminated sequence number to HID keycode
     * ESC[2~ = Insert, ESC[3~ = Delete, etc.
     */
    static int tildeSequenceToHid(int num) {
        return switch (num) {
            case 2 -> KeyCode.INSERT;
            case 3 -> KeyCode.DELETE;
            case 5 -> KeyCode.PAGE_UP;
            case 6 -> KeyCode.PAGE_DOWN;
            case 11 -> KeyCode.F1;
            case 12 -> KeyCode.F2;
            case 13 -> KeyCode.F3;
            case 14 -> KeyCode.F4;
            case 15 -> KeyCode.F5;
            case 17 -> KeyCode.F6;
            case 18 -> KeyCode.F7;
            case 19 -> KeyCode.F8;
            case 20 -> KeyCode.F9;
            case 21 -> KeyCode.F10;
            case 23 -> KeyCode.F11;
            case 24 -> KeyCode.F12;
            default -> KeyCode.NONE;
        };
    }
    
    /**
     * Map SS3 sequence letter to HID keycode
     * ESC O P = F1, etc.
     */
    static int ss3LetterToHid(char letter) {
        return switch (letter) {
            case 'P' -> KeyCode.F1;
            case 'Q' -> KeyCode.F2;
            case 'R' -> KeyCode.F3;
            case 'S' -> KeyCode.F4;
            default -> KeyCode.NONE;
        };
    }
    
    /**
     * Decode terminal modifier code to modifier flags
     * Terminal format: ESC[1;mod<key> where mod encodes Shift/Alt/Ctrl
     * 
     * Terminal modifier encoding (1-based):
     * 1 = no modifiers
     * 2 = Shift
     * 3 = Alt
     * 4 = Shift+Alt
     * 5 = Ctrl
     * 6 = Shift+Ctrl
     * 7 = Alt+Ctrl
     * 8 = Shift+Alt+Ctrl
     */
    static int decodeTerminalModifiers(int terminalModCode) {
        int mods = 0;
        int mask = terminalModCode - 1; // Convert to 0-based bitmask
        
        if ((mask & 1) != 0) mods |= MOD_SHIFT;
        if ((mask & 2) != 0) mods |= MOD_ALT;
        if ((mask & 4) != 0) mods |= MOD_CONTROL;
        
        return mods;
    }
    
    // ===== EVENT FACTORIES =====
    
    /**
     * Create KeyDown event from HID keycode
     */
    static KeyDownEvent createKeyDown(ContextPath sourcePath, int hidKeyCode, int modifiers) {
        return new KeyDownEvent(
            sourcePath,
            new NoteBytes(hidKeyCode),
            new NoteBytes(0), // scancode always 0 for console
            modifiers
        );
    }
    
    /**
     * Create KeyUp event from HID keycode
     */
    static KeyUpEvent createKeyUp(ContextPath sourcePath, int hidKeyCode, int modifiers) {
        return new KeyUpEvent(
            sourcePath,
            new NoteBytes(hidKeyCode),
            new NoteBytes(0),
            modifiers
        );
    }
    
    /**
     * Create KeyChar event from Unicode codepoint
     */
    static KeyCharEvent createKeyChar(ContextPath sourcePath, int codepoint, int modifiers) {
        return new KeyCharEvent(
            sourcePath,
            new NoteBytes(codepoint),
            modifiers
        );
    }
    
    /**
     * Create KeyDown + KeyUp pair for a key press
     */
    static void emitKeyPress(ContextPath sourcePath, int hidKeyCode, int modifiers, 
                            java.util.function.Consumer<io.netnotes.engine.io.input.events.RoutedEvent> emitter) {
        emitter.accept(createKeyDown(sourcePath, hidKeyCode, modifiers));
        emitter.accept(createKeyUp(sourcePath, hidKeyCode, modifiers));
    }
    
    /**
     * Process printable ASCII and emit both KeyDown/Up + KeyChar
     */
    static void emitPrintableChar(ContextPath sourcePath, int ascii, 
                                 java.util.function.Consumer<io.netnotes.engine.io.input.events.RoutedEvent> emitter) {
        int[] hidMapping = asciiToHid(ascii);
        int hidCode = hidMapping[0];
        int modifiers = hidMapping[1];
        
        if (hidCode != KeyCode.NONE) {
            // Emit key down/up
            emitter.accept(createKeyDown(sourcePath, hidCode, modifiers));
            emitter.accept(createKeyUp(sourcePath, hidCode, modifiers));
        }
        
        // Always emit char event for printable characters
        emitter.accept(createKeyChar(sourcePath, ascii, modifiers));
    }
    
    /**
     * Process control character (Ctrl+A through Ctrl+Z)
     */
    static void emitControlChar(ContextPath sourcePath, int ctrlChar,
                               java.util.function.Consumer<io.netnotes.engine.io.input.events.RoutedEvent> emitter) {
        if (ctrlChar >= 1 && ctrlChar <= 26) {
            int hidCode = KeyCode.A + (ctrlChar - 1);
            emitKeyPress(sourcePath, hidCode, MOD_CONTROL, emitter);
        }
    }
    
    /**
     * Process special keys (Enter, Backspace, Tab, Escape)
     */
    static void emitSpecialKey(ContextPath sourcePath, int ascii,
                              java.util.function.Consumer<io.netnotes.engine.io.input.events.RoutedEvent> emitter) {
        int hidCode = switch (ascii) {
            case 13, 10 -> KeyCode.ENTER;
            case 8, 127 -> KeyCode.BACKSPACE;
            case 9 -> KeyCode.TAB;
            case 27 -> KeyCode.ESCAPE;
            default -> KeyCode.NONE;
        };
        
        if (hidCode != KeyCode.NONE) {
            emitKeyPress(sourcePath, hidCode, 0, emitter);
        }
    }
}