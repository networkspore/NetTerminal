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
        this.regionPool = regionPool;
    }

    @Override
    protected RoutedEvent onContainerMove(ContextPath source, NoteBytesReadOnly type, int flags, NoteBytes[] payload) {
        int x = 0;
        int y = 0;
        if(payload != null && payload.length > 1){
            x = payload[0].getAsInt();
            y = payload[1].getAsInt();
        }
        Point2D point2d = new Point2D(x, y);
        return new TerminalMoveEvent(source, type, flags, point2d);
    }

    @Override
    protected RoutedEvent onContainerResize(ContextPath sourcePath, NoteBytesReadOnly type, int flags, NoteBytes[] payload) {
        TerminalRectangle rectangle = regionPool.obtain();
        if(payload != null && payload.length > 3){
            int x = payload[0].getAsInt();
            int y = payload[1].getAsInt();
            int width = payload[2].getAsInt();
            int height = payload[3].getAsInt();
            rectangle.set(x, y, width, height);
        }
        return new TerminalResizeEvent(sourcePath, type, flags, rectangle);
    }


    
    
}
