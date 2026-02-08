package io.netnotes.terminal.components.panels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.layout.TerminalGroupCallbackEntry;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutable;
import io.netnotes.terminal.layout.TerminalSizeable;
import io.netnotes.engine.ui.layout.LayoutGroup.LayoutDataInterface;

/**
 * A panel that can contain multiple renderables stacked in the same space,
 * but only one is visible at a time. The visibility policy ensures that
 * only the designated visibleContent can be made visible.
 * 
 * Implements TerminalSizeable to delegate sizing to the visible content.
 * Supports scroll offsets and content padding for use in scrollable containers.
 * Enforces unique renderable names within the stack.
 */
public class TerminalStackPanel extends TerminalRenderable implements TerminalSizeable {
    
    private final List<TerminalRenderable> stack = new ArrayList<>();
    private final Map<String, TerminalRenderable> nameToRenderable = new HashMap<>();
    private TerminalRenderable visibleContent = null;
    
    private int scrollOffsetX = 0;
    private int scrollOffsetY = 0;
    private final TerminalInsets contentPadding = new TerminalInsets();
    
    // For scroll-aware sizing (content can be larger than viewport)
    private TerminalRectangle contentSize = null;
    
    private final String layoutGroupId;
    private final String layoutCallbackId;
    private TerminalGroupCallbackEntry layoutCallbackEntry = null;
    
    public TerminalStackPanel(String name) {
        super(name);
        this.layoutGroupId = "stackpanel-" + getName();
        this.layoutCallbackId = "stackpanel-default";
        init();
    }
    
    private void init() {
        this.layoutCallbackEntry = new TerminalGroupCallbackEntry(
            getLayoutCallbackId(),
            this::layoutStack
        );
        registerGroupCallback(getLayoutGroupId(), layoutCallbackEntry);
    }
    
    public String getLayoutCallbackId() {
        return layoutCallbackId;
    }
    
    public String getLayoutGroupId() {
        return layoutGroupId;
    }
    
    public TerminalGroupCallbackEntry getTerminalGroupCallbackEntry() {
        return layoutCallbackEntry;
    }
    
    /**
     * Visibility policy: only the designated visibleContent can be visible.
     * - If trying to hide, always allow (return true)
     * - If trying to show, only allow if it's the visibleContent
     */
    private boolean visibilityPolicy(TerminalRenderable renderable, boolean isVisible) {
        if (!isVisible) return true; // Always allow hiding
        if (renderable == visibleContent) return true; // Allow showing if it's the designated visible content
        return false; // Prevent showing any other content
    }
    
    /**
     * Set scroll offset for the content. This shifts the content's position
     * within the stack panel's viewport.
     */
    public void setScrollOffset(int x, int y) {
        int clampedX = Math.max(0, x);
        int clampedY = Math.max(0, y);
        
        if (this.scrollOffsetX != clampedX || this.scrollOffsetY != clampedY) {
            this.scrollOffsetX = clampedX;
            this.scrollOffsetY = clampedY;
            requestLayoutUpdate();
        }
    }
    
    public int getScrollOffsetX() {
        return scrollOffsetX;
    }
    
    public int getScrollOffsetY() {
        return scrollOffsetY;
    }
    
    /**
     * Set content padding. This creates internal spacing within the stack panel.
     */
    public void setContentPadding(TerminalInsets padding) {
        if (padding == null) {
            if (!this.contentPadding.isZero()) {
                this.contentPadding.clear();
                requestLayoutUpdate();
            }
            return;
        }
        
        if (!this.contentPadding.equals(padding)) {
            this.contentPadding.copyFrom(padding);
            requestLayoutUpdate();
        }
    }
    
    public void setContentPadding(int padding) {
        int clamped = Math.max(0, padding);
        if (this.contentPadding.getTop() != clamped ||
            this.contentPadding.getRight() != clamped ||
            this.contentPadding.getBottom() != clamped ||
            this.contentPadding.getLeft() != clamped) {
            this.contentPadding.setAll(clamped);
            requestLayoutUpdate();
        }
    }
    
    public TerminalInsets getContentPadding() {
        return contentPadding;
    }
    
    /**
     * Set the desired content size. Used when content should be larger than the viewport
     * (e.g., for scrolling). If null, content fills the available space.
     */
    public void setContentSize(TerminalRectangle size) {
        if (this.contentSize != size && (this.contentSize == null || !this.contentSize.equals(size))) {
            this.contentSize = size;
            requestLayoutUpdate();
        }
    }
    
