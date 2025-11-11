package io.netnotes.gui.nvg.core;

import io.netnotes.gui.nvg.input.*;
import io.netnotes.gui.nvg.resources.FontManager;
import io.netnotes.security.SecureInputClient;
import io.netnotes.security.SecureInputEvent;

import java.util.*;
import java.util.concurrent.*;

import static org.lwjgl.glfw.GLFW.*;

/**
 * CommandCenter - A shell/REPL process (like bash)
 * 
 * This is NOT the root controller - it's a process that:
 * - Interprets commands
 * - Spawns child processes
 * - Provides scripting/REPL environment
 * - Renders a terminal UI
 * 
 * It runs INSIDE ProcessContainer, just like any other process.
 */
public class CommandCenter extends Process {
    // Parent container
    private final ProcessContainer container;
    
    // Visual rendering
    private final long vg;
    private final FontManager fontManager;
    private final List<OutputLine> outputLines;
    
    // Shell state
    private final StringBuilder inputBuffer;
    private int cursorPosition;
    private final List<String> commandHistory;
    private int historyPosition;
    
    // Child processes spawned by this shell
    private final Map<Integer, ChildProcess> children = new ConcurrentHashMap<>();
    
    // Secure input for child processes
    private SecureInputClient secureClient;
    private ChildProcess secureInputTarget;
    
    public CommandCenter(ProcessContainer container, long vg, FontManager fontManager) {
        this.container = container;
        this.vg = vg;
        this.fontManager = fontManager;
        this.outputLines = new CopyOnWriteArrayList<>();
        this.inputBuffer = new StringBuilder();
        this.commandHistory = new ArrayList<>();
        this.cursorPosition = 0;
        this.historyPosition = 0;
    }
    
    @Override
    public ProcessInputHandler getInputHandler() {
        return new ProcessInputHandler() {
            @Override
            public InputMode getInputMode() {
                // Shell uses RAW_EVENTS to handle its own line editing
                return InputMode.RAW_EVENTS;
            }
            
            @Override
            public void handleRawEvent(RawEvent event) {
                handleShellInput(event);
            }
        };
    }
    
    @Override
    public void execute() {
        output("NetNotes Shell v1.0", OutputType.SYSTEM);
        output("Type 'help' for commands", OutputType.SYSTEM);
        
        // Shell runs forever until killed
        while (!isKilled()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
        
        // Cleanup
        killAllChildren();
    }
    
    /**
     * Handle input when shell has focus
     */
    private void handleShellInput(RawEvent event) {
        // Use InputRecordReader for structured access
        InputRecordReader reader = event.getReader();
        
        if (reader.isKeyDown()) {
            int key = reader.getKey();
            
            switch (key) {
                case GLFW_KEY_ENTER:
                    executeCommand(inputBuffer.toString());
                    inputBuffer.setLength(0);
                    cursorPosition = 0;
                    break;
                    
                case GLFW_KEY_BACKSPACE:
                    if (cursorPosition > 0) {
                        inputBuffer.deleteCharAt(--cursorPosition);
                    }
                    break;
                    
                case GLFW_KEY_LEFT:
                    if (cursorPosition > 0) cursorPosition--;
                    break;
                    
                case GLFW_KEY_RIGHT:
                    if (cursorPosition < inputBuffer.length()) cursorPosition++;
                    break;
                    
                case GLFW_KEY_UP:
                    navigateHistory(-1);
                    break;
                    
                case GLFW_KEY_DOWN:
                    navigateHistory(1);
                    break;
                    
                case GLFW_KEY_HOME:
                    cursorPosition = 0;
                    break;
                    
                case GLFW_KEY_END:
                    cursorPosition = inputBuffer.length();
                    break;
                    
                case GLFW_KEY_C:
                    if (reader.hasControl()) {
                        // Ctrl+C - kill foreground child
                        killForegroundChild();
                    }
                    break;
            }
        } else if (reader.isKeyChar()) {
            char ch = (char) reader.getCodepoint();
            inputBuffer.insert(cursorPosition++, ch);
        }
    }
    
    /**
     * Navigate command history
     */
    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty()) return;
        
