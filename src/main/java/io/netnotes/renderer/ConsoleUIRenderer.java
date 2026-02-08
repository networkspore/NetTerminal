package io.netnotes.renderer;


import io.netnotes.engine.ui.RendererStates;
import io.netnotes.engine.ui.UIRenderer;
import io.netnotes.engine.ui.containers.Container;
import io.netnotes.engine.ui.containers.ContainerCommands;
import io.netnotes.engine.ui.containers.ContainerConfig;
import io.netnotes.engine.ui.containers.ContainerId;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.DebouncedVirtualExecutor.DebounceStrategy;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.Color;
import io.netnotes.engine.utils.virtualExecutors.DebouncedVirtualExecutor;
import io.netnotes.engine.utils.virtualExecutors.SerializedScheduledVirtualExecutor;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.Terminal.Signal;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * ConsoleUIRenderer - Terminal container manager
 * 
 * SIMPLIFIED DESIGN:
 * - Renderer just renders RenderableState - doesn't know about containers
 * - RenderManager handles all container lifecycle and commits
 * - No backward dependency from Renderer to RenderManager
 */
public class ConsoleUIRenderer extends UIRenderer<ConsoleContainer> {
    public static final int RENDERER_INITIALIZING    = 0;
    public static final int RENDERER_READY           = 1;
    public static final int RENDERER_HAS_ACTIVE      = 2;
    public static final int RENDERER_SWITCHING_FOCUS = 3;
    public static final int RENDERER_CLEARING_SCREEN = 4;
    public static final int RENDERER_HANDLING_RESIZE = 5;
    public static final int RENDERER_SHUTTING_DOWN   = 6;

    private static final ThreadLocal<StringBuilder> RENDER_BUFFER = ThreadLocal.withInitial(() -> new StringBuilder(8192));

    private final BitFlagStateMachine rendererState;

    private static final long RESIZE_DEBOUNCE_MS = 80;

    private final String description = "JLine3 terminal renderer";

    
    // ===== TERMINAL =====
    private final Terminal terminal;
    private final Attributes originalAttributes;
    private volatile int termWidth;
    private volatile int termHeight;
    
    // ===== RENDERING =====
    private final DebouncedVirtualExecutor<Void> resizeDebouncer = 
        new DebouncedVirtualExecutor<>(RESIZE_DEBOUNCE_MS, TimeUnit.MILLISECONDS, DebounceStrategy.LEADING);
    
    private final SerializedScheduledVirtualExecutor scheduledExecutor =
        new SerializedScheduledVirtualExecutor();

    private final ConsoleRenderManager renderManager;
    
    private CompletableFuture<Void> resizePollFuture;
    private volatile boolean signalBasedResizeWorking = false;
    private static final long SIGNAL_TEST_DURATION_MS = 2000;

    private final ConsoleInputCapture inputCapture;
    private Runnable onCtrlC = null;

    // ===== SIGNAL HANDLER =====
    private Terminal.SignalHandler resizeHandler;

    /**
     * Constructor
     */
    public ConsoleUIRenderer() throws IOException {
        super("console-renderer");
        this.terminal = TerminalBuilder.builder()
            .system(true)
            .encoding("UTF-8")
            .build();
        
        this.termWidth = terminal.getWidth();
        this.termHeight = terminal.getHeight();
        this.originalAttributes = terminal.getAttributes();

        this.renderManager = new ConsoleRenderManager(this);
        
        inputCapture = new ConsoleInputCapture(terminal, this::handleInputEvent);
        inputCapture.setOnCtrlC(this::handleCtrlC);

        this.rendererState = new BitFlagStateMachine("ConsoleUIRenderer");
        rendererState.addState(RENDERER_INITIALIZING);

        setupRendererStateTransitions();
        
        Log.logMsg("[ConsoleUIRenderer] Terminal created: " + termWidth + "x" + termHeight);
    }

