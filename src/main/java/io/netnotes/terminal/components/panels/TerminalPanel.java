package io.netnotes.terminal.components.panels;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TextStyle;

public class TerminalPanel extends TerminalRenderable {
    
    private boolean drawBorder = false;
    private TextStyle.BoxStyle borderStyle = TextStyle.BoxStyle.SINGLE;
    private String title = null;
    
    public TerminalPanel(String name) {
        super(name);
    }
        
    public void setBorder(boolean enabled) {
        if (this.drawBorder != enabled) {
            this.drawBorder = enabled;
            invalidate();
        }
    }
    
    public void setBorderStyle(TextStyle.BoxStyle style) {
        if (this.borderStyle != style) {
            this.borderStyle = style;
            if (drawBorder) {
                invalidate();
            }
        }
    }
    
    public void setTitle(String title) {
        if ((this.title == null && title != null) || 
            (this.title != null && !this.title.equals(title))) {
            this.title = title;
            if (drawBorder) {
                invalidate();
            }
        }
    }
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (!drawBorder) return;
        
        // ✓ Use helper methods for dimensions
        int width = getWidth();
        int height = getHeight();
        
        if (width <= 0 || height <= 0) return;
        
        // ✓ Use LOCAL coordinates (0, 0) - base class handles translation
        drawBox(batch, 0, 0, width, height, borderStyle);
        
        // ✓ Use LOCAL coordinates for title - base class handles translation
        if (title != null && !title.isEmpty() && width > 4) {
            String displayTitle = title.length() > width - 4 
                ? title.substring(0, width - 4) 
                : title;
            // Title at local position (2, 0) - top-left with 2 char offset
            printAt(batch, 2, 0, displayTitle, null);
        }
    }

    
}