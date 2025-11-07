package io.netnotes.gui.core.executors.commands;

import io.netnotes.gui.core.executors.CommandCenter;
import io.netnotes.gui.core.executors.CommandContext;

/**
 * EchoCommand - Echoes arguments
 */
public class EchoCommand extends AbstractCommand {
    @SuppressWarnings("unused")
    private final CommandCenter m_commandCenter;
    
    public EchoCommand(CommandCenter commandCenter) {
        super("echo", "Print text", "echo <text>", "print");
        this.m_commandCenter = commandCenter;
    }
    
    @Override
    public void execute(String[] args, CommandContext context) {
        if (args.length == 0) {
            context.output("");
        } else {
            context.output(String.join(" ", args));
        }
    }
}