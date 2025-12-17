package io.netnotes.app.console;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.system.SystemProcess;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import org.jline.terminal.Terminal;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * ConsoleApplication
 * 
 */
public class ConsoleApplication {
    
    private ConsoleUIRenderer uiRenderer;
    private ConsoleInputCapture inputCapture;
    private SystemProcess systemProcess;
    private volatile boolean running = false;
    private final CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();
    private volatile boolean shutdownInProgress = false;
   
    /**
     * Start the application
     */
   public CompletableFuture<Void> start() {
        running = true;
        
        // Setup shutdown hook EARLY (before anything can crash)
        setupShutdownHook();
        
        return initialize()
            .thenCompose(v -> bootstrap())
            .thenCompose(v -> waitForSystemReady())
            .thenCompose(v -> openSystemTerminal())
            .thenCompose(v -> waitForShutdown())
            .exceptionally(ex -> {
                System.err.println("Startup failed: " + ex.getMessage());
                ex.printStackTrace();
                shutdown();
                return null;
            });
    }
    

    /**
     * Initialize console infrastructure
     */
    private CompletableFuture<Void> initialize() {
        try {
            Log.logMsg("[ConsoleApplication] Initializing terminal...");
            
            // Create UI renderer (creates JLine3 terminal)
            uiRenderer = new ConsoleUIRenderer();
            
  
            Log.logMsg("[ConsoleApplication] Setting terminal to raw mode...");
            Terminal terminal = uiRenderer.getTerminal();
            
            org.jline.terminal.Attributes raw = new org.jline.terminal.Attributes(
                terminal.getAttributes()
            );
            
            // Disable canonical mode (line buffering)
            raw.setLocalFlag(org.jline.terminal.Attributes.LocalFlag.ICANON, false);
            
            // Disable echo
            raw.setLocalFlag(org.jline.terminal.Attributes.LocalFlag.ECHO, false);
            
            // Disable signal generation
            raw.setLocalFlag(org.jline.terminal.Attributes.LocalFlag.ISIG, false);
            
            // Disable extended input processing
            raw.setLocalFlag(org.jline.terminal.Attributes.LocalFlag.IEXTEN, false);
            
            // Set minimum characters to read (0 = non-blocking)
            raw.setControlChar(org.jline.terminal.Attributes.ControlChar.VMIN, 0);
            
            // Set timeout in deciseconds (1 = 100ms)
            raw.setControlChar(org.jline.terminal.Attributes.ControlChar.VTIME, 1);
            
            // Apply the attributes
            terminal.setAttributes(raw);
            
            Log.logMsg("[ConsoleApplication] Terminal set to raw mode");
            
            // NOW create input capture - terminal is in raw mode
            inputCapture = new ConsoleInputCapture(terminal, CoreConstants.DEFAULT_KEYBOARD_ID);
            inputCapture.setShutdownConsumer(v -> this.shutdown());
    
            return CompletableFuture.completedFuture(null);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Bootstrap the engine
     */
    private CompletableFuture<Void> bootstrap() {
        Log.logMsg("[ConsoleApplication] Bootstrapping engine...");
        
        // Bootstrap SystemProcess with console renderer and input capture
        // inputCapture extends KeyboardInput, so it works directly
        return SystemProcess.bootstrap(inputCapture, uiRenderer)
            .thenApply(process -> {
                this.systemProcess = process;
                Log.logMsg("[ConsoleApplication] Engine bootstrap complete");
                return null;
            });
    }
    
    /**
     * Wait for system to be fully initialized
     * 
     * FIXED: This now waits for READY state, which is set after services start
     * but before terminal creation (terminal creation is a command, not init)
     */
    private CompletableFuture<Void> waitForSystemReady() {
        Log.logMsg("[ConsoleApplication] Waiting for system to be ready...");
        
        return CompletableFuture.runAsync(() -> {
            int attempts = 0;
            while (!systemProcess.isReady() && attempts < 100) {
                try {
                    Thread.sleep(100);
                    attempts++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for system", e);
                }
            }
            
            if (!systemProcess.isReady()) {
                throw new RuntimeException("System failed to become ready after 10 seconds");
            }
            
            Log.logMsg("[ConsoleApplication] System is ready");
        });
    }
    
    /**
     * Open the system terminal (main UI)
     * 
     * FIXED: This is now a command sent to an already-ready system
     */
    private CompletableFuture<Void> openSystemTerminal() {
        Log.logMsg("[ConsoleApplication] Opening system terminal...");
        
        // Build command message
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, SystemProcess.SYSTEM_INIT_CMDS.OPEN_TERMINAL);
        
        // Request terminal open
        return systemProcess.request(
            systemProcess.getContextPath(),
            command.toNoteBytes(),
            Duration.ofSeconds(5)
        )
        .thenCompose(response -> {
            // Parse response
            NoteBytesMap resp = 
                response.getPayload().getAsNoteBytesMap();
            
            NoteBytes status = resp.get(Keys.STATUS);
            
            if (status != null && status.equals(
                ProtocolMesssages.SUCCESS)) {
                
                Log.logMsg("[ConsoleApplication] System terminal opened successfully");
                return CompletableFuture.completedFuture(null);
            } else {
                NoteBytes errorMsg =  resp.get(Keys.MSG);
                String error = errorMsg != null ? errorMsg.getAsString() : "Unknown error";
                
                throw new RuntimeException("Failed to open terminal: " + error);
            }
        });
    }
    
    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!shutdownInProgress) {
                System.err.println("\n[ConsoleApplication] Shutdown signal received");
                emergencyShutdown();
            }
        }, "shutdown-hook"));
        
        // Also handle uncaught exceptions
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("[ConsoleApplication] Uncaught exception in thread " + 
                thread.getName() + ": " + throwable.getMessage());
            throwable.printStackTrace();
            emergencyShutdown();
        });
    }

    /**
     * Wait for shutdown signal
     */
    private CompletableFuture<Void> waitForShutdown() {
        Log.logMsg("[ConsoleApplication] Application running. Press Ctrl+C to exit.");
        
        // Return the shutdownFuture instead of a never-completing future
        return shutdownFuture;
    }

    public boolean isRunning(){
        return running;
    }
    
    /**
     * Graceful shutdown
     */
    /**
     * Graceful shutdown
     */
    public void shutdown() {
        if (shutdownInProgress) return;
        shutdownInProgress = true;
        running = false;
        
        Log.logMsg("[ConsoleApplication] Shutting down...");
        
        try {
            // Stop input capture first (stops reading from terminal)
            if (inputCapture != null) {
                inputCapture.stop();
            }
            
            // Kill system process
            if (systemProcess != null) {
                systemProcess.kill();
            }
            
            // Shutdown UI renderer (restores terminal)
            if (uiRenderer != null) {
                uiRenderer.shutdown();
            }
            
            Log.logMsg("[ConsoleApplication] Shutdown complete");
            
        } catch (Exception e) {
            System.err.println("[ConsoleApplication] Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Complete the shutdown future to unblock waitForShutdown()
            shutdownFuture.complete(null);
            
            // Force exit after a short delay
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    System.exit(0);
                } catch (InterruptedException e) {
                    System.exit(0);
                }
            }, "exit-thread").start();
        }
    }

    /**
     * Emergency shutdown - minimal cleanup, just restore terminal
     */
    public void emergencyShutdown() {
        if (shutdownInProgress) return;
        shutdownInProgress = true;
        
        System.err.println("[ConsoleApplication] Emergency shutdown...");
        
        // Just restore the terminal, don't do anything fancy
        if (uiRenderer != null) {
            try {
                uiRenderer.shutdown();
                
            } catch (Exception e) {
                // Ignore - we're crashing anyway
            }
        }
        
        // Complete shutdown future
        shutdownFuture.complete(null);
        
        // Force exit immediately
        System.exit(1);
    }
 
}