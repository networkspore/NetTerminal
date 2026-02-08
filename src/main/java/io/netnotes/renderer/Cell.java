package io.netnotes.renderer;

import io.netnotes.terminal.TextStyle;

public class Cell {
    char character = '\0';
    TextStyle style = new TextStyle();
    
    public void set(char ch, TextStyle style) {
        this.character = ch;
        this.style = style != null ? style.copy() : new TextStyle();
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