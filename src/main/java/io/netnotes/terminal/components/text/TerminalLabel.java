package io.netnotes.terminal.components.text;

import io.netnotes.debug.BatchTraceAspect;
import io.netnotes.engine.ui.LabelTruncation;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.TextAlignment;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.layout.TerminalLayoutContext;

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
    
    /**
     * Controls how a wrapped label estimates its FIT_CONTENT height when the
     * width is parent-driven (for example width=FILL).
     */
    public enum WrappedHeightStrategy {
        /**
         * Height is based only on explicit line breaks in the text.
         * This preserves the legacy behaviour.
         */
        EXPLICIT_LINES_ONLY,

        /**
         * Height is estimated from the label's current or requested width.
         *
         * <p>If neither width is known yet during an early measure pass, this
         * strategy falls back to explicit line counting. For parent-driven
         * widths such as {@code FILL}, prefer {@link #CURRENT_WIDTH_OR_HINT}
         * or {@link #WIDTH_HINT} when you need stable first-pass measurement.
         */
        CURRENT_WIDTH,

        /**
         * Height is estimated from an application-provided wrap-width hint.
         */
        WIDTH_HINT,

        /**
         * Prefer the current/requested width, but fall back to the configured
         * wrap-width hint when no usable width is available yet.
         */
        CURRENT_WIDTH_OR_HINT
    }

    
    private String text;
    private TextStyle style;
    private TextAlignment alignment = TextAlignment.LEFT;
    private LabelTruncation truncation = LabelTruncation.END;
    private boolean wordWrap = false;
    private int maxLines = 0;
    private WrappedHeightStrategy wrappedHeightStrategy = WrappedHeightStrategy.EXPLICIT_LINES_ONLY;
    private int wrapWidthHint = 0;

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
            // Text changes can affect both intrinsic size and painted content.
            requestLayoutUpdate();
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

    public void setWrappedHeightStrategy(WrappedHeightStrategy strategy) {
        WrappedHeightStrategy next = strategy != null
            ? strategy
            : WrappedHeightStrategy.EXPLICIT_LINES_ONLY;
        if (this.wrappedHeightStrategy != next) {
            this.wrappedHeightStrategy = next;
            requestLayoutUpdate();
        }
    }

    public WrappedHeightStrategy getWrappedHeightStrategy() {
        return wrappedHeightStrategy;
    }

    /**
     * Optional content-width hint used by wrapped-height measurement.
     * The value is expressed in content columns, excluding insets.
     *
     * <p>This hint matters primarily when width is parent-driven (for example
     * {@code FILL}) and the caller chooses {@link WrappedHeightStrategy#WIDTH_HINT}
     * or {@link WrappedHeightStrategy#CURRENT_WIDTH_OR_HINT}. For intrinsically
     * sized widths such as {@code FIT_CONTENT}, the measured width is already
     * known and the hint is usually irrelevant.
     */
    public void setWrapWidthHint(int wrapWidthHint) {
        int clamped = Math.max(0, wrapWidthHint);
        if (this.wrapWidthHint != clamped) {
            this.wrapWidthHint = clamped;
            requestLayoutUpdate();
        }
    }

    public int getWrapWidthHint() {
        return wrapWidthHint;
    }
    
    // ===== RENDERING =====
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        BatchTraceAspect.onTextRenderSelf(this, text, true);
        if (text == null || text.isEmpty()) {
            BatchTraceAspect.onTextRenderSelf(this, text, false);
            return;
        }

        int width  = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        String[] lines = text.split("\\R", maxLines + 1 > 0 ? maxLines : -1);

        // Effective row limit: the lesser of the allocated height and maxLines (if set).
        int rowLimit = maxLines > 0 ? Math.min(maxLines, height) : height;

        int y = 0;
        for (String line : lines) {
            if (y >= rowLimit) break;

            if (wordWrap && line.length() > width) {
                while (!line.isEmpty() && y < rowLimit) {
                    String chunk = line.length() <= width ? line : line.substring(0, width);
                    printAligned(batch, chunk, y, width);
                    line = line.length() <= width ? "" : line.substring(width);
                    y++;
                }
            } else {
                printAligned(batch, truncateText(line, width), y, width);
                y++;
            }
        }
    }

    private void printAligned(TerminalBatchBuilder batch, String text, int y, int width) {
        int x = switch (alignment) {
            case CENTER -> Math.max(0, (width - text.length()) / 2);
            case RIGHT  -> Math.max(0, width - text.length());
            default     -> 0;
        };
        printAt(batch, x, y, text, style);
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

    /**
     * Maximum number of lines this label may occupy. 0 means unlimited.
     * Affects both {@code measureContent} (so the parent sizes correctly) and
     * rendering (excess lines are dropped). When combined with {@code wordWrap},
     * wrapped lines count toward this limit.
     */
    public void setMaxLines(int maxLines) {
        int clamped = Math.max(0, maxLines);
        if (this.maxLines != clamped) {
            this.maxLines = clamped;
            requestLayoutUpdate();
        }
    }

    public int getMaxLines() { return maxLines; }

    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        int measuredWidth = getWidthPreference() == SizePreference.FIT_CONTENT
            ? measureContentWidth()
            : getMinWidth();

        int measuredHeight = getHeightPreference() == SizePreference.FIT_CONTENT
            ? measureContentHeight()
            : getMinHeight();

        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, measuredWidth, measuredHeight);
        return measured;
    }

    private int measureContentWidth() {
        if (text == null || text.isEmpty()) {
            return getMinWidth();
        }

        String[] lines = text.split("\\R", -1);
        int maxLen = 0;
        for (String line : lines) {
            maxLen = Math.max(maxLen, line.length());
        }

        return Math.max(getMinWidth(), maxLen + getInsets().getHorizontal());
    }

    private int measureContentHeight() {
        if (text == null || text.isEmpty()) return getMinHeight();

        int lineCount = countMeasuredLines();
        return Math.max(getMinHeight(), Math.max(1, lineCount) + getInsets().getVertical());
    }

    private int countMeasuredLines() {
        String[] lines = text.split("\\R", -1);
        int wrapWidth = resolveWrapMeasureWidth();
        int lineCount = 0;

        for (String line : lines) {
            int visualLines = 1;
            if (wordWrap && wrapWidth > 0) {
                visualLines = Math.max(1, (line.length() + wrapWidth - 1) / wrapWidth);
            }

            lineCount += visualLines;
            if (maxLines > 0 && lineCount >= maxLines) {
                return maxLines;
            }
        }

        return Math.max(1, lineCount);
    }

    private int resolveWrapMeasureWidth() {
        if (!wordWrap) {
            return 0;
        }

        return switch (wrappedHeightStrategy) {
            case EXPLICIT_LINES_ONLY -> 0;
            case CURRENT_WIDTH -> resolveCurrentWrapWidth();
            case WIDTH_HINT -> Math.max(0, wrapWidthHint);
            case CURRENT_WIDTH_OR_HINT -> {
                int currentWidth = resolveCurrentWrapWidth();
                yield currentWidth > 0 ? currentWidth : Math.max(0, wrapWidthHint);
            }
        };
    }

    private int resolveCurrentWrapWidth() {
        TerminalRectangle requested = getRequestedRegion();
        if (requested != null) {
            return Math.max(0, requested.getWidth() - getInsets().getHorizontal());
        }
        return Math.max(0, getWidth() - getInsets().getHorizontal());
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
        private int maxLines = 0;
        private WrappedHeightStrategy wrappedHeightStrategy = WrappedHeightStrategy.EXPLICIT_LINES_ONLY;
        private int wrapWidthHint = 0;
        private int x = 0, y = 0, width = 10, height = 1;
        
        public Builder name(String name) { this.name = name; return this; }
        public Builder text(String text) { this.text = text; return this; }
        public Builder style(TextStyle style) { this.style = style; return this; }
        public Builder alignment(TextAlignment align) { this.alignment = align; return this; }
        public Builder truncation(LabelTruncation trunc) { this.truncation = trunc; return this; }
        public Builder wordWrap(boolean wrap) { this.wordWrap = wrap; return this; }
        public Builder maxLines(int max)       { this.maxLines = max; return this; }
        public Builder wrappedHeightStrategy(WrappedHeightStrategy strategy) {
            this.wrappedHeightStrategy = strategy != null
                ? strategy
                : WrappedHeightStrategy.EXPLICIT_LINES_ONLY;
            return this;
        }
        public Builder wrapWidthHint(int hint) {
            this.wrapWidthHint = Math.max(0, hint);
            return this;
        }
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
            label.setMaxLines(maxLines);
            label.setWrappedHeightStrategy(wrappedHeightStrategy);
            label.setWrapWidthHint(wrapWidthHint);
            return label;
        }
    }
}
