package io.netnotes.terminal.layout;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRectanglePool;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.engine.ui.renderer.layout.LayoutData;


/**
 * TerminalLayoutData - Terminal-specific layout data
 * 
 * Extends LayoutData with TerminalRectangle for 2D positioning
 */
public final class TerminalLayoutData extends LayoutData<
    TerminalBatchBuilder, 
    TerminalRenderable, 
    TerminalRectangle, 
    TerminalLayoutData, 
    TerminalLayoutData.TerminalLayoutDataBuilder
>{
    public TerminalLayoutData(){
        super();
    }

    public void initialize(TerminalLayoutDataBuilder builder) {
        super.initialize(builder);
        if (builder.setX)      setAxisChange(TerminalRegion.AXIS_X);
        if (builder.setY)      setAxisChange(TerminalRegion.AXIS_Y);
        if (builder.setWidth)  setAxisChange(TerminalRegion.AXIS_W);
        if (builder.setHeight) setAxisChange(TerminalRegion.AXIS_H);
        TerminalLayoutDataPool.getInstance().recycleBuilder(builder);
    }

    @Override
    public void initialize(TerminalRectangle rect){
        super.initialize(rect);
    }


    
    @Override
    public void recycleRegion() {
        TerminalRectangle rect = spatialRegion;
        spatialRegion = null;
        if(rect != null){
            TerminalRectanglePool.getInstance().recycle(rect);
        }
    }

    public void reset(){
        super.reset(); // clears axisXSet/axisYSet/axisWidthSet/axisHeightSet
    }


    /**
     * Axis-selective merge: copies {@code current} into {@code target}, then
     * overwrites only the axes that were explicitly set by the builder.
     *
     * Called by Renderable.applySpatialChange so that a top-down pass never
     * overwrites a FIT_CONTENT dimension committed by the bottom-up pass, and
     * vice versa.
     *
     * The parent's absolute screen position is stored on spatialRegion by
     * apply() via setParentAbsolutePosition(parentRegion.getAbsolutePosition()).
     * We must forward that stored value — NOT spatialRegion.getX()/getY(),
     * which are the child's relative coords and have nothing to do with the
     * parent's screen offset.
     */
    @Override
    public void mergeIntoRegion(TerminalRectangle current, TerminalRectangle target) {
        target.copyFrom(current);
        if (spatialRegion != null) {
            target.setParentAbsolutePosition(spatialRegion.getParentAbsolutePosition());
            if (hasAxisChange(TerminalRegion.AXIS_X)) target.setX(spatialRegion.getX());
            if (hasAxisChange(TerminalRegion.AXIS_Y)) target.setY(spatialRegion.getY());
            if (hasAxisChange(TerminalRegion.AXIS_W)) target.setWidth(spatialRegion.getWidth());
            if (hasAxisChange(TerminalRegion.AXIS_H)) target.setHeight(spatialRegion.getHeight());
        }
    }

    public static TerminalLayoutDataBuilder getBuilder(){
        return TerminalLayoutDataPool.getInstance().obtainBuilder();
    }

    public final static class TerminalLayoutDataBuilder extends LayoutData.Builder<
        TerminalBatchBuilder, 
        TerminalRenderable, 
        TerminalRectangle, 
        TerminalLayoutData, 
        TerminalLayoutData.TerminalLayoutDataBuilder
    >{

        protected boolean setX = false;
        protected boolean setY = false;
        protected boolean setWidth = false;
        protected boolean setHeight = false;
      
        protected void initSpatialRegion(){
            spatialRegion = TerminalRectanglePool.getInstance().obtain();
        }

        public TerminalLayoutDataBuilder setX(int x) {
            if(spatialRegion == null){
                initSpatialRegion();
            }
            spatialRegion.setX(x);
            this.setX = true;
            return this;
        }
        
        public TerminalLayoutDataBuilder setY(int y) {
             if(spatialRegion == null){
                initSpatialRegion();
            }
            spatialRegion.setY(y);
            this.setY = true;
            return this;
        }
        
        public TerminalLayoutDataBuilder setWidth(int width) {
            if(spatialRegion == null){
                initSpatialRegion();
            }
            spatialRegion.setWidth(width);
            this.setWidth = true;
            return this;
        }
        
        public TerminalLayoutDataBuilder setHeight(int height) {
            if(spatialRegion == null){
                initSpatialRegion();
            }
            spatialRegion.setHeight(height);
            this.setHeight = true;
            return this;
        }
        
        public TerminalLayoutDataBuilder setPosition(int x, int y) {
            return setX(x).setY(y);
        }
        
        public TerminalLayoutDataBuilder setSize(int width, int height) {
            return setWidth(width).setHeight(height);
        }
        
        public TerminalLayoutDataBuilder setBounds(int x, int y, int width, int height) {
            return setX(x).setY(y).setWidth(width).setHeight(height);
        }

        public TerminalLayoutDataBuilder setBounds(TerminalRectangle rectangle) {
            return setRegion(rectangle);
        }
        
        public TerminalLayoutDataBuilder setRegion(TerminalRectangle region) {
            if (region != null) {
                return setBounds(region.getX(), region.getY(), 
                               region.getWidth(), region.getHeight());
            }
            return this;
        }

        public void reset(){
            super.reset();
            setX = false;
            setY = false;
            setWidth = false;
            setHeight = false;
        }
        
        @Override
        public TerminalLayoutData build() {
            TerminalLayoutData data = TerminalLayoutDataPool.getInstance().obtainData();
            data.initialize(this);
            return data;
        }
    }



}