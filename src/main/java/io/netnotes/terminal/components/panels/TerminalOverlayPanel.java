package io.netnotes.terminal.components.panels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutCallback;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutGroupCallback;
import io.netnotes.terminal.layout.TerminalSizeable;
import io.netnotes.engine.ui.LayoutOverflowStrategy;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.layout2d.Overflow;
import io.netnotes.engine.ui.renderer.LayoutGroup.LayoutDataInterface;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * TerminalOverlayPanel — multi-visible Z-axis stacking container.
 *
 * Holds multiple renderables occupying the same space; up to maxVisibleNodes
 * may be visible simultaneously. There is no concept of a preferred or primary
 * child — all visible children are peers.
 *
 * VISIBILITY MODEL:
 * - isHiddenManaged() (via TerminalSizeable) governs whether this panel may
 *   call hide()/show() on a child and whether it sets the hidden flag in layout
 *   data. A child with isHiddenManaged()=false controls its own visibility; the
 *   panel assigns it coordinates when it is in the visible set but never forces
 *   a hide/show transition on it.
 * - Hidden children receive hidden(true) in their layout data and no coordinates.
 *   Stale coordinates cannot leak through to hidden nodes.
 *
 * MAX VISIBLE NODES:
 * - N > 0 : up to N children visible simultaneously. The visible set is
 *           maintained in insertion order; adding a new visible child when the
 *           set is full evicts the least-recently-shown entry.
 * - -1    : unlimited — every child added is shown by default.
 * - 0     : none — all children are hidden regardless of show() calls.
 *
 * MEASURE:
 * - The panel's content footprint is the intersection of all visible children's
 *   bounds (i.e. min width and min height across the visible set). This
 *   represents the guaranteed region in which all visible content overlaps.
 *   If no children are visible the footprint is zero.
 *
 * SCROLL / OVERFLOW:
 * - CLIP (default) : visible content scrolled out of viewport is hidden.
 * - OVERFLOW       : content is positioned with scroll offset applied but not
 *                    clipped; an outer container handles clipping.
 */
public class TerminalOverlayPanel extends TerminalGroupRegion {

    // ── stack state ───────────────────────────────────────────────────────────

    private final List<TerminalRenderable>        stack            = new ArrayList<>();
    private final Map<String, TerminalRegion> nameToRenderable = new HashMap<>();

    /**
     * Ordered visible set — children in this list receive coordinates and
     * hidden(false) during layout. Maintained in least-recently-shown order so
     * the head is always the eviction candidate when maxVisibleNodes is exceeded.
     */
    private final List<TerminalRegion> visibleSet = new ArrayList<>();

    // ── configuration ─────────────────────────────────────────────────────────

    /**
     * Maximum number of simultaneously visible children.
     *  -1 = unlimited (default)
     *   0 = none
     *  N  = up to N
     */
    private int maxVisibleNodes = -1;

    private int scrollOffsetX = 0;
    private int scrollOffsetY = 0;
    private final TerminalInsets padding = new TerminalInsets();
    private Overflow overflowStrategy = Overflow.HIDDEN;


    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    public TerminalOverlayPanel(String name) {
        super(name, "overlay-panel");
        padding.setOnChanged(insets -> requestLayoutUpdate());
        syncOverflowClipPolicy();

    }

    @Override
    protected TerminalLayoutGroupCallback createLayoutCallback() {
        return this::layoutStack;
    }


    // =========================================================================
    // SCROLL / PADDING
    // =========================================================================

    public void setScrollOffset(int x, int y) {
        if (applyScrollOffset(x, y)) requestLayoutUpdate();
    }

    /** Layout-time update — avoids scheduling a redundant follow-up pass. */
    void setScrollOffsetDuringLayout(int x, int y) {
        applyScrollOffset(x, y);
    }

    private boolean applyScrollOffset(int x, int y) {
        int cx = Math.max(0, x);
        int cy = Math.max(0, y);
        if (this.scrollOffsetX != cx || this.scrollOffsetY != cy) {
            this.scrollOffsetX = cx;
            this.scrollOffsetY = cy;
            return true;
        }
        return false;
    }

    public int getScrollOffsetX() { return scrollOffsetX; }
    public int getScrollOffsetY() { return scrollOffsetY; }

    public void setPadding(TerminalInsets insets) { setInsets(insets); }
    public void setPadding(int pad)               { setContentPadding(pad); }

    public void setContentPadding(int pad) {
        int c = Math.max(0, pad);
        if (padding.getTop() != c || padding.getRight() != c
                || padding.getBottom() != c || padding.getLeft() != c) {
            padding.setAll(c);
        }
    }

