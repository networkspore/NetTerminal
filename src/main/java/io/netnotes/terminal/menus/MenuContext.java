package io.netnotes.terminal.menus;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.ui.UIProtocol;

/**
 * MenuContext - Hierarchical menu navigation with UI protocol
 * 
 * Features:
 * - Action items (execute code)
 * - Sub-menus (navigate deeper)
 * - Protected sub-menus (password required)
 * - Information display items (show text, no action)
 * - Separators (visual grouping)
 * - Back navigation
 * - Breadcrumb tracking
 */
public class MenuContext {
    
    private final ContextPath currentPath;
    private final MenuContext parent;
    private final Map<String, MenuItem> items = new LinkedHashMap<>();
    private final String title;
    private final String description; // Optional subtitle/description
   
    
    // Callbacks
    private Consumer<String> onItemSelected;
    private Runnable onBack;
    
    public MenuContext(ContextPath path, String title) {
        this(path, title, null, null);
    }
    
    public MenuContext(ContextPath path, String title, MenuContext parent) {
        this(path, title, null, parent);
    }
    
    public MenuContext(ContextPath path, String title, String description, MenuContext parent) {
        this.currentPath = path;
        this.title = title;
        this.description = description;
        this.parent = parent;
    }
    
    // ===== MENU BUILDING =====
    
    /**
     * Add action item
     */
    public MenuContext addItem(String name, String description, Runnable action) {
        items.put(name, new MenuItem(name, description, MenuItemType.ACTION, action));
        return this;
    }
    
    /**
     * Add action item with icon/badge
     */
    public MenuContext addItem(String name, String description, String badge, Runnable action) {
        MenuItem item = new MenuItem(name, description, MenuItemType.ACTION, action);
        item.badge = badge;
        items.put(name, item);
        return this;
    }
    
    /**
     * Add sub-menu
     */
    public MenuContext addSubMenu(
            String name,
            String description,
            Function<MenuContext, MenuContext> builder) {
        
        ContextPath subPath = currentPath.append(name);
        MenuContext subMenu = new MenuContext(subPath, description, this);
        builder.apply(subMenu);
        
        items.put(name, new MenuItem(name, description, MenuItemType.SUBMENU, subMenu));
        return this;
    }
    

    /**
     * Add information item (displays text, no action)
     */
    public MenuContext addInfoItem(String name, String description) {
        items.put(name, new MenuItem(name, description, MenuItemType.INFO, null));
        return this;
    }
    
    /**
     * Add separator for visual grouping
     */
    public MenuContext addSeparator(String label) {
        String sepId = "separator-" + items.size();
        items.put(sepId, new MenuItem(sepId, label, MenuItemType.SEPARATOR, null));
        return this;
    }
    
    /**
     * Add back navigation item explicitly
     * (Note: Back is usually automatic if parent exists)
     */
    public MenuContext addBackItem(String description) {
        items.put("back", new MenuItem("back", description != null ? description : "Back", 
            MenuItemType.BACK, null));
        return this;
    }
    
    // ===== DISPLAY =====
    

          /**
     * Build display command (caller will send through container)
     */
    public NoteBytesMap buildDisplayCommand() {
        List<UIProtocol.MenuItem> uiItems = new ArrayList<>();
        
        for (MenuItem item : items.values()) {
            uiItems.add(new UIProtocol.MenuItem(
                item.name,
                item.description,
                convertType(item.type)
            ));
        }
        
        NoteBytesMap menuCmd = UIProtocol.showMenu(
            title,
            currentPath.toString(),
            uiItems,
            parent != null
        );

        // Add description if present
        if (description != null && !description.isEmpty()) {
            menuCmd.put("description", new NoteBytes(description));
        }
        
        // Add breadcrumb trail
        menuCmd.put("breadcrumb", new NoteBytes(getBreadcrumb()));
        
        
        if (description != null) {
            menuCmd.put("description", new NoteBytes(description));
        }
        
        return menuCmd;
    }

    
    /**
     * Get breadcrumb trail (path from root to current)
     */
    private String getBreadcrumb() {
        List<String> trail = new ArrayList<>();
        MenuContext current = this;
        
        while (current != null) {
            trail.add(0, current.title);
            current = current.parent;
        }
        
        return String.join(" > ", trail);
    }
    
    // ===== NAVIGATION =====
    
    /**
     * Navigate to item by name
     */
    public CompletableFuture<MenuContext> navigate(String itemName) {
        MenuItem item = items.get(itemName);
        if (item == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown item: " + itemName));
        }
        
        // Notify callback
        if (onItemSelected != null) {
            onItemSelected.accept(itemName);
        }
        
        return switch (item.type) {
            case ACTION -> {
                ((Runnable) item.target).run();
                yield CompletableFuture.completedFuture(this); // Stay on same menu
            }
            case SUBMENU -> CompletableFuture.completedFuture((MenuContext) item.target);
            case BACK -> {
                if (onBack != null) {
                    onBack.run();
                }
                yield CompletableFuture.completedFuture(parent);
            }
            case INFO, SEPARATOR -> CompletableFuture.completedFuture(this); // Stay
        };
    }
    
    /**
     * Back to parent
     */
    public MenuContext back() {
        if (onBack != null) {
            onBack.run();
        }
        return parent;
    }
    
    /**
     * Find item by name
     */
    public MenuItem getItem(String name) {
        return items.get(name);
    }
    
    /**
     * Get all items
     */
    public Collection<MenuItem> getItems() {
        return Collections.unmodifiableCollection(items.values());
    }

    // ===== CALLBACKS =====
    
    /**
     * Set callback for when item is selected
     */
    public void setOnItemSelected(Consumer<String> callback) {
        this.onItemSelected = callback;
    }
    
    /**
     * Set callback for when back is pressed
     */
    public void setOnBack(Runnable callback) {
        this.onBack = callback;
    }
    
    // ===== GETTERS =====
    
    public String getTitle() {
        return title;
    }
    
    public String getDescription() {
        return description;
    }
    
    public ContextPath getPath() {
        return currentPath;
    }
    
    public MenuContext getParent() {
        return parent;
    }
    
    public boolean hasParent() {
        return parent != null;
    }
    
    // ===== UTILITIES =====
    
    /**
     * Convert internal type to UI protocol type
     */
    private UIProtocol.MenuItemType convertType(MenuItemType type) {
        return switch (type) {
            case ACTION -> UIProtocol.MenuItemType.ACTION;
            case SUBMENU -> UIProtocol.MenuItemType.SUBMENU;
            case INFO, SEPARATOR, BACK -> UIProtocol.MenuItemType.ACTION;
        };
    }
    
    // ===== INNER CLASSES =====
    
    /**
     * Menu item
     */
    public static class MenuItem {
        public final String name;
        public final String description;
        public final MenuItemType type;
        public final Object target;
        public String badge; // Optional badge/icon
        public boolean enabled = true;
        
        MenuItem(String name, String description, MenuItemType type, Object target) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.target = target;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public void setBadge(String badge) {
            this.badge = badge;
        }
    }
    
    /**
     * Menu item types
     */
    public enum MenuItemType {
        ACTION,              // Execute runnable
        SUBMENU,            // Navigate to sub-menu
        INFO,               // Display only (no action)
        SEPARATOR,          // Visual separator
        BACK                // Explicit back navigation
    }
}