package io.netnotes.terminal.components;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.netnotes.terminal.Position;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;

/**
 * TerminalTextBox - Scrollable text display with focus support
 * 
 * COMPONENT PATTERN:
 * - Mutable state via setText(), addLine(), clear()
 * - Focus support for keyboard scrolling
 * - Builder available for fluent construction
 * - Damage-optimized updates
 * 
 * USAGE:
 * // Simple constructor
 * TerminalTextBox box = new TerminalTextBox("logs");
 * box.setBounds(5, 5, 40, 10);
 * box.addLine("Log line 1");
 * box.addLine("Log line 2");
 * 
 * // Or builder pattern
 * TerminalTextBox box = TerminalTextBox.builder()
 *     .title("Logs")
 *     .size(40, 10)
 *     .content("Line 1", "Line 2")
 *     .scrollable(true)
 *     .build();
 */
public class TerminalTextBox extends TerminalRegion {
    
    // Styling
    private BoxStyle boxStyle = BoxStyle.SINGLE;
    private TextStyle textStyle = TextStyle.NORMAL;
    private TextStyle borderStyleNormal = TextStyle.BORDER;
    private TextStyle borderStyleFocused = TextStyle.BORDER_FOCUSED;
    private TextStyle scrollIndicatorStyle = TextStyle.STATUS_INFO;
    
    // Title
    private String title = null;
    private Position titlePlacement = Position.TOP_CENTER;
    
    // Content
    private final List<String> lines = new ArrayList<>();
    private final List<TextStyle> lineStyles = new ArrayList<>(); // Per-line styling
    private ContentAlignment alignment = ContentAlignment.LEFT;
    private int padding = 1;
    
    // Features
    private boolean showLineNumbers = false;
    private int lineNumberWidth = 4;
    private TextStyle lineNumberStyle = TextStyle.BRIGHT_BLACK;
    
    // Scrolling
    private boolean scrollable = false;
    private int verticalScroll = 0;
    private int horizontalScroll = 0;
    private TextOverflow overflow = TextOverflow.TRUNCATE;
    
    // Search highlighting
    private String searchTerm = null;
    private TextStyle searchHighlightStyle = TextStyle.BLACK_ON_YELLOW;
    
    // ===== CONSTRUCTORS =====
    
    public TerminalTextBox(String name) {
        super(name);
    }
    
    public TerminalTextBox(String name, String text) {
        this(name);
        addLine(text);
    }
    
    private TerminalTextBox(Builder builder) {
        super(builder.name);
        this.boxStyle = builder.boxStyle;
        this.textStyle = builder.textStyle;
        this.title = builder.title;
        this.titlePlacement = builder.titlePlacement;
        this.lines.addAll(builder.lines);
        this.alignment = builder.alignment;
        this.padding = builder.padding;
        this.scrollable = builder.scrollable;
        this.overflow = builder.overflow;
        this.showLineNumbers = builder.showLineNumbers;
        setFocusable(scrollable);
        setBounds(builder.x, builder.y, builder.width, builder.height);
    }
    
    // ===== MUTABLE API =====
    
    public void setText(String text) {
        lines.clear();
        lineStyles.clear();
        lines.add(text);
        lineStyles.add(textStyle);
        verticalScroll = 0;
        invalidate();
    }
    
    public void setLines(List<String> newLines) {
        lines.clear();
        lineStyles.clear();
        lines.addAll(newLines);
        // Fill with default styles
        for (int i = 0; i < newLines.size(); i++) {
            lineStyles.add(textStyle);
        }
        verticalScroll = Math.min(verticalScroll, Math.max(0, lines.size() - getContentHeight()));
        invalidate();
    }
    
    public void addLine(String line) {
        addLine(line, textStyle);
    }
    
    public void addLine(String line, TextStyle style) {
        lines.add(line);
        lineStyles.add(style != null ? style : textStyle);
        invalidateContent();
    }
    
    public void setLineStyle(int index, TextStyle style) {
        if (index >= 0 && index < lines.size()) {
            lineStyles.set(index, style != null ? style : textStyle);
            invalidateContent();
        }
    }
    
    public void insertLine(int index, String line) {
        insertLine(index, line, textStyle);
    }
    
    public void insertLine(int index, String line, TextStyle style) {
        lines.add(Math.min(index, lines.size()), line);
        lineStyles.add(Math.min(index, lineStyles.size()), style != null ? style : textStyle);
        invalidateContent();
    }
    
