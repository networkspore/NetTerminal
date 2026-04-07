package io.netnotes.terminal.components.text;

import io.netnotes.engine.ui.LabelTruncation;
import io.netnotes.terminal.*;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.*;
import io.netnotes.engine.io.input.events.*;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.noteBytes.KeyRunTable;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesRunnablePair;
import io.netnotes.engine.ui.Position;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.renderer.RenderableStates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ScrollableLogViewer - Damage-aware scrollable log with keyboard controls
 * 
 * FEATURES:
 * - Scroll with Up/Down, PageUp/PageDown, Home/End
 * - Auto-scrolls to bottom when new lines added (unless manually scrolled)
 * - Shows scroll indicators when not at top/bottom
 * - Optional wrapping with content-aware measurement
 * - Thread-safe line management
 * 
 * DAMAGE TRACKING:
 * - Scroll operations invalidate visible content area
 * - New lines invalidate bottom area (if auto-scrolling)
 * - Clear invalidates entire content area
 * 
 * KEYBOARD CONTROLS:
 * - Up/Down: Scroll one line
 * - PageUp/PageDown: Scroll one page
 * - Home: Jump to top
 * - End: Jump to bottom (auto-scroll mode)
 */
public class ScrollableTextViewer extends TerminalRegion {
    
    private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
    private volatile int maxLines = 1000;

    private final boolean showBorder;
    private final String title;
    private boolean wordWrap = false;
    private LabelTruncation truncation = LabelTruncation.END;
    private TerminalLabel.WrappedHeightStrategy wrappedHeightStrategy =
        TerminalLabel.WrappedHeightStrategy.EXPLICIT_LINES_ONLY;
    private int wrapWidthHint = 0;
    
    // Scroll state
    private int scrollOffset = 0;  // Rendered rows from bottom (0 = showing latest)
    private boolean autoScroll = true;  // Auto-scroll to bottom on new lines
    
    // Keyboard handling
    private NoteBytesReadOnly keyHandlerId = null;
    private final KeyRunTable keyRunTable = new KeyRunTable(new NoteBytesRunnablePair[]{
        new NoteBytesRunnablePair(KeyCodeBytes.UP, this::handleScrollUp),
        new NoteBytesRunnablePair(KeyCodeBytes.DOWN, this::handleScrollDown),
        new NoteBytesRunnablePair(KeyCodeBytes.PAGE_UP, this::handlePageUp),
        new NoteBytesRunnablePair(KeyCodeBytes.PAGE_DOWN, this::handlePageDown),
        new NoteBytesRunnablePair(KeyCodeBytes.HOME, this::handleHome),
        new NoteBytesRunnablePair(KeyCodeBytes.END, this::handleEnd)
    });
    
    public ScrollableTextViewer(String name) {
        this(name, true, null);
    }
    
    public ScrollableTextViewer(String name, boolean showBorder, String title) {
        super(name);
        this.showBorder = showBorder;
        this.title = title;
    }
    
    public ScrollableTextViewer(String name, int width, int height) {
        this(name, new TerminalRectangle(0, 0, width, height), true, null);
    }
    
    public ScrollableTextViewer(String name, TerminalRectangle region, boolean showBorder, String title) {
        super(name);

        this.showBorder = showBorder;
        this.title = title;

        setRegion(region);
    }

    public void setWordWrap(boolean wordWrap) {
        if (this.wordWrap != wordWrap) {
            this.wordWrap = wordWrap;
            onPresentationSettingsChanged(true);
        }
    }

    public boolean isWordWrap() {
        return wordWrap;
    }

    public void setTruncation(LabelTruncation truncation) {
        LabelTruncation next = truncation != null ? truncation : LabelTruncation.END;
        if (this.truncation != next) {
            this.truncation = next;
            invalidateContentArea();
        }
    }

    public LabelTruncation getTruncation() {
        return truncation;
    }

    public void setWrappedHeightStrategy(TerminalLabel.WrappedHeightStrategy strategy) {
        TerminalLabel.WrappedHeightStrategy next = strategy != null
            ? strategy
            : TerminalLabel.WrappedHeightStrategy.EXPLICIT_LINES_ONLY;
        if (this.wrappedHeightStrategy != next) {
            this.wrappedHeightStrategy = next;
            onPresentationSettingsChanged(true);
        }
    }

    public TerminalLabel.WrappedHeightStrategy getWrappedHeightStrategy() {
        return wrappedHeightStrategy;
    }

