package io.netnotes.terminal.components.install;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.components.display.TerminalBitmap;
import io.netnotes.terminal.components.display.TerminalBitmap.RenderMode;
import io.netnotes.terminal.components.display.TerminalBitmap.ScaleMode;
import io.netnotes.terminal.components.display.TerminalBitmapView;
import io.netnotes.terminal.components.install.InstallStep.Status;
import io.netnotes.terminal.components.panels.TerminalDivider;
import io.netnotes.terminal.components.panels.TerminalVStack;
import io.netnotes.terminal.components.panels.TerminalVStack.VAlignment;
import io.netnotes.engine.ui.SizePreference;

/**
 * TerminalInstallWizard - Component-based installation progress wizard
 *
 * <p>Provides a self-contained installation wizard UI built from smaller
 * terminal components. It composes:
 * <ul>
 *   <li>An optional <b>brand panel</b> (full-width {@link TerminalBitmapView} for logos / art)
 *   <li>A <b>brand divider</b> (shown only when a brand bitmap is present)
 *   <li>A <b>header row</b> showing the wizard title and overall progress bar
 *   <li>A <b>header divider</b> ({@link TerminalDivider}) separating header from step list
 *   <li>A <b>step list</b> ({@link TerminalInstallStepRow} per step inside a {@link TerminalVStack})
 *   <li>A <b>footer divider</b> and status line
 * </ul>
 *
 * LAYOUT OVERVIEW (80 cols, 24 rows, with brand):
 * <pre>
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  ▄▄▄▄▄  ▄  ▄▄▄▄▄  ▄▄▄▄▄  ▄  ▄  ▄▄▄▄▄  ▄▄▄▄▄  ▄▄▄▄▄  ▄▄▄▄▄               ║
 * ║  █   █  █  █      █      █  █  █   █  ▄▄  █  ▄▄  █  █               ║
 * ║  █   █  █  █▄▄▄   ▀▄▄    █▀▀█  █   █  █   █  █   █  █▄▄▄            ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  NetNotes Installer v1.0                        Overall:  52% [██████░░░░░] ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  ✓  1. Verify System Requirements                                      DONE ║
 * ║  ◉  2. Configure Database…                              67% [███████░░░░░]  ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  Step 2 of 4  ·  Elapsed: 00:12  ·  Press Ctrl+C to cancel                 ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * </pre>
 *
 * BRAND USAGE:
 * <pre>
 *   wizard.setBrandAsciiArt(new String[]{
 *       " ███╗   ██╗███████╗████████╗",
 *       " ████╗  ██║██╔════╝╚══██╔══╝",
 *       " ██╔██╗ ██║█████╗     ██║   ",
 *   }, '█', 4);
 *
 *   // or with a TerminalBitmap
 *   TerminalBitmap logo = new TerminalBitmap(120, 24);
 *   logo.drawRect(0, 0, 120, 24, true);
 *   wizard.setBrandBitmap(logo, RenderMode.SEXTANT, 4);
 * </pre>
 *
 * THREAD SAFETY:
 * All public mutating methods end with {@link #invalidate()} so they are safe
 * to call from non-render threads.  Do <em>not</em> call them from inside a
 * render callback.
 */
public class TerminalInstallWizard extends TerminalRegion {

    // ===== INNER TYPES =====

    /**
     * Callback fired when all steps reach a terminal state.
     */
    @FunctionalInterface
    public interface CompletionListener {
        /**
         * @param success    {@code true} if all non-optional steps completed without error
         * @param failedStep the first failed step, or {@code null} on success
         */
        void onWizardComplete(boolean success, InstallStep failedStep);
    }

    // ===== DEFAULT STYLES =====

    private TextStyle styleBorder       = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleTitle        = TextStyle.BOLD;
    private TextStyle styleOverallFill  = TextStyle.BOLD.withForeground(TextStyle.Color.CYAN);
    private TextStyle styleOverallEmpty = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleFooter       = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleSuccess      = TextStyle.BOLD.withForeground(TextStyle.Color.GREEN);
    private TextStyle styleError        = TextStyle.BOLD.withForeground(TextStyle.Color.RED);
    private LineStyle borderStyle       = LineStyle.DOUBLE;
    private char      overallFillChar   = '█';
    private char      overallEmptyChar  = '░';

    // ===== LAYOUT CONSTANTS =====

