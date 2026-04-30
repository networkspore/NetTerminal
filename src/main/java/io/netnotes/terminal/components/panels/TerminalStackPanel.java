package io.netnotes.terminal.components.panels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netnotes.debug.RenderDiagnostics;
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
import io.netnotes.engine.ui.renderer.LayoutGroup.LayoutDataInterface;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * TerminalStackPanel — single-visible Z-axis stacking container.
 *
 * Holds multiple renderables occupying the same space; exactly one is visible
 * at a time. Visibility is not negotiated: being the current child means being
 * visible; not being the current child means being hidden. There is no
 * isHiddenManaged concept here — the panel owns the hidden state completely.
 *
 * PRIMARY API:
 * - setVisibleContent(renderable/name) — swap the visible child.
 * - getVisibleContent()                — returns the current child.
 *
 * SCROLL / OVERFLOW:
 * - CLIP (default) : content scrolled out of viewport is hidden.
 * - OVERFLOW       : content is positioned with scroll offset applied but not
 *                    clipped; an outer container handles clipping.
 */
public class TerminalStackPanel extends TerminalGroupRegion {

    // ── stack state ───────────────────────────────────────────────────────────

    private final List<TerminalRegion>        stack            = new ArrayList<>();
    private final Map<String, TerminalRegion> nameToRenderable = new HashMap<>();

    /**
     * The single currently visible child. All layout sizing and coordinate
     * assignment is derived from this child. Null if the stack is empty or
     * no content has been set.
     */
    private TerminalRegion currentContent = null;

    // ── configuration ─────────────────────────────────────────────────────────

    private int scrollOffsetX = 0;
    private int scrollOffsetY = 0;

    private LayoutOverflowStrategy overflowStrategy = LayoutOverflowStrategy.CLIP;

