package io.netnotes.terminal.components;

import io.netnotes.terminal.Position;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import java.util.function.Consumer;

public class TerminalButton extends TerminalRegion {
    
    // Button type determines default styling
    public enum ButtonType {
        DEFAULT,    // Standard button
        PRIMARY,    // Primary action
        SUCCESS,    // Positive action
        DANGER,     // Destructive action
        LINK        // Link-style button (no border)
    }
    
    private String text;
    private Consumer<TerminalButton> onActivate;
    private ButtonType type = ButtonType.DEFAULT;
    private boolean enabled = true;
    private boolean showBorder = true;
    private BoxStyle borderStyle = BoxStyle.SINGLE;
    private Position labelPosition = Position.CENTER;
    
    // Style overrides (null = use defaults based on type)
    private TextStyle normalStyle = null;
    private TextStyle focusedStyle = null;
    private TextStyle disabledStyle = null;
    
    public TerminalButton(String name, String text) {
        super(name);
        this.text = text;
        setFocusable(true);
    }
    
    public TerminalButton(String name, String text, ButtonType type) {
        this(name, text);
        this.type = type;
    }
    
    // ===== CONFIGURATION =====
    
    public void setText(String text) {
        if ((this.text == null && text != null) || 
            (this.text != null && !this.text.equals(text))) {
            this.text = text;
            invalidate();
        }
    }
    
