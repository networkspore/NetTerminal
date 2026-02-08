package io.netnotes.terminal.events;

import java.util.HashMap;
import java.util.Map;

import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.events.containerEvents.*;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.events.BaseEvent;
import io.netnotes.engine.io.input.events.EncryptedInputEvent;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyCharEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyRepeatEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyUpEvent;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.utils.LoggingHelpers.Log;

public final class TerminalEventsFactory {

    private static final Map<NoteBytesReadOnly, EventDeserializer> REGISTRY = new HashMap<>();

    @FunctionalInterface
    private interface EventDeserializer {
        RoutedEvent create(ContextPath sourcePath, NoteBytesReadOnly type, int stateFlags, NoteBytes[] payload);
    }

    static {

                
        // ===== Keyboard Events =====
        //NoteBytesReadOnly sourceId, int key, int scancode, int stateFlags
        REGISTRY.put(EventBytes.EVENT_KEY_DOWN, (src, type, flags, p) ->
            new KeyDownEvent(src,
                type,
                flags,
                p[0],
                p[1]
            ));

        REGISTRY.put(EventBytes.EVENT_KEY_UP, (src, type, flags, p) ->
            new KeyUpEvent(src,
                type,
                flags,
                p[0],
                p[1]
                ));
        //NoteBytesReadOnly sourceId, int key, int scancode, int stateFlags
        REGISTRY.put(EventBytes.EVENT_KEY_REPEAT, (src, type, flags, p) ->
            new KeyRepeatEvent(src,
                type,
                flags,
                p[0],
                p[1]
            ));

        //NoteBytesReadOnly sourceId, int codepoint, int stateFlags
        REGISTRY.put(EventBytes.EVENT_KEY_CHAR, (src, type, flags, p) ->
            new KeyCharEvent(src,
                type,
                flags,
                p[0]
            ));

  
          // ===== Container Events =====

        REGISTRY.put(EventBytes.EVENT_CONTAINER_FOCUS_GAINED, (src, type, flags, p) ->
            new TerminalFocusGainedEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_FOCUS_LOST, (src, type, flags, p) ->
            new TerminalFocusLostEvent(src, type, flags));
      
        REGISTRY.put(EventBytes.EVENT_CONTAINER_FOCUS_GAINED, (src, type, flags, p) ->
            new TerminalFocusGainedEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_FOCUS_LOST, (src, type, flags, p) ->
            new TerminalFocusLostEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_REGION_CHANGED, (src, type, flags, p) ->{
            if(p == null){
                throw new IllegalStateException("Invalid ContainerMoveEvent: missing payload");
            }
            return new TerminalRegionEvent(src, type, flags, TerminalRectangle.fromNoteBytes(p[0].getAsNoteBytesMap()));
        });

        REGISTRY.put(EventBytes.EVENT_CONTAINER_MOVE, (src, type, flags, p) ->{
            if(p == null){
                throw new IllegalStateException("Invalid ContainerMoveEvent: missing payload");
            }
            return new TerminalMoveEvent(src, type, flags, p[0].getAsInt(), p[1].getAsInt());
        });

        REGISTRY.put(EventBytes.EVENT_CONTAINER_CLOSED, (src, type, flags, p) ->
            new TerminalCloseEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_MINIMIZE, (src, type, flags, p) ->
            new TerminalMinimizeEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_MAXIMIZE, (src, type, flags, p) ->
            new TerminalMaximizeEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_RESTORE, (src, type, flags, p) ->
            new TerminalRestoreEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_SHOWN, (src, type, flags, p) ->
            new TerminalShownEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_HIDDEN, (src, type, flags, p) ->
            new TerminalHiddenEvent(src, type, flags));
    }

    /**
     * Deserializes a RoutedPacket into a typed InputEvent.
     */
    public static RoutedEvent from( ContextPath sourcePath, NoteBytes packet) {

        if (packet.getType() == NoteBytesMetaData.NOTE_BYTES_ENCRYPTED_TYPE) {
            return new EncryptedInputEvent(sourcePath, packet);
        }

        if(packet.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
            return new BaseEvent(sourcePath, ProtocolMesssages.UNKNOWN, 0, new NoteBytes[]{packet});
        }

        NoteBytesMap body = packet.getAsNoteBytesMap();

        NoteBytesReadOnly typeBytes = body.getReadOnly(Keys.EVENT);

        if (typeBytes == null) {
            throw new IllegalStateException("Invalid InputPacket: missing EVENT");
        }
        NoteBytes stateFlags = body.get(Keys.STATE_FLAGS);
        NoteBytes payloadNote = body.get(Keys.PAYLOAD);

        String description = EventBytes.getEventDescription(typeBytes); // for validation
        Log.logMsg("[RoutedEventFactory.from] Deserializing event: " + description);

        
        int flags = stateFlags != null ?stateFlags.getAsInt() : 0;
        NoteBytesArrayReadOnly payloadReadOnly = payloadNote != null ? payloadNote.getAsNoteBytesArrayReadOnly() : null;
        NoteBytes[] payloadArray = payloadReadOnly != null ? payloadReadOnly.getAsArray() : new NoteBytes[0];

        EventDeserializer constructor = REGISTRY.get(typeBytes);
        if (constructor == null) {
            return new BaseEvent(sourcePath, typeBytes, flags, payloadArray);
        }

        return constructor.create(sourcePath, typeBytes, flags, payloadArray);
    }

}
