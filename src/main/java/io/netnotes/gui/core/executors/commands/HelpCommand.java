package io.netnotes.gui.core.executors.commands;

import java.util.Collections;
import java.util.List;

import io.netnotes.gui.core.executors.*;

/**
 * HelpCommand - Shows available commands
 */
public class HelpCommand extends AbstractCommand {
    private final CommandRegistry m_registry;
    
    public HelpCommand(CommandRegistry registry) {
        super("help", "Show available commands", "help [command]", "?");
        this.m_registry = registry;
    }
    
    @Override
    public void execute(String[] args, CommandContext context) {
        if (args.length == 0) {
            context.output("Available commands:");
            List<String> commands = m_registry.getAllCommandNames();
            Collections.sort(commands);
            
            for (String cmdName : commands) {
                Command cmd = m_registry.getCommand(cmdName);
                context.output(String.format("  %-15s %s", cmdName, cmd.getDescription()));
            }
            context.output("");
            context.output("Type 'help <command>' for more information");
        } else {
            String cmdName = args[0];
            Command cmd = m_registry.getCommand(cmdName);
            
            if (cmd == null) {
                context.error("Unknown command: " + cmdName);
            } else {
                context.output("Command: " + cmd.getName());
                context.output("Description: " + cmd.getDescription());
                context.output("Usage: " + cmd.getUsage());
                if (cmd.getAliases().length > 0) {
                    context.output("Aliases: " + String.join(", ", cmd.getAliases()));
                }
            }
        }
    }
}