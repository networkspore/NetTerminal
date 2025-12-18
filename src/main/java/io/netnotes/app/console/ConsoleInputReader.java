package io.netnotes.app.console;

import io.netnotes.engine.io.input.Keyboard.KeyCode;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * ConsoleInputReader - Reads from InputStream and produces keyboard events
 * 
 * Handles both ASCII and UTF-8 multi-byte sequences:
 * - ASCII (0x00-0x7F): Single byte, processed as before
 * - UTF-8 (0x80+): Multi-byte sequences decoded to Unicode codepoints
 * - ANSI escape sequences: Special key handling
 * - Control characters (Ctrl+A-Z): Returned for special handling
 * 
 * UTF-8 byte patterns:
 * - 0xxxxxxx: 1-byte (ASCII)
 * - 110xxxxx 10xxxxxx: 2-byte
 * - 1110xxxx 10xxxxxx 10xxxxxx: 3-byte
 * - 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx: 4-byte
 */
public class ConsoleInputReader {
    
    private static final int MOD_ALT = 0x0004;
    
    private final InputStream input;
    
    /**
     * Result of reading input - contains both raw character and generated events
     */
    public static class ReadResult {
        public final int rawChar;           // The raw character code read (-1 for EOF)
        public final int codepoint;         // Unicode codepoint (for UTF-8)
        public final List<RoutedEvent> events;  // Events to emit (may be empty)
        
        public ReadResult(int rawChar, int codepoint, List<RoutedEvent> events) {
            this.rawChar = rawChar;
            this.codepoint = codepoint;
            this.events = events;
        }
        
        public boolean isEOF() {
            return rawChar == -1;
        }
        
        public boolean isCtrlC() {
            return rawChar == 3;
        }
        
        public boolean isCtrlD() {
            return rawChar == 4;
        }
    }
    
    /**
     * Create a reader for the given input stream
     */
    public ConsoleInputReader(InputStream input) {
        this.input = input;
    }
    
    /**
     * Read and process input, handling UTF-8 multi-byte sequences
     * 
     * @param sourcePath Context path for emitted events
     * @return ReadResult containing raw char, codepoint, and events
     * @throws IOException on read errors
     */
    public ReadResult readNext(ContextPath sourcePath) throws IOException {
        List<RoutedEvent> events = new ArrayList<>();
        
        int firstByte = input.read();
        
        if (firstByte == -1) {
            // EOF
            return new ReadResult(-1, -1, events);
        }
        
        // Check if this is a UTF-8 multi-byte sequence
        int codepoint;
        if ((firstByte & 0x80) == 0) {
            // Single-byte ASCII (0xxxxxxx)
            codepoint = firstByte;
        } else {
            // Multi-byte UTF-8 sequence
            codepoint = decodeUtf8(firstByte);
            if (codepoint == -1) {
                // Invalid UTF-8, treat as raw byte
                Log.logError("[ConsoleInputReader] Invalid UTF-8 sequence starting with: 0x" + 
                           Integer.toHexString(firstByte));
                return new ReadResult(firstByte, firstByte, events);
            }
            
            // For multi-byte UTF-8, generate KeyChar event only
            events.add(ConsoleEventFactory.createKeyChar(sourcePath, codepoint, 0));
            return new ReadResult(firstByte, codepoint, events);
        }
        
        // Single-byte handling (ASCII range)
        // For control characters (1-26, except Tab/LF/CR), return raw char
        if (KeyCode.isControlChar(firstByte) && firstByte != 9 && firstByte != 10 && firstByte != 13) {
            int hidCode = KeyCode.controlCharToHid(firstByte);
            addKeyPress(sourcePath, hidCode, ConsoleEventFactory.MOD_CONTROL, events);
            return new ReadResult(firstByte, codepoint, events);
        }
        
        // Process normal ASCII input
        processInput(firstByte, sourcePath, events);
        return new ReadResult(firstByte, codepoint, events);
    }
    
    /**
     * Decode UTF-8 multi-byte sequence
     * Returns Unicode codepoint or -1 on error
     */
    private int decodeUtf8(int firstByte) throws IOException {
        int codepoint;
        int bytesNeeded;
        
        if ((firstByte & 0xE0) == 0xC0) {
            // 2-byte sequence: 110xxxxx 10xxxxxx
            bytesNeeded = 1;
            codepoint = firstByte & 0x1F;
        } else if ((firstByte & 0xF0) == 0xE0) {
            // 3-byte sequence: 1110xxxx 10xxxxxx 10xxxxxx
            bytesNeeded = 2;
            codepoint = firstByte & 0x0F;
        } else if ((firstByte & 0xF8) == 0xF0) {
            // 4-byte sequence: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
            bytesNeeded = 3;
            codepoint = firstByte & 0x07;
        } else {
            // Invalid UTF-8 start byte
            return -1;
        }
        
        // Read continuation bytes
        for (int i = 0; i < bytesNeeded; i++) {
            int nextByte = input.read();
            
            // Check for valid continuation byte (10xxxxxx)
            if ((nextByte & 0xC0) != 0x80) {
                return -1;
            }
            
            codepoint = (codepoint << 6) | (nextByte & 0x3F);
        }
        
        return codepoint;
    }
    
