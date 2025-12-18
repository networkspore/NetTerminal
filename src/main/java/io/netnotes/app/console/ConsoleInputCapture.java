package io.netnotes.app.console;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.input.KeyboardInput;
import io.netnotes.engine.io.input.events.*;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.streams.StreamUtils;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;

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
public class ConsoleInputCapture extends KeyboardInput {
    
    private final Terminal terminal;
    private final ExecutorService executor;
    private volatile boolean capturing = false;

    private CompletableFuture<Void> captureStarted = new CompletableFuture<>();
    private Future<?> capturingFuture = null;
    private Consumer<Void> shutdownConsumer;
    private InputStream input = null;
    private ConsoleInputReader reader = null;

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
            
            // Create reader for this input stream
            reader = new ConsoleInputReader(input);
            
            Log.logMsg("[ConsoleInputCapture] Starting read loop...");
      
            int totalEvents = 0;
            while (capturing && !Thread.currentThread().isInterrupted()) {
                try {
                    // Read next input - gets both raw char and events
                    ConsoleInputReader.ReadResult result = reader.readNext(contextPath);
                    
                    Log.logMsg("[ConsoleInputCapture] input");

                    // Check for EOF
                    if (result.isEOF()) {
                        break;
                    }
                    
                    // Handle Ctrl+C specially - trigger shutdown without emitting events
                    if (result.isCtrlC()) {
                
                        if (shutdownConsumer != null) {
                            shutdownConsumer.accept(null);
                        }
                        continue; // Don't emit events for Ctrl+C
                    }
                    
                    
                    // Emit all events from this read
                    for (RoutedEvent event : result.events) {
                        emitEvent(event);
                    }
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
     * Emit event to registered consumer
     * Clean separation: reader creates, capture routes
     */
    private void emitEvent(RoutedEvent event) {
        Consumer<RoutedEvent> consumer = getEventConsumer();
        if (consumer != null) {
            consumer.accept(event);
        }
    }
    
    /**
     * Set consumer for Ctrl+C shutdown signal
     */
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
        
        // Signal process completion
        complete();
    }
}