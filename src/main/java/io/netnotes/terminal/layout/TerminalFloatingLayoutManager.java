package io.netnotes.terminal.layout;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.engine.ui.FloatingLayerManager;
import io.netnotes.engine.ui.Point2D;
import io.netnotes.engine.ui.SpatialRegionPool;

public class TerminalFloatingLayoutManager extends FloatingLayerManager<
    TerminalBatchBuilder,
    TerminalRenderable,
    Point2D,
    TerminalRectangle,
    TerminalLayoutContext,
    TerminalLayoutData,
    TerminalLayoutCallback
> {

    public TerminalFloatingLayoutManager(String containerName, SpatialRegionPool<TerminalRectangle> regionPool) {
        super(containerName, regionPool);
    }
    
}
