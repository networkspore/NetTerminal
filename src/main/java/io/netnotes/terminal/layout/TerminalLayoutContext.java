package io.netnotes.terminal.layout;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.engine.ui.layout.LayoutContext;
import io.netnotes.engine.ui.Point2D;

/**
 * TerminalLayoutContext - Terminal-specific layout context
 * 
 * Provides terminal-specific information for layout calculations including:
 * - Allocated space in rows/cols terminology
 * - Parent/child relationships
 * - Text centering and positioning helpers
 * - Bounds checking and clamping
 */
public class TerminalLayoutContext extends LayoutContext<
    TerminalBatchBuilder,
    TerminalRenderable,
    Point2D,
    TerminalRectangle,
    TerminalLayoutData,
    TerminalLayoutCallback,
    TerminalLayoutContext,
    TerminalLayoutNode
> {
     
    public TerminalLayoutContext() {
        super();
    }

    
}