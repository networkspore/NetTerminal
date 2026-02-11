package io.netnotes.terminal.input;

import java.util.function.Consumer;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyCharEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyCharEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.noteBytes.KeyRunTable;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.NoteIntegerArray;
import io.netnotes.noteBytes.collections.NoteBytesRunnablePair;

/**
 * TerminalTextInput - Damage-aware text input with cursor
 * 
 * DAMAGE TRACKING:
 * - Character insertion/deletion invalidates only the input line
 * - Cursor movement doesn't trigger render (cursor is moved separately)
 * - Full clear invalidates entire input area
 */
public class TerminalTextInput extends TerminalRenderable {
    public static final int MAX_LENGTH = 256;
    
    private final NoteIntegerArray buffer = new NoteIntegerArray();
    private int cursorPos = 0;
    private final KeyRunTable keyRunTable;
    private Consumer<String> onComplete;
    private Consumer<String> onEscape;
    private NoteBytesReadOnly keyDownHandlerId = null;
    private NoteBytesReadOnly keyCharHandlerId = null;
    
    public TerminalTextInput(String name, int row, int col) {
        this(name, row, col, MAX_LENGTH);
    }
    
    public TerminalTextInput(String name, int row, int col, int maxLength) {
        super(name);
        
        this.keyRunTable = new KeyRunTable(
            new NoteBytesRunnablePair(KeyCodeBytes.ENTER, this::handleEnter),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, this::handleEscape),
            new NoteBytesRunnablePair(KeyCodeBytes.BACKSPACE, this::handleBackspace),
            new NoteBytesRunnablePair(KeyCodeBytes.DELETE, this::handleDelete),
            new NoteBytesRunnablePair(KeyCodeBytes.LEFT, this::handleLeft),
            new NoteBytesRunnablePair(KeyCodeBytes.RIGHT, this::handleRight),
            new NoteBytesRunnablePair(KeyCodeBytes.HOME, this::handleHome),
            new NoteBytesRunnablePair(KeyCodeBytes.END, this::handleEnd)
        );

        keyDownHandlerId = addKeyDownHandler(this::handleKeyDown);
        keyCharHandlerId = addKeyCharHandler(this::handleKeyChar);
        TerminalRectangle region = regionPool.obtain();
        region.set(col, row, maxLength, 1, 0, 0);
        setRegion(region);
    }
    
    @Override
    protected void setupStateTransitions() {
        // No custom transitions
    }
    
    public void setOnComplete(Consumer<String> onComplete) {
        this.onComplete = onComplete;
    }
    
    public void setOnEscape(Consumer<String> onEscape) {
        this.onEscape = onEscape;
    }
    
    public String getText() {
        return buffer.toString();
    }
    
    public NoteIntegerArray getBuffer() {
        return buffer.copy();
    }
    
    /**
     * Clear with smart invalidation
     */
    public void clear() {
        if (!buffer.isEmpty() || cursorPos != 0) {
            buffer.clear();
            cursorPos = 0;
            invalidate();
        }
    }
    
    /**
     * Set text with smart invalidation
     */
    public void setText(String text) {
        String currentText = buffer.toString();
        if (!currentText.equals(text)) {
            buffer.clear();
            buffer.append(text);
            cursorPos = buffer.size();
            invalidate();
        }
    }
    
    // ===== DAMAGE-AWARE RENDERING =====
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        
        this.clear(batch);
                
        // Render the text
        String text = buffer.toString();
        
        this.printAt(batch,0,0, text);
        this.moveCursor(batch, cursorPos, 0);
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
    
    /**
     * Insert codepoint with smart invalidation
     */
    private void insertCodePoint(int codepoint) {
        if (buffer.size() >= MAX_LENGTH) return;
        
        if (Character.isValidCodePoint(codepoint) && 
            !Character.isISOControl(codepoint)) {
            buffer.insertCodePoint(cursorPos, codepoint);
            cursorPos++;
            invalidate();
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
    
    /**
     * Backspace with smart invalidation
     */
    private void handleBackspace() {
        if (cursorPos > 0) {
            buffer.deleteCodePointAt(cursorPos - 1);
            cursorPos--;
            invalidate();
        }
    }
    
    /**
     * Delete with smart invalidation
     */
    private void handleDelete() {
        if (cursorPos < buffer.size()) {
            buffer.deleteCodePointAt(cursorPos);
            invalidate();
        }
    }
    
    /**
     * Cursor movement - updates cursor without invalidating
     * (cursor position is updated via moveCursor command)
     */
    private void handleLeft() {
        if (cursorPos > 0) {
            cursorPos--;
            // Only update cursor position, no need to redraw text
            invalidate();
        }
    }
    
    private void handleRight() {
        if (cursorPos < buffer.size()) {
            cursorPos++;
            invalidate();
        }
    }
    
    private void handleHome() {
        if (cursorPos != 0) {
            cursorPos = 0;
            invalidate();
        }
    }
    
    private void handleEnd() {
        int endPos = buffer.size();
        if (cursorPos != endPos) {
            cursorPos = endPos;
            invalidate();
        }
    }
    
    public void close() {
        buffer.clear();
        cursorPos = 0;
        if (keyCharHandlerId != null) {
        
            removeKeyCharHandler(keyCharHandlerId);
        }
        if (keyDownHandlerId != null) {
            removeKeyDownHandler(keyDownHandlerId);
        }
    }
}