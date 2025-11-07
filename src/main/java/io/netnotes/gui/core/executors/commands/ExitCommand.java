package io.netnotes.gui.core.executors.commands;

import io.netnotes.gui.core.executors.CommandCenter;
import io.netnotes.gui.core.executors.CommandContext;

/**
 * ExitCommand - Closes the command center
 */
public class ExitCommand extends AbstractCommand {
    private final CommandCenter m_commandCenter;
    
    public ExitCommand(CommandCenter commandCenter) {
        super("exit", "Close command center", "exit", "quit", "q");
        this.m_commandCenter = commandCenter;
    }
    
    @Override
    public void execute(String[] args, CommandContext context) {
        context.system("Closing command center...");
        m_commandCenter.deactivate();
    }
}