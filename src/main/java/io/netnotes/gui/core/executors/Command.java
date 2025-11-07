package io.netnotes.gui.core.executors;

/**
 * Command interface
 */
public interface Command {
    String getName();
    String getDescription();
    String getUsage();
    String[] getAliases();
    void execute(String[] args, CommandContext context) throws CommandException;
}
