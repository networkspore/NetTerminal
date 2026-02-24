package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.SizePreference;

public interface TerminalSizeable {
    
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

    default boolean isHiddenManaged() { return true;  }
}