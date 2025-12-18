package io.netnotes.app.console;

import io.netnotes.engine.core.system.control.containers.ContainerCommands;
import io.netnotes.engine.core.system.control.containers.ContainerType;
import io.netnotes.engine.core.system.control.containers.TerminalCommands;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.MessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * ConsoleUIRenderer - Terminal/console implementation of UIRenderer
 * 
 * Uses JLine3 for cross-platform terminal control.
 * 
 * NOW FIXED:
 * - Returns CompletableFuture<Void> (matches interface)
 * - Handles terminal commands directly (no more double-nesting!)
 * - Clean command dispatch
 */
public class ConsoleUIRenderer implements UIRenderer {
    private final String description = "JLine3 terminal renderer, utlizing UTF-8 encoding";

    private final Terminal terminal;
    private final Attributes originalAttributes;

    private final Map<NoteBytes, ContainerBuffer> containers = new ConcurrentHashMap<>();
    private volatile NoteBytes focusedContainerId = null;
    private volatile boolean active = false;
    private volatile boolean batchMode = false;
    
    // Terminal dimensions
    private volatile int termWidth;
    private volatile int termHeight;
    private Map<NoteBytes,MessageExecutor> m_msgExecMap = new HashMap<>();
    private Set<ContainerType> supportedTypes = Set.of(ContainerType.TERMINAL);
    
    
    public ConsoleUIRenderer() throws IOException {
        this.terminal = TerminalBuilder.builder()
            .system(true)
            .encoding("UTF-8")
            .build();
        
        // Save original attributes BEFORE any modifications
        this.originalAttributes = terminal.getAttributes();
        
        this.termWidth = terminal.getWidth();
        this.termHeight = terminal.getHeight();
        
        setupExecMap();
        
        Log.logMsg("[ConsoleUIRenderer] Terminal created: " + termWidth + "x" + termHeight);
        Log.logMsg("[ConsoleUIRenderer] Original attributes: " + originalAttributes);
    }

   
    @Override
    public CompletableFuture<Void> initialize() {
        // Make idempotent - safe to call multiple times
        if (active) {
            Log.logMsg("[ConsoleUIRenderer] Already initialized, skipping");
            return CompletableFuture.completedFuture(null);
        }
        
        active = true;
        
        // CRITICAL: Manually force raw mode attributes
        Log.logMsg("[ConsoleUIRenderer] Setting raw mode...");
        
        Attributes raw = new Attributes(originalAttributes);
        
        // Disable canonical mode (line buffering)
        raw.setLocalFlag(Attributes.LocalFlag.ICANON, false);
        
        // Disable echo
        raw.setLocalFlag(Attributes.LocalFlag.ECHO, false);
        
        // Disable signal generation
        raw.setLocalFlag(Attributes.LocalFlag.ISIG, false);
        
        // Disable extended input processing
        raw.setLocalFlag(Attributes.LocalFlag.IEXTEN, false);
        
        // Set minimum characters to read (0 = non-blocking)
        raw.setControlChar(Attributes.ControlChar.VMIN, 0);
        
        // Set timeout in deciseconds (1 = 100ms)
        raw.setControlChar(Attributes.ControlChar.VTIME, 1);
        
        // Apply the attributes
        terminal.setAttributes(raw);
        
        // Verify it worked
        Attributes current = terminal.getAttributes();
        Log.logMsg("[ConsoleUIRenderer] After setting raw mode: " + current);
        Log.logMsg("[ConsoleUIRenderer] ICANON disabled: " + 
            !current.getLocalFlag(Attributes.LocalFlag.ICANON));
        Log.logMsg("[ConsoleUIRenderer] ECHO disabled: " + 
            !current.getLocalFlag(Attributes.LocalFlag.ECHO));

        System.out.flush();
        System.err.flush();
        // Clear screen on startup
        clearScreen();
        
        // Hide cursor initially
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.flush();
        
        Log.logMsg("[ConsoleUIRenderer] Initialized in raw mode");
        return CompletableFuture.completedFuture(null);
    }
    
