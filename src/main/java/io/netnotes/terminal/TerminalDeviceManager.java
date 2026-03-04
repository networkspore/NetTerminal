package io.netnotes.terminal;

import io.netnotes.engine.ui.renderer.DeviceManager;
import io.netnotes.noteBytes.NoteBytes;

public abstract class TerminalDeviceManager extends DeviceManager
<
    TerminalContainerHandle,
    TerminalDeviceManager
>{

    protected TerminalDeviceManager(NoteBytes deviceId, NoteBytes mode, NoteBytes deviceType) {
        super(deviceId, mode, deviceType);
    }

   
    
}
