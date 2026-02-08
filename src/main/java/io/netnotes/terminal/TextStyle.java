package io.netnotes.terminal;
import java.util.Objects;

import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * TextStyle - Comprehensive text styling for terminal rendering
 * 
 * Supports the full range of JLine3 AttributedStyle capabilities:
 * - Text attributes: bold, faint/dim, italic, underline, blink, inverse, hidden, strikethrough
 * - Color modes: 16 named colors, 256-color palette, 24-bit RGB
 * - Both foreground and background colors
 */
public class TextStyle {
    
    /**
     * Named color enumeration (16 colors)
     * Corresponds to ANSI standard colors
     */
    public enum Color {
        DEFAULT,
        BLACK, RED, GREEN, YELLOW,
        BLUE, MAGENTA, CYAN, WHITE,
        BRIGHT_BLACK, BRIGHT_RED, BRIGHT_GREEN, BRIGHT_YELLOW,
        BRIGHT_BLUE, BRIGHT_MAGENTA, BRIGHT_CYAN, BRIGHT_WHITE
    }

    /**
     * Box drawing styles for component borders
     */
    public enum BoxStyle {
        SINGLE(new char[]{'─', '│', '┌', '┐', '└', '┘'}),
        DOUBLE(new char[]{'═', '║', '╔', '╗', '╚', '╝'}),
        ROUNDED(new char[]{'─', '│', '╭', '╮', '╰', '╯'}),
        THICK(new char[]{'━', '┃', '┏', '┓', '┗', '┛'}),
        // ASCII fallback for limited terminal support
        ASCII(new char[]{'-', '|', '+', '+', '+', '+'});
        
        private final char[] chars;
        
        BoxStyle(char[] chars) {
            this.chars = chars;
        }
        
        public char[] getChars() {
            return chars;
        }
        
        public char horizontal() { return chars[0]; }
        public char vertical() { return chars[1]; }
        public char topLeft() { return chars[2]; }
        public char topRight() { return chars[3]; }
        public char bottomLeft() { return chars[4]; }
        public char bottomRight() { return chars[5]; }
    }
    public static final NoteBytesMap NORMAL_BYTES = new TextStyle().toNoteBytes();

