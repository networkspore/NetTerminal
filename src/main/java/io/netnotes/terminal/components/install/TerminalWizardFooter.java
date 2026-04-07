package io.netnotes.terminal.components.install;

import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.layout.TerminalLayoutContext;

/**
 * TerminalWizardFooter - Single-row installation status line.
 *
 * Renders:
 *   "  Step 2 of 4  ·  Elapsed: 00:12  ·  Press Ctrl+C to cancel"
 *
 * Height is always 1 (FIT_CONTENT). Width is FILL.
 */
public class TerminalWizardFooter extends TerminalRegion {

    private int     stepsDone   = 0;
    private int     stepsTotal  = 0;
    private boolean showElapsed = true;
    private long    elapsedMs   = 0L;
    private String  activeDetail = null;
    private boolean complete    = false;
    private boolean hasError    = false;

    private TextStyle styleNormal  = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleSuccess = TextStyle.BOLD.withForeground(TextStyle.Color.GREEN);
    private TextStyle styleError   = TextStyle.BOLD.withForeground(TextStyle.Color.RED);

    public TerminalWizardFooter(String name) {
        super(name);
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FIT_CONTENT);
        setMinHeight(1);
    }

    // ===== SETTERS =====

    public void update(int stepsDone, int stepsTotal, long elapsedMs,
                       String activeDetail, boolean complete, boolean hasError) {
        this.stepsDone    = stepsDone;
        this.stepsTotal   = stepsTotal;
        this.elapsedMs    = elapsedMs;
        this.activeDetail = activeDetail;
        this.complete     = complete;
        this.hasError     = hasError;
        invalidate();
    }

    public void setShowElapsed(boolean show)     {
        if (this.showElapsed != show) {
            this.showElapsed = show;
            invalidate();
        }
    }
    public void setStyleNormal(TextStyle s)      { this.styleNormal  = s;  invalidate(); }
    public void setStyleSuccess(TextStyle s)     { this.styleSuccess = s;  invalidate(); }
    public void setStyleError(TextStyle s)       { this.styleError   = s;  invalidate(); }

    // ===== SIZING =====

    public int getPreferredHeight() {
        return resolveMeasuredHeight();
    }

    public int getPreferredWidth()  {
        return resolveMeasuredWidth();
    }

    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, resolveMeasuredWidth(), resolveMeasuredHeight());
        return measured;
    }

    int getIntrinsicContentWidth() {
        return buildText().length();
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

    private int resolveMeasuredHeight() {
        return switch (getHeightPreference()) {
            case STATIC -> getRegion().getHeight();
            case FIT_CONTENT -> Math.max(getMinHeight(), 1 + getInsets().getVertical());
            default -> getMinHeight();
        };
    }


    // ===== RENDERING =====

    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        int w = getWidth();
        if (w <= 0) return;

        String text = buildText();
        TextStyle style = complete ? (hasError ? styleError : styleSuccess) : styleNormal;
        printAt(batch, 0, 0, text.length() <= w ? text : text.substring(0, w), style);
    }

    private String buildText() {
        StringBuilder sb = new StringBuilder("  ");
        sb.append("Step ").append(stepsDone).append(" of ").append(stepsTotal);

        if (showElapsed && elapsedMs > 0) {
            long secs = elapsedMs / 1000L;
            sb.append("  ·  Elapsed: ").append(String.format("%02d:%02d", secs / 60, secs % 60));
        }

        if (activeDetail != null && !activeDetail.isBlank()) {
            sb.append("  ·  ").append(activeDetail);
        }

        if (complete) {
            sb.append("  ·  ").append(hasError ? "FAILED" : "COMPLETE");
        } else {
            sb.append("  ·  Press Ctrl+C to cancel");
        }

        return sb.toString();
    }
}
