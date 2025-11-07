package io.netnotes.gui.nvg.uiNode.input;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SUPER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SUPER;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_1;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_2;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_3;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;
import static org.lwjgl.glfw.GLFW.glfwGetKey;
import static org.lwjgl.glfw.GLFW.glfwGetMouseButton;
import static org.lwjgl.glfw.GLFW.glfwSetCharCallback;
import static org.lwjgl.glfw.GLFW.glfwSetCharModsCallback;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetFramebufferSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;
import static org.lwjgl.glfw.GLFW.glfwSetScrollCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowFocusCallback;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.netnotes.gui.nvg.uiNode.input.InputPacket.Factory;
import io.netnotes.gui.nvg.uiNode.input.InputPacket.StateFlags;

public class InputSourceManager {
    private final long window;
    private final InputEventRouter router;
    private final Factory factory;
    
    // Mouse state
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private final Map<Integer, Long> buttonDownTime = new HashMap<>();
    private final Map<Integer, Integer> clickCount = new HashMap<>();
    
    // Keyboard state
    private final Set<Integer> pressedKeys = new HashSet<>();
    
    // Configuration
    private static final long DOUBLE_CLICK_TIME_MS = 300;
    private static final double DOUBLE_CLICK_DISTANCE = 5.0;
    
    public InputSourceManager(long window, int sourceId, InputEventRouter router) {
        this.window = window;
        this.router = router;
        this.factory = new Factory(sourceId);
        setupCallbacks();
    }
    
    private void setupCallbacks() {
        // Mouse position callback
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            double dx = xpos - lastMouseX;
            double dy = ypos - lastMouseY;
            
            // Absolute position
            router.routeEvent(factory.createMouseMove(xpos, ypos, getCurrentModifiers()));
            
            // Relative movement (if significant)
            if (Math.abs(dx) > 0.1 || Math.abs(dy) > 0.1) {
                router.routeEvent(factory.createMouseMoveRelative(dx, dy, getCurrentModifiers()));
            }
            
            lastMouseX = xpos;
            lastMouseY = ypos;
        });
        
        // Mouse button callback
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (action == GLFW_PRESS) {
                buttonDownTime.put(button, System.currentTimeMillis());
                router.routeEvent(factory.createMouseButtonDown(
                    button, lastMouseX, lastMouseY, mods | getMouseButtonFlags()));
            } else if (action == GLFW_RELEASE) {
                router.routeEvent(factory.createMouseButtonUp(
                    button, lastMouseX, lastMouseY, mods | getMouseButtonFlags()));
                
                // Generate click event
                Long downTime = buttonDownTime.remove(button);
                if (downTime != null) {
                    long duration = System.currentTimeMillis() - downTime;
                    if (duration < 500) { // Click threshold
                        handleClick(button, mods);
                    }
                }
            }
        });
        
        // Scroll callback
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            router.routeEvent(factory.createScroll(
                xoffset, yoffset, lastMouseX, lastMouseY, getCurrentModifiers()));
        });
        
        // Key callback
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                pressedKeys.add(key);
                router.routeEvent(factory.createKeyDown(key, scancode, mods));
            } else if (action == GLFW_RELEASE) {
                pressedKeys.remove(key);
                router.routeEvent(factory.createKeyUp(key, scancode, mods));
            } else if (action == GLFW_REPEAT) {
                router.routeEvent(factory.createKeyRepeat(key, scancode, mods));
            }
        });
        
        // Character callback (for text input)
        glfwSetCharCallback(window, (win, codepoint) -> {
            router.routeEvent(factory.createKeyChar(codepoint, getCurrentModifiers()));
        });
        
        // Character with modifiers callback
        glfwSetCharModsCallback(window, (win, codepoint, mods) -> {
            router.routeEvent(factory.createKeyCharMods(codepoint, mods));
        });
        
        // Window focus callback
        glfwSetWindowFocusCallback(window, (win, focused) -> {
            if (focused) {
                router.routeEvent(factory.createFocusGained());
            } else {
                router.routeEvent(factory.createFocusLost());
                // Clear state on focus loss
                pressedKeys.clear();
                buttonDownTime.clear();
            }
        });
        
        // Framebuffer size callback (for HiDPI)
        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            router.routeEvent(factory.createFramebufferResize(width, height));
        });
    }
    
    private void handleClick(int button, int mods) {
        long now = System.currentTimeMillis();
        Integer count = clickCount.getOrDefault(button, 0);
        
        // Reset click count if too much time has passed
        // (you'd need to track last click time per button for this)
        count++;
        clickCount.put(button, count);
        
        router.routeEvent(factory.createMouseClick(
            button, lastMouseX, lastMouseY, count, mods | getMouseButtonFlags()));
        
        // Schedule reset of click count
        // (implementation detail - could use a timer)
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
}
