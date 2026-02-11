package io.netnotes.terminal.components.input;

import java.util.function.Consumer;
import org.bouncycastle.util.Arrays;
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
import io.netnotes.noteBytes.NoteBytesEphemeral;
import io.netnotes.noteBytes.collections.NoteBytesRunnablePair;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * TerminalPasswordField - Secure password input with configurable masking
 * 
 * FEATURES:
 * - Extends TerminalRegion for layout integration
 * - Three display modes: MASKED (show mask character), INVISIBLE (hide all), VISIBLE (show text)
 * - Configurable mask character (default: '*')
 * - Secure password handling with NoteBytesEphemeral
 * - High-security mode: prevents password retrieval, only verification
 * - Fixed-length buffer approach from PasswordReader
 * - Same scrolling and positioning as TerminalTextField
 * - Composable styling (background, foreground, attributes)
 * 
 * DISPLAY MODES:
 * - MASKED: Shows mask character for each input character (e.g., "****")
 * - INVISIBLE: Shows nothing (completely hidden text)
 * - VISIBLE: Shows actual text (DISABLED in high-security mode)
 * 
 * SECURITY MODES:
 * 
 * NORMAL MODE (default):
 * - getPassword() returns NoteBytesEphemeral with password bytes
 * - VISIBLE display mode allowed
 * - Password can be retrieved for authentication
 * 
 * HIGH-SECURITY MODE (isHighSecurity = true):
 * - getPassword() returns null (password cannot be retrieved)
 * - VISIBLE display mode automatically converted to MASKED
 * - Password never decoded to String in memory
 * - UTF-8 bytes stored directly, never converted to codepoints
 * - Only verification callbacks allowed (password passed as ephemeral)
 * - Maximum security for sensitive environments
 * 
 * BUFFER ARCHITECTURE:
 * - Fixed-length byte array (MAX_PASSWORD_BYTE_LENGTH = 256 bytes)
 * - Keystroke tracking (MAX_KEYSTROKE_COUNT = 128 keystrokes)
 * - UTF-8 bytes stored directly, not converted to String
 * - Backspace removes by keystroke (multi-byte character aware)
 */
public class TerminalPasswordField extends TerminalRegion {
    public static final int MAX_PASSWORD_BYTE_LENGTH = 256;
    public static final int MAX_KEYSTROKE_COUNT = 128;
    
    /**
     * Display mode for password field
     */
    public enum DisplayMode {
        /** Show mask character for each character */
        MASKED,
        /** Show nothing (completely invisible) */
        INVISIBLE,
        /** Show actual text (DISABLED in high-security mode) */
        VISIBLE
    }
    
    // Security configuration
    private final boolean isHighSecurity;
    
    // Password buffer - fixed length for security
    private NoteBytesEphemeral passwordBytes = new NoteBytesEphemeral(new byte[MAX_PASSWORD_BYTE_LENGTH]);
    private byte[] keystrokeLengths = new byte[MAX_KEYSTROKE_COUNT];
    private int currentLength = 0;      // Total bytes used
    private int keystrokeCount = 0;     // Number of keystrokes
    private int scrollOffset = 0;
    
    // Configuration
    private TextPosition textPosition = TextPosition.LEFT;
    private DisplayMode displayMode = DisplayMode.MASKED;
    private char maskChar = '*';
    private boolean fixedCursor = false;
    
    // Styling
    private TextStyle baseStyle = TextStyle.NORMAL;
    private TextStyle textStyle = TextStyle.NORMAL;
    
    // Event handling
    private final KeyRunTable keyRunTable;
    private Consumer<NoteBytesEphemeral> onComplete;
    private Consumer<NoteBytesEphemeral> onEscape;
    private Runnable onChange;
    private NoteBytesReadOnly keyDownHandlerId = null;
    private NoteBytesReadOnly keyCharHandlerId = null;
    
    /**
     * Create password field in normal security mode
     */
    public TerminalPasswordField(String name) {
        this(name, false);
    }
    
    /**
     * Create password field with security mode option
     * 
     * @param name Field name
     * @param isHighSecurity If true, enables high-security mode:
     *                       - getPassword() returns null
     *                       - VISIBLE mode disabled
     *                       - Password never decoded to String
     */
    public TerminalPasswordField(String name, boolean isHighSecurity) {
        super(name);
        this.isHighSecurity = isHighSecurity;
        this.keyRunTable = new KeyRunTable();
        setFocusable(true);
        setupKeyHandlers();
        
        // In high-security mode, force MASKED if VISIBLE was set
        if (isHighSecurity && displayMode == DisplayMode.VISIBLE) {
            displayMode = DisplayMode.MASKED;
        }

    }
    
