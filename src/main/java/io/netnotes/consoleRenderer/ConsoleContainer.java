package io.netnotes.consoleRenderer;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.ui.Point2D;
import io.netnotes.engine.ui.Position;
import io.netnotes.engine.ui.TextAlignment;
import io.netnotes.engine.ui.containers.Container;
import io.netnotes.engine.ui.containers.ContainerCommands;
import io.netnotes.engine.ui.containers.ContainerId;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.LoggingHelpers.LogLevel;
import io.netnotes.terminal.StyleConstants;
import io.netnotes.terminal.TerminalCommands;
import io.netnotes.terminal.TerminalContainerConfig;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRectanglePool;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

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
public class ConsoleContainer extends Container<
    Point2D,
    TerminalRectangle,
    TerminalContainerConfig,
    ConsoleContainer
> {
    private static final LogLevel LOG_LEVEL = LogLevel.IMPORTANT;

    private final TerminalRectanglePool regionPool;
    private TerminalRectangle contentBounds;
    private TerminalRectangle prevContentBounds;

    // Cell buffers (indexed as [y][x] for natural row-major ordering)
    private Cell[][] cells;
    private Cell[][] prevCells;
    private boolean fullRepaintPending = false;
    private final AtomicBoolean boundsChangedPending = new AtomicBoolean(false);
    private TerminalRectangle[] pendingDamageRects = null;

    // Cursor state (using x,y coordinates)
    private int cursorX = 0;
    private int cursorY = 0;

    /**
     * CURSOR STATE MODEL
     *
     * cursorDesired  — what the container/component *wants*.
     *                  Set by handleShowCursor / handleHideCursor.
     *                  Survives focus loss; intent is remembered.
     *                  Default true so a freshly-focused container shows a cursor
     *                  until a component explicitly hides it.
     *
     * effectiveCursorVisible() — cursorDesired && isFocused().
     *                  Only the focused container should ever claim the physical cursor.
     *                  Exposed via getRenderableState() so the renderer never has to
     *                  know about focus — it just renders what the state says.
     */
    private boolean cursorDesired = true;
   
    /**
     * Constructor
     */
    public ConsoleContainer(
        ContainerId id,
        String title,
        ContextPath ownerPath,
        TerminalContainerConfig config,
        String rendererId,
        TerminalRectanglePool pool
    ) {
        super(id, title, ownerPath, config, rendererId);
        this.regionPool = pool;
        TerminalRectangle initialRegion = config.initialRegion();
        if (initialRegion == null) {
            Log.logError("[ConsoleContainer] initialRegion is null, using minimum bounds");
            initialRegion = pool.obtain();
            initialRegion.set(0, 0, ConsoleContainerLayoutManager.MIN_COL_WIDTH, ConsoleContainerLayoutManager.MIN_ROW_HEIGHT);
        }
        this.contentBounds = pool.obtain();
        this.contentBounds.set(initialRegion);

        this.prevContentBounds = pool.obtain();
        this.prevContentBounds.copyFrom(initialRegion);

        this.allocatedBounds = pool.obtain();
        this.allocatedBounds.copyFrom(initialRegion);

        rebuildBufferSize();
    }

    
    // ===== MESSAGE MAP SETUP =====
    
    @Override
    protected void setupMessageMap() {
        // Individual terminal commands
        msgMap.put(TerminalCommands.TERMINAL_MOVE_CURSOR, this::handleMoveCursor);
        msgMap.put(TerminalCommands.TERMINAL_SHOW_CURSOR, this::handleShowCursor);
        msgMap.put(TerminalCommands.TERMINAL_HIDE_CURSOR, this::handleHideCursor);
    }

    @Override
    protected void setupBatchMsgMap(){
        batchMsgMap.put(TerminalCommands.TERMINAL_CLEAR, cmd->clearInternal());
        batchMsgMap.put(TerminalCommands.TERMINAL_PRINT, (cmd)->executePrintInternal(cmd, false));
        batchMsgMap.put(TerminalCommands.TERMINAL_PRINTLN, (cmd)->executePrintInternal(cmd, true));
        batchMsgMap.put(TerminalCommands.TERMINAL_PRINT_AT, this::executePrintAtInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_PRINT_CODEPOINT_AT, this::executePrintCodePointAtInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_MOVE_CURSOR, this::executeMoveCursorInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_SHOW_CURSOR, (cmd)->{ cursorDesired = true; });
        batchMsgMap.put(TerminalCommands.TERMINAL_HIDE_CURSOR, (cmd)->{ cursorDesired = false; });
        batchMsgMap.put(TerminalCommands.TERMINAL_CLEAR_LINE, (cmd)->clearLineInternal(cursorY));
        batchMsgMap.put(TerminalCommands.TERMINAL_CLEAR_LINE_AT, this::executeClearLineAtInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_CLEAR_REGION, this::executeClearRegionInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_BOX, this::executeDrawBoxInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_HLINE, this::executeDrawHLineInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_VLINE, this::executeDrawVLineInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_FILL_REGION, this::executeFillRegionInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_BORDERED_TEXT, this::executeDrawBorderedTextInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_PANEL, this::executeDrawPanelInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_BUTTON, this::executeDrawButtonInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_PROGRESS_BAR, this::executeDrawProgressBarInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_TEXT_BLOCK, this::executeDrawTextBlockInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_SHADE_REGION, this::executeShadeRegionInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_TABLE_BORDER, this::executeDrawTableBorderInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_SPARKLINE,      this::executeDrawSparklineInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_SCROLLBAR,      this::executeDrawScrollbarInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_BITMAP,         this::executeDrawBitmapInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_BRAILLE_BITMAP, this::executeDrawBrailleBitmapInternal);
        batchMsgMap.put(TerminalCommands.TERMINAL_DRAW_SEXTANT_BITMAP, this::executeDrawSextantBitmapInternal);
    }

    public boolean hasBoundsChangedPending() { return boundsChangedPending.get(); }

    @Override
    protected void setupStateTransitions() {
        // Container-specific state transitions can go here
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    protected CompletableFuture<Void> initializeRenderer() {
        Log.logMsg("[ConsoleContainer] Renderer initialized: " + id, LogLevel.GENERAL);
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== RENDER STATE ACCESS =====
    
    /**
     * Get renderable state snapshot (PULL-BASED)
     * Called by renderer when it wants to render
     */
    public CompletableFuture<RenderableState> getRenderableState() {
        if(containerExecutor.isCurrentThread()){
            return CompletableFuture.completedFuture(createStateSnapshot());
        }else{
            return containerExecutor.submit(() -> {
              return createStateSnapshot();
            });
        }
        
    }

    private RenderableState createStateSnapshot() {
        int h = getHeight(), w = getWidth();
        boolean boundsChanged = boundsChangedPending.get();

        // Consume both pending flags atomically on the container thread
        boolean repaint = fullRepaintPending || boundsChanged;
        fullRepaintPending = false;

        TerminalRectangle[] snapDamage = pendingDamageRects;
        pendingDamageRects = null;

        Cell[][] snapCells = new Cell[h][w];
        Cell[][] snapPrev = repaint ? null : new Cell[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                snapCells[y][x] = new Cell();
                if (repaint) {
                    snapCells[y][x].copyFrom(cells[y][x]);
                } else {
                    Cell current = cells[y][x];
                    // Damaged blank cell must snapshot as explicit space so the renderer
                    // physically clears that terminal position rather than skipping it.
                    if (current.isBlank() && prevCells[y][x].isForceRepaint()) {
                        snapCells[y][x].set(' ', new TextStyle());
                    } else {
                        snapCells[y][x].copyFrom(current);
                    }
                    snapPrev[y][x] = new Cell();
                    snapPrev[y][x].copyFrom(prevCells[y][x]);
                }
            }
        }

        return new RenderableState(
            h, w, allocatedBounds.getX(), allocatedBounds.getY(),
            cursorY, cursorX,
            effectiveCursorVisible(),
            snapDamage,
            snapCells, snapPrev
        );
    }


    /** Cursor is only physically visible when this container is focused and the component wants it. */
    private boolean effectiveCursorVisible() {
        return cursorDesired && isFocused();
    }

    private void setBoundsStates(boolean isManaged, boolean isOffScreen){
        if (isManaged) {
            stateMachine.addState(Container.STATE_LAYOUT_MANAGED);
        } else {
            stateMachine.removeState(Container.STATE_LAYOUT_MANAGED);
        }
        if (isOffScreen) {
            stateMachine.addState(Container.STATE_OFF_SCREEN);
        } else {
            stateMachine.removeState(Container.STATE_OFF_SCREEN);
        }
    }

    public  CompletableFuture<Void> setAllocatedBounds(int x, int y, int width, int height, boolean isManaged, boolean isOffScreen){
        return containerExecutor.execute(()->{
            setBoundsStates(isManaged, isOffScreen);
            TerminalRectangle region = regionPool.obtain();
            region.set(x, y, width, height);
            setAllocatedBoundsInternal(region);
        });
    }

    public CompletableFuture<Void> setAllocatedBoundsOffScreen(boolean isManaged, boolean isOffScreen){
        return containerExecutor.execute(()->{
            setBoundsStates(isManaged, isOffScreen);
            TerminalRectangle region = regionPool.obtain();
            region.copyFrom(allocatedBounds);
            setAllocatedBoundsInternal(region);
        });
    }

    @Override
    public CompletableFuture<Void> setAllocatedBounds(TerminalRectangle region){
        return containerExecutor.execute(()->{
            setAllocatedBoundsInternal(region);
        });
    }

  

    public boolean isBoundsManaged(){
        return stateMachine.hasState(Container.STATE_LAYOUT_MANAGED);
    }

    public boolean isOffScreen(){
        return stateMachine.hasState(Container.STATE_OFF_SCREEN);
    }

    private void setAllocatedBoundsInternal(TerminalRectangle region){
        boundsChangedPending.set(true);
        allocatedBounds.copyFrom(region);

        boolean isBoundsManaged = stateMachine.hasState(Container.STATE_LAYOUT_MANAGED);
        boolean isOffScreen = stateMachine.hasState(Container.STATE_OFF_SCREEN);

        if(isBoundsManaged){
            handleContentBoundsInternal(region);
        }
        region.setPosition(0, 0);
        NoteBytesMap resizeEvent = ContainerCommands.containerRegionChanged(
            id.toNoteBytes(), 
            region.toNoteBytes(),
            isBoundsManaged,
            isOffScreen
        );
        emitEvent(resizeEvent);
        regionPool.recycle(region);
    }


    /**
     * Invalidates prevCells so the next differential render treats every cell 
     * as changed. Called after an external screen clear (e.g. terminal resize)
     * to force a full repaint without waiting for a new batch from the client.
     */
    public CompletableFuture<Void> invalidateRenderCache() {
        return containerExecutor.execute(() -> {
            int height = contentBounds.getHeight();
            int width  = contentBounds.getWidth();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    prevCells[y][x].markForceRepaint();
                }
            }
        });
    }

     


    @Override
    protected TerminalRectangle[] handleBatchDamageRegions(NoteBytes damageRegionBytes) {
        if(damageRegionBytes == null) return new TerminalRectangle[0];

        if(damageRegionBytes.getType() != NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE){
            Throwable ex = new IllegalArgumentException("damageRegionBytes array expected");
            Log.logError("[ConsoleContainer:"+ getId() + "] handleBatchDamageRegions", ex);
            throw new RuntimeException(ex);
        }

        return damageRegionBytes
            .getAsNoteBytesArrayReadOnly()
            .getAsReadOnlyStream()
            .map(TerminalRectangle::fromNoteBytes)
            .toArray(TerminalRectangle[]::new);

    }

    @Override
    protected TerminalRectangle handleBatchContentBounds(NoteBytes contentBoundsBytes){
        if(contentBoundsBytes == null){
            return null;
        }
        return TerminalRectangle.fromNoteBytes(
            contentBoundsBytes, 
            regionPool
        );
    }

    @Override
    protected void handleBatchBounds(
        TerminalRectangle newContentBounds,
        TerminalRectangle[] damageRegions
    ){
        if(isBoundsManaged()){
            newContentBounds.copyFrom(allocatedBounds);
        }
        pendingDamageRects = damageRegions;
        if(!newContentBounds.equals(contentBounds)){
            handleContentBoundsInternal(newContentBounds);
        }else{
            invalidateDamageRegions(damageRegions);
        }
        regionPool.recycle(newContentBounds);
    }


    private void invalidateDamageRegions(TerminalRectangle[] damageRegions) {
        if(damageRegions == null) return;
        int height = contentBounds.getHeight();
        int width  = contentBounds.getWidth();
        
        for (TerminalRectangle damage : damageRegions) {
            if (damage == null) continue;
            
            int startY = Math.max(0, damage.getY());
            int endY   = Math.min(height, damage.getY() + damage.getHeight());
            int startX = Math.max(0, damage.getX());
            int endX   = Math.min(width,  damage.getX() + damage.getWidth());
            
            if (endY <= startY || endX <= startX) {
                continue;
            }
            
            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {
                    cells[y][x].clear();
                    prevCells[y][x].markForceRepaint();
                }
            }
        }
    }

    private void handleContentBoundsInternal(TerminalRectangle newContentBounds) {
        prevContentBounds.copyFrom(contentBounds);
        contentBounds.copyFrom(newContentBounds);
        rebuildBufferSize();
    }


    /**
     * Rebuild clean buffer for complete render
     * @param width
     * @param height
     */
    private void rebuildBufferSize() {
        int newWidth  = contentBounds.getWidth();
        int newHeight = contentBounds.getHeight();

        Cell[][] newCells     = new Cell[newHeight][newWidth];
        Cell[][] newPrevCells = new Cell[newHeight][newWidth];

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                newCells[y][x]     = new Cell();
                newPrevCells[y][x] = new Cell();
                newPrevCells[y][x].markForceRepaint();
            }
        }

        this.cells     = newCells;
        this.prevCells = newPrevCells;
        this.fullRepaintPending = true;
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
    
  

    @Override
    protected void onBatchComplete() {
        requestRenderInternal();
    }
    
    // ===== INDIVIDUAL COMMAND HANDLERS =====
    

 
    private CompletableFuture<Void> handleMoveCursor(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            executeMoveCursorInternal(command);
            requestRenderInternal();
        });
    }
    
    private CompletableFuture<Void> handleShowCursor(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            cursorDesired = true;
            // Only worth re-rendering for cursor state change if we're the focused container.
            // If not focused, the desired state is stored and will take effect on focus gain.
            if (isFocused()) requestRenderInternal();
        });
    }
    
    private CompletableFuture<Void> handleHideCursor(NoteBytesMap command) {
        return containerExecutor.execute(() -> {
            cursorDesired = false;
            if (isFocused()) requestRenderInternal();
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

    private void executePrintCodePointAtInternal(NoteBytesMap cmd) {
        NoteBytes xBytes = cmd.get(Keys.X);
        NoteBytes yBytes = cmd.get(Keys.Y);
        NoteBytes cpBytes = cmd.get(TerminalCommands.CODE_POINT);
        NoteBytes styleBytes = cmd.get(Keys.STYLE);

        if (xBytes == null || yBytes == null || cpBytes == null) return;

        int x = xBytes.getAsInt();
        int y = yBytes.getAsInt();
        int codePoint = cpBytes.getAsInt();
        TextStyle style = parseStyle(styleBytes);

        printCodePointAtInternal(x, y, codePoint, style);
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
        NoteBytes styleBytes = cmd.get(Keys.STYLE);
        if (regionBytes == null) return;
        TextStyle textStyle = styleBytes != null ?  TextStyle.fromNoteBytes(styleBytes) : new TextStyle(); 
        
        TerminalRectangle region = TerminalRectangle.fromNoteBytes(regionBytes);
        TerminalRectangle renderRegion = renderRegionBytes != null ? TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;
        
        String title = cmd.getAsString(Keys.TITLE, null);
        String titlePosStr = cmd.getAsString(TerminalCommands.TITLE_POS, "TOP_CENTER");
        Position titlePos = Position.valueOf(titlePosStr);
        String boxStyleName = cmd.getAsString(TerminalCommands.LINE_STYLE, "SINGLE");
        LineStyle boxStyle = LineStyle.valueOf(boxStyleName);
        
        if(renderRegion != null){
            drawBoxInternal(region, renderRegion, title, titlePos, boxStyle, textStyle);
        }else{
            drawBoxInternal(region.getX(), region.getY(), region.getWidth(), region.getHeight(), title, titlePos, textStyle, boxStyle);
            regionPool.recycle(region);
        }
    }
        
    private void executeDrawHLineInternal(NoteBytesMap cmd) {
        NoteBytes xBytes = cmd.get(Keys.X);
        NoteBytes yBytes = cmd.get(Keys.Y);
        NoteBytes lengthBytes = cmd.get(Keys.LENGTH);
        NoteBytes styleBytes = cmd.get(Keys.STYLE);
        NoteBytes lineStyleBytes = cmd.get(TerminalCommands.LINE_STYLE);

        if (xBytes == null || yBytes == null || lengthBytes == null) return;
        
        TextStyle style = styleBytes != null ? TextStyle.fromNoteBytes(styleBytes) : new TextStyle();
        LineStyle lineStyle = lineStyleBytes != null ? LineStyle.valueOf(lineStyleBytes.getAsString()) : LineStyle.SINGLE;
        
        drawHLineInternal(
            xBytes.getAsInt(),
            yBytes.getAsInt(),
            lengthBytes.getAsInt(),
            style, 
            lineStyle
        );
    }
    
    private void executeDrawVLineInternal(NoteBytesMap cmd) {
        NoteBytes xBytes = cmd.get(Keys.X);
        NoteBytes yBytes = cmd.get(Keys.Y);
        NoteBytes lengthBytes = cmd.get(Keys.LENGTH);
        NoteBytes styleBytes = cmd.get(Keys.STYLE);
        NoteBytes lineStyleBytes = cmd.get(TerminalCommands.LINE_STYLE);

        if (xBytes == null || yBytes == null || lengthBytes == null) return;
        TextStyle style = styleBytes != null ? TextStyle.fromNoteBytes(styleBytes) : new TextStyle();
        LineStyle lineStyle = lineStyleBytes != null ? LineStyle.valueOf(lineStyleBytes.getAsString()) : LineStyle.SINGLE;

        drawVLineInternal(
            xBytes.getAsInt(),
            yBytes.getAsInt(),
            lengthBytes.getAsInt(),
            style,
            lineStyle
        );
    }

    private void executeDrawSparklineInternal(NoteBytesMap cmd) {
        NoteBytes regionBytes = cmd.get(Keys.REGION);
        if (regionBytes == null) return;

        NoteBytes renderRegionBytes = cmd.get(TerminalCommands.RENDER_REGION);
        TerminalRectangle region       = TerminalRectangle.fromNoteBytes(regionBytes);
        TerminalRectangle renderRegion = renderRegionBytes != null
            ? TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;

        int[] raw = readNoteIntArray(cmd.get(Keys.DATA));
        float[] values = new float[raw.length];
        for (int i = 0; i < raw.length; i++) values[i] = Float.intBitsToFloat(raw[i]);

        TextStyle style     = parseStyle(cmd.get(Keys.STYLE));
        TextStyle peakStyle = parseStyle(cmd.get(TerminalCommands.PEAK_STYLE));

        drawSparklineInternal(region, renderRegion, values, style, peakStyle);

        regionPool.recycle(region);
        if (renderRegion != null) regionPool.recycle(renderRegion);
    }
    
    private void executeFillRegionInternal(NoteBytesMap cmd) {
        NoteBytes regionBytes      = cmd.get(Keys.REGION);
        NoteBytes renderRegionBytes = cmd.get(TerminalCommands.RENDER_REGION);
        NoteBytes codePointBytes   = cmd.get(TerminalCommands.CODE_POINT);
        NoteBytes styleBytes       = cmd.get(Keys.STYLE);

        if (regionBytes == null || codePointBytes == null) return;

        TerminalRectangle region       = TerminalRectangle.fromNoteBytes(regionBytes.getAsNoteBytesMap());
        TerminalRectangle renderRegion = renderRegionBytes != null
            ? TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;
        int codePoint = codePointBytes.getAsInt();
        TextStyle style = parseStyle(styleBytes);

        // When a renderRegion is present use it as the effective draw area,
        // not the full logical region — this is what the clip intersection produced
        TerminalRectangle drawTarget = renderRegion != null ? renderRegion : region;
        fillRegionInternal(drawTarget, codePoint, style);

        regionPool.recycle(region);
        if (renderRegion != null) regionPool.recycle(renderRegion);
    }

    private void executeDrawScrollbarInternal(NoteBytesMap cmd) {
        NoteBytes regionBytes = cmd.get(Keys.REGION);
        if (regionBytes == null) return;

        NoteBytes renderRegionBytes = cmd.get(TerminalCommands.RENDER_REGION);
        TerminalRectangle region       = TerminalRectangle.fromNoteBytes(regionBytes);
        TerminalRectangle renderRegion = renderRegionBytes != null
            ? TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;

        int     scrollPos    = cmd.getAsInt(TerminalCommands.SCROLL_POS,    0);
        int     totalItems   = cmd.getAsInt(Keys.ITEM_COUNT,                1);
        int     visibleItems = cmd.getAsInt(Keys.VISIBLE_ITEMS,             1);
        boolean showArrows   = cmd.getAsBoolean(TerminalCommands.SHOW_ARROWS, false);
        TextStyle trackStyle = parseStyle(cmd.get(TerminalCommands.TRACK_STYLE));
        TextStyle thumbStyle = parseStyle(cmd.get(TerminalCommands.THUMB_STYLE));

        drawScrollbarInternal(region, renderRegion,
            scrollPos, totalItems, visibleItems, showArrows, trackStyle, thumbStyle);

        regionPool.recycle(region);
        if (renderRegion != null) regionPool.recycle(renderRegion);
    }

    private void executeDrawBitmapInternal(NoteBytesMap cmd) {
        NoteBytes regionBytes = cmd.get(Keys.REGION);
        if (regionBytes == null) return;

        NoteBytes renderRegionBytes = cmd.get(TerminalCommands.RENDER_REGION);
        TerminalRectangle region       = TerminalRectangle.fromNoteBytes(regionBytes);
        TerminalRectangle renderRegion = renderRegionBytes != null
            ? TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;

        int  pixelW  = cmd.getAsInt(Keys.WIDTH,  0);
        int  pixelH  = cmd.getAsInt(Keys.HEIGHT, 0);
        byte[] pixels = cmd.getAsByteArray(Keys.DATA, null);
        TextStyle style = parseStyle(cmd.get(Keys.STYLE));

        if (pixelW <= 0 || pixelH <= 0 || pixels == null) return;
        drawBitmapInternal(region, renderRegion, pixelW, pixelH, pixels, style);

        regionPool.recycle(region);
        if (renderRegion != null) regionPool.recycle(renderRegion);
    }

  

    private void executeDrawBrailleBitmapInternal(NoteBytesMap cmd) {
        NoteBytes regionBytes = cmd.get(Keys.REGION);
        if (regionBytes == null) return;

        NoteBytes renderRegionBytes = cmd.get(TerminalCommands.RENDER_REGION);
        TerminalRectangle region       = TerminalRectangle.fromNoteBytes(regionBytes);
        TerminalRectangle renderRegion = renderRegionBytes != null
            ? TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;

        int    pixelW  = cmd.getAsInt(Keys.WIDTH,  0);
        int    pixelH  = cmd.getAsInt(Keys.HEIGHT, 0);
        byte[] pixels  = cmd.getAsByteArray(Keys.DATA, null);
        TextStyle style = parseStyle(cmd.get(Keys.STYLE));

        if (pixelW <= 0 || pixelH <= 0 || pixels == null) return;
        drawBrailleBitmapInternal(region, renderRegion, pixelW, pixelH, pixels, style);

        regionPool.recycle(region);
        if (renderRegion != null) regionPool.recycle(renderRegion);
    }

    private void executeDrawSextantBitmapInternal(NoteBytesMap cmd) {
        NoteBytes regionBytes = cmd.get(Keys.REGION);
        if (regionBytes == null) return;

        NoteBytes renderRegionBytes = cmd.get(TerminalCommands.RENDER_REGION);
        TerminalRectangle region       = TerminalRectangle.fromNoteBytes(regionBytes);
        TerminalRectangle renderRegion = renderRegionBytes != null
            ? TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;

        int    pixelW  = cmd.getAsInt(Keys.WIDTH,  0);
        int    pixelH  = cmd.getAsInt(Keys.HEIGHT, 0);
        byte[] pixels  = cmd.getAsByteArray(Keys.DATA, null);
        TextStyle style = parseStyle(cmd.get(Keys.STYLE));

        if (pixelW <= 0 || pixelH <= 0 || pixels == null) return;
        drawSextantBitmapInternal(region, renderRegion, pixelW, pixelH, pixels, style);

        regionPool.recycle(region);
        if (renderRegion != null) regionPool.recycle(renderRegion);
    }

    
    
    // ===== LOW-LEVEL DRAWING OPERATIONS =====
    
    private void clearInternal() {
        Log.logMsg("[ConsoleContainer] CLEAR executing", LOG_LEVEL);

        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                cells[y][x].clear();
            }
        }

        cursorX = 0;
        cursorY = 0;
    }
    
    private void printInternal(String text, TextStyle style, boolean newline) {
        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();
        int offset = 0;
        while (offset < text.length()) {
            if (cursorY >= height) break;
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp == '\n' || cursorX >= width) {
                cursorY++;
                cursorX = 0;
                if (cp == '\n') continue;
            }
            cells[cursorY][cursorX].set(cp, style);
            int dw = cells[cursorY][cursorX].getDisplayWidth();
            if (dw == 2 && cursorX + 1 < width) {
                cells[cursorY][cursorX + 1].setAsContinuation();
            }
            cursorX += dw;
        }
        if (newline) {
            cursorY++;
            cursorX = 0;
        }
    }
    
    private void printAtInternal(int x, int y, String text, TextStyle style) {
        Log.logMsg("[ConsoleContainer] printAt:" + x + "," + y + Cell.SPACE_STR + text, LOG_LEVEL);
        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();
        
        if (y < 0 || y >= height) return;
        
        int printX = x;
        int offset = 0;

        while (offset < text.length() && printX < width) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            if (printX < 0) {
                printX += Cell.computeDisplayWidth(cp);
                continue;
            }
            cells[y][printX].set(cp, style);
            int dw = cells[y][printX].getDisplayWidth();
            if (dw == 2 && printX + 1 < width) {
                cells[y][printX + 1].setAsContinuation();
            }
            printX += dw;
        }
    }

    private void printCodePointAtInternal(int x, int y, int codePoint, TextStyle style) {
        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();
        if (y < 0 || y >= height) return;
        if (x < 0 || x >= width) return;

        TextStyle useStyle = style != null ? style : new TextStyle();
        int dw = Cell.computeDisplayWidth(codePoint);

        cells[y][x].set(codePoint, useStyle);

        if (dw == 2) {
            if (x + 1 < width) {
                cells[y][x + 1].setAsContinuation();
            }
        } else {
            if (x + 1 < width && cells[y][x + 1].isContinuation()) {
                cells[y][x + 1].clear();
            }
        }
    }
    
    private void clearLineInternal(int y) {
        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();

        if (y < 0 || y >= height) return;
        
        for (int x = 0; x < width; x++) {
            cells[y][x].clear();
        }
    }
    
    private void clearRegionInternal(TerminalRectangle region) {
        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();

        int startX = Math.max(contentBounds.getX(), region.getX());
        int startY = Math.max(contentBounds.getY(), region.getY());
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
        TextStyle textStyle,
        LineStyle style
    ) {
        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();

        if (x < 0 || y < 0 || x + boxWidth > width || y + boxHeight > height) return;
        if (boxWidth < 2 || boxHeight < 2) return;
        
        char[] chars = style.getChars();
        
        // Top border
        printAtInternal(x, y, String.valueOf(chars[2]), textStyle);
        for (int i = 1; i < boxWidth - 1; i++) {
            printAtInternal(x + i, y, String.valueOf(chars[0]), textStyle);
        }
        printAtInternal(x + boxWidth - 1, y, String.valueOf(chars[3]), textStyle);
        
        // Title positioning
        if (title != null && !title.isEmpty()) {
            int titleX = calculateTitleX(x, boxWidth, title, titlePos);
            int titleY = calculateTitleY(y, boxHeight, titlePos);
            
            if (titleX >= x && titleX + title.length() + 2 <= x + boxWidth) {
                printAtInternal(titleX, titleY, Cell.SPACE_STR + title + Cell.SPACE_STR, textStyle);
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
                            String title, Position titlePos, LineStyle style, TextStyle textStyle) {
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
                int ch = cx == x ? chars[2] : ((cx == x + boxWidth - 1) ? chars[3] : chars[0]);
                printCodePointAtInternal(cx, y, ch, textStyle);
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
                    printAtInternal(renderX, titleY, Cell.SPACE_STR + visible + Cell.SPACE_STR, textStyle);
                }
            }
        }
        
        // Sides - only visible rows
        for (int cy = Math.max(y + 1, visTop); cy < Math.min(y + boxHeight - 1, visBottom); cy++) {
            if (x >= visLeft && x < visRight) {
                printAtInternal(x, cy, String.valueOf(chars[1]), textStyle);
            }
            if (x + boxWidth - 1 >= visLeft && x + boxWidth - 1 < visRight) {
                printAtInternal(x + boxWidth - 1, cy, String.valueOf(chars[1]), textStyle);
            }
        }
        
        // Bottom border
        if (y + boxHeight - 1 >= visTop && y + boxHeight - 1 < visBottom) {
            for (int cx = Math.max(x, visLeft); cx < Math.min(x + boxWidth, visRight); cx++) {
                char ch;
                if (cx == x) ch = chars[4]; // bottom-left
                else if (cx == x + boxWidth - 1) ch = chars[5]; // bottom-right
                else ch = chars[0]; // horizontal
                printAtInternal(cx, y + boxHeight - 1, String.valueOf(ch), textStyle);
            }
        }
        regionPool.recycle(region);
        regionPool.recycle(renderRegion);
    }


    private void drawSparklineInternal(
        TerminalRectangle region, 
        TerminalRectangle renderRegion,
        float[] values, 
        TextStyle style, 
        TextStyle peakStyle
    ) {
        int bufW = contentBounds.getWidth();
        int bufH = contentBounds.getHeight();

        int visLeft   = renderRegion != null ? renderRegion.getX()                           : 0;
        int visTop    = renderRegion != null ? renderRegion.getY()                           : 0;
        int visRight  = renderRegion != null ? renderRegion.getX() + renderRegion.getWidth() : bufW;
        int visBottom = renderRegion != null ? renderRegion.getY() + renderRegion.getHeight(): bufH;

        int ox = region.getX();
        int oy = region.getY();
        int rw = region.getWidth();
        int rh = region.getHeight();

        TextStyle base = style != null ? style : new TextStyle();

        // Locate peak index for optional highlight
        int peakIdx = 0;
        float peakVal = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > peakVal) { peakVal = values[i]; peakIdx = i; }
        }

        // Lower eighth-block codepoints: index 1–7 = ▁–▇, index 8 = █, index 0 = space
        final int[] EIGHTH_BLOCKS = {
            ' ', 0x2581, 0x2582, 0x2583, 0x2584, 0x2585, 0x2586, 0x2587, 0x2588
        };

        for (int i = 0; i < values.length && i < rw; i++) {
            int cx = ox + i;
            if (cx < visLeft || cx >= visRight) continue;

            float val = Math.max(0f, Math.min(1f, values[i]));
            int totalEighths = Math.round(val * rh * 8);
            TextStyle cellStyle = (peakStyle != null && i == peakIdx) ? peakStyle : base;

            for (int row = 0; row < rh; row++) {
                int cy = oy + row;
                if (cy < visTop || cy >= visBottom) continue;

                // row 0 = top of region; rowFromBottom 0 = bottom row
                int rowFromBottom    = rh - 1 - row;
                int rowEighthsStart  = rowFromBottom * 8;
                int rowEighthsEnd    = rowEighthsStart + 8;

                int cp;
                if (totalEighths >= rowEighthsEnd) {
                    cp = 0x2588;    // █ fully filled
                } else if (totalEighths > rowEighthsStart) {
                    cp = EIGHTH_BLOCKS[totalEighths - rowEighthsStart];
                } else {
                    cp = ' ';       // empty
                }
                cells[cy][cx].set(cp, cellStyle);
            }
        }
    }


    // Column × row → Unicode bit position (ISO 11548-1 encoding)
    // Layout:  dot1(TL)=bit0  dot4(TR)=bit3
    //          dot2(ML)=bit1  dot5(MR)=bit4
    //          dot3(BL-mid)=bit2  dot6(BR-mid)=bit5
    //          dot7(BL)=bit6  dot8(BR)=bit7
    private static final int[][] BRAILLE_BITS = {
        {0, 1, 2, 6},   // left column  (rows 0–3)
        {3, 4, 5, 7}    // right column (rows 0–3)
    };

    private void drawBrailleBitmapInternal(
        TerminalRectangle region, TerminalRectangle renderRegion,
        int pixelWidth, int pixelHeight, byte[] pixels, TextStyle style
    ) {
        int bufW = contentBounds.getWidth();
        int bufH = contentBounds.getHeight();

        int visLeft   = renderRegion != null ? renderRegion.getX()                           : 0;
        int visTop    = renderRegion != null ? renderRegion.getY()                           : 0;
        int visRight  = renderRegion != null ? renderRegion.getX() + renderRegion.getWidth() : bufW;
        int visBottom = renderRegion != null ? renderRegion.getY() + renderRegion.getHeight(): bufH;

        int ox = region.getX();
        int oy = region.getY();

        int charCols = (pixelWidth  + 1) / 2;
        int charRows = (pixelHeight + 3) / 4;
        TextStyle s  = style != null ? style : new TextStyle();

        for (int charY = 0; charY < charRows; charY++) {
            int cy = oy + charY;
            if (cy < visTop || cy >= visBottom || cy < 0 || cy >= bufH) continue;

            for (int charX = 0; charX < charCols; charX++) {
                int cx = ox + charX;
                if (cx < visLeft || cx >= visRight || cx < 0 || cx >= bufW) continue;

                int px = charX * 2;
                int py = charY * 4;
                int pattern = 0;

                for (int dc = 0; dc < 2; dc++) {
                    for (int dr = 0; dr < 4; dr++) {
                        if (getPixel(pixels, pixelWidth, pixelHeight, px + dc, py + dr))
                            pattern |= (1 << BRAILLE_BITS[dc][dr]);
                    }
                }

                // U+2800 + pattern covers the entire 256-char braille block
                cells[cy][cx].set(0x2800 + pattern, s);
            }
        }
    }

    // Column × row → sextant mask bit
    // Layout:  TL=bit0  TR=bit1
    //          ML=bit2  MR=bit3
    //          BL=bit4  BR=bit5
    private static final int[][] SEXTANT_BITS = {
        {0, 2, 4},   // left column  (rows 0–2)
        {1, 3, 5}    // right column (rows 0–2)
    };

    private void drawSextantBitmapInternal(
        TerminalRectangle region, TerminalRectangle renderRegion,
        int pixelWidth, int pixelHeight, byte[] pixels, TextStyle style
    ) {
        int bufW = contentBounds.getWidth();
        int bufH = contentBounds.getHeight();

        int visLeft   = renderRegion != null ? renderRegion.getX()                           : 0;
        int visTop    = renderRegion != null ? renderRegion.getY()                           : 0;
        int visRight  = renderRegion != null ? renderRegion.getX() + renderRegion.getWidth() : bufW;
        int visBottom = renderRegion != null ? renderRegion.getY() + renderRegion.getHeight(): bufH;

        int ox = region.getX();
        int oy = region.getY();

        int charCols = (pixelWidth  + 1) / 2;
        int charRows = (pixelHeight + 2) / 3;
        TextStyle s  = style != null ? style : new TextStyle();

        for (int charY = 0; charY < charRows; charY++) {
            int cy = oy + charY;
            if (cy < visTop || cy >= visBottom || cy < 0 || cy >= bufH) continue;

            for (int charX = 0; charX < charCols; charX++) {
                int cx = ox + charX;
                if (cx < visLeft || cx >= visRight || cx < 0 || cx >= bufW) continue;

                int px = charX * 2;
                int py = charY * 3;
                int sextantMask = 0;

                for (int dc = 0; dc < 2; dc++) {
                    for (int dr = 0; dr < 3; dr++) {
                        if (getPixel(pixels, pixelWidth, pixelHeight, px + dc, py + dr))
                            sextantMask |= (1 << SEXTANT_BITS[dc][dr]);
                    }
                }

                if (TextStyle.Sextant.isBlank(sextantMask)) {
                    // Transparent — leave cell unchanged (caller fills background separately)
                    // Alternatively: cells[cy][cx].set(' ', s); for opaque blank
                } else if (TextStyle.Sextant.requiresFill(sextantMask)) {
                    // FULL_MASK (0b111111): no safe single glyph exists.
                    // A background-filled space preserves display width consistency.
                    cells[cy][cx].set(' ', s);
                } else {
                    // All other masks: U+1FB00–U+1FB3B sextant glyphs.
                    // 0b010101 (▌) and 0b101010 (▐) resolve here too — they are
                    // single-width half-blocks from the block-elements range, not
                    // legacy-computing chars.  Cell.computeDisplayWidth handles them
                    // correctly, but mixing them into a sextant grid can cause column
                    // misalignment if the terminal treats the sextant block as double-width.
                    int cp = TextStyle.Sextant.codepointForMask(sextantMask);
                    printCodePointAtInternal(cx, cy, cp, s);
                }
            }
        }
    }


    private void drawBitmapInternal(
        TerminalRectangle region, TerminalRectangle renderRegion,
        int pixelWidth, int pixelHeight, byte[] pixels, TextStyle style
    ) {
        int bufW = contentBounds.getWidth();
        int bufH = contentBounds.getHeight();

        int visLeft   = renderRegion != null ? renderRegion.getX()                           : 0;
        int visTop    = renderRegion != null ? renderRegion.getY()                           : 0;
        int visRight  = renderRegion != null ? renderRegion.getX() + renderRegion.getWidth() : bufW;
        int visBottom = renderRegion != null ? renderRegion.getY() + renderRegion.getHeight(): bufH;

        int ox = region.getX();
        int oy = region.getY();

        int charCols = (pixelWidth  + 1) / 2;
        int charRows = (pixelHeight + 1) / 2;
        TextStyle s  = style != null ? style : new TextStyle();

        for (int charY = 0; charY < charRows; charY++) {
            int cy = oy + charY;
            if (cy < visTop || cy >= visBottom || cy < 0 || cy >= bufH) continue;

            for (int charX = 0; charX < charCols; charX++) {
                int cx = ox + charX;
                if (cx < visLeft || cx >= visRight || cx < 0 || cx >= bufW) continue;

                int px = charX * 2;
                int py = charY * 2;

                // Quadrant bit layout: bit3=UL bit2=UR bit1=LL bit0=LR
                int mask = 0;
                if (getPixel(pixels, pixelWidth, pixelHeight, px,   py  )) mask |= 0b1000;
                if (getPixel(pixels, pixelWidth, pixelHeight, px+1, py  )) mask |= 0b0100;
                if (getPixel(pixels, pixelWidth, pixelHeight, px,   py+1)) mask |= 0b0010;
                if (getPixel(pixels, pixelWidth, pixelHeight, px+1, py+1)) mask |= 0b0001;

                cells[cy][cx].set(TextStyle.Quadrant.quadrantFromMask(mask).getCodepoint(), s);
            }
        }
    }

    // ── Executor ─────────────────────────────────────────────────────────────────

    private void executeDrawTableBorderInternal(NoteBytesMap cmd) {
        NoteBytes regionBytes = cmd.get(Keys.REGION);
        if (regionBytes == null) return;

        NoteBytes renderRegionBytes = cmd.get(TerminalCommands.RENDER_REGION);

        TerminalRectangle region       = TerminalRectangle.fromNoteBytes(regionBytes);
        TerminalRectangle renderRegion = renderRegionBytes != null
            ? TerminalRectangle.fromNoteBytes(renderRegionBytes) : null;

        String boxStyleName = cmd.getAsString(TerminalCommands.LINE_STYLE, "SINGLE");
        LineStyle boxStyle = LineStyle.valueOf(boxStyleName);

        TextStyle style = parseStyle(cmd.get(Keys.STYLE));

        int[] hSeps = readNoteIntArray(cmd.get(TerminalCommands.H_SEPARATORS));
        int[] vSeps = readNoteIntArray(cmd.get(TerminalCommands.V_SEPARATORS));

        String   title    = cmd.getAsString(Keys.TITLE,    null);
        String   titlePos = cmd.getAsString(TerminalCommands.TITLE_POS, "TOP_CENTER");
        Position pos      = Position.valueOf(titlePos);

        drawTableBorderInternal(region, renderRegion, boxStyle, style,
                                hSeps, vSeps, title, pos);

        regionPool.recycle(region);
        if (renderRegion != null) regionPool.recycle(renderRegion);
    }


    // ── Core draw ─────────────────────────────────────────────────────────────────

    private void drawTableBorderInternal(
        TerminalRectangle region,
        TerminalRectangle renderRegion,   // null = no clipping beyond buffer bounds
        LineStyle          boxStyle,
        TextStyle         style,
        int[]             hSeps,          // local Y positions of H-separators
        int[]             vSeps,          // local X positions of V-separators
        String            title,
        Position          titlePos
    ) {
        if (region.getWidth() < 2 || region.getHeight() < 2) return;

        // Effective visibility window: renderRegion if supplied, else full buffer
        int bufW = contentBounds.getWidth();
        int bufH = contentBounds.getHeight();

        int visLeft   = renderRegion != null ? renderRegion.getX()                       : 0;
        int visTop    = renderRegion != null ? renderRegion.getY()                       : 0;
        int visRight  = renderRegion != null ? renderRegion.getX() + renderRegion.getWidth()  : bufW;
        int visBottom = renderRegion != null ? renderRegion.getY() + renderRegion.getHeight() : bufH;

        int ox = region.getX();   // origin X
        int oy = region.getY();   // origin Y
        int rw = region.getWidth();
        int rh = region.getHeight();

        char[] jc = boxStyle.getChars();
   
        // Sets for O(1) lookup during draw
        Set<Integer> hSet = toSet(hSeps);
        Set<Integer> vSet = toSet(vSeps);

        // ── 1. Outer box top row ──────────────────────────────────────────────────
        int topY = oy;
        if (topY >= visTop && topY < visBottom) {
            for (int cx = Math.max(ox, visLeft); cx < Math.min(ox + rw, visRight); cx++) {
                char ch;
                if      (cx == ox)           ch = jc[2];                    // ┌
                else if (cx == ox + rw - 1)  ch = jc[3];                    // ┐
                else if (vSet.contains(cx))  ch = jc[8];                    // ┬
                else                         ch = jc[0];                    // ─
                printCodePointAtInternal(cx, topY, ch, style);
            }
        }

        // ── 2. Outer box bottom row ───────────────────────────────────────────────
        int botY = oy + rh - 1;
        if (botY >= visTop && botY < visBottom) {
            for (int cx = Math.max(ox, visLeft); cx < Math.min(ox + rw, visRight); cx++) {
                char ch;
                if      (cx == ox)           ch = jc[4];                    // └
                else if (cx == ox + rw - 1)  ch = jc[5];                    // ┘
                else if (vSet.contains(cx))  ch = jc[9];                    // ┴
                else                         ch = jc[0];                    // ─
                printCodePointAtInternal(cx, botY, ch, style);
            }
        }

        // ── 3. Interior rows ──────────────────────────────────────────────────────
        for (int cy = oy + 1; cy < oy + rh - 1; cy++) {
            if (cy < visTop || cy >= visBottom) continue;

            boolean isHSep = hSet.contains(cy);

            if (isHSep) {
                // Full horizontal separator row
                for (int cx = Math.max(ox, visLeft); cx < Math.min(ox + rw, visRight); cx++) {
                    char ch;
                    if      (cx == ox)           ch = jc[6];                // ├
                    else if (cx == ox + rw - 1)  ch = jc[7];                // ┤
                    else if (vSet.contains(cx))  ch = jc[10];               // ┼
                    else                         ch = jc[0];                // ─
                    printCodePointAtInternal(cx, cy, ch, style);
                }
            } else {
                // Non-separator row — only left/right walls and V-separator columns
                if (ox >= visLeft && ox < visRight) {
                    printCodePointAtInternal(ox, cy, jc[1], style);         // │ left wall
                }
                if (ox + rw - 1 >= visLeft && ox + rw - 1 < visRight) {
                    printCodePointAtInternal(ox + rw - 1, cy, jc[1], style);// │ right wall
                }
                for (int vx : vSeps) {
                    if (vx >= visLeft && vx < visRight) {
                        printCodePointAtInternal(vx, cy, jc[1], style);     // │ column divider
                    }
                }
            }
        }

        // ── 4. Optional title (drawn last — overwrites border chars) ──────────────
        if (title != null && !title.isEmpty()) {
            int titleX = calculateTitleX(ox, rw, title, titlePos);
            int titleY = calculateTitleY(oy, rh, titlePos);
            if (titleY >= visTop && titleY < visBottom && titleX < visRight) {
                String visible = clipString(title, titleX, visLeft, visRight);
                if (!visible.isEmpty()) {
                    printAtInternal(Math.max(titleX, visLeft), titleY,
                                    " " + visible + " ", style);
                }
            }
        }
    }

    private static Set<Integer> toSet(int[] arr) {
        if (arr == null || arr.length == 0) return Collections.emptySet();
        Set<Integer> set = new HashSet<>(arr.length * 2);
        for (int v : arr) set.add(v);
        return set;
    }

    private static int[] readNoteIntArray(NoteBytes value) {
        if(value == null)
            return new int[0];

        if(value.getType() != NoteBytesMetaData.NOTE_INTEGER_ARRAY_TYPE){
            Throwable ex = new IllegalArgumentException("" + value + " is not a NoteIntegerArray");
            Log.logError("[ConsoleContainer]", "readNoteIntArray", ex);
            return new int[0];
        }

        return value.getAsNoteIntegerArray().getAsArray();
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

    private void drawHLineInternal(int x, int y, int length, TextStyle style, LineStyle lineStyle) {
        for (int i = 0; i < length; i++) {
            printCodePointAtInternal(x, y, lineStyle.horizontal(), style);
        }
    }
    
    private void drawVLineInternal(int x, int y, int length, TextStyle style, LineStyle lineStyle) {
        for (int i = 0; i < length; i++) {
            printCodePointAtInternal(x, y + i,lineStyle.vertical(), style);
        }
    }
    
    private void fillRegionInternal(TerminalRectangle region, int codePoint, TextStyle style) {
        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();
        int startX = Math.max(contentBounds.getX(), region.getX());
        int startY = Math.max(contentBounds.getY(), region.getY());
        int endX = Math.min(width - 1, region.getX() + region.getWidth() - 1);
        int endY = Math.min(height - 1, region.getY() + region.getHeight() - 1);
        int dw = Cell.computeDisplayWidth(codePoint);

        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                if (dw == 2) {
                    if (x + 1 <= endX) {
                        cells[y][x].set(codePoint, style);
                        cells[y][x + 1].setAsContinuation();
                        x++;
                    } else {
                        cells[y][x].set(' ', style); // no room for wide char at right edge
                    }
                } else {
                    cells[y][x].set(codePoint, style);
                }
            }
        }
    }
    
    private void swapBuffersInternal() {
        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                prevCells[y][x].copyFrom(cells[y][x]);
            }
        }
        boundsChangedPending.set(false);
    }


    private void executeDrawBorderedTextInternal(NoteBytesMap cmd) {
        NoteBytes regionBytes = cmd.get(Keys.REGION);
        if (regionBytes == null) return;
        
        TerminalRectangle region = TerminalRectangle.fromNoteBytes(regionBytes);
        String text = cmd.getAsString(Keys.TEXT, "");
        String textPosStr = cmd.getAsString(TerminalCommands.TITLE_POS, "CENTER");
        Position textPos = Position.valueOf(textPosStr);
        String boxStyleName = cmd.getAsString(TerminalCommands.LINE_STYLE, "SINGLE");
        LineStyle boxStyle = LineStyle.valueOf(boxStyleName);
        
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
        String boxStyleName = cmd.getAsString(TerminalCommands.LINE_STYLE, "SINGLE");
        LineStyle boxStyle = LineStyle.valueOf(boxStyleName);
        
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

    private void drawScrollbarInternal(
        TerminalRectangle region, 
        TerminalRectangle renderRegion,
        int scrollPos, 
        int totalItems, 
        int visibleItems,
        boolean showArrows, 
        TextStyle trackStyle, 
        TextStyle thumbStyle
    ) {
        int bufW = contentBounds.getWidth();
        int bufH = contentBounds.getHeight();

        int visLeft   = renderRegion != null ? renderRegion.getX()                           : 0;
        int visTop    = renderRegion != null ? renderRegion.getY()                           : 0;
        int visRight  = renderRegion != null ? renderRegion.getX() + renderRegion.getWidth() : bufW;
        int visBottom = renderRegion != null ? renderRegion.getY() + renderRegion.getHeight(): bufH;

        int ox = region.getX();
        int oy = region.getY();
        int rh = region.getHeight();

        TextStyle track = trackStyle != null ? trackStyle : new TextStyle();
        TextStyle thumb = thumbStyle != null ? thumbStyle : new TextStyle();

        // Guard: nothing to scroll
        if (totalItems <= visibleItems) {
            // Draw a plain track — no thumb needed
            for (int row = 0; row < rh; row++) {
                int cy = oy + row;
                if (cy >= visTop && cy < visBottom && ox >= visLeft && ox < visRight)
                    cells[cy][ox].set('│', track);
            }
            return;
        }

        int arrowRows  = showArrows ? 1 : 0;
        int trackStart = oy + arrowRows;
        int trackEnd   = oy + rh - arrowRows;      // exclusive
        int trackH     = trackEnd - trackStart;
        if (trackH <= 0) return;

        // Thumb geometry
        int thumbH   = Math.max(1, visibleItems * trackH / Math.max(1, totalItems));
        int thumbOff = (int)((long)(scrollPos) * (trackH - thumbH)
                            / Math.max(1, totalItems - visibleItems));
        thumbOff = Math.max(0, Math.min(thumbOff, trackH - thumbH));
        int thumbStart = trackStart + thumbOff;
        int thumbEnd   = thumbStart + thumbH;       // exclusive

        // Top arrow
        if (showArrows && oy >= visTop && oy < visBottom && ox >= visLeft && ox < visRight)
            cells[oy][ox].set('▲', track);

        // Track rows
        for (int cy = trackStart; cy < trackEnd; cy++) {
            if (cy < visTop || cy >= visBottom) continue;
            if (ox < visLeft || ox >= visRight)  continue;
            boolean isThumb = (cy >= thumbStart && cy < thumbEnd);
            cells[cy][ox].set(isThumb ? '█' : '░', isThumb ? thumb : track);
        }

        // Bottom arrow
        int botArrowY = oy + rh - 1;
        if (showArrows && botArrowY >= visTop && botArrowY < visBottom
                && ox >= visLeft && ox < visRight)
            cells[botArrowY][ox].set('▼', track);
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
        TextAlignment align = TextAlignment.valueOf(alignName);
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
        int shadeChar = shadeStr.isEmpty() ? '░' : shadeStr.charAt(0);
        TextStyle style = parseStyle(cmd.get(Keys.STYLE));
        
        if(renderRegion == null){
            shadeRegionInternal(region, shadeChar, style);
        }else{
            fillRegionInternal(renderRegion, shadeChar, style);
        }
    }


    private void drawBorderedTextInternal(TerminalRectangle region, String text, Position textPos,
                                        LineStyle boxStyle, TextStyle textStyle, TextStyle borderStyle) {
        drawBoxInternal(region.getX(), region.getY(), region.getWidth(), region.getHeight(), 
                    null, null, borderStyle, boxStyle);
        
        if (text != null && !text.isEmpty() && region.getWidth() > 2 && region.getHeight() > 2) {
            int[] coords = calculateTextPosition(region, text, textPos);
            printAtInternal(coords[0], coords[1], text, textStyle);
        }
    }

    private void drawPanelInternal(TerminalRectangle region, String title, Position titlePos,
                                LineStyle boxStyle, TextStyle borderStyle, TextStyle fillStyle) {
        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();       

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
                    title, titlePos, borderStyle, boxStyle);
    }

    private void drawButtonInternal(TerminalRectangle region, String label, Position labelPos,
                                boolean selected, TextStyle style) {
        TextStyle buttonStyle = selected ? style.inverse() : style;
        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();

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
                                String label, Position labelPos, boolean selected, TextStyle style
    ) {
        TextStyle buttonStyle = selected ? style.inverse() : style;

        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();

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
                                String title, Position titlePos, LineStyle boxStyle, 
                                TextStyle borderStyle, TextStyle fillStyle
    ) {
        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();

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
        
        drawBoxInternal(region, renderRegion, title, titlePos, boxStyle,borderStyle);
    }

    private int[] calculateTextPosition(TerminalRectangle region, String text, Position pos) {
        int textLen = displayWidth(text);
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
                                        TextStyle style, TextStyle emptyStyle
    ) {
        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();

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
                    cells[y][x].set(TerminalCommands.PROGRESS_BLOCKS[partialIndex].codePointAt(0), style);
                } else {
                    // Empty
                    cells[y][x].set(' ', emptyStyle);
                }
            }
        }
    }

    private void drawProgressBarInternal(TerminalRectangle region, TerminalRectangle renderRegion,
                                        double progress, TextStyle style, TextStyle emptyStyle
    ) {
        int height = contentBounds.getHeight();
        int width = contentBounds.getWidth();

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
                                    TextAlignment align, TextStyle style
    ) {
        if (text == null || text.isEmpty()) return;
        int height = contentBounds.getHeight();

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
            if (align == TextAlignment.CENTER) {
                x = region.getX() + (region.getWidth() - line.length()) / 2;
            } else if (align == TextAlignment.RIGHT) {
                x = region.getX() + region.getWidth() - line.length();
            }
            
            x = Math.max(region.getX(), Math.min(x, region.getX() + region.getWidth() - line.length()));
            
            printAtInternal(x, y, line, style);
            y++;
        }
    }

    private void drawTextBlockInternal(TerminalRectangle region, TerminalRectangle renderRegion,
                                    String text, TextAlignment align, TextStyle style) {
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
                
                if (align == TextAlignment.CENTER) {
                    x = region.getX() + (region.getWidth() - line.length()) / 2;
                } else if (align == TextAlignment.RIGHT) {
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

    private void shadeRegionInternal(TerminalRectangle region, int shadeCodepoint, TextStyle style) {
        fillRegionInternal(region, shadeCodepoint, style);
    }


    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            w += Cell.computeDisplayWidth(cp);
            i += Character.charCount(cp);
        }
        return w;
    }

    private String[] wrapText(String text, int maxWidth) {
        if (maxWidth <= 0) return new String[0];
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) { lines.add(""); continue; }
            StringBuilder currentLine = new StringBuilder();
            int currentWidth = 0;
            for (String word : paragraph.split("\\s+")) {
                int wordWidth = displayWidth(word);
                if (currentWidth == 0) {
                    currentLine.append(word);
                    currentWidth = wordWidth;
                } else if (currentWidth + 1 + wordWidth <= maxWidth) {
                    currentLine.append(' ').append(word);
                    currentWidth += 1 + wordWidth;
                } else {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                    currentWidth = wordWidth;
                }
            }
            if (currentLine.length() > 0) lines.add(currentLine.toString());
        }
        return lines.toArray(new String[0]);
    }
    
    // Row-major, MSB-first bit packing.  Returns false for any out-of-bounds access.
    private static boolean getPixel(byte[] pixels, int pixelWidth, int pixelHeight, int px, int py) {
        if (px < 0 || px >= pixelWidth || py < 0 || py >= pixelHeight) return false;
        int bitIndex = py * pixelWidth + px;
        int byteIdx  = bitIndex >> 3;
        int bitOff   = 7 - (bitIndex & 7);   // MSB-first
        return byteIdx < pixels.length && (pixels[byteIdx] & (1 << bitOff)) != 0;
    }
   
    
    // ===== HELPERS =====

    private String clipString(String text, int textX, int visLeft, int visRight) {
        if (textX >= visRight) return "";
        StringBuilder result = new StringBuilder();
        int col = textX;
        int offset = 0;
        while (offset < text.length()) {
            int cp = text.codePointAt(offset);
            int w = Cell.computeDisplayWidth(cp);
            if (col + w > visRight) break;
            if (col >= visLeft) {
                result.appendCodePoint(cp);
            } else if (col + w > visLeft) {
                // wide char straddles visLeft — emit space to hold the column
                result.append(' ');
            }
            col += w;
            offset += Character.charCount(cp);
        }
        return result.toString();
    }
    
    private TextStyle parseStyle(NoteBytes styleBytes) {
        if (styleBytes == null) return new TextStyle();
        return TextStyle.fromNoteBytes(styleBytes);
    }
    
    // ===== ACCESSORS =====
    
    int getX() { return contentBounds.getX(); }
    int getY() { return contentBounds.getY(); }
    int getHeight() { return contentBounds.getHeight(); }
    int getWidth() { return contentBounds.getWidth(); }
    int getCursorX() { return cursorX; }
    int getCursorY() { return cursorY; }
    /** Returns the effective cursor visibility — desired AND focused. */
    boolean isCursorVisible() { return effectiveCursorVisible(); }
    /** Returns the raw desired cursor state, regardless of focus. */
    boolean isCursorDesired() { return cursorDesired; }
    
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
        // Re-render immediately so effectiveCursorVisible() resolves with the stored
        // cursorDesired — the cursor appears (or stays hidden if desired=false) without
        // waiting for the next component render cycle.
        requestRenderInternal();
    }

    @Override
    protected void onFocusRevoked() {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.EVENT, io.netnotes.engine.io.input.events.EventBytes.EVENT_CONTAINER_FOCUS_LOST);
        emitEvent(map);
        // Re-render so effectiveCursorVisible() now returns false — the cursor is
        // physically hidden without the component needing to do anything.
        requestRenderInternal();
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


    @Override
    protected void onEventStreamClosed() {
        destroyNow();
    }


   

    
}
