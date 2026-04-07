package io.netnotes.terminal.components;

import io.netnotes.engine.ui.Orientation;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.*;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutContext;
/**
 * TerminalProgressBar - Single-line progress display with optional percentage text.
 */
public class TerminalProgressBar extends TerminalRegion {
    public final static int MIN_SIZE = 5;
    public enum Style {
        CLASSIC,    // |25%|=====-----|
        BLOCKS,     // [█████░░░░░] 25%
        SHADED,     // ▓▓▓▓▓░░░░░ 25%
        ARROWS,     // >>>>>----- 25%
        SMOOTH      // Uses drawProgressBar command (sub-character resolution)
    }
    
    
    private final TerminalInsets insets = new TerminalInsets();
    
    private double currentPercent = 0;
    private Style style;
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

        if(orientation == Orientation.VERTICAL){
            setMinSize(1, MIN_SIZE);
       
        }else{
            setMinSize(MIN_SIZE, 1);
        }
      
        setWidthPreference(SizePreference.STATIC);
        setHeightPreference(SizePreference.STATIC);
    }

    public TerminalProgressBar(Builder builder){
        this(builder.name, builder.style, builder.orientation);
        this.showPercentage = builder.showPercentage;
        
        setBounds(builder.x, builder.y, builder.width, builder.height);
        updatePercentDouble(builder.initialPercent);
    }
    
    // ===== CONFIGURATION =====

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
    
    public void setBorderTextStyle(TextStyle style) {
        this.textStyle = style;
        invalidate();
    }
    

   
    
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        int drawX = insets.getLeft();
        int drawY = insets.getTop();
        int drawWidth = width - insets.getHorizontal();
        int drawHeight = height - insets.getVertical();
        if (drawWidth <= 0 || drawHeight <= 0) return;

        if (orientation == Orientation.HORIZONTAL) {
            renderHorizontal(batch, drawX, drawY, drawWidth, drawHeight);
        } else if (style == Style.SMOOTH) {
            drawProgressBar(batch, drawX, drawY, drawWidth, drawHeight,
                currentPercent, filledStyle, emptyStyle);
        } else {
            renderVertical(batch, drawX, drawY, drawWidth, drawHeight);
        }
    }
    
    private void renderHorizontal(TerminalBatchBuilder batch, int x, int y, int width, int height) {
        int row = y + Math.max(0, (height - 1) / 2);
        if (style == Style.SMOOTH) {
            renderSmoothHorizontal(batch, x, row, width);
            return;
        }

        renderStyledHorizontal(batch, x, row, width);
    }
    
    private void renderVertical(TerminalBatchBuilder batch, int x, int y, int width, int height) {
        double pct = Math.max(0.0, Math.min(1.0, currentPercent));
        int filled = (int) (pct * height);
        
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
        for (int row = 0; row < height; row++) {
            int invertedY = y + (height - 1 - row);
            String ch = (row < filled) ? fillChar : emptyChar;
            TextStyle style = (row < filled) ? filledStyle : emptyStyle;
            printAt(batch, x, invertedY, ch, style);
        }
        
        // Percentage overlay
        if (showPercentage && width > 3) {
            String pctText = getPercentText();
            int row = y + (height / 2);
            int textX = x + Math.max(0, (width - pctText.length()) / 2);
            printAt(batch, textX, row, pctText, textStyle);
        }
    }
    
    private void renderStyledHorizontal(TerminalBatchBuilder batch, int x, int y, int width) {
        int pct = (int) Math.max(0, Math.min(1, currentPercent));
        String percentText = showPercentage ? " " + getPercentText() : "";
        int barWidth = Math.max(1, width - percentText.length() - 2);
        int filled = (int) (pct * barWidth);

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

        String leftCap = style == Style.CLASSIC ? "|" : "[";
        String rightCap = style == Style.CLASSIC ? "|" : "]";

        printAt(batch, x, y, leftCap, textStyle);
        if (filled > 0) {
            printAt(batch, x + 1, y, fillChar.repeat(filled), filledStyle);
        }
        if (barWidth - filled > 0) {
            printAt(batch, x + 1 + filled, y, emptyChar.repeat(barWidth - filled), emptyStyle);
        }
        printAt(batch, x + 1 + barWidth, y, rightCap, textStyle);
        if (!percentText.isEmpty()) {
            printAt(batch, x + 2 + barWidth, y, percentText, textStyle);
        }
    }

    private void renderSmoothHorizontal(TerminalBatchBuilder batch, int x, int y, int width) {
        String percentText = showPercentage ? " " + getPercentText() : "";
        int barWidth = Math.max(0, width - percentText.length());

        if (barWidth > 0) {
            drawProgressBar(batch, x, y, barWidth, 1, currentPercent, filledStyle, emptyStyle);
        }
        if (!percentText.isEmpty()) {
            int textX = barWidth > 0 ? x + barWidth : x;
            printAt(batch, textX, y, percentText, textStyle);
        }
    }

    public String getProgressNumberString(){
        return String.format("%.1f",currentPercent * 100.0);
    }

    private String getPercentText() {
        return getProgressNumberString() + "%";
    }
    
    // ===== STATE UPDATES =====
    /**
     * 
     * @param percent 0.0 - 1.0
     */
    public void updatePercentDouble(double percent) {
        double clamped = Math.max(0, Math.min(1, percent));
        if (this.currentPercent != clamped) {
            this.currentPercent = clamped;
            invalidate();
        }
    }

    public void setProgress(double percent){
        updatePercentDouble(percent);
    }
    
    public void complete() { updatePercentDouble(1); }
    public void reset() { updatePercentDouble(0); }
    
    public void incrementDouble(double delta) { updatePercentDouble(currentPercent + delta); }
    public void decrementDouble(double delta) { updatePercentDouble(currentPercent - delta); }
    

    public void incrementInt(int delta) { updatePercentDouble(currentPercent + (delta/100.0)); }
    public void decrementInt(int delta) { updatePercentDouble(currentPercent - (delta/100.0)); }
    // ===== GETTERS =====
    
    public double getCurrentPercent() { return currentPercent; }
    public Style getStyle() { return style; }
    public Orientation getOrientation() { return orientation; }
    public boolean isComplete() { return currentPercent >= 100; }

    

    public void setProgressStyle(Style style){
        this.style = style;
        invalidate();
    }

    
    @Override
    public void setMinSize(int minWidth, int minHeight) {
        super.setMinWidth(Math.max(minWidth, 1));
        super.setMinHeight(Math.max(minHeight, MIN_SIZE));
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
        protected boolean showPercentage = true;
        protected int x = 0, y = 0, width = 20, height = 1;
        protected int minWidth = MIN_SIZE;
        protected int minHeight = 1;
        
        public Builder name(String name) { this.name = name; return this; }
        public Builder style(Style style) { this.style = style; return this; }
        public Builder orientation(Orientation orient) { this.orientation = orient; return this; }
        public Builder percent(double pct) { this.initialPercent = pct; return this; }
        public Builder showPercentage(boolean show) { this.showPercentage = show; return this; }
        public Builder position(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }
        public Builder bounds(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            return this;
        }
        public Builder minWidth(int minWidth) { this.minWidth = Math.max(minWidth, MIN_SIZE); return this; }
        public Builder minHeight(int minHeight) { this.minHeight = Math.max(1, minHeight); return this; }
        public TerminalProgressBar build() {
            return new TerminalProgressBar(this);
        }
    }

    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        int measuredWidth = getWidthPreference() == SizePreference.FIT_CONTENT
            ? measureContentWidth()
            : super.getMinWidth() + insets.getHorizontal();

        int measuredHeight = getHeightPreference() == SizePreference.FIT_CONTENT
            ? measureContentHeight()
            : super.getMinHeight() + insets.getVertical();

        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, measuredWidth, measuredHeight);
        return measured;
    }

    private int measureContentWidth() {
        int contentWidth;
        if (orientation == Orientation.VERTICAL) {
            contentWidth = showPercentage ? Math.max(1, getPercentText().length()) : 1;
        } else {
            int barWidth = switch (style) {
                case CLASSIC, BLOCKS -> 3;
                default -> 1;
            };
            contentWidth = barWidth + (showPercentage ? 1 + getPercentText().length() : 0);
        }

        return Math.max(super.getMinWidth() + insets.getHorizontal(), contentWidth + insets.getHorizontal());
    }

    private int measureContentHeight() {
        int contentHeight = orientation == Orientation.VERTICAL ? MIN_SIZE : 1;
        return Math.max(super.getMinHeight() + insets.getVertical(), contentHeight + insets.getVertical());
    }
}
