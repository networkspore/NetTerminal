package io.netnotes.gui.fx.uiNode;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.INode;
import io.netnotes.engine.NodeControllerInterface;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;

/**
 * UI Node - manages JavaFX display and input handling.
 * Implements INode to participate in the node messaging system.
 */
public class FxUINode implements INode {
    
    private final NoteBytesReadOnly nodeId;
    private NodeControllerInterface controller;
    private AppDataInterface appInterface;
    
    private volatile boolean active = false;
    
    public FxUINode(NoteBytesReadOnly nodeId) {
        this.nodeId = nodeId;
    }
    
    @Override
    public NoteBytesReadOnly getNodeId() {
        return nodeId;
    }
    
    @Override
    public CompletableFuture<Void> initialize(AppDataInterface appInterface) {
        this.appInterface = appInterface;
        this.active = true;
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void setNodeControllerInterface(NodeControllerInterface nodeControllerInterface) {
        this.controller = nodeControllerInterface;
    }
    
    /**
     * Initialize with JavaFX primary stage (called after authentication)
     */
    public void initializeWithStage(Stage primaryStage, PipedOutputStream inputOutputStream) {
        /*Platform.runLater(() -> {
            // Initialize JavaFX adapter
            JavaFxAdapter.initialize("primary", primaryStage, inputOutputStream);
            this.adapter = JavaFxAdapter.getInstance();
            
            // Start the adapter to begin emitting events
            adapter.start(null); // We use OutputStream directly
            
            // Get primary canvas
            Canvas canvas = JavaFxAdapter.getPrimaryCanvas();
            this.primaryCanvas = wrapCanvas(canvas);
            
            // Set initial content
            NoteBytesArray initialSegments = createWelcomeScreen();
            primaryCanvas.setSegments(initialSegments);
        });*/
    }
    /*
    private LayoutCanvas wrapCanvas(Canvas canvas) {
         Wrap existing canvas from adapter
        LayoutCanvas layoutCanvas = new LayoutCanvas(
            (int) canvas.getWidth(),
            (int) canvas.getHeight()
        );
        // We'd need to modify LayoutCanvas to accept existing canvas
        // Or have adapter create LayoutCanvas directly
        return layoutCanvas;*

        return null;
    }/
    
    @Override
    public CompletableFuture<Void> receiveRawMessage(PipedOutputStream messageStream, 
                                                     PipedOutputStream replyStream) {
        // Handle messages from other nodes
        // Could be:
        // - Render commands (update segment tree)
        // - Stage control commands (create window, move, resize)
        // - Query commands (get canvas dimensions, hit test)
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> shutdown() {
        active = false;
        
        if (adapter != null) {
            Platform.runLater(() -> {
                adapter.stop();
                // Close all stages
                JavaFxAdapter.closeStage("primary");
            });
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public boolean isActive() {
        return active;
    }
    
    /**
     * Create a simple welcome screen
     */
    private NoteBytesArray createWelcomeScreen() {
        // Build a simple segment tree
        NoteBytesArray segments = new NoteBytesArray();
        
        // TODO: Build actual segment tree
        
        return segments;
    }

    @Override
    public boolean isActive() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isActive'");
    }

    @Override
    public CompletableFuture<Void> receiveRawMessage(PipedOutputStream arg0, PipedOutputStream arg1)
            throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'receiveRawMessage'");
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'shutdown'");
    }
}