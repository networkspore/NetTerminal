package io.netnotes.terminal.components.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.components.panels.TerminalVStack;
import io.netnotes.terminal.components.panels.TerminalAbstractStack.HAlignment;
import io.netnotes.terminal.components.panels.TerminalDivider;
import io.netnotes.terminal.components.text.TerminalLabel;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutGroupCallback;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.renderer.layout.LayoutGroup.LayoutDataInterface;

/**
 * TerminalDialog - Single-screen modal overlay with title, body, and action buttons.
 *
 * <p>A dialog is a <em>one-shot, single-screen</em> UI element. It has no internal
 * navigation — use {@link TerminalSetupFlow} when you need multiple steps.
 *
 * <p>Structure:
 * <pre>
 * ╔═══════════════════════════════╗
 * ║  Title                      ✕ ║
 * ╠═══════════════════════════════╣
 * ║                               ║
 * ║   (body component)            ║
 * ║                               ║
 * ╠═══════════════════════════════╣
 * ║        [ Cancel ]  [  OK  ]   ║
 * ╚═══════════════════════════════╝
 * </pre>
 *
 * BUTTON MODEL:
 * <p>Buttons are added with {@link #addButton(String, ButtonStyle, Runnable)} in
 * left-to-right order.  The last button added is considered the "primary" action.
 * Pressing ENTER activates the focused button; ESC triggers {@link #setOnClose}.
 *
 * LAYOUT ACCOUNTING (for callers sizing the dialog):
 * <ul>
 *   <li>Header overhead: 3 rows — top border + title row + header divider
 *   <li>Footer overhead: 3 rows — footer divider + button row + bottom border
 *   <li>Body: all remaining rows
 * </ul>
 *
 * USAGE:
 * <pre>
 *   TerminalDialog dialog = new TerminalDialog("confirm-delete");
 *   dialog.setTitle("Confirm Delete");
 *   dialog.setBody(new TerminalLabel("msg", "Are you sure you want to delete this?"));
 *   dialog.addButton("Cancel", ButtonStyle.SECONDARY, dialog::close);
 *   dialog.addButton("Delete", ButtonStyle.DANGER,   () -> {
 *       performDelete();
 *       dialog.close();
 *   });
 *   dialog.setOnClose(() -> removeOverlay(dialog));
 *   dialog.open();
 * </pre>
 */
public class TerminalDialog extends TerminalRegion {

    private static final String INNER_GROUP = "dialog-inner";

    // ===== INNER TYPES =====

    /** Visual style hint for a button — callers map this to TextStyle. */
    public enum ButtonStyle {
        PRIMARY,
        SECONDARY,
        DANGER,
        SUCCESS
    }

    /** Internal representation of a single footer button. */
    private static final class DialogButton {
        final String      label;
        final ButtonStyle style;
        final Runnable    action;

        DialogButton(String label, ButtonStyle style, Runnable action) {
            this.label  = label;
            this.style  = style;
            this.action = action;
        }
    }

    // ===== STATE =====

    private String  title       = "";
    private boolean showClose   = true;   // ✕ in title bar
    private boolean isOpen      = false;
    private int     focusedBtn  = -1;     // index into buttons list

    private TerminalRenderable body = null;

    private final List<DialogButton> buttons = new ArrayList<>();

    // Lifecycle callbacks
    private Runnable onClose = null;
    private Runnable onOpen  = null;

    // ===== STYLES =====

    private LineStyle borderStyle       = LineStyle.ROUNDED;
    private TextStyle styleBorder       = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleTitle        = TextStyle.BOLD;
    private TextStyle styleCloseBtn     = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleBtnPrimary   = TextStyle.BOLD.withForeground(TextStyle.Color.CYAN);
    private TextStyle styleBtnSecondary = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleBtnDanger    = TextStyle.BOLD.withForeground(TextStyle.Color.RED);
    private TextStyle styleBtnSuccess   = TextStyle.BOLD.withForeground(TextStyle.Color.GREEN);
    private TextStyle styleBtnFocused   = TextStyle.BOLD.withBackground(TextStyle.Color.BRIGHT_BLACK);

