package io.netnotes.terminal.components.panels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netnotes.debug.RenderDiagnostics;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutGroupCallback;
import io.netnotes.terminal.layout.TerminalSizeable;
import io.netnotes.engine.ui.LayoutOverflowStrategy;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.renderer.layout.LayoutGroup.LayoutDataInterface;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * TerminalStackPanel - Z-axis stacking container.
 *
 * Holds multiple renderables occupying the same space; only one is visible at a
 * time. Sizing is delegated to the currently visible content unless overridden
 * by an explicit size preference set directly on this panel.
 *
 * Supports scroll offsets and content padding for use in scrollable containers.
 * Enforces unique renderable names within the stack.
 *
 * OVERFLOW STRATEGY:
 * - CLIP (default) : content that extends beyond the viewport after applying the
 *                    scroll offset is hidden.
 * - OVERFLOW       : content is positioned with the scroll offset applied but not
 *                    clipped — useful when an outer container handles clipping.
 */
public class TerminalStackPanel extends TerminalRegion {

    private final List<TerminalRenderable>          stack           = new ArrayList<>();
    private final Map<String, TerminalRenderable>   nameToRenderable = new HashMap<>();
    private TerminalRenderable                       visibleContent  = null;

    private int scrollOffsetX = 0;
    private int scrollOffsetY = 0;
    private final TerminalInsets padding = new TerminalInsets();

    private LayoutOverflowStrategy overflowStrategy = LayoutOverflowStrategy.CLIP;

    private final String layoutGroupId;
    private final String layoutCallbackId;
    private TerminalLayoutGroupCallback layoutCallback = null;

    public TerminalStackPanel(String name) {
        super(name);
        this.layoutGroupId   = "stackpanel-" + getName();
        this.layoutCallbackId = "stackpanel-default";
        padding.setOnChanged(insets -> requestLayoutUpdate());
        init();
    }

    private void init() {
        this.layoutCallback = this::layoutStack;
        registerChildGroupCallback(getLayoutGroupId(), layoutCallback);
    }

    public String getLayoutCallbackId()             { return layoutCallbackId;    }
    public String getLayoutGroupId()                { return layoutGroupId;       }
    public TerminalLayoutGroupCallback getTerminalGroupCallback() { return layoutCallback; }

    // ===== SCROLL / PADDING =====

    /**
     * Set scroll offset for the content. Shifts content within the viewport.
     */
    public void setScrollOffset(int x, int y) {
        if (applyScrollOffset(x, y)) {
            requestLayoutUpdate();
        }
    }

    /**
     * Layout-time scroll update that avoids scheduling a follow-up pass while a
     * parent container is already computing this stack's layout.
     */
    void setScrollOffsetDuringLayout(int x, int y) {
        applyScrollOffset(x, y);
    }

    private boolean applyScrollOffset(int x, int y) {
        int clampedX = Math.max(0, x);
        int clampedY = Math.max(0, y);
        if (this.scrollOffsetX != clampedX || this.scrollOffsetY != clampedY) {
            this.scrollOffsetX = clampedX;
            this.scrollOffsetY = clampedY;
            return true;
        }
        return false;
    }

    public int getScrollOffsetX() { return scrollOffsetX; }
    public int getScrollOffsetY() { return scrollOffsetY; }

    /**
     * Set padding from a TerminalInsets instance.
     * Alias: {@link #setInsets(TerminalInsets)}
     */
    public void setPadding(TerminalInsets insets) {
        setInsets(insets);
    }

    /**
     * Set padding uniformly on all four sides.
     * Alias retained for consistency with other panel types.
     */
    public void setPadding(int pad) {
        setContentPadding(pad);
    }

    /**
     * Set padding uniformly on all four sides.
     */
    public void setContentPadding(int pad) {
        int clamped = Math.max(0, pad);
        if (padding.getTop()    != clamped ||
            padding.getRight()  != clamped ||
            padding.getBottom() != clamped ||
            padding.getLeft()   != clamped) {
            padding.setAll(clamped);
        }
    }

    /**
     * Set insets from a TerminalInsets instance (all four sides independently).
     * Consistent with the naming convention used across other panel types.
     */
    public void setInsets(TerminalInsets newInsets) {
        if (newInsets == null) {
            if (!padding.isZero()) {
                padding.clear();
            }
            return;
        }
        if (!padding.equals(newInsets)) {
            padding.copyFrom(newInsets);
        }
    }

