package io.netnotes.terminal.events.containerEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.ui.Point2D;
import io.netnotes.engine.ui.containers.containerEvents.ContainerResizeEvent;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.terminal.TerminalRectangle;

public class TerminalResizeEvent extends ContainerResizeEvent<
    Point2D,
    TerminalRectangle
> {


    
    public TerminalResizeEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int flags, TerminalRectangle rectangle) {
        super(sourcePath, typeBytes, flags, rectangle);
    }
  

    @Override
    public String toString() {
        return String.format("ContainerResizeEvent[%s, source=%s]", 
            getRegion().toString(), getSourcePath());
    }

}