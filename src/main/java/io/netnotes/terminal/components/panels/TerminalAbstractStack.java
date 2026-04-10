package io.netnotes.terminal.components.panels;

import java.util.Arrays;

import io.netnotes.engine.ui.LayoutOverflowStrategy;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutCallback;
import io.netnotes.terminal.layout.TerminalLayoutGroupCallback;
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
public abstract class TerminalAbstractStack extends TerminalRegion {

    // ── alignment enums ───────────────────────────────────────────────────────

    public enum VAlignment { TOP, CENTER, BOTTOM }
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

    protected int                    spacing            = 0;
    protected LayoutOverflowStrategy overflowStrategy   = LayoutOverflowStrategy.CLIP;


    protected VAlignment vAlignment;
    protected HAlignment hAlignment;

    // ── layout group identity ─────────────────────────────────────────────────

    protected final String layoutGroupId;
    protected final String layoutCallbackId;

    /**
     * Held so subclasses can expose it via a getter if callers need to
     * de-register or inspect the callback reference.
     */
    protected TerminalLayoutGroupCallback layoutCallback = null;

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
        super(name);
        this.layoutGroupId          = groupPrefix + "-" + getName();
        this.layoutCallbackId       = groupPrefix + "-default";
        this.setWidthPreference(defaultWidth);
        this.setHeightPreference(defaultHeight);
        this.vAlignment              = defaultVAlign;
        this.hAlignment              = defaultHAlign;

        // Wire the padding callback so any mutation triggers inset recalc + layout.
        this.padding.setOnChanged(this::onPaddingChanged);
    }

    // =========================================================================
    // ABSTRACT CONTRACT — subclasses must implement
    // =========================================================================

    /**
     * Register the layout group callback with the rendering system.
     * Must be called at the very end of the concrete subclass constructor,
     * after all fields are initialised and the super() chain has completed.
     *
     * Example:
     * <pre>
     *   {@literal @}Override
     *   protected void initLayoutCallback() {
     *       this.layoutCallback = this::layoutAllChildren;
     *       registerChildGroupCallback(layoutGroupId, layoutCallback);
     *   }
     * </pre>
     */
    protected abstract void initLayoutCallback();

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
            requestLayoutUpdate();
        }
    }

    public LayoutOverflowStrategy getOverflowStrategy() { return overflowStrategy; }

    // ── alignment ─────────────────────────────────────────────────────────────

    public void setVAlignment(VAlignment vAlignment) {
        if (this.vAlignment != vAlignment && vAlignment != null) {
            this.vAlignment = vAlignment;
            requestLayoutUpdate();
        }
    }

    public VAlignment getVAlignment() { return vAlignment; }

    public void setHAlignment(HAlignment hAlignment) {
        if (this.hAlignment != hAlignment && hAlignment != null) {
            this.hAlignment = hAlignment;
            requestLayoutUpdate();
        }
    }

    public HAlignment getHAlignment() { return hAlignment; }


    // =========================================================================
    // CHILD MANAGEMENT
    // =========================================================================

    @Override
    public void addChild(TerminalRenderable child) {
        addChild(child, null);
    }

    @Override
    public void addChild(TerminalRenderable child, TerminalLayoutCallback cb) {
        super.addChild(child, null);
        addToLayoutGroup(child, layoutGroupId);
    }

    @Override protected void onLayoutManagerSet(boolean hasLayoutManager) {
        // If the layout manager is being removed, de-register the callback to avoid orphaned references.
        if (!hasLayoutManager && layoutCallback != null) {
            destroyLayoutGroup(layoutGroupId);
            layoutCallback = null;
        }
        
    }

    // =========================================================================
    // SHARED LAYOUT HELPERS
    // =========================================================================

    /**
     * Returns true if {@code child} should participate in the layout pass.
     * Layout-excluded children are skipped entirely (no position assigned).
     */
    protected boolean shouldIncludeInLayout(TerminalRenderable child) {
        return !child.isLayoutExcluded();
    }

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

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Override
    protected void onDestroying() {
        destroyLayoutGroup(layoutGroupId);
    }
}
