package io.netnotes.terminal.layout;

import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.layout.TerminalLayoutData.TerminalLayoutDataBuilder;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netnotes.engine.state.ConcurrentBitFlagStateMachine;
import io.netnotes.engine.virtualExecutors.SerializedVirtualExecutor;
import io.netnotes.engine.virtualExecutors.VirtualExecutors;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalDamageAccumulator;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRectanglePool;

/**
 * TerminalLayoutTestHarness
 *
 * LAYOUT PHASE STATE MACHINE:
 *
 *   STATE_LAYOUT_ACTIVE  — a layout pass is currently executing
 *   STATE_LAYOUT_PENDING — pass finished but another is queued (debounce window)
 *   STATE_LAYOUT_IDLE    — no pass running, no pass pending; geometry is stable
 *
 * The state machine executor is the uiExecutor, so all onStateAdded /
 * onStateRemoved callbacks fire on the UI thread. STATE_LAYOUT_IDLE is only
 * entered once all queued passes have drained — intermediate passes stay in
 * ACTIVE or PENDING, so tests never observe a false idle between passes.
 *
 * USAGE IN TESTS
 * ──────────────
 * Register a persistent step-dispatch handler on STATE_LAYOUT_IDLE *before*
 * triggering any layout changes. Use an int[] step counter to fan out work
 * across numbered cases. The final case opens a TestGate so the JUnit thread
 * can exit. There is no need for CompletableFuture or blocking wait calls
 * between steps — the state machine drives execution entirely.
 *
 *   int[] step = {0};
 *   harness.getStateMachine().onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
 *       switch (step[0]++) {
 *           case 0 -> { /* assert, trigger next action *\/ }
 *           case 1 -> { /* assert, gate.open() *\/ }
 *       }
 *   });
 *   rootPanel.addChild(myComponent);
 *   harness.triggerRender();
 *   gate.awaitDone();
 */
public class TerminalLayoutTestHarness {

    // ── Harness layout phase states ──────────────────────────────────────────

    /** A layout pass is currently executing. */
    public static final int STATE_LAYOUT_ACTIVE  = 1;

    /** No pass running, but another is queued in the debounce window. */
    public static final int STATE_LAYOUT_PENDING = 2;

    /** No pass running and none pending — geometry is stable and safe to read. */
    public static final int STATE_LAYOUT_IDLE    = 3;

    // ── Infrastructure ───────────────────────────────────────────────────────

    public final String name = "TerminalLayoutTestHarness";

    private TerminalLayoutManager layoutManager;
    private TerminalFloatingLayoutManager floatingLayoutManager;

    private TerminalRectanglePool regionPool;
    private TerminalRenderable focused        = null;
    private TerminalRenderable rootRenderable = null;
    protected TerminalDamageAccumulator damageAccumulator = null;
    private SerializedVirtualExecutor uiExecutor = VirtualExecutors.getUiExecutor();
    private TerminalRectangle allocatedRegion;
    private NoteBytesObject lastBatchCommand;

    private final ConcurrentBitFlagStateMachine harnessState;

    // ── Constructor ──────────────────────────────────────────────────────────

    public TerminalLayoutTestHarness(int width, int height) {
        this.regionPool        = TerminalRectanglePool.getInstance();
        this.damageAccumulator = new TerminalDamageAccumulator(regionPool);
        this.allocatedRegion   = new TerminalRectangle(0, 0, width, height);
        this.floatingLayoutManager = new TerminalFloatingLayoutManager(name, regionPool);
        this.layoutManager     = new TerminalLayoutManager(name, floatingLayoutManager);
        this.layoutManager.setFocusRequester(this::requestFocusInternal);
        this.layoutManager.setRenderRequester(this::renderableRequestRender);

        this.harnessState = new ConcurrentBitFlagStateMachine("harness");
        this.harnessState.setSerialExecutor(VirtualExecutors.getUiExecutor());

        // Route layout manager callbacks through the phase switch.
        layoutManager.setLayoutStateListener(active -> {
            switch (resolvePhase(active)) {
                case ACTIVE  -> onLayoutActive();
                case PENDING -> onLayoutPending();
                case IDLE    -> onLayoutIdle();
            }
        });
    }

    // ── Layout phase resolution ──────────────────────────────────────────────

    private enum LayoutPhase { ACTIVE, PENDING, IDLE }

