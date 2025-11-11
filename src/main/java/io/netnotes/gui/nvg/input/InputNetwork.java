package io.netnotes.gui.nvg.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import io.netnotes.engine.utils.CollectionHelpers;
import io.netnotes.gui.nvg.input.events.InputRecord;

/**
 * InputNetwork - Manages a network of input nodes with routing capabilities.
 * Provides centralized control over input flow and processing.
 */
public class InputNetwork implements AutoCloseable {
    private final Map<String, InputNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, List<String>> routes = new ConcurrentHashMap<>();
    private final Map<String, Thread> routingThreads = new ConcurrentHashMap<>();
    private final Executor executor;
    private final InputEventRouter eventRouter;
    private volatile boolean running = true;
    
    public InputNetwork(Executor executor) {
        this.executor = executor;
        this.eventRouter = new InputEventRouter();
    }
    
    /**
     * Create and register a new input node.
     * Node will automatically use active sources from the registry.
     */
    public InputNode createNode(String nodeId) throws Exception {
        if (nodes.containsKey(nodeId)) {
            throw new IllegalStateException("Node already exists: " + nodeId);
        }
        
        InputNode node = new InputNode(nodeId, executor);
        nodes.put(nodeId, node);
        return node;
    }
    
    /**
     * Create a node with custom queues (for connecting to external systems)
     */
    public InputNode createNode(
            String nodeId,
            BlockingQueue<java.util.concurrent.CompletableFuture<byte[]>> writeQueue,
            BlockingQueue<InputRecord> readQueue) throws Exception {
        if (nodes.containsKey(nodeId)) {
            throw new IllegalStateException("Node already exists: " + nodeId);
        }
        
        InputNode node = new InputNode(nodeId, writeQueue, readQueue, executor);
        nodes.put(nodeId, node);
        return node;
    }
    
    /**
     * Get a node by ID
     */
    public InputNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }
    
    /**
     * Remove a node
     */
    public void removeNode(String nodeId) {
        InputNode node = nodes.remove(nodeId);
        if (node != null) {
            // Stop any routing threads for this node
            String routeKey = "route_" + nodeId;
            Thread routingThread = routingThreads.remove(routeKey);
            if (routingThread != null) {
                routingThread.interrupt();
            }
            
            node.close();
        }
        routes.remove(nodeId);
    }
    
    /**
     * Route events from one node to another
     */
    public void routeEvents(String fromNodeId, String toNodeId) {
        if (!nodes.containsKey(fromNodeId) || !nodes.containsKey(toNodeId)) {
            throw new IllegalArgumentException("Both nodes must exist");
        }
        
        routes.computeIfAbsent(fromNodeId, k -> new ArrayList<>()).add(toNodeId);
        
        // Start a routing task
        startRouting(fromNodeId, toNodeId);
    }
    
    /**
     * Start routing events between nodes
     */
    private void startRouting(String fromNodeId, String toNodeId) {
        InputNode fromNode = nodes.get(fromNodeId);
        InputNode toNode = nodes.get(toNodeId);
        
        String routeKey = "route_" + fromNodeId + "_to_" + toNodeId;
        
        Thread routingThread = new Thread(() -> {
            try {
                BlockingQueue<InputRecord> fromQueue = fromNode.getReadQueue();
                BlockingQueue<java.util.concurrent.CompletableFuture<byte[]>> toQueue = toNode.getWriteQueue();
                
                while (running && fromNode.isEnabled() && toNode.isEnabled() && 
                       !Thread.currentThread().isInterrupted()) {
                    InputRecord event = fromQueue.take();
                    
                    // Apply filter if present
                    InputNode.InputFilter filter = toNode.getFilter();
                    if (filter != null && !filter.shouldPass(event)) {
                        continue; // Skip this event
                    }
                    
                    // Apply transformer if present
                    InputNode.InputTransformer transformer = toNode.getTransformer();
                    if (transformer != null) {
                        event = transformer.transform(event);
                        if (event == null) {
                            continue; // Transformer filtered it out
                        }
                    }
                    
                    // Get the original bytes from the InputRecord and enqueue as future
                    final InputRecord finalEvent = event;
                        CompletableFuture<byte[]> future = CompletableFuture.supplyAsync(()->
                            CollectionHelpers.appendBytes(finalEvent.sourceId().get(), finalEvent.body().get()));
                    
                    toQueue.put(future);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, routeKey);
        
        routingThread.setDaemon(true);
        routingThreads.put(routeKey, routingThread);
        routingThread.start();
    }
    
    /**
     * Create a fan-out: route one node's output to multiple destinations
     */
    public void fanOut(String sourceNodeId, String... targetNodeIds) {
        for (String targetId : targetNodeIds) {
            routeEvents(sourceNodeId, targetId);
        }
    }
    
    /**
     * Create a merge: route multiple nodes' outputs to one destination
     */
    public void merge(String targetNodeId, String... sourceNodeIds) {
        for (String sourceId : sourceNodeIds) {
            routeEvents(sourceId, targetNodeId);
        }
    }
    
    /**
     * Start all nodes in the network
     */
    public void startAll() {
        nodes.values().forEach(InputNode::start);
    }
    
    /**
     * Stop all nodes in the network
     */
    public void stopAll() {
        nodes.values().forEach(InputNode::stop);
    }
    
    /**
     * Enable/disable a specific node
     */
    public void setNodeEnabled(String nodeId, boolean enabled) {
        InputNode node = nodes.get(nodeId);
        if (node != null) {
            node.setEnabled(enabled);
        }
    }
    
    /**
     * Get the global event router
     */
    public InputEventRouter getEventRouter() {
        return eventRouter;
    }
    
    /**
     * Get all node IDs
     */
    public java.util.Set<String> getNodeIds() {
        return new java.util.HashSet<>(nodes.keySet());
    }
    
    /**
     * Get routing configuration for a node
     */
    public List<String> getRoutes(String nodeId) {
        return new ArrayList<>(routes.getOrDefault(nodeId, new ArrayList<>()));
    }
    
    /**
     * Get statistics about the network
     */
    public NetworkStats getStats() {
        return new NetworkStats(
            nodes.size(),
            routes.values().stream().mapToInt(List::size).sum(),
            nodes.values().stream().filter(InputNode::isEnabled).count()
        );
    }
    
    @Override
    public void close() {
        running = false;
        
        // Interrupt all routing threads
        routingThreads.values().forEach(Thread::interrupt);
        routingThreads.clear();
        
        stopAll();
        nodes.values().forEach(InputNode::close);
        nodes.clear();
        routes.clear();
    }
    
    /**
     * Network statistics
     */
    public record NetworkStats(int totalNodes, int totalRoutes, long activeNodes) {}
}