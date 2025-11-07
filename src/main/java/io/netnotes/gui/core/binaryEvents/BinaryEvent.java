package io.netnotes.gui.core.binaryEvents;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteIntegerArray;
import io.netnotes.engine.noteBytes.NoteShort;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

/**
 * Binary event using NoteBytes format.
 * 
 * Structure:
 * [1 byte type tag = NOTE_BYTES_ARRAY_TYPE]
 * [4 bytes array length]
 * [NoteShort: event type code]
 * [NoteBytes...: payload items]
 */
public class BinaryEvent {
    
    // Event type codes (using short = 2 bytes)
    public static final class Types {
        private Types() {}
        
        // Cursor events (0-99)
        public static final short CURSOR_MOVE_CHAR_FORWARD     = 0;
        public static final short CURSOR_MOVE_CHAR_BACKWARD    = 1;
        public static final short CURSOR_MOVE_WORD_FORWARD     = 2;
        public static final short CURSOR_MOVE_WORD_BACKWARD    = 3;
        public static final short CURSOR_MOVE_LINE_START       = 4;
        public static final short CURSOR_MOVE_LINE_END         = 5;
        public static final short CURSOR_MOVE_DOC_START        = 6;
        public static final short CURSOR_MOVE_DOC_END          = 7;
        public static final short CURSOR_MOVE_ABSOLUTE         = 8;
        public static final short CURSOR_MOVE_UP               = 9;
        public static final short CURSOR_MOVE_DOWN             = 10;
        
        
        // Selection events (100-199)
        public static final short SELECTION_SET                = 100;
        public static final short SELECTION_CLEAR              = 101;
        public static final short SELECTION_EXTEND             = 102;
        public static final short SELECTION_WORD               = 103;
        public static final short SELECTION_LINE               = 104;
        public static final short SELECTION_ALL                = 105;
        
        // Text mutation events (200-299)
        public static final short TEXT_INSERT                  = 200;
        public static final short TEXT_DELETE_CHAR_BEFORE      = 201;
        public static final short TEXT_DELETE_CHAR_AFTER       = 202;
        public static final short TEXT_DELETE_RANGE            = 203;
        public static final short TEXT_REPLACE_RANGE           = 204;
        
        // Segment events (300-399)
        public static final short SEGMENT_INSERT               = 300;
        public static final short SEGMENT_REMOVE               = 301;
        public static final short SEGMENT_MOVE                 = 302;
        public static final short SEGMENT_UPDATE               = 303;
        
        // State events (400-499)
        public static final short STATE_SET_FLAG               = 400;
        public static final short STATE_CLEAR_FLAG             = 401;
        public static final short STATE_TOGGLE_FLAG            = 402;
        
        // Viewport events (500-599)
        public static final short VIEWPORT_SCROLL              = 500;
        public static final short VIEWPORT_RESIZE              = 501;
        public static final short VIEWPORT_ZOOM                = 502;
        
        // Focus events (600-699)
        public static final short FOCUS_SET                    = 600;
        public static final short FOCUS_CLEAR                  = 601;
        public static final short FOCUS_NEXT                   = 602;
        public static final short FOCUS_PREV                   = 603;
        
        // Grid resize events (700-799)
        public static final short GRID_RESIZE_START            = 700;
        public static final short GRID_RESIZE_UPDATE           = 701;
        public static final short GRID_RESIZE_END              = 702;
        
        // Composite events (800-899)
        public static final short BATCH_BEGIN                  = 800;
        public static final short BATCH_END                    = 801;
        public static final short TRANSACTION_BEGIN            = 802;
        public static final short TRANSACTION_COMMIT           = 803;
        public static final short TRANSACTION_ROLLBACK         = 804;

        // Raw input events (1000-1099)
        public static final short RAW_KEY_PRESSED         = 1000;
        public static final short RAW_KEY_RELEASED        = 1001;
        public static final short RAW_KEY_TYPED           = 1002;
        public static final short RAW_MOUSE_PRESSED       = 1010;
        public static final short RAW_MOUSE_RELEASED      = 1011;
        public static final short RAW_MOUSE_MOVED         = 1012;
        public static final short RAW_MOUSE_DRAGGED       = 1013;
        public static final short RAW_MOUSE_ENTERED       = 1014;
        public static final short RAW_MOUSE_EXITED        = 1015;
        public static final short RAW_MOUSE_SCROLL        = 1016;
        public static final short RAW_FOCUS_GAINED        = 1020;
        public static final short RAW_FOCUS_LOST          = 1021;
        public static final short RAW_HSCROLL             = 1022;
        public static final short RAW_VSCROLL             = 1023;
        public static final short RAW_WINDOW_DETATCHED      = 1024;
        public static final short RAW_WINDOW_ATTACHED      = 1025;
    }
    
