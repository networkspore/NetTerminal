package io.netnotes.gui.core.executors.commands;

import io.netnotes.gui.core.executors.CommandCenter;
import io.netnotes.gui.core.executors.CommandContext;

/**
 * ClearCommand - Clears the terminal
 */
public class ClearCommand extends AbstractCommand {
    
    @SuppressWarnings("unused")
    private final CommandCenter m_commandCenter;
    
    public ClearCommand(CommandCenter commandCenter) {
        super("clear", "Clear the terminal", "clear", "cls");
        this.m_commandCenter = commandCenter;
    }
    
    @Override
    public void execute(String[] args, CommandContext context) {
        // Clear via Ctrl+L simulation is already implemented in CommandCenter
        context.system("Screen cleared");
    }
}