package io.netnotes.app.console;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.input.Keyboard.KeyCode;
import io.netnotes.engine.io.input.KeyboardInput;
import io.netnotes.engine.io.input.events.*;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
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
    private static final int MOD_ALT = 0x0004;


    
    private final Terminal terminal;
    private final ExecutorService executor;
    private volatile boolean capturing = false;

    private CompletableFuture<Void> captureStarted = new CompletableFuture<>();
    private Future<?> capturingFuture = null;
    // Modifier state tracking (best effort)
    private Consumer<Void> shutdownConsumer;


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

        if (isCaptureLoopRunning()) {
            Log.logMsg("[ConsoleInputCapture] Capture already started at: " + contextPath);
            return captureStarted;
        }
  
        Log.logMsg("[ConsoleInputCapture] Starting capture at: " + contextPath);
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
     */
    private void captureLoop() {
        Log.logMsg("[ConsoleInputCapture] captureLoop() entered");
        
        // Get the raw input stream
        java.io.InputStream input = terminal.input();
        Log.logMsg("[ConsoleInputCapture] Got InputStream: " + input);
        
        try {
  
            captureStarted.complete(null);
            
            Log.logMsg("[ConsoleInputCapture] Capture loop started and ready, entering read loop");
            Log.logMsg("[ConsoleInputCapture] Terminal type: " + terminal.getType());
            Log.logMsg("[ConsoleInputCapture] Terminal size: " + terminal.getSize());
            Log.logMsg("[ConsoleInputCapture] Thread: " + Thread.currentThread().getName());
            
            // Verify terminal is in raw mode
            org.jline.terminal.Attributes attrs = terminal.getAttributes();
            boolean rawMode = !attrs.getLocalFlag(org.jline.terminal.Attributes.LocalFlag.ICANON);
            Log.logMsg("[ConsoleInputCapture] Raw mode active: " + rawMode);
            
            if (!rawMode) {
                Log.logError("[ConsoleInputCapture] WARNING: Terminal NOT in raw mode!");
            }
            
            Log.logMsg("[ConsoleInputCapture] Starting blocking read loop...");
            Log.logMsg("[ConsoleInputCapture] Press any key to test input...");
            
            // Force flush all output before starting read
            System.out.flush();
            System.err.flush();
            
            int totalReads = 0;
            
            while (capturing && !Thread.currentThread().isInterrupted()) {
                try {
                    // Simple blocking read - will return when data is available
                    // This is interruptible, so stop() can kill it
                    System.err.println("[ConsoleInputCapture] About to call blocking read() #" + totalReads);
                    System.err.flush();
                    
                    int ch = input.read();
                    
                    System.err.println("[ConsoleInputCapture] read() returned: " + ch);
                    System.err.flush();
                    
                    if (ch == -1) {
                        // EOF
                        Log.logMsg("[ConsoleInputCapture] EOF detected, exiting");
                        break;
                    }
                    
                    Log.logMsg("[ConsoleInputCapture] *** RECEIVED INPUT: " + ch + 
                        " (char: '" + (ch >= 32 && ch <= 126 ? (char)ch : "?") + "') ***");
                    
                    totalReads++;
                    
                    // Process the input
                    processInput(ch);
                    
                } catch (InterruptedIOException e) {
                    Log.logMsg("[ConsoleInputCapture] Read interrupted");
                    break;
                } catch (IOException e) {
                    if (capturing) {
                        Log.logError("[ConsoleInputCapture] IOException: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                }
            }
            
            Log.logMsg("[ConsoleInputCapture] Exited read loop. Total reads: " + totalReads);
            
        } catch (Exception e) {
            Log.logError("[ConsoleInputCapture] Fatal error in capture loop: " + e.getMessage());
            e.printStackTrace();
            captureStarted.completeExceptionally(e);
        } finally {
            capturing = false;
            captureStarted = new CompletableFuture<>();
            Log.logMsg("[ConsoleInputCapture] Capture loop ended");
        }
    }
     
    // ===== INPUT PROCESSING =====
    
    /**
     * Process a single input character and emit appropriate events
     */
    private void processInput(int ch) {
        // Ctrl+C detection
        if (ch == 3) {
            System.err.println("[ConsoleInputCapture] Ctrl+C - shutting down");
            if (shutdownConsumer != null) {
                shutdownConsumer.accept(null);
            }
            return;
        }
        
        try {
            // ESC sequence
            if (ch == 27) {
                processEscapeSequence();
                return;
            }
            
            // Printable ASCII (32-126)
            if (ch >= 32 && ch <= 126) {
                ConsoleEventFactory.emitPrintableChar(contextPath, ch, this::emitEvent);
                return;
            }
            
            // Special keys (Enter, Backspace, Tab)
            if (ch == 13 || ch == 10 || ch == 8 || ch == 127 || ch == 9) {
                ConsoleEventFactory.emitSpecialKey(contextPath, ch, this::emitEvent);
                return;
            }
            
            // Ctrl+A through Ctrl+Z (ASCII 1-26)
            if (ch >= 1 && ch <= 26) {
                ConsoleEventFactory.emitControlChar(contextPath, ch, this::emitEvent);
            }
            
        } catch (Exception e) {
            System.err.println("[ConsoleInputCapture] Error: " + e.getMessage());
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
        int next = reader.read(10);
        
        if (next == -1 || next == -2) {
            // Just ESC key by itself
            ConsoleEventFactory.emitKeyPress(contextPath, KeyCode.ESCAPE, 0, this::emitEvent);
            return;
        }
        
        if (next == '[') {
            // CSI sequence
            processCsiSequence(reader);
        } else if (next == 'O') {
            // SS3 sequence
            processSs3Sequence(reader);
        } else if (next >= 32 && next <= 126) {
            // Alt+key (ESC followed by printable char)
            int[] hidMapping = ConsoleEventFactory.asciiToHid(next);
            int hidCode = hidMapping[0];
            int baseMods = hidMapping[1];
            
            if (hidCode != KeyCode.NONE) {
                ConsoleEventFactory.emitKeyPress(contextPath, hidCode, baseMods | MOD_ALT, this::emitEvent);
            }
            
            // Also emit char event with Alt modifier
            emitEvent(ConsoleEventFactory.createKeyChar(contextPath, next, baseMods | MOD_ALT));
        } else {
            System.err.println("[ConsoleInputCapture] Unknown ESC sequence: ESC " + next);
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
        
        // Modifier sequences: ESC[1;mod<key>
        if (code >= '0' && code <= '9') {
            StringBuilder numBuf = new StringBuilder();
            numBuf.append((char)code);
            
            int next = reader.read(10);
            while (next >= '0' && next <= '9') {
                numBuf.append((char)next);
                next = reader.read(10);
            }
            
            if (next == ';') {
                int modCode = reader.read(10) - '0';
                int keyLetter = reader.read(10);
                int hidCode = ConsoleEventFactory.csiLetterToHid((char)keyLetter);
                int mods = ConsoleEventFactory.decodeTerminalModifiers(modCode);
                
                if (hidCode != 0) {
                    ConsoleEventFactory.emitKeyPress(contextPath, hidCode, mods, this::emitEvent);
                }
            } else if (next == '~') {
                int num = Integer.parseInt(numBuf.toString());
                int hidCode = ConsoleEventFactory.tildeSequenceToHid(num);
                if (hidCode != 0) {
                    ConsoleEventFactory.emitKeyPress(contextPath, hidCode, 0, this::emitEvent);
                }
            }
            return;
        }
        
        // Standard sequences (no modifiers)
        int hidCode = ConsoleEventFactory.csiLetterToHid((char)code);
        if (hidCode != 0) {
            ConsoleEventFactory.emitKeyPress(contextPath, hidCode, 0, this::emitEvent);
        }
    }

    private void processSs3Sequence(NonBlockingReader reader) throws IOException {
        int code = reader.read(10);
        int hidCode = ConsoleEventFactory.ss3LetterToHid((char)code);
        if (hidCode != 0) {
            ConsoleEventFactory.emitKeyPress(contextPath, hidCode, 0, this::emitEvent);
        }
    }
    
    
    

    
    /**
     * Emit event to registered consumer
     */
    private void emitEvent(RoutedEvent event) {
        if(event instanceof KeyDownEvent keyDownEvent){
            System.err.println("event happened");
           // Log.logMsg("[ConsoleInputCapture] emitEvent:" + Keyboard.getCharBytes(keyDownEvent.getKeyCodeBytes()));
        }
        Consumer<RoutedEvent> consumer = getEventConsumer();
        if (consumer != null) {
            consumer.accept(event);
        }
    }
    
    public void setShutdownConsumer(Consumer<Void> consumer) {
        this.shutdownConsumer = consumer;
    }
    

    // ===== OVERRIDES =====
    
    /**
     * StreamChannel not used in console mode - we emit events directly
     */
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {

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
        
        Log.logMsg("[ConsoleInputCapture] Stopping...");
        capturing = false;
        capturingFuture.cancel(true);
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
        
        // Signal process completion
        complete();
    }

}