    private static final int OVERALL_BAR_WIDTH = 20;
    private static final int TITLE_PADDING     = 2;

    // ===== STATE =====

    private String  title       = "Installation Wizard";
    private String  subtitle    = null;
    private boolean showBorder  = true;
    private boolean showFooter  = true;
    private boolean showElapsed = true;

    // Step registry (ordered)
    private final List<InstallStep>            steps    = new ArrayList<>();
    private final List<TerminalInstallStepRow> stepRows = new ArrayList<>();

    // Overall progress (0–1), auto-computed or manually overridden
    private float   overallProgress     = 0f;
    private boolean autoOverallProgress = true;

    // Currently active (RUNNING) step
    private InstallStep activeStep = null;

    // Elapsed time tracking
    private long startTimeMs = -1L;
    private long elapsedMs   = 0L;

    // Completion
    private CompletionListener completionListener = null;
    private boolean wizardComplete = false;

    // ── Child components ──────────────────────────────────────────────────────
    private final TerminalVStack rootStack;
    private TerminalDivider      brandDivider;    // shown only when brand is present
    private TerminalBitmapView   brandView;       // null when no brand configured
    private TerminalDivider      headerDivider;
    private TerminalDivider      footerDivider;
    private TerminalVStack       stepListStack;

    // ===== CONSTRUCTION =====

    public TerminalInstallWizard(String name) {
        super(name);
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FILL);

        rootStack = new TerminalVStack(name + "-root");
        rootStack.setSpacing(0);
        rootStack.setVAlignment(VAlignment.TOP);

