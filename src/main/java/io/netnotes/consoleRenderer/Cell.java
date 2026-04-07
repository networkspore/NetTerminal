package io.netnotes.consoleRenderer;

import io.netnotes.terminal.TextStyle;
import org.jline.utils.WCWidth;

public class Cell {

    /**
     * Blank cell — codepoint 0 means this position should display as a space.
     * Not the same as a space character (32), which is an explicit space with style.
     */
    public static final int BLANK = 0;

    public static final int SPACE_CP = 32;
    public static final String SPACE_STR = " ";

    /**
     * Sentinel used in prevCells to force repaint on next diff.
     * -1 is outside the Unicode range [0, 0x10FFFF] so it never equals a real codepoint.
     */
    public static final int FORCE_REPAINT_SENTINEL = -1;

    /**
     * displayWidth == 0 means this cell is a continuation placeholder —
     * the preceding cell is a wide (2-column) character that occupies this column too.
     * Renderers must skip continuation cells when emitting characters.
     */
    int codepoint = BLANK;
    int displayWidth = 1;
    TextStyle style = new TextStyle();

    /**
     * Set cell content from a codepoint.
     * Automatically computes display width via WCWidth.
     * Wide characters (e.g. emoji, many CJK) have displayWidth == 2.
     */
    public void set(int codepoint, TextStyle style) {
        this.codepoint = codepoint;
        this.displayWidth = computeDisplayWidth(codepoint);
        this.style = style != null ? style.copy() : new TextStyle();
    }

   

    /**
     * Set this cell as a continuation placeholder for a preceding wide character.
     * displayWidth == 0 tells the renderer to skip this column.
     */
    public void setAsContinuation() {
        this.codepoint = BLANK;
        this.displayWidth = 0;
        this.style = new TextStyle();
    }

    public void clear() {
        this.codepoint = BLANK;
        this.displayWidth = 1;
        this.style = new TextStyle();
    }

    /**
     * Mark this cell to force repaint on next differential render.
     * Used by invalidateDamageRegions() in ConsoleContainer.
     */
    public void markForceRepaint() {
        this.codepoint = FORCE_REPAINT_SENTINEL;
    }

    public boolean isBlank() {
        return codepoint == BLANK;
    }

    public boolean isContinuation() {
        return displayWidth == 0;
    }

    public boolean isForceRepaint() {
        return codepoint == FORCE_REPAINT_SENTINEL;
    }

    public void copyFrom(Cell other) {
        this.codepoint = other.codepoint;
        this.displayWidth = other.displayWidth;
        this.style = other.style.copy();
    }

     public Cell copy() {
        Cell c = new Cell();
        c.codepoint = this.codepoint;
        c.style = this.style.copy(); // TextStyle already has copyFrom — add a copy() that does new+copyFrom
        c.displayWidth = this.displayWidth;
        return c;
    }

    /**
     * Convert codepoint to a String for terminal output.
     * Handles supplementary characters (outside BMP) via surrogate pairs.
     */
    public String asString() {
        if (codepoint <= 0) return SPACE_STR;
        return new String(Character.toChars(codepoint));
    }

    public int getCodepoint() { return codepoint; }
    public int getDisplayWidth() { return displayWidth; }
    public TextStyle getStyle() { return style; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Cell other)) return false;
        return codepoint == other.codepoint
            && displayWidth == other.displayWidth
            && style.equals(other.style);
    }

    @Override
    public int hashCode() {
        int result = codepoint;
        result = 31 * result + displayWidth;
        result = 31 * result + style.hashCode();
        return result;
    }

    /**
     * Compute terminal display width for a codepoint.
     * WCWidth returns:
     *   -1 for non-printable control characters  → treat as 1
     *    0 for combining/zero-width characters    → treat as 1 (safe default)
     *    1 for normal width characters
     *    2 for wide characters (emoji, CJK wide)
     */
    public static int computeDisplayWidth(int codepoint) {
        if (codepoint <= 0) return 1;
        int w = WCWidth.wcwidth(codepoint);
        return (w >= 1) ? w : 1;
    }
}