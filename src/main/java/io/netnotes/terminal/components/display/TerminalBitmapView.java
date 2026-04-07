package io.netnotes.terminal.components.display;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.components.display.TerminalBitmap.RenderMode;
import io.netnotes.terminal.components.display.TerminalBitmap.ScaleMode;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.engine.ui.SizePreference;

/**
 * TerminalBitmapView - Component for rendering a {@link TerminalBitmap} using
 * sub-character Unicode encodings.
 *
 * <p>At render time the component:
 * <ol>
 *   <li>Calculates the physical pixel canvas from its current character size
 *       multiplied by the sub-pixel factor of the chosen mode.
 *   <li>Calls {@link TerminalBitmap#scaleToTarget} to produce a scaled
 *       {@code byte[]} at exactly that canvas resolution.
 *   <li>Dispatches the appropriate inherited batch draw command:
 *       {@code drawBitmap}, {@code drawBrailleBitmap}, or {@code drawSextantBitmap}.
 * </ol>
 *
 * <p>The component is <em>read-only</em> with respect to the bitmap — it never
 * modifies the data model.  Callers mutate the {@link TerminalBitmap} and then
 * call {@link #invalidate()} (or {@link #setBitmap(TerminalBitmap)}) to
 * schedule a repaint.
 *
 * RENDERING MODES
 * <pre>
 *   QUADRANT  — 2×2 sub-pixels/cell, ▖▗▘▝▚▞▟▙▛▜ etc.   widest support
 *   BRAILLE   — 2×4 sub-pixels/cell, ⠿ etc.             good for graphs
 *   SEXTANT   — 2×3 sub-pixels/cell, 🬀…🬻 etc.          best resolution
 * </pre>
 *
 * USAGE:
 * <pre>
 *   // Sinewave plot
 *   TerminalBitmap bmp = new TerminalBitmap(128, 32);
 *   float[] wave = new float[128];
 *   for (int i = 0; i &lt; 128; i++) wave[i] = 0.5f + 0.5f * (float)Math.sin(i * 0.3);
 *   bmp.plotLine(wave);
 *
 *   TerminalBitmapView view = new TerminalBitmapView("wave", bmp);
 *   view.setRenderMode(RenderMode.BRAILLE);
 *   view.setScaleMode(ScaleMode.FIT);
 *   view.setStyle(TextStyle.NORMAL.withForeground(TextStyle.Color.CYAN));
 *
 *   // Logo from ASCII art, SEXTANT, fills parent width
 *   TerminalBitmap logo = TerminalBitmap.fromAsciiArt(LOGO_ROWS, '#');
 *   TerminalBitmapView logoView = new TerminalBitmapView("logo", logo);
 *   logoView.setRenderMode(RenderMode.SEXTANT);
 *   logoView.setScaleMode(ScaleMode.FIT);
 *   logoView.setBilinear(true);
 * </pre>
 *
 * SIZING:
 * <ul>
 *   <li>Default: width = FILL, height = FIT_CONTENT (auto-height from aspect ratio)
 *   <li>Override with {@link #setWidthPreference} / {@link #setHeightPreference}
 *       and {@link #setFixedAspectRatio} as needed.
 *   <li>Layout sizing is driven by {@link #measureContent(TerminalLayoutContext[])}.
 *       The preferred-size methods remain only as compatibility helpers for
 *       older callers.
 * </ul>
 */
public class TerminalBitmapView extends TerminalRegion {

    // ===== STATE =====

    private TerminalBitmap bitmap;
    private RenderMode     renderMode  = RenderMode.SEXTANT;
    private ScaleMode      scaleMode   = ScaleMode.FIT;
    private TextStyle      style       = TextStyle.NORMAL;
    private boolean        bilinear    = false;

    /**
     * When > 0, FIT_CONTENT height preserves this width:height ratio using the
     * currently known content width.
     */
    private float fixedAspectRatio = 0f;

    // ===== CONSTRUCTION =====

