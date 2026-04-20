package io.netnotes.terminal.components.panels;

import java.util.Map;

import io.netnotes.debug.RenderDiagnostics;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.components.HScrollIndicator;
import io.netnotes.terminal.components.VScrollIndicator;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalSizeable;
import io.netnotes.engine.ui.renderer.LayoutGroup.LayoutDataInterface;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.noteBytes.KeyRunTable;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesRunnablePair;
import io.netnotes.engine.ui.BorderPanel;
import io.netnotes.engine.ui.ScrollIndicator;
import io.netnotes.engine.ui.SizePreference;

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
 */
public class TerminalScrollPanel extends TerminalBorderPanel {

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
        this.contentPadding.setOnChanged(insets -> applyContentPadding());
        this.vScrollIndicator = new VScrollIndicator(name + "-vscroll");
        this.hScrollIndicator = new HScrollIndicator(name + "-hscroll");
        syncCenterStackSizing();
        applyContentPadding();
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
            try (ekd) {
                keyRunTable.run(ekd.getKeyCodeBytes());
            }
        }
    }

    public void setContentPadding(int padding) {
        int clamped = Math.max(0, padding);
        if (this.contentPadding.getTop()    != clamped ||
            this.contentPadding.getRight()  != clamped ||
            this.contentPadding.getBottom() != clamped ||
            this.contentPadding.getLeft()   != clamped) {
            this.contentPadding.setAll(clamped);
        }
    }

    private void applyContentPadding() {
        TerminalStackPanel centerStack = getRegionStack(BorderPanel.CENTER);
        if (centerStack != null) {
            centerStack.setInsets(contentPadding);
        }
        requestLayoutUpdate();
    }



    public void setContentInsets(TerminalInsets padding) {
        if (padding == null) {
            if (!this.contentPadding.isZero()) {
                this.contentPadding.clear();
            }
            return;
        }
        if (!this.contentPadding.equals(padding)) {
            this.contentPadding.copyFrom(padding);
        }
    }

    public TerminalInsets getContentPadding() {
        return contentPadding;
    }

    public void setScrollMode(ScrollMode mode) {
        mode = mode != null ? mode : ScrollMode.FIT_TO_VIEWPORT;
        if (this.scrollMode != mode) {
            this.scrollMode = mode;
            syncCenterStackSizing();
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
            BorderPanel position = vScrollPosition == VScrollPosition.LEFT ? BorderPanel.LEFT : BorderPanel.RIGHT;
            clearPanel(position);
        }
        this.vScrollIndicator = indicator;
        updateScrollIndicatorPositions();
    }

    public void setHScrollIndicator(ScrollIndicator<TerminalRenderable> indicator) {
        if (hScrollIndicator != null) {
            BorderPanel position = hScrollPosition == HScrollPosition.TOP ? BorderPanel.TOP : BorderPanel.BOTTOM;
            clearPanel(position);
        }
        this.hScrollIndicator = indicator;
        updateScrollIndicatorPositions();
    }

    public void setVScrollPosition(VScrollPosition position) {
        if (this.vScrollPosition != position) {
            if (vScrollIndicator != null) {
                BorderPanel oldPosition = vScrollPosition == VScrollPosition.LEFT ? BorderPanel.LEFT : BorderPanel.RIGHT;
                clearPanel(oldPosition);
            }
            this.vScrollPosition = position;
            updateScrollIndicatorPositions();
        }
    }

    public void setHScrollPosition(HScrollPosition position) {
        if (this.hScrollPosition != position) {
            if (hScrollIndicator != null) {
                BorderPanel oldPosition = hScrollPosition == HScrollPosition.TOP ? BorderPanel.TOP : BorderPanel.BOTTOM;
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
        syncIndicatorPlacement(
            vScrollIndicator,
            verticalScrollEnabled,
            vScrollPosition == VScrollPosition.LEFT ? BorderPanel.LEFT : BorderPanel.RIGHT,
            vScrollPosition == VScrollPosition.LEFT ? BorderPanel.RIGHT : BorderPanel.LEFT
        );

        syncIndicatorPlacement(
            hScrollIndicator,
            horizontalScrollEnabled,
            hScrollPosition == HScrollPosition.TOP ? BorderPanel.TOP : BorderPanel.BOTTOM,
            hScrollPosition == HScrollPosition.TOP ? BorderPanel.BOTTOM : BorderPanel.TOP
        );
    }

    private void syncIndicatorPlacement(
        ScrollIndicator<TerminalRenderable> indicator,
        boolean enabled,
        BorderPanel targetRegion,
        BorderPanel alternateRegion
    ) {
        if (indicator == null) {
            return;
        }

        TerminalRenderable indicatorRenderable = indicator.getRenderable();
        removeIndicatorIfPresent(alternateRegion, indicatorRenderable);

        if (!enabled) {
            removeIndicatorIfPresent(targetRegion, indicatorRenderable);
            return;
        }

        if (getPanel(targetRegion) != indicatorRenderable) {
            setPanel(targetRegion, indicatorRenderable);
        }
    }

    private void removeIndicatorIfPresent(BorderPanel region, TerminalRenderable indicatorRenderable) {
        if (region == null || indicatorRenderable == null) {
            return;
        }
        if (getPanel(region) == indicatorRenderable) {
            clearPanel(region);
        }
    }

    /**
     * Set the primary content for the scroll panel, replacing any existing content.
     * Content sizing is determined by the content's TerminalSizeable implementation.
     */
    public void setContent(TerminalRenderable content) {
        if (content == null) {
            clearPanel(BorderPanel.CENTER);
        } else {
            setPanel(BorderPanel.CENTER, content);
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
        String traceOwner = getSwapTraceOwner(BorderPanel.CENTER);
        TerminalRenderable previousContent = getContent();
        RenderDiagnostics.logSwapTraceEvent(
            traceOwner,
            "TerminalScrollPanel.swapContent:start",
            () -> "scrollPanel=" + RenderDiagnostics.summarizeRenderable(this)
                + "\n\tpreviousContent=" + RenderDiagnostics.summarizeRenderable(previousContent)
                + "\n\tnewContent=" + RenderDiagnostics.summarizeRenderable(newContent)
                + "\n\tscroll=(" + scrollX + "," + scrollY + ")"
        );
        swapPanel(BorderPanel.CENTER, newContent);

        // Reset scroll position when swapping content
        scrollX = 0;
        scrollY = 0;

        requestLayoutUpdate();
        RenderDiagnostics.logSwapTraceEvent(
            traceOwner,
            "TerminalScrollPanel.swapContent:end",
            () -> "visibleContent=" + RenderDiagnostics.summarizeRenderable(getContent())
                + "\n\tscroll=(" + scrollX + "," + scrollY + ")"
        );
    }

    /**
     * Swap to different content by name.
     */
    public void swapContent(String contentName) {
        TerminalStackPanel centerStack = getRegionStack(BorderPanel.CENTER);
        if (centerStack != null) {
            String traceOwner = getSwapTraceOwner(BorderPanel.CENTER);
            TerminalRenderable previousContent = centerStack.getContent();
            TerminalRenderable targetContent = centerStack.getContent(contentName);
            RenderDiagnostics.armSwapTrace(
                traceOwner,
                "TerminalScrollPanel.swapContent(String)",
                this,
                centerStack,
                previousContent,
                targetContent
            );
            RenderDiagnostics.logSwapTraceEvent(
                traceOwner,
                "TerminalScrollPanel.swapContent(String):start",
                () -> "contentName=" + contentName
                    + "\n\tpreviousContent=" + RenderDiagnostics.summarizeRenderable(previousContent)
                    + "\n\ttargetContent=" + RenderDiagnostics.summarizeRenderable(targetContent)
                    + "\n\tscroll=(" + scrollX + "," + scrollY + ")"
            );
            centerStack.setVisibleContent(contentName);

            // Reset scroll position when swapping content
            scrollX = 0;
            scrollY = 0;

            requestLayoutUpdate();
            RenderDiagnostics.logSwapTraceEvent(
                traceOwner,
                "TerminalScrollPanel.swapContent(String):end",
                () -> "visibleContent=" + RenderDiagnostics.summarizeRenderable(centerStack.getContent())
                    + "\n\tscroll=(" + scrollX + "," + scrollY + ")"
            );
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
        addToPanel(BorderPanel.CENTER, content);
    }



    /**
     * Remove content from the CENTER region by reference.
     */
    public void removeContent(TerminalRenderable content) {
        if (content == null) {
            return;
        }
        removeFromPanel(BorderPanel.CENTER, content);
    }

    /**
     * Remove content from the CENTER region by name.
     */
    public void removeContent(String contentName) {
        TerminalStackPanel centerStack = getRegionStack(BorderPanel.CENTER);
        if (centerStack != null) {
            centerStack.removeFromStack(contentName);
        }
    }

    /**
     * Get the currently visible content in the CENTER region.
     */
    public TerminalRenderable getContent() {
        return getPanel(BorderPanel.CENTER);
    }

    /**
     * Get content from the CENTER stack by name.
     */
    public TerminalRenderable getContent(String contentName) {
        TerminalStackPanel centerStack = getRegionStack(BorderPanel.CENTER);
        if (centerStack != null) {
            return centerStack.getContent(contentName);
        }
        return null;
    }

    /**
     * Clear all content from the CENTER region.
     */
    public void clearContent() {
        clearPanel(BorderPanel.CENTER);
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
        if (visibleContent == null) return;
        TerminalRectangle centerRegion = getCenterRegion();
        if (centerRegion == null) return;
        TerminalRectangle contentRegion = visibleContent.getRegion();
        if (contentRegion == null) return;
        int viewportHeight = centerRegion.getHeight() - contentPadding.getVertical();
        scrollTo(scrollX, Math.max(0, contentRegion.getHeight() - viewportHeight));
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
        TerminalStackPanel centerStack = getRegionStack(BorderPanel.CENTER);
        return centerStack != null ? centerStack.getRegion() : null;
    }

    private void syncCenterStackSizing() {
        TerminalStackPanel centerStack = getRegionStack(BorderPanel.CENTER);
        if (centerStack == null) {
            return;
        }

        SizePreference pref = scrollMode == ScrollMode.FIXED_SIZE
            ? SizePreference.FIT_CONTENT
            : SizePreference.FILL;

        if (centerStack.getWidthPreference() != pref) {
            centerStack.setWidthPreference(pref);
        }
        if (centerStack.getHeightPreference() != pref) {
            centerStack.setHeightPreference(pref);
        }
    }


    private TerminalRectangle getContentSize(
        TerminalRenderable content,
        TerminalLayoutContext centerContext,
        int viewportWidth,
        int viewportHeight
    ) {
        TerminalRectangle measuredStack = centerContext != null
            ? centerContext.getMeasuredContentBounds()
            : null;
        if (measuredStack != null) {
            return new TerminalRectangle(
                0,
                0,
                Math.max(0, measuredStack.getWidth() - contentPadding.getHorizontal()),
                Math.max(0, measuredStack.getHeight() - contentPadding.getVertical())
            );
        }

        TerminalStackPanel centerStack = getRegionStack(BorderPanel.CENTER);
        if (content instanceof TerminalSizeable s) {
            int w = resolveContentDimension(
                content,
                centerStack,
                viewportWidth,
                s.getPercentWidth(),
                s.getWidthPreference(),
                true
            );
            int h = resolveContentDimension(
                content,
                centerStack,
                viewportHeight,
                s.getPercentHeight(),
                s.getHeightPreference(),
                false
            );
            return new TerminalRectangle(0, 0, w, h);
        }
        return new TerminalRectangle(0, 0, Math.max(0, viewportWidth), Math.max(0, viewportHeight));
    }

    private int resolveContentDimension(
        TerminalRenderable content,
        TerminalStackPanel centerStack,
        int viewportSize,
        double percent,
        SizePreference preference,
        boolean isWidth
    ) {
        int available = Math.max(0, viewportSize);
        SizePreference resolvedPreference = resolveContentPreference(centerStack, preference, isWidth);
        int min = 0;

        if (content instanceof TerminalSizeable sizeable) {
            min = isWidth ? sizeable.getMinWidth() : sizeable.getMinHeight();
        }

        return switch (resolvedPreference) {
            case FIT_CONTENT, STATIC -> Math.max(min, readRequestedOrCurrentDimension(content, isWidth));
            case PERCENT -> Math.max(min, (int) (available * percent));
            case FILL -> scrollMode == ScrollMode.FIXED_SIZE
                ? Math.max(min, readRequestedOrCurrentDimension(content, isWidth))
                : Math.max(min, available);
            default -> Math.max(min, available);
        };
    }

    private SizePreference resolveContentPreference(
        TerminalStackPanel centerStack,
        SizePreference preference,
        boolean isWidth
    ) {
        if (preference != SizePreference.INHERIT) {
            return preference;
        }
        if (centerStack != null) {
            return isWidth ? centerStack.getWidthPreference() : centerStack.getHeightPreference();
        }
        return scrollMode == ScrollMode.FIXED_SIZE
            ? SizePreference.FIT_CONTENT
            : SizePreference.FILL;
    }

    private int readRequestedOrCurrentDimension(TerminalRenderable content, boolean isWidth) {
        TerminalRectangle requested = content.getRequestedRegion();
        if (requested != null) {
            return isWidth ? requested.getWidth() : requested.getHeight();
        }
        TerminalRectangle current = content.getRegion();
        return isWidth ? current.getWidth() : current.getHeight();
    }


    @Override
    protected void layoutAllPanels(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        TerminalStackPanel centerStack = getRegionStack(BorderPanel.CENTER);
        TerminalRenderable content = centerStack != null ? centerStack.getContent() : null;

        super.layoutAllPanels(contexts, dataInterfaces);

        if (centerStack == null) {
            updateScrollIndicators(0, 0, 0, 0, false, false);
            return;
        }

        TerminalRectangle allocatedRegion = resolveAllocatedRegion(dataInterfaces, centerStack);
        if (allocatedRegion == null) {
            updateScrollIndicators(0, 0, 0, 0, false, false);
            return;
        }

        if (content == null || renderableIsExcluded(content)) {
            scrollX = 0;
            scrollY = 0;
            centerStack.setScrollOffsetDuringLayout(0, 0);
            updateScrollIndicators(
                Math.max(0, allocatedRegion.getWidth() - contentPadding.getHorizontal()),
                Math.max(0, allocatedRegion.getHeight() - contentPadding.getVertical()),
                0,
                0,
                false,
                false
            );
            return;
        }

        TerminalLayoutContext centerContext = findContext(contexts, centerStack);
        int viewportWidth  = Math.max(0, allocatedRegion.getWidth()  - contentPadding.getHorizontal());
        int viewportHeight = Math.max(0, allocatedRegion.getHeight() - contentPadding.getVertical());
        TerminalRectangle contentSize = getContentSize(content, centerContext, viewportWidth, viewportHeight);
        int contentWidth  = contentSize.getWidth();
        int contentHeight = contentSize.getHeight();

        int maxScrollX = horizontalScrollEnabled ? Math.max(0, contentWidth  - viewportWidth)  : 0;
        int maxScrollY = verticalScrollEnabled   ? Math.max(0, contentHeight - viewportHeight) : 0;
        scrollX = Math.max(0, Math.min(scrollX, maxScrollX));
        scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
        centerStack.setScrollOffsetDuringLayout(scrollX, scrollY);

        if (viewportWidth <= 0 || viewportHeight <= 0) {
            RenderDiagnostics.logRenderBlocker(
                "scrollpanel-viewport-collapse:" + getName(),
                "TerminalScrollPanel.layoutAllPanels",
                "non-positive-viewport",
                () -> "scrollPanel=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\tpanelSizing=" + RenderDiagnostics.summarizeSizing(this)
                    + "\n\tcenterStack=" + RenderDiagnostics.summarizeRenderable(centerStack)
                    + "\n\tcenterSizing=" + RenderDiagnostics.summarizeSizing(centerStack)
                    + "\n\tcenterRegion=" + RenderDiagnostics.summarizeRegion(allocatedRegion)
                    + "\n\tcontent=" + RenderDiagnostics.summarizeRenderable(content)
                    + "\n\tcontentSizing=" + RenderDiagnostics.summarizeSizing(content)
                    + "\n\tcontentPadding=" + contentPadding
                    + "\n\tviewport=" + viewportWidth + "x" + viewportHeight
                    + "\n\tresolvedContent=" + contentWidth + "x" + contentHeight
                    + "\n\tscroll=(" + scrollX + "," + scrollY + ")"
                    + "\n\tscrollMode=" + scrollMode
            );
        }

        if (contentWidth <= 0 || contentHeight <= 0) {
            RenderDiagnostics.logRenderBlocker(
                "scrollpanel-content-collapse:" + getName() + ":" + content.getName(),
                "TerminalScrollPanel.layoutAllPanels",
                "resolved-content-size-non-positive",
                () -> "scrollPanel=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\tpanelSizing=" + RenderDiagnostics.summarizeSizing(this)
                    + "\n\tcenterRegion=" + RenderDiagnostics.summarizeRegion(allocatedRegion)
                    + "\n\tcontent=" + RenderDiagnostics.summarizeRenderable(content)
                    + "\n\tcontentSizing=" + RenderDiagnostics.summarizeSizing(content)
                    + "\n\tcontentPadding=" + contentPadding
                    + "\n\tviewport=" + viewportWidth + "x" + viewportHeight
                    + "\n\tresolvedContent=" + contentWidth + "x" + contentHeight
                    + "\n\tscroll=(" + scrollX + "," + scrollY + ")"
                    + "\n\tscrollMode=" + scrollMode
            );
        }

        boolean needsVScroll = verticalScrollEnabled   && contentHeight > viewportHeight;
        boolean needsHScroll = horizontalScrollEnabled && contentWidth  > viewportWidth;

        updateScrollIndicators(
            viewportWidth,
            viewportHeight,
            contentWidth,
            contentHeight,
            needsVScroll,
            needsHScroll
        );
    }

    private TerminalLayoutContext findContext(
        TerminalLayoutContext[] contexts,
        TerminalRenderable target
    ) {
        if (contexts == null || target == null) {
            return null;
        }
        for (TerminalLayoutContext context : contexts) {
            if (context != null && context.getRenderable() == target) {
                return context;
            }
        }
        return null;
    }

    private TerminalRectangle resolveAllocatedRegion(
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces,
        TerminalRenderable renderable
    ) {
        if (renderable == null) {
            return null;
        }

        LayoutDataInterface<TerminalLayoutData> dataInterface = dataInterfaces.get(renderable.getName());
        if (dataInterface != null && dataInterface.hasLayoutData()) {
            TerminalLayoutData layoutData = dataInterface.getLayoutData();
            if (layoutData != null && layoutData.hasRegion()) {
                return layoutData.getSpatialRegion();
            }
        }

        TerminalRectangle requested = renderable.getRequestedRegion();
        return requested != null ? requested : renderable.getRegion();
    }

    private void updateScrollIndicators(
        int viewportWidth,
        int viewportHeight,
        int contentWidth,
        int contentHeight,
        boolean needsVScroll,
        boolean needsHScroll
    ) {
        if (autoShowScrollIndicators) {
            if (vScrollIndicator != null) {
                if (needsVScroll) {
                    vScrollIndicator.getRenderable().show();
                    vScrollIndicator.updatePosition(
                        scrollY,
                        Math.max(0, contentHeight - viewportHeight),
                        Math.max(0, viewportHeight)
                    );
                } else {
                    vScrollIndicator.getRenderable().hide();
                }
            }
            if (hScrollIndicator != null) {
                if (needsHScroll) {
                    hScrollIndicator.getRenderable().show();
                    hScrollIndicator.updatePosition(
                        scrollX,
                        Math.max(0, contentWidth - viewportWidth),
                        Math.max(0, viewportWidth)
                    );
                } else {
                    hScrollIndicator.getRenderable().hide();
                }
            }
            return;
        }

        if (vScrollIndicator != null) {
            vScrollIndicator.updatePosition(
                scrollY,
                Math.max(0, contentHeight - viewportHeight),
                Math.max(0, viewportHeight)
            );
        }
        if (hScrollIndicator != null) {
            hScrollIndicator.updatePosition(
                scrollX,
                Math.max(0, contentWidth - viewportWidth),
                Math.max(0, viewportWidth)
            );
        }
    }

}
