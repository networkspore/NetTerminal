package io.netnotes.renderer;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.MessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.ui.containers.Container;
import io.netnotes.engine.ui.containers.ContainerCommands;
import io.netnotes.engine.ui.containers.ContainerConfig;
import io.netnotes.engine.ui.containers.ContainerId;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.terminal.Position;
import io.netnotes.terminal.StyleConstants;
import io.netnotes.terminal.TerminalCommands;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRectanglePool;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.BoxStyle;

import java.util.concurrent.CompletableFuture;

/**
 * ConsoleContainer - Pull-based terminal container
 * 
 * COORDINATE SYSTEM:
 * - Uses x,y coordinates (x = horizontal/column, y = vertical/row)
 * - x increases left to right, y increases top to bottom
 * - Origin (0,0) is top-left corner
 * 
 * SIMPLIFIED DESIGN:
 * 1. No concept of "active" vs "inactive" - renderer decides what to render
 * 2. Container just maintains state and provides renderable snapshots
 * 3. Renderer uses shouldRender() to check VISIBLE + !ERROR + !HIDDEN
 * 4. All rendering decisions in ConsoleRenderManager
 */
public class ConsoleContainer extends Container<ConsoleContainer> {
    private TerminalRectanglePool regionPool = TerminalRectanglePool.getInstance();
    // Cell buffers (indexed as [y][x] for natural row-major ordering)
    private Cell[][] cells;
    private Cell[][] prevCells;
    
    // Cursor state (using x,y coordinates)
    private int cursorX = 0;
    private int cursorY = 0;
    private boolean cursorVisible = true;

