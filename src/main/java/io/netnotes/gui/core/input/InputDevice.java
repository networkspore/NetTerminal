package io.netnotes.gui.core.input;


/**
 * Information about a physical input device
 */
public class InputDevice {
    public final byte deviceId;
    public final String name;
    public final short type;
    public final String vendorId;
    public final String productId;
    
    public static class DeviceType {
        public static final short MOUSE = 1;
        public static final short KEYBOARD = 2;
        public static final short TOUCHPAD = 3;
        public static final short TOUCHSCREEN = 4;
        public static final short PEN_TABLET = 5;
        public static final short GAMEPAD = 6;

        public static final short CUSTOM_1 = 1001;
        public static final short CUSTOM_2 = 1002;
        public static final short CUSTOM_3 = 1003;
    }

    
    public InputDevice(byte deviceId, String name, short type) {
        this.deviceId = deviceId;
        this.name = name;
        this.type = type;
        this.vendorId = "";
        this.productId = "";
    }
}
