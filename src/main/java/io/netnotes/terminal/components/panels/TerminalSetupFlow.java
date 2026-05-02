package io.netnotes.terminal.components.panels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.netnotes.debug.RenderDiagnostics;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.components.panels.TerminalAbstractStack;
import io.netnotes.engine.ui.layout2d.AlignSelf;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutGroupCallback;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.renderer.LayoutGroup.LayoutDataInterface;

/**
 * TerminalSetupFlow - Interactive multi-step user-driven wizard.
 *
 * <p>Unlike {@link io.netnotes.terminal.components.install.TerminalInstallWizard}
 * (which <em>displays progress of automated work</em>), a {@code TerminalSetupFlow}
 * <em>waits for the user at every step</em>.  It is designed for initial
 * configuration flows, onboarding, and any scenario where the user must
 * provide input or make choices before proceeding.
 *
 * <p>Structure:
 * <pre>
 * ╭──────────────────────────────────────────────────────────────────────────╮
 * │  Step 2 of 4  ●●○○  Network Configuration                               │
 * ├──────────────────────────────────────────────────────────────────────────┤
 * │                                                                          │
 * │   (active step body — any TerminalRenderable)                            │
 * │                                                                          │
 * ├──────────────────────────────────────────────────────────────────────────┤
 * │  [✕ Cancel]                             [ ← Back ]  [ Next → ]          │
 * ╰──────────────────────────────────────────────────────────────────────────╯
 * </pre>
 *
 * LAYOUT ACCOUNTING (for callers sizing the flow):
 * <ul>
 *   <li>Header overhead: 2 rows — top border + title row + header divider (drawn by child)
 *   <li>Footer overhead: 2 rows — footer divider (drawn by child) + button/nav row + bottom border
 *   <li>Body: all remaining rows
 * </ul>
 *
 * NAVIGATION:
 * <ul>
 *   <li>{@link #next()} — validate current step and advance
 *   <li>{@link #back()} — go back to previous step (no validation)
 *   <li>{@link #cancel()} — fires onCancel callback and hides
 *   <li>{@link #finish()} — fires onFinish callback after final step is validated
 * </ul>
 *
 * USAGE:
 * <pre>
 *   TerminalSetupFlow flow = new TerminalSetupFlow("initial-setup");
 *   flow.setFlowTitle("Initial Setup");
 *
 *   flow.addStep(new FlowStep("welcome", "Welcome",
 *       new TerminalLabel("lbl", "Press Next to begin…")));
 *
 *   TerminalTextField hostField = new TerminalTextField("host");
 *   flow.addStep(new FlowStep("network", "Network", hostField)
 *       .withValidator(() -> !hostField.getText().isBlank()
 *           ? null : "Hostname is required"));
 *
 *   flow.setOnFinish(flow::close);
 *   flow.setOnCancel(flow::close);
 *   flow.start();
 * </pre>
 */
public class TerminalSetupFlow extends TerminalGroupRegion {



    // ===== INNER TYPES =====

    @FunctionalInterface
    public interface Validator {
        /** @return null = valid; non-null = error message to display */
        String validate();
    }

    @FunctionalInterface
    public interface FinishHandler {
        void onFinish();
    }

    public static class FlowStep {
        private final String          id;
        private final String          title;
        private final TerminalRegion  body;
        private Validator             validator = null;
        private Runnable              onEnter   = null;
        private Runnable              onLeave   = null;

        public FlowStep(String id, String title, TerminalRegion body) {
            if (id == null || id.isBlank())
                throw new IllegalArgumentException("FlowStep id must not be blank");
            this.id    = id;
            this.title = title != null ? title : id;
            this.body  = body;
        }

        public FlowStep withValidator(Validator v) { this.validator = v; return this; }
        public FlowStep withOnEnter(Runnable r)    { this.onEnter   = r; return this; }
        public FlowStep withOnLeave(Runnable r)    { this.onLeave   = r; return this; }

        public String        getId()        { return id; }
        public String        getTitle()     { return title; }
        public TerminalRegion getBody()     { return body; }
        public Validator     getValidator() { return validator; }
    }