    public TerminalRectangle getContentSize() {
        return contentSize;
    }
    
    /**
     * Add a renderable to the stack. It will be hidden by default unless it's
     * the first item and no visible content is set.
     * 
     * @throws IllegalArgumentException if a renderable with the same name already exists
     */
    public void addToStack(TerminalRenderable renderable) {
        if (renderable == null) {
            throw new IllegalArgumentException("Cannot add null renderable to stack");
        }
        
        String name = renderable.getName();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Renderable must have a non-empty name");
        }
        
        if (nameToRenderable.containsKey(name)) {
            throw new IllegalArgumentException("Renderable with name '" + name + "' already exists in stack");
        }
        
        if (stack.contains(renderable)) {
            return; // Already in stack (shouldn't happen if names are unique, but defensive)
        }
        
        stack.add(renderable);
        nameToRenderable.put(name, renderable);
        addChild(renderable);
        addToLayoutGroup(renderable, layoutGroupId);
        
        // Set visibility policy for the renderable
        renderable.setVisibilityPolicy(this::visibilityPolicy);
        
        // If this is the first item and no visible content is set, make it visible
        if (stack.size() == 1 && visibleContent == null) {
            setVisibleContent(renderable);
        } else {
            // Otherwise, hide it
            renderable.hide();
        }
        
