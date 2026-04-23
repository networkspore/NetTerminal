package io.netnotes.terminal.layout;

import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.layout.TerminalLayoutData.TerminalLayoutDataBuilder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.virtualExecutors.SerializedVirtualExecutor;
import io.netnotes.engine.virtualExecutors.VirtualExecutors;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalDamageAccumulator;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRectanglePool;

/**
 * TerminalLayoutTestHarness
 */
public class TerminalLayoutTestHarness {
    public final String name = "TerminalLayoutTestHarness";

    private TerminalLayoutManager layoutManager;
    private TerminalFloatingLayoutManager floatingLayoutManager;

    private TerminalRectanglePool regionPool;
    private TerminalRenderable focused = null;
    private TerminalRenderable rootRenderable = null;
    protected TerminalDamageAccumulator damageAccumulator = null;
    private SerializedVirtualExecutor uiExecutor = VirtualExecutors.getUiExecutor();
    private TerminalRectangle allocatedRegion;
    private NoteBytesObject lastBatchCommand;

    public TerminalLayoutTestHarness(int width, int height) {
        this.regionPool = TerminalRectanglePool.getInstance();
        this.damageAccumulator = new TerminalDamageAccumulator(regionPool);
        this.allocatedRegion = new TerminalRectangle(0, 0, width, height);
        this.floatingLayoutManager = new TerminalFloatingLayoutManager(name, regionPool);
        this.layoutManager = new TerminalLayoutManager(name, floatingLayoutManager);
        this.layoutManager.setFocusRequester(this::requestFocusInternal);
        this.layoutManager.setRenderRequester(this::renderableRequestRender);

    }

    private void renderableRequestRender(TerminalRenderable renderable) {
        uiExecutor.runRentrant(() -> renderableRequestRenderInternal(renderable));
    }