    public void removeLine(int index) {
        if (index >= 0 && index < lines.size()) {
            lines.remove(index);
            lineStyles.remove(index);
            verticalScroll = Math.min(verticalScroll, Math.max(0, lines.size() - getContentHeight()));
            invalidateContent();
        }
    }
    
    public void clear() {
        if (!lines.isEmpty()) {
            lines.clear();
            lineStyles.clear();
            verticalScroll = 0;
            horizontalScroll = 0;
            invalidate();
        }
    }
    
    public void setTitle(String title) {
        if ((this.title == null && title != null) || 
            (this.title != null && !this.title.equals(title))) {
            this.title = title;
            invalidate();
        }
    }
    
    public void setScrollable(boolean scrollable) {
        if (this.scrollable != scrollable) {
            this.scrollable = scrollable;
            setFocusable(scrollable);
            if (!scrollable) {
                verticalScroll = 0;
                horizontalScroll = 0;
            }
            invalidate();
        }
    }
    
    public void setShowLineNumbers(boolean show) {
        if (this.showLineNumbers != show) {
            this.showLineNumbers = show;
            invalidate();
        }
    }
    
    public void setSearchTerm(String term) {
        if ((this.searchTerm == null && term != null) || 
            (this.searchTerm != null && !this.searchTerm.equals(term))) {
            this.searchTerm = term;
            invalidateContent();
        }
    }
    
    public void setAlignment(ContentAlignment alignment) {
        if (this.alignment != alignment) {
            this.alignment = alignment;
            invalidateContent();
        }
    }
    
    public void setTextStyle(TextStyle style) {
        if (this.textStyle != style) {
            this.textStyle = style;
            invalidateContent();
        }
    }
    
    public void setBorderStyle(BoxStyle style) {
        if (this.boxStyle != style) {
            this.boxStyle = style;
            invalidate();
        }
    }
    
    // ===== SCROLLING =====
    
    public void scrollVertical(int delta) {
        if (!scrollable || lines.isEmpty()) return;
        
        int contentHeight = getContentHeight();
        int maxScroll = Math.max(0, lines.size() - contentHeight);
        int newScroll = Math.max(0, Math.min(maxScroll, verticalScroll + delta));
        
        if (newScroll != verticalScroll) {
            verticalScroll = newScroll;
            invalidateContent();
        }
    }
    
    public void scrollHorizontal(int delta) {
        if (overflow != TextOverflow.SCROLL) return;
        
        int maxWidth = lines.stream().mapToInt(String::length).max().orElse(0);
        int contentWidth = getContentWidth();
        int maxScroll = Math.max(0, maxWidth - contentWidth);
        int newScroll = Math.max(0, Math.min(maxScroll, horizontalScroll + delta));
        
        if (newScroll != horizontalScroll) {
            horizontalScroll = newScroll;
            invalidateContent();
        }
    }
    
    public void scrollToTop() { verticalScroll = 0; invalidateContent(); }
    public void scrollToBottom() { 
        verticalScroll = Math.max(0, lines.size() - getContentHeight()); 
        invalidateContent();
    }
    
    // ===== FOCUS MANAGEMENT =====
    
    @Override
    protected void setupEventHandlers() {
        addKeyDownHandler(event -> {
            if (!(event instanceof KeyDownEvent kd) || !scrollable) return;
            
            if (kd.getKeyCodeBytes().equals(KeyCodeBytes.UP)) {
                scrollVertical(-1);
            } else if (kd.getKeyCodeBytes().equals(KeyCodeBytes.DOWN)) {
                scrollVertical(1);
            } else if (kd.getKeyCodeBytes().equals(KeyCodeBytes.PAGE_UP)) {
                scrollVertical(-getContentHeight());
            } else if (kd.getKeyCodeBytes().equals(KeyCodeBytes.PAGE_DOWN)) {
                scrollVertical(getContentHeight());
            } else if (kd.getKeyCodeBytes().equals(KeyCodeBytes.HOME)) {
                scrollToTop();
            } else if (kd.getKeyCodeBytes().equals(KeyCodeBytes.END)) {
                scrollToBottom();
            } else if (kd.getKeyCodeBytes().equals(KeyCodeBytes.LEFT)) {
                scrollHorizontal(-1);
            } else if (kd.getKeyCodeBytes().equals(KeyCodeBytes.RIGHT)) {
                scrollHorizontal(1);
            }
        });
    }
    
    @Override
    public void onFocusGained() {
        invalidate();
    }
    
    @Override
    public void onFocusLost() {
        invalidate();
    }
    
    // ===== RENDERING =====
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        int width = getWidth();
        int height = getHeight();
        if (width < 3 || height < 3) return;
        
