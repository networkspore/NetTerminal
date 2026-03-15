package io.netnotes.terminal.components.panels;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.TextAlignment;

/**
 * TerminalDivider - A decorative horizontal or vertical separator line
 *
 * <p>Renders a single-row (HORIZONTAL) or single-column (VERTICAL) line using
 * box-drawing characters from a chosen {@link LineStyle}. An optional label
 * can be embedded in the line, aligned left, center, or right.
 *
 * <pre>
 *   Horizontal (no label):
 *     ────────────────────────────────
 *
 *   Horizontal (centered label):
 *     ──────── Installation ────────
 *
 *   Horizontal (left label):
 *     ─ Step 1 ────────────────────
 * </pre>
 *
 * SIZING:
 * <ul>
 *   <li>HORIZONTAL: height = 1 (FIT_CONTENT → getPreferredHeight() == 1), width = FILL
 *   <li>VERTICAL:   width  = 1 (FIT_CONTENT → getPreferredWidth()  == 1), height = FILL
 * </ul>
 *
 * USAGE:
 * <pre>
 *   TerminalDivider div = new TerminalDivider("section-sep", Orientation.HORIZONTAL);
 *   div.setLabel("Network Setup");
 *   div.setLabelAlignment(TextAlignment.CENTER);
 *   div.setLineStyle(LineStyle.DOUBLE);
 *   stack.addChild(div);
 * </pre>
 */
public class TerminalDivider extends TerminalRegion {

    // ===== TYPES =====

    public enum Orientation {
        HORIZONTAL,
        VERTICAL
    }

    // ===== CONSTANTS =====

    /** Padding cols/rows left and right of the embedded label */
    private static final int LABEL_PAD = 1;

    // ===== STATE =====

    private final Orientation orientation;
    private LineStyle  lineStyle      = LineStyle.SINGLE;
    private TextStyle  lineTextStyle  = TextStyle.NORMAL;
    private TextStyle  labelTextStyle = TextStyle.BOLD;

    private String        label          = null;
    private TextAlignment labelAlignment = TextAlignment.CENTER;

    // ===== CONSTRUCTION =====

    /**
     * Create a divider with the given orientation.
     *
     * @param name        component name (used for debugging)
     * @param orientation HORIZONTAL or VERTICAL
     */
    public TerminalDivider(String name, Orientation orientation) {
        super(name);
        this.orientation = orientation;
        if (orientation == Orientation.HORIZONTAL) {
            // Width fills parent; height is content-driven (always 1 row)
            setWidthPreference(SizePreference.FILL);
            setHeightPreference(SizePreference.FIT_CONTENT);
            setMinHeight(1);
        } else {
            // Width is content-driven (always 1 col); height fills parent
            setWidthPreference(SizePreference.FIT_CONTENT);
            setMinWidth(1);
            setHeightPreference(SizePreference.FILL);
        }
    }

    /** Convenience constructor for a horizontal divider. */
    public TerminalDivider(String name) {
        this(name, Orientation.HORIZONTAL);
    }

    // ===== SIZING =====

    /**
     * A horizontal divider is always exactly 1 row tall.
     * A vertical divider defers to the parent for height (FILL), so this
     * returns minHeight for the layout's minimum-size pass.
     */
    @Override
    public int getPreferredHeight() {
        if (orientation == Orientation.HORIZONTAL) {
            return 1;
        }
        // FILL — layout will assign height; report minimum so parent can plan
        return getMinHeight();
    }

    /**
     * A vertical divider is always exactly 1 column wide.
     * A horizontal divider defers to the parent for width (FILL).
     */
    @Override
    public int getPreferredWidth() {
        if (orientation == Orientation.VERTICAL) {
            return 1;
        }
        // FILL — report minimum so parent can plan
        return getMinWidth();
    }

    // ===== CONFIGURATION =====

    /** Set the box-drawing line style (SINGLE, DOUBLE, ROUNDED, THICK, ASCII…). */
    public TerminalDivider setLineStyle(LineStyle lineStyle) {
        if (lineStyle != null && this.lineStyle != lineStyle) {
            this.lineStyle = lineStyle;
            invalidate();
        }
        return this;
    }

    /** Set the {@link TextStyle} applied to the line characters themselves. */
    public TerminalDivider setLineTextStyle(TextStyle style) {
        if (style != null) {
            this.lineTextStyle = style;
            invalidate();
        }
        return this;
    }

