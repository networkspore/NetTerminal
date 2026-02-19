package io.netnotes.terminal;

import java.util.concurrent.CompletableFuture;

import io.netnotes.terminal.events.TerminalEventsFactory;
import io.netnotes.terminal.events.containerEvents.TerminalResizeEvent;
import io.netnotes.terminal.layout.TerminalFloatingLayoutManager;
import io.netnotes.terminal.layout.TerminalLayoutCallback;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutManager;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.ui.Point2D;
import io.netnotes.engine.ui.containers.ContainerHandle;
import io.netnotes.engine.utils.LoggingHelpers.Log;

public class TerminalContainerHandle extends ContainerHandle<
    TerminalBatchBuilder,
    TerminalContainerHandle,
    Point2D,
    TerminalRenderable,
    TerminalRectangle,
    TerminalDeviceManager,
    TerminalLayoutManager,
    TerminalFloatingLayoutManager,
    TerminalLayoutContext,
    TerminalLayoutData,
    TerminalLayoutCallback,
    TerminalEventsFactory,
    TerminalContainerConfig,
    TerminalContainerHandle.TerminalBuilder
> {
    private final TerminalRectanglePool regionPool = TerminalRectanglePool.getInstance();
    public TerminalContainerHandle(TerminalBuilder builder){
        super(builder);
    }

    @Override
    protected TerminalLayoutManager createRenderableLayoutManager(TerminalFloatingLayoutManager layerManager) {
        return new TerminalLayoutManager("layout-manager:" + getName(), layerManager);
    }


    @Override
    protected TerminalFloatingLayoutManager createFloatingLayerManager() {
        return new TerminalFloatingLayoutManager("layer-manager:" + getName(),  TerminalRectanglePool.getInstance());
    }


    protected void setupStateTransitions() {}


    @Override
    protected TerminalRectangle extractRegionFromCreateResponse(NoteBytesMap responseMap) {
        NoteBytes regionBytes =  responseMap.get(Keys.REGION);
        if(regionBytes == null || regionBytes.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
            throw new IllegalStateException("valid region required in response");
        }
        return TerminalRectangle.fromNoteBytes(regionBytes);
    }


    @Override
    protected TerminalBatchBuilder createBatch() {
        return new TerminalBatchBuilder();
    }

    @Override
    protected void setupRoutedMessageMap() { }


    @Override
    protected void onContainerResized(RoutedEvent event) {
         if (!(event instanceof TerminalResizeEvent)) {
            return;
        }
        
        TerminalRectangle newRegion = extractRegionFromResizeEvent(event);
        
        if (newRegion == null) {
            return;
        }
        
        TerminalRectangle oldRegion = allocatedRegion;
        
        if (!regionsEqual(oldRegion, newRegion)) {
            allocatedRegion = newRegion;
            
            Log.logMsg(String.format("[TerminalContainer:%s] Resized: %s -> %s",getName(), oldRegion, newRegion));
            
            if (notifyOnResize != null) {
                notifyOnResize.accept(self());
            }
           
            applyRegionToRenderable(rootRenderable, allocatedRegion);
            
            regionPool.recycle(oldRegion);   
        }
    }


    @Override
    protected TerminalRectangle extractRegionFromResizeEvent(RoutedEvent event) {
        if(event instanceof TerminalResizeEvent resizeEvent){
            return resizeEvent.getAndConsumeRegion();
        }
        return null;
    }

  
    @Override
    public CompletableFuture<Void> requestContainerRegion(TerminalRectangle region) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'requestRegion'");
    }

    
    @Override
    protected void onContainerRendered(RoutedEvent event) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onContainerRendered'");
    }


    @Override
    protected boolean regionsEqual(TerminalRectangle a, TerminalRectangle b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        
        return a.equals(b);
    }

    @Override
    protected void applyRegionToRenderable(TerminalRenderable renderable, TerminalRectangle region) {
        renderable.setRegion(region);
    }


    public static TerminalBuilder builder(String name, ContextPath rendererPath, NoteBytesReadOnly id){
        return new TerminalBuilder(name,rendererPath, id);
    }


    public static class TerminalBuilder extends ContainerHandle.Builder<
        TerminalContainerHandle,
        TerminalRectangle,
        TerminalContainerConfig,
        TerminalBuilder
    > {

        protected TerminalBuilder(String name,ContextPath rendererPath, NoteBytesReadOnly rendererId) {
            super(name, rendererPath, rendererId);
   
        }

        @Override
        public TerminalContainerHandle build() {
            return new TerminalContainerHandle(this);
        }

        @Override
        protected TerminalContainerConfig createContainerConfig() {
            return new TerminalContainerConfig();
        }
        
    }


    // === FocusManagement ===

    protected int compareByScreenPosition(TerminalRenderable a, TerminalRenderable b) {
        int tabCmp = compareFocusIndex(a, b);
        if (tabCmp != 0) {
            return tabCmp;
        }

        Point2D pa = getAbsolutePoint(a);
        Point2D pb = getAbsolutePoint(b);
        if (pa == null || pb == null) {
            return 0;
        }

        int y = Integer.compare(pa.getY(), pb.getY());
        if (y != 0) {
            return y;
        }
        return Integer.compare(pa.getX(), pb.getX());
    }

    protected Point2D getAbsolutePoint(TerminalRenderable renderable){
        TerminalRectangle region = renderable.getEffectiveAbsoluteRegion();
        if (region == null) {
            return null;
        }
        Point2D point = region.getAbsolutePosition();
        renderable.getRegionPool().recycle(region);
        return point;
    }

    @Override
    protected TerminalEventsFactory createEventsFactory() {
        return new TerminalEventsFactory(regionPool);
    }



    
}