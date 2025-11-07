package io.netnotes.gui.nvg.uiNode.input.events;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.AtomicSequence;
import io.netnotes.gui.nvg.uiNode.input.InputPacket;





    public record InputRecord(
        int sourceId,
        short type,
        long atomicSequence,
        int stateFlags,
        boolean aux0,
        boolean aux1,
        NoteBytesArray payload
    ) {
        public static InputRecord fromNoteBytes(NoteBytesMap body) {
            NoteBytes typeBytes = body.get(InputPacket.Factory.TYPE_KEY);
            NoteBytes seqBytes  = body.get(InputPacket.Factory.SEQUENCE_KEY);

            if (typeBytes == null || seqBytes == null) {
                throw new IllegalStateException("Invalid InputPacket: missing type or sequence");
            }

            short type = typeBytes.getAsShort();
            long seqLong = seqBytes.getAsLong();
            boolean aux0 = AtomicSequence.readAux0(seqLong);
            boolean aux1 = AtomicSequence.readAux1(seqLong);

            int flags = 0;
            NoteBytes stateFlags = body.get(InputPacket.Factory.STATE_FLAGS_KEY);
            if (stateFlags != null) flags = stateFlags.getAsInt();

            NoteBytesArray payload = null;
            NoteBytes payloadNote = body.get(InputPacket.Factory.PAYLOAD_KEY);
            if (payloadNote != null) payload = payloadNote.getAsNoteBytesArray();

      
            return switch (type) {
               // case BinaryEvent.Types.RAW_MOUSE_MOVED -> new MouseMoveEvent(seqLong, aux0, aux1, payload);
               // case BinaryEvent.Types.RAW_KEY_PRESSED -> new KeyPressEvent(seqLong, aux0, aux1, payload);
                default -> new InputRecord(0, type, seqLong, flags, aux0, aux1, payload);
            };
        }
    }