    /**
     * Embed a text label inside the divider line.
     * Pass {@code null} to remove an existing label.
     */
    public TerminalDivider setLabel(String label) {
        this.label = label;
        invalidate();
        return this;
    }

    /** Set where the embedded label sits along the divider axis. */
    public TerminalDivider setLabelAlignment(TextAlignment alignment) {
        if (alignment != null) {
            this.labelAlignment = alignment;
            invalidate();
        }
        return this;
    }

    /** Set the {@link TextStyle} applied to the embedded label characters. */
    public TerminalDivider setLabelTextStyle(TextStyle style) {
        if (style != null) {
            this.labelTextStyle = style;
            invalidate();
        }
        return this;
    }

    // ===== GETTERS =====

    public Orientation   getOrientation()    { return orientation; }
    public LineStyle     getLineStyle()       { return lineStyle; }
    public TextStyle     getLineTextStyle()   { return lineTextStyle; }
    public String        getLabel()           { return label; }
    public TextAlignment getLabelAlignment()  { return labelAlignment; }
    public TextStyle     getLabelTextStyle()  { return labelTextStyle; }

    // ===== RENDERING =====

    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        TerminalRectangle r = getRegion();
        if (r == null) return;

        int w = r.getWidth();
        int h = r.getHeight();
        if (w <= 0 || h <= 0) return;

        if (orientation == Orientation.HORIZONTAL) {
            renderHorizontal(batch, w);
        } else {
            renderVertical(batch, h);
        }
    }

    // ----- horizontal -----

    private void renderHorizontal(TerminalBatchBuilder batch, int width) {
        if (label == null || label.isEmpty()) {
            drawHLine(batch, 0, 0, width, lineStyle, lineTextStyle);
            return;
        }

        // Label-inset line:  ──── label ────
        String displayed = " " + label + " ";
        int labelLen = displayed.length();

        if (labelLen + 2 * LABEL_PAD >= width) {
            // Not enough room — just render the label, no line
            printAt(batch, 0, 0, truncate(displayed, width), labelTextStyle);
            return;
        }

        int labelStart = computeLabelStart(width, labelLen, labelAlignment);
        int labelEnd   = labelStart + labelLen;

        // Left segment
        if (labelStart > 0) {
            drawHLine(batch, 0, 0, labelStart, lineStyle, lineTextStyle);
        }
        // Label
        printAt(batch, labelStart, 0, displayed, labelTextStyle);
        // Right segment
        int rightLen = width - labelEnd;
        if (rightLen > 0) {
            drawHLine(batch, labelEnd, 0, rightLen, lineStyle, lineTextStyle);
        }
    }

    // ----- vertical -----

    private void renderVertical(TerminalBatchBuilder batch, int height) {
        if (label == null || label.isEmpty()) {
            drawVLine(batch, 0, 0, height, lineStyle, lineTextStyle);
            return;
        }

        // Label chars stacked vertically along the column
        String displayed = label.trim();
        int labelLen = Math.min(displayed.length(), height - 2 * LABEL_PAD);
        if (labelLen <= 0) {
            drawVLine(batch, 0, 0, height, lineStyle, lineTextStyle);
            return;
        }

        int labelStart = computeLabelStart(height, labelLen, labelAlignment);
        int labelEnd   = labelStart + labelLen;

        // Top segment
        if (labelStart > 0) {
            drawVLine(batch, 0, 0, labelStart, lineStyle, lineTextStyle);
        }
        // Label characters, one per row
        for (int i = 0; i < labelLen; i++) {
            printAt(batch, 0, labelStart + i, String.valueOf(displayed.charAt(i)), labelTextStyle);
        }
        // Bottom segment
        int bottomLen = height - labelEnd;
        if (bottomLen > 0) {
            drawVLine(batch, 0, labelEnd, bottomLen, lineStyle, lineTextStyle);
        }
    }

    // ===== HELPERS =====

    private int computeLabelStart(int totalSpan, int labelLen, TextAlignment align) {
        switch (align) {
            case LEFT:   return LABEL_PAD;
            case RIGHT:  return Math.max(LABEL_PAD, totalSpan - labelLen - LABEL_PAD);
            case CENTER:
            default:     return Math.max(LABEL_PAD, (totalSpan - labelLen) / 2);
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}