    @Override
    public TerminalInsets getInsets() { return padding; }

    // ===== OVERFLOW STRATEGY =====

    public LayoutOverflowStrategy getOverflowStrategy() { return overflowStrategy; }

    public void setOverflowStrategy(LayoutOverflowStrategy strategy) {
        if (strategy != null && this.overflowStrategy != strategy) {
            this.overflowStrategy = strategy;
            requestLayoutUpdate();
        }
    }

    // ===== PERCENT SIZE OVERRIDES =====

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

    // ===== STACK MANAGEMENT =====

    /**
     * Visibility policy: only the designated visibleContent may be shown.
     */
    private boolean visibilityPolicy(TerminalRenderable renderable, boolean isVisible) {
        if (!isVisible) return true;               // always allow hiding
        return renderable == visibleContent;        // allow showing only the active content
    }

    /**
     * Add a renderable to the stack. Hidden by default unless it is the first
     * item and no visible content has been set yet.
     *
     * @throws IllegalArgumentException if a different renderable with the same name already exists
     */
    public void addToStack(TerminalRenderable renderable) {
        if (renderable == null) {
            throw new IllegalArgumentException("Cannot add null renderable to stack");
        }
        if (stack.contains(renderable)) return;

        String name = renderable.getName();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Renderable must have a non-empty name");
        }

        TerminalRenderable existing = nameToRenderable.get(name);
        if (existing != null) {
            if (existing == renderable) return;
            throw new IllegalArgumentException(
                "Renderable with name '" + name + "' already exists in stack");
        }

        stack.add(renderable);
        nameToRenderable.put(name, renderable);
        addChild(renderable);
        addToLayoutGroup(renderable, layoutGroupId);
        renderable.setVisibilityPolicy(this::visibilityPolicy);

        if (stack.size() == 1 && visibleContent == null) {
            setVisibleContent(renderable);
        } else {
            renderable.hide();
        }