    private void setupKeyHandlers() {
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
        ensureCursorVisible();
        invalidate();
    }
    
    @Override
    protected void onFocusLost() {
        super.onFocusLost();
        invalidate();
    }
    
    // ===== CONFIGURATION =====
    
    public TerminalPasswordField withTextPosition(TextPosition position) {
        if (position != null && this.textPosition != position) {
            this.textPosition = position;
            scrollOffset = 0;
            invalidate();
        }
        return this;
    }
    
    public TerminalPasswordField withDisplayMode(DisplayMode mode) {
        if (mode == null) return this;
        
        // High-security mode: prevent VISIBLE
        if (isHighSecurity && mode == DisplayMode.VISIBLE) {
            Log.logError("[TerminalPasswordField] VISIBLE mode disabled in high-security mode");
            return this;
        }
        
        if (this.displayMode != mode) {
            this.displayMode = mode;
            invalidate();
        }
        return this;
    }
    
    public TerminalPasswordField withMaskChar(char maskChar) {
        if (this.maskChar != maskChar) {
            this.maskChar = maskChar;
            if (displayMode == DisplayMode.MASKED) {
                invalidate();
            }
        }
        return this;
    }

    public TerminalPasswordField withFixedCursor(boolean fixedCursor) {
        if (this.fixedCursor != fixedCursor) {
            this.fixedCursor = fixedCursor;
            if (fixedCursor) {
                scrollOffset = 0;
            }
            invalidate();
        }
        return this;
    }
    
    public TerminalPasswordField withBaseStyle(TextStyle style) {
        this.baseStyle = style != null ? style : TextStyle.NORMAL;
        invalidate();
        return this;
    }
    
    public TerminalPasswordField withTextStyle(TextStyle style) {
        this.textStyle = style != null ? style : TextStyle.NORMAL;
        invalidate();
        return this;
    }
    
    public TerminalPasswordField withBackgroundColor(TextStyle.Color color) {
        this.baseStyle = this.baseStyle.copy().bgColor(color);
        invalidate();
        return this;
    }
    
    public TerminalPasswordField withForegroundColor(TextStyle.Color color) {
        this.textStyle = this.textStyle.copy().color(color);
        invalidate();
        return this;
    }
    
    public TerminalPasswordField withOnComplete(Consumer<NoteBytesEphemeral> handler) {
        this.onComplete = handler;
        return this;
    }
    
    public TerminalPasswordField withOnEscape(Consumer<NoteBytesEphemeral> handler) {
        this.onEscape = handler;
        return this;
    }
    
    public TerminalPasswordField withOnChange(Runnable handler) {
        this.onChange = handler;
        return this;
    }
    
    public boolean isHighSecurity() {
        return isHighSecurity;
    }
    
    // ===== PASSWORD MANAGEMENT =====
    
    /**
     * Get password as ephemeral bytes (caller must close)
     * 
     * @return NoteBytesEphemeral containing password, or null in high-security mode
     */
    public NoteBytesEphemeral getPassword() {
        if (isHighSecurity) {
            Log.logError("[TerminalPasswordField] getPassword() disabled in high-security mode");
            return null;
        }
        
        // Return copy of actual password bytes (only used bytes)
        byte[] passwordCopy = new byte[currentLength];
        System.arraycopy(passwordBytes.get(), 0, passwordCopy, 0, currentLength);
        return new NoteBytesEphemeral(passwordCopy);
    }
    
    /**
     * Get password length in bytes
     */
    public int getPasswordLength() {
        if(isHighSecurity){
            Log.logError("[TerminalPasswordField] getPasswordLength() disabled in high-security mode");
            return -1;
        }
        return currentLength;
    }
    
    /**
     * Get keystroke count (number of characters entered)
     */
    public int getKeystrokeCount() {
        if(isHighSecurity){
            Log.logError("[TerminalPasswordField] getKeystrokeCount() disabled in high-security mode");
            return -1;
        }
        return keystrokeCount;
    }
    
    /**
     * Clear password from memory (SECURITY CRITICAL)
     */
    public void clear() {
        if (currentLength > 0 || keystrokeCount > 0 || scrollOffset != 0) {
            passwordBytes.close();
            passwordBytes = new NoteBytesEphemeral(new byte[MAX_PASSWORD_BYTE_LENGTH]);
            Arrays.fill(keystrokeLengths, (byte) 0);
            currentLength = 0;
            keystrokeCount = 0;
            scrollOffset = 0;
            invalidate();
        }
    }
    
