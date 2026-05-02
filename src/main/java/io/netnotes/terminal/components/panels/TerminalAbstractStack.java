package io.netnotes.terminal.components.panels;

import java.util.Arrays;

import io.netnotes.engine.ui.LayoutOverflowStrategy;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.layout2d.AlignContent;
import io.netnotes.engine.ui.layout2d.AlignSelf;
import io.netnotes.engine.ui.layout2d.FlexBasis;
import io.netnotes.engine.ui.layout2d.FlexDirection;
import io.netnotes.engine.ui.layout2d.FlexGrow;
import io.netnotes.engine.ui.layout2d.FlexShrink;
import io.netnotes.engine.ui.layout2d.FlexWrap;
import io.netnotes.engine.ui.layout2d.Overflow;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalSizeable;

/**
 * TerminalAbstractStack — shared base for TerminalHStack and TerminalVStack.
 *
 * Owns all fields and helpers that are axis-independent:
 *   - padding / border-enforced insets
 *   - drawBorder / drawSeparators / border visual style
 *   - spacing, overflowStrategy
 *   - defaultWidthPreference / defaultHeightPreference
 *   - vAlignment / hAlignment
 *   - layoutGroupId / layoutCallbackId / layoutCallback reference
 *
 * Subclasses supply:
 *   - Their own axis-specific layout pass (layoutAllChildren)
 *   - initLayoutCallback() — called at the END of the subclass constructor
 *     to register the layout group once all fields are initialised.
 *   - getPreferredWidth() / getPreferredHeight() — axis-aware sizing.
 *   - renderSelf() — axis-appropriate border drawing.
 */
public abstract class TerminalAbstractStack extends TerminalGroupRegion {

    // ── alignment enums (deprecated — use Layout2D AlignSelf/FlexDirection) ──

    @Deprecated(since = "0.12.0", forRemoval = true)
    public enum VAlignment { TOP, CENTER, BOTTOM }
    @Deprecated(since = "0.12.0", forRemoval = true)
    public enum HAlignment { LEFT, CENTER, RIGHT }

    // ── padding / border insets ───────────────────────────────────────────────

    /**
     * Raw padding set by the caller. Always read via getInsets() so that
     * border enforcement is applied automatically when drawBorder=true.
     */
    protected final TerminalInsets padding = new TerminalInsets();

    /**
     * Cached border-enforced insets: each side clamped to at least 1 so that
     * box-drawing characters always have room to render.
     * Recomputed by updateBorderInsets() whenever padding or drawBorder changes.
     */
    protected final TerminalInsets borderInsets = new TerminalInsets();

    // ── border / separator state ──────────────────────────────────────────────

    protected boolean   drawBorder     = false;
    protected boolean   drawSeparators = false;
    protected LineStyle borderStyle    = LineStyle.SINGLE;
    protected TextStyle borderTextStyle = TextStyle.NORMAL;

    // ── layout state ─────────────────────────────────────────────────────────

    protected int             spacing            = 0;
    protected Overflow        overflowStrategy   = Overflow.HIDDEN;
    protected FlexDirection   direction          = FlexDirection.ROW;
    protected AlignSelf       vAlignment         = AlignSelf.AUTO;
    protected AlignSelf       hAlignment         = AlignSelf.AUTO;
    protected FlexWrap        wrap               = FlexWrap.NOWRAP;
    protected AlignContent    alignItems         = AlignContent.FLEX_START;
    protected FlexGrow        defaultWidthGrow   = FlexGrow.NONE;
    protected FlexShrink      defaultWidthShrink = FlexShrink.NONE;
    protected FlexBasis       defaultWidthBasis  = FlexBasis.CONTENT;
    protected FlexGrow        defaultHeightGrow  = FlexGrow.NONE;
    protected FlexShrink      defaultHeightShrink = FlexShrink.NONE;
    protected FlexBasis       defaultHeightBasis = FlexBasis.CONTENT;