        requestLayoutUpdate();
    }
    
    /**
     * Remove a renderable from the stack by reference.
     */
    public void removeFromStack(TerminalRenderable renderable) {
        if (!stack.contains(renderable)) {
            return;
        }
        
        stack.remove(renderable);
        nameToRenderable.remove(renderable.getName());
        removeChild(renderable);
        
        // Clear visibility policy
        renderable.setVisibilityPolicy(null);
        
        // If we're removing the visible content, show the next one if available
        if (renderable == visibleContent) {
            visibleContent = null;
            if (!stack.isEmpty()) {
                setVisibleContent(stack.get(0));
            }
        }
        
        requestLayoutUpdate();
    }
    
    /**
     * Remove a renderable from the stack by name.
     */
    public void removeFromStack(String name) {
        TerminalRenderable renderable = nameToRenderable.get(name);
        if (renderable != null) {
            removeFromStack(renderable);
        }
    }
    
    /**
     * Set which renderable should be visible by reference. This will hide all others.
     */
    public void setVisibleContent(TerminalRenderable renderable) {
        if (renderable != null && !stack.contains(renderable)) {
            throw new IllegalArgumentException("Renderable must be in stack before setting as visible");
        }
        
        // Hide all current content
        for (TerminalRenderable item : stack) {
            if (item != renderable && !item.isHidden()) {
                item.hide();
            }
        }
        
        // Set the new visible content
        visibleContent = renderable;
        
        // Show the new visible content if it's not null
        if (visibleContent != null && visibleContent.isHidden()) {
            visibleContent.show();
        }
        
        requestLayoutUpdate();
    }
    
    /**
     * Set which renderable should be visible by name. This will hide all others.
     */
    public void setVisibleContent(String name) {
        TerminalRenderable renderable = nameToRenderable.get(name);
        if (renderable == null) {
            throw new IllegalArgumentException("No renderable with name '" + name + "' exists in stack");
        }
        setVisibleContent(renderable);
    }
    
    /**
     * Get the currently visible content.
     */
    public TerminalRenderable getVisibleContent() {
        return visibleContent;
    }
    
    /**
     * Clear all content from the stack.
     */
    public void clearStack() {
        for (TerminalRenderable renderable : new ArrayList<>(stack)) {
            removeFromStack(renderable);
        }
    }
    
    /**
     * Get all renderables in the stack (including hidden ones).
     */
    public List<TerminalRenderable> getStackContents() {
        return new ArrayList<>(stack);
    }
    
    /**
     * Check if a renderable is in the stack by reference.
     */
    public boolean contains(TerminalRenderable renderable) {
        return stack.contains(renderable);
    }
    
    /**
     * Check if a renderable with the given name is in the stack.
     */
    public boolean contains(String name) {
        return nameToRenderable.containsKey(name);
    }
    
    /**
     * Get a renderable by name.
     */
    public TerminalRenderable getContent(String name) {
        return nameToRenderable.get(name);
    }
    
    /**
     * Get the number of items in the stack.
     */
    public int getStackSize() {
        return stack.size();
    }
    
    /**
     * Check if the stack is empty.
     */
    public boolean isEmpty() {
        return stack.isEmpty();
    }
    
    /**
     * Get the index of a renderable in the stack.
     * Returns -1 if not found.
     */
    public int indexOf(TerminalRenderable renderable) {
        return stack.indexOf(renderable);
    }
    
    /**
     * Get the index of a renderable by name.
     * Returns -1 if not found.
     */
    public int indexOf(String name) {
        TerminalRenderable renderable = nameToRenderable.get(name);
        return renderable != null ? stack.indexOf(renderable) : -1;
    }
    
    /**
     * Get content at a specific index.
     * Returns null if index is out of bounds.
     */
    public TerminalRenderable getContentAt(int index) {
        if (index < 0 || index >= stack.size()) {
            return null;
        }
        return stack.get(index);
    }
    
    /**
     * Layout callback: all stack items occupy the same space, with scroll offset and padding applied.
     */
    protected void layoutStack(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        if (contexts.length == 0) return;
        
        TerminalRectangle parentPanel = contexts[0].getParentRegion();
        if (parentPanel == null) return;
        
        int panelWidth = parentPanel.getWidth();
        int panelHeight = parentPanel.getHeight();
        
        // Calculate viewport (available space after padding)
        int viewportWidth = panelWidth - contentPadding.getHorizontal();
        int viewportHeight = panelHeight - contentPadding.getVertical();
        
        // Determine content dimensions
        int contentWidth;
        int contentHeight;
        
        if (contentSize != null) {
            // Use explicit content size (for scrolling scenarios)
            contentWidth = contentSize.getWidth();
            contentHeight = contentSize.getHeight();
        } else {
            // Default: content fills viewport
            contentWidth = viewportWidth;
            contentHeight = viewportHeight;
        }
        
        // All items in the stack get the same layout with scroll offset applied
        for (TerminalLayoutContext context : contexts) {
            TerminalRenderable child = context.getRenderable();
            
            if (!stack.contains(child)) {
                continue; // Skip if not in our stack
            }
            
            boolean shouldBeHidden = child.isHidden();
            
            // Apply scroll offset and padding
            int x = contentPadding.getLeft() - scrollOffsetX;
            int y = contentPadding.getTop() - scrollOffsetY;
            
            TerminalLayoutData.TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder()
                .setX(x)
                .setY(y)
                .setWidth(contentWidth)
                .setHeight(contentHeight);
            
            // Only manage hidden state if the child allows it
            if (shouldManageHidden(child)) {
                builder.hidden(shouldBeHidden);
            }
            
            TerminalLayoutData layout = builder.build();
            dataInterfaces.get(child.getName()).setLayoutData(layout);
        }
    }
    
    private boolean shouldManageHidden(TerminalRenderable child) {
        if (child instanceof TerminalLayoutable) {
            return ((TerminalLayoutable) child).isHiddenManaged();
        }
        return true;
    }
    
    // TerminalSizeable implementation - delegate to visible content
    
    @Override
    public SizePreference getWidthPreference() {
        if (visibleContent instanceof TerminalSizeable) {
            return ((TerminalSizeable) visibleContent).getWidthPreference();
        }
        return SizePreference.FIT_CONTENT; // Default
    }
    
    @Override
    public SizePreference getHeightPreference() {
        if (visibleContent instanceof TerminalSizeable) {
            return ((TerminalSizeable) visibleContent).getHeightPreference();
        }
        return SizePreference.FIT_CONTENT; // Default
    }
    
    @Override
    public int getMinWidth() {
        if (visibleContent instanceof TerminalSizeable) {
            return ((TerminalSizeable) visibleContent).getMinWidth();
        }
        return 1; // Minimum sensible width
    }
    
    @Override
    public int getMinHeight() {
        if (visibleContent instanceof TerminalSizeable) {
            return ((TerminalSizeable) visibleContent).getMinHeight();
        }
        return 1; // Minimum sensible height
    }
    
    @Override
    public int getPreferredWidth() {
        if (visibleContent instanceof TerminalSizeable) {
            return ((TerminalSizeable) visibleContent).getPreferredWidth();
        }
        return getMinWidth();
    }
    
    @Override
    public int getPreferredHeight() {
        if (visibleContent instanceof TerminalSizeable) {
            return ((TerminalSizeable) visibleContent).getPreferredHeight();
        }
        return getMinHeight();
    }
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        // Stack panel doesn't render anything itself
    }
    
    @Override
    protected void onCleanup() {
        destroyLayoutGroup(layoutGroupId);
        layoutCallbackEntry = null;
    }
}