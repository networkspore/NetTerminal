package io.netnotes.terminal.layout;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.engine.ui.layout.LayoutGroup;
import io.netnotes.engine.ui.Point2D;

public class TerminalLayoutGroup extends LayoutGroup<
    TerminalBatchBuilder,
    TerminalRenderable,
    Point2D,
    TerminalRectangle,
    TerminalLayoutData,
    TerminalLayoutContext,
    TerminalLayoutGroupCallback,
    TerminalLayoutNode,
    TerminalGroupCallbackEntry,
    TerminalLayoutGroup
> {

    public TerminalLayoutGroup(String groupId) {
        super(groupId);
    }
    
}