    public void setWrapWidthHint(int wrapWidthHint) {
        int clamped = Math.max(0, wrapWidthHint);
        if (this.wrapWidthHint != clamped) {
            this.wrapWidthHint = clamped;
            onPresentationSettingsChanged(true);
        }
    }

    public int getWrapWidthHint() {
        return wrapWidthHint;
    }
    
    @Override
    protected void setupStateTransitions() {
        stateMachine.onStateAdded(RenderableStates.STATE_FOCUSED, (old, now, bit) -> {
            registerKeyboardHandler();
        });
        
        stateMachine.onStateRemoved(RenderableStates.STATE_FOCUSED, (old, now, bit) -> {
            removeKeyboardHandler();
        });
    }
    
    // ===== DAMAGE-AWARE RENDERING =====
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        List<String> currentLines = snapshotLines();
        TextStyle titleStyle = hasFocus() ? TextStyle.NORMAL : TextStyle.DIM;
        
        if (showBorder) {
            renderFrame(batch, titleStyle);
        }

        int contentStartY = getContentStartY();
        int contentStartX = getContentStartX();
        int contentWidth = getContentWidth();
        int contentHeight = getContentHeight();
        if (contentWidth <= 0 || contentHeight <= 0) {
            return;
        }

        renderLines(batch, currentLines, contentStartY, contentStartX, contentWidth, contentHeight);
    }
    
    private void renderFrame(TerminalBatchBuilder batch, TextStyle titleStyle) {
        TerminalRectangle region = getRegionPool().obtain();
        region.set(0, 0, getWidth(), getHeight(), 0, 0);
        drawBox(batch, region, title, Position.TOP_CENTER, LineStyle.SINGLE, titleStyle);
        getRegionPool().recycle(region);
    }
    
    private void renderLines(TerminalBatchBuilder batch, List<String> currentLines,
                            int startY, int startX, int width, int height) {
        List<String> renderableRows = buildRenderableRows(currentLines, width);
        int totalRows = renderableRows.size();
        if (clampScrollOffset(totalRows, height) && scrollOffset == 0) {
            autoScroll = true;
        }
        
        // Calculate visible range based on scroll offset
        // scrollOffset = 0 means show latest rendered rows (bottom)
        // scrollOffset > 0 means scrolled up from bottom
        int visibleEnd = totalRows - scrollOffset;
        int visibleStart = Math.max(0, visibleEnd - height);
        
        // Clamp to valid range
        visibleEnd = Math.min(totalRows, visibleEnd);
        visibleStart = Math.max(0, visibleStart);
        
        // Render visible rows
        int currentY = startY;
        for (int i = visibleStart; i < visibleEnd && currentY < startY + height; i++) {
            printAt(batch, startX, currentY, fitLineToWidth(renderableRows.get(i), width), TextStyle.NORMAL);
            currentY++;
        }
        
        // Show scroll indicators
        if (totalRows > height) {
            renderScrollIndicators(batch, totalRows, visibleStart, visibleEnd, startY, startX, width, height);
        }
    }
    
    private void renderScrollIndicators(
        TerminalBatchBuilder batch,
        int totalRows,
        int visibleStart,
        int visibleEnd,
        int startY,
        int startX,
        int width,
        int height
    ) {
        // Top indicator (more content above).
        if (visibleStart > 0) {
            String indicator = String.format("↑ %d more", visibleStart);
            int indicatorX = Math.max(startX, startX + width - indicator.length() - 1);
            printAt(batch, indicatorX,startY, indicator, TextStyle.INFO);
        }
        
        // Bottom indicator (more content below).
        int remainingBelow = scrollOffset;
        if (remainingBelow > 0) {
            String indicator = String.format("↓ %d more", remainingBelow);
            int indicatorX = Math.max(startX, startX + width - indicator.length() - 1);
            printAt(batch, indicatorX,startY + height - 1, indicator, TextStyle.INFO);
        }
        
        // Show position indicator if scrolled.
        if (!autoScroll) {
            String position = String.format("[%d/%d]", visibleEnd, totalRows);
            int posX = startX + 1;
            printAt(batch, posX, startY, position, TextStyle.INFO);
        }
    }
    
    // ===== LINE MANAGEMENT WITH SMART INVALIDATION =====
    
    /**
     * Add line - invalidates appropriately based on auto-scroll state
     */
    public void addLine(String line) {
        synchronized (lines) {
            lines.add(line != null ? line : "");
            
            while (lines.size() > maxLines) {
                lines.remove(0);
            }
        }
        
        if (autoScroll) {
            scrollOffset = 0;
        } else {
            scrollOffset += countVisualRows(line, resolveCurrentWrapWidth());
        }
        if (clampScrollOffset(getScrollableRowCount(), getContentHeight()) && scrollOffset == 0) {
            autoScroll = true;
        }
        onContentChanged();
    }



    @Override
    public int getMinWidth() {
        return Math.max(super.getMinWidth(), showBorder ? 4 : 2);
    }

    @Override
    public int getMinHeight() {
        return Math.max(super.getMinHeight(), showBorder ? 2 : 1);
    }

    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        int measuredWidth = getWidthPreference() == SizePreference.FIT_CONTENT
            ? measureOuterWidth()
            : getMinWidth();

        int measuredHeight = getHeightPreference() == SizePreference.FIT_CONTENT
            ? measureOuterHeight()
            : getMinHeight();

        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, measuredWidth, measuredHeight);
        return measured;
    }
    
    public void addLines(String... newLines) {
        synchronized (lines) {
            for (String line : newLines) {
                lines.add(line != null ? line : "");
            }
            
            while (lines.size() > maxLines) {
                lines.remove(0);
            }
        }
        
        if (autoScroll) {
            scrollOffset = 0;
        } else {
            scrollOffset += countVisualRows(newLines, resolveCurrentWrapWidth());
        }
        if (clampScrollOffset(getScrollableRowCount(), getContentHeight()) && scrollOffset == 0) {
            autoScroll = true;
        }
        onContentChanged();
    }
    
    /**
     * Clear with full invalidation
     */
    public void clear() {
        synchronized (lines) {
            if (!lines.isEmpty()) {
                lines.clear();
                scrollOffset = 0;
                autoScroll = true;
                onContentChanged();
            }
        }
    }
    
    // ===== SCROLL CONTROLS =====

    private void registerKeyboardHandler() {
        if (keyHandlerId == null) {
            keyHandlerId = addKeyDownHandler(this::handleKeyboardEvent);
        }
    }

    private void handleKeyboardEvent(RoutedEvent event) {
        if (!isVisible()) return;
        
        if (event instanceof EphemeralRoutedEvent ephemeralEvent) {
            try (ephemeralEvent) {
                if (ephemeralEvent instanceof EphemeralKeyDownEvent ekd) {
                    keyRunTable.run(ekd.getKeyCodeBytes());
                    event.setConsumed(true);
                }
            }
            return;
        }
        
        if (event instanceof KeyDownEvent keyDown) {
            keyRunTable.run(keyDown.getKeyCodeBytes());
            event.setConsumed(true);
        }
    }
    
    private void handleScrollUp() {
        int contentHeight = getContentHeight();
        int totalRows = getScrollableRowCount();
        int maxOffset = Math.max(0, totalRows - contentHeight);
        
        if (scrollOffset < maxOffset) {
            scrollOffset++;
            autoScroll = false;
            invalidateContent();
        }
    }
    
    private void handleScrollDown() {
        if (scrollOffset > 0) {
            scrollOffset--;
            if (scrollOffset == 0) {
                autoScroll = true;
            }
            invalidateContent();
        }
    }
    
    private void handlePageUp() {
        int contentHeight = getContentHeight();
        int totalRows = getScrollableRowCount();
        int maxOffset = Math.max(0, totalRows - contentHeight);
        
        scrollOffset = Math.min(maxOffset, scrollOffset + contentHeight);
        autoScroll = false;
        invalidateContent();
    }
    
    private void handlePageDown() {
        int contentHeight = getContentHeight();
        
        scrollOffset = Math.max(0, scrollOffset - contentHeight);
        if (scrollOffset == 0) {
            autoScroll = true;
        }
        invalidateContent();
    }
    
    private void handleHome() {
        int contentHeight = getContentHeight();
        int totalRows = getScrollableRowCount();
        int maxOffset = Math.max(0, totalRows - contentHeight);
        
        if (scrollOffset != maxOffset) {
            scrollOffset = maxOffset;
            autoScroll = false;
            invalidateContent();
        }
    }
    
    private void handleEnd() {
        if (scrollOffset != 0 || !autoScroll) {
            scrollOffset = 0;
            autoScroll = true;
            invalidateContent();
        }
    }
    
    /**
     * Programmatically scroll to a specific line (0 = oldest)
     */
    public void scrollToLine(int lineIndex) {
        int contentHeight = getContentHeight();
        int totalLines = getLineCount();
        
        if (lineIndex < 0 || lineIndex >= totalLines) return;
        
        int totalRows = getScrollableRowCount();
        int targetRow = getRenderableRowIndexForLine(lineIndex);

        // Calculate offset needed to show this logical line at the top.
        int targetOffset = totalRows - targetRow - contentHeight;
        scrollOffset = Math.max(0, Math.min(totalRows - contentHeight, targetOffset));
        autoScroll = (scrollOffset == 0);
        invalidateContent();
    }
    
    /**
     * Enable/disable auto-scroll mode
     */
    public void setAutoScroll(boolean autoScroll) {
        if (this.autoScroll != autoScroll) {
            this.autoScroll = autoScroll;
            if (autoScroll) {
                scrollOffset = 0;
                invalidateContent();
            }
        }
    }
    
    public boolean isAutoScroll() {
        return autoScroll;
    }
    
    public int getScrollOffset() {
        return scrollOffset;
    }
    
    // ===== INVALIDATION HELPERS =====
    
    private void invalidateContent() {
        invalidateContentArea();
    }

    private void onContentChanged() {
        if (isSizedByContent()) {
            requestLayoutUpdate();
        }
        invalidateContentArea();
    }

    private void invalidateContentArea() {
        int contentWidth = getContentWidth();
        int contentHeight = getContentHeight();
        if (contentWidth <= 0 || contentHeight <= 0) {
            return;
        }
        invalidateRegion(getContentStartX(), getContentStartY(), contentWidth, contentHeight);
    }

    private boolean clampScrollOffset(int totalLines) {
        return clampScrollOffset(totalLines, getContentHeight());
    }

    private boolean clampScrollOffset(int totalRows, int contentHeight) {
        int maxOffset = contentHeight <= 0 ? 0 : Math.max(0, totalRows - contentHeight);
        if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
            return true;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
            return true;
        }
        return false;
    }

    private int getContentStartX() {
        return getBaseContentStartX() + getInsets().getLeft();
    }

    private int getContentStartY() {
        return getBaseContentStartY() + getInsets().getTop();
    }

    private int getContentWidth() {
        return Math.max(0, getWidth() - getFrameHorizontalPadding() - getInsets().getHorizontal());
    }

    private int getContentHeight() {
        return Math.max(0, getHeight() - getFrameVerticalPadding() - getInsets().getVertical());
    }
    
    public void setMaxLines(int maxLines) {
        this.maxLines = Math.max(100, maxLines);
        boolean trimmed = false;
        synchronized (lines) {
            while (lines.size() > this.maxLines) {
                lines.remove(0);
                trimmed = true;
            }
        }
        if (trimmed) {
            if (clampScrollOffset(getScrollableRowCount(), getContentHeight()) && scrollOffset == 0) {
                autoScroll = true;
            }
            onContentChanged();
        }
    }
    
    public int getLineCount() {
        synchronized (lines) {
            return lines.size();
        }
    }
    
    public List<String> getAllLines() {
        return snapshotLines();
    }

    private int measureOuterWidth() {
        int maxLine = 0;
        synchronized (lines) {
            for (String line : lines) {
                if (line != null) {
                    maxLine = Math.max(maxLine, line.length());
                }
            }
        }

        if (showBorder && title != null) {
            maxLine = Math.max(maxLine, title.length());
        }

        return Math.max(getMinWidth(), maxLine + getFrameHorizontalPadding() + getInsets().getHorizontal());
    }

    private int measureOuterHeight() {
        return Math.max(
            getMinHeight(),
            countMeasuredRows() + getFrameVerticalPadding() + getInsets().getVertical()
        );
    }

    private int countMeasuredRows() {
        List<String> snapshot = snapshotLines();
        if (snapshot.isEmpty()) {
            return 1;
        }

        int wrapWidth = resolveWrapMeasureWidth();
        return Math.max(1, countVisualRows(snapshot, wrapWidth));
    }
    
    public boolean isEmpty() {
        synchronized (lines) {
            return lines.isEmpty();
        }
    }

    private void onPresentationSettingsChanged(boolean affectsMeasurement) {
        if (clampScrollOffset(getScrollableRowCount()) && scrollOffset == 0) {
            autoScroll = true;
        }
        if (affectsMeasurement) {
            requestLayoutUpdate();
        }
        invalidateContentArea();
    }

    private List<String> snapshotLines() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }

    private List<String> buildRenderableRows(List<String> sourceLines, int wrapWidth) {
        if (!wordWrap || wrapWidth <= 0) {
            return sourceLines;
        }

        List<String> rows = new ArrayList<>();
        for (String line : sourceLines) {
            appendWrappedRows(rows, sanitizeLine(line), wrapWidth);
        }
        return rows;
    }

    private void appendWrappedRows(List<String> rows, String line, int wrapWidth) {
        if (line.isEmpty()) {
            rows.add("");
            return;
        }

        for (int start = 0; start < line.length(); start += wrapWidth) {
            rows.add(line.substring(start, Math.min(line.length(), start + wrapWidth)));
        }
    }

    private int getScrollableRowCount() {
        if (getLineCount() <= 0) {
            return 0;
        }
        return countVisualRows(snapshotLines(), resolveCurrentWrapWidth());
    }

    private int getRenderableRowIndexForLine(int lineIndex) {
        if (!wordWrap) {
            return lineIndex;
        }

        int wrapWidth = resolveCurrentWrapWidth();
        if (wrapWidth <= 0) {
            return lineIndex;
        }

        int rowIndex = 0;
        synchronized (lines) {
            int boundedIndex = Math.min(lineIndex, lines.size());
            for (int i = 0; i < boundedIndex; i++) {
                rowIndex += countVisualRows(lines.get(i), wrapWidth);
            }
        }
        return rowIndex;
    }

    private int countVisualRows(List<String> sourceLines, int wrapWidth) {
        int total = 0;
        for (String line : sourceLines) {
            total += countVisualRows(line, wrapWidth);
        }
        return total;
    }

    private int countVisualRows(String[] sourceLines, int wrapWidth) {
        int total = 0;
        for (String line : sourceLines) {
            total += countVisualRows(line, wrapWidth);
        }
        return total;
    }

    private int countVisualRows(String line, int wrapWidth) {
        if (!wordWrap || wrapWidth <= 0) {
            return 1;
        }
        String safeLine = sanitizeLine(line);
        return Math.max(1, (safeLine.length() + wrapWidth - 1) / wrapWidth);
    }

    private int resolveWrapMeasureWidth() {
        if (!wordWrap) {
            return 0;
        }
        if (getWidthPreference() == SizePreference.FIT_CONTENT) {
            return measureIntrinsicContentWidth();
        }

        return switch (wrappedHeightStrategy) {
            case EXPLICIT_LINES_ONLY -> 0;
            case CURRENT_WIDTH -> resolveCurrentWrapWidth();
            case WIDTH_HINT -> Math.max(0, wrapWidthHint);
            case CURRENT_WIDTH_OR_HINT -> {
                int currentWidth = resolveCurrentWrapWidth();
                yield currentWidth > 0 ? currentWidth : Math.max(0, wrapWidthHint);
            }
        };
    }

    private int resolveCurrentWrapWidth() {
        TerminalRectangle requested = getRequestedRegion();
        if (requested != null) {
            return Math.max(0, requested.getWidth() - getFrameHorizontalPadding() - getInsets().getHorizontal());
        }
        return getContentWidth();
    }

    private int measureIntrinsicContentWidth() {
        int maxLine = 0;
        synchronized (lines) {
            for (String line : lines) {
                maxLine = Math.max(maxLine, sanitizeLine(line).length());
            }
        }
        return maxLine;
    }

    private String fitLineToWidth(String line, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        String safeLine = sanitizeLine(line);
        if (wordWrap) {
            return safeLine;
        }
        return truncateLine(safeLine, maxWidth);
    }

    private String truncateLine(String line, int maxWidth) {
        if (line.length() <= maxWidth || truncation == LabelTruncation.NONE) {
            return line;
        }
        if (maxWidth <= 3) {
            return line.substring(0, maxWidth);
        }

        return switch (truncation) {
            case END -> line.substring(0, maxWidth - 3) + "...";
            case START -> "..." + line.substring(Math.max(0, line.length() - (maxWidth - 3)));
            case MIDDLE -> {
                if (maxWidth < 5) {
                    yield line.substring(0, maxWidth);
                }
                int head = (maxWidth - 3) / 2;
                int tail = maxWidth - 3 - head;
                yield line.substring(0, head) + "..." + line.substring(line.length() - tail);
            }
            case NONE -> line;
        };
    }

    private String sanitizeLine(String line) {
        return line != null ? line : "";
    }

    private int getBaseContentStartX() {
        return showBorder ? 2 : 0;
    }

    private int getBaseContentStartY() {
        return showBorder ? 1 : 0;
    }

    private int getFrameHorizontalPadding() {
        return showBorder ? 4 : 0;
    }

    private int getFrameVerticalPadding() {
        return showBorder ? 2 : 0;
    }
    
    private void removeKeyboardHandler() {
        if (keyHandlerId != null) {
            removeKeyDownHandler(keyHandlerId);
            keyHandlerId = null;
        }
    }

    /**
     * Cleanup when component is removed
     */
    public void cleanup() {
        removeKeyboardHandler();
    }
}
