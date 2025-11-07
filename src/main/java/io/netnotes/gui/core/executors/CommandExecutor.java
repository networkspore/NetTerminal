package io.netnotes.gui.core.executors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CommandExecutor - Parses and executes command lines
 */
public class CommandExecutor {
    private final CommandCenter m_commandCenter;
    private final CommandContext m_context;
    
    public CommandExecutor(CommandCenter commandCenter) {
        this.m_commandCenter = commandCenter;
        this.m_context = new CommandContext(commandCenter);
    }
    
    public void execute(String commandLine) {
        try {
            String[] parts = parseCommandLine(commandLine);
            if (parts.length == 0) {
                return;
            }
            
            String commandName = parts[0];
            String[] args = Arrays.copyOfRange(parts, 1, parts.length);
            
            CommandRegistry registry = m_commandCenter.getCommandRegistry();
            Command command = registry.getCommand(commandName);
            
            if (command == null) {
                m_context.error("Command not found: " + commandName);
                m_context.system("Type 'help' for available commands");
                return;
            }
            
            command.execute(args, m_context);
            
        } catch (CommandException e) {
            m_context.error("Error: " + e.getMessage());
        } catch (Exception e) {
            m_context.error("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Parse command line respecting quotes and escapes
     */
    private String[] parseCommandLine(String commandLine) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escape = false;
        
        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            
            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }
            
            if (c == '\\') {
                escape = true;
                continue;
            }
            
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            
            if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            
            current.append(c);
        }
        
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        
        return parts.toArray(new String[0]);
    }
}