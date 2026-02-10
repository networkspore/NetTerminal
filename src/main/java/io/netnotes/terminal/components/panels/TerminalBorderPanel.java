package io.netnotes.terminal.components.panels;

import java.util.EnumMap;
import java.util.Map;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.layout.TerminalGroupCallbackEntry;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutable;
import io.netnotes.terminal.layout.TerminalSizeable;
import io.netnotes.engine.ui.layout.LayoutGroup.LayoutDataInterface;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.components.TerminalRegion;

/**
 * A border layout panel with 5 regions: TOP, BOTTOM, LEFT, RIGHT, CENTER.
 * Each region uses a TerminalStackPanel internally to support multiple
 * renderables with only one visible at a time.
 */
public class TerminalBorderPanel extends TerminalRegion {

    public enum Panel {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT,
        CENTER
    }
    
    private final TerminalInsets padding = new TerminalInsets();
    private final EnumMap<Panel, TerminalStackPanel> regionStacks = new EnumMap<>(Panel.class);
    
    // Default sizes for regions when children don't specify (use -1 for "not set")
    private int defaultTopHeight = -1;
    private int defaultBottomHeight = -1;
    private int defaultLeftWidth = -1;
    private int defaultRightWidth = -1;


    private SizePreference widthPreference = null;
    private SizePreference heightPreference = null;
    
    private final String layoutGroupId;
    private final String layoutCallbackId;
    private TerminalGroupCallbackEntry layoutCallbackEntry = null;
    
    public TerminalBorderPanel(String name) {
        super(name);
        this.layoutGroupId = "borderpanel-" + getName();
        this.layoutCallbackId = "borderpanel-default";
        
        // Create a stack panel for each region
        for (Panel panel : Panel.values()) {
            TerminalStackPanel stack = new TerminalStackPanel(name + "-" + panel.name().toLowerCase());
            regionStacks.put(panel, stack);
            addChild(stack);
        }
        
        init();
    }

    private void init() {
        this.layoutCallbackEntry = new TerminalGroupCallbackEntry(
            getLayoutCallbackId(),
            this::layoutAllPanels
        );
        registerGroupCallback(getLayoutGroupId(), layoutCallbackEntry);
        
        // Add all stack panels to the layout group
        for (TerminalStackPanel stack : regionStacks.values()) {
            addToLayoutGroup(stack, layoutGroupId);
        }
    }

    public TerminalGroupCallbackEntry getTerminalGroupCallbackEntry() { 
        return layoutCallbackEntry; 
    }

    public String getLayoutCallbackId() {
        return layoutCallbackId;
    }
    
    public String getLayoutGroupId() {
        return layoutGroupId;
    }
    
    public void setPadding(int padding) {
        int clamped = Math.max(0, padding);
        if (this.padding.getTop() != clamped ||
            this.padding.getRight() != clamped ||
            this.padding.getBottom() != clamped ||
            this.padding.getLeft() != clamped) {
            this.padding.setAll(clamped);
            requestLayoutUpdate();
        }
    }

    public void setInsets(TerminalInsets padding) {
        if (padding == null) {
            if (!this.padding.isZero()) {
                this.padding.clear();
                requestLayoutUpdate();
            }
            return;
        }

        if (!this.padding.equals(padding)) {
            this.padding.copyFrom(padding);
            requestLayoutUpdate();
        }
    }
    
    
    public TerminalInsets getInsets() {
        return padding;
    }

    @Override
    public void setPercentWidth(float percent) {
        super.setPercentWidth(percent);
        requestLayoutUpdate();
    }

    @Override
    public void setPercentHeight(float percent) {
        super.setPercentHeight(percent);
        requestLayoutUpdate();
    }
    
    /**
     * Set default height for TOP region when child doesn't specify size.
     * Use -1 to disable (will calculate from child).
     */
    public void setDefaultTopHeight(int height) {
        if (this.defaultTopHeight != height) {
            this.defaultTopHeight = height;
            requestLayoutUpdate();
        }
    }
    
    /**
     * Set default height for BOTTOM region when child doesn't specify size.
     * Use -1 to disable (will calculate from child).
     */
    public void setDefaultBottomHeight(int height) {
        if (this.defaultBottomHeight != height) {
            this.defaultBottomHeight = height;
            requestLayoutUpdate();
        }
    }
    
