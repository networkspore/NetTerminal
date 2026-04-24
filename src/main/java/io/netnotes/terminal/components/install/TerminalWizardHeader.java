package io.netnotes.terminal.components.install;


import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.layout.TerminalLayoutContext;

/**
 * TerminalWizardHeader - Single-row title + overall progress bar.
 *
 * Renders:
 *   "  Netnotes Installer v1.0          Overall:  52% [██████░░░░░]"
 *
 * Height is always 1 (FIT_CONTENT). Width is FILL.
 * All display state is set via setters; call invalidate() after any change.
 */
public class TerminalWizardHeader extends TerminalRegion {


    private static final int TITLE_PADDING     = 2;

    private String    title            = "Installation Wizard";
    private String    subtitle         = null;
    private float     overallProgress  = 0f;
    private char      fillChar         = '█';
    private char      emptyChar        = '░';
    private int       overallBarWidth  = 20;

    private TextStyle styleTitle       = TextStyle.BOLD;
    private TextStyle styleBarLabel    = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);
    private TextStyle styleBarFill     = TextStyle.BOLD.withForeground(TextStyle.Color.CYAN);
    private TextStyle styleBarEmpty    = TextStyle.NORMAL.withForeground(TextStyle.Color.BRIGHT_BLACK);

    public TerminalWizardHeader(String name) {
        super(name);
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FIT_CONTENT);
        setMinHeight(1);
    }

    // ===== SETTERS =====

    public void setTitle(String title)           { this.title = title != null ? title : ""; requestLayoutUpdate(); }
    public void setSubtitle(String subtitle)     { this.subtitle = subtitle;                 requestLayoutUpdate(); }
    public void setOverallProgress(float p)      { this.overallProgress = Math.max(0f, Math.min(1f, p)); invalidate(); }
    public void setFillChar(char c)              { this.fillChar  = c; invalidate(); }
    public void setEmptyChar(char c)             { this.emptyChar = c; invalidate(); }
    public void setStyleTitle(TextStyle s)       { this.styleTitle    = s; invalidate(); }
    public void setStyleBarLabel(TextStyle s)    { this.styleBarLabel = s; invalidate(); }
    public void setStyleBarFill(TextStyle s)     { this.styleBarFill  = s; invalidate(); }
    public void setStyleBarEmpty(TextStyle s)    { this.styleBarEmpty = s; invalidate(); }
    public void setOverallBarWidth(int i)        { this.overallBarWidth = i; requestLayoutUpdate(); }
    

    public String getTitle() {return title; }
    public String getSubtitle() { return subtitle;}
    public float getOverallProgress() { return overallProgress; }
    public char getFillChar() { return fillChar; }
    public char getEmptyChar() { return emptyChar; }
    public TextStyle getStyleTitle() { return styleTitle; }
    public TextStyle getStyleBarLabel() { return styleBarLabel; }
    public TextStyle getStyleBarFill() { return styleBarFill; }
    public TextStyle getStyleBarEmpty() { return styleBarEmpty; }

    // ===== SIZING =====

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

    int getIntrinsicContentWidth() {
        return buildTitleText().length() + 2 + buildRightBlockPreview().length();
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

        String titleText = buildTitleText();

        int    filled    = Math.max(0, Math.round(overallProgress * overallBarWidth));
        String pctStr    = String.format("%3d%%", (int)(overallProgress * 100f));
        String barLabel  = "Overall: " + pctStr + " [";
        String fillPart  = repeat(fillChar,  filled);
        String emptyPart = repeat(emptyChar, overallBarWidth - filled) + "]";
        String rightBlock = barLabel + fillPart + emptyPart;

        // Full layout: title + complete overall progress block.
        if (w >= rightBlock.length() + 6) {
            int maxTitleW = w - rightBlock.length() - 2;
            String displayedTitle = maxTitleW > 0 ? truncate(titleText, maxTitleW) : "";
            if (!displayedTitle.isEmpty()) {
                printAt(batch, 0, 0, displayedTitle, styleTitle);
            }

            int barX = w - rightBlock.length();
            if (barX >= 0 && barX + rightBlock.length() <= w) {
                printAt(batch, barX, 0, barLabel, styleBarLabel);
                int bx = barX + barLabel.length();
                if (!fillPart.isEmpty()) { printAt(batch, bx, 0, fillPart, styleBarFill); bx += fillPart.length(); }
                printAt(batch, bx, 0, emptyPart, styleBarEmpty);
            }
            return;
        }

        // Compact layout for narrow widths: title + right-aligned percentage only.
        if (w >= pctStr.length() + 4) {
            int compactX = w - pctStr.length();
            int maxTitleW = Math.max(0, compactX - 1);
            String displayedTitle = maxTitleW > 0 ? truncate(titleText, maxTitleW) : "";
            if (!displayedTitle.isEmpty()) {
                printAt(batch, 0, 0, displayedTitle, styleTitle);
            }
            printAt(batch, compactX, 0, pctStr, styleBarLabel);
            return;
        }

        // Ultra-narrow fallback: prefer title, otherwise show clipped percentage.
        String titleFallback = truncate(titleText, w);
        if (!titleFallback.isEmpty()) {
            printAt(batch, 0, 0, titleFallback, styleTitle);
        } else {
            String pctFallback = pctStr.substring(Math.max(0, pctStr.length() - w));
            printAt(batch, 0, 0, pctFallback, styleBarLabel);
        }
    }

    private String buildTitleText() {
        String text = " ".repeat(TITLE_PADDING) + title;
        if (subtitle != null && !subtitle.isBlank()) {
            text += "  " + subtitle;
        }
        return text;
    }

    private String buildRightBlockPreview() {
        return "Overall: 100% [" + repeat(fillChar, overallBarWidth) + "]";
    }

    private static String truncate(String s, int max) {
        if (s == null || max <= 0) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
    private static String repeat(char c, int n) {
        if (n <= 0) return "";
        char[] buf = new char[n]; java.util.Arrays.fill(buf, c); return new String(buf);
    }
}
