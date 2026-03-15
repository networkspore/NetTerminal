package io.netnotes.terminal.components.install;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.components.install.InstallStep.Status;

/**
 * TerminalInstallStepRow - Visual row component for one {@link InstallStep}
 *
 * <p>Renders a compact, single-row (or optionally expanded) representation of
 * a step inside a {@link TerminalInstallWizard}:
 *
 * <pre>
 *   Compact (showProgress = false):
 *   ✓  1. Download Dependencies          COMPLETE
 *   ◉  2. Configure Database…        [████░░░░░]  47%
 *   ○  3. Start Services                 PENDING
 *   ✗  4. Verify Signatures              ERROR
 *
 *   Expanded (showDetail = true, extra log lines shown):
 *   ◉  2. Configure Database…        [████░░░░░]  47%
 *      │  Applying schema migrations…
 *      │  migration_001.sql … OK
 *      │  migration_002.sql … OK
 * </pre>
 *
 * SIZING:
 * <ul>
 *   <li>Width:  FILL (takes full parent width)
 *   <li>Height: FIT_CONTENT — {@link #getPreferredHeight()} returns the exact
 *               number of rows needed (1 compact, or 1 + detail + log lines when
 *               expanded). The layout system reads this via {@code isSizedByContent()}.
 * </ul>
 *
 * The component triggers a layout update whenever expansion state or log-line
 * count changes, so the parent {@code TerminalVStack} re-measures automatically.
 */
public class TerminalInstallStepRow extends TerminalRegion {

    // ===== STYLE DEFAULTS — override via setters =====

