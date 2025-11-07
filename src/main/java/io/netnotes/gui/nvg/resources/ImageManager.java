package io.netnotes.gui.nvg.resources;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import io.netnotes.gui.core.resources.ResourceRegistry;

/**
 * Manages image loading and registration for NanoVG.
 * 
 * Supports:
 * - Loading PNG, JPG, BMP, TGA
 * - Loading from filesystem
 * - Loading from classpath resources
 * - Loading from byte arrays
 * - Image patterns for fills
 * - Automatic cleanup
 */
class ImageManager {
    private static ImageManager INSTANCE = null;
    private final long vg;
    private final Map<String, Integer> imageHandles = new HashMap<>();
    
    private ImageManager(long vg) {
        this.vg = vg;
        init();
    }

    public static ImageManager getInstance(long vg){
        if(INSTANCE == null){
            INSTANCE = new ImageManager(vg);
        }
        return INSTANCE;
    }
    
    /**
     * Load an image from filesystem
     */
    public void loadImage(String name, String filepath, int flags) throws IOException {
        if (imageHandles.containsKey(name)) {
            System.err.println("Image already loaded: " + name);
            return;
        }
        
        Path path = Paths.get(filepath);
        if (!Files.exists(path)) {
            throw new IOException("Image file not found: " + filepath);
        }
        
        int handle = NanoVG.nvgCreateImage(vg, filepath, flags);
        if (handle == 0) {
            throw new IOException("Failed to load image: " + filepath);
        }
        
        imageHandles.put(name, handle);
        System.out.println("Loaded image: " + name + " from " + filepath);
    }
    
    /**
     * Load an image from filesystem (no flags)
     */
    public void loadImage(String name, String filepath) throws IOException {
        loadImage(name, filepath, 0);
    }

    public void LoadImageSuppressed(String name, String filepath) {
        try{
            loadImage(name, filepath, 0);
        }catch(IOException e){
            System.err.println("Error loading image: " + name + ": " + filepath);
        }
    }
    
    /**
     * Load an image from classpath resources
     */
    public void loadImageFromResource(String name, String resourcePath, int flags) throws IOException {
        if (imageHandles.containsKey(name)) {
            System.err.println("Image already loaded: " + name);
            return;
        }
        
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IOException("Image resource not found: " + resourcePath);
        }
        
        byte[] imageData = stream.readAllBytes();
        loadImageFromBytes(name, imageData, flags);
        
        System.out.println("Loaded image: " + name + " from resource " + resourcePath);
    }
    
    /**
     * Load an image from byte array using STB Image
     */
    public void loadImageFromBytes(String name, byte[] imageData, int flags) throws IOException {
        if (imageHandles.containsKey(name)) {
            System.err.println("Image already loaded: " + name);
            return;
        }
        
        ByteBuffer buffer = memAlloc(imageData.length);
        buffer.put(imageData);
        buffer.flip();
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            
            ByteBuffer imageBuffer = STBImage.stbi_load_from_memory(buffer, w, h, channels, 4);
            
            if (imageBuffer == null) {
                memFree(buffer);
                throw new IOException("Failed to decode image: " + STBImage.stbi_failure_reason());
            }
            
            int width = w.get(0);
            int height = h.get(0);
            
            int handle = NanoVG.nvgCreateImageRGBA(vg, width, height, flags, imageBuffer);
            
            STBImage.stbi_image_free(imageBuffer);
            memFree(buffer);
            
            if (handle == 0) {
                throw new IOException("Failed to create NanoVG image");
            }
            
            imageHandles.put(name, handle);
        }
    }
    
    /**
     * Create an empty image (for dynamic rendering)
     */
    public void createEmptyImage(String name, int width, int height, int flags) {
        if (imageHandles.containsKey(name)) {
            System.err.println("Image already exists: " + name);
            return;
        }
        
        int handle = NanoVG.nvgCreateImageRGBA(vg, width, height, flags, (ByteBuffer) null);
        if (handle == 0) {
            System.err.println("Failed to create empty image: " + name);
            return;
        }
        
        imageHandles.put(name, handle);
    }
    
    /**
     * Update an image's pixel data
     */
    public void updateImage(String name, byte[] pixelData) {
        Integer handle = imageHandles.get(name);
        if (handle == null) {
            System.err.println("Image not found: " + name);
            return;
        }
        
        ByteBuffer buffer = memAlloc(pixelData.length);
        buffer.put(pixelData);
        buffer.flip();
        
        NanoVG.nvgUpdateImage(vg, handle, buffer);
        
        memFree(buffer);
    }
    
    /**
     * Check if an image is loaded
     */
    public boolean hasImage(String name) {
        return imageHandles.containsKey(name);
    }
    
    /**
     * Get the internal image handle
     */
    public int getImageHandle(String name) {
        return imageHandles.getOrDefault(name, 0);
    }
    
    /**
     * Get image dimensions
     */
    public int[] getImageSize(String name) {
        Integer handle = imageHandles.get(name);
        if (handle == null) {
            return new int[]{0, 0};
        }
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            
            NanoVG.nvgImageSize(vg, handle, w, h);
            
            return new int[]{w.get(0), h.get(0)};
        }
    }
    
    /**
     * Delete a specific image
     */
    public void deleteImage(String name) {
        Integer handle = imageHandles.remove(name);
        if (handle != null) {
            NanoVG.nvgDeleteImage(vg, handle);
        }
    }
    
    /**
     * Clean up all loaded images
     */
    public void dispose() {
        for (Integer handle : imageHandles.values()) {
            NanoVG.nvgDeleteImage(vg, handle);
        }
        imageHandles.clear();
    }

    public static final String APP_ICON_15 = "APP_ICON_15";
    public static final String APP_LOGO_256 = "APP_LOGO_256";
    public static final String CLOSE_ICON = "CLOSE_ICON";
    public static final String MINIMIZE_ICON = "MINIMIZE_ICON";
    public static final String MAXIMIZE_ICON = "MAXIMIZE_ICON";
    public static final String NETWORK_ICON256 = "NETWORK_ICON256";
    public static final String NETWORK_ICON = "NETWORK_ICON";
    public static final String SETTINGS_ICON_120 = "SETTINGS_ICON_120";
    public static final String UNKNOWN_IMAGE_PATH = "UNKNOWN_IMAGE_PATH";
    
    private void init(){
        LoadImageSuppressed(APP_ICON_15, ResourceRegistry.APP_ICON_15);
        LoadImageSuppressed(APP_LOGO_256, ResourceRegistry.APP_LOGO_256);
        LoadImageSuppressed(CLOSE_ICON, ResourceRegistry.CLOSE_ICON);
        LoadImageSuppressed(MINIMIZE_ICON, ResourceRegistry.MINIMIZE_ICON);
        LoadImageSuppressed(MAXIMIZE_ICON, ResourceRegistry.MAXIMIZE_ICON);
        LoadImageSuppressed(NETWORK_ICON256, ResourceRegistry.NETWORK_ICON256);
        LoadImageSuppressed(NETWORK_ICON, ResourceRegistry.NETWORK_ICON);
        LoadImageSuppressed(SETTINGS_ICON_120, ResourceRegistry.SETTINGS_ICON_120);
        LoadImageSuppressed(UNKNOWN_IMAGE_PATH, ResourceRegistry.UNKNOWN_IMAGE_PATH);
    }
}

