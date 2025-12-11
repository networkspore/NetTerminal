package io.netnotes.app.console;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.input.KeyboardInput;
import io.netnotes.engine.io.input.events.*;
import io.netnotes.engine.io.process.StreamChannel;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * ConsoleInputCapture - Console/terminal keyboard input source
 * 
 * Extends KeyboardInput to capture raw keyboard input from JLine3 terminal:
 * - Character-by-character capture (no line buffering)
 * - Special key detection (arrows, function keys, etc.)
 * - Direct event emission (no StreamChannel serialization)
 * - Partial modifier state tracking (what terminals can report)
 * 
 * Uses JLine3's NonBlockingReader in a background thread to continuously
 * read input and convert it to RoutedEvents that are emitted to consumers.
 * 
 * Architecture:
 * JLine3 Terminal → NonBlockingReader → ConsoleEventFactory → RoutedEvent → Consumers
 * 
 * Key Mappings:
 * - Printable ASCII (32-126): KEY_CHAR events
 * - Enter/Return (13/10): KEY_DOWN/UP
 * - Backspace (8/127): KEY_DOWN/UP
 * - Tab (9): KEY_DOWN/UP
 * - Ctrl+A-Z (1-26): KEY_DOWN/UP with 'A'-'Z' key code + MOD_CONTROL flag
 * - Arrow keys: ESC[A/B/C/D → KEY_DOWN/UP with VK codes
 * - Function keys, Home, End, Insert, Delete, PageUp/Down
 * 
 * Modifier Tracking Limitations:
 * - Ctrl: Detected via ASCII control codes (reliable)
 * - Alt: Detected via ESC prefix in some terminals (partial)
 * - Shift: Inferred from uppercase chars and special sequences (partial)
 * - Caps Lock: Cannot be detected (terminal sends modified chars)
 * - Num Lock: Cannot be detected (not reported)
 * - Scroll Lock: Cannot be detected (not reported)
 */
public class ConsoleInputCapture extends KeyboardInput {
    
    // State flags (matching C++ constants)
    private static final int MOD_SHIFT = 0x0001;
    private static final int MOD_CONTROL = 0x0002;
    private static final int MOD_ALT = 0x0004;
    //private static final int MOD_SUPER = 0x0008;
    //private static final int MOD_CAPS_LOCK = 0x0010;    // Not detectable
    //private static final int MOD_NUM_LOCK = 0x0020;     // Not detectable
    //private static final int MOD_SCROLL_LOCK = 0x0040;  // Not detectable
    
    private final Terminal terminal;
    private final ExecutorService executor;
    private volatile boolean capturing = false;
    
    // Modifier state tracking (best effort)
    private volatile int currentModifiers = 0;
    
    public ConsoleInputCapture(Terminal terminal, String inputId) {
        super(inputId);
        this.terminal = terminal;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "console-input-" + inputId);
            t.setDaemon(true);
            return t;
        });
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> run() {
        if (capturing) {
            return CompletableFuture.completedFuture(null);
        }
        
        capturing = true;
        
        System.out.println("[ConsoleInputCapture] Starting capture at: " + contextPath);
        
        // Start capture loop in background
        executor.submit(this::captureLoop);
        
        return getCompletionFuture();
    }
    
    /**
     * Main capture loop - runs continuously in background thread
     */
    private void captureLoop() {
        NonBlockingReader reader = terminal.reader();
        
        try {
            while (capturing) {
                // Read next character with timeout
                int ch = reader.read(10); // 10ms timeout
                
                if (ch == -2) {
                    // Timeout - no input available, continue
                    continue;
                } else if (ch == -1) {
                    // EOF - terminal closed
                    System.out.println("[ConsoleInputCapture] Terminal closed (EOF)");
                    break;
                }
                
                // Process the character and emit events
                processInput(ch);
            }
        } catch (IOException e) {
            if (capturing) {
                System.err.println("[ConsoleInputCapture] Read error: " + e.getMessage());
            }
        } finally {
            capturing = false;
            System.out.println("[ConsoleInputCapture] Capture loop ended");
        }
    }
    
    // ===== INPUT PROCESSING =====
    
    /**
     * Process a single input character and emit appropriate events
     */
    private void processInput(int ch) {
        try {
            // Check for ESC sequence (special keys or Alt modifier)
            if (ch == 27) {
                processEscapeSequence();
                return;
            }
            
            // Printable ASCII characters
            if (ch >= 32 && ch <= 126) {
                // Detect implicit shift (uppercase letters)
                int modifiers = 0;
                if (ch >= 'A' && ch <= 'Z') {
                    modifiers |= MOD_SHIFT;
                }
                
                emitEvent(ConsoleEventFactory.createKeyChar(contextPath, ch, modifiers));
                return;
            }
            
            // Special control characters
            switch (ch) {
                case 13, 10 -> { // Enter/Return
                    emitEvent(ConsoleEventFactory.createKeyDown(contextPath, 13, 0, currentModifiers));
                    emitEvent(ConsoleEventFactory.createKeyUp(contextPath, 13, 0, currentModifiers));
                }
                case 8, 127 -> { // Backspace
                    emitEvent(ConsoleEventFactory.createKeyDown(contextPath, 8, 0, currentModifiers));
                    emitEvent(ConsoleEventFactory.createKeyUp(contextPath, 8, 0, currentModifiers));
                }
                case 9 -> { // Tab
                    emitEvent(ConsoleEventFactory.createKeyDown(contextPath, 9, 0, currentModifiers));
                    emitEvent(ConsoleEventFactory.createKeyUp(contextPath, 9, 0, currentModifiers));
                }
                default -> {
                    // Ctrl+A through Ctrl+Z (ASCII 1-26)
                    if (ch >= 1 && ch <= 26) {
                        int key = 'A' + (ch - 1);
                        int modifiers = MOD_CONTROL;
                        
                        emitEvent(ConsoleEventFactory.createKeyDown(contextPath, key, 0, modifiers));
                        emitEvent(ConsoleEventFactory.createKeyUp(contextPath, key, 0, modifiers));
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("[ConsoleInputCapture] Error processing input: " + e.getMessage());
        }
    }
    
    /**
     * Process ANSI escape sequence for special keys
     * 
     * Sequences:
     * - ESC[A: Up arrow
     * - ESC[B: Down arrow
     * - ESC[C: Right arrow
     * - ESC[D: Left arrow
     * - ESC[H: Home
     * - ESC[F: End
     * - ESC[2~: Insert
     * - ESC[3~: Delete
     * - ESC[5~: Page Up
     * - ESC[6~: Page Down
     * - ESC <char>: Alt+char (in some terminals)
     */
    private void processEscapeSequence() throws IOException {
        NonBlockingReader reader = terminal.reader();
        
        // Read next character after ESC
        int next = reader.read(10);
        if (next == -1 || next == -2) {
            // Just ESC key by itself
            emitEvent(ConsoleEventFactory.createKeyDown(contextPath, 27, 0, currentModifiers));
            emitEvent(ConsoleEventFactory.createKeyUp(contextPath, 27, 0, currentModifiers));
            return;
        }
        
        if (next == '[') {
            // CSI (Control Sequence Introducer) - most common
            processCsiSequence(reader);
        } else if (next == 'O') {
            // SS3 (Single Shift Three) - alternative for some terminals
            processSs3Sequence(reader);
        } else {
            // Alt+key sequence (ESC followed by regular key)
            // Some terminals send this for Alt modifier
            if (next >= 32 && next <= 126) {
                int modifiers = MOD_ALT;
                emitEvent(ConsoleEventFactory.createKeyChar(contextPath, next, modifiers));
            } else {
                System.err.println("[ConsoleInputCapture] Unknown escape sequence: ESC " + (char)next);
            }
        }
    }
    
    /**
     * Process CSI sequence (ESC[...)
     * 
     * Enhanced to detect modifier keys encoded in sequences.
     * Format: ESC[1;modifiers<key> where modifiers is a bitmask:
     * - 2: Shift
     * - 3: Alt
     * - 4: Shift+Alt
     * - 5: Ctrl
     * - 6: Shift+Ctrl
     * - 7: Alt+Ctrl
     * - 8: Shift+Alt+Ctrl
     */
    private void processCsiSequence(NonBlockingReader reader) throws IOException {
        int code = reader.read(10);
        
        // Check for modifier sequences
        if (code >= '0' && code <= '9') {
            // Could be a sequence with modifiers: ESC[1;mod<key>
            StringBuilder numBuf = new StringBuilder();
            numBuf.append((char)code);
            
            // Read until we hit a non-digit
            int next = reader.read(10);
            while (next >= '0' && next <= '9') {
                numBuf.append((char)next);
                next = reader.read(10);
            }
            
            if (next == ';') {
                // Modifier sequence: ESC[1;mod<key>
                int modCode = reader.read(10) - '0';
                int keyCode = reader.read(10);
                
                int modifiers = decodeModifiers(modCode);
                emitKeyWithModifiers(keyCode, modifiers);
            } else if (next == '~') {
                // Simple number sequence: ESC[num~
                int num = Integer.parseInt(numBuf.toString());
                emitKeyDownUp(getKeyForTildeSequence(num), currentModifiers);
            }
            return;
        }
        
        // Standard sequences (no modifiers)
        switch (code) {
            case 'A' -> emitKeyDownUp(38, currentModifiers); // Up arrow (VK_UP)
            case 'B' -> emitKeyDownUp(40, currentModifiers); // Down arrow (VK_DOWN)
            case 'C' -> emitKeyDownUp(39, currentModifiers); // Right arrow (VK_RIGHT)
            case 'D' -> emitKeyDownUp(37, currentModifiers); // Left arrow (VK_LEFT)
            case 'H' -> emitKeyDownUp(36, currentModifiers); // Home (VK_HOME)
            case 'F' -> emitKeyDownUp(35, currentModifiers); // End (VK_END)
            
            default -> {
                System.err.println("[ConsoleInputCapture] Unknown CSI sequence: ESC[" + (char)code);
            }
        }
    }
    
    /**
     * Decode terminal modifier code to our modifier flags
     * Terminal codes: 2=Shift, 3=Alt, 5=Ctrl, 6=Shift+Ctrl, etc.
     */
    private int decodeModifiers(int terminalModCode) {
        int mods = 0;
        
        // Terminal uses 1-based: subtract 1 to get bitmask
        int mask = terminalModCode - 1;
        
        if ((mask & 1) != 0) mods |= MOD_SHIFT;
        if ((mask & 2) != 0) mods |= MOD_ALT;
        if ((mask & 4) != 0) mods |= MOD_CONTROL;
        
        return mods;
    }
    
    /**
     * Get key code for tilde-terminated sequence (ESC[n~)
     */
    private int getKeyForTildeSequence(int num) {
        return switch (num) {
            case 2 -> 45;   // Insert (VK_INSERT)
            case 3 -> 46;   // Delete (VK_DELETE)
            case 5 -> 33;   // Page Up (VK_PAGE_UP)
            case 6 -> 34;   // Page Down (VK_PAGE_DOWN)
            case 11 -> 112; // F1
            case 12 -> 113; // F2
            case 13 -> 114; // F3
            case 14 -> 115; // F4
            case 15 -> 116; // F5
            case 17 -> 118; // F6
            case 18 -> 119; // F7
            case 19 -> 120; // F8
            case 20 -> 121; // F9
            case 21 -> 122; // F10
            case 23 -> 123; // F11
            case 24 -> 124; // F12
            default -> 0;
        };
    }
    
    /**
     * Emit key events with specific modifiers
     */
    private void emitKeyWithModifiers(int keyCode, int modifiers) {
        int vkCode = switch (keyCode) {
            case 'A' -> 38; // Up
            case 'B' -> 40; // Down
            case 'C' -> 39; // Right
            case 'D' -> 37; // Left
            case 'H' -> 36; // Home
            case 'F' -> 35; // End
            default -> 0;
        };
        
        if (vkCode != 0) {
            emitKeyDownUp(vkCode, modifiers);
        }
    }
    
    /**
     * Process SS3 sequence (ESC O...)
     * Alternative encoding for some keys (especially function keys)
     */
    private void processSs3Sequence(NonBlockingReader reader) throws IOException {
        int code = reader.read(10);
        
        switch (code) {
            case 'P' -> emitKeyDownUp(112); // F1
            case 'Q' -> emitKeyDownUp(113); // F2
            case 'R' -> emitKeyDownUp(114); // F3
            case 'S' -> emitKeyDownUp(115); // F4
            default -> {
                System.err.println("[ConsoleInputCapture] Unknown SS3 sequence: ESC O" + (char)code);
            }
        }
    }
    
    // ===== EVENT EMISSION =====
    
    /**
     * Emit key down and key up events (simulates key press)
     */
    private void emitKeyDownUp(int key) {
        emitKeyDownUp(key, currentModifiers);
    }
    
    /**
     * Emit key down and key up events with specific modifiers
     */
    private void emitKeyDownUp(int key, int modifiers) {
        emitEvent(ConsoleEventFactory.createKeyDown(contextPath, key, 0, modifiers));
        emitEvent(ConsoleEventFactory.createKeyUp(contextPath, key, 0, modifiers));
    }
    
    /**
     * Emit event to registered consumer
     */
    private void emitEvent(RoutedEvent event) {
        Consumer<RoutedEvent> consumer = getEventConsumer();
        if (consumer != null) {
            consumer.accept(event);
        }
    }
    
    // ===== OVERRIDES =====
    
    /**
     * StreamChannel not used in console mode - we emit events directly
     */
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        System.err.println("[ConsoleInputCapture] StreamChannel not supported - events are emitted directly from JLine3");
        throw new UnsupportedOperationException("ConsoleInputCapture does not use StreamChannel");
    }
    
    /**
     * Input sources don't receive messages
     */
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void release() {
        stop();
        super.release();
    }
    
    @Override
    public boolean isActive() {
        return capturing;
    }
    
    /**
     * Stop capturing input
     */
    public void stop() {
        if (!capturing) return;
        
        capturing = false;
        executor.shutdown();
        
        System.out.println("[ConsoleInputCapture] Stopped");
    }
}