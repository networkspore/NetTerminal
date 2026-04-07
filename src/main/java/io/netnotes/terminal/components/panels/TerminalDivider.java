package io.netnotes.terminal.components.panels;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.engine.ui.Orientation;
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
 *   <li>HORIZONTAL: height = 1 (FIT_CONTENT, driven by minHeight), width = FILL.
 *       The label collapses with "…" when constrained. Set width to FIT_CONTENT
 *       to use the label length as a minimum (wedge behaviour).
 *   <li>VERTICAL:   width  = 1 (FIT_CONTENT, driven by minWidth),  height = FILL.
 *       Set height to FIT_CONTENT for wedge behaviour.
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


    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        int measuredWidth = getWidthPreference() == SizePreference.FIT_CONTENT
            ? Math.max(getMinWidth(), measureIntrinsicWidth() + getInsets().getHorizontal())
            : getMinWidth();

        int measuredHeight = getHeightPreference() == SizePreference.FIT_CONTENT
            ? Math.max(getMinHeight(), measureIntrinsicHeight() + getInsets().getVertical())
            : getMinHeight();

        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, measuredWidth, measuredHeight);
        return measured;
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
            // Not enough room for label + flanking line segments.
            // Show as much of the label as fits, with a trailing ellipsis.
            if (width >= 2) {
                String clipped = displayed.substring(0, Math.max(0, width - 1)) + "…";
                printAt(batch, 0, 0, clipped, labelTextStyle);
            } else if (width == 1) {
                printAt(batch, 0, 0, "…", labelTextStyle);
            }
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

        // Label chars stacked vertically along the column.
        String displayed = label.trim();
        int labelLen = Math.min(displayed.length(), height - 2 * LABEL_PAD);

        if (labelLen <= 0) {
            // No room for any label chars — show an ellipsis char if there is at least 1 row.
            if (height >= 1) {
                drawVLine(batch, 0, 0, Math.max(0, height - 1), lineStyle, lineTextStyle);
                printAt(batch, 0, height - 1, "…", labelTextStyle);
            }
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

    private int measureIntrinsicWidth() {
        if (orientation == Orientation.VERTICAL) {
            return 1;
        }

        if (label == null || label.isEmpty()) {
            return 1;
        }

        return label.length() + 2 + (2 * LABEL_PAD);
    }

    private int measureIntrinsicHeight() {
        if (orientation == Orientation.HORIZONTAL) {
            return 1;
        }

        if (label == null || label.isEmpty()) {
            return 1;
        }

        return Math.max(1, label.trim().length() + (2 * LABEL_PAD));
    }

}