    /**
     * Constructor
     */
    public ConsoleContainer(
        ContainerId id,
        String title,
        ContextPath ownerPath,
        ContainerConfig config,
        String rendererId,
        int width,
        int height
    ) {
        super(id, title, ownerPath, config, rendererId);
     
        this.height = height;
        this.width = width;
        
        // Allocate buffers
        this.cells = new Cell[height][width];
        this.prevCells = new Cell[height][width];
        
        // Initialize cells
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                cells[y][x] = new Cell();
                prevCells[y][x] = new Cell();
                prevCells[y][x].character = (char) 0xFFFF; // Force initial render
            }
        }
    }
    
    // ===== MESSAGE MAP SETUP =====
    
    @Override
    protected void setupMessageMap() {
        // Batch command
        msgMap.put(ContainerCommands.CONAINER_BATCH, this::handleBatchCommand);
        
        // Individual terminal commands
        msgMap.put(TerminalCommands.TERMINAL_CLEAR, this::handleClear);
        msgMap.put(TerminalCommands.TERMINAL_PRINT, this::terminalPrint);
        msgMap.put(TerminalCommands.TERMINAL_PRINTLN, this::terminalPrintLn);
        msgMap.put(TerminalCommands.TERMINAL_PRINT_AT, this::handlePrintAt);
        msgMap.put(TerminalCommands.TERMINAL_MOVE_CURSOR, this::handleMoveCursor);
        msgMap.put(TerminalCommands.TERMINAL_SHOW_CURSOR, this::handleShowCursor);
        msgMap.put(TerminalCommands.TERMINAL_HIDE_CURSOR, this::handleHideCursor);
        msgMap.put(TerminalCommands.TERMINAL_CLEAR_LINE, this::handleClearLine);
        msgMap.put(TerminalCommands.TERMINAL_CLEAR_LINE_AT, this::handleClearLineAt);
        msgMap.put(TerminalCommands.TERMINAL_CLEAR_REGION, this::handleClearRegion);
        msgMap.put(TerminalCommands.TERMINAL_DRAW_BOX, this::handleDrawBox);
        msgMap.put(TerminalCommands.TERMINAL_DRAW_HLINE, this::handleDrawHLine);
        msgMap.put(TerminalCommands.TERMINAL_DRAW_VLINE, this::handleDrawVLine);
        msgMap.put(TerminalCommands.TERMINAL_FILL_REGION, this::handleFillRegion);
        msgMap.put(TerminalCommands.TERMINAL_DRAW_BORDERED_TEXT, this::handleDrawBorderedText);
        msgMap.put(TerminalCommands.TERMINAL_DRAW_PANEL, this::handleDrawPanel);
        msgMap.put(TerminalCommands.TERMINAL_DRAW_BUTTON, this::handleDrawButton);
        msgMap.put(TerminalCommands.TERMINAL_DRAW_PROGRESS_BAR, this::handleDrawProgressBar);
        msgMap.put(TerminalCommands.TERMINAL_DRAW_TEXT_BLOCK, this::handleDrawTextBlock);
        msgMap.put(TerminalCommands.TERMINAL_SHADE_REGION, this::handleShadeRegion);
    }

    @Override
    protected void setupBatchMsgMap(){
        batchMsgMap.put(TerminalCommands.TERMINAL_CLEAR, (cmd)->clearInternal());
        batchMsgMap.put(TerminalCommands.TERMINAL_PRINT, (cmd)->executePrintInternal(cmd, false));
        batchMsgMap.put(TerminalCommands.TERMINAL_PRINTLN, (cmd)->executePrintInternal(cmd, true));
        batchMsgMap.put(TerminalCommands.TERMINAL_PRINT_AT, (cmd)->executePrintAtInternal(cmd));
        batchMsgMap.put(TerminalCommands.TERMINAL_MOVE_CURSOR, (cmd)->executeMoveCursorInternal(cmd));
        batchMsgMap.put(TerminalCommands.TERMINAL_SHOW_CURSOR, (cmd)->{ cursorVisible = true; });
        batchMsgMap.put(TerminalCommands.TERMINAL_HIDE_CURSOR, (cmd)->{ cursorVisible = false; });
        batchMsgMap.put(TerminalCommands.TERMINAL_CLEAR_LINE, (cmd)->clearLineInternal(cursorY));
        batchMsgMap.put(TerminalCommands.TERMINAL_CLEAR_LINE_AT, (cmd)-> executeClearLineAtInternal(cmd));
        batchMsgMap.put(TerminalCommands.TERMINAL_CLEAR_REGION, (cmd)->executeClearRegionInternal(cmd));
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_BOX, (cmd)->executeDrawBoxInternal(cmd));
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_HLINE, (cmd)->executeDrawHLineInternal(cmd));
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_VLINE, (cmd)->executeDrawVLineInternal(cmd));
        batchMsgMap.put(TerminalCommands.TERMINAL_FILL_REGION, (cmd)->executeFillRegionInternal(cmd));
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_BORDERED_TEXT, (cmd)->executeDrawBorderedTextInternal(cmd));
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_PANEL, (cmd)->executeDrawPanelInternal(cmd));
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_BUTTON, (cmd)->executeDrawButtonInternal(cmd));
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_PROGRESS_BAR, (cmd)->executeDrawProgressBarInternal(cmd));
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_TEXT_BLOCK, (cmd)->executeDrawTextBlockInternal(cmd));
        batchMsgMap.put(TerminalCommands.TERMINAL_SHADE_REGION, (cmd)->executeShadeRegionInternal(cmd));
    }

    @Override
    protected void setupStateTransitions() {
        // Container-specific state transitions can go here
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    protected CompletableFuture<Void> initializeRenderer() {
        Log.logMsg("[ConsoleContainer] Renderer initialized: " + id);
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== RENDER STATE ACCESS =====
    
    /**
     * Get renderable state snapshot (PULL-BASED)
     * Called by renderer when it wants to render
     */
    public CompletableFuture<ConsoleRenderManager.RenderableState> getRenderableState() {
        return containerExecutor.submit(() -> {
            return new ConsoleRenderManager.RenderableState(
                height, width,
                cursorY, cursorX,
                cursorVisible,
                cells,
                prevCells
            );
        });
    }
    
    /**
     * Commit render - called by RenderManager after successful render
     * Updates prevCells to match cells (for differential rendering)
     */
    public CompletableFuture<Void> commitRender() {
        return containerExecutor.execute(() -> {
            swapBuffersInternal();
        });
    }
    
    /**
     * Request render - sets RENDER_REQUESTED state
     * RenderManager polls this flag and renders when ready
     */
    private void requestRenderInternal() {
        // Only request render if container should be rendered
        if (shouldRender()) {
            stateMachine.addState(STATE_RENDER_REQUESTED);
            notifyRequestMade();
        }
    }
    
    // ===== BATCH COMMAND HANDLER =====
    
    /**
     * Handle batch of commands atomically
     */
    public CompletableFuture<Void> handleBatchCommand(NoteBytesMap message) {
        NoteBytes cmdsBytes = message.get(ContainerCommands.BATCH_COMMANDS);
        if (cmdsBytes == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("batch_commands required")
            );
        }
        
        NoteBytes[] cmdsArray = cmdsBytes.getAsNoteBytesArray().getAsArray();
        
        return containerExecutor.execute(() -> {
            // Execute all commands serially
            for (NoteBytes cmdBytes : cmdsArray) {
                NoteBytesMap cmd = cmdBytes.getAsNoteBytesMap();
                executeCommandInternal(cmd);
            }
            
            // Single render request after all commands
            requestRenderInternal();
        });
    }
 
    /**
     * Execute single command internally (within serial executor)
     * Does NOT request render - caller handles that
     */
    private void executeCommandInternal(NoteBytesMap cmd) {
        NoteBytesReadOnly cmdType = cmd.getReadOnly(Keys.CMD);
        if (cmdType == null) {
            Log.logError("[ConsoleContainer:" + id + "] Command missing 'cmd' field");
            return;
        }
        Log.logNoteBytes("[ConsoleContainer.executCommandInternal]", cmd);

        MessageExecutor msgExec = batchMsgMap.get(cmdType);
        
        if(msgExec != null){
            msgExec.execute(cmd);
        } else {
            Log.logError("[ConsoleContainer:" + id + "] Unknown command: " + cmdType);
        }
    }
    
    // ===== INDIVIDUAL COMMAND HANDLERS =====
    
    public CompletableFuture<Void> handleClear(NoteBytesMap command) {
        Log.logMsg("[ConsoleContainer.handleClear]");
        return containerExecutor.execute(() -> {
            clearInternal();
            requestRenderInternal();
        });
    }
    
    public CompletableFuture<Void> terminalPrint(NoteBytesMap command) {
        return handlePrint(command, false);
    }

    public CompletableFuture<Void> terminalPrintLn(NoteBytesMap command) {
        return handlePrint(command, true);
    }

    public CompletableFuture<Void> handlePrint(NoteBytesMap command, boolean newline) {
        NoteBytes textBytes = command.get(Keys.TEXT);
        if (textBytes == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        return containerExecutor.execute(() -> {
            executePrintInternal(command, newline);
            requestRenderInternal();
        });
    }
    
    public CompletableFuture<Void> handlePrintAt(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            executePrintAtInternal(command);
            requestRenderInternal();
        });
    }
    
    public CompletableFuture<Void> handleMoveCursor(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            executeMoveCursorInternal(command);
            requestRenderInternal();
        });
    }
    
    public CompletableFuture<Void> handleShowCursor(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            cursorVisible = true;
            requestRenderInternal();
        });
    }
    
    public CompletableFuture<Void> handleHideCursor(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            cursorVisible = false;
            requestRenderInternal();
        });
    }
    
    public CompletableFuture<Void> handleClearLine(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            clearLineInternal(cursorY);
            requestRenderInternal();
        });
    }
    
    public CompletableFuture<Void> handleClearLineAt(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            executeClearLineAtInternal(command);
            requestRenderInternal();
        });
    }
    
    public CompletableFuture<Void> handleClearRegion(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            executeClearRegionInternal(command);
            requestRenderInternal();
        });
    }
    
    public CompletableFuture<Void> handleDrawBox(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            executeDrawBoxInternal(command);
            requestRenderInternal();
        });
    }
    
    public CompletableFuture<Void> handleDrawHLine(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            executeDrawHLineInternal(command);
            requestRenderInternal();
        });
    }
    
    public CompletableFuture<Void> handleDrawVLine(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            executeDrawVLineInternal(command);
            requestRenderInternal();
        });
    }
    
    public CompletableFuture<Void> handleFillRegion(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            executeFillRegionInternal(command);
            requestRenderInternal();
        });
    }
    

    // Draw bordered text
    public CompletableFuture<Void> handleDrawBorderedText(NoteBytesMap message) {
        return containerExecutor.execute(() -> {
            executeDrawBorderedTextInternal(message);
            requestRenderInternal();
        });
    }

    public CompletableFuture<Void> handleDrawPanel(NoteBytesMap message) {
        return containerExecutor.execute(() -> {
            executeDrawPanelInternal(message);
            requestRenderInternal();
        });
    }

    public CompletableFuture<Void> handleDrawButton(NoteBytesMap message) {
        return containerExecutor.execute(() -> {
            executeDrawButtonInternal(message);
            requestRenderInternal();
        });
    }

    public CompletableFuture<Void> handleDrawProgressBar(NoteBytesMap message) {
        return containerExecutor.execute(() -> {
            executeDrawProgressBarInternal(message);
            requestRenderInternal();
        });
    }

    public CompletableFuture<Void> handleDrawTextBlock(NoteBytesMap message) {
        return containerExecutor.execute(() -> {
            executeDrawTextBlockInternal(message);
            requestRenderInternal();
        });
    }

    public CompletableFuture<Void> handleShadeRegion(NoteBytesMap message) {
        return containerExecutor.execute(() -> {
            executeShadeRegionInternal(message);
            requestRenderInternal();
        });
    }
    // ===== INTERNAL EXECUTION METHODS =====
    
    private void executePrintInternal(NoteBytesMap cmd, boolean newline) {
        NoteBytes textBytes = cmd.get(Keys.TEXT);
        NoteBytes styleBytes = cmd.get(Keys.STYLE);
        
        if (textBytes == null) return;
        
        String text = textBytes.getAsString();
        TextStyle style = parseStyle(styleBytes);
        
        printInternal(text, style, newline);
    }
    
    private void executePrintAtInternal(NoteBytesMap cmd) {
        NoteBytes xBytes = cmd.get(Keys.X);
        NoteBytes yBytes = cmd.get(Keys.Y);
        NoteBytes textBytes = cmd.get(Keys.TEXT);
        NoteBytes styleBytes = cmd.get(Keys.STYLE);
        
        if (xBytes == null || yBytes == null || textBytes == null) return;
        
        int x = xBytes.getAsInt();
        int y = yBytes.getAsInt();
        String text = textBytes.getAsString();
        TextStyle style = parseStyle(styleBytes);
        
        printAtInternal(x, y, text, style);
    }
    
    private void executeMoveCursorInternal(NoteBytesMap cmd) {
        NoteBytes xBytes = cmd.get(Keys.X);
        NoteBytes yBytes = cmd.get(Keys.Y);
        
        if (xBytes == null || yBytes == null) return;
        
        cursorX = xBytes.getAsInt();
        cursorY = yBytes.getAsInt();
    }
    
    private void executeClearLineAtInternal(NoteBytesMap cmd) {
        NoteBytes yBytes = cmd.get(Keys.Y);
        if (yBytes == null) return;
        
        clearLineInternal(yBytes.getAsInt());
    }
    
    private void executeClearRegionInternal(NoteBytesMap cmd) {
        NoteBytes regionBytes = cmd.get(Keys.REGION);
        if (regionBytes == null) return;
        
        TerminalRectangle region = TerminalRectangle.fromNoteBytes(regionBytes.getAsNoteBytesMap());
        clearRegionInternal(region);
    }
    
    private void executeDrawBoxInternal(NoteBytesMap cmd) {
        NoteBytes regionBytes = cmd.get(Keys.REGION);
        NoteBytes renderRegionBytes = cmd.get(TerminalCommands.RENDER_REGION);
        if (regionBytes == null) return;
        
        TerminalRectangle region = TerminalRectangle.fromNoteBytes(regionBytes);
        TerminalRectangle renderRegion = renderRegionBytes != null ? TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;
        
        String title = cmd.getAsString(Keys.TITLE, null);
        String titlePosStr = cmd.getAsString(TerminalCommands.TITLE_POS, "TOP_CENTER");
        Position titlePos = Position.valueOf(titlePosStr);
        String boxStyleName = cmd.getAsString(TerminalCommands.BOX_STYLE, "SINGLE");
        BoxStyle boxStyle = BoxStyle.valueOf(boxStyleName);
        
        if(renderRegion != null){
            drawBoxInternal(region, renderRegion, title, titlePos, boxStyle);
        }else{
            drawBoxInternal(region.getX(), region.getY(), region.getWidth(), region.getHeight(), title, titlePos, boxStyle);
            regionPool.recycle(region);
        }
    }
        
    private void executeDrawHLineInternal(NoteBytesMap cmd) {
        NoteBytes xBytes = cmd.get(Keys.X);
        NoteBytes yBytes = cmd.get(Keys.Y);
        NoteBytes lengthBytes = cmd.get(Keys.LENGTH);
        
        if (xBytes == null || yBytes == null || lengthBytes == null) return;
        
        drawHLineInternal(
            xBytes.getAsInt(),
            yBytes.getAsInt(),
            lengthBytes.getAsInt()
        );
    }
    
    private void executeDrawVLineInternal(NoteBytesMap cmd) {
        NoteBytes xBytes = cmd.get(Keys.X);
        NoteBytes yBytes = cmd.get(Keys.Y);
        NoteBytes lengthBytes = cmd.get(Keys.LENGTH);
        
        if (xBytes == null || yBytes == null || lengthBytes == null) return;
        
        drawVLineInternal(
            xBytes.getAsInt(),
            yBytes.getAsInt(),
            lengthBytes.getAsInt()
        );
    }
    
    private void executeFillRegionInternal(NoteBytesMap cmd) {
        NoteBytes regionBytes = cmd.get(Keys.REGION);
        NoteBytes codePointBytes = cmd.get(TerminalCommands.CODE_POINT);
        NoteBytes styleBytes = cmd.get(Keys.STYLE);
        
        if (regionBytes == null || codePointBytes == null) return;
        
        TerminalRectangle region = TerminalRectangle.fromNoteBytes(regionBytes.getAsNoteBytesMap());
        int codePoint = codePointBytes.getAsInt();
        TextStyle style = parseStyle(styleBytes);
        
        fillRegionInternal(region, codePoint, style);
    }
    
    // ===== LOW-LEVEL DRAWING OPERATIONS =====
    
    private void clearInternal() {
        Log.logMsg("[ConsoleContainer] CLEAR - prevCells[0][0]: '" + 
        prevCells[0][0].character + "' -> cells[0][0]: '" + 
        cells[0][0].character + "' after clear");
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                cells[y][x].clear();
            }
        }
        Log.logMsg("[ConsoleContainer] CLEAR executed - prevCells[0][0]: '" + 
        prevCells[0][0].character + "' -> cells[0][0]: '" + 
        cells[0][0].character + "' after clear");

        cursorX = 0;
        cursorY = 0;
    }
    
    private void printInternal(String text, TextStyle style, boolean newline) {
        for (char ch : text.toCharArray()) {
            if (cursorY >= height) break;
            
            if (ch == '\n' || cursorX >= width) {
                cursorY++;
                cursorX = 0;
                if (ch == '\n') continue;
            }
            
            cells[cursorY][cursorX].set(ch, style);
            cursorX++;
        }
        
        if (newline) {
            cursorY++;
            cursorX = 0;
        }
    }
    
    private void printAtInternal(int x, int y, String text, TextStyle style) {
        if (y < 0 || y >= height || x < 0) return;
        
        int printX = x;
        for (char ch : text.toCharArray()) {
            if (printX >= width) break;
            if (printX >= 0) {
                cells[y][printX].set(ch, style);
            }
            printX++;
        }
    }
    
    private void clearLineInternal(int y) {
        if (y < 0 || y >= height) return;
        
        for (int x = 0; x < width; x++) {
            cells[y][x].clear();
        }
    }
    
    private void clearRegionInternal(TerminalRectangle region) {
        int startX = Math.max(0, region.getX());
        int startY = Math.max(0, region.getY());
        int endX = Math.min(width - 1, region.getX() + region.getWidth() - 1);
        int endY = Math.min(height - 1, region.getY() + region.getHeight() - 1);
        
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                cells[y][x].clear();
            }
        }
    }
    
    private void drawBoxInternal(
        int x, 
        int y,
        int boxWidth, 
        int boxHeight, 
        String title, 
        Position titlePos, 
        BoxStyle style
    ) {
        if (x < 0 || y < 0 || x + boxWidth > width || y + boxHeight > height) return;
        if (boxWidth < 2 || boxHeight < 2) return;
        
        char[] chars = style.getChars();
        
        // Top border
        printAtInternal(x, y, String.valueOf(chars[2]), new TextStyle());
        for (int i = 1; i < boxWidth - 1; i++) {
            printAtInternal(x + i, y, String.valueOf(chars[0]), new TextStyle());
        }
        printAtInternal(x + boxWidth - 1, y, String.valueOf(chars[3]), new TextStyle());
        
        // Title positioning
        if (title != null && !title.isEmpty()) {
            int titleX = calculateTitleX(x, boxWidth, title, titlePos);
            int titleY = calculateTitleY(y, boxHeight, titlePos);
            
            if (titleX >= x && titleX + title.length() + 2 <= x + boxWidth) {
                printAtInternal(titleX, titleY, " " + title + " ", new TextStyle());
            }
        }
        
        // Sides
        for (int i = 1; i < boxHeight - 1; i++) {
            printAtInternal(x, y + i, String.valueOf(chars[1]), new TextStyle());
            printAtInternal(x + boxWidth - 1, y + i, String.valueOf(chars[1]), new TextStyle());
        }
        
        // Bottom border
        printAtInternal(x, y + boxHeight - 1, String.valueOf(chars[4]), new TextStyle());
        for (int i = 1; i < boxWidth - 1; i++) {
            printAtInternal(x + i, y + boxHeight - 1, String.valueOf(chars[0]), new TextStyle());
        }
        printAtInternal(x + boxWidth - 1, y + boxHeight - 1, String.valueOf(chars[5]), new TextStyle());
    }

    private void drawBoxInternal(TerminalRectangle region, TerminalRectangle renderRegion,
                            String title, Position titlePos, BoxStyle style) {
        int x = region.getX();
        int y = region.getY();
        int boxWidth = region.getWidth();
        int boxHeight = region.getHeight();
        
        if (boxWidth < 2 || boxHeight < 2) return;
        
        char[] chars = style.getChars();
        
        int visLeft = renderRegion.getX();
        int visTop = renderRegion.getY();
        int visRight = renderRegion.getX() + renderRegion.getWidth();
        int visBottom = renderRegion.getY() + renderRegion.getHeight();
        
        // Top border - only visible portion
        if (y >= visTop && y < visBottom) {
            for (int cx = Math.max(x, visLeft); cx < Math.min(x + boxWidth, visRight); cx++) {
                char ch;
                if (cx == x) ch = chars[2]; // top-left
                else if (cx == x + boxWidth - 1) ch = chars[3]; // top-right
                else ch = chars[0]; // horizontal
                printAtInternal(cx, y, String.valueOf(ch), new TextStyle());
            }
        }
        
        // Title - calculate position based on full region, render only visible
        if (title != null && !title.isEmpty()) {
            int titleX = calculateTitleX(x, boxWidth, title, titlePos);
            int titleY = calculateTitleY(y, boxHeight, titlePos);
            
            if (titleY >= visTop && titleY < visBottom && titleX >= visLeft && titleX + title.length() < visRight) {
                String visible = clipString(title, titleX, visLeft, visRight);
                int renderX = Math.max(titleX, visLeft);
                if (!visible.isEmpty()) {
                    printAtInternal(renderX, titleY, " " + visible + " ", new TextStyle());
                }
            }
        }
        
        // Sides - only visible rows
        for (int cy = Math.max(y + 1, visTop); cy < Math.min(y + boxHeight - 1, visBottom); cy++) {
            if (x >= visLeft && x < visRight) {
                printAtInternal(x, cy, String.valueOf(chars[1]), new TextStyle());
            }
            if (x + boxWidth - 1 >= visLeft && x + boxWidth - 1 < visRight) {
                printAtInternal(x + boxWidth - 1, cy, String.valueOf(chars[1]), new TextStyle());
            }
        }
        
        // Bottom border
        if (y + boxHeight - 1 >= visTop && y + boxHeight - 1 < visBottom) {
            for (int cx = Math.max(x, visLeft); cx < Math.min(x + boxWidth, visRight); cx++) {
                char ch;
                if (cx == x) ch = chars[4]; // bottom-left
                else if (cx == x + boxWidth - 1) ch = chars[5]; // bottom-right
                else ch = chars[0]; // horizontal
                printAtInternal(cx, y + boxHeight - 1, String.valueOf(ch), new TextStyle());
            }
        }
        regionPool.recycle(region);
        regionPool.recycle(renderRegion);
    }
    
    private int calculateTitleX(int x, int boxWidth, String title, Position pos) {
        return switch (pos) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> x + 1;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> x + (boxWidth - title.length()) / 2;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> x + boxWidth - title.length() - 1;
        };
    }

    private int calculateTitleY(int y, int boxHeight, Position pos) {
        return switch (pos) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> y;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> y + boxHeight / 2;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> y + boxHeight - 1;
        };
    }

    private void drawHLineInternal(int x, int y, int length) {
        for (int i = 0; i < length; i++) {
            printAtInternal(x + i, y, "─", new TextStyle());
        }
    }
    
    private void drawVLineInternal(int x, int y, int length) {
        for (int i = 0; i < length; i++) {
            printAtInternal(x, y + i, "│", new TextStyle());
        }
    }
    
    private void fillRegionInternal(TerminalRectangle region, int codePoint, TextStyle style) {
        int startX = Math.max(0, region.getX());
        int startY = Math.max(0, region.getY());
        int endX = Math.min(width - 1, region.getX() + region.getWidth() - 1);
        int endY = Math.min(height - 1, region.getY() + region.getHeight() - 1);
        
        char fillChar = (char) codePoint;
        
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                cells[y][x].set(fillChar, style);
            }
        }
    }
    
    private void swapBuffersInternal() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                prevCells[y][x].copyFrom(cells[y][x]);
            }
        }
    }


