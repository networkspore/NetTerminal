package io.netnotes.terminal.components;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TextStyle;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import java.util.function.Consumer;

public class TerminalButton extends TerminalRenderable {
    
    private String text;
    private Consumer<TerminalButton> onActivate;

    public TerminalButton(String name, String text) {
        super(name);
        this.text = text;
    }
    
    public void setText(String text) {
        if ((this.text == null && text != null) || 
            (this.text != null && !this.text.equals(text))) {
            this.text = text;
            invalidate();
        }
    }
    
    public void setOnActivate(Consumer<TerminalButton> handler) {
        this.onActivate = handler;
    }
    
    @Override
    protected void setupEventHandlers() {
        addKeyDownHandler(event -> {
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
        if (onActivate != null) {
            onActivate.accept(this);
        }
    }
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (text == null) return;
        
        int width = getWidth();
        if (width <= 2) return;
        
        boolean focused = hasFocus();
        String prefix = focused ? "[ " : "  ";
        String suffix = focused ? " ]" : "  ";
        String displayText = text.length() > width - 4 ? text.substring(0, width - 4) : text;
        String fullText = prefix + displayText + suffix;
        TextStyle style = focused ? TextStyle.INVERSE : TextStyle.NORMAL;
        
        printAt(batch, 0, 0, fullText, style);
    }
    
}