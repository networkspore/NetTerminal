package io.netnotes.terminal.components.install;

import java.util.ArrayList;
import java.util.List;

import io.netnotes.debug.RenderDiagnostics;
import io.netnotes.engine.ui.LabelTruncation;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.TextAlignment;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.components.TerminalProgressBar;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.components.panels.TerminalHStack;
import io.netnotes.terminal.components.panels.TerminalVStack;
import io.netnotes.terminal.components.text.TerminalLabel;
import io.netnotes.terminal.components.install.InstallStep.Status;
import io.netnotes.terminal.layout.TerminalLayoutContext;


/**
 * TerminalInstallStepRow - Visual row for one {@link InstallStep}.
 *
 * Compact:
 *   ◉  2. Configure Database…        47% [████░░░░]
 *   ✓  1. Download Dependencies                DONE
 *
 * Expanded (detail + log lines):
 *   ◉  2. Configure Database…        47% [████░░░░]
 *      │  Applying schema migrations…
 *      │  migration_001.sql … OK
 *
 * IS a TerminalVStack:
 *   row 0: TerminalHStack (icon | name | progressBar/statusTag)
 *   row 1: detail TerminalLabel   (hidden when not expanded)
 *   rows 2-N: log TerminalLabel pool  (hidden when slots unused)
 */
public class TerminalInstallStepRow extends TerminalVStack {
    private static final long LAYOUT_LOG_SUPPRESS_NS = 100_000_000L;

    // ===== STYLE DEFAULTS =====

