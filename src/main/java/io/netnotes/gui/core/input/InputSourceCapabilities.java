package io.netnotes.gui.fx.uiNode.input;

/**
 * Describes what an input source can do.
 */
public final class InputSourceCapabilities {
    public final String name;

    // Basic input categories
    private final boolean supportsMouse;
    private final boolean supportsKeyboard;
    private final boolean supportsTouch;
    private final boolean supportsScroll;
    private final boolean supportsPen;
    private final boolean supportsGamepad;

    private final boolean supportsSceneLocation;
    private final boolean supportsSceneSize;
    private final boolean supportsNodeTracking;

    // Lifecycle support
    private final boolean supportsLifecycle;
    private final boolean supportsSceneLifecycle;
    private final boolean supportsStageLifecycle;
    private final boolean supportsStagePosition;
    private final boolean supportsStageSize;
    private final boolean supportsStageIconified;
    private final boolean supportsStageMaximized;
    private final boolean supportsStageFullscreen;
    private final boolean supportsStageFocused;
    private final boolean supportsStageAlwaysOnTop;

    // Enabled features
    private final boolean mouseEnabled;
    private final boolean keyboardEnabled;
    private final boolean touchEnabled;
    private final boolean scrollEnabled;
    private final boolean penEnabled;
    private final boolean gamepadEnabled;

    private final boolean sceneLocationEnabled;
    private final boolean sceneSizeEnabled;
    private final boolean trackNodeEnabled;

    // Lifecycle enabled
    private final boolean lifecycleEnabled;
    private final boolean sceneLifecycleEnabled;
    private final boolean stageLifecycleEnabled;
    private final boolean stagePositionEnabled;
    private final boolean stageSizeEnabled;
    private final boolean stageIconifiedEnabled;
    private final boolean stageMaximizedEnabled;
    private final boolean stageFullscreenEnabled;
    private final boolean stageFocusedEnabled;
    private final boolean stageAlwaysOnTopEnabled;

    // Coordinate systems
    private final boolean providesAbsoluteCoordinates;
    private final boolean providesRelativeCoordinates;

    // Advanced features
    private final boolean supportsMultipleDevices;
    private final boolean supportsHighPrecision;
    private final boolean supportsGlobalCapture;
    private final boolean providesScancodes;

    // Timing
    private final boolean providesNanosecondTimestamps;
    private final long estimatedLatencyNanos;

    // Operational mode
    private final boolean isPollingBased;
    private final int maxPollingRateHz;

