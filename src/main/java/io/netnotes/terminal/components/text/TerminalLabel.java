package io.netnotes.terminal.components.text;

import io.netnotes.engine.ui.LabelTruncation;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.TextAlignment;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.LoggingHelpers.LogLevel;
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
    private TextStyle style;
    private TextAlignment alignment = TextAlignment.LEFT;
    private LabelTruncation truncation = LabelTruncation.END;
    private boolean wordWrap = false;
    
    public TerminalLabel(String name){
        this(name, "");
    }

    public TerminalLabel(String name, TextStyle style){
        this(name, "", style);
    }
    public TerminalLabel(String name, String text) {
        this(name, text, TextStyle.NORMAL);
   
    }
    
    public TerminalLabel(String name, String text, TextStyle style) {
        super(name);
        this.text = text;
        this.style = style;
        this.setWidthPreference(SizePreference.FIT_CONTENT);
        this.setHeightPreference(SizePreference.FIT_CONTENT);
    }
    
    // ===== CONFIGURATION =====
    
    public void setText(String text) {
        if ((this.text == null && text != null) || 
            (this.text != null && !this.text.equals(text))) {
            this.text = text;
            requestLayoutUpdate();
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
            requestLayoutUpdate();
        }
    }
    
    public void setTextTruncation(LabelTruncation truncation) {
        if (this.truncation != truncation) {
            this.truncation = truncation;
            requestLayoutUpdate();
        }
    }
    
    public void setWordWrap(boolean wordWrap) {
        if (this.wordWrap != wordWrap) {
            this.wordWrap = wordWrap;
            requestLayoutUpdate();
        }
    }
    
    // ===== RENDERING =====
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (text == null || text.isEmpty()) return;
        Log.logMsg("[TerminalLabel] rendering: " + text, LogLevel.IMPORTANT);
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

    // ===== SIZEABLE IMPLEMENTATION =====
    
    @Override
    public int getPreferredWidth() {
        SizePreference pref = getWidthPreference();
        
        // Handle STATIC - delegate to parent which uses region.getWidth()
        if (pref == SizePreference.STATIC) {
            return super.getPreferredWidth();
        }
        
        // Handle PERCENT and FILL - return minimum, layout will calculate actual size
        if (pref == SizePreference.PERCENT || pref == SizePreference.FILL) {
            return getMinWidth();
        }
        
        // FIT_CONTENT: Calculate based on text content
        if (text == null || text.isEmpty()) {
            return getMinWidth();
        }
        
        // Find the longest line in the text
        String[] lines = text.split("\\R", -1);
        int maxLen = 0;
        for (String line : lines) {
            maxLen = Math.max(maxLen, line.length());
        }
        
        // Return max of minimum width or (content width + insets)
        // Note: getMinWidth() already includes insets, so we add insets to content
        return Math.max(getMinWidth(), maxLen + getInsets().getHorizontal());
    }

    @Override
    public int getPreferredHeight() {
        SizePreference pref = getHeightPreference();
        
        // Handle STATIC - delegate to parent which uses region.getHeight()
        if (pref == SizePreference.STATIC) {
            return super.getPreferredHeight();
        }
        
        // Handle PERCENT and FILL - return minimum, layout will calculate actual size
        if (pref == SizePreference.PERCENT || pref == SizePreference.FILL) {
            return getMinHeight();
        }
        
        // FIT_CONTENT: Calculate based on text content
        if (text == null || text.isEmpty()) {
            return getMinHeight();
        }
        
        // Count the number of lines
        int lineCount = text.split("\\R", -1).length;
        
        // Return max of minimum height or (line count + insets)
        // Ensure at least 1 line
        return Math.max(getMinHeight(), Math.max(1, lineCount) + getInsets().getVertical());
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