    /**
     * Set default width for LEFT region when child doesn't specify size.
     * Use -1 to disable (will calculate from child).
     */
    public void setDefaultLeftWidth(int width) {
        if (this.defaultLeftWidth != width) {
            this.defaultLeftWidth = width;
            requestLayoutUpdate();
        }
    }
    
    /**
     * Set default width for RIGHT region when child doesn't specify size.
     * Use -1 to disable (will calculate from child).
     */
    public void setDefaultRightWidth(int width) {
        if (this.defaultRightWidth != width) {
            this.defaultRightWidth = width;
            requestLayoutUpdate();
        }
    }
    
    public int getDefaultTopHeight() { return defaultTopHeight; }
    public int getDefaultBottomHeight() { return defaultBottomHeight; }
    public int getDefaultLeftWidth() { return defaultLeftWidth; }
    public int getDefaultRightWidth() { return defaultRightWidth; }
    
    /**
     * Set a single child for a region, replacing any existing content.
     * The stack for that region will be cleared and only this child will be added.
     */
    public void setPanel(Panel region, TerminalRenderable child) {
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
    public void swapPanel(Panel region, TerminalRenderable newChild) {
        if (region == null) {
            throw new IllegalArgumentException("Panel cannot be null");
        }
        
        if (newChild == null) {
            return;
        }
        
        TerminalStackPanel stack = regionStacks.get(region);
        
        // Add to stack if not already present
        if (!stack.contains(newChild)) {
            stack.addToStack(newChild);
        }
        
        // Make it the visible content
        stack.setVisibleContent(newChild);
        
        requestLayoutUpdate();
    }
    
    /**
     * Add a child to a region's stack without making it visible.
     * Useful for pre-loading content that will be swapped to later.
     */
    public void addToPanel(Panel region, TerminalRenderable child) {
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
    public void removeFromPanel(Panel region, TerminalRenderable child) {
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
    public TerminalRenderable getPanel(Panel region) {
        if (region == null) {
            return null;
        }
        
        TerminalStackPanel stack = regionStacks.get(region);
        return stack.getVisibleContent();
    }
    
    /**
     * Get the stack panel for a region (allows direct access to all stack operations).
     */
    public TerminalStackPanel getRegionStack(Panel region) {
        return regionStacks.get(region);
    }
    
    /**
     * Clear all content from a region.
     */
    public void clearPanel(Panel region) {
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
        for (Panel region : Panel.values()) {
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
        if (contexts.length == 0) return;
        
        TerminalRectangle parentPanel = contexts[0].getParentRegion();
        if (parentPanel == null) return;
        
        int horizontalPadding = padding.getHorizontal();
        int verticalPadding = padding.getVertical();

        int availableWidth = parentPanel.getWidth() - horizontalPadding;
        int availableHeight = parentPanel.getHeight() - verticalPadding;
        
        // Calculate dimensions for each region
        int topHeight = 0;
        int bottomHeight = 0;
        int leftWidth = 0;
        int rightWidth = 0;
        
        TerminalStackPanel topStack = regionStacks.get(Panel.TOP);
        TerminalRenderable topChild = topStack.getVisibleContent();
        if (topChild != null && shouldIncludeInLayout(topStack)) {
            if (defaultTopHeight > 0) {
                topHeight = defaultTopHeight;
            } else {
                topHeight = calculatePreferredHeight(topChild, availableWidth);
            }
            topHeight = Math.min(topHeight, availableHeight);
        }
        
        TerminalStackPanel bottomStack = regionStacks.get(Panel.BOTTOM);
        TerminalRenderable bottomChild = bottomStack.getVisibleContent();
        if (bottomChild != null && shouldIncludeInLayout(bottomStack)) {
            if (defaultBottomHeight > 0) {
                bottomHeight = defaultBottomHeight;
            } else {
                bottomHeight = calculatePreferredHeight(bottomChild, availableWidth);
            }
            int remainingHeight = availableHeight - topHeight;
            bottomHeight = Math.min(bottomHeight, remainingHeight);
        }
        
        int middleHeight = availableHeight - topHeight - bottomHeight;
        int middleY = padding.getTop() + topHeight;
        
        TerminalStackPanel leftStack = regionStacks.get(Panel.LEFT);
        TerminalRenderable leftChild = leftStack.getVisibleContent();
        if (leftChild != null && shouldIncludeInLayout(leftStack)) {
            if (defaultLeftWidth > 0) {
                leftWidth = defaultLeftWidth;
            } else {
                leftWidth = calculatePreferredWidth(leftChild, middleHeight);
            }
            leftWidth = Math.min(leftWidth, availableWidth);
        }
        
        TerminalStackPanel rightStack = regionStacks.get(Panel.RIGHT);
        TerminalRenderable rightChild = rightStack.getVisibleContent();
        if (rightChild != null && shouldIncludeInLayout(rightStack)) {
            if (defaultRightWidth > 0) {
                rightWidth = defaultRightWidth;
            } else {
                rightWidth = calculatePreferredWidth(rightChild, middleHeight);
            }
            int remainingWidth = availableWidth - leftWidth;
            rightWidth = Math.min(rightWidth, remainingWidth);
        }
        
        // Layout each stack panel
        layoutStackPanel(dataInterfaces, topStack, 
            padding.getLeft(), padding.getTop(), availableWidth, topHeight, parentPanel);
        
        layoutStackPanel(dataInterfaces, bottomStack,
            padding.getLeft(), padding.getTop() + availableHeight - bottomHeight, 
            availableWidth, bottomHeight, parentPanel);
        
        layoutStackPanel(dataInterfaces, leftStack,
            padding.getLeft(), middleY, leftWidth, middleHeight, parentPanel);
        
        layoutStackPanel(dataInterfaces, rightStack,
            padding.getLeft() + availableWidth - rightWidth, middleY, 
            rightWidth, middleHeight, parentPanel);
        
        int centerWidth = availableWidth - leftWidth - rightWidth;
        int centerX = padding.getLeft() + leftWidth;
        layoutStackPanel(dataInterfaces, regionStacks.get(Panel.CENTER),
            centerX, middleY, Math.max(0, centerWidth), Math.max(0, middleHeight), parentPanel);
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
        
        TerminalLayoutData.TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder()
            .setX(x)
            .setY(y)
            .setWidth(width)
            .setHeight(height);

        if (!inBounds) {
            builder.hidden(true);
        } else if (shouldManageHidden(stack)) {
            // Show the stack if it has visible content, hide it if empty
            boolean hasVisibleContent = stack.getVisibleContent() != null;
            builder.hidden(!hasVisibleContent);
        }

        TerminalLayoutData layout = builder.build();
        dataInterfaces.get(stack.getName()).setLayoutData(layout);
    }
    
    private int calculatePreferredHeight(TerminalRenderable child, int availableWidth) {
        if (child.getRequestedRegion() != null) {
            return child.getRequestedRegion().getHeight();
        }
        
        if (child instanceof TerminalSizeable) {
            TerminalSizeable sizeable = (TerminalSizeable) child;
            SizePreference heightPref = sizeable.getHeightPreference();
            
            if (heightPref == SizePreference.FILL) {
                // For FILL preference, use minimum height as the "preferred" size
                // The actual fill will happen during layout
                return sizeable.getMinHeight();
            } else if (heightPref == SizePreference.FIT_CONTENT) {
                // Use the child's preferred height
                return sizeable.getPreferredHeight();
            }
        }
        
        // Fallback: use minimum sensible height
        return 1;
    }
    
    private int calculatePreferredWidth(TerminalRenderable child, int availableHeight) {
        if (child.getRequestedRegion() != null) {
            return child.getRequestedRegion().getWidth();
        }
        
        if (child instanceof TerminalSizeable) {
            TerminalSizeable sizeable = (TerminalSizeable) child;
            SizePreference widthPref = sizeable.getWidthPreference();
            
            if (widthPref == SizePreference.FILL) {
                // For FILL preference, use minimum width as the "preferred" size
                // The actual fill will happen during layout
                return sizeable.getMinWidth();
            } else if (widthPref == SizePreference.FIT_CONTENT) {
                // Use the child's preferred width
                return sizeable.getPreferredWidth();
            }
        }
        
        // Fallback: use minimum sensible width
        return 1;
    }

    private boolean shouldIncludeInLayout(TerminalRenderable child) {
        if (child.isHidden()) {
            return false;
        }
        
        if (child.isInvisible()) {
            return true;
        }
        
        return true;
    }

    private boolean shouldManageHidden(TerminalRenderable child) {
        if (child instanceof TerminalLayoutable) {
            return ((TerminalLayoutable) child).isHiddenManaged();
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
    
    public void setWidthPreference(SizePreference widthPreference) {
        this.widthPreference = widthPreference;
        invalidate();
    }

    public void setHeightPreference(SizePreference heightPreference) {
        this.heightPreference = heightPreference;
        invalidate();
    }

    @Override
    public SizePreference getWidthPreference() {
        if(widthPreference != null){
            return widthPreference;
        }
        TerminalStackPanel centerStack = regionStacks.get(Panel.CENTER);
        if (centerStack != null) {
            return centerStack.getWidthPreference();
        }
        return SizePreference.FIT_CONTENT;
    }
    
    @Override
    public SizePreference getHeightPreference() {
        if(heightPreference != null){
            return heightPreference;
        }
        TerminalStackPanel centerStack = regionStacks.get(Panel.CENTER);
        if (centerStack != null) {
            return centerStack.getHeightPreference();
        }
        return SizePreference.FIT_CONTENT;
    }

    private int minWidth = -1;
    private int minHeight = -1;
    
    

    public void setMinWidth(int minWidth) {
        this.minWidth = minWidth;
        invalidate();
    }

    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
        invalidate();
    }

    @Override
    public int getMinWidth() {
        TerminalStackPanel centerStack = regionStacks.get(Panel.CENTER);
        int centerMin = centerStack != null ? centerStack.getMinWidth() : -1;
        return Math.max(1, Math.max(centerMin, minWidth)) + padding.getHorizontal();
    }
    
    @Override
    public int getMinHeight() {
        TerminalStackPanel centerStack = regionStacks.get(Panel.CENTER);
        int centerMin = centerStack != null ? centerStack.getMinHeight() : -1;
        return Math.max(1, Math.max(centerMin, minHeight)) + padding.getVertical();
    }
    
    @Override
    public int getPreferredWidth() {
        if (widthPreference == SizePreference.STATIC) {
            return region.getWidth();
        }
        if (widthPreference == SizePreference.PERCENT) {
            return getMinWidth();
        }
        TerminalStackPanel centerStack = regionStacks.get(Panel.CENTER);
        if (centerStack != null) {
            int centerPref = centerStack.getPreferredWidth();
            // Add padding and side panels
            int leftWidth = 0;
            int rightWidth = 0;
            
            // Check if we have explicit defaults
            if (defaultLeftWidth > 0) {
                leftWidth = defaultLeftWidth;
            } else {
                TerminalRenderable leftChild = regionStacks.get(Panel.LEFT).getVisibleContent();
                if (leftChild != null && leftChild instanceof TerminalSizeable) {
                    leftWidth = ((TerminalSizeable) leftChild).getPreferredWidth();
                }
            }
            
            if (defaultRightWidth > 0) {
                rightWidth = defaultRightWidth;
            } else {
                TerminalRenderable rightChild = regionStacks.get(Panel.RIGHT).getVisibleContent();
                if (rightChild != null && rightChild instanceof TerminalSizeable) {
                    rightWidth = ((TerminalSizeable) rightChild).getPreferredWidth();
                }
            }
            
            return centerPref + leftWidth + rightWidth + padding.getHorizontal();
        }
        return getMinWidth();
    }
    
    @Override
    public int getPreferredHeight() {
        if (heightPreference == SizePreference.STATIC) {
            return region.getHeight();
        }
        if (heightPreference == SizePreference.PERCENT) {
            return getMinHeight();
        }
        TerminalStackPanel centerStack = regionStacks.get(Panel.CENTER);
        if (centerStack != null) {
            int centerPref = centerStack.getPreferredHeight();
            // Add padding and top/bottom panels
            int topHeight = 0;
            int bottomHeight = 0;
            
            // Check if we have explicit defaults
            if (defaultTopHeight > 0) {
                topHeight = defaultTopHeight;
            } else {
                TerminalRenderable topChild = regionStacks.get(Panel.TOP).getVisibleContent();
                if (topChild != null && topChild instanceof TerminalSizeable) {
                    topHeight = ((TerminalSizeable) topChild).getPreferredHeight();
                }
            }
            
            if (defaultBottomHeight > 0) {
                bottomHeight = defaultBottomHeight;
            } else {
                TerminalRenderable bottomChild = regionStacks.get(Panel.BOTTOM).getVisibleContent();
                if (bottomChild != null && bottomChild instanceof TerminalSizeable) {
                    bottomHeight = ((TerminalSizeable) bottomChild).getPreferredHeight();
                }
            }
            
            return centerPref + topHeight + bottomHeight + padding.getVertical();
        }
        return getMinHeight();
    }
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        // Border panel doesn't render anything itself
    }
    
    @Override
    protected void onCleanup() {
        destroyLayoutGroup(layoutGroupId);
        layoutCallbackEntry = null;
    }
}
