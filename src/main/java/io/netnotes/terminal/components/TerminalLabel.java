package io.netnotes.terminal.components;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TextStyle;

public class TerminalLabel extends TerminalRenderable {
    
    private String text;
    private TextStyle style = TextStyle.NORMAL;
    
    public TerminalLabel(String name, String text) {
        super(name);
        this.text = text;
    }
    
    public void setText(String text) {
        if ((this.text == null && text != null) || 
            (this.text != null && !this.text.equals(text))) {
            this.text = text;
            invalidate();
        }
    }
    
    public void setStyle(TextStyle style) {
        if (this.style != style) {
            this.style = style;
            invalidate();
        }
    }
    
    public String getText() {
        return text;
    }
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (text == null || text.isEmpty()) return;
        
        int width = getWidth();
        if (width <= 0) return;
        
        String displayText = text.length() > width ? text.substring(0, width) : text;
        printAt(batch, 0, 0, displayText, style);
    }
}