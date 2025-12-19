package io.netnotes.app.console;

import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle.TextStyle;

public class Cell {
    private char character = '\0';
    private TextStyle style = new TextStyle();
    
    public void set(char ch, TextStyle style) {
        this.character = ch;
        this.style = style;
    }
    
    public void clear() {
        this.character = '\0';
        this.style = new TextStyle();
    }

    public void copyFrom(Cell other) {
        this.character = other.character;
        this.style = other.style.copy();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Cell)) return false;
        Cell other = (Cell) obj;
        return character == other.character && 
            style.equals(other.style);
    }
}