    /**
     * Get cursor position (in keystrokes, not bytes)
     * Note: Always at end for password field (no mid-text editing)
     */
    public int getCursorPosition() {
        if(isHighSecurity){
            Log.logError("[TerminalPasswordField] getCursorPosition() disabled in high-security mode");
            return -1;
        }
        return keystrokeCount;
    }
    
    // ===== SCROLLING LOGIC =====
    
    private void ensureCursorVisible() {
        if (fixedCursor) {
            scrollOffset = 0;
            return;
        }
        int visibleWidth = getWidth();
        if (visibleWidth <= 0) return;
        
        int effectiveWidth = hasFocus() ? visibleWidth - 1 : visibleWidth;
        if (effectiveWidth <= 0) return;
        
        // Cursor is always at end (keystrokeCount position)
        // Scroll as needed based on text position
        switch (textPosition) {
            case LEFT:
                if (keystrokeCount - scrollOffset >= effectiveWidth) {
                    scrollOffset = keystrokeCount - effectiveWidth + 1;
                }
                if (keystrokeCount < scrollOffset) {
                    scrollOffset = keystrokeCount;
                }
                break;
                
            case RIGHT:
                int minScroll = Math.max(0, keystrokeCount - effectiveWidth);
                
                if (keystrokeCount < scrollOffset) {
                    scrollOffset = keystrokeCount;
                }
                if (keystrokeCount - scrollOffset >= effectiveWidth) {
                    scrollOffset = Math.min(keystrokeCount - effectiveWidth + 1, minScroll);
                }
                break;
                
            case CENTER:
                int halfWidth = effectiveWidth / 2;
                scrollOffset = Math.max(0, keystrokeCount - halfWidth);
                
                int maxScroll = Math.max(0, keystrokeCount - effectiveWidth);
                scrollOffset = Math.min(scrollOffset, maxScroll);
                break;
        }
    }
    
    /**
     * Calculate visible text representation based on display mode
     * 
     * Note: We work in keystrokes, not bytes, for display purposes
     */
    private RenderInfo calculateRenderInfo() {
        int visibleWidth = getWidth();
        if (visibleWidth <= 0) {
            return new RenderInfo("", 0, 0);
        }
        
        // Build display text based on mode
        String displayText;
        switch (displayMode) {
            case INVISIBLE:
                displayText = "";
                break;
                
            case MASKED:
                // Create masked string (one mask char per keystroke)
                int visibleCount = Math.min(keystrokeCount - scrollOffset, visibleWidth);
                displayText = String.valueOf(maskChar).repeat(Math.max(0, visibleCount));
                break;
                
            case VISIBLE:
                // Show actual text - decode from UTF-8 bytes
                // NOTE: This is why high-security mode disables VISIBLE
                if (currentLength == 0) {
                    displayText = "";
                } else {
                    try {
                        String fullText = new String(passwordBytes.get(), 0, currentLength, 
                                                    java.nio.charset.StandardCharsets.UTF_8);
                        
                        // For visible mode, we need to figure out character positions
                        // This is approximate since we're scrolling by keystroke
                        int startChar = Math.min(scrollOffset, fullText.length());
                        int endChar = Math.min(scrollOffset + visibleWidth, fullText.length());
                        displayText = fullText.substring(startChar, endChar);
                    } catch (Exception e) {
                        Log.logError("[TerminalPasswordField] Failed to decode password for display", e);
                        displayText = String.valueOf(maskChar).repeat(keystrokeCount);
                    }
                }
                break;
                
            default:
                displayText = "";
        }
        
        // Calculate cursor position and drawing offset
        int visualCursorPos = fixedCursor ? 0 : keystrokeCount - scrollOffset;
        
        int drawOffset = 0;
        int displayLength = displayMode == DisplayMode.INVISIBLE ? 0 : 
                           Math.min(keystrokeCount - scrollOffset, visibleWidth);
        
        switch (textPosition) {
            case LEFT:
                drawOffset = 0;
                break;
            case RIGHT:
                drawOffset = Math.max(0, visibleWidth - displayLength);
                break;
            case CENTER:
                drawOffset = Math.max(0, (visibleWidth - displayLength) / 2);
                break;
        }
        
        return new RenderInfo(displayText, visualCursorPos, drawOffset);
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
            
            // Draw text (or mask characters, or nothing)
            if (displayText.length() > 0) {
                printAt(batch, info.drawOffset, 0, displayText.toString(), textStyle);
            }
            
            // Position cursor
            moveCursor(batch, info.drawOffset + info.cursorCol, 0);
        } else {
            // No cursor - just draw text without extra space
            if (!info.text.isEmpty()) {
                printAt(batch, info.drawOffset, 0, info.text, textStyle);
            }
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
        NoteBytes utf8 = event.getUTF8();
        
        if (utf8 == null) {
            Log.logError("[TerminalPasswordField] Key not in lookup table");
            return;
        }
        
        insertUTF8Bytes(utf8);
    }
    
