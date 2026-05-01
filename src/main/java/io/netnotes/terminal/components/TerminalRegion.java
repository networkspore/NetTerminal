package io.netnotes.terminal.components;

import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.layout2d.FlexBasis;
import io.netnotes.engine.ui.layout2d.FlexGrow;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.components.panels.TerminalGroupRegion;
import io.netnotes.terminal.components.panels.TerminalPanel;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutCallback;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalSizeable;

/**
 * TerminalRegion — base region component with size preferences, insets, and dimensionality.
 *
 * EMPTY-BY-NATURE SEMANTIC
 * ───────────────────────
 * TerminalRegion is intentionally designed to be empty by nature. It has no built-in layout
 * capability and does not own a layout group. The class is provided for:
 *
 * 1. **Simple components** that only need size preferences and positioning (e.g., spacers)
 * 2. **Layout utilities** that operate on child dimensions without owning children
 *
 * Components that need to own children and orchestrate their layout should extend
 * {@link TerminalGroupRegion} or overide this class instead.
 *
 * ENFORCEMENT RULES
 * ─────────────────
 * 1. **No children allowed** — addChild() throws IllegalStateException if called directly
 *    on TerminalRegion. Child management is handled by TerminalGroupRegion subclasses.
 *
 * 2. **FIT_CONTENT not supported** — setWidthPreference() and setHeightPreference()
 *    throw IllegalStateException if FIT_CONTENT is set on TerminalRegion. Content-dependent
 *    sizing cannot be determined as no rendering or child layout exists.
 *
 * 3. **Default measureContent() provided** — base measurement supports STATIC,
 *    parent-dependent, and FIT_CONTENT sizing from child geometry. Subclasses
 *    can override for specialized measurement behavior.
 *
 * USAGE EXAMPLES
 * ──────────────
 *
 * // Correct: Spacer component that only needs size preferences
 * public class MySpacer extends TerminalRegion {
 *     public MySpacer(String name, int width, int height) {
 *         super(name, 0, 0, width, height);
 *     }
 * }
 *
 * // Correct: Extending for child ownership
 * public class MyPanel extends TerminalGroupRegion {
 *     public MyPanel(String name) {
 *         super(name);
 *     }
 *     // Now can addChild(), use FIT_CONTENT, implement measureContent()
 * }
 */
public class TerminalRegion extends TerminalRenderable implements TerminalSizeable {
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_W = 2;
    public static final int AXIS_H = 3;

    protected final TerminalInsets insets;
    private SizePreference widthPreference  = SizePreference.STATIC;
    private SizePreference heightPreference = SizePreference.STATIC;
    private float percentWidth  = 0f;
    private float percentHeight = 0f;

    // Layout2D fields — primary source of truth for layout containers
    // Mapped from SizePreference during migration (Tier 1)
    private FlexGrow widthGrow  = FlexGrow.NONE;
    private FlexGrow heightGrow = FlexGrow.NONE;
    private FlexBasis widthBasis  = FlexBasis.auto();
    private FlexBasis heightBasis = FlexBasis.auto();
    private int minWidth  = 0;
    private int minHeight = 0;
    private int maxWidth  = Integer.MAX_VALUE;
    private int maxHeight = Integer.MAX_VALUE;

    public TerminalRegion(String regionName) {
        super(regionName);
        insets = new TerminalInsets();
        insets.setOnChanged(this::handleInsetsChanged);
    }

    public TerminalRegion(String regionName, int x, int y, int width, int height) {
        this(regionName);
        setRegion(x, y, width, height);
    }

    private void handleInsetsChanged(TerminalInsets insets) {
        onInsetsChanged(insets);
        requestLayoutUpdate();
    }

    protected void onInsetsChanged(TerminalInsets insets) {}

    // ── Min size ──────────────────────────────────────────────────────────────
    public void setMinSize(int minWidth, int minHeight) {
        this.minWidth = Math.max(0, minWidth);
        this.minHeight = Math.max(0, minHeight);
        requestLayoutUpdate();
    }

    public void setMinWidth(int minWidth) {
        this.minWidth = Math.max(0, minWidth);
        requestLayoutUpdate();
    }

    public void setMinHeight(int minHeight) {
        this.minHeight = Math.max(0, minHeight);
        requestLayoutUpdate();
    }

    @Override public int getMinWidth()  { return minWidth; }
    @Override public int getMinHeight() { return minHeight; }

