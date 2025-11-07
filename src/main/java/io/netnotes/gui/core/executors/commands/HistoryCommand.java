package io.netnotes.gui.core.executors.commands;

import java.util.List;

import io.netnotes.gui.core.executors.CommandCenter;
import io.netnotes.gui.core.executors.CommandContext;
import io.netnotes.gui.core.executors.CommandException;

/**
 * HistoryCommand - Shows command history
 */
public class HistoryCommand extends AbstractCommand {
    private final CommandCenter m_commandCenter;
    
    public HistoryCommand(CommandCenter commandCenter) {
        super("history", "Show command history", "history [n]", "hist");
        this.m_commandCenter = commandCenter;
    }
    
    @Override
    public void execute(String[] args, CommandContext context) throws CommandException {
        List<String> history = m_commandCenter.getCommandHistory();
        
        int count = history.size();
        if (args.length > 0) {
            try {
                count = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                throw new CommandException("Invalid number: " + args[0]);
            }
        }
        
        int start = Math.max(0, history.size() - count);
        for (int i = start; i < history.size(); i++) {
            context.output(String.format("%4d  %s", i + 1, history.get(i)));
        }
    }
}