    /** Spaces inserted before the title text inside the title row. */
    private static final int TITLE_PAD = 2;

    // ===== CHILD COMPONENTS =====

    private final TerminalVStack  rootStack;
    private final TerminalLabel   titleLabel;
    private final TerminalDivider headerDivider;
    private final TerminalVStack  bodyWrapper;
    private final TerminalDivider footerDivider;
    private final String innerGroupName;
    private TerminalLayoutGroupCallback innerGroupCallback = null;

    // ===== CONSTRUCTION =====

    public TerminalDialog(String name) {
        super(name);
        setWidthPreference(SizePreference.FIT_CONTENT);
        setHeightPreference(SizePreference.FIT_CONTENT);
        setFocusable(true);

        rootStack     = new TerminalVStack(name + "-root");
        titleLabel    = new TerminalLabel(name + "-title", "", styleTitle);
        headerDivider = new TerminalDivider(name + "-hdiv");
        bodyWrapper   = new TerminalVStack(name + "-body");
        footerDivider = new TerminalDivider(name + "-fdiv");
        innerGroupName = name + INNER_GROUP;

        buildLayout();
        addChild(rootStack);
        addToLayoutGroup(rootStack, innerGroupName);
        innerGroupCallback = this::layoutRootStack;
        registerChildGroupCallback(innerGroupName, innerGroupCallback);
        hide();
    }

    private void buildLayout() {
        rootStack.setSpacing(0);
        rootStack.setHAlignment(HAlignment.LEFT);

        titleLabel.setWidthPreference(SizePreference.FIT_CONTENT);
        titleLabel.setHeightPreference(SizePreference.FIT_CONTENT);

        headerDivider.setLineStyle(borderStyle);
        headerDivider.setLineTextStyle(styleBorder);

        bodyWrapper.setWidthPreference(SizePreference.FILL);
        bodyWrapper.setHeightPreference(SizePreference.FILL);
        bodyWrapper.setHAlignment(HAlignment.LEFT);
        bodyWrapper.setSpacing(0);

        footerDivider.setLineStyle(borderStyle);
        footerDivider.setLineTextStyle(styleBorder);

        rootStack.addChild(titleLabel);
        rootStack.addChild(headerDivider);
        rootStack.addChild(bodyWrapper);
        rootStack.addChild(footerDivider);
        // Button row is rendered manually in renderSelf (below the footer divider)
    }

    private void layoutRootStack(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        if (contexts.length == 0) return;
        TerminalRectangle parent = contexts[0].getParentRegion();
        if (parent == null) return;

        dataInterfaces.get(rootStack.getName()).setLayoutData(
            TerminalLayoutData.getBuilder()
                .setX(1)
                .setY(1)
                .setWidth(Math.max(1, parent.getWidth() - 2))
                .setHeight(Math.max(1, parent.getHeight() - 3))
                .build()
        );
    }

    private int renderOverheadRows() {
        return 6;
    }

    @Override
    public int getMinHeight() {
        return super.getMinHeight() + renderOverheadRows();
    }

    public int getPreferredWidth() {
        return resolveMeasuredWidth();
    }

