package io.netnotes.consoleRenderer;


import io.netnotes.debug.RenderDiagnostics;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.state.ConcurrentBitFlagStateMachine;
import io.netnotes.engine.state.BitFlagStateMachine.StateSnapshot;
import io.netnotes.engine.ui.containers.Container;
import io.netnotes.engine.ui.containers.ContainerId;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.LoggingHelpers.LogLevel;
import io.netnotes.engine.virtualExecutors.VirtualExecutors;

import java.util.ArrayList;
import java.util.List;
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

    private static final LogLevel LOG_LEVEL = LogLevel.IMPORTANT;
    
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
        Log.logMsg("[ConsoleRenderManager] Render loop started", LOG_LEVEL);
    }
    
    /**
     * Stop the render loop
     */
    public void stop() {
        running = false;
        if (renderLoop != null) {
            renderLoop.cancel(false);
        }
        Log.logMsg("[ConsoleRenderManager] Render loop stopped", LOG_LEVEL);
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
        
        if (isDirtyForCurrentGen()) {
            // Consume dirty before rendering. markDirty() during an in-flight render
            // will re-set dirtyGen, triggering a follow-up render on the next tick.
            dirtyGen = -1;
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
        Log.logMsg("[ConsoleRenderManager] Enqueuing container request", LOG_LEVEL);
        if (!requestQueue.contains(container)) {
            requestQueue.offer(container);
        }
    }
    
    /**
     * Process pending requests for a single container
     */
    private void processContainerRequests(ConsoleContainer container) {


        Log.logMsg("[ConsoleRenderManager] processing container requests", LOG_LEVEL);
        
        ConcurrentBitFlagStateMachine state = container.getStateMachine();
        
        // DESTROY takes precedence over everything
        if (state.hasState(Container.STATE_DESTROY_REQUESTED)) {
            handleDestroyRequest(container);
            return;
        }

        if (state.hasState(Container.STATE_RENDER_REQUESTED)) {
            handleRenderRequest(container);
        }
     
        if (state.hasState(Container.STATE_UPDATE_REQUESTED)) {
            handleUpdateRequest(container);
        }
        
        // Process in order of priority
        if (state.hasState(Container.STATE_FOCUS_REQUESTED)) {
            handleFocusRequest(container, state);
        }
        
        if (state.hasState(Container.STATE_SHOW_REQUESTED)) {
            handleShowRequest(container);
        }
        
        if (state.hasState(Container.STATE_HIDE_REQUESTED)) {
            handleHideRequest(container);
        }
        
        if (state.hasState(Container.STATE_MAXIMIZE_REQUESTED)) {
            handleMaximizeRequest(container);
        }
        
        if (state.hasState(Container.STATE_RESTORE_REQUESTED)) {
            handleRestoreRequest(container);
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
    private void handleUpdateRequest(ConsoleContainer container) {
        // Container.handleUpdateContainer() → grantUpdate() owns STATE_UPDATE_REQUESTED and the updateFuture.
        // RenderManager's only job here is to mark the screen dirty if this container is visible.
        if (container.shouldRender()) markDirty();
    }
    /**
     * Handle render request
     */
    private void handleRenderRequest(ConsoleContainer container) {
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
    private void handleFocusRequest(ConsoleContainer container, ConcurrentBitFlagStateMachine stateMachine) {

        if (!stateMachine.hasAnyState(Container.STATE_VISIBLE, Container.STATE_HIDDEN)) {
            container.clearRequest(Container.STATE_FOCUS_REQUESTED);
            return;
        }
        if (!renderer.getLayoutManager().canGrantFocus(container.getId())) {
            container.clearRequest(Container.STATE_FOCUS_REQUESTED);
            return;
        }
        if (stateMachine.hasState(Container.STATE_FOCUSED)) {
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
            Log.logMsg("[ConsoleRenderManager] Focus granted to: " + container.getId(), LOG_LEVEL);
        });
    }
        
    /**
     * Handle show request
     */
    private void handleShowRequest(ConsoleContainer container) {
        container.getStateMachine().removeState(Container.STATE_SHOW_REQUESTED);
        container.grantShow().thenRun(() -> {
            renderer.getLayoutManager().onContainerShown(container);
            markDirty();
            Log.logMsg("[ConsoleRenderManager] Show granted: " + container.getId(), LOG_LEVEL);
        });
    }
    
    /**
     * Handle hide request
     */
    private void handleHideRequest(ConsoleContainer container) {
        container.getStateMachine().removeState(Container.STATE_HIDE_REQUESTED);
        container.grantHide().thenRun(() -> {
            renderer.getLayoutManager().onContainerHidden(container);
            markDirty();
        });
    }
    
    /**
     * Handle maximize request
     */
    private void handleMaximizeRequest(ConsoleContainer container) {
        boolean canGrant = container.getStateMachine().hasState(Container.STATE_FOCUSED);
        
        if (canGrant) {
            container.getStateMachine().removeState(Container.STATE_MAXIMIZE_REQUESTED);
            container.grantMaximize().thenRun(() -> {
                markDirty();
                Log.logMsg("[ConsoleRenderManager] Maximize granted to: " + 
                    container.getId(), LOG_LEVEL);
            });
        } else {
            container.clearRequest(Container.STATE_MAXIMIZE_REQUESTED);
            Log.logMsg("[ConsoleRenderManager] Maximize denied for: " + 
                container.getId() + " (not focused)", LOG_LEVEL);
        }
    }
    
    /**
     * Handle restore request
     */
    private void handleRestoreRequest(ConsoleContainer container) {
        boolean canGrant = container.getStateMachine().hasState(Container.STATE_MAXIMIZED);
        
        if (canGrant) {
            container.getStateMachine().removeState(Container.STATE_RESTORE_REQUESTED);
            container.grantRestore().thenRun(() -> {
                markDirty();
                Log.logMsg("[ConsoleRenderManager] Restore granted to: " + 
                    container.getId(), LOG_LEVEL);
            });
        } else {
            container.clearRequest(Container.STATE_RESTORE_REQUESTED);
            Log.logMsg("[ConsoleRenderManager] Restore denied for: " + 
                container.getId() + " (not maximized)", LOG_LEVEL);
        }
    }
    
    /**
     * Handle destroy request
     */
    private void handleDestroyRequest(ConsoleContainer container) {
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
        long currentGen = generation.get();
        

        List<ConsoleContainer> all = renderer.getLayoutManager().getVisibleContainers();
        List<ConsoleContainer> toRender = new ArrayList<>(all.size());
        boolean hasVisibleInFlight = false;

        for (ConsoleContainer c : all) {
            if (!c.shouldRender()) {
                continue;
            }
            if (renderInFlight.containsKey(c.getId())) {
                hasVisibleInFlight = true;
            } else {
                toRender.add(c);
            }
        }
 
        if (toRender.isEmpty()) {
            if (hasVisibleInFlight) {
                RenderDiagnostics.logRenderBlocker(
                    "render-loop-inflight-only",
                    250_000_000L,
                    "ConsoleRenderManager.renderVisibleContainers",
                    "all-visible-containers-already-in-flight",
                    () -> "frameTime=" + frameTime
                );
                markDirty();
            }
            return;
        }

        int size = toRender.size();
        RenderableState[] states = new RenderableState[size];
        CompletableFuture<?>[] futureArray = new CompletableFuture<?>[size];

        for (int i = 0; i < size; i++) {
            final int idx = i;
            futureArray[i] = toRender.get(i).getRenderableState()
                .thenAccept(s -> states[idx] = s);
        }
        CompletableFuture.allOf(futureArray).thenRun(() -> {
            if (!isGenerationCurrent(currentGen)) return;

            boolean boundsChanged = false;
            for (RenderableState s : states) {
                if (s != null && s.hasBoundsChanged()) {
                    boundsChanged = true;
                    break;
                }
            }

            if (boundsChanged) {
                if (hasVisibleInFlightVisibleSet(all)) {
                    RenderDiagnostics.logRenderBlocker(
                        "render-bounds-change-deferred",
                        250_000_000L,
                        "ConsoleRenderManager.renderVisibleContainers",
                        "bounds-change-deferred-by-in-flight-render",
                        () -> "frameTime=" + frameTime
                    );
                    markDirty();
                    return;
                }
                renderer.clearScreen();
            }

            for (int i = 0; i < size; i++) {
                if (states[i] != null) {
                    RenderableState stateToRender = boundsChanged
                        ? forceFullRepaint(states[i])
                        : states[i];
                    renderWithState(toRender.get(i), stateToRender, currentGen, frameTime);
                }
            }

            renderer.applyCursorState(resolveFocusedContainerForCursor());
        });
    }

    private boolean hasVisibleInFlightVisibleSet(List<ConsoleContainer> visibleContainers) {
        for (ConsoleContainer container : visibleContainers) {
            if (container.shouldRender() && renderInFlight.containsKey(container.getId())) {
                return true;
            }
        }
        return false;
    }

    private RenderableState forceFullRepaint(RenderableState state) {
        return new RenderableState(
            state.rows(),
            state.cols(),
            state.offsetX(),
            state.offsetY(),
            state.cursorRow(),
            state.cursorCol(),
            state.cursorVisible(),
            null,
            state.cells(),
            null
        );
    }

    private ConsoleContainer resolveFocusedContainerForCursor() {
        for (ConsoleContainer container : renderer.getAllContainers()) {
            if (container.getStateMachine().hasState(Container.STATE_FOCUSED)) {
                return container;
            }
        }
        return (ConsoleContainer) renderer.getFocusedContainer();
    }
    
    /**
     * Only renders if container.shouldRender() returns true
     */
    private void renderWithState(
        ConsoleContainer container, 
        RenderableState state, 
        long currentGen, 
        long frameTime
    ) {
        ContainerId id = container.getId();
        if (renderInFlight.containsKey(id)) {
            RenderDiagnostics.logRenderDrop(
                "render-inflight-skip:" + id,
                250_000_000L,
                "ConsoleRenderManager.renderWithState",
                "already-in-flight",
                () -> "container=" + id
            );
            markDirty();
            return;
        }

        RenderFailureTracker tracker = failureTrackers.computeIfAbsent(id, k -> new RenderFailureTracker());
        if (tracker.shouldSkipRender(frameTime)) {
            RenderDiagnostics.logRenderDrop(
                "render-skip-after-failures:" + id,
                "ConsoleRenderManager.renderWithState",
                "failure-backoff",
                () -> "container=" + id
                    + "\n\thealth=" + getRenderHealth(id)
            );
            container.getStateMachine().addState(Container.STATE_RENDER_ERROR);
            return;
        }

        if (state.rows() <= 0 || state.cols() <= 0) {
            RenderDiagnostics.logRenderDrop(
                "render-state-empty:" + id,
                "ConsoleRenderManager.renderWithState",
                "non-positive-render-state",
                () -> "container=" + id
                    + "\n\trows=" + state.rows()
                    + "\n\tcols=" + state.cols()
                    + "\n\toffset=(" + state.offsetX() + "," + state.offsetY() + ")"
                    + "\n\tboundsChanged=" + state.hasBoundsChanged()
            );
        }

        if (!isGenerationCurrent(currentGen)) return;

        CompletableFuture<Void> guard = new CompletableFuture<>();
        renderInFlight.put(id, guard);

        try {
            renderer.renderState(state, currentGen);
            tracker.recordSuccess(System.nanoTime());
            container.getStateMachine().removeState(Container.STATE_RENDER_ERROR);
        } catch (Exception ex) {
            renderInFlight.remove(id);
            guard.complete(null);
            tracker.recordFailure(System.nanoTime());
            container.getStateMachine().addState(Container.STATE_RENDER_ERROR);
            escalateContainerError(tracker, container);
            return;
        }

        container.commitRender().whenComplete((v, ex) -> {
            renderInFlight.remove(id);
            guard.complete(null);
            if (ex != null) {
                Log.logError("[ConsoleRenderManager] commitRender failed for " + id + ": " + ex.getMessage());
                tracker.recordFailure(System.nanoTime());
                container.getStateMachine().addState(Container.STATE_RENDER_ERROR);
                escalateContainerError(tracker, container);
                // Re-mark dirty so the frame gets retried
                markDirty();
            }
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
     * Mark as dirty for current generation (content change)
     */
    public void markDirty() {
        dirtyGen = generation.get();
    }


    public void markDirtyForNewGeneration() {
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
            Log.logMsg("[ConsoleRenderManager] Reset render failures for: " + containerId, LOG_LEVEL);
        }
    }
    

}
