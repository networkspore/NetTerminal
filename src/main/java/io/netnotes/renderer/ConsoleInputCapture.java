package io.netnotes.renderer;


import io.netnotes.engine.io.input.Keyboard.KeyCode;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.streams.StreamUtils;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * ConsoleInputCapture - Console/terminal keyboard input source
 * 
 * Extends KeyboardInput to capture raw keyboard input from JLine3 terminal.
 * Uses ConsoleInputReader to parse input into events.
 * 
 * Pattern:
 * 1. Reader reads raw bytes and returns ReadResult (raw char + events)
 * 2. Capture checks raw char for special control codes (Ctrl+C, Ctrl+D)
 * 3. Capture emits events to consumers
 * 
 * Responsibilities:
 * - Lifecycle management (start/stop)
 * - Thread management
 * - Terminal configuration validation
 * - Special control character handling (Ctrl+C shutdown)
 * - Event routing
 */
public class ConsoleInputCapture {
    private static final int MOD_ALT = 0x0004;

    private final Terminal terminal;
    private final ExecutorService executor;
    private volatile boolean capturing = false;

    private CompletableFuture<Void> captureStarted = new CompletableFuture<>();
    private Future<?> capturingFuture = null;

    private InputStream input = null;
    private Consumer<NoteBytesMap> emitter;
    private Runnable onCtrlC = null;

