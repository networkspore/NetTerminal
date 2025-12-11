package io.netnotes.app.console;

import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * ConsoleUIRenderer - Terminal/console implementation of UIRenderer
 * 
 * Uses JLine3 for cross-platform terminal control:
 * - ANSI escape sequences for rendering
 * - Terminal capabilities detection
 * - Raw mode for input capture
 * 
 * Supports all operations from TerminalContainerHandle:
 * - Positioned text rendering (printAt)
 * - Cursor control
 * - Text styling (colors, bold, etc.)
 * - Box drawing
 * - Screen clearing
 * 
 * Architecture:
 * - Each container gets a virtual "screen buffer"
 * - Focused container is rendered to actual terminal
 * - Batch operations buffer then render at once
 */
public class ConsoleUIRenderer implements UIRenderer {
    
    private final Terminal terminal;
    private final Map<String, ContainerBuffer> containers = new ConcurrentHashMap<>();
    private volatile String focusedContainerId = null;
    private volatile boolean active = false;
    private volatile boolean batchMode = false;
    
    // Terminal dimensions
    private volatile int termWidth;
    private volatile int termHeight;
    
    public ConsoleUIRenderer() throws IOException {
        this.terminal = TerminalBuilder.builder()
            .system(true)
            .encoding("UTF-8")
            .build();
        
        // Get terminal size
        this.termWidth = terminal.getWidth();
        this.termHeight = terminal.getHeight();
        
        // Enable raw mode (character-at-a-time input)
        terminal.enterRawMode();
        
        System.out.println("[ConsoleUIRenderer] Terminal initialized: " + 
            termWidth + "x" + termHeight);
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        active = true;
        
        // Clear screen on startup
        clearScreen();
        
        // Hide cursor initially
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.flush();
        
        System.out.println("[ConsoleUIRenderer] Initialized");
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<NoteBytesMap> render(NoteBytesMap command) {
        if (!active) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Renderer not active")
            );
        }
        
