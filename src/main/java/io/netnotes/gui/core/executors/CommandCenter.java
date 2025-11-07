package io.netnotes.gui.core.executors;

import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.glfw.GLFW.*;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import io.netnotes.gui.core.executors.commands.*;

import io.netnotes.engine.noteBytes.*;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.gui.nvg.resources.FontManager;
import io.netnotes.gui.nvg.uiNode.input.InputPacket;
import io.netnotes.gui.nvg.uiNode.input.RawEvent;

/**
 * CommandCenter - Modal overlay for Unix-like command interface.
 * Locks the application and provides a command-line interface for system control.
 */
public class CommandCenter {
    
    // State flags - Using int positions for cleaner code
    public static final int STATE_INACTIVE = 0;
    public static final int STATE_ACTIVE = 1;
    public static final int STATE_VISIBLE = 2;
    public static final int STATE_ANIMATING = 3;
    public static final int STATE_INPUT_FOCUSED = 4;
    public static final int STATE_AUTOCOMPLETE_VISIBLE = 5;
    public static final int STATE_HISTORY_BROWSING = 6;
    
    private final long m_vg;
    private final FontManager m_fontManager;
    private final BitFlagStateMachine m_stateMachine;
    private final CommandRegistry m_commandRegistry;
    private final CommandExecutor m_executor;
    
    // Layout
    private float m_windowWidth;
    private float m_windowHeight;
    private float m_overlayAlpha = 0.0f;
    private float m_terminalY = 0.0f;
    
    // Terminal state
    private final StringBuilder m_inputBuffer;
    private int m_cursorPosition;
    private final List<String> m_commandHistory;
    private int m_historyIndex;
    private final List<OutputLine> m_outputLines;
    private int m_scrollOffset;
    
    // Auto-completion
    private List<String> m_completionCandidates;
    private int m_completionIndex;
    
    // Visual config
    private static final float TERMINAL_HEIGHT_RATIO = 0.6f;
    private static final float ANIMATION_SPEED = 8.0f;
    private static final int MAX_OUTPUT_LINES = 1000;
    private static final int VISIBLE_OUTPUT_LINES = 20;
    
    // Input handling
    private final BlockingQueue<RawEvent> m_inputQueue;
    private long m_lastBlinkTime;
    private boolean m_cursorVisible;
    
    public CommandCenter(long vg, FontManager fontManager, float windowWidth, float windowHeight) {
        this.m_vg = vg;
        this.m_fontManager = fontManager;
        this.m_windowWidth = windowWidth;
        this.m_windowHeight = windowHeight;
        this.m_stateMachine = new BitFlagStateMachine("CommandCenter");
        this.m_commandRegistry = new CommandRegistry();
        this.m_executor = new CommandExecutor(this);
        
        this.m_inputBuffer = new StringBuilder();
        this.m_cursorPosition = 0;
        this.m_commandHistory = new ArrayList<>();
        this.m_historyIndex = -1;
        this.m_outputLines = new CopyOnWriteArrayList<>();
        this.m_scrollOffset = 0;
        
        this.m_inputQueue = new LinkedBlockingQueue<>();
        this.m_lastBlinkTime = System.currentTimeMillis();
        this.m_cursorVisible = true;
        
        registerBuiltinCommands();
        setupStateTransitions();
        setupStateConstraints();
    }
    
    private void setupStateConstraints() {
        // Cannot be both ACTIVE and INACTIVE simultaneously
        // Note: STATE_INACTIVE = 0 (no bits set), so we only need to ensure
        // that if ACTIVE is set, we're not in the zero state
        // This constraint is more conceptual - enforced by activation/deactivation logic
    }
    