    private void setupExecMap(){
        m_msgExecMap.put(ContainerCommands.CREATE_CONTAINER,    this::handleCreateContainer);
        m_msgExecMap.put(ContainerCommands.SHOW_CONTAINER,      this::handleShowContainer);
        m_msgExecMap.put(ContainerCommands.HIDE_CONTAINER,      this::handleHideContainer);
        m_msgExecMap.put(ContainerCommands.FOCUS_CONTAINER,     this::handleFocusContainer);
        m_msgExecMap.put(ContainerCommands.MAXIMIZE_CONTAINER,  this::handleMaximizeContainer);
        m_msgExecMap.put(ContainerCommands.RESTORE_CONTAINER,   this::handleRestoreContainer);
        m_msgExecMap.put(ContainerCommands.DESTROY_CONTAINER,   this::handleDestroyContainer);

        m_msgExecMap.put(TerminalCommands.TERMINAL_CLEAR,       this::handleClear);
        m_msgExecMap.put(TerminalCommands.TERMINAL_PRINT,       this::handlePrint);
        m_msgExecMap.put(TerminalCommands.TERMINAL_PRINTLN,     this::handlePrintLn);
        m_msgExecMap.put(TerminalCommands.TERMINAL_PRINT_AT,    this::handlePrintAt);
        m_msgExecMap.put(TerminalCommands.TERMINAL_MOVE_CURSOR, this::handleMoveCursor);
        m_msgExecMap.put(TerminalCommands.TERMINAL_SHOW_CURSOR, this::handleShowCursor);
        m_msgExecMap.put(TerminalCommands.TERMINAL_HIDE_CURSOR, this::handleHideCursor);
        m_msgExecMap.put(TerminalCommands.TERMINAL_CLEAR_LINE,  this::handleClearLine);
        m_msgExecMap.put(TerminalCommands.TERMINAL_CLEAR_LINE_AT, this::handleClearLineAt);
        m_msgExecMap.put(TerminalCommands.TERMINAL_CLEAR_REGION,this::handleClearRegion);
        m_msgExecMap.put(TerminalCommands.TERMINAL_DRAW_BOX,    this::handleDrawBox);
        m_msgExecMap.put(TerminalCommands.TERMINAL_DRAW_HLINE,  this::handleDrawHLine);
        m_msgExecMap.put(TerminalCommands.TERMINAL_BEGIN_BATCH, this::handleBeginBatch);
        m_msgExecMap.put(TerminalCommands.TERMINAL_END_BATCH,   this::handleEndBatch);
    }

    private void handleBeginBatch(NoteBytesMap msg){
        batchMode = true;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Set<ContainerType> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public boolean supports(ContainerType type) {
        return supportedTypes.contains(type);
    }

    @Override
    public CompletableFuture<Void> render(NoteBytesMap command) {
        if (!active) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Renderer not active")
            );
        }

        //Log.logNoteBytes("[ConsoleUiRenderer.render]", command.toNoteBytes());
        