        requestLayoutUpdate();
    }

    @Override
    public void removeChild(TerminalRenderable renderable) {
        removeFromStack(renderable);
    }

    public void removeFromStack(TerminalRenderable renderable) {
        if (!stack.contains(renderable)) return;

        stack.remove(renderable);
        nameToRenderable.remove(renderable.getName());
        super.removeChild(renderable);
        renderable.setVisibilityPolicy(null);

        if (renderable == visibleContent) visibleContent = null;

        requestLayoutUpdate();
    }

    public void removeFromStack(String name) {
        TerminalRenderable renderable = nameToRenderable.get(name);
        if (renderable != null) removeFromStack(renderable);
    }

    public void setVisibleContent(TerminalRenderable renderable) {
        if (renderable != null && !stack.contains(renderable)) {
            throw new IllegalArgumentException(
                "Renderable must be in stack before setting as visible");
        }
        TerminalRenderable previousVisible = visibleContent;
        RenderDiagnostics.logSwapTrace(
            "TerminalStackPanel.setVisibleContent:start",
            this,
            () -> "previousVisible=" + RenderDiagnostics.summarizeRenderable(previousVisible)
                + "\n\ttargetVisible=" + RenderDiagnostics.summarizeRenderable(renderable)
                + "\n\tstackSize=" + stack.size()
        );
        for (TerminalRenderable item : stack) {
            if (item != renderable && !item.isHidden()) {
                RenderDiagnostics.logSwapTrace(
                    "TerminalStackPanel.setVisibleContent:hide",
                    item,
                    () -> "stack=" + RenderDiagnostics.summarizeRenderable(this)
                        + "\n\ttargetVisible=" + RenderDiagnostics.summarizeRenderable(renderable)
                );
                item.hide();
            }
        }
        visibleContent = renderable;
        if (visibleContent != null && visibleContent.isHidden()) {
            RenderDiagnostics.logSwapTrace(
                "TerminalStackPanel.setVisibleContent:show",
                visibleContent,
                () -> "stack=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\tpreviousVisible=" + RenderDiagnostics.summarizeRenderable(previousVisible)
            );
            visibleContent.show();
        }
        RenderDiagnostics.logSwapTrace(
            "TerminalStackPanel.setVisibleContent:before-requestLayoutUpdate",
            this,
            () -> "currentVisible=" + RenderDiagnostics.summarizeRenderable(visibleContent)
        );
        requestLayoutUpdate();
        RenderDiagnostics.logSwapTrace(
            "TerminalStackPanel.setVisibleContent:end",
            this,
            () -> "currentVisible=" + RenderDiagnostics.summarizeRenderable(visibleContent)
                + "\n\tpreviousVisible=" + RenderDiagnostics.summarizeRenderable(previousVisible)
        );
    }

    public void setVisibleContent(String name) {
        TerminalRenderable renderable = nameToRenderable.get(name);
        if (renderable == null) {
            throw new IllegalArgumentException(
                "No renderable with name '" + name + "' exists in stack");
        }
        setVisibleContent(renderable);
    }

    public void clearStack() {
        for (TerminalRenderable renderable : new ArrayList<>(stack)) {
            removeFromStack(renderable);
        }
    }

    public TerminalRenderable getVisibleContent()              { return visibleContent; }
    public List<TerminalRenderable> getStackContents()         { return new ArrayList<>(stack); }
    public boolean contains(TerminalRenderable renderable)     { return stack.contains(renderable); }
    public boolean contains(String name)                       { return nameToRenderable.containsKey(name); }
    public TerminalRenderable getContent(String name)          { return nameToRenderable.get(name); }
    public int getStackSize()                                  { return stack.size(); }
    public boolean isEmpty()                                   { return stack.isEmpty(); }
    public int indexOf(TerminalRenderable renderable)          { return stack.indexOf(renderable); }
    public int indexOf(String name) {
        TerminalRenderable r = nameToRenderable.get(name);
        return r != null ? stack.indexOf(r) : -1;
    }
    public TerminalRenderable getContentAt(int index) {
        return (index >= 0 && index < stack.size()) ? stack.get(index) : null;
    }

    // ===== LAYOUT =====

    protected void layoutStack(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        if (contexts.length == 0) {
            return;
        }

        TerminalRectangle parentPanel = contexts[0].getParentRegion();
        if (parentPanel == null) {
            Log.logError("[TerminalStackPanel] layoutStack: parent region is null"
                + " this.region=" + (region != null ? region.toString() : "null"));
            return;
        }

        TerminalInsets ins = getInsets();
        int viewportWidth  = parentPanel.getWidth()  - ins.getHorizontal();
        int viewportHeight = parentPanel.getHeight() - ins.getVertical();

        TerminalRenderable visible = visibleContent;

        if (visible == null) return;

        TerminalLayoutContext visibleContext = findContext(contexts, visible);

        int contentWidth;
        int contentHeight;

        if (visible instanceof TerminalSizeable s) {
            SizePreference wp = resolveVisiblePreference(s, true);
            SizePreference hp = resolveVisiblePreference(s, false);

            contentWidth = switch (wp) {
                case FILL    -> viewportWidth;
                case FIT_CONTENT ->  visibleContext.getMeasuredContentBounds().getWidth();
                case PERCENT -> Math.max(s.getMinWidth(),
                    (int)(viewportWidth * s.getPercentWidth()));
                case STATIC  -> visibleContext != null && visibleContext.getRequestedRegion() != null
                    ? visibleContext.getRequestedRegion().getWidth()
                    : visible.getRegion().getWidth();
                default      -> viewportWidth;
            };

            contentHeight = switch (hp) {
                case FILL    -> viewportHeight;
                case FIT_CONTENT -> visibleContext.getMeasuredContentBounds().getHeight();
                case PERCENT -> Math.max(s.getMinHeight(),
                    (int)(viewportHeight * s.getPercentHeight()));
                case STATIC  -> visibleContext != null && visibleContext.getRequestedRegion() != null
                    ? visibleContext.getRequestedRegion().getHeight()
                    : visible.getRegion().getHeight();
                default      -> viewportHeight;
            };
        } else {
            contentWidth  = viewportWidth;
            contentHeight = viewportHeight;
        }

        for (TerminalLayoutContext context : contexts) {
            TerminalRenderable child = context.getRenderable();

            if (!stack.contains(child)) {
                Log.logError("[TerminalStackPanel] layoutStack: skipping unknown child: "
                    + child.getName());
                continue;
            }

            if (child.isLayoutExcluded()) {
                dataInterfaces.get(child.getName())
                    .setLayoutData(TerminalLayoutData.getBuilder().build());
                continue;
            }

            boolean manageHidden = shouldManageHidden(child);

            if (!manageHidden && child != visible && child.isHidden()) {
                continue;
            }

            boolean shouldBeHidden = child != visible;

            int x = ins.getLeft() - scrollOffsetX;
            int y = ins.getTop()  - scrollOffsetY;

            if (!shouldBeHidden && overflowStrategy != LayoutOverflowStrategy.OVERFLOW) {
                boolean outOfBounds = (x + contentWidth  <= 0) || (x >= parentPanel.getWidth())
                                   || (y + contentHeight <= 0) || (y >= parentPanel.getHeight());
                if (outOfBounds) {
                    shouldBeHidden = true;
                }
            }

            TerminalLayoutData.TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder()
                .setX(x)
                .setY(y)
                .setWidth(contentWidth)
                .setHeight(contentHeight);

            if (manageHidden) {
                builder.hidden(shouldBeHidden);
            }

            dataInterfaces.get(child.getName()).setLayoutData(builder.build());
        }
    }

    private TerminalLayoutContext findContext(
        TerminalLayoutContext[] contexts,
        TerminalRenderable target
    ) {
        for (TerminalLayoutContext context : contexts) {
            if (context.getRenderable() == target) {
                return context;
            }
        }
        return null;
    }

    private boolean shouldManageHidden(TerminalRenderable child) {
        if (child instanceof TerminalSizeable sizable) {
            return sizable.isHiddenManaged();
        }
        return true;
    }

    private SizePreference resolveVisiblePreference(TerminalSizeable sizable, boolean isWidth) {
        SizePreference pref = isWidth 
            ? sizable.getWidthPreference() 
            : sizable.getHeightPreference();
        if(pref == SizePreference.INHERIT){
            return isWidth ? getWidthPreference() : getHeightPreference();
        }
        return pref;
    }

    // ===== SIZEABLE DELEGATION (with optional overrides) =====


    @Override
    public SizePreference getSizePreference(int axis) {
        return switch (axis) {
            case AXIS_W -> getWidthPreference();
            case AXIS_H -> getHeightPreference();
            default -> throw new IllegalArgumentException(
                "getSizePreference TerminalStackPanel does not have axis: " + axis);
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


    /**
     * Pre-pass that runs before layoutStack. Sizes the panel from the visible
     * child's context data so that a parent with FIT_CONTENT sizing gets the
     * correct answer without calling getPreferredWidth/Height on the child.
     *
     * Only the visible child contributes — hidden children are irrelevant to the
     * panel's own footprint. Padding is added on top of the content size.
     */
    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        SizePreference ownWP = getWidthPreference();
        SizePreference ownHP = getHeightPreference();
        TerminalInsets ins = getInsets();

        int contentW = 0;
        int contentH = 0;

        if (visibleContent != null && childContexts != null) {
            TerminalLayoutContext visibleCtx = findContext(childContexts, visibleContent);

            if (visibleCtx != null) {
                if (ownWP == SizePreference.FIT_CONTENT) {
                    contentW = readDimension(visibleCtx, true);
                }
                if (ownHP == SizePreference.FIT_CONTENT) {
                    contentH = readDimension(visibleCtx, false);
                }
            }
        }

        int w = switch (ownWP) {
            case STATIC      -> region.getWidth();
            case FIT_CONTENT -> Math.max(getMinWidth(),  contentW + ins.getHorizontal());
            default          -> getMinWidth();   // FILL / PERCENT — floor only
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

    /**
     * Reads a single dimension from a child context — never calls
     * getPreferredWidth/Height on the child.
     * Priority: measuredContentBounds → requestedRegion → currentRegion.
     */
    private int readDimension(TerminalLayoutContext ctx, boolean isWidth) {
        TerminalRectangle bounds = ctx.getMeasuredContentBounds();
        if (bounds != null) return isWidth ? bounds.getWidth() : bounds.getHeight();

        TerminalRectangle requested = ctx.getRequestedRegion();
        if (requested != null) return isWidth ? requested.getWidth() : requested.getHeight();

        TerminalRenderable child = ctx.getRenderable();
        return isWidth ? child.getRegion().getWidth() : child.getRegion().getHeight();
    }

    // ===== RENDERING =====

    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        // StackPanel does not render anything itself
    }

    @Override
    protected void onDestroying() {
        destroyLayoutGroup(layoutGroupId);
        layoutCallback = null;
    }
}