    @Override
    public int getMinSize(int axis) {
        return switch (axis) {
            case AXIS_W -> getMinWidth();
            case AXIS_H -> getMinHeight();
            default -> throw new IllegalArgumentException(
                "getMinSize TerminalRegion does not have axis: " + axis);
        };
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = Math.max(0, maxWidth);
        requestLayoutUpdate();
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = Math.max(0, maxHeight);
        requestLayoutUpdate();
    }

    public int getMaxWidth()  { return maxWidth; }
    public int getMaxHeight() { return maxHeight; }

    // ── SizePreference ────────────────────────────────────────────────────────


    @Override
    public SizePreference getSizePreference(int axis) {
        return switch (axis) {
            case AXIS_W -> widthPreference;
            case AXIS_H -> heightPreference;
            default -> throw new IllegalArgumentException(
                "getSizePreference TerminalRegion does not have axis: " + axis);
        };
    }

    // ── Layout2D ──────────────────────────────────────────────────────────────

    /**
     * Get the flex-grow for the width axis.
     * @return The FlexGrow for width
     */
    public FlexGrow getWidthGrow()  { return widthGrow; }

    /**
     * Get the flex-grow for the height axis.
     * @return The FlexGrow for height
     */
    public FlexGrow getHeightGrow() { return heightGrow; }

    /**
     * Get the flex-basis for the width axis.
     * @return The FlexBasis for width
     */
    public FlexBasis getWidthBasis()  { return widthBasis; }

    /**
     * Get the flex-basis for the height axis.
     * @return The FlexBasis for height
     */
    public FlexBasis getHeightBasis() { return heightBasis; }

    /**
     * Set flex-grow for width axis.
     * @param widthGrow The FlexGrow value
     */
    public void setWidthGrow(FlexGrow widthGrow) {
        this.widthGrow = widthGrow != null ? widthGrow : FlexGrow.NONE;
        requestLayoutUpdate();
    }

    /**
     * Set flex-grow for height axis.
     * @param heightGrow The FlexGrow value
     */
    public void setHeightGrow(FlexGrow heightGrow) {
        this.heightGrow = heightGrow != null ? heightGrow : FlexGrow.NONE;
        requestLayoutUpdate();
    }

    /**
     * Set flex-basis for width axis.
     * @param widthBasis The FlexBasis value
     */
    public void setWidthBasis(FlexBasis widthBasis) {
        this.widthBasis = widthBasis != null ? widthBasis : FlexBasis.auto();
        requestLayoutUpdate();
    }

    /**
     * Set flex-basis for height axis.
     * @param heightBasis The FlexBasis value
     */
    public void setHeightBasis(FlexBasis heightBasis) {
        this.heightBasis = heightBasis != null ? heightBasis : FlexBasis.auto();
        requestLayoutUpdate();
    }

    /**
     * Get the Layout2D flex-grow for the given axis.
     * @param axis AXIS_W or AXIS_H
     * @return The FlexGrow for the axis
     */
    public FlexGrow getWidthGrow(int axis) {
        return switch (axis) {
            case AXIS_W -> widthGrow;
            case AXIS_H -> heightGrow;
            default -> throw new IllegalArgumentException(
                "getWidthGrow TerminalRegion does not have axis: " + axis);
        };
    }

    /**
     * Get the Layout2D flex-basis for the given axis.
     * @param axis AXIS_W or AXIS_H
     * @return The FlexBasis for the axis
     */
    public FlexBasis getWidthBasis(int axis) {
        return switch (axis) {
            case AXIS_W -> widthBasis;
            case AXIS_H -> heightBasis;
            default -> throw new IllegalArgumentException(
                "getWidthBasis TerminalRegion does not have axis: " + axis);
        };
    }

    // ── Deprecated SizePreference bridge ──────────────────────────────────────
    //
    // Migration guide (SizePreference → Layout2D):
    //   FILL        → FlexGrow.FULL + FlexBasis.auto()
    //   PERCENT     → FlexGrow.NONE  + FlexBasis.percent(...)
    //   INHERIT     → copied from parent's Layout2D enum at runtime
    //   FIT_CONTENT → FlexGrow.NONE  + FlexBasis.content()
    //   STATIC      → FlexGrow.NONE  + FlexBasis.pixels(...)
    //
    // These methods keep the old API working while Layout2D is the primary
    // source of truth. New code should use getWidthGrow()/setWidthBasis().

    /**
     * @deprecated Use {@link #getWidthGrow()} or {@link #setWidthGrow(FlexGrow)} instead.
     *   Migration: FILL → FlexGrow.FULL, PERCENT → FlexBasis.percent(),
     *   FIT_CONTENT → FlexBasis.content(), STATIC → FlexBasis.pixels()
     */
    @Deprecated
    public void setWidthPreference(SizePreference widthPreference) {
        this.widthPreference = widthPreference;
        applyWidthPreference(widthPreference);
    }