    // ── layout group identity ─────────────────────────────────────────────────


    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    /**
     * @param name               component name (passed to TerminalRegion)
     * @param groupPrefix        prefix for layoutGroupId, e.g. "hstack" or "vstack"
     * @param defaultWidth       default child-width preference
     * @param defaultHeight      default child-height preference
     * @param defaultVAlign      vertical alignment default
     * @param defaultHAlign      horizontal alignment default
     */
    protected TerminalAbstractStack(
        String name,
        String groupPrefix,
        SizePreference defaultWidth,
        SizePreference defaultHeight,
        VAlignment defaultVAlign,
        HAlignment defaultHAlign
    ) {
        super(name, groupPrefix);

        this.setWidthPreference(defaultWidth);
        this.setHeightPreference(defaultHeight);
        this.vAlignment              = defaultVAlign;
        this.hAlignment              = defaultHAlign;

        // Wire the padding callback so any mutation triggers inset recalc + layout.
        this.padding.setOnChanged(this::onPaddingChanged);
        syncOverflowClipPolicy();
    }

    // =========================================================================
    // INSETS — padding change hook and border enforcement
    // =========================================================================

    /**
     * Called by the TerminalInsets onChange hook whenever any padding value is
     * mutated. Keeps borderInsets in sync and triggers a layout pass.
     */
    private void onPaddingChanged(TerminalInsets p) {
        updateBorderInsets();
        requestLayoutUpdate();
    }

    /**
     * Recomputes the cached borderInsets from the current padding, clamping each
     * side to at least 1. Must be called whenever drawBorder or padding changes.
     */
    protected void updateBorderInsets() {
        borderInsets.set(
            Math.max(1, padding.getTop()),
            Math.max(1, padding.getRight()),
            Math.max(1, padding.getBottom()),
            Math.max(1, padding.getLeft())
        );
    }

    /**
     * Returns the effective insets for layout calculations.
     * When drawBorder=true each side is at least 1 (border-enforced, cached).
     * When drawBorder=false the raw padding is returned.
     */
    @Override
    public TerminalInsets getInsets() {
        return drawBorder ? borderInsets : padding;
    }

    // =========================================================================
    // CONFIGURATION — padding
    // =========================================================================

    /**
     * Sets uniform padding on all sides. Negative values are clamped to 0.
     * The onChange hook on {@code padding} fires automatically, triggering
     * borderInsets recalc and requestLayoutUpdate().
     */
    public void setPadding(int value) {
        int clamped = Math.max(0, value);
        if (padding.getTop()    != clamped ||
            padding.getRight()  != clamped ||
            padding.getBottom() != clamped ||
            padding.getLeft()   != clamped) {
            padding.setAll(clamped);   // fires onPaddingChanged
        }
    }

    /**
     * Copies {@code newInsets} into the padding field.
     * Passing {@code null} clears all padding to 0.
     */
    public void setInsets(TerminalInsets newInsets) {
        if (newInsets == null) {
            if (!padding.isZero()) {
                padding.clear();       // fires onPaddingChanged
            }
            return;
        }
        if (!padding.equals(newInsets)) {
            padding.copyFrom(newInsets); // fires onPaddingChanged
        }
    }

    // =========================================================================
    // CONFIGURATION — border / separators
    // =========================================================================

    public void setDrawBorder(boolean drawBorder) {
        if (this.drawBorder != drawBorder) {
            this.drawBorder = drawBorder;
            updateBorderInsets();   // insets change affects layout
            requestLayoutUpdate();
            invalidate();           // visual change — box-drawing chars appear/disappear
        }
    }

    public boolean isDrawBorder() { return drawBorder; }

    public void setDrawSeparators(boolean drawSeparators) {
        if (this.drawSeparators != drawSeparators) {
            this.drawSeparators = drawSeparators;
            requestLayoutUpdate();  // separator columns/rows occupy layout space
            invalidate();           // separators may render even without borders
        }
    }

    public boolean isDrawSeparators() { return drawSeparators; }

    public void setBorderStyle(LineStyle style) {
        if (style != null && this.borderStyle != style) {
            this.borderStyle = style;
            invalidate();
        }
    }

    public LineStyle getBorderStyle() { return borderStyle; }

