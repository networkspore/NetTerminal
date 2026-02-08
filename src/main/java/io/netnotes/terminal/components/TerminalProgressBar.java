package io.netnotes.terminal.components;

import io.netnotes.terminal.*;

/**
 * TerminalProgressBar - Damage-aware progress bar
 * 
 * DAMAGE TRACKING:
 * - Percent changes invalidate bar line only
 * - Message changes invalidate message line only
 * - Combined updates invalidate both lines
 */
public class TerminalProgressBar extends TerminalRenderable {
    
    private final Style style;
    
    private double currentPercent = 0;
    
    public enum Style {
        CLASSIC, BLOCKS, SHADED, ARROWS
    }
    
    public TerminalProgressBar(String name) {
        this(name, Style.CLASSIC);
    }
    
    public TerminalProgressBar(String name, Style style) {
        this(name,0, 0 , 20, style);
    }
    
    public TerminalProgressBar(String name, int row, int col, int width) {
        this(name, row, col, width, Style.CLASSIC);
    }
    
    public TerminalProgressBar(String name, int row, int col, int width, Style style) {
        super(name);
        this.style = style;

        setBounds(col, row, width, 1);
    }
    
 
    // ===== DAMAGE-AWARE RENDERING =====
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        int width = getWidth();
        if (width <= 0) return;
        
        // Bar at local row 0
        String bar = generateBar(currentPercent, width);
        printAt(batch, 0, 0, bar, TextStyle.NORMAL);
    }

    private String generateBar(double percent, int width) {
        int pct = (int) Math.max(0, Math.min(100, percent));
        int barWidth = Math.max(1, width - 6);
        int filled = (int) ((pct / 100.0) * barWidth);
        
        return switch (style) {
            case CLASSIC -> String.format("|%2d%%|%s%s|",
                pct, "=".repeat(filled), "-".repeat(barWidth - filled));
            case BLOCKS -> String.format("[%s%s] %2d%%",
                "█".repeat(filled), "░".repeat(barWidth - filled), pct);
            case SHADED -> String.format("%s%s %2d%%",
                "▓".repeat(filled), "░".repeat(barWidth - filled), pct);
            case ARROWS -> String.format("%s%s %2d%%",
                ">".repeat(filled), "-".repeat(barWidth - filled), pct);
        };
    }
    
    // ===== STATE UPDATES WITH SMART INVALIDATION =====
    
    public void updatePercent(double percent) {
        double clamped = Math.max(0, Math.min(100, percent));
        if (this.currentPercent != clamped) {
            this.currentPercent = clamped;
            invalidate();
        }
    }
    
    public void updatePercent(double percent, String message) {
        boolean percentChanged = false;
        
        double clamped = Math.max(0, Math.min(100, percent));
        if (this.currentPercent != clamped) {
            this.currentPercent = clamped;
            percentChanged = true;
        }
        
        if (percentChanged) {
           invalidate();
        }
    }


    
    public void complete() { updatePercent(100); }
    
    public void reset() {
        boolean changed = false;
        if (this.currentPercent != 0) {
            this.currentPercent = 0;
            changed = true;
        }
        if (changed) {
            invalidate();
        }
    }
    
    // ===== BAR GENERATION =====
    public double getCurrentPercent() { return currentPercent; }
    public Style getStyle() { return style; }
    public boolean isComplete() { return currentPercent >= 100; }
    public double getFraction() { return currentPercent / 100.0; }
}