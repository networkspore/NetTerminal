package io.netnotes.terminal.components.panels;

import java.util.EnumMap;
import java.util.Map;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.layout.TerminalGroupCallbackEntry;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalSizeable;
import io.netnotes.engine.ui.BorderPanel;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.renderer.layout.LayoutGroup.LayoutDataInterface;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.LoggingHelpers.LogLevel;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.components.TerminalRegion;

/**
 * A border layout panel with 5 regions: TOP, BOTTOM, LEFT, RIGHT, CENTER.
 * Each region uses a TerminalStackPanel internally to support multiple
 * renderables with only one visible at a time.
 */
public class TerminalBorderPanel extends TerminalRegion {

    
    
    private final EnumMap<BorderPanel, TerminalStackPanel> regionStacks = new EnumMap<>(BorderPanel.class);
    
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
        String className = this.getClass().getName();
        className = className.substring(className.lastIndexOf(".") + 1);
        this.layoutGroupId = className + "-" + getName();
        this.layoutCallbackId = layoutGroupId + "-callback";
        
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
        
        // Calculate dimensions for each region
        int topHeight = 0;
        int bottomHeight = 0;
        int leftWidth = 0;
        int rightWidth = 0;
        
        TerminalStackPanel topStack = regionStacks.get(BorderPanel.TOP);
        TerminalRenderable topChild = topStack.getVisibleContent();
        if (topChild != null && shouldIncludeInLayout(topStack)) {
            if (defaultTopHeight > 0) {
                topHeight = defaultTopHeight;
            } else {
                topHeight = calculatePreferredHeight(topChild, availableWidth);
            }
            topHeight = Math.min(topHeight, availableHeight);
        }

        TerminalStackPanel bottomStack = regionStacks.get(BorderPanel.BOTTOM);
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
        
        int middleHeight = Math.max(0, availableHeight - topHeight - bottomHeight);
        int middleY = insets.getTop() + topHeight;
        
        TerminalStackPanel leftStack = regionStacks.get(BorderPanel.LEFT);
        TerminalRenderable leftChild = leftStack.getVisibleContent();
        if (leftChild != null && shouldIncludeInLayout(leftStack)) {
            if (defaultLeftWidth > 0) {
                leftWidth = defaultLeftWidth;
            } else {
                leftWidth = calculatePreferredWidth(leftChild, middleHeight);
            }
            leftWidth = Math.min(leftWidth, availableWidth);
        }
        
