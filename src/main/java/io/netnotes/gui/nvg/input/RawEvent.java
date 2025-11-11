package io.netnotes.gui.nvg.input;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.gui.nvg.input.events.InputRecord;

/**
 * RawEvent - Unified wrapper for input events
 * 
 * Bridges between InputRecord (network/node layer) and 
 * process-level input handling.
 */
public class RawEvent {
    private final InputRecord record;
    private final InputRecordReader reader;
    
    /**
     * Create from InputRecord
     */
    public RawEvent(InputRecord record) {
        this.record = record;
        this.reader = new InputRecordReader(record);
    }
    

    public RawEvent(byte[] eventBytes) {
        // Parse packet: [sourceId][body]
        NoteBytesReadOnly sourceId = NoteBytesReadOnly.readNote(eventBytes, 0);
        NoteBytesReadOnly body = NoteBytesReadOnly.readNote(eventBytes, sourceId.byteLength());
        
        this.record = InputRecord.fromNoteBytes(sourceId, body);
        this.reader = new InputRecordReader(record);
    }
    
    /**
     * Get the underlying InputRecord
     */
    public InputRecord getRecord() {
        return record;
    }
    
    /**
     * Get the reader for convenient access
     */
    public InputRecordReader getReader() {
        return reader;
    }
    
    /**
     * Get event type
     */
    public NoteBytesReadOnly getType() {
        return record.type();
    }
    
    /**
     * Get source ID
     */
    public NoteBytesReadOnly getSourceId() {
        return record.sourceId();
    }
    
    /**
     * Get atomic sequence
     */
    public long getAtomicSequence() {
        return record.atomicSequence();
    }
    
    /**
     * Get state flags
     */
    public int getStateFlags() {
        return record.stateFlags();
    }
    
    /**
     * Get raw bytes (for legacy code)
     */
    public byte[] getData() {
        // Reconstruct packet format
        byte[] sourceBytes = record.sourceId().get();
        byte[] bodyBytes = record.body().get();
        
        byte[] result = new byte[sourceBytes.length + bodyBytes.length];
        System.arraycopy(sourceBytes, 0, result, 0, sourceBytes.length);
        System.arraycopy(bodyBytes, 0, result, sourceBytes.length, bodyBytes.length);
        
        return result;
    }
    
    // Convenience methods delegating to reader
    
    public boolean isKeyDown() {
        return reader.isKeyDown();
    }
    
    public boolean isKeyUp() {
        return reader.isKeyUp();
    }
    
    public boolean isKeyChar() {
        return reader.isKeyChar();
    }
    
    public boolean isMouseMove() {
        return reader.isMouseMoveAbsolute() || reader.isMouseMoveRelative();
    }
    
    public boolean isMouseButton() {
        return reader.isMouseButton();
    }
    
    public boolean isScroll() {
        return reader.isScroll();
    }
    
    public int getKey() {
        return reader.getKey();
    }
    
    public int getScancode() {
        return reader.getScancode();
    }
    
    public int getCodepoint() {
        return reader.getCodepoint();
    }
    
    public double getMouseX() {
        return reader.getMouseX();
    }
    
    public double getMouseY() {
        return reader.getMouseY();
    }
    
    public int getButton() {
        return reader.getButton();
    }
    
    public boolean hasModifier(int flag) {
        return reader.hasModifier(flag);
    }
    
    public boolean hasShift() {
        return reader.hasShift();
    }
    
    public boolean hasControl() {
        return reader.hasControl();
    }
    
    public boolean hasAlt() {
        return reader.hasAlt();
    }
    
    @Override
    public String toString() {
        return String.format("RawEvent[type=%s, source=%s, timestamp=%d]", 
            record.type(), record.sourceId(), record.atomicSequence());
    }
}