package io.netnotes.terminal.layout;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.engine.ui.layout.LayoutCallback;
import io.netnotes.engine.ui.Point2D;

@FunctionalInterface
public interface TerminalLayoutCallback extends LayoutCallback<
    TerminalBatchBuilder,
    TerminalRenderable,
    Point2D,
    TerminalRectangle,
    TerminalLayoutContext,
    TerminalLayoutData,
    TerminalLayoutCallback
> {
}