    /**
     * Process a single ASCII input character and generate appropriate events
     */
    private void processInput(int ch, ContextPath sourcePath, List<RoutedEvent> events) {
        try {
            // ESC sequence
            if (ch == 27) {
                processEscapeSequence(sourcePath, events);
                return;
            }
            
            // Printable ASCII (32-126)
            if (ch >= 32 && ch <= 126) {
                addPrintableChar(sourcePath, ch, events);
                return;
            }
            
            // Special keys (Enter, Backspace, Tab)
            if (ch == 13 || ch == 10 || ch == 8 || ch == 127 || ch == 9) {
                addSpecialKey(sourcePath, ch, events);
                return;
            }
            
        } catch (Exception e) {
            Log.logError("[ConsoleInputReader] Error processing input: " + e.getMessage());
        }
    }
    
    /**
     * Process ANSI escape sequence for special keys
     */
    private void processEscapeSequence(ContextPath sourcePath, List<RoutedEvent> events) throws IOException {
        int next = input.read();
        
        if (next == -1) {
            // Just ESC key by itself
            addKeyPress(sourcePath, KeyCode.ESCAPE, 0, events);
            return;
        }
        
        if (next == '[') {
            // CSI sequence
            processCsiSequence(sourcePath, events);
        } else if (next == 'O') {
            // SS3 sequence
            processSs3Sequence(sourcePath, events);
        } else if (next >= 32 && next <= 126) {
            // Alt+key (ESC followed by printable char)
            int[] hidMapping = ConsoleEventFactory.asciiToHid(next);
            int hidCode = hidMapping[0];
            int baseMods = hidMapping[1];
            
            if (hidCode != KeyCode.NONE) {
                addKeyPress(sourcePath, hidCode, baseMods | MOD_ALT, events);
            }
            
            // Also add char event with Alt modifier
            events.add(ConsoleEventFactory.createKeyChar(sourcePath, next, baseMods | MOD_ALT));
        } else {
            Log.logMsg("[ConsoleInputReader] Unknown ESC sequence: ESC " + next);
        }
    }
    
    /**
     * Process CSI sequence (ESC[...)
     */
    private void processCsiSequence(ContextPath sourcePath, List<RoutedEvent> events) throws IOException {
        int code = input.read();
        
        // Modifier sequences: ESC[1;mod<key>
        if (code >= '0' && code <= '9') {
            StringBuilder numBuf = new StringBuilder();
            numBuf.append((char)code);
            
            int next = input.read();
            while (next >= '0' && next <= '9') {
                numBuf.append((char)next);
                next = input.read();
            }
            
            if (next == ';') {
                int modCode = input.read() - '0';
                int keyLetter = input.read();
                int hidCode = ConsoleEventFactory.csiLetterToHid((char)keyLetter);
                int mods = ConsoleEventFactory.decodeTerminalModifiers(modCode);
                
                if (hidCode != 0) {
                    addKeyPress(sourcePath, hidCode, mods, events);
                }
            } else if (next == '~') {
                int num = Integer.parseInt(numBuf.toString());
                int hidCode = ConsoleEventFactory.tildeSequenceToHid(num);
                if (hidCode != 0) {
                    addKeyPress(sourcePath, hidCode, 0, events);
                }
            }
            return;
        }
        
        // Standard sequences (no modifiers)
        int hidCode = ConsoleEventFactory.csiLetterToHid((char)code);
        if (hidCode != 0) {
            addKeyPress(sourcePath, hidCode, 0, events);
        }
    }
    
    /**
     * Process SS3 sequence (ESC O ...)
     */
    private void processSs3Sequence(ContextPath sourcePath, List<RoutedEvent> events) throws IOException {
        int code = input.read();
        int hidCode = ConsoleEventFactory.ss3LetterToHid((char)code);
        if (hidCode != 0) {
            addKeyPress(sourcePath, hidCode, 0, events);
        }
    }
    
    // ===== EVENT GENERATION HELPERS =====
    
    private void addKeyPress(ContextPath sourcePath, int hidCode, int modifiers, List<RoutedEvent> events) {
        events.add(ConsoleEventFactory.createKeyDown(sourcePath, hidCode, modifiers));
        events.add(ConsoleEventFactory.createKeyUp(sourcePath, hidCode, modifiers));
    }
    
    private void addPrintableChar(ContextPath sourcePath, int ascii, List<RoutedEvent> events) {
        int[] hidMapping = ConsoleEventFactory.asciiToHid(ascii);
        int hidCode = hidMapping[0];
        int modifiers = hidMapping[1];
        
        if (hidCode != KeyCode.NONE) {
            // Standard event order: KeyDown → KeyChar → KeyUp
            events.add(ConsoleEventFactory.createKeyDown(sourcePath, hidCode, modifiers));
            events.add(ConsoleEventFactory.createKeyChar(sourcePath, ascii, modifiers));
            events.add(ConsoleEventFactory.createKeyUp(sourcePath, hidCode, modifiers));
        } else {
            // Fallback: just char event if we can't map to HID
            events.add(ConsoleEventFactory.createKeyChar(sourcePath, ascii, modifiers));
        }
    }
    
    private void addSpecialKey(ContextPath sourcePath, int ascii, List<RoutedEvent> events) {
        int hidCode = switch (ascii) {
            case 13, 10 -> KeyCode.ENTER;
            case 8, 127 -> KeyCode.BACKSPACE;
            case 9 -> KeyCode.TAB;
            case 27 -> KeyCode.ESCAPE;
            default -> KeyCode.NONE;
        };
        
        if (hidCode != KeyCode.NONE) {
            addKeyPress(sourcePath, hidCode, 0, events);
        }
    }
}