    public void setInsets(TerminalInsets newInsets) {
        if (newInsets == null) {
            if (!padding.isZero()) padding.clear();
            return;
        }
        if (!padding.equals(newInsets)) padding.copyFrom(newInsets);
    }

    @Override
    public TerminalInsets getInsets() { return padding; }

    // =========================================================================
    // OVERFLOW
    // =========================================================================

    public LayoutOverflowStrategy getOverflowStrategy() { return overflowStrategy; }

    public void setOverflowStrategy(LayoutOverflowStrategy strategy) {
        if (strategy != null && this.overflowStrategy != strategy) {
            this.overflowStrategy = strategy;
            syncOverflowClipPolicy();
            requestLayoutUpdate();
        }
    }

    private void syncOverflowClipPolicy() {
        setOverflowClipPolicy(
            overflowStrategy == Overflow.VISIBLE
                ? TerminalRenderable.OverflowClipPolicy.INHERIT_PARENT_CLIP
                : TerminalRenderable.OverflowClipPolicy.CLIP_TO_SELF_BOUNDS
        );
    }

    // =========================================================================
    // MAX VISIBLE NODES
    // =========================================================================

    /**
     * Set the maximum number of simultaneously visible children.
     * -1 = unlimited, 0 = none, N = up to N.
     *
     * Changing this value trims or expands the visible set immediately and
     * schedules a layout update.
     */
    public void setMaxVisibleNodes(int max) {
        if (this.maxVisibleNodes == max) return;
        this.maxVisibleNodes = max;
        trimVisibleSet();
        requestLayoutUpdate();
    }

    public int getMaxVisibleNodes() { return maxVisibleNodes; }

    // =========================================================================
    // PERCENT SIZE OVERRIDES
    // =========================================================================

    @Override
    public void setPercentWidth(double percent) {
        super.setPercentWidth(percent);
        requestLayoutUpdate();
    }

    @Override
    public void setPercentHeight(double percent) {
        super.setPercentHeight(percent);
        requestLayoutUpdate();
    }

    // =========================================================================
    // STACK MANAGEMENT
    // =========================================================================

    /**
     * Visibility policy injected into each child. A child may only self-show
     * if it is in the visible set and maxVisibleNodes permits it. Hiding is
     * always allowed so the panel can always push a child to hidden.
     */
    private boolean visibilityPolicy(TerminalRenderable renderable, boolean isVisible) {
        if (!isVisible) return true;
        if (maxVisibleNodes == 0) return false;
        return visibleSet.contains(renderable);
    }

    public void addToStack(TerminalRenderable renderable) {
        addChild(renderable);
    }

    /**
     * Add a renderable to the stack. In unlimited mode (-1) the child is shown
     * immediately. In capped modes (N > 0) the child is hidden until explicitly
     * shown via showContent(). In mode 0 the child is always hidden.
     *
     * @throws IllegalArgumentException on null, nameless, or duplicate-name input.
     */
    @Override
    public void addChild(TerminalRenderable renderable, TerminalLayoutCallback cb) {
        if (!getUIExecutor().isCurrentThread()) {
            getUIExecutor().runLater(() -> addChild(renderable, null));
            return;
        }

        if (renderable == null) {
            throw new IllegalArgumentException("Cannot add null renderable to stack");
        }
        TerminalRegion child = checkTerminalRegion(renderable);

        if (stack.contains(child)) return;

        String name = child.getName();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Renderable must have a non-empty name");
        }
        TerminalRenderable existing = nameToRenderable.get(name);
        if (existing != null) {
            if (existing == child) return;
            throw new IllegalArgumentException(
                "Renderable with name '" + name + "' already exists in stack");
        }

        stack.add(child);
        nameToRenderable.put(name, child);

        child.setVisibilityPolicy(this::visibilityPolicy);

        if (maxVisibleNodes == -1) {
            // Unlimited: show every child by default.
            addToVisibleSet(child);
        } else {
 
           child.setVisible(false);
        }

