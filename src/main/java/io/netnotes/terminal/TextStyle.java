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
    public static final TextStyle BLACK = new TextStyle().color(Color.BLACK);
    public static final TextStyle RED = new TextStyle().color(Color.RED);
    public static final TextStyle GREEN = new TextStyle().color(Color.GREEN);
    public static final TextStyle YELLOW = new TextStyle().color(Color.YELLOW);
    public static final TextStyle BLUE = new TextStyle().color(Color.BLUE);
    public static final TextStyle MAGENTA = new TextStyle().color(Color.MAGENTA);
    public static final TextStyle CYAN = new TextStyle().color(Color.CYAN);
    public static final TextStyle WHITE = new TextStyle().color(Color.WHITE);

    // Bright foreground colors
    public static final TextStyle BRIGHT_BLACK = new TextStyle().color(Color.BRIGHT_BLACK);
    public static final TextStyle BRIGHT_RED = new TextStyle().color(Color.BRIGHT_RED);
    public static final TextStyle BRIGHT_GREEN = new TextStyle().color(Color.BRIGHT_GREEN);
    public static final TextStyle BRIGHT_YELLOW = new TextStyle().color(Color.BRIGHT_YELLOW);
    public static final TextStyle BRIGHT_BLUE = new TextStyle().color(Color.BRIGHT_BLUE);
    public static final TextStyle BRIGHT_MAGENTA = new TextStyle().color(Color.BRIGHT_MAGENTA);
    public static final TextStyle BRIGHT_CYAN = new TextStyle().color(Color.BRIGHT_CYAN);
    public static final TextStyle BRIGHT_WHITE = new TextStyle().color(Color.BRIGHT_WHITE);

    public static final TextStyle HEADER = new TextStyle().color(Color.BRIGHT_WHITE).bold();
    public static final TextStyle SUBHEADER = new TextStyle().color(Color.BRIGHT_CYAN);
    public static final TextStyle TITLE = new TextStyle().color(Color.BRIGHT_YELLOW).bold();


    // UI Component styles - Interactive
    public static final TextStyle SELECTED = new TextStyle().inverse();
    public static final TextStyle FOCUSED = new TextStyle().color(Color.BRIGHT_WHITE).bold();
    public static final TextStyle DISABLED = new TextStyle().color(Color.BRIGHT_BLACK);
    public static final TextStyle ACTIVE = new TextStyle().color(Color.BRIGHT_GREEN);
    public static final TextStyle INACTIVE = new TextStyle().color(Color.BRIGHT_BLACK);

    // UI Component styles - Borders
    public static final TextStyle BORDER = new TextStyle().color(Color.BRIGHT_BLACK);
    public static final TextStyle BORDER_FOCUSED = new TextStyle().color(Color.BRIGHT_CYAN);
    public static final TextStyle BORDER_ERROR = new TextStyle().color(Color.RED);

    // Status indicators
    public static final TextStyle STATUS_OK = new TextStyle().color(Color.GREEN);
    public static final TextStyle STATUS_WARN = new TextStyle().color(Color.YELLOW);
    public static final TextStyle STATUS_ERROR = new TextStyle().color(Color.RED).bold();
    public static final TextStyle STATUS_INFO = new TextStyle().color(Color.CYAN);

    // Data display
    public static final TextStyle LABEL = new TextStyle().color(Color.BRIGHT_BLACK);
    public static final TextStyle VALUE = new TextStyle().color(Color.BRIGHT_WHITE);
    public static final TextStyle EMPHASIZED = new TextStyle().bold();
    public static final TextStyle DEEMPHASIZED = new TextStyle().faint();

    // Backgrounds - Common solid colors
    public static final TextStyle BG_BLACK = new TextStyle().bgColor(Color.BLACK);
    public static final TextStyle BG_RED = new TextStyle().bgColor(Color.RED);
    public static final TextStyle BG_GREEN = new TextStyle().bgColor(Color.GREEN);
    public static final TextStyle BG_YELLOW = new TextStyle().bgColor(Color.YELLOW);
    public static final TextStyle BG_BLUE = new TextStyle().bgColor(Color.BLUE);
    public static final TextStyle BG_MAGENTA = new TextStyle().bgColor(Color.MAGENTA);
    public static final TextStyle BG_CYAN = new TextStyle().bgColor(Color.CYAN);
    public static final TextStyle BG_WHITE = new TextStyle().bgColor(Color.WHITE);

    // Common color combinations
    public static final TextStyle WHITE_ON_BLUE = new TextStyle().color(Color.WHITE).bgColor(Color.BLUE);
    public static final TextStyle WHITE_ON_RED = new TextStyle().color(Color.WHITE).bgColor(Color.RED);
    public static final TextStyle WHITE_ON_GREEN = new TextStyle().color(Color.WHITE).bgColor(Color.GREEN);
    public static final TextStyle BLACK_ON_WHITE = new TextStyle().color(Color.BLACK).bgColor(Color.WHITE);
    public static final TextStyle BLACK_ON_YELLOW = new TextStyle().color(Color.BLACK).bgColor(Color.YELLOW);

    // Menu and list styles
    public static final TextStyle MENU_ITEM = new TextStyle().color(Color.WHITE);
    public static final TextStyle MENU_SELECTED = new TextStyle().color(Color.BLACK).bgColor(Color.BRIGHT_CYAN);
    public static final TextStyle MENU_DISABLED = new TextStyle().color(Color.BRIGHT_BLACK);
    public static final TextStyle MENU_HEADER = new TextStyle().color(Color.BRIGHT_YELLOW).bold();

    // Form styles
    public static final TextStyle INPUT = new TextStyle().color(Color.WHITE);
    public static final TextStyle INPUT_FOCUSED = new TextStyle().color(Color.BRIGHT_WHITE).bold();
    public static final TextStyle INPUT_ERROR = new TextStyle().color(Color.RED);
    public static final TextStyle PLACEHOLDER = new TextStyle().color(Color.BRIGHT_BLACK).italic();

    // Button styles
    public static final TextStyle BUTTON = new TextStyle().color(Color.WHITE).bgColor(Color.BLUE);
    public static final TextStyle BUTTON_FOCUSED = new TextStyle().color(Color.BLACK).bgColor(Color.BRIGHT_CYAN).bold();
    public static final TextStyle BUTTON_DISABLED = new TextStyle().color(Color.BRIGHT_BLACK).bgColor(Color.BLACK);
    public static final TextStyle BUTTON_PRIMARY = new TextStyle().color(Color.WHITE).bgColor(Color.BLUE).bold();
    public static final TextStyle BUTTON_SUCCESS = new TextStyle().color(Color.WHITE).bgColor(Color.GREEN).bold();
    public static final TextStyle BUTTON_DANGER = new TextStyle().color(Color.WHITE).bgColor(Color.RED).bold();

    // Progress bar styles
    public static final TextStyle PROGRESS_FILLED = new TextStyle().color(Color.GREEN).bgColor(Color.GREEN);
    public static final TextStyle PROGRESS_EMPTY = new TextStyle().color(Color.BRIGHT_BLACK);
    public static final TextStyle PROGRESS_TEXT = new TextStyle().color(Color.WHITE).bold();

    // Table styles
    public static final TextStyle TABLE_HEADER = new TextStyle().color(Color.BRIGHT_WHITE).bold();
    public static final TextStyle TABLE_ROW_EVEN = new TextStyle().color(Color.WHITE);
    public static final TextStyle TABLE_ROW_ODD = new TextStyle().color(Color.BRIGHT_BLACK);
    public static final TextStyle TABLE_SELECTED = new TextStyle().inverse();
    public static final TextStyle TABLE_BORDER = new TextStyle().color(Color.BRIGHT_BLACK);

    // Notification styles
    public static final TextStyle NOTIFICATION_INFO = new TextStyle().color(Color.BLACK).bgColor(Color.CYAN);
    public static final TextStyle NOTIFICATION_SUCCESS = new TextStyle().color(Color.BLACK).bgColor(Color.GREEN);
    public static final TextStyle NOTIFICATION_WARNING = new TextStyle().color(Color.BLACK).bgColor(Color.YELLOW);
    public static final TextStyle NOTIFICATION_ERROR = new TextStyle().color(Color.WHITE).bgColor(Color.RED).bold();

    // Link/Reference styles
    public static final TextStyle LINK = new TextStyle().color(Color.BRIGHT_BLUE).underline();
    public static final TextStyle LINK_VISITED = new TextStyle().color(Color.MAGENTA).underline();
    public static final TextStyle LINK_HOVER = new TextStyle().color(Color.BRIGHT_CYAN).underline().bold();


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

    /**
     * Create a copy of a base style with modifications
     * This allows for style reuse without modifying the original
     */
    public static TextStyle from(TextStyle base) {
        return base.copy();
    }

    /**
     * Quick style with just foreground color
     */
    public static TextStyle fg(Color color) {
        return new TextStyle().color(color);
    }

    /**
     * Quick style with just background color
     */
    public static TextStyle bg(Color color) {
        return new TextStyle().bgColor(color);
    }

    /**
     * Quick style with foreground and background
     */
    public static TextStyle colors(Color fg, Color bg) {
        return new TextStyle().color(fg).bgColor(bg);
    }


    /**
     * Create a style with only bold attribute
     */
    public static TextStyle bold(Color color) {
        return new TextStyle().color(color).bold();
    }

    /**
     * Create a style with only italic attribute
     */
    public static TextStyle italic(Color color) {
        return new TextStyle().color(color).italic();
    }

    /**
     * Create a style with only underline attribute
     */
    public static TextStyle underline(Color color) {
        return new TextStyle().color(color).underline();
    }

    /**
     * Create inverted style with specific color
     */
    public static TextStyle inverse(Color color) {
        return new TextStyle().color(color).inverse();
    }

    /**
     * Create grayscale style from indexed palette (232-255)
     * @param level 0-23 (0=darkest, 23=lightest)
     */
    public static TextStyle grayscale(int level) {
        if (level < 0 || level > 23) {
            throw new IllegalArgumentException("Grayscale level must be 0-23");
        }
        return new TextStyle().fgIndexed(232 + level);
    }

    /**
     * Create grayscale background from indexed palette
     */
    public static TextStyle grayscaleBg(int level) {
        if (level < 0 || level > 23) {
            throw new IllegalArgumentException("Grayscale level must be 0-23");
        }
        return new TextStyle().bgIndexed(232 + level);
    }

    /**
     * Create style from 6x6x6 RGB cube (16-231 in 256-color palette)
     * @param r 0-5 red level
     * @param g 0-5 green level
     * @param b 0-5 blue level
     */
    public static TextStyle cube(int r, int g, int b) {
        if (r < 0 || r > 5 || g < 0 || g > 5 || b < 0 || b > 5) {
            throw new IllegalArgumentException("Cube values must be 0-5");
        }
        int index = 16 + (r * 36) + (g * 6) + b;
        return new TextStyle().fgIndexed(index);
    }

    /**
     * Create background style from RGB cube
     */
    public static TextStyle cubeBg(int r, int g, int b) {
        if (r < 0 || r > 5 || g < 0 || g > 5 || b < 0 || b > 5) {
            throw new IllegalArgumentException("Cube values must be 0-5");
        }
        int index = 16 + (r * 36) + (g * 6) + b;
        return new TextStyle().bgIndexed(index);
    }

    // ===== PART 3: ADD CHAINABLE MODIFIERS (add as new methods) =====

    /**
     * Create a new style by adding bold to this style
     * Does not modify the original
     */
    public TextStyle withBold() {
        return this.copy().bold();
    }

    /**
     * Create a new style by adding italic to this style
     */
    public TextStyle withItalic() {
        return this.copy().italic();
    }

    /**
     * Create a new style by adding underline to this style
     */
    public TextStyle withUnderline() {
        return this.copy().underline();
    }

    /**
     * Create a new style by adding faint to this style
     */
    public TextStyle withFaint() {
        return this.copy().faint();
    }

    /**
     * Create a new style by adding inverse to this style
     */
    public TextStyle withInverse() {
        return this.copy().inverse();
    }

    /**
     * Create a new style by adding strikethrough to this style
     */
    public TextStyle withStrikethrough() {
        return this.copy().strikethrough();
    }

    /**
     * Create a new style by adding blink to this style
     */
    public TextStyle withBlink() {
        return this.copy().blink();
    }

    /**
     * Create a new style by adding hidden to this style
     */
    public TextStyle withHidden() {
        return this.copy().hidden();
    }

    /**
     * Create a new style with different foreground color
     */
    public TextStyle withColor(Color color) {
        return this.copy().color(color);
    }

    /**
     * Create a new style with different background color
     */
    public TextStyle withBgColor(Color bgColor) {
        return this.copy().bgColor(bgColor);
    }

    /**
     * Create a new style with both colors changed
     */
    public TextStyle withColors(Color fg, Color bg) {
        return this.copy().color(fg).bgColor(bg);
    }

    /**
     * Create a new style with RGB foreground
     */
    public TextStyle withFgRgb(int r, int g, int b) {
        return this.copy().fgRgb(r, g, b);
    }

    /**
     * Create a new style with RGB background
     */
    public TextStyle withBgRgb(int r, int g, int b) {
        return this.copy().bgRgb(r, g, b);
    }

    /**
     * Create a new style with indexed foreground
     */
    public TextStyle withFgIndexed(int index) {
        return this.copy().fgIndexed(index);
    }

    /**
     * Create a new style with indexed background
     */
    public TextStyle withBgIndexed(int index) {
        return this.copy().bgIndexed(index);
    }

    // ===== PART 4: ADD UTILITY METHODS =====

    /**
     * Check if this style has any attributes set (not default/normal)
     */
    public boolean hasAttributes() {
        return bold || faint || italic || underline || blink || 
            inverse || hidden || strikethrough;
    }

    /**
     * Check if this style has any color set (not default)
     */
    public boolean hasColor() {
        return (fgMode == ColorMode.NAMED && foreground != Color.DEFAULT) ||
            (fgMode == ColorMode.INDEXED && fgIndexed != -1) ||
            (fgMode == ColorMode.RGB && fgRgb != -1) ||
            (bgMode == ColorMode.NAMED && background != Color.DEFAULT) ||
            (bgMode == ColorMode.INDEXED && bgIndexed != -1) ||
            (bgMode == ColorMode.RGB && bgRgb != -1);
    }

    /**
     * Check if this style has foreground color set
     */
    public boolean hasForeground() {
        return (fgMode == ColorMode.NAMED && foreground != Color.DEFAULT) ||
            (fgMode == ColorMode.INDEXED && fgIndexed != -1) ||
            (fgMode == ColorMode.RGB && fgRgb != -1);
    }

    /**
     * Check if this style has background color set
     */
    public boolean hasBackground() {
        return (bgMode == ColorMode.NAMED && background != Color.DEFAULT) ||
            (bgMode == ColorMode.INDEXED && bgIndexed != -1) ||
            (bgMode == ColorMode.RGB && bgRgb != -1);
    }

    /**
     * Check if this is the default style (no modifications)
     */
    public boolean isDefault() {
        return !hasColor() && !hasAttributes();
    }

    /**
     * Reset all attributes but keep colors
     */
    public TextStyle clearAttributes() {
        this.bold = false;
        this.faint = false;
        this.italic = false;
        this.underline = false;
        this.blink = false;
        this.inverse = false;
        this.hidden = false;
        this.strikethrough = false;
        return this;
    }

    /**
     * Reset colors but keep attributes
     */
    public TextStyle clearColors() {
        this.fgMode = ColorMode.NAMED;
        this.foreground = Color.DEFAULT;
        this.fgIndexed = -1;
        this.fgRgb = -1;
        this.bgMode = ColorMode.NAMED;
        this.background = Color.DEFAULT;
        this.bgIndexed = -1;
        this.bgRgb = -1;
        return this;
    }

    /**
     * Merge another style into this one
     * Other style's non-default values override this style's values
     */
    public TextStyle merge(TextStyle other) {
        if (other == null) return this;
        
        // Merge foreground
        if (other.hasForeground()) {
            this.fgMode = other.fgMode;
            this.foreground = other.foreground;
            this.fgIndexed = other.fgIndexed;
            this.fgRgb = other.fgRgb;
        }
        
        // Merge background
        if (other.hasBackground()) {
            this.bgMode = other.bgMode;
            this.background = other.background;
            this.bgIndexed = other.bgIndexed;
            this.bgRgb = other.bgRgb;
        }
        
        // Merge attributes (only if set in other)
        if (other.bold) this.bold = true;
        if (other.faint) this.faint = true;
        if (other.italic) this.italic = true;
        if (other.underline) this.underline = true;
        if (other.blink) this.blink = true;
        if (other.inverse) this.inverse = true;
        if (other.hidden) this.hidden = true;
        if (other.strikethrough) this.strikethrough = true;
        
        return this;
    }

    /**
     * Create a merged style without modifying either original
     */
    public static TextStyle merged(TextStyle base, TextStyle overlay) {
        return base.copy().merge(overlay);
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