    /**
     * @deprecated Use {@link #getHeightGrow()} or {@link #setHeightGrow(FlexGrow)} instead.
     *   Migration: FILL → FlexGrow.FULL, PERCENT → FlexBasis.percent(),
     *   FIT_CONTENT → FlexBasis.content(), STATIC → FlexBasis.pixels()
     */
    @Deprecated
    public void setHeightPreference(SizePreference heightPreference) {
        this.heightPreference = heightPreference;
        applyHeightPreference(heightPreference);
    }

    /**
     * @deprecated Use {@link #getWidthGrow()} or {@link #getWidthBasis()} instead.
     */
    @Deprecated
    public SizePreference getWidthPreference() { return widthPreference; }

    /**
     * @deprecated Use {@link #getHeightGrow()} or {@link #getHeightBasis()} instead.
     */
    @Deprecated
    public SizePreference getHeightPreference() { return heightPreference; }

    /**
     * Apply a SizePreference value to the Layout2D fields.
     * Used by the deprecated setters to keep Layout2D in sync.
     */
    private void applyWidthPreference(SizePreference pref) {
        applyWidthPreference(pref, percentWidth);
    }

    private void applyHeightPreference(SizePreference pref) {
        applyHeightPreference(pref, percentHeight);
    }

    private void applyWidthPreference(SizePreference pref, float percent) {
        switch (pref) {
            case FILL:
                this.widthGrow = FlexGrow.FULL;
                this.widthBasis = FlexBasis.auto();
                break;
            case PERCENT:
                this.widthGrow = FlexGrow.NONE;
                this.widthBasis = FlexBasis.percent(percent);
                break;
            case FIT_CONTENT:
                this.widthGrow = FlexGrow.NONE;
                this.widthBasis = FlexBasis.content();
                break;
            case STATIC:
                this.widthGrow = FlexGrow.NONE;
                this.widthBasis = FlexBasis.pixels(getMinWidth());
                break;
            case INHERIT:
                this.widthGrow = FlexGrow.NONE;
                this.widthBasis = FlexBasis.auto();
                break;
        }
    }

    private void applyHeightPreference(SizePreference pref, float percent) {
        switch (pref) {
            case FILL:
                this.heightGrow = FlexGrow.FULL;
                this.heightBasis = FlexBasis.auto();
                break;
            case PERCENT:
                this.heightGrow = FlexGrow.NONE;
                this.heightBasis = FlexBasis.percent(percent);
                break;
            case FIT_CONTENT:
                this.heightGrow = FlexGrow.NONE;
                this.heightBasis = FlexBasis.content();
                break;
            case STATIC:
                this.heightGrow = FlexGrow.NONE;
                this.heightBasis = FlexBasis.pixels(getMinHeight());
                break;
            case INHERIT:
                // INHERIT copies from parent at layout time — use NONE as default
                this.heightGrow = FlexGrow.NONE;
                this.heightBasis = FlexBasis.auto();
                break;
        }
    }

    protected FlexGrow growFor(SizePreference pref) {
        return switch (pref) {
            case FILL -> FlexGrow.FULL;
            default -> FlexGrow.NONE;
        };
    }




    public static FlexBasis basisFor(SizePreference pref, double percent, double fallback) {
        return switch (pref) {
            case INHERIT -> FlexBasis.auto(); // will be set from parent at layout time
            case PERCENT -> FlexBasis.percent(percent);
            case FIT_CONTENT -> FlexBasis.content();
            default -> FlexBasis.auto();
        };
    }

    public static FlexBasis basisFor(SizePreference pref, TerminalPanel parent) {
        return switch (pref) {
            case INHERIT -> parent.getWidthBasis();
            case FIT_CONTENT -> FlexBasis.content();
            default -> FlexBasis.auto();
        };
    }

    @Override public TerminalInsets getInsets() { return insets; }

    public void setInsets(int all) { insets.setAll(all); }

    public void setInsets(TerminalInsets padding) {

        if (padding == null) {
            if (!insets.isZero()) insets.clear();
            return;
        }

        if (!insets.equals(padding)) insets.copyFrom(padding);
    }

    @Override public void addChild(TerminalRenderable renderable, TerminalLayoutCallback callback){
         if (this.getClass() == TerminalRegion.class) {
            throw new IllegalStateException("Cannot add a child to TerminalRegion");
        }
        super.addChild(renderable, callback);
    }


    // ── Percent dimensions ────────────────────────────────────────────────────

    @Override public double getPercentWidth()  { return percentWidth; }
    @Override public double getPercentHeight() { return percentHeight; }