    private InputSourceCapabilities(
            String name,
            boolean supportsMouse,
            boolean supportsKeyboard,
            boolean supportsTouch,
            boolean supportsScroll,
            boolean supportsPen,
            boolean supportsGamepad,
            boolean providesAbsoluteCoordinates,
            boolean providesRelativeCoordinates,
            boolean supportsMultipleDevices,
            boolean supportsHighPrecision,
            boolean supportsGlobalCapture,
            boolean providesScancodes,
            boolean providesNanosecondTimestamps,
            long estimatedLatencyNanos,
            boolean isPollingBased,
            int maxPollingRateHz,
            boolean mouseEnabled,
            boolean keyboardEnabled,
            boolean touchEnabled,
            boolean scrollEnabled,
            boolean penEnabled,
            boolean gamepadEnabled,
            boolean supportsSceneLocation,
            boolean supportsSceneSize,
            boolean sceneLocationEnabled,
            boolean sceneSizeEnabled,
            boolean supportsLifecycle,
            boolean lifecycleEnabled,
            boolean supportsNodeTracking,
            boolean nodeTrackingEnabled,
            boolean supportsSceneLifecycle,
            boolean sceneLifecycleEnabled,
            boolean supportsStageLifecycle,
            boolean stageLifecycleEnabled,
            boolean supportsStagePosition,
            boolean stagePositionEnabled,
            boolean supportsStageSize,
            boolean stageSizeEnabled,
            boolean supportsStageIconified,
            boolean stageIconifiedEnabled,
            boolean supportsStageMaximized,
            boolean stageMaximizedEnabled,
            boolean supportsStageFullscreen,
            boolean stageFullscreenEnabled,
            boolean supportsStageFocused,
            boolean stageFocusedEnabled,
            boolean supportsStageAlwaysOnTop,
            boolean stageAlwaysOnTopEnabled
    ) {
        this.name = name;
        this.supportsMouse = supportsMouse;
        this.supportsKeyboard = supportsKeyboard;
        this.supportsTouch = supportsTouch;
        this.supportsScroll = supportsScroll;
        this.supportsPen = supportsPen;
        this.supportsGamepad = supportsGamepad;
        this.providesAbsoluteCoordinates = providesAbsoluteCoordinates;
        this.providesRelativeCoordinates = providesRelativeCoordinates;
        this.supportsMultipleDevices = supportsMultipleDevices;
        this.supportsHighPrecision = supportsHighPrecision;
        this.supportsGlobalCapture = supportsGlobalCapture;
        this.providesScancodes = providesScancodes;
        this.providesNanosecondTimestamps = providesNanosecondTimestamps;
        this.estimatedLatencyNanos = estimatedLatencyNanos;
        this.isPollingBased = isPollingBased;
        this.maxPollingRateHz = maxPollingRateHz;
        
        this.mouseEnabled = mouseEnabled;
        this.keyboardEnabled = keyboardEnabled;
        this.touchEnabled = touchEnabled;
        this.scrollEnabled = scrollEnabled;
        this.penEnabled = penEnabled;
        this.gamepadEnabled = gamepadEnabled;
        
        this.supportsSceneLocation = supportsSceneLocation;
        this.supportsSceneSize = supportsSceneSize;
        this.supportsNodeTracking = supportsNodeTracking;
        this.sceneLocationEnabled = sceneLocationEnabled;
        this.sceneSizeEnabled = sceneSizeEnabled;
        this.trackNodeEnabled = nodeTrackingEnabled;
        
        this.supportsLifecycle = supportsLifecycle;
        this.lifecycleEnabled = lifecycleEnabled;
        this.supportsSceneLifecycle = supportsSceneLifecycle;
        this.sceneLifecycleEnabled = sceneLifecycleEnabled;
        this.supportsStageLifecycle = supportsStageLifecycle;
        this.stageLifecycleEnabled = stageLifecycleEnabled;
        this.supportsStagePosition = supportsStagePosition;
        this.stagePositionEnabled = stagePositionEnabled;
        this.supportsStageSize = supportsStageSize;
        this.stageSizeEnabled = stageSizeEnabled;
        this.supportsStageIconified = supportsStageIconified;
        this.stageIconifiedEnabled = stageIconifiedEnabled;
        this.supportsStageMaximized = supportsStageMaximized;
        this.stageMaximizedEnabled = stageMaximizedEnabled;
        this.supportsStageFullscreen = supportsStageFullscreen;
        this.stageFullscreenEnabled = stageFullscreenEnabled;
        this.supportsStageFocused = supportsStageFocused;
        this.stageFocusedEnabled = stageFocusedEnabled;
        this.supportsStageAlwaysOnTop = supportsStageAlwaysOnTop;
        this.stageAlwaysOnTopEnabled = stageAlwaysOnTopEnabled;
    }

    // Support checks
    public boolean isMouseSupported() { return supportsMouse; }
    public boolean isKeyboardSupported() { return supportsKeyboard; }
    public boolean isTouchSupported() { return supportsTouch; }
    public boolean isScrollSupported() { return supportsScroll; }
    public boolean isPenSupported() { return supportsPen; }
    public boolean isGamepadSupported() { return supportsGamepad; }

    // Enabled checks
    public boolean isMouseEnabled() { return mouseEnabled && supportsMouse; }
    public boolean isKeyboardEnabled() { return keyboardEnabled && supportsKeyboard; }
    public boolean isTouchEnabled() { return touchEnabled && supportsTouch; }
    public boolean isScrollEnabled() { return scrollEnabled && supportsScroll; }
    public boolean isPenEnabled() { return penEnabled && supportsPen; }
    public boolean isGamepadEnabled() { return gamepadEnabled && supportsGamepad; }

