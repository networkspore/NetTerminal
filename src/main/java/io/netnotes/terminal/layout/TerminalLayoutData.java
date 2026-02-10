package io.netnotes.terminal.layout;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRectanglePool;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.engine.ui.layout.LayoutData;


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
    protected boolean setX = false;
    protected boolean setY = false;
    protected boolean setWidth = false;
    protected boolean setHeight = false;

    public TerminalLayoutData(){
        super();
    }

    public void initialize(TerminalLayoutDataBuilder builder) {
        super.initialize(builder);
        this.setX = builder.setX;
        this.setY = builder.setY;
        this.setWidth = builder.setWidth;
        this.setHeight = builder.setHeight;

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
        super.reset();
        setX = false;
        setY = false;
        setWidth = false;
        setHeight = false;
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
            spatialRegion.setX(height);
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