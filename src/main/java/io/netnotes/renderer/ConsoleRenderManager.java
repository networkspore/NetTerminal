package io.netnotes.renderer;


import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.state.BitFlagStateMachine.StateSnapshot;
import io.netnotes.engine.ui.containers.Container;
import io.netnotes.engine.ui.containers.ContainerId;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    
    private final ConsoleUIRenderer renderer;
    private final AtomicLong generation = new AtomicLong(0);
    
    // Focused container - determines what gets rendered
    private final AtomicReference<ConsoleContainer> focusedContainer = 
        new AtomicReference<>(null);
    
    // Request queue - containers enqueue themselves
    private final ConcurrentLinkedQueue<ConsoleContainer> requestQueue = 
        new ConcurrentLinkedQueue<>();
    
    // Generation-based dirty tracking
    private volatile long dirtyGen = -1;
    
    // Render loop control
    private volatile boolean running = false;
    private CompletableFuture<Void> renderLoop;
    private CompletableFuture<Void> renderInFlight = null;
    
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
    
    public ConsoleRenderManager(ConsoleUIRenderer renderer) {
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

    private void tick(long frameTime) throws Exception {
        processQueuedRequests();
        
        // Render if dirty for current generation
        if (isDirtyForCurrentGen()) {
            renderFocusedContainer(frameTime);
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
        if (!container.isEventStreamReady()) {
            return;
        }

        Log.logMsg("[ConsoleRenderManager] processing container requests");
        
        StateSnapshot snap = container.getStateMachine().getSnapshot();
        
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
        
        // Re-enqueue if still has pending requests
        if (hasAnyPendingRequests(snap)) {
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
    private void handleUpdateRequest(ConsoleContainer container,
                                     BitFlagStateMachine.StateSnapshot snap) {
        // Only render if this is the focused container
        if (focusedContainer.get() == container) {
            markDirty();
        }
        
        container.getStateMachine().removeState(Container.STATE_UPDATE_REQUESTED);
    }

    /**
     * Handle render request
     */
    private void handleRenderRequest(ConsoleContainer container,
                                     BitFlagStateMachine.StateSnapshot snap) {
        // Only render if this is the focused container
        if (focusedContainer.get() == container) {
            markDirty();
        }
        
        container.getStateMachine().removeState(Container.STATE_RENDER_REQUESTED);
    }
    
    /**
     * Handle focus request
     */
    private void handleFocusRequest(ConsoleContainer container, 
                                     BitFlagStateMachine.StateSnapshot snap) {
        boolean canGrant = snap.hasState(Container.STATE_VISIBLE) && 
                          !snap.hasState(Container.STATE_HIDDEN);
        
        if (canGrant) {
            // Revoke focus from current
            ConsoleContainer current = focusedContainer.get();
            if (current != null && current != container) {
                current.revokeFocus().thenRun(() -> {
                    Log.logMsg("[ConsoleRenderManager] Focus revoked from: " + 
                        current.getId());
                });
            }
            
            // Grant focus and set as focused
            container.grantFocus().thenRun(() -> {
                focusedContainer.set(container);
                markDirtyForNewGeneration(); // Focus change = new generation
                Log.logMsg("[ConsoleRenderManager] Focus granted to: " + 
                    container.getId() + " (gen=" + generation.get() + ")");
            });
        } else {
            container.clearRequest(Container.STATE_FOCUS_REQUESTED);
            Log.logMsg("[ConsoleRenderManager] Focus denied for: " + 
                container.getId() + " (not visible or hidden)");
        }
    }
    
    /**
     * Handle show request
     */
    private void handleShowRequest(ConsoleContainer container, 
                                    BitFlagStateMachine.StateSnapshot snap) {
        container.grantShow().thenRun(() -> {
            markDirty();
            Log.logMsg("[ConsoleRenderManager] Show granted to: " + container.getId());
            
            // If no focused container, try to focus this one
            if (focusedContainer.get() == null) {
                ConsoleContainer currentFocused = renderer.getAllContainers()
                    .stream()
                    .filter(c -> {
                        BitFlagStateMachine.StateSnapshot s = c.getStateMachine().getSnapshot();
                        return s.hasState(Container.STATE_VISIBLE) && 
                            s.hasState(Container.STATE_FOCUSED);
                    })
                    .findFirst()
                    .orElse(null);
                
                if (currentFocused == null) {
                    container.requestFocus();
                    enqueueRequest(container);
                }
            }
        });
    }
    
    /**
     * Handle hide request
     */
    private void handleHideRequest(ConsoleContainer container, 
                                    BitFlagStateMachine.StateSnapshot snap) {
        container.grantHide().thenRun(() -> {
            if (focusedContainer.get() == container) {
                findNewFocusedContainer();
            }
            markDirty();
            Log.logMsg("[ConsoleRenderManager] Hide granted to: " + container.getId());
        });
    }
    
    /**
     * Handle maximize request
     */
    private void handleMaximizeRequest(ConsoleContainer container, 
                                       BitFlagStateMachine.StateSnapshot snap) {
        boolean canGrant = snap.hasState(Container.STATE_FOCUSED);
        
        if (canGrant) {
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
    private void handleDestroyRequest(ConsoleContainer container, 
                                      BitFlagStateMachine.StateSnapshot snap) {
        container.grantDestroy().thenRun(() -> {
            if (focusedContainer.get() == container) {
                findNewFocusedContainer();
            }
            
            // Clean up failure tracker
            failureTrackers.remove(container.getId());
            
            Log.logMsg("[ConsoleRenderManager] Destroy granted to: " + 
                container.getId());
        });
    }
    
    /**
     * Find new focused container after current loses focus/hides/destroys
     */
    private void findNewFocusedContainer() {
        ConsoleContainer nextFocused = renderer.getAllContainers()
            .stream()
            .filter(c -> {
                BitFlagStateMachine.StateSnapshot snap = c.getStateMachine().getSnapshot();
                return snap.hasState(Container.STATE_VISIBLE) && 
                       !snap.hasState(Container.STATE_HIDDEN) &&
                       !snap.hasState(Container.STATE_DESTROYED);
            })
            .findFirst()
            .orElse(null);
        
        if (nextFocused != null) {
            nextFocused.requestFocus().thenRun(() -> {
                enqueueRequest(nextFocused);
                Log.logMsg("[ConsoleRenderManager] Auto-focusing: " + 
                    nextFocused.getId());
            });
        } else {
            focusedContainer.set(null);
            Log.logMsg("[ConsoleRenderManager] No focused container");
        }
    }
    
    /**
     * Check if dirty for current generation
     */
    private boolean isDirtyForCurrentGen() {
        long currentGen = generation.get();
        return dirtyGen == currentGen;
    }
    
    /**
     * Render focused container
     * Only renders if container.shouldRender() returns true
     */
    private void renderFocusedContainer(long renderTime) {
        if (renderInFlight != null) {
            return; // a render is already pending
        }
        
        ConsoleContainer focused = focusedContainer.get();
        if (focused == null) {
            return;
        }
        
        // Check if container should be rendered
        // This checks: VISIBLE && !HIDDEN && !ERROR && !DESTROYED
        if (!focused.shouldRender()) {
            Log.logMsg("[ConsoleRenderManager] Skipping render for " + focused.getId() + 
                " - shouldRender() = false");
            
            // Clear dirty to avoid spinning
            long currentGen = generation.get();
            if (dirtyGen == currentGen) {
                dirtyGen = -1;
            }
            return;
        }
        
        long currentGen = generation.get();
        
        // Check if container has too many recent failures
        RenderFailureTracker tracker = failureTrackers.computeIfAbsent(
            focused.getId(), 
            k -> new RenderFailureTracker()
        );
        
        if (tracker.shouldSkipRender(renderTime)) {
            Log.logError("[ConsoleRenderManager] Skipping render for " + focused.getId() + 
                " due to " + tracker.consecutiveFailures + " consecutive failures");
            
            focused.getStateMachine().addState(Container.STATE_RENDER_ERROR);
            
            // Clear dirty to avoid spinning
            if (dirtyGen == currentGen) {
                dirtyGen = -1;
            }
            return;
        }
        
        // Pull state asynchronously
        renderInFlight = focused.getRenderableState()
            .thenAccept(state -> {
                if (state == null) {
                    throw new CompletionException(new NullPointerException("Render state is null"));
                }
                
                if (!isGenerationCurrent(currentGen)) {
                    Log.logMsg("[ConsoleRenderManager] Generation changed during state pull, skipping render");
                    return;
                }
                
                try {
                    renderer.renderState(state, currentGen);
                    
                    // Commit render after successful rendering
                    focused.commitRender();
                    
                    // Clear dirty after successful render
                    if (dirtyGen == currentGen) {
                        dirtyGen = -1;
                    }
                    
                    tracker.recordSuccess(System.nanoTime());
                    focused.getStateMachine().removeState(Container.STATE_RENDER_ERROR);
                } catch (Exception ex) {
                    Log.logError("[ConsoleRenderManager]", "Failed to render state", ex);
                    tracker.recordFailure(System.nanoTime());
                    focused.getStateMachine().addState(Container.STATE_RENDER_ERROR);

                    escalateContainerError(tracker, focused);
                }
            })
            .whenComplete((v, ex) -> {
                if(ex != null){
                    Log.logError("[ConsoleRenderManager]", "Failed to pull render state", ex);
         
                    tracker.recordFailure(System.nanoTime());
                    focused.getStateMachine().addState(Container.STATE_RENDER_ERROR);

                    // Clear dirty to avoid spinning on failed state
                    long currentGen2 = generation.get();
                    if (dirtyGen == currentGen2) {
                        dirtyGen = -1;
                    }

                    escalateContainerError(tracker, focused);
                }
                // Clear in-flight flag
                renderInFlight = null;
            });
    }

    private void escalateContainerError(RenderFailureTracker tracker, ConsoleContainer focused){
        if (tracker.consecutiveFailures >= MAX_RENDER_FAILURES) {
            Log.logError("[ConsoleRenderManager] Escalating to ERROR state for " + 
                focused.getId() + " after " + MAX_RENDER_FAILURES + " failures");
            focused.getStateMachine().addState(Container.STATE_ERROR);
        }
    }
    
    /**
     * Set focused container (called when renderer wants to change focus)
     */
    public void setFocused(ConsoleContainer container) {
        ConsoleContainer current = focusedContainer.get();
        if (current == container) {
            return;
        }
        
        focusedContainer.set(container);
        
        if (container != null) {
            markDirtyForNewGeneration();
        }
    }
    
    /**
     * Handle resize - increments generation
     */
    public void onResize(int width, int height) {
        for (ConsoleContainer container : renderer.getAllContainers()) {
            container.resize(width, height);
        }
        markDirtyForNewGeneration();
        Log.logMsg("[ConsoleRenderManager] Resize applied: " + width + "x" + height + 
            " (gen=" + generation.get() + ")");
    }
    
    /**
     * Mark as dirty for current generation (content change)
     */
    private void markDirty() {
        dirtyGen = generation.get();
    }
    
    /**
     * Mark dirty with new generation (layout/focus change)
     */
    private void markDirtyForNewGeneration() {
        long newGen = generation.incrementAndGet();
        dirtyGen = newGen;
    }
    
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
     * Get focused container
     */
    public ConsoleContainer getFocusedContainer() {
        return focusedContainer.get();
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