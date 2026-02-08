package io.netnotes.terminal.menus;

import java.util.*;
import io.netnotes.terminal.*;
import io.netnotes.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.*;
import io.netnotes.engine.io.input.events.*;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.noteBytes.KeyRunTable;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesRunnablePair;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * MenuNavigator - Damage-aware menu component
 * 
 * DAMAGE TRACKING:
 * - Selection changes only invalidate affected menu items
 * - Scroll changes only invalidate scroll indicators
 * - Full menu changes invalidate entire allocated area
 */
public class MenuNavigator extends TerminalRenderable {
    
    private int horizontalScrollOffset = 0;
    private final Stack<MenuContext> navigationStack = new Stack<>();
    private MenuContext currentMenu;
    
    // Selection state
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    
    private static final int MAX_VISIBLE_ITEMS = 15;
    
    // Event filtering
    private EventFilter keyboardFilter = null;
    private NoteBytesReadOnly keyHandlerId = null;
    
    // Key mapping
    private final KeyRunTable keyRunTable = new KeyRunTable(new NoteBytesRunnablePair[]{
        new NoteBytesRunnablePair(KeyCodeBytes.UP, this::handleNavigateUp),
        new NoteBytesRunnablePair(KeyCodeBytes.DOWN, this::handleNavigateDown),
        new NoteBytesRunnablePair(KeyCodeBytes.LEFT, this::handleScrollLeft),
        new NoteBytesRunnablePair(KeyCodeBytes.RIGHT, this::handleScrollRight),
        new NoteBytesRunnablePair(KeyCodeBytes.ENTER, this::handleSelectCurrent),
        new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, this::handleBack),
        new NoteBytesRunnablePair(KeyCodeBytes.PAGE_UP, this::handlePageUp),
        new NoteBytesRunnablePair(KeyCodeBytes.PAGE_DOWN, this::handlePageDown),
        new NoteBytesRunnablePair(KeyCodeBytes.HOME, this::handleHome),
        new NoteBytesRunnablePair(KeyCodeBytes.END, this::handleEnd),
    });
    
    // States
    public static final int IDLE = 10;
    public static final int DISPLAYING_MENU = 11;
    public static final int NAVIGATING = 12;
    public static final int WAITING_PASSWORD = 13;
    public static final int EXECUTING_ACTION = 14;
    
    public MenuNavigator(String name) {
        super(name);
        stateMachine.addState(IDLE);
    }
    
    @Override
    protected void setupStateTransitions() {
        stateMachine.onStateAdded(IDLE, (old, now, bit) -> {
            removeKeyboardHandler();
        });
        
        stateMachine.onStateAdded(DISPLAYING_MENU, (old, now, bit) -> {
            registerKeyboardHandler();
        });
        
        stateMachine.onStateAdded(WAITING_PASSWORD, (old, now, bit) -> {
            removeKeyboardHandler();
        });
        
        stateMachine.onStateRemoved(WAITING_PASSWORD, (old, now, bit) -> {
            if (stateMachine.hasState(DISPLAYING_MENU)) {
                registerKeyboardHandler();
            }
        });
    }
    
    public void setKeyboardFilter(EventFilter filter) {
        this.keyboardFilter = filter;
        if (stateMachine.hasState(DISPLAYING_MENU)) {
            removeKeyboardHandler();
            registerKeyboardHandler();
        }
    }
    
    public EventFilter getKeyboardFilter() {
        return keyboardFilter;
    }
    
    private void registerKeyboardHandler() {
        if (keyHandlerId != null) return;
        
        if (keyboardFilter != null) {
            keyHandlerId = addKeyDownHandler(this::handleKeyboardEvent, keyboardFilter);
        } else {
            keyHandlerId = addKeyDownHandler(this::handleKeyboardEvent);
        }
    }
    
    private void removeKeyboardHandler() {
        if (keyHandlerId != null) {
            removeKeyDownHandler(keyHandlerId);
            keyHandlerId = null;
        }
    }
    
    @Override
    public void onFocusGained() {
        super.onFocusGained();
        // Invalidate header and footer to update focus indicators
        invalidateHLine(0, 0, getWidth());  // Header area
        invalidateHLine(0, getHeight() - 2, getWidth());  // Footer area
    }

    @Override
    protected void onFocusLost() {
        super.onFocusLost();
        // Invalidate header and footer to update focus indicators
        invalidateHLine(0, 0, getWidth());  // Header area
        invalidateHLine(0, getHeight() - 2, getWidth());  // Footer area
    }
    
    // ===== DAMAGE-AWARE RENDERING =====
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (currentMenu == null) return;
        
        // ✓ Use helper methods instead of allocatedRegion
        int width = getWidth();
        int height = getHeight();
        
        if (width <= 0 || height <= 0) return;
        
        // ✓ Calculate dimensions with local width (no base coordinates needed)
        MenuDimensions dims = calculateMenuDimensions(currentMenu, width);
        
        // ✓ Track current Y position as we stack render sections
        int currentY = 0;
        
        // ✓ Each render method returns the next available Y position
        currentY = renderHeader(batch, currentMenu, dims, currentY);
        
        if (!navigationStack.isEmpty()) {
            currentY = renderBreadcrumb(batch, currentMenu, dims, currentY);
        }
        
        String description = currentMenu.getDescription();
        if (description != null && !description.isEmpty()) {
            currentY = renderDescription(batch, currentMenu, dims, currentY);
        }
        
        currentY = renderMenuItems(batch, currentMenu, dims, selectedIndex, 
            scrollOffset, horizontalScrollOffset, currentY, height);
        
        renderFooter(batch, currentMenu, dims, !navigationStack.isEmpty(), 
            currentY, height);
    }
    
    /**
     * Invalidate only the changed menu items
     */
    private void invalidateSelectionChange(int oldIndex, int newIndex) {
        if (currentMenu == null) return;
        
        // ✓ Calculate start Y for menu items section
        String description = currentMenu.getDescription();
        int descriptionLines = 0;
        if (description != null && !description.isEmpty()) {
            descriptionLines = Math.min(description.split("\n").length + 1, 9);
        }
        
        // Header (3) + breadcrumb (2) + description + spacing
        int startY = 5 + descriptionLines;
        
        int width = getWidth();
        
        // ✓ Invalidate old selected item
        if (oldIndex >= 0 && oldIndex < MAX_VISIBLE_ITEMS) {
            invalidateHLine(0, startY + oldIndex, width);
        }
        
        // ✓ Invalidate new selected item
        if (newIndex >= 0 && newIndex < MAX_VISIBLE_ITEMS) {
            invalidateHLine(0, startY + newIndex, width);
        }
    }
    
    // ===== RENDERING HELPERS =====
    
    /**
     * Render the menu header box with title
     * @return Next available Y position after this section
     */
    private int renderHeader(TerminalBatchBuilder batch, MenuContext menu,
                            MenuDimensions dims, int localY) {
        String title = menu.getTitle();
        int boxX = dims.getBoxXOffset();
        int boxWidth = dims.getBoxWidth();
        
        // Header box is 3 rows tall
        int boxHeight = 3;
        
        // ✓ Use base class drawBox with LOCAL coordinates
        // ✓ Add visual indicator if MenuNavigator has focus
        BoxStyle style = hasFocus() ? BoxStyle.DOUBLE : BoxStyle.SINGLE;
        TextStyle titleStyle = hasFocus() ? TextStyle.BOLD : TextStyle.NORMAL;

        drawBox(batch, boxX, localY, boxWidth, boxHeight, title,Position.CENTER, style, titleStyle);
        
        // Return next available Y position (after the 3-row header box)
        return localY + boxHeight;
    }
    
    private int renderBreadcrumb(TerminalBatchBuilder batch, MenuContext menu, MenuDimensions dims, int localY) {
        // Build breadcrumb trail from root to current menu
        List<String> trail = new ArrayList<>();
        MenuContext current = menu;
        
        while (current != null) {
            trail.add(0, current.getTitle());
            current = current.getParent();
        }
        
        String breadcrumb = String.join(" > ", trail);
        int boxX = dims.getBoxXOffset();
        int boxWidth = dims.getBoxWidth();
        
        // ✓ Truncate breadcrumb if too long for box
        if (breadcrumb.length() > boxWidth - 4) {
            breadcrumb = "..." + breadcrumb.substring(breadcrumb.length() - (boxWidth - 7));
        }
        
        // ✓ Center breadcrumb horizontally in box
        int textX = boxX + (boxWidth - breadcrumb.length()) / 2;
        
        // ✓ Use base class printAt with LOCAL coordinates
        printAt(batch, textX, localY, breadcrumb, TextStyle.INFO);
        
        // Breadcrumb takes 1 row, plus 1 row spacing
        return localY + 2;
    }
    
    private int renderDescription(TerminalBatchBuilder batch, MenuContext menu,
                             MenuDimensions dims, int localY) {
        String description = menu.getDescription();
        if (description == null || description.isEmpty()) {
            return localY;
        }
        
        String[] lines = description.split("\n");
        int boxX = dims.getBoxXOffset();
        int maxLines = Math.min(lines.length, 8);  // Limit to 8 lines
        
        // ✓ Render each line with LOCAL coordinates
        for (int i = 0; i < maxLines; i++) {
            String line = lines[i];
            int lineY = localY + i;
            int lineX = boxX + 4;  // 4 chars indent from box edge
            
            // ✓ Truncate line if needed
            int maxLength = dims.getBoxWidth() - 8;
            if (line.length() > maxLength) {
                line = line.substring(0, maxLength - 3) + "...";
            }
            
            // ✓ Use base class printAt with LOCAL coordinates
            printAt(batch, lineX, lineY, line, TextStyle.NORMAL);
        }
        
        // Return next Y after description lines plus 1 row spacing
        return localY + maxLines + 1;
    }
    
    private int renderMenuItems(TerminalBatchBuilder batch, MenuContext menu, MenuDimensions dims, 
        int selectedIdx, int scrollOff, int horizScroll, int localY, int totalHeight
    ) {
        List<MenuContext.MenuItem> items = new ArrayList<>(menu.getItems());
        List<MenuContext.MenuItem> selectableItems = items.stream()
            .filter(item -> item.type != MenuContext.MenuItemType.SEPARATOR &&
                        item.type != MenuContext.MenuItemType.INFO)
            .toList();
        
        // Clamp selected index to valid range
        int actualSelectedIdx = Math.min(selectedIdx, 
            Math.max(0, selectableItems.size() - 1));
        
        // Calculate visible window
        int totalItems = items.size();
        int visibleStart = scrollOff;
        int visibleEnd = Math.min(visibleStart + MAX_VISIBLE_ITEMS, totalItems);
        
        int startY = localY;
        int boxX = dims.getBoxXOffset();
        int boxWidth = dims.getBoxWidth();
        
        // ✓ Render scroll indicator at top if needed
        if (scrollOff > 0) {
            String indicator = "↑ More above";
            int indicatorX = boxX + (boxWidth - indicator.length()) / 2;
            printAt(batch, indicatorX, startY - 1, indicator, TextStyle.INFO);
        }
        
        // ✓ Track selectable index separately from item index
        int selectableIndex = 0;
        
        // ✓ Render visible items
        for (int i = visibleStart; i < visibleEnd; i++) {
            MenuContext.MenuItem item = items.get(i);
            int itemY = startY + (i - visibleStart);
            
            // Check if this item is selected
            boolean isSelected = (item.type != MenuContext.MenuItemType.SEPARATOR &&
                                item.type != MenuContext.MenuItemType.INFO &&
                                selectableIndex == actualSelectedIdx);
            
            // ✓ Render individual item with LOCAL coordinates
            renderMenuItem(batch, item, itemY, isSelected, dims, horizScroll);
            
            // Increment selectable counter for non-separator/info items
            if (item.type != MenuContext.MenuItemType.SEPARATOR &&
                item.type != MenuContext.MenuItemType.INFO) {
                selectableIndex++;
            }
        }
        
        // ✓ Render scroll indicator at bottom if needed
        if (visibleEnd < totalItems) {
            String indicator = "↓ More below";
            int indicatorX = boxX + (boxWidth - indicator.length()) / 2;
            int indicatorY = startY + (visibleEnd - visibleStart);
            printAt(batch, indicatorX, indicatorY, indicator, TextStyle.INFO);
        }
        
        // Return next Y after items section
        return startY + MAX_VISIBLE_ITEMS;
    }
    
    private void renderMenuItem(TerminalBatchBuilder batch, MenuContext.MenuItem item, int localY, 
        boolean isSelected, MenuDimensions dims, int horizScroll
    ) {
        int boxX = dims.getBoxXOffset();
        int contentStartX = boxX + 2;  // 2 chars padding from box edge
        int contentWidth = dims.getItemContentWidth();
        
        switch (item.type) {
            case SEPARATOR -> {
                // ✓ Draw separator line
                String separatorLine = "─".repeat(contentWidth);
                printAt(batch, contentStartX, localY, separatorLine, TextStyle.NORMAL);
                
                // ✓ Optional label in center of separator
                if (item.description != null && !item.description.isEmpty()) {
                    int labelX = contentStartX + 
                        (contentWidth - item.description.length()) / 2;
                    printAt(batch, labelX, localY, 
                        " " + item.description + " ", TextStyle.BOLD);
                }
            }
            
            case INFO -> {
                // ✓ Centered info text
                int infoX = contentStartX + 
                    (contentWidth - item.description.length()) / 2;
                printAt(batch, infoX, localY, item.description, TextStyle.INFO);
            }
            
            default -> {
                // ✓ Regular menu item with optional badge
                String badge = item.badge != null ? " [" + item.badge + "]" : "";
                String itemText = item.description + badge;
                
                if (isSelected) {
                    // ✓ Selected item with ">" indicator and inverse colors
                    String indicator = "> ";
                    int textWidth = contentWidth - indicator.length();
                    
                    String displayText;
                    if (itemText.length() <= textWidth) {
                        displayText = itemText;
                    } else {
                        // ✓ Apply horizontal scroll for long items
                        int startIdx = Math.min(horizScroll, 
                            Math.max(0, itemText.length() - textWidth));
                        int endIdx = Math.min(itemText.length(), startIdx + textWidth);
                        displayText = itemText.substring(startIdx, endIdx);
                    }
                    
                    // ✓ Pad to full width for proper inverse highlighting
                    displayText = String.format("%-" + textWidth + "s", displayText);
                    
                    // ✓ Render with inverse style and focus indicator
                    printAt(batch, contentStartX, localY, indicator + displayText, 
                        TextStyle.INVERSE);
                } else {
                    // ✓ Unselected item with indent
                    String displayText = "  " + truncateText(itemText, contentWidth - 2);
                    
                    // ✓ Dim disabled items
                    TextStyle style = item.enabled ? TextStyle.NORMAL : TextStyle.INFO;
                    printAt(batch, contentStartX, localY, displayText, style);
                }
            }
        }
    }
    
    private void renderFooter(TerminalBatchBuilder batch, MenuContext menu, MenuDimensions dims, boolean hasParent,
        int localY, int totalHeight
    ) {
        int boxX = dims.getBoxXOffset();
        int boxWidth = dims.getBoxWidth();
        
        // ✓ Calculate footer position (near bottom of component)
        int footerY = totalHeight - 2;
        
        // ✓ Different help text based on context
        String help = hasParent || menu.hasParent()
            ? "↑↓: Navigate  ←→: Scroll Text  Enter: Select  ESC: Back  Home/End: Jump"
            : "↑↓: Navigate  ←→: Scroll Text  Enter: Select  Home/End: Jump";
        
        // ✓ Truncate if needed
        if (help.length() > boxWidth - 4) {
            help = help.substring(0, boxWidth - 7) + "...";
        }
        
        int helpX = boxX + (boxWidth - help.length()) / 2;
        
        // ✓ Draw separator line above help text
        drawHLine(batch, boxX, footerY - 1, boxWidth);
        
        // ✓ Render help text
        // ✓ Highlight help text if MenuNavigator has focus
        TextStyle helpStyle = hasFocus() ? TextStyle.INFO : TextStyle.NORMAL;
        printAt(batch, helpX, footerY, help, helpStyle);
    }
    
    // ===== EVENT HANDLING =====
    
    private void handleKeyboardEvent(RoutedEvent event) {
        if (!stateMachine.hasState(DISPLAYING_MENU)) return;
        
        if (event instanceof EphemeralRoutedEvent ephemeralEvent) {
            try (ephemeralEvent) {
                if (ephemeralEvent instanceof EphemeralKeyDownEvent ekd) {
                    keyRunTable.run(ekd.getKeyCodeBytes());
                    event.setConsumed(true);
                }
            }
            return;
        }
        
        if (event instanceof KeyDownEvent keyDown) {
            keyRunTable.run(keyDown.getKeyCodeBytes());
            event.setConsumed(true);
        }
    }
    
    public void resetScrollOffset() {
        horizontalScrollOffset = 0;
    }
    
    // ===== NAVIGATION HANDLERS WITH SMART INVALIDATION =====
    
    private void handleNavigateUp() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        int oldIndex = selectedIndex;
        selectedIndex = (selectedIndex - 1 + selectableItems.size()) % 
            selectableItems.size();
        
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
            invalidate(); // Full redraw on scroll
        } else {
            invalidateSelectionChange(oldIndex, selectedIndex);
        }
    }
    
    private void handleNavigateDown() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        int oldIndex = selectedIndex;
        selectedIndex = (selectedIndex + 1) % selectableItems.size();
        
        if (selectedIndex >= scrollOffset + MAX_VISIBLE_ITEMS) {
            scrollOffset = selectedIndex - MAX_VISIBLE_ITEMS + 1;
            invalidate(); // Full redraw on scroll
        } else {
            invalidateSelectionChange(oldIndex, selectedIndex);
        }
    }
    
    private void handleSelectCurrent() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        
        if (selectedIndex < 0 || selectedIndex >= selectableItems.size()) return;
        
        MenuContext.MenuItem selectedItem = selectableItems.get(selectedIndex);
        
        stateMachine.removeState(DISPLAYING_MENU);
        stateMachine.addState(NAVIGATING);
        
        currentMenu.navigate(selectedItem.name)
            .thenAccept(targetMenu -> {
                stateMachine.removeState(NAVIGATING);
                
                if (targetMenu == null) {
                    stateMachine.addState(WAITING_PASSWORD);
                } else if (targetMenu == currentMenu) {
                    stateMachine.addState(DISPLAYING_MENU);
                } else {
                    showMenu(targetMenu);
                }
            })
            .exceptionally(ex -> {
                Log.logError("[MenuNavigator] Navigation failed: " + ex.getMessage());
                stateMachine.removeState(NAVIGATING);
                stateMachine.addState(DISPLAYING_MENU);
                return null;
            });
    }
    
    private void handleBack() {
        if (navigationStack.isEmpty()) {
            // At root
        } else {
            MenuContext previousMenu = navigationStack.pop();
            currentMenu = previousMenu;
            selectedIndex = 0;
            scrollOffset = 0;
            
            stateMachine.removeState(WAITING_PASSWORD);
            stateMachine.removeState(EXECUTING_ACTION);
            stateMachine.addState(DISPLAYING_MENU);
            
            invalidate(); // Full redraw on menu change
        }
    }
    
    private void handlePageUp() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = Math.max(0, selectedIndex - MAX_VISIBLE_ITEMS);
        scrollOffset = Math.max(0, scrollOffset - MAX_VISIBLE_ITEMS);
        invalidate();
    }
    
    private void handlePageDown() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = Math.min(selectableItems.size() - 1, 
            selectedIndex + MAX_VISIBLE_ITEMS);
        
        int maxScroll = Math.max(0, selectableItems.size() - MAX_VISIBLE_ITEMS);
        scrollOffset = Math.min(maxScroll, scrollOffset + MAX_VISIBLE_ITEMS);
        
        invalidate();
    }
    
    private void handleHome() {
        selectedIndex = 0;
        scrollOffset = 0;
        invalidate();
    }
    
    private void handleEnd() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = selectableItems.size() - 1;
        scrollOffset = Math.max(0, selectableItems.size() - MAX_VISIBLE_ITEMS);
        invalidate();
    }
    
    private void handleScrollRight() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty() || selectedIndex >= selectableItems.size()) 
            return;
        
        MenuContext.MenuItem item = selectableItems.get(selectedIndex);
        String itemText = item.description + 
            (item.badge != null ? " [" + item.badge + "]" : "");
        
        MenuDimensions dims = calculateMenuDimensions(currentMenu, region.getWidth());
        int maxWidth = dims.getItemContentWidth() - 2;
        
        if (itemText.length() > maxWidth) {
            int maxOffset = itemText.length() - maxWidth;
            int newOffset = Math.min(maxOffset, horizontalScrollOffset + 5);
            
            if (newOffset != horizontalScrollOffset) {
                horizontalScrollOffset = newOffset;
                invalidateSelectionChange(selectedIndex, selectedIndex);
            }
        }
    }
    
    private void handleScrollLeft() {
        if (horizontalScrollOffset > 0) {
            horizontalScrollOffset = Math.max(0, horizontalScrollOffset - 5);
            invalidateSelectionChange(selectedIndex, selectedIndex);
        }
    }
    
    // ===== HELPERS =====
    
    private List<MenuContext.MenuItem> getSelectableItems() {
        if (currentMenu == null) return List.of();
        
        return currentMenu.getItems().stream()
            .filter(item -> item.type != MenuContext.MenuItemType.SEPARATOR &&
                           item.type != MenuContext.MenuItemType.INFO)
            .toList();
    }
    
    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
    
    private MenuDimensions calculateMenuDimensions(MenuContext menu, int availableWidth) {
        List<MenuContext.MenuItem> items = new ArrayList<>(menu.getItems());
        
        // Calculate maximum text length for sizing
        int maxTextLength = menu.getTitle().length();
        for (MenuContext.MenuItem item : items) {
            int itemLength = item.description.length();
            if (item.badge != null) {
                itemLength += item.badge.length() + 3;  // " [badge]"
            }
            maxTextLength = Math.max(maxTextLength, itemLength);
        }
        
        // Calculate box dimensions
        int contentWidth = maxTextLength + 8;
        int maxAllowedWidth = availableWidth - 8;
        int boxWidth = Math.max(40, Math.min(contentWidth, maxAllowedWidth));
        
        // ✓ Calculate offset from LEFT edge of component (not absolute position)
        int boxXOffset = (availableWidth - boxWidth) / 2;
        int itemContentWidth = boxWidth - 4;  // 2 chars padding on each side
        
        return new MenuDimensions(boxWidth, boxXOffset, itemContentWidth);
    }
    
    // ===== MENU CONTROL =====
    
    public void showMenu(MenuContext menu) {
        if (menu == null) return;
        
        if (currentMenu != null && currentMenu != menu) {
            navigationStack.push(currentMenu);
        }
        
        currentMenu = menu;
        selectedIndex = 0;
        scrollOffset = 0;
        
        stateMachine.removeState(IDLE);
        stateMachine.removeState(NAVIGATING);
        stateMachine.removeState(EXECUTING_ACTION);
        stateMachine.addState(DISPLAYING_MENU);
        
        invalidate(); // Full invalidation on menu change
    }
    
    public void refreshMenu() {
        if (stateMachine.hasState(DISPLAYING_MENU) && currentMenu != null) {
            invalidate();
        }
    }
    
    public void onPasswordSuccess(String menuItemName) {
        if (!stateMachine.hasState(WAITING_PASSWORD)) return;
        
        stateMachine.removeState(WAITING_PASSWORD);
        stateMachine.addState(NAVIGATING);
        
        currentMenu.navigate(menuItemName)
            .thenAccept(targetMenu -> {
                stateMachine.removeState(NAVIGATING);
                if (targetMenu != null) {
                    showMenu(targetMenu);
                } else {
                    stateMachine.addState(DISPLAYING_MENU);
                }
            })
            .exceptionally(ex -> {
                stateMachine.removeState(NAVIGATING);
                stateMachine.addState(DISPLAYING_MENU);
                return null;
            });
    }
    
    public void onPasswordCancelled() {
        stateMachine.removeState(WAITING_PASSWORD);
        stateMachine.addState(DISPLAYING_MENU);
    }
    
    public void cleanup() {
        removeKeyboardHandler();
    }
    
    // ===== GETTERS =====
    
    public MenuContext getCurrentMenu() { return currentMenu; }
    public boolean hasMenu() { return currentMenu != null; }
    public boolean isDisplayingMenu() { 
        return stateMachine.hasState(DISPLAYING_MENU); 
    }
    public boolean isWaitingForPassword() { 
        return stateMachine.hasState(WAITING_PASSWORD); 
    }
}