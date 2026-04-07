package io.netnotes.terminal.components.panels;

import java.util.EnumMap;
import java.util.Map;

import io.netnotes.debug.RenderDiagnostics;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutGroupCallback;
import io.netnotes.terminal.layout.TerminalSizeable;
import io.netnotes.engine.ui.BorderPanel;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.renderer.layout.LayoutGroup.LayoutDataInterface;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.LoggingHelpers.LogLevel;
import io.netnotes.noteBytes.processing.IntCounter;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.components.TerminalRegion;

/**
 * A border layout panel with 5 regions: TOP, BOTTOM, LEFT, RIGHT, CENTER.
 * Each region uses a TerminalStackPanel internally to support multiple
 * renderables with only one visible at a time.
 */
public class TerminalBorderPanel extends TerminalRegion {

    private static final LogLevel LOG_LEVEL = LogLevel.GENERAL;
    
    
    private final EnumMap<BorderPanel, TerminalStackPanel> regionStacks = new EnumMap<>(BorderPanel.class);
    
    // Default sizes for regions when children don't exist (use -1 for "not set")
    private int reservedTopHeight = -1;
    private int reservedBottomHeight = -1;
    private int reservedLeftWidth = -1;
    private int reservedRightWidth = -1;

    private final String layoutGroupId;
    private final String layoutCallbackId;
    private TerminalLayoutGroupCallback layoutCallback = null;
    
    public TerminalBorderPanel(String name) {
        super(name);
        String className = this.getClass().getName();
        className = className.substring(className.lastIndexOf(".") + 1);
        this.layoutGroupId = className + "-" + getName();
        this.layoutCallbackId = layoutGroupId + "-callback";
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FILL);

        // Create a stack panel for each region
        for (BorderPanel panel : BorderPanel.values()) {
            TerminalStackPanel stack = new TerminalStackPanel(name + "-" + panel.name().toLowerCase());
            switch(panel){
                case BorderPanel.CENTER:
                    stack.setWidthPreference(SizePreference.FILL);
                    stack.setHeightPreference(SizePreference.FILL);
                break;
                case TOP:
                case BOTTOM:
                    stack.setWidthPreference(SizePreference.FILL);
                    stack.setHeightPreference(SizePreference.FIT_CONTENT);
                    break;
                case LEFT:
                case RIGHT:
                    stack.setWidthPreference(SizePreference.FIT_CONTENT);
                    stack.setHeightPreference(SizePreference.FILL);
                    break;
            }
            regionStacks.put(panel, stack);
            addChild(stack);
        }
        
