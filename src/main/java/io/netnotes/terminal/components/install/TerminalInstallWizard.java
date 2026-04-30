package io.netnotes.terminal.components.install;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.netnotes.debug.RenderDiagnostics;
import io.netnotes.terminal.TerminalRenderable;
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
import io.netnotes.engine.ui.Orientation;
import io.netnotes.engine.ui.SizePreference;

/**
 * TerminalInstallWizard - Component-based installation progress wizard.
 *
 * <h3>Layout</h3>
 * <pre>
 *   ╔══════════════════════════════════════════════════════╗
 *   ║  Netnotes Installer v1.0          Overall: 52% [██] ║  ← TerminalWizardHeader (HStack)
 *   ╠══════════════════════════════════════════════════════╣
 *   ║  ✓  1. Download Dependencies              COMPLETE  ║
 *   ║  ◉  2. Configure Database…      47% [████░░░░]      ║
 *   ║     │  Applying schema migrations…                  ║
 *   ╠══════════════════════════════════════════════════════╣
 *   ║  Step 2 of 4  ·  Elapsed: 00:12  ·  Migrating…     ║  ← TerminalWizardFooter (HStack)
 *   ╚══════════════════════════════════════════════════════╝
 * </pre>
 *
 * <h3>Thread safety</h3>
 * All public mutating methods end with {@link #invalidate()} so they are safe
 * to call from non-render threads. Do <em>not</em> call them from inside a
 * render callback.
 */
public class TerminalInstallWizard extends TerminalVStack {

    /**
     * Set to {@code true} locally when diagnosing layout issues.
     * Kept {@code false} in production to avoid string allocations on every
     * layout pass.  When false, {@code requestLayoutUpdate()} is a no-op
     * override and the snapshot helper is never called.
     */
    private static final boolean DEBUG_LAYOUT = false;

    private static final long LAYOUT_LOG_SUPPRESS_NS = 150_000_000L;


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

    /**
     * Callback fired when a step state transitions.
     */
    @FunctionalInterface
    public interface StepStateListener {
        /**
         * @param step       the step whose state changed
         * @param oldStatus  the previous status, or null for new steps
         * @param newStatus  the new status
         */
        void onStepStateChanged(InstallStep step, Status oldStatus, Status newStatus);
    }

    // ===== STATE =====

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
    private StepStateListener stepStateListener = null;
    private boolean wizardComplete = false;

    // Footer state cache — avoids redundant invalidate() when nothing changed
    private int    completedStepsCount = -1;
    private long   displayedElapsedMs  = -1;
    private String displayedDetail     = null;
    private boolean hadError           = false;

    // ── Child components ─────────────────────────────────────────────────────

    private final TerminalWizardHeader headerComponent;
    private final TerminalWizardFooter footerComponent;
    private final TerminalDivider      brandDivider;
    private final TerminalBitmapView   brandView;
    private final TerminalDivider      headerDivider;
    private final TerminalDivider      footerDivider;
    private final TerminalVStack       stepListStack;
    private final TerminalRegion       contentSpacer;


    // ===== CONSTRUCTION =====

    protected TerminalInstallWizard(Builder builder) {
        super(builder.name);

        headerComponent = new TerminalWizardHeader(name + "-termWizardHeader");
        footerComponent = new TerminalWizardFooter(name + "-termWizardFooter");
        brandView       = new TerminalBitmapView(name + "-term-install-wizard-brand");
        brandDivider    = new TerminalDivider(name + "-term-install-wizard-bdiv", Orientation.HORIZONTAL);
        headerDivider   = new TerminalDivider(name + "-term-install-wizard-hdiv", Orientation.HORIZONTAL);
        footerDivider   = new TerminalDivider(name + "-fdiv", Orientation.HORIZONTAL);
        contentSpacer   = new TerminalRegion(name + "-term-install-wizard-spacer");
        stepListStack   = new TerminalVStack(name + "-term-install-wizard-steps");

        this.autoOverallProgress = builder.autoOverallProgress;
        this.completionListener  = builder.completionListener;

        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FILL);
        setDrawBorder(builder.showBorder);
        setBorderStyle(builder.borderLineStyle);
        setBorderTextStyle(builder.borderTextStyle);
        setDrawSeparators(builder.borderSeparators);

