package io.netnotes.gui.core.binaryEvents;

/**
 * Factory methods for creating specific binary events.
 * Each method documents its binary format.
 */
public class BinaryEvents {

    //////
    /// 
    /// TODO: methods to implement
    /// 
    //////
  
    public static BinaryEvent cursorMoveDocStart(){
        throw new IllegalStateException("not implemented");
    }
    
    public static BinaryEvent cursorMoveLineStart(){
        throw new IllegalStateException("not implemented");
    }
    public static BinaryEvent cursorMoveDocEnd(){
        throw new IllegalStateException("not implemented");
    }
    public static BinaryEvent cursorMoveLineEnd(){
        throw new IllegalStateException("not implemented");
    }
    public static BinaryEvent selectionAll(){
        throw new IllegalStateException("not implemented");
    }
    public static BinaryEvent cursorMoveToPosition(double x, double y){
        return null;
    }
    public static BinaryEvent dragEnd(double x, double y){
        throw new IllegalStateException("not implemented");
    }
    public static BinaryEvent dragStart(double x, double y){
        throw new IllegalStateException("not implemented");
    }
    public static BinaryEvent dragUpdate(double x, double y){
        throw new IllegalStateException("not implemented");
    }

    public static BinaryEvent scrollDelta(double deltaX, double deltaY){
        throw new IllegalStateException("not implemented");
    }
    public static BinaryEvent click(double x, double y){
        throw new IllegalStateException("not implemented");
    }
    public static BinaryEvent doubleClick(double x, double y){
        throw new IllegalStateException("not implemented");
    }
    public static BinaryEvent tripleClick(double x, double y){
        throw new IllegalStateException("not implemented");
    }
    
