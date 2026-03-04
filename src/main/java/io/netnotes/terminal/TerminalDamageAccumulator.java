package io.netnotes.terminal;

import io.netnotes.engine.ui.Point2D;
import io.netnotes.engine.ui.renderer.DamageAccumulator;

public class TerminalDamageAccumulator extends DamageAccumulator<
    Point2D,
    TerminalRectangle
> {

    public TerminalDamageAccumulator(TerminalRectanglePool pool) {
        super(pool);
    }
    
}