    private TextStyle stylePending      = TextStyle.NORMAL;
    private TextStyle styleRunning      = TextStyle.BOLD.withForeground(TextStyle.Color.CYAN);
    private TextStyle styleComplete     = TextStyle.BOLD.withForeground(TextStyle.Color.GREEN);
    private TextStyle styleError        = TextStyle.BOLD.withForeground(TextStyle.Color.RED);
    private TextStyle styleSkipped      = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleLabel        = TextStyle.NORMAL;
    private TextStyle styleLabelActive  = TextStyle.BOLD;
    private TextStyle styleDetail       = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleLog          = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleStatusTag    = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);

    // ===== STATE =====

    private InstallStep step;
    private boolean     expanded            = false;
    private int         maxVisibleLogLines  = 5;
    private int         spinnerFrame        = 0;

    // ===== CHILD COMPONENTS =====

    // Main row
    private final TerminalHStack    mainRow;
    private final TerminalLabel     iconLabel;
    private final TerminalLabel     nameLabel;
    private final TerminalProgressBar progressBar;
    private final TerminalLabel     statusTagLabel;

    // Expanded area
    private final TerminalLabel         detailLabel;
    private final List<TerminalLabel>   logLabels = new ArrayList<>();

    // ===== CONSTRUCTION =====

    public TerminalInstallStepRow(String name, InstallStep step) {
        super(name);
        if (step == null) throw new IllegalArgumentException("step must not be null");
        this.step = step;

        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FIT_CONTENT);
        // Keep the row itself non-zero during early measurement so parent fit-content
        // stacks do not collapse before child layout has stabilized.
        setMinHeight(1);
        setSpacing(0);

        // ── main row ──────────────────────────────────────────────────────────
        mainRow = new TerminalHStack(name + "-row");
        mainRow.setWidthPreference(SizePreference.FILL);
        mainRow.setHeightPreference(SizePreference.FIT_CONTENT);
        // Keep the header row non-zero during early passes so parent fit-content
        // containers always reserve at least one visible line.
        mainRow.setMinHeight(1);
        mainRow.setSpacing(0);

        // Icon: always 2 cols ("◉ ")
        iconLabel = new TerminalLabel(name + "-trm-install-icon");
        iconLabel.setWidthPreference(SizePreference.FIT_CONTENT);
        iconLabel.setMinWidth(2);
        iconLabel.setMinHeight(1);

        // Name: takes all remaining width, truncates at end
        nameLabel = new TerminalLabel(name + "-trm-install-lbl");
        nameLabel.setWidthPreference(SizePreference.FILL);
        nameLabel.setMinWidth(3);
        nameLabel.setMinHeight(1);
        nameLabel.setTextTruncation(LabelTruncation.END);

        progressBar = new TerminalProgressBar(name + "-trm-install-prog");
        progressBar.setWidthPreference(SizePreference.FIT_CONTENT);
        progressBar.setHeightPreference(SizePreference.FIT_CONTENT);
        progressBar.setMinWidth(12);
        progressBar.setMinHeight(1);
        progressBar.hide();

        // Status tag: FILL, shown when not RUNNING (mutually exclusive with progressBar,
        // which is also FILL — exactly one of the two is in the layout at a time).
        statusTagLabel = new TerminalLabel(name + "-trm-install-tag");
        statusTagLabel.setWidthPreference(SizePreference.FIT_CONTENT);
        statusTagLabel.setMinWidth(8);
        statusTagLabel.setMinHeight(1);

        statusTagLabel.setTextAlignment(TextAlignment.RIGHT);

        mainRow.addChild(iconLabel);
        mainRow.addChild(nameLabel);
        mainRow.addChild(progressBar);
        mainRow.addChild(statusTagLabel);

        // ── detail label (row 1 when expanded) ───────────────────────────────
        detailLabel = new TerminalLabel(name + "-detail");
        detailLabel.setWidthPreference(SizePreference.FILL);
        detailLabel.setHeightPreference(SizePreference.FIT_CONTENT);
        detailLabel.setMinHeight(1);
        detailLabel.setTextStyle(styleDetail);
        detailLabel.hide();

        // ── log label pool (rows 2-N when expanded) ───────────────────────────
        addChild(mainRow);
        addChild(detailLabel);
        rebuildLogPool(maxVisibleLogLines);

        syncFromStep();
    }

    // ===== POOL MANAGEMENT =====

    /**
     * Rebuild the log label pool to hold exactly {@code size} slots.
     * Called once on construction and again only when maxVisibleLogLines changes.
     * All slots start hidden; syncFromStep() shows and fills the live ones.
     */
    private void rebuildLogPool(int size) {
        // Remove existing log labels from the VStack
        for (TerminalLabel l : logLabels) {
            removeChild(l);
        }
        logLabels.clear();

        for (int i = 0; i < size; i++) {
            TerminalLabel l = new TerminalLabel(getName() + "-log" + i);
            l.setWidthPreference(SizePreference.FILL);
            l.setHeightPreference(SizePreference.FIT_CONTENT);
            l.setMinHeight(1);
            l.setTextStyle(styleLog);
            l.hide();
            logLabels.add(l);
            addChild(l);
        }
    }

    // ===== STEP SYNC =====

    /**
     * Push all current step state into the child components and mark dirty.
     * Use this instead of bare {@code invalidate()} whenever step data may have
     * changed (status, progress, detail, log lines).
     */
    public void refresh() {
        syncFromStep();
        invalidate();
    }

    /**
     * Push all current step state into the child components.
     * Package-private so {@link TerminalInstallWizard} can call it directly
     * when it needs to control whether a layout update is also requested.
     * Does NOT call requestLayoutUpdate or invalidate — callers decide.
     */
    void syncFromStep() {
        Status status = step.getStatus();

        // ── icon ─────────────────────────────────────────────────────────────
        char icon = (status == Status.RUNNING)
                ? InstallStep.SPINNER_FRAMES[spinnerFrame]
                : status.icon();
        iconLabel.setText(icon + " ");
        iconLabel.setTextStyle(iconStyle(status));

        // ── name ─────────────────────────────────────────────────────────────
        nameLabel.setText(buildStepLabelText());
        nameLabel.setTextStyle(status == Status.RUNNING ? styleLabelActive : styleLabel);

        // ── right side: progress bar XOR status tag ───────────────────────────
        boolean showProgress = status == Status.RUNNING && step.isShowProgress();
        if (showProgress) {
            progressBar.setProgress(step.getProgress());
            progressBar.show();
            statusTagLabel.hide();
        } else {
            statusTagLabel.setText(status.toString());
            statusTagLabel.setTextStyle(styleStatusTag);
            statusTagLabel.show();
            progressBar.hide();
        }

        // ── detail + log pool ────────────────────────────────────────────────
        syncExpandedArea();
    }

    private void syncExpandedArea() {
        if (!expanded) {
            detailLabel.hide();
            for (TerminalLabel l : logLabels) l.hide();
            return;
        }

        // Detail line
        String detail = step.getDetail();
        if (detail != null && !detail.isBlank()) {
            detailLabel.setText("   │  " + detail);
            detailLabel.show();
        } else {
            detailLabel.hide();
        }

        // Log pool — fill from the tail of the log list
        List<String> logs = step.getLogLines();
        int startLog = Math.max(0, logs.size() - maxVisibleLogLines);
        int slot = 0;
        for (int i = startLog; i < logs.size() && slot < logLabels.size(); i++, slot++) {
            logLabels.get(slot).setText("   │  " + logs.get(i));
            logLabels.get(slot).show();
        }
        // Hide unused slots
        for (int i = slot; i < logLabels.size(); i++) {
            logLabels.get(i).hide();
        }
    }

    // ===== PUBLIC API =====

    public InstallStep getStep() { return step; }

    public void setStep(InstallStep step) {
        if (step == null) return;
        this.step = step;
        syncFromStep();
        invalidate();
        requestLayoutUpdate();
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            syncFromStep();
            requestLayoutUpdate();
        }
    }

    public boolean isExpanded() { return expanded; }

    public void setMaxVisibleLogLines(int max) {
        int clamped = Math.max(1, max);
        if (this.maxVisibleLogLines != clamped) {
            this.maxVisibleLogLines = clamped;
            rebuildLogPool(clamped);
            if (expanded) {
                syncExpandedArea();
                requestLayoutUpdate();
            }
        }
    }

    /**
     * Advance spinner one frame. Height does not change — no layout update needed.
     */
    public void advanceSpinner() {
        spinnerFrame = (spinnerFrame + 1) % InstallStep.SPINNER_FRAMES.length;
        // Only the icon text and progress value change — no resize.
        char icon = InstallStep.SPINNER_FRAMES[spinnerFrame];
        iconLabel.setText(icon + " ");
        if (step.isShowProgress()) {
            progressBar.setProgress(step.getProgress());
        }
    }

    /**
     * Call after a log line is added so the pool updates.
     * Triggers layout only when the row is expanded (height may change).
     */
    public void onLogLineAdded() {
        if (!expanded) return;
        syncExpandedArea();
        requestLayoutUpdate();
    }

    @Override
    public void requestLayoutUpdate() {
        if (mainRow != null && detailLabel != null) {
            logRowLayoutSnapshot("requestLayoutUpdate");
        }
        super.requestLayoutUpdate();
    }

    // ===== STYLE SETTERS =====
    // Each setter updates the field AND pushes to the affected child immediately.

    public void setStylePending(TextStyle s)     {
        stylePending = s;
        if (step.getStatus() == Status.PENDING) iconLabel.setTextStyle(s);
        invalidate();
    }
    public void setStyleRunning(TextStyle s)     {
        styleRunning = s;
        if (step.getStatus() == Status.RUNNING) iconLabel.setTextStyle(s);
        invalidate();
    }
    public void setStyleComplete(TextStyle s)    {
        styleComplete = s;
        if (step.getStatus() == Status.COMPLETE) iconLabel.setTextStyle(s);
        invalidate();
    }
    public void setStyleError(TextStyle s)       {
        styleError = s;
        if (step.getStatus() == Status.ERROR) iconLabel.setTextStyle(s);
        invalidate();
    }
    public void setStyleSkipped(TextStyle s)     {
        styleSkipped = s;
        if (step.getStatus() == Status.SKIPPED) iconLabel.setTextStyle(s);
        invalidate();
    }
    public void setStyleLabel(TextStyle s)       {
        styleLabel = s;
        if (step.getStatus() != Status.RUNNING) nameLabel.setTextStyle(s);
        invalidate();
    }
    public void setStyleLabelActive(TextStyle s) {
        styleLabelActive = s;
        if (step.getStatus() == Status.RUNNING) nameLabel.setTextStyle(s);
        invalidate();
    }
    public void setStyleDetail(TextStyle s)      {
        styleDetail = s;
        detailLabel.setTextStyle(s);
        invalidate();
    }
    public void setStyleLog(TextStyle s)         {
        styleLog = s;
        for (TerminalLabel l : logLabels) l.setTextStyle(s);
        invalidate();
    }
    public void setStyleStatusTag(TextStyle s)   {
        styleStatusTag = s;
        statusTagLabel.setTextStyle(s);
        invalidate();
    }

    public void setProgressFillStyle(TextStyle s)  { progressBar.setFilledStyle(s); }
    public void setProgressEmptyStyle(TextStyle s) { progressBar.setEmptyStyle(s); }
    public void setProgressStyle(TerminalProgressBar.Style c) { progressBar.setProgressStyle(c); }

    public int getPreferredWidth() {
        return resolveMeasuredWidth();
    }

    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        TerminalRectangle measured = super.measureContent(childContexts);
        if (childContexts == null && getHeightPreference() == SizePreference.FIT_CONTENT) {
            measured.set(0, 0, measured.getWidth(), measureCurrentContentHeight());
        }
        return measured;
    }



    // ===== HELPERS =====

    int getIntrinsicContentWidth() {
        int mainWidth = iconLabel.getMinWidth()
            + buildStepLabelText().length()
            + measureRightSlotWidth();
        int expandedWidth = 0;
        if (expanded) {
            expandedWidth = Math.max(expandedWidth, prefixedWidth(step.getDetail()));
            List<String> logs = step.getLogLines();
            int startLog = Math.max(0, logs.size() - maxVisibleLogLines);
            for (int i = startLog; i < logs.size(); i++) {
                expandedWidth = Math.max(expandedWidth, prefixedWidth(logs.get(i)));
            }
        }
        return Math.max(mainWidth, expandedWidth);
    }

    int measureCurrentContentHeight() {
        int totalHeight = 0;
        int visibleCount = 0;

        for (TerminalRenderable child : getChildren()) {
            if (!shouldMeasureCurrentChild(child)) {
                continue;
            }
            visibleCount++;
            totalHeight += measureCurrentChildHeight(child);
        }

        if (visibleCount > 1) {
            totalHeight += (visibleCount - 1) * (isDrawSeparators() ? 1 : getSpacing());
        }

        return Math.max(getMinHeight(), totalHeight + getInsets().getVertical());
    }

    private int resolveMeasuredWidth() {
        return switch (getWidthPreference()) {
            case STATIC -> getRegion().getWidth();
            case FIT_CONTENT -> Math.max(
                getMinWidth(),
                getIntrinsicContentWidth() + getInsets().getHorizontal()
            );
            default -> getMinWidth();
        };
    }

    private TextStyle iconStyle(Status s) {
        return switch (s) {
            case PENDING  -> stylePending;
            case RUNNING  -> styleRunning;
            case COMPLETE -> styleComplete;
            case ERROR    -> styleError;
            case SKIPPED  -> styleSkipped;
        };
    }

    private String buildStepLabelText() {
        String expandGlyph = expanded ? "▾ " : "▸ ";
        String numStr = step.getStepNumber() > 0 ? step.getStepNumber() + ". " : "";
        return expandGlyph + numStr + step.getDisplayName();
    }

    private int measureRightSlotWidth() {
        boolean showProgress = step.getStatus() == Status.RUNNING && step.isShowProgress();
        if (showProgress) {
            return progressBar.getMinWidth();
        }

        String statusText = step.getStatus().toString();
        return Math.max(statusTagLabel.getMinWidth(), statusText.length() );
    }

    private int prefixedWidth(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return "   │  ".length() + text.length();
    }

    private boolean shouldMeasureCurrentChild(TerminalRenderable child) {
        return child != null
            && !child.isHidden();
    }

    private int measureCurrentChildHeight(TerminalRenderable child) {
        TerminalRectangle requested = child.getRequestedRegion();
        if (requested != null) {
            return requested.getHeight();
        }

        if (child instanceof TerminalRegion terminalRegion) {
            TerminalRectangle measured = terminalRegion.measureContent(null);
            int measuredHeight = measured.getHeight();
            terminalRegion.getRegionPool().recycle(measured);
            if (measuredHeight > 0) {
                return measuredHeight;
            }
        }

        return child.getRegion().getHeight();
    }

    private void logRowLayoutSnapshot(String stage) {
        boolean hasDetail = step.getDetail() != null && !step.getDetail().isBlank();
        boolean suspicious = expanded
            || hasDetail
            || !step.getLogLines().isEmpty()
            || getRegion().getHeight() <= 1
            || mainRow.getRegion().getHeight() <= 1;

        if (!suspicious) {
            return;
        }

        RenderDiagnostics.logImportant(
            "wizard-step-row-layout:" + getName() + ":" + stage,
            LAYOUT_LOG_SUPPRESS_NS,
            () -> "[WizardStepRowLayout] " + stage
                + "\n\trow=" + summarizeRowComponent(this)
                + "\n\tstatus=" + step.getStatus()
                + "\n\texpanded=" + expanded
                + "\n\thasDetail=" + hasDetail
                + "\n\tlogLines=" + step.getLogLines().size()
                + "\n\tvisibleLogLabels=" + countVisibleLogLabels() + "/" + logLabels.size()
                + "\n\tmainRow=" + summarizeRowComponent(mainRow)
                + "\n\ticonLabel=" + summarizeRowComponent(iconLabel)
                + "\n\tnameLabel=" + summarizeRowComponent(nameLabel)
                + "\n\tprogressBar=" + summarizeRowComponent(progressBar)
                + "\n\tstatusTagLabel=" + summarizeRowComponent(statusTagLabel)
                + "\n\tdetailLabel=" + summarizeRowComponent(detailLabel)
                + "\n\tlogLabelPool=" + RenderDiagnostics.summarizeRenderables(logLabels, 6)
        );
    }

    private int countVisibleLogLabels() {
        int visibleCount = 0;
        for (TerminalLabel label : logLabels) {
            if (!label.isHidden()) {
                visibleCount++;
            }
        }
        return visibleCount;
    }

    private String summarizeRowComponent(TerminalRenderable renderable) {
        return RenderDiagnostics.summarizeRenderable(renderable)
            + ", "
            + RenderDiagnostics.summarizeSizing(renderable);
    }


}
