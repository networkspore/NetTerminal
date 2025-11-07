package io.netnotes.gui.core.executors;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * CommandRegistry - Manages available commands
 */
public class CommandRegistry {
    private final Map<String, Command> m_commands;
    private final Map<String, String> m_aliases;
    
    public CommandRegistry() {
        this.m_commands = new ConcurrentHashMap<>();
        this.m_aliases = new ConcurrentHashMap<>();
    }
    
    public void register(Command command) {
        m_commands.put(command.getName(), command);
        for (String alias : command.getAliases()) {
            m_aliases.put(alias, command.getName());
        }
    }
    
    public void unregister(String name) {
        Command cmd = m_commands.remove(name);
        if (cmd != null) {
            for (String alias : cmd.getAliases()) {
                m_aliases.remove(alias);
            }
        }
    }
    
    public Command getCommand(String name) {
        String actualName = m_aliases.getOrDefault(name, name);
        return m_commands.get(actualName);
    }
    
    public List<String> getAllCommandNames() {
        return new ArrayList<>(m_commands.keySet());
    }
    
    public List<String> findCompletions(String prefix) {
        if (prefix.isEmpty()) {
            return getAllCommandNames();
        }
        
        return m_commands.keySet().stream()
            .filter(name -> name.startsWith(prefix))
            .sorted()
            .collect(Collectors.toList());
    }
    
    public Map<String, Command> getAllCommands() {
        return new HashMap<>(m_commands);
    }
}