package io.netnotes.terminal.menus;

import java.util.*;

import io.netnotes.terminal.*;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.components.panels.TerminalVStack;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.components.text.TerminalLabel;
import io.netnotes.terminal.menus.MenuChangeEvent.MenuChangeType;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.*;
import io.netnotes.engine.io.input.events.*;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.engine.ui.LabelTruncation;
import io.netnotes.engine.ui.Position;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.TextAlignment;
import io.netnotes.noteBytes.KeyRunTable;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesRunnablePair;

/**
 * MenuNavigator - Component-based menu display and keyboard navigation.
 */
public class MenuNavigator extends TerminalVStack {
    private static final int MIN_USABLE_WIDTH = 
        "↑↓: Navigate  Enter: Select  Home/End: Jump".length();
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
    private boolean descriptionWordWrap = true;
    private int descriptionMaxLines = 0;
    private SizePreference descriptionWidthPreference = SizePreference.FILL;
    private SizePreference descriptionHeightPreference = SizePreference.FIT_CONTENT;
    private TerminalLabel.WrappedHeightStrategy descriptionWrappedHeightStrategy =
        TerminalLabel.WrappedHeightStrategy.CURRENT_WIDTH_OR_HINT;
    private int descriptionWrapWidthHint = MIN_USABLE_WIDTH;
    private TextStyle descriptionTextStyle = null;
    private TextAlignment descriptionTextAlignment = TextAlignment.LEFT;
    private LabelTruncation descriptionTruncation = LabelTruncation.END;
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
    public static final int EXECUTING_ACTION = 14;

    // ===== CONSTRUCTION =====

    public MenuNavigator(String name) {
        super(name);
        setSpacing(0);
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FIT_CONTENT);
        setMinWidth(MIN_USABLE_WIDTH);
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
        if (!uiExecutor.isCurrentThread()) {
            uiExecutor.runLater(() -> showMenu(menu));
            return;
        }

        if (currentMenu != null && currentMenu != menu) {
            currentMenu.setOnChanged(null);
            if (shouldPushNavigationStack(menu)) {
                navigationStack.push(currentMenu);
            }
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
        if (!uiExecutor.isCurrentThread()) {
            uiExecutor.runLater(this::refreshMenu);
            return;
        }
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
        if(layoutManager != null) layoutManager.beginBatch();
        try{
            clearChildren();
            currentItemViews.clear();

            if (currentMenu == null) return;

            List<MenuItemView> builtItemViews = new ArrayList<>();

  
            addChild(new MenuHeaderView(getName() + "-header"));

   
            if (currentMenu.hasParent()) {
                addChild(new MenuBreadcrumbView(
                    getName() + "-breadcrumb", buildBreadcrumbText(), this));
            }

            String desc = currentMenu.getDescription();
            if (desc != null && !desc.isEmpty()) {
                TerminalLabel descLabel = new TerminalLabel(getName() + "-desc", desc, textStyle);
                descLabel.setTextStyle(getResolvedDescriptionTextStyle());
                descLabel.setTextAlignment(descriptionTextAlignment);
                descLabel.setTextTruncation(descriptionTruncation);
                descLabel.setWordWrap(descriptionWordWrap);
                descLabel.setMaxLines(descriptionMaxLines);
                descLabel.setWrappedHeightStrategy(descriptionWrappedHeightStrategy);
                descLabel.setWrapWidthHint(descriptionWrapWidthHint);
                descLabel.setWidthPreference(descriptionWidthPreference);
                descLabel.setHeightPreference(descriptionHeightPreference);
                addChild(descLabel);
            }

  
            if (scrollOffset > 0) {
                TerminalLabel upLabel = new TerminalLabel(
                    getName() + "-scroll-up", "↑ More above", textStyle);
                upLabel.setTextAlignment(TextAlignment.CENTER);
                upLabel.setWidthPreference(SizePreference.FILL);
                addChild(upLabel);
            }

    
            List<MenuItem> allItems = new ArrayList<>(currentMenu.getItems());
            int visibleEnd      = Math.min(scrollOffset + MAX_VISIBLE_ITEMS, allItems.size());
            int selectableIndex = countSelectableBefore(allItems, scrollOffset);

            for (int i = scrollOffset; i < visibleEnd; i++) {
                MenuItem item = allItems.get(i);
                boolean isSelectable = isSelectable(item);
                boolean isSelected   = isSelectable && (selectableIndex == selectedIndex);

                MenuItemView view = new MenuItemView(
                    getName() + "-item-" + i, item, isSelected, horizontalScrollOffset, this);
                builtItemViews.add(view);
                addChild(view);

                if (isSelectable) selectableIndex++;
            }

     
            if (visibleEnd < allItems.size()) {
                TerminalLabel downLabel = new TerminalLabel(
                    getName() + "-scroll-down", "↓ More below", textStyle);
                downLabel.setTextAlignment(TextAlignment.CENTER);
                downLabel.setWidthPreference(SizePreference.FILL);
                addChild(downLabel);
            }

            addChild(new MenuFooterView(
                getName() + "-footer",
                !navigationStack.isEmpty() || currentMenu.hasParent()));

            currentItemViews.addAll(builtItemViews);
        }finally{
            if(layoutManager != null) layoutManager.endBatch();
        }
    }

