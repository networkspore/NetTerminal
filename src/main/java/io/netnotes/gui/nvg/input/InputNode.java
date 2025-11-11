package io.netnotes.gui.nvg.input;

import java.io.PipedOutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import io.netnotes.gui.nvg.input.events.InputRecord;

/**
 * InputNode - A node in the input processing network.
 * Provides control points for routing, filtering, and transforming input events.
 * 
 * Threading model:
 * - Writer runs on its own thread, writes to PipedOutputStream
 * - Reader runs on its own thread, creates PipedInputStream connected to the PipedOutputStream
 * This avoids deadlocks when reading and writing to the same pipe.
 */
public class InputNode implements AutoCloseable {
    private final String nodeId;
    private final PipedOutputStream outputStream;
    private final InputPacketWriter writer;
    private final InputPacketReader reader;
    private final BlockingQueue<CompletableFuture<byte[]>> writeQueue;
    private final BlockingQueue<InputRecord> readQueue;
    
    // Control flags
    private volatile boolean enabled = true;
    private volatile InputFilter filter = null;
    private volatile InputTransformer transformer = null;
    
    /**
     * Create an input node with specified queues and executor.
     * Uses InputSourceRegistry to get active sources dynamically.
     */
    public InputNode(
            String nodeId,
            BlockingQueue<CompletableFuture<byte[]>> writeQueue,
            BlockingQueue<InputRecord> readQueue,
            Executor executor) throws Exception {
        this.nodeId = nodeId;
        this.writeQueue = writeQueue;
        this.readQueue = readQueue;
        
        // Create piped output stream (writer will write to this on its own thread)
        this.outputStream = new PipedOutputStream();
        
        // Create writer (writes to outputStream on dedicated thread)
        this.writer = new InputPacketWriter(writeQueue, outputStream, executor);
        
        // Create reader with dynamic source supplier from registry
 
        
        this.reader = new InputPacketReader( outputStream, readQueue, executor);
    }
    
    /**
     * Create an input node with default queues
     */
    public InputNode(String nodeId, Executor executor) throws Exception {
        this(
            nodeId,
            new LinkedBlockingQueue<>(),
            new LinkedBlockingQueue<>(),
            executor
        );
    }
    
    /**
     * Start the node (begin processing)
     * Writer starts on its own thread, Reader starts on its own thread
     */
    public void start() {
        writer.start();  // Starts writing to PipedOutputStream on dedicated thread
        reader.start();  // Starts reading from PipedInputStream on dedicated thread
    }
    
    /**
     * Stop the node
     */
    public void stop() {
        writer.stop();
        reader.stop();
    }
    
    /**
     * Get the write queue for enqueueing events
     */
    public BlockingQueue<CompletableFuture<byte[]>> getWriteQueue() {
        return writeQueue;
    }
    
    /**
     * Get the read queue for consuming processed events
     */
    public BlockingQueue<InputRecord> getReadQueue() {
        return readQueue;
    }
    
    /**
     * Enable or disable this node
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Set a filter to selectively pass events
     */
    public void setFilter(InputFilter filter) {
        this.filter = filter;
    }
    
    /**
     * Get the current filter
     */
    public InputFilter getFilter() {
        return filter;
    }
    
    /**
     * Set a transformer to modify events
     */
    public void setTransformer(InputTransformer transformer) {
        this.transformer = transformer;
    }
    
    /**
     * Get the current transformer
     */
    public InputTransformer getTransformer() {
        return transformer;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    @Override
    public void close() {
        stop();
        writer.close();
        // Reader will close its PipedInputStream automatically
    }
    
    /**
     * Filter interface for event filtering
     */
    @FunctionalInterface
    public interface InputFilter {
        boolean shouldPass(InputRecord event);
    }
    
    /**
     * Transformer interface for event transformation
     */
    @FunctionalInterface
    public interface InputTransformer {
        InputRecord transform(InputRecord event);
    }
}