        buildLayout();
        addChild(rootStack);
    }

    // ===== LAYOUT CONSTRUCTION =====

    private void buildLayout() {
        // Brand components — hidden until a bitmap is assigned
        brandView = new TerminalBitmapView(getName() + "-brand");
        brandView.setWidthPreference(SizePreference.FILL);
        brandView.setHeightPreference(SizePreference.FIT_CONTENT);
        brandView.setRenderMode(RenderMode.SEXTANT);
        brandView.setScaleMode(ScaleMode.FIT);
        brandView.setBilinear(true);
        brandView.hide();   // invisible until setBrandBitmap is called

        brandDivider = new TerminalDivider(getName() + "-bdiv", TerminalDivider.Orientation.HORIZONTAL);
        brandDivider.setLineStyle(borderStyle);
        brandDivider.setLineTextStyle(styleBorder);
        brandDivider.hide();

        headerDivider = new TerminalDivider(getName() + "-hdiv", TerminalDivider.Orientation.HORIZONTAL);
        headerDivider.setLineStyle(borderStyle);
        headerDivider.setLineTextStyle(styleBorder);

        stepListStack = new TerminalVStack(getName() + "-steps");
        stepListStack.setSpacing(0);
        stepListStack.setVAlignment(VAlignment.TOP);
        stepListStack.setWidthPreference(SizePreference.FILL);
        stepListStack.setHeightPreference(SizePreference.FIT_CONTENT);

        footerDivider = new TerminalDivider(getName() + "-fdiv", TerminalDivider.Orientation.HORIZONTAL);
        footerDivider.setLineStyle(borderStyle);
        footerDivider.setLineTextStyle(styleBorder);

        // Order: [brand] [brandDivider] headerDivider stepList footerDivider
        rootStack.addChild(brandView);
        rootStack.addChild(brandDivider);
        rootStack.addChild(headerDivider);
        rootStack.addChild(stepListStack);
        rootStack.addChild(footerDivider);
    }

    // ===== BRAND PANEL =====

    /**
     * Assign a pre-built {@link TerminalBitmap} as the branding area displayed
     * above the wizard header.
     *
     * @param bitmap     the logo / splash bitmap (not null)
     * @param mode       sub-character rendering mode (SEXTANT recommended)
     * @param heightRows fixed character-row height for the brand area; pass
     *                   {@code 0} to auto-size from the bitmap's aspect ratio
     */
    public TerminalInstallWizard setBrandBitmap(TerminalBitmap bitmap, RenderMode mode, int heightRows) {
        if (bitmap == null) return this;

        brandView.setBitmap(bitmap);
        brandView.setRenderMode(mode != null ? mode : RenderMode.SEXTANT);

        if (heightRows > 0) {
            brandView.setHeightPreference(SizePreference.FIT_CONTENT);
            brandView.setMinHeight(heightRows);
            // Override preferred height to the fixed value
            brandView.setFixedAspectRatio(0f);  // disable aspect lock
        } else {
            brandView.setAspectRatioFromBitmap();
        }

        brandView.show();
        brandDivider.show();
        requestLayoutUpdate();
        invalidate();
        return this;
    }

    /**
     * Build a {@link TerminalBitmap} from ASCII art and use it as the brand area.
     *
     * <pre>
     *   wizard.setBrandAsciiArt(new String[]{
     *       "##   ## ## ####### ##   ##",
     *       "###  ## ## ##      ###  ##",
     *       "## # ## ## #####   ## # ##",
     *   }, '#', 0);
     * </pre>
     *
     * @param rows       ASCII art lines (all the same width ideally)
     * @param fillChar   character treated as a lit pixel
     * @param heightRows fixed height in character rows; 0 = auto from aspect ratio
     */
    public TerminalInstallWizard setBrandAsciiArt(String[] rows, char fillChar, int heightRows) {
        return setBrandBitmap(TerminalBitmap.fromAsciiArt(rows, fillChar),
                              RenderMode.SEXTANT, heightRows);
    }

    /**
     * Set the {@link RenderMode} used for the brand area.
     * Has no effect if no brand bitmap has been set.
     */
    public TerminalInstallWizard setBrandRenderMode(RenderMode mode) {
        if (mode != null) {
            brandView.setRenderMode(mode);
            if (!brandView.isHidden()) invalidate();
        }
        return this;
    }

    /** Set the ink/foreground style for the brand bitmap pixels. */
    public TerminalInstallWizard setBrandStyle(TextStyle style) {
        if (style != null) {
            brandView.setStyle(style);
            if (!brandView.isHidden()) invalidate();
        }
        return this;
    }

    /** Remove the brand area and hide the brand divider. */
    public TerminalInstallWizard clearBrand() {
        brandView.hide();
        brandDivider.hide();
        requestLayoutUpdate();
        invalidate();
        return this;
    }

    /** Return whether a brand bitmap is currently displayed. */
    public boolean hasBrand() {
        return !brandView.isHidden();
    }

    // ===== STEP MANAGEMENT =====

    /** Add a single step to the wizard. */
    public void addStep(InstallStep step) {
        if (step == null) return;
        step.setStepNumber(steps.size() + 1);
        steps.add(step);
        appendStepRow(step);
        invalidate();
    }

    /** Add multiple steps at once, then rebuild layout in one pass. */
    public void addSteps(List<InstallStep> newSteps) {
        if (newSteps == null || newSteps.isEmpty()) return;
        for (InstallStep s : newSteps) {
            s.setStepNumber(steps.size() + 1);
            steps.add(s);
        }
        rebuildStepRows();
        invalidate();
    }

    /** Remove all steps and reset the wizard to empty. */
    public void clearSteps() {
        steps.clear();
        stepRows.clear();
        stepListStack.clearChildren();
        activeStep      = null;
        wizardComplete  = false;
        overallProgress = 0f;
        startTimeMs     = -1L;
        invalidate();
    }

    /** Rebuild step-row components from the current step list. */
    public void rebuildStepRows() {
        stepRows.clear();
        stepListStack.clearChildren();
        for (InstallStep s : steps) {
            appendStepRow(s);
        }
        requestLayoutUpdate();
    }

    private void appendStepRow(InstallStep step) {
        TerminalInstallStepRow row = new TerminalInstallStepRow(
            getName() + "-step-" + step.getId(), step);
        stepRows.add(row);
        stepListStack.addChild(row);
    }

    /** Unmodifiable view of the registered steps. */
    public List<InstallStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    // ===== WIZARD CONTROL =====

    /** Transition a step to {@link Status#RUNNING}. */
    public void beginStep(String stepId) {
        InstallStep step = findStep(stepId);
        if (step == null) return;

        if (startTimeMs < 0) startTimeMs = System.currentTimeMillis();

        if (activeStep != null && activeStep != step
                && activeStep.getStatus() == Status.RUNNING) {
            activeStep.setStatus(Status.COMPLETE);
            activeStep.setProgress(1f);
            refreshRow(activeStep);
        }

        step.setStatus(Status.RUNNING);
        activeStep = step;
        refreshRow(step);
        updateOverallProgress();
        invalidate();
    }

    /** Update progress (0–1) and optional detail text for the given step. */
    public void updateProgress(String stepId, float progress, String detail) {
        InstallStep step = findStep(stepId);
        if (step == null) return;
        step.setProgress(progress);
        if (detail != null) step.setDetail(detail);
        refreshRow(step);
        updateOverallProgress();
        invalidate();
    }

    /** Append a log line to a step (shown when that row is expanded). */
    public void logLine(String stepId, String line) {
        InstallStep step = findStep(stepId);
        if (step == null) return;
        step.addLogLine(line);
        TerminalInstallStepRow row = findRow(step);
        if (row != null) {
            row.onLogLineAdded();
        }
        invalidate();
    }

    /** Mark a step as successfully completed. */
    public void completeStep(String stepId) {
        InstallStep step = findStep(stepId);
        if (step == null) return;
        step.setStatus(Status.COMPLETE);
        step.setProgress(1f);
        if (activeStep == step) activeStep = null;
        refreshRow(step);
        updateOverallProgress();
        checkWizardCompletion();
        invalidate();
    }

    /** Mark a step as failed with an optional error message. */
    public void failStep(String stepId, String errorMessage) {
        InstallStep step = findStep(stepId);
        if (step == null) return;
        step.setStatus(Status.ERROR);
        step.setErrorMessage(errorMessage);
        if (errorMessage != null) step.setDetail(errorMessage);
        if (activeStep == step) activeStep = null;
        refreshRow(step);
        updateOverallProgress();
        checkWizardCompletion();
        invalidate();
    }

    /** Mark a step as skipped (e.g. optional feature not selected). */
    public void skipStep(String stepId) {
        InstallStep step = findStep(stepId);
        if (step == null) return;
        step.setStatus(Status.SKIPPED);
        if (activeStep == step) activeStep = null;
        refreshRow(step);
        updateOverallProgress();
        checkWizardCompletion();
        invalidate();
    }

    /** Reset all steps to PENDING and clear elapsed time, ready for a fresh run. */
    public void resetWizard() {
        for (InstallStep s : steps) s.reset();
        activeStep      = null;
        wizardComplete  = false;
        overallProgress = 0f;
        startTimeMs     = -1L;
        elapsedMs       = 0L;
        for (TerminalInstallStepRow row : stepRows) {
            row.setExpanded(false);
            row.invalidate();
        }
        invalidate();
    }

    // ===== SPINNER TICK =====

    /**
     * Advance all running step spinners and update elapsed time.
     * Call from a periodic timer (e.g. every 100–150 ms).
     */
    public void tick() {
        if (startTimeMs > 0 && !wizardComplete) {
            elapsedMs = System.currentTimeMillis() - startTimeMs;
        }
        for (TerminalInstallStepRow row : stepRows) {
            if (row.getStep().getStatus() == Status.RUNNING) {
                row.advanceSpinner();
            }
        }
        invalidate();
    }

    // ===== EXPAND/COLLAPSE =====

    public void setStepExpanded(String stepId, boolean expanded) {
        InstallStep step = findStep(stepId);
        if (step == null) return;
        TerminalInstallStepRow row = findRow(step);
        if (row != null) row.setExpanded(expanded);
    }

    public void collapseAll() {
        for (TerminalInstallStepRow row : stepRows) row.setExpanded(false);
    }

    public void expandActive() {
        for (TerminalInstallStepRow row : stepRows) {
            row.setExpanded(row.getStep().getStatus() == Status.RUNNING);
        }
    }

    // ===== CONFIGURATION =====

    public TerminalInstallWizard setTitle(String title) {
        this.title = title != null ? title : "";
        invalidate();
        return this;
    }

    public TerminalInstallWizard setSubtitle(String subtitle) {
        this.subtitle = subtitle;
        invalidate();
        return this;
    }

    public TerminalInstallWizard setShowBorder(boolean show) {
        this.showBorder = show;
        invalidate();
        return this;
    }

    public TerminalInstallWizard setShowFooter(boolean show) {
        this.showFooter = show;
        invalidate();
        return this;
    }

    public TerminalInstallWizard setShowElapsed(boolean show) {
        this.showElapsed = show;
        invalidate();
        return this;
    }

    public TerminalInstallWizard setBorderStyle(LineStyle style) {
        if (style != null) {
            this.borderStyle = style;
            brandDivider.setLineStyle(style);
            headerDivider.setLineStyle(style);
            footerDivider.setLineStyle(style);
            invalidate();
        }
        return this;
    }

    public TerminalInstallWizard setOverallProgress(float progress) {
        this.autoOverallProgress = false;
        this.overallProgress = Math.max(0f, Math.min(1f, progress));
        invalidate();
        return this;
    }

    public TerminalInstallWizard setAutoOverallProgress(boolean auto) {
        this.autoOverallProgress = auto;
        if (auto) updateOverallProgress();
        return this;
    }

    public TerminalInstallWizard setCompletionListener(CompletionListener listener) {
        this.completionListener = listener;
        return this;
    }

    // ===== STYLE SETTERS =====

    public void setStyleBorder(TextStyle s) {
        this.styleBorder = s;
        brandDivider.setLineTextStyle(s);
        headerDivider.setLineTextStyle(s);
        footerDivider.setLineTextStyle(s);
    }
    public void setStyleTitle(TextStyle s)       { this.styleTitle       = s; }
    public void setStyleOverallFill(TextStyle s) { this.styleOverallFill = s; }
    public void setStyleOverallEmpty(TextStyle s){ this.styleOverallEmpty = s; }
    public void setStyleFooter(TextStyle s)      { this.styleFooter      = s; }
    public void setStyleSuccess(TextStyle s)     { this.styleSuccess     = s; }
    public void setStyleError(TextStyle s)       { this.styleError       = s; }
    public void setOverallFillChar(char c)       { this.overallFillChar  = c; }
    public void setOverallEmptyChar(char c)      { this.overallEmptyChar = c; }

    // ===== RENDERING =====

    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        TerminalRectangle r = getRegion();
        if (r == null) return;
        int w = r.getWidth();
        int h = r.getHeight();
        if (w <= 0 || h <= 0) return;

        int row = 0;

        // ── Outer top border ──────────────────────────────────────────────────
        if (showBorder) {
            renderTopBorder(batch, w, row++);
        }

        // ── Header row: title + overall progress bar ──────────────────────────
        // (brand + brandDivider are child components rendered by the child pass)
        if (row < h) {
            renderHeader(batch, w, row++);
        }

        // ── Footer status line ────────────────────────────────────────────────
        if (showFooter && h > row + 1) {
            int footerRow = h - (showBorder ? 2 : 1);
            if (footerRow > row) {
                renderFooterContent(batch, w, footerRow);
            }
        }

        // ── Outer bottom border ───────────────────────────────────────────────
        if (showBorder && h > 1) {
            renderBottomBorder(batch, w, h - 1);
        }

        // ── Side border verticals ─────────────────────────────────────────────
        if (showBorder) {
            renderSideBorders(batch, w, h);
        }
    }

    // ----- border helpers -----

    private void renderTopBorder(TerminalBatchBuilder batch, int w, int row) {
        printAt(batch, 0,     row, String.valueOf(borderStyle.topLeft()),  styleBorder);
        drawHLine(batch, 1,   row, w - 2, borderStyle, styleBorder);
        printAt(batch, w - 1, row, String.valueOf(borderStyle.topRight()), styleBorder);
    }

    private void renderBottomBorder(TerminalBatchBuilder batch, int w, int row) {
        printAt(batch, 0,     row, String.valueOf(borderStyle.bottomLeft()),  styleBorder);
        drawHLine(batch, 1,   row, w - 2, borderStyle, styleBorder);
        printAt(batch, w - 1, row, String.valueOf(borderStyle.bottomRight()), styleBorder);
    }

    private void renderSideBorders(TerminalBatchBuilder batch, int w, int h) {
        String vert = String.valueOf(borderStyle.vertical());
        for (int y = 1; y < h - 1; y++) {
            printAt(batch, 0,     y, vert, styleBorder);
            printAt(batch, w - 1, y, vert, styleBorder);
        }
    }

    // ----- header -----

    private void renderHeader(TerminalBatchBuilder batch, int w, int row) {
        int innerX = showBorder ? 1 : 0;
        int innerW = showBorder ? w - 2 : w;

        String titleText = " ".repeat(TITLE_PADDING) + title;
        if (subtitle != null && !subtitle.isBlank()) {
            titleText += "  " + subtitle;
        }

        String pctStr    = String.format("%3d%%", (int)(overallProgress * 100f));
        int    filled    = Math.max(0, Math.min(Math.round(overallProgress * OVERALL_BAR_WIDTH), OVERALL_BAR_WIDTH));
        String barLabel  = "Overall: " + pctStr + " [";
        String fillPart  = repeat(overallFillChar, filled);
        String emptyPart = repeat(overallEmptyChar, OVERALL_BAR_WIDTH - filled) + "]";
        String rightBlock = barLabel + fillPart + emptyPart;

        int maxTitleW = innerW - rightBlock.length() - 2;
        String displayedTitle = maxTitleW > 0 ? truncate(titleText, maxTitleW) : "";
        printAt(batch, innerX, row, displayedTitle, styleTitle);

        int barX = innerX + innerW - rightBlock.length();
        if (barX > innerX + displayedTitle.length()) {
            printAt(batch, barX, row, barLabel, styleFooter);
            int bx = barX + barLabel.length();
            if (!fillPart.isEmpty()) {
                printAt(batch, bx, row, fillPart, styleOverallFill);
                bx += fillPart.length();
            }
            printAt(batch, bx, row, emptyPart, styleOverallEmpty);
        }
    }

    // ----- footer -----

    private void renderFooterContent(TerminalBatchBuilder batch, int w, int row) {
        int innerX = showBorder ? 1 : 0;
        int innerW = showBorder ? w - 2 : w;

        StringBuilder sb = new StringBuilder("  ");

        long done = steps.stream().filter(s -> s.getStatus().isTerminal()).count();
        sb.append("Step ").append(done).append(" of ").append(steps.size());

        if (showElapsed && startTimeMs > 0) {
            long secs = elapsedMs / 1000L;
            sb.append("  ·  Elapsed: ")
              .append(String.format("%02d:%02d", secs / 60, secs % 60));
        }

        if (activeStep != null) {
            String detail = activeStep.getDetail();
            if (detail != null && !detail.isBlank()) {
                sb.append("  ·  ").append(detail);
            }
        }

        if (wizardComplete) {
            boolean hasError = steps.stream().anyMatch(s -> s.getStatus() == Status.ERROR);
            sb.append("  ·  ").append(hasError ? "FAILED" : "COMPLETE");
        } else {
            sb.append("  ·  Press Ctrl+C to cancel");
        }

        TextStyle footerStyle = wizardComplete
                ? (steps.stream().anyMatch(s -> s.getStatus() == Status.ERROR)
                   ? styleError : styleSuccess)
                : styleFooter;

        printAt(batch, innerX, row, truncate(sb.toString(), innerW), footerStyle);
    }

    // ===== INTERNAL HELPERS =====

    private void updateOverallProgress() {
        if (!autoOverallProgress || steps.isEmpty()) return;
        float sum = 0f;
        for (InstallStep s : steps) {
            switch (s.getStatus()) {
                case COMPLETE:
                case SKIPPED:  sum += 1f; break;
                case RUNNING:  sum += s.getProgress(); break;
                default:       break;
            }
        }
        overallProgress = Math.min(1f, sum / steps.size());
    }

    private void checkWizardCompletion() {
        if (wizardComplete) return;
        for (InstallStep s : steps) {
            if (!s.getStatus().isTerminal()) return;
        }
        wizardComplete = true;
        if (completionListener != null) {
            InstallStep failed = steps.stream()
                    .filter(s -> s.getStatus() == Status.ERROR)
                    .findFirst().orElse(null);
            completionListener.onWizardComplete(failed == null, failed);
        }
    }

    private void refreshRow(InstallStep step) {
        TerminalInstallStepRow row = findRow(step);
        if (row != null) row.invalidate();
    }

    private InstallStep findStep(String id) {
        if (id == null) return null;
        for (InstallStep s : steps) {
            if (id.equals(s.getId())) return s;
        }
        return null;
    }

    private TerminalInstallStepRow findRow(InstallStep step) {
        for (TerminalInstallStepRow row : stepRows) {
            if (row.getStep() == step) return row;
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null || max <= 0) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String repeat(char c, int count) {
        if (count <= 0) return "";
        char[] buf = new char[count];
        java.util.Arrays.fill(buf, c);
        return new String(buf);
    }
}