private void executeDrawBorderedTextInternal(NoteBytesMap cmd) {
    NoteBytes regionBytes = cmd.get(Keys.REGION);
    if (regionBytes == null) return;
    
    TerminalRectangle region = TerminalRectangle.fromNoteBytes(regionBytes);
    String text = cmd.getAsString(Keys.TEXT, "");
    String textPosStr = cmd.getAsString(TerminalCommands.TITLE_POS, "CENTER");
    Position textPos = Position.valueOf(textPosStr);
    String boxStyleName = cmd.getAsString(TerminalCommands.BOX_STYLE, "SINGLE");
    BoxStyle boxStyle = BoxStyle.valueOf(boxStyleName);
    
    TextStyle textStyle = parseStyle(cmd.get(Keys.STYLE));
    TextStyle borderStyle = parseStyle(cmd.get(StyleConstants.BORDER_STYLE));
    
    drawBorderedTextInternal(region, text, textPos, boxStyle, textStyle, borderStyle);
}

private void executeDrawPanelInternal(NoteBytesMap cmd) {
    NoteBytes regionBytes = cmd.get(Keys.REGION);
    if (regionBytes == null) return;

    NoteBytes renderRegionBytes = cmd.get(TerminalCommands.RENDER_REGION);

    TerminalRectangle region = TerminalRectangle.fromNoteBytes(regionBytes);
    TerminalRectangle renderRegion = renderRegionBytes != null ? 
        TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;
    
    String title = cmd.getAsString(Keys.TITLE, null);
    String titlePosStr = cmd.getAsString(TerminalCommands.TITLE_POS, "TOP_CENTER");
    Position titlePos = Position.valueOf(titlePosStr);
    String boxStyleName = cmd.getAsString(TerminalCommands.BOX_STYLE, "SINGLE");
    BoxStyle boxStyle = BoxStyle.valueOf(boxStyleName);
    
    TextStyle borderStyle = parseStyle(cmd.get(Keys.STYLE));
    TextStyle fillStyle = parseStyle(cmd.get(StyleConstants.BG_STYLE));
    if(renderRegion == null){
        drawPanelInternal(region, title, titlePos, boxStyle, borderStyle, fillStyle);
        regionPool.recycle(region);
    }else{
        drawPanelInternal(region, renderRegion, title, titlePos, boxStyle, borderStyle, fillStyle);
        regionPool.recycle(region);
        regionPool.recycle(renderRegion);
    }
}

