package io.netnotes.gui.core.executors;

/**
 * CommandException - Thrown when command execution fails
 */
public class CommandException extends Exception {
    public CommandException(String message) {
        super(message);
    }
    
    public CommandException(String message, Throwable cause) {
        super(message, cause);
    }
}