    public void setType(ButtonType type) {
        if (this.type != type) {
            this.type = type;
            invalidate();
        }
    }
    
    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            setFocusable(enabled);
            invalidate();
        }
    }
    
    public void setShowBorder(boolean showBorder) {
        if (this.showBorder != showBorder) {
            this.showBorder = showBorder;
            invalidate();
        }
    }
    
    public void setBorderStyle(BoxStyle style) {
        if (this.borderStyle != style) {
            this.borderStyle = style;
            invalidate();
        }
    }
    
    public void setLabelPosition(Position position) {
        if (this.labelPosition != position) {
            this.labelPosition = position;
            invalidate();
        }
    }
    
    // Style overrides
    public void setNormalStyle(TextStyle style) {
        this.normalStyle = style;
        invalidate();
    }
    
    public void setFocusedStyle(TextStyle style) {
        this.focusedStyle = style;
        invalidate();
    }
    
    public void setDisabledStyle(TextStyle style) {
        this.disabledStyle = style;
        invalidate();
    }
    
    public void setOnActivate(Consumer<TerminalButton> handler) {
        this.onActivate = handler;
    }
    
    // ===== EVENT HANDLING =====
    
    @Override
    protected void setupEventHandlers() {
        addKeyDownHandler(event -> {
            if (!enabled) return;
            
            if (event instanceof KeyDownEvent kd) {
                if (kd.getKeyCodeBytes().equals(KeyCodeBytes.ENTER) || 
                    kd.getKeyCodeBytes().equals(KeyCodeBytes.SPACE)) {
                    activate();
                }
            } else if (event instanceof EphemeralKeyDownEvent kd) {
                if (kd.getKeyCodeBytes().equals(KeyCodeBytes.ENTER) || 
                    kd.getKeyCodeBytes().equals(KeyCodeBytes.SPACE)) {
                    activate();
                }
            }
        });
    }
    
    public void activate() {
        if (!enabled) return;
        if (onActivate != null) {
            onActivate.accept(this);
        }
    }
    
    // ===== RENDERING =====
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (text == null || text.isEmpty()) return;
        
        int width = getWidth();
        int height = getHeight();
        if (width < 3 || height < 1) return;
        
        TextStyle style = getEffectiveStyle();
        
        if (showBorder && height >= 3) {
            // Bordered button using drawButton command
            drawButton(batch, 0, 0, width, height, text, labelPosition, hasFocus(), style);
        } else if (showBorder && height == 1) {
            // Single-line bordered button
            renderSingleLineBordered(batch, width, style);
        } else {
            // Borderless button (text only)
            renderBorderless(batch, width, height, style);
        }
    }
    
    private void renderSingleLineBordered(TerminalBatchBuilder batch, int width, TextStyle style) {
        // Format: [ Label ] or [Label] depending on width
        int availableWidth = width - 4; // For "[ " and " ]"
        if (availableWidth < text.length()) {
            availableWidth = width - 2; // For "[" and "]"
        }
        
        String displayText = text.length() > availableWidth ? 
            text.substring(0, availableWidth) : text;
        
        String formatted;
        if (width >= text.length() + 4) {
            int padding = (width - text.length() - 4) / 2;
            String spaces = " ".repeat(padding);
            formatted = "[" + spaces + " " + displayText + " " + spaces + "]";
        } else {
            formatted = "[" + displayText + "]";
        }
        
        printAt(batch, 0, 0, formatted, style);
    }
    
    private void renderBorderless(TerminalBatchBuilder batch, int width, int height, TextStyle style) {
        // Center text in available space
        int[] pos = calculateLocalPosition(text, labelPosition);
        printAt(batch, pos[0], pos[1], text, style);
        
        // Add focus indicators if focused
        if (hasFocus() && enabled) {
            printAt(batch, Math.max(0, pos[0] - 2), pos[1], ">", style);
            printAt(batch, Math.min(width - 1, pos[0] + text.length() + 1), pos[1], "<", style);
        }
    }
    
    private TextStyle getEffectiveStyle() {
        // Priority: explicit style override > type-based default > fallback
        if (!enabled) {
            return disabledStyle != null ? disabledStyle : TextStyle.BUTTON_DISABLED;
        } else if (hasFocus()) {
            if (focusedStyle != null) return focusedStyle;
            return TextStyle.BUTTON_FOCUSED;
        } else {
            if (normalStyle != null) return normalStyle;
            
            return switch (type) {
                case PRIMARY -> TextStyle.BUTTON_PRIMARY;
                case SUCCESS -> TextStyle.BUTTON_SUCCESS;
                case DANGER -> TextStyle.BUTTON_DANGER;
                case LINK -> TextStyle.LINK;
                default -> TextStyle.BUTTON;
            };
        }
    }
    
    // ===== GETTERS =====
    
    public String getText() { return text; }
    public ButtonType getType() { return type; }
    public boolean isEnabled() { return enabled; }
    public boolean isShowBorder() { return showBorder; }

    @Override
    public int getPreferredWidth() {
        int textLen = text != null ? text.length() : 0;
        int borderExtra = showBorder ? 4 : 0;
        return Math.max(getMinWidth(), textLen + borderExtra);
    }

    @Override
    public int getPreferredHeight() {
        return Math.max(getMinHeight(), 1);
    }
    
    // ===== BUILDER =====
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name = "button";
        private String text = "Button";
        private ButtonType type = ButtonType.DEFAULT;
        private boolean enabled = true;
        private boolean showBorder = true;
        private BoxStyle borderStyle = BoxStyle.SINGLE;
        private Position labelPosition = Position.CENTER;
        private int x = 0, y = 0, width = 10, height = 3;
        private Consumer<TerminalButton> onActivate = null;
        
        public Builder name(String name) { this.name = name; return this; }
        public Builder text(String text) { this.text = text; return this; }
        public Builder type(ButtonType type) { this.type = type; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder showBorder(boolean showBorder) { this.showBorder = showBorder; return this; }
        public Builder borderStyle(BoxStyle style) { this.borderStyle = style; return this; }
        public Builder labelPosition(Position pos) { this.labelPosition = pos; return this; }
        public Builder position(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }
        public Builder bounds(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            return this;
        }
        public Builder onActivate(Consumer<TerminalButton> handler) {
            this.onActivate = handler;
            return this;
        }
        
        public TerminalButton build() {
            TerminalButton button = new TerminalButton(name, text, type);
            button.setBounds(x, y, width, height);
            button.setEnabled(enabled);
            button.setShowBorder(showBorder);
            button.setBorderStyle(borderStyle);
            button.setLabelPosition(labelPosition);
            button.setOnActivate(onActivate);
            return button;
        }
    }
}
