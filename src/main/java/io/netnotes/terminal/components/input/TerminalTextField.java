package io.netnotes.terminal.components.input;

import java.util.function.Consumer;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyCharEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyCharEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.engine.ui.TextPosition;
import io.netnotes.noteBytes.KeyRunTable;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.NoteIntegerArray;
import io.netnotes.noteBytes.collections.NoteBytesRunnablePair;

/**
 * TerminalTextField - Scrollable, focusable text input with configurable styling
 * 
 * FEATURES:
 * - Extends TerminalRegion for layout integration
 * - Configurable text positioning (LEFT, CENTER, RIGHT)
 * - Horizontal scrolling when text exceeds visible width
 * - Composable styling (background, foreground, attributes)
 * - Focus-aware cursor management
 * - Damage-aware rendering
 * 
 * SCROLLING BEHAVIOR:
 * - LEFT: Text scrolls left as cursor moves right past visible area
 * - RIGHT: Text scrolls right as cursor moves left past visible area
 * - CENTER: Text scrolls bidirectionally to keep cursor visible
 * 
 * CURSOR MANAGEMENT:
 * - Cursor visible only when focused
 * - Cursor positioned with space reserved in text (when focused)
 * - Cursor position preserved across focus changes
 * 
 * STYLING:
 * - Base style applied to entire field background
 * - Text style for foreground text
 * - Automatic background fill using fillRegion
 */
public class TerminalTextField extends TerminalRegion {
    public static final int MAX_LENGTH = 256;
    
    // Text buffer and cursor
    private final NoteIntegerArray buffer = new NoteIntegerArray();
    private int cursorPos = 0;
    private int scrollOffset = 0;  // How many characters are scrolled off-screen
    
    // Configuration
    private TextPosition textPosition = TextPosition.LEFT;
    private int maxLength = MAX_LENGTH;
    
    // Styling
    private TextStyle baseStyle = TextStyle.NORMAL;      // Background and base
    private TextStyle textStyle = TextStyle.NORMAL;      // Foreground text
    
    // Event handling
    private final KeyRunTable keyRunTable;
    private Consumer<String> onComplete;
    private Consumer<String> onEscape;
    private Consumer<String> onChange;
    private NoteBytesReadOnly keyDownHandlerId = null;
    private NoteBytesReadOnly keyCharHandlerId = null;
    
    public TerminalTextField(String name) {
        super(name);
        this.keyRunTable = new KeyRunTable();
        
        setFocusable(true);
        setupKeyHandlers();
    }
    
