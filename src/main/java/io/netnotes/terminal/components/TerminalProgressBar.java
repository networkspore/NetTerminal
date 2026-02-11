package io.netnotes.terminal.components;

import io.netnotes.engine.ui.Orientation;
import io.netnotes.terminal.*;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutable;
/**
 * TerminalProgressBar - Enhanced progress bar with color support
 * 
 * FEATURES:
 * - Multiple visual styles
 * - Custom colors for filled/empty portions
 * - Optional label and percentage display
 * - Vertical or horizontal orientation
 */
public class TerminalProgressBar extends TerminalRegion implements TerminalLayoutable {
    public final static int MIN_WIDTH = 8;
    public enum Style {
        CLASSIC,    // |25%|=====-----|
        BLOCKS,     // [█████░░░░░] 25%
        SHADED,     // ▓▓▓▓▓░░░░░ 25%
        ARROWS,     // >>>>>----- 25%
        SMOOTH      // Uses drawProgressBar command (sub-character resolution)
    }
    
    
    private final TerminalInsets insets = new TerminalInsets();
    
    private double currentPercent = 0;
    private String label = null;
    private final Style style;
    private final Orientation orientation;
    private boolean showPercentage = true;
    
    // Style customization
    private TextStyle filledStyle = TextStyle.PROGRESS_FILLED;
    private TextStyle emptyStyle = TextStyle.PROGRESS_EMPTY;
    private TextStyle textStyle = TextStyle.PROGRESS_TEXT;

    private boolean isHiddenManaged = true;

    public TerminalProgressBar(String name) {
        this(name, Style.SMOOTH, Orientation.HORIZONTAL);
    }
    
    public TerminalProgressBar(String name, Style style) {
        this(name, style, Orientation.HORIZONTAL);
    }
    
    public TerminalProgressBar(String name, Style style, Orientation orientation) {
        super(name);
        this.style = style;
        this.orientation = orientation;
        setMinWidth(MIN_WIDTH);
        setMinHeight(1);
        setWidthPreference(SizePreference.STATIC);
        setHeightPreference(SizePreference.STATIC);
    }

    public TerminalProgressBar(Builder builder){
        this(builder.name, builder.style, builder.orientation);
        this.label = builder.label;
        this.showPercentage = builder.showPercentage;
        setMinWidth(builder.minWidth);
        setMinHeight(builder.minHeight);
        setBounds(builder.x, builder.y, builder.width, builder.height);
        updatePercent(builder.initialPercent);
    }
    
    // ===== CONFIGURATION =====
    
    public void setLabel(String label) {
        if ((this.label == null && label != null) || 
            (this.label != null && !this.label.equals(label))) {
            this.label = label;
            invalidate();
        }
    }
    
    public void setShowPercentage(boolean show) {
        if (this.showPercentage != show) {
            this.showPercentage = show;
            invalidate();
        }
    }
    
    public void setFilledStyle(TextStyle style) {
        this.filledStyle = style;
        invalidate();
    }
    
    public void setEmptyStyle(TextStyle style) {
        this.emptyStyle = style;
        invalidate();
    }
    
    public void setTextStyle(TextStyle style) {
        this.textStyle = style;
        invalidate();
    }
    
    
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        int drawWidth = Math.max (getMinWidth() - insets.getHorizontal(), width - insets.getHorizontal());
        int drawHeight = Math.max (getMinHeight() - insets.getVertical(), height - insets.getVertical());

