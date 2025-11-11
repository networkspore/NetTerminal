package io.netnotes.gui.nvg.input;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.gui.nvg.input.events.InputRecord;

public final class InputPacketReader {
    private InputSourceRegistry m_sourceRegistry = InputSourceRegistry.getInstance();
    private final PipedOutputStream m_outputStream;
    private final BlockingQueue<InputRecord> m_eventQueue;
    private final Executor m_decodeExecutor;
    private volatile Thread m_readerThread;

    public InputPacketReader(
            PipedOutputStream outputStream,
            BlockingQueue<InputRecord> eventQueue, 
            Executor decodeExecutor) {
        this.m_outputStream = Objects.requireNonNull(outputStream);
        this.m_eventQueue = Objects.requireNonNull(eventQueue);
        this.m_decodeExecutor = Objects.requireNonNull(decodeExecutor);
    }

    /**
     * Continuously read and decode packets from the input stream.
     * Runs indefinitely until stream closes or error occurs.
     * Creates the PipedInputStream on its own thread to avoid deadlock.
     */
    public void start() {
        CompletableFuture.runAsync(this::readLoop, m_decodeExecutor);
    }

    private void readLoop() {
        m_readerThread = Thread.currentThread();
        
        // Create the PipedInputStream on this thread, connecting to the PipedOutputStream
        try (PipedInputStream inputStream = new PipedInputStream(m_outputStream, 8192);
             NoteBytesReader reader = new NoteBytesReader(inputStream)) {
            
            while (!Thread.currentThread().isInterrupted()) {
                NoteBytesReadOnly sourceId = reader.nextNoteBytesReadOnly();
                if (sourceId == null) break;

                if (m_sourceRegistry.containsSourceId(sourceId)) {
                    NoteBytesReadOnly bodyNoteBytes = reader.nextNoteBytesReadOnly();
                    
                    if (bodyNoteBytes != null && 
                        bodyNoteBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                        
                        // Pass both sourceId and bodyNoteBytes to create the InputRecord
                        InputRecord event = InputRecord.fromNoteBytes(sourceId, bodyNoteBytes);
                        m_eventQueue.put(event);
                    } else {
                        // TODO: Input error - wrong type, might be out of sync
                        System.err.println("InputPacketReader: Invalid body type for source " + sourceId);
                    }
                } else {
                    // TODO: Input error - bad source ID, might be out of sync
                    System.err.println("InputPacketReader: Unknown source ID: " + sourceId);
                    // Skip the body bytes to try to recover sync
                    reader.nextNoteBytes();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("InputPacketReader: Interrupted");
        } catch (Exception e) {
            System.err.println("InputPacketReader: Error in read loop");
            e.printStackTrace();
        } finally {
            System.out.println("InputPacketReader: Stopped");
        }
    }
    
    public void stop() {
        if (m_readerThread != null) {
            m_readerThread.interrupt();
        }
    }
}