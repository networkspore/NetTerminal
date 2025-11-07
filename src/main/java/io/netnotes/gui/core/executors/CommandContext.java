package io.netnotes.gui.core.executors;


/**
 * CommandContext - Provides context and output methods for commands
 */
public class CommandContext {
    private final CommandCenter m_commandCenter;
    
    public CommandContext(CommandCenter commandCenter) {
        this.m_commandCenter = commandCenter;
    }
    
    public void output(String text) {
        m_commandCenter.outputLine(text, CommandCenter.OutputType.OUTPUT);
    }
    
    public void error(String text) {
        m_commandCenter.outputLine(text, CommandCenter.OutputType.ERROR);
    }
    
    public void warning(String text) {
        m_commandCenter.outputLine(text, CommandCenter.OutputType.WARNING);
    }
    
    public void system(String text) {
        m_commandCenter.outputLine(text, CommandCenter.OutputType.SYSTEM);
    }
    
    public CommandCenter getCommandCenter() {
        return m_commandCenter;
    }
}