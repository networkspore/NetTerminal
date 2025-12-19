package io.netnotes.app.console;

import io.netnotes.engine.core.system.control.containers.*;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.core.system.control.ui.UIReplyExec;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolObjects;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBoolean;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.Terminal.Signal;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ConsoleUIRenderer - Full terminal container manager
 * 
 * Responsibilities:
 * - Create and manage ConsoleContainer instances
 * - Handle all container lifecycle commands
 * - Setup bidirectional streams for containers
 * - Coordinate rendering (differential, rate-limited, debounced)
 * - Broadcast terminal events (resize, etc.) to all containers
 * 
 * Reply Pattern:
 * - All handleMessage operations return CompletableFuture<Void>
 * - Success: future completes normally
 * - Success is handled with replyExec 
 * - Failure: future completes exceptionally with error message
 * - RenderingService converts ERROR execotuib to reply 

 */
public class ConsoleUIRenderer implements UIRenderer {
    private final String description = "JLine3 terminal renderer";
    private final Set<ContainerType> supportedTypes = Set.of(ContainerType.TERMINAL);
    
    // ===== TERMINAL =====
    private final Terminal terminal;
    private final Attributes originalAttributes;
    private volatile int termWidth;
    private volatile int termHeight;
    
    // ===== CONTAINER MANAGEMENT =====
    private final Map<ContainerId, ConsoleContainer> containers = new ConcurrentHashMap<>();
    private final Map<ContextPath, List<ContainerId>> ownerContainers = new ConcurrentHashMap<>();
    private volatile ContainerId focusedContainerId = null;
    private volatile boolean active = false;
    