        try {
            NoteBytes cmd = command.get(Keys.CMD);
            
            MessageExecutor msgExec = m_msgExecMap.get(cmd);
      
            if(msgExec != null){
                msgExec.execute(command);
            }else{
                Log.logError("[ConsoleUIRenderer] Unknown command: " + cmd);
            }
        
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    // ===== CONTAINER LIFECYCLE =====
    
    private void handleCreateContainer(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        NoteBytes title = command.get(Keys.TITLE);

        ContainerBuffer buffer = new ContainerBuffer(
            containerId, 
            title, 
            termWidth, 
            termHeight
        );
        
        containers.put(containerId, buffer);
        
        focusedContainerId = containerId;
        
        renderContainer(buffer);

        Log.logMsg("[ConsoleUIRenderer] Container created: " + containerId);
    }
    
    private void handleDestroyContainer(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        
        containers.remove(containerId);
        
        if (containerId.equals(focusedContainerId)) {
            focusedContainerId = null;
            clearScreen();
        }
        
        Log.logMsg("[ConsoleUIRenderer] Container destroyed: " + containerId);
    }
    
    private void handleShowContainer(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        ContainerBuffer buffer = containers.get(containerId);
        
        if (buffer != null) {
            
            buffer.visible = true;
        }
    }
    
    private void handleHideContainer(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        ContainerBuffer buffer = containers.get(containerId);
        
        if (buffer != null) {
            buffer.visible = false;
            
            if (containerId.equals(focusedContainerId)) {
                clearScreen();
            }
        }
    }
    
    private void handleFocusContainer(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        ContainerBuffer buffer = containers.get(containerId);
        
        if (buffer != null && buffer.visible) {
            // log msg to use title
            Log.logMsg("[ConsoleUiRenderer] + focusedContainer: " + buffer.title);
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
    
    // ===== TERMINAL COMMANDS =====
    
    private void handleClear(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        ContainerBuffer buffer = containers.get(containerId);
        
        if (buffer != null) {
            buffer.clear();
            autoRender(buffer);
        }
    }
    private void handlePrintLn(NoteBytesMap command) {
        handlePrint(command,true);
    }
    private void handlePrint(NoteBytesMap command) {
        handlePrint(command,false);
    }
    
    private void handlePrint(NoteBytesMap command, boolean newline) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        NoteBytes textBytes = command.get(Keys.TEXT);
        NoteBytes styleBytes = command.get(Keys.STYLE);

        if(containerId != null && textBytes != null){
            String text = textBytes.getAsString();
            TextStyle style = parseStyle(styleBytes);
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                buffer.print(text, style, newline);
                autoRender(buffer);
            }
        }
    }
    
    private void handlePrintAt(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        
        NoteBytes rowBytes = command.get(Keys.ROW);
        NoteBytes colBytes = command.get(Keys.COL);
        NoteBytes textBytes = command.get(Keys.TEXT);
        NoteBytes styleBytes = command.get(Keys.STYLE);

        if(containerId != null && rowBytes != null && colBytes != null && textBytes != null){
            int row = rowBytes.getAsInt();
            int col = colBytes.getAsInt();
            String text = textBytes.getAsString();
            TextStyle style = parseStyle(styleBytes);
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                buffer.printAt(row, col, text, style);
                autoRender(buffer);
            }
        }
    }
    