        super.addChild(child, null);
    }



    @Override
    public void removeChild(TerminalRenderable renderable) {
        removeFromStack(renderable);
    }

    public void removeFromStack(TerminalRenderable renderable) {
        if (!getUIExecutor().isCurrentThread()) {
            getUIExecutor().runLater(() -> removeFromStack(renderable));
            return;
        }

        if (!stack.contains(renderable)) return;
        stack.remove(renderable);
        nameToRenderable.remove(renderable.getName());
        visibleSet.remove(renderable);
        renderable.setVisibilityPolicy(null);
        super.removeChild(renderable);
        
    }

    public void removeFromStack(String name) {
        TerminalRenderable r = nameToRenderable.get(name);
        if (r != null) removeFromStack(r);
    }

    public void clearStack() {
        if (!getUIExecutor().isCurrentThread()) {
            getUIExecutor().runLater(this::clearStack);
            return;
        }

        for (TerminalRenderable r : new ArrayList<>(stack)) {
            removeFromStack(r);
        }
    }

    @Override
    public void clearChildren() {
        clearStack();
    }

    // =========================================================================
    // VISIBILITY CONTROL
    // =========================================================================

    /**
     * Add a child to the visible set without hiding others. Respects
     * maxVisibleNodes by evicting the least-recently-shown entry when the set
     * is full. Does nothing if maxVisibleNodes == 0.
     *
     * @throws IllegalArgumentException if the renderable is not in the stack.
     */
    public void showContent(TerminalRegion renderable) {
        if (maxVisibleNodes == 0) return;
        if (!stack.contains(renderable)) {
            throw new IllegalArgumentException(
                "Renderable must be in stack before showing");
        }
        addToVisibleSet(renderable);
        if (renderable.isHidden()) {
            renderable.setVisible(true);
        }
        requestLayoutUpdate();
    }

    public void showContent(String name) {
        TerminalRegion r = nameToRenderable.get(name);
        if (r == null) {
            throw new IllegalArgumentException(
                "No renderable with name '" + name + "' exists in stack");
        }
        showContent(r);
    }

    /**
     * Remove a child from the visible set and hide it (if managed).
     */
    public void hideContent(TerminalRenderable renderable) {
        if (!visibleSet.remove(renderable)) return;
        renderable.setVisible(false);
        requestLayoutUpdate();
    }

    public void hideContent(String name) {
        TerminalRenderable r = nameToRenderable.get(name);
        if (r != null) hideContent(r);
    }

    /** Hide all managed visible children and clear the visible set. */
    public void hideAll() {
        hideAllManaged();
        requestLayoutUpdate();
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    public List<TerminalRenderable> getVisibleSet()        { return new ArrayList<>(visibleSet); }
    public List<TerminalRenderable> getStackContents()     { return new ArrayList<>(stack); }
    public boolean contains(TerminalRenderable renderable) { return stack.contains(renderable); }
    public boolean contains(String name)                   { return nameToRenderable.containsKey(name); }
    public TerminalRenderable getContent(String name)      { return nameToRenderable.get(name); }
    public int getStackSize()                              { return stack.size(); }
    public boolean isEmpty()                               { return stack.isEmpty(); }
    public int indexOf(TerminalRenderable renderable)      { return stack.indexOf(renderable); }
    public int indexOf(String name) {
        TerminalRenderable r = nameToRenderable.get(name);
        return r != null ? stack.indexOf(r) : -1;
    }
    public TerminalRenderable getContentAt(int index) {
        return (index >= 0 && index < stack.size()) ? stack.get(index) : null;
    }
    public boolean isVisible(TerminalRenderable renderable) { return visibleSet.contains(renderable); }
    public boolean isVisible(String name) {
        TerminalRenderable r = nameToRenderable.get(name);
        return r != null && visibleSet.contains(r);
    }

    // =========================================================================
    // LAYOUT
    // =========================================================================

    protected void layoutStack(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        if (contexts.length == 0) return;

        TerminalRectangle parentPanel = contexts[0].getParentRegion();
        if (parentPanel == null) {
            Log.logError("[TerminalOverlayPanel] layoutStack: parent region is null"
                + " this.region=" + (region != null ? region.toString() : "null"));
            return;
        }

        TerminalInsets ins = getInsets();
        int viewportWidth  = parentPanel.getWidth()  - ins.getHorizontal();
        int viewportHeight = parentPanel.getHeight() - ins.getVertical();

        // All visible children share the same origin; each is sized independently.
        int x = ins.getLeft() - scrollOffsetX;
        int y = ins.getTop()  - scrollOffsetY;

        for (TerminalLayoutContext context : contexts) {
            TerminalRegion child = checkTerminalRegion(context.getRenderable());

            if (!stack.contains(child)) {
                Log.logError("[TerminalOverlayPanel] layoutStack: skipping unknown child: "
                    + child.getName());
                continue;
            }

            if (!child.isVisible()) {
                dataInterfaces.get(child.getName())
                    .setLayoutData(TerminalLayoutData.getBuilder().build());
                continue;
            }


            boolean inVisible = maxVisibleNodes != 0 && visibleSet.contains(child);

            if (!inVisible) {
                dataInterfaces.get(child.getName())
                    .setLayoutData(TerminalLayoutData.getBuilder().hidden(true).build());
                continue;
            }

            // Visible child — resolve its own dimensions independently.
            int childWidth  = resolveChildDimension(child, context, viewportWidth,  true);
            int childHeight = resolveChildDimension(child, context, viewportHeight, false);

            boolean outOfBounds = overflowStrategy != Overflow.VISIBLE
                && ((x + childWidth  <= 0) || (x >= parentPanel.getWidth())
                 || (y + childHeight <= 0) || (y >= parentPanel.getHeight()));

            TerminalLayoutData.TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder()
                .setX(x)
                .setY(y)
                .setWidth(Math.max(0, childWidth))
                .setHeight(Math.max(0, childHeight));

       
            builder.hidden(outOfBounds);
            

            dataInterfaces.get(child.getName()).setLayoutData(builder.build());
        }
    }

    // =========================================================================
    // MEASURE
    // =========================================================================

    /**
     * Pre-pass sizing: the panel's content footprint is the intersection of all
     * visible children's bounds — i.e. the minimum width and minimum height
     * across the visible set. This is the guaranteed area in which all visible
     * layers overlap.
     *
     * If no children are visible the footprint is zero.
     */
    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        SizePreference ownWP = getWidthPreference();
        SizePreference ownHP = getHeightPreference();
        TerminalInsets ins   = getInsets();

        // Single loop to calculate both dimensions
        int intersectW = Integer.MAX_VALUE;
        int intersectH = Integer.MAX_VALUE;
        boolean anyVisible = false;

        for (TerminalRegion child : visibleSet) {
            if (!canUnhide(child)) {
                continue;
            }
            anyVisible = true;
            TerminalLayoutContext ctx = childContexts != null
                ? findContext(childContexts, child)
                : null;

            // Get child's width preference (respecting INHERIT)
            SizePreference childWidthPref = child.getWidthPreference() == SizePreference.INHERIT
                ? ownWP
                : child.getWidthPreference();
            int minWidth = child.getMinWidth();

            // Calculate width based on child's preference
            switch (childWidthPref) {
                case FIT_CONTENT:
                    intersectW = clampDimension(child, readContentDimension(child, ctx, true), true);
                    break;
                case FILL:
                    intersectW = Math.min(intersectW, minWidth);
                    break;
                case PERCENT:
                    intersectW = Math.min(intersectW, minWidth);
                    break;
                case STATIC:
                default:
                    int width = childContexts != null && ctx != null && ctx.getRequestedRegion() != null
                        ? ctx.getRequestedRegion().getWidth()
                        : child.getRegion().getWidth();
                    intersectW = Math.min(intersectW, clampDimension(child, width, true));
                    break;
            }

            // Get child's height preference (respecting INHERIT)
            SizePreference childHeightPref = child.getHeightPreference() == SizePreference.INHERIT
                ? ownHP
                : child.getHeightPreference();
            int minHeight = child.getMinHeight();

            // Calculate height based on child's preference
            switch (childHeightPref) {
                case FIT_CONTENT:
                    int childHeight = clampDimension(child, readContentDimension(child, ctx, false), false);
                    intersectH = Math.min(intersectH, childHeight);
                    break;
                case FILL:
                    intersectH = Math.min(intersectH, minHeight);
                    break;
                case PERCENT:
                    intersectH = Math.min(intersectH, minHeight);
                    break;
                case STATIC:
                default:
                    int height = childContexts != null && ctx != null && ctx.getRequestedRegion() != null
                        ? ctx.getRequestedRegion().getHeight()
                        : child.getRegion().getHeight();
                    intersectH = Math.min(intersectH, clampDimension(child, height, false));
                    break;
            }
        }

        int contentW = anyVisible && intersectW != Integer.MAX_VALUE ? intersectW : 0;
        int contentH = anyVisible && intersectH != Integer.MAX_VALUE ? intersectH : 0;

        int w = switch (ownWP) {
            case STATIC      -> region.getWidth();
            case FIT_CONTENT -> clampDimension(this, contentW + ins.getHorizontal(), true);
            default          -> getMinWidth();
        };
        int h = switch (ownHP) {
            case STATIC      -> region.getHeight();
            case FIT_CONTENT -> clampDimension(this, contentH + ins.getVertical(), false);
            default          -> getMinHeight();
        };

        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, w, h);
        return measured;
    }

    // =========================================================================
    // SIZEABLE DELEGATION
    // =========================================================================

    @Override
    public SizePreference getSizePreference(int axis) {
        return switch (axis) {
            case AXIS_W -> getWidthPreference();
            case AXIS_H -> getHeightPreference();
            default -> throw new IllegalArgumentException(
                "getSizePreference: unknown axis " + axis);
        };
    }

    @Override
    public boolean isAxisParentDependent(int axis) {
        return switch (axis) {
            case AXIS_W -> getWidthPreference().isParentDependent();
            case AXIS_H -> getHeightPreference().isParentDependent();
            default -> false;
        };
    }

    @Override
    public boolean isAxisContentDependent(int axis) {
        return switch (axis) {
            case AXIS_W -> getWidthPreference().isContentDependent();
            case AXIS_H -> getHeightPreference().isContentDependent();
            default -> false;
        };
    }

    // =========================================================================
    // RENDERING
    // =========================================================================

    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        // OverlayPanel does not render anything itself.
    }


    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Add a child to the visible set, refreshing its MRU position. If the set
     * is at capacity, the least-recently-shown entry is evicted and hidden.
     */
    private void addToVisibleSet(TerminalRegion renderable) {
        visibleSet.remove(renderable);
        if (maxVisibleNodes > 0 && visibleSet.size() >= maxVisibleNodes) {
            TerminalRenderable evicted = visibleSet.remove(0);
            evicted.setVisible(false);
        }
        visibleSet.add(renderable);
    }

    /**
     * Trim the visible set to the current maxVisibleNodes limit, evicting from
     * the least-recently-shown end.
     */
    private void trimVisibleSet() {
        if (maxVisibleNodes < 0) return;
        while (maxVisibleNodes == 0 || visibleSet.size() > maxVisibleNodes) {
            if (visibleSet.isEmpty()) break;
            TerminalRenderable evicted = visibleSet.remove(0);
            evicted.setVisible(false);
        }
    }

    private void hideAllManaged() {
        for (TerminalRenderable r : new ArrayList<>(visibleSet)) {
            r.setVisible(false);
        }
        visibleSet.clear();
    }

   
    private int resolveChildDimension(
        TerminalRegion child,
        TerminalLayoutContext ctx,
        int viewportSize,
        boolean isWidth
    ) {
        if (child instanceof TerminalSizeable s) {
            SizePreference pref = isWidth ? s.getWidthPreference() : s.getHeightPreference();
            if (pref == SizePreference.INHERIT) {
                pref = isWidth ? getWidthPreference() : getHeightPreference();
            }
            int min = isWidth ? s.getMinWidth() : s.getMinHeight();
            return switch (pref) {
                case FILL        -> Math.max(min, viewportSize);
                case FIT_CONTENT -> {
                    int dimension;
                    TerminalRectangle measured = ctx != null ? ctx.getMeasuredContentBounds() : null;
                    if (measured != null) {
                        dimension = isWidth ? measured.getWidth() : measured.getHeight();
                    } else if (ctx != null && ctx.getRequestedRegion() != null) {
                        dimension = isWidth
                            ? ctx.getRequestedRegion().getWidth()
                            : ctx.getRequestedRegion().getHeight();
                    } else {
                        dimension = isWidth ? child.getRegion().getWidth() : child.getRegion().getHeight();
                    }
                    yield Math.max(min, dimension);
                }
                case PERCENT -> Math.max(min, (int)(viewportSize *
                    (isWidth ? s.getPercentWidth() : s.getPercentHeight())));
                case STATIC -> {
                    int dimension;
                    if (ctx != null && ctx.getRequestedRegion() != null) {
                        dimension = isWidth
                            ? ctx.getRequestedRegion().getWidth()
                            : ctx.getRequestedRegion().getHeight();
                    } else {
                        dimension = isWidth ? child.getRegion().getWidth() : child.getRegion().getHeight();
                    }
                    yield Math.max(min, dimension);
                }
                default -> Math.max(min, viewportSize);
            };
        }

        TerminalRectangle requested = child.getRequestedRegion();
        int dimension = requested != null
            ? (isWidth ? requested.getWidth() : requested.getHeight())
            : (isWidth ? child.getRegion().getWidth() : child.getRegion().getHeight());
        int min = isWidth ? child.getMinWidth() : child.getMinHeight();
        return Math.max(min, dimension);
    }

    private TerminalLayoutContext findContext(
        TerminalLayoutContext[] contexts,
        TerminalRenderable target
    ) {
        for (TerminalLayoutContext ctx : contexts) {
            if (ctx == null) {
                Log.logError("[TerminalOverlayPanel] findContext: null context in array");
                continue;
            }
            if (ctx.getRenderable() == target) return ctx;
        }
        return null;
    }

}
