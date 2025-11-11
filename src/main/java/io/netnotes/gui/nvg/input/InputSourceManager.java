package io.netnotes.gui.nvg.input;

import static org.lwjgl.glfw.GLFW.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.io.InputPacket.Factory;
import io.netnotes.engine.messaging.EventBytes.StateFlags;
import io.netnotes.utils.Execs;

/**
 * Asynchronous input source manager that produces CompletableFuture<byte[]>
 * for a blocking queue, decoupling input capture from processing.
 */
public class InputSourceManager {
    private final long window;
    private final BlockingQueue<CompletableFuture<byte[]>> eventQueue;
    private final Factory factory;
    
    // Mouse state
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private final Map<Integer, Long> buttonDownTime = new HashMap<>();
    private final Map<Integer, Integer> clickCount = new HashMap<>();
    private final Map<Integer, Long> lastClickTime = new HashMap<>();
    private final Map<Integer, Double> lastClickX = new HashMap<>();
    private final Map<Integer, Double> lastClickY = new HashMap<>();
    
    // Keyboard state
    private final Set<Integer> pressedKeys = new HashSet<>();
    
    // Control flags
    private volatile boolean enabled = true;
    private volatile boolean captureEnabled = true;
    
    // Configuration
    private static final long DOUBLE_CLICK_TIME_MS = 300;
    private static final double DOUBLE_CLICK_DISTANCE = 5.0;
    
    public InputSourceManager(
            long window, 
            int sourceId, 
            BlockingQueue<CompletableFuture<byte[]>> eventQueue) {
        this.window = window;
        this.eventQueue = eventQueue;
        this.factory = new Factory(sourceId);
        setupCallbacks();
    }
    
