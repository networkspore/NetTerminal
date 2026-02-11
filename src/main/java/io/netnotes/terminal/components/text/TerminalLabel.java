package io.netnotes.terminal.components.text;

import io.netnotes.engine.ui.LabelTruncation;
import io.netnotes.engine.ui.TextAlignment;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.components.TerminalRegion;

/**
 * TerminalLabel - Enhanced text label with alignment and wrapping
 * 
 * FEATURES:
 * - Text alignment (left, center, right)
 * - Word wrapping support
 * - Truncation with ellipsis
 * - Multi-line support
 */
public class TerminalLabel extends TerminalRegion {
    

    
    private String text;
    private TextStyle style = TextStyle.NORMAL;
    private TextAlignment alignment = TextAlignment.LEFT;
    private LabelTruncation truncation = LabelTruncation.END;
    private boolean wordWrap = false;
    
    public TerminalLabel(String name){
        this(name, "");
    }

    public TerminalLabel(String name, TextStyle style){
        this(name, "");
        this.style = style;
    }
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
    
    public void setTextStyle(TextStyle style) {
        if (this.style != style) {
            this.style = style != null ? style : TextStyle.NORMAL;
            invalidate();
        }
    }
    
    public void setTextAlignment(TextAlignment alignment) {
        if (this.alignment != alignment) {
            this.alignment = alignment;
            invalidate();
        }
    }
    
    public void setTextTruncation(LabelTruncation truncation) {
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
        if (text.length() <= maxWidth || truncation == LabelTruncation.NONE) {
            return text;
        }
        
        return switch (truncation) {
            case END -> text.substring(0, Math.max(0, maxWidth - 3)) + "…";
            case START -> "…" + text.substring(Math.max(0, text.length() - maxWidth + 3));
            case MIDDLE -> {
                if (maxWidth < 5) yield text.substring(0, maxWidth);
                int half = (maxWidth - 3) / 2;
                yield text.substring(0, half) + "…" + 
                      text.substring(text.length() - (maxWidth - 3 - half));
            }
            default -> text.substring(0, maxWidth);
        };
    }
    
    // ===== GETTERS =====
    
    public String getText() { return text; }
    public TextStyle getStyle() { return style; }
    public TextAlignment getAlignment() { return alignment; }
    public LabelTruncation getTruncation() { return truncation; }
    public boolean isWordWrap() { return wordWrap; }

    @Override
    public int getPreferredWidth() {
        if (text == null || text.isEmpty()) {
            return getMinWidth();
        }
        String[] lines = text.split("\\R", -1);
        int maxLen = 0;
        for (String line : lines) {
            maxLen = Math.max(maxLen, line.length());
        }
        return Math.max(getMinWidth(), maxLen);
    }

    @Override
    public int getPreferredHeight() {
        if (text == null || text.isEmpty()) {
            return getMinHeight();
        }
        int lines = text.split("\\R", -1).length;
        return Math.max(getMinHeight(), Math.max(1, lines));
    }
    
    // ===== BUILDER =====
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name = "label";
        private String text = "";
        private TextStyle style = TextStyle.NORMAL;
        private TextAlignment alignment = TextAlignment.LEFT;
        private LabelTruncation truncation = LabelTruncation.END;
        private boolean wordWrap = false;
        private int x = 0, y = 0, width = 10, height = 1;
        
        public Builder name(String name) { this.name = name; return this; }
        public Builder text(String text) { this.text = text; return this; }
        public Builder style(TextStyle style) { this.style = style; return this; }
        public Builder alignment(TextAlignment align) { this.alignment = align; return this; }
        public Builder truncation(LabelTruncation trunc) { this.truncation = trunc; return this; }
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
            label.setTextAlignment(alignment);
            label.setTextTruncation(truncation);
            label.setWordWrap(wordWrap);
            return label;
        }
    }
}