    /**
     * Setup renderer state machine transitions
     */
    @Override
    protected void setupRendererStateTransitions() {
        state.onStateAdded(RendererStates.HAS_ACTIVE, (old, now, bit) -> {
            Log.logMsg("[ConsoleUIRenderer] Active container set");
        });
        
        state.onStateRemoved(RendererStates.HAS_ACTIVE, (old, now, bit) -> {
            Log.logMsg("[ConsoleUIRenderer] No active container");
            
            if (!state.hasState(RendererStates.SWITCHING_FOCUS)) {
                state.addState(RendererStates.CLEARING_SCREEN);
            }
        });
        
        state.onStateAdded(RendererStates.CLEARING_SCREEN, (old, now, bit) -> {
            clearScreen();
            state.removeState(RendererStates.CLEARING_SCREEN);
        });
        
        state.onStateAdded(RendererStates.HANDLING_RESIZE, (old, now, bit) -> {
            Log.logMsg("[ConsoleUIRenderer] Handling resize");
        });
        
        state.onStateRemoved(RendererStates.HANDLING_RESIZE, (old, now, bit) -> {
            Log.logMsg("[ConsoleUIRenderer] Resize complete");
        });
    }

    private void handleCtrlC() {    
        if(onCtrlC != null){
            onCtrlC.run();
        }
    }

    public void setOnCtrlC(Runnable onCtrlC){
        this.onCtrlC = onCtrlC;
    }

    @Override
    protected CompletableFuture<Void> handleFocusContainer(
        NoteBytesMap msg, 
        RoutedPacket packet
    ) {
        ConsoleContainer container = getContainerFromMsg(msg);
        if (container == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Container not found")
            );
        }
        