        buildLayout(builder);
    }


    // ===== LAYOUT CONSTRUCTION =====

    private void buildLayout(Builder builder) {

        // ── brand panel ───────────────────────────────────────────────────────
        brandView.setWidthPreference(SizePreference.FILL);
        brandView.setHeightPreference(SizePreference.FIT_CONTENT);
        brandView.setRenderMode(RenderMode.SEXTANT);
        brandView.setScaleMode(ScaleMode.FIT);
        brandView.setBilinear(true);
        brandView.hide();

        brandDivider.setLineStyle(builder.borderLineStyle);
        brandDivider.setLineTextStyle(builder.borderTextStyle);
        brandDivider.hide();

        // ── header ────────────────────────────────────────────────────────────
        headerComponent.setTitle(builder.title);
        headerComponent.setSubtitle(builder.subtitle);
        headerComponent.setStyleTitle(builder.styleTitle);
        headerComponent.setStyleBarFill(builder.styleOverallFill);
        headerComponent.setStyleBarEmpty(builder.styleOverallEmpty);
        headerComponent.setStyleBarLabel(builder.styleFooter);
        headerComponent.setFillChar(builder.overallFillChar);
        headerComponent.setEmptyChar(builder.overallEmptyChar);
        headerComponent.setOverallBarWidth(builder.overallBarWidth);

        headerDivider.setLineStyle(builder.borderLineStyle);
        headerDivider.setLineTextStyle(builder.borderTextStyle);

        // ── step list ─────────────────────────────────────────────────────────
        stepListStack.setSpacing(0);
        stepListStack.setVAlignment(VAlignment.TOP);
        stepListStack.setWidthPreference(SizePreference.FILL);
        stepListStack.setHeightPreference(SizePreference.FIT_CONTENT);

        // ── content spacer — pushes footer to the bottom ─────────────────────
        contentSpacer.setWidthPreference(SizePreference.FILL);
        contentSpacer.setHeightPreference(SizePreference.FILL);

        // ── footer ────────────────────────────────────────────────────────────
        footerDivider.setLineStyle(builder.borderLineStyle);
        footerDivider.setLineTextStyle(builder.borderTextStyle);

        footerComponent.setShowElapsed(builder.showElapsed);
        footerComponent.setStyleNormal(builder.styleFooter);
        footerComponent.setStyleSuccess(builder.styleSuccess);
        footerComponent.setStyleError(builder.styleError);

        if (!builder.showFooter) {
            footerDivider.hide();
            footerComponent.hide();
        }

        // ── wire up (directly to self — no rootStack) ─────────────────────────
        addChild(brandView);
        addChild(brandDivider);
        addChild(headerComponent);
        addChild(headerDivider);
        addChild(stepListStack);
        addChild(contentSpacer);
        addChild(footerDivider);
        addChild(footerComponent);
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
        appendStepRow(step);   // no layout request inside
        updateOverallProgress();
        syncFooter();
        requestLayoutUpdate(); // single layout request
        invalidate();
    }

    /**
     * Add multiple steps at once, then rebuild layout in one pass.
     * Prefer this over repeated {@link #addStep} calls when pre-loading a step list.
     */
    public void addSteps(List<InstallStep> newSteps) {
        if (newSteps == null || newSteps.isEmpty()) return;
        for (InstallStep s : newSteps) {
            s.setStepNumber(steps.size() + 1);
            steps.add(s);
            appendStepRow(s);  // no layout request inside
        }
        updateOverallProgress();
        syncFooter();
        requestLayoutUpdate(); // single layout request for the whole batch
        invalidate();
    }

    /**
     * Update step status with listener notification.
     *
     * @param step      the step to update
     * @param newStatus the new status to set
     * @return true if the status actually changed
     */
    private boolean updateStepStatus(InstallStep step, Status newStatus) {
        Status oldStatus = step.getStatus();
        if (oldStatus == newStatus) {
            return false;
        }

        step.setStatus(newStatus);
        if (stepStateListener != null) {
            stepStateListener.onStepStateChanged(step, oldStatus, newStatus);
        }

        // Reset footer cache whenever step state changes
        completedStepsCount = -1;
        displayedElapsedMs  = -1;
        displayedDetail     = null;
        hadError            = false;
        return true;
    }

    /** Update visual-only progress during animation without invalidating layout. */
    public void updateProgressVisualOnly(String stepId, float progress) {
        InstallStep step = findStep(stepId);
        if (step == null) return;

        if (step.getStatus() == Status.RUNNING && step.getProgress() != progress) {
            step.setProgress(progress);

            // Push the new value straight to the header bar — no layout needed
            if (headerComponent != null) {
                headerComponent.setOverallProgress(overallProgress);
                headerComponent.invalidate();
            }
        }
    }

    /** Update overall progress only if actual change occurred. */
    private void updateProgress(String stepId, float progress, String detail, boolean force) {
        InstallStep step = findStep(stepId);
        if (step == null) return;

        boolean detailChanged   = detail != null && !Objects.equals(step.getDetail(), detail);
        boolean progressChanged = step.getProgress() != progress;

        step.setProgress(progress);
        if (detail != null) step.setDetail(detail);

        if (force || progressChanged || detailChanged) {
            refreshRow(step, detailChanged);
            updateOverallProgress();
            invalidate();
        }
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
        elapsedMs       = 0L;
        headerComponent.setOverallProgress(overallProgress);
        syncFooter();
        requestLayoutUpdate();
        invalidate();
    }

    /** Rebuild step-row components from the current step list. */
    public void rebuildStepRows() {
        rebuildStepRowsInternal();
        requestLayoutUpdate();
        invalidate();
    }

    private void rebuildStepRowsInternal() {
        stepRows.clear();
        stepListStack.clearChildren();
        for (InstallStep s : steps) {
            appendStepRow(s);
        }
    }

    /**
     * Appends a single row for {@code step} to the step list stack.
     * Does NOT call {@code requestLayoutUpdate()} — the caller is responsible
     * for issuing exactly one layout request after all structural changes are done.
     */
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
            updateStepStatus(activeStep, Status.COMPLETE);
            activeStep.setProgress(1f);
            refreshRow(activeStep);
        }

        updateStepStatus(step, Status.RUNNING);
        activeStep = step;
        refreshRow(step);
        updateOverallProgress();
        syncFooter();
        invalidate();
    }

    /** Update progress (0.0–1.0) and optional detail text for the given step. */
    public void updateProgress(String stepId, float progress, String detail) {
        updateProgress(stepId, progress, detail, false);
    }

    /** Force a full redraw of the given step's row. */
    public void forceRefreshStep(String stepId) {
        updateProgress(stepId, findStep(stepId) != null ? findStep(stepId).getProgress() : 0f,
                       null, true);
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
        updateStepStatus(step, Status.COMPLETE);
        step.setProgress(1f);
        if (activeStep == step) activeStep = null;
        refreshRow(step);
        updateOverallProgress();
        checkWizardCompletion();
        syncFooter();
        invalidate();
    }

    /** Mark a step as failed with an optional error message. */
    public void failStep(String stepId, String errorMessage) {
        InstallStep step = findStep(stepId);
        if (step == null) return;
        boolean detailChanged = errorMessage != null
                && !java.util.Objects.equals(step.getDetail(), errorMessage);
        updateStepStatus(step, Status.ERROR);
        step.setErrorMessage(errorMessage);
        if (errorMessage != null) step.setDetail(errorMessage);
        if (activeStep == step) activeStep = null;
        refreshRow(step, detailChanged);
        updateOverallProgress();
        checkWizardCompletion();
        syncFooter();
        invalidate();
    }

    /** Mark a step as skipped (e.g. optional feature not selected). */
    public void skipStep(String stepId) {
        InstallStep step = findStep(stepId);
        if (step == null) return;
        updateStepStatus(step, Status.SKIPPED);
        if (activeStep == step) activeStep = null;
        refreshRow(step);
        updateOverallProgress();
        checkWizardCompletion();
        syncFooter();
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
        headerComponent.setOverallProgress(overallProgress);

        // Clear footer state cache
        completedStepsCount = -1;
        displayedElapsedMs  = -1;
        displayedDetail     = null;
        hadError            = false;

        for (TerminalInstallStepRow row : stepRows) {
            row.setExpandedQuiet(false);  // quiet — we issue one layout update below
            row.refresh();               // pushes reset state (PENDING icon/text) into labels
        }
        syncFooter();
        requestLayoutUpdate();
        invalidate();
    }

    // ===== SPINNER TICK =====

    /**
     * Advance all running step spinners and update elapsed time.
     * Call from a periodic timer (e.g. every 100–150 ms).
     */
    public void tick() {
        boolean needsInvalidate = false;

        // Update elapsed time
        if (startTimeMs > 0 && !wizardComplete) {
            long nextElapsedMs = System.currentTimeMillis() - startTimeMs;
            long previousDisplayedSeconds = elapsedMs / 1000L;
            long nextDisplayedSeconds = nextElapsedMs / 1000L;
            if (nextDisplayedSeconds != previousDisplayedSeconds) {
                elapsedMs = nextElapsedMs;
                needsInvalidate = true;
            }
        }

        // Advance spinners only for running steps
        int rowsWithSpinners = 0;
        for (TerminalInstallStepRow row : stepRows) {
            if (row.getStep().getStatus() == Status.RUNNING) {
                row.advanceSpinner();
                rowsWithSpinners++;
            }
        }
        needsInvalidate |= (rowsWithSpinners > 0);

        // Update footer if needed
        boolean footerChanged = syncFooter();
        needsInvalidate |= footerChanged;

        if (needsInvalidate) {
            invalidate();
        }
    }

    private boolean syncFooter() {
        if (footerComponent == null) return false;

        String detail = activeStep != null ? activeStep.getDetail() : null;
        int done = 0;
        boolean hasErr = false;

        for (InstallStep step : steps) {
            Status status = step.getStatus();
            if (status.isTerminal()) done++;
            if (status == Status.ERROR) hasErr = true;
        }

        // Check cache — skip update if nothing changed
        boolean changed = (completedStepsCount != done
                        || displayedElapsedMs  != elapsedMs
                        || !Objects.equals(displayedDetail, detail)
                        || hadError            != hasErr);

        completedStepsCount = done;
        displayedElapsedMs  = elapsedMs;
        displayedDetail     = detail;
        hadError            = hasErr;

        footerComponent.update(done, steps.size(), elapsedMs, detail, wizardComplete, hasErr);
        return changed;
    }

    // ===== EXPAND / COLLAPSE =====

    /** Expand or collapse the detail area for a single step row. */
    public void setStepExpanded(String stepId, boolean expanded) {
        InstallStep step = findStep(stepId);
        if (step == null) return;
        TerminalInstallStepRow row = findRow(step);
        if (row != null) row.setExpanded(expanded); // single row → one layout request is fine
    }

    /**
     * Collapse all step rows in a single layout pass.
     * Uses {@link TerminalInstallStepRow#setExpandedQuiet} so only one
     * {@code requestLayoutUpdate()} is issued for the entire batch.
     */
    public void collapseAll() {
        boolean changed = false;
        for (TerminalInstallStepRow row : stepRows) {
            if (row.isExpanded()) {
                row.setExpandedQuiet(false);
                changed = true;
            }
        }
        if (changed) requestLayoutUpdate();
    }

    /**
     * Expand only the currently running rows, collapse the rest — single layout pass.
     * Uses {@link TerminalInstallStepRow#setExpandedQuiet} so only one
     * {@code requestLayoutUpdate()} is issued for the entire batch.
     */
    public void expandActive() {
        boolean changed = false;
        for (TerminalInstallStepRow row : stepRows) {
            boolean shouldExpand = row.getStep().getStatus() == Status.RUNNING;
            if (row.isExpanded() != shouldExpand) {
                row.setExpandedQuiet(shouldExpand);
                changed = true;
            }
        }
        if (changed) requestLayoutUpdate();
    }

    // ===== CONFIGURATION =====

    public TerminalInstallWizard withTitle(String title) {
        title = title != null ? title : "";
        if (headerComponent != null) headerComponent.setTitle(title);
        requestLayoutUpdate();
        invalidate();
        return this;
    }

    public String getTitle() {
        return headerComponent.getTitle();
    }

    public TerminalWizardHeader getTerminalWizardHeader() {
        return headerComponent;
    }

    public TerminalInstallWizard withSubtitle(String subtitle) {
        headerComponent.setSubtitle(subtitle);
        return this;
    }

    public TerminalInstallWizard withBorder(boolean show) {
        this.setDrawBorder(show);
        return this;
    }

    public TerminalInstallWizard withShowFooter(boolean show) {
        if (footerDivider   != null) { if (show) footerDivider.show();   else footerDivider.hide(); }
        if (footerComponent != null) { if (show) footerComponent.show(); else footerComponent.hide(); }
        requestLayoutUpdate();
        return this;
    }

    public boolean isShowFooter() {
        return !footerDivider.isHidden();
    }

    public TerminalInstallWizard withShowElapsed(boolean show) {
        if (footerComponent != null) footerComponent.setShowElapsed(show);
        return this;
    }

    public TerminalInstallWizard withBorderStyle(LineStyle style) {
        if (style != null) setBorderStyle(style);
        return this;
    }

    public TerminalInstallWizard withOverallProgress(float progress) {
        this.autoOverallProgress = false;
        this.overallProgress = Math.max(0f, Math.min(1f, progress));
        invalidate();
        return this;
    }

    public TerminalInstallWizard withAutoOverallProgress(boolean auto) {
        this.autoOverallProgress = auto;
        if (auto) updateOverallProgress();
        invalidate();
        return this;
    }

    public TerminalInstallWizard withCompletionListener(CompletionListener listener) {
        this.completionListener = listener;
        return this;
    }

    public TerminalInstallWizard withStepStateListener(StepStateListener listener) {
        this.stepStateListener = listener;
        return this;
    }

    // ===== STYLE SETTERS =====

    @Override
    public void setBorderStyle(LineStyle style) {
        if (style != null) {
            brandDivider.setLineStyle(style);
            headerDivider.setLineStyle(style);
            footerDivider.setLineStyle(style);
            super.setBorderStyle(style);
        }
    }

    @Override
    public void setBorderTextStyle(TextStyle s) {
        brandDivider.setLineTextStyle(s);
        headerDivider.setLineTextStyle(s);
        footerDivider.setLineTextStyle(s);
        super.setBorderTextStyle(s);
    }

    public void setStyleTitle(TextStyle s)        { headerComponent.setStyleTitle(s); }
    public void setStyleOverallFill(TextStyle s)  { headerComponent.setStyleBarFill(s); }
    public void setStyleOverallEmpty(TextStyle s) { headerComponent.setStyleBarEmpty(s); }
    public void setStyleFooter(TextStyle s)        { footerComponent.setStyleNormal(s); }
    public void setStyleSuccess(TextStyle s)       { footerComponent.setStyleSuccess(s); }
    public void setStyleError(TextStyle s)         { footerComponent.setStyleError(s); }
    public void setOverallFillChar(char c)         { headerComponent.setFillChar(c); }
    public void setOverallEmptyChar(char c)        { headerComponent.setEmptyChar(c); }

    // ===== LAYOUT UPDATE OVERRIDE =====

    /**
     * Overridden to gate debug logging. When {@link #DEBUG_LAYOUT} is false
     * this is a transparent pass-through to the super implementation with no
     * extra overhead.
     */
    @Override
    public void requestLayoutUpdate() {
        if (DEBUG_LAYOUT) {
            logWizardLayoutSnapshot("requestLayoutUpdate");
        }
        super.requestLayoutUpdate();
    }

    // ===== INTERNAL HELPERS =====

    private void updateOverallProgress() {
        if (!autoOverallProgress || steps.isEmpty()) return;
        float sum = 0f;
        for (InstallStep s : steps) {
            switch (s.getStatus()) {
                case COMPLETE: case SKIPPED: sum += 1f; break;
                case RUNNING:  sum += s.getProgress(); break;
                default: break;
            }
        }
        overallProgress = Math.min(1f, sum / steps.size());
        if (headerComponent != null) headerComponent.setOverallProgress(overallProgress);
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
        refreshRow(step, false);
    }

    private void refreshRow(InstallStep step, boolean layoutMayHaveChanged) {
        TerminalInstallStepRow row = findRow(step);
        if (row != null) {
            row.syncFromStep();
            if (layoutMayHaveChanged && row.isExpanded()) {
                row.requestLayoutUpdate();
                requestLayoutUpdate();
            }
            row.invalidate();
        }
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

    // ===== DEBUG LOGGING =====
    // All methods in this section are gated on DEBUG_LAYOUT.

    private void logWizardLayoutSnapshot(String stage) {
        RenderDiagnostics.logImportant(
            "wizard-layout:" + getName() + ":" + stage,
            LAYOUT_LOG_SUPPRESS_NS,
            () -> "[WizardLayout] " + stage
                + "\n\twizard=" + summarizeWizardComponent(this)
                + "\n\tsteps=" + steps.size()
                + "\n\trows=" + stepRows.size()
                + "\n\texpandedRows=" + countExpandedRows()
                + "\n\tbrandView=" + summarizeWizardComponent(brandView)
                + "\n\tbrandDivider=" + summarizeWizardComponent(brandDivider)
                + "\n\theader=" + summarizeWizardComponent(headerComponent)
                + "\n\theaderDivider=" + summarizeWizardComponent(headerDivider)
                + "\n\tstepListStack=" + summarizeWizardComponent(stepListStack)
                + "\n\tcontentSpacer=" + summarizeWizardComponent(contentSpacer)
                + "\n\tfooterDivider=" + summarizeWizardComponent(footerDivider)
                + "\n\tfooter=" + summarizeWizardComponent(footerComponent)
                + "\n\tstepRows=" + RenderDiagnostics.summarizeRenderables(stepRows, 6)
        );
    }

    private int countExpandedRows() {
        int count = 0;
        for (TerminalInstallStepRow row : stepRows) {
            if (row.isExpanded()) count++;
        }
        return count;
    }

    private String summarizeWizardComponent(TerminalRenderable renderable) {
        return RenderDiagnostics.summarizeRenderable(renderable)
            + ", "
            + RenderDiagnostics.summarizeSizing(renderable);
    }

    // ===== BUILDER =====

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {

        // required
        private final String name;

        // content (mutable post-construction, but sensible defaults here)
        private String title    = "Installation Wizard";
        private String subtitle = null;

        // structural — effectively final after construction
        private boolean showBorder          = true;
        private boolean showFooter          = true;
        private boolean showElapsed         = true;
        private boolean borderSeparators    = false;
        private boolean autoOverallProgress = true;
        private int     overallBarWidth     = 20;
        private char    overallFillChar     = '█';
        private char    overallEmptyChar    = '░';
        private LineStyle borderLineStyle   = LineStyle.DOUBLE;

        // styles — effectively final after construction
        private TextStyle borderTextStyle   = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
        private TextStyle styleTitle        = TextStyle.BOLD;
        private TextStyle styleOverallFill  = TextStyle.BOLD.withForeground(TextStyle.Color.CYAN);
        private TextStyle styleOverallEmpty = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
        private TextStyle styleFooter       = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
        private TextStyle styleSuccess      = TextStyle.BOLD.withForeground(TextStyle.Color.GREEN);
        private TextStyle styleError        = TextStyle.BOLD.withForeground(TextStyle.Color.RED);

        // callbacks
        private CompletionListener completionListener = null;

        public Builder(String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
        }

        // ── structural ────────────────────────────────────────────────────────

        public Builder title(String title) {
            this.title = title != null ? title : "";
            return this;
        }

        public Builder subtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        public Builder showBorder(boolean show) {
            this.showBorder = show;
            return this;
        }

        public Builder borderSeparators(boolean show) {
            this.borderSeparators = show;
            return this;
        }

        public Builder showFooter(boolean show) {
            this.showFooter = show;
            return this;
        }

        public Builder showElapsed(boolean show) {
            this.showElapsed = show;
            return this;
        }

        public Builder autoOverallProgress(boolean auto) {
            this.autoOverallProgress = auto;
            return this;
        }

        public Builder overallBarWidth(int width) {
            this.overallBarWidth = Math.max(4, width);
            return this;
        }

        public Builder overallFillChar(char c) {
            this.overallFillChar = c;
            return this;
        }

        public Builder overallEmptyChar(char c) {
            this.overallEmptyChar = c;
            return this;
        }

        public Builder borderStyle(LineStyle style) {
            this.borderLineStyle = Objects.requireNonNull(style, "borderStyle must not be null");
            return this;
        }

        // ── styles ────────────────────────────────────────────────────────────

        public Builder styleBorder(TextStyle s) {
            this.borderTextStyle = Objects.requireNonNull(s);
            return this;
        }

        public Builder styleTitle(TextStyle s) {
            this.styleTitle = Objects.requireNonNull(s);
            return this;
        }

        public Builder styleOverallFill(TextStyle s) {
            this.styleOverallFill = Objects.requireNonNull(s);
            return this;
        }

        public Builder styleOverallEmpty(TextStyle s) {
            this.styleOverallEmpty = Objects.requireNonNull(s);
            return this;
        }

        public Builder styleFooter(TextStyle s) {
            this.styleFooter = Objects.requireNonNull(s);
            return this;
        }

        public Builder styleSuccess(TextStyle s) {
            this.styleSuccess = Objects.requireNonNull(s);
            return this;
        }

        public Builder styleError(TextStyle s) {
            this.styleError = Objects.requireNonNull(s);
            return this;
        }

        /**
         * Convenience: apply one {@code base} and one {@code accent} style
         * to all text-style fields simultaneously.
         * <p>
         * Equivalent to calling all eight individual style setters; useful for
         * quickly switching between light/dark or monochrome themes.
         */
        public Builder uniformStyle(TextStyle base, TextStyle accent) {
            this.borderTextStyle   = base;
            this.styleTitle        = accent;
            this.styleOverallFill  = accent;
            this.styleOverallEmpty = base;
            this.styleFooter       = base;
            this.styleSuccess      = accent;
            this.styleError        = accent;
            return this;
        }

        /**
         * Convenience factory: apply a two-tone theme to a builder instance.
         * Useful when the builder is constructed elsewhere and passed in.
         *
         * <pre>
         *   Builder.styleFrom(wizard.toBuilder(), TextStyle.NORMAL, TextStyle.BOLD)
         *          .build();
         * </pre>
         */
        public static Builder styleFrom(Builder b, TextStyle normal, TextStyle accent) {
            return b.styleBorder(normal)
                    .styleTitle(accent)
                    .styleOverallFill(accent)
                    .styleOverallEmpty(normal)
                    .styleFooter(normal)
                    .styleSuccess(accent)
                    .styleError(accent);
        }

        // ── callback ─────────────────────────────────────────────────────────

        public Builder onComplete(CompletionListener listener) {
            this.completionListener = listener;
            return this;
        }

        // ── terminal ─────────────────────────────────────────────────────────

        public TerminalInstallWizard build() {
            return new TerminalInstallWizard(this);
        }
    }
}