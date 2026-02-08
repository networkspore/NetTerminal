package io.netnotes.terminal;
import io.netnotes.engine.ui.containers.DeviceManager;

public abstract class TerminalDeviceManager extends DeviceManager
<
    TerminalContainerHandle,
    TerminalDeviceManager
>{

    protected TerminalDeviceManager(String deviceId, String mode, String deviceType) {
        super(deviceId, mode, deviceType);
    }

   
    
}