private void executeDrawButtonInternal(NoteBytesMap cmd) {
     NoteBytes regionBytes = cmd.get(Keys.REGION);
    NoteBytes renderRegionBytes = cmd.get(TerminalCommands.RENDER_REGION);
    if (regionBytes == null) return;
    
    TerminalRectangle region = TerminalRectangle.fromNoteBytes(regionBytes);
    TerminalRectangle renderRegion = renderRegionBytes != null ? 
        TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;
    
    String label = cmd.getAsString(Keys.TEXT, "");
    String labelPosStr = cmd.getAsString(TerminalCommands.TITLE_POS, "CENTER");
    Position labelPos = Position.valueOf(labelPosStr);
    boolean selected = cmd.getAsBoolean(TerminalCommands.SELECTED, false);
    TextStyle style = parseStyle(cmd.get(Keys.STYLE));
    
    if(renderRegion == null){
        drawButtonInternal(region, label, labelPos, selected, style);
    }else{
        drawButtonInternal(region, renderRegion, label, labelPos, selected, style);
    }
}

private void executeDrawProgressBarInternal(NoteBytesMap cmd) {
    NoteBytes regionBytes = cmd.get(Keys.REGION);
    if (regionBytes == null) return;
    NoteBytes renderRegionBytes = cmd.get(TerminalCommands.RENDER_REGION);

    TerminalRectangle region = TerminalRectangle.fromNoteBytes(regionBytes);
    TerminalRectangle renderRegion = renderRegionBytes != null ? 
        TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;
    
    double progress = cmd.getAsDouble(TerminalCommands.PROGRESS, 0.0);
    TextStyle style = parseStyle(cmd.get(Keys.STYLE));
    TextStyle emptyStyle = parseStyle(cmd.get(StyleConstants.EMPTY_STYLE));
    
    if(renderRegion == null){
        drawProgressBarInternal(region, progress, style, emptyStyle);
    }else{
        drawProgressBarInternal(region, renderRegion, progress, style, emptyStyle);
    }
}

