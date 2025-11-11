package io.netnotes.gui.nvg.input.events;

import java.io.IOException;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.AtomicSequence;
import io.netnotes.gui.nvg.input.InputPacket;





public record InputRecord(
        NoteBytesReadOnly sourceId,
        NoteBytesReadOnly type,
        long atomicSequence,
        int stateFlags,
        boolean aux0,
        boolean aux1,
        NoteBytesArrayReadOnly payload,
        NoteBytesReadOnly body
    ) {
        public static InputRecord fromNoteBytes(NoteBytesReadOnly sourceId, NoteBytesReadOnly bodyNoteBytes){

                    
            NoteBytesMap body = bodyNoteBytes.getAsNoteBytesMap();
            
            NoteBytes typeBytes = body.get(InputPacket.Factory.TYPE_KEY);
            NoteBytes seqBytes  = body.get(InputPacket.Factory.SEQUENCE_KEY);

            if (typeBytes == null || seqBytes == null) {
                throw new IllegalStateException("Invalid InputPacket: missing type or sequence");
            }

            NoteBytesReadOnly type = typeBytes.getAsReadOnly();
            long long48Bit = AtomicSequence.decodeAtomicSequence(seqBytes.getBytes(), 0);
            boolean aux0 = AtomicSequence.readAux0(long48Bit);
            boolean aux1 = AtomicSequence.readAux1(long48Bit);
            long seq = AtomicSequence.readAtomicSequence(long48Bit);
            int flags = 0;
            NoteBytes stateFlags = body.get(InputPacket.Factory.STATE_FLAGS_KEY);
            if (stateFlags != null) flags = stateFlags.getAsInt();

            NoteBytesArrayReadOnly payload = null;
            NoteBytes payloadNote = body.get(InputPacket.Factory.PAYLOAD_KEY);
            if (payloadNote != null) payload = payloadNote.getAsNoteBytesArrayReadOnly();

            return new InputRecord(sourceId, type, seq, flags, aux0, aux1, payload, bodyNoteBytes);
        }
    }