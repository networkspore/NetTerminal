package io.netnotes.consoleRenderer;


import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.state.BitFlagStateMachine.StateSnapshot;
import io.netnotes.engine.ui.containers.Container;
import io.netnotes.engine.ui.containers.ContainerId;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * ConsoleRenderManager - Optimized pull-based rendering coordinator
 * 
 * SIMPLIFIED DESIGN:
 * - Removed concept of "active" state from container
 * - Uses container.shouldRender() which checks: VISIBLE && !HIDDEN && !ERROR
 * - Focused container gets rendered - that's what "active" really meant
 * - Error states directly prevent rendering via shouldRender()
 */
public class ConsoleRenderManager {
    
    private final ConsoleRenderer renderer;
    private final AtomicLong generation = new AtomicLong(0);
    
    // NOTE: focused container tracking lives in Renderer base (focusedContainerId / getFocusedContainer()).
    // RenderManager delegates to renderer for all focus authority.
    
    // Request queue - containers enqueue themselves
    private final ConcurrentLinkedQueue<ConsoleContainer> requestQueue = 
        new ConcurrentLinkedQueue<>();
    
    // Generation-based dirty tracking
    private volatile long dirtyGen = -1;
    
    // Render loop control
    private volatile boolean running = false;
    private CompletableFuture<Void> renderLoop;
    private final Map<ContainerId, CompletableFuture<Void>> renderInFlight = new ConcurrentHashMap<>();
    
    // Frame timing
    private static final long FRAME_NS = 16_000_000; // ~60fps
    private long nextFrameTime = System.nanoTime();
    
    // Render failure tracking
    private static final int MAX_RENDER_FAILURES = 3;
    private static final long RENDER_FAILURE_RESET_NS = 5_000_000_000L; // 5s
    private final Map<ContainerId, RenderFailureTracker> failureTrackers = new ConcurrentHashMap<>();
    
    /**
     * Tracks render failures for a container
     */
    private static class RenderFailureTracker {
        int consecutiveFailures = 0;
        long lastFailureTime = 0;
        long lastSuccessTime = 0;
        
        boolean shouldSkipRender(long now) {
            // Reset failure count if enough time has passed
            if (now - lastFailureTime > RENDER_FAILURE_RESET_NS) {
                consecutiveFailures = 0;
                return false;
            }
            
            // Skip if too many consecutive failures
            return consecutiveFailures >= MAX_RENDER_FAILURES;
        }
        
        void recordFailure(long now) {
            consecutiveFailures++;
            lastFailureTime = now;
            Log.logError("[RenderFailureTracker] Failure #" + consecutiveFailures + 
                " at " + (now - lastSuccessTime) + "ns since last success");
        }
        
        void recordSuccess(long now) {
            consecutiveFailures = 0;
            lastSuccessTime = now;
        }
    }

    
    public ConsoleRenderManager(ConsoleRenderer renderer) {
        this.renderer = renderer;
    }
    
    /**
     * Start the render loop
     */
    public void start() {
        if (running) return;
        
        running = true;
        renderLoop = CompletableFuture.runAsync(this::renderLoopImpl, VirtualExecutors.getVirtualExecutor());
        Log.logMsg("[ConsoleRenderManager] Render loop started");
    }
    
    /**
     * Stop the render loop
     */
    public void stop() {
        running = false;
        if (renderLoop != null) {
            renderLoop.cancel(false);
        }
        Log.logMsg("[ConsoleRenderManager] Render loop stopped");
    }
    