private void executeDrawTextBlockInternal(NoteBytesMap cmd) {
    NoteBytes regionBytes = cmd.get(Keys.REGION);
    if (regionBytes == null) return;

    NoteBytes renderRegionBytes = cmd.get(TerminalCommands.RENDER_REGION);

    TerminalRectangle region = TerminalRectangle.fromNoteBytes(regionBytes);
    TerminalRectangle renderRegion = renderRegionBytes != null ? 
        TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;
    
    String text = cmd.getAsString(Keys.TEXT, "");
    String alignName = cmd.getAsString(TerminalCommands.ALIGN, "LEFT");
    TerminalCommands.Alignment align = TerminalCommands.Alignment.valueOf(alignName);
    TextStyle style = parseStyle(cmd.get(Keys.STYLE));
    
    if(renderRegion == null){
        drawTextBlockInternal(region, text, align, style);
    }else{
        drawTextBlockInternal(region, renderRegion, text, align, style);
    }
}

private void executeShadeRegionInternal(NoteBytesMap cmd) {
    NoteBytes regionBytes = cmd.get(Keys.REGION);
    if (regionBytes == null) return;

    NoteBytes renderRegionBytes = cmd.get(TerminalCommands.RENDER_REGION);
    TerminalRectangle region = TerminalRectangle.fromNoteBytes(regionBytes);
    TerminalRectangle renderRegion = renderRegionBytes != null ? 
        TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;
    
    String shadeStr = cmd.getAsString(TerminalCommands.SHADE_CHAR, "░");
    char shadeChar = shadeStr.isEmpty() ? '░' : shadeStr.charAt(0);
    TextStyle style = parseStyle(cmd.get(Keys.STYLE));
    
    if(renderRegion == null){
        shadeRegionInternal(region, shadeChar, style);
    }else{
        fillRegionInternal(renderRegion, shadeChar, style);
    }
}