    public boolean isSceneLocationEnabled() { return sceneLocationEnabled && supportsSceneLocation; }
    public boolean isSceneSizeEnabled() { return sceneSizeEnabled && supportsSceneSize; }
    public boolean isNodeTrackingEnabled() { return trackNodeEnabled && supportsNodeTracking; }

    public boolean isSceneLocationSupported() { return supportsSceneLocation; }
    public boolean isSceneSizeSupported() { return supportsSceneSize; }
    public boolean isNodeTrackingSupported() { return supportsNodeTracking; }

    // Lifecycle checks
    public boolean isLifeCycleEnabled() { return lifecycleEnabled && supportsLifecycle; }
    public boolean isLifeCycleSupported() { return supportsLifecycle; }
    public boolean isSceneLifecycleEnabled() { return sceneLifecycleEnabled && supportsSceneLifecycle; }
    public boolean isSceneLifecycleSupported() { return supportsSceneLifecycle; }
    public boolean isStageLifecycleEnabled() { return stageLifecycleEnabled && supportsStageLifecycle; }
    public boolean isStageLifecycleSupported() { return supportsStageLifecycle; }

    // Stage property checks
    public boolean isStagePositionEnabled() { return stagePositionEnabled && supportsStagePosition; }
    public boolean isStagePositionSupported() { return supportsStagePosition; }
    public boolean isStageSizeEnabled() { return stageSizeEnabled && supportsStageSize; }
    public boolean isStageSizeSupported() { return supportsStageSize; }
    public boolean isStageIconifiedEnabled() { return stageIconifiedEnabled && supportsStageIconified; }
    public boolean isStageIconifiedSupported() { return supportsStageIconified; }
    public boolean isStageMaximizedEnabled() { return stageMaximizedEnabled && supportsStageMaximized; }
    public boolean isStageMaximizedSupported() { return supportsStageMaximized; }
    public boolean isStageFullscreenEnabled() { return stageFullscreenEnabled && supportsStageFullscreen; }
    public boolean isStageFullscreenSupported() { return supportsStageFullscreen; }
    public boolean isStageFocusedEnabled() { return stageFocusedEnabled && supportsStageFocused; }
    public boolean isStageFocusedSupported() { return supportsStageFocused; }
    public boolean isStageAlwaysOnTopEnabled() { return stageAlwaysOnTopEnabled && supportsStageAlwaysOnTop; }
    public boolean isStageAlwaysOnTopSupported() { return supportsStageAlwaysOnTop; }

    // Coordinate and feature checks
    public boolean isProvidesAbsoluteCoordinates() { return providesAbsoluteCoordinates; }
    public boolean isProvidesRelativeCoordinates() { return providesRelativeCoordinates; }
    public boolean isSupportsMultipleDevices() { return supportsMultipleDevices; }
    public boolean isSupportsHighPrecision() { return supportsHighPrecision; }
    public boolean isSupportsGlobalCapture() { return supportsGlobalCapture; }
    public boolean isProvidesScancodes() { return providesScancodes; }

    public boolean isProvidesNanosecondTimestamps() { return providesNanosecondTimestamps; }
    public long getEstimatedLatencyNanos() { return estimatedLatencyNanos; }
    public boolean isPollingBased() { return isPollingBased; }
    public int getMaxPollingRateHz() { return maxPollingRateHz; }

    public static class Builder {
        private String name;

        private boolean supportsMouse = false;
        private boolean supportsKeyboard = false;
        private boolean supportsTouch = false;
        private boolean supportsScroll = false;
        private boolean supportsPen = false;
        private boolean supportsGamepad = false;

        private Boolean providesAbsoluteCoordinates = null;
        private Boolean providesRelativeCoordinates = null;

        private boolean supportsMultipleDevices = true;
        private boolean supportsHighPrecision = false;
        private boolean supportsGlobalCapture = false;
        private boolean providesScanCodes = false;

        private boolean providesNanosecondTimestamps = true;
        private long estimatedLatencyNanos = 16_000_000;

        private boolean isPollingBased = false;
        private int maxPollingRateHz = 0;

        private boolean mouseEnabled = false;
        private boolean keyboardEnabled = false;
        private boolean touchEnabled = false;
        private boolean scrollEnabled = false;
        private boolean penEnabled = false;
        private boolean gamepadEnabled = false;

