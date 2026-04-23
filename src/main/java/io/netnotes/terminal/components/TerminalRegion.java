package io.netnotes.terminal.components;

import io.netnotes.engine.ui.SizePreference;

import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalSizeable;

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
    private int minWidth  = 0;
    private int minHeight = 0;
    private boolean isHiddenManaged = true;

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

    // ── SizePreference ────────────────────────────────────────────────────────

    @Override public SizePreference getWidthPreference()  { return widthPreference; }
    @Override public SizePreference getHeightPreference() { return heightPreference; }

    public void setWidthPreference(SizePreference widthPreference) {
        this.widthPreference = widthPreference != null ? widthPreference : SizePreference.STATIC;
        requestLayoutUpdate();
    }

    public void setHeightPreference(SizePreference heightPreference) {
        this.heightPreference = heightPreference != null ? heightPreference : SizePreference.STATIC;
        requestLayoutUpdate();
    }

    @Override
    public SizePreference getSizePreference(int axis) {
        return switch (axis) {
            case AXIS_W -> widthPreference;
            case AXIS_H -> heightPreference;
            default -> throw new IllegalArgumentException(
                "getSizePreference TerminalRegion does not have axis: " + axis);
        };
    }

    // ── Hidden-managed ────────────────────────────────────────────────────────

    @Override public boolean isHiddenManaged() { return isHiddenManaged; }

    public void setIsHiddenManaged(boolean isHiddenManaged) {
        if (this.isHiddenManaged != isHiddenManaged) {
            this.isHiddenManaged = isHiddenManaged;
            requestLayoutUpdate();
        }
    }

    // ── Insets ────────────────────────────────────────────────────────────────

    @Override public TerminalInsets getInsets() { return insets; }

    public void setInsets(int all) { insets.setAll(all); }

    public void setInsets(TerminalInsets padding) {

        if (padding == null) {
            if (!insets.isZero()) insets.clear();
            return;
        }

        if (!insets.equals(padding)) insets.copyFrom(padding);
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

    // ── Spatial-axis classification ───────────────────────────────────────────
    //
    // Do NOT override isSizedByContent() or isSizedByParent() here — the base
    // class computes them from these four methods.

    @Override public int     getNumSpatialAxes()      { return 4; }
    @Override public boolean isPositionAxis(int axis) { return axis == AXIS_X || axis == AXIS_Y; }

    @Override
    public boolean isAxisParentDependent(int axis) {
        return switch (axis) {
            case AXIS_W -> widthPreference.isParentDependent();
            case AXIS_H -> heightPreference.isParentDependent();
            default     -> false;
        };
    }

    @Override
    public boolean isAxisContentDependent(int axis) {
        return switch (axis) {
            case AXIS_W -> widthPreference.isContentDependent();
            case AXIS_H -> heightPreference.isContentDependent();
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
        int w = resolveOwnDimension(true,  childContexts);
        int h = resolveOwnDimension(false, childContexts);

        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, w, h);
        return measured;
    }

    /**
     * Resolve one dimension for this node during the content pre-pass.
     *
     * FILL/PERCENT: parent-allocated size is unknown at this phase — report
     * the minimum floor so ancestors can at least reserve that much space.
     *
     * FIT_CONTENT: scan children, preferring in-flight measurements over
     * committed geometry.
     */
    private int resolveOwnDimension(boolean isWidth, TerminalLayoutContext[] childContexts) {
        SizePreference pref = isWidth ? widthPreference : heightPreference;

        if (pref.isFixed()) {
            return isWidth ? region.getWidth() : region.getHeight();
        }
        if (pref.isParentDependent()) {
            return isWidth ? getMinWidth() : getMinHeight();
        }

        // FIT_CONTENT
        int maxChildSize = 0;
        java.util.List<TerminalRenderable> children = getChildren();

        for (int i = 0; i < children.size(); i++) {
            TerminalRenderable child = children.get(i);
            if (child.isHiddenDesired()) continue;

            int childSize = resolveChildDimension(
                child,
                isWidth,
                (childContexts != null && i < childContexts.length) ? childContexts[i] : null
            );
            maxChildSize = Math.max(maxChildSize, childSize);
        }

        int insetPad = isWidth ? insets.getHorizontal() : insets.getVertical();
        return Math.max(isWidth ? getMinWidth() : getMinHeight(), maxChildSize + insetPad);
    }

    /**
     * Resolve one dimension for a single child.
     *
     * Priority:
     *   1. In-flight measuredContentBounds from this pass — freshest data.
     *   2. Parent-dependent child (FILL/PERCENT) contributes only minSize.
     *   3. Child's requested region — user-staged geometry.
     *   4. Child's committed region — last known good value.
     */
    private int resolveChildDimension(
        TerminalRenderable child,
        boolean isWidth,
        TerminalLayoutContext ctx
    ) {
        if (ctx != null) {
            TerminalRectangle bounds = ctx.getMeasuredContentBounds();
            if (bounds != null) {
                return isWidth ? bounds.getWidth() : bounds.getHeight();
            }
        }

        if (child instanceof TerminalSizeable sizeable) {
            SizePreference pref = resolveChildPreference(isWidth, sizeable);
            if (pref.isParentDependent()) {
                return isWidth ? sizeable.getMinWidth() : sizeable.getMinHeight();
            }
        }

        TerminalRectangle requested = child.getRequestedRegion();
        if (requested != null) {
            return isWidth ? requested.getWidth() : requested.getHeight();
        }

        return isWidth ? child.getRegion().getWidth() : child.getRegion().getHeight();
    }

    private SizePreference resolveChildPreference(boolean isWidth, TerminalSizeable sizeable) {
        SizePreference pref = isWidth ? sizeable.getWidthPreference() : sizeable.getHeightPreference();
        if (pref != SizePreference.INHERIT) {
            return pref;
        }
        return isWidth ? widthPreference : heightPreference;
    }

    // ── Common measurement helpers for subclasses ─────────────────────────────

    /**
     * Reads a measurement dimension from a child renderable.
     *
     * This method implements the common logic for determining a child's size
     * during the content measurement pass, with the following priority:
     *
     * 1. In-flight measuredContentBounds from this pass — freshest data
     * 2. Parent-dependent child (FILL/PERCENT) contributes only minSize
     * 3. Child's requested region — user-staged geometry
     * 4. Child's committed region — last known good value
     *
     * @param child The child renderable to measure
     * @param ctx The layout context for the child (may be null)
     * @param parentPref The parent's size preference for inheritance
     * @param isWidth true to measure width, false to measure height
     * @return The measured dimension
     */
    protected int readMeasurementDimension(
        TerminalRenderable child,
        TerminalLayoutContext ctx,
        SizePreference parentPref,
        boolean isWidth
    ) {
        if (ctx != null) {
            TerminalRectangle bounds = ctx.getMeasuredContentBounds();
            if (bounds != null) {
                return isWidth ? bounds.getWidth() : bounds.getHeight();
            }
        }

        if (child instanceof TerminalSizeable sizeable) {
            SizePreference pref = isWidth ? sizeable.getWidthPreference() : sizeable.getHeightPreference();
            if (pref == SizePreference.INHERIT) {
                pref = parentPref;
            }
            if (pref.isParentDependent()) {
                return isWidth ? sizeable.getMinWidth() : sizeable.getMinHeight();
            }
        }

        TerminalRectangle requested = child.getRequestedRegion();
        if (requested != null) {
            return isWidth ? requested.getWidth() : requested.getHeight();
        }

        return isWidth ? child.getRegion().getWidth() : child.getRegion().getHeight();
    }

    /**
     * Calculates the maximum width from children when width preference is FIT_CONTENT.
     *
     * @param children List of child renderables
     * @param childContexts Array of layout contexts for children
     * @param parentPref The parent's width preference
     * @return The maximum width required by children
     */
    protected int calculateMaxWidth(
        java.util.List<TerminalRenderable> children,
        TerminalLayoutContext[] childContexts,
        SizePreference parentPref
    ) {
        int maxWidth = 0;

        for (int i = 0; i < children.size(); i++) {
            TerminalRenderable child = children.get(i);
            if (child.isHiddenDesired()) continue;

            TerminalLayoutContext ctx = childContexts != null && i < childContexts.length
                ? childContexts[i]
                : null;

            int width = readMeasurementDimension(child, ctx, parentPref, true);
            maxWidth = Math.max(maxWidth, width);
        }

        return maxWidth;
    }

    /**
     * Calculates the total height from children when height preference is FIT_CONTENT.
     *
     * @param children List of child renderables
     * @param childContexts Array of layout contexts for children
     * @param parentPref The parent's height preference
     * @return The total height required by children
     */
    protected int calculateTotalHeight(
        java.util.List<TerminalRenderable> children,
        TerminalLayoutContext[] childContexts,
        SizePreference parentPref
    ) {
        int totalHeight = 0;

        for (int i = 0; i < children.size(); i++) {
            TerminalRenderable child = children.get(i);
            if (child.isHiddenDesired()) continue;

            TerminalLayoutContext ctx = childContexts != null && i < childContexts.length
                ? childContexts[i]
                : null;

            int height = readMeasurementDimension(child, ctx, parentPref, false);
            if (height > 0) {
                totalHeight += height;
            }
        }

        return totalHeight;
    }

    /**
     * Calculates the total width from children when width preference is FIT_CONTENT.
     *
     * @param children List of child renderables
     * @param childContexts Array of layout contexts for children
     * @param parentPref The parent's width preference
     * @return The total width required by children
     */
    protected int calculateTotalWidth(
        java.util.List<TerminalRenderable> children,
        TerminalLayoutContext[] childContexts,
        SizePreference parentPref
    ) {
        int totalWidth = 0;

        for (int i = 0; i < children.size(); i++) {
            TerminalRenderable child = children.get(i);
            if (child.isHiddenDesired()) continue;

            TerminalLayoutContext ctx = childContexts != null && i < childContexts.length
                ? childContexts[i]
                : null;

            int width = readMeasurementDimension(child, ctx, parentPref, true);
            if (width > 0) {
                totalWidth += width;
            }
        }

        return totalWidth;
    }

    /**
     * Calculates the maximum height from children when height preference is FIT_CONTENT.
     *
     * @param children List of child renderables
     * @param childContexts Array of layout contexts for children
     * @param parentPref The parent's height preference
     * @return The maximum height required by children
     */
    protected int calculateMaxHeight(
        java.util.List<TerminalRenderable> children,
        TerminalLayoutContext[] childContexts,
        SizePreference parentPref
    ) {
        int maxHeight = 0;

        for (int i = 0; i < children.size(); i++) {
            TerminalRenderable child = children.get(i);
            if (child.isHiddenDesired()) continue;

            TerminalLayoutContext ctx = childContexts != null && i < childContexts.length
                ? childContexts[i]
                : null;

            int height = readMeasurementDimension(child, ctx, parentPref, false);
            maxHeight = Math.max(maxHeight, height);
        }

        return maxHeight;
    }

    @Override
    protected TerminalRenderable[] createRenderableArray(int size) {
        return new TerminalRenderable[size];
    }

    /**
     * Reads a single dimension (width or height) for a child purely from its
     * context — never calls {@code getPreferredWidth/Height()} on the child.
     * Priority: measuredContentBounds → requestedRegion → currentRegion.
     *
     * @param ctx The layout context for the child
     * @param isWidth true to read width, false to read height
     * @return The dimension value
     */
    protected int readDimension(TerminalLayoutContext ctx, boolean isWidth) {
        TerminalRectangle bounds = ctx.getMeasuredContentBounds();
        if (bounds != null) {
            return isWidth ? bounds.getWidth() : bounds.getHeight();
        }

        TerminalRenderable child = ctx.getRenderable();
        TerminalRectangle requested = child.getRequestedRegion();
        if (requested != null) {
            return isWidth ? requested.getWidth() : requested.getHeight();
        }

        return isWidth ? child.getRegion().getWidth() : child.getRegion().getHeight();
    }

}
