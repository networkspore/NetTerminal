package io.netnotes.terminal.menus;

import java.util.*;
import io.netnotes.terminal.*;
import io.netnotes.terminal.TextStyle.LineStyle;
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
 * Extends TerminalVStack directly so that addChild/removeChild are
 * layout-group-aware by default. There is no intermediate container —
 * this IS the stack, eliminating the stale-layout-group problem that
 * occurs when a plain TerminalRegion wraps a VStack and calls removeChild
 * without unregistering from the layout group.
 *
 * Width is FILL  — the parent (typically a BorderPanel CENTER) gives it
 *                  real horizontal space; FIT_CONTENT here would collapse
 *                  to minWidth because FILL children are excluded from the
 *                  FIT_CONTENT preferred-width sum in TerminalVStack.
 * Height is FIT_CONTENT — grows to accommodate the visible items.
 *
 * Visual layout (children rebuilt dynamically by rebuildComponents):
 *   MenuHeaderView      – border box + title,   always 3 rows
 *   MenuBreadcrumbView  – breadcrumb trail,      2 rows  (nested menus only)
 *   TerminalLabel       – description text              (when present)
 *   TerminalLabel       – ↑ scroll indicator            (when scrolled)
 *   MenuItemView × N    – one per visible item,  1 row each
 *   TerminalLabel       – ↓ scroll indicator            (more items below)
 *   MenuFooterView      – separator + help text, 2 rows
 */
public class MenuNavigator extends TerminalVStack {

    // ===== NAVIGATION STATE =====

    private final Stack<MenuContext> navigationStack = new Stack<>();
    private MenuContext currentMenu;
    private int selectedIndex         = 0;
    private int scrollOffset          = 0;
    private int horizontalScrollOffset = 0;

    private static final int MAX_VISIBLE_ITEMS = 15;
    private final KeyRunTable keyRunTable;
    private TextStyle textStyle = TextStyle.NORMAL;
    private TextStyle focusedStyle = TextStyle.INFO;
    private LineStyle lineStyle = LineStyle.SINGLE;
    /**
     * Typed references to the currently visible item views so that
     * a selection change can call setSelected() on only the two affected
     * rows, avoiding a full rebuild on every arrow key press.
     */
    private final List<MenuItemView> currentItemViews = new ArrayList<>();

    // ===== KEYBOARD =====

    private EventFilter       keyboardFilter = null;
    private NoteBytesReadOnly keyHandlerId   = null;


    // ===== STATES =====

    public static final int IDLE             = 10;
    public static final int DISPLAYING_MENU  = 11;
    public static final int NAVIGATING       = 12;
    public static final int WAITING_PASSWORD = 13;
    public static final int EXECUTING_ACTION = 14;

    // ===== CONSTRUCTION =====

    public MenuNavigator(String name) {
        super(name);
        setSpacing(0);
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FIT_CONTENT);
    