        private boolean supportsSceneLocation = false;
        private boolean supportsSceneSize = false;
        private boolean supportsNodeTracking = false;

        private boolean sceneLocationEnabled = false;
        private boolean sceneSizeEnabled = false;
        private boolean nodeTrackingEnabled = false;

        private boolean supportsLifecycle = false;
        private boolean lifecycleEnabled = false;
        private boolean supportsSceneLifecycle = false;
        private boolean sceneLifecycleEnabled = false;
        private boolean supportsStageLifecycle = false;
        private boolean stageLifecycleEnabled = false;
        private boolean supportsStagePosition = false;
        private boolean stagePositionEnabled = false;
        private boolean supportsStageSize = false;
        private boolean stageSizeEnabled = false;
        private boolean supportsStageIconified = false;
        private boolean stageIconifiedEnabled = false;
        private boolean supportsStageMaximized = false;
        private boolean stageMaximizedEnabled = false;
        private boolean supportsStageFullscreen = false;
        private boolean stageFullscreenEnabled = false;
        private boolean supportsStageFocused = false;
        private boolean stageFocusedEnabled = false;
        private boolean supportsStageAlwaysOnTop = false;
        private boolean stageAlwaysOnTopEnabled = false;

        public Builder(String name) {
            this.name = name;
        }

        public Builder supportsMouse() { this.supportsMouse = true; return this; }
        public Builder supportsKeyboard() { this.supportsKeyboard = true; return this; }
        public Builder supportsTouch() { this.supportsTouch = true; return this; }
        public Builder supportsScroll() { this.supportsScroll = true; return this; }
        public Builder supportsPen() { this.supportsPen = true; return this; }
        public Builder supportsGamepad() { this.supportsGamepad = true; return this; }

        public Builder providesAbsoluteCoordinates() { this.providesAbsoluteCoordinates = true; return this; }
        public Builder providesRelativeCoordinates() { this.providesRelativeCoordinates = true; return this; }

        public Builder withMultiDevice() { this.supportsMultipleDevices = true; return this; }
        public Builder withHighPrecision() { this.supportsHighPrecision = true; return this; }
        public Builder withGlobalCapture() { this.supportsGlobalCapture = true; return this; }
        public Builder withScanCodes() { this.providesScanCodes = true; return this; }

        public Builder providesNanosecondTimestamps() { this.providesNanosecondTimestamps = true; return this; }
        public Builder estimatedLatencyNanos(long nanos) { this.estimatedLatencyNanos = nanos; return this; }

        public Builder withPollingMode(int maxRateHz) {
            this.isPollingBased = true;
            this.maxPollingRateHz = maxRateHz;
            return this;
        }

        public Builder supportsSceneLocation() { this.supportsSceneLocation = true; return this; }
        public Builder supportsSceneSize() { this.supportsSceneSize = true; return this; }
        public Builder supportsNodeTracking() { this.supportsNodeTracking = true; return this; }

        public Builder enableMouse() { this.supportsMouse = true; this.mouseEnabled = true; return this; }
        public Builder enableKeyboard() { this.supportsKeyboard = true; this.keyboardEnabled = true; return this; }
        public Builder enableTouch() { this.supportsTouch = true; this.touchEnabled = true; return this; }
        public Builder enableScroll() { this.supportsScroll = true; this.scrollEnabled = true; return this; }
        public Builder enablePen() { this.supportsPen = true; this.penEnabled = true; return this; }
        public Builder enableGamepad() { this.supportsGamepad = true; this.gamepadEnabled = true; return this; }

        public Builder enableSceneLocation() { 
            this.supportsSceneLocation = true;
            this.sceneLocationEnabled = true; 
            return this; 
        }
        public Builder enableSceneSize() { 
            this.supportsSceneSize = true;
            this.sceneSizeEnabled = true; 
            return this; 
        }
        public Builder enableNodeTracking() { 
            this.supportsNodeTracking = true;
            this.nodeTrackingEnabled = true; 
            return this; 
        }