    // ===== STATE =====

    private String  flowTitle  = "Setup";
    private boolean showBorder = true;
    private boolean showCancel = true;
    private boolean isOpen     = false;

    private final List<FlowStep> steps        = new ArrayList<>();
    private int                  currentIndex = 0;

    private String validationError = null;

    private Runnable      onCancel = null;
    private FinishHandler onFinish = null;

    // ===== STYLES =====

    private LineStyle borderStyle        = LineStyle.ROUNDED;
    private TextStyle styleBorder        = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleFlowTitle     = TextStyle.BOLD;
    private TextStyle styleStepTitle     = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleDotActive     = TextStyle.BOLD.withForeground(TextStyle.Color.CYAN);
    private TextStyle styleDotDone       = TextStyle.NORMAL.withForeground(TextStyle.Color.GREEN);
    private TextStyle styleDotPending    = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleNavBtn        = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleNavBtnPrimary = TextStyle.BOLD.withForeground(TextStyle.Color.CYAN);
    private TextStyle styleNavBtnCancel  = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleValidationErr = TextStyle.BOLD.withForeground(TextStyle.Color.RED);

    private static final char DOT_DONE    = '●';
    private static final char DOT_ACTIVE  = '◉';
    private static final char DOT_PENDING = '○';

    // ===== CHILD COMPONENTS =====

    private final TerminalVStack  rootStack;
    private final TerminalDivider headerDivider;
    private final TerminalVStack  bodySlot;
    private final TerminalDivider footerDivider;

    private TerminalRenderable mountedBody = null;

    // ===== CONSTRUCTION =====

    public TerminalSetupFlow(String name) {
        super(name, "setup-flow");
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FILL);
        setFocusable(true);

        rootStack     = new TerminalVStack(name + "-root");
        headerDivider = new TerminalDivider(name + "-hdiv");
        bodySlot      = new TerminalVStack(name + "-body");
        footerDivider = new TerminalDivider(name + "-fdiv");

        buildLayout();

