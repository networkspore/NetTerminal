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
     *   2. Child's requested region — user-staged geometry.
     *   3. Child's committed region — last known good value.
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

        TerminalRectangle requested = child.getRequestedRegion();
        if (requested != null) {
            return isWidth ? requested.getWidth() : requested.getHeight();
        }

        return isWidth ? child.getRegion().getWidth() : child.getRegion().getHeight();
    }

    @Override
    protected TerminalRenderable[] createRenderableArray(int size) {
        return new TerminalRenderable[size];
    }
}
