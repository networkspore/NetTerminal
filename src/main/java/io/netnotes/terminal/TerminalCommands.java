package io.netnotes.terminal;
import io.netnotes.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

/**
 * Terminal command factory methods
 * 
 * DESIGN PHILOSOPHY:
 * - Supports both cursor-relative and absolute positioning
 * - Component-focused commands for modern UI rendering
 * - All region operations use TerminalRectangle for clipping safety
 * 
 * Coordinate System:
 *   x = horizontal position (0 = left)
 *   y = vertical position (0 = top)
 *   
 * All parameters follow the convention: x before y
 * Regions are represented using TerminalRectangle objects
 */
public class TerminalCommands {
    public static final String PRESS_ANY_KEY = "Press any key to continue...";

    // Command type constants
    public static final NoteBytesReadOnly TERMINAL_CLEAR = 
        new NoteBytesReadOnly("clear");
    public static final NoteBytesReadOnly TERMINAL_PRINT = 
        new NoteBytesReadOnly("print");
    public static final NoteBytesReadOnly TERMINAL_PRINTLN = 
        new NoteBytesReadOnly("println");
    public static final NoteBytesReadOnly TERMINAL_PRINT_AT = 
        new NoteBytesReadOnly("print_at");    
    public static final NoteBytesReadOnly TERMINAL_MOVE_CURSOR = 
        new NoteBytesReadOnly("move_cursor");
    public static final NoteBytesReadOnly TERMINAL_SHOW_CURSOR = 
        new NoteBytesReadOnly("show_cursor");
    public static final NoteBytesReadOnly TERMINAL_HIDE_CURSOR = 
        new NoteBytesReadOnly("hide_cursor");
    public static final NoteBytesReadOnly TERMINAL_CLEAR_LINE = 
        new NoteBytesReadOnly("clear_line");
    public static final NoteBytesReadOnly TERMINAL_CLEAR_LINE_AT = 
        new NoteBytesReadOnly("clear_line_at");
    public static final NoteBytesReadOnly TERMINAL_CLEAR_REGION = 
        new NoteBytesReadOnly("clear_region");
    public static final NoteBytesReadOnly TERMINAL_DRAW_BOX = 
        new NoteBytesReadOnly("draw_box");
    public static final NoteBytesReadOnly TERMINAL_DRAW_HLINE = 
        new NoteBytesReadOnly("draw_hline");
    public static final NoteBytesReadOnly TERMINAL_DRAW_VLINE = 
        new NoteBytesReadOnly("draw_vline");
    public static final NoteBytesReadOnly TERMINAL_FILL_REGION = 
        new NoteBytesReadOnly("fill_region");
    public static final NoteBytesReadOnly TERMINAL_DRAW_BORDERED_TEXT = 
        new NoteBytesReadOnly("draw_bordered_text");
    public static final NoteBytesReadOnly TERMINAL_DRAW_PANEL = 
        new NoteBytesReadOnly("draw_panel");
    public static final NoteBytesReadOnly TERMINAL_DRAW_BUTTON = 
        new NoteBytesReadOnly("draw_button");
    public static final NoteBytesReadOnly TERMINAL_DRAW_PROGRESS_BAR = 
        new NoteBytesReadOnly("draw_progress_bar");
    public static final NoteBytesReadOnly TERMINAL_DRAW_TEXT_BLOCK = 
        new NoteBytesReadOnly("draw_text_block");
    public static final NoteBytesReadOnly TERMINAL_SHADE_REGION = 
        new NoteBytesReadOnly("shade_region");

    // Additional parameter constants
    public static final NoteBytesReadOnly BOX_STYLE = 
        new NoteBytesReadOnly("box_style");
    public static final NoteBytesReadOnly CODE_POINT =
        new NoteBytesReadOnly("code_point");
    public static final NoteBytesReadOnly PROGRESS =
        new NoteBytesReadOnly("progress");
    public static final NoteBytesReadOnly ALIGN =
        new NoteBytesReadOnly("align");
    public static final NoteBytesReadOnly SHADE_CHAR =
        new NoteBytesReadOnly("shade_char");
    public static final NoteBytesReadOnly SELECTED =
        new NoteBytesReadOnly("selected");
    public static final NoteBytesReadOnly TITLE_POS = 
        new NoteBytesReadOnly("title_pos");
    public static final NoteBytesReadOnly RENDER_REGION = 
        new NoteBytesReadOnly("render_region");