        if (style == Style.SMOOTH) {
            
          

            drawProgressBar(batch, insets.getLeft(), insets.getTop(),drawWidth , drawHeight, 
                currentPercent / 100.0, filledStyle, emptyStyle);
            
            // Overlay label/percentage if needed
            if (label != null || showPercentage) {
                String text = buildOverlayText();
                int x = (width - text.length()) / 2;
                int y = height / 2;
                printAt(batch, x, y, text, textStyle);
            }
        } else if (orientation == Orientation.HORIZONTAL) {
            renderHorizontal(batch, drawWidth, drawHeight);
        } else {
            renderVertical(batch, drawWidth, drawHeight);
        }
    }
    
    private void renderHorizontal(TerminalBatchBuilder batch, int width, int height) {
        String bar = generateHorizontalBar(currentPercent, width);
        int y = height / 2;
        printAt(batch, 0, y, bar, TextStyle.NORMAL);
        
        // Label on line above if present
        if (label != null && height > 1) {
            int x = (width - label.length()) / 2;
            printAt(batch, Math.max(0, x), y - 1, label, textStyle);
        }
    }
    
    private void renderVertical(TerminalBatchBuilder batch, int width, int height) {
        int pct = (int) Math.max(0, Math.min(100, currentPercent));
        int filled = (int) ((pct / 100.0) * height);
        
        String fillChar = switch (style) {
            case BLOCKS -> "█";
            case SHADED -> "▓";
            default -> "=";
        };
        
        String emptyChar = switch (style) {
            case BLOCKS -> "░";
            case SHADED -> "░";
            default -> "-";
        };
        
        // Draw from bottom to top
        for (int y = 0; y < height; y++) {
            int invertedY = height - 1 - y;
            String ch = (y < filled) ? fillChar : emptyChar;
            TextStyle style = (y < filled) ? filledStyle : emptyStyle;
            printAt(batch, 0, invertedY, ch, style);
        }
        
        // Percentage overlay
        if (showPercentage && width > 3) {
            String pctText = pct + "%";
            int y = height / 2;
            int x = (width - pctText.length()) / 2;
            printAt(batch, Math.max(0, x), y, pctText, textStyle);
        }
    }
    
    private String generateHorizontalBar(double percent, int width) {
        int pct = (int) Math.max(0, Math.min(100, percent));
        
        // Calculate bar width (reserve space for percentage if shown)
        int reservedSpace = showPercentage ? 5 : 0; // " 25%"
        int barWidth = Math.max(1, width - reservedSpace - 2); // -2 for brackets
        int filled = (int) ((pct / 100.0) * barWidth);
        
        String fillChar = switch (style) {
            case BLOCKS -> "█";
            case SHADED -> "▓";
            case ARROWS -> ">";
            default -> "=";
        };
        
        String emptyChar = switch (style) {
            case BLOCKS -> "░";
            case SHADED -> "░";
            case ARROWS -> "-";
            default -> "-";
        };
        
        String bar = fillChar.repeat(filled) + emptyChar.repeat(barWidth - filled);
        
        if (style == Style.CLASSIC) {
            return String.format("|%s|%s", bar, showPercentage ? String.format(" %2d%%", pct) : "");
        } else {
            return String.format("[%s]%s", bar, showPercentage ? String.format(" %2d%%", pct) : "");
        }
    }
    
    private String buildOverlayText() {
        if (label != null && showPercentage) {
            return label + " " + (int)currentPercent + "%";
        } else if (label != null) {
            return label;
        } else if (showPercentage) {
            return (int)currentPercent + "%";
        }
        return "";
    }
    
    // ===== STATE UPDATES =====
    
    public void updatePercent(double percent) {
        double clamped = Math.max(0, Math.min(100, percent));
        if (this.currentPercent != clamped) {
            this.currentPercent = clamped;
            invalidate();
        }
    }
    
    public void complete() { updatePercent(100); }
    public void reset() { updatePercent(0); }
    
    public void increment(double delta) { updatePercent(currentPercent + delta); }
    public void decrement(double delta) { updatePercent(currentPercent - delta); }
    
    // ===== GETTERS =====
    
    public double getCurrentPercent() { return currentPercent; }
    public String getLabel() { return label; }
    public Style getStyle() { return style; }
    public Orientation getOrientation() { return orientation; }
    public boolean isComplete() { return currentPercent >= 100; }
    public double getFraction() { return currentPercent / 100.0; }
    

    
    @Override
    public void setMinWidth(int minWidth) {
        super.setMinWidth(Math.max(minWidth, MIN_WIDTH));
    }

    @Override
    public void setMinHeight(int minHeight) {
        super.setMinHeight(Math.max(1, minHeight));
    }


    @Override
    public TerminalInsets getInsets() {
       return insets;
    }

    @Override
    public boolean isHiddenManaged() {
        return isHiddenManaged;
    }

    public void setIsHiddenManaged(boolean ishiddenManaged){
        this.isHiddenManaged = ishiddenManaged;
    }



    // ===== BUILDER =====
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        protected String name = "progressbar";
        protected Style style = Style.SMOOTH;
        protected Orientation orientation = Orientation.HORIZONTAL;
        protected double initialPercent = 0;
        protected String label = null;
        protected boolean showPercentage = true;
        protected int x = 0, y = 0, width = 20, height = 1;
        protected int minWidth = MIN_WIDTH;
        protected int minHeight = 1;
        
        public Builder name(String name) { this.name = name; return this; }
        public Builder style(Style style) { this.style = style; return this; }
        public Builder orientation(Orientation orient) { this.orientation = orient; return this; }
        public Builder percent(double pct) { this.initialPercent = pct; return this; }
        public Builder label(String label) { this.label = label; return this; }
        public Builder showPercentage(boolean show) { this.showPercentage = show; return this; }
        public Builder position(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }
        public Builder bounds(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            return this;
        }
        public Builder minWidth(int minWidth) { this.minWidth = Math.max(minWidth, MIN_WIDTH); return this; }
        public Builder minHeight(int minHeight) { this.minHeight = Math.max(1, minHeight); return this; }
        public TerminalProgressBar build() {
            return new TerminalProgressBar(this);
        }
    }

}