    /**
     * Main render loop
     */
    private void renderLoopImpl() {
        while (running) {
            long now = System.nanoTime();
            if (now >= nextFrameTime) {
                try {
                    tick(now);
                    nextFrameTime += FRAME_NS;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.logError("[ConsoleRenderManager] Render loop error: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                LockSupport.parkNanos(nextFrameTime - now);
            }
        }
    }

    private void tick(long frameTime) throws InterruptedException {
        processQueuedRequests();
        
        // Render if dirty for current generation
        if (isDirtyForCurrentGen()) {
            renderVisibleContainers(frameTime);
        }
    }
    
    /**
     * Process only containers that have enqueued themselves
     */
    private void processQueuedRequests() {
        ConsoleContainer container;
        while ((container = requestQueue.poll()) != null) {
            processContainerRequests(container);
        }
    }
    
    /**
     * Enqueue container for request processing
     */
    public void enqueueRequest(ConsoleContainer container) {
        Log.logMsg("[ConsoleRenderManager] Enqueuing container request");
        if (!requestQueue.contains(container)) {
            requestQueue.offer(container);
        }
    }
    
    /**
     * Process pending requests for a single container
     */
    private void processContainerRequests(ConsoleContainer container) {
       

        Log.logMsg("[ConsoleRenderManager] processing container requests");
        
        StateSnapshot snap = container.getState();
        
        // DESTROY takes precedence over everything
        if (snap.hasState(Container.STATE_DESTROY_REQUESTED)) {
            handleDestroyRequest(container, snap);
            return;
        }

        if (snap.hasState(Container.STATE_RENDER_REQUESTED)) {
            handleRenderRequest(container, snap);
        }
     
        if (snap.hasState(Container.STATE_UPDATE_REQUESTED)) {
            handleUpdateRequest(container, snap);
        }
        
        // Process in order of priority
        if (snap.hasState(Container.STATE_FOCUS_REQUESTED)) {
            handleFocusRequest(container, snap);
        }
        
        if (snap.hasState(Container.STATE_SHOW_REQUESTED)) {
            handleShowRequest(container, snap);
        }
        
        if (snap.hasState(Container.STATE_HIDE_REQUESTED)) {
            handleHideRequest(container, snap);
        }
        
        if (snap.hasState(Container.STATE_MAXIMIZE_REQUESTED)) {
            handleMaximizeRequest(container, snap);
        }
        
        if (snap.hasState(Container.STATE_RESTORE_REQUESTED)) {
            handleRestoreRequest(container, snap);
        }
        
        // Re-enqueue if container still has pending requests (re-read current state)
        StateSnapshot afterSnap = container.getState();
        if (hasAnyPendingRequests(afterSnap)) {
            requestQueue.offer(container);
        }
    }
    
    /**
     * Check if snapshot has any pending requests
     */
    private boolean hasAnyPendingRequests(BitFlagStateMachine.StateSnapshot snap) {
        return snap.hasState(Container.STATE_RENDER_REQUESTED) ||
            snap.hasState(Container.STATE_UPDATE_REQUESTED) ||
            snap.hasState(Container.STATE_FOCUS_REQUESTED) ||
            snap.hasState(Container.STATE_SHOW_REQUESTED) ||
            snap.hasState(Container.STATE_HIDE_REQUESTED) ||
            snap.hasState(Container.STATE_MAXIMIZE_REQUESTED) ||
            snap.hasState(Container.STATE_RESTORE_REQUESTED) ||
            snap.hasState(Container.STATE_DESTROY_REQUESTED);
    }
    
    /**
     * Handle update request (content change)
     */
    private void handleUpdateRequest(ConsoleContainer container, StateSnapshot snap) {
        // Container.handleUpdateContainer() → grantUpdate() owns STATE_UPDATE_REQUESTED and the updateFuture.
        // RenderManager's only job here is to mark the screen dirty if this container is visible.
        if (container.shouldRender()) markDirty();
    }
    /**
     * Handle render request
     */
    private void handleRenderRequest(ConsoleContainer container, StateSnapshot snap) {
        if (container.shouldRender()) markDirty();
        container.getStateMachine().removeState(Container.STATE_RENDER_REQUESTED);
    }
    
    /**
     * Handle focus request.
     *
     * Ownership rules enforced here:
     *  - canGrantFocus() / visibility checks happen first (layout manager arbitrates)
     *  - revokeFocus() owns clearing STATE_FOCUSED on the outgoing container and fires onFocusRevoked()
     *  - grantFocus() owns clearing STATE_FOCUS_REQUESTED, setting STATE_FOCUSED, firing onFocusGranted()
     *  - renderer.onFocusGranted/Revoked() are the single authority for Renderer.focusedContainerId
     *  - layoutManager.onFocusGranted() is called last (for reflow) — it must NOT call requestFocus() again
     */
    private void handleFocusRequest(ConsoleContainer container, StateSnapshot snap) {
        if (!snap.hasState(Container.STATE_VISIBLE) || snap.hasState(Container.STATE_HIDDEN)) {
            container.clearRequest(Container.STATE_FOCUS_REQUESTED);
            return;
        }
        if (!renderer.getLayoutManager().canGrantFocus(container.getId())) {
            container.clearRequest(Container.STATE_FOCUS_REQUESTED);
            return;
        }
        if (snap.hasState(Container.STATE_FOCUSED)) {
            // Already focused — clear request flag without re-granting or calling onFocusGranted again.
            container.getStateMachine().removeState(Container.STATE_FOCUS_REQUESTED);
            return;
        }

        // Revoke focus from any currently focused container.
        // revokeFocus() clears STATE_FOCUSED + STATE_FOCUS_REQUESTED and fires container.onFocusRevoked()
        // (which emits the focus-lost event to the client).
        // renderer.onFocusRevoked() then clears Renderer.focusedContainerId.
        renderer.getAllContainers().stream()
            .filter(c -> c != container && c.getStateMachine().hasState(Container.STATE_FOCUSED))
            .forEach(prev -> {
                prev.revokeFocus();
                renderer.onFocusRevoked(prev);
            });

        // grantFocus() owns: clear STATE_FOCUS_REQUESTED, set STATE_FOCUSED, call container.onFocusGranted().
        // Chaining ensures renderer state and layout reflow only happen after the container state is committed.
        container.grantFocus().thenRun(() -> {
            renderer.onFocusGranted(container);             // sets Renderer.focusedContainerId
            renderer.getLayoutManager().onFocusGranted(container.getId()); // updates focusedIndex, reflows
            Log.logMsg("[ConsoleRenderManager] Focus granted to: " + container.getId());
        });
    }
        
    /**
     * Handle show request
     */
    private void handleShowRequest(ConsoleContainer container, StateSnapshot snap) {
        container.getStateMachine().removeState(Container.STATE_SHOW_REQUESTED);
        container.grantShow().thenRun(() -> {
            renderer.getLayoutManager().onContainerShown(container);
            markDirty();
            Log.logMsg("[ConsoleRenderManager] Show granted: " + container.getId());
        });
    }
    
    /**
     * Handle hide request
     */
    private void handleHideRequest(ConsoleContainer container, StateSnapshot snap) {
        container.getStateMachine().removeState(Container.STATE_HIDE_REQUESTED);
        container.grantHide().thenRun(() -> {
            renderer.getLayoutManager().onContainerHidden(container);
            markDirty();
        });
    }
    
    /**
     * Handle maximize request
     */
    private void handleMaximizeRequest(ConsoleContainer container, 
                                       BitFlagStateMachine.StateSnapshot snap) {
        boolean canGrant = snap.hasState(Container.STATE_FOCUSED);
        
        if (canGrant) {
            container.getStateMachine().removeState(Container.STATE_MAXIMIZE_REQUESTED);
            container.grantMaximize().thenRun(() -> {
                markDirty();
                Log.logMsg("[ConsoleRenderManager] Maximize granted to: " + 
                    container.getId());
            });
        } else {
            container.clearRequest(Container.STATE_MAXIMIZE_REQUESTED);
            Log.logMsg("[ConsoleRenderManager] Maximize denied for: " + 
                container.getId() + " (not focused)");
        }
    }
    
    /**
     * Handle restore request
     */
    private void handleRestoreRequest(ConsoleContainer container, 
                                      BitFlagStateMachine.StateSnapshot snap) {
        boolean canGrant = snap.hasState(Container.STATE_MAXIMIZED);
        
        if (canGrant) {
            container.getStateMachine().removeState(Container.STATE_RESTORE_REQUESTED);
            container.grantRestore().thenRun(() -> {
                markDirty();
                Log.logMsg("[ConsoleRenderManager] Restore granted to: " + 
                    container.getId());
            });
        } else {
            container.clearRequest(Container.STATE_RESTORE_REQUESTED);
            Log.logMsg("[ConsoleRenderManager] Restore denied for: " + 
                container.getId() + " (not maximized)");
        }
    }
    
    /**
     * Handle destroy request
     */
    private void handleDestroyRequest(ConsoleContainer container, StateSnapshot snap) {
        container.getStateMachine().removeState(Container.STATE_DESTROY_REQUESTED);
        container.grantDestroy().thenRun(() -> {
            renderer.getLayoutManager().onContainerRemoved(container);
            failureTrackers.remove(container.getId());
            // Focus tracking lives in Renderer base; onContainerDestroyed will clear it if needed.
            markDirty();
        });
    }
    
  
    
    /**
     * Check if dirty for current generation
     */
    private boolean isDirtyForCurrentGen() {
        long currentGen = generation.get();
        return dirtyGen == currentGen;
    }

    private void renderVisibleContainers(long frameTime) {
        renderer.getAllContainers().stream()
            .filter(ConsoleContainer::shouldRender)
            .forEach(c -> renderContainer(c, frameTime));
        
        // After all cell content has been written, emit the final cursor state once.
        // Using the focused container's desired cursor state (via effectiveCursorVisible)
        // ensures no unfocused container can stomp the cursor position or visibility.
        renderer.applyCursorState(
            (ConsoleContainer) renderer.getFocusedContainer()
        );
    }
    
    /**
     * Only renders if container.shouldRender() returns true
     */
    private void renderContainer(ConsoleContainer container, long frameTime) {
        ContainerId id = container.getId();
        if (renderInFlight.containsKey(id)) return;

        RenderFailureTracker tracker = failureTrackers.computeIfAbsent(id, k -> new RenderFailureTracker());
        if (tracker.shouldSkipRender(frameTime)) {
            container.getStateMachine().addState(Container.STATE_RENDER_ERROR);
            return;
        }

        long currentGen = generation.get();
      
        CompletableFuture<Void> inFlight = container.getRenderableState()
            .thenAccept(state -> {
                if (!isGenerationCurrent(currentGen)) return;
                renderer.renderState(state, currentGen);
                container.commitRender();
                if (dirtyGen == currentGen) dirtyGen = -1;
                tracker.recordSuccess(System.nanoTime());
                container.getStateMachine().removeState(Container.STATE_RENDER_ERROR);
            })
            .whenComplete((v, ex) -> {
                renderInFlight.remove(id);
                if (ex != null) {
                    tracker.recordFailure(System.nanoTime());
                    container.getStateMachine().addState(Container.STATE_RENDER_ERROR);
                    escalateContainerError(tracker, container);
                }
            });

        renderInFlight.put(id, inFlight);
    }

    private void escalateContainerError(RenderFailureTracker tracker, ConsoleContainer focused){
        if (tracker.consecutiveFailures >= MAX_RENDER_FAILURES) {
            Log.logError("[ConsoleRenderManager] Escalating to ERROR state for " + 
                focused.getId() + " after " + MAX_RENDER_FAILURES + " failures");
            focused.getStateMachine().addState(Container.STATE_ERROR);
        }
    }
    
  
    
  
    /**
     * Mark as dirty for current generation (content change)
     */
    private void markDirty() {
        dirtyGen = generation.get();
    }
    
    /**
     * Mark dirty with new generation (layout/focus change)
    
    private void markDirtyForNewGeneration() {
        long newGen = generation.incrementAndGet();
        dirtyGen = newGen;
    } */
    
    /**
     * Check if generation is still current
     */
    public boolean isGenerationCurrent(long gen) {
        return generation.get() == gen;
    }
    
    /**
     * Get current generation
     */
    public long getCurrentGeneration() {
        return generation.get();
    }
    
    /**
     * Get render health for a container
     */
    public String getRenderHealth(ContainerId containerId) {
        RenderFailureTracker tracker = failureTrackers.get(containerId);
        if (tracker == null) {
            return "No render history";
        }
        
        long now = System.nanoTime();
        long timeSinceLastFailure = now - tracker.lastFailureTime;
        long timeSinceLastSuccess = now - tracker.lastSuccessTime;
        
        return String.format(
            "Failures: %d, Last failure: %dns ago, Last success: %dns ago, Skip: %s",
            tracker.consecutiveFailures,
            timeSinceLastFailure,
            timeSinceLastSuccess,
            tracker.shouldSkipRender(now)
        );
    }
    
    /**
     * Reset render failures for a container (useful for recovery)
     */
    public void resetRenderFailures(ContainerId containerId) {
        RenderFailureTracker tracker = failureTrackers.get(containerId);
        if (tracker != null) {
            tracker.consecutiveFailures = 0;
            Log.logMsg("[ConsoleRenderManager] Reset render failures for: " + containerId);
        }
    }
    
    // ===== RENDERABLE STATE =====
    
    public static class RenderableState {
        public final int rows;
        public final int cols;
        public final int cursorRow;
        public final int cursorCol;
        public final boolean cursorVisible;
        public final Cell[][] cells;
        public final Cell[][] prevCells;
        
        public RenderableState(
            int rows, int cols,
            int cursorRow, int cursorCol,
            boolean cursorVisible,
            Cell[][] cells,
            Cell[][] prevCells
        ) {
            this.rows = rows;
            this.cols = cols;
            this.cursorRow = cursorRow;
            this.cursorCol = cursorCol;
            this.cursorVisible = cursorVisible;
            this.cells = cells;
            this.prevCells = prevCells;
        }
    }
}