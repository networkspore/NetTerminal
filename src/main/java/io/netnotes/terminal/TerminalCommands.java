package io.netnotes.terminal;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.consoleRenderer.Cell;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.ui.Position;
import io.netnotes.engine.ui.TextAlignment;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.NoteIntegerArray;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.collections.NoteBytesPair;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;

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
    
    
    // ==== parameter constants ====
    public static final NoteBytesReadOnly PEAK_STYLE = 
        new NoteBytesReadOnly("peak_style");
    public static final NoteBytesReadOnly LINE_STYLE = 
        new NoteBytesReadOnly("line_style");
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
    public static final NoteBytesReadOnly SCROLL_POS =
        new NoteBytesReadOnly("scroll_pos");
    public static final NoteBytesReadOnly SHOW_ARROWS =
        new NoteBytesReadOnly("show_arrows");
    public static final NoteBytesReadOnly TRACK_STYLE =
        new NoteBytesReadOnly("track_style");
    public static final NoteBytesReadOnly THUMB_STYLE =
        new NoteBytesReadOnly("thumb_style");

    // === Command type constants ===
    public static final NoteBytesReadOnly TERMINAL_DRAW_SEXTANT_BITMAP =
        new NoteBytesReadOnly("draw_sextant_bitmap");
    public static final NoteBytesReadOnly TERMINAL_DRAW_BRAILLE_BITMAP =
        new NoteBytesReadOnly("draw_braille_bitmap");
    public static final NoteBytesReadOnly TERMINAL_DRAW_BITMAP =
        new NoteBytesReadOnly("draw_bitmap");
    public static final NoteBytesReadOnly TERMINAL_DRAW_SCROLLBAR =
        new NoteBytesReadOnly("draw_scrollbar");
    public static final NoteBytesReadOnly TERMINAL_DRAW_SPARKLINE =
        new NoteBytesReadOnly("draw_sparkline");
    public static final NoteBytesReadOnly TERMINAL_CLEAR = 
        new NoteBytesReadOnly("clear");
    public static final NoteBytesReadOnly TERMINAL_PRINT = 
        new NoteBytesReadOnly("print");
    public static final NoteBytesReadOnly TERMINAL_PRINTLN = 
        new NoteBytesReadOnly("println");
    public static final NoteBytesReadOnly TERMINAL_PRINT_AT = 
        new NoteBytesReadOnly("print_at");    
    public static final NoteBytesReadOnly TERMINAL_PRINT_CODEPOINT_AT =
        new NoteBytesReadOnly("print_codepoint_at");
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
    public static final NoteBytesReadOnly TERMINAL_DRAW_TABLE_BORDER =
        new NoteBytesReadOnly("draw_table_border");
    public static final NoteBytesReadOnly H_SEPARATORS =
        new NoteBytesReadOnly("h_separators");   // comma-delimited Y positions
    public static final NoteBytesReadOnly V_SEPARATORS =
        new NoteBytesReadOnly("v_separators");   // comma-delimited X positions



  
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
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_PRINT);
        map.put(Keys.TEXT, text);
        if (style != null) {
            map.put(Keys.STYLE, style.toNoteBytes());
        }
        return map.toNoteBytes();
    }

    /**
     * Print line (with newline) at cursor position
     */
    public static NoteBytesObject println(String text, TextStyle style) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_PRINTLN);
        map.put(Keys.TEXT, text);
        if (style != null) {
            map.put(Keys.STYLE, style.toNoteBytes());
        }
        return map.toNoteBytes();
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

    /**
     * Print a single codepoint at a specific position.
     * @param x horizontal position
     * @param y vertical position
     * @param codePoint Unicode codepoint
     * @param style text style
     */
    public static NoteBytesObject printCodePointAt(int x, int y, int codePoint, TextStyle style) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_PRINT_CODEPOINT_AT),
            new NoteBytesPair(Keys.X, x),
            new NoteBytesPair(Keys.Y, y),
            new NoteBytesPair(CODE_POINT, codePoint),
            new NoteBytesPair(Keys.STYLE, style == null ? TextStyle.NORMAL_BYTES : style.toNoteBytes())
        });
    }

    // ===== TEXT ALIGNMENT HELPERS =====
    
    
    
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
     * @param lineStyle box border style
     * @param style text style for border
     */
    public static NoteBytesObject drawBox(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        String title, 
        Position titlePosition, 
        LineStyle lineStyle, 
        TextStyle style
    ) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_DRAW_BOX);
        map.put(Keys.REGION, region.toNoteBytes());
        if(renderRegion != null){
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        }
        if(title != null && !title.isEmpty()){
            map.put(Keys.TITLE, title);
            if(titlePosition != null){
                map.put(TITLE_POS, titlePosition.name());
            }
        }
        if (lineStyle != null) {
            map.put(LINE_STYLE, lineStyle.name());
        }
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
    public static NoteBytes drawHLine(int x, int y, int length, TextStyle style, LineStyle lineStyle) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_DRAW_HLINE);
        map.put(Keys.X, x);
        map.put(Keys.Y, y);
        map.put(Keys.LENGTH, length);
        if (style != null) {
            map.put(Keys.STYLE, style.toNoteBytes());
        }
        if(lineStyle != null){
            map.put(LINE_STYLE, lineStyle.name());
        }
        return map.toNoteBytes();
    }
    
    /**
     * Draw horizontal line (default style)
     */
    public static NoteBytes drawHLine(int x, int y, int length) {
        return drawHLine(x, y, length, null, null);
    }

    /**
     * Draw vertical line
     * @param x horizontal position
     * @param y starting vertical position
     * @param length line length
     * @param style text style
     */
    public static NoteBytes drawVLine(int x, int y, int length, TextStyle style, LineStyle lineStyle) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_DRAW_VLINE);
        map.put(Keys.X, x);
        map.put(Keys.Y, y);
        map.put(Keys.LENGTH, length);
        if (style != null) {
            map.put(Keys.STYLE, style.toNoteBytes());
        }
        if(lineStyle != null){
            map.put(LINE_STYLE, lineStyle.name());
        }
        return map.toNoteBytes();
    }
    
    /**
     * Draw vertical line (default style)
     */
    public static NoteBytes drawVLine(int x, int y, int length) {
        return drawVLine(x, y, length, null, null);
    }

    // ===== FILL OPERATIONS =====
    
    /**
     * Fill rectangular region with character
     * @param region the region to fill
     * @param cp Unicode code point to fill with
     * @param style text style
     */
    public static NoteBytesObject fillRegion(TerminalRectangle region, TerminalRectangle renderRegion, int cp, TextStyle style) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_FILL_REGION);
        map.put(Keys.REGION, region.toNoteBytes());
        if(renderRegion != null){
            map.put(TerminalCommands.RENDER_REGION, renderRegion.toNoteBytes());
        }
        map.put(CODE_POINT, cp);
        if(style != null){
            map.put(Keys.STYLE, style.toNoteBytes());
        }
        return map.toNoteBytes();
       
    }

    public static NoteBytesObject fillRegion(TerminalRectangle region, TerminalRectangle renderRegion, String character, TextStyle style) {
        return fillRegion(region, renderRegion, character.codePointAt(0), style);
    }

    /**
     * Fill region with space (for background color)
     * @param region the region to fill
     * @param style text style (typically just background color)
     */
    public static NoteBytesObject fillBackground(TerminalRectangle region,TerminalRectangle renderRegion, TextStyle style) {
        return fillRegion(region, renderRegion, Cell.SPACE_STR, style);
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
     * @param lineStyle border style
     * @param borderStyle style for border
     * @param fillStyle style for background
     */
    public static NoteBytes drawPanel(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        String title, 
        Position titlePosition,
        LineStyle lineStyle, 
        TextStyle borderStyle, 
        TextStyle fillStyle
    ) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_DRAW_PANEL);
        map.put(Keys.REGION, region.toNoteBytes());
        
        if (renderRegion != null) {
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        }
        if (title != null && !title.isEmpty()) {  // FIXED: was title.isEmpty()
            map.put(Keys.TITLE, title);
            if (titlePosition != null) {
                map.put(TITLE_POS, titlePosition.name());
            }
        }
        if (lineStyle != null) {
            map.put(LINE_STYLE, lineStyle.name());
        }
        if (borderStyle != null) {
            map.put(Keys.STYLE, borderStyle.toNoteBytes());
        }
        if (fillStyle != null) {
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
    public static NoteBytes drawButton(
        TerminalRectangle region, 
        TerminalRectangle renderRegion, 
        String label, 
        Position labelPos,
        boolean selected, 
        TextStyle style
    ) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_DRAW_BUTTON);
        map.put(Keys.REGION, region.toNoteBytes());
        
        if (renderRegion != null) {
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        }
        if (label != null && !label.isEmpty()) {
            map.put(Keys.TEXT, label);
            if (labelPos != null) {
                map.put(TITLE_POS, labelPos.name());
            }
        }
        map.put(SELECTED, selected);
        if (style != null) {
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
        Cell.SPACE_STR,    // U+0020 (empty)
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
        TextAlignment align, 
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
     * @param lineStyle border style
     * @param textStyle style for text
     * @param borderStyle style for border
     */
    public static NoteBytesObject drawBorderedText(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        String text, 
        Position textPos,
        LineStyle lineStyle, 
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
        if(lineStyle != null){
            map.put(LINE_STYLE, lineStyle.name());
        }
        if(textStyle != null){
            map.put(Keys.STYLE, textStyle.toNoteBytes());
        }
        if(borderStyle != null){
            map.put(StyleConstants.BORDER_STYLE, borderStyle.toNoteBytes());
        }
        return map.toNoteBytes();
    }

    public static NoteBytesObject drawTableBorder(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        LineStyle          lineStyle,
        TextStyle         style,
        int[]             hSeparators,
        int[]             vSeparators,
        String            title,
        Position          titlePos
    ) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD,    TERMINAL_DRAW_TABLE_BORDER);
        map.put(Keys.REGION, region.toNoteBytes());

        if (renderRegion != null) {
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        }
        if (lineStyle != null) {
            map.put(LINE_STYLE, lineStyle.name());
        }
        if (style != null) {
            map.put(Keys.STYLE, style.toNoteBytes());
        }
        if (hSeparators != null && hSeparators.length > 0) {
            map.put(H_SEPARATORS, new NoteIntegerArray(hSeparators));
        }
        if (vSeparators != null && vSeparators.length > 0) {
            map.put(V_SEPARATORS, new NoteIntegerArray(vSeparators));
        }
        if (title != null && !title.isEmpty()) {
            map.put(Keys.TITLE, title);
            if (titlePos != null) {
                map.put(TITLE_POS, titlePos.name());
            }
        }
        return map.toNoteBytes();
    }

    public static NoteBytesObject drawTableRowBorder(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        LineStyle          lineStyle,
        TextStyle         style,
        int...            hSeparators
    ) {
        return drawTableBorder(region, renderRegion, lineStyle, style,
            hSeparators, null, null, null);
    }

    public static NoteBytesObject drawTableColBorder(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        LineStyle          lineStyle,
        TextStyle         style,
        int...            vSeparators
    ) {
        return drawTableBorder(region, renderRegion, lineStyle, style,
            null, vSeparators, null, null);
    }

    /**
     * 
     * @param region full area
     * @param renderRegion optional - area of region to render
     * @param values normalised 0.0–1.0
     * @param style optional - character styling
     * @param peakStyle  optional — highlights the max value cell
     * @return
     */
    public static NoteBytesObject drawSparkline(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        double[] values,          
        TextStyle style,
        TextStyle peakStyle
    ) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, TERMINAL_DRAW_SPARKLINE);
        map.put(Keys.REGION, region.toNoteBytes());

        if (renderRegion != null) {
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        }
        if (peakStyle != null) {
            map.put(PEAK_STYLE, peakStyle.toNoteBytes());
        }
        if (style != null) {
            map.put(Keys.STYLE, style.toNoteBytes());
        }

        int[] ints = new int[values.length];
        for(int i = 0; i < values.length ; i++){
            ints[i] = Float.floatToIntBits((float) values[i]);
        }
        map.put(Keys.DATA, new NoteIntegerArray(ints));
        
        return map.toNoteBytes();
    }

    /***
     * 
     * @param region
     * @param renderRegion optional
     * @param scrollPos
     * @param totalItems
     * @param visibleItems
     * @param showArrows
     * @param trackStyle
     * @param thumbStyle
     * @return
     */
    public static NoteBytesObject drawScrollbar(
        TerminalRectangle region, TerminalRectangle renderRegion,
        int scrollPos, int totalItems, int visibleItems,
        boolean showArrows, TextStyle trackStyle, TextStyle thumbStyle
    ) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD,          TERMINAL_DRAW_SCROLLBAR);
        map.put(Keys.REGION,       region.toNoteBytes());
        if (renderRegion != null) map.put(RENDER_REGION, renderRegion.toNoteBytes());
        map.put(SCROLL_POS,        scrollPos);
        map.put(Keys.ITEM_COUNT,   totalItems);
        map.put(Keys.VISIBLE_ITEMS,visibleItems);
        map.put(SHOW_ARROWS,       showArrows);
        if (trackStyle != null) map.put(TRACK_STYLE, trackStyle.toNoteBytes());
        if (thumbStyle != null) map.put(THUMB_STYLE, thumbStyle.toNoteBytes());
        return map.toNoteBytes();
    }

    /**
     * 
     * @param region
     * @param renderRegion optional
     * @param pixelWidth
     * @param pixelHeight
     * @param pixels row-major, 1 bit per pixel, packed
     * @param style optional
     * @return
     */
    public static NoteBytesObject drawBitmap(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        int pixelWidth,
        int pixelHeight,
        byte[] pixels,     
        TextStyle style
    ){
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.REGION, region.toNoteBytes());
        map.put(Keys.CMD, TERMINAL_DRAW_BITMAP);
        if (renderRegion != null) {
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        }
        map.put(Keys.WIDTH, pixelWidth);
        map.put(Keys.HEIGHT, pixelHeight);
        map.put(Keys.DATA, new NoteBytes(pixels, NoteBytesMetaData.IMAGE_TYPE));
        if(style != null){
            map.put(Keys.STYLE, style.toNoteBytes());
        }
        return map.toNoteBytes();
    }

    /**
     * 
     * @param region
     * @param renderRegion optional
     * @param pixelWidth    chars * 2
     * @param pixelHeight   chars * 4
     * @param pixels        row-major, 1 bit per pixel, packed
     * @param style optional
     * @return
     */
    public static NoteBytesObject drawBrailleBitmap(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        int pixelWidth,   
        int pixelHeight,
        byte[] pixels,
        TextStyle style
    ){
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.REGION, region.toNoteBytes());
        map.put(Keys.CMD, TERMINAL_DRAW_BRAILLE_BITMAP);
        if (renderRegion != null) {
            map.put(RENDER_REGION, renderRegion.toNoteBytes());
        }
        map.put(Keys.WIDTH, pixelWidth);
        map.put(Keys.HEIGHT, pixelHeight);
        map.put(Keys.DATA, new NoteBytes(pixels, NoteBytesMetaData.IMAGE_TYPE));
        if(style != null){
            map.put(Keys.STYLE, style.toNoteBytes());
        }
        return map.toNoteBytes();
    }

    public static NoteBytesObject drawSextantBitmap(
        TerminalRectangle region, 
        TerminalRectangle renderRegion,
        int pixelWidth, 
        int pixelHeight,
        byte[] pixels,
        TextStyle style
    ) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD,    TERMINAL_DRAW_SEXTANT_BITMAP);
        map.put(Keys.REGION, region.toNoteBytes());
        if (renderRegion != null) map.put(RENDER_REGION, renderRegion.toNoteBytes());
        map.put(Keys.WIDTH,  pixelWidth);
        map.put(Keys.HEIGHT, pixelHeight);
        map.put(Keys.DATA,   new NoteBytes(pixels, NoteBytesMetaData.IMAGE_TYPE));
        if (style != null) map.put(Keys.STYLE, style.toNoteBytes());
        return map.toNoteBytes();
    }

    public static NoteBytesObject shadeRegion(
        TerminalRectangle region,
        TerminalRectangle renderRegion,
        float intensity,   // 0.0=space 0.25=░ 0.5=▒ 0.75=▓ 1.0=█
        TextStyle style
    ){
        return TerminalCommands.shadeRegion(region, renderRegion, TextStyle.shadeCharForIntensity(intensity), style);
    }

    // ===== PIXEL-LIKE RENDERING CONSTANTS =====

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