    @Override
    public void setPercentWidth(double percent) {
        this.percentWidth = (float) percent;
        requestLayoutUpdate();
    }

    @Override
    public void setPercentHeight(double percent) {
        this.percentHeight = (float) percent;
        requestLayoutUpdate();
    }

    public double getPercent(int axis) {
        // We only have getPercentWidth/Height, so we map axis
        return axis == TerminalRegion.AXIS_W ? getPercentWidth() : getPercentHeight();
    }

    // ── Spatial-axis classification ───────────────────────────────────────────
    //
    // Do NOT override isSizedByContent() or isSizedByParent() here — the base
    // class computes them from these four methods.

    @Override public int     getNumSpatialAxes()      { return 4; }
    @Override public boolean isPositionAxis(int axis) { return axis == AXIS_X || axis == AXIS_Y; }

    @Override
    public boolean isAxisParentDependent(int axis) {
        return switch (axis) {
            case AXIS_W -> widthGrow.isNonZero() || widthBasis.isPercent();
            case AXIS_H -> heightGrow.isNonZero() || heightBasis.isPercent();
            default     -> false;
        };
    }

    @Override
    public boolean isAxisContentDependent(int axis) {
        return switch (axis) {
            case AXIS_W -> widthBasis.isContent();
            case AXIS_H -> heightBasis.isContent();
            default     -> false;
        };
    }

    // ── Content measurement (layout-pass) ────────────────────────────────────
    //
    // Called by the layout manager during the bottom-up content pre-pass.
    // childContexts[i] corresponds to getChildren().get(i). A null slot means
    // the child is not content-sized or not dirty this pass — fall back to its
    // committed geometry.
    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        // TerminalRegion is empty-by-nature — measure based on Layout2D preferences.
        return new TerminalRectangle(0, 0,
            isAxisParentDependent(AXIS_W) ? getMinWidth()
                : clampDimension(this, getRequestedRegion() != null
                    ? getRequestedRegion().getWidth()
                    : getWidth(), true),
            isAxisParentDependent(AXIS_H) ? getMinHeight()
                : clampDimension(this, getRequestedRegion() != null
                    ? getRequestedRegion().getHeight()
                    : getHeight(), true));
    }

   

    public static int clampDimension( TerminalRegion region, int value, boolean isWidth){
        return Math.min(
            Math.max(
                value, 
                isWidth 
                    ? region.getMinWidth() 
                    : region.getMinHeight()
            ), 
            isWidth 
                ? region.getMaxWidth() 
                : region.getMaxHeight()
        );
    }

    // ── Common measurement helpers for subclasses ─────────────────────────────
    @Override
    protected TerminalRenderable[] createRenderableArray(int size) {
        return new TerminalRenderable[size];
    }

  
    /**
     * Reads a child's REGION dimension for the {@code layoutAllChildren()} path.
     *
     * When the parent is allocating a region to each child:
     * A FIT_CONTENT child uses its measured content bounds as the allocation hint.
     * A STATIC child uses its requested or current region.
     *
     * @param child   The child renderable being laid out
     * @param ctx     The layout context for the child
     * @param isWidth true to read width, false to read height
     * @return The region dimension to allocate to the child
     */
    protected static int readContentDimension(TerminalRegion child, TerminalLayoutContext ctx, boolean isWidth) {
        TerminalRectangle bounds = ctx != null ? ctx.getMeasuredContentBounds() : null;

        if (bounds != null) {
            return clampDimension(child, isWidth ? bounds.getWidth() : bounds.getHeight(), isWidth);
        }

        TerminalRectangle requested = child.getRequestedRegion();
        if (requested != null) {
            return clampDimension(child, isWidth ? requested.getWidth() : requested.getHeight(), isWidth);
        }

        TerminalRectangle region = child.getRegion();
        return clampDimension(child, isWidth ? region.getWidth() : region.getHeight(), isWidth);
    }


    public static TerminalRegion checkTerminalRegion(TerminalRenderable  renderable){
        return checkTerminalRegion(renderable, "Component child must inherits form TerminalRegion");
    }
    public static TerminalRegion checkTerminalRegion(TerminalRenderable renderable, String errorMsg){
        if(renderable instanceof TerminalRegion tr){
            return tr;
        }
        throw new IllegalStateException(errorMsg);  
    }

    /**
     * Specifically tests for the isForcedHiddenDesired flag
     * returns false if the child is force hidden
     * (null safe)
     */
    protected boolean canUnhide(TerminalRenderable child) {
        return child != null && !child.isHiddenForced();
    }


}