    // ── layout group ──────────────────────────────────────────────────────────


    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    public TerminalStackPanel(String name) {
        super(name, "stackpanel");
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
            overflowStrategy == LayoutOverflowStrategy.OVERFLOW
                ? TerminalRenderable.OverflowClipPolicy.INHERIT_PARENT_CLIP
                : TerminalRenderable.OverflowClipPolicy.CLIP_TO_SELF_BOUNDS
        );
    }

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
     * Visibility policy injected into each child. Only the current child may
     * be visible; all others are unconditionally hidden by this panel.
     * Hiding is always allowed so the panel can always push a child to hidden.
     */
    private boolean visibilityPolicy(TerminalRenderable renderable, boolean isVisible) {
        if (!isVisible) return true;             // always allow hiding
        return renderable == currentContent;     // only the current child may show
    }
    /**
     * Adds a renderable to the stack.
     *
     * @param renderable the renderable to add
     * @param cb ignored; the stack panel does not support child-specific layout callbacks
     */
    @Override
    public void addChild(TerminalRenderable renderable, TerminalLayoutCallback cb) {

        addToStack(renderable);
    }

    /**
     * Add a renderable to the stack.
     *
     * The first child added is automatically promoted to current content.
     * Subsequent children are hidden immediately — the panel owns their
     * hidden state from the moment they are added.
     *
     * @throws IllegalArgumentException nameless, or duplicate-name input.
     */

     public void addToStack(TerminalRenderable renderable) {
         if (!getUIExecutor().isCurrentThread()) {
            if (!isAttachedToLayoutManager()) {
                getUIExecutor().submit(() -> addToStack(renderable), null).join();
            } else {
                getUIExecutor().runLater(() -> addToStack(renderable));
            }
            return;
        }

        if (renderable == null) {
            setVisibleContent((TerminalRegion) null);
            return;
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

        if (currentContent == null) {
            // First child: auto-promote.
            currentContent = child;
            child.show();
        } else {
            // All subsequent children start hidden — no negotiation.
            child.hide();
        }

        super.addChild(child, null);
    }

    @Override
    public void removeChild(TerminalRenderable renderable) {
        removeFromStack(checkTerminalRegion(renderable));
    }

    public void removeFromStack(TerminalRegion child) {
        if (!getUIExecutor().isCurrentThread()) {
            if (!isAttachedToLayoutManager()) {
                getUIExecutor().submit(() -> removeFromStack(child), null).join();
            } else {
                getUIExecutor().runLater(() -> removeFromStack(child));
            }
            return;
        }

        if (child == null) {
            setVisibleContent((TerminalRegion) null);
            return;
        }

        if (!stack.contains(child)) return;
        stack.remove(child);
        nameToRenderable.remove(child.getName());

        child.setVisibilityPolicy(null);

        if (child == currentContent) {
            // Promote the last remaining child, if any.
            currentContent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
            if (currentContent != null) currentContent.show();
        }

        super.removeChild(child);
    }

    public void removeFromStack(String name) {
        TerminalRegion r = nameToRenderable.get(name);
        if (r != null) removeFromStack(r);
    }

    public void clearStack() {
        if (!getUIExecutor().isCurrentThread()) {
            if (!isAttachedToLayoutManager()) {
                getUIExecutor().submit(this::clearStack, null).join();
            } else {
                getUIExecutor().runLater(this::clearStack);
            }
            return;
        }

        for (TerminalRegion r : new ArrayList<>(stack)) {
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
     * Swap the visible child. The previous current child is unconditionally
     * hidden; the new one is unconditionally shown. There is no isHiddenManaged
     * check — the panel owns this transition completely.
     *
     * Passing null clears the current content (all children hidden).
     *
     */
    public void setVisibleContent(TerminalRegion child) {
        if (child != null && !stack.contains(child)) {
            return;
        }

        TerminalRenderable previous = currentContent;
        RenderDiagnostics.logSwapTrace(
            "TerminalStackPanel.setVisibleContent:start", this,
            () -> "previous=" + RenderDiagnostics.summarizeRenderable(previous)
                + "\n\ttarget=" + RenderDiagnostics.summarizeRenderable(child)
                + "\n\tstackSize=" + stack.size()
        );

        if (previous != null && previous != child) {
            RenderDiagnostics.logSwapTrace(
                "TerminalStackPanel.setVisibleContent:hide", previous,
                () -> "stack=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\ttarget=" + RenderDiagnostics.summarizeRenderable(child)
            );
            previous.hide();
        }

        currentContent = child;

        if (child != null && child.isHidden()) {
            RenderDiagnostics.logSwapTrace(
                "TerminalStackPanel.setVisibleContent:show", child,
                () -> "stack=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\tprevious=" + RenderDiagnostics.summarizeRenderable(previous)
            );
            child.show();
        }

        RenderDiagnostics.logSwapTrace(
            "TerminalStackPanel.setVisibleContent:end", this,
            () -> "current=" + RenderDiagnostics.summarizeRenderable(currentContent)
                + "\n\tprevious=" + RenderDiagnostics.summarizeRenderable(previous)
        );

    }

    public void setVisibleContent(String name) {
        TerminalRegion r = nameToRenderable.get(name);
        if (r == null) {
            throw new IllegalArgumentException(
                "No renderable with name '" + name + "' exists in stack");
        }
        setVisibleContent(r);
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    /** Returns the single currently visible child, or null if none. */
    public TerminalRegion getContent()          { return currentContent; }
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
            Log.logError("[TerminalStackPanel] layoutStack: parent region is null"
                + " this.region=" + (region != null ? region.toString() : "null"));
            return;
        }

        TerminalInsets ins = getInsets();
        int viewportWidth  = parentPanel.getWidth()  - ins.getHorizontal();
        int viewportHeight = parentPanel.getHeight() - ins.getVertical();

        TerminalLayoutContext currentContext = currentContent != null
            ? findContext(contexts, currentContent) : null;
        int contentWidth  = resolveContentDimension(currentContent, currentContext, viewportWidth,  true);
        int contentHeight = resolveContentDimension(currentContent, currentContext, viewportHeight, false);

        for (TerminalLayoutContext context : contexts) {
            TerminalRenderable child = context.getRenderable();

            if (!stack.contains(child)) {
                Log.logError("[TerminalStackPanel] layoutStack: skipping unknown child: "
                    + child.getName());
                continue;
            }

            if (!canUnhide(child)) {
                dataInterfaces.get(child.getName())
                    .setLayoutData(TerminalLayoutData.getBuilder().build());
                continue;
            }

            if (child != currentContent) {
                // Not current — unconditionally hidden, no coordinates.
                dataInterfaces.get(child.getName())
                    .setLayoutData(TerminalLayoutData.getBuilder().hidden(true).build());
                continue;
            }

            // Current child — assign coordinates.
            int x = ins.getLeft() - scrollOffsetX;
            int y = ins.getTop()  - scrollOffsetY;

            boolean outOfBounds = overflowStrategy != LayoutOverflowStrategy.OVERFLOW
                && ((x + contentWidth  <= 0) || (x >= parentPanel.getWidth())
                 || (y + contentHeight <= 0) || (y >= parentPanel.getHeight()));

            dataInterfaces.get(child.getName()).setLayoutData(
                TerminalLayoutData.getBuilder()
                    .setX(x)
                    .setY(y)
                    .setWidth(Math.max(0, contentWidth))
                    .setHeight(Math.max(0, contentHeight))
                    .hidden(outOfBounds)
                    .build()
            );
        }
    }

    // =========================================================================
    // MEASURE
    // =========================================================================

    /**
     * Pre-pass sizing: only the current visible child contributes to this
     * panel's footprint.
     */
    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        SizePreference ownWP = getWidthPreference();
        SizePreference ownHP = getHeightPreference();
        TerminalInsets ins   = getInsets();

        int contentW = 0;
        int contentH = 0;

        if (currentContent != null) {
            TerminalLayoutContext ctx = childContexts != null
                ? findContext(childContexts, currentContent)
                : null;

            // Get child's width preference (respecting INHERIT)
            SizePreference childWidthPref = currentContent.getWidthPreference() == SizePreference.INHERIT
                ? ownWP
                : currentContent.getWidthPreference();
            int minWidth = currentContent.getMinWidth();

            // Calculate width based on child's preference
            switch (childWidthPref) {
                case FILL:
                    contentW = Math.max(minWidth, readContentDimension(currentContent, ctx, true));
                    break;
                case FIT_CONTENT:
                    contentW = Math.max(minWidth, readContentDimension(currentContent, ctx, true));
                    break;
                case PERCENT:
                    contentW = Math.max(minWidth, (int) (readContentDimension(currentContent, ctx, true) * currentContent.getPercentWidth()));
                    break;
                case STATIC:
                default:
                    int width = ctx != null && ctx.getRequestedRegion() != null
                        ? ctx.getRequestedRegion().getWidth()
                        : currentContent.getRegion().getWidth();
                    contentW = Math.max(minWidth, width);
                    break;
            }

            // Get child's height preference (respecting INHERIT)
            SizePreference childHeightPref = currentContent.getHeightPreference() == SizePreference.INHERIT
                ? ownHP
                : currentContent.getHeightPreference();
            int minHeight = currentContent.getMinHeight();

            // Calculate height based on child's preference
            switch (childHeightPref) {
                case FILL:
                    contentH = Math.max(minHeight, readContentDimension(currentContent, ctx, false));
                    break;
                case FIT_CONTENT:
                    contentH = Math.max(minHeight, readContentDimension(currentContent, ctx, false));
                    break;
                case PERCENT:
                    contentH = Math.max(minHeight, (int) (readContentDimension(currentContent, ctx, false) * currentContent.getPercentHeight()));
                    break;
                case STATIC:
                default:
                    int height = ctx != null && ctx.getRequestedRegion() != null
                        ? ctx.getRequestedRegion().getHeight()
                        : currentContent.getRegion().getHeight();
                    contentH = Math.max(minHeight, height);
                    break;
            }
        }

        int w = switch (ownWP) {
            case STATIC      -> region.getWidth();
            case FIT_CONTENT -> Math.max(getMinWidth(),  contentW + ins.getHorizontal());
            default          -> getMinWidth();
        };
        int h = switch (ownHP) {
            case STATIC      -> region.getHeight();
            case FIT_CONTENT -> Math.max(getMinHeight(), contentH + ins.getVertical());
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
        // StackPanel does not render anything itself.
    }


    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private int resolveContentDimension(
        TerminalRenderable content,
        TerminalLayoutContext ctx,
        int viewportSize,
        boolean isWidth
    ) {
        if (content == null) return 0;

        if (content instanceof TerminalSizeable s) {
            SizePreference pref = resolveVisiblePreference(s, isWidth);
            int min = isWidth ? s.getMinWidth() : s.getMinHeight();
            return switch (pref) {
                case FILL        -> viewportSize;
                case FIT_CONTENT -> {
                    TerminalRectangle measured = ctx != null ? ctx.getMeasuredContentBounds() : null;
                    if (measured != null) {
                        yield isWidth ? measured.getWidth() : measured.getHeight();
                    }
                    TerminalRectangle requested = content.getRequestedRegion();
                    if (requested != null) {
                        yield isWidth ? requested.getWidth() : requested.getHeight();
                    }
                    yield isWidth
                        ? content.getRegion().getWidth()
                        : content.getRegion().getHeight();
                }
                case PERCENT -> Math.max(min, (int)(viewportSize *
                    (isWidth ? s.getPercentWidth() : s.getPercentHeight())));
                case STATIC -> {
                    if (ctx != null && ctx.getRequestedRegion() != null) {
                        yield isWidth
                            ? ctx.getRequestedRegion().getWidth()
                            : ctx.getRequestedRegion().getHeight();
                    }
                    yield isWidth
                        ? content.getRegion().getWidth()
                        : content.getRegion().getHeight();
                }
                default -> viewportSize;
            };
        }

        TerminalRectangle requested = content.getRequestedRegion();
        if (requested != null) return isWidth ? requested.getWidth() : requested.getHeight();
        return isWidth ? content.getRegion().getWidth() : content.getRegion().getHeight();
    }

    private SizePreference resolveVisiblePreference(TerminalSizeable s, boolean isWidth) {
        SizePreference pref = isWidth ? s.getWidthPreference() : s.getHeightPreference();
        if (pref == SizePreference.INHERIT) {
            return isWidth ? getWidthPreference() : getHeightPreference();
        }
        return pref;
    }

    private TerminalLayoutContext findContext(
        TerminalLayoutContext[] contexts,
        TerminalRenderable target
    ) {
        for (TerminalLayoutContext ctx : contexts) {
            if (ctx == null) {
                Log.logError("[TerminalStackPanel] findContext: null context in array");
                continue;
            }
            if (ctx.getRenderable() == target) return ctx;
        }
        return null;
    }

}
