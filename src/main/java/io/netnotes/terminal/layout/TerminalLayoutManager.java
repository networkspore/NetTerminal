package io.netnotes.terminal.layout;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TerminalRenderable.TerminalGroupStateEntry;
import io.netnotes.engine.ui.Point2D;
import io.netnotes.engine.ui.RenderableLayoutManager;

/**
 * TerminalLayoutManager - Manages layout tree for terminal renderables
 * 
 * TERMINAL-SPECIFIC CONCERNS:
 * - Character-cell coordinate system (rows/cols not pixels)
 * - Hidden regions collapse to zero rows/cols (preserves original bounds)
 * - Identity layout maintains current allocation (useful for static text)
 * - Regions measured in terminal character positions
 * 
 * HIDDEN STATE HANDLING:
 * - applyHidden() preserves original region for cheap toggle restoration
 * - Collapsed region maintains offset (position) but zero size
 * - Pre-hidden region stored on renderable, restored on re-show
 * 
 * THREAD SAFETY:
 * - All methods execute on uiExecutor (inherited from parent)
 * - Factory methods are stateless, safe for concurrent calls
 * - Region pooling prevents allocation thrash during layout passes
 */
public class TerminalLayoutManager extends RenderableLayoutManager<
    TerminalBatchBuilder,
    TerminalRenderable,
    Point2D,
    TerminalRectangle,
    TerminalLayoutContext,
    TerminalLayoutData,
    TerminalLayoutCallback,
    TerminalLayoutGroupCallback,
    TerminalGroupCallbackEntry,
    TerminalGroupStateEntry,
    TerminalLayoutGroup,
    TerminalLayoutNode
>
 {

    public TerminalLayoutManager(String containerName, TerminalFloatingLayoutManager floatingManager) {
        super(containerName, floatingManager);
    }

    @Override
    protected TerminalLayoutNode createRenderableNode(TerminalRenderable renderable) {
        return new TerminalLayoutNode(renderable);
    }

    @Override
    protected TerminalLayoutContext createRenderableContext(TerminalLayoutNode node) {
        TerminalLayoutContext context = TerminalLayoutContextPool.getInstance().obtain();
        context.initialize(node);
        return context;
    }

    @Override
    protected void recycleLayoutData(TerminalLayoutData layoutData) {
        TerminalLayoutDataPool.getInstance().recycleData(layoutData);
    }

     @Override
    protected void recycleLayoutContext(TerminalLayoutContext context) {
        TerminalLayoutContextPool.getInstance().recycle(context);
    }

     @Override
     protected TerminalLayoutContext[] createContextArray(int size) {
        return new TerminalLayoutContext[size];
     }

     @Override
     protected void recycleLayoutContexts(TerminalLayoutContext[] contexts) {
        for(int i = 0; i < contexts.length ; i++){
            TerminalLayoutContext ctx = contexts[i];
            contexts[i] = null;
            if(ctx != null){
                recycleLayoutContext(ctx);
            }
        }
     }

     @Override
     protected TerminalLayoutGroup createEmptyGroup(String groupId) {
        return new TerminalLayoutGroup(groupId);
     }

 }