    private NoteBytesArray delegate;

    public BinaryEvent() {
        delegate = new NoteBytesArray();
    }
    
    public BinaryEvent(byte[] bytes) {
        delegate = new NoteBytesArray(bytes);
    }
    
    public short getType(){
       
        if (delegate.byteLength() == 0) {
            throw new IllegalStateException("Empty event");
        }

        NoteBytes first = delegate.get(0);
        
        if (first.getType() != NoteBytesMetaData.SHORT_TYPE) {
            throw new IllegalStateException("Event type must be SHORT");
        }

        return delegate.getAsShort();
    }
    
    /**
     * Get payload item at index (index 0 is event type)
     */
    public NoteBytes getPayload(int index) {
        return delegate.get(index + 1); // Skip type at index 0
    }
    
    /**
     * Get payload item at index (index 0 is event type)
     */
    public int getPayloadSize() {
        return delegate.size() -1; // Skip type at index 0
    }
    
  
    // ========== Builder Pattern ==========
    
    public static class Builder {
        private final NoteBytesArray array = new NoteBytesArray();
        
        public Builder type(short eventType) {
            array.add(new NoteShort(eventType));
            return this;
        }
        
        public Builder add(Object value) {
            array.add(NoteBytes.of(value));
            return this;
        }
      
        
        public BinaryEvent build() {
            return new BinaryEvent(array.get());
        }
    }
    
    // ========== Type-Safe Accessors ==========
    
    /**
     * Get payload as specific type with validation
     */
    public byte getPayloadAsByte(int index) {
        NoteBytes payload = getPayload(index);
        if (payload.getType() != NoteBytesMetaData.BYTE_TYPE) {
            throw new IllegalStateException("Expected BYTE at index " + index);
        }
        return payload.getAsByte();
    }
    
    public short getPayloadAsShort(int index) {
        NoteBytes payload = getPayload(index);
        if (payload.getType() != NoteBytesMetaData.SHORT_TYPE) {
            throw new IllegalStateException("Expected SHORT at index " + index);
        }
        return payload.getAsShort();
    }
    
    public int getPayloadAsInt(int index) {
        NoteBytes payload = getPayload(index);
        if (payload.getType() != NoteBytesMetaData.INTEGER_TYPE) {
            throw new IllegalStateException("Expected INTEGER at index " + index);
        }
        return payload.getAsInt();
    }
    
    public long getPayloadAsLong(int index) {
        NoteBytes payload = getPayload(index);
        if (payload.getType() != NoteBytesMetaData.LONG_TYPE) {
            throw new IllegalStateException("Expected LONG at index " + index);
        }
        return payload.getAsLong();
    }
    
    public float getPayloadAsFloat(int index) {
        NoteBytes payload = getPayload(index);
        if (payload.getType() != NoteBytesMetaData.FLOAT_TYPE) {
            throw new IllegalStateException("Expected FLOAT at index " + index);
        }
        return payload.getAsFloat();
    }
    
    public double getPayloadAsDouble(int index) {
        NoteBytes payload = getPayload(index);
        if (payload.getType() != NoteBytesMetaData.DOUBLE_TYPE) {
            throw new IllegalStateException("Expected DOUBLE at index " + index);
        }
        return payload.getAsDouble();
    }
    
    public boolean getPayloadAsBoolean(int index) {
        NoteBytes payload = getPayload(index);
        if (payload.getType() != NoteBytesMetaData.BOOLEAN_TYPE) {
            throw new IllegalStateException("Expected BOOLEAN at index " + index);
        }
        return payload.getAsBoolean();
    }
    
    public String getPayloadAsString(int index) {
        NoteBytes payload = getPayload(index);
        if (payload.getType() != NoteBytesMetaData.STRING_TYPE) {
            throw new IllegalStateException("Expected STRING at index " + index);
        }
        return payload.getAsString();
    }
    
    public NoteIntegerArray getPayloadAsPath(int index) {
        NoteBytes payload = getPayload(index);
        if (payload.getType() != NoteBytesMetaData.NOTE_INTEGER_ARRAY_TYPE) {
            throw new IllegalStateException("Expected NOTE_INTEGER_ARRAY at index " + index);
        }
        return payload.getAsNoteIntegerArray();
    }
    
    public NoteBytesObject getPayloadAsObject(int index) {
        NoteBytes payload = getPayload(index);
        if (payload.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            throw new IllegalStateException("Expected NOTE_BYTES_OBJECT at index " + index);
        }
        return payload.getAsNoteBytesObject();
    }
    
    public NoteBytesArray getPayloadAsArray(int index) {
        NoteBytes payload = getPayload(index);
        if (payload.getType() != NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
            throw new IllegalStateException("Expected NOTE_BYTES_ARRAY at index " + index);
        }
        return payload.getAsNoteBytesArray();
    }
}