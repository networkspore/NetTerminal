package io.netnotes.terminal;

import io.netnotes.engine.ui.containers.ContainerConfig;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.collections.NoteBytesMap;

public class TerminalContainerConfig extends ContainerConfig<
    TerminalRectangle,
    TerminalContainerConfig
> {
    
    public TerminalContainerConfig(){
        super();
    }
    public TerminalContainerConfig(NoteBytesMap noteBytes){
        super(noteBytes);
    }

    @Override
    protected TerminalRectangle createRegionFromnNoteBytes(NoteBytes regionBytes) {
        return TerminalRectangle.fromNoteBytes(regionBytes);
    }
    
}
