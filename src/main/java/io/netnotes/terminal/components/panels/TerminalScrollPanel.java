package io.netnotes.terminal.components.panels;

import java.util.Map;

import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.components.HScrollIndicator;
import io.netnotes.terminal.components.VScrollIndicator;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalSizeable;
import io.netnotes.engine.ui.layout.LayoutGroup.LayoutDataInterface;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.noteBytes.KeyRunTable;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesRunnablePair;
import io.netnotes.engine.ui.ScrollIndicator;

/**
 * A scrollable panel that extends TerminalBorderPanel.
 * Acts as a coordinator/helper that configures the CENTER region StackPanel
 * with scroll offsets and content padding. The actual layout work is delegated
 * to the StackPanel's layout callback.
 * 
 * Supports two scroll modes:
 * - FIT_TO_VIEWPORT: Content resizes to fit viewport, respects minimum size, shows scrollbars when viewport < min
 * - FIXED_SIZE: Content stays at preferred size, shows scrollbars when viewport < content
 * 
 * Implements TerminalSizeable to delegate sizing to the CENTER region stack.
 */
public class TerminalScrollPanel extends TerminalBorderPanel implements TerminalSizeable {
    
    public static final int STATE_INACTIVE = 10;
    public static final int STATE_ACTIVE = 11;
    
    public enum VScrollPosition { LEFT, RIGHT }
    public enum HScrollPosition { TOP, BOTTOM }
    
    public enum ScrollMode {
        /** Content resizes to fit viewport, but respects minimum size. Shows scrollbars when viewport < minimum. */
        FIT_TO_VIEWPORT,
        /** Content stays at preferred size. Shows scrollbars when viewport < content size. */
        FIXED_SIZE
    }
    
    private final TerminalInsets contentPadding = new TerminalInsets();
    
    private int scrollX = 0;
    private int scrollY = 0;
    
    private boolean verticalScrollEnabled = true;
    private boolean horizontalScrollEnabled = false;
    private boolean keyboardScrollEnabled = true;
    private boolean autoShowScrollIndicators = true;
    
    private ScrollMode scrollMode = ScrollMode.FIT_TO_VIEWPORT;
    
    private ScrollIndicator<TerminalRenderable> vScrollIndicator;
    private ScrollIndicator<TerminalRenderable> hScrollIndicator;
    private VScrollPosition vScrollPosition = VScrollPosition.RIGHT;
    private HScrollPosition hScrollPosition = HScrollPosition.BOTTOM;
    
    private int lineScrollAmount = 1;
    private int pageScrollAmount = 0;
    
    private NoteBytesReadOnly keyHandlerId = null;
    private final KeyRunTable keyRunTable = new KeyRunTable(new NoteBytesRunnablePair[]{
        new NoteBytesRunnablePair(KeyCodeBytes.UP, this::scrollLineUp),
        new NoteBytesRunnablePair(KeyCodeBytes.DOWN, this::scrollLineDown),
        new NoteBytesRunnablePair(KeyCodeBytes.LEFT, this::scrollLineLeft),
        new NoteBytesRunnablePair(KeyCodeBytes.RIGHT, this::scrollLineRight),
        new NoteBytesRunnablePair(KeyCodeBytes.PAGE_UP, this::pageUp),
        new NoteBytesRunnablePair(KeyCodeBytes.PAGE_DOWN, this::pageDown),
        new NoteBytesRunnablePair(KeyCodeBytes.HOME, this::scrollToTop),
        new NoteBytesRunnablePair(KeyCodeBytes.END, this::scrollToBottom),
    });
    
    public TerminalScrollPanel(String name) {
        super(name);
        this.vScrollIndicator = new VScrollIndicator(name + "-vscroll");
        this.hScrollIndicator = new HScrollIndicator(name + "-hscroll");
        init();
    }
    
    private void init() {
        updateScrollIndicatorPositions();
    }
    
    @Override
    protected void setupStateTransitions() {
        super.setupStateTransitions();
        
        stateMachine.onStateAdded(STATE_ACTIVE, (old, now, bit) -> {
            if (keyboardScrollEnabled) {
                registerKeyboardHandler();
            }
        });
        
        stateMachine.onStateRemoved(STATE_ACTIVE, (old, now, bit) -> {
            removeKeyboardHandler();
        });
        
        stateMachine.addState(STATE_INACTIVE);
    }
    
