package io.netnotes.terminal.layout;

public interface TerminalSizeable {
    
    public enum SizePreference {
        FILL,         // Take all available space
        FIT_CONTENT,  // Use preferred/requested size
        INHERIT       // Use parent's default (or null for same effect)
    }
    
    SizePreference getWidthPreference();
    SizePreference getHeightPreference();
    default int getMinWidth(){ return 1; };
    default int getMinHeight() { return 1; };
    int getPreferredWidth();
    int getPreferredHeight();
}