        keyRunTable = new KeyRunTable(new NoteBytesRunnablePair[]{
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

    // ===== FOCUS — update header/footer styling =====

    @Override
    public void onFocusGained() {
        super.onFocusGained();
        invalidateFocusSensitiveChildren();
    }

    @Override
    protected void onFocusLost() {
        super.onFocusLost();
        invalidateFocusSensitiveChildren();
    }

    private void invalidateFocusSensitiveChildren() {
        for (TerminalRenderable child : getChildren()) {
            if (child instanceof MenuHeaderView
                || child instanceof MenuFooterView
                || child instanceof MenuBreadcrumbView
                || child instanceof MenuItemView) {
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
        selectedIndex          = 0;
        scrollOffset           = 0;
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
     * Clears all children and rebuilds the component tree for the current
     * menu + scroll state. Because this object IS the VStack, removeChild
     * and addChild are layout-group-aware — no stale group entries.
     */
    private void rebuildComponents() {
        for (TerminalRenderable child : new ArrayList<>(getChildren())) {
            removeChild(child);
        }
        currentItemViews.clear();

        if (currentMenu == null) return;

        // --- Header (always present) ---
        addChild(new MenuHeaderView(getName() + "-header"));

        // --- Breadcrumb (only when inside a sub-menu) ---
        if (!navigationStack.isEmpty()) {
            addChild(new MenuBreadcrumbView(
                getName() + "-breadcrumb", buildBreadcrumbText(), this));
        }

        // --- Description (optional) ---
        String desc = currentMenu.getDescription();
        if (desc != null && !desc.isEmpty()) {
            TerminalLabel descLabel = new TerminalLabel(getName() + "-desc", desc, textStyle);
            descLabel.setWordWrap(true);
            descLabel.setWidthPreference(SizePreference.FILL);
            descLabel.setHeightPreference(SizePreference.FIT_CONTENT);
            addChild(descLabel);
        }

        // --- Scroll-up indicator ---
        if (scrollOffset > 0) {
            TerminalLabel upLabel = new TerminalLabel(
                getName() + "-scroll-up", "↑ More above", textStyle);
            upLabel.setTextAlignment(TextAlignment.CENTER);
            upLabel.setWidthPreference(SizePreference.FILL);
            addChild(upLabel);
        }

        // --- Visible item rows ---
        List<MenuContext.MenuItem> allItems = new ArrayList<>(currentMenu.getItems());
        int visibleEnd      = Math.min(scrollOffset + MAX_VISIBLE_ITEMS, allItems.size());
        int selectableIndex = countSelectableBefore(allItems, scrollOffset);

        for (int i = scrollOffset; i < visibleEnd; i++) {
            MenuContext.MenuItem item = allItems.get(i);
            boolean isSelectable = isSelectable(item);
            boolean isSelected   = isSelectable && (selectableIndex == selectedIndex);

            MenuItemView view = new MenuItemView(
                getName() + "-item-" + i, item, isSelected, horizontalScrollOffset, this);
            currentItemViews.add(view);
            addChild(view);

            if (isSelectable) selectableIndex++;
        }

        // --- Scroll-down indicator ---
        if (visibleEnd < allItems.size()) {
            TerminalLabel downLabel = new TerminalLabel(
                getName() + "-scroll-down", "↓ More below", textStyle);
            downLabel.setTextAlignment(TextAlignment.CENTER);
            downLabel.setWidthPreference(SizePreference.FILL);
            addChild(downLabel);
        }

        // --- Footer (always present) ---
        addChild(new MenuFooterView(
            getName() + "-footer",
            !navigationStack.isEmpty() || currentMenu.hasParent()));
    }

    /**
     * Targeted update for a selection change that doesn't cross a scroll
     * boundary. Only the two affected MenuItemView instances are touched.
     */
    private void updateSelection(int oldSelectableIndex, int newSelectableIndex) {
        if (currentMenu == null) return;
        List<MenuContext.MenuItem> allItems = new ArrayList<>(currentMenu.getItems());
        int selectableIndex = countSelectableBefore(allItems, scrollOffset);

        for (MenuItemView view : currentItemViews) {
            if (!isSelectable(view.item)) continue;

            boolean wasSelected   = (selectableIndex == oldSelectableIndex);
            boolean isNowSelected = (selectableIndex == newSelectableIndex);

            if (wasSelected || isNowSelected) {
                view.setSelected(isNowSelected, horizontalScrollOffset);
            }
            selectableIndex++;
        }
    }

    // ===== HELPERS =====

    private String buildBreadcrumbText() {
        List<String> trail = new ArrayList<>();
        MenuContext c = currentMenu;
        while (c != null) {
            trail.add(0, c.getTitle() != null ? c.getTitle() : "");
            c = c.getParent();
        }
        return String.join(" > ", trail);
    }

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

    // ===== KEYBOARD =====

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
        scrollOffset  = 0;
        stateMachine.removeState(WAITING_PASSWORD);
        stateMachine.removeState(EXECUTING_ACTION);
        stateMachine.addState(DISPLAYING_MENU);
        rebuildComponents();
    }

    private void handlePageUp() {
        if (getSelectableItems().isEmpty()) return;
        selectedIndex = Math.max(0, selectedIndex - MAX_VISIBLE_ITEMS);
        scrollOffset  = Math.max(0, scrollOffset  - MAX_VISIBLE_ITEMS);
        rebuildComponents();
    }

    private void handlePageDown() {
        List<MenuContext.MenuItem> selectable = getSelectableItems();
        if (selectable.isEmpty()) return;
        selectedIndex = Math.min(selectable.size() - 1, selectedIndex + MAX_VISIBLE_ITEMS);
        scrollOffset  = Math.min(
            Math.max(0, selectable.size() - MAX_VISIBLE_ITEMS),
            scrollOffset + MAX_VISIBLE_ITEMS);
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
    public void cleanup()                  { removeKeyboardHandler(); }

    // ===== GETTERS =====

    public MenuContext getCurrentMenu()   { return currentMenu; }
    public boolean hasMenu()              { return currentMenu != null; }
    public boolean isDisplayingMenu()     { return stateMachine.hasState(DISPLAYING_MENU); }
    public boolean isWaitingForPassword() { return stateMachine.hasState(WAITING_PASSWORD); }
    public TextStyle getTextStyle() { return textStyle; }
    public LineStyle getLineStyle() { return lineStyle; }

    

    public TextStyle getFocusedStyle() {
        return focusedStyle;
    }
    
    public void setFocusedStyle(TextStyle focusedStyle) {
        TextStyle next = focusedStyle != null ? focusedStyle : TextStyle.INFO;
        if (this.focusedStyle != next) {
            this.focusedStyle = next;
            onStyleChanged();
        }
    }


    public void setTextStyle(TextStyle textStyle) { 
        TextStyle next = textStyle != null ? textStyle : TextStyle.NORMAL;
        if (this.textStyle != next) {
            this.textStyle = next; 
            onStyleChanged();
        }
    }

    public void setLineStyle(LineStyle lineStyle) { 
        LineStyle next = lineStyle != null ? lineStyle : LineStyle.SINGLE;
        if (this.lineStyle != next) {
            this.lineStyle = next; 
            onStyleChanged();
        }
    }

    private void onStyleChanged() {
        for (TerminalRenderable child : getChildren()) {
            if (child instanceof TerminalLabel label) {
                label.setTextStyle(textStyle);
            } else {
                child.invalidate();
            }
        }
    }
    // =========================================================================
    // INNER COMPONENT CLASSES
    // =========================================================================

    /**
     * Titled border box. Always 3 rows.
     * Non-static: reads hasFocus() from the outer MenuNavigator for box style.
     */
    private class MenuHeaderView extends TerminalRegion {

        MenuHeaderView(String name) {
            super(name);
            setWidthPreference(SizePreference.FILL);
            setHeightPreference(SizePreference.FIT_CONTENT);
            setMinHeight(3);
        }

        @Override public int getPreferredHeight() { return 3; }

        @Override
        protected void renderSelf(TerminalBatchBuilder batch) {
            int w = getWidth();
            if (w <= 0 || getHeight() < 3) return;

            LineStyle boxStyle = MenuNavigator.this.getLineStyle();
            TextStyle titleStyle = MenuNavigator.this.hasFocus()
                ? MenuNavigator.this.getFocusedStyle().copy().bold()
                : MenuNavigator.this.getTextStyle();

            String title = (currentMenu != null && currentMenu.getTitle() != null)
                ? currentMenu.getTitle() : "";

            drawBox(batch, 0, 0, w, 3, title, Position.CENTER, boxStyle, titleStyle);
        }
    }

    /**
     * Centred breadcrumb trail (row 0) plus one blank spacing row (row 1).
     * Always 2 rows. Static: no outer state needed.
     */
    private static class MenuBreadcrumbView extends TerminalRegion {
        private final String breadcrumb;
        private final MenuNavigator nav;

        MenuBreadcrumbView(String name, String breadcrumb, MenuNavigator navigator) {
            super(name);
            this.breadcrumb = breadcrumb != null ? breadcrumb : "";
            this.nav = navigator;
            setWidthPreference(SizePreference.FILL);
            setHeightPreference(SizePreference.FIT_CONTENT);
            setMinHeight(2);
        }

        @Override public int getPreferredHeight() { return 2; }

        @Override
        protected void renderSelf(TerminalBatchBuilder batch) {
            int w = getWidth();
            if (w <= 0) return;
            String text = breadcrumb;
            if (text.length() > w - 4) {
                text = "..." + text.substring(text.length() - Math.max(0, w - 7));
            }
            TextStyle style = nav.hasFocus() ? nav.getFocusedStyle() : nav.getTextStyle();
            printAt(batch, Math.max(0, (w - text.length()) / 2), 0, text, style);
            // row 1 intentionally blank
        }
    }

    /**
     * One menu item row. Mutable via setSelected() for targeted redraws.
     * Package-private so the outer class can hold a typed list.
     */
    static class MenuItemView extends TerminalRegion {

        final MenuContext.MenuItem item;
        private boolean selected;
        private int     horizScroll;
        private final MenuNavigator nav;

        MenuItemView(
            String name,
            MenuContext.MenuItem item,
            boolean selected,
            int horizScroll,
            MenuNavigator nav
        ) {
            super(name);
            this.item        = item;
            this.selected    = selected;
            this.horizScroll = horizScroll;
            this.nav         = nav;
            setWidthPreference(SizePreference.FILL);
            setHeightPreference(SizePreference.FIT_CONTENT);
            setMinHeight(1);
        }

        @Override public int getPreferredHeight() { return 1; }

        void setSelected(boolean selected, int horizScroll) {
            if (this.selected != selected || this.horizScroll != horizScroll) {
                this.selected    = selected;
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
            drawHLine(batch, 2, 0, lineW, nav.getLineStyle(), nav.getTextStyle());
            if (item.description != null && !item.description.isEmpty()) {
                int labelX = 2 + Math.max(0, (lineW - item.description.length()) / 2);
                printAt(batch, labelX, 0, " " + item.description + " ", nav.getTextStyle().copy().bold());
            }
        }

        private void renderInfo(TerminalBatchBuilder batch, int w) {
            String text = item.description != null ? item.description : "";
            printAt(batch, Math.max(0, (w - text.length()) / 2), 0, text, nav.getFocusedStyle());
        }

        private void renderAction(TerminalBatchBuilder batch, int w) {
            String badge    = item.badge != null ? " [" + item.badge + "]" : "";
            String fullText = (item.description != null ? item.description : "") + badge;
            int    contentW = Math.max(0, w - 4);

            if (selected) {
                String indicator = "> ";
                int    textW     = Math.max(0, contentW - indicator.length());
                String displayText;
                if (fullText.length() <= textW) {
                    displayText = fullText;
                } else {
                    int start = Math.min(horizScroll, Math.max(0, fullText.length() - textW));
                    displayText = fullText.substring(start,
                        Math.min(fullText.length(), start + textW));
                }
                displayText = String.format("%-" + textW + "s", displayText);
                TextStyle base = nav.hasFocus() ? nav.getFocusedStyle() : nav.getTextStyle();
                printAt(batch, 2, 0, indicator + displayText, base.copy().inverse());
            } else {
                String displayText = "  " + truncate(fullText, Math.max(0, contentW - 2));
                printAt(batch, 2, 0, displayText,
                    item.enabled ? nav.getTextStyle() : nav.getFocusedStyle());
            }
        }

        private static String truncate(String text, int max) {
            if (max <= 0)             return "";
            if (text.length() <= max) return text;
            return text.substring(0, Math.max(0, max - 3)) + "...";
        }
    }

    /**
     * Horizontal separator (row 0) + keyboard help text (row 1). Always 2 rows.
     * Non-static: reads hasFocus() from the outer MenuNavigator for help text style.
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

        @Override public int getPreferredHeight() { return 2; }

        @Override
        protected void renderSelf(TerminalBatchBuilder batch) {
            int w = getWidth();
            if (w <= 0) return;
            TextStyle borderStyle = MenuNavigator.this.hasFocus()
                ? MenuNavigator.this.getFocusedStyle()
                : MenuNavigator.this.getTextStyle();
            drawHLine(batch, 0, 0, w, lineStyle, borderStyle);
            String help = hasBack
                ? "↑↓: Navigate  ←→: Scroll  Enter: Select  ESC: Back  Home/End: Jump"
                : "↑↓: Navigate  ←→: Scroll  Enter: Select  Home/End: Jump";
            if (help.length() > w - 4) {
                help = help.substring(0, Math.max(0, w - 7)) + "...";
            }
            TextStyle style = MenuNavigator.this.hasFocus()
                ? MenuNavigator.this.getFocusedStyle()
                : MenuNavigator.this.getTextStyle();
            printAt(batch, Math.max(0, (w - help.length()) / 2), 1, help, style);
        }
    }
}