    private void registerKeyboardHandler() {
        if (keyHandlerId != null) return;
        keyHandlerId = addKeyDownHandler(this::handleKeyDown);
    }
    
    private void removeKeyboardHandler() {
        if (keyHandlerId != null) {
            removeKeyDownHandler(keyHandlerId);
            keyHandlerId = null;
        }
    }
    
    private void handleKeyDown(RoutedEvent event) {
        if (event instanceof KeyDownEvent kd) {
            keyRunTable.run(kd.getKeyCodeBytes());
        } else if (event instanceof EphemeralKeyDownEvent ekd) {
            keyRunTable.run(ekd.getKeyCodeBytes());
            ekd.close();
        }
    }
    
    public void setContentPadding(int padding) {
        int clamped = Math.max(0, padding);
        if (this.contentPadding.getTop() != clamped ||
            this.contentPadding.getRight() != clamped ||
            this.contentPadding.getBottom() != clamped ||
            this.contentPadding.getLeft() != clamped) {
            this.contentPadding.setAll(clamped);
            
            // Update the CENTER stack panel's padding
            TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
            if (centerStack != null) {
                centerStack.setContentPadding(contentPadding);
            }
            
            requestLayoutUpdate();
        }
    }
    
    public void setContentInsets(TerminalInsets padding) {
        if (padding == null) {
            if (!this.contentPadding.isZero()) {
                this.contentPadding.clear();
                
                // Update the CENTER stack panel's padding
                TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
                if (centerStack != null) {
                    centerStack.setContentPadding(this.contentPadding);
                }
                
                requestLayoutUpdate();
            }
            return;
        }
        
        if (!this.contentPadding.equals(padding)) {
            this.contentPadding.copyFrom(padding);
            
            // Update the CENTER stack panel's padding
            TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
            if (centerStack != null) {
                centerStack.setContentPadding(this.contentPadding);
            }
            
            requestLayoutUpdate();
        }
    }
    
    public TerminalInsets getContentPadding() {
        return contentPadding;
    }
    
