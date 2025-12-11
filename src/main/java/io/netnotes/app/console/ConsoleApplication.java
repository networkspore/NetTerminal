package io.netnotes.app.console;

import io.netnotes.engine.core.system.SystemProcess;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * ConsoleApplication - Main entry point for console-based application
 * 
 * Responsibilities:
 * 1. Detect if graphics environment is available
 * 2. Initialize console/terminal infrastructure (JLine3)
 * 3. Bootstrap the engine with ConsoleUIRenderer
 * 4. Wire up console input capture (direct event emission)
 * 5. Handle graceful shutdown
 * 
 * Simplified Architecture:
 * ConsoleApplication
 *   ↓
 * ConsoleUIRenderer (terminal rendering)
 *   ↓
 * SystemProcess (engine bootstrap)
 *   ↓
 * SystemTerminalContainer (screens, menus)
 *   ↑
 * ConsoleInputCapture (extends KeyboardInput, directly emits RoutedEvents)
 * 
 * Usage:
 * java -jar netnotes-console.jar
 */
public class ConsoleApplication {
    
    private ConsoleUIRenderer uiRenderer;
    private ConsoleInputCapture inputCapture;
    private SystemProcess systemProcess;
    private volatile boolean running = false;
    
    public static void main(String[] args) {
        ConsoleApplication app = new ConsoleApplication();
        
        try {
            app.start().join();
        } catch (Exception e) {
            System.err.println("Application failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Start the application
     */
    public CompletableFuture<Void> start() {
        System.out.println("=================================================");
        System.out.println("                   Netnotes");
        System.out.println("=================================================");
        System.out.println();
        
        // Check environment
        if (isGraphicsAvailable()) {
            System.out.println("Graphics environment detected.");
            System.out.println("Note: Running in console mode. Use GUI launcher for graphical interface.");
            System.out.println();
        }
        
        running = true;
        
        return CompletableFuture.runAsync(this::initialize)
            .thenCompose(v -> bootstrap())
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
    private void initialize() {
        try {
            System.out.println("[ConsoleApplication] Initializing terminal...");
            
            // Create UI renderer (initializes JLine3 terminal)
            uiRenderer = new ConsoleUIRenderer();
            
            Terminal terminal = uiRenderer.getTerminal();
            
            // Create input capture (extends KeyboardInput, directly emits events)
            inputCapture = new ConsoleInputCapture(terminal, "console-keyboard");
            
            System.out.println("[ConsoleApplication] Terminal initialized");
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize terminal", e);
        }
    }
    
    /**
     * Bootstrap the engine
     */
    private CompletableFuture<Void> bootstrap() {
        System.out.println("[ConsoleApplication] Bootstrapping engine...");
        
        // Bootstrap SystemProcess with console renderer and input capture
        // inputCapture extends KeyboardInput, so it works directly
        return SystemProcess.bootstrap(inputCapture, uiRenderer)
            .thenApply(process -> {
                this.systemProcess = process;
                System.out.println("[ConsoleApplication] Engine bootstrap complete");
                return null;
            });
    }
    
    /**
     * Open the system terminal (main UI)
     */
    private CompletableFuture<Void> openSystemTerminal() {
        System.out.println("[ConsoleApplication] Opening system terminal...");
        
        // Wait for system to be ready
        return waitForSystemReady()
            .thenCompose(v -> {
                // Build command message
                io.netnotes.engine.noteBytes.collections.NoteBytesMap command = 
                    new io.netnotes.engine.noteBytes.collections.NoteBytesMap();
                command.put(io.netnotes.engine.messaging.NoteMessaging.Keys.CMD, 
                    SystemProcess.SYSTEM_INIT_CMDS.OPEN_TERMINAL);
                
                // Request terminal open
                return systemProcess.request(
                    systemProcess.getContextPath(),
                    command.toNoteBytes(),
                    java.time.Duration.ofSeconds(5)
                );
            })
            .thenCompose(response -> {
                // Parse response
                io.netnotes.engine.noteBytes.collections.NoteBytesMap resp = 
                    response.getPayload().getAsNoteBytesMap();
                
                io.netnotes.engine.noteBytes.NoteBytes status = 
                    resp.get(io.netnotes.engine.messaging.NoteMessaging.Keys.STATUS);
                
                if (status != null && status.equals(
                    io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages.SUCCESS)) {
                    
                    System.out.println("[ConsoleApplication] System terminal opened successfully");
                    return CompletableFuture.completedFuture(null);
                } else {
                    io.netnotes.engine.noteBytes.NoteBytes errorMsg = 
                        resp.get(io.netnotes.engine.messaging.NoteMessaging.Keys.MSG);
                    String error = errorMsg != null ? errorMsg.getAsString() : "Unknown error";
                    
                    throw new RuntimeException("Failed to open terminal: " + error);
                }
            });
    }
    
    /**
     * Wait for system to be fully initialized
     */
    private CompletableFuture<Void> waitForSystemReady() {
        return CompletableFuture.runAsync(() -> {
            int attempts = 0;
            while (!systemProcess.isReady() && attempts < 50) {
                try {
                    Thread.sleep(100);
                    attempts++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for system", e);
                }
            }
            
            if (!systemProcess.isReady()) {
                throw new RuntimeException("System failed to become ready");
            }
        });
    }
    
    /**
     * Wait for shutdown signal
     */
    private CompletableFuture<Void> waitForShutdown() {
        System.out.println("[ConsoleApplication] Application running. Press Ctrl+C to exit.");
        
        // Setup shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[ConsoleApplication] Shutdown signal received");
            shutdown();
        }));
        
        // Wait indefinitely
        return new CompletableFuture<>();
    }
    
    /**
     * Graceful shutdown
     */
    private void shutdown() {
        if (!running) return;
        
        running = false;
        
        System.out.println("[ConsoleApplication] Shutting down...");
        
        try {
            // Stop input capture
            if (inputCapture != null) {
                inputCapture.stop();
            }
            
            // Shutdown UI renderer
            if (uiRenderer != null) {
                uiRenderer.shutdown();
            }
            
            // Kill system process
            if (systemProcess != null) {
                systemProcess.kill();
            }
            
            System.out.println("[ConsoleApplication] Shutdown complete");
            
        } catch (Exception e) {
            System.err.println("[ConsoleApplication] Error during shutdown: " + e.getMessage());
        }
    }
    
    /**
     * Check if graphics environment is available
     * Used to inform user about GUI vs console mode
     */
    private boolean isGraphicsAvailable() {
        try {
            return !java.awt.GraphicsEnvironment.isHeadless();
        } catch (Exception e) {
            return false;
        }
    }
}