package io.netnotes.terminal.menus;

public class MenuChangeEvent {
    public enum MenuChangeType {
        STRUCTURAL,    // item added, removed, reordered — full rebuild required
        ITEM_UPDATED   // property on existing item (badge, enabled) — targeted invalidate
    }

    private final MenuContext      source;
    private final MenuChangeType   type;
    private final String           itemName; // null for STRUCTURAL

    MenuChangeEvent(MenuContext source, MenuChangeType type, String itemName) {
        this.source   = source;
        this.type     = type;
        this.itemName = itemName;
    }

    public MenuContext getSource() {
        return source;
    }

    public MenuChangeType getType() {
        return type;
    }

    public String getItemName() {
        return itemName;
    }

    
}