    public ConsoleInputCapture(Terminal terminal, Consumer<NoteBytesMap> emitter) {
        this.emitter = emitter;
        this.terminal = terminal;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "console-input");
            t.setDaemon(true);
            return t;
        });
        
    }
    public void setEmitter(Consumer<NoteBytesMap> emitter){
        this.emitter = emitter;
    }

    public void setOnCtrlC(Runnable onCtrlC){
        this.onCtrlC = onCtrlC;
    }
    // ===== LIFECYCLE =====
    
  
    public CompletableFuture<Void> run() {
        if (isCaptureLoopRunning()) {
           
            return captureStarted;
        }
  
        Log.logMsg("[ConsoleInputCapture] Starting capture at: ");
        capturing = true;
        
        // Start capture loop in background
        capturingFuture = executor.submit(this::captureLoop);

        Log.logMsg("[ConsoleInputCapture] Capture started, returning completed future");
        return captureStarted;
    }

    public boolean isCaptureLoopRunning() {
        return capturingFuture != null && !capturingFuture.isDone() && !capturingFuture.isCancelled();
    }
    
    /**
     * Main capture loop - runs continuously in background thread
     * 
     * Clean pattern:
     * 1. Read from ConsoleInputReader → get ReadResult
     * 2. Check raw character for special handling (Ctrl+C)
     * 3. Emit all events from result
     */
    private void captureLoop() {
        Log.logMsg("[ConsoleInputCapture] captureLoop() entered");
        
        // Get the raw input stream
        input = terminal.input();
        Log.logMsg("[ConsoleInputCapture] Got InputStream: " + input);
        
        try {
            captureStarted.complete(null);
            
            // Verify terminal is in raw mode
            Attributes attrs = terminal.getAttributes();
            boolean rawMode = !attrs.getLocalFlag(Attributes.LocalFlag.ICANON);
            Log.logMsg("[ConsoleInputCapture] Raw mode active: " + rawMode);
            
            if (!rawMode) {
                Log.logError("[ConsoleInputCapture] WARNING: Terminal NOT in raw mode!");
            }
            
    
            
            Log.logMsg("[ConsoleInputCapture] Starting read loop...");
      
            int totalEvents = 0;
            while (capturing && !Thread.currentThread().isInterrupted()) {
                try {
                    // Read next input - gets both raw char and events
                    readNext();
                    Log.logMsg("[ConsoleInputCapture] event captured");
                    totalEvents++;
                } catch (InterruptedIOException e) {
                    Log.logMsg("[ConsoleInputCapture] Read interrupted");
                    break;
                } catch (Exception e) {
                    if (capturing) {
                        Log.logError("[ConsoleInputCapture] Error in read loop: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                }
            }
            
            Log.logMsg("[ConsoleInputCapture] Exited read loop. Total events (grouped): " + totalEvents);
            
        } catch (Exception e) {
            Log.logError("[ConsoleInputCapture] Fatal error in capture loop: " + e.getMessage());
            e.printStackTrace();
            captureStarted.completeExceptionally(e);
        } finally {
            StreamUtils.safeClose(input);
            capturing = false;
            captureStarted = new CompletableFuture<>();
            Log.logMsg("[ConsoleInputCapture] Capture loop ended");
        }
    }


    /**
     * Read and process input, handling UTF-8 multi-byte sequences
     * 
     * @param sourcePath Context path for emitted events
     * @return ReadResult containing raw char, codepoint, and events
     * @throws IOException on read errors
     */
    public void readNext() throws IOException {
        int firstByte = input.read();
        
        if (firstByte == -1) {
            Log.logError("[ConsoleInputReader] End of file reached");
            // EOF
            stop();
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
                return;
            }
            
            // For multi-byte UTF-8, generate KeyChar event only
            emitter.accept(ConsoleEventFactory.createKeyChar(codepoint, 0));

        }
        
        // Single-byte handling (ASCII range)
        // For control characters (1-26, except Tab/LF/CR), return raw char
        if (KeyCode.isControlChar(firstByte) && firstByte != 9 && firstByte != 10 && firstByte != 13) {
            // Check for Ctrl+C before emitting
            if (firstByte == 3) { // Ctrl+C
                handleCtrlC();
                return;
            }
            int hidCode = KeyCode.controlCharToHid(firstByte);
            addKeyPress(hidCode, ConsoleEventFactory.MOD_CONTROL);
            return;
        }
        
        // Process normal ASCII input
        processInput(firstByte);
    }

    private void handleCtrlC() {
        if(onCtrlC != null){
            onCtrlC.run();
        }
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
    private void processInput(int ch) {
        try {
            // ESC sequence
            if (ch == 27) {
                processEscapeSequence();
                return;
            }
            
            // Printable ASCII (32-126)
            if (ch >= 32 && ch <= 126) {
                addPrintableChar(ch);
                return;
            }
            
            // Special keys (Enter, Backspace, Tab)
            if (ch == 13 || ch == 10 || ch == 8 || ch == 127 || ch == 9) {
                addSpecialKey( ch);
                return;
            }
            
        } catch (Exception e) {
            Log.logError("[ConsoleInputReader] Error processing input: " + e.getMessage());
        }
    }
    
    /**
     * Process ANSI escape sequence for special keys
     */
    private void processEscapeSequence() throws IOException {
        int next = input.read();
        
        if (next == -1) {
            // Just ESC key by itself
            addKeyPress(KeyCode.ESCAPE, 0);
            return;
        }
        
        if (next == '[') {
            // CSI sequence
            processCsiSequence();
        } else if (next == 'O') {
            // SS3 sequence
            processSs3Sequence();
        } else if (next >= 32 && next <= 126) {
            // Alt+key (ESC followed by printable char)
            int[] hidMapping = ConsoleEventFactory.asciiToHid(next);
            int hidCode = hidMapping[0];
            int baseMods = hidMapping[1];
            
            if (hidCode != KeyCode.NONE) {
                addKeyPress(hidCode, baseMods | MOD_ALT);
            }
            
            // Also add char event with Alt modifier
            emitter.accept(ConsoleEventFactory.createKeyChar(next, baseMods | MOD_ALT));
        } else {
            Log.logMsg("[ConsoleInputReader] Unknown ESC sequence: ESC " + next);
        }
    }
    
    /**
     * Process CSI sequence (ESC[...)
     */
    private void processCsiSequence() throws IOException {
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
                    addKeyPress(hidCode, mods);
                }
            } else if (next == '~') {
                int num = Integer.parseInt(numBuf.toString());
                int hidCode = ConsoleEventFactory.tildeSequenceToHid(num);
                if (hidCode != 0) {
                    addKeyPress(hidCode, 0);
                }
            }
            return;
        }
        
        // Standard sequences (no modifiers)
        int hidCode = ConsoleEventFactory.csiLetterToHid((char)code);
        if (hidCode != 0) {
            addKeyPress(hidCode, 0);
        }
    }
    
    /**
     * Process SS3 sequence (ESC O ...)
     */
    private void processSs3Sequence() throws IOException {
        int code = input.read();
        int hidCode = ConsoleEventFactory.ss3LetterToHid((char)code);
        if (hidCode != 0) {
            addKeyPress(hidCode, 0);
        }
    }
    
    // ===== EVENT GENERATION HELPERS =====
    
    private void addKeyPress(int hidCode, int modifiers) {
        emitter.accept(ConsoleEventFactory.createKeyDown(hidCode, modifiers));
        emitter.accept(ConsoleEventFactory.createKeyUp(hidCode, modifiers));
    }
    
    private void addPrintableChar(int ascii) {
        int[] hidMapping = ConsoleEventFactory.asciiToHid(ascii);
        int hidCode = hidMapping[0];
        int modifiers = hidMapping[1];
        
        if (hidCode != KeyCode.NONE) {
            // Standard event order: KeyDown → KeyChar → KeyUp
            emitter.accept(ConsoleEventFactory.createKeyDown(hidCode, modifiers));
            emitter.accept(ConsoleEventFactory.createKeyChar(ascii, modifiers));
            emitter.accept(ConsoleEventFactory.createKeyUp(hidCode, modifiers));
        } else {
            // Fallback: just char event if we can't map to HID
            emitter.accept(ConsoleEventFactory.createKeyChar(ascii, modifiers));
        }
    }
    
    private void addSpecialKey(int ascii) {
        int hidCode = switch (ascii) {
            case 13, 10 -> KeyCode.ENTER;
            case 8, 127 -> KeyCode.BACKSPACE;
            case 9 -> KeyCode.TAB;
            case 27 -> KeyCode.ESCAPE;
            default -> KeyCode.NONE;
        };
        
        if (hidCode != KeyCode.NONE) {
            addKeyPress(hidCode, 0);
        }
    }
        
 


    public boolean isActive() {
        return capturing;
    }
    
    /**
     * Stop capturing input
     */
    public void stop() {
        if (!capturing) return;
        
        Log.logMsg("[ConsoleInputCapture] Stopping...");
        capturing = false;
        
        if (capturingFuture != null) {
            capturingFuture.cancel(true);
        }
        
        executor.shutdown();
        
        try {
            // Wait for executor to finish
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                Log.logMsg("[ConsoleInputCapture] Forcing executor shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        
        Log.logMsg("[ConsoleInputCapture] Stopped");
  
    }
}