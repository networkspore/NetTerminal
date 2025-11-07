package io.netnotes.gui.core.executors.commands;

import io.netnotes.gui.core.executors.Command;

/**
 * Abstract base command for common functionality
 */
public abstract class AbstractCommand implements Command {
    private final String m_name;
    private final String m_description;
    private final String m_usage;
    private final String[] m_aliases;
    
    protected AbstractCommand(String name, String description, String usage, String... aliases) {
        this.m_name = name;
        this.m_description = description;
        this.m_usage = usage;
        this.m_aliases = aliases;
    }
    
    @Override
    public String getName() {
        return m_name;
    }
    
    @Override
    public String getDescription() {
        return m_description;
    }
    
    @Override
    public String getUsage() {
        return m_usage;
    }
    
    @Override
    public String[] getAliases() {
        return m_aliases;
    }
}