    public void setBorderTextStyle(TextStyle style) {
        if (style != null && !this.borderTextStyle.equals(style)) {
            this.borderTextStyle = style;
            invalidate();
        }
    }

    public TextStyle getBorderTextStyle() { return borderTextStyle; }

    // =========================================================================
    // CONFIGURATION — layout
    // =========================================================================

    public void setSpacing(int spacing) {
        if (this.spacing != spacing) {
            this.spacing = Math.max(0, spacing);
            requestLayoutUpdate();
        }
    }

    public int getSpacing() { return spacing; }

    public void setOverflowStrategy(LayoutOverflowStrategy strategy) {
        if (strategy != null && this.overflowStrategy != strategy) {
            this.overflowStrategy = strategy;
            syncOverflowClipPolicy();
            requestLayoutUpdate();
        }
    }

    public LayoutOverflowStrategy getOverflowStrategy() { return overflowStrategy.toLayoutOverflowStrategy(); }

    protected void syncOverflowClipPolicy() {
        setOverflowClipPolicy(
            overflowStrategy == Overflow.VISIBLE
                ? TerminalRenderable.OverflowClipPolicy.INHERIT_PARENT_CLIP
                : TerminalRenderable.OverflowClipPolicy.CLIP_TO_SELF_BOUNDS
        );
    }

   // ── alignment (deprecated wrappers) ─────────────────────────────────────

    @Deprecated(since = "0.12.0", forRemoval = true)
    public void setVAlignment(VAlignment vAlignment) {
        if (vAlignment != null) {
            this.vAlignment = switch (vAlignment) {
                case TOP -> AlignSelf.FLEX_START;
                case CENTER -> AlignSelf.CENTER;
                case BOTTOM -> AlignSelf.FLEX_END;
            };
            requestLayoutUpdate();
        }
    }

    @Deprecated(since = "0.12.0", forRemoval = true)
    public VAlignment getVAlignment() {
        return switch (vAlignment) {
            case FLEX_START -> VAlignment.TOP;
            case CENTER -> VAlignment.CENTER;
            case FLEX_END -> VAlignment.BOTTOM;
            default -> VAlignment.TOP;
        };
    }

    @Deprecated(since = "0.12.0", forRemoval = true)
    public void setHAlignment(HAlignment hAlignment) {
        if (hAlignment != null) {
            this.hAlignment = switch (hAlignment) {
                case LEFT -> AlignSelf.FLEX_START;
                case CENTER -> AlignSelf.CENTER;
                case RIGHT -> AlignSelf.FLEX_END;
            };
            requestLayoutUpdate();
        }
    }

    @Deprecated(since = "0.12.0", forRemoval = true)
    public HAlignment getHAlignment() {
        return switch (hAlignment) {
            case FLEX_START -> HAlignment.LEFT;
            case CENTER -> HAlignment.CENTER;
            case FLEX_END -> HAlignment.RIGHT;
            default -> HAlignment.LEFT;
        };
    }


    // =========================================================================
    // SHARED LAYOUT HELPERS
    // =========================================================================

    /**
     * Returns true if the stack should manage the hidden flag of {@code child}.
     * Non-TerminalSizeable children are always managed.
     * TerminalSizeable children are managed only when isHiddenManaged() is true.
     */
    protected boolean shouldManageHidden(TerminalRenderable child) {
        return !(child instanceof TerminalSizeable s) || s.isHiddenManaged();
    }

    /**
     * Returns true when the child's position and size fit entirely within the
     * parent region (all coordinates non-negative, no edge overrun).
     */
    protected boolean isWithinParentBounds(int x, int y, int w, int h,
            TerminalRectangle parent) {
        return x >= 0 && y >= 0
            && x + w <= parent.getWidth()
            && y + h <= parent.getHeight();
    }

    /**
     * Appends {@code value} to {@code arr}, returning the new array.
     * Used to grow junction-position arrays during the layout pass.
     */
    protected static int[] appendInt(int[] arr, int value) {
        int[] next = Arrays.copyOf(arr, arr.length + 1);
        next[arr.length] = value;
        return next;
    }
}