    // ===== RENDERING =====
    private final ScheduledExecutorService renderScheduler = 
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConsoleRenderer");
            t.setDaemon(true);
            return t;
        });
    private final Map<ContainerId, ScheduledFuture<?>> pendingRenders = new ConcurrentHashMap<>();
    private volatile long lastRenderTime = 0;
    private static final long MIN_RENDER_INTERVAL_MS = 16; // ~60fps
    private Map<NoteBytesReadOnly, RoutedMessageExecutor> msgMap = new ConcurrentHashMap<>();
    private UIReplyExec replyExec;
    /**
     * Constructor
     */
    public ConsoleUIRenderer() throws IOException {
        this.terminal = TerminalBuilder.builder()
            .system(true)
            .encoding("UTF-8")
            .build();
        
        this.termWidth = terminal.getWidth();
        this.termHeight = terminal.getHeight();
        this.originalAttributes = terminal.getAttributes();

        setupHandlers();
        
        
        Log.logMsg("[ConsoleUIRenderer] Terminal created: " + termWidth + "x" + termHeight);
    }
    
    private void setupHandlers(){
        terminal.handle(Signal.WINCH, this::handleTerminalResize);

        msgMap.put(ContainerCommands.CREATE_CONTAINER, this::handleCreateContainer);
        msgMap.put(ContainerCommands.DESTROY_CONTAINER, this::handleDestroyContainer);
        msgMap.put(ContainerCommands.SHOW_CONTAINER, this::handleShowContainer);
        msgMap.put(ContainerCommands.HIDE_CONTAINER, this::handleHideContainer);
        msgMap.put(ContainerCommands.FOCUS_CONTAINER, this::handleFocusContainer);
        msgMap.put(ContainerCommands.MAXIMIZE_CONTAINER, this::handleMaximizeContainer);
        msgMap.put(ContainerCommands.RESTORE_CONTAINER, this::handleRestoreContainer);
        msgMap.put(ContainerCommands.QUERY_CONTAINER, this::handleQueryContainer);
        msgMap.put(ContainerCommands.LIST_CONTAINERS, this::handleListContainers);
    }
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> initialize() {
        if (active) {
            Log.logMsg("[ConsoleUIRenderer] Already initialized");
            return CompletableFuture.completedFuture(null);
        }
        
        active = true;
        
        // Enter alternate screen buffer
        terminal.writer().print("\033[?1049h");
        
        // Setup raw mode
        Attributes raw = new Attributes(originalAttributes);
        raw.setLocalFlag(Attributes.LocalFlag.ICANON, false);
        raw.setLocalFlag(Attributes.LocalFlag.ECHO, false);
        raw.setLocalFlag(Attributes.LocalFlag.ISIG, false);
        raw.setLocalFlag(Attributes.LocalFlag.IEXTEN, false);
        raw.setControlChar(Attributes.ControlChar.VMIN, 0);
        raw.setControlChar(Attributes.ControlChar.VTIME, 1);
        terminal.setAttributes(raw);
        
        // Initial setup
        terminal.writer().print("\033[?25l"); // Hide cursor
        terminal.writer().print("\033[2J\033[H"); // Clear and home
        terminal.flush();
        
        Log.logMsg("[ConsoleUIRenderer] Initialized with alternate buffer");
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> shutdown() {
        active = false;
        
        // Shutdown renderer
        renderScheduler.shutdown();
        try {
            renderScheduler.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Destroy all containers
        List<CompletableFuture<Void>> futures = containers.values().stream()
            .map(Container::destroy)
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                containers.clear();
                ownerContainers.clear();
                focusedContainerId = null;
                
                try {
                    // Show cursor
                    terminal.writer().print("\033[?25h");
                    
                    // Exit alternate buffer
                    terminal.writer().print("\033[?1049l");
                    
                    // Restore attributes
                    terminal.setAttributes(originalAttributes);
                    
                    terminal.flush();
                    terminal.close();
                    
                    Log.logMsg("[ConsoleUIRenderer] Shutdown complete");
                } catch (Exception e) {
                    Log.logError("[ConsoleUIRenderer] Error during shutdown: " + e.getMessage());
                }
            });
    }
    
    @Override
    public boolean isActive() {
        return active;
    }
    
    // ===== CONTAINER MANAGEMENT =====
    
    @Override
    public Container createContainer(
        ContainerId id,
        String title,
        ContainerType type,
        ContextPath ownerPath,
        ContainerConfig config,
        String rendererId
    ) {
        ConsoleContainer container = new ConsoleContainer(
            id, title, type, ownerPath, config,
            rendererId,
            terminal,
            termWidth,
            termHeight
        );
        
        containers.put(id, container);
        
        // Track by owner
        ownerContainers.computeIfAbsent(ownerPath, k -> new ArrayList<>())
            .add(id);
        
        // Set as focused if first container
        if (focusedContainerId == null) {
            focusedContainerId = id;
        }
        
        Log.logMsg("[ConsoleUIRenderer] Container created: " + id);
        
        return container;
    }
    
    @Override
    public Container getContainer(ContainerId id) {
        return containers.get(id);
    }
    
    @Override
    public boolean hasContainer(ContainerId id) {
        return containers.containsKey(id);
    }
    
    @Override
    public List<Container> getAllContainers() {
        return new ArrayList<>(containers.values());
    }
    
    @Override
    public List<Container> getContainersByOwner(ContextPath ownerPath) {
        List<ContainerId> ids = ownerContainers.get(ownerPath);
        if (ids == null) {
            return List.of();
        }
        
        return ids.stream()
            .map(key-> (Container) containers.get(key))
            .filter(Objects::nonNull)
            .toList();
    }
    
    @Override
    public int getContainerCount() {
        return containers.size();
    }
    
    @Override
    public Container getFocusedContainer() {
        return focusedContainerId != null ? containers.get(focusedContainerId) : null;
    }
    
    
    // ===== MESSAGE HANDLING =====
  
    /**
     * Handle container command
     * Returns CompletableFuture<Void> that completes normally on success,
     * exceptionally on failure
     */
    @Override
    public CompletableFuture<Void> handleMessage(NoteBytesMap msg, RoutedPacket packet) {
        NoteBytes cmdBytes = msg.get(Keys.CMD);
        
        if (cmdBytes == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("'cmd' required")
            );
        }
        
        // Dispatch commands
        RoutedMessageExecutor msgExec = msgMap.get(cmdBytes);
        if(msgExec != null){
            return msgExec.execute(msg, packet);
        } else {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown command: " + cmdBytes)
            );
        }
    }
    
    private CompletableFuture<Void> handleCreateContainer(NoteBytesMap msg, RoutedPacket packet) {
        try {
            // Parse parameters
            NoteBytes titleBytes = msg.get(Keys.TITLE);
            NoteBytes typeBytes = msg.get(Keys.TYPE);
            NoteBytes pathBytes = msg.get(Keys.PATH);
            NoteBytes configBytes = msg.get(Keys.CONFIG);
            NoteBytes autoFocusBytes = msg.getOrDefault(ContainerCommands.AUTO_FOCUS, NoteBoolean.FALSE);
            NoteBytes rendererIdBytes = msg.get(ContainerCommands.RENDERER_ID);
            
            String title = titleBytes != null ? titleBytes.getAsString() : "Untitled";
            ContainerType type = typeBytes != null ? 
                ContainerType.valueOf(typeBytes.getAsString()) : ContainerType.TERMINAL;
            ContextPath ownerPath = pathBytes != null ? 
                ContextPath.fromNoteBytes(pathBytes) : null;
            ContainerConfig config = configBytes != null ? 
                ContainerConfig.fromNoteBytes(configBytes) : new ContainerConfig();
            boolean autoFocus = autoFocusBytes.getAsBoolean();
            String rendererId = rendererIdBytes != null ? rendererIdBytes.getAsString() : "console";
 

            
            // Generate ID
            ContainerId containerId = ContainerId.generate();
            
            // Create container
            Container container = createContainer(
                containerId, title, type, ownerPath, config, rendererId
            );
            
            // Initialize
            return container.initialize()
                .thenCompose(v -> {
                    // Handle auto-focus
                    if (autoFocus) {
                        ContainerId previousFocus = focusedContainerId;
                        focusedContainerId = containerId;
                        
                        if (previousFocus != null && !previousFocus.equals(containerId)) {
                            Container prevContainer = containers.get(previousFocus);
                            if (prevContainer != null) {
                                prevContainer.unfocus();
                            }
                        }
                        
                        return container.focus();
                    }
                    
                    return CompletableFuture.completedFuture(null);
                })
                .thenAccept(result -> {
                // Success - reply with SUCCESS
                    reply(packet, ProtocolObjects.SUCCESS_OBJECT);
                })
                .exceptionally(ex -> {
                    // Failure - reply with ERROR
                    String errorMsg = ex.getCause() != null ? 
                        ex.getCause().getMessage() : ex.getMessage();
                    
                    Log.logError("[RenderingService] Renderer error: " + errorMsg);
                    reply(packet, ProtocolObjects.getErrorObject(errorMsg));
                    
                    return null;
                });
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void reply(RoutedPacket packet, NoteBytesObject msg){
        replyExec.reply(packet, msg.readOnly());
    }

    private void reply(RoutedPacket packet, NoteBytesReadOnly msg){
        replyExec.reply(packet, msg);
    }
    
    private CompletableFuture<Void> handleDestroyContainer(NoteBytesMap msg, RoutedPacket packet) {
        ConsoleContainer container = getContainerFromMsg(msg);
        if (container == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Container not found")
            );
        }
        
        return container.destroy()
            .thenRun(() -> {
                // Unregister
                containers.remove(container.getId());
                
                // Remove from owner tracking
                List<ContainerId> ownerList = ownerContainers.get(container.getOwnerPath());
                if (ownerList != null) {
                    ownerList.remove(container.getId());
                    if (ownerList.isEmpty()) {
                        ownerContainers.remove(container.getOwnerPath());
                    }
                }
                
                // Clear focus if this was focused
                if (container.getId().equals(focusedContainerId)) {
                    focusedContainerId = null;
                    clearScreen();
                }
            })
            .thenAccept(result -> {
            // Success - reply with SUCCESS
                reply(packet, ProtocolObjects.SUCCESS_OBJECT);
            })
            .exceptionally(ex -> {
                // Failure - reply with ERROR
                String errorMsg = ex.getCause() != null ? 
                    ex.getCause().getMessage() : ex.getMessage();
                
                Log.logError("[RenderingService] Renderer error: " + errorMsg);
                reply(packet, ProtocolObjects.getErrorObject(errorMsg));
                
                return null;
            });
    }
    
    private CompletableFuture<Void> handleShowContainer(NoteBytesMap msg, RoutedPacket packet) {
        ConsoleContainer container = getContainerFromMsg(msg);
        if (container == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Container not found")
            );
        }
        
        return container.show()
            .thenAccept(result -> {
            // Success - reply with SUCCESS
                reply(packet, ProtocolObjects.SUCCESS_OBJECT);
            })
            .exceptionally(ex -> {
                // Failure - reply with ERROR
                String errorMsg = ex.getCause() != null ? 
                    ex.getCause().getMessage() : ex.getMessage();
                
                Log.logError("[RenderingService] Renderer error: " + errorMsg);
                reply(packet, ProtocolObjects.getErrorObject(errorMsg));
                
                return null;
            });
    }
    
    private CompletableFuture<Void> handleHideContainer(NoteBytesMap msg, RoutedPacket packet) {
        ConsoleContainer container = getContainerFromMsg(msg);
        if (container == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Container not found")
            );
        }
        
        return container.hide()
            .thenAccept(result -> {
            // Success - reply with SUCCESS
                reply(packet, ProtocolObjects.SUCCESS_OBJECT);
            })
            .exceptionally(ex -> {
                // Failure - reply with ERROR
                String errorMsg = ex.getCause() != null ? 
                    ex.getCause().getMessage() : ex.getMessage();
                
                Log.logError("[RenderingService] Renderer error: " + errorMsg);
                reply(packet, ProtocolObjects.getErrorObject(errorMsg));
                
                return null;
            });
    }
    
    private CompletableFuture<Void> handleFocusContainer(NoteBytesMap msg, RoutedPacket packet) {
        ConsoleContainer container = getContainerFromMsg(msg);
        if (container == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Container not found")
            );
        }
        
        // Update focus
        ContainerId previousFocus = focusedContainerId;
        focusedContainerId = container.getId();
        
        // Unfocus previous
        if (previousFocus != null && !previousFocus.equals(container.getId())) {
            Container prevContainer = containers.get(previousFocus);
            if (prevContainer != null) {
                prevContainer.unfocus();
            }
        }
        
        return container.focus()
            .thenRun(() -> scheduleRender(container))
            .thenAccept(result -> {
            // Success - reply with SUCCESS
                reply(packet, ProtocolObjects.SUCCESS_OBJECT);
            })
            .exceptionally(ex -> {
                // Failure - reply with ERROR
                String errorMsg = ex.getCause() != null ? 
                    ex.getCause().getMessage() : ex.getMessage();
                
                Log.logError("[RenderingService] Renderer error: " + errorMsg);
                reply(packet, ProtocolObjects.getErrorObject(errorMsg));
                
                return null;
            });
    }
    
    private CompletableFuture<Void> handleMaximizeContainer(NoteBytesMap msg, RoutedPacket packet) {
        ConsoleContainer container = getContainerFromMsg(msg);
        if (container == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Container not found")
            );
        }
        
        return container.maximize()
            .thenAccept(result -> {
                // Success - reply with SUCCESS
                reply(packet, ProtocolObjects.SUCCESS_OBJECT);
            })
            .exceptionally(ex -> {
                // Failure - reply with ERROR
                String errorMsg = ex.getCause() != null ? 
                    ex.getCause().getMessage() : ex.getMessage();
                
                Log.logError("[RenderingService] Renderer error: " + errorMsg);
                reply(packet, ProtocolObjects.getErrorObject(errorMsg));
                
                return null;
            });
    }
    
    private CompletableFuture<Void> handleRestoreContainer(NoteBytesMap msg, RoutedPacket packet) {
        ConsoleContainer container = getContainerFromMsg(msg);
        if (container == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Container not found")
            );
        }
        
        return container.restore()
            .thenAccept(result -> {
                // Success - reply with SUCCESS
                reply(packet, ProtocolObjects.SUCCESS_OBJECT);
            })
            .exceptionally(ex -> {
                // Failure - reply with ERROR
                String errorMsg = ex.getCause() != null ? 
                    ex.getCause().getMessage() : ex.getMessage();
                
                Log.logError("[RenderingService] Renderer error: " + errorMsg);
                reply(packet, ProtocolObjects.getErrorObject(errorMsg));
                
                return null;
            });
    }
    
    private CompletableFuture<Void> handleQueryContainer(NoteBytesMap msg, RoutedPacket packet) {
        ConsoleContainer container = getContainerFromMsg(msg);
        if (container == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Container not found")
            );
        }

        ContainerInfo info = container.getInfo();
        
        NoteBytesObject response = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.STATUS, ProtocolMesssages.SUCCESS),
            new NoteBytesPair(Keys.DATA, info.toNoteBytes())
        });
        
        // Query doesn't fail - just return success
        // RenderingService could be enhanced to return data in future
        return CompletableFuture.completedFuture(null)
            .thenAccept(result -> {
                // Success - reply with SUCCESS
                reply(packet, response);
            })
            .exceptionally(ex -> {
                // Failure - reply with ERROR
                String errorMsg = ex.getCause() != null ? 
                    ex.getCause().getMessage() : ex.getMessage();
                
                Log.logError("[RenderingService] Renderer error: " + errorMsg);
                reply(packet, ProtocolObjects.getErrorObject(errorMsg));
                
                return null;
            });
    }
    
    private CompletableFuture<Void> handleListContainers(NoteBytesMap msg, RoutedPacket packet) {
        List<ContainerInfo> infoList = containers.values().stream()
            .map(Container::getInfo)
            .toList();

        NoteBytesObject response = new NoteBytesObject(new NoteBytesPair[]{
        new NoteBytesPair(Keys.STATUS, ProtocolMesssages.SUCCESS),
        new NoteBytesPair("count", infoList.size()),
        new NoteBytesPair("containers", new NoteBytesArrayReadOnly(
            infoList.stream()
                .map(ContainerInfo::toNoteBytes)
                .toArray(NoteBytes[]::new)))
        });

        return CompletableFuture.completedFuture(null)
            .thenAccept(result -> {
                // Success - reply with SUCCESS
                reply(packet, response);
            })
            .exceptionally(ex -> {
                // Failure - reply with ERROR
                String errorMsg = ex.getCause() != null ? 
                    ex.getCause().getMessage() : ex.getMessage();
                
                Log.logError("[RenderingService] Renderer error: " + errorMsg);
                reply(packet, ProtocolObjects.getErrorObject(errorMsg));
                
                return null;
            });
    }
    
    private ConsoleContainer getContainerFromMsg(NoteBytesMap msg) {
        NoteBytes idBytes = msg.get(Keys.CONTAINER_ID);
        if (idBytes == null) return null;
        
        ContainerId id = ContainerId.fromNoteBytes(idBytes);
        return containers.get(id);
    }
    
    // ===== STREAM HANDLING =====
    
    @Override
    public boolean canHandleStreamFrom(ContextPath fromPath) {
        // Check if we have a container whose owner path matches the handle's parent
        ContextPath handleParent = fromPath.getParent();
        
        if (handleParent == null) {
            return false;
        }
        
        for (ConsoleContainer container : containers.values()) {
            if (container.getOwnerPath() != null && 
                container.getOwnerPath().equals(handleParent)) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        Log.logMsg("[ConsoleUIRenderer] Stream channel received from: " + fromPath);
        
        // Find target container
        ConsoleContainer targetContainer = findContainerByHandlePath(fromPath);
        
        if (targetContainer == null) {
            Log.logError("[ConsoleUIRenderer] No container found for handle: " + fromPath);
            channel.getReadyFuture().completeExceptionally(
                new IllegalStateException("No container found for handle: " + fromPath)
            );
            return;
        }
        
        Log.logMsg("[ConsoleUIRenderer] Routing stream to container: " + targetContainer.getId());
        
        // Pass render stream to Container
        targetContainer.handleRenderStream(channel, fromPath);
        
        
    }

    public void handleEventStream(StreamChannel eventChannel, ContextPath fromPath){
         Log.logMsg("[ConsoleUIRenderer] Event Stream channel received from: " + fromPath);
        
        // Find target container
        ConsoleContainer targetContainer = findContainerByHandlePath(fromPath);
        
        if (targetContainer == null) {
            Log.logError("[ConsoleUIRenderer] No container found for handle: " + fromPath);
            eventChannel.getReadyFuture().completeExceptionally(
                new IllegalStateException("No container found for handle: " + fromPath)
            );
            return;
        }
        
        Log.logMsg("[ConsoleUIRenderer] Routing event stream to container: " + targetContainer.getId());

        targetContainer.handleEventStream(eventChannel, fromPath);
        
    }
    
    private ConsoleContainer findContainerByHandlePath(ContextPath handlePath) {
        ContextPath handleParent = handlePath.getParent();
        
        if (handleParent == null) {
            return null;
        }
        
        for (ConsoleContainer container : containers.values()) {
            if (container.getOwnerPath() != null && 
                container.getOwnerPath().equals(handleParent)) {
                return container;
            }
        }
        
        return null;
    }
    
    // ===== RENDERING =====
    
    public void scheduleRender(ConsoleContainer container) {
        if (!container.getId().equals(focusedContainerId)) return;
        
        ContainerId id = container.getId();
        
        // Cancel pending
        ScheduledFuture<?> pending = pendingRenders.get(id);
        if (pending != null && !pending.isDone()) {
            pending.cancel(false);
        }
        
        // Schedule new
        ScheduledFuture<?> future = renderScheduler.schedule(
            () -> renderContainer(container),
            5,
            TimeUnit.MILLISECONDS
        );
        
        pendingRenders.put(id, future);
    }
    
    private void renderContainer(ConsoleContainer container) {
        // Rate limiting, differential rendering, etc.
        // (Implementation from previous ConsoleUIRenderer)
    }
    
    private void clearScreen() {
        terminal.writer().print("\033[2J\033[H");
        terminal.flush();
    }
    
    // ===== TERMINAL EVENT HANDLING =====
    
    private void handleTerminalResize(Signal signal) {
        int newWidth = terminal.getWidth();
        int newHeight = terminal.getHeight();
        
        if (newWidth == termWidth && newHeight == termHeight) {
            return;
        }
        
        Log.logMsg(String.format(
            "[ConsoleUIRenderer] Terminal resized: %dx%d → %dx%d",
            termWidth, termHeight, newWidth, newHeight
        ));
        
        termWidth = newWidth;
        termHeight = newHeight;
        
        // Resize ALL containers (they emit events themselves)
        for (ConsoleContainer container : containers.values()) {
            container.resize(newWidth, newHeight);
        }
        
        // Re-render focused
        if (focusedContainerId != null) {
            ConsoleContainer focused = containers.get(focusedContainerId);
            if (focused != null) {
                clearScreen();
                scheduleRender(focused);
            }
        }
    }
    
    // ===== CAPABILITIES =====
    
    @Override
    public boolean supports(ContainerType type) {
        return supportedTypes.contains(type);
    }
    
    @Override
    public Set<ContainerType> getSupportedTypes() {
        return supportedTypes;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    public Terminal getTerminal() {
        return terminal;
    }
   
	@Override
	public void setUIReplyExec(UIReplyExec exec) {
		replyExec = exec;
	}
}