    public int getPreferredHeight() {
        return resolveMeasuredHeight();
    }

    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, resolveMeasuredWidth(), resolveMeasuredHeight());
        return measured;
    }

    // ===== CONFIGURATION =====

    public TerminalDialog setTitle(String title) {
        String resolved = title != null ? title : "";
        if (!this.title.equals(resolved)) {
            this.title = resolved;
            titleLabel.setText(" ".repeat(TITLE_PAD) + this.title);
            notifyContentChanged();
        }
        return this;
    }

    public TerminalDialog setShowCloseButton(boolean show) {
        if (this.showClose != show) {
            this.showClose = show;
            notifyContentChanged();
        }
        return this;
    }

    /**
     * Set the body component — the main content area of the dialog.
     * Replaces any previously set body.
     */
    public TerminalDialog setBody(TerminalRegion body) {
        if (this.body != null) {
            bodyWrapper.removeChild(this.body);
        }
        this.body = body;
        if (body != null) {
            bodyWrapper.addChild(body);
        }
        notifyContentChanged();
        return this;
    }

    /**
     * Add an action button to the dialog footer.
     * Buttons are laid out right-to-left (last added = rightmost = primary).
     */
    public TerminalDialog addButton(String label, ButtonStyle style, Runnable action) {
        buttons.add(new DialogButton(label, style, action));
        if (focusedBtn < 0) focusedBtn = 0;
        else focusedBtn = buttons.size() - 1;
        notifyContentChanged();
        return this;
    }

    public TerminalDialog setOnClose(Runnable handler) { this.onClose = handler; return this; }
    public TerminalDialog setOnOpen(Runnable handler)  { this.onOpen  = handler; return this; }

    // ===== LIFECYCLE =====

    public void open() {
        if (isOpen) return;
        isOpen = true;
        show();
        if (focusedBtn < 0 && !buttons.isEmpty()) focusedBtn = buttons.size() - 1;
        invalidate();
        if (onOpen != null) onOpen.run();
    }

    public void close() {
        if (!isOpen) return;
        isOpen = false;
        hide();
        invalidate();
        if (onClose != null) onClose.run();
    }

    public boolean isOpen() { return isOpen; }

    // ===== KEYBOARD NAVIGATION =====

    public void focusPrevButton() {
        if (buttons.isEmpty()) return;
        focusedBtn = (focusedBtn - 1 + buttons.size()) % buttons.size();
        invalidate();
    }

    public void focusNextButton() {
        if (buttons.isEmpty()) return;
        focusedBtn = (focusedBtn + 1) % buttons.size();
        invalidate();
    }

    public void activateFocusedButton() {
        if (focusedBtn >= 0 && focusedBtn < buttons.size()) {
            Runnable action = buttons.get(focusedBtn).action;
            if (action != null) action.run();
        }
    }

    public void escape() { close(); }

    // ===== STYLE SETTERS =====

    public void setBorderStyle(LineStyle s)       { this.borderStyle = s; headerDivider.setLineStyle(s); footerDivider.setLineStyle(s); invalidate(); }
    public void setStyleBorder(TextStyle s)       { this.styleBorder = s; headerDivider.setLineTextStyle(s); footerDivider.setLineTextStyle(s); invalidate(); }
    public void setStyleTitle(TextStyle s)        { this.styleTitle  = s; titleLabel.setTextStyle(s); invalidate(); }
    public void setStyleBtnPrimary(TextStyle s)   { this.styleBtnPrimary   = s; invalidate(); }
    public void setStyleBtnSecondary(TextStyle s) { this.styleBtnSecondary = s; invalidate(); }
    public void setStyleBtnDanger(TextStyle s)    { this.styleBtnDanger    = s; invalidate(); }
    public void setStyleBtnSuccess(TextStyle s)   { this.styleBtnSuccess   = s; invalidate(); }
    public void setStyleBtnFocused(TextStyle s)   { this.styleBtnFocused   = s; invalidate(); }

    // ===== RENDERING =====

    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        TerminalRectangle r = getRegion();
        if (r == null) return;
        int w = r.getWidth();
        int h = r.getHeight();
        if (w <= 0 || h <= 0) return;

        fillRegion(batch, 1, 1, Math.max(0, w - 2), Math.max(0, h - 2), ' ', TextStyle.NORMAL);
        renderBorder(batch, w, h);

        // ✕ close button sits in the top-right of the title row (row 1 inside border)
        if (showClose && h > 1) {
            printAt(batch, w - 2, 1, "✕", styleCloseBtn);
        }

        // Button row occupies the row just above the bottom border
        if (!buttons.isEmpty() && h > 2) {
            renderButtonRow(batch, w, h - 2);
        }
    }

    private void renderBorder(TerminalBatchBuilder batch, int w, int h) {
        printAt(batch, 0,     0, String.valueOf(borderStyle.topLeft()),  styleBorder);
        drawHLine(batch, 1,   0, w - 2, borderStyle, styleBorder);
        printAt(batch, w - 1, 0, String.valueOf(borderStyle.topRight()), styleBorder);

        printAt(batch, 0,     h - 1, String.valueOf(borderStyle.bottomLeft()),  styleBorder);
        drawHLine(batch, 1,   h - 1, w - 2, borderStyle, styleBorder);
        printAt(batch, w - 1, h - 1, String.valueOf(borderStyle.bottomRight()), styleBorder);

        String vert = String.valueOf(borderStyle.vertical());
        for (int y = 1; y < h - 1; y++) {
            printAt(batch, 0,     y, vert, styleBorder);
            printAt(batch, w - 1, y, vert, styleBorder);
        }
    }

    private void renderButtonRow(TerminalBatchBuilder batch, int w, int row) {
        int innerW = w - 2;
        int x      = 1 + innerW;

        for (int i = buttons.size() - 1; i >= 0; i--) {
            DialogButton btn   = buttons.get(i);
            String       label = renderButtonLabel(btn);
            boolean      focused = (i == focusedBtn);

            x -= label.length() + 1;
            if (x < 1) break;

            TextStyle style = focused ? styleBtnFocused : buttonStyle(btn.style);
            printAt(batch, x, row, label, style);
        }
    }

    private TextStyle buttonStyle(ButtonStyle s) {
        switch (s) {
            case PRIMARY:   return styleBtnPrimary;
            case DANGER:    return styleBtnDanger;
            case SUCCESS:   return styleBtnSuccess;
            case SECONDARY:
            default:        return styleBtnSecondary;
        }
    }

    private String renderButtonLabel(DialogButton button) {
        return "[ " + button.label + " ]";
    }

    private int resolveMeasuredWidth() {
        return switch (getWidthPreference()) {
            case STATIC -> getRegion().getWidth();
            case FIT_CONTENT -> Math.max(getMinWidth(), calculateFitContentWidth());
            default -> getMinWidth();
        };
    }

    private int resolveMeasuredHeight() {
        return switch (getHeightPreference()) {
            case STATIC -> getRegion().getHeight();
            case FIT_CONTENT -> Math.max(getMinHeight(), calculateFitContentHeight());
            default -> getMinHeight();
        };
    }

    private int calculateFitContentWidth() {
        int titleWidth = TITLE_PAD + title.length() + (showClose ? 2 : 0);
        int buttonWidth = 0;
        for (DialogButton button : buttons) {
            buttonWidth += renderButtonLabel(button).length() + 1;
        }

        int innerWidth = Math.max(titleWidth, Math.max(buttonWidth, measureRenderableDimension(body, true)));
        return innerWidth + 2;
    }

    private int calculateFitContentHeight() {
        return measureRenderableDimension(body, false) + renderOverheadRows();
    }

    private int measureRenderableDimension(TerminalRenderable renderable, boolean width) {
        if (renderable == null || renderable.isHidden()) {
            return 0;
        }

        TerminalRectangle requested = renderable.getRequestedRegion();
        if (requested != null) {
            return width ? requested.getWidth() : requested.getHeight();
        }

        TerminalRectangle region = renderable.getRegion();
        int currentDimension = width ? region.getWidth() : region.getHeight();
        if (currentDimension > 0) {
            return currentDimension;
        }

        if (renderable instanceof TerminalRegion terminalRegion) {
            TerminalRectangle measured = terminalRegion.measureContent(null);
            int measuredDimension = width ? measured.getWidth() : measured.getHeight();
            terminalRegion.getRegionPool().recycle(measured);
            if (measuredDimension > 0) {
                return measuredDimension;
            }
        }

        return currentDimension;
    }

    @Override
    protected void onDestroying() {
        destroyLayoutGroup(innerGroupName);
        innerGroupCallback = null;
    }
}