private void drawBorderedTextInternal(TerminalRectangle region, String text, Position textPos,
                                      BoxStyle boxStyle, TextStyle textStyle, TextStyle borderStyle) {
    drawBoxInternal(region.getX(), region.getY(), region.getWidth(), region.getHeight(), 
                   null, null, boxStyle);
    
    if (text != null && !text.isEmpty() && region.getWidth() > 2 && region.getHeight() > 2) {
        int[] coords = calculateTextPosition(region, text, textPos);
        printAtInternal(coords[0], coords[1], text, textStyle);
    }
}

private void drawPanelInternal(TerminalRectangle region, String title, Position titlePos,
                              BoxStyle boxStyle, TextStyle borderStyle, TextStyle fillStyle) {
    int fillX = region.getX() + 1;
    int fillY = region.getY() + 1;
    int fillWidth = region.getWidth() - 2;
    int fillHeight = region.getHeight() - 2;
    
    if (fillWidth > 0 && fillHeight > 0) {
        for (int y = fillY; y < fillY + fillHeight; y++) {
            for (int x = fillX; x < fillX + fillWidth; x++) {
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    cells[y][x].set(' ', fillStyle);
                }
            }
        }
    }
    
    drawBoxInternal(region.getX(), region.getY(), region.getWidth(), region.getHeight(), 
                   title, titlePos, boxStyle);
}

private void drawButtonInternal(TerminalRectangle region, String label, Position labelPos,
                               boolean selected, TextStyle style) {
    TextStyle buttonStyle = selected ? style.inverse() : style;
    
    for (int y = region.getY(); y < region.getY() + region.getHeight(); y++) {
        for (int x = region.getX(); x < region.getX() + region.getWidth(); x++) {
            if (x >= 0 && x < width && y >= 0 && y < height) {
                cells[y][x].set(' ', buttonStyle);
            }
        }
    }
    
    if (!label.isEmpty()) {
        int[] coords = calculateTextPosition(region, label, labelPos);
        printAtInternal(coords[0], coords[1], label, buttonStyle);
    }
}

private void drawButtonInternal(TerminalRectangle region, TerminalRectangle renderRegion,
                               String label, Position labelPos, boolean selected, TextStyle style) {
    TextStyle buttonStyle = selected ? style.inverse() : style;
    
    int visLeft = renderRegion.getX();
    int visTop = renderRegion.getY();
    int visRight = renderRegion.getX() + renderRegion.getWidth();
    int visBottom = renderRegion.getY() + renderRegion.getHeight();
    
    // Fill button - only visible portion
    for (int y = Math.max(region.getY(), visTop); y < Math.min(region.getY() + region.getHeight(), visBottom); y++) {
        for (int x = Math.max(region.getX(), visLeft); x < Math.min(region.getX() + region.getWidth(), visRight); x++) {
            if (x >= 0 && x < width && y >= 0 && y < height) {
                cells[y][x].set(' ', buttonStyle);
            }
        }
    }
    
    // Label - calculate position based on full region, render only visible
    if (!label.isEmpty()) {
        int[] coords = calculateTextPosition(region, label, labelPos);
        int labelX = coords[0];
        int labelY = coords[1];
        
        if (labelY >= visTop && labelY < visBottom) {
            String visible = clipString(label, labelX, visLeft, visRight);
            int renderX = Math.max(labelX, visLeft);
            if (!visible.isEmpty()) {
                printAtInternal(renderX, labelY, visible, buttonStyle);
            }
        }
    }
}