    private TextStyle stylePending     = TextStyle.NORMAL;
    private TextStyle styleRunning     = TextStyle.BOLD.withForeground(TextStyle.Color.CYAN);
    private TextStyle styleComplete    = TextStyle.BOLD.withForeground(TextStyle.Color.GREEN);
    private TextStyle styleError       = TextStyle.BOLD.withForeground(TextStyle.Color.RED);
    private TextStyle styleSkipped     = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);

    private TextStyle styleLabel       = TextStyle.NORMAL;
    private TextStyle styleLabelActive = TextStyle.BOLD;
    private TextStyle styleDetail      = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleLog         = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleStatusTag   = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);

    private TextStyle styleProgressFill  = TextStyle.BOLD.withForeground(TextStyle.Color.CYAN);
    private TextStyle styleProgressEmpty = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private char fillChar  = '█';
    private char emptyChar = '░';

    // Layout constants
    private static final int ICON_COL         = 0;
    private static final int ICON_WIDTH        = 2;   // icon + space
    private static final int NUMBER_MAX_WIDTH  = 4;   // "99. " max
    private static final int STATUS_TAG_WIDTH  = 10;
    private static final int PROGRESS_WIDTH    = 12;
    private static final int INDENT_WIDTH      = ICON_WIDTH + NUMBER_MAX_WIDTH;
    private static final int LOG_BRANCH_CHAR_W = 3;   // "│  "

    // ===== STATE =====

    private InstallStep step;
    private boolean     expanded           = false;
    private int         maxVisibleLogLines  = 5;
    private int         spinnerFrame        = 0;

    // ===== CONSTRUCTION =====

    public TerminalInstallStepRow(String name, InstallStep step) {
        super(name);
        if (step == null) throw new IllegalArgumentException("step must not be null");
        this.step = step;
        // Width always fills the parent; height is driven by our content
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FIT_CONTENT);
        setMinHeight(1);
    }

    // ===== SIZING =====

    /**
     * Returns the exact number of rows this row needs given its current state.
     * The layout system calls this because heightPreference == FIT_CONTENT.
     */
    @Override
    public int getPreferredHeight() {
        return computeNeededHeight();
    }

    private int computeNeededHeight() {
        int h = 1; // always 1 for the main row
        if (!expanded) return h;
        if (step.getDetail() != null && !step.getDetail().isBlank()) h++;
        h += Math.min(step.getLogLines().size(), maxVisibleLogLines);
        return Math.max(1, h);
    }

    // ===== STEP BINDING =====

    public InstallStep getStep() { return step; }

    public void setStep(InstallStep step) {
        if (step == null) return;
        this.step = step;
        requestLayoutUpdate();
        invalidate();
    }

    // ===== CONFIGURATION =====

    public void setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            // Preferred height changed — tell the layout system
            requestLayoutUpdate();
            invalidate();
        }
    }

    public boolean isExpanded() { return expanded; }

    public void setMaxVisibleLogLines(int max) {
        int clamped = Math.max(1, max);
        if (this.maxVisibleLogLines != clamped) {
            this.maxVisibleLogLines = clamped;
            if (expanded) {
                requestLayoutUpdate();
                invalidate();
            }
        }
    }

    // ===== SPINNER ADVANCE =====

    /**
     * Advance the spinner by one frame and trigger a repaint.
     * Height does not change, so no layout update needed.
     */
    public void advanceSpinner() {
        spinnerFrame = (spinnerFrame + 1) % InstallStep.SPINNER_FRAMES.length;
        invalidate();
    }

    /**
     * Call after adding a new log line so the row can grow if needed.
     * (The wizard's {@code logLine()} method should call this.)
     */
    public void onLogLineAdded() {
        if (expanded) {
            requestLayoutUpdate();
            invalidate();
        }
    }

    // ===== STYLE SETTERS =====

    public void setStylePending(TextStyle s)     { this.stylePending     = s; }
    public void setStyleRunning(TextStyle s)     { this.styleRunning     = s; }
    public void setStyleComplete(TextStyle s)    { this.styleComplete    = s; }
    public void setStyleError(TextStyle s)       { this.styleError       = s; }
    public void setStyleSkipped(TextStyle s)     { this.styleSkipped     = s; }
    public void setStyleLabel(TextStyle s)       { this.styleLabel       = s; }
    public void setStyleLabelActive(TextStyle s) { this.styleLabelActive = s; }
    public void setStyleDetail(TextStyle s)      { this.styleDetail      = s; }
    public void setStyleLog(TextStyle s)         { this.styleLog         = s; }
    public void setFillChar(char c)              { this.fillChar  = c; }
    public void setEmptyChar(char c)             { this.emptyChar = c; }

    // ===== RENDERING =====

    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        TerminalRectangle r = getRegion();
        if (r == null || r.getWidth() <= 0) return;

        int w = r.getWidth();
        int h = r.getHeight();

        // ── Row 0: icon + number + name + [progress|status-tag] ──
        renderMainRow(batch, 0, w);

        if (!expanded || h <= 1) return;

        // ── Extra rows: detail line then log lines ──
        int row = 1;
        String detail = step.getDetail();
        if (detail != null && !detail.isBlank() && row < h) {
            renderIndentedLine(batch, row++, detail, styleDetail, w);
        }

        java.util.List<String> logs = step.getLogLines();
        int startLog = Math.max(0, logs.size() - maxVisibleLogLines);
        for (int i = startLog; i < logs.size() && row < h; i++, row++) {
            renderIndentedLine(batch, row, logs.get(i), styleLog, w);
        }
    }

    private void renderMainRow(TerminalBatchBuilder batch, int row, int w) {
        Status status = step.getStatus();

        // 1 ── Status icon (animated spinner when RUNNING)
        char icon = (status == Status.RUNNING)
                ? InstallStep.SPINNER_FRAMES[spinnerFrame]
                : status.icon();
        printAt(batch, ICON_COL, row, String.valueOf(icon), iconStyle(status));

        // 2 ── Step number + display name
        int numX      = ICON_COL + ICON_WIDTH;
        String numStr  = step.getStepNumber() > 0 ? step.getStepNumber() + ". " : "";
        String nameStr = numStr + step.getDisplayName();
        TextStyle labelStyle = (status == Status.RUNNING) ? styleLabelActive : styleLabel;

        int rightReserve  = computeRightReserve(status);
        int maxNameWidth  = Math.max(1, w - numX - rightReserve - 1);
        String displayedName = padRight(truncate(nameStr, maxNameWidth), maxNameWidth);
        printAt(batch, numX, row, displayedName, labelStyle);

        // 3 ── Right side: progress bar or status tag
        int rightX = numX + maxNameWidth + 1;
        if (rightX < w) {
            if (status == Status.RUNNING && step.isShowProgress()) {
                renderProgressBar(batch, row, rightX, w);
            } else {
                renderStatusTag(batch, row, rightX, w, status);
            }
        }
    }

    private void renderProgressBar(TerminalBatchBuilder batch, int row, int x, int totalW) {
        int available = totalW - x;
        if (available < 5) return;

        int pct       = (int)(step.getProgress() * 100f);
        String pctStr = String.format("%3d%%", pct);
        int barAvail  = available - pctStr.length() - 3; // " [" + "]"
        if (barAvail < 1) {
            printAt(batch, x, row, pctStr, styleProgressFill);
            return;
        }

        int filled = Math.max(0, Math.min(Math.round(step.getProgress() * barAvail), barAvail));

        printAt(batch, x, row, pctStr + " ", styleProgressFill);
        int barX = x + pctStr.length() + 1;

        String openFill = "[" + repeat(fillChar, filled);
        printAt(batch, barX, row, openFill, styleProgressFill);

        int emptyX = barX + openFill.length();
        printAt(batch, emptyX, row, repeat(emptyChar, barAvail - filled) + "]", styleProgressEmpty);
    }

    private void renderStatusTag(TerminalBatchBuilder batch, int row, int x, int totalW, Status status) {
        String tag    = statusTag(status);
        int available = totalW - x;
        if (available < 1) return;
        String paddedTag = repeat(' ', Math.max(0, available - tag.length())) + tag;
        printAt(batch, x, row, truncate(paddedTag, available), styleStatusTag);
    }

    private void renderIndentedLine(TerminalBatchBuilder batch, int row, String text, TextStyle style, int w) {
        printAt(batch, INDENT_WIDTH, row, "│  ", styleDetail);
        int indent = INDENT_WIDTH + LOG_BRANCH_CHAR_W;
        int maxLen = Math.max(0, w - indent);
        if (maxLen > 0 && text != null && !text.isBlank()) {
            printAt(batch, indent, row, truncate(text.stripLeading(), maxLen), style);
        }
    }

    // ===== HELPERS =====

    private TextStyle iconStyle(Status s) {
        switch (s) {
            case PENDING:  return stylePending;
            case RUNNING:  return styleRunning;
            case COMPLETE: return styleComplete;
            case ERROR:    return styleError;
            case SKIPPED:  return styleSkipped;
            default:       return TextStyle.NORMAL;
        }
    }

    private static String statusTag(Status s) {
        switch (s) {
            case PENDING:  return "PENDING";
            case RUNNING:  return "RUNNING…";
            case COMPLETE: return "DONE";
            case ERROR:    return "FAILED";
            case SKIPPED:  return "SKIPPED";
            default:       return "";
        }
    }

    private int computeRightReserve(Status status) {
        return (status == Status.RUNNING && step.isShowProgress())
                ? PROGRESS_WIDTH : STATUS_TAG_WIDTH;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private static String repeat(char c, int count) {
        if (count <= 0) return "";
        char[] buf = new char[count];
        java.util.Arrays.fill(buf, c);
        return new String(buf);
    }
}