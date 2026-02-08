package io.netnotes.terminal.events.containerEvents;
    
import io.netnotes.engine.io.ContextPath;
import io.netnotes.noteBytes.NoteBytesReadOnly;


public final class TerminalMoveEvent extends TerminalRoutedEvent {
    private final ContextPath sourcePath;
    private final int x;
    private final int y;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;

    public TerminalMoveEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int flags, int x, int y) {
        this.sourcePath = sourcePath;
        this.x = x;
        this.y = y;
        this.stateFlags = flags;
        this.typeBytes = typeBytes;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public int getX() { return x; }
    public int getY() { return y; }
    
    @Override
    public String toString() {
        return String.format("ContainerMoveEvent[pos=(%d,%d), source=%s]", 
            x, y, getSourcePath());
    }

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
