package io.netnotes.terminal.components;


import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TextStyle;
import io.netnotes.engine.ui.ScrollIndicator;

public class HScrollIndicator extends TerminalRenderable implements ScrollIndicator<TerminalRenderable> {
    
    public enum Style {
        SIMPLE,     // Just a moving block
        BAR,        // Track with proportional thumb
        ARROWS      // Track with arrows at ends
    }
    
    private int current = 0;
    private int max = 0;
    private int viewportSize = 0;
    private Style style = Style.BAR;
    
    private TextStyle trackStyle = TextStyle.BRIGHT_BLACK;
    private TextStyle thumbStyle = TextStyle.WHITE;
    private TextStyle arrowStyle = TextStyle.STATUS_INFO;
    
    public HScrollIndicator(String name) {
        this(name, Style.BAR);
    }
    
    public HScrollIndicator(String name, Style style) {
        super(name);
        this.style = style;
    }
    
    @Override
    public TerminalRenderable getRenderable() {
        return this;
    }
    
    @Override
    public void updatePosition(int current, int max, int viewportSize) {
        if (this.current != current || this.max != max || this.viewportSize != viewportSize) {
            this.current = current;
            this.max = max;
            this.viewportSize = viewportSize;
            invalidate();
        }
    }
    
    @Override
    public int getPreferredSize() {
        return 1;
    }
    
    public void setStyle(Style style) {
        if (this.style != style) {
            this.style = style;
            invalidate();
        }
    }
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        int width = getWidth();
        if (width <= 0 || max <= 0) return;
        
        switch (style) {
            case SIMPLE -> renderSimple(batch, width);
            case BAR -> renderBar(batch, width);
            case ARROWS -> renderArrows(batch, width);
        }
    }
    
    private void renderSimple(TerminalBatchBuilder batch, int width) {
        float scrollPercent = (max > 0) ? (float) current / max : 0;
        int position = (int) (scrollPercent * (width - 1));
        
        printAt(batch, position, 0, "█", thumbStyle);
    }
    
    private void renderBar(TerminalBatchBuilder batch, int width) {
        // Draw track
        for (int x = 0; x < width; x++) {
            printAt(batch, x, 0, "─", trackStyle);
        }
        
        // Calculate thumb
        int thumbWidth = Math.max(1,
            (int)((double)viewportSize / (max + viewportSize) * width));
        int thumbPosition = (max > 0)
            ? (int)((double)current / max * (width - thumbWidth))
            : 0;
        
        // Draw thumb
        for (int x = thumbPosition; x < thumbPosition + thumbWidth && x < width; x++) {
            printAt(batch, x, 0, "█", thumbStyle);
        }
    }
    
    private void renderArrows(TerminalBatchBuilder batch, int width) {
        if (width < 3) {
            renderBar(batch, width);
            return;
        }
        
        // Left arrow
        printAt(batch, 0, 0, "◄", current > 0 ? arrowStyle : trackStyle);
        
        // Right arrow
        printAt(batch, width - 1, 0, "►", 
            current < max ? arrowStyle : trackStyle);
        
        // Track
        int trackWidth = width - 2;
        for (int x = 1; x < width - 1; x++) {
            printAt(batch, x, 0, "─", trackStyle);
        }
        
        // Thumb
        if (trackWidth > 0) {
            int thumbWidth = Math.max(1,
                (int)((double)viewportSize / (max + viewportSize) * trackWidth));
            int thumbPosition = 1 + ((max > 0)
                ? (int)((double)current / max * (trackWidth - thumbWidth))
                : 0);
            
            for (int x = thumbPosition; x < thumbPosition + thumbWidth && x < width - 1; x++) {
                printAt(batch, x, 0, "█", thumbStyle);
            }
        }
    }
}