    // ===== SCREEN OPERATIONS =====
    
    /**
     * Clear entire screen
     */
    public static NoteBytesObject clear() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_CLEAR)
        });
    }

    // ===== TEXT OUTPUT =====
    
    /**
     * Print text at cursor position
     */
    public static NoteBytesObject print(String text, TextStyle style) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_PRINT),
            new NoteBytesPair(Keys.TEXT, text),
            new NoteBytesPair(Keys.STYLE, style == null ? TextStyle.NORMAL_BYTES : style.toNoteBytes())
        });
    }

    /**
     * Print line (with newline) at cursor position
     */
    public static NoteBytesObject println(String text, TextStyle style) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_PRINTLN),
            new NoteBytesPair(Keys.TEXT, text),
            new NoteBytesPair(Keys.STYLE, style == null ? TextStyle.NORMAL_BYTES : style.toNoteBytes())
        });
    }

    /**
     * Print text at specific position
     * @param x horizontal position
     * @param y vertical position
     * @param text text to print
     * @param style text style
     */
    public static NoteBytesObject printAt(int x, int y, String text, TextStyle style) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_PRINT_AT),
            new NoteBytesPair(Keys.X, x),
            new NoteBytesPair(Keys.Y, y),
            new NoteBytesPair(Keys.TEXT, text),
            new NoteBytesPair(Keys.STYLE, style == null ? TextStyle.NORMAL_BYTES : style.toNoteBytes())
        });
    }

    // ===== TEXT ALIGNMENT HELPERS =====
    
    public enum Alignment {
        LEFT, CENTER, RIGHT
    }
    
    /**
     * Print text centered vertically within a region
     * @param region the bounding region
     * @param x horizontal position
     * @param text text to print
     * @param style text style
     */
    public static NoteBytesObject printAtCenterY(TerminalRectangle region, int x, String text, TextStyle style) {
        int centerY = region.getY() + (region.getHeight() / 2);
        return printAt(x, centerY, text, style);
    }

    /**
     * Print text centered horizontally within a region
     * @param region the bounding region
     * @param y vertical position
     * @param text text to print
     * @param style text style
     */
    public static NoteBytesObject printAtCenterX(TerminalRectangle region, int y, String text, TextStyle style) {
        int halfText = text.length() / 2;
        int centerX = region.getX() + (region.getWidth() / 2) - halfText;
        return printAt(Math.max(region.getX(), centerX), y, text, style);
    }

    /**
     * Print text centered both horizontally and vertically within a region
     * @param region the bounding region
     * @param text text to print
     * @param style text style
     */
    public static NoteBytesObject printAtCenter(TerminalRectangle region, String text, TextStyle style) {
        int halfText = text.length() / 2;
        int centerX = region.getX() + (region.getWidth() / 2) - halfText;
        int centerY = region.getY() + (region.getHeight() / 2);
        return printAt(Math.max(region.getX(), centerX), centerY, text, style);
    }

    // ===== CURSOR OPERATIONS =====
    
    /**
     * Move cursor to position
     * @param x horizontal position
     * @param y vertical position
     */
    public static NoteBytesObject moveCursor(int x, int y) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_MOVE_CURSOR),
            new NoteBytesPair(Keys.X, x),
            new NoteBytesPair(Keys.Y, y)
        });
    }

    /**
     * Show cursor
     */
    public static NoteBytesObject showCursor() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_SHOW_CURSOR)
        });
    }

    /**
     * Hide cursor
     */
    public static NoteBytesObject hideCursor() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_HIDE_CURSOR)
        });
    }

    // ===== CLEAR OPERATIONS =====
    
    /**
     * Clear line at cursor position
     */
    public static NoteBytesObject clearLine() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_CLEAR_LINE)
        });
    }

    /**
     * Clear specific line
     * @param y vertical position of line
     */
    public static NoteBytesObject clearLineAt(int y) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_CLEAR_LINE_AT),
            new NoteBytesPair(Keys.Y, y)
        });
    }

    /**
     * Clear rectangular region
     * @param region the region to clear
     */
    public static NoteBytesObject clearRegion(TerminalRectangle region) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_CLEAR_REGION),
            new NoteBytesPair(Keys.REGION, region.toNoteBytes())
        });
    }

    // ===== DRAWING OPERATIONS =====
    
    /**
     * Draw box with border
     * @param region the box bounds
     * @param title optional title (can be null)
     * @param boxStyle box border style
     * @param style text style for border
     */
    public static NoteBytesObject drawBox(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        String title, 
        Position titlePosition, 
        BoxStyle boxStyle, 
        TextStyle style
    ) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_DRAW_BOX);
        map.put(Keys.REGION, region.toNoteBytes());
        if(renderRegion != null){
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        }
        if(title != null && title.isEmpty()){
            map.put(Keys.TITLE, title);
            if(titlePosition != null){
                map.put(TITLE_POS, titlePosition.name());
            }
        }
        map.put(BOX_STYLE, boxStyle.name());
        if(style != null){
            map.put(Keys.STYLE, style.toNoteBytes());
        }
        return map.toNoteBytes();
    }


    /**
     * Draw horizontal line
     * @param x starting horizontal position
     * @param y vertical position
     * @param length line length
     * @param style text style
     */
    public static NoteBytesObject drawHLine(int x, int y, int length, TextStyle style) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_DRAW_HLINE),
            new NoteBytesPair(Keys.X, x),
            new NoteBytesPair(Keys.Y, y),
            new NoteBytesPair(Keys.LENGTH, length),
            new NoteBytesPair(Keys.STYLE, style == null ? TextStyle.NORMAL_BYTES : style.toNoteBytes())
        });
    }
    
    /**
     * Draw horizontal line (default style)
     */
    public static NoteBytesObject drawHLine(int x, int y, int length) {
        return drawHLine(x, y, length, TextStyle.NORMAL);
    }

    /**
     * Draw vertical line
     * @param x horizontal position
     * @param y starting vertical position
     * @param length line length
     * @param style text style
     */
    public static NoteBytesObject drawVLine(int x, int y, int length, TextStyle style) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_DRAW_VLINE),
            new NoteBytesPair(Keys.X, x),
            new NoteBytesPair(Keys.Y, y),
            new NoteBytesPair(Keys.LENGTH, length),
            new NoteBytesPair(Keys.STYLE, style == null ? TextStyle.NORMAL_BYTES : style.toNoteBytes())
        });
    }
    
    /**
     * Draw vertical line (default style)
     */
    public static NoteBytesObject drawVLine(int x, int y, int length) {
        return drawVLine(x, y, length, TextStyle.NORMAL);
    }

    // ===== FILL OPERATIONS =====
    
    /**
     * Fill rectangular region with character
     * @param region the region to fill
     * @param cp Unicode code point to fill with
     * @param style text style
     */
    public static NoteBytesObject fillRegion(TerminalRectangle region, int cp, TextStyle style) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_FILL_REGION),
            new NoteBytesPair(Keys.REGION, region.toNoteBytes()),
            new NoteBytesPair(CODE_POINT, cp),
            new NoteBytesPair(Keys.STYLE, style == null ? TextStyle.NORMAL_BYTES : style.toNoteBytes())
        });
    }

    public static NoteBytesObject fillRegion(TerminalRectangle region, String character, TextStyle style) {
        return fillRegion(region, character.codePointAt(0), style);
    }


    /**
     * Fill rectangular region with character
     * @param region the region to fill
     * @param fillChar character to fill with
     * @param style text style
     */
    public static NoteBytesObject fillRegion(TerminalRectangle region, char fillChar, TextStyle style) {
        return fillRegion(region, (int) fillChar, style);
    }
    
    /**
     * Fill region with space (for background color)
     * @param region the region to fill
     * @param style text style (typically just background color)
     */
    public static NoteBytesObject fillBackground(TerminalRectangle region, TextStyle style) {
        return fillRegion(region, " ", style);
    }

    // ===== COMPONENT RENDERING HELPERS =====
    
    /**
     * Shade region using Unicode block characters
     * Useful for creating visual depth/shadows
     * @param region the region to shade
     * @param shadeChar Unicode shade character (░, ▒, ▓, or custom)
     * @param style text style
     */
    public static NoteBytesObject shadeRegion(TerminalRectangle region,TerminalRectangle renderRegion, int shadeChar, TextStyle style) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_SHADE_REGION);
        if(renderRegion != null){
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        } 
        map.put(Keys.REGION, region.toNoteBytes());
        map.put(SHADE_CHAR, shadeChar);
        if(style != null){
            map.put(Keys.STYLE, style.toNoteBytes());
        }
        return map.toNoteBytes();
    }
    
    /**
     * Common shade characters
     */
    public static final String SHADE_LIGHT = "░";   // U+2591
    public static final String SHADE_MEDIUM = "▒";  // U+2592
    public static final String SHADE_DARK = "▓";    // U+2593
    public static final String SHADE_FULL = "█";    // U+2588
    
    /**
     * Draw a panel - box with filled background
     * @param region panel bounds
     * @param title optional title
     * @param boxStyle border style
     * @param borderStyle style for border
     * @param fillStyle style for background
     */
    public static NoteBytesObject drawPanel(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        String title, 
        Position titlePosition,
        BoxStyle boxStyle, 
        TextStyle borderStyle, 
        TextStyle fillStyle
    ) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_DRAW_PANEL);
        map.put(Keys.REGION, region.toNoteBytes());
        if(renderRegion != null){
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        }
        if(title != null && title.isEmpty()){
            map.put(Keys.TITLE, title);
            if(titlePosition != null){
                map.put(TITLE_POS, titlePosition.name());
            }
        }
        if(boxStyle != null){
            map.put(BOX_STYLE, boxStyle.name());
        }
        if(borderStyle != null){
            map.put(Keys.STYLE, borderStyle.toNoteBytes());
        }
        if(fillStyle != null){
            map.put(StyleConstants.BG_STYLE, fillStyle.toNoteBytes());
        }
        return map.toNoteBytes();
    }

 
    /**
     * Draw a button component
     * @param region button bounds
     * @param label button text
     * @param selected whether button is selected/focused
     * @param style button style
     */
    public static NoteBytesObject drawButton(TerminalRectangle region, TerminalRectangle renderRegion, String label, Position labelPos,
                                             boolean selected, TextStyle style) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_DRAW_PANEL);
        map.put(Keys.REGION, region.toNoteBytes());
        if(renderRegion != null){
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        }
        if(label != null && label.isEmpty()){
            map.put(Keys.TEXT, label);
            if(labelPos != null){
                map.put(TITLE_POS, labelPos.name());
            }
        }
        map.put(SELECTED, selected);
        if(style != null){
            map.put(Keys.STYLE, style.toNoteBytes());
        }
        return map.toNoteBytes();
    }


    /**
     * Draw a progress bar using block characters
     * Uses Unicode block characters for sub-character resolution
     * @param region progress bar bounds
     * @param progress value 0.0 to 1.0
     * @param style style for filled portion
     * @param emptyStyle style for empty portion
     */
    public static NoteBytesObject drawProgressBar(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        double progress,
        TextStyle style, 
        TextStyle emptyStyle
    ) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_DRAW_PROGRESS_BAR);
        map.put(Keys.REGION, region.toNoteBytes());
        if(renderRegion != null){
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        }
        map.put(PROGRESS, Math.max(0.0, Math.min(1.0, progress)));
        map.put(Keys.STYLE, style == null ? TextStyle.NORMAL_BYTES : style.toNoteBytes());
        map.put(StyleConstants.EMPTY_STYLE, emptyStyle.toNoteBytes());

        return map.toNoteBytes();
       
    }
    
    /**
     * Block characters for progress bars (1/8 resolution)
     */
    public static final String[] PROGRESS_BLOCKS = {
        " ",    // U+0020 (empty)
        "▏",    // U+258F (1/8)
        "▎",    // U+258E (2/8)
        "▍",    // U+258D (3/8)
        "▌",    // U+258C (4/8)
        "▋",    // U+258B (5/8)
        "▊",    // U+258A (6/8)
        "▉",    // U+2589 (7/8)
        "█"     // U+2588 (full)
    };
    
    /**
     * Draw text block with word wrapping
     * @param region bounds for text
     * @param text text to render (may contain newlines)
     * @param align text alignment
     * @param style text style
     */
    public static NoteBytesObject drawTextBlock(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        String text, 
        Alignment align, 
        TextStyle style
    ) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_DRAW_TEXT_BLOCK);
        map.put(Keys.REGION, region.toNoteBytes());
        if(renderRegion != null){
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        } 
        map.put(Keys.TEXT, text);
        map.put(ALIGN, align.name());
        if( style != null ){
            map.put(Keys.STYLE, style.toNoteBytes());
        }
        return map.toNoteBytes();
    }
    
    /**
     * Draw bordered text box (box + centered text)
     * @param region bounds
     * @param text text to display
     * @param boxStyle border style
     * @param textStyle style for text
     * @param borderStyle style for border
     */
    public static NoteBytesObject drawBorderedText(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        String text, 
        Position textPos,
        BoxStyle boxStyle, 
        TextStyle textStyle, 
        TextStyle borderStyle
    ) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_DRAW_BORDERED_TEXT);
        map.put(Keys.REGION, region.toNoteBytes());
        if(renderRegion != null){
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        }
        if(text != null && !text.isEmpty()){
            map.put(Keys.TEXT, text);
            if(textPos != null){
                map.put(TITLE_POS, textPos.name());
            }
        }
        if(boxStyle != null){
            map.put(BOX_STYLE, boxStyle.name());
        }
        if(textStyle != null){
            map.put(Keys.STYLE, textStyle.toNoteBytes());
        }
        if(borderStyle != null){
            map.put(StyleConstants.BORDER_STYLE, borderStyle.toNoteBytes());
        }
        return map.toNoteBytes();
    }

    // ===== PIXEL-LIKE RENDERING CONSTANTS =====
    
    /**
     * Quadrant characters for 2x2 "pixel" rendering within a single character cell
     * These allow basic bitmap-style rendering at 2x resolution
     */
    public static final String QUADRANT_UPPER_LEFT = "▘";      // U+2598
    public static final String QUADRANT_UPPER_RIGHT = "▝";     // U+259D
    public static final String QUADRANT_LOWER_LEFT = "▖";      // U+2596
    public static final String QUADRANT_LOWER_RIGHT = "▗";     // U+2597
    public static final String QUADRANT_UPPER_HALF = "▀";      // U+2580
    public static final String QUADRANT_LOWER_HALF = "▄";      // U+2584
    public static final String QUADRANT_LEFT_HALF = "▌";       // U+258C
    public static final String QUADRANT_RIGHT_HALF = "▐";      // U+2590
    
    /**
     * Sextant characters for 2x3 "pixel" rendering (6 sub-pixels per cell)
     * Added in Unicode 13.0 for legacy computing symbols
     */
    public static final String SEXTANT_1 = "🬀";     // U+1FB00 (upper-left)
    public static final String SEXTANT_2 = "🬁";     // U+1FB01 (upper-right)
    // ... more sextant chars available up to U+1FB3B
    
    /**
     * Box drawing light/heavy variants for visual weight
     */
    public static final String BOX_LIGHT_HORIZONTAL = "─";     // U+2500
    public static final String BOX_HEAVY_HORIZONTAL = "━";     // U+2501
    public static final String BOX_LIGHT_VERTICAL = "│";       // U+2502
    public static final String BOX_HEAVY_VERTICAL = "┃";       // U+2503
    
    /**
     * Eighth block characters for high-resolution bars
     */
    public static final String LOWER_ONE_EIGHTH_BLOCK = "▁";   // U+2581
    public static final String LOWER_TWO_EIGHTHS_BLOCK = "▂";  // U+2582
    public static final String LOWER_THREE_EIGHTHS_BLOCK = "▃"; // U+2583
    public static final String LOWER_FOUR_EIGHTHS_BLOCK = "▄";  // U+2584
    public static final String LOWER_FIVE_EIGHTHS_BLOCK = "▅";  // U+2585
    public static final String LOWER_SIX_EIGHTHS_BLOCK = "▆";   // U+2586
    public static final String LOWER_SEVEN_EIGHTHS_BLOCK = "▇"; // U+2587
    public static final String FULL_BLOCK = "█";                 // U+2588
    
    public static final String UPPER_ONE_EIGHTH_BLOCK = "▔";   // U+2594
    
    /**
     * Braille characters for 2x4 "pixel" rendering (8 dots per cell)
     * Allows very high resolution "graphics" in text mode
     * Unicode range: U+2800 - U+28FF
     * 
     * Dot numbering (ISO 11548-1):
     *   1 4
     *   2 5
     *   3 6
     *   7 8
     */
    public static final String BRAILLE_BLANK = "⠀";            // U+2800
    
    /**
     * Create a Braille character from dot pattern
     * @param dots boolean array of 8 dots (true = raised)
     * @return Unicode Braille character
     */
    public static String createBrailleChar(boolean[] dots) {
        if (dots.length != 8) {
            throw new IllegalArgumentException("Braille requires exactly 8 dots");
        }
        
        int pattern = 0;
        int[] bits = {0, 1, 2, 6, 3, 4, 5, 7};  // Braille Unicode encoding order
        
        for (int i = 0; i < 8; i++) {
            if (dots[i]) {
                pattern |= (1 << bits[i]);
            }
        }
        
        return Character.toString((char) (0x2800 + pattern));
    }

}