package io.netnotes.app.console;

import io.netnotes.engine.core.system.control.containers.*;
import io.netnotes.engine.core.system.control.terminal.TerminalCommands;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import org.jline.terminal.Terminal;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * ConsoleContainer - Terminal-based container implementation
 * 
 * Manages:
 * - Character cell buffer (for differential rendering)
 * - Cursor state
 * - Terminal-specific rendering logic
 * - Drawing operations (boxes, lines, text)
 */
public class ConsoleContainer extends Container {
    
    // ===== TERMINAL STATE =====
    private final Terminal terminal;
    private int rows;
    private int cols;
    
    // ===== CELL BUFFERS =====
    private Cell[][] cells;
    private Cell[][] prevCells;
    
    // ===== CURSOR STATE =====
    private int cursorRow = 0;
    private int cursorCol = 0;
    private boolean cursorVisible = true;
    
    /**
     * Constructor
     */
    public ConsoleContainer(
        ContainerId id,
        String title,
        ContainerType type,
        ContextPath ownerPath,
        ContainerConfig config,
        String rendererId,
        Terminal terminal,
        int cols,
        int rows
    ) {
        super(id, title, type, ownerPath, config, rendererId);
        this.terminal = terminal;
        this.rows = rows;
        this.cols = cols;
        
        // Allocate buffers
        this.cells = new Cell[rows][cols];
        this.prevCells = new Cell[rows][cols];
        
        // Initialize cells
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells[r][c] = new Cell();
                prevCells[r][c] = new Cell();
            }
        }
    }
    
    // ===== MESSAGE MAP SETUP =====
    
    @Override
    protected void setupMessageMap() {
        // Terminal commands
        msgMap.put(TerminalCommands.TERMINAL_CLEAR, this::handleClear);
        msgMap.put(TerminalCommands.TERMINAL_PRINT, (msg, pkt) -> handlePrint(msg, false));
        msgMap.put(TerminalCommands.TERMINAL_PRINTLN, (msg, pkt) -> handlePrint(msg, true));
        msgMap.put(TerminalCommands.TERMINAL_PRINT_AT, this::handlePrintAt);
        msgMap.put(TerminalCommands.TERMINAL_MOVE_CURSOR, this::handleMoveCursor);
        msgMap.put(TerminalCommands.TERMINAL_SHOW_CURSOR, this::handleShowCursor);
        msgMap.put(TerminalCommands.TERMINAL_HIDE_CURSOR, this::handleHideCursor);
        msgMap.put(TerminalCommands.TERMINAL_CLEAR_LINE, this::handleClearLine);
        msgMap.put(TerminalCommands.TERMINAL_CLEAR_LINE_AT, this::handleClearLineAt);
        msgMap.put(TerminalCommands.TERMINAL_CLEAR_REGION, this::handleClearRegion);
        msgMap.put(TerminalCommands.TERMINAL_DRAW_BOX, this::handleDrawBox);
        msgMap.put(TerminalCommands.TERMINAL_DRAW_HLINE, this::handleDrawHLine);
        
        // Container commands
        msgMap.put(ContainerCommands.UPDATE_CONTAINER, this::handleUpdateContainer);
    }
    
    // ===== LIFECYCLE IMPLEMENTATION =====
    
    @Override
    protected CompletableFuture<Void> initializeRenderer() {
        Log.logMsg("[ConsoleContainer] Renderer initialized: " + id);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    protected CompletableFuture<Void> destroyRenderer() {
        Log.logMsg("[ConsoleContainer] Renderer destroyed: " + id);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    protected CompletableFuture<Void> showRenderer() {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    protected CompletableFuture<Void> hideRenderer() {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    protected CompletableFuture<Void> focusRenderer() {
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== TERMINAL COMMAND HANDLERS =====
    
    private CompletableFuture<Void> handleClear(NoteBytesMap command, Object packet) {
        clear();
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handlePrint(NoteBytesMap command, boolean newline) {
        NoteBytes textBytes = command.get(Keys.TEXT);
        NoteBytes styleBytes = command.get(Keys.STYLE);
        
        if (textBytes != null) {
            String text = textBytes.getAsString();
            TextStyle style = parseStyle(styleBytes);
            print(text, style, newline);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handlePrintAt(NoteBytesMap command, Object packet) {
        NoteBytes rowBytes = command.get(Keys.ROW);
        NoteBytes colBytes = command.get(Keys.COL);
        NoteBytes textBytes = command.get(Keys.TEXT);
        NoteBytes styleBytes = command.get(Keys.STYLE);
        
        if (rowBytes != null && colBytes != null && textBytes != null) {
            int row = rowBytes.getAsInt();
            int col = colBytes.getAsInt();
            String text = textBytes.getAsString();
            TextStyle style = parseStyle(styleBytes);
            
            printAt(row, col, text, style);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleMoveCursor(NoteBytesMap command, Object packet) {
        NoteBytes rowBytes = command.get(Keys.ROW);
        NoteBytes colBytes = command.get(Keys.COL);
        
        if (rowBytes != null && colBytes != null) {
            cursorRow = rowBytes.getAsInt();
            cursorCol = colBytes.getAsInt();
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleShowCursor(NoteBytesMap command, Object packet) {
        cursorVisible = true;
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleHideCursor(NoteBytesMap command, Object packet) {
        cursorVisible = false;
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleClearLine(NoteBytesMap command, Object packet) {
        clearLine(cursorRow);
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleClearLineAt(NoteBytesMap command, Object packet) {
        NoteBytes rowBytes = command.get(Keys.ROW);
        if (rowBytes != null) {
            clearLine(rowBytes.getAsInt());
        }
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleClearRegion(NoteBytesMap command, Object packet) {
        NoteBytes startRowBytes = command.get(TerminalCommands.START_ROW);
        NoteBytes startColBytes = command.get(TerminalCommands.START_COL);
        NoteBytes endRowBytes = command.get(TerminalCommands.END_ROW);
        NoteBytes endColBytes = command.get(TerminalCommands.END_COL);
        
        if (startRowBytes != null && startColBytes != null && 
            endRowBytes != null && endColBytes != null) {
            clearRegion(
                startRowBytes.getAsInt(),
                startColBytes.getAsInt(),
                endRowBytes.getAsInt(),
                endColBytes.getAsInt()
            );
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleDrawBox(NoteBytesMap command, Object packet) {
        NoteBytes startRowBytes = command.get(TerminalCommands.START_ROW);
        NoteBytes startColBytes = command.get(TerminalCommands.START_COL);
        NoteBytes widthBytes = command.get(Keys.WIDTH);
        NoteBytes heightBytes = command.get(Keys.HEIGHT);
        NoteBytes titleBytes = command.get(Keys.TITLE);
        NoteBytes boxStyleBytes = command.get(TerminalCommands.BOX_STYLE);
        
        if (startRowBytes != null && startColBytes != null && 
            widthBytes != null && heightBytes != null && boxStyleBytes != null) {
            
            int startRow = startRowBytes.getAsInt();
            int startCol = startColBytes.getAsInt();
            int width = widthBytes.getAsInt();
            int height = heightBytes.getAsInt();
            String titleStr = titleBytes != null ? titleBytes.getAsString() : "";
            BoxStyle boxStyle = BoxStyle.valueOf(boxStyleBytes.getAsString());
            
            drawBox(startRow, startCol, width, height, titleStr, boxStyle);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleDrawHLine(NoteBytesMap command, Object packet) {
        NoteBytes rowBytes = command.get(Keys.ROW);
        NoteBytes startColBytes = command.get(TerminalCommands.START_COL);
        NoteBytes lengthBytes = command.get(Keys.LENGTH);
        
        if (rowBytes != null && startColBytes != null && lengthBytes != null) {
            drawHLine(
                rowBytes.getAsInt(),
                startColBytes.getAsInt(),
                lengthBytes.getAsInt()
            );
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== TERMINAL OPERATIONS =====
    
    public void clear() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells[r][c].clear();
                prevCells[r][c].character = (char) 0xFFFF; // Force redraw
            }
        }
        cursorRow = 0;
        cursorCol = 0;
    }
    
    public void print(String text, TextStyle style, boolean newline) {
        for (char ch : text.toCharArray()) {
            if (cursorRow >= rows) break;
            
            if (ch == '\n' || cursorCol >= cols) {
                cursorRow++;
                cursorCol = 0;
                if (ch == '\n') continue;
            }
            
            cells[cursorRow][cursorCol].set(ch, style);
            cursorCol++;
        }
        
        if (newline) {
            cursorRow++;
            cursorCol = 0;
        }
    }
    
    public void printAt(int row, int col, String text, TextStyle style) {
        if (row < 0 || row >= rows) return;
        
        int c = col;
        for (char ch : text.toCharArray()) {
            if (c >= cols) break;
            cells[row][c].set(ch, style);
            c++;
        }
    }
    
    public void clearLine(int row) {
        if (row < 0 || row >= rows) return;
        
        for (int c = 0; c < cols; c++) {
            cells[row][c].clear();
        }
    }
    
    public void clearRegion(int startRow, int startCol, int endRow, int endCol) {
        for (int r = Math.max(0, startRow); r <= Math.min(rows - 1, endRow); r++) {
            for (int c = Math.max(0, startCol); c <= Math.min(cols - 1, endCol); c++) {
                cells[r][c].clear();
            }
        }
    }
    
    public void drawBox(int startRow, int startCol, int width, int height, 
                        String title, BoxStyle style) {
        char[] chars = style.getChars();
        
        // Top border
        printAt(startRow, startCol, String.valueOf(chars[2]), new TextStyle());
        for (int i = 1; i < width - 1; i++) {
            printAt(startRow, startCol + i, String.valueOf(chars[0]), new TextStyle());
        }
        printAt(startRow, startCol + width - 1, String.valueOf(chars[3]), new TextStyle());
        
        // Title
        if (title != null && !title.isEmpty()) {
            int titlePos = startCol + (width - title.length()) / 2;
            printAt(startRow, titlePos, " " + title + " ", new TextStyle());
        }
        
        // Sides
        for (int r = 1; r < height - 1; r++) {
            printAt(startRow + r, startCol, String.valueOf(chars[1]), new TextStyle());
            printAt(startRow + r, startCol + width - 1, String.valueOf(chars[1]), new TextStyle());
        }
        
        // Bottom border
        printAt(startRow + height - 1, startCol, String.valueOf(chars[4]), new TextStyle());
        for (int i = 1; i < width - 1; i++) {
            printAt(startRow + height - 1, startCol + i, String.valueOf(chars[0]), new TextStyle());
        }
        printAt(startRow + height - 1, startCol + width - 1, String.valueOf(chars[5]), new TextStyle());
    }
    
    public void drawHLine(int row, int startCol, int length) {
        for (int i = 0; i < length; i++) {
            printAt(row, startCol + i, "─", new TextStyle());
        }
    }
    
    // ===== RESIZE HANDLING =====
    
    public void resize(int newCols, int newRows) {
        if (this.rows == newRows && this.cols == newCols) {
            return;
        }
        
        // Create new buffers
        Cell[][] newCells = new Cell[newRows][newCols];
        Cell[][] newPrevCells = new Cell[newRows][newCols];
        
        // Initialize
        for (int r = 0; r < newRows; r++) {
            for (int c = 0; c < newCols; c++) {
                newCells[r][c] = new Cell();
                newPrevCells[r][c] = new Cell();
            }
        }
        
        // Copy existing content
        int copyRows = Math.min(this.rows, newRows);
        int copyCols = Math.min(this.cols, newCols);
        
        for (int r = 0; r < copyRows; r++) {
            for (int c = 0; c < copyCols; c++) {
                newCells[r][c].copyFrom(cells[r][c]);
                newPrevCells[r][c].copyFrom(prevCells[r][c]);
            }
        }
        
        // Update
        this.rows = newRows;
        this.cols = newCols;
        this.cells = newCells;
        this.prevCells = newPrevCells;
        
        // Clamp cursor
        this.cursorRow = Math.min(this.cursorRow, newRows - 1);
        this.cursorCol = Math.min(this.cursorCol, newCols - 1);
        
        // Emit resize event
        NoteBytesMap resizeEvent = ContainerCommands.containerResized(id, newCols, newRows);
        emitEvent(resizeEvent);
    }
    
    // ===== RENDERING ACCESS =====
    
    public Cell getCell(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            return new Cell();
        }
        return cells[row][col];
    }
    
    public Cell getPrevCell(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            return new Cell();
        }
        return prevCells[row][col];
    }
    
    public void swapBuffers() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                prevCells[r][c].copyFrom(cells[r][c]);
            }
        }
    }
    
    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public int getCursorRow() { return cursorRow; }
    public int getCursorCol() { return cursorCol; }
    public boolean isCursorVisible() { return cursorVisible; }
    
    // ===== HELPER CLASSES =====
    
    public static class Cell {
        public char character = '\0';
        public TextStyle style = new TextStyle();
        
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
            return character == other.character && style.equals(other.style);
        }
    }
    
    public static class TextStyle {
        public Color foreground = Color.DEFAULT;
        public Color background = Color.DEFAULT;
        public boolean bold = false;
        public boolean inverse = false;
        public boolean underline = false;
        
        public TextStyle copy() {
            TextStyle copy = new TextStyle();
            copy.foreground = this.foreground;
            copy.background = this.background;
            copy.bold = this.bold;
            copy.inverse = this.inverse;
            copy.underline = this.underline;
            return copy;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof TextStyle)) return false;
            TextStyle other = (TextStyle) obj;
            return foreground == other.foreground &&
                   background == other.background &&
                   bold == other.bold &&
                   inverse == other.inverse &&
                   underline == other.underline;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(foreground, background, bold, inverse, underline);
        }
    }
    
    public enum Color {
        DEFAULT,
        BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE,
        BRIGHT_BLACK, BRIGHT_RED, BRIGHT_GREEN, BRIGHT_YELLOW,
        BRIGHT_BLUE, BRIGHT_MAGENTA, BRIGHT_CYAN, BRIGHT_WHITE
    }
    
    public enum BoxStyle {
        SINGLE(new char[]{'─', '│', '┌', '┐', '└', '┘'}),
        DOUBLE(new char[]{'═', '║', '╔', '╗', '╚', '╝'}),
        ROUNDED(new char[]{'─', '│', '╭', '╮', '╰', '╯'}),
        THICK(new char[]{'━', '┃', '┏', '┓', '┗', '┛'});
        
        private final char[] chars;
        
        BoxStyle(char[] chars) {
            this.chars = chars;
        }
        
        public char[] getChars() {
            return chars;
        }
    }
    
    // ===== STYLE PARSING =====
    
    private TextStyle parseStyle(NoteBytes styleBytes) {
        if (styleBytes == null) return new TextStyle();
        
        NoteBytesMap styleMap = styleBytes.getAsNoteBytesMap();
        TextStyle style = new TextStyle();
        
        NoteBytes fg = styleMap.get(Keys.FOREGROUND);
        if (fg != null) style.foreground = Color.valueOf(fg.getAsString());
        
        NoteBytes bg = styleMap.get(Keys.BACKGROUND);
        if (bg != null) style.background = Color.valueOf(bg.getAsString());
        
        NoteBytes bold = styleMap.get(Keys.BOLD);
        if (bold != null) style.bold = bold.getAsBoolean();
        
        NoteBytes inverse = styleMap.get(Keys.INVERSE);
        if (inverse != null) style.inverse = inverse.getAsBoolean();
        
        NoteBytes underline = styleMap.get(Keys.UNDERLINE);
        if (underline != null) style.underline = underline.getAsBoolean();
        
        return style;
    }
}