    private void handleEphemeralChar(EphemeralKeyCharEvent event) {
        NoteBytes utf8 = event.getUTF8();
        
        if (utf8 == null) {
            Log.logError("[TerminalPasswordField] Key not in lookup table");
            return;
        }
        
        insertUTF8Bytes(utf8);
    }
    
    /**
     * Insert UTF-8 bytes directly into password buffer
     * This is the core security feature - bytes stored directly, not decoded
     */
    private void insertUTF8Bytes(NoteBytes utf8) {
        // Check buffer space
        if (keystrokeCount >= MAX_KEYSTROKE_COUNT) {
            Log.logError("[TerminalPasswordField] Password too long (max keystrokes exceeded)");
            return;
        }
        
        int utf8ByteLength = utf8.byteLength();
        
        if (currentLength + utf8ByteLength > MAX_PASSWORD_BYTE_LENGTH) {
            Log.logError("[TerminalPasswordField] Password too long (max bytes exceeded)");
            return;
        }
        
        // Copy UTF-8 bytes directly into password buffer
        System.arraycopy(utf8.get(), 0, passwordBytes.get(), currentLength, utf8ByteLength);
        
        // Record this keystroke
        keystrokeLengths[keystrokeCount] = (byte) utf8ByteLength;
        keystrokeCount++;
        currentLength += utf8ByteLength;
        
        ensureCursorVisible();
        invalidate();
        fireOnChange();
    }
    
    private void handleEnter() {
        if (onComplete != null) {
            // Create ephemeral copy of password bytes (only used bytes)
            byte[] passwordCopy = new byte[currentLength];
            System.arraycopy(passwordBytes.get(), 0, passwordCopy, 0, currentLength);
            NoteBytesEphemeral password = new NoteBytesEphemeral(passwordCopy);
            
            onComplete.accept(password);
            // Caller is responsible for closing password
        }
    }
    
    private void handleEscape() {
        if (onEscape != null) {
            // Create ephemeral copy of password bytes (only used bytes)
            byte[] passwordCopy = new byte[currentLength];
            System.arraycopy(passwordBytes.get(), 0, passwordCopy, 0, currentLength);
            NoteBytesEphemeral password = new NoteBytesEphemeral(passwordCopy);
            
            onEscape.accept(password);
            // Caller is responsible for closing password
        }
    }
    
    /**
     * Handle backspace - removes last keystroke (multi-byte aware)
     */
    private void handleBackspace() {
        if (keystrokeCount > 0) {
            // Get the length of the last keystroke
            keystrokeCount--;
            int lastKeystrokeLength = keystrokeLengths[keystrokeCount];
            
            // Zero out the bytes of the last keystroke (security)
            for (int i = 0; i < lastKeystrokeLength; i++) {
                passwordBytes.get()[currentLength - lastKeystrokeLength + i] = 0;
            }
            
            currentLength -= lastKeystrokeLength;
            keystrokeLengths[keystrokeCount] = 0;
            
            ensureCursorVisible();
            invalidate();
            fireOnChange();
        }
    }
    
    /**
     * Delete not supported (cursor always at end for password)
     */
    private void handleDelete() {
        // No-op: password fields don't support mid-text editing
    }
    
    private void handleLeft() {
        // No-op: password fields don't support cursor movement
    }
    
    private void handleRight() {
        // No-op: password fields don't support cursor movement
    }
    
    private void handleHome() {
        // No-op: password fields don't support cursor movement
    }
    
    private void handleEnd() {
        // No-op: cursor always at end
    }
    
    private void fireOnChange() {
        if (onChange != null) {
            onChange.run();
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
        // Securely clear password buffer
        passwordBytes.close();
        Arrays.fill(keystrokeLengths, (byte) 0);
        currentLength = 0;
        keystrokeCount = 0;
    }
}
