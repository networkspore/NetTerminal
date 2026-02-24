package io.netnotes.terminal;

import java.util.concurrent.CompletableFuture;

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
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.ui.Point2D;
import io.netnotes.engine.ui.containers.Container;
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
    TerminalRectanglePool,
    TerminalEventsFactory,
    TerminalContainerConfig,
    TerminalContainerHandle.TerminalBuilder
> {

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
    protected TerminalBatchBuilder createBatch() {
        return new TerminalBatchBuilder();
    }

    @Override
    protected void setupRoutedMessageMap() { }

    private boolean handleRegionStateChange(TerminalRegionChangedEvent event){
        boolean isChanged = false;
        if(event.isLayoutManaged()){
            if(!stateMachine.hasState(Container.STATE_LAYOUT_MANAGED)){
                stateMachine.addState(Container.STATE_LAYOUT_MANAGED);
                isChanged = true;
            }
        }else{
            if(stateMachine.hasState(Container.STATE_LAYOUT_MANAGED)){
                stateMachine.removeState(Container.STATE_LAYOUT_MANAGED);
                isChanged = true;
            }
        }
        if(event.isOffScreen()){
            if(!stateMachine.hasState(Container.STATE_OFF_SCREEN)){
                stateMachine.addState(Container.STATE_OFF_SCREEN);
                isChanged = true;
            }
        }else{
            if(stateMachine.hasState(Container.STATE_OFF_SCREEN)){
                stateMachine.removeState(Container.STATE_OFF_SCREEN);
                isChanged = true;
            }
        }

        return isChanged;
    }

    @Override
    protected void onContainerRegionChanged(RoutedEvent event) {
        if (!(event instanceof TerminalRegionChangedEvent regionChangedEvent)) {
            return;
        }
        TerminalRectangle newRegion = regionChangedEvent.getAndConsumeRegion();
        if (newRegion == null) {
            return;
        }

        boolean isStateChanged = handleRegionStateChange(regionChangedEvent);
        
        TerminalRectangle oldRegion = allocatedRegion;
        
        if (!regionsEqual(oldRegion, newRegion) || isStateChanged) {
            allocatedRegion = newRegion;
            
            Log.logMsg(String.format("[TerminalContainer:%s] Resized: %s -> %s",getName(), oldRegion, newRegion));
            
            if (notifyOnRegionChanged != null) {
                notifyOnRegionChanged.accept(self());
            }
           
            applyRegionToRenderable(rootRenderable, allocatedRegion);
            
            regionPool.recycle(oldRegion);   
        }
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
    protected NoteBytesMap preprocessOutgoingRenderCommand(NoteBytesMap command) {
        if (command == null || allocatedRegion == null) {
            return command;
        }
        CursorCommandFilter.ClampResult result =
            CursorCommandFilter.clampToBounds(command, allocatedRegion.toNoteBytes());
        return result.command();
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

   


    
}
