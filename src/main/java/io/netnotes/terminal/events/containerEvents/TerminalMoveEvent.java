package io.netnotes.terminal.events.containerEvents;
    
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.ui.Point2D;
import io.netnotes.engine.ui.containers.containerEvents.ContainerMoveEvent;
import io.netnotes.noteBytes.NoteBytesReadOnly;


public class TerminalMoveEvent extends ContainerMoveEvent<Point2D> {

    public TerminalMoveEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int flags, Point2D point2d) {
        super(sourcePath, typeBytes, flags, point2d);
    }
}