        init();
    }

    private void init() {
        this.layoutCallback = this::layoutAllPanels;
        registerChildGroupCallback(getLayoutGroupId(), layoutCallback);
        
        // Add all stack panels to the layout group
        for (TerminalStackPanel stack : regionStacks.values()) {
            addToLayoutGroup(stack, layoutGroupId);
        }
    }

    public TerminalLayoutGroupCallback getTerminalGroupCallbackEntry() {
        return layoutCallback;
    }

    public String getLayoutCallbackId() {
        return layoutCallbackId;
    }
    
    public String getLayoutGroupId() {
        return layoutGroupId;
    }

    protected String getSwapTraceOwner(BorderPanel region) {
        return getName() + ":" + (region != null ? region.name().toLowerCase() : "unknown");
    }
    
  

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
    
    /**
     * Set default height for TOP region when child doesn't specify size.
     * Use -1 to disable (will calculate from child).
     */
    public void setReservedTopHeight(int height) {
        if (this.reservedTopHeight != height) {
            this.reservedTopHeight = height;
            requestLayoutUpdate();
        }
    }
    
    /**
     * Set default height for BOTTOM region when child doesn't specify size.
     * Use -1 to disable (will calculate from child).
     */
    public void setReservedBottomHeight(int height) {
        if (this.reservedBottomHeight != height) {
            this.reservedBottomHeight = height;
            requestLayoutUpdate();
        }
    }
    
    /**
     * Set default width for LEFT region when child doesn't specify size.
     * Use -1 to disable (will calculate from child).
     */
    public void setReservedLeftWidth(int width) {
        if (this.reservedLeftWidth != width) {
            this.reservedLeftWidth = width;
            requestLayoutUpdate();
        }
    }
    
    /**
     * Set default width for RIGHT region when child doesn't specify size.
     * Use -1 to disable (will calculate from child).
     */
    public void setReservedRightWidth(int width) {
        if (this.reservedRightWidth != width) {
            this.reservedRightWidth = width;
            requestLayoutUpdate();
        }
    }
    
    public int getReservedTopHeight() { return reservedTopHeight; }
    public int getReservedBottomHeight() { return reservedBottomHeight; }
    public int getReservedLeftWidth() { return reservedLeftWidth; }
    public int getReservedRightWidth() { return reservedRightWidth; }
    
    /**
     * Set a single child for a region, replacing any existing content.
     * The stack for that region will be cleared and only this child will be added.
     */
    public void setPanel(BorderPanel region, TerminalRenderable child) {
        if (region == null) {
            throw new IllegalArgumentException("Panel cannot be null");
        }
        
        TerminalStackPanel stack = regionStacks.get(region);
        stack.clearStack();
        
        if (child != null) {
            stack.addToStack(child);
            stack.setVisibleContent(child);
        }
        
        requestLayoutUpdate();
    }
    
    /**
     * Swap to a different child in a region. If the child is not already in the
     * region's stack, it will be added. The child will become visible and all
     * other children in that region will be hidden.
     */
    public void swapPanel(BorderPanel region, TerminalRenderable newChild) {
        if (region == null) {
            throw new IllegalArgumentException("Panel cannot be null");
        }
        
        if (newChild == null) {
            return;
        }
        
        TerminalStackPanel stack = regionStacks.get(region);
        TerminalRenderable previousVisible = stack != null ? stack.getVisibleContent() : null;
        String traceOwner = getSwapTraceOwner(region);

        RenderDiagnostics.armSwapTrace(
            traceOwner,
            "TerminalBorderPanel.swapPanel",
            this,
            stack,
            previousVisible,
            newChild
        );
        RenderDiagnostics.logSwapTraceEvent(
            traceOwner,
            "TerminalBorderPanel.swapPanel:start",
            () -> "region=" + region
                + "\n\tborderPanel=" + RenderDiagnostics.summarizeRenderable(this)
                + "\n\tstack=" + RenderDiagnostics.summarizeRenderable(stack)
                + "\n\tpreviousVisible=" + RenderDiagnostics.summarizeRenderable(previousVisible)
                + "\n\tnewChild=" + RenderDiagnostics.summarizeRenderable(newChild)
        );
        
        // Add to stack if not already present
        if (!stack.contains(newChild)) {
            stack.addToStack(newChild);
        }
        
        // Make it the visible content
        if (stack.getVisibleContent() != newChild) {
            stack.setVisibleContent(newChild);
        }
        requestLayoutUpdate();

        RenderDiagnostics.logSwapTraceEvent(
            traceOwner,
            "TerminalBorderPanel.swapPanel:end",
            () -> "region=" + region
                + "\n\tstack=" + RenderDiagnostics.summarizeRenderable(stack)
                + "\n\tcurrentVisible=" + RenderDiagnostics.summarizeRenderable(stack.getVisibleContent())
        );
    }
    
    /**
     * Add a child to a region's stack without making it visible.
     * Useful for pre-loading content that will be swapped to later.
     */
    public void addToPanel(BorderPanel region, TerminalRenderable child) {
        if (region == null) {
            throw new IllegalArgumentException("Panel cannot be null");
        }
        
        if (child == null) {
            return;
        }
        
        TerminalStackPanel stack = regionStacks.get(region);
        
        if (!stack.contains(child)) {
            stack.addToStack(child);
        }
    }
    
    /**
     * Remove a child from a region's stack.
     */
    public void removeFromPanel(BorderPanel region, TerminalRenderable child) {
        if (region == null) {
            throw new IllegalArgumentException("Panel cannot be null");
        }
        
        if (child == null) {
            return;
        }
        
        TerminalStackPanel stack = regionStacks.get(region);
        stack.removeFromStack(child);
        
        requestLayoutUpdate();
    }
    
    /**
     * Get the currently visible child in a region.
     */
    public TerminalRenderable getPanel(BorderPanel region) {
        if (region == null) {
            return null;
        }
        
        TerminalStackPanel stack = regionStacks.get(region);
        return stack.getVisibleContent();
    }
    
    /**
     * Get the stack panel for a region (allows direct access to all stack operations).
     */
    public TerminalStackPanel getRegionStack(BorderPanel region) {
        return regionStacks.get(region);
    }
    
    /**
     * Clear all content from a region.
     */
    public void clearPanel(BorderPanel region) {
        if (region == null) {
            return;
        }
        
        TerminalStackPanel stack = regionStacks.get(region);
        stack.clearStack();
        
        requestLayoutUpdate();
    }
    
    /**
     * Clear all regions.
     */
    public void clearAllPanels() {
        for (BorderPanel region : BorderPanel.values()) {
            clearPanel(region);
        }
    }
    
    /**
     * Layout callback: positions the 5 region stack panels in border layout.
     */
    protected void layoutAllPanels(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        
        if (contexts.length == 0){ 
            Log.logError("[TerminalBorderPanel] contexts: 0");
            return;
        }
  
        TerminalRectangle parentPanel = contexts[0].getParentRegion();
 
        if (parentPanel == null){
            Log.logError("[TerminalBorderPanel] parent region is null" +
                "this.Region:" + (this.region != null ? this.region.toString() : "null")
            );
            return; 
        }
   
        int horizontalPadding = insets.getHorizontal();
        int verticalPadding = insets.getVertical();

        int availableWidth = parentPanel.getWidth() - horizontalPadding;
        int availableHeight = parentPanel.getHeight() - verticalPadding;

        if (availableWidth <= 0 || availableHeight <= 0) {
            RenderDiagnostics.logRenderBlocker(
                "borderpanel-no-space:" + getName(),
                "TerminalBorderPanel.layoutAllPanels",
                "non-positive-parent-space",
                () -> "panel=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\tpanelSizing=" + RenderDiagnostics.summarizeSizing(this)
                    + "\n\tparentPanel=" + RenderDiagnostics.summarizeRegion(parentPanel)
                    + "\n\tinsets=" + insets
                    + "\n\tavailableWidth=" + availableWidth
                    + "\n\tavailableHeight=" + availableHeight
            );
        }
        
        // Calculate dimensions for each region.
        // Priority: content measurement > default reservation > 0 (region collapses).
        // Defaults only apply when the slot is empty — they reserve space, not override content.
        IntCounter topHeight = new IntCounter();
        IntCounter bottomHeight = new IntCounter();
        IntCounter leftWidth = new IntCounter();
        IntCounter rightWidth = new IntCounter();

        TerminalStackPanel topStack = regionStacks.get(BorderPanel.TOP);
        TerminalLayoutContext topContext = findContext(contexts, topStack);
        TerminalRenderable topChild = topStack.getVisibleContent();
        if (topChild != null && shouldIncludeInLayout(topStack)) {
            topHeight.set(resolveChildHeight(topContext, topStack));
        } else if (topChild == null && reservedTopHeight > 0) {
            topHeight.set(reservedTopHeight);
        }
        if (topHeight.get() > 0) {
            topHeight.set(Math.min(topHeight.get(), availableHeight));
        }

        TerminalStackPanel bottomStack = regionStacks.get(BorderPanel.BOTTOM);
        TerminalLayoutContext bottomContext = findContext(contexts, bottomStack);
        TerminalRenderable bottomChild = bottomStack.getVisibleContent();
        if (bottomChild != null && shouldIncludeInLayout(bottomStack)) {
            bottomHeight.set(resolveChildHeight(bottomContext, bottomStack));
        } else if (bottomChild == null && reservedBottomHeight > 0) {
            bottomHeight.set(reservedBottomHeight);
        }
        if (bottomHeight.get() > 0) {
            int remainingHeight = availableHeight - topHeight.get();
            bottomHeight.set(Math.min(bottomHeight.get(), remainingHeight));
        }

        int middleHeight = Math.max(0, availableHeight - topHeight.get() - bottomHeight.get());
        int middleY = insets.getTop() + topHeight.get();

        TerminalStackPanel leftStack = regionStacks.get(BorderPanel.LEFT);
        TerminalLayoutContext leftContext = findContext(contexts, leftStack);
        TerminalRenderable leftChild = leftStack.getVisibleContent();
        if (leftChild != null && shouldIncludeInLayout(leftStack)) {
            leftWidth.set(resolveChildWidth(leftContext, leftStack));
        } else if (leftChild == null && reservedLeftWidth > 0) {
            leftWidth.set(reservedLeftWidth);
        }
        if (leftWidth.get() > 0) {
            leftWidth.set(Math.min(leftWidth.get(), availableWidth));
        }

        TerminalStackPanel rightStack = regionStacks.get(BorderPanel.RIGHT);
        TerminalLayoutContext rightContext = findContext(contexts, rightStack);
        TerminalRenderable rightChild = rightStack.getVisibleContent();
        if (rightChild != null && shouldIncludeInLayout(rightStack)) {
            rightWidth.set(resolveChildWidth(rightContext, rightStack));
        } else if (rightChild == null && reservedRightWidth > 0) {
            rightWidth.set(reservedRightWidth);
        }
        if (rightWidth.get() > 0) {
            int remainingWidth = availableWidth - leftWidth.get();
            rightWidth.set(Math.min(rightWidth.get(), remainingWidth));
        }

        TerminalStackPanel centerStack = regionStacks.get(BorderPanel.CENTER);
        TerminalRenderable centerChild = centerStack.getVisibleContent();
        int centerWidth = availableWidth - leftWidth.get() - rightWidth.get();

        if (centerChild != null && (middleHeight <= 0 || centerWidth <= 0)) {
            RenderDiagnostics.logRenderBlocker(
                "borderpanel-center-collapse:" + getName(),
                "TerminalBorderPanel.layoutAllPanels",
                middleHeight <= 0 ? "center-height-collapsed-by-surrounding-regions" : "center-width-collapsed-by-side-regions",
                () -> "panel=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\tpanelSizing=" + RenderDiagnostics.summarizeSizing(this)
                    + "\n\tparentPanel=" + RenderDiagnostics.summarizeRegion(parentPanel)
                    + "\n\ttopHeight=" + topHeight
                    + "\n\tbottomHeight=" + bottomHeight
                    + "\n\tleftWidth=" + leftWidth
                    + "\n\trightWidth=" + rightWidth
                    + "\n\tcenterWidth=" + centerWidth
                    + "\n\tmiddleHeight=" + middleHeight
                    + "\n\ttopChild=" + RenderDiagnostics.summarizeRenderable(topChild)
                    + "\n\tbottomChild=" + RenderDiagnostics.summarizeRenderable(bottomChild)
                    + "\n\tleftChild=" + RenderDiagnostics.summarizeRenderable(leftChild)
                    + "\n\trightChild=" + RenderDiagnostics.summarizeRenderable(rightChild)
                    + "\n\tcenterChild=" + RenderDiagnostics.summarizeRenderable(centerChild)
                    + "\n\tcenterSizing=" + RenderDiagnostics.summarizeSizing(centerChild)
            );
        }
        
        // Layout each stack panel
        layoutStackPanel(dataInterfaces, topStack, 
            insets.getLeft(), insets.getTop(), availableWidth, topHeight.get(), parentPanel);
        
        layoutStackPanel(dataInterfaces, bottomStack,
            insets.getLeft(), insets.getTop() + availableHeight - bottomHeight.get(),
            availableWidth, bottomHeight.get(), parentPanel);
        
        layoutStackPanel(dataInterfaces, leftStack,
            insets.getLeft(), middleY, leftWidth.get(), middleHeight, parentPanel);
        
        layoutStackPanel(dataInterfaces, rightStack,
            insets.getLeft() + availableWidth - rightWidth.get(), middleY,
            rightWidth.get(), middleHeight, parentPanel);
        
        int centerX = insets.getLeft() + leftWidth.get();
        layoutStackPanel(dataInterfaces, centerStack,
            centerX, middleY, Math.max(0, centerWidth), middleHeight, parentPanel);

        Log.logMsg((this instanceof TerminalScrollPanel 
                ? "[TerminalScrollPanel]" 
                : "[TerminalBorderPanel]"
            )
            + "\n\tTop Stack:" + availableWidth + " , " + topHeight
            + "\n\tCenterStack:" + centerWidth +" , " + middleHeight 
        , LOG_LEVEL);
    }
    
    private void layoutStackPanel(
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces,
        TerminalStackPanel stack,
        int x,
        int y,
        int width,
        int height,
        TerminalRectangle parentPanel
    ) {
        boolean inBounds = isWithinParentBounds(x, y, width, height, parentPanel);
        TerminalRenderable visibleContent = stack.getVisibleContent();
        
        TerminalLayoutData.TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder()
            .setX(x)
            .setY(y)
            .setWidth(width)
            .setHeight(height);

        if (visibleContent != null && (width <= 0 || height <= 0 || !inBounds)) {
            RenderDiagnostics.logRenderBlocker(
                "borderpanel-stack-collapse:" + getName() + ":" + stack.getName(),
                "TerminalBorderPanel.layoutStackPanel",
                !inBounds ? "stack-hidden-out-of-parent-bounds" : "stack-allocated-non-positive-size",
                () -> "panel=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\tstack=" + RenderDiagnostics.summarizeRenderable(stack)
                    + "\n\tstackSizing=" + RenderDiagnostics.summarizeSizing(stack)
                    + "\n\tvisibleContent=" + RenderDiagnostics.summarizeRenderable(visibleContent)
                    + "\n\tvisibleSizing=" + RenderDiagnostics.summarizeSizing(visibleContent)
                    + "\n\tallocatedBounds=" + RenderDiagnostics.summarizeRegion(new TerminalRectangle(x, y, width, height))
                    + "\n\tparentPanel=" + RenderDiagnostics.summarizeRegion(parentPanel)
            );
        }

        if (!inBounds) {
            builder.hidden(true);
        } else if (shouldManageHidden(stack)) {
            // Show the stack if it has visible content, hide it if empty
            boolean hasVisibleContent = visibleContent != null;
            builder.hidden(!hasVisibleContent);
        }

        TerminalLayoutData layout = builder.build();
        dataInterfaces.get(stack.getName()).setLayoutData(layout);
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

    /**
     * Resolves the height that a side region stack should occupy.
     *
     * Resolution order (mirrors the context-first contract of the other panels):
     *   FIT_CONTENT → measuredContentBounds (throws if absent — pre-pass is required)
     *   STATIC       → requestedRegion → currentRegion
     *   FILL/PERCENT → minHeight (the stack fills whatever the border panel assigns;
     *                  returning minHeight here gives the border panel a floor to work from)
     */
    private int resolveChildHeight(TerminalLayoutContext context, TerminalRenderable child) {
        if (child instanceof TerminalSizeable sizeable) {
            SizePreference pref = sizeable.getHeightPreference();
            return switch (pref) {
                case FIT_CONTENT -> {
                    TerminalRectangle measured = context != null
                        ? context.getMeasuredContentBounds() : null;
                    if (measured == null) throw new IllegalStateException(
                        "FIT_CONTENT height requires measured content bounds for: "
                            + child.getName());
                    yield measured.getHeight();
                }
                case STATIC -> child.getRequestedRegion() != null
                    ? child.getRequestedRegion().getHeight()
                    : child.getRegion().getHeight();
                default -> sizeable.getMinHeight();   // FILL / PERCENT — floor only
            };
        }

        // Non-sizeable: requestedRegion is the only sizing hint available.
        if (child.getRequestedRegion() != null) {
            return child.getRequestedRegion().getHeight();
        }
        return 1;
    }

    /**
     * Resolves the width that a side region stack should occupy.
     */
    private int resolveChildWidth(TerminalLayoutContext context, TerminalRenderable child) {
        if (child instanceof TerminalSizeable sizeable) {
            SizePreference pref = sizeable.getWidthPreference();
            return switch (pref) {
                case FIT_CONTENT -> {
                    TerminalRectangle measured = context != null
                        ? context.getMeasuredContentBounds() : null;
                    if (measured == null) throw new IllegalStateException(
                        "FIT_CONTENT width requires measured content bounds for: "
                            + child.getName());
                    yield measured.getWidth();
                }
                case STATIC -> child.getRequestedRegion() != null
                    ? child.getRequestedRegion().getWidth()
                    : child.getRegion().getWidth();
                default -> sizeable.getMinWidth();    // FILL / PERCENT — floor only
            };
        }

        if (child.getRequestedRegion() != null) {
            return child.getRequestedRegion().getWidth();
        }
        return 1;
    }

    private boolean shouldIncludeInLayout(TerminalRenderable child) {
        return !child.isLayoutExcluded();
    }

    private boolean shouldManageHidden(TerminalRenderable child) {
        if (child instanceof TerminalSizeable sizeable) {
            return sizeable.isHiddenManaged();
        }
        return true;
    }

    private boolean isWithinParentBounds(
        int x,
        int y,
        int width,
        int height,
        TerminalRectangle parentPanel
    ) {
        return x >= 0 &&
            y >= 0 &&
            x + width <= parentPanel.getWidth() &&
            y + height <= parentPanel.getHeight();
    }

    
    
    
    // ===== TerminalSizeable implementation =====

    
    /**
     * Pre-pass that computes this panel's own content size from the region stack
     * contexts. Only meaningful when the panel itself is FIT_CONTENT on either axis,
     * which is uncommon (defaults are FILL/FILL).
     *
     * Width  = leftW  + centerW + rightW  + insets.horizontal
     * Height = topH   + centerH + bottomH + insets.vertical
     *
     * The default-override values (defaultTopHeight, etc.) are applied here too,
     * mirroring the logic in the layout callback. FILL/PERCENT regions contribute
     * their minSize floor — they don't have an intrinsic size, so that's the best
     * approximation without a known parent size.
     * 
     */
    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        SizePreference ownWP = getWidthPreference();
        SizePreference ownHP = getHeightPreference();

        int contentW = 0;
        int contentH = 0;

        if (ownWP == SizePreference.FIT_CONTENT || ownHP == SizePreference.FIT_CONTENT) {

            int topH    = resolveRegionHeight(childContexts, BorderPanel.TOP,    reservedTopHeight);
            int bottomH = resolveRegionHeight(childContexts, BorderPanel.BOTTOM, reservedBottomHeight);
            int leftW   = resolveRegionWidth (childContexts, BorderPanel.LEFT,   reservedLeftWidth);
            int rightW  = resolveRegionWidth (childContexts, BorderPanel.RIGHT,  reservedRightWidth);

            TerminalLayoutContext centerCtx = findRegionContext(childContexts, BorderPanel.CENTER);
            TerminalStackPanel centerStack  = regionStacks.get(BorderPanel.CENTER);
            int centerW = centerStack != null ? readDimension(centerCtx, centerStack, true)  : 0;
            int centerH = centerStack != null ? readDimension(centerCtx, centerStack, false) : 0;

            contentW = leftW + centerW + rightW;
            contentH = topH  + centerH + bottomH;
        }

        int w = switch (ownWP) {
            case STATIC      -> region.getWidth();
            case FIT_CONTENT -> Math.max(getMinWidth(),  contentW + insets.getHorizontal());
            default          -> getMinWidth();   // FILL / PERCENT — floor only
        };
        int h = switch (ownHP) {
            case STATIC      -> region.getHeight();
            case FIT_CONTENT -> Math.max(getMinHeight(), contentH + insets.getVertical());
            default          -> getMinHeight();
        };

        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, w, h);
        return measured;
    }

    /**
     * Resolves the height contribution of a TOP or BOTTOM region.
     * Content measurement wins when the slot is occupied.
     * The default only applies when the slot is empty — reserving space, not overriding content.
     * Returns 0 when neither is applicable.
     */
    private int resolveRegionHeight(TerminalLayoutContext[] contexts, BorderPanel region, int defaultH) {
        TerminalStackPanel stack = regionStacks.get(region);
        if (stack == null || stack.isLayoutExcluded()) return 0;

        TerminalRenderable visible = stack.getVisibleContent();
        if (visible != null) {
            TerminalLayoutContext ctx = findRegionContext(contexts, region);
            return readDimension(ctx, stack, false);
        }

        return defaultH > 0 ? defaultH : 0;
    }

    /**
     * Resolves the width contribution of a LEFT or RIGHT region.
     * Content measurement wins when the slot is occupied.
     * The default only applies when the slot is empty — reserving space, not overriding content.
     * Returns 0 when neither is applicable.
     */
    private int resolveRegionWidth(TerminalLayoutContext[] contexts, BorderPanel region, int defaultW) {
        TerminalStackPanel stack = regionStacks.get(region);
        if (stack == null || stack.isLayoutExcluded()) return 0;

        TerminalRenderable visible = stack.getVisibleContent();
        if (visible != null) {
            TerminalLayoutContext ctx = findRegionContext(contexts, region);
            return readDimension(ctx, stack, true);
        }

        return defaultW > 0 ? defaultW : 0;
    }

    /**
     * Finds the layout context for a named region stack.
     */
    private TerminalLayoutContext findRegionContext(TerminalLayoutContext[] contexts, BorderPanel region) {
        TerminalStackPanel stack = regionStacks.get(region);
        return stack != null ? findContext(contexts, stack) : null;
    }

    /**
     * Reads a single dimension from a stack's context.
     * Priority: measuredContentBounds → requestedRegion → currentRegion.
     */
    private int readDimension(TerminalLayoutContext ctx, TerminalRenderable child, boolean isWidth) {
        if (ctx != null) {
            TerminalRectangle bounds = ctx.getMeasuredContentBounds();
            if (bounds != null) return isWidth ? bounds.getWidth() : bounds.getHeight();

            TerminalRectangle requested = ctx.getRequestedRegion();
            if (requested != null) return isWidth ? requested.getWidth() : requested.getHeight();
        }
        TerminalRectangle current = child.getRegion();
        return isWidth ? current.getWidth() : current.getHeight();
    }
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        // Border panel doesn't render anything itself
    }
    
    @Override
    protected void onDestroying() {
        destroyLayoutGroup(layoutGroupId);
        layoutCallback = null;
    }

  
}