        addChild(rootStack);

    }

    @Override protected TerminalLayoutGroupCallback createLayoutCallback(){ return this::layoutRootStack; }

    private void buildLayout() {
        rootStack.setSpacing(0);
        rootStack.setVAlignment(AlignSelf.FLEX_START);
        rootStack.setHAlignment(AlignSelf.FLEX_START);

        headerDivider.setLineStyle(borderStyle);
        headerDivider.setLineTextStyle(styleBorder);

        bodySlot.setWidthPreference(SizePreference.FILL);
        bodySlot.setHeightPreference(SizePreference.FIT_CONTENT);
        bodySlot.setHAlignment(AlignSelf.FLEX_START);
        bodySlot.setSpacing(0);

        footerDivider.setLineStyle(borderStyle);
        footerDivider.setLineTextStyle(styleBorder);

        rootStack.addChild(headerDivider);
        rootStack.addChild(bodySlot);
        rootStack.addChild(footerDivider);
    }

    private void layoutRootStack(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        if (contexts.length == 0) return;
        TerminalRectangle parent = contexts[0].getParentRegion();
        if (parent == null) return;

        int innerX = showBorder ? 1 : 0;
        int innerY = showBorder ? 2 : 1;
        int innerW = parent.getWidth() - (showBorder ? 2 : 0);
        int innerH = parent.getHeight() - innerY - (showBorder ? 2 : 1);

        if (innerW <= 0 || innerH <= 0) {
            RenderDiagnostics.logRenderBlocker(
                "setupflow-inner-space:" + getName(),
                "TerminalSetupFlow.layout",
                "non-positive-inner-layout-bounds",
                () -> "flow=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\tparent=" + RenderDiagnostics.summarizeRegion(parent)
                    + "\n\tshowBorder=" + showBorder
                    + "\n\tinnerX=" + innerX
                    + "\n\tinnerY=" + innerY
                    + "\n\tinnerW=" + innerW
                    + "\n\tinnerH=" + innerH
            );
        }

        dataInterfaces.get(rootStack.getName()).setLayoutData(
            TerminalLayoutData.getBuilder()
                .setX(innerX)
                .setY(innerY)
                .setWidth(Math.max(0, innerW))
                .setHeight(Math.max(0, innerH))
                .build()
        );
    }

    private int renderOverheadRows() {
        return 4 + (showBorder ? 2 : 0);
    }

    @Override
    public int getMinHeight() {
        return Math.max(super.getMinHeight(), renderOverheadRows());
    }

    public int getPreferredHeight() {
        return resolveMeasuredHeight();
    }

    public int getPreferredWidth() {
        return resolveMeasuredWidth();
    }

    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, resolveMeasuredWidth(), resolveMeasuredHeight());
        return measured;
    }

    // ===== STEP MANAGEMENT =====

    public TerminalSetupFlow addStep(FlowStep step) {
        if (step == null) return this;
        steps.add(step);
        notifyContentChanged();
        return this;
    }

    public TerminalSetupFlow setSteps(List<FlowStep> newSteps) {
        steps.clear();
        if (newSteps != null) steps.addAll(newSteps);
        currentIndex = 0;
        notifyContentChanged();
        return this;
    }

    public List<FlowStep> getSteps()     { return Collections.unmodifiableList(steps); }
    public int            getCurrentIndex() { return currentIndex; }
    public int            getStepCount()    { return steps.size(); }

    public FlowStep getCurrentStep() {
        if (steps.isEmpty() || currentIndex < 0 || currentIndex >= steps.size()) return null;
        return steps.get(currentIndex);
    }

    // ===== LIFECYCLE =====

    public void start() {
        if (steps.isEmpty()) return;
        isOpen          = true;
        currentIndex    = 0;
        validationError = null;
        show();
        mountStep(currentIndex);
        invalidate();
    }

    public void goToStep(int index) {
        if (index < 0 || index >= steps.size()) return;
        unmountCurrent();
        currentIndex    = index;
        validationError = null;
        mountStep(currentIndex);
        invalidate();
    }

    public void close() {
        if (!isOpen) return;
        isOpen = false;
        unmountCurrent();
        hide();
        invalidate();
    }

    public boolean isOpen() { return isOpen; }

    // ===== NAVIGATION =====

    public boolean next() {
        FlowStep step = getCurrentStep();
        if (step == null) return false;

        if (step.getValidator() != null) {
            String err = step.getValidator().validate();
            if (err != null) {
                validationError = err;
                invalidate();
                return false;
            }
        }
        validationError = null;
        if (step.onLeave != null) step.onLeave.run();

        if (currentIndex == steps.size() - 1) {
            finish();
            return true;
        }

        unmountCurrent();
        currentIndex++;
        mountStep(currentIndex);
        invalidate();
        return true;
    }

    public void back() {
        if (currentIndex <= 0) return;
        FlowStep step = getCurrentStep();
        if (step != null && step.onLeave != null) step.onLeave.run();
        validationError = null;
        unmountCurrent();
        currentIndex--;
        mountStep(currentIndex);
        invalidate();
    }

    public void cancel() {
        close();
        if (onCancel != null) onCancel.run();
    }

    public void finish() {
        close();
        if (onFinish != null) onFinish.onFinish();
    }

    // ===== CALLBACKS =====

    public TerminalSetupFlow setOnCancel(Runnable handler)      { this.onCancel = handler; return this; }
    public TerminalSetupFlow setOnFinish(FinishHandler handler) { this.onFinish = handler; return this; }

    // ===== CONFIGURATION =====

    public TerminalSetupFlow setFlowTitle(String title)    { String resolved = title != null ? title : ""; if (!this.flowTitle.equals(resolved)) { this.flowTitle = resolved; notifyContentChanged(); } return this; }
    public TerminalSetupFlow setShowBorder(boolean show)   { if (this.showBorder != show) { this.showBorder = show; requestLayoutUpdate(); invalidate(); } return this; }
    public TerminalSetupFlow setShowCancel(boolean show)   { if (this.showCancel != show) { this.showCancel = show; notifyContentChanged(); } return this; }

    // ===== STYLE SETTERS =====

    public void setBorderStyle(LineStyle s)        { this.borderStyle = s; headerDivider.setLineStyle(s); footerDivider.setLineStyle(s); invalidate(); }
    public void setStyleBorder(TextStyle s)        { this.styleBorder = s; headerDivider.setLineTextStyle(s); footerDivider.setLineTextStyle(s); invalidate(); }
    public void setStyleFlowTitle(TextStyle s)     { this.styleFlowTitle     = s; invalidate(); }
    public void setStyleStepTitle(TextStyle s)     { this.styleStepTitle     = s; invalidate(); }
    public void setStyleDotActive(TextStyle s)     { this.styleDotActive     = s; invalidate(); }
    public void setStyleDotDone(TextStyle s)       { this.styleDotDone       = s; invalidate(); }
    public void setStyleDotPending(TextStyle s)    { this.styleDotPending    = s; invalidate(); }
    public void setStyleNavBtn(TextStyle s)        { this.styleNavBtn        = s; invalidate(); }
    public void setStyleNavBtnPrimary(TextStyle s) { this.styleNavBtnPrimary = s; invalidate(); }
    public void setStyleValidationErr(TextStyle s) { this.styleValidationErr = s; invalidate(); }

    // ===== RENDERING =====

    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        TerminalRectangle r = getRegion();
        if (r == null) return;
        int w = r.getWidth();
        int h = r.getHeight();
        if (w <= 0 || h <= 0) return;

        if (showBorder) {
            fillRegion(batch, 1, 1, Math.max(0, w - 2), Math.max(0, h - 2), ' ', TextStyle.NORMAL);
        } else {
            fillRegion(batch, 0, 0, w, h, ' ', TextStyle.NORMAL);
        }
        if (showBorder) {
            renderOuterBorder(batch, w, h);
        }

        // Header row: sits just inside the top border
        renderHeader(batch, w, showBorder ? 1 : 0);

        // Footer row: sits just inside the bottom border, above it
        int footerRow = h - (showBorder ? 2 : 1);
        if (footerRow > (showBorder ? 1 : 0)) {
            renderFooter(batch, w, footerRow);
        }
    }

    @Override
    protected TerminalInsets getChildRenderInsets() {
        return showBorder ? new TerminalInsets(1) : null;
    }

    private void renderOuterBorder(TerminalBatchBuilder batch, int w, int h) {
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

    private void renderHeader(TerminalBatchBuilder batch, int w, int row) {
        int innerX = showBorder ? 1 : 0;
        int innerW = showBorder ? w - 2 : w;

        String stepCounter = "Step " + (currentIndex + 1) + " of " + steps.size();
        String leftText    = "  " + flowTitle + "  ·  " + stepCounter;

        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) dots.append(' ');
            if      (i < currentIndex)  dots.append(DOT_DONE);
            else if (i == currentIndex) dots.append(DOT_ACTIVE);
            else                        dots.append(DOT_PENDING);
        }

        FlowStep cur     = getCurrentStep();
        String stepTitle = cur != null ? cur.getTitle() : "";

        int dotStart = Math.max(leftText.length() + 2, (innerW - dots.length()) / 2);
        int titleX   = Math.max(dotStart + dots.length() + 2, innerW - stepTitle.length() - 1);

        // Left text
        printAt(batch, innerX, row, truncate(leftText, Math.max(0, dotStart - 1)), styleFlowTitle);

        // Dot indicators with per-character colouring
        if (dotStart + dots.length() <= innerW) {
            for (int i = 0; i < steps.size(); i++) {
                int cx = innerX + dotStart + (i > 0 ? i * 2 : 0);
                if (cx >= innerX + innerW) break;
                char      dc;
                TextStyle ds;
                if      (i < currentIndex)  { dc = DOT_DONE;    ds = styleDotDone; }
                else if (i == currentIndex) { dc = DOT_ACTIVE;  ds = styleDotActive; }
                else                        { dc = DOT_PENDING; ds = styleDotPending; }
                printAt(batch, cx, row, String.valueOf(dc), ds);
            }
        }

        // Step title — right-aligned
        if (titleX + stepTitle.length() < innerX + innerW) {
            printAt(batch, innerX + titleX, row, stepTitle, styleStepTitle);
        }
    }

    private void renderFooter(TerminalBatchBuilder batch, int w, int row) {
        int innerX = showBorder ? 1 : 0;
        int innerW = showBorder ? w - 2 : w;

        // Validation error on the left
        if (validationError != null && !validationError.isBlank()) {
            String err = "⚠ " + validationError;
            printAt(batch, innerX + 2, row, truncate(err, innerW / 2), styleValidationErr);
        }

        // Nav buttons right-aligned: [Finish/Next →]  [← Back]  [✕ Cancel]
        boolean isLast      = currentIndex == steps.size() - 1;
        String  nextLabel   = isLast ? "[ Finish ]" : "[ Next → ]";
        String  backLabel   = "[ ← Back ]";
        String  cancelLabel = "[✕ Cancel]";

        int x = innerX + innerW;

        x -= nextLabel.length() + 1;
        if (x > innerX) printAt(batch, x, row, nextLabel, styleNavBtnPrimary);

        if (currentIndex > 0) {
            x -= backLabel.length() + 1;
            if (x > innerX) printAt(batch, x, row, backLabel, styleNavBtn);
        }

        if (showCancel) {
            x -= cancelLabel.length() + 1;
            if (x > innerX) printAt(batch, x, row, cancelLabel, styleNavBtnCancel);
        }
    }

    // ===== BODY MOUNTING =====

    private void mountStep(int index) {
        if (index < 0 || index >= steps.size()) return;
        FlowStep step = steps.get(index);
        TerminalRegion body = step.getBody();
        if (body != null) {
            body.setWidthPreference(SizePreference.FILL);
            bodySlot.addChild(body);
            mountedBody = body;
        }
        if (step.onEnter != null) step.onEnter.run();
    }

    private void unmountCurrent() {
        if (mountedBody != null) {
            bodySlot.removeChild(mountedBody);
            mountedBody = null;
        }
    }

    // ===== HELPERS =====

    private static String truncate(String s, int max) {
        if (s == null || max <= 0) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
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
        int bodyWidth = 0;
        int maxStepTitleWidth = 0;
        for (FlowStep step : steps) {
            bodyWidth = Math.max(bodyWidth, measureRenderableDimension(step.getBody(), true));
            maxStepTitleWidth = Math.max(maxStepTitleWidth, step.getTitle().length());
        }

        int stepCount = Math.max(1, steps.size());
        String stepCounter = "Step " + stepCount + " of " + stepCount;
        String leftText = "  " + flowTitle + "  ·  " + stepCounter;
        int dotsWidth = steps.isEmpty() ? 0 : (steps.size() * 2) - 1;
        int headerWidth = leftText.length();
        if (dotsWidth > 0) {
            headerWidth += 2 + dotsWidth;
        }
        if (maxStepTitleWidth > 0) {
            headerWidth += 2 + maxStepTitleWidth;
        }

        int footerWidth = "[ Finish ]".length() + 1;
        if (steps.size() > 1) {
            footerWidth += "[ ← Back ]".length() + 1;
        }
        if (showCancel) {
            footerWidth += "[✕ Cancel]".length() + 1;
        }

        int innerWidth = Math.max(bodyWidth, Math.max(headerWidth, footerWidth));
        return innerWidth + (showBorder ? 2 : 0);
    }

    private int calculateFitContentHeight() {
        int bodyHeight = 0;
        for (FlowStep step : steps) {
            bodyHeight = Math.max(bodyHeight, measureRenderableDimension(step.getBody(), false));
        }
        return bodyHeight + renderOverheadRows();
    }

    private int measureRenderableDimension(TerminalRenderable renderable, boolean width) {
        if (renderable.isHidden()) {
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

}
