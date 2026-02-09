package io.netnotes.terminal.components;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TextStyle;
import io.netnotes.engine.ui.ScrollIndicator;

/**
 * VScrollIndicator - Enhanced vertical scroll indicator
 * 
 * IMPROVEMENTS:
 * - Better visual styles
 * - Color customization
 * - Track and thumb distinction
 */
public class VScrollIndicator extends TerminalRenderable implements ScrollIndicator<TerminalRenderable> {
    
    public enum Style {
        SIMPLE,     // Just a moving block
        BAR,        // Track with proportional thumb
        ARROWS      // Track with arrows at ends
    }
    
    private int current = 0;
    private int max = 0;
    private int viewportSize = 0;
    private Style style = Style.BAR;
    
    // Styling
    private TextStyle trackStyle = TextStyle.BRIGHT_BLACK;
    private TextStyle thumbStyle = TextStyle.WHITE;
    private TextStyle arrowStyle = TextStyle.STATUS_INFO;
    
    public VScrollIndicator(String name) {
        this(name, Style.BAR);
    }
    
    public VScrollIndicator(String name, Style style) {
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
    
    public void setTrackStyle(TextStyle style) {
        this.trackStyle = style;
        invalidate();
    }
    
    public void setThumbStyle(TextStyle style) {
        this.thumbStyle = style;
        invalidate();
    }
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        int height = getHeight();
        if (height <= 0 || max <= 0) return;
        
        switch (style) {
            case SIMPLE -> renderSimple(batch, height);
            case BAR -> renderBar(batch, height);
            case ARROWS -> renderArrows(batch, height);
        }
    }
    
    private void renderSimple(TerminalBatchBuilder batch, int height) {
        // Just a moving indicator
        float scrollPercent = (max > 0) ? (float) current / max : 0;
        int position = (int) (scrollPercent * (height - 1));
        
        printAt(batch, 0, position, "█", thumbStyle);
    }
    
    private void renderBar(TerminalBatchBuilder batch, int height) {
        // Draw track
        for (int y = 0; y < height; y++) {
            printAt(batch, 0, y, "│", trackStyle);
        }
        
        // Calculate thumb size and position
        int thumbHeight = Math.max(1, 
            (int)((double)viewportSize / (max + viewportSize) * height));
        int thumbPosition = (max > 0)
            ? (int)((double)current / max * (height - thumbHeight))
            : 0;
        
        // Draw thumb
        for (int y = thumbPosition; y < thumbPosition + thumbHeight && y < height; y++) {
            printAt(batch, 0, y, "█", thumbStyle);
        }
    }
    
    private void renderArrows(TerminalBatchBuilder batch, int height) {
        if (height < 3) {
            renderBar(batch, height);
            return;
        }
        
        // Up arrow
        printAt(batch, 0, 0, "▲", current > 0 ? arrowStyle : trackStyle);
        
        // Down arrow
        printAt(batch, 0, height - 1, "▼", 
            current < max ? arrowStyle : trackStyle);
        
        // Track in middle
        int trackHeight = height - 2;
        for (int y = 1; y < height - 1; y++) {
            printAt(batch, 0, y, "│", trackStyle);
        }
        
        // Thumb
        if (trackHeight > 0) {
            int thumbHeight = Math.max(1,
                (int)((double)viewportSize / (max + viewportSize) * trackHeight));
            int thumbPosition = 1 + ((max > 0)
                ? (int)((double)current / max * (trackHeight - thumbHeight))
                : 0);
            
            for (int y = thumbPosition; y < thumbPosition + thumbHeight && y < height - 1; y++) {
                printAt(batch, 0, y, "█", thumbStyle);
            }
        }
    }
}