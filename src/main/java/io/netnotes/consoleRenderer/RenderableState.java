package io.netnotes.consoleRenderer;

import io.netnotes.terminal.TerminalRectangle;

public record RenderableState(
    int rows, int cols, int offsetX, int offsetY,
    int cursorRow, int cursorCol,
    boolean cursorVisible,
    TerminalRectangle[] damageRects,
    Cell[][] cells,
    Cell[][] prevCells
) {
    public boolean hasBoundsChanged() {
        return prevCells == null; 
    }
}