package io.netnotes.gui.core.binaryEvents;

import io.netnotes.engine.state.BitFlagStateMachine;

/**
 * Composes high-level input events from raw input stream.
 * Uses BitFlagStateMachine to track input state.
 */
public class InputStateMachine {
    
    // Input state bits
    public static final class InputBits {
        public static final long SHIFT_DOWN     = 1L << 0;
        public static final long CTRL_DOWN      = 1L << 1;
        public static final long ALT_DOWN       = 1L << 2;
        public static final long META_DOWN      = 1L << 3;
        
        public static final long MOUSE_DOWN     = 1L << 4;
        public static final long MOUSE_MOVED    = 1L << 5;
        public static final long DRAGGING       = 1L << 6;
        
        public static final long PRIMARY_BTN    = 1L << 7;
        public static final long SECONDARY_BTN  = 1L << 8;
        public static final long MIDDLE_BTN     = 1L << 9;
    }
    
    private final BitFlagStateMachine inputState = new BitFlagStateMachine("input");
    private final BinaryEventQueue outputQueue;
    
    // Click detection state
    private long lastClickTime = 0;
    private int clickCount = 0;
    private double lastClickX = 0;
    private double lastClickY = 0;
    
    // Drag detection state
    private int mouseDownX = 0;
    private int mouseDownY = 0;
    private boolean dragStarted = false;
    
    // Configuration
    private static final long DOUBLE_CLICK_TIME_MS = 500;
    private static final int DOUBLE_CLICK_DISTANCE = 5;
    private static final int DRAG_THRESHOLD = 5;
    
    public InputStateMachine(BinaryEventQueue outputQueue) {
        this.outputQueue = outputQueue;
        setupTransitions();
    }
    
    private void setupTransitions() {
        // MOUSE_DOWN + MOUSE_MOVED → DRAGGING
        inputState.addTransition(
            InputBits.MOUSE_MOVED,
            true,
            (oldState, newState, bit) -> {
                return BitFlagStateMachine.anySet(newState, InputBits.MOUSE_DOWN);
            },
            (oldState, newState, bit) -> {
                // Check if we've moved enough to start dragging
                if (!dragStarted) {
                    // Will be checked in processRawEvent
                }
            }
        );
        
        // MOUSE_UP → clear DRAGGING and MOUSE_DOWN
        inputState.onStateRemoved(InputBits.MOUSE_DOWN, (oldState, newState, bit) -> {
            inputState.removeState(InputBits.DRAGGING);
            dragStarted = false;
        });
    }
    
    /**
     * Process raw input event and emit high-level events
     */
    public void processRawEvent(BinaryEvent rawEvent) {
        short type = rawEvent.getType();
        
        switch (type) {
            case BinaryEvent.Types.RAW_KEY_PRESSED -> processKeyPressed(rawEvent);
            case BinaryEvent.Types.RAW_KEY_RELEASED -> processKeyReleased(rawEvent);
            case BinaryEvent.Types.RAW_KEY_TYPED -> processKeyTyped(rawEvent);
            case BinaryEvent.Types.RAW_MOUSE_PRESSED -> processMousePressed(rawEvent);
            case BinaryEvent.Types.RAW_MOUSE_RELEASED -> processMouseReleased(rawEvent);
            case BinaryEvent.Types.RAW_MOUSE_MOVED -> processMouseMoved(rawEvent);
            case BinaryEvent.Types.RAW_MOUSE_DRAGGED -> processMouseDragged(rawEvent);
            case BinaryEvent.Types.RAW_MOUSE_ENTERED -> processMouseEntered(rawEvent);
            case BinaryEvent.Types.RAW_MOUSE_EXITED -> processMouseExited(rawEvent);
            case BinaryEvent.Types.RAW_MOUSE_SCROLL -> processMouseScroll(rawEvent);
        }
    }
    
