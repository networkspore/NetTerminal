package io.netnotes.app.console;

import io.netnotes.engine.core.system.control.containers.ContainerCommands;
import io.netnotes.engine.core.system.control.containers.ContainerType;
import io.netnotes.engine.core.system.control.terminal.TerminalCommands;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.MessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;

/**
 * ConsoleUIRenderer - Flicker-free terminal rendering
 * 
 * Anti-flicker techniques:
 * 1. Alternate screen buffer - atomic buffer swapping
 * 2. Proper batch mode - single atomic write per frame
 * 3. Differential rendering - only update changed cells
 * 4. Rate limiting - max 60fps
 * 5. Debouncing - group rapid updates
 * 6. Pre-allocated buffers - no GC pauses
 * 7. Style optimization - only emit codes when changed
 */
public class ConsoleUIRenderer implements UIRenderer {
    private final String description = "JLine3 flicker-free terminal renderer";

    private final Terminal terminal;
    private final Attributes originalAttributes;

    private final Map<NoteBytes, ContainerBuffer> containers = new ConcurrentHashMap<>();
    private volatile NoteBytes focusedContainerId = null;
    private volatile boolean active = false;
    
    // Batch mode
    private volatile boolean batchMode = false;
    private final List<Runnable> batchedOperations = new ArrayList<>();
    
    // Rate limiting
    private volatile long lastRenderTime = 0;
    private static final long MIN_RENDER_INTERVAL_MS = 16; // ~60fps
    