    private void handleMoveCursor(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        
        NoteBytes rowBytes = command.get(Keys.ROW);
        NoteBytes colBytes = command.get(Keys.COL);

        if(containerId != null && rowBytes != null && colBytes != null){
            int row = rowBytes.getAsInt();
            int col = colBytes.getAsInt();
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                buffer.cursorRow = row;
                buffer.cursorCol = col;
            }
        }
    }
    
    private void handleShowCursor(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        if(containerId != null){
            ContainerBuffer buffer = containers.get(containerId);
            
            if (buffer != null) {
                buffer.cursorVisible = true;
                autoRender(buffer);
            }
        }
    }
    
    private void handleHideCursor(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        if(containerId != null){
            ContainerBuffer buffer = containers.get(containerId);
            
            if (buffer != null) {
                buffer.cursorVisible = false;
                autoRender(buffer);
            }
        }
    }
    
    private void handleClearLine(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        if(containerId != null){
            ContainerBuffer buffer = containers.get(containerId);
            
            if (buffer != null) {
                buffer.clearLine(buffer.cursorRow);
                autoRender(buffer);
            }
        }
    }
    
    private void handleClearLineAt(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        NoteBytes rowBytes = command.get(Keys.ROW);

        if(containerId != null && rowBytes != null){
            int row = rowBytes.getAsInt();
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                buffer.clearLine(row);
                autoRender(buffer);
            }
        }
    }
    
    private void handleClearRegion(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);

        NoteBytes startRowBytes = command.get(TerminalCommands.START_ROW);
        NoteBytes startColBytes = command.get(TerminalCommands.START_COL);
        NoteBytes endRowBytes = command.get(TerminalCommands.END_ROW);
        NoteBytes endColBytes =  command.get(TerminalCommands.END_COL);

        if(containerId != null && startRowBytes != null && startColBytes != null
            && endRowBytes != null && endColBytes != null
        ){
            int startRow = startRowBytes.getAsInt();
            int startCol = startColBytes.getAsInt();
            int endRow = endRowBytes.getAsInt();
            int endCol = endColBytes.getAsInt();
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                buffer.clearRegion(startRow, startCol, endRow, endCol);
                autoRender(buffer);
            }
        }
    }
    
    private void handleDrawBox(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        NoteBytes startRowBytes = command.get(TerminalCommands.START_ROW);
        NoteBytes startColBytes = command.get(TerminalCommands.START_COL);
        NoteBytes widthBytes = command.get(Keys.WIDTH);
        NoteBytes heightBytes = command.get(Keys.HEIGHT);
        NoteBytes titleBytes = command.get(Keys.TITLE);
        NoteBytes boxStyleBytes = command.get(TerminalCommands.BOX_STYLE);

        if(containerId != null && startRowBytes != null && startColBytes != null && widthBytes != null &&
            heightBytes != null && titleBytes != null && boxStyleBytes != null
        ){
            int startRow = startRowBytes.getAsInt();
            int startCol = startColBytes.getAsInt();
            int width = widthBytes.getAsInt();
            int height = heightBytes.getAsInt();
            String title = titleBytes.getAsString();
            String boxStyleStr = boxStyleBytes.getAsString();
            
            BoxStyle boxStyle = BoxStyle.valueOf(boxStyleStr);
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                buffer.drawBox(startRow, startCol, width, height, title, boxStyle);
                autoRender(buffer);
            }
        }
    }
    
    private void handleDrawHLine(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        
        NoteBytes rowBytes = command.get(Keys.ROW);
        NoteBytes startColBytes = command.get(TerminalCommands.START_COL);
        NoteBytes lengthBytes = command.get(Keys.LENGTH);
        if(containerId != null && rowBytes != null && lengthBytes != null){
            int row = rowBytes.getAsInt();
            int startCol = startColBytes.getAsInt();
            int length = lengthBytes.getAsInt();
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                buffer.drawHLine(row, startCol, length);
                autoRender(buffer);
            }
        }
    }
    
    private void handleEndBatch(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        if(containerId != null){
            ContainerBuffer buffer = containers.get(containerId);
            
            batchMode = false;
            
            if (buffer != null) {
                renderContainer(buffer);
            }
        }
    }
    
    // ===== RENDERING =====
    
    /**
     * Auto-render if not in batch mode and container is focused
     */
    private void autoRender(ContainerBuffer buffer) {
        if (!batchMode && buffer.id.equals(focusedContainerId)) {
            renderContainer(buffer);
        }
    }
    
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
        // Clear screen + clear scrollback buffer
        terminal.writer().print("\033[2J\033[3J\033[H");
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
            default -> base + 7;
        };
    }
    
    private TextStyle parseStyle(NoteBytes styleBytes) {
        if (styleBytes == null) return new TextStyle();
        
        NoteBytesMap styleMap = styleBytes.getAsNoteBytesMap();
       
        NoteBytes foreground = styleMap.get(Keys.FOREGROUND);
        NoteBytes background = styleMap.get(Keys.BACKGROUND);
        NoteBytes bold = styleMap.get(Keys.BOLD);
        NoteBytes inverse = styleMap.get(Keys.INVERSE);
        NoteBytes underline = styleMap.get(Keys.UNDERLINE);

        TextStyle style = new TextStyle();
        
        if (foreground != null) {
            style.foreground = Color.valueOf(foreground.getAsString());
        }
        if (background != null) {
            style.background = Color.valueOf(background.getAsString());
        }
        if (bold != null) {
            style.bold = bold.getAsBoolean();
        }
        if (inverse != null) {
            style.inverse =inverse.getAsBoolean();
        }
        if (underline != null) {
            style.underline = underline.getAsBoolean();
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
            // Restore cursor
            terminal.puts(InfoCmp.Capability.cursor_visible);

            // Clear screen and reset position
            clearScreen();
            moveCursor(0, 0);
            
            // Exit raw mode
            terminal.setAttributes(originalAttributes);
            
            terminal.flush();
            terminal.close();
            
            Log.logMsg("[ConsoleUIRenderer] Shutdown complete");
        } catch (Exception e) {
            // Even if logging fails, try to restore terminal
            Log.logError("[ConsoleUIRenderer] Error during shutdown: " + e.getMessage());
        }
    }
    
    public Terminal getTerminal() {
        return terminal;
    }
    
    // ===== CONTAINER BUFFER =====
    
    private static class ContainerBuffer {
        final NoteBytes id;
        final NoteBytes title;
        final int rows;
        final int cols;
        final Cell[][] cells;
        
        int cursorRow = 0;
        int cursorCol = 0;
        boolean cursorVisible = true;
        boolean visible = true;
        
        ContainerBuffer(NoteBytes id, NoteBytes title, int cols, int rows) {
            this.id = id;
            this.title = title;
            this.rows = rows;
            this.cols = cols;
            this.cells = new Cell[rows][cols];
            
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