private void drawPanelInternal(TerminalRectangle region, TerminalRectangle renderRegion,
                              String title, Position titlePos, BoxStyle boxStyle, 
                              TextStyle borderStyle, TextStyle fillStyle) {
    int fillX = region.getX() + 1;
    int fillY = region.getY() + 1;
    int fillWidth = region.getWidth() - 2;
    int fillHeight = region.getHeight() - 2;
    
    int visLeft = renderRegion.getX();
    int visTop = renderRegion.getY();
    int visRight = renderRegion.getX() + renderRegion.getWidth();
    int visBottom = renderRegion.getY() + renderRegion.getHeight();
    
    // Fill interior - only visible portion
    if (fillWidth > 0 && fillHeight > 0) {
        for (int y = Math.max(fillY, visTop); y < Math.min(fillY + fillHeight, visBottom); y++) {
            for (int x = Math.max(fillX, visLeft); x < Math.min(fillX + fillWidth, visRight); x++) {
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    cells[y][x].set(' ', fillStyle);
                }
            }
        }
    }
    
    drawBoxInternal(region, renderRegion, title, titlePos, boxStyle);
}

private int[] calculateTextPosition(TerminalRectangle region, String text, Position pos) {
    int textLen = text.length();
    int x = region.getX() + 1;
    int y = region.getY() + 1;
    
    x = switch (pos) {
        case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> region.getX() + 1;
        case TOP_CENTER, CENTER, BOTTOM_CENTER -> region.getX() + (region.getWidth() - textLen) / 2;
        case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> region.getX() + region.getWidth() - textLen - 1;
    };
    
    y = switch (pos) {
        case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> region.getY() + 1;
        case CENTER_LEFT, CENTER, CENTER_RIGHT -> region.getY() + (region.getHeight() / 2);
        case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> region.getY() + region.getHeight() - 2;
    };
    
    x = Math.max(region.getX() + 1, Math.min(x, region.getX() + region.getWidth() - textLen - 1));
    y = Math.max(region.getY() + 1, Math.min(y, region.getY() + region.getHeight() - 2));
    
    return new int[] { x, y };
}

private void drawProgressBarInternal(TerminalRectangle region, double progress, 
                                    TextStyle style, TextStyle emptyStyle) {
    progress = Math.max(0.0, Math.min(1.0, progress));
    
    int barWidth = region.getWidth();
    double exactFilled = progress * barWidth;
    int fullBlocks = (int) exactFilled;
    double fraction = exactFilled - fullBlocks;
    
    // Calculate which partial block to use (0-8)
    int partialIndex = (int) Math.round(fraction * 8);
    
    for (int y = region.getY(); y < region.getY() + region.getHeight(); y++) {
        if (y < 0 || y >= height) continue;
        
        for (int x = region.getX(); x < region.getX() + barWidth; x++) {
            if (x < 0 || x >= width) continue;
            
            int pos = x - region.getX();
            
            if (pos < fullBlocks) {
                // Full block
                cells[y][x].set('█', style);
            } else if (pos == fullBlocks && partialIndex > 0 && partialIndex < 8) {
                // Partial block
                cells[y][x].set(TerminalCommands.PROGRESS_BLOCKS[partialIndex].charAt(0), style);
            } else {
                // Empty
                cells[y][x].set(' ', emptyStyle);
            }
        }
    }
}

private void drawProgressBarInternal(TerminalRectangle region, TerminalRectangle renderRegion,
                                    double progress, TextStyle style, TextStyle emptyStyle) {
    progress = Math.max(0.0, Math.min(1.0, progress));
    
    int barWidth = region.getWidth();
    double exactFilled = progress * barWidth;
    int fullBlocks = (int) exactFilled;
    double fraction = exactFilled - fullBlocks;
    int partialIndex = (int) Math.round(fraction * 8);
    
    int visLeft = renderRegion.getX();
    int visTop = renderRegion.getY();
    int visRight = renderRegion.getX() + renderRegion.getWidth();
    int visBottom = renderRegion.getY() + renderRegion.getHeight();
    
    for (int y = Math.max(region.getY(), visTop); y < Math.min(region.getY() + region.getHeight(), visBottom); y++) {
        if (y < 0 || y >= height) continue;
        
        for (int x = Math.max(region.getX(), visLeft); x < Math.min(region.getX() + barWidth, visRight); x++) {
            if (x < 0 || x >= width) continue;
            
            int pos = x - region.getX();
            
            if (pos < fullBlocks) {
                cells[y][x].set('█', style);
            } else if (pos == fullBlocks && partialIndex > 0 && partialIndex < 8) {
                cells[y][x].set(TerminalCommands.PROGRESS_BLOCKS[partialIndex].charAt(0), style);
            } else {
                cells[y][x].set(' ', emptyStyle);
            }
        }
    }
}

private void drawTextBlockInternal(TerminalRectangle region, String text, 
                                  TerminalCommands.Alignment align, TextStyle style) {
    if (text == null || text.isEmpty()) return;
    
    // Simple word wrapping implementation
    String[] lines = wrapText(text, region.getWidth());
    
    int y = region.getY();
    for (String line : lines) {
        if (y >= region.getY() + region.getHeight()) break;
        if (y < 0 || y >= height) {
            y++;
            continue;
        }
        
        int x = region.getX();
        
        // Apply alignment
        if (align == TerminalCommands.Alignment.CENTER) {
            x = region.getX() + (region.getWidth() - line.length()) / 2;
        } else if (align == TerminalCommands.Alignment.RIGHT) {
            x = region.getX() + region.getWidth() - line.length();
        }
        
        x = Math.max(region.getX(), Math.min(x, region.getX() + region.getWidth() - line.length()));
        
        printAtInternal(x, y, line, style);
        y++;
    }
}

