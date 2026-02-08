package io.netnotes.terminal.layout;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.engine.ui.layout.GroupLayoutCallback;
import io.netnotes.engine.ui.Point2D;

@FunctionalInterface
public interface TerminalLayoutGroupCallback extends GroupLayoutCallback<
    TerminalBatchBuilder,
    TerminalRenderable,
    Point2D,
    TerminalRectangle,
    TerminalLayoutData,
    TerminalLayoutContext,
    TerminalLayoutNode,
    TerminalLayoutGroupCallback
> {
    
}