        public Builder enableLifeCycle() { 
            this.supportsLifecycle = true;
            this.lifecycleEnabled = true; 
            return this; 
        }
        public Builder supportsLifeCycle() { this.supportsLifecycle = true; return this; }

        public Builder enableSceneLifecycle() {
            this.supportsSceneLifecycle = true;
            this.sceneLifecycleEnabled = true;
            return this;
        }
        public Builder supportsSceneLifecycle() { this.supportsSceneLifecycle = true; return this; }

        public Builder enableStageLifecycle() {
            this.supportsStageLifecycle = true;
            this.stageLifecycleEnabled = true;
            return this;
        }
        public Builder supportsStageLifecycle() { this.supportsStageLifecycle = true; return this; }

        public Builder enableStagePosition() {
            this.supportsStagePosition = true;
            this.stagePositionEnabled = true;
            return this;
        }
        public Builder supportsStagePosition() { this.supportsStagePosition = true; return this; }

        public Builder enableStageSize() {
            this.supportsStageSize = true;
            this.stageSizeEnabled = true;
            return this;
        }
        public Builder supportsStageSize() { this.supportsStageSize = true; return this; }

        public Builder enableStageIconified() {
            this.supportsStageIconified = true;
            this.stageIconifiedEnabled = true;
            return this;
        }
        public Builder supportsStageIconified() { this.supportsStageIconified = true; return this; }

        public Builder enableStageMaximized() {
            this.supportsStageMaximized = true;
            this.stageMaximizedEnabled = true;
            return this;
        }
        public Builder supportsStageMaximized() { this.supportsStageMaximized = true; return this; }

        public Builder enableStageFullscreen() {
            this.supportsStageFullscreen = true;
            this.stageFullscreenEnabled = true;
            return this;
        }
        public Builder supportsStageFullscreen() { this.supportsStageFullscreen = true; return this; }

        public Builder enableStageFocused() {
            this.supportsStageFocused = true;
            this.stageFocusedEnabled = true;
            return this;
        }
        public Builder supportsStageFocused() { this.supportsStageFocused = true; return this; }

        public Builder enableStageAlwaysOnTop() {
            this.supportsStageAlwaysOnTop = true;
            this.stageAlwaysOnTopEnabled = true;
            return this;
        }
        public Builder supportsStageAlwaysOnTop() { this.supportsStageAlwaysOnTop = true; return this; }

        public InputSourceCapabilities build() {
            boolean abs = (providesAbsoluteCoordinates != null) ? providesAbsoluteCoordinates :
                          (supportsTouch || supportsPen);
            boolean rel = (providesRelativeCoordinates != null) ? providesRelativeCoordinates :
                          (supportsMouse || supportsGamepad);

            return new InputSourceCapabilities(
                name,
                supportsMouse,
                supportsKeyboard,
                supportsTouch,
                supportsScroll,
                supportsPen,
                supportsGamepad,
                abs,
                rel,
                supportsMultipleDevices,
                supportsHighPrecision,
                supportsGlobalCapture,
                providesScanCodes,
                providesNanosecondTimestamps,
                estimatedLatencyNanos,
                isPollingBased,
                maxPollingRateHz,
                mouseEnabled,
                keyboardEnabled,
                touchEnabled,
                scrollEnabled,
                penEnabled,
                gamepadEnabled,
                supportsSceneLocation,
                supportsSceneSize,
                sceneLocationEnabled,
                sceneSizeEnabled,
                supportsLifecycle,
                lifecycleEnabled,
                supportsNodeTracking,
                nodeTrackingEnabled,
                supportsSceneLifecycle,
                sceneLifecycleEnabled,
                supportsStageLifecycle,
                stageLifecycleEnabled,
                supportsStagePosition,
                stagePositionEnabled,
                supportsStageSize,
                stageSizeEnabled,
                supportsStageIconified,
                stageIconifiedEnabled,
                supportsStageMaximized,
                stageMaximizedEnabled,
                supportsStageFullscreen,
                stageFullscreenEnabled,
                supportsStageFocused,
                stageFocusedEnabled,
                supportsStageAlwaysOnTop,
                stageAlwaysOnTopEnabled
            );
        }
    }
}