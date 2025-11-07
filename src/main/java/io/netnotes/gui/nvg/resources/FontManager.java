package io.netnotes.gui.nvg.resources;

import org.lwjgl.nanovg.NanoVG;

import io.netnotes.gui.core.resources.ResourceRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Manages font loading and registration for NanoVG.
 * 
 * Supports:
 * - Loading from filesystem
 * - Loading from classpath resources
 * - Loading from byte arrays
 * - Font family management
 * - Automatic cleanup
 */
public class FontManager {
    private static FontManager INSTANCE = null;
    private final long vg;
    private final Map<String, Integer> fontHandles = new HashMap<>();
    
    private FontManager(long vg) {
        this.vg = vg;
        init();
    }

    public static FontManager getInstance(long vg){
        if(INSTANCE == null){
            INSTANCE = new FontManager(vg);
        }
        return INSTANCE;
    }
    
    /**
     * Load a font from the filesystem
     */
    public String loadFont(String name, String filepath){
        if (fontHandles.containsKey(name)) {
            return name;
        }
        
        Path path = Paths.get(filepath);
        if (!Files.exists(path)) {
            return null;
        }
        
        int handle = nvgCreateFont(vg, name, filepath);
        if (handle == -1) {
            return null;
        }
        
        fontHandles.put(name, handle);
        return name;
    }
    
    /**
     * Load a font from classpath resources
     */
    public void loadFontFromResource(String name, String resourcePath) throws IOException {
        if (fontHandles.containsKey(name)) {
            System.err.println("Font already loaded: " + name);
            return;
        }
        
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IOException("Font resource not found: " + resourcePath);
        }
        
        ByteBuffer fontBuffer = ioResourceToByteBuffer(stream, 512 * 1024);
        int handle = NanoVG.nvgCreateFontMem(vg, name, fontBuffer, false);
        
        if (handle == -1) {
            memFree(fontBuffer);
            throw new IOException("Failed to load font from resource: " + resourcePath);
        }
        
        fontHandles.put(name, handle);
        System.out.println("Loaded font: " + name + " from resource " + resourcePath);
    }
    
    /**
     * Load a font from byte array
     */
    public void loadFontFromBytes(String name, byte[] fontData) throws IOException {
        if (fontHandles.containsKey(name)) {
            System.err.println("Font already loaded: " + name);
            return;
        }
        
        ByteBuffer buffer = memAlloc(fontData.length);
        buffer.put(fontData);
        buffer.flip();
        
        int handle = NanoVG.nvgCreateFontMem(vg, name, buffer, false);
        
        if (handle == -1) {
            memFree(buffer);
            throw new IOException("Failed to load font from bytes");
        }
        
        fontHandles.put(name, handle);
        System.out.println("Loaded font: " + name + " from byte array");
    }
    
    /**
     * Add a font fallback (for missing glyphs)
     */
    public void addFallback(String baseFont, String fallbackFont) {
        Integer base = fontHandles.get(baseFont);
        Integer fallback = fontHandles.get(fallbackFont);
        
        if (base == null || fallback == null) {
            System.err.println("Cannot add fallback: one or both fonts not loaded");
            return;
        }
        
        nvgAddFallbackFontId(vg, base, fallback);
    }
    
    /**
     * Check if a font is loaded
     */
    public boolean hasFont(String name) {
        return fontHandles.containsKey(name);
    }
    
    /**
     * Get the internal font handle (for advanced use)
     */
    public int getFontHandle(String name) {
        return fontHandles.getOrDefault(name, -1);
    }
    
    /**
     * Apply a font for rendering (convenience method)
     */
    public void applyFont(String name, float size) {
        if (!hasFont(name)) {
            System.err.println("Font not loaded: " + name);
            return;
        }
        
        nvgFontFace(vg, name);
        nvgFontSize(vg, size);
    }
    
    /**
     * Clean up all loaded fonts
     */
    public void dispose() {
        // NanoVG handles font cleanup when context is deleted
        fontHandles.clear();
    }
    
    /**
     * Helper: Load a resource into a ByteBuffer
     */
    private static ByteBuffer ioResourceToByteBuffer(InputStream source, int bufferSize) throws IOException {
        ByteBuffer buffer = memAlloc(bufferSize);
        
        try (ReadableByteChannel rbc = Channels.newChannel(source)) {
            while (true) {
                int bytes = rbc.read(buffer);
                if (bytes == -1) {
                    break;
                }
                if (buffer.remaining() == 0) {
                    // Grow buffer
                    ByteBuffer newBuffer = memAlloc(buffer.capacity() * 2);
                    buffer.flip();
                    newBuffer.put(buffer);
                    memFree(buffer);
                    buffer = newBuffer;
                }
            }
        }
        
        buffer.flip();
        return buffer;
    }
    
    /**
     * Load common system fonts (convenience method)
     */
    public void init() {
        // Try to load common system fonts
        String os = System.getProperty("os.name").toLowerCase();
        loadFont("ocr", ResourceRegistry.PRIMARY_FONT);
        loadFont("sansEmoji", ResourceRegistry.EMOJI_FONT);
      
        if (os.contains("win")) {
            loadFont("sans", "C:/Windows/Fonts/arial.ttf");
            loadFont("sans-bold", "C:/Windows/Fonts/arialbd.ttf");
            loadFont("mono", "C:/Windows/Fonts/consola.ttf");
        } else if (os.contains("mac")) {
            loadFont("sans", "/System/Library/Fonts/Helvetica.ttc");
            loadFont("sans-bold", "/System/Library/Fonts/Helvetica.ttc");
            loadFont("mono", "/System/Library/Fonts/Monaco.ttf");
        } else if (os.contains("nux")) {
            loadFont("sans", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
            loadFont("sans-bold", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf");
            loadFont("mono", "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf");
        }

            



       
    }
}
