package io.netnotes.terminal.events.containerEvents;

import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.noteBytes.NoteBytesReadOnly;

public class TerminalRegionEvent extends TerminalRoutedEvent {
    private final TerminalRectangle region;
    private final ContextPath sourcePath;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;
    
    public TerminalRegionEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int flags, TerminalRectangle region) {
        this.sourcePath = sourcePath;
        this.region = region;
        this.stateFlags = flags;
        this.typeBytes = typeBytes;
    }
    
    public TerminalRectangle getRegion() { return region; }
    
    @Override
    public String toString() {
        return String.format("ContainerResizeEvent[%s, source=%s]", 
            region, getSourcePath());
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }

    @Override
    public NoteBytesReadOnly getEventTypeBytes() {
        return typeBytes;
    }

     @Override
    public int getStateFlags() {
        return stateFlags;
    }
    @Override
    public void setStateFlags(int flags) {
        stateFlags = flags;
    }
}