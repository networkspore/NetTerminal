package io.netnotes.gui.nvg.input;

import static org.lwjgl.glfw.GLFW.*;

/**
 * KeyBindings - Centralized key binding configuration
 * Makes it easy to customize shortcuts
 */
public class KeyBindings {
    
    /**
     * Check if the command center toggle key combo is pressed
     * Default: Ctrl+` (backtick/grave accent)
     * Alternative: Ctrl+Alt+` for systems where Ctrl+` conflicts
     */
    public static boolean isCommandCenterToggle(int key, int mods) {
        // Primary binding: Ctrl+`
        if (key == GLFW_KEY_GRAVE_ACCENT && 
            (mods & GLFW_MOD_CONTROL) != 0 &&
            (mods & GLFW_MOD_ALT) == 0) {
            return true;
        }
        
        // Alternative binding: Ctrl+Alt+`
        if (key == GLFW_KEY_GRAVE_ACCENT && 
            (mods & GLFW_MOD_CONTROL) != 0 &&
            (mods & GLFW_MOD_ALT) != 0) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if Ctrl is pressed (cross-platform)
     * On macOS, also accepts Command key
     */
    public static boolean isCtrlPressed(int mods) {
        return (mods & GLFW_MOD_CONTROL) != 0;
    }
    
    /**
     * Check if Alt is pressed
     */
    public static boolean isAltPressed(int mods) {
        return (mods & GLFW_MOD_ALT) != 0;
    }
    
    /**
     * Check if Shift is pressed
     */
    public static boolean isShiftPressed(int mods) {
        return (mods & GLFW_MOD_SHIFT) != 0;
    }
    
    /**
     * Check if Super (Windows/Command) is pressed
     */
    public static boolean isSuperPressed(int mods) {
        return (mods & GLFW_MOD_SUPER) != 0;
    }
    
    // Common editor shortcuts
    
    public static boolean isCopy(int key, int mods) {
        return key == GLFW_KEY_C && isCtrlPressed(mods);
    }
    
    public static boolean isPaste(int key, int mods) {
        return key == GLFW_KEY_V && isCtrlPressed(mods);
    }
    
    public static boolean isCut(int key, int mods) {
        return key == GLFW_KEY_X && isCtrlPressed(mods);
    }
    
    public static boolean isSelectAll(int key, int mods) {
        return key == GLFW_KEY_A && isCtrlPressed(mods);
    }
    
    public static boolean isUndo(int key, int mods) {
        return key == GLFW_KEY_Z && isCtrlPressed(mods) && !isShiftPressed(mods);
    }
    
    public static boolean isRedo(int key, int mods) {
        return (key == GLFW_KEY_Z && isCtrlPressed(mods) && isShiftPressed(mods)) ||
               (key == GLFW_KEY_Y && isCtrlPressed(mods));
    }
    
    // Command line shortcuts
    
    public static boolean isClearLine(int key, int mods) {
        return key == GLFW_KEY_U && isCtrlPressed(mods);
    }
    
    public static boolean isClearToEnd(int key, int mods) {
        return key == GLFW_KEY_K && isCtrlPressed(mods);
    }
    
    public static boolean isDeleteWord(int key, int mods) {
        return key == GLFW_KEY_W && isCtrlPressed(mods);
    }
    
    public static boolean isClearScreen(int key, int mods) {
        return key == GLFW_KEY_L && isCtrlPressed(mods);
    }
    
    public static boolean isStartOfLine(int key, int mods) {
        return (key == GLFW_KEY_A && isCtrlPressed(mods)) || key == GLFW_KEY_HOME;
    }
    
    public static boolean isEndOfLine(int key, int mods) {
        return (key == GLFW_KEY_E && isCtrlPressed(mods)) || key == GLFW_KEY_END;
    }
    
    public static boolean isWordBackward(int key, int mods) {
        return key == GLFW_KEY_LEFT && isCtrlPressed(mods);
    }
    
    public static boolean isWordForward(int key, int mods) {
        return key == GLFW_KEY_RIGHT && isCtrlPressed(mods);
    }
    
    public static boolean isDeleteWordBackward(int key, int mods) {
        return (key == GLFW_KEY_BACKSPACE && isCtrlPressed(mods)) ||
               (key == GLFW_KEY_W && isCtrlPressed(mods));
    }
    
    public static boolean isDeleteWordForward(int key, int mods) {
        return key == GLFW_KEY_DELETE && isCtrlPressed(mods);
    }
    
    // Application shortcuts
    
    public static boolean isQuit(int key, int mods) {
        return key == GLFW_KEY_Q && isCtrlPressed(mods);
    }
    
    public static boolean isNewWindow(int key, int mods) {
        return key == GLFW_KEY_N && isCtrlPressed(mods);
    }
    
    public static boolean isCloseWindow(int key, int mods) {
        return key == GLFW_KEY_W && isCtrlPressed(mods) && isShiftPressed(mods);
    }
    
    public static boolean isFind(int key, int mods) {
        return key == GLFW_KEY_F && isCtrlPressed(mods);
    }
    
    public static boolean isSave(int key, int mods) {
        return key == GLFW_KEY_S && isCtrlPressed(mods);
    }
    
    public static boolean isOpen(int key, int mods) {
        return key == GLFW_KEY_O && isCtrlPressed(mods);
    }
    
    /**
     * Get a human-readable description of a key combination
     */
    public static String getKeyDescription(int key, int mods) {
        StringBuilder sb = new StringBuilder();
        
        if (isCtrlPressed(mods)) {
            sb.append("Ctrl+");
        }
        if (isAltPressed(mods)) {
            sb.append("Alt+");
        }
        if (isShiftPressed(mods)) {
            sb.append("Shift+");
        }
        if (isSuperPressed(mods)) {
            sb.append("Super+");
        }
        
        sb.append(getKeyName(key));
        
        return sb.toString();
    }
    
    /**
     * Get a human-readable name for a key
     */
    public static String getKeyName(int key) {
        switch (key) {
            case GLFW_KEY_SPACE: return "Space";
            case GLFW_KEY_APOSTROPHE: return "'";
            case GLFW_KEY_COMMA: return ",";
            case GLFW_KEY_MINUS: return "-";
            case GLFW_KEY_PERIOD: return ".";
            case GLFW_KEY_SLASH: return "/";
            case GLFW_KEY_SEMICOLON: return ";";
            case GLFW_KEY_EQUAL: return "=";
            case GLFW_KEY_LEFT_BRACKET: return "[";
            case GLFW_KEY_BACKSLASH: return "\\";
            case GLFW_KEY_RIGHT_BRACKET: return "]";
            case GLFW_KEY_GRAVE_ACCENT: return "`";
            case GLFW_KEY_ESCAPE: return "Esc";
            case GLFW_KEY_ENTER: return "Enter";
            case GLFW_KEY_TAB: return "Tab";
            case GLFW_KEY_BACKSPACE: return "Backspace";
            case GLFW_KEY_INSERT: return "Insert";
            case GLFW_KEY_DELETE: return "Delete";
            case GLFW_KEY_RIGHT: return "Right";
            case GLFW_KEY_LEFT: return "Left";
            case GLFW_KEY_DOWN: return "Down";
            case GLFW_KEY_UP: return "Up";
            case GLFW_KEY_PAGE_UP: return "PageUp";
            case GLFW_KEY_PAGE_DOWN: return "PageDown";
            case GLFW_KEY_HOME: return "Home";
            case GLFW_KEY_END: return "End";
            case GLFW_KEY_CAPS_LOCK: return "CapsLock";
            case GLFW_KEY_SCROLL_LOCK: return "ScrollLock";
            case GLFW_KEY_NUM_LOCK: return "NumLock";
            case GLFW_KEY_PRINT_SCREEN: return "PrintScreen";
            case GLFW_KEY_PAUSE: return "Pause";
            default:
                // F-keys
                if (key >= GLFW_KEY_F1 && key <= GLFW_KEY_F25) {
                    return "F" + (key - GLFW_KEY_F1 + 1);
                }
                // Letters
                if (key >= GLFW_KEY_A && key <= GLFW_KEY_Z) {
                    return String.valueOf((char) key);
                }
                // Numbers
                if (key >= GLFW_KEY_0 && key <= GLFW_KEY_9) {
                    return String.valueOf((char) key);
                }
                return "Key" + key;
        }
    }
}