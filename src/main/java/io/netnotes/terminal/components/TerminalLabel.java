package io.netnotes.terminal.components;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalCommands;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TextStyle;

/**
 * TerminalLabel - Enhanced text label with alignment and wrapping
 * 
 * FEATURES:
 * - Text alignment (left, center, right)
 * - Word wrapping support
 * - Truncation with ellipsis
 * - Multi-line support
 */
public class TerminalLabel extends TerminalRenderable {
    
    public enum Truncation {
        NONE,           // No truncation (may overflow)
        END,            // "This is a long tex..."
        START,          // "...is a long text"
        MIDDLE          // "This is...ng text"
    }
    
    private String text;
    private TextStyle style = TextStyle.NORMAL;
    private TerminalCommands.Alignment alignment = TerminalCommands.Alignment.LEFT;
    private Truncation truncation = Truncation.END;
    private boolean wordWrap = false;
    
    public TerminalLabel(String name, String text) {
        super(name);
        this.text = text;
    }
    
    public TerminalLabel(String name, String text, TextStyle style) {
        super(name);
        this.text = text;
        this.style = style;
    }
    
    // ===== CONFIGURATION =====
    
    public void setText(String text) {
        if ((this.text == null && text != null) || 
            (this.text != null && !this.text.equals(text))) {
            this.text = text;
            invalidate();
        }
    }
    
    public void setStyle(TextStyle style) {
        if (this.style != style) {
            this.style = style != null ? style : TextStyle.NORMAL;
            invalidate();
        }
    }
    
    public void setAlignment(TerminalCommands.Alignment alignment) {
        if (this.alignment != alignment) {
            this.alignment = alignment;
            invalidate();
        }
    }
    
    public void setTruncation(Truncation truncation) {
        if (this.truncation != truncation) {
            this.truncation = truncation;
            invalidate();
        }
    }
    
    public void setWordWrap(boolean wordWrap) {
        if (this.wordWrap != wordWrap) {
            this.wordWrap = wordWrap;
            invalidate();
        }
    }
    
    // ===== RENDERING =====
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (text == null || text.isEmpty()) return;
        
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        
        if (wordWrap && height > 1) {
            // Use drawTextBlock for word wrapping
            drawTextBlock(batch, 0, 0, width, height, text, alignment, style);
        } else {
            // Single line or no wrapping
            String displayText = truncateText(text, width);
            
            int x = switch (alignment) {
                case CENTER -> Math.max(0, (width - displayText.length()) / 2);
                case RIGHT -> Math.max(0, width - displayText.length());
                default -> 0;
            };
            
            printAt(batch, x, 0, displayText, style);
        }
    }
    
    private String truncateText(String text, int maxWidth) {
        if (text.length() <= maxWidth || truncation == Truncation.NONE) {
            return text;
        }
        
        return switch (truncation) {
            case END -> text.substring(0, Math.max(0, maxWidth - 3)) + "...";
            case START -> "..." + text.substring(Math.max(0, text.length() - maxWidth + 3));
            case MIDDLE -> {
                if (maxWidth < 5) yield text.substring(0, maxWidth);
                int half = (maxWidth - 3) / 2;
                yield text.substring(0, half) + "..." + 
                      text.substring(text.length() - (maxWidth - 3 - half));
            }
            default -> text.substring(0, maxWidth);
        };
    }
    
    // ===== GETTERS =====
    
    public String getText() { return text; }
    public TextStyle getStyle() { return style; }
    public TerminalCommands.Alignment getAlignment() { return alignment; }
    public Truncation getTruncation() { return truncation; }
    public boolean isWordWrap() { return wordWrap; }
    
    // ===== BUILDER =====
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name = "label";
        private String text = "";
        private TextStyle style = TextStyle.NORMAL;
        private TerminalCommands.Alignment alignment = TerminalCommands.Alignment.LEFT;
        private Truncation truncation = Truncation.END;
        private boolean wordWrap = false;
        private int x = 0, y = 0, width = 10, height = 1;
        
        public Builder name(String name) { this.name = name; return this; }
        public Builder text(String text) { this.text = text; return this; }
        public Builder style(TextStyle style) { this.style = style; return this; }
        public Builder alignment(TerminalCommands.Alignment align) { this.alignment = align; return this; }
        public Builder truncation(Truncation trunc) { this.truncation = trunc; return this; }
        public Builder wordWrap(boolean wrap) { this.wordWrap = wrap; return this; }
        public Builder position(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }
        public Builder bounds(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            return this;
        }
        
        public TerminalLabel build() {
            TerminalLabel label = new TerminalLabel(name, text, style);
            label.setBounds(x, y, width, height);
            label.setAlignment(alignment);
            label.setTruncation(truncation);
            label.setWordWrap(wordWrap);
            return label;
        }
    }
}