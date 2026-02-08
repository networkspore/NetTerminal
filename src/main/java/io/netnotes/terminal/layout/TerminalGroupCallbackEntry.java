package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.layout.GroupCallbackEntry;
import io.netnotes.engine.ui.layout.GroupCallbackPredicate;

public class TerminalGroupCallbackEntry extends GroupCallbackEntry
<
    TerminalLayoutGroup,
    TerminalLayoutGroupCallback,
    TerminalGroupCallbackEntry
>{
    public TerminalGroupCallbackEntry(
        String callbackId,
        TerminalLayoutGroupCallback callback
    ){
        super(callbackId, callback);
    }

     public TerminalGroupCallbackEntry(
        GroupCallbackPredicate<TerminalLayoutGroup> predicate, 
        TerminalLayoutGroupCallback callback
    ) {
        super(predicate, callback);
    }

    /**
     * 
     * @param callbackId existing calback with an equal name will be replaced
     * @param predicate callback will run if returns true, can be null
     * @param callback callback for group
     * @param priority priority to run callback
     */
    public TerminalGroupCallbackEntry(
        String callbackId, 
        GroupCallbackPredicate<TerminalLayoutGroup> predicate, 
        TerminalLayoutGroupCallback callback,
        int priority
    ) {
        super(callbackId, predicate, callback, priority);
    }

    public TerminalGroupCallbackEntry(
        String callbackId, 
        GroupCallbackPredicate<TerminalLayoutGroup> predicate, 
        TerminalLayoutGroupCallback callback
    ) {
        super(callbackId, predicate, callback);
    }

   
    public TerminalGroupCallbackEntry(TerminalLayoutGroupCallback callback) {
        super(callback);
    }
    
}
