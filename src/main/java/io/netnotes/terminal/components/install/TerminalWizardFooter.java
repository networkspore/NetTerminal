package io.netnotes.terminal.components.install;

import io.netnotes.engine.ui.LabelTruncation;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.components.panels.TerminalHStack;
import io.netnotes.terminal.components.text.TerminalLabel;

/**
 * TerminalWizardFooter – single-row installation status line.
 *
 * Renders (example):
 *   "  Step 2 of 4  ·  Elapsed: 00:12  ·  Applying schema migrations…"
 *
 * Layout is fully delegated to {@link TerminalHStack} + children:
 * <pre>
 *   stepLabel    (FIT_CONTENT)  "  Step 2 of 4"
 *   spacer       (FILL)          flexible gap
 *   elapsedLabel (FIT_CONTENT)  "  ·  Elapsed: 00:12"  [hidden when elapsed ≤ 0]
 *   detailLabel  (FILL, END-truncates)  "  ·  Detail or status"
 * </pre>
 *
 * No custom {@code renderSelf} or {@code measureContent} — sizing and
 * rendering are inherited from the HStack and its children.
 */
public class TerminalWizardFooter extends TerminalHStack {

    // Cached style values; applied on every update() call
    private boolean   showElapsed  = true;
    private TextStyle styleNormal  = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleSuccess = TextStyle.BOLD.withForeground(TextStyle.Color.GREEN);
    private TextStyle styleError   = TextStyle.BOLD.withForeground(TextStyle.Color.RED);

    private final TerminalLabel stepLabel;
    private final TerminalLabel elapsedLabel;
    private final TerminalLabel detailLabel;

    // ===== CONSTRUCTION =====

    public TerminalWizardFooter(String name) {
        super(name);
        setSpacing(0);
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FIT_CONTENT);
        setMinHeight(1);

        // Left: step counter — "  Step 2 of 4"
        stepLabel = new TerminalLabel(name + "-steps");
        stepLabel.setWidthPreference(SizePreference.FIT_CONTENT);
        addChild(stepLabel);

        // Flexible spacer: pushes elapsed + detail toward the right
        TerminalRegion spacer = new TerminalRegion(name + "-spcr");
        spacer.setWidthPreference(SizePreference.FILL);
        spacer.setHeightPreference(SizePreference.FIT_CONTENT);
        spacer.setMinHeight(1);
        addChild(spacer);

        // Right: elapsed time — "  ·  Elapsed: 00:12"
        elapsedLabel = new TerminalLabel(name + "-time");
        elapsedLabel.setWidthPreference(SizePreference.FIT_CONTENT);
        addChild(elapsedLabel);

        // Far right: active detail or completion status; truncates when narrow
        detailLabel = new TerminalLabel(name + "-detail");
        detailLabel.setWidthPreference(SizePreference.FILL);
        detailLabel.setTextTruncation(LabelTruncation.END);
        addChild(detailLabel);
    }

    // ===== STATE UPDATE =====

    /**
     * Pushes all wizard state into the child labels and re-applies styles.
     * Call from the wizard's {@code syncFooter()} — the wizard handles its own
     * change-detection cache so this method unconditionally updates.
     *
     * @param done         number of steps that have reached a terminal state
     * @param total        total number of registered steps
     * @param elapsedMs    milliseconds elapsed since the wizard started
     * @param activeDetail detail text from the currently running step (may be null)
     * @param complete     true when all steps have finished
     * @param hasError     true when at least one step failed with an error
     */
    public void update(int done, int total, long elapsedMs,
                       String activeDetail, boolean complete, boolean hasError) {

        // ── step counter ─────────────────────────────────────────────────────
        if (total <= 0) {
            stepLabel.setText("  Ready");
        } else {
            int displayStep = complete ? total : Math.min(total, Math.max(1, done + 1));
            stepLabel.setText("  Step " + displayStep + " of " + total);
        }

        // ── elapsed time ─────────────────────────────────────────────────────
        if (showElapsed && elapsedMs > 0) {
            long secs = elapsedMs / 1000L;
            elapsedLabel.setText(String.format("  ·  Elapsed: %02d:%02d", secs / 60, secs % 60));
            elapsedLabel.show();
        } else {
            elapsedLabel.setText("");
            elapsedLabel.hide();
        }

        // ── detail / completion status ────────────────────────────────────────
        detailLabel.setText(buildDetailText(activeDetail, done, total, complete, hasError));

        // ── styles ────────────────────────────────────────────────────────────
        // Base style shifts to success/error on completion.
        TextStyle base = complete ? (hasError ? styleError : styleSuccess) : styleNormal;
        stepLabel.setTextStyle(base);
        elapsedLabel.setTextStyle(base);
        // Error style is applied directly to the detail label for clear visibility,
        // even when the step counter is rendered in the neutral base style.
        detailLabel.setTextStyle(hasError ? styleError : base);

        invalidate();
    }

    // ===== CONFIGURATION =====

    /**
     * Shows or hides the elapsed-time segment.
     * Takes effect on the next {@link #update} call.
     */
    public void setShowElapsed(boolean show) {
        if (this.showElapsed != show) {
            this.showElapsed = show;
            invalidate();
        }
    }

    // ===== STYLE SETTERS =====
    // Styles are applied on each update() call; setters here just cache the value.

    /** Normal style — used while the wizard is in progress. */
    public void setStyleNormal(TextStyle s)  { if (s != null) this.styleNormal  = s; }

    /** Success style — used when all steps complete without error. */
    public void setStyleSuccess(TextStyle s) { if (s != null) this.styleSuccess = s; }

    /** Error style — used when any step fails; applied to the detail label as well. */
    public void setStyleError(TextStyle s)   { if (s != null) this.styleError   = s; }

    // ===== HELPERS =====

    /**
     * Composes the detail label text from the active step's detail and the
     * overall completion/in-progress status.
     *
     * <ul>
     *   <li>While running with detail:  "  ·  &lt;detail&gt;"</li>
     *   <li>While running, no detail:   "  ·  Press Ctrl+C to cancel"</li>
     *   <li>On completion:              "  ·  &lt;detail&gt;  ·  COMPLETE" (or FAILED)</li>
     * </ul>
     */
    private String buildDetailText(String activeDetail, int done, int total,
                                   boolean complete, boolean hasError) {
        StringBuilder sb = new StringBuilder();

        if (activeDetail != null && !activeDetail.isBlank()) {
            sb.append("  ·  ").append(activeDetail);
        }

        if (complete) {
            sb.append("  ·  ").append(hasError ? "FAILED" : "COMPLETE");
        } else if (sb.isEmpty() && done < total) {
            sb.append("  ·  Press Ctrl+C to cancel");
        }

        return sb.toString();
    }
}