package io.netnotes.terminal.components.install;

import io.netnotes.engine.ui.LabelTruncation;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.components.TerminalProgressBar;
import io.netnotes.terminal.components.panels.TerminalHStack;
import io.netnotes.terminal.components.text.TerminalLabel;

/**
 * TerminalWizardHeader – single-row title + overall progress bar.
 *
 * Renders (example):
 *   "  Netnotes Installer v1.0          Overall:  52% [██████░░░░░]"
 *
 * Layout is fully delegated to {@link TerminalHStack} + children:
 * <pre>
 *   titleLabel (FILL, truncates at end)
 *   barLabel   (FIT_CONTENT, " Overall: ")
 *   overallBar (FIT_CONTENT, min 20 cols)
 * </pre>
 *
 * No custom {@code renderSelf} or {@code measureContent} — sizing and
 * rendering are inherited from {@code TerminalHStack} and its children.
 * On narrow terminals the title truncates before the bar is pushed off-screen;
 * if the bar itself no longer fits, the HStack simply clips it.
 */
public class TerminalWizardHeader extends TerminalHStack {

    // Stored separately so setSubtitle() can compose without re-parsing the label
    private String title    = "";
    private String subtitle = null;

    // Cached values whose getters callers may need (avoids reaching into ProgressBar)
    private float overallProgress = 0f;


    private final TerminalLabel       titleLabel;
    private final TerminalLabel       barLabel;
    private final TerminalProgressBar overallBar;

    // ===== CONSTRUCTION =====

    public TerminalWizardHeader(String name) {
        super(name);
        setSpacing(0);
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FIT_CONTENT);
        setMinHeight(1);

        // Title — consumes all remaining width; end-truncates when too narrow
        titleLabel = new TerminalLabel(name + "-title");
        titleLabel.setWidthPreference(SizePreference.FILL);
        titleLabel.setTextTruncation(LabelTruncation.END);
        addChild(titleLabel);

        // Fixed " Overall: " separator
        barLabel = new TerminalLabel(name + "-bar-label");
        barLabel.setText(" Overall: ");
        barLabel.setWidthPreference(SizePreference.FIT_CONTENT);
        addChild(barLabel);

        // Overall progress bar — percentage shown inline by the bar itself
        overallBar = new TerminalProgressBar(name + "-bar");
        overallBar.setWidthPreference(SizePreference.FIT_CONTENT);
        overallBar.setHeightPreference(SizePreference.FIT_CONTENT);
        overallBar.setMinWidth(20);
        overallBar.setShowPercentage(true);
        addChild(overallBar);
    }

    // ===== CONTENT SETTERS =====

    /** Sets the primary title text. Triggers a label update (layout-aware). */
    public void setTitle(String text) {
        this.title = text != null ? text : "";
        refreshTitleLabel();
    }

    /**
     * Sets an optional subtitle appended to the title: "  Title  Subtitle".
     * Pass {@code null} or blank to clear.
     */
    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
        refreshTitleLabel();
    }

    /** Sets overall progress in the range [0, 1]. */
    public void setOverallProgress(float p) {
        overallProgress = Math.max(0f, Math.min(1f, p));
        overallBar.setProgress(overallProgress);
    }

    /**
     * Sets the width of the progress bar in character columns.
     * Clamped to a minimum of 4.
     */
    public void setOverallBarWidth(int cols) {
        overallBar.setMinWidth(Math.max(4, cols));
    }

    /**
     * Sets the fill character for the progress bar (e.g. {@code '█'}).
     * Delegates to {@link TerminalProgressBar#setFillChar(char)} — adapt if
     * the ProgressBar API differs in your build.
     */
    public void setFillChar(char c) {
        overallBar.setFillChar(c);
    }

    /**
     * Sets the empty character for the progress bar (e.g. {@code '░'}).
     * Delegates to {@link TerminalProgressBar#setEmptyChar(char)} — adapt if
     * the ProgressBar API differs in your build.
     */
    public void setEmptyChar(char c) {
        overallBar.setEmptyChar(c);
    }

    // ===== STYLE DELEGATES =====

    /** Applies a text style to the title label. */
    public void setStyleTitle(TextStyle s) {
        if (s != null) titleLabel.setTextStyle(s);
    }

    /** Applies a text style to the " Overall: " separator label. */
    public void setStyleBarLabel(TextStyle s) {
        if (s != null) barLabel.setTextStyle(s);
    }

    /** Applies a text style to the filled portion of the progress bar. */
    public void setStyleBarFill(TextStyle s) {
        if (s != null) overallBar.setFilledStyle(s);
    }

    /** Applies a text style to the empty portion of the progress bar. */
    public void setStyleBarEmpty(TextStyle s) {
        if (s != null) overallBar.setEmptyStyle(s);
    }

    // ===== GETTERS =====

    /** Returns the primary title (without subtitle). */
    public String getTitle()    { return title; }

    /** Returns the subtitle, or {@code null} if none is set. */
    public String getSubtitle() { return subtitle; }

    /** Returns the last progress value passed to {@link #setOverallProgress(float)}. */
    public float  getOverallProgress() { return overallProgress; }

    /** Returns the current fill character. */
    public char   getFillChar()  { return overallBar.getFillChar(); }

    /** Returns the current empty character. */
    public char   getEmptyChar() { return overallBar.getEmptyChar(); }

    /** Returns the current text style of the title label. */
    public TextStyle getStyleTitle()    { return titleLabel.getStyle(); }

    /** Returns the current text style of the bar separator label. */
    public TextStyle getStyleBarLabel() { return barLabel.getStyle(); }

    /** Returns the filled-portion style from the underlying progress bar. */
    public TextStyle getStyleBarFill()  { return overallBar.getFilledStyle(); }

    /** Returns the empty-portion style from the underlying progress bar. */
    public TextStyle getStyleBarEmpty() { return overallBar.getEmptyStyle(); }

    // ===== INTERNAL =====

    /**
     * Rebuilds the title label text from the stored title + subtitle fields.
     * A 2-space left pad is preserved to match the original header's inset.
     */
    private void refreshTitleLabel() {
        StringBuilder sb = new StringBuilder("  ").append(title);
        if (subtitle != null && !subtitle.isBlank()) {
            sb.append("  ").append(subtitle);
        }
        titleLabel.setText(sb.toString());
    }
}