package io.netnotes.terminal;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import io.netnotes.terminal.events.TerminalEventsFactory;
import io.netnotes.terminal.events.containerEvents.TerminalRegionChangedEvent;
import io.netnotes.terminal.layout.TerminalFloatingLayoutManager;
import io.netnotes.terminal.layout.TerminalLayoutCallback;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutManager;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.ui.Point2D;
import io.netnotes.engine.ui.renderer.ContainerHandle;


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
    TerminalRectanglePool,
    TerminalEventsFactory,
    TerminalContainerConfig,
    TerminalRegionChangedEvent,
    TerminalDamageAccumulator,
    TerminalContainerHandle.TerminalBuilder
> {
   // private final static LogLevel LOG_LEVEL = LogLevel.IMPORTANT;

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


    @Override
    protected TerminalDamageAccumulator createDamageAcculator(TerminalRectanglePool pool) {
        return new TerminalDamageAccumulator(pool);
    }

    protected void setupStateTransitions() {}





    @Override
    protected TerminalBatchBuilder createBatch() {
        return new TerminalBatchBuilder(regionPool);
    }

    @Override
    protected void setupRoutedMessageMap() { }



    @Override
    protected Predicate<RoutedEvent> createContainerPredicate() {
        return new ContainerPredicate();
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



    public static TerminalBuilder builder(String name, ContextPath rendererPath, NoteBytesReadOnly id){
        return new TerminalBuilder(name,rendererPath, id);
    }


    public static class TerminalBuilder extends ContainerHandle.Builder<
        TerminalContainerHandle,
        TerminalRectanglePool,
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
    protected TerminalRectangle createRegionFromNoteBytes(NoteBytes regionBytes) {
     
        return TerminalRectangle.fromNoteBytes(regionBytes, regionPool);
    }

    @Override
    protected TerminalEventsFactory createEventsFactory(TerminalRectanglePool pool) {
        return new TerminalEventsFactory(pool);
    }

    @Override
    protected TerminalRectanglePool createRegionPool() {
        return TerminalRectanglePool.getInstance();
    }

    @Override
    protected TerminalRegionChangedEvent checkRegionChangedEventInstance(RoutedEvent event) {
        if(event instanceof TerminalRegionChangedEvent regionEvent){
            return regionEvent;
        }
        return null;
    }



 

 

   


    
}
