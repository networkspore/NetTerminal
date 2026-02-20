package io.netnotes.terminal.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.ui.Point2D;
import io.netnotes.engine.ui.containers.ContainerEventsFactory;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRectanglePool;
import io.netnotes.terminal.events.containerEvents.TerminalMoveEvent;
import io.netnotes.terminal.events.containerEvents.TerminalResizeEvent;

public class TerminalEventsFactory extends ContainerEventsFactory<
    Point2D,
    TerminalRectangle
> {
    private final TerminalRectanglePool regionPool;
    public TerminalEventsFactory(TerminalRectanglePool regionPool){
        super(); //initialize factories
        this.regionPool = regionPool;
    }

    @Override
    protected RoutedEvent onContainerMove(ContextPath source, NoteBytesReadOnly type, int flags, NoteBytes payload) {
        Point2D point2d = Point2D.fromNoteBytes(payload);
        return new TerminalMoveEvent(source, type, flags, point2d);
    }

    @Override
    protected RoutedEvent onContainerResize(ContextPath sourcePath, NoteBytesReadOnly type, int flags, NoteBytes payload) {
        TerminalRectangle rectangle = TerminalRectangle.fromNoteBytes(payload, regionPool);
        return new TerminalResizeEvent(sourcePath, type, flags, rectangle);
    }


    
    
}