    private LayoutPhase resolvePhase(boolean active) {
        if (active)                           return LayoutPhase.ACTIVE;
        if (layoutManager.hasPendingLayout()) return LayoutPhase.PENDING;
        return LayoutPhase.IDLE;
    }

    private void onLayoutActive() {
        harnessState.removeState(STATE_LAYOUT_IDLE);
        harnessState.removeState(STATE_LAYOUT_PENDING);
        harnessState.addState(STATE_LAYOUT_ACTIVE);
    }

    private void onLayoutPending() {
        harnessState.removeState(STATE_LAYOUT_IDLE);
        harnessState.removeState(STATE_LAYOUT_ACTIVE);
        harnessState.addState(STATE_LAYOUT_PENDING);
    }

    private void onLayoutIdle() {
        harnessState.removeState(STATE_LAYOUT_ACTIVE);
        harnessState.removeState(STATE_LAYOUT_PENDING);
        harnessState.addState(STATE_LAYOUT_IDLE);
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    private void renderableRequestRender(TerminalRenderable renderable) {
        uiExecutor.runRentrant(() -> renderableRequestRenderInternal(renderable));
    }

    private void renderableRequestRenderInternal(TerminalRenderable renderable) {
        if (renderable != null && rootRenderable != renderable) {
            return;
        }
        if (!rootRenderable.needsRender() && damageAccumulator.isEmpty()) {
            return;
        }
        render();
    }

    private void render() {
        if (!uiExecutor.isCurrentThread()) {
            uiExecutor.runLater(this::render);
            return;
        }
        if (rootRenderable == null) {
            return;
        }

        List<TerminalRectangle> damageRegions = null;
        try (TerminalBatchBuilder batch = new TerminalBatchBuilder(regionPool)) {
            rootRenderable.toBatch(batch);
            if (allocatedRegion != null) {
                floatingLayoutManager.toBatch(batch, allocatedRegion);
            }

            damageRegions = damageAccumulator.drainRegions();

            if (batch.isBatchEmpty() && damageRegions.isEmpty()) {
                layoutManager.clearIdleCommittingNodes();
                rootRenderable.clearRenderFlag();
                return;
            }

            NoteBytesObject batchCommand = buildBatchCommand(batch, damageRegions);
            sendRenderCommand(batchCommand);
            layoutManager.notifyRenderDispatched();
            rootRenderable.clearRenderFlag();

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            damageRegions = null;
        }
    }

    protected TerminalRectangle getContentBoundsForBatch(TerminalBatchBuilder batch) {
        return rootRenderable != null ? rootRenderable.getRegion() : null;
    }

    protected NoteBytesObject buildBatchCommand(
            TerminalBatchBuilder batch, List<TerminalRectangle> damage) {
        TerminalRectangle contentBounds = getContentBoundsForBatch(batch);
        NoteBytesObject result = batch.build(contentBounds, damage);
        if (contentBounds != null) regionPool.recycle(contentBounds);
        for (TerminalRectangle region : damage) regionPool.recycle(region);
        return result;
    }

    // ── Focus ────────────────────────────────────────────────────────────────

    private void requestFocusInternal(TerminalRenderable renderable) {
        if (renderable == null || !renderable.isFocusable()) return;
        setFocusedInternal(renderable);
    }

    private void setFocusedInternal(TerminalRenderable next) {
        if (next == focused) return;
        if (focused != null) focused.clearFocus();
        focused = next;
        if (focused != null) focused.focus();
    }

    // ── Attachment ───────────────────────────────────────────────────────────

    /**
     * Attach a root renderable and block until the first STATE_LAYOUT_IDLE is
     * reached. This is the one permitted synchronization point for
     * infrastructure setup — it is not a test-assertion wait. After this
     * returns, isIdle() is guaranteed to be true and tests may safely read
     * geometry or register their step-dispatch handlers.
     */
    public void attach(TerminalRenderable root) {
        CountDownLatch firstIdle = new CountDownLatch(1);
        AtomicBoolean  fired     = new AtomicBoolean(false);

        // Register before attaching so we never miss the transition.
        harnessState.onStateAdded(STATE_LAYOUT_IDLE, (old, now, bit) -> {
            if (fired.compareAndSet(false, true)) firstIdle.countDown();
        });

        uiExecutor.runRentrant(() -> attachInternal(root));

        // Race guard: if the tree was already idle before attachInternal ran.
        if (harnessState.hasState(STATE_LAYOUT_IDLE) && fired.compareAndSet(false, true)) {
            firstIdle.countDown();
        }

        try {
            if (!firstIdle.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("attach: timed out waiting for first layout idle");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("attach: interrupted", e);
        }
    }

    private void attachInternal(TerminalRenderable root) {
        if (!uiExecutor.isCurrentThread()) {
            uiExecutor.runLater(() -> attachInternal(root));
            return;
        }
        TerminalRenderable old = rootRenderable;
        if (old == root) return;

        if (old != null) {
            old.unregisterRenderable();
            old.setDamageAccumulator(null);
            damageAccumulator.clear();
        }

        this.rootRenderable  = root;
        this.focused         = null;
        this.lastBatchCommand = null;

        if (root == null) return;

        this.layoutManager.registerRenderable(rootRenderable, (ctx) -> {
            TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder();
            TerminalRectangle regionUpdate = ctx.getRequestedRegion();
            System.out.println("[HARNESS] root layout callback fired");
            System.out.println("[HARNESS]   requestedRegion = " + regionUpdate);
            System.out.println("[HARNESS]   currentRegion   = " + ctx.getCurrentRegion());
            System.out.println("[HARNESS]   allocatedRegion = " + allocatedRegion);
            if (regionUpdate == null) regionUpdate = ctx.getCurrentRegion();
            builder.setHeight(regionUpdate.getHeight());
            builder.setWidth(regionUpdate.getWidth());
            System.out.println("[HARNESS]   -> built h=" + regionUpdate.getHeight()
                + " w=" + regionUpdate.getWidth());
            return builder.build();
        });

        this.rootRenderable.setDamageAccumulator(this::accumulateDamage);
        this.rootRenderable.setRegion(allocatedRegion);
    }

    private void accumulateDamage(TerminalRectangle absoluteRegion) {
        damageAccumulator.add(absoluteRegion);
    }

    private void sendRenderCommand(NoteBytesObject batchCommand) {
        this.lastBatchCommand = batchCommand;
    }

    // ── Region management ────────────────────────────────────────────────────

    /**
     * Update the allocated region and notify the root renderable. Fires on the
     * UI executor and returns immediately — the state machine will enter
     * STATE_LAYOUT_IDLE once the resulting layout pass (or passes) settle, at
     * which point the test's step-dispatch handler will advance to its next
     * case.
     */
    public void setAllocatedRegion(int x, int y, int width, int height) {
        uiExecutor.runRentrant(() -> {
            allocatedRegion.set(x, y, width, height);
            if (rootRenderable != null) rootRenderable.setBounds(allocatedRegion);
        });
    }

    public void setAllocatedRegion(TerminalRectangle region) {
        setAllocatedRegion(region.getX(), region.getY(), region.getWidth(), region.getHeight());
    }

    // ── Trigger ──────────────────────────────────────────────────────────────

    /**
     * Request a layout/render pass. Use to kick off the layout chain in tests
     * after adding components to the hierarchy.
     */
    public void triggerRender() {
        uiExecutor.runRentrant(() -> {
            if (rootRenderable != null) {
                rootRenderable.requestLayoutUpdate();
            }
        });
    }

    // ── State inspection ─────────────────────────────────────────────────────

    /**
     * @return true if no layout pass is running and none is pending.
     *         Safe to assert renderable geometry when this is true.
     */
    public boolean isIdle()    { return harnessState.hasState(STATE_LAYOUT_IDLE); }

    /** @return true if a layout pass is currently executing. */
    public boolean isActive()  { return harnessState.hasState(STATE_LAYOUT_ACTIVE); }

    /** @return true if a pass finished but another is queued (debounce window). */
    public boolean isPending() { return harnessState.hasState(STATE_LAYOUT_PENDING); }

    // ── Accessors ────────────────────────────────────────────────────────────

    public NoteBytesObject getLastBatchCommand()         { return lastBatchCommand; }
    public TerminalRenderable getRoot()                  { return rootRenderable; }
    public int getWidth()                                { return allocatedRegion.getWidth(); }
    public int getHeight()                               { return allocatedRegion.getHeight(); }
    public ConcurrentBitFlagStateMachine getStateMachine(){ return harnessState; }
}