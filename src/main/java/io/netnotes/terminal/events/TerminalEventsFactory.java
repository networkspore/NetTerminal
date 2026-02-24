package io.netnotes.terminal.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.ui.Point2D;
import io.netnotes.engine.ui.containers.ContainerEventsFactory;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRectanglePool;
import io.netnotes.terminal.events.containerEvents.TerminalRegionChangedEvent;

public class TerminalEventsFactory extends ContainerEventsFactory<
    Point2D,
    TerminalRectangle
> {
    private final TerminalRectanglePool regionPool;

    public TerminalEventsFactory(TerminalRectanglePool regionPool){
        super(); //initialize factories
        this.regionPool = regionPool;

        if(regionPool == null){
            throw new NullPointerException("[TerminalEventsFactory] instantiated with null region pool");
        }
    }

    @Override
    protected TerminalRegionChangedEvent onContainerRegionChanged(
        ContextPath sourcePath,
        NoteBytesReadOnly type, 
        int flags, 
        NoteBytes payload
    ) {

        if(regionPool == null){
            throw new NullPointerException("[TerminalEventsFactory] regionPool became null");
        }

        TerminalRectangle rectangle = TerminalRectangle.fromNoteBytes(payload, regionPool);
        return new TerminalRegionChangedEvent(sourcePath, type, flags, rectangle);
    }

}
