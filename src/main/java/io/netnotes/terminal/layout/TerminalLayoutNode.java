package io.netnotes.terminal.layout;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.engine.ui.layout.LayoutNode;
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
        TerminalLayoutDataPool.getInstance().recycleData(layoutData);
    }



}