package io.netnotes.gui.nvg.core;

import io.netnotes.gui.nvg.input.*;
import io.netnotes.gui.nvg.input.events.InputRecord;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.state.BitFlagStateMachine;

import java.util.*;
import java.util.concurrent.*;

/**
 * ProcessContainer - The root process execution environment
 * 
 * This is the base controller that:
 * - Manages ALL processes (including CommandCenter)
 * - Routes input to appropriate processes
 * - Coordinates global state
 * - Provides execution context
 * 
 * CommandCenter is just one process running inside this container.
 */
public class ProcessContainer {
    // Core components
    private final ProcessManager processManager;
    private final ProcessInputRouter processInputRouter;
    private final BitFlagStateMachine stateMachine;
    private final InputSourceRegistry registry;
    private final InputNode containerInputNode;
    private final InputNetwork inputNetwork;
    
    // Context path for this container
    private final ContextPath rootPath;
    
    // Source tracking
    private NoteBytesReadOnly primaryInputSourceId;
    private InputSourceManager primarySourceManager;
    private final String id;
    
    // Process tracking
    private final Map<Class<?>, Process> singletonProcesses = new ConcurrentHashMap<>();
    
    public ProcessContainer(String id, ContextPath rootPath, 
                           InputNetwork inputNetwork, Executor executor) {
        this.id = id;
        this.rootPath = rootPath;
        this.inputNetwork = inputNetwork;
        this.stateMachine = new BitFlagStateMachine(id);
        this.registry = InputSourceRegistry.getInstance();
        
        this.processManager = new ProcessManager(this, stateMachine);
        this.processInputRouter = new ProcessInputRouter(processManager);
        
        try {
            this.containerInputNode = inputNetwork.createNode("container:" + id);
            containerInputNode.start();
            startEventProcessor();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create container input node", e);
        }
        
        // Initialize container state
        StateMachineScopes.setContainerFlags(stateMachine, 
            StateMachineScopes.Container.ACTIVE | 
            StateMachineScopes.Container.ENABLED |
            StateMachineScopes.Container.BUBBLE_MODE);
        
        // Link state machine to registry
        registry.linkToStateMachine(() -> stateMachine.getState());
    }
    
