package io.netnotes.terminal.menus;

import java.util.*;
import io.netnotes.terminal.*;
import io.netnotes.terminal.TextStyle.BoxStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.components.panels.TerminalVStack;
import io.netnotes.terminal.components.text.TerminalLabel;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.*;
import io.netnotes.engine.io.input.events.*;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.engine.ui.Position;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.TextAlignment;
import io.netnotes.noteBytes.KeyRunTable;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesRunnablePair;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * MenuNavigator - Component-based menu display and keyboard navigation.
 *
 * Each visual section is a child TerminalRegion with its own renderSelf,
 * so sizing flows naturally through the component tree instead of being
 * calculated manually.
 *
 * Layout (managed by internal TerminalVStack):
 *   MenuHeaderView      – box + title, always 3 rows
 *   MenuBreadcrumbView  – breadcrumb trail, 2 rows (only when nested)
 *   TerminalLabel       – description text (only when present)
 *   TerminalLabel       – ↑ scroll indicator (only when scrolled)
 *   MenuItemView × N    – one per visible menu item, 1 row each
 *   TerminalLabel       – ↓ scroll indicator (only when more items below)
 *   MenuFooterView      – separator + help text, 2 rows
 */
public class MenuNavigator extends TerminalRegion {

    // ===== NAVIGATION STATE =====

    private final Stack<MenuContext> navigationStack = new Stack<>();
    private MenuContext currentMenu;
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    private int horizontalScrollOffset = 0;

    private static final int MAX_VISIBLE_ITEMS = 15;

    // ===== COMPONENT TREE =====

    /** Single child that owns all the visual sections. */
    private final TerminalVStack contentStack;

    /**
     * Direct references to the item views currently in the stack so that
     * a selection change can update only the two affected rows instead of
     * rebuilding the whole child list.
     */
    private final List<MenuItemView> currentItemViews = new ArrayList<>();

    // ===== KEYBOARD =====

    private EventFilter keyboardFilter = null;
    private NoteBytesReadOnly keyHandlerId = null;

