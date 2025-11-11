package io.netnotes.gui.nvg.input;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * InputSourceHelper - Utility methods for working with the InputSourceRegistry
 * and creating common input source configurations.
 */
public class InputSourceHelper {
    
    /**
     * Create and register a GLFW window input source
     */
    public static NoteBytesReadOnly registerGLFWSource(String windowName, long glfwWindow) {
        InputSourceCapabilities capabilities = new InputSourceCapabilities.Builder(windowName)
            .enableMouse()
            .enableKeyboard()
            .enableScroll()
            .providesAbsoluteCoordinates()
            .providesRelativeCoordinates()
            .withScanCodes()
            .enableStagePosition()
            .enableStageSize()
            .enableStageFocused()
            .build();
        
        ContextPath contextPath = ContextPath.of(windowName, "ui", "glfw", windowName);
        
        NoteBytesReadOnly sourceId = InputSourceRegistry.getInstance()
            .registerSource(windowName, capabilities, contextPath);
        
        // Activate the source immediately
        InputSourceRegistry.getInstance().activateSource(sourceId);
        
        return sourceId;
    }
    
    /**
     * Create and register a network input source
     */
    public static NoteBytesReadOnly registerNetworkSource(String clientName, String protocol) {
        InputSourceCapabilities capabilities = new InputSourceCapabilities.Builder(clientName)
            .enableMouse()
            .enableKeyboard()
            .providesAbsoluteCoordinates()
            .build();
        
        ContextPath contextPath = ContextPath.of(clientName, "network", protocol, clientName);
        
        return InputSourceRegistry.getInstance()
            .registerSource(clientName, capabilities, contextPath);
    }
    
    /**
     * Create and register a recording playback source
     */
    public static NoteBytesReadOnly registerPlaybackSource(String recordingName) {
        InputSourceCapabilities capabilities = new InputSourceCapabilities.Builder(recordingName)
            .enableMouse()
            .enableKeyboard()
            .enableScroll()
            .providesAbsoluteCoordinates()
            .providesRelativeCoordinates()
            .providesNanosecondTimestamps()
            .build();
        
        ContextPath contextPath = ContextPath.of(recordingName, "playback", "recordings", recordingName);
        
        NoteBytesReadOnly sourceId = InputSourceRegistry.getInstance()
            .registerSource(recordingName, capabilities, contextPath);
        
        // Mark as playback mode
        InputSourceRegistry.getInstance()
            .setSourceState(sourceId, SourceState.PLAYBACK_BIT, true);
        
        return sourceId;
    }
    
    /**
     * Create and register a gamepad source
     */
    public static NoteBytesReadOnly registerGamepadSource(String gamepadName, int gamepadId) {
        InputSourceCapabilities capabilities = new InputSourceCapabilities.Builder(gamepadName)
            .enableGamepad()
            .withPollingMode(60) // 60 Hz polling
            .build();
        
        ContextPath contextPath = ContextPath.of(gamepadName, "input", "gamepad", gamepadName);
        
        return InputSourceRegistry.getInstance()
            .registerSource(gamepadName, capabilities, contextPath);
    }
    
    /**
     * Print all registered sources (for debugging)
     */
    public static void printAllSources() {
        System.out.println("=== Registered Input Sources ===");
        System.out.println(InputSourceRegistry.getInstance().getSummary());
        System.out.println();
        
        for (SourceInfo info : InputSourceRegistry.getInstance().getAllSources()) {
            System.out.println(info);
            System.out.println("  Capabilities: " + info.capabilities.name);
            System.out.println("  Statistics: " + info.statistics);
            System.out.println();
        }
    }
}