package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.SizePreference;

public interface TerminalSizeable {
    
    SizePreference getWidthPreference();
    SizePreference getHeightPreference();
    default int getMinWidth(){ return 1; };
    default int getMinHeight() { return 1; };
    TerminalInsets getInsets();

    // Percentage-based sizing support
    default double getPercentWidth() { return 0; }
    default void setPercentWidth(double percent) { }
    default double getPercentHeight() { return 0; }
    default void setPercentHeight(double percent) { }

    default boolean isHiddenManaged() { return true;  }
}