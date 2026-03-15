package io.netnotes.terminal.components.text;

import io.netnotes.terminal.*;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.*;
import io.netnotes.engine.io.input.events.*;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.noteBytes.KeyRunTable;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesRunnablePair;
import io.netnotes.engine.ui.Position;
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
    
    // Scroll state
    private int scrollOffset = 0;  // Lines from bottom (0 = showing latest)
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
        List<String> currentLines;
        synchronized (lines) {
            currentLines = new ArrayList<>(lines);
        }
        TextStyle titleStyle = hasFocus() ? TextStyle.NORMAL : TextStyle.DIM;
        
        if (showBorder) {
            renderWithBorder(batch, currentLines, titleStyle);
        } else {
            renderWithoutBorder(batch, currentLines, titleStyle);
        }
    }
    
    private void renderWithBorder(TerminalBatchBuilder batch, List<String> currentLines, TextStyle titleStyle) {
        TerminalRectangle region = TerminalRectanglePool.getInstance().obtain();
        region.set(0,0, getWidth(), getHeight(), 0 ,0);
        
        drawBox(batch, region, title, Position.TOP_CENTER, LineStyle.SINGLE, titleStyle);
        
        TerminalRectanglePool.getInstance().recycle(region);
        
        int contentstartY = getContentStartY();
        int contentStartCol = getContentStartX();
        int contentWidth = getContentWidth();
        int contentHeight = getContentHeight();
        if (contentWidth <= 0 || contentHeight <= 0) {
            return;
        }
        
        renderLines(batch, currentLines, contentstartY, contentStartCol, 
            contentWidth, contentHeight);
    }
    
    private void renderWithoutBorder(TerminalBatchBuilder batch, List<String> currentLines, TextStyle titleStyle) {
        int contentstartY = getContentStartY();
        int contentStartCol = getContentStartX();
        int contentWidth = getContentWidth();
        int contentHeight = getContentHeight();
        if (contentWidth <= 0 || contentHeight <= 0) {
            return;
        }
        renderLines(batch, currentLines, contentstartY, contentStartCol, contentWidth, contentHeight);
    }
    
    private void renderLines(TerminalBatchBuilder batch, List<String> currentLines,
                            int startY, int startX, int width, int height) {
        int totalLines = currentLines.size();
        
        // Calculate visible range based on scroll offset
        // scrollOffset = 0 means show latest lines (bottom)
        // scrollOffset > 0 means scrolled up from bottom
        int visibleEnd = totalLines - scrollOffset;
        int visibleStart = Math.max(0, visibleEnd - height);
        
        // Clamp to valid range
        visibleEnd = Math.min(totalLines, visibleEnd);
        visibleStart = Math.max(0, visibleStart);
        
        // Render visible lines
        int currentY = startY;
        for (int i = visibleStart; i < visibleEnd && currentY < startY + height; i++) {
            String line = currentLines.get(i);
            String truncated = truncateLine(line, width);
            printAt(batch, startX, currentY, truncated, TextStyle.NORMAL);
            currentY++;
        }
        
        // Show scroll indicators
        if (totalLines > height) {
            renderScrollIndicators(batch, currentLines, startY, startX, width, height);
        }
    }
    
    private void renderScrollIndicators(TerminalBatchBuilder batch, List<String> currentLines,
                                       int startY, int startX, int width, int height) {
        int totalLines = currentLines.size();
        int visibleEnd = totalLines - scrollOffset;
        int visibleStart = Math.max(0, visibleEnd - height);
        
        // Top indicator (more content above)
        if (visibleStart > 0) {
            String indicator = String.format("↑ %d more", visibleStart);
            int indicatorX = startX + width - indicator.length() - 1;
            printAt(batch, indicatorX,startY, indicator, TextStyle.INFO);
        }
        
        // Bottom indicator (more content below)
        int remainingBelow = scrollOffset;
        if (remainingBelow > 0) {
            String indicator = String.format("↓ %d more", remainingBelow);
            int indicatorX = startX + width - indicator.length() - 1;
            printAt(batch, indicatorX,startY + height - 1, indicator, TextStyle.INFO);
        }
        
        // Show position indicator if scrolled
        if (!autoScroll) {
            String position = String.format("[%d/%d]", visibleEnd, totalLines);
            int posX = startX + 1;
            printAt(batch, posX, startY, position, TextStyle.INFO);
        }
    }
    
    // ===== LINE MANAGEMENT WITH SMART INVALIDATION =====
    
    /**
     * Add line - invalidates appropriately based on auto-scroll state
     */
    public void addLine(String line) {
        int totalLines;
        synchronized (lines) {
            lines.add(line != null ? line : "");
            
            while (lines.size() > maxLines) {
                lines.remove(0);
            }
            totalLines = lines.size();
        }
        
        if (autoScroll) {
            scrollOffset = 0;
        } else {
            scrollOffset++;
        }
        if (clampScrollOffset(totalLines) && scrollOffset == 0) {
            autoScroll = true;
        }
        onContentChanged();
    }

    @Override
    public int getPreferredWidth() {
        int maxLine = 0;
        synchronized (lines) {
            for (String line : lines) {
                if (line != null) {
                    maxLine = Math.max(maxLine, line.length());
                }
            }
        }
        if (title != null) {
            maxLine = Math.max(maxLine, title.length());
        }
        int borderExtra = showBorder ? 4 : 2;
        return Math.max(getMinWidth(), maxLine + borderExtra);
    }

    @Override
    public int getPreferredHeight() {
        int lineCount;
        synchronized (lines) {
            lineCount = Math.max(1, lines.size());
        }
        int borderExtra = showBorder ? 2 : 0;
        return Math.max(getMinHeight(), lineCount + borderExtra);
    }

    @Override
    public int getMinWidth() {
        return Math.max(super.getMinWidth(), showBorder ? 4 : 2);
    }

    @Override
    public int getMinHeight() {
        return Math.max(super.getMinHeight(), showBorder ? 2 : 1);
    }
    
    public void addLines(String... newLines) {
        int totalLines;
        synchronized (lines) {
            for (String line : newLines) {
                lines.add(line != null ? line : "");
            }
            
            while (lines.size() > maxLines) {
                lines.remove(0);
            }
            totalLines = lines.size();
        }
        
        if (autoScroll) {
            scrollOffset = 0;
        } else {
            scrollOffset += newLines.length;
        }
        if (clampScrollOffset(totalLines) && scrollOffset == 0) {
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
        int totalLines = getLineCount();
        int maxOffset = Math.max(0, totalLines - contentHeight);
        
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
        int totalLines = getLineCount();
        int maxOffset = Math.max(0, totalLines - contentHeight);
        
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
        int totalLines = getLineCount();
        int maxOffset = Math.max(0, totalLines - contentHeight);
        
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
        
        // Calculate offset needed to show this line at top
        int targetOffset = totalLines - lineIndex - contentHeight;
        scrollOffset = Math.max(0, Math.min(totalLines - contentHeight, targetOffset));
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
        int contentHeight = getContentHeight();
        int maxOffset = contentHeight <= 0 ? 0 : Math.max(0, totalLines - contentHeight);
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
        return showBorder ? 2 : 0;
    }

    private int getContentStartY() {
        return showBorder ? 1 : 0;
    }

    private int getContentWidth() {
        return Math.max(0, showBorder ? getWidth() - 4 : getWidth());
    }

    private int getContentHeight() {
        return Math.max(0, showBorder ? getHeight() - 2 : getHeight());
    }
    
    public void setMaxLines(int maxLines) {
        this.maxLines = Math.max(100, maxLines);
        boolean trimmed = false;
        int totalLines;
        synchronized (lines) {
            while (lines.size() > this.maxLines) {
                lines.remove(0);
                trimmed = true;
            }
            totalLines = lines.size();
        }
        if (trimmed) {
            if (clampScrollOffset(totalLines) && scrollOffset == 0) {
                autoScroll = true;
            }
            onContentChanged();
        }
    }
    
    private String truncateLine(String line, int maxWidth) {
        if (line.length() <= maxWidth) return line;
        
        if (maxWidth > 3) {
            return line.substring(0, maxWidth - 3) + "...";
        }
        
        return line.substring(0, maxWidth);
    }
    
    public int getLineCount() {
        synchronized (lines) {
            return lines.size();
        }
    }
    
    public List<String> getAllLines() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }
    
    public boolean isEmpty() {
        synchronized (lines) {
            return lines.isEmpty();
        }
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