    /**
     * Create TextStyle from NoteBytes
     * @param styleBytes NoteBytes containing style data
     * @return TextStyle instance, or NORMAL if styleBytes is null
     */
    public static TextStyle fromNoteBytes(NoteBytes styleBytes) {
        if (styleBytes == null) return new TextStyle();
        
        NoteBytesMap styleMap = styleBytes.getAsNoteBytesMap();
        TextStyle style = new TextStyle();
        
        // Parse foreground color
        NoteBytes fgMode = styleMap.get(StyleConstants.FG_MODE);
        if (fgMode != null) {
            try{    
                ColorMode mode = ColorMode.valueOf(fgMode.getAsString());
                switch(mode){
                    case INDEXED:
                        NoteBytes indexed = styleMap.get(StyleConstants.FG_INDEXED);
                        if (indexed != null) {
                            style.fgIndexed(indexed.getAsInt());
                        }
                        break;
                    case NAMED:
                        NoteBytes fg = styleMap.get(Keys.FOREGROUND);
                        if (fg != null) {
                            try {
                                style.color(Color.valueOf(fg.getAsString()));
                            } catch (IllegalArgumentException e) {
                                // Invalid color name, use default
                            }
                        }
                        break;
                    case RGB:
                        NoteBytes rgb = styleMap.get(StyleConstants.FG_RGB);
                        if (rgb != null) {
                            style.fgRgb(rgb.getAsInt());
                        }
                        break;
                    default:
                        break;
                }
            }catch(IllegalArgumentException ex){
                Log.logError("[TextStyle.fromNoteBytes]","fgMode invalid", ex);
            }
        }
        
        // Parse background color
        NoteBytes bgMode = styleMap.get(StyleConstants.BG_MODE);
        if (bgMode != null) {
            try{
                ColorMode mode = ColorMode.valueOf(bgMode.getAsString());
                switch(mode){
                    case INDEXED:
                        NoteBytes indexed = styleMap.get(StyleConstants.BG_INDEXED);
                        if (indexed != null) {
                            style.bgIndexed(indexed.getAsInt());
                        }
                        break;
                    case NAMED:
                        NoteBytes bg = styleMap.get(Keys.BACKGROUND);
                        if (bg != null) {
                            try {
                                style.bgColor(Color.valueOf(bg.getAsString()));
                            } catch (IllegalArgumentException e) {
                                // Invalid color name, use default
                            }
                        }
                        break;
                    case RGB:
                        NoteBytes rgb = styleMap.get(StyleConstants.BG_RGB);
                        if (rgb != null) {
                            style.bgRgb(rgb.getAsInt());
                        }
                        break;
                    default:
                        break;
                }
            }catch(IllegalArgumentException ex){
                Log.logError("[TextStyle.fromNoteBytes]","bgMode invalid", ex);
            }
        }
        
        // Parse text attributes
        NoteBytes bold = styleMap.get(Keys.BOLD);
        if (bold != null && bold.getAsBoolean()) style.bold();
        
        NoteBytes faint = styleMap.get(StyleConstants.FAINT);
        if (faint != null && faint.getAsBoolean()) style.faint();
        
        NoteBytes italic = styleMap.get(StyleConstants.ITALIC);
        if (italic != null && italic.getAsBoolean()) style.italic();
        
        NoteBytes underline = styleMap.get(Keys.UNDERLINE);
        if (underline != null && underline.getAsBoolean()) style.underline();
        
        NoteBytes blink = styleMap.get(StyleConstants.BLINK);
        if (blink != null && blink.getAsBoolean()) style.blink();
        
        NoteBytes inverse = styleMap.get(Keys.INVERSE);
        if (inverse != null && inverse.getAsBoolean()) style.inverse();
        
        NoteBytes hidden = styleMap.get(StyleConstants.HIDDEN);
        if (hidden != null && hidden.getAsBoolean()) style.hidden();
        
        NoteBytes strikethrough = styleMap.get(StyleConstants.STRIKETHROUGH);
        if (strikethrough != null && strikethrough.getAsBoolean()) style.strikethrough();
        
        return style;
    }
    // Predefined semantic styles
    public static final TextStyle NORMAL = new TextStyle();
    public static final TextStyle BOLD = new TextStyle().bold();
    public static final TextStyle DIM = new TextStyle().faint();
    public static final TextStyle ITALIC = new TextStyle().italic();
    public static final TextStyle INVERSE = new TextStyle().inverse();
    public static final TextStyle UNDERLINE = new TextStyle().underline();
    public static final TextStyle STRIKETHROUGH = new TextStyle().strikethrough();
    public static final TextStyle HIDDEN = new TextStyle().hidden();
    public static final TextStyle BLINK = new TextStyle().blink();
    
    // Semantic colors
    public static final TextStyle ERROR = new TextStyle().color(Color.RED).bold();
    public static final TextStyle SUCCESS = new TextStyle().color(Color.GREEN);
    public static final TextStyle WARNING = new TextStyle().color(Color.YELLOW);
    public static final TextStyle INFO = new TextStyle().color(Color.CYAN);
    public static final TextStyle DEBUG = new TextStyle().color(Color.BRIGHT_BLACK);
    
    // Color mode enum
    public enum ColorMode {
        NAMED,      // 16 named colors
        INDEXED,    // 256-color palette (0-255)
        RGB         // 24-bit true color
    }
    
    // Foreground color state
    private ColorMode fgMode = ColorMode.NAMED;
    private Color foreground = Color.DEFAULT;
    private int fgIndexed = -1;     // For 256-color mode
    private int fgRgb = -1;          // For RGB mode (24-bit packed)
    
    // Background color state
    private ColorMode bgMode = ColorMode.NAMED;
    private Color background = Color.DEFAULT;
    private int bgIndexed = -1;     // For 256-color mode
    private int bgRgb = -1;          // For RGB mode (24-bit packed)
    
