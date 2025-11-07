package io.netnotes.gui.nvg.uiNode.input;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

public class RawEvent {
    private final NoteBytesReadOnly m_type;
    private final long m_sequence;
    private final int m_stateFlags;
    private final NoteBytes m_payload;
    
    private RawEvent(NoteBytesReadOnly type, long sequence, int stateFlags, NoteBytes payload) {
        this.m_type = type;
        this.m_sequence = sequence;
        this.m_stateFlags = stateFlags;
        this.m_payload = payload;
    }
    
    public static RawEvent fromNoteBytes(NoteBytesMap body) {
        NoteBytes typeBytes = body.get(InputPacket.Factory.TYPE_KEY);
        NoteBytes seqBytes = body.get(InputPacket.Factory.SEQUENCE_KEY);
        NoteBytes flagsBytes = body.get(InputPacket.Factory.STATE_FLAGS_KEY);
        NoteBytes payload = body.get(InputPacket.Factory.PAYLOAD_KEY);
        
        if (typeBytes == null || seqBytes == null) {
            return null;
        }
        
        NoteBytesReadOnly type = new NoteBytesReadOnly(typeBytes.getBytes());
        long sequence = seqBytes.getAsLong();
        int stateFlags = flagsBytes != null ? flagsBytes.getAsInt() : 0;
        
        return new RawEvent(type, sequence, stateFlags, payload);
    }
    
    public NoteBytesReadOnly getType() {
        return m_type;
    }
    
    public long getSequence() {
        return m_sequence;
    }
    
    public int getStateFlags() {
        return m_stateFlags;
    }
    
    public NoteBytes getPayload() {
        return m_payload;
    }
    
    public boolean hasModifier(int modifier) {
        return InputPacket.StateFlags.hasFlag(m_stateFlags, modifier);
    }
    
    public boolean isConsumed() {
        return hasModifier(InputPacket.StateFlags.STATE_CONSUMED);
    }
    
    public void consume() {
        // Note: Would need to modify stateFlags to mark as consumed
        // For immutability, this would create a new event or use a flag elsewhere
    }
}