    /**
     * CURSOR_MOVE_ABSOLUTE
     * 
     * Format: [2 bytes type] [4 bytes globalOffset]
     * Total: 6 bytes
     */
    public static BinaryEvent cursorMoveAbsolute(int globalOffset) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.CURSOR_MOVE_ABSOLUTE)
            .add(globalOffset)
            .build();
    }
    
    /**
     * CURSOR_MOVE_CHAR_FORWARD
     * 
     * Format: [2 bytes type]
     * Total: 2 bytes
     */
    public static BinaryEvent cursorMoveCharForward() {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.CURSOR_MOVE_CHAR_FORWARD)
            .build();
    }
    
    /**
     * CURSOR_MOVE_CHAR_BACKWARD
     * 
     * Format: [2 bytes type]
     * Total: 2 bytes
     */
    public static BinaryEvent cursorMoveCharBackward() {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.CURSOR_MOVE_CHAR_BACKWARD)
            .build();
    }

    /**
     * CURSOR_MOVE_UP
     * Format: [SHORT: type]
     */
    public static BinaryEvent cursorMoveUp() {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.CURSOR_MOVE_UP)
            .build();
    }
    
    /**
     * CURSOR_MOVE_DOWN
     * Format: [SHORT: type]
     */
    public static BinaryEvent cursorMoveDown() {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.CURSOR_MOVE_DOWN)
            .build();
    }
    
    /**
     * SELECTION_SET
     * 
     * Format: [2 bytes type] [4 bytes startOffset] [4 bytes endOffset]
     * Total: 10 bytes
     */
    public static BinaryEvent selectionSet(int startOffset, int endOffset) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.SELECTION_SET)
            .add(startOffset)
            .add(endOffset)
            .build();
    }
    
    /**
     * SELECTION_CLEAR
     * 
     * Format: [2 bytes type]
     * Total: 2 bytes
     */
    public static BinaryEvent selectionClear() {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.SELECTION_CLEAR)
            .build();
    }
    
    /**
     * SELECTION_EXTEND
     * Format: [SHORT: type]
     */
    public static BinaryEvent selectionExtend() {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.SELECTION_EXTEND)
            .build();
    }

    /**
     * SELECTION_WORD
     * Format: [SHORT: type]
     */
    public static BinaryEvent selectionWord() {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.SELECTION_WORD)
            .build();
    }

    /**
     * TEXT_INSERT
     * 
     * Format: [2 bytes type] [4 bytes offset] [2 bytes textLength] [N bytes utf8Text]
     * Total: 8 + N bytes
     */
    public static BinaryEvent textInsert(int offset, String text) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.TEXT_INSERT)
            .add(offset)
            .add(text)
            .build();
    }

    /**
     * SELECTION_LINE
     * Format: [SHORT: type]
     */
    public static BinaryEvent selectionLine() {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.SELECTION_LINE)
            .build();
    }
    
    /**
     * TEXT_DELETE_CHAR_BEFORE
     * 
     * Format: [2 bytes type] [4 bytes offset]
     * Total: 6 bytes
     */
    public static BinaryEvent textDeleteCharBefore(int offset) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.TEXT_DELETE_CHAR_BEFORE)
            .add(offset)
            .build();
    }
    
    /**
     * TEXT_DELETE_CHAR_AFTER
     * 
     * Format: [2 bytes type] [4 bytes offset]
     * Total: 6 bytes
     */
    public static BinaryEvent textDeleteCharAfter(int offset) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.TEXT_DELETE_CHAR_AFTER)
            .add(offset)
            .build();
    }
    
    /**
     * TEXT_DELETE_RANGE
     * 
     * Format: [2 bytes type] [4 bytes startOffset] [4 bytes endOffset]
     * Total: 10 bytes
     */
    public static BinaryEvent textDeleteRange(int startOffset, int endOffset) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.TEXT_DELETE_RANGE)
            .add(startOffset)
            .add(endOffset)
            .build();
    }
    
    /**
     * VIEWPORT_SCROLL
     * 
     * Format: [2 bytes type] [4 bytes x] [4 bytes y]
     * Total: 10 bytes
     */
    public static BinaryEvent viewportScroll(double x, double y) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.VIEWPORT_SCROLL)
            .add(x)
            .add(y)
            .build();
    }
    
    /**
     * STATE_SET_FLAG
     * 
     * Format: [2 bytes type] [2 bytes segmentIdLength] [N bytes segmentId] [8 bytes flagMask]
     * Total: 12 + N bytes
     */
    public static BinaryEvent stateSetFlag(String segmentId, long flagMask) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.STATE_SET_FLAG)
            .add(segmentId)
            .add(flagMask)
            .build();
    }
    
    /**
     * STATE_CLEAR_FLAG
     * 
     * Format: [2 bytes type] [2 bytes segmentIdLength] [N bytes segmentId] [8 bytes flagMask]
     * Total: 12 + N bytes
     */
    public static BinaryEvent stateClearFlag(String segmentId, long flagMask) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.STATE_CLEAR_FLAG)
            .add(segmentId)
            .add(flagMask)
            .build();
    }
    
    /**
     * FOCUS_SET
     * 
     * Format: [2 bytes type] [2 bytes segmentIdLength] [N bytes segmentId]
     * Total: 4 + N bytes
     */
    public static BinaryEvent focusSet(String segmentId) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.FOCUS_SET)
            .add(segmentId)
            .build();
    }

    /**
     * FOCUS_NEXT
     * Format: [SHORT: type]
     */
    public static BinaryEvent focusNext() {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.FOCUS_NEXT)
            .build();
    }

    /**
     * FOCUS_PREV
     * Format: [SHORT: type]
     */
    public static BinaryEvent focusPrev() {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.FOCUS_PREV)
            .build();
    }
    
    /**
     * GRID_RESIZE_UPDATE
     * 
     * Format: [2 bytes type] [1 byte isColumn] [2 bytes trackIndex] [4 bytes deltaPixels]
     * Total: 9 bytes
     */
    public static BinaryEvent gridResizeUpdate(boolean isColumn, short trackIndex, int deltaPixels) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.GRID_RESIZE_UPDATE)
            .add(isColumn)
            .add(trackIndex)
            .add(deltaPixels)
            .build();
    }
    
    /**
     * BATCH_BEGIN
     * 
     * Format: [2 bytes type] [2 bytes eventCount]
     * Total: 4 bytes
     */
    public static BinaryEvent batchBegin(short eventCount) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.BATCH_BEGIN)
            .add(eventCount)
            .build();
    }
    
    /**
     * BATCH_END
     * 
     * Format: [2 bytes type]
     * Total: 2 bytes
     */
    public static BinaryEvent batchEnd() {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.BATCH_END)
            .build();
    }

     /**
     * RAW_KEY_PRESSED
     * Format: [SHORT: type] [INTEGER: keyCode] [BYTE: modifiers]
     * 
     * Modifier bits:
     * bit 0: shift
     * bit 1: ctrl
     * bit 2: alt
     * bit 3: meta
     */
    public static BinaryEvent keyPressed(String nodeId, int keyCode, boolean shift, boolean ctrl, boolean alt, boolean meta) {
        byte modifiers = 0;
        if (shift) modifiers |= 1;
        if (ctrl) modifiers |= 2;
        if (alt) modifiers |= 4;
        if (meta) modifiers |= 8;
        
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_KEY_PRESSED)
            .add(keyCode)
            .add(modifiers)
            .build();
    }
    
    /**
     * RAW_KEY_RELEASED
     * Format: [SHORT: type] [INTEGER: keyCode] [BYTE: modifiers]
     */
    public static BinaryEvent keyReleased(String nodeId, int keyCode, boolean shift, boolean ctrl, boolean alt, boolean meta) {
        byte modifiers = 0;
        if (shift) modifiers |= 1;
        if (ctrl) modifiers |= 2;
        if (alt) modifiers |= 4;
        if (meta) modifiers |= 8;
        
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_KEY_RELEASED)
            .add(keyCode)
            .add(modifiers)
            .build();
    }
    
    /**
     * RAW_KEY_TYPED
     * Format: [SHORT: type] [STRING: character]
     */
    public static BinaryEvent keyTyped(String nodeId, String character) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_KEY_TYPED)
            .add(character)
            .build();
    }
    
    /**
     * RAW_MOUSE_PRESSED
     * Format: [SHORT: type] [DOUBLE: x] [DOUBLE: y] [BYTE: button] [LONG: timestamp]
     */
    public static BinaryEvent mousePressed(String nodeId, double x, double y, byte button, long timestamp) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_MOUSE_PRESSED)
            .add(x)
            .add(y)
            .add(button)
            .add(timestamp)
            .build();
    }
    
    /**
     * RAW_MOUSE_RELEASED
     * Format: [SHORT: type] [DOUBLE: x] [DOUBLE: y] [BYTE: button] [LONG: timestamp]
     */
    public static BinaryEvent mouseReleased(String nodeId, double x, double y, byte button, long timestamp) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_MOUSE_RELEASED)
            .add(x)
            .add(y)
            .add(button)
            .add(timestamp)
            .build();
    }
    
    /**
     * RAW_MOUSE_MOVED
     * Format: [SHORT: type] [DOUBLE: x] [DOUBLE: y]
     */
    public static BinaryEvent mouseMoved(String nodeId, double x, double y) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_MOUSE_MOVED)
            .add(x)
            .add(y)
            .build();
    }
    
    /**
     * RAW_MOUSE_DRAGGED
     * Format: [SHORT: type] [DOUBLE: x] [DOUBLE: y]
     */
    public static BinaryEvent mouseDragged(String nodeId, double x, double y) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_MOUSE_DRAGGED)
            .add(x)
            .add(y)
            .build();
    }
    
    /**
     * RAW_MOUSE_ENTERED
     * Format: [SHORT: type] [DOUBLE: x] [DOUBLE: y]
     */
    public static BinaryEvent mouseEntered(String nodeId, double x, double y) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_MOUSE_ENTERED)
            .add(x)
            .add(y)
            .build();
    }
    
    /**
     * RAW_MOUSE_EXITED
     * Format: [SHORT: type] [DOUBLE: x] [DOUBLE: y]
     */
    public static BinaryEvent mouseExited(String nodeId, double x, double y) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_MOUSE_EXITED)
            .add(x)
            .add(y)
            .build();
    }
    
    /**
     * RAW_MOUSE_SCROLL
     * Format: [SHORT: type] [DOUBLE: x] [DOUBLE: y] [INTEGER: deltaX] [INTEGER: deltaY]
     */
    public static BinaryEvent mouseScroll(String nodeId, double x, double y, double deltaX, double deltaY) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_MOUSE_SCROLL)
            .add(x)
            .add(y)
            .add(deltaX)
            .add(deltaY)
            .build();
    }
    
    /**
     * RAW_HSCROLL
     * Format: [SHORT: type] [DOUBLE: value]
     */
    public static BinaryEvent hScroll(String nodeId, double value) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_HSCROLL)
            .add(value)
            .build();
    }

    /**
     * RAW_VSCROLL
     * Format: [SHORT: type] [DOUBLE: value]
     */
    public static BinaryEvent vScroll(String nodeId, double value) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_VSCROLL)
            .add(value)
            .build();
    }
    

    /**
     * RAW_FOCUS_GAINED
     * Format: [SHORT: type]
     */
    public static BinaryEvent focusGained(String nodeId) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_FOCUS_GAINED)
            .build();
    }
    
    /**
     * RAW_FOCUS_LOST
     * Format: [SHORT: type]
     */
    public static BinaryEvent focusLost(String nodeId) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_FOCUS_LOST)
            .build();
    }

    public static BinaryEvent windowAttached(String canvasId, String windowId ) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_WINDOW_ATTACHED)
            .add(canvasId)
            .add(windowId)
            .build();
    }

    public static BinaryEvent windowDetached(String canvasId, String windowId) {
        return new BinaryEvent.Builder()
            .type(BinaryEvent.Types.RAW_WINDOW_DETATCHED)
            .add(canvasId)
            .add(windowId)
            .build();
    }
}