    // Debouncing
    private final ScheduledExecutorService renderScheduler = 
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConsoleRenderer");
            t.setDaemon(true);
            return t;
        });
    private final Map<NoteBytes, ScheduledFuture<?>> pendingRenders = new ConcurrentHashMap<>();
    
    // Terminal dimensions
    private volatile int termWidth;
    private volatile int termHeight;
    
    private Map<NoteBytes, MessageExecutor> m_msgExecMap = new HashMap<>();
    private Set<ContainerType> supportedTypes = Set.of(ContainerType.TERMINAL);
    
    public ConsoleUIRenderer() throws IOException {
        this.terminal = TerminalBuilder.builder()
            .system(true)
            .encoding("UTF-8")
            .build();
        
        this.originalAttributes = terminal.getAttributes();
        this.termWidth = terminal.getWidth();
        this.termHeight = terminal.getHeight();
        
        setupExecMap();
        
        Log.logMsg("[ConsoleUIRenderer] Terminal created: " + termWidth + "x" + termHeight);
    }

    @Override
    public CompletableFuture<Void> initialize() {
        if (active) {
            Log.logMsg("[ConsoleUIRenderer] Already initialized");
            return CompletableFuture.completedFuture(null);
        }
        
        active = true;
        
        // === TECHNIQUE 1: ALTERNATE SCREEN BUFFER ===
        // This is the single biggest anti-flicker improvement
        terminal.writer().print("\033[?1049h"); // Enter alternate buffer
        
        // === RAW MODE SETUP ===
        Attributes raw = new Attributes(originalAttributes);
        raw.setLocalFlag(Attributes.LocalFlag.ICANON, false);
        raw.setLocalFlag(Attributes.LocalFlag.ECHO, false);
        raw.setLocalFlag(Attributes.LocalFlag.ISIG, false);
        raw.setLocalFlag(Attributes.LocalFlag.IEXTEN, false);
        raw.setControlChar(Attributes.ControlChar.VMIN, 0);
        raw.setControlChar(Attributes.ControlChar.VTIME, 1);
        terminal.setAttributes(raw);
        
        // === INITIAL SETUP ===
        terminal.writer().print("\033[?25l"); // Hide cursor
        terminal.writer().print("\033[2J\033[H"); // Clear and home
        terminal.flush();
        
        Log.logMsg("[ConsoleUIRenderer] Initialized with alternate buffer");
        return CompletableFuture.completedFuture(null);
    }
    
    private void setupExecMap() {
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
        
        try {
            NoteBytes cmd = command.get(Keys.CMD);
            MessageExecutor msgExec = m_msgExecMap.get(cmd);
      
            if (msgExec != null) {
                msgExec.execute(command);
            } else {
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
        
        scheduleRender(buffer);
    }
    
    private void handleDestroyContainer(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        
        // Cancel any pending renders
        ScheduledFuture<?> pending = pendingRenders.remove(containerId);
        if (pending != null) {
            pending.cancel(false);
        }
        
        containers.remove(containerId);
        
        if (containerId.equals(focusedContainerId)) {
            focusedContainerId = null;
            clearScreen();
        }
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
            focusedContainerId = containerId;
            scheduleRender(buffer);
        }
    }
    
    private void handleMaximizeContainer(NoteBytesMap command) {
        handleFocusContainer(command);
    }
    
    private void handleRestoreContainer(NoteBytesMap command) {
        handleFocusContainer(command);
    }
    
    // ===== BATCH MODE =====
    
    private void handleBeginBatch(NoteBytesMap msg) {
        synchronized (batchedOperations) {
            batchMode = true;
            batchedOperations.clear();
        }
    }

    private void handleEndBatch(NoteBytesMap msg) {
        NoteBytes containerId = msg.get(Keys.CONTAINER_ID);
        ContainerBuffer buffer = containers.get(containerId);
        
        synchronized (batchedOperations) {
            // Execute all batched operations on the buffer
            for (Runnable op : batchedOperations) {
                op.run();
            }
            batchedOperations.clear();
            batchMode = false;
        }
        
        // Now render once (debounced)
        if (buffer != null) {
            scheduleRender(buffer);
        }
    }
    
    // ===== TERMINAL COMMANDS =====
    
    private void handleClear(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        ContainerBuffer buffer = containers.get(containerId);
        
        if (buffer != null) {
            Runnable op = () -> buffer.clear();
            executeOrBatch(op, buffer);
        }
    }
    
    private void handlePrintLn(NoteBytesMap command) {
        handlePrint(command, true);
    }
    
    private void handlePrint(NoteBytesMap command) {
        handlePrint(command, false);
    }
    
    private void handlePrint(NoteBytesMap command, boolean newline) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        NoteBytes textBytes = command.get(Keys.TEXT);
        NoteBytes styleBytes = command.get(Keys.STYLE);

        if (containerId != null && textBytes != null) {
            String text = textBytes.getAsString();
            TextStyle style = parseStyle(styleBytes);
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                Runnable op = () -> buffer.print(text, style, newline);
                executeOrBatch(op, buffer);
            }
        }
    }
    
    private void handlePrintAt(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        NoteBytes rowBytes = command.get(Keys.ROW);
        NoteBytes colBytes = command.get(Keys.COL);
        NoteBytes textBytes = command.get(Keys.TEXT);
        NoteBytes styleBytes = command.get(Keys.STYLE);

        if (containerId != null && rowBytes != null && colBytes != null && textBytes != null) {
            int row = rowBytes.getAsInt();
            int col = colBytes.getAsInt();
            String text = textBytes.getAsString();
            TextStyle style = parseStyle(styleBytes);
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                Runnable op = () -> buffer.printAt(row, col, text, style);
                executeOrBatch(op, buffer);
            }
        }
    }
    
    private void handleMoveCursor(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        NoteBytes rowBytes = command.get(Keys.ROW);
        NoteBytes colBytes = command.get(Keys.COL);

        if (containerId != null && rowBytes != null && colBytes != null) {
            int row = rowBytes.getAsInt();
            int col = colBytes.getAsInt();
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                Runnable op = () -> {
                    buffer.cursorRow = row;
                    buffer.cursorCol = col;
                };
                executeOrBatch(op, buffer);
            }
        }
    }
    
    private void handleShowCursor(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        if (containerId != null) {
            ContainerBuffer buffer = containers.get(containerId);
            
            if (buffer != null) {
                Runnable op = () -> buffer.cursorVisible = true;
                executeOrBatch(op, buffer);
            }
        }
    }
    
    private void handleHideCursor(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        if (containerId != null) {
            ContainerBuffer buffer = containers.get(containerId);
            
            if (buffer != null) {
                Runnable op = () -> buffer.cursorVisible = false;
                executeOrBatch(op, buffer);
            }
        }
    }
    
    private void handleClearLine(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        if (containerId != null) {
            ContainerBuffer buffer = containers.get(containerId);
            
            if (buffer != null) {
                Runnable op = () -> buffer.clearLine(buffer.cursorRow);
                executeOrBatch(op, buffer);
            }
        }
    }
    
    private void handleClearLineAt(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        NoteBytes rowBytes = command.get(Keys.ROW);

        if (containerId != null && rowBytes != null) {
            int row = rowBytes.getAsInt();
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                Runnable op = () -> buffer.clearLine(row);
                executeOrBatch(op, buffer);
            }
        }
    }
    
    private void handleClearRegion(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        NoteBytes startRowBytes = command.get(TerminalCommands.START_ROW);
        NoteBytes startColBytes = command.get(TerminalCommands.START_COL);
        NoteBytes endRowBytes = command.get(TerminalCommands.END_ROW);
        NoteBytes endColBytes = command.get(TerminalCommands.END_COL);

        if (containerId != null && startRowBytes != null && startColBytes != null
            && endRowBytes != null && endColBytes != null) {
            int startRow = startRowBytes.getAsInt();
            int startCol = startColBytes.getAsInt();
            int endRow = endRowBytes.getAsInt();
            int endCol = endColBytes.getAsInt();
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                Runnable op = () -> buffer.clearRegion(startRow, startCol, endRow, endCol);
                executeOrBatch(op, buffer);
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

        if (containerId != null && startRowBytes != null && startColBytes != null && 
            widthBytes != null && heightBytes != null && boxStyleBytes != null) {
            
            int startRow = startRowBytes.getAsInt();
            int startCol = startColBytes.getAsInt();
            int width = widthBytes.getAsInt();
            int height = heightBytes.getAsInt();
            String title = titleBytes != null ? titleBytes.getAsString() : "";
            String boxStyleStr = boxStyleBytes.getAsString();
            
            BoxStyle boxStyle = BoxStyle.valueOf(boxStyleStr);
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                Runnable op = () -> buffer.drawBox(startRow, startCol, width, height, title, boxStyle);
                executeOrBatch(op, buffer);
            }
        }
    }
    
    private void handleDrawHLine(NoteBytesMap command) {
        NoteBytes containerId = command.get(Keys.CONTAINER_ID);
        NoteBytes rowBytes = command.get(Keys.ROW);
        NoteBytes startColBytes = command.get(TerminalCommands.START_COL);
        NoteBytes lengthBytes = command.get(Keys.LENGTH);
        
        if (containerId != null && rowBytes != null && startColBytes != null && lengthBytes != null) {
            int row = rowBytes.getAsInt();
            int startCol = startColBytes.getAsInt();
            int length = lengthBytes.getAsInt();
            
            ContainerBuffer buffer = containers.get(containerId);
            if (buffer != null) {
                Runnable op = () -> buffer.drawHLine(row, startCol, length);
                executeOrBatch(op, buffer);
            }
        }
    }
    
    // ===== BATCH EXECUTION =====
    
    private void executeOrBatch(Runnable operation, ContainerBuffer buffer) {
        synchronized (batchedOperations) {
            if (batchMode) {
                batchedOperations.add(operation);
            } else {
                operation.run();
                scheduleRender(buffer);
            }
        }
    }
    
    // ===== RENDERING =====
    
    /**
     * === TECHNIQUE 5: DEBOUNCED RENDERING ===
     * Schedule a render, cancelling any pending render for this container
     */
    private void scheduleRender(ContainerBuffer buffer) {
        NoteBytes id = buffer.id;
        
        // Cancel pending render
        ScheduledFuture<?> pending = pendingRenders.get(id);
        if (pending != null && !pending.isDone()) {
            pending.cancel(false);
        }
        
        // Schedule new render
        ScheduledFuture<?> future = renderScheduler.schedule(
            () -> renderContainer(buffer),
            5, // 5ms debounce
            TimeUnit.MILLISECONDS
        );
        
        pendingRenders.put(id, future);
    }
    
    /**
     * === TECHNIQUE 2 & 3: DIFFERENTIAL RENDERING ===
     * Only update cells that changed since last frame
     */
    private void renderContainer(ContainerBuffer buffer) {
        if (!buffer.id.equals(focusedContainerId)) return;
        
        // === TECHNIQUE 4: RATE LIMITING ===
        long now = System.currentTimeMillis();
        long elapsed = now - lastRenderTime;
        if (elapsed < MIN_RENDER_INTERVAL_MS) {
            // Too soon, reschedule
            renderScheduler.schedule(
                () -> renderContainer(buffer),
                MIN_RENDER_INTERVAL_MS - elapsed,
                TimeUnit.MILLISECONDS
            );
            return;
        }
        lastRenderTime = now;
        
        try {
            // Pre-allocate string builder (avoid reallocations)
            StringBuilder updates = new StringBuilder(4096);
            
            // Hide cursor during update
            updates.append("\033[?25l");
            
            // === DIFFERENTIAL RENDERING ===
            // Only emit codes for changed cells
            TextStyle currentStyle = new TextStyle();
            int consecutiveUnchanged = 0;
            
            for (int row = 0; row < buffer.rows; row++) {
                for (int col = 0; col < buffer.cols; col++) {
                    Cell current = buffer.getCell(row, col);
                    Cell previous = buffer.getPrevCell(row, col);
                    
                    if (current.equals(previous)) {
                        consecutiveUnchanged++;
                        continue;
                    }
                    
                    // Cell changed - position cursor
                    updates.append(String.format("\033[%d;%dH", row + 1, col + 1));
                    
                    // Update style if changed
                    if (!current.style.equals(currentStyle)) {
                        updates.append("\033[0m");
                        appendStyleCodes(updates, current.style);
                        currentStyle = current.style;
                    }
                    
                    // Write character
                    updates.append(current.character != '\0' ? current.character : ' ');
                    
                    consecutiveUnchanged = 0;
                }
            }
            
            // Reset style
            updates.append("\033[0m");
            
            // Position and show cursor if visible
            if (buffer.cursorVisible) {
                updates.append(String.format("\033[%d;%dH", 
                    buffer.cursorRow + 1, buffer.cursorCol + 1));
                updates.append("\033[?25h");
            }
            
            // === ATOMIC WRITE ===
            terminal.writer().write(updates.toString());
            terminal.flush();
            
            // Swap buffers for next frame
            buffer.swapBuffers();
            
        } catch (Exception e) {
            Log.logError("[ConsoleUIRenderer] Render error: " + e.getMessage());
        }
    }
    
    private void clearScreen() {
        terminal.writer().print("\033[2J\033[H");
        terminal.flush();
    }
    
    private void appendStyleCodes(StringBuilder sb, TextStyle style) {
        if (style.bold) sb.append("\033[1m");
        if (style.underline) sb.append("\033[4m");
        if (style.inverse) sb.append("\033[7m");
        
        if (style.foreground != Color.DEFAULT) {
            sb.append("\033[").append(getColorCode(style.foreground, false)).append("m");
        }
        
        if (style.background != Color.DEFAULT) {
            sb.append("\033[").append(getColorCode(style.background, true)).append("m");
        }
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
            style.inverse = inverse.getAsBoolean();
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
        
        // Cancel all pending renders
        renderScheduler.shutdown();
        try {
            renderScheduler.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        try {
            // Show cursor
            terminal.writer().print("\033[?25h");
            
            // === EXIT ALTERNATE BUFFER ===
            terminal.writer().print("\033[?1049l");
            
            // Restore attributes
            terminal.setAttributes(originalAttributes);
            
            terminal.flush();
            terminal.close();
            
            Log.logMsg("[ConsoleUIRenderer] Shutdown complete");
        } catch (Exception e) {
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
        private final Cell[][] prevCells;
        
        int cursorRow = 0;
        int cursorCol = 0;
        boolean cursorVisible = true;
        boolean visible = true;
        
        ContainerBuffer(NoteBytes id, NoteBytes title, int cols, int rows) {
            this.id = id;
            this.title = title;
            this.rows = rows;
            this.cols = cols;
            
            // Allocate both buffers
            this.cells = new Cell[rows][cols];
            this.prevCells = new Cell[rows][cols];
            
            // Initialize both buffers
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    cells[r][c] = new Cell();
                    prevCells[r][c] = new Cell();  // Initialize previous buffer
                }
            }
        }


        /**
         * Get cell from previous frame (for differential rendering)
         */
        Cell getPrevCell(int row, int col) {
            if (row < 0 || row >= rows || col < 0 || col >= cols) {
                return new Cell();
            }
            return prevCells[row][col];
        }

        /**
         * Swap buffers after rendering
         * Copy current frame to previous frame for next differential render
         */
        void swapBuffers() {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    prevCells[r][c].copyFrom(cells[r][c]);
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

        void copyFrom(Cell other) {
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
    
    private static class TextStyle {
        Color foreground = Color.DEFAULT;
        Color background = Color.DEFAULT;
        boolean bold = false;
        boolean inverse = false;
        boolean underline = false;

        TextStyle copy(){
            TextStyle textStyle = new TextStyle();
            textStyle.foreground = this.foreground;
            textStyle.background = this.background;
            textStyle.bold = this.bold;
            textStyle.inverse = this.inverse;
            textStyle.underline = this.underline;
            return textStyle;
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