        // Border (use focused style when has focus)
        TextStyle borderStyle = hasFocus() ? borderStyleFocused : borderStyleNormal;
        
        if (title != null && !title.isEmpty()) {
            drawBox(batch, 0, 0, width, height, title, titlePlacement, boxStyle, borderStyle);
        } else {
            drawBox(batch, 0, 0, width, height, boxStyle, borderStyle);
        }
        
        // Content
        if (!lines.isEmpty()) {
            renderContent(batch, width, height);
        }
        
        // Scroll indicators
        if (scrollable && lines.size() > getContentHeight()) {
            renderScrollIndicators(batch, width, height);
        }
    }
    
    private void renderContent(TerminalBatchBuilder batch, int width, int height) {
        int contentStartRow = getContentStartRow();
        int contentHeight = getContentHeight();
        int contentWidth = getContentWidth();
        
        int startLine = verticalScroll;
        int endLine = Math.min(lines.size(), startLine + contentHeight);
        
        for (int i = startLine; i < endLine; i++) {
            String line = lines.get(i);
            TextStyle lineStyle = lineStyles.get(i);
            int row = contentStartRow + (i - startLine);
            int col = padding + 1;
            
            // Line numbers
            if (showLineNumbers) {
                String lineNum = String.format("%" + lineNumberWidth + "d", i + 1);
                printAt(batch, col, row, lineNum, lineNumberStyle);
                col += lineNumberWidth + 1; // +1 for space
            }
            
            // Process line content
            String displayLine = processOverflow(line, contentWidth - (showLineNumbers ? lineNumberWidth + 1 : 0));
            
            // Apply alignment
            int textCol = switch (alignment) {
                case CENTER -> col + (contentWidth - displayLine.length()) / 2;
                case RIGHT -> width - padding - 1 - displayLine.length();
                default -> col;
            };
            
            // Render with search highlighting if needed
            if (searchTerm != null && !searchTerm.isEmpty() && line.contains(searchTerm)) {
                renderLineWithHighlight(batch, textCol, row, displayLine, line, lineStyle);
            } else {
                printAt(batch, textCol, row, displayLine, lineStyle);
            }
        }
    }
    
    private void renderLineWithHighlight(TerminalBatchBuilder batch, int col, int row, 
                                         String displayLine, String originalLine, TextStyle baseStyle) {
        int searchIndex = originalLine.indexOf(searchTerm);
        if (searchIndex == -1) {
            printAt(batch, col, row, displayLine, baseStyle);
            return;
        }
        
        // Render in segments: before match, match (highlighted), after match
        String before = originalLine.substring(0, searchIndex);
        String match = originalLine.substring(searchIndex, searchIndex + searchTerm.length());
        String after = originalLine.substring(searchIndex + searchTerm.length());
        
        int currentCol = col;
        if (!before.isEmpty()) {
            printAt(batch, currentCol, row, before, baseStyle);
            currentCol += before.length();
        }
        printAt(batch, currentCol, row, match, searchHighlightStyle);
        currentCol += match.length();
        if (!after.isEmpty()) {
            printAt(batch, currentCol, row, after, baseStyle);
        }
    }
    
    private void renderScrollIndicators(TerminalBatchBuilder batch, int width, int height) {
        int contentStartRow = getContentStartRow();
        int contentHeight = getContentHeight();
        int indicatorCol = width - 2;
        
        // Up indicator
        if (verticalScroll > 0) {
            printAt(batch, indicatorCol, contentStartRow, "▲", scrollIndicatorStyle);
        }
        
        // Down indicator
        if (verticalScroll + contentHeight < lines.size()) {
            printAt(batch, indicatorCol, contentStartRow + contentHeight - 1, "▼", scrollIndicatorStyle);
        }
        
        // Scroll position indicator (bar)
        if (contentHeight > 2) {
            float scrollPercent = (float) verticalScroll / Math.max(1, lines.size() - contentHeight);
            int barPos = (int) (scrollPercent * (contentHeight - 2));
            printAt(batch, indicatorCol, contentStartRow + 1 + barPos, "█", scrollIndicatorStyle);
        }
        
        // Horizontal indicators
        if (overflow == TextOverflow.SCROLL) {
            int midRow = contentStartRow + contentHeight / 2;
            if (horizontalScroll > 0) {
                printAt(batch, 1, midRow, "◄", scrollIndicatorStyle);
            }
            
            int maxWidth = lines.stream().mapToInt(String::length).max().orElse(0);
            if (horizontalScroll + getContentWidth() < maxWidth) {
                printAt(batch, width - 2, midRow, "►", scrollIndicatorStyle);
            }
        }
    }
    
    // ===== HELPER METHODS =====
    
    private String processOverflow(String line, int availableWidth) {
        if (line.length() <= availableWidth) return line;
        
        return switch (overflow) {
            case WRAP -> line.substring(0, availableWidth);
            case TRUNCATE -> line.substring(0, availableWidth - 3) + "...";
            case TRUNCATE_START -> "..." + line.substring(line.length() - availableWidth + 3);
            case SCROLL -> {
                int start = Math.min(horizontalScroll, line.length() - availableWidth);
                int end = Math.min(line.length(), start + availableWidth);
                yield line.substring(Math.max(0, start), end);
            }
        };
    }
    
    private int getContentStartRow() {
        return 1 + padding;
    }
    
    private int getContentHeight() {
        int reserved = 2 + (2 * padding);
        return Math.max(1, getHeight() - reserved);
    }
    
    private int getContentWidth() {
        return Math.max(1, getWidth() - 2 - (2 * padding));
    }
    
    private void invalidateContent() {
        int contentStartRow = getContentStartRow();
        int contentHeight = getContentHeight();
        invalidateRegion(1, contentStartRow, getWidth() - 2, contentHeight);
    }
    
    // ===== GETTERS =====
    
    public List<String> getLines() { return new ArrayList<>(lines); }
    public int getLineCount() { return lines.size(); }
    public String getLine(int index) { 
        return index >= 0 && index < lines.size() ? lines.get(index) : null; 
    }
    public int getVerticalScroll() { return verticalScroll; }
    public boolean isScrollable() { return scrollable; }

    @Override
    public int getPreferredWidth() {
        int maxLine = lines.stream().mapToInt(String::length).max().orElse(0);
        if (showLineNumbers) {
            maxLine += lineNumberWidth + 1;
        }

        if (title != null) {
            maxLine = Math.max(maxLine, title.length());
        }

        int preferred = maxLine + (2 * padding) + 2;
        return Math.max(getMinWidth(), preferred);
    }

    @Override
    public int getPreferredHeight() {
        int contentLines = Math.max(1, lines.size());
        int preferred = contentLines + (2 * padding) + 2;
        return Math.max(getMinHeight(), preferred);
    }

    @Override
    public int getMinWidth() {
        int paddedMin = (2 * padding) + 2;
        return Math.max(super.getMinWidth(), Math.max(3, paddedMin));
    }

    @Override
    public int getMinHeight() {
        int paddedMin = (2 * padding) + 2;
        return Math.max(super.getMinHeight(), Math.max(3, paddedMin));
    }
    
    // ===== ENUMS =====
    
    public enum TextOverflow { WRAP, TRUNCATE, SCROLL, TRUNCATE_START }
    public enum ContentAlignment { LEFT, CENTER, RIGHT }
    
    // ===== BUILDER =====
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name = "textbox";
        private int x = 0, y = 0, width = 40, height = 10;
        private BoxStyle boxStyle = BoxStyle.SINGLE;
        private TextStyle textStyle = TextStyle.NORMAL;
        private String title = null;
        private Position titlePlacement = Position.TOP_CENTER;
        private List<String> lines = new ArrayList<>();
        private ContentAlignment alignment = ContentAlignment.LEFT;
        private int padding = 1;
        private boolean scrollable = false;
        private boolean showLineNumbers = false;
        private TextOverflow overflow = TextOverflow.TRUNCATE;
        
        public Builder name(String name) { this.name = name; return this; }
        public Builder position(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }
        public Builder bounds(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            return this;
        }
        public Builder boxStyle(BoxStyle style) { this.boxStyle = style; return this; }
        public Builder textStyle(TextStyle style) { this.textStyle = style; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder titlePlacement(Position placement) { this.titlePlacement = placement; return this; }
        public Builder content(String... lines) { 
            this.lines.addAll(Arrays.asList(lines)); 
            return this; 
        }
        public Builder addLine(String line) { this.lines.add(line); return this; }
        public Builder alignment(ContentAlignment align) { this.alignment = align; return this; }
        public Builder padding(int padding) { this.padding = padding; return this; }
        public Builder scrollable(boolean scrollable) { this.scrollable = scrollable; return this; }
        public Builder showLineNumbers(boolean show) { this.showLineNumbers = show; return this; }
        public Builder overflow(TextOverflow overflow) { this.overflow = overflow; return this; }
        
        public TerminalTextBox build() {
            return new TerminalTextBox(this);
        }
    }
    
    @Override
    protected void setupStateTransitions() {}
}