        try {
            String cmd = command.get("cmd").getAsString();
            
            switch (cmd) {
                case "create_container" -> handleCreateContainer(command);
                case "destroy_container" -> handleDestroyContainer(command);
                case "show_container" -> handleShowContainer(command);
                case "hide_container" -> handleHideContainer(command);
                case "focus_container" -> handleFocusContainer(command);
                case "maximize_container" -> handleMaximizeContainer(command);
                case "restore_container" -> handleRestoreContainer(command);
                case "update_container" -> handleUpdateContainer(command);
                default -> System.err.println("[ConsoleUIRenderer] Unknown command: " + cmd);
            }
            
            return CompletableFuture.completedFuture(new NoteBytesMap());
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    // ===== CONTAINER LIFECYCLE =====
    
    private void handleCreateContainer(NoteBytesMap command) {
        String containerId = command.get("container_id").getAsString();
        String title = command.get("title").getAsString();
        
        ContainerBuffer buffer = new ContainerBuffer(
            containerId, 
            title, 
            termWidth, 
            termHeight
        );
        
        containers.put(containerId, buffer);
        
        System.out.println("[ConsoleUIRenderer] Container created: " + containerId);
    }
    
    private void handleDestroyContainer(NoteBytesMap command) {
        String containerId = command.get("container_id").getAsString();
        
        containers.remove(containerId);
        
        if (containerId.equals(focusedContainerId)) {
            focusedContainerId = null;
            clearScreen();
        }
        
        System.out.println("[ConsoleUIRenderer] Container destroyed: " + containerId);
    }
    
    private void handleShowContainer(NoteBytesMap command) {
        String containerId = command.get("container_id").getAsString();
        ContainerBuffer buffer = containers.get(containerId);
        
        if (buffer != null) {
            buffer.visible = true;
        }
    }
    
    private void handleHideContainer(NoteBytesMap command) {
        String containerId = command.get("container_id").getAsString();
        ContainerBuffer buffer = containers.get(containerId);
        
        if (buffer != null) {
            buffer.visible = false;
            
            if (containerId.equals(focusedContainerId)) {
                clearScreen();
            }
        }
    }
    
    private void handleFocusContainer(NoteBytesMap command) {
        String containerId = command.get("container_id").getAsString();
        ContainerBuffer buffer = containers.get(containerId);
        
        if (buffer != null && buffer.visible) {
            focusedContainerId = containerId;
            renderContainer(buffer);
        }
    }
    
    private void handleMaximizeContainer(NoteBytesMap command) {
        // In console, maximize = full screen (already is)
        handleFocusContainer(command);
    }
    
    private void handleRestoreContainer(NoteBytesMap command) {
        // In console, restore = same as focus
        handleFocusContainer(command);
    }
    
    private void handleUpdateContainer(NoteBytesMap command) {
        String containerId = command.get("container_id").getAsString();
        NoteBytesMap updates = command.get("updates").getAsNoteBytesMap();
        
        ContainerBuffer buffer = containers.get(containerId);
        if (buffer == null) return;
        
        // Check for terminal commands
        if (updates.has("terminal_command")) {
            NoteBytesMap termCmd = updates.get("terminal_command").getAsNoteBytesMap();
            handleTerminalCommand(buffer, termCmd);
        }
    }
    
    // ===== TERMINAL COMMANDS =====
    
    private void handleTerminalCommand(ContainerBuffer buffer, NoteBytesMap command) {
        String cmd = command.get("cmd").getAsString();
        
        switch (cmd) {
            case "terminal_clear" -> buffer.clear();
            case "terminal_print" -> handlePrint(buffer, command, false);
            case "terminal_println" -> handlePrint(buffer, command, true);
            case "terminal_print_at" -> handlePrintAt(buffer, command);
            case "terminal_move_cursor" -> handleMoveCursor(buffer, command);
            case "terminal_show_cursor" -> buffer.cursorVisible = true;
            case "terminal_hide_cursor" -> buffer.cursorVisible = false;
            case "terminal_clear_line" -> handleClearLine(buffer, command);
            case "terminal_clear_line_at" -> handleClearLineAt(buffer, command);
            case "terminal_clear_region" -> handleClearRegion(buffer, command);
            case "terminal_draw_box" -> handleDrawBox(buffer, command);
            case "terminal_draw_hline" -> handleDrawHLine(buffer, command);
            case "terminal_begin_batch" -> batchMode = true;
            case "terminal_end_batch" -> {
                batchMode = false;
                renderContainer(buffer);
            }
            default -> System.err.println("[ConsoleUIRenderer] Unknown terminal command: " + cmd);
        }
        
        // Auto-render if not in batch mode
        if (!batchMode && buffer.id.equals(focusedContainerId)) {
            renderContainer(buffer);
        }
    }
    
    private void handlePrint(ContainerBuffer buffer, NoteBytesMap command, boolean newline) {
        String text = command.get("text").getAsString();
        TextStyle style = parseStyle(command.get("style"));
        
        buffer.print(text, style, newline);
    }
    
    private void handlePrintAt(ContainerBuffer buffer, NoteBytesMap command) {
        int row = command.get("row").getAsInt();
        int col = command.get("col").getAsInt();
        String text = command.get("text").getAsString();
        TextStyle style = parseStyle(command.get("style"));
        
        buffer.printAt(row, col, text, style);
    }
    
    private void handleMoveCursor(ContainerBuffer buffer, NoteBytesMap command) {
        int row = command.get("row").getAsInt();
        int col = command.get("col").getAsInt();
        
        buffer.cursorRow = row;
        buffer.cursorCol = col;
    }
    
    private void handleClearLine(ContainerBuffer buffer, NoteBytesMap command) {
        buffer.clearLine(buffer.cursorRow);
    }
    
    private void handleClearLineAt(ContainerBuffer buffer, NoteBytesMap command) {
        int row = command.get("row").getAsInt();
        buffer.clearLine(row);
    }
    
    private void handleClearRegion(ContainerBuffer buffer, NoteBytesMap command) {
        int startRow = command.get("start_row").getAsInt();
        int startCol = command.get("start_col").getAsInt();
        int endRow = command.get("end_row").getAsInt();
        int endCol = command.get("end_col").getAsInt();
        
        buffer.clearRegion(startRow, startCol, endRow, endCol);
    }
    
    private void handleDrawBox(ContainerBuffer buffer, NoteBytesMap command) {
        int startRow = command.get("start_row").getAsInt();
        int startCol = command.get("start_col").getAsInt();
        int width = command.get("width").getAsInt();
        int height = command.get("height").getAsInt();
        String title = command.get("title").getAsString();
        String boxStyleStr = command.get("box_style").getAsString();
        
        BoxStyle boxStyle = BoxStyle.valueOf(boxStyleStr);
        buffer.drawBox(startRow, startCol, width, height, title, boxStyle);
    }
    
    private void handleDrawHLine(ContainerBuffer buffer, NoteBytesMap command) {
        int row = command.get("row").getAsInt();
        int startCol = command.get("start_col").getAsInt();
        int length = command.get("length").getAsInt();
        
        buffer.drawHLine(row, startCol, length);
    }
    
    // ===== RENDERING =====
    
    private void renderContainer(ContainerBuffer buffer) {
        if (!buffer.id.equals(focusedContainerId)) return;
        
        // Clear screen
        clearScreen();
        
        // Render buffer contents
        for (int row = 0; row < buffer.rows; row++) {
            for (int col = 0; col < buffer.cols; col++) {
                Cell cell = buffer.getCell(row, col);
                if (cell.character != '\0') {
                    moveCursor(row, col);
                    applyStyle(cell.style);
                    terminal.writer().print(cell.character);
                    resetStyle();
                }
            }
        }
        
        // Position cursor
        if (buffer.cursorVisible) {
            moveCursor(buffer.cursorRow, buffer.cursorCol);
            terminal.puts(InfoCmp.Capability.cursor_visible);
        } else {
            terminal.puts(InfoCmp.Capability.cursor_invisible);
        }
        
        terminal.flush();
    }
    
    // ===== TERMINAL PRIMITIVES =====
    
    private void clearScreen() {
        terminal.puts(InfoCmp.Capability.clear_screen);
        terminal.flush();
    }
    
    private void moveCursor(int row, int col) {
        terminal.puts(InfoCmp.Capability.cursor_address, row, col);
    }
    
    private void applyStyle(TextStyle style) {
        if (style.bold) {
            terminal.writer().print("\033[1m");
        }
        if (style.underline) {
            terminal.writer().print("\033[4m");
        }
        if (style.inverse) {
            terminal.writer().print("\033[7m");
        }
        
        // Foreground color
        if (style.foreground != Color.DEFAULT) {
            terminal.writer().print("\033[" + getColorCode(style.foreground, false) + "m");
        }
        
        // Background color
        if (style.background != Color.DEFAULT) {
            terminal.writer().print("\033[" + getColorCode(style.background, true) + "m");
        }
    }
    
    private void resetStyle() {
        terminal.writer().print("\033[0m");
    }
    
    private int getColorCode(Color color, boolean background) {
        int base = background ? 40 : 30;
        int brightBase = background ? 100 : 90;
        
        return switch (color) {
            case BLACK -> base + 0;
            case RED -> base + 1;
            case GREEN -> base + 2;
            case YELLOW -> base + 3;
            case BLUE -> base + 4;
            case MAGENTA -> base + 5;
            case CYAN -> base + 6;
            case WHITE -> base + 7;
            case BRIGHT_BLACK -> brightBase + 0;
            case BRIGHT_RED -> brightBase + 1;
            case BRIGHT_GREEN -> brightBase + 2;
            case BRIGHT_YELLOW -> brightBase + 3;
            case BRIGHT_BLUE -> brightBase + 4;
            case BRIGHT_MAGENTA -> brightBase + 5;
            case BRIGHT_CYAN -> brightBase + 6;
            case BRIGHT_WHITE -> brightBase + 7;
            default -> base + 7; // Default to white/black
        };
    }
    
    private TextStyle parseStyle(Object styleObj) {
        if (styleObj == null) return new TextStyle();
        
        NoteBytesMap styleMap = (NoteBytesMap) styleObj;
        TextStyle style = new TextStyle();
        
        if (styleMap.has("fg")) {
            style.foreground = Color.valueOf(styleMap.get("fg").getAsString());
        }
        if (styleMap.has("bg")) {
            style.background = Color.valueOf(styleMap.get("bg").getAsString());
        }
        if (styleMap.has("bold")) {
            style.bold = styleMap.get("bold").getAsBoolean();
        }
        if (styleMap.has("inverse")) {
            style.inverse = styleMap.get("inverse").getAsBoolean();
        }
        if (styleMap.has("underline")) {
            style.underline = styleMap.get("underline").getAsBoolean();
        }
        
        return style;
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public boolean isActive() {
        return active;
    }
    
    @Override
    public void shutdown() {
        active = false;
        
        try {
            // Restore terminal
            terminal.puts(InfoCmp.Capability.cursor_visible);
            clearScreen();
            moveCursor(0, 0);
            terminal.flush();
            terminal.close();
        } catch (IOException e) {
            System.err.println("[ConsoleUIRenderer] Error closing terminal: " + e.getMessage());
        }
    }
    
    public Terminal getTerminal() {
        return terminal;
    }
    
    // ===== CONTAINER BUFFER =====
    
    /**
     * Virtual screen buffer for a container
     */
    private static class ContainerBuffer {
        final String id;
        final String title;
        final int rows;
        final int cols;
        final Cell[][] cells;
        
        int cursorRow = 0;
        int cursorCol = 0;
        boolean cursorVisible = true;
        boolean visible = true;
        
        ContainerBuffer(String id, String title, int cols, int rows) {
            this.id = id;
            this.title = title;
            this.rows = rows;
            this.cols = cols;
            this.cells = new Cell[rows][cols];
            
            // Initialize cells
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    cells[r][c] = new Cell();
                }
            }
        }

        
        void clear() {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    cells[r][c].clear();
                }
            }
            cursorRow = 0;
            cursorCol = 0;
        }
        
        void print(String text, TextStyle style, boolean newline) {
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
        
        void printAt(int row, int col, String text, TextStyle style) {
            if (row < 0 || row >= rows) return;
            
            int c = col;
            for (char ch : text.toCharArray()) {
                if (c >= cols) break;
                cells[row][c].set(ch, style);
                c++;
            }
        }
        
        void clearLine(int row) {
            if (row < 0 || row >= rows) return;
            
            for (int c = 0; c < cols; c++) {
                cells[row][c].clear();
            }
        }
        
        void clearRegion(int startRow, int startCol, int endRow, int endCol) {
            for (int r = Math.max(0, startRow); r <= Math.min(rows - 1, endRow); r++) {
                for (int c = Math.max(0, startCol); c <= Math.min(cols - 1, endCol); c++) {
                    cells[r][c].clear();
                }
            }
        }
        
        void drawBox(int startRow, int startCol, int width, int height, String title, BoxStyle style) {
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
        
        void drawHLine(int row, int startCol, int length) {
            for (int i = 0; i < length; i++) {
                printAt(row, startCol + i, "─", new TextStyle());
            }
        }
        
        Cell getCell(int row, int col) {
            if (row < 0 || row >= rows || col < 0 || col >= cols) {
                return new Cell();
            }
            return cells[row][col];
        }
    }
    
    private static class Cell {
        char character = '\0';
        TextStyle style = new TextStyle();
        
        void set(char ch, TextStyle style) {
            this.character = ch;
            this.style = style;
        }
        
        void clear() {
            this.character = '\0';
            this.style = new TextStyle();
        }
    }
    
    // ===== STYLE CLASSES =====
    
    private static class TextStyle {
        Color foreground = Color.DEFAULT;
        Color background = Color.DEFAULT;
        boolean bold = false;
        boolean inverse = false;
        boolean underline = false;
    }
    
    private enum Color {
        DEFAULT,
        BLACK, RED, GREEN, YELLOW,
        BLUE, MAGENTA, CYAN, WHITE,
        BRIGHT_BLACK, BRIGHT_RED, BRIGHT_GREEN, BRIGHT_YELLOW,
        BRIGHT_BLUE, BRIGHT_MAGENTA, BRIGHT_CYAN, BRIGHT_WHITE
    }
    
    private enum BoxStyle {
        SINGLE(new char[]{'─', '│', '┌', '┐', '└', '┘'}),
        DOUBLE(new char[]{'═', '║', '╔', '╗', '╚', '╝'}),
        ROUNDED(new char[]{'─', '│', '╭', '╮', '╰', '╯'}),
        THICK(new char[]{'━', '┃', '┏', '┓', '┗', '┛'});
        
        private final char[] chars;
        
        BoxStyle(char[] chars) {
            this.chars = chars;
        }
        
        char[] getChars() {
            return chars;
        }
    }
}