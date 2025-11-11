package io.netnotes.gui.nvg.core;


 /**
     * Input modes - progressive levels of control
     */
    public enum InputMode {
        /**
         * CommandCenter filters and parses input (DEFAULT)
         * - Handles line editing (backspace, arrows, history)
         * - Maintains input buffer
         * - Sends complete lines to process
         * Use for: Simple commands, text input
         */
        FILTERED,
        
        /**
         * Process receives RawEvent objects
         * - Process manages its own buffer
         * - CommandCenter still intercepts events
         * Use for: Text editors, custom key bindings
         */
        RAW_EVENTS,
        
        /**
         * Process receives direct GLFW callbacks
         * - Bypasses CommandCenter event processing
         * - Still goes through GLFW
         * Use for: Games, real-time input
         */
        DIRECT_GLFW,
        
        /**
         * Process receives HID reports from NoteDaemon
         * - Requires NoteDaemon running
         * - Exclusive hardware access
         * - Falls back to RAW_EVENTS if unavailable
         * Use for: Password entry, secure authentication
         */
        SECURE
    }