    private void setupStateTransitions() {
        // Import static bit() for cleaner code
        var INACTIVE = BitFlagStateMachine.bit(STATE_INACTIVE);
        var ACTIVE = BitFlagStateMachine.bit(STATE_ACTIVE);
        var VISIBLE = BitFlagStateMachine.bit(STATE_VISIBLE);
        var ANIMATING = BitFlagStateMachine.bit(STATE_ANIMATING);
        var INPUT_FOCUSED = BitFlagStateMachine.bit(STATE_INPUT_FOCUSED);
        
        // When activating, start animation
        m_stateMachine.addTransition(
            INACTIVE, 
            ACTIVE.or(ANIMATING),
            (oldState, newState) -> {
                m_overlayAlpha = 0.0f;
                m_terminalY = -m_windowHeight * TERMINAL_HEIGHT_RATIO;
            }
        );
        
        // When animation completes, focus input
        m_stateMachine.addTransition(
            ACTIVE.or(ANIMATING),
            ACTIVE.or(VISIBLE).or(INPUT_FOCUSED),
            (oldState, newState) -> {
                m_overlayAlpha = 1.0f;
                m_terminalY = 0.0f;
            }
        );
        
        // When deactivating, start close animation
        m_stateMachine.addTransition(
            ACTIVE.or(VISIBLE).or(INPUT_FOCUSED),
            ANIMATING,
            (oldState, newState) -> {
                clearInput();
            }
        );
        
        // When close animation completes, go inactive
        m_stateMachine.addTransition(
            ANIMATING,
            INACTIVE,
            (oldState, newState) -> {
                m_overlayAlpha = 0.0f;
                m_terminalY = -m_windowHeight * TERMINAL_HEIGHT_RATIO;
            }
        );
    }
    
    public void activate() {
        if (!m_stateMachine.hasFlag(STATE_ACTIVE)) {
            m_stateMachine.setState(
                BitFlagStateMachine.bit(STATE_ACTIVE)
                    .or(BitFlagStateMachine.bit(STATE_ANIMATING))
            );
            outputLine("Command Center Active - Type 'help' for commands", OutputType.SYSTEM);
        }
    }
    
    public void deactivate() {
        if (m_stateMachine.hasFlag(STATE_ACTIVE)) {
            m_stateMachine.setState(BitFlagStateMachine.bit(STATE_ANIMATING));
        }
    }
    
    public void toggle() {
        if (m_stateMachine.hasFlag(STATE_ACTIVE)) {
            deactivate();
        } else {
            activate();
        }
    }
    