        historyPosition = Math.max(0, Math.min(commandHistory.size(), 
                                               historyPosition + direction));
        
        if (historyPosition < commandHistory.size()) {
            inputBuffer.setLength(0);
            inputBuffer.append(commandHistory.get(historyPosition));
            cursorPosition = inputBuffer.length();
        }
    }
    
    /**
     * Execute a command - spawn child process
     */
    private void executeCommand(String line) {
        line = line.trim();
        if (line.isEmpty()) return;
        
        commandHistory.add(line);
        historyPosition = commandHistory.size();
        
        output("> " + line, OutputType.INPUT);
        
        // Parse command
        String[] parts = line.split("\\s+");
        String command = parts[0];
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        
        // Check for built-in commands
        if (handleBuiltIn(command, args)) {
            return;
        }
        
        // Create child process
        ChildProcess child = createChildProcess(command, args);
        if (child == null) {
            error("Unknown command: " + command);
            return;
        }
        
        // Spawn child in container
        int pid = container.startProcess(child);
        children.put(pid, child);
        
        // If child needs secure input, set it up
        if (child.getInputHandler().getInputMode() == InputMode.SECURE) {
            setupSecureInputForChild(child);
        }
    }
    
    /**
     * Handle built-in shell commands
     */
    private boolean handleBuiltIn(String command, String[] args) {
        return switch (command) {
            case "help" -> {
                showHelp();
                yield true;
            }
            case "history" -> {
                showHistory();
                yield true;
            }
            case "clear" -> {
                outputLines.clear();
                yield true;
            }
            case "exit" -> {
                kill();
                yield true;
            }
            case "jobs" -> {
                showJobs();
                yield true;
            }
            case "kill" -> {
                if (args.length > 0) {
                    try {
                        int pid = Integer.parseInt(args[0]);
                        container.killProcess(pid);
                        output("Killed process " + pid, OutputType.SYSTEM);
                    } catch (NumberFormatException e) {
                        error("Invalid PID: " + args[0]);
                    }
                }
                yield true;
            }
            default -> false;
        };
    }
    
    /**
     * Create a child process based on command name
     */
    private ChildProcess createChildProcess(String command, String[] args) {
        // This would be implemented with actual process classes
        // For now, returning null to indicate "command not found"
        return null;
        
        /* Example implementation:
        return switch (command) {
            case "ls" -> new LsProcess(this, args);
            case "pwd" -> new PwdProcess(this);
            case "cd" -> new CdProcess(this, args);
            case "password" -> new PasswordProcess(this);
            case "edit" -> new EditorProcess(this, args);
            default -> null;
        };
        */
    }
    
    /**
     * Set up secure input for a child process
     */
    private void setupSecureInputForChild(ChildProcess child) {
        if (!SecureInputClient.isAvailable()) {
            warning("NoteDaemon not available - child will use fallback");
            return;
        }
        
        try {
            secureClient = new SecureInputClient();
            secureInputTarget = child;
            
            secureClient.addListener(new SecureInputClient.KeyEventListener() {
                @Override
                public void onKeyPressed(int scancode, int modifiers) {
                    if (secureInputTarget != null && secureInputTarget.isAlive()) {
                        SecureInputEvent event = new SecureInputEvent(
                            SecureInputEvent.Type.KEY_PRESSED,
                            scancode,
                            modifiers
                        );
                        secureInputTarget.getInputHandler().handleSecureInput(event);
                    }
                }
                
                @Override
                public void onKeyReleased(int scancode, int modifiers) {
                    if (secureInputTarget != null && secureInputTarget.isAlive()) {
                        SecureInputEvent event = new SecureInputEvent(
                            SecureInputEvent.Type.KEY_RELEASED,
                            scancode,
                            modifiers
                        );
                        secureInputTarget.getInputHandler().handleSecureInput(event);
                    }
                }
                
                @Override
                public void onDeviceDisconnected() {
                    error("Keyboard disconnected!");
                    teardownSecureInput();
                }
            });
            
            if (secureClient.requestKeyboard()) {
                // Start reading in background
                new Thread(() -> {
                    try {
                        secureClient.readEvents();
                    } catch (Exception e) {
                        error("Secure input error: " + e.getMessage());
                    } finally {
                        teardownSecureInput();
                    }
                }, "SecureInput-Reader").start();
                
                output("Secure input activated", OutputType.SYSTEM);
            }
        } catch (Exception e) {
            warning("Failed to activate secure input: " + e.getMessage());
        }
    }
    
    /**
     * Tear down secure input
     */
    private void teardownSecureInput() {
        if (secureClient != null) {
            try {
                secureClient.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            secureClient = null;
            secureInputTarget = null;
        }
    }
    
    /**
     * Kill foreground child process (Ctrl+C behavior)
     */
    private void killForegroundChild() {
        Process fg = container.getProcessManager().getForegroundProcess();
        if (fg != null && fg != this) {
            fg.kill();
            output("^C", OutputType.SYSTEM);
        }
    }
    
    /**
     * Kill all child processes on shutdown
     */
    private void killAllChildren() {
        for (ChildProcess child : children.values()) {
            child.kill();
        }
        children.clear();
        teardownSecureInput();
    }
    
    /**
     * Output methods
     */
    public void output(String message, OutputType type) {
        outputLines.add(new OutputLine(message, type, System.currentTimeMillis()));
    }
    
    public void output(String message) {
        output(message, OutputType.OUTPUT);
    }
    
    public void error(String message) {
        output("ERROR: " + message, OutputType.ERROR);
    }
    
    public void warning(String message) {
        output("WARNING: " + message, OutputType.WARNING);
    }
    
    private void showHelp() {
        output("Built-in commands:", OutputType.SYSTEM);
        output("  help     - Show this help", OutputType.SYSTEM);
        output("  history  - Show command history", OutputType.SYSTEM);
        output("  clear    - Clear screen", OutputType.SYSTEM);
        output("  jobs     - List running jobs", OutputType.SYSTEM);
        output("  kill PID - Kill a process", OutputType.SYSTEM);
        output("  exit     - Exit shell", OutputType.SYSTEM);
    }
    
    private void showHistory() {
        for (int i = 0; i < commandHistory.size(); i++) {
            output(String.format("%4d  %s", i + 1, commandHistory.get(i)), 
                   OutputType.OUTPUT);
        }
    }
    
    private void showJobs() {
        output("Running processes:", OutputType.SYSTEM);
        for (Map.Entry<Integer, ChildProcess> entry : children.entrySet()) {
            if (entry.getValue().isAlive()) {
                output(String.format("  [%d] %s", entry.getKey(), 
                       entry.getValue().getClass().getSimpleName()), 
                       OutputType.OUTPUT);
            }
        }
    }
    
    /**
     * Render the terminal UI
     */
    public void render() {
        // Render output lines
        float y = 50;
        for (OutputLine line : outputLines) {
            renderLine(line, y);
            y += 20;
        }
        
        // Render prompt
        renderPrompt(y);
    }
    
    private void renderLine(OutputLine line, float y) {
        // NanoVG rendering implementation would go here
        // nvgText(vg, x, y, line.text);
    }
    
    private void renderPrompt(float y) {
        String prompt = "$ " + inputBuffer.toString();
        // Render with cursor at cursorPosition
        // nvgText(vg, x, y, prompt);
    }

    /**
     * Output type enumeration
     */
    public enum OutputType {
        INPUT,    // User input echo
        OUTPUT,   // Normal output
        ERROR,    // Error messages
        WARNING,  // Warning messages
        SYSTEM    // System messages
    }
    
    /**
     * Output line data structure
     */
    public static class OutputLine {
        public final String text;
        public final OutputType type;
        public final long timestamp;
        
        public OutputLine(String text, OutputType type, long timestamp) {
            this.text = text;
            this.type = type;
            this.timestamp = timestamp;
        }
    }
}