        return container.handleFocusContainer(msg)
            .thenAccept(v -> replySuccess(packet))
            .exceptionally(ex -> {
                replyError(packet, ex.getMessage());
                return null;
            });
    }

    /**
     * Route input events to focused container
     */
    private void handleInputEvent(NoteBytesMap event) {
        if (focusedContainerId == null) {
            Log.logMsg("[ConsoleUIRenderer] No focused container, dropping event");
            return;
        }
        
        ConsoleContainer container = getConsoleContainer(focusedContainerId);
        if (container == null) {
            Log.logError("[ConsoleUIRenderer] Focused container not found: " + focusedContainerId);
            return;
        }
        
        container.emitEvent(event);
    }
 
    // ===== LIFECYCLE =====
    
    @Override
    protected CompletableFuture<Void> doInitialize() {
        return CompletableFuture.runAsync(() -> {
            terminal.writer().print("\033[?1049h");
            
            Attributes raw = new Attributes(originalAttributes);
            raw.setLocalFlag(Attributes.LocalFlag.ICANON, false);
            raw.setLocalFlag(Attributes.LocalFlag.ECHO, false);
            raw.setLocalFlag(Attributes.LocalFlag.ISIG, false);
            raw.setLocalFlag(Attributes.LocalFlag.IEXTEN, false);
            raw.setControlChar(Attributes.ControlChar.VMIN, 0);
            raw.setControlChar(Attributes.ControlChar.VTIME, 1);
            terminal.setAttributes(raw);
            
            terminal.writer().print("\033[?25l");
            terminal.writer().print("\033[2J\033[H");
            terminal.flush();

            renderManager.start();
            initializeTerminalHandlers();
            
            rendererState.addState(RENDERER_READY);

            Log.logMsg("[ConsoleUIRenderer] Terminal initialized");

            inputCapture.run().thenRun(() -> {
                Log.logMsg("[ConsoleUIRenderer] Input capture started");
            }).exceptionally(ex -> {
                Log.logError("[ConsoleUIRenderer] Failed to start input capture: " + ex.getMessage());
                return null;
            });
        });
    }

    /**
     * Register terminal resize signal handler
     */
    private boolean registerTerminalHandlers() {
        try {
            resizeHandler = signal -> {
                if (signal == Signal.WINCH) {
                    signalBasedResizeWorking = true;
                    resizeDebouncer.submit(this::handleTerminalResize);
                }
            };
            
            terminal.handle(Signal.WINCH, resizeHandler);
            Log.logMsg("[ConsoleUIRenderer] Resize handler registered");
            return true;
        } catch (Exception e) {
            Log.logError("[ConsoleUIRenderer] Failed to register resize signal: " + e.getMessage());
            return false;
        }
    }

    /**
     * Initialize resize handling with automatic fallback
     */
    private void initializeTerminalHandlers() {
        boolean signalRegistered = registerTerminalHandlers();
        
        if (signalRegistered) {
            Log.logMsg("[ConsoleUIRenderer] Signal-based resize registered, starting test period...");
            startResizePolling();
            scheduledExecutor.schedule(this::evaluateResizeMethod, 
                SIGNAL_TEST_DURATION_MS, TimeUnit.MILLISECONDS);
        } else {
            Log.logMsg("[ConsoleUIRenderer] Signal-based resize not available, using polling");
            startResizePolling();
        }
    }

    /**
     * Evaluate whether signal-based resize is working
     */
    private void evaluateResizeMethod() {
        if (signalBasedResizeWorking) {
            Log.logMsg("[ConsoleUIRenderer] Signal-based resize working, stopping poll");
            stopResizePolling();
        } else {
            Log.logMsg("[ConsoleUIRenderer] Signal-based resize not detected, continuing with poll");
        }
    }

    /**
     * Start polling for terminal size changes
     */
    private void startResizePolling() {
        if (resizePollFuture != null && !resizePollFuture.isDone()) {
            return;
        }
        
        resizePollFuture = scheduledExecutor.scheduleAtFixedRate(
            this::checkForResize,
            0,
            100,
            TimeUnit.MILLISECONDS
        );
        
        Log.logMsg("[ConsoleUIRenderer] Resize polling started (100ms interval)");
    }

    /**
     * Stop polling for terminal size changes
     */
    private void stopResizePolling() {
        if (resizePollFuture != null) {
            resizePollFuture.cancel(false);
            resizePollFuture = null;
            Log.logMsg("[ConsoleUIRenderer] Resize polling stopped");
        }
    }

    /**
     * Check for terminal size changes (polling method)
     */
    private void checkForResize() {
        try {
            int newWidth = terminal.getWidth();
            int newHeight = terminal.getHeight();
            
            if (newWidth != termWidth || newHeight != termHeight) {
                Log.logMsg("[ConsoleUIRenderer] Size change detected via polling: " + 
                    termWidth + "x" + termHeight + " -> " + newWidth + "x" + newHeight);
                
                resizeDebouncer.submit(this::handleTerminalResize);
            }
        } catch (Exception e) {
            Log.logError("[ConsoleUIRenderer] Error checking terminal size: " + e.getMessage());
        }
    }

    /**
     * Unregister terminal resize signal handler
     */
    private void unregisterTerminalHandlers() {
        if (resizeHandler != null) {
            terminal.handle(Signal.WINCH, Terminal.SignalHandler.SIG_DFL);
            resizeHandler = null;
            Log.logMsg("[ConsoleUIRenderer] Resize handler unregistered");
        }
        stopResizePolling();
    }

    @Override
    protected CompletableFuture<Void> doShutdown() {
        Log.logMsg("[ConsoleUIRenderer] Shutdown starting");
        
        rendererState.addState(RENDERER_SHUTTING_DOWN);
        
        renderManager.stop();
        rendererExecutor.shutdown();

        if (inputCapture != null) {
            inputCapture.stop();
        }
        
        unregisterTerminalHandlers();

        scheduledExecutor.shutdown();
        try {
            scheduledExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        try {
            terminal.writer().print("\033[2J\033[H");
            terminal.writer().print("\033[?25h");
            terminal.writer().print("\033[?1049l");
            terminal.setAttributes(originalAttributes);
            terminal.flush();
            terminal.close();
            Log.logMsg("[ConsoleUIRenderer] Terminal closed");
        } catch (Exception e) {
            Log.logError("[ConsoleUIRenderer] Error closing terminal: " + e.getMessage());
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== CONTAINER MANAGEMENT =====
    
    @Override
    protected CompletableFuture<ConsoleContainer> doCreateContainer(
        ContainerId id,
        String title,
        ContextPath ownerPath,
        ContainerConfig config,
        String rendererId
    ) {

        ConsoleContainer container = new ConsoleContainer(
            id, title, ownerPath, config,
            rendererId,
            termWidth,
            termHeight
        );
        container.setOnRequestMade(c -> {
            if (c instanceof ConsoleContainer cc) {
                renderManager.enqueueRequest(cc);
            }
        });
        
        return CompletableFuture.completedFuture(container);
    }

    @Override
    protected CompletableFuture<NoteBytesReadOnly> onContainerCreated(
        ContainerId containerId,
        NoteBytesMap createMsg
    ) {
        int width = terminal.getWidth();
        int height = terminal.getHeight();
        
        NoteBytes rendererIdBytes = createMsg.get(ContainerCommands.RENDERER_ID);
        
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
        response.put(ContainerCommands.RENDERER_ID, rendererIdBytes);
        response.put(Keys.WIDTH, width);
        response.put(Keys.HEIGHT, height);

        Log.logNoteBytes("[ConsoleRenderer.onContainerCreated]", response);
        
        return CompletableFuture.completedFuture(response.toNoteBytesReadOnly());
    }

    public Container<ConsoleContainer> createContainer(
        ContainerId id,
        String title,
        ContextPath ownerPath,
        ContainerConfig config,
        String rendererId
    ) {
        state.addState(RendererStates.CREATING_CONTAINER);
        
        try {
            ConsoleContainer container = new ConsoleContainer(
                id, title, ownerPath, config,
                rendererId,
                termWidth,
                termHeight
            );
            
            containers.put(id, container);
            
            ownerContainers.computeIfAbsent(ownerPath, k -> new ArrayList<>())
                .add(id);
            
            if (containers.size() == 1) {
                state.addState(RendererStates.HAS_CONTAINERS);
            }
            
            if (focusedContainerId == null) {
                focusedContainerId = id;
                state.addState(RendererStates.HAS_FOCUSED_CONTAINER);
            }
            
            Log.logMsg("[ConsoleUIRenderer] Container created: " + id);
            
            return container;
            
        } finally {
            state.removeState(RendererStates.CREATING_CONTAINER);
        }
    }

    @Override
    public ConsoleContainer getContainerFromMsg(NoteBytesMap msg){
        Container<ConsoleContainer> container = super.getContainerFromMsg(msg);
        return container != null && container instanceof ConsoleContainer consoleContainer ? consoleContainer : null;
    }

    protected ConsoleContainer getConsoleContainer(ContainerId id) {
        return containers.get(id);
    }
    
    // ===== STREAM HANDLING =====
    
    @Override
    public boolean canHandleStreamFrom(ContextPath fromPath) {
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
        
        ConsoleContainer targetContainer = findContainerByHandlePath(fromPath);
        
        if (targetContainer == null) {
            Log.logError("[ConsoleUIRenderer] No container found for handle: " + fromPath);
            channel.getReadyFuture().completeExceptionally(
                new IllegalStateException("No container found for handle: " + fromPath)
            );
            return;
        }
        
        Log.logMsg("[ConsoleUIRenderer] Routing stream to container: " + targetContainer.getId());
        
        targetContainer.handleRenderStream(channel, fromPath);
    }

    @Override
    public void handleEventStream(StreamChannel eventChannel, ContextPath fromPath) {
        Log.logMsg("[ConsoleUIRenderer] Event Stream channel received from: " + fromPath);
        
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
    
    /**
     * Render a container's state
     * PURE RENDERING - doesn't know about containers or lifecycle
     * RenderManager commits the render after this succeeds
     */
    public void renderState(ConsoleRenderManager.RenderableState state, long generation) {
        try {
            StringBuilder updates = RENDER_BUFFER.get();
            int estimatedSize = state.rows * state.cols * 8;
            
            if (updates.capacity() < estimatedSize) {
                updates.ensureCapacity(estimatedSize);
            }
            updates.setLength(0);
            
            // Hide cursor during update
            updates.append("\033[?25l");
            
            // Differential rendering
            TextStyle currentStyle = new TextStyle();
            int changedCells = 0;
            
            for (int row = 0; row < state.rows; row++) {
                for (int col = 0; col < state.cols; col++) {
                    Cell current = state.cells[row][col];
                    Cell previous = state.prevCells[row][col];
                    
                    if (current.equals(previous)) {
                        continue;
                    }
                    
                    changedCells++;
                    
                    // Position cursor
                    updates.append(String.format("\033[%d;%dH", row + 1, col + 1));
                    
                    // Update style if changed
                    if (!current.style.equals(currentStyle)) {
                        updates.append("\033[0m");
                        appendStyleCodes(updates, current.style);
                        currentStyle = current.style.copy();
                    }
                    
                    // Write character
                    updates.append(current.character != '\0' ? current.character : ' ');
                }
            }
            
            // Reset style
            updates.append("\033[0m");
            
            // Position and show cursor if visible
            if (state.cursorVisible) {
                updates.append(String.format("\033[%d;%dH", 
                    state.cursorRow + 1, state.cursorCol + 1));
                updates.append("\033[?25h");
            }
            
            // Write to terminal (atomic operation)
            if (changedCells > 0 || state.cursorVisible) {
                terminal.writer().write(updates.toString());
                terminal.flush();
            }
            
        } catch (Exception e) {
            Log.logError("[ConsoleUIRenderer] Render error: " + e.getMessage());
            e.printStackTrace();
            throw e; // Propagate to RenderManager for error handling
        }
    }
    
    private void appendStyleCodes(StringBuilder sb, TextStyle style) {
        // Text attributes
        if (style.isBold()) sb.append("\033[1m");
        if (style.isFaint()) sb.append("\033[2m");
        if (style.isItalic()) sb.append("\033[3m");
        if (style.isUnderline()) sb.append("\033[4m");
        if (style.isBlink()) sb.append("\033[5m");
        if (style.isInverse()) sb.append("\033[7m");
        if (style.isHidden()) sb.append("\033[8m");
        if (style.isStrikethrough()) sb.append("\033[9m");
        
        // Foreground color
        switch (style.getFgMode()) {
            case NAMED:
                if (style.getForeground() != TextStyle.Color.DEFAULT) {
                    sb.append("\033[").append(getColorCode(style.getForeground(), false)).append("m");
                }
                break;
            case INDEXED:
                int fgIdx = style.getFgIndexed();
                if (fgIdx >= 0 && fgIdx <= 255) {
                    sb.append("\033[38;5;").append(fgIdx).append("m");
                }
                break;
            case RGB:
                int fgRgb = style.getFgRgb();
                if (fgRgb >= 0) {
                    int r = (fgRgb >> 16) & 0xFF;
                    int g = (fgRgb >> 8) & 0xFF;
                    int b = fgRgb & 0xFF;
                    sb.append("\033[38;2;").append(r).append(";").append(g).append(";").append(b).append("m");
                }
                break;
        }
        
        // Background color
        switch (style.getBgMode()) {
            case NAMED:
                if (style.getBackground() != TextStyle.Color.DEFAULT) {
                    sb.append("\033[").append(getColorCode(style.getBackground(), true)).append("m");
                }
                break;
            case INDEXED:
                int bgIdx = style.getBgIndexed();
                if (bgIdx >= 0 && bgIdx <= 255) {
                    sb.append("\033[48;5;").append(bgIdx).append("m");
                }
                break;
            case RGB:
                int bgRgb = style.getBgRgb();
                if (bgRgb >= 0) {
                    int r = (bgRgb >> 16) & 0xFF;
                    int g = (bgRgb >> 8) & 0xFF;
                    int b = bgRgb & 0xFF;
                    sb.append("\033[48;2;").append(r).append(";").append(g).append(";").append(b).append("m");
                }
                break;
        }
    }

    private int getColorCode(Color color, boolean background) {
        int base = background ? 40 : 30;
        int brightBase = background ? 100 : 90;
        
        return switch (color) {
            case BLACK -> base + 0;
            case RED -> base + 1;
            case GREEN -> base + 2;
            case YELLOW -> base + 3;
            case BLUE -> base + 4;
            case MAGENTA -> base + 5;
            case CYAN -> base + 6;
            case WHITE -> base + 7;
            case BRIGHT_BLACK -> brightBase + 0;
            case BRIGHT_RED -> brightBase + 1;
            case BRIGHT_GREEN -> brightBase + 2;
            case BRIGHT_YELLOW -> brightBase + 3;
            case BRIGHT_BLUE -> brightBase + 4;
            case BRIGHT_MAGENTA -> brightBase + 5;
            case BRIGHT_CYAN -> brightBase + 6;
            case BRIGHT_WHITE -> brightBase + 7;
            default -> base + 7;
        };
    }
    
    private void clearScreen() {
        terminal.writer().print("\033[2J\033[H");
        terminal.flush();
    }
    
    // ===== TERMINAL EVENT HANDLING =====

    private void handleTerminalResize() {
        rendererExecutor.execute(() -> {
            doHandleTerminalResize();
        });
    }

    private void doHandleTerminalResize() {
        try {
            rendererState.addState(RENDERER_HANDLING_RESIZE);
            
            int newWidth = terminal.getWidth();
            int newHeight = terminal.getHeight();
            
            if (newWidth == termWidth && newHeight == termHeight) {
                rendererState.removeState(RENDERER_HANDLING_RESIZE);
                return;
            }
            
            Log.logMsg(String.format("[ConsoleUIRenderer] Resize: %dx%d -> %dx%d",
                termWidth, termHeight, newWidth, newHeight));
            
            termWidth = newWidth;
            termHeight = newHeight;
            
            clearScreen();
            
            // Delegate to render manager
            renderManager.onResize(newWidth, newHeight);
            
            rendererState.removeState(RENDERER_HANDLING_RESIZE);
            
        } catch (Exception e) {
            Log.logError("[ConsoleUIRenderer] Resize error: " + e.getMessage());
            rendererState.removeState(RENDERER_HANDLING_RESIZE);
        }
    }
    
    // ===== CAPABILITIES =====
    


    
    @Override
    public String getDescription() {
        return description;
    }
    
    public Terminal getTerminal() {
        return terminal;
    }

    @Override
    public boolean isActive() {
        return state.hasState(RendererStates.READY) && 
            terminal != null &&
            !state.hasState(RendererStates.SHUTTING_DOWN);
    }

    @Override
    protected CompletableFuture<Void> onContainerDestroyed(ContainerId containerId) {
        return rendererExecutor.execute(() -> {
            Log.logMsg("[ConsoleUIRenderer] Container destroyed: " + containerId);
            
            ConsoleContainer container = containers.remove(containerId);
            
            if (container == null) {
                Log.logError("[ConsoleUIRenderer] Container not found: " + containerId);
                return;
            }
            
            if (containers.isEmpty()) {
                state.removeState(RendererStates.HAS_CONTAINERS);
                state.removeState(RendererStates.HAS_VISIBLE_CONTAINERS);
            }
            
            if (container.getOwnerPath() != null) {
                List<ContainerId> ownerList = ownerContainers.get(container.getOwnerPath());
                if (ownerList != null) {
                    ownerList.remove(containerId);
                    if (ownerList.isEmpty()) {
                        ownerContainers.remove(container.getOwnerPath());
                    }
                }
            }
            
            if (containerId.equals(focusedContainerId)) {
                focusedContainerId = null;
                state.removeState(RendererStates.HAS_FOCUSED_CONTAINER);
            }
            
            Log.logMsg("[ConsoleUIRenderer] Container cleanup complete: " + containerId);
        });
    }
}