    /**
     * Create a view bound to {@code bitmap}.  The bitmap reference is mutable —
     * modify it and call {@link #invalidate()} to repaint.
     */
    public TerminalBitmapView(String name, TerminalBitmap bitmap) {
        super(name);
        this.bitmap = bitmap;
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FIT_CONTENT);
        setMinHeight(1);
        setMinWidth(1);
    }

    /** Convenience constructor — creates an empty 1×1 bitmap as a placeholder. */
    public TerminalBitmapView(String name) {
        this(name, new TerminalBitmap(1, 1));
    }

    // ===== BITMAP BINDING =====

    /** Replace the bitmap and schedule a repaint + layout update. */
    public void setBitmap(TerminalBitmap bitmap) {
        if (bitmap == null) return;
        this.bitmap = bitmap;
        requestLayoutUpdate();
        invalidate();
    }

    public TerminalBitmap getBitmap() { return bitmap; }

    // ===== CONFIGURATION =====

    /**
     * Set the sub-character rendering mode.
     * Triggers a layout update because the sub-pixel factor affects measured size.
     */
    public TerminalBitmapView setRenderMode(RenderMode mode) {
        if (mode != null && this.renderMode != mode) {
            this.renderMode = mode;
            requestLayoutUpdate();
            invalidate();
        }
        return this;
    }

    public TerminalBitmapView setScaleMode(ScaleMode mode) {
        if (mode != null && this.scaleMode != mode) {
            this.scaleMode = mode;
            invalidate();
        }
        return this;
    }

    /** Set the foreground / ink colour used for lit pixels. */
    public TerminalBitmapView setStyle(TextStyle style) {
        if (style != null) {
            this.style = style;
            invalidate();
        }
        return this;
    }

    /**
     * Use bilinear sampling when scaling the bitmap to the pixel canvas.
     * Produces smoother results for photographs or gradients;
     * nearest-neighbour (default) is better for pixel art.
     */
    public TerminalBitmapView setBilinear(boolean bilinear) {
        if (this.bilinear != bilinear) {
            this.bilinear = bilinear;
            invalidate();
        }
        return this;
    }

    /**
     * Fix the component height so that the bitmap's aspect ratio is preserved
     * given its current character width.  Set to 0 to disable.
     *
     * <p>Only meaningful when heightPreference is FIT_CONTENT.
     */
    public TerminalBitmapView setFixedAspectRatio(float ratio) {
        if (this.fixedAspectRatio != ratio) {
            this.fixedAspectRatio = Math.max(0f, ratio);
            requestLayoutUpdate();
        }
        return this;
    }

    /**
     * Convenience: derive the aspect ratio from the bitmap's own logical
     * dimensions, correcting for the non-square sub-pixel grid of the current
     * rendering mode.
     */
    public TerminalBitmapView setAspectRatioFromBitmap() {
        if (bitmap == null) return this;
        RenderMode m = renderMode.resolve();
        // Character cells are roughly 2:1 (width:height) in most terminals.
        // Sub-pixel factor adjusts for the encoding's sub-row count.
        float cellAspect  = 2.0f;                         // approx terminal cell w/h ratio
        float pixPerCharW = m.subCols;
        float pixPerCharH = m.subRows;
        // charH needed = (bmpH / pixPerCharH) rows
        // charW given  = (bmpW / pixPerCharW) cols
        // aspectRatio (charH/charW) = (bmpH * pixPerCharW) / (bmpW * pixPerCharH)
        float ratio = ((float)bitmap.getLogicalHeight() * pixPerCharW)
                    / ((float)bitmap.getLogicalWidth()  * pixPerCharH * cellAspect);
        return setFixedAspectRatio(Math.max(0.05f, ratio));
    }

    // ===== SIZING =====

    /**
     * Compatibility helper for callers that still need width-constrained
     * bitmap height outside the layout pre-pass.
     */
    public int getPreferredHeightForWidth(int width) {
        return resolveMeasuredHeightForWidth(width);
    }

    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        TerminalRectangle measured = getRegionPool().obtain();
        int measuredWidth = resolveMeasuredWidth();
        int measuredHeight = resolveMeasuredHeight(measuredWidth);

        measured.set(0, 0, measuredWidth, measuredHeight);
        return measured;
    }

    private int resolveMeasuredWidth() {
        return switch (getWidthPreference()) {
            case STATIC -> getRegion().getWidth();
            case FIT_CONTENT -> Math.max(
                getMinWidth(),
                measureBitmapContentWidth() + getInsets().getHorizontal()
            );
            default -> getMinWidth();
        };
    }

    private int resolveMeasuredHeight(int measuredOuterWidth) {
        return switch (getHeightPreference()) {
            case STATIC -> getRegion().getHeight();
            case FIT_CONTENT -> resolveMeasuredHeightForWidth(measuredOuterWidth);
            default -> getMinHeight();
        };
    }

    private int resolveMeasuredHeightForWidth(int outerWidth) {
        int contentWidth = Math.max(1, outerWidth - getInsets().getHorizontal());
        int contentHeight = measureBitmapContentHeight(contentWidth);
        return Math.max(getMinHeight(), contentHeight + getInsets().getVertical());
    }

    private int measureBitmapContentWidth() {
        if (bitmap == null) {
            return 1;
        }

        RenderMode mode = renderMode.resolve();
        return Math.max(1, (bitmap.getLogicalWidth() + mode.subCols - 1) / mode.subCols);
    }

    private int measureBitmapContentHeight(int contentWidth) {
        int resolvedWidth = Math.max(1, contentWidth);

        if (fixedAspectRatio > 0f) {
            return Math.max(1, Math.round(resolvedWidth * fixedAspectRatio));
        }

        if (bitmap == null) {
            return 1;
        }

        RenderMode mode = renderMode.resolve();
        return Math.max(1, (bitmap.getLogicalHeight() + mode.subRows - 1) / mode.subRows);
    }

    // ===== RENDERING =====

    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (bitmap == null) return;

        TerminalRectangle r = getRegion();
        if (r == null) return;

        int charW = r.getWidth();
        int charH = r.getHeight();
        if (charW <= 0 || charH <= 0) return;

        RenderMode effective = renderMode.resolve();

        // ── Physical pixel canvas size ───────────────────────────────────────
        int pixW = charW * effective.subCols;
        int pixH = charH * effective.subRows;

        // ── Scale the logical bitmap down to the physical canvas ─────────────
        byte[] scaled = bitmap.scaleToTarget(pixW, pixH, scaleMode, bilinear);
        if (scaled.length == 0) return;

        // ── Dispatch the appropriate inherited draw command ───────────────────
        switch (effective) {
            case BRAILLE:
                drawBrailleBitmap(batch, 0, 0, charW, charH, pixW, pixH, scaled, style);
                break;
            case SEXTANT:
                drawSextantBitmap(batch, 0, 0, charW, charH, pixW, pixH, scaled, style);
                break;
            case QUADRANT:
            default:
                drawBitmap(batch, 0, 0, charW, charH, pixW, pixH, scaled, style);
                break;
        }
    }

    // ===== HELPERS =====

    /** Shortcut: set the bitmap via ASCII art and invalidate. */
    public void setAsciiArt(String[] rows, char fillChar) {
        setBitmap(TerminalBitmap.fromAsciiArt(rows, fillChar));
    }

    /** Shortcut: set the bitmap from a boolean grid and invalidate. */
    public void setBooleanGrid(boolean[][] grid) {
        setBitmap(TerminalBitmap.fromBooleanGrid(grid));
    }

    /**
     * Shortcut: plot a normalised line series into the current bitmap and
     * invalidate.  This replaces whatever was in the bitmap previously.
     */
    public void plotLine(float[] values) {
        if (bitmap == null) return;
        bitmap.fill(false);
        bitmap.plotLine(values);
        invalidate();
    }

    /**
     * Shortcut: plot normalised bars into the current bitmap and invalidate.
     */
    public void plotBars(float[] values, int barWidthPx, int gapPx) {
        if (bitmap == null) return;
        bitmap.fill(false);
        bitmap.plotBars(values, barWidthPx, gapPx);
        invalidate();
    }
}