        TerminalStackPanel rightStack = regionStacks.get(BorderPanel.RIGHT);
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
            insets.getLeft(), insets.getTop(), availableWidth, topHeight, parentPanel);
        
        layoutStackPanel(dataInterfaces, bottomStack,
            insets.getLeft(), insets.getTop() + availableHeight - bottomHeight, 
            availableWidth, bottomHeight, parentPanel);
        
        layoutStackPanel(dataInterfaces, leftStack,
            insets.getLeft(), middleY, leftWidth, middleHeight, parentPanel);
        
        layoutStackPanel(dataInterfaces, rightStack,
            insets.getLeft() + availableWidth - rightWidth, middleY, 
            rightWidth, middleHeight, parentPanel);
        
        int centerWidth = availableWidth - leftWidth - rightWidth;
        int centerX = insets.getLeft() + leftWidth;
        TerminalStackPanel centerStack = regionStacks.get(BorderPanel.CENTER);
        layoutStackPanel(dataInterfaces, centerStack,
            centerX, middleY, Math.max(0, centerWidth), middleHeight, parentPanel);

        Log.logMsg((this instanceof TerminalScrollPanel 
                ? "[TerminalScrollPanel]" 
                : "[TerminalBorderPanel]"
            )
            + "\n\tTop Stack:" + availableWidth + " , " + topHeight
            + "\n\tCenterStack:" + centerWidth +" , " + middleHeight 
        , LogLevel.IMPORTANT);
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
        if (child instanceof TerminalSizeable sizeable) {
            SizePreference heightPref = sizeable.getHeightPreference();
            
            return switch (heightPref) {
                case STATIC -> child.getRequestedRegion() != null 
                    ? child.getRequestedRegion().getHeight() 
                    : child.getRegion().getHeight();
                case FILL   -> sizeable.getMinHeight();
                case PERCENT -> sizeable.getMinHeight();
                case FIT_CONTENT -> sizeable.getPreferredHeight();
                default -> sizeable.getMinHeight();
            };
        }
        
        // Non-sizeable: requestedRegion is the only hint we have
        if (child.getRequestedRegion() != null) {
            return child.getRequestedRegion().getHeight();
        }
        
        return 1;
    }
    
    private int calculatePreferredWidth(TerminalRenderable child, int availableHeight) {
        if (child instanceof TerminalSizeable sizeable) {
            SizePreference widthPref = sizeable.getWidthPreference();
            
            return switch (widthPref) {
                case STATIC -> child.getRequestedRegion() != null 
                    ? child.getRequestedRegion().getWidth() 
                    : child.getRegion().getWidth();
                case FILL    -> sizeable.getMinWidth();
                case PERCENT -> sizeable.getMinWidth();
                case FIT_CONTENT -> sizeable.getPreferredWidth();
                default -> sizeable.getMinWidth();
            };
        }
        
        // Non-sizeable: requestedRegion is the only hint we have
        if (child.getRequestedRegion() != null) {
            return child.getRequestedRegion().getWidth();
        }
        
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
    
    public void setWidthPreference(SizePreference widthPreference) {
        this.widthPreference = widthPreference;
        requestLayoutUpdate();
    }

    public void setHeightPreference(SizePreference heightPreference) {
        this.heightPreference = heightPreference;
        requestLayoutUpdate();
    }

    @Override
    public SizePreference getWidthPreference() {
        if(widthPreference != null){
            return widthPreference;
        }
        TerminalStackPanel centerStack = regionStacks.get(BorderPanel.CENTER);
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
        TerminalStackPanel centerStack = regionStacks.get(BorderPanel.CENTER);
        if (centerStack != null) {
            return centerStack.getHeightPreference();
        }
        return SizePreference.FIT_CONTENT;
    }

    private int minWidth = -1;
    private int minHeight = -1;
    
    

    public void setMinWidth(int minWidth) {
        this.minWidth = minWidth;
        requestLayoutUpdate();
    }

    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
        requestLayoutUpdate();
    }

    @Override
    public int getMinWidth() {
        TerminalStackPanel centerStack = regionStacks.get(BorderPanel.CENTER);
        int centerMin = centerStack != null ? centerStack.getMinWidth() : -1;
        return Math.max(1, Math.max(centerMin, minWidth)) + insets.getHorizontal();
    }
    
    @Override
    public int getMinHeight() {
        TerminalStackPanel centerStack = regionStacks.get(BorderPanel.CENTER);
        int centerMin = centerStack != null ? centerStack.getMinHeight() : -1;
        return Math.max(1, Math.max(centerMin, minHeight)) + insets.getVertical();
    }
    
    @Override
    public int getPreferredWidth() {
        if (widthPreference == SizePreference.STATIC) {
            return region.getWidth();
        }
        if (widthPreference == SizePreference.PERCENT) {
            return getMinWidth();
        }
        TerminalStackPanel centerStack = regionStacks.get(BorderPanel.CENTER);
        if (centerStack != null) {
            int centerPref = centerStack.getPreferredWidth();
            // Add insets and side panels
            int leftWidth = 0;
            int rightWidth = 0;
            
            // Check if we have explicit defaults
            if (defaultLeftWidth > 0) {
                leftWidth = defaultLeftWidth;
            } else {
                TerminalRenderable leftChild = regionStacks.get(BorderPanel.LEFT).getVisibleContent();
                if (leftChild != null && leftChild instanceof TerminalSizeable) {
                    leftWidth = ((TerminalSizeable) leftChild).getPreferredWidth();
                }
            }
            
            if (defaultRightWidth > 0) {
                rightWidth = defaultRightWidth;
            } else {
                TerminalRenderable rightChild = regionStacks.get(BorderPanel.RIGHT).getVisibleContent();
                if (rightChild != null && rightChild instanceof TerminalSizeable) {
                    rightWidth = ((TerminalSizeable) rightChild).getPreferredWidth();
                }
            }
            
            return centerPref + leftWidth + rightWidth + insets.getHorizontal();
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
        TerminalStackPanel centerStack = regionStacks.get(BorderPanel.CENTER);
        if (centerStack != null) {
            int centerPref = centerStack.getPreferredHeight();
            // Add insets and top/bottom panels
            int topHeight = 0;
            int bottomHeight = 0;
            
            // Check if we have explicit defaults
            if (defaultTopHeight > 0) {
                topHeight = defaultTopHeight;
            } else {
                TerminalRenderable topChild = regionStacks.get(BorderPanel.TOP).getVisibleContent();
                if (topChild != null && topChild instanceof TerminalSizeable) {
                    topHeight = ((TerminalSizeable) topChild).getPreferredHeight();
                }
            }
            
            if (defaultBottomHeight > 0) {
                bottomHeight = defaultBottomHeight;
            } else {
                TerminalRenderable bottomChild = regionStacks.get(BorderPanel.BOTTOM).getVisibleContent();
                if (bottomChild != null && bottomChild instanceof TerminalSizeable) {
                    bottomHeight = ((TerminalSizeable) bottomChild).getPreferredHeight();
                }
            }
            
            return centerPref + topHeight + bottomHeight + insets.getVertical();
        }
        return getMinHeight();
    }
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        // Border panel doesn't render anything itself
    }
    
    @Override
    protected void onDestroying() {
        destroyLayoutGroup(layoutGroupId);
        layoutCallbackEntry = null;
    }

  
}