    /**
     * Start processing events from the container's input node
     */
    private void startEventProcessor() {
        Thread processor = new Thread(() -> {
            BlockingQueue<InputRecord> queue = containerInputNode.getReadQueue();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    InputRecord record = queue.take();
                    RawEvent event = new RawEvent(record);
                    processInputRouter.routeInput(event, rootPath);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("Error processing input event: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, "ProcessContainer-" + id + "-InputProcessor");
        processor.setDaemon(true);
        processor.start();
    }
    
    /**
     * Register input source and connect to container
     * 
     * @param window GLFW window handle
     * @param sourcePath Context path for this source
     */
    public void registerInputSource(long window, ContextPath sourcePath) {
        // Register source in registry
        InputSourceCapabilities caps = new InputSourceCapabilities.Builder("PrimaryInput")
            .enableKeyboard()
            .enableMouse()
            .enableScroll()
            .withScanCodes()
            .providesAbsoluteCoordinates()
            .providesRelativeCoordinates()
            .build();
        
        primaryInputSourceId = registry.registerSource(
            "PrimaryInput",
            caps,
            sourcePath,
            true
        );
        
        registry.activateSource(primaryInputSourceId);
        
        // Create event queue that will be fed by InputSourceManager
        BlockingQueue<CompletableFuture<byte[]>> eventQueue = new LinkedBlockingQueue<>();
        
        // Create InputSourceManager - this sets up GLFW callbacks
        primarySourceManager = new InputSourceManager(
            window,
            primaryInputSourceId.getAsInt(),
            eventQueue
        );
        
        // Connect the source manager's output queue to our container's input node
        connectSourceToContainer(eventQueue);
        
        System.out.println("Registered input source: " + primaryInputSourceId.getAsInt() + 
                         " at path: " + sourcePath);
    }
    
    /**
     * Connect a source's event queue to the container's input node
     * This creates the data flow: GLFW callbacks → SourceManager → eventQueue → InputNode → ProcessRouter
     */
    private void connectSourceToContainer(BlockingQueue<CompletableFuture<byte[]>> sourceQueue) {
        Thread connector = new Thread(() -> {
            BlockingQueue<CompletableFuture<byte[]>> nodeWriteQueue = 
                containerInputNode.getWriteQueue();
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Take event from source manager
                    CompletableFuture<byte[]> futureEvent = sourceQueue.take();
                    
                    // Pass it to the input node
                    nodeWriteQueue.put(futureEvent);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("Error connecting source to container: " + e.getMessage());
                }
            }
        }, "SourceConnector-" + id);
        connector.setDaemon(true);
        connector.start();
    }
    
    /**
     * Enable/disable input capture
     */
    public void setInputEnabled(boolean enabled) {
        if (primarySourceManager != null) {
            if (enabled) {
                primarySourceManager.enable();
            } else {
                primarySourceManager.disable();
            }
        }
        
        if (enabled) {
            stateMachine.setFlag(StateMachineScopes.Container.toBigInteger(
                StateMachineScopes.Container.ENABLED));
        } else {
            stateMachine.clearFlag(StateMachineScopes.Container.toBigInteger(
                StateMachineScopes.Container.ENABLED));
        }
    }
    
    /**
     * Check if input is enabled
     */
    public boolean isInputEnabled() {
        return StateMachineScopes.hasContainerFlag(stateMachine, 
            StateMachineScopes.Container.ENABLED);
    }
    
    /**
     * Start a process
     */
    public int startProcess(Process process) {
        return processManager.startProcess(process);
    }
    
    /**
     * Start or get a singleton process (like CommandCenter)
     */
    public <T extends Process> T getOrCreateSingleton(Class<T> processClass, 
                                                        ProcessFactory<T> factory) {
        @SuppressWarnings("unchecked")
        T existing = (T) singletonProcesses.get(processClass);
        
        if (existing != null && existing.isAlive()) {
            return existing;
        }
        
        T newProcess = factory.create();
        singletonProcesses.put(processClass, newProcess);
        processManager.startProcess(newProcess);
        
        return newProcess;
    }
    
    /**
     * Kill a process
     */
    public boolean killProcess(int pid) {
        return processManager.killProcess(pid);
    }
    
    /**
     * Get process manager
     */
    public ProcessManager getProcessManager() {
        return processManager;
    }
    
    /**
     * Get state machine
     */
    public BitFlagStateMachine getStateMachine() {
        return stateMachine;
    }
    
    /**
     * Get root path
     */
    public ContextPath getRootPath() {
        return rootPath;
    }
    
    /**
     * Get the input network
     */
    public InputNetwork getInputNetwork() {
        return inputNetwork;
    }
    
    /**
     * Get the container's input node
     */
    public InputNode getContainerInputNode() {
        return containerInputNode;
    }
    
    /**
     * Get the primary input source manager (for advanced control)
     */
    public InputSourceManager getPrimarySourceManager() {
        return primarySourceManager;
    }
    
    /**
     * Shutdown container
     */
    public void shutdown() {
        // Set shutting down flag
        stateMachine.setFlag(StateMachineScopes.Container.toBigInteger(
            StateMachineScopes.Container.SHUTTING_DOWN));
        
        // Stop input
        if (primarySourceManager != null) {
            primarySourceManager.cleanup();
            primarySourceManager = null;
        }
        
        // Shutdown all processes
        processManager.shutdownAll();
        
        // Unregister source
        if (primaryInputSourceId != null) {
            registry.unregisterSource(primaryInputSourceId);
        }
        
        // Close input node
        if (containerInputNode != null) {
            containerInputNode.close();
        }
        
        // Clear container state
        stateMachine.clearFlag(StateMachineScopes.Container.toBigInteger(
            StateMachineScopes.Container.ACTIVE));
    }
    
    /**
     * Factory interface for creating processes
     */
    @FunctionalInterface
    public interface ProcessFactory<T extends Process> {
        T create();
    }
}