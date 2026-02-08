package io.netnotes.terminal.events.containerEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.noteBytes.NoteBytesReadOnly;

public class TerminalFocusGainedEvent extends TerminalRoutedEvent {
    private final ContextPath sourcePath;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;

    public TerminalFocusGainedEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int flags) {
        this.sourcePath = sourcePath;
        this.stateFlags = flags;
        this.typeBytes = typeBytes;
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