    private void processKeyPressed(BinaryEvent event) {
        int keyCode = event.getPayloadAsInt(0);
        byte modifiers = event.getPayloadAsByte(1);
        
        // Update modifier state
        updateModifiers(modifiers);
        
        // Emit high-level navigation events based on key + modifiers
        boolean shift = BitFlagStateMachine.anySet(inputState.getState(), InputBits.SHIFT_DOWN);
        boolean ctrl = BitFlagStateMachine.anySet(inputState.getState(), InputBits.CTRL_DOWN);
        
        switch (keyCode) {
            case 37 -> { // LEFT
                outputQueue.enqueue(BinaryEvents.cursorMoveCharBackward());
                if (shift) {
                    outputQueue.enqueue(BinaryEvents.selectionExtend());
                } else {
                    outputQueue.enqueue(BinaryEvents.selectionClear());
                }
            }
            
            case 39 -> { // RIGHT
                outputQueue.enqueue(BinaryEvents.cursorMoveCharForward());
                if (shift) {
                    outputQueue.enqueue(BinaryEvents.selectionExtend());
                } else {
                    outputQueue.enqueue(BinaryEvents.selectionClear());
                }
            }
 
            case 36 -> { // HOME
                if (ctrl) {
                    outputQueue.enqueue(BinaryEvents.cursorMoveDocStart());
                } else {
                    outputQueue.enqueue(BinaryEvents.cursorMoveLineStart());
                }
                if (shift) {
                    outputQueue.enqueue(BinaryEvents.selectionExtend());
                }
            }
            
            case 35 -> { // END
                if (ctrl) {
                    outputQueue.enqueue(BinaryEvents.cursorMoveDocEnd());
                } else {
                    outputQueue.enqueue(BinaryEvents.cursorMoveLineEnd());
                }
                if (shift) {
                    outputQueue.enqueue(BinaryEvents.selectionExtend());
                }
            }
            
            case 8 -> { // BACKSPACE
                outputQueue.enqueue(BinaryEvents.textDeleteCharBefore(0)); // offset computed later
            }
            
            case 127 -> { // DELETE
                outputQueue.enqueue(BinaryEvents.textDeleteCharAfter(0));
            }
            
            case 65 -> { // A
                if (ctrl) {
                    outputQueue.enqueue(BinaryEvents.selectionAll());
                }
            }
            
            // ... other key mappings
        }
    }

    
    private void processKeyReleased(BinaryEvent event) {
        byte modifiers = event.getPayloadAsByte(1);
        updateModifiers(modifiers);
    }
    
    private void processKeyTyped(BinaryEvent event) {
        String character = event.getPayloadAsString(0);
        outputQueue.enqueue(BinaryEvents.textInsert(0, character)); // offset computed later
    }
    
    private void processMousePressed(BinaryEvent event) {
        int x = event.getPayloadAsInt(0);
        int y = event.getPayloadAsInt(1);
        byte button = event.getPayloadAsByte(2);
        long timestamp = event.getPayloadAsLong(3);
        
        // Update state
        inputState.addState(InputBits.MOUSE_DOWN);
        updateButton(button, true);
        
        mouseDownX = x;
        mouseDownY = y;
        dragStarted = false;
        
        // Detect multi-click
        detectClick(x, y, timestamp);
        
        // Emit cursor move
        outputQueue.enqueue(BinaryEvents.cursorMoveToPosition(x, y));
    }
     
    private void processMouseReleased(BinaryEvent event) {
        int x = event.getPayloadAsInt(0);
        int y = event.getPayloadAsInt(1);
        byte button = event.getPayloadAsByte(2);
        
        // Update state
        inputState.removeState(InputBits.MOUSE_DOWN);
        updateButton(button, false);
        
        if (dragStarted) {
            outputQueue.enqueue(BinaryEvents.dragEnd(x, y));
        }
    }
    
    private void processMouseMoved(BinaryEvent event) {
        int x = event.getPayloadAsInt(0);
        int y = event.getPayloadAsInt(1);
        
        inputState.addState(InputBits.MOUSE_MOVED);
    }
    
