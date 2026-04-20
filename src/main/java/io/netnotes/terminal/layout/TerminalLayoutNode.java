package io.netnotes.terminal.layout;

//import io.netnotes.debug.RenderDiagnostics;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.engine.ui.renderer.LayoutNode;
import io.netnotes.engine.ui.Point2D;

/**
 * TerminalLayoutNode - Layout node for terminal renderables
 * 
 * Manages layout calculation and application for terminal renderables
 */
public class TerminalLayoutNode extends LayoutNode<
    TerminalBatchBuilder,
    TerminalRenderable,
    Point2D,
    TerminalRectangle,
    TerminalLayoutData,
    TerminalLayoutContext,
    TerminalLayoutCallback,
    TerminalLayoutGroupCallback,
    TerminalLayoutGroup,
    TerminalLayoutNode
> {
    
    public TerminalLayoutNode(TerminalRenderable renderable) {
        super(renderable);
    }
    

    /**
     * Get terminal-specific renderable
     * Convenience cast to avoid repeated casting
     */
    @Override
    public TerminalRenderable getRenderable() {
        return (TerminalRenderable) super.getRenderable();
    }


    @Override
    protected TerminalLayoutData obtainLayoutData() {
        return TerminalLayoutDataPool.getInstance().obtainData();
    }


    @Override
    protected void recycleLayoutData(TerminalLayoutData layoutData) {
        layoutData.recycleRegion();
        TerminalLayoutDataPool.getInstance().recycleData(layoutData);
    }

    /*
    @Override
    public void calculateLayout(TerminalLayoutContext context) {
        TerminalRectangle parentRegion = context != null ? context.getParentRegion() : null;
        if (parentRegion == null) {
            RenderDiagnostics.logRenderBlocker(
                "layout-node-parent-null:" + getName(),
                "TerminalLayoutNode.calculateLayout",
                "null-parent-region",
                () -> "node=" + RenderDiagnostics.summarizeNode(this)
            );
        } else if (getRenderable().isEffectivelyVisible()
            && (parentRegion.getWidth() <= 0 || parentRegion.getHeight() <= 0)) {
            RenderDiagnostics.logRenderBlocker(
                "layout-node-parent-empty:" + getName(),
                "TerminalLayoutNode.calculateLayout",
                "empty-parent-region",
                () -> "parent=" + RenderDiagnostics.summarizeRegion(parentRegion)
                    + "\n\tnode=" + RenderDiagnostics.summarizeNode(this)
            );
        }
        super.calculateLayout(context);
    } */

}