    private void renderableRequestRenderInternal(TerminalRenderable renderable) {
        if (renderable != null && rootRenderable != renderable) {
            
            return;
        }

        /*if (!renderReadySnapshot) {
            
            return;
        }*/


        if (!rootRenderable.needsRender() && damageAccumulator.isEmpty()) {
            /*logRenderDropped(
                String.format(
                    "request arrived with no pending damage or dirty renderables (committingNodes=%s)",
                    renderableLayoutManager.summarizeCommittingNodes()
                ),
                RenderableLayoutManager.DiagnosticMode.TRACE,
                ROUTINE_DIAGNOSTIC_LOG_LEVEL
            );*/
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
        try (TerminalBatchBuilder batch = new TerminalBatchBuilder(regionPool);) {
            rootRenderable.toBatch(batch);
            if (allocatedRegion != null) {
                floatingLayoutManager.toBatch(batch, allocatedRegion);
            }

            // Drain after toBatch — ownership of regions transfers to us.
            // We are responsible for recycling them after use.
            damageRegions = damageAccumulator.drainRegions();

            if (batch.isBatchEmpty() && damageRegions.isEmpty()) {
              
                /*String.format(
                    "batch builder and damage accumulator were both empty (committingNodes=%s)",
                    layoutManager.summarizeCommittingNodes()
                )*/
                
                layoutManager.clearIdleCommittingNodes();
                rootRenderable.clearRenderFlag();
                return; // damageRegions is empty so nothing to recycle
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
    

     protected NoteBytesObject buildBatchCommand(TerminalBatchBuilder batch, List<TerminalRectangle> damage) {
        TerminalRectangle contentBounds = getContentBoundsForBatch(batch);
        NoteBytesObject result = batch.build(contentBounds, damage);

        // Both contentBounds and damage regions have been serialized into result.
        // Recycle them now — no caller above us holds a reference to either.
        if (contentBounds != null) {
            regionPool.recycle(contentBounds);
        }
        for (TerminalRectangle region : damage) {
            regionPool.recycle(region);
        }

        return result;
    }

    private void requestFocusInternal(TerminalRenderable renderable) {
        if (renderable == null) {
            return;
        }
        if (!renderable.isFocusable()) {
            return;
        }
        setFocusedInternal(renderable);
    }

    private void setFocusedInternal(TerminalRenderable next) {
        if (next == focused) {
            return;
        }

        if (focused != null) {
            focused.clearFocus();
        }

        focused = next;
        if (focused != null) {
            focused.focus();
        }
    }


    public NoteBytesObject getLastBatchCommand() {
        return lastBatchCommand;
    }

    /**
     * Wire a root renderable into the layout system and give it a region,
     * *no callback for now as the callback is set internally align with the allocated region
     *
     * @param root         the root renderable (e.g. a TerminalRegion)
     */
    public void attach(TerminalRenderable root) {
        if(!uiExecutor.isCurrentThread()){
            uiExecutor.runLater(() -> attach(root));
            return;
        }
        TerminalRenderable old = rootRenderable;
        if (old == root) return;

        if (old != null) {
            old.unregisterRenderable();
            // Remove: old.setRenderRequest(null);
            old.setDamageAccumulator(null);
            damageAccumulator.clear();
        }

        this.rootRenderable = root;
        this.focused = null;
        this.lastBatchCommand = null;

        if (root == null) return;

        this.layoutManager.registerRenderable(rootRenderable, (ctx) -> {
            TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder();
            TerminalRectangle regionUpdate = ctx.getRequestedRegion();
            builder.setHeight(regionUpdate.getHeight());
            builder.setWidth(regionUpdate.getWidth());
            return builder.build();
        });

        this.rootRenderable.setDamageAccumulator(this::accumulateDamage);
        this.rootRenderable.setRegion(allocatedRegion);
    }

    private void accumulateDamage(TerminalRectangle absoluteRegion) {
        damageAccumulator.add(absoluteRegion);
    }

    /**
     * parse batch command
     * @param batchCommand
     */
    private void sendRenderCommand(NoteBytesObject batchCommand){
        this.lastBatchCommand = batchCommand;
    }

    public void setAllocatedRegion(int x, int y, int width, int height){
        allocatedRegion.set(x, y, width, height);
        rootRenderable.setBounds(allocatedRegion);
    }

    /**
     * Update the allocated region on the harness, which will be propagated to rootRenderable
     * and trigger a layout cascade through the preferredSizing methods.
     */
    public void setAllocatedRegion(TerminalRectangle allocatedRegion) {
        this.allocatedRegion = allocatedRegion;
        setAllocatedRegion(allocatedRegion.getX(), allocatedRegion.getY(),
                           allocatedRegion.getWidth(), allocatedRegion.getHeight());
    }

    public TerminalRenderable getRoot() { return rootRenderable; }
    public int getWidth()  { return allocatedRegion.getWidth(); }
    public int getHeight() { return allocatedRegion.getHeight(); }



    /**
     * Wait for the next layout pass to complete.
     * This blocks until the debounced layout cascade finishes and the callback fires.
     *
     * @return true if layout completed, false if timeout
     */

        public boolean waitForLayoutComplete() {
        CompletableFuture<Boolean> done = new CompletableFuture<>();

        layoutManager.setLayoutStateListener(active -> {
            if (!active) done.complete(Boolean.TRUE);
        });

        try {
            return done.get(1, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for layout", e);
        } catch (Exception e) {
            throw new AssertionError("Failed while waiting for layout", e);
        } finally {
            layoutManager.setLayoutStateListener(null);
        }
    }


    /**
     * Ensure all layout work completes by flushing the UI executor.
     * This is needed after mutations like resizing to ensure the cascade
     * through preferredSizing methods has fully committed.
     */
    public void flushLayout() {
        try {
            uiExecutor.submit(() -> null).get(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while draining UI executor", e);
        } catch (Exception e) {
            throw new AssertionError("Failed while draining UI executor", e);
        }
    }
}