    private void processMouseDragged(BinaryEvent event) {
        int x = event.getPayloadAsInt(0);
        int y = event.getPayloadAsInt(1);
        
        inputState.addState(InputBits.MOUSE_MOVED);
        
        // Check if we should start dragging
        if (BitFlagStateMachine.anySet(inputState.getState(), InputBits.MOUSE_DOWN) && !dragStarted) {
            int dx = Math.abs(x - mouseDownX);
            int dy = Math.abs(y - mouseDownY);
            
            if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) {
                dragStarted = true;
                inputState.addState(InputBits.DRAGGING);
                outputQueue.enqueue(BinaryEvents.dragStart(mouseDownX, mouseDownY));
            }
        }

        if (dragStarted) {
            outputQueue.enqueue(BinaryEvents.dragUpdate(x, y));
            outputQueue.enqueue(BinaryEvents.cursorMoveToPosition(x, y));
            outputQueue.enqueue(BinaryEvents.selectionExtend());
        }
    }
    
    private void processMouseEntered(BinaryEvent event) {
        // Could emit hover events
    }
    
    private void processMouseExited(BinaryEvent event) {
        // Could emit unhover events
    }
    
    private void processMouseScroll(BinaryEvent event) {
        int x = event.getPayloadAsInt(0);
        int y = event.getPayloadAsInt(1);
        int deltaX = event.getPayloadAsInt(2);
        int deltaY = event.getPayloadAsInt(3);
        
        // Apply scroll to current viewport position (computed by StateProcessor)
        outputQueue.enqueue(BinaryEvents.scrollDelta(deltaX, deltaY));
    }
    
    private void detectClick(int x, int y, long timestamp) {
        long timeSinceLastClick = timestamp - lastClickTime;
        int distanceFromLastClick = (int) Math.sqrt(
            Math.pow(x - lastClickX, 2) + Math.pow(y - lastClickY, 2)
        );
        
        if (timeSinceLastClick < DOUBLE_CLICK_TIME_MS && 
            distanceFromLastClick < DOUBLE_CLICK_DISTANCE) {
            clickCount++;
        } else {
            clickCount = 1;
        }
        
        lastClickTime = timestamp;
        lastClickX = x;
        lastClickY = y;
        
        // Emit click event
        if (clickCount == 1) {
            outputQueue.enqueue(BinaryEvents.click(x, y));
        } else if (clickCount == 2) {
            outputQueue.enqueue(BinaryEvents.doubleClick(x, y));
            outputQueue.enqueue(BinaryEvents.selectionWord());
        } else if (clickCount == 3) {
            outputQueue.enqueue(BinaryEvents.tripleClick(x, y));
            outputQueue.enqueue(BinaryEvents.selectionLine());
        }
    }
    
    private void updateModifiers(byte modifiers) {
        if ((modifiers & 1) != 0) {
            inputState.addState(InputBits.SHIFT_DOWN);
        } else {
            inputState.removeState(InputBits.SHIFT_DOWN);
        }
        
        if ((modifiers & 2) != 0) {
            inputState.addState(InputBits.CTRL_DOWN);
        } else {
            inputState.removeState(InputBits.CTRL_DOWN);
        }
        
        if ((modifiers & 4) != 0) {
            inputState.addState(InputBits.ALT_DOWN);
        } else {
            inputState.removeState(InputBits.ALT_DOWN);
        }
        
        if ((modifiers & 8) != 0) {
            inputState.addState(InputBits.META_DOWN);
        } else {
            inputState.removeState(InputBits.META_DOWN);
        }
    }
    
    private void updateButton(byte button, boolean pressed) {
        long flag = switch (button) {
            case 1 -> InputBits.PRIMARY_BTN;
            case 2 -> InputBits.SECONDARY_BTN;
            case 3 -> InputBits.MIDDLE_BTN;
            default -> 0L;
        };
        
        if (pressed) {
            inputState.addState(flag);
        } else {
            inputState.removeState(flag);
        }
    }
    
    public BitFlagStateMachine getInputState() {
        return inputState;
    }
}
