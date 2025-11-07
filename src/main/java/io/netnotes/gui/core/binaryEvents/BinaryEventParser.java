package io.netnotes.gui.fx.uiNode.binaryEvents;

import io.netnotes.engine.noteBytes.NoteIntegerArray;

/**
 * Parse binary events into handler callbacks
 */
public class BinaryEventParser {
    
    public static void parse(BinaryEvent event, LayoutEventHandler handler) {
        short type = event.getType();
        
        switch (type) {
            case BinaryEvent.Types.CURSOR_MOVE_ABSOLUTE:
                if (event.getPayloadSize() == 1) {
                    handler.onCursorMoveAbsolute(event.getPayloadAsInt(0));
                } else if (event.getPayloadSize() == 3) {
                    handler.onCursorMoveWithPath(
                        event.getPayloadAsPath(0),
                        event.getPayloadAsInt(1),
                        event.getPayloadAsInt(2)
                    );
                }
                break;
                
            case BinaryEvent.Types.CURSOR_MOVE_CHAR_FORWARD:
                handler.onCursorMoveCharForward();
                break;
                
            case BinaryEvent.Types.CURSOR_MOVE_CHAR_BACKWARD:
                handler.onCursorMoveCharBackward();
                break;
                
            case BinaryEvent.Types.SELECTION_SET:
                handler.onSelectionSet(
                    event.getPayloadAsInt(0),
                    event.getPayloadAsInt(1)
                );
                break;
                
            case BinaryEvent.Types.SELECTION_CLEAR:
                handler.onSelectionClear();
                break;
                
            case BinaryEvent.Types.TEXT_INSERT:
                handler.onTextInsert(
                    event.getPayloadAsInt(0),
                    event.getPayloadAsString(1)
                );
                break;
                
            case BinaryEvent.Types.TEXT_DELETE_CHAR_BEFORE:
                handler.onTextDeleteCharBefore(event.getPayloadAsInt(0));
                break;
                
            case BinaryEvent.Types.TEXT_DELETE_CHAR_AFTER:
                handler.onTextDeleteCharAfter(event.getPayloadAsInt(0));
                break;
                
            case BinaryEvent.Types.TEXT_DELETE_RANGE:
                handler.onTextDeleteRange(
                    event.getPayloadAsInt(0),
                    event.getPayloadAsInt(1)
                );
                break;
                
            case BinaryEvent.Types.VIEWPORT_SCROLL:
                handler.onViewportScroll(
                    event.getPayloadAsInt(0),
                    event.getPayloadAsInt(1)
                );
                break;
                
            case BinaryEvent.Types.STATE_SET_FLAG:
                handler.onStateSetFlag(
                    event.getPayloadAsString(0),
                    event.getPayloadAsLong(1)
                );
                break;
                
            case BinaryEvent.Types.STATE_CLEAR_FLAG:
                handler.onStateClearFlag(
                    event.getPayloadAsString(0),
                    event.getPayloadAsLong(1)
                );
                break;
                
            case BinaryEvent.Types.FOCUS_SET:
                handler.onFocusSet(event.getPayloadAsString(0));
                break;
                
            case BinaryEvent.Types.GRID_RESIZE_UPDATE:
                handler.onGridResizeUpdate(
                    event.getPayloadAsBoolean(0),
                    event.getPayloadAsShort(1),
                    event.getPayloadAsInt(2)
                );
                break;
                
            case BinaryEvent.Types.BATCH_BEGIN:
                handler.onBatchBegin(event.getPayloadAsShort(0));
                break;
                
            case BinaryEvent.Types.BATCH_END:
                handler.onBatchEnd();
                break;
                
            default:
                handler.onUnknownEvent(type, event);
        }
    }
    
    public interface LayoutEventHandler {
        void onCursorMoveAbsolute(int globalOffset);
        void onCursorMoveWithPath(NoteIntegerArray path, int localOffset, int globalOffset);
        void onCursorMoveCharForward();
        void onCursorMoveCharBackward();
        void onSelectionSet(int startOffset, int endOffset);
        void onSelectionClear();
        void onTextInsert(int offset, String text);
        void onTextDeleteCharBefore(int offset);
        void onTextDeleteCharAfter(int offset);
        void onTextDeleteRange(int startOffset, int endOffset);
        void onViewportScroll(int x, int y);
        void onStateSetFlag(String segmentId, long flagMask);
        void onStateClearFlag(String segmentId, long flagMask);
        void onFocusSet(String segmentId);
        void onGridResizeUpdate(boolean isColumn, short trackIndex, int deltaPixels);
        void onBatchBegin(short eventCount);
        void onBatchEnd();
        void onUnknownEvent(short type, BinaryEvent event);
    }
}