    protected void setupKeyHandlers() {
        this.keyRunTable.setKeyRunnables(
            new NoteBytesRunnablePair(KeyCodeBytes.ENTER, this::handleEnter),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, this::handleEscape),
            new NoteBytesRunnablePair(KeyCodeBytes.BACKSPACE, this::handleBackspace),
            new NoteBytesRunnablePair(KeyCodeBytes.DELETE, this::handleDelete),
            new NoteBytesRunnablePair(KeyCodeBytes.LEFT, this::handleLeft),
            new NoteBytesRunnablePair(KeyCodeBytes.RIGHT, this::handleRight),
            new NoteBytesRunnablePair(KeyCodeBytes.HOME, this::handleHome),
            new NoteBytesRunnablePair(KeyCodeBytes.END, this::handleEnd)
        );
    }
    
    @Override
    protected void setupEventHandlers() {
        super.setupEventHandlers();
        keyDownHandlerId = addKeyDownHandler(this::handleKeyDown);
        keyCharHandlerId = addKeyCharHandler(this::handleKeyChar);
    }
    
    @Override
    protected void onFocusGained() {
        super.onFocusGained();
        // Restore cursor position when gaining focus
        ensureCursorVisible();
        invalidate();
    }
    
    @Override
    protected void onFocusLost() {
        super.onFocusLost();
        // Cursor will not be drawn when not focused
        invalidate();
    }
    
    // ===== CONFIGURATION =====
    
    public TerminalTextField withTextPosition(TextPosition position) {
        if (position != null && this.textPosition != position) {
            this.textPosition = position;
            scrollOffset = 0;  // Reset scroll when changing position
            invalidate();
        }
        return this;
    }
    
    public TerminalTextField withMaxLength(int maxLength) {
        this.maxLength = Math.max(1, Math.min(maxLength, MAX_LENGTH));
        return this;
    }
    
    public TerminalTextField withBaseStyle(TextStyle style) {
        this.baseStyle = style != null ? style : TextStyle.NORMAL;
        invalidate();
        return this;
    }
    
    public TerminalTextField withTextStyle(TextStyle style) {
        this.textStyle = style != null ? style : TextStyle.NORMAL;
        invalidate();
        return this;
    }
    
    public TerminalTextField withBackgroundColor(TextStyle.Color color) {
        this.baseStyle = this.baseStyle.copy().bgColor(color);
        invalidate();
        return this;
    }
    
    public TerminalTextField withForegroundColor(TextStyle.Color color) {
        this.textStyle = this.textStyle.copy().color(color);
        invalidate();
        return this;
    }
    
    public TerminalTextField withOnComplete(Consumer<String> handler) {
        this.onComplete = handler;
        return this;
    }
    
    public TerminalTextField withOnEscape(Consumer<String> handler) {
        this.onEscape = handler;
        return this;
    }
    
    public TerminalTextField withOnChange(Consumer<String> handler) {
        this.onChange = handler;
        return this;
    }
    
    // ===== TEXT MANAGEMENT =====
    
    public String getText() {
        return buffer.toString();
    }
    
    public void setText(String text) {
        String currentText = buffer.toString();
        if (!currentText.equals(text)) {
            buffer.clear();
            if (text != null) {
                buffer.append(text);
            }
            cursorPos = buffer.size();
            scrollOffset = 0;
            ensureCursorVisible();
            invalidate();
        }
    }
    
    public void clear() {
        if (!buffer.isEmpty() || cursorPos != 0 || scrollOffset != 0) {
            buffer.clear();
            cursorPos = 0;
            scrollOffset = 0;
            invalidate();
        }
    }
    
    public int getCursorPosition() {
        return cursorPos;
    }
    
    public void setCursorPosition(int pos) {
        int newPos = Math.max(0, Math.min(pos, buffer.size()));
        if (newPos != cursorPos) {
            cursorPos = newPos;
            ensureCursorVisible();
            invalidate();
        }
    }
    
    // ===== SCROLLING LOGIC =====
    
    /**
     * Ensure cursor is visible within the field by adjusting scroll offset
     */
    private void ensureCursorVisible() {
        int visibleWidth = getWidth();
        if (visibleWidth <= 0) return;
        
        // When focused, we need space for the cursor
        int effectiveWidth = hasFocus() ? visibleWidth - 1 : visibleWidth;
        if (effectiveWidth <= 0) return;
        
        switch (textPosition) {
            case LEFT:
                // Scroll left if cursor is beyond right edge
                if (cursorPos - scrollOffset >= effectiveWidth) {
                    scrollOffset = cursorPos - effectiveWidth + 1;
                }
                // Scroll right if cursor is before left edge
                if (cursorPos < scrollOffset) {
                    scrollOffset = cursorPos;
                }
                break;
                
            case RIGHT:
                // For right-aligned, we show the rightmost characters
                int textLength = buffer.size();
                int minScroll = Math.max(0, textLength - effectiveWidth);
                
                if (cursorPos < scrollOffset) {
                    scrollOffset = cursorPos;
                }
                if (cursorPos - scrollOffset >= effectiveWidth) {
                    scrollOffset = Math.min(cursorPos - effectiveWidth + 1, minScroll);
                }
                break;
                
            case CENTER:
                // Try to keep cursor near center
                int halfWidth = effectiveWidth / 2;
                scrollOffset = Math.max(0, cursorPos - halfWidth);
                
                // Don't scroll past the end
                int maxScroll = Math.max(0, buffer.size() - effectiveWidth);
                scrollOffset = Math.min(scrollOffset, maxScroll);
                break;
        }
    }
    
    /**
     * Calculate visible text and cursor position for rendering
     */
    private RenderInfo calculateRenderInfo() {
        int visibleWidth = getWidth();
        if (visibleWidth <= 0) {
            return new RenderInfo("", 0, 0);
        }
        
        String fullText = buffer.toString();
        int textLength = fullText.length();
        
        // Calculate visible text based on scroll
        int endIdx = Math.min(textLength, scrollOffset + visibleWidth);
        String visibleText = fullText.substring(scrollOffset, endIdx);
        
        // Calculate cursor position in visible space
        int visualCursorPos = cursorPos - scrollOffset;
        
        // Calculate drawing offset based on text position
        int drawOffset = 0;
        switch (textPosition) {
            case LEFT:
                drawOffset = 0;
                break;
            case RIGHT:
                drawOffset = Math.max(0, visibleWidth - visibleText.length());
                break;
            case CENTER:
                drawOffset = Math.max(0, (visibleWidth - visibleText.length()) / 2);
                break;
        }
        
        return new RenderInfo(visibleText, visualCursorPos, drawOffset);
    }
    
    private static class RenderInfo {
        final String text;
        final int cursorCol;
        final int drawOffset;
        
        RenderInfo(String text, int cursorCol, int drawOffset) {
            this.text = text;
            this.cursorCol = cursorCol;
            this.drawOffset = drawOffset;
        }
    }
    
    // ===== RENDERING =====
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        // Fill entire field with background style
        fillRegion(batch, 0, 0, getWidth(), getHeight(), ' ', baseStyle);
        
        RenderInfo info = calculateRenderInfo();
        
        if (hasFocus()) {
            // Insert space at cursor position for cursor visibility
            StringBuilder displayText = new StringBuilder(info.text);
            if (info.cursorCol >= 0 && info.cursorCol <= displayText.length()) {
                displayText.insert(info.cursorCol, ' ');
            }
            
            // Draw text
            printAt(batch, info.drawOffset, 0, displayText.toString(), textStyle);
            
            // Position cursor at the space we inserted
            moveCursor(batch, info.drawOffset + info.cursorCol, 0);
        } else {
            // No cursor - just draw text without extra space
            printAt(batch, info.drawOffset, 0, info.text, textStyle);
        }
    }
    
    // ===== EVENT HANDLING =====
    
    private void handleKeyDown(RoutedEvent event) {
        if (event instanceof KeyDownEvent keyDown) {
            handleKeyDown(keyDown);
        } else if (event instanceof EphemeralKeyDownEvent keyDown) {
            handleEphemeralKeyDown(keyDown);
            keyDown.close();
        }
    }
    
    private void handleKeyChar(RoutedEvent event) {
        if (event instanceof KeyCharEvent keyChar) {
            handleKeyChar(keyChar);
        } else if (event instanceof EphemeralKeyCharEvent keyChar) {
            handleEphemeralChar(keyChar);
            keyChar.close();
        }
    }
    
    private void handleEphemeralKeyDown(EphemeralKeyDownEvent event) {
        keyRunTable.run(event.getKeyCodeBytes());
    }
    
    private void handleKeyDown(KeyDownEvent event) {
        keyRunTable.run(event.getKeyCodeBytes());
    }
    
    private void handleKeyChar(KeyCharEvent event) {
        NoteBytes codepointBytes = event.getCodepointData();
        if (codepointBytes != null) {
            int codepoint = codepointBytes.getAsInt();
            insertCodePoint(codepoint);
        }
    }
    
    private void handleEphemeralChar(EphemeralKeyCharEvent event) {
        NoteBytes codepointBytes = event.getCodePointBytes();
        if (codepointBytes != null) {
            int codepoint = codepointBytes.getAsInt();
            insertCodePoint(codepoint);
        }
    }
    
    private void insertCodePoint(int codepoint) {
        if (buffer.size() >= maxLength) return;
        
        if (Character.isValidCodePoint(codepoint) && !Character.isISOControl(codepoint)) {
            buffer.insertCodePoint(cursorPos, codepoint);
            cursorPos++;
            ensureCursorVisible();
            invalidate();
            fireOnChange();
        }
    }
    
    private void handleEnter() {
        if (onComplete != null) {
            onComplete.accept(buffer.toString());
        }
    }
    
    private void handleEscape() {
        if (onEscape != null) {
            onEscape.accept(buffer.toString());
        }
    }
    
    private void handleBackspace() {
        if (cursorPos > 0) {
            buffer.deleteCodePointAt(cursorPos - 1);
            cursorPos--;
            ensureCursorVisible();
            invalidate();
            fireOnChange();
        }
    }
    
    private void handleDelete() {
        if (cursorPos < buffer.size()) {
            buffer.deleteCodePointAt(cursorPos);
            ensureCursorVisible();
            invalidate();
            fireOnChange();
        }
    }
    
    private void handleLeft() {
        if (cursorPos > 0) {
            cursorPos--;
            ensureCursorVisible();
            invalidate();
        }
    }
    
    private void handleRight() {
        if (cursorPos < buffer.size()) {
            cursorPos++;
            ensureCursorVisible();
            invalidate();
        }
    }
    
    private void handleHome() {
        if (cursorPos != 0) {
            cursorPos = 0;
            ensureCursorVisible();
            invalidate();
        }
    }
    
    private void handleEnd() {
        int endPos = buffer.size();
        if (cursorPos != endPos) {
            cursorPos = endPos;
            ensureCursorVisible();
            invalidate();
        }
    }
    
    private void fireOnChange() {
        if (onChange != null) {
            onChange.accept(buffer.toString());
        }
    }
    
    // ===== CLEANUP =====
    
    @Override
    protected void onCleanup() {
        if (keyCharHandlerId != null) {
            removeKeyCharHandler(keyCharHandlerId);
            keyCharHandlerId = null;
        }
        if (keyDownHandlerId != null) {
            removeKeyDownHandler(keyDownHandlerId);
            keyDownHandlerId = null;
        }
        buffer.clear();
    }
}