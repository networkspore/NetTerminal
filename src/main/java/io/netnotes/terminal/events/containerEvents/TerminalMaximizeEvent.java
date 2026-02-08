package io.netnotes.terminal.events.containerEvents;


import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public final class TerminalMaximizeEvent extends TerminalRoutedEvent {
    private final ContextPath sourcePath;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;

    public TerminalMaximizeEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int flags) {
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
