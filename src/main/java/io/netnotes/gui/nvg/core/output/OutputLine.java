package io.netnotes.gui.nvg.core.output;

    /**
     * Output line data structure
     */
    public class OutputLine {
        public final String text;
        public final OutputType type;
        public final long timestamp;
        
        public OutputLine(String text, OutputType type, long timestamp) {
            this.text = text;
            this.type = type;
            this.timestamp = timestamp;
        }
    }