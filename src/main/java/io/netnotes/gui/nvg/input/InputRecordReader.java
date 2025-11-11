package io.netnotes.gui.nvg.input;

import io.netnotes.engine.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.gui.nvg.input.events.InputRecord;

/**
 * InputRecordReader - Helper for reading data from InputRecord payload
 * 
 * This simplifies access to the typed data in InputRecord payloads,
 * providing convenient accessors for common input event data.
 */
public class InputRecordReader {
    private final InputRecord record;
    private final NoteBytesArrayReadOnly payload;
    
    public InputRecordReader(InputRecord record) {
        this.record = record;
        this.payload = record.payload();
    }
    
    /**
     * Check if payload exists and has data
     */
    public boolean hasPayload() {
        return payload != null && payload.size() > 0;
    }
    
    /**
     * Get payload size
     */
    public int getPayloadSize() {
        return hasPayload() ? payload.size() : 0;
    }
    
    /**
     * Get a payload item as integer
     */
    public int getInt(int index) {
        if (!hasPayload() || index >= payload.size()) {
            throw new IndexOutOfBoundsException("Payload index " + index + " out of bounds");
        }
        return payload.get(index).getAsInt();
    }
    
    /**
     * Get a payload item as double
     */
    public double getDouble(int index) {
        if (!hasPayload() || index >= payload.size()) {
            throw new IndexOutOfBoundsException("Payload index " + index + " out of bounds");
        }
        return payload.get(index).getAsDouble();
    }
    
    /**
     * Get a payload item as string
     */
    public String getString(int index) {
        if (!hasPayload() || index >= payload.size()) {
            throw new IndexOutOfBoundsException("Payload index " + index + " out of bounds");
        }
        return payload.get(index).getAsString();
    }
    
    // Keyboard event accessors
    
    /**
     * Get key code (for KEY_DOWN, KEY_UP, KEY_REPEAT)
     */
    public int getKey() {
        return getInt(0);
    }
    
    /**
     * Get scan code (for keyboard events)
     */
    public int getScancode() {
        return getInt(1);
    }
    
    /**
     * Get codepoint (for KEY_CHAR events)
     */
    public int getCodepoint() {
        return getInt(0);
    }
    
    // Mouse event accessors
    
    /**
     * Get mouse X coordinate
     */
    public double getMouseX() {
        if (isMouseMoveAbsolute()) {
            return getDouble(0);
        } else if (isMouseButton()) {
            return getDouble(1);
        } else if (isMouseClick()) {
            return getDouble(1);
        } else if (isScroll()) {
            return getDouble(2);
        }
        throw new IllegalStateException("Not a mouse position event");
    }
    
    /**
     * Get mouse Y coordinate
     */
    public double getMouseY() {
        if (isMouseMoveAbsolute()) {
            return getDouble(1);
        } else if (isMouseButton()) {
            return getDouble(2);
        } else if (isMouseClick()) {
            return getDouble(2);
        } else if (isScroll()) {
            return getDouble(3);
        }
        throw new IllegalStateException("Not a mouse position event");
    }
    
    /**
     * Get mouse button (for button events)
     */
    public int getButton() {
        if (isMouseButton() || isMouseClick()) {
            return getInt(0);
        }
        throw new IllegalStateException("Not a mouse button event");
    }
    
    /**
     * Get scroll offsets
     */
    public double getScrollX() {
        if (!isScroll()) {
            throw new IllegalStateException("Not a scroll event");
        }
        return getDouble(0);
    }
    
    public double getScrollY() {
        if (!isScroll()) {
            throw new IllegalStateException("Not a scroll event");
        }
        return getDouble(1);
    }
    
    /**
     * Get click count (for MOUSE_CLICK)
     */
    public int getClickCount() {
        if (!isMouseClick()) {
            throw new IllegalStateException("Not a click event");
        }
        return getInt(3);
    }
    
    // Type checks
    
    public boolean isKeyDown() {
        return record.type().equals(InputPacket.Types.TYPE_KEY_DOWN);
    }
    
    public boolean isKeyUp() {
        return record.type().equals(InputPacket.Types.TYPE_KEY_UP);
    }
    
    public boolean isKeyRepeat() {
        return record.type().equals(InputPacket.Types.TYPE_KEY_REPEAT);
    }
    
    public boolean isKeyChar() {
        return record.type().equals(InputPacket.Types.TYPE_KEY_CHAR);
    }
    
    public boolean isMouseMoveAbsolute() {
        return record.type().equals(InputPacket.Types.TYPE_MOUSE_MOVE_ABSOLUTE);
    }
    
    public boolean isMouseMoveRelative() {
        return record.type().equals(InputPacket.Types.TYPE_MOUSE_MOVE_RELATIVE);
    }
    
    public boolean isMouseButton() {
        return record.type().equals(InputPacket.Types.TYPE_MOUSE_BUTTON_DOWN) ||
               record.type().equals(InputPacket.Types.TYPE_MOUSE_BUTTON_UP);
    }
    
    public boolean isMouseButtonDown() {
        return record.type().equals(InputPacket.Types.TYPE_MOUSE_BUTTON_DOWN);
    }
    
    public boolean isMouseButtonUp() {
        return record.type().equals(InputPacket.Types.TYPE_MOUSE_BUTTON_UP);
    }
    
    public boolean isMouseClick() {
        return record.type().equals(InputPacket.Types.TYPE_MOUSE_CLICK);
    }
    
    public boolean isScroll() {
        return record.type().equals(InputPacket.Types.TYPE_SCROLL);
    }
    
    // Modifier checks
    
    public boolean hasModifier(int flag) {
        return (record.stateFlags() & flag) != 0;
    }
    
    public boolean hasShift() {
        return hasModifier(InputPacket.StateFlags.MOD_SHIFT);
    }
    
    public boolean hasControl() {
        return hasModifier(InputPacket.StateFlags.MOD_CONTROL);
    }
    
    public boolean hasAlt() {
        return hasModifier(InputPacket.StateFlags.MOD_ALT);
    }
    
    public boolean hasSuper() {
        return hasModifier(InputPacket.StateFlags.MOD_SUPER);
    }
    
    /**
     * Get the underlying record
     */
    public InputRecord getRecord() {
        return record;
    }
}