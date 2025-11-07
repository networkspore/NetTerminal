package io.netnotes.gui.core.input;
import java.io.EOFException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.gui.core.input.events.InputRecord;

public final class InputPacketReader {

    private final InputStream m_input;
    private final BlockingQueue<InputRecord> m_eventQueue;
    private final Executor m_decodeExecutor;

    public InputPacketReader(InputStream input, BlockingQueue<InputRecord> eventQueue, Executor decodeExecutor) {
        this.m_input = Objects.requireNonNull(input);
        this.m_eventQueue = Objects.requireNonNull(eventQueue);
        this.m_decodeExecutor = Objects.requireNonNull(decodeExecutor);
    }

    /**
     * Continuously read and decode packets from the input stream.
     * Runs indefinitely until stream closes or error occurs.
     */
    public void start() {
        CompletableFuture.runAsync(this::readLoop, m_decodeExecutor);
    }

    private void readLoop() {
        try(NoteBytesReader reader = new NoteBytesReader(m_input)) {
            while (true) {
        
                NoteBytes sourceId =  reader.nextNoteBytes();
                if (sourceId == null) break;
               
                NoteBytes bodyNoteBytes = reader.nextNoteBytes();

                if(bodyNoteBytes == null) break;
                NoteBytesMap body = bodyNoteBytes.getAsNoteBytesMap();
                

                NoteBytes type = body.get(InputPacket.Factory.TYPE_KEY);
                if(type != null){

                    InputRecord event = InputRecord.fromNoteBytes(body);
                    m_eventQueue.put(event);
                }
            }
        } catch (EOFException e) {
            System.out.println("InputPacketReader: Stream closed normally");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