    private void setupCallbacks() {
        // Mouse position callback
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (!enabled) return;
            
            double dx = xpos - lastMouseX;
            double dy = ypos - lastMouseY;
            
            // Enqueue absolute position event
            enqueueEvent(() -> factory.createMouseMove(xpos, ypos, getCurrentModifiers()));
            
            // Enqueue relative movement if significant
            if (Math.abs(dx) > 0.1 || Math.abs(dy) > 0.1) {
                enqueueEvent(() -> factory.createMouseMoveRelative(dx, dy, getCurrentModifiers()));
            }
            
            lastMouseX = xpos;
            lastMouseY = ypos;
        });
        
        // Mouse button callback
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (!enabled) return;
            
            if (action == GLFW_PRESS) {
                buttonDownTime.put(button, System.currentTimeMillis());
                enqueueEvent(() -> factory.createMouseButtonDown(
                    button, lastMouseX, lastMouseY, mods | getMouseButtonFlags()));
            } else if (action == GLFW_RELEASE) {
                enqueueEvent(() -> factory.createMouseButtonUp(
                    button, lastMouseX, lastMouseY, mods | getMouseButtonFlags()));
                
                // Generate click event
                Long downTime = buttonDownTime.remove(button);
                if (downTime != null) {
                    long duration = System.currentTimeMillis() - downTime;
                    if (duration < 500) {
                        handleClick(button, mods);
                    }
                }
            }
        });
        
        // Scroll callback
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (!enabled) return;
            enqueueEvent(() -> factory.createScroll(
                xoffset, yoffset, lastMouseX, lastMouseY, getCurrentModifiers()));
        });
        
        // Key callback
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (!enabled) return;
            
            if (action == GLFW_PRESS) {
                pressedKeys.add(key);
                enqueueEvent(() -> factory.createKeyDown(key, scancode, mods));
            } else if (action == GLFW_RELEASE) {
                pressedKeys.remove(key);
                enqueueEvent(() -> factory.createKeyUp(key, scancode, mods));
            } else if (action == GLFW_REPEAT) {
                enqueueEvent(() -> factory.createKeyRepeat(key, scancode, mods));
            }
        });
        
        // Character callback
        glfwSetCharCallback(window, (win, codepoint) -> {
            if (!enabled) return;
            enqueueEvent(() -> factory.createKeyChar(codepoint, getCurrentModifiers()));
        });
        
        // Character with modifiers callback
        glfwSetCharModsCallback(window, (win, codepoint, mods) -> {
            if (!enabled) return;
            enqueueEvent(() -> factory.createKeyCharMods(codepoint, mods));
        });
        
        // Window focus callback
        glfwSetWindowFocusCallback(window, (win, focused) -> {
            if (!enabled) return;
            
            if (focused) {
                enqueueEvent(() -> factory.createFocusGained());
            } else {
                enqueueEvent(() -> factory.createFocusLost());
                // Clear state on focus loss
                pressedKeys.clear();
                buttonDownTime.clear();
            }
        });
        
        // Framebuffer size callback
        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            if (!enabled) return;
            enqueueEvent(() -> factory.createFramebufferResize(width, height));
        });
    }
    
    /**
     * Enqueue an event as a CompletableFuture
     */
    private void enqueueEvent(java.util.function.Supplier<byte[]> eventSupplier) {
        if (!captureEnabled) return;
        
        CompletableFuture<byte[]> future = CompletableFuture.supplyAsync(eventSupplier, Execs.getVirtualExecutor());
        
        try {
            eventQueue.put(future);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Log error or handle appropriately
        }
    }
    
    private void handleClick(int button, int mods) {
        long now = System.currentTimeMillis();
        
        // Get last click time and position for this button
        Long lastTime = lastClickTime.get(button);
        Double lastX = lastClickX.get(button);
        Double lastY = lastClickY.get(button);
        
        // Determine if this is a continuation of a multi-click sequence
        boolean isMultiClick = false;
        if (lastTime != null && lastX != null && lastY != null) {
            long timeDelta = now - lastTime;
            double distance = Math.sqrt(
                Math.pow(lastMouseX - lastX, 2) + 
                Math.pow(lastMouseY - lastY, 2)
            );
            
            isMultiClick = (timeDelta < DOUBLE_CLICK_TIME_MS) && 
                          (distance < DOUBLE_CLICK_DISTANCE);
        }
        
        // Update click count
        int i = clickCount.getOrDefault(button, 0);
        int count = isMultiClick ? i + 1 : 1;
    
        clickCount.put(button, count);
        
        // Store this click's time and position
        lastClickTime.put(button, now);
        lastClickX.put(button, lastMouseX);
        lastClickY.put(button, lastMouseY);
        
        enqueueEvent(() -> factory.createMouseClick(
            button, lastMouseX, lastMouseY, count, mods | getMouseButtonFlags()));
    }
    
    private int getCurrentModifiers() {
        int mods = 0;
        if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS || 
            glfwGetKey(window, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS) {
            mods |= StateFlags.MOD_SHIFT;
        }
        if (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS || 
            glfwGetKey(window, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS) {
            mods |= StateFlags.MOD_CONTROL;
        }
        if (glfwGetKey(window, GLFW_KEY_LEFT_ALT) == GLFW_PRESS || 
            glfwGetKey(window, GLFW_KEY_RIGHT_ALT) == GLFW_PRESS) {
            mods |= StateFlags.MOD_ALT;
        }
        if (glfwGetKey(window, GLFW_KEY_LEFT_SUPER) == GLFW_PRESS || 
            glfwGetKey(window, GLFW_KEY_RIGHT_SUPER) == GLFW_PRESS) {
            mods |= StateFlags.MOD_SUPER;
        }
        return mods;
    }
    
    private int getMouseButtonFlags() {
        int flags = 0;
        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS) {
            flags |= StateFlags.MOUSE_BUTTON_1;
        }
        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_2) == GLFW_PRESS) {
            flags |= StateFlags.MOUSE_BUTTON_2;
        }
        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_3) == GLFW_PRESS) {
            flags |= StateFlags.MOUSE_BUTTON_3;
        }
        return flags;
    }
    
    // Control methods
    
    public void enable() {
        this.enabled = true;
    }
    
    public void disable() {
        this.enabled = false;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setCaptureEnabled(boolean captureEnabled) {
        this.captureEnabled = captureEnabled;
    }
    
    public boolean isCaptureEnabled() {
        return captureEnabled;
    }
    
    public void cleanup() {
        disable();
        pressedKeys.clear();
        buttonDownTime.clear();
        clickCount.clear();
        lastClickTime.clear();
        lastClickX.clear();
        lastClickY.clear();
    }
}