    public void handleInput(RawEvent event) {
        if (!m_stateMachine.hasFlag(STATE_INPUT_FOCUSED)) {
            return;
        }
        
        try {
            m_inputQueue.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void processInputQueue() {
        List<RawEvent> events = new ArrayList<>();
        m_inputQueue.drainTo(events);
        
        for (RawEvent event : events) {
            processInputEvent(event);
        }
    }
    
    private void processInputEvent(RawEvent event) {
        NoteBytesReadOnly type = event.getType();
        
        if (type.equals(InputPacket.Types.TYPE_KEY_CHAR)) {
            // Text input
            NoteBytes payload = event.getPayload();
            if (payload != null) {
                int codepoint = payload.getAsInt();
                if (codepoint >= 32 && codepoint < 127) { // Printable ASCII
                    insertChar((char) codepoint);
                }
            }
        } else if (type.equals(InputPacket.Types.TYPE_KEY_DOWN)) {
            handleKeyDown(event);
        }
    }
    
    private void handleKeyDown(RawEvent event) {
        NoteBytes payload = event.getPayload();
        if (payload == null) return;
        
        NoteBytesArray payloadArray = payload.getAsNoteBytesArray();
        if (payloadArray == null || payloadArray.size() < 1) return;
        
        int key = payloadArray.get(0).getAsInt();
        int mods = event.getStateFlags();
        
        switch (key) {
            case GLFW_KEY_ESCAPE:
                deactivate();
                break;
                
            case GLFW_KEY_ENTER:
            case GLFW_KEY_KP_ENTER:
                executeCommand();
                break;
                
            case GLFW_KEY_BACKSPACE:
                if (m_cursorPosition > 0) {
                    m_inputBuffer.deleteCharAt(m_cursorPosition - 1);
                    m_cursorPosition--;
                }
                break;
                
            case GLFW_KEY_DELETE:
                if (m_cursorPosition < m_inputBuffer.length()) {
                    m_inputBuffer.deleteCharAt(m_cursorPosition);
                }
                break;
                
            case GLFW_KEY_LEFT:
                if (m_cursorPosition > 0) {
                    m_cursorPosition--;
                }
                break;
                
            case GLFW_KEY_RIGHT:
                if (m_cursorPosition < m_inputBuffer.length()) {
                    m_cursorPosition++;
                }
                break;
                
            case GLFW_KEY_HOME:
                m_cursorPosition = 0;
                break;
                
            case GLFW_KEY_END:
                m_cursorPosition = m_inputBuffer.length();
                break;
                
            case GLFW_KEY_UP:
                navigateHistory(-1);
                break;
                
            case GLFW_KEY_DOWN:
                navigateHistory(1);
                break;
                
            case GLFW_KEY_TAB:
                handleAutoComplete();
                break;
                
            case GLFW_KEY_U:
                if ((mods & InputPacket.StateFlags.MOD_CONTROL) != 0) {
                    // Ctrl+U: Clear line
                    clearInput();
                }
                break;
                
            case GLFW_KEY_K:
                if ((mods & InputPacket.StateFlags.MOD_CONTROL) != 0) {
                    // Ctrl+K: Clear from cursor to end
                    m_inputBuffer.setLength(m_cursorPosition);
                }
                break;
                
            case GLFW_KEY_W:
                if ((mods & InputPacket.StateFlags.MOD_CONTROL) != 0) {
                    // Ctrl+W: Delete word
                    deleteWord();
                }
                break;
                
            case GLFW_KEY_L:
                if ((mods & InputPacket.StateFlags.MOD_CONTROL) != 0) {
                    // Ctrl+L: Clear screen
                    m_outputLines.clear();
                    m_scrollOffset = 0;
                }
                break;
        }
        
        resetCursorBlink();
    }
    
    private void insertChar(char c) {
        m_inputBuffer.insert(m_cursorPosition, c);
        m_cursorPosition++;
        resetCursorBlink();
    }
    
    private void deleteWord() {
        if (m_cursorPosition == 0) return;
        
        int pos = m_cursorPosition - 1;
        // Skip whitespace
        while (pos >= 0 && Character.isWhitespace(m_inputBuffer.charAt(pos))) {
            pos--;
        }
        // Delete word
        while (pos >= 0 && !Character.isWhitespace(m_inputBuffer.charAt(pos))) {
            pos--;
        }
        
        m_inputBuffer.delete(pos + 1, m_cursorPosition);
        m_cursorPosition = pos + 1;
    }
    
    private void clearInput() {
        m_inputBuffer.setLength(0);
        m_cursorPosition = 0;
        m_historyIndex = -1;
        m_completionCandidates = null;
        m_completionIndex = 0;
    }
    
    private void navigateHistory(int direction) {
        if (m_commandHistory.isEmpty()) return;
        
        if (m_historyIndex == -1 && direction < 0) {
            // Start browsing from most recent
            m_historyIndex = m_commandHistory.size() - 1;
        } else {
            m_historyIndex += direction;
        }
        
        if (m_historyIndex < 0) {
            m_historyIndex = 0;
        } else if (m_historyIndex >= m_commandHistory.size()) {
            m_historyIndex = -1;
            clearInput();
            return;
        }
        
        String historicalCommand = m_commandHistory.get(m_historyIndex);
        m_inputBuffer.setLength(0);
        m_inputBuffer.append(historicalCommand);
        m_cursorPosition = m_inputBuffer.length();
    }
    
    private void handleAutoComplete() {
        String currentInput = m_inputBuffer.toString();
        
        if (m_completionCandidates == null) {
            // Generate candidates
            m_completionCandidates = m_commandRegistry.findCompletions(currentInput);
            m_completionIndex = 0;
            
            if (m_completionCandidates.isEmpty()) {
                return;
            }
            
            if (m_completionCandidates.size() == 1) {
                // Single match - complete it
                String completion = m_completionCandidates.get(0);
                m_inputBuffer.setLength(0);
                m_inputBuffer.append(completion);
                m_cursorPosition = m_inputBuffer.length();
                m_completionCandidates = null;
                return;
            }
        } else {
            // Cycle through candidates
            m_completionIndex = (m_completionIndex + 1) % m_completionCandidates.size();
        }
        
        // Show current candidate
        String candidate = m_completionCandidates.get(m_completionIndex);
        m_inputBuffer.setLength(0);
        m_inputBuffer.append(candidate);
        m_cursorPosition = m_inputBuffer.length();
        
        m_stateMachine.setFlag(STATE_AUTOCOMPLETE_VISIBLE);
    }
    
    private void executeCommand() {
        String commandLine = m_inputBuffer.toString().trim();
        
        if (!commandLine.isEmpty()) {
            m_commandHistory.add(commandLine);
            outputLine("> " + commandLine, OutputType.INPUT);
            
            m_executor.execute(commandLine);
        }
        
        clearInput();
        m_completionCandidates = null;
        m_stateMachine.clearFlag(STATE_AUTOCOMPLETE_VISIBLE);
    }
    
    public void outputLine(String text, OutputType type) {
        m_outputLines.add(new OutputLine(text, type, System.currentTimeMillis()));
        
        // Trim old lines
        while (m_outputLines.size() > MAX_OUTPUT_LINES) {
            m_outputLines.remove(0);
        }
        
        // Auto-scroll to bottom
        m_scrollOffset = Math.max(0, m_outputLines.size() - VISIBLE_OUTPUT_LINES);
    }
    
    private void resetCursorBlink() {
        m_lastBlinkTime = System.currentTimeMillis();
        m_cursorVisible = true;
    }
    
    public void update(float deltaTime) {
        processInputQueue();
        
        // Update animations
        if (m_stateMachine.hasFlag(STATE_ANIMATING)) {
            float targetAlpha = m_stateMachine.hasFlag(STATE_ACTIVE) ? 1.0f : 0.0f;
            float targetY = m_stateMachine.hasFlag(STATE_ACTIVE) ? 0.0f : 
                -m_windowHeight * TERMINAL_HEIGHT_RATIO;
            
            m_overlayAlpha += (targetAlpha - m_overlayAlpha) * ANIMATION_SPEED * deltaTime;
            m_terminalY += (targetY - m_terminalY) * ANIMATION_SPEED * deltaTime;
            
            if (Math.abs(m_overlayAlpha - targetAlpha) < 0.01f && 
                Math.abs(m_terminalY - targetY) < 1.0f) {
                m_overlayAlpha = targetAlpha;
                m_terminalY = targetY;
                
                if (m_stateMachine.hasFlag(STATE_ACTIVE)) {
                    m_stateMachine.setState(
                        BitFlagStateMachine.bit(STATE_ACTIVE)
                            .or(BitFlagStateMachine.bit(STATE_VISIBLE))
                            .or(BitFlagStateMachine.bit(STATE_INPUT_FOCUSED))
                    );
                } else {
                    m_stateMachine.clearAllStates();
                }
            }
        }
        
        // Update cursor blink
        long now = System.currentTimeMillis();
        if (now - m_lastBlinkTime > 500) {
            m_cursorVisible = !m_cursorVisible;
            m_lastBlinkTime = now;
        }
    }
    
    public void render() {
        if (!m_stateMachine.hasFlag(STATE_ACTIVE) && !m_stateMachine.hasFlag(STATE_ANIMATING)) {
            return;
        }
        
        nvgSave(m_vg);
        
        // Render darkened overlay
        renderOverlay();
        
        // Render terminal window
        renderTerminal();
        
        nvgRestore(m_vg);
    }
    
    private void renderOverlay() {
        NVGColor overlayColor = NVGColor.create();
        nvgRGBAf(0.0f, 0.0f, 0.0f, 0.7f * m_overlayAlpha, overlayColor);
        
        nvgBeginPath(m_vg);
        nvgRect(m_vg, 0, 0, m_windowWidth, m_windowHeight);
        nvgFillColor(m_vg, overlayColor);
        nvgFill(m_vg);
    }
    
    private void renderTerminal() {
        float terminalHeight = m_windowHeight * TERMINAL_HEIGHT_RATIO;
        float padding = 20.0f;
        
        nvgSave(m_vg);
        nvgTranslate(m_vg, 0, m_terminalY);
        
        // Terminal background
        NVGColor bgColor1 = NVGColor.create();
        NVGColor bgColor2 = NVGColor.create();
        nvgRGBAf(0.1f, 0.1f, 0.12f, 0.95f * m_overlayAlpha, bgColor1);
        nvgRGBAf(0.05f, 0.05f, 0.06f, 0.95f * m_overlayAlpha, bgColor2);
        
        NVGPaint bg = NVGPaint.create();
        nvgBoxGradient(m_vg, 0, 0, m_windowWidth, terminalHeight, 0, 20,
            bgColor1, bgColor2, bg);
        
        nvgBeginPath(m_vg);
        nvgRect(m_vg, 0, 0, m_windowWidth, terminalHeight);
        nvgFillPaint(m_vg, bg);
        nvgFill(m_vg);
        
        // Border
        NVGColor borderColor = NVGColor.create();
        nvgRGBAf(0.3f, 0.6f, 0.8f, m_overlayAlpha, borderColor);
        
        nvgBeginPath(m_vg);
        nvgRect(m_vg, 0, 0, m_windowWidth, terminalHeight);
        nvgStrokeColor(m_vg, borderColor);
        nvgStrokeWidth(m_vg, 2.0f);
        nvgStroke(m_vg);
        
        // Render output lines
        renderOutput(padding, terminalHeight - 80);
        
        // Render input line
        renderInputLine(padding, terminalHeight - 60);
        
        // Render auto-complete suggestions
        if (m_stateMachine.hasFlag(STATE_AUTOCOMPLETE_VISIBLE) && m_completionCandidates != null) {
            renderAutoComplete(padding, terminalHeight - 40);
        }
        
        nvgRestore(m_vg);
    }
    
    private void renderOutput(float x, float maxY) {
        float y = maxY;
        float lineHeight = 20.0f;
        
        int startLine = Math.max(0, m_outputLines.size() - VISIBLE_OUTPUT_LINES - m_scrollOffset);
        int endLine = Math.min(m_outputLines.size(), startLine + VISIBLE_OUTPUT_LINES);
        
        nvgFontFaceId(m_vg, m_fontManager.getFontHandle(FontManager.MONO));
        nvgFontSize(m_vg, 14.0f);
        nvgTextAlign(m_vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        
        for (int i = endLine - 1; i >= startLine; i--) {
            OutputLine line = m_outputLines.get(i);
            NVGColor baseColor = getOutputColor(line.type);
            NVGColor color = NVGColor.create();
            nvgRGBAf(baseColor.r(), baseColor.g(), baseColor.b(), m_overlayAlpha, color);
            nvgFillColor(m_vg, color);
            nvgText(m_vg, x, y, line.text);
            y -= lineHeight;
            
            if (y < 30) break;
        }
    }
    
    private void renderInputLine(float x, float y) {
        nvgFontFaceId(m_vg, m_fontManager.getFontHandle(FontManager.MONO));
        nvgFontSize(m_vg, 16.0f);
        nvgTextAlign(m_vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        
        // Prompt
        NVGColor promptColor = NVGColor.create();
        nvgRGBAf(0.4f, 0.8f, 0.4f, m_overlayAlpha, promptColor);
        nvgFillColor(m_vg, promptColor);
        
        float[] bounds = new float[4];
        nvgTextBounds(m_vg, x, y, "> ", bounds);
        nvgText(m_vg, x, y, "> ");
        
        float inputX = bounds[2] + 5;
        
        // Input text
        String inputText = m_inputBuffer.toString();
        NVGColor textColor = NVGColor.create();
        nvgRGBAf(1.0f, 1.0f, 1.0f, m_overlayAlpha, textColor);
        nvgFillColor(m_vg, textColor);
        nvgText(m_vg, inputX, y, inputText);
        
        // Cursor
        if (m_cursorVisible && m_stateMachine.hasFlag(STATE_INPUT_FOCUSED)) {
            String beforeCursor = inputText.substring(0, m_cursorPosition);
            nvgTextBounds(m_vg, inputX, y, beforeCursor, bounds);
            float cursorX = bounds[2];
            
            NVGColor cursorColor = NVGColor.create();
            nvgRGBAf(0.4f, 0.8f, 0.4f, m_overlayAlpha, cursorColor);
            
            nvgBeginPath(m_vg);
            nvgRect(m_vg, cursorX, y, 2, 18);
            nvgFillColor(m_vg, cursorColor);
            nvgFill(m_vg);
        }
    }
    
    private void renderAutoComplete(float x, float y) {
        if (m_completionCandidates == null || m_completionCandidates.isEmpty()) return;
        
        nvgFontFaceId(m_vg, m_fontManager.getFontHandle(FontManager.MONO));
        nvgFontSize(m_vg, 12.0f);
        nvgTextAlign(m_vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        
        float offsetX = x + 100;
        for (int i = 0; i < Math.min(5, m_completionCandidates.size()); i++) {
            String candidate = m_completionCandidates.get(i);
            boolean isSelected = (i == m_completionIndex);
            
            NVGColor candidateColor = NVGColor.create();
            if (isSelected) {
                nvgRGBAf(1.0f, 1.0f, 0.0f, m_overlayAlpha, candidateColor);
            } else {
                nvgRGBAf(0.6f, 0.6f, 0.6f, m_overlayAlpha, candidateColor);
            }
            
            nvgFillColor(m_vg, candidateColor);
            nvgText(m_vg, offsetX, y, candidate);
            offsetX += 120;
        }
    }
    
    private NVGColor getOutputColor(OutputType type) {
        NVGColor color = NVGColor.create();
        switch (type) {
            case INPUT: 
                nvgRGBf(0.4f, 0.8f, 0.4f, color);
                break;
            case OUTPUT: 
                nvgRGBf(1.0f, 1.0f, 1.0f, color);
                break;
            case ERROR: 
                nvgRGBf(1.0f, 0.3f, 0.3f, color);
                break;
            case WARNING: 
                nvgRGBf(1.0f, 0.8f, 0.3f, color);
                break;
            case SYSTEM: 
                nvgRGBf(0.3f, 0.6f, 1.0f, color);
                break;
            default: 
                nvgRGBf(0.8f, 0.8f, 0.8f, color);
                break;
        }
        return color;
    }
    
    public void resize(float width, float height) {
        this.m_windowWidth = width;
        this.m_windowHeight = height;
    }
    
    private void registerBuiltinCommands() {
        m_commandRegistry.register(new HelpCommand(m_commandRegistry));
        m_commandRegistry.register(new ClearCommand(this));
        m_commandRegistry.register(new ExitCommand(this));
        m_commandRegistry.register(new EchoCommand(this));
        m_commandRegistry.register(new HistoryCommand(this));
    }
    
    public BitFlagStateMachine getStateMachine() {
        return m_stateMachine;
    }
    
    public CommandRegistry getCommandRegistry() {
        return m_commandRegistry;
    }
    
    public List<String> getCommandHistory() {
        return new ArrayList<>(m_commandHistory);
    }
    
    // Supporting classes
    public enum OutputType {
        INPUT, OUTPUT, ERROR, WARNING, SYSTEM
    }
    
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