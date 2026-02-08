package io.netnotes.terminal.layout;

/**
 * TerminalLayoutable - optional layout overrides for terminal renderables.
 *
 * Extends TerminalSizeable to allow components to:
 * - Override sizing preferences
 * - Opt out of parent-managed hidden state changes
 */
public interface TerminalLayoutable extends TerminalSizeable {

    /**
     * Whether the parent layout should clear hidden state when the child is in-bounds.
     * Return false to preserve the component's hidden state when it re-enters bounds.
     */
    default boolean isHiddenManaged() {
        return true;
    }
}