private void drawTextBlockInternal(TerminalRectangle region, TerminalRectangle renderRegion,
                                  String text, TerminalCommands.Alignment align, TextStyle style) {
    if (text == null || text.isEmpty()) return;
    
    String[] lines = wrapText(text, region.getWidth());
    
    int visLeft = renderRegion.getX();
    int visTop = renderRegion.getY();
    int visRight = renderRegion.getX() + renderRegion.getWidth();
    int visBottom = renderRegion.getY() + renderRegion.getHeight();
    
    int y = region.getY();
    for (String line : lines) {
        if (y >= region.getY() + region.getHeight()) break;
        if (y >= visTop && y < visBottom) {
            int x = region.getX();
            
            if (align == TerminalCommands.Alignment.CENTER) {
                x = region.getX() + (region.getWidth() - line.length()) / 2;
            } else if (align == TerminalCommands.Alignment.RIGHT) {
                x = region.getX() + region.getWidth() - line.length();
            }
            
            x = Math.max(region.getX(), Math.min(x, region.getX() + region.getWidth() - line.length()));
            
            String visible = clipString(line, x, visLeft, visRight);
            int renderX = Math.max(x, visLeft);
            if (!visible.isEmpty()) {
                printAtInternal(renderX, y, visible, style);
            }
        }
        y++;
    }
}

private void shadeRegionInternal(TerminalRectangle region, char shadeChar, TextStyle style) {
    fillRegionInternal(region, shadeChar, style);
}

// ===== ADD TEXT WRAPPING HELPER =====

private String[] wrapText(String text, int maxWidth) {
    if (maxWidth <= 0) return new String[0];
    
    java.util.List<String> lines = new java.util.ArrayList<>();
    String[] paragraphs = text.split("\n", -1);
    
    for (String paragraph : paragraphs) {
        if (paragraph.isEmpty()) {
            lines.add("");
            continue;
        }
        
        String[] words = paragraph.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (currentLine.length() == 0) {
                currentLine.append(word);
            } else if (currentLine.length() + 1 + word.length() <= maxWidth) {
                currentLine.append(" ").append(word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
    }
    
    return lines.toArray(new String[0]);
}
    
    // ===== RESIZE HANDLING =====
    
    public CompletableFuture<Void> resize(int newWidth, int newHeight) {
        return containerExecutor.execute(() -> {
            if (this.height == newHeight && this.width == newWidth) {
                return;
            }
            
            Log.logMsg("[ConsoleContainer:" + id + "] Resizing: " + 
                width + "x" + height + " -> " + newWidth + "x" + newHeight);
            
            // Create new buffers
            Cell[][] newCells = new Cell[newHeight][newWidth];
            Cell[][] newPrevCells = new Cell[newHeight][newWidth];
            
            // Initialize all cells
            for (int y = 0; y < newHeight; y++) {
                for (int x = 0; x < newWidth; x++) {
                    newCells[y][x] = new Cell();
                    newPrevCells[y][x] = new Cell();
                }
            }
            
            // Copy existing content
            if (this.cells != null) {
                int copyHeight = Math.min(this.height, newHeight);
                int copyWidth = Math.min(this.width, newWidth);
                
                for (int y = 0; y < copyHeight; y++) {
                    for (int x = 0; x < copyWidth; x++) {
                        if (cells[y] != null && cells[y][x] != null) {
                            newCells[y][x].copyFrom(cells[y][x]);
                        }
                    }
                }
            }
            
            // Update dimensions
            this.height = newHeight;
            this.width = newWidth;
            this.cells = newCells;
            this.prevCells = newPrevCells;
            
            // Clamp cursor
            this.cursorX = Math.min(this.cursorX, newWidth - 1);
            this.cursorY = Math.min(this.cursorY, newHeight - 1);
            
            // Request render
            requestRenderInternal();
            
            // Emit resize event
            NoteBytesMap resizeEvent = ContainerCommands.containerResized(id, newWidth, newHeight);
            emitEvent(resizeEvent);
        });
    }
    
    // ===== HELPERS =====

    private String clipString(String text, int textX, int visLeft, int visRight) {
        if (textX >= visRight || textX + text.length() <= visLeft) return "";
        
        int startIdx = Math.max(0, visLeft - textX);
        int endIdx = Math.min(text.length(), visRight - textX);
        
        if (startIdx >= endIdx) return "";
        return text.substring(startIdx, endIdx);
    }
    
    private TextStyle parseStyle(NoteBytes styleBytes) {
        if (styleBytes == null) return new TextStyle();
        return TextStyle.fromNoteBytes(styleBytes);
    }
    
    // ===== ACCESSORS =====
    
    public int getHeight() { return height; }
    public int getWidth() { return width; }
    public int getCursorX() { return cursorX; }
    public int getCursorY() { return cursorY; }
    public boolean isCursorVisible() { return cursorVisible; }
    
    // ===== EVENT DISPATCHING =====
    
    @Override
    protected void onDestroyGranted() {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.EVENT, io.netnotes.engine.io.input.events.EventBytes.EVENT_CONTAINER_CLOSED);
        emitEvent(map);
    }

    @Override
    protected void onFocusGranted() {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.EVENT, io.netnotes.engine.io.input.events.EventBytes.EVENT_CONTAINER_FOCUS_GAINED);
        emitEvent(map);
    }

    @Override
    protected void onFocusRevoked() {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.EVENT, io.netnotes.engine.io.input.events.EventBytes.EVENT_CONTAINER_FOCUS_LOST);
        emitEvent(map);
    }

    @Override
    protected void onHideGranted() {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.EVENT, io.netnotes.engine.io.input.events.EventBytes.EVENT_CONTAINER_HIDDEN);
        emitEvent(map);
    }

    @Override
    protected void onMaximizeGranted() {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.EVENT, io.netnotes.engine.io.input.events.EventBytes.EVENT_CONTAINER_MAXIMIZE);
        emitEvent(map);
    }

    @Override
    protected void onRestoreGranted() {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.EVENT, io.netnotes.engine.io.input.events.EventBytes.EVENT_CONTAINER_RESTORE);
        emitEvent(map);
    }

    @Override
    protected void onShowGranted() {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.EVENT, io.netnotes.engine.io.input.events.EventBytes.EVENT_CONTAINER_SHOWN);
        emitEvent(map);
    }
}