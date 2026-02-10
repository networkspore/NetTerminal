package io.netnotes.terminal.layout;

public interface TerminalSizeable {
    
    public enum SizePreference {
        STATIC,
        FILL,         // Take all available space (equivalent to PERCENT with 100%)
        FIT_CONTENT,  // Use preferred/requested size
        PERCENT,      // Use percentWidth or percentHeight fields
        INHERIT       // Use parent's default (or null for same effect)
    }
    
    SizePreference getWidthPreference();
    SizePreference getHeightPreference();
    default int getMinWidth(){ return 1; };
    default int getMinHeight() { return 1; };
    int getPreferredWidth();
    int getPreferredHeight();
    TerminalInsets getInsets();

    // Percentage-based sizing support
    default float getPercentWidth() { return 0f; }
    default void setPercentWidth(float percent) { }
    default float getPercentHeight() { return 0f; }
    default void setPercentHeight(float percent) { }
}