    // Text attributes
    private boolean bold = false;
    private boolean faint = false;       // Also called DIM
    private boolean italic = false;
    private boolean underline = false;
    private boolean blink = false;
    private boolean inverse = false;
    private boolean hidden = false;      // Also called CONCEAL
    private boolean strikethrough = false;

    public TextStyle() {}
    
    // ===== NAMED COLOR METHODS =====
    
    public TextStyle color(Color fg) {
        this.fgMode = ColorMode.NAMED;
        this.foreground = fg;
        this.fgIndexed = -1;
        this.fgRgb = -1;
        return this;
    }
    
    public TextStyle bgColor(Color bg) {
        this.bgMode = ColorMode.NAMED;
        this.background = bg;
        this.bgIndexed = -1;
        this.bgRgb = -1;
        return this;
    }
    
    // ===== 256-COLOR PALETTE METHODS =====
    
    /**
     * Set foreground to indexed color (0-255)
     * Colors 0-15: Standard colors
     * Colors 16-231: 6x6x6 RGB cube
     * Colors 232-255: Grayscale ramp
     */
    public TextStyle fgIndexed(int colorIndex) {
        if (colorIndex < 0 || colorIndex > 255) {
            throw new IllegalArgumentException("Color index must be 0-255");
        }
        this.fgMode = ColorMode.INDEXED;
        this.fgIndexed = colorIndex;
        this.foreground = Color.DEFAULT;
        this.fgRgb = -1;
        return this;
    }
    
    /**
     * Set background to indexed color (0-255)
     */
    public TextStyle bgIndexed(int colorIndex) {
        if (colorIndex < 0 || colorIndex > 255) {
            throw new IllegalArgumentException("Color index must be 0-255");
        }
        this.bgMode = ColorMode.INDEXED;
        this.bgIndexed = colorIndex;
        this.background = Color.DEFAULT;
        this.bgRgb = -1;
        return this;
    }
    
    // ===== RGB TRUE COLOR METHODS =====
    
