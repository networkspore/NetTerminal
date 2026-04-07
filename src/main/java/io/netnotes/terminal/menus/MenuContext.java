package io.netnotes.terminal.menus;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.terminal.menus.MenuChangeEvent.MenuChangeType;

/**
 * MenuContext - Hierarchical menu model for the terminal menu renderer.
 * 
 * Features:
 * - Action items (execute code)
 * - Sub-menus (navigate deeper)
 * - Information display items (show text, no action)
 * - Separators (visual grouping)
 * - Back navigation
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
    private Consumer<MenuChangeEvent> onChanged;
    private int updateDepth = 0;
    private boolean changePending = false;

    private boolean pendingStructural  = false;
    private final Set<String> pendingChangedItems = new LinkedHashSet<>();
    
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
        items.put(name, new MenuItem(name, description, MenuItem.MenuItemType.ACTION, action));
        notifyChanged();
        return this;
    }
    
    /**
     * Add action item with icon/badge
     */
    public MenuContext addItem(String name, String description, String badge, Runnable action) {
        MenuItem item = new MenuItem(name, description, MenuItem.MenuItemType.ACTION, action);
        item.badge = badge;
        items.put(name, item);
        notifyChanged();
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

        items.put(name, new MenuItem(name, description, MenuItem.MenuItemType.SUBMENU, subMenu));
        notifyChanged();
        return this;
    }
    

    /**
     * Add information item (displays text, no action)
     */
    public MenuContext addInfoItem(String name, String description) {
        items.put(name, new MenuItem(name, description, MenuItem.MenuItemType.INFO, null));
        notifyChanged();
        return this;
    }
    
    /**
     * Add separator for visual grouping
     */
    public MenuContext addSeparator(String label) {
        String sepId = "separator-" + items.size();
        items.put(sepId, new MenuItem(sepId, label, MenuItem.MenuItemType.SEPARATOR, null));
        notifyChanged();
        return this;
    }
    
    /**
     * Add back navigation item explicitly
     * (Note: Back is usually automatic if parent exists)
     */
    public MenuContext addBackItem(String description) {
        items.put("back", new MenuItem("back", description != null ? description : "Back", 
            MenuItem.MenuItemType.BACK, null));
        notifyChanged();
        return this;
    }
    
    // ===== NAVIGATION =====
     
    public MenuContext navigate(String itemName) {
        MenuItem item = items.get(itemName);
        if (item == null) throw new IllegalArgumentException("Unknown item: " + itemName);
        if (onItemSelected != null) onItemSelected.accept(itemName);
        return switch (item.type) {
            case ACTION    -> { ((Runnable) item.target).run(); yield this; }
            case SUBMENU   -> (MenuContext) item.target;
            case BACK      -> { if (onBack != null) onBack.run(); yield parent; }
            default        -> this;
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

    public MenuContext updateItem(String name, Consumer<MenuItem> mutator) {
        MenuItem item = items.get(name);
        if (item != null) {
            mutator.accept(item);
            notifyChanged(MenuChangeType.ITEM_UPDATED, name);
        }
        return this;
    }

    public MenuContext beginUpdate() {
        updateDepth++;
        return this;
    }

    public MenuContext endUpdate() {
        if (updateDepth <= 0) return this;
        updateDepth--;
        if (updateDepth == 0 && changePending) {
            changePending = false;
            MenuChangeEvent event = pendingStructural
                ? new MenuChangeEvent(this, MenuChangeType.STRUCTURAL, null)
                : new MenuChangeEvent(this, MenuChangeType.ITEM_UPDATED,
                    pendingChangedItems.size() == 1
                        ? pendingChangedItems.iterator().next()
                        : null); // null item name = "multiple items updated"
            pendingStructural = false;
            pendingChangedItems.clear();
            fireChanged(event);
        }
        return this;
    }

    public MenuContext batchUpdate(Runnable updateBlock) {
        beginUpdate();
        try {
            if (updateBlock != null) {
                updateBlock.run();
            }
        } finally {
            endUpdate();
        }
        return this;
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

    /**
     * Set callback to notify when the menu structure changes so renderables
     * can trigger invalidation/redraw.
     */
    public void setOnChanged(Consumer<MenuChangeEvent> onChanged) {
        this.onChanged = onChanged;
    }

    private void notifyChanged() {                         // structural
        fireChanged(MenuChangeType.STRUCTURAL, null);
    }

    private void notifyChanged(MenuChangeType type, String itemName) {
        fireChanged(type, itemName);
    }

    public void fireChanged() {
        fireChanged(MenuChangeType.STRUCTURAL, null);
    }

    public void fireChanged(MenuChangeType type, String itemName) {
        if (updateDepth > 0) {
            if (type == MenuChangeType.STRUCTURAL) pendingStructural = true;
            if (itemName != null) pendingChangedItems.add(itemName);
            changePending = true;
            return;
        }
        fireChanged(new MenuChangeEvent(this, type, itemName));
    }

    private void fireChanged(MenuChangeEvent event) {
        if (onChanged != null) {
            onChanged.accept(event);
        }
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
    
}
