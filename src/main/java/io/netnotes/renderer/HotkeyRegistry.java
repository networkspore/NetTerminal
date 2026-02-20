package io.netnotes.renderer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HotkeyRegistry {

    public record Hotkey(int hidCode, int modifiers) {}

    private final Map<Hotkey, Runnable> handlers = new ConcurrentHashMap<>();

    public void register(int hidCode, int modifiers, Runnable handler) {
        handlers.put(new Hotkey(hidCode, modifiers), handler);
    }

    public void unregister(int hidCode, int modifiers) {
        handlers.remove(new Hotkey(hidCode, modifiers));
    }

    /** @return true if consumed */
    public boolean dispatch(int hidCode, int modifiers) {
        Hotkey hotkey = new Hotkey(hidCode, modifiers);
        Runnable handler = handlers.get(hotkey);
        if (handler != null) {
            handler.run();
            return true;
        } 
        return false;
    }
}