    /**
     * Set foreground to RGB color (24-bit true color)
     * @param r Red component (0-255)
     * @param g Green component (0-255)
     * @param b Blue component (0-255)
     */
    public TextStyle fgRgb(int r, int g, int b) {
        if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
            throw new IllegalArgumentException("RGB components must be 0-255");
        }
        this.fgMode = ColorMode.RGB;
        this.fgRgb = (r << 16) | (g << 8) | b;
        this.foreground = Color.DEFAULT;
        this.fgIndexed = -1;
        return this;
    }
    
    /**
     * Set foreground to RGB color from packed int
     * @param rgb Packed RGB value (0xRRGGBB)
     */
    public TextStyle fgRgb(int rgb) {
        this.fgMode = ColorMode.RGB;
        this.fgRgb = rgb & 0xFFFFFF;
        this.foreground = Color.DEFAULT;
        this.fgIndexed = -1;
        return this;
    }
    
    /**
     * Set background to RGB color (24-bit true color)
     */
    public TextStyle bgRgb(int r, int g, int b) {
        if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
            throw new IllegalArgumentException("RGB components must be 0-255");
        }
        this.bgMode = ColorMode.RGB;
        this.bgRgb = (r << 16) | (g << 8) | b;
        this.background = Color.DEFAULT;
        this.bgIndexed = -1;
        return this;
    }
    
    /**
     * Set background to RGB color from packed int
     */
    public TextStyle bgRgb(int rgb) {
        this.bgMode = ColorMode.RGB;
        this.bgRgb = rgb & 0xFFFFFF;
        this.background = Color.DEFAULT;
        this.bgIndexed = -1;
        return this;
    }
    
    // ===== TEXT ATTRIBUTE METHODS =====
    
    public TextStyle bold() {
        this.bold = true;
        return this;
    }
    
    /**
     * Set faint/dim attribute (SGR 2)
     * Makes text appear lighter or with reduced intensity
     */
    public TextStyle faint() {
        this.faint = true;
        return this;
    }
    
    /**
     * Alias for faint() - same as DIM attribute
     */
    public TextStyle dim() {
        return faint();
    }
    
    public TextStyle italic() {
        this.italic = true;
        return this;
    }
    
    public TextStyle underline() {
        this.underline = true;
        return this;
    }
    
    /**
     * Set blink attribute (SGR 5)
     * Note: Not widely supported by modern terminals
     */
    public TextStyle blink() {
        this.blink = true;
        return this;
    }
    
    public TextStyle inverse() {
        this.inverse = true;
        return this;
    }
    
    /**
     * Set hidden/conceal attribute (SGR 8)
     * Makes text invisible (useful for passwords)
     */
    public TextStyle hidden() {
        this.hidden = true;
        return this;
    }
    
    /**
     * Alias for hidden()
     */
    public TextStyle conceal() {
        return hidden();
    }
    
    /**
     * Set strikethrough attribute (SGR 9)
     */
    public TextStyle strikethrough() {
        this.strikethrough = true;
        return this;
    }
    
    /**
     * Alias for strikethrough()
     */
    public TextStyle crossedOut() {
        return strikethrough();
    }

    // ===== LEGACY COMPATIBILITY METHODS =====
    
    public TextStyle withForeground(Color foreground) {
        return color(foreground);
    }

    public TextStyle withBackground(Color background) {
        return bgColor(background);
    }

    // ===== SETTERS =====
    
    public void setForeground(Color foreground) {
        this.fgMode = ColorMode.NAMED;
        this.foreground = foreground;
        this.fgIndexed = -1;
        this.fgRgb = -1;
    }

    public void setBackground(Color background) {
        this.bgMode = ColorMode.NAMED;
        this.background = background;
        this.bgIndexed = -1;
        this.bgRgb = -1;
    }

    public void setBold(boolean bold) {
        this.bold = bold;
    }
    
    public void setFaint(boolean faint) {
        this.faint = faint;
    }
    
    public void setItalic(boolean italic) {
        this.italic = italic;
    }

    public void setInverse(boolean inverse) {
        this.inverse = inverse;
    }

    public void setUnderline(boolean underline) {
        this.underline = underline;
    }
    
    public void setBlink(boolean blink) {
        this.blink = blink;
    }
    
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
    
    public void setStrikethrough(boolean strikethrough) {
        this.strikethrough = strikethrough;
    }

    // ===== GETTERS =====
    
    public ColorMode getFgMode() {
        return fgMode;
    }
    
    public ColorMode getBgMode() {
        return bgMode;
    }
    
    public Color getForeground() {
        return foreground;
    }

    public Color getBackground() {
        return background;
    }
    
    public int getFgIndexed() {
        return fgIndexed;
    }
    
    public int getBgIndexed() {
        return bgIndexed;
    }
    
    public int getFgRgb() {
        return fgRgb;
    }
    
    public int getBgRgb() {
        return bgRgb;
    }

    public boolean isBold() {
        return bold;
    }
    
    public boolean isFaint() {
        return faint;
    }
    
    public boolean isDim() {
        return faint;
    }
    
    public boolean isItalic() {
        return italic;
    }

    public boolean isInverse() {
        return inverse;
    }

    public boolean isUnderline() {
        return underline;
    }
    
    public boolean isBlink() {
        return blink;
    }
    
    public boolean isHidden() {
        return hidden;
    }
    
    public boolean isStrikethrough() {
        return strikethrough;
    }
    
    // ===== SERIALIZATION =====
    
    public NoteBytesMap toNoteBytes() {
        NoteBytesMap map = new NoteBytesMap();
        
        // Foreground color
        map.put(StyleConstants.FG_MODE, fgMode.name());
        if (fgMode == ColorMode.NAMED && foreground != null) {
            map.put(Keys.FOREGROUND, foreground.name());
        } else if (fgMode == ColorMode.INDEXED) {
            map.put(StyleConstants.FG_INDEXED, fgIndexed);
        } else if (fgMode == ColorMode.RGB) {
            map.put(StyleConstants.FG_RGB, fgRgb);
        }
        
        // Background color
        map.put(StyleConstants.BG_MODE, bgMode.name());
        if (bgMode == ColorMode.NAMED && background != null) {
            map.put(Keys.BACKGROUND, background.name());
        } else if (bgMode == ColorMode.INDEXED) {
            map.put(StyleConstants.BG_INDEXED, bgIndexed);
        } else if (bgMode == ColorMode.RGB) {
            map.put(StyleConstants.BG_RGB, bgRgb);
        }
        
        // Text attributes
        if (bold) map.put(Keys.BOLD, true);
        if (faint) map.put(StyleConstants.FAINT, true);
        if (italic) map.put(StyleConstants.ITALIC, true);
        if (underline) map.put(Keys.UNDERLINE, true);
        if (blink) map.put(StyleConstants.BLINK, true);
        if (inverse) map.put(Keys.INVERSE, true);
        if (hidden) map.put(StyleConstants.HIDDEN, true);
        if (strikethrough) map.put(StyleConstants.STRIKETHROUGH, true);
        
        return map;
    }

    public TextStyle copy() {
        TextStyle textStyle = new TextStyle();
        
        // Copy color state
        textStyle.fgMode = this.fgMode;
        textStyle.foreground = this.foreground;
        textStyle.fgIndexed = this.fgIndexed;
        textStyle.fgRgb = this.fgRgb;
        
        textStyle.bgMode = this.bgMode;
        textStyle.background = this.background;
        textStyle.bgIndexed = this.bgIndexed;
        textStyle.bgRgb = this.bgRgb;
        
        // Copy attributes
        textStyle.bold = this.bold;
        textStyle.faint = this.faint;
        textStyle.italic = this.italic;
        textStyle.underline = this.underline;
        textStyle.blink = this.blink;
        textStyle.inverse = this.inverse;
        textStyle.hidden = this.hidden;
        textStyle.strikethrough = this.strikethrough;
        
        return textStyle;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TextStyle)) return false;
        TextStyle other = (TextStyle) obj;
        return fgMode == other.fgMode &&
            bgMode == other.bgMode &&
            foreground == other.foreground &&
            background == other.background &&
            fgIndexed == other.fgIndexed &&
            bgIndexed == other.bgIndexed &&
            fgRgb == other.fgRgb &&
            bgRgb == other.bgRgb &&
            bold == other.bold &&
            faint == other.faint &&
            italic == other.italic &&
            underline == other.underline &&
            blink == other.blink &&
            inverse == other.inverse &&
            hidden == other.hidden &&
            strikethrough == other.strikethrough;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fgMode, bgMode, foreground, background, 
            fgIndexed, bgIndexed, fgRgb, bgRgb,
            bold, faint, italic, underline, blink, inverse, hidden, strikethrough);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TextStyle[");
        
        // Foreground
        if (fgMode == ColorMode.NAMED) {
            sb.append("fg=").append(foreground);
        } else if (fgMode == ColorMode.INDEXED) {
            sb.append("fg=idx(").append(fgIndexed).append(")");
        } else if (fgMode == ColorMode.RGB) {
            int r = (fgRgb >> 16) & 0xFF;
            int g = (fgRgb >> 8) & 0xFF;
            int b = fgRgb & 0xFF;
            sb.append("fg=rgb(").append(r).append(",").append(g).append(",").append(b).append(")");
        }
        
        // Background
        if (bgMode == ColorMode.NAMED) {
            sb.append(", bg=").append(background);
        } else if (bgMode == ColorMode.INDEXED) {
            sb.append(", bg=idx(").append(bgIndexed).append(")");
        } else if (bgMode == ColorMode.RGB) {
            int r = (bgRgb >> 16) & 0xFF;
            int g = (bgRgb >> 8) & 0xFF;
            int b = bgRgb & 0xFF;
            sb.append(", bg=rgb(").append(r).append(",").append(g).append(",").append(b).append(")");
        }
        
        // Attributes
        if (bold) sb.append(", bold");
        if (faint) sb.append(", faint");
        if (italic) sb.append(", italic");
        if (underline) sb.append(", underline");
        if (blink) sb.append(", blink");
        if (inverse) sb.append(", inverse");
        if (hidden) sb.append(", hidden");
        if (strikethrough) sb.append(", strikethrough");
        
        sb.append("]");
        return sb.toString();
    }
}