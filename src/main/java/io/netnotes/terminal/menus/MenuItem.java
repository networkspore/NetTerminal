package io.netnotes.terminal.menus;


/**
 * Menu item
 */
public class MenuItem {
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

    final String name;
    final String description;
    final MenuItem.MenuItemType type;
    final Object target;
    String badge; // Optional badge/icon
    boolean enabled = true;
    
    MenuItem(String name, String description, MenuItem.MenuItemType type, Object target) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.target = target;
    }
    
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    void setBadge(String badge) {
        this.badge = badge;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public MenuItem.MenuItemType getType() {
        return type;
    }

    public Object getTarget() {
        return target;
    }

    public String getBadge() {
        return badge;
    }

    public boolean isEnabled() {
        return enabled;
    }

    
}