    private final KeyRunTable keyRunTable = new KeyRunTable(new NoteBytesRunnablePair[]{
        new NoteBytesRunnablePair(KeyCodeBytes.UP,        this::handleNavigateUp),
        new NoteBytesRunnablePair(KeyCodeBytes.DOWN,      this::handleNavigateDown),
        new NoteBytesRunnablePair(KeyCodeBytes.LEFT,      this::handleScrollLeft),
        new NoteBytesRunnablePair(KeyCodeBytes.RIGHT,     this::handleScrollRight),
        new NoteBytesRunnablePair(KeyCodeBytes.ENTER,     this::handleSelectCurrent),
        new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE,    this::handleBack),
        new NoteBytesRunnablePair(KeyCodeBytes.PAGE_UP,   this::handlePageUp),
        new NoteBytesRunnablePair(KeyCodeBytes.PAGE_DOWN, this::handlePageDown),
        new NoteBytesRunnablePair(KeyCodeBytes.HOME,      this::handleHome),
        new NoteBytesRunnablePair(KeyCodeBytes.END,       this::handleEnd),
    });

    // ===== STATES =====

    public static final int IDLE             = 10;
    public static final int DISPLAYING_MENU  = 11;
    public static final int NAVIGATING       = 12;
    public static final int WAITING_PASSWORD = 13;
    public static final int EXECUTING_ACTION = 14;

    // ===== CONSTRUCTION =====

    public MenuNavigator(String name) {
        super(name);

        contentStack = new TerminalVStack(name + "-content");
        contentStack.setSpacing(0);
        contentStack.setWidthPreference(SizePreference.FILL);
        contentStack.setHeightPreference(SizePreference.FIT_CONTENT);
        addChild(contentStack);

        setWidthPreference(SizePreference.FIT_CONTENT);
        setHeightPreference(SizePreference.FIT_CONTENT);

        stateMachine.addState(IDLE);
    }

    @Override
    protected void setupStateTransitions() {
        stateMachine.onStateAdded(IDLE,             (o, n, b) -> removeKeyboardHandler());
        stateMachine.onStateAdded(DISPLAYING_MENU,  (o, n, b) -> registerKeyboardHandler());
        stateMachine.onStateAdded(WAITING_PASSWORD, (o, n, b) -> removeKeyboardHandler());
        stateMachine.onStateRemoved(WAITING_PASSWORD, (o, n, b) -> {
            if (stateMachine.hasState(DISPLAYING_MENU)) registerKeyboardHandler();
        });
    }

    // ===== FOCUS – invalidate header/footer so box style updates =====

    @Override
    public void onFocusGained() {
        super.onFocusGained();
        invalidateHeaderAndFooter();
    }

    @Override
    protected void onFocusLost() {
        super.onFocusLost();
        invalidateHeaderAndFooter();
    }

    private void invalidateHeaderAndFooter() {
        for (TerminalRenderable child : contentStack.getChildren()) {
            if (child instanceof MenuHeaderView || child instanceof MenuFooterView) {
                child.invalidate();
            }
        }
    }

    // ===== PUBLIC API =====

    public void showMenu(MenuContext menu) {
        if (menu == null) return;

        if (currentMenu != null && currentMenu != menu) {
            currentMenu.setOnChanged(null);
            navigationStack.push(currentMenu);
        }

        currentMenu = menu;
        currentMenu.setOnChanged(this::onMenuChanged);
        selectedIndex = 0;
        scrollOffset = 0;
        horizontalScrollOffset = 0;

        rebuildComponents();

        stateMachine.removeState(IDLE);
        stateMachine.removeState(NAVIGATING);
        stateMachine.removeState(EXECUTING_ACTION);
        stateMachine.addState(DISPLAYING_MENU);
    }

    public void refreshMenu() {
        if (stateMachine.hasState(DISPLAYING_MENU) && currentMenu != null) {
            rebuildComponents();
        }
    }

    public void resetScrollOffset() {
        horizontalScrollOffset = 0;
    }

    // ===== COMPONENT BUILDING =====

    /**
     * Clears and rebuilds the entire child component list.
     * Called on menu change, scroll change, and back navigation.
     * For simple selection changes use {@link #updateSelection} instead.
     */
    private void rebuildComponents() {
        // Remove all children from the content stack
        for (TerminalRenderable child : new ArrayList<>(contentStack.getChildren())) {
            contentStack.removeChild(child);
        }
        currentItemViews.clear();

        if (currentMenu == null) return;

        // -- Header --
        contentStack.addChild(new MenuHeaderView(getName() + "-header"));

        // -- Breadcrumb (only when there is navigation history) --
        if (!navigationStack.isEmpty()) {
            contentStack.addChild(new MenuBreadcrumbView(
                getName() + "-breadcrumb",
                buildBreadcrumbText()
            ));
        }

        // -- Description --
        String desc = currentMenu.getDescription();
        if (desc != null && !desc.isEmpty()) {
            TerminalLabel descLabel = new TerminalLabel(getName() + "-desc", desc);
            descLabel.setWordWrap(true);
            descLabel.setWidthPreference(SizePreference.FILL);
            descLabel.setHeightPreference(SizePreference.FIT_CONTENT);
            contentStack.addChild(descLabel);
        }

        // -- Scroll-up indicator --
        if (scrollOffset > 0) {
            TerminalLabel upLabel = new TerminalLabel(getName() + "-scroll-up", "↑ More above");
            upLabel.setTextAlignment(TextAlignment.CENTER);
            upLabel.setWidthPreference(SizePreference.FILL);
            contentStack.addChild(upLabel);
        }

        // -- Visible menu item rows --
        List<MenuContext.MenuItem> allItems = new ArrayList<>(currentMenu.getItems());
        int visibleEnd = Math.min(scrollOffset + MAX_VISIBLE_ITEMS, allItems.size());

        // The selected index tracks across ALL selectable items; we need the
        // selectable count for items that come before the visible window.
        int selectableIndex = countSelectableBefore(allItems, scrollOffset);

        for (int i = scrollOffset; i < visibleEnd; i++) {
            MenuContext.MenuItem item = allItems.get(i);
            boolean isSelectable = isSelectable(item);
            boolean isSelected   = isSelectable && (selectableIndex == selectedIndex);

            MenuItemView view = new MenuItemView(
                getName() + "-item-" + i, item, isSelected, horizontalScrollOffset);
            currentItemViews.add(view);
            contentStack.addChild(view);

            if (isSelectable) selectableIndex++;
        }

        // -- Scroll-down indicator --
        if (visibleEnd < allItems.size()) {
            TerminalLabel downLabel = new TerminalLabel(getName() + "-scroll-down", "↓ More below");
            downLabel.setTextAlignment(TextAlignment.CENTER);
            downLabel.setWidthPreference(SizePreference.FILL);
            contentStack.addChild(downLabel);
        }

        // -- Footer --
        contentStack.addChild(new MenuFooterView(
            getName() + "-footer",
            !navigationStack.isEmpty() || currentMenu.hasParent()
        ));
    }

    /**
     * Targeted update for a selection change within the current visible window.
     * Only the two affected {@link MenuItemView} instances are touched.
     */
    private void updateSelection(int oldSelectableIndex, int newSelectableIndex) {
        List<MenuContext.MenuItem> allItems = new ArrayList<>(currentMenu.getItems());
        int selectableIndex = countSelectableBefore(allItems, scrollOffset);

        for (MenuItemView view : currentItemViews) {
            if (!isSelectable(view.item)) continue;

            boolean wasSelected = (selectableIndex == oldSelectableIndex);
            boolean isNowSelected = (selectableIndex == newSelectableIndex);

            if (wasSelected || isNowSelected) {
                view.setSelected(isNowSelected, horizontalScrollOffset);
            }
            selectableIndex++;
        }
    }

    // ===== BREADCRUMB =====

    private String buildBreadcrumbText() {
        List<String> trail = new ArrayList<>();
        MenuContext c = currentMenu;
        while (c != null) {
            trail.add(0, c.getTitle() != null ? c.getTitle() : "");
            c = c.getParent();
        }
        return String.join(" > ", trail);
    }

    // ===== HELPERS =====

    private static boolean isSelectable(MenuContext.MenuItem item) {
        return item.type != MenuContext.MenuItemType.SEPARATOR
            && item.type != MenuContext.MenuItemType.INFO;
    }

    private static int countSelectableBefore(List<MenuContext.MenuItem> items, int upToIndex) {
        int count = 0;
        for (int i = 0; i < upToIndex && i < items.size(); i++) {
            if (isSelectable(items.get(i))) count++;
        }
        return count;
    }

    private List<MenuContext.MenuItem> getSelectableItems() {
        if (currentMenu == null) return List.of();
        return currentMenu.getItems().stream()
            .filter(MenuNavigator::isSelectable)
            .toList();
    }

    private void onMenuChanged(MenuContext menu) {
        if (menu == currentMenu) rebuildComponents();
    }

    // ===== KEYBOARD HANDLER REGISTRATION =====

    private void registerKeyboardHandler() {
        if (keyHandlerId != null) return;
        keyHandlerId = keyboardFilter != null
            ? addKeyDownHandler(this::handleKeyboardEvent, keyboardFilter)
            : addKeyDownHandler(this::handleKeyboardEvent);
    }

    private void removeKeyboardHandler() {
        if (keyHandlerId != null) {
            removeKeyDownHandler(keyHandlerId);
            keyHandlerId = null;
        }
    }

    private void handleKeyboardEvent(RoutedEvent event) {
        if (!stateMachine.hasState(DISPLAYING_MENU)) return;

        if (event instanceof EphemeralRoutedEvent ephemeral) {
            try (ephemeral) {
                if (ephemeral instanceof EphemeralKeyDownEvent ekd) {
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

    // ===== NAVIGATION HANDLERS =====

    private void handleNavigateUp() {
        List<MenuContext.MenuItem> selectable = getSelectableItems();
        if (selectable.isEmpty()) return;

        int oldIndex = selectedIndex;
        selectedIndex = (selectedIndex - 1 + selectable.size()) % selectable.size();

        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
            rebuildComponents();
        } else {
            updateSelection(oldIndex, selectedIndex);
        }
    }

    private void handleNavigateDown() {
        List<MenuContext.MenuItem> selectable = getSelectableItems();
        if (selectable.isEmpty()) return;

        int oldIndex = selectedIndex;
        selectedIndex = (selectedIndex + 1) % selectable.size();

        if (selectedIndex >= scrollOffset + MAX_VISIBLE_ITEMS) {
            scrollOffset = selectedIndex - MAX_VISIBLE_ITEMS + 1;
            rebuildComponents();
        } else {
            updateSelection(oldIndex, selectedIndex);
        }
    }

    private void handleSelectCurrent() {
        List<MenuContext.MenuItem> selectable = getSelectableItems();
        if (selectedIndex < 0 || selectedIndex >= selectable.size()) return;

        MenuContext.MenuItem selectedItem = selectable.get(selectedIndex);

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
        if (navigationStack.isEmpty()) return;

        currentMenu.setOnChanged(null);
        currentMenu = navigationStack.pop();
        currentMenu.setOnChanged(this::onMenuChanged);
        selectedIndex = 0;
        scrollOffset = 0;

        stateMachine.removeState(WAITING_PASSWORD);
        stateMachine.removeState(EXECUTING_ACTION);
        stateMachine.addState(DISPLAYING_MENU);

        rebuildComponents();
    }

    private void handlePageUp() {
        List<MenuContext.MenuItem> selectable = getSelectableItems();
        if (selectable.isEmpty()) return;
        selectedIndex = Math.max(0, selectedIndex - MAX_VISIBLE_ITEMS);
        scrollOffset  = Math.max(0, scrollOffset  - MAX_VISIBLE_ITEMS);
        rebuildComponents();
    }

    private void handlePageDown() {
        List<MenuContext.MenuItem> selectable = getSelectableItems();
        if (selectable.isEmpty()) return;
        selectedIndex = Math.min(selectable.size() - 1, selectedIndex + MAX_VISIBLE_ITEMS);
        int maxScroll = Math.max(0, selectable.size() - MAX_VISIBLE_ITEMS);
        scrollOffset  = Math.min(maxScroll, scrollOffset + MAX_VISIBLE_ITEMS);
        rebuildComponents();
    }

    private void handleHome() {
        selectedIndex = 0;
        scrollOffset  = 0;
        rebuildComponents();
    }

    private void handleEnd() {
        List<MenuContext.MenuItem> selectable = getSelectableItems();
        if (selectable.isEmpty()) return;
        selectedIndex = selectable.size() - 1;
        scrollOffset  = Math.max(0, selectable.size() - MAX_VISIBLE_ITEMS);
        rebuildComponents();
    }

    private void handleScrollRight() {
        List<MenuContext.MenuItem> selectable = getSelectableItems();
        if (selectable.isEmpty() || selectedIndex >= selectable.size()) return;
        // Clamp generously; the item view will clamp to text length on render
        horizontalScrollOffset = Math.min(horizontalScrollOffset + 5, 500);
        updateSelection(selectedIndex, selectedIndex);
    }

    private void handleScrollLeft() {
        if (horizontalScrollOffset <= 0) return;
        horizontalScrollOffset = Math.max(0, horizontalScrollOffset - 5);
        updateSelection(selectedIndex, selectedIndex);
    }

    // ===== PASSWORD CALLBACKS =====

    public void onPasswordSuccess(String menuItemName) {
        if (!stateMachine.hasState(WAITING_PASSWORD)) return;
        stateMachine.removeState(WAITING_PASSWORD);
        stateMachine.addState(NAVIGATING);

        currentMenu.navigate(menuItemName)
            .thenAccept(targetMenu -> {
                stateMachine.removeState(NAVIGATING);
                if (targetMenu != null) showMenu(targetMenu);
                else stateMachine.addState(DISPLAYING_MENU);
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

    // ===== CONFIGURATION =====

    public void setKeyboardFilter(EventFilter filter) {
        this.keyboardFilter = filter;
        if (stateMachine.hasState(DISPLAYING_MENU)) {
            removeKeyboardHandler();
            registerKeyboardHandler();
        }
    }

    public EventFilter getKeyboardFilter() { return keyboardFilter; }

    public void cleanup() { removeKeyboardHandler(); }

    // ===== GETTERS =====

    public MenuContext getCurrentMenu()        { return currentMenu; }
    public boolean hasMenu()                   { return currentMenu != null; }
    public boolean isDisplayingMenu()          { return stateMachine.hasState(DISPLAYING_MENU); }
    public boolean isWaitingForPassword()      { return stateMachine.hasState(WAITING_PASSWORD); }

    // =========================================================================
    // INNER COMPONENT CLASSES
    // =========================================================================

    /**
     * Renders the titled box at the top of the menu.
     * Always exactly 3 rows tall.
     * Non-static so it can read the outer MenuNavigator's focus state for box style.
     */
    private class MenuHeaderView extends TerminalRegion {

        MenuHeaderView(String name) {
            super(name);
            setWidthPreference(SizePreference.FILL);
            setHeightPreference(SizePreference.FIT_CONTENT);
            setMinHeight(3);
        }

        @Override
        public int getPreferredHeight() { return 3; }

        @Override
        protected void renderSelf(TerminalBatchBuilder batch) {
            int w = getWidth();
            if (w <= 0 || getHeight() < 3) return;

            // Use outer navigator's focus to pick box style
            BoxStyle  boxStyle   = MenuNavigator.this.hasFocus() ? BoxStyle.DOUBLE : BoxStyle.SINGLE;
            TextStyle titleStyle = MenuNavigator.this.hasFocus() ? TextStyle.BOLD  : TextStyle.NORMAL;

            String title = currentMenu != null && currentMenu.getTitle() != null
                ? currentMenu.getTitle()
                : "";

            drawBox(batch, 0, 0, w, 3, title, Position.CENTER, boxStyle, titleStyle);
        }
    }

    /**
     * Renders the breadcrumb trail centred on one row, with a blank row below.
     * Always 2 rows tall.
     */
    private static class MenuBreadcrumbView extends TerminalRegion {
        private final String breadcrumb;

        MenuBreadcrumbView(String name, String breadcrumb) {
            super(name);
            this.breadcrumb = breadcrumb != null ? breadcrumb : "";
            setWidthPreference(SizePreference.FILL);
            setHeightPreference(SizePreference.FIT_CONTENT);
            setMinHeight(2);
        }

        @Override
        public int getPreferredHeight() { return 2; }

        @Override
        protected void renderSelf(TerminalBatchBuilder batch) {
            int w = getWidth();
            if (w <= 0) return;

            String text = breadcrumb;
            if (text.length() > w - 4) {
                text = "..." + text.substring(text.length() - Math.max(0, w - 7));
            }
            int x = Math.max(0, (w - text.length()) / 2);
            printAt(batch, x, 0, text, TextStyle.INFO);
            // Row 1 is intentionally blank (spacing below breadcrumb)
        }
    }

    /**
     * Renders a single menu item on one row.
     * Mutable: call {@link #setSelected} to flip selection state without rebuilding.
     */
    static class MenuItemView extends TerminalRegion {

        final MenuContext.MenuItem item;
        private boolean selected;
        private int horizScroll;

        MenuItemView(String name, MenuContext.MenuItem item,
                     boolean selected, int horizScroll) {
            super(name);
            this.item       = item;
            this.selected   = selected;
            this.horizScroll = horizScroll;
            setWidthPreference(SizePreference.FILL);
            setHeightPreference(SizePreference.FIT_CONTENT);
            setMinHeight(1);
        }

        @Override
        public int getPreferredHeight() { return 1; }

        void setSelected(boolean selected, int horizScroll) {
            if (this.selected != selected || this.horizScroll != horizScroll) {
                this.selected   = selected;
                this.horizScroll = horizScroll;
                invalidate();
            }
        }

        @Override
        protected void renderSelf(TerminalBatchBuilder batch) {
            int w = getWidth();
            if (w <= 0) return;

            switch (item.type) {
                case SEPARATOR -> renderSeparator(batch, w);
                case INFO      -> renderInfo(batch, w);
                default        -> renderAction(batch, w);
            }
        }

        private void renderSeparator(TerminalBatchBuilder batch, int w) {
            int lineW = Math.max(0, w - 4);
            printAt(batch, 2, 0, "─".repeat(lineW), TextStyle.NORMAL);
            if (item.description != null && !item.description.isEmpty()) {
                int labelX = 2 + Math.max(0, (lineW - item.description.length()) / 2);
                printAt(batch, labelX, 0, " " + item.description + " ", TextStyle.BOLD);
            }
        }

        private void renderInfo(TerminalBatchBuilder batch, int w) {
            String text = item.description != null ? item.description : "";
            int x = Math.max(0, (w - text.length()) / 2);
            printAt(batch, x, 0, text, TextStyle.INFO);
        }

        private void renderAction(TerminalBatchBuilder batch, int w) {
            String badge    = item.badge != null ? " [" + item.badge + "]" : "";
            String fullText = (item.description != null ? item.description : "") + badge;
            int contentW    = Math.max(0, w - 4); // 2-char left pad + 2-char right pad

            if (selected) {
                String indicator = "> ";
                int    textW     = Math.max(0, contentW - indicator.length());

                String displayText;
                if (fullText.length() <= textW) {
                    displayText = fullText;
                } else {
                    int start = Math.min(horizScroll,
                        Math.max(0, fullText.length() - textW));
                    displayText = fullText.substring(start,
                        Math.min(fullText.length(), start + textW));
                }

                // Pad to full width so the inverse highlight covers the whole row
                displayText = String.format("%-" + textW + "s", displayText);
                printAt(batch, 2, 0, indicator + displayText, TextStyle.INVERSE);

            } else {
                String displayText = "  " + truncate(fullText, Math.max(0, contentW - 2));
                TextStyle style    = item.enabled ? TextStyle.NORMAL : TextStyle.INFO;
                printAt(batch, 2, 0, displayText, style);
            }
        }

        private static String truncate(String text, int max) {
            if (max <= 0)              return "";
            if (text.length() <= max)  return text;
            return text.substring(0, Math.max(0, max - 3)) + "...";
        }
    }

    /**
     * Renders the separator line and keyboard help text at the bottom.
     * Always exactly 2 rows tall.
     * Non-static so it can read the outer MenuNavigator's focus state for text style.
     */
    private class MenuFooterView extends TerminalRegion {
        private final boolean hasBack;

        MenuFooterView(String name, boolean hasBack) {
            super(name);
            this.hasBack = hasBack;
            setWidthPreference(SizePreference.FILL);
            setHeightPreference(SizePreference.FIT_CONTENT);
            setMinHeight(2);
        }

        @Override
        public int getPreferredHeight() { return 2; }

        @Override
        protected void renderSelf(TerminalBatchBuilder batch) {
            int w = getWidth();
            if (w <= 0) return;

            drawHLine(batch, 0, 0, w);

            String help = hasBack
                ? "↑↓: Navigate  ←→: Scroll  Enter: Select  ESC: Back  Home/End: Jump"
                : "↑↓: Navigate  ←→: Scroll  Enter: Select  Home/End: Jump";

            if (help.length() > w - 4) {
                help = help.substring(0, Math.max(0, w - 7)) + "...";
            }

            int helpX = Math.max(0, (w - help.length()) / 2);
            TextStyle style = MenuNavigator.this.hasFocus() ? TextStyle.INFO : TextStyle.NORMAL;
            printAt(batch, helpX, 1, help, style);
        }
    }
}