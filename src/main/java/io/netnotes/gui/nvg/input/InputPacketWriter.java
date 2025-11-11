package io.netnotes.gui.nvg.input;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes input packets from a blocking queue to a PipedOutputStream.
 * This creates the write side of the input pipeline.
 */
public class InputPacketWriter implements AutoCloseable {
    private final BlockingQueue<CompletableFuture<byte[]>> eventQueue;
    private final PipedOutputStream outputStream;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread writerThread;
    
    public InputPacketWriter(
            BlockingQueue<CompletableFuture<byte[]>> eventQueue,
            PipedOutputStream outputStream,
            Executor executor) {
        this.eventQueue = eventQueue;
        this.outputStream = outputStream;
        this.executor = executor;
    }
    
    /**
     * Start the writer loop
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            CompletableFuture.runAsync(this::writeLoop, executor)
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    running.set(false);
                    return null;
                });
        }
    }
    
    /**
     * Main write loop - takes futures from queue and writes resolved bytes to stream
     */
    private void writeLoop() {
        writerThread = Thread.currentThread();
        
        try {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                // Take next event future from queue (blocking)
                CompletableFuture<byte[]> eventFuture = eventQueue.take();
                
                // Wait for the future to resolve
                byte[] eventBytes = eventFuture.get();
                
                if (eventBytes != null && eventBytes.length > 0) {
                    // Write to output stream
                    outputStream.write(eventBytes);
                    outputStream.flush();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("InputPacketWriter: Interrupted");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            running.set(false);
            System.out.println("InputPacketWriter: Stopped");
        }
    }
    
    /**
     * Stop the writer
     */
    public void stop() {
        running.set(false);
        if (writerThread != null) {
            writerThread.interrupt();
        }
    }
    
    /**
     * Check if writer is running
     */
    public boolean isRunning() {
        return running.get();
    }
    
    @Override
    public void close() {
        stop();
        try {
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}