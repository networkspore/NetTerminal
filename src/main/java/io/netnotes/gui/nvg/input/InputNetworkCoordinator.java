package io.netnotes.gui.nvg.input;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public class InputNetworkCoordinator {
    private final InputNetwork network;
    private final Map<Long, WindowInputContext> windows = new ConcurrentHashMap<>();
    
    public WindowInputContext registerWindow(long glfwWindow, String windowId) {
        // Create input source for window
        NoteBytesReadOnly sourceId = InputSourceHelper.registerGLFWSource(
            windowId, glfwWindow);
        
        // Create input node for window
        InputNode windowNode = network.createNode("window:" + windowId);
        
        // Create input source manager
        BlockingQueue<CompletableFuture<byte[]>> eventQueue = 
            new LinkedBlockingQueue<>();
        InputSourceManager sourceManager = new InputSourceManager(
            glfwWindow, sourceId.getAsInt(), eventQueue);
        
        // Connect source manager to window node
        // Events flow: SourceManager → eventQueue → WindowNode
        connectSourceToNode(eventQueue, windowNode);
        
        WindowInputContext context = new WindowInputContext(
            glfwWindow, sourceId, windowNode, sourceManager);
        windows.put(glfwWindow, context);
        
        return context;
    }
    
    public void routeWindowToContainer(String windowId, String containerId) {
        // Route events from window to container's input node
        network.routeEvents("window:" + windowId, "container:" + containerId);
    }
}