    private void updateSelection(int oldSelectableIndex, int newSelectableIndex) {
        if (currentMenu == null) return;
        List<MenuItem> allItems = new ArrayList<>(currentMenu.getItems());
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

    private static boolean isSelectable(MenuItem item) {
        return item.type != MenuItem.MenuItemType.SEPARATOR
            && item.type != MenuItem.MenuItemType.INFO;
    }

    private static int countSelectableBefore(List<MenuItem> items, int upToIndex) {
        int count = 0;
        for (int i = 0; i < upToIndex && i < items.size(); i++) {
            if (isSelectable(items.get(i))) count++;
        }
        return count;
    }

    /**
     * Returns the raw item index of the n-th selectable item, or -1 when
     * {@code selectableN} is out of range.
     */
    private static int rawIndexOfSelectable(List<MenuItem> allItems, int selectableN) {
        if (selectableN < 0) return -1;

        int count = 0;
        for (int i = 0; i < allItems.size(); i++) {
            if (!isSelectable(allItems.get(i))) continue;
            if (count == selectableN) return i;
            count++;
        }
        return -1;
    }

    private static int clampScrollOffset(List<MenuItem> allItems, int candidate) {
        int maxOffset = Math.max(0, allItems.size() - MAX_VISIBLE_ITEMS);
        return Math.max(0, Math.min(candidate, maxOffset));
    }

    /**
     * Keeps the raw-item viewport aligned with the currently selected
     * selectable-item index.
     *
     * @return true when the raw scroll offset changed and a rebuild is needed
     */
    private boolean syncViewportToSelection(List<MenuItem> allItems) {
        int rawSelected = rawIndexOfSelectable(allItems, selectedIndex);
        if (rawSelected < 0) return false;

        int nextScrollOffset = scrollOffset;
        if (rawSelected < scrollOffset) {
            nextScrollOffset = rawSelected;
        } else if (rawSelected >= scrollOffset + MAX_VISIBLE_ITEMS) {
            nextScrollOffset = rawSelected - MAX_VISIBLE_ITEMS + 1;
        }

        nextScrollOffset = clampScrollOffset(allItems, nextScrollOffset);
        if (nextScrollOffset == scrollOffset) {
            return false;
        }

        scrollOffset = nextScrollOffset;
        return true;
    }

    private List<MenuItem> getSelectableItems() {
        if (currentMenu == null) return List.of();
        return currentMenu.getItems().stream()
            .filter(MenuNavigator::isSelectable)
            .toList();
    }

    private void onMenuChanged(MenuChangeEvent event) {
        Runnable action;
        if (event.getType() == MenuChangeType.STRUCTURAL || event.getItemName() == null) {
            action = () -> { if (event.getSource() == currentMenu) rebuildComponents(); };
        } else {
            action = () -> { if (event.getSource() == currentMenu) refreshItemView(event.getItemName()); };
        }

        if (!uiExecutor.isCurrentThread()) {
            uiExecutor.runLater(action);
        } else {
            action.run();
        }
    }

    private void refreshItemView(String itemName) {
        MenuItem item = currentMenu.getItem(itemName);
        if (item == null) return;
        for (MenuItemView view : currentItemViews) {
            if (view.item.name.equals(itemName)) {
                view.refreshFrom(item); // updates local state + calls invalidate()
                return;
            }
        }
    }

    private boolean shouldPushNavigationStack(MenuContext targetMenu) {
        return currentMenu != null
            && !currentMenu.hasParent()
            && targetMenu != null
            && !targetMenu.hasParent();
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
        List<MenuItem> selectable = getSelectableItems();
        if (selectable.isEmpty()) return;
        int oldIndex = selectedIndex;
        selectedIndex = (selectedIndex - 1 + selectable.size()) % selectable.size();

        List<MenuItem> allItems = new ArrayList<>(currentMenu.getItems());
        if (syncViewportToSelection(allItems)) {
            rebuildComponents();
        } else {
            updateSelection(oldIndex, selectedIndex);
        }
    }

    private void handleNavigateDown() {
        List<MenuItem> selectable = getSelectableItems();
        if (selectable.isEmpty()) return;
        int oldIndex = selectedIndex;
        selectedIndex = (selectedIndex + 1) % selectable.size();

        List<MenuItem> allItems = new ArrayList<>(currentMenu.getItems());
        if (syncViewportToSelection(allItems)) {
            rebuildComponents();
        } else {
            updateSelection(oldIndex, selectedIndex);
        }
    }

    private void handleSelectCurrent() {
        List<MenuItem> selectable = getSelectableItems();
        if (selectedIndex < 0 || selectedIndex >= selectable.size()) return;
        MenuItem item = selectable.get(selectedIndex);

        MenuContext next = currentMenu.navigate(item.name);
        if (next == currentMenu) {
            // action executed, stay
        } else if (next != null) {
            showMenu(next);
        }
    }

    private void handleBack() {
        if (currentMenu == null) return;

        MenuContext parentMenu = currentMenu.getParent();
        if (parentMenu != null) {
            currentMenu.setOnChanged(null);
            currentMenu = parentMenu;
            currentMenu.setOnChanged(this::onMenuChanged);
            selectedIndex = 0;
            scrollOffset  = 0;
            horizontalScrollOffset = 0;
            stateMachine.removeState(EXECUTING_ACTION);
            stateMachine.addState(DISPLAYING_MENU);
            rebuildComponents();
            return;
        }

        if (navigationStack.isEmpty()) return;
        currentMenu.setOnChanged(null);
        currentMenu = navigationStack.pop();
        currentMenu.setOnChanged(this::onMenuChanged);
        selectedIndex = 0;
        scrollOffset  = 0;
        horizontalScrollOffset = 0;
        stateMachine.removeState(EXECUTING_ACTION);
        stateMachine.addState(DISPLAYING_MENU);
        rebuildComponents();
    }

    private void handlePageUp() {
        List<MenuItem> selectable = getSelectableItems();
        if (selectable.isEmpty()) return;
        int oldIndex = selectedIndex;
        selectedIndex = Math.max(0, selectedIndex - MAX_VISIBLE_ITEMS);

        List<MenuItem> allItems = new ArrayList<>(currentMenu.getItems());
        if (syncViewportToSelection(allItems)) {
            rebuildComponents();
        } else {
            updateSelection(oldIndex, selectedIndex);
        }
    }

    private void handlePageDown() {
        List<MenuItem> selectable = getSelectableItems();
        if (selectable.isEmpty()) return;
        int oldIndex = selectedIndex;
        selectedIndex = Math.min(selectable.size() - 1, selectedIndex + MAX_VISIBLE_ITEMS);

        List<MenuItem> allItems = new ArrayList<>(currentMenu.getItems());
        if (syncViewportToSelection(allItems)) {
            rebuildComponents();
        } else {
            updateSelection(oldIndex, selectedIndex);
        }
    }

    private void handleHome() {
        selectedIndex = 0;
        scrollOffset  = 0;
        rebuildComponents();
    }

    private void handleEnd() {
        List<MenuItem> selectable = getSelectableItems();
        if (selectable.isEmpty()) return;
        selectedIndex = selectable.size() - 1;

        List<MenuItem> allItems = new ArrayList<>(currentMenu.getItems());
        int rawSelected = rawIndexOfSelectable(allItems, selectedIndex);
        scrollOffset = rawSelected >= 0
            ? clampScrollOffset(allItems, rawSelected - MAX_VISIBLE_ITEMS + 1)
            : 0;
        rebuildComponents();
    }

    private void handleScrollRight() {
        List<MenuItem> selectable = getSelectableItems();
        if (selectable.isEmpty() || selectedIndex >= selectable.size()) return;
        horizontalScrollOffset = Math.min(horizontalScrollOffset + 5, 500);
        updateSelection(selectedIndex, selectedIndex);
    }

    private void handleScrollLeft() {
        if (horizontalScrollOffset <= 0) return;
        horizontalScrollOffset = Math.max(0, horizontalScrollOffset - 5);
        updateSelection(selectedIndex, selectedIndex);
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
    public TextStyle getTextStyle() { return textStyle; }
    public LineStyle getLineStyle() { return lineStyle; }

    

    public TextStyle getFocusedStyle() {
        return focusedStyle;
    }

    public boolean isDescriptionWordWrap() { return descriptionWordWrap; }
    public int getDescriptionMaxLines() { return descriptionMaxLines; }
    public SizePreference getDescriptionWidthPreference() { return descriptionWidthPreference; }
    public SizePreference getDescriptionHeightPreference() { return descriptionHeightPreference; }
    public TerminalLabel.WrappedHeightStrategy getDescriptionWrappedHeightStrategy() {
        return descriptionWrappedHeightStrategy;
    }
    public int getDescriptionWrapWidthHint() { return descriptionWrapWidthHint; }
    public TextStyle getDescriptionTextStyle() { return getResolvedDescriptionTextStyle(); }
    public TextAlignment getDescriptionTextAlignment() { return descriptionTextAlignment; }
    public LabelTruncation getDescriptionTruncation() { return descriptionTruncation; }
    
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

    public void setDescriptionWordWrap(boolean descriptionWordWrap) {
        if (this.descriptionWordWrap != descriptionWordWrap) {
            this.descriptionWordWrap = descriptionWordWrap;
            refreshMenu();
        }
    }

    public void setDescriptionMaxLines(int descriptionMaxLines) {
        int clamped = Math.max(0, descriptionMaxLines);
        if (this.descriptionMaxLines != clamped) {
            this.descriptionMaxLines = clamped;
            refreshMenu();
        }
    }

    public void setDescriptionWidthPreference(SizePreference descriptionWidthPreference) {
        SizePreference next = descriptionWidthPreference != null
            ? descriptionWidthPreference
            : SizePreference.FILL;
        if (this.descriptionWidthPreference != next) {
            this.descriptionWidthPreference = next;
            refreshMenu();
        }
    }

    public void setDescriptionHeightPreference(SizePreference descriptionHeightPreference) {
        SizePreference next = descriptionHeightPreference != null
            ? descriptionHeightPreference
            : SizePreference.FIT_CONTENT;
        if (this.descriptionHeightPreference != next) {
            this.descriptionHeightPreference = next;
            refreshMenu();
        }
    }

    public void setDescriptionWrappedHeightStrategy(
        TerminalLabel.WrappedHeightStrategy descriptionWrappedHeightStrategy
    ) {
        TerminalLabel.WrappedHeightStrategy next = descriptionWrappedHeightStrategy != null
            ? descriptionWrappedHeightStrategy
            : TerminalLabel.WrappedHeightStrategy.CURRENT_WIDTH_OR_HINT;
        if (this.descriptionWrappedHeightStrategy != next) {
            this.descriptionWrappedHeightStrategy = next;
            refreshMenu();
        }
    }

    public void setDescriptionWrapWidthHint(int descriptionWrapWidthHint) {
        int clamped = Math.max(0, descriptionWrapWidthHint);
        if (this.descriptionWrapWidthHint != clamped) {
            this.descriptionWrapWidthHint = clamped;
            refreshMenu();
        }
    }

    public void setDescriptionTextStyle(TextStyle descriptionTextStyle) {
        if (this.descriptionTextStyle != descriptionTextStyle) {
            this.descriptionTextStyle = descriptionTextStyle;
            onStyleChanged();
        }
    }

    public void setDescriptionTextAlignment(TextAlignment descriptionTextAlignment) {
        TextAlignment next = descriptionTextAlignment != null
            ? descriptionTextAlignment
            : TextAlignment.LEFT;
        if (this.descriptionTextAlignment != next) {
            this.descriptionTextAlignment = next;
            refreshMenu();
        }
    }

    public void setDescriptionTruncation(LabelTruncation descriptionTruncation) {
        LabelTruncation next = descriptionTruncation != null
            ? descriptionTruncation
            : LabelTruncation.END;
        if (this.descriptionTruncation != next) {
            this.descriptionTruncation = next;
            refreshMenu();
        }
    }

    private void onStyleChanged() {
        for (TerminalRenderable child : getChildren()) {
            if (child instanceof TerminalLabel label) {
                label.setTextStyle(isDescriptionLabel(label)
                    ? getResolvedDescriptionTextStyle()
                    : textStyle);
            } else {
                child.invalidate();
            }
        }
    }

    private boolean isDescriptionLabel(TerminalRenderable renderable) {
        return renderable != null && (getName() + "-desc").equals(renderable.getName());
    }

    private TextStyle getResolvedDescriptionTextStyle() {
        return descriptionTextStyle != null ? descriptionTextStyle : textStyle;
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
        }

        @Override
        public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
            // Header is always exactly 3 rows: top border, title, bottom border.
            TerminalRectangle r = getRegionPool().obtain();
            r.set(0, 0, getMinWidth(), 3);
            return r;
        }

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
        }

        @Override
        public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
            // Breadcrumb trail (row 0) + blank spacing row (row 1) = always 2 rows.
            TerminalRectangle r = getRegionPool().obtain();
            r.set(0, 0, getMinWidth(), 2);
            return r;
        }

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

        final MenuItem item;
        private boolean selected;
        private int     horizScroll;
        private final MenuNavigator nav;

        MenuItemView(
            String name,
            MenuItem item,
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
        }

        @Override
        public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
            // Each item occupies exactly 1 row.
            TerminalRectangle r = getRegionPool().obtain();
            r.set(0, 0, getMinWidth(), 1);
            return r;
        }

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

        void refreshFrom(MenuItem updated) {
            // No layout change — badge and enabled only affect rendering, not size
            invalidate();
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
        }

        @Override
        public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
            // Separator line (row 0) + keyboard help text (row 1) = always 2 rows.
            TerminalRectangle r = getRegionPool().obtain();
            r.set(0, 0, getMinWidth(), 2);
            return r;
        }

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