    public void setScrollMode(ScrollMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("ScrollMode cannot be null");
        }
        if (this.scrollMode != mode) {
            this.scrollMode = mode;
            requestLayoutUpdate();
        }
    }
    
    public ScrollMode getScrollMode() {
        return scrollMode;
    }
    
    public void setVerticalScrollEnabled(boolean enabled) {
        if (this.verticalScrollEnabled != enabled) {
            this.verticalScrollEnabled = enabled;
            if (!enabled) {
                scrollY = 0;
            }
            updateScrollIndicatorPositions();
            requestLayoutUpdate();
        }
    }
    
    public void setHorizontalScrollEnabled(boolean enabled) {
        if (this.horizontalScrollEnabled != enabled) {
            this.horizontalScrollEnabled = enabled;
            if (!enabled) {
                scrollX = 0;
            }
            updateScrollIndicatorPositions();
            requestLayoutUpdate();
        }
    }
    
    public void setKeyboardScrollEnabled(boolean enabled) {
        if (this.keyboardScrollEnabled != enabled) {
            this.keyboardScrollEnabled = enabled;
            if (enabled && stateMachine.hasState(STATE_ACTIVE)) {
                registerKeyboardHandler();
            } else {
                removeKeyboardHandler();
            }
        }
    }
    
    public void setAutoShowScrollIndicators(boolean auto) {
        if (this.autoShowScrollIndicators != auto) {
            this.autoShowScrollIndicators = auto;
            requestLayoutUpdate();
        }
    }
    
    public void setVScrollIndicator(ScrollIndicator<TerminalRenderable> indicator) {
        if (vScrollIndicator != null) {
            Panel position = vScrollPosition == VScrollPosition.LEFT ? Panel.LEFT : Panel.RIGHT;
            clearPanel(position);
        }
        this.vScrollIndicator = indicator;
        updateScrollIndicatorPositions();
    }
    
    public void setHScrollIndicator(ScrollIndicator<TerminalRenderable> indicator) {
        if (hScrollIndicator != null) {
            Panel position = hScrollPosition == HScrollPosition.TOP ? Panel.TOP : Panel.BOTTOM;
            clearPanel(position);
        }
        this.hScrollIndicator = indicator;
        updateScrollIndicatorPositions();
    }
    
    public void setVScrollPosition(VScrollPosition position) {
        if (this.vScrollPosition != position) {
            if (vScrollIndicator != null) {
                Panel oldPosition = vScrollPosition == VScrollPosition.LEFT ? Panel.LEFT : Panel.RIGHT;
                clearPanel(oldPosition);
            }
            this.vScrollPosition = position;
            updateScrollIndicatorPositions();
        }
    }
    
    public void setHScrollPosition(HScrollPosition position) {
        if (this.hScrollPosition != position) {
            if (hScrollIndicator != null) {
                Panel oldPosition = hScrollPosition == HScrollPosition.TOP ? Panel.TOP : Panel.BOTTOM;
                clearPanel(oldPosition);
            }
            this.hScrollPosition = position;
            updateScrollIndicatorPositions();
        }
    }
    
    public void setLineScrollAmount(int amount) {
        this.lineScrollAmount = Math.max(1, amount);
    }
    
    public void setPageScrollAmount(int amount) {
        this.pageScrollAmount = Math.max(0, amount);
    }
    
    private void updateScrollIndicatorPositions() {
        if (vScrollIndicator != null && verticalScrollEnabled) {
            Panel position = vScrollPosition == VScrollPosition.LEFT ? Panel.LEFT : Panel.RIGHT;
            setPanel(position, vScrollIndicator.getRenderable());
        }
        
        if (hScrollIndicator != null && horizontalScrollEnabled) {
            Panel position = hScrollPosition == HScrollPosition.TOP ? Panel.TOP : Panel.BOTTOM;
            setPanel(position, hScrollIndicator.getRenderable());
        }
    }
    
    /**
     * Set the primary content for the scroll panel, replacing any existing content.
     * Content sizing is determined by the content's TerminalSizeable implementation.
     */
    public void setContent(TerminalRenderable content) {
        if (content == null) {
            clearPanel(Panel.CENTER);
        } else {
            setPanel(Panel.CENTER, content);
        }
        requestLayoutUpdate();
    }
    
    /**
     * Swap to different content in the CENTER region.
     * If the content is not already added, it will be added to the stack.
     */
    public void swapContent(TerminalRenderable newContent) {
        if (newContent == null) {
            return;
        }
        swapPanel(Panel.CENTER, newContent);
        
        // Reset scroll position when swapping content
        scrollX = 0;
        scrollY = 0;
        
        requestLayoutUpdate();
    }
    
    /**
     * Swap to different content by name.
     */
    public void swapContent(String contentName) {
        TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
        if (centerStack != null) {
            centerStack.setVisibleContent(contentName);
            
            // Reset scroll position when swapping content
            scrollX = 0;
            scrollY = 0;
            
            requestLayoutUpdate();
        }
    }
    
    /**
     * Add content to the CENTER region stack without making it visible.
     * Useful for preloading content that will be swapped to later.
     */
    public void addContent(TerminalRenderable content) {
        if (content == null) {
            return;
        }
        addToPanel(Panel.CENTER, content);
    }
    
    /**
     * Remove content from the CENTER region by reference.
     */
    public void removeContent(TerminalRenderable content) {
        if (content == null) {
            return;
        }
        removeFromPanel(Panel.CENTER, content);
    }
    
    /**
     * Remove content from the CENTER region by name.
     */
    public void removeContent(String contentName) {
        TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
        if (centerStack != null) {
            centerStack.removeFromStack(contentName);
        }
    }
    
    /**
     * Get the currently visible content in the CENTER region.
     */
    public TerminalRenderable getContent() {
        return getPanel(Panel.CENTER);
    }
    
    /**
     * Get content from the CENTER stack by name.
     */
    public TerminalRenderable getContent(String contentName) {
        TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
        if (centerStack != null) {
            return centerStack.getContent(contentName);
        }
        return null;
    }
    
    /**
     * Clear all content from the CENTER region.
     */
    public void clearContent() {
        clearPanel(Panel.CENTER);
    }
    
    public void scrollTo(int x, int y) {
        boolean changed = false;
        
        if (horizontalScrollEnabled && this.scrollX != x) {
            this.scrollX = Math.max(0, x);
            changed = true;
        }
        
        if (verticalScrollEnabled && this.scrollY != y) {
            this.scrollY = Math.max(0, y);
            changed = true;
        }
        
        if (changed) {
            requestLayoutUpdate();
        }
    }
    
    public void scrollBy(int dx, int dy) {
        scrollTo(scrollX + dx, scrollY + dy);
    }
    
    public void scrollToTop() {
        scrollTo(scrollX, 0);
    }
    
    public void scrollToBottom() {
        TerminalRenderable visibleContent = getContent();
        if (visibleContent != null) {
            TerminalRectangle contentSize = getContentSize(visibleContent);
            if (contentSize != null) {
                TerminalRectangle centerRegion = getCenterRegion();
                if (centerRegion != null) {
                    int viewportHeight = centerRegion.getHeight() - contentPadding.getVertical();
                    scrollTo(scrollX, Math.max(0, contentSize.getHeight() - viewportHeight));
                }
            }
        }
    }
    
    private void scrollLineUp() {
        scrollBy(0, -lineScrollAmount);
    }
    
    private void scrollLineDown() {
        scrollBy(0, lineScrollAmount);
    }
    
    private void scrollLineLeft() {
        scrollBy(-lineScrollAmount, 0);
    }
    
    private void scrollLineRight() {
        scrollBy(lineScrollAmount, 0);
    }
    
    public void pageUp() {
        TerminalRectangle centerRegion = getCenterRegion();
        if (centerRegion != null) {
            int scrollAmount = pageScrollAmount > 0 
                ? pageScrollAmount 
                : centerRegion.getHeight() - contentPadding.getVertical();
            scrollBy(0, -scrollAmount);
        }
    }
    
    public void pageDown() {
        TerminalRectangle centerRegion = getCenterRegion();
        if (centerRegion != null) {
            int scrollAmount = pageScrollAmount > 0 
                ? pageScrollAmount 
                : centerRegion.getHeight() - contentPadding.getVertical();
            scrollBy(0, scrollAmount);
        }
    }
    
    public int getScrollX() { return scrollX; }
    public int getScrollY() { return scrollY; }
    
    public void activate() {
        if (stateMachine.hasState(STATE_ACTIVE)) return;
        transitionTo(STATE_INACTIVE, STATE_ACTIVE);
    }
    
    public void deactivate() {
        if (stateMachine.hasState(STATE_INACTIVE)) return;
        transitionTo(STATE_ACTIVE, STATE_INACTIVE);
    }
    
    public boolean isActive() {
        return stateMachine.hasState(STATE_ACTIVE);
    }
    
    private TerminalRectangle getCenterRegion() {
        // Get the stack panel for the center region
        TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
        if (centerStack != null && centerStack.getEffectiveRegion() != null) {
            return centerStack.getEffectiveRegion();
        }
        return null;
    }
    
    /**
     * Calculate the content size based on the scroll mode and content's TerminalSizeable implementation.
     */
    private TerminalRectangle getContentSize(TerminalRenderable content) {
        if (content == null) {
            return null;
        }
        
        TerminalRectangle centerRegion = getCenterRegion();
        if (centerRegion == null) {
            return null;
        }
        
        int viewportWidth = centerRegion.getWidth() - contentPadding.getHorizontal();
        int viewportHeight = centerRegion.getHeight() - contentPadding.getVertical();
        
        if (content instanceof TerminalSizeable) {
            TerminalSizeable sizeable = (TerminalSizeable) content;
            
            int width, height;
            
            if (scrollMode == ScrollMode.FIT_TO_VIEWPORT) {
                // Resize to fit viewport, but respect minimum size
                int minWidth = sizeable.getMinWidth();
                int minHeight = sizeable.getMinHeight();
                
                width = Math.max(minWidth, viewportWidth);
                height = Math.max(minHeight, viewportHeight);
            } else {
                // FIXED_SIZE: Use preferred size
                width = sizeable.getPreferredWidth();
                height = sizeable.getPreferredHeight();
            }
            
            return new TerminalRectangle(0, 0, width, height);
        }
        
        // Default: use viewport size (no scrolling)
        return new TerminalRectangle(0, 0, viewportWidth, viewportHeight);
    }
    
    /**
     * ScrollPanel's layout is simple - it just coordinates the CENTER stack panel's
     * scroll offset and content size. The parent BorderPanel handles the actual
     * layout of all 5 regions, and each StackPanel handles its own content layout.
     */
    @Override
    protected void layoutAllPanels(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        // Let BorderPanel handle the 5-region layout
        super.layoutAllPanels(contexts, dataInterfaces);
        
        // Get the CENTER stack panel
        TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
        if (centerStack == null) return;
        
        // Get the visible content
        TerminalRenderable content = centerStack.getVisibleContent();
        if (content == null) return;
        
        // Get the center region bounds (set by BorderPanel)
        TerminalRectangle centerRegion = getCenterRegion();
        if (centerRegion == null) return;
        
        // Calculate content size based on scroll mode
        TerminalRectangle contentSize = getContentSize(content);
        if (contentSize == null) return;
        
        int viewportWidth = centerRegion.getWidth() - contentPadding.getHorizontal();
        int viewportHeight = centerRegion.getHeight() - contentPadding.getVertical();
        
        int contentWidth = contentSize.getWidth();
        int contentHeight = contentSize.getHeight();
        
        // Determine if scrollbars are needed
        boolean needsVScroll = verticalScrollEnabled && contentHeight > viewportHeight;
        boolean needsHScroll = horizontalScrollEnabled && contentWidth > viewportWidth;
        
        // Update scroll indicators
        if (autoShowScrollIndicators) {
            if (vScrollIndicator != null) {
                if (needsVScroll) {
                    vScrollIndicator.getRenderable().show();
                    vScrollIndicator.updatePosition(scrollY, contentHeight - viewportHeight, viewportHeight);
                } else {
                    vScrollIndicator.getRenderable().hide();
                }
            }
            
            if (hScrollIndicator != null) {
                if (needsHScroll) {
                    hScrollIndicator.getRenderable().show();
                    hScrollIndicator.updatePosition(scrollX, contentWidth - viewportWidth, viewportWidth);
                } else {
                    hScrollIndicator.getRenderable().hide();
                }
            }
        } else {
            if (vScrollIndicator != null) {
                vScrollIndicator.updatePosition(scrollY, contentHeight - viewportHeight, viewportHeight);
            }
            if (hScrollIndicator != null) {
                hScrollIndicator.updatePosition(scrollX, contentWidth - viewportWidth, viewportWidth);
            }
        }
        
        // Clamp scroll position to valid range
        int maxScrollX = horizontalScrollEnabled ? Math.max(0, contentWidth - viewportWidth) : 0;
        int maxScrollY = verticalScrollEnabled ? Math.max(0, contentHeight - viewportHeight) : 0;
        
        scrollX = Math.max(0, Math.min(scrollX, maxScrollX));
        scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
        
        // Configure the CENTER stack panel with scroll offset and content size
        // The StackPanel's own layout callback will apply these when laying out its children
        centerStack.setScrollOffset(scrollX, scrollY);
        centerStack.setContentSize(contentSize);
    }
    
    // TerminalSizeable implementation - delegate to CENTER region stack
    
    @Override
    public SizePreference getWidthPreference() {
        TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
        if (centerStack != null) {
            return centerStack.getWidthPreference();
        }
        return SizePreference.FIT_CONTENT;
    }
    
    @Override
    public SizePreference getHeightPreference() {
        TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
        if (centerStack != null) {
            return centerStack.getHeightPreference();
        }
        return SizePreference.FIT_CONTENT;
    }
    
    @Override
    public int getMinWidth() {
        TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
        if (centerStack != null) {
            return centerStack.getMinWidth();
        }
        return 1;
    }
    
    @Override
    public int getMinHeight() {
        TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
        if (centerStack != null) {
            return centerStack.getMinHeight();
        }
        return 1;
    }
    
    @Override
    public int getPreferredWidth() {
        TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
        if (centerStack != null) {
            return centerStack.getPreferredWidth();
        }
        return getMinWidth();
    }
    
    @Override
    public int getPreferredHeight() {
        TerminalStackPanel centerStack = getRegionStack(Panel.CENTER);
        if (centerStack != null) {
            return centerStack.getPreferredHeight();
        }
        return getMinHeight();
    }
}