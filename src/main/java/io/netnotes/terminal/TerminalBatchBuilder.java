package io.netnotes.terminal;
import io.netnotes.terminal.TextStyle.BoxStyle;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.ui.BatchBuilder;
import io.netnotes.engine.ui.Position;
import io.netnotes.engine.ui.TextAlignment;

/**
 * TerminalBatchBuilder - Build atomic batches of terminal commands with clip region support
 * 
 * NOTE: This builder manages which commands are included based on clip regions.
 * It does NOT modify command content - boundary enforcement happens at the TerminalRenderable level.
 * 
 * Coordinate System: Uses x,y where:
 *   x = horizontal position (0 = left)
 *   y = vertical position (0 = top)
 *   
 * All parameters follow the convention: x before y
 */
public class TerminalBatchBuilder extends BatchBuilder<TerminalRectangle>{
    
    public TerminalBatchBuilder() {
        super();
    }
    
    @Override
    public void addCommand(NoteBytesMap cmd) {
        commands.add(cmd.toNoteBytes());
    }
    
    @Override
    public void addCommand(NoteBytes cmd) {
        commands.add(cmd);
    }
    
    // ===== COMMAND FILTERING METHODS =====
    // These methods filter which commands get added to the batch based on clip region.
    // Actual boundary enforcement (clamping/clipping of command content) happens in TerminalRenderable.
    
    /**
     * Clear screen (always executes, not filtered)
     */
    public void clear() {
        NoteBytesObject cmd = TerminalCommands.clear();
        addCommand(cmd);
    }
    
    /**
     * Print text (not position-based, not filtered)
     */
    public void print(String text) {
        print(text, TextStyle.NORMAL);
    }
    
    public void print(String text, TextStyle style) {
        NoteBytesObject cmd = TerminalCommands.print(text, style);
        addCommand(cmd);
    }
    
    /**
     * Print line (not position-based, not filtered)
     */
    public void println(String text) {
        println(text, TextStyle.NORMAL);
    }
    
    public void println(String text, TextStyle style) {
        NoteBytesObject cmd = TerminalCommands.println(text, style);
        addCommand(cmd);
    }
    
    /**
     * Print at position - CHECKS CLIP REGION
     * Only adds command if position intersects clip region.
     * TerminalRenderable will handle actual text clipping/clamping.
     */
    public void printAt(int x, int y, String text) {
        printAt(x, y, text, TextStyle.NORMAL);
    }
    
    public void printAt(int x, int y, String text, TextStyle style) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Skip if position is completely outside clip region
            if (y < clip.getY() || y >= clip.getY() + clip.getHeight()) {
                return;
            }
            
            if (x >= clip.getX() + clip.getWidth()) {
                return;
            }
            
            // Check if any part of the text would be visible
            int endX = x + text.length();
            if (endX <= clip.getX()) {
                return;  // Text ends before clip region starts
            }
        }
        
        // Add command - TerminalRenderable will handle boundary enforcement
        addCommand(TerminalCommands.printAt(x, y, text, style));
    }

    public void printAtPosition(TerminalRectangle region, String text, Position pos, TextStyle style) {
        TerminalRectangle clip = getCurrentClipRegion();
        if (clip != null && !region.intersects(clip)) return;
        int[] values = calculatePosition(region, text, pos);

        addCommand(TerminalCommands.printAt(values[0], values[1], text, style));
    }



    /**
     * Calculate position within region based on Position enum
     * @return Point2D with x,y coordinates
     */
    public static int[] calculatePosition(TerminalRectangle region, String text, Position pos) {
        int textLen = text.length();
        int x = region.getX();
        int y = region.getY();
        
        // Horizontal
        switch (pos) {
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> 
                x = region.getX() + (region.getWidth() / 2) - (textLen / 2);
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> 
                x = region.getX() + region.getWidth() - textLen;
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> 
                x = region.getX();
        }
        
        // Vertical
        switch (pos) {
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> 
                y = region.getY() + (region.getHeight() / 2);
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> 
                y = region.getY() + region.getHeight() - 1;
            case TOP_CENTER, TOP_LEFT, TOP_RIGHT-> 
                y = region.getY();
        }
        
        return new int[] { Math.max(region.getX(), x), Math.max(region.getY(), y) };
    }

    
    public void printAtCenterY(TerminalRectangle region, int x, String text) {
        printAtCenterY(region, x, text, null);
    }
    /**
     * Print text centered vertically within a region
     */
    public void printAtCenterY(TerminalRectangle region, int x, String text, TextStyle style) {
        int y = region.getCenterY();

        NoteBytesObject cmd = TerminalCommands.printAt(x, y, text, style);
        addCommand(cmd);
    }

    
    public void printAtCenterX(TerminalRectangle region, int y, String text) {
        printAtCenterX(region, y, text, null);
    }
    /**
     * Print text centered horizontally within a region
     */
    public void printAtCenterX(TerminalRectangle region, int y, String text, TextStyle style) {
        int x = region.getCenterX() - (text.length() / 2);
        NoteBytesObject cmd = TerminalCommands.printAt(x, y, text, style);
        addCommand(cmd);
    }
    
    public void printAtCenter(TerminalRectangle region, String text){
        printAtCenter(region, text, null);
    }
    /**
     * Print text centered both horizontally and vertically within a region
     */
    public void printAtCenter(TerminalRectangle region, String text, TextStyle style) {
        int x = region.getCenterX() - (text.length() / 2);
        int y = region.getCenterY();
        NoteBytesObject cmd = TerminalCommands.printAt(x, y, text, style);
        addCommand(cmd);
    }
    
    /**
     * Move cursor (not filtered - cursor operations are global)
     */
    public void moveCursor(int x, int y) {
        NoteBytesObject cmd = TerminalCommands.moveCursor(x, y);
        addCommand(cmd);
    }
    
    public void showCursor() {
        NoteBytesObject cmd = TerminalCommands.showCursor();
        addCommand(cmd);
    }
    
    public void hideCursor() {
        NoteBytesObject cmd = TerminalCommands.hideCursor();
        addCommand(cmd);
    }
    
    /**
     * Clear line at cursor (not position-based, not filtered)
     */
    public void clearLine() {
        NoteBytesObject cmd = TerminalCommands.clearLine();
        addCommand(cmd);
    }
    
    /**
     * Clear specific line - CHECKS CLIP REGION
     * TerminalRenderable will enforce that clear only affects its bounds.
     */
    public void clearLineAt(int y) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Skip if line is outside clip region
            if (y < clip.getY() || y >= clip.getY() + clip.getHeight()) {
                return;
            }
        }
        
        addCommand(TerminalCommands.clearLineAt(y));
    }
    
    /**
     * Clear rectangular region - CHECKS CLIP REGION
     * TerminalRenderable will clamp the region to its bounds.
     */
    public void clearRegion(TerminalRectangle region) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Check if region intersects clip region at all
            if (!region.intersects(clip)) {
                return;
            }
        }
        
        addCommand(TerminalCommands.clearRegion(region));
    }

    /**
     * Draw box - CHECKS CLIP REGION
     * TerminalRenderable will handle boundary enforcement of box drawing.
     */
    public void drawBox(TerminalRectangle region, TerminalRectangle renderRegion, String title, Position titlePos, BoxStyle boxStyle, TextStyle textStyle) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Check if box intersects clip region
            if (!region.intersects(clip)) {
                return;
            }
        }
        addCommand(TerminalCommands.drawBox(region, renderRegion, title, titlePos, boxStyle, textStyle));
    }



    
    /**
     * Draw horizontal line - CHECKS CLIP REGION
     * TerminalRenderable will clamp line to its bounds.
     */
    public void drawHLine(int x, int y, int length) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Skip if line is outside clip region
            if (y < clip.getY() || y >= clip.getY() + clip.getHeight()) {
                return;
            }
            
            int endX = x + length;
            if (endX <= clip.getX() || x >= clip.getX() + clip.getWidth()) {
                return;
            }
        }
        
        addCommand(TerminalCommands.drawHLine(x, y, length));
    }

    /**
     * Draw vertical line - CHECKS CLIP REGION
     * TerminalRenderable will clamp line to its bounds.
     */
    public void drawVLine(int x, int y, int length) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Skip if line is outside clip region
            if (x < clip.getX() || x >= clip.getX() + clip.getWidth()) {
                return;
            }
            
            int endY = y + length;
            if (endY <= clip.getY() || y >= clip.getY() + clip.getHeight()) {
                return;
            }
        }
        
        addCommand(TerminalCommands.drawVLine(x, y, length));
    }

    /**
     * Fill region with character - CHECKS CLIP REGION
     * TerminalRenderable will clamp region to its bounds.
     */
    public void fillRegion(TerminalRectangle region, String fillChar, TextStyle style) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Check if region intersects clip region
            if (!region.intersects(clip)) {
                return;
            }
        }
        
        addCommand(TerminalCommands.fillRegion(region,null, fillChar, style));
    }
    
    /**
     * Fill region with character (code point version)
     */
    public void fillRegion(TerminalRectangle region, TerminalRectangle renderRegion, int cp, TextStyle style) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Check if region intersects clip region
            if (!region.intersects(clip)) {
                return;
            }
        }
        
        addCommand(TerminalCommands.fillRegion(region, renderRegion, cp, style));
    }

    public void fillRegion(TerminalRectangle region, TerminalRectangle renderRegion, String character, TextStyle style) {
        fillRegion(region, renderRegion, character.codePointAt(0), style);
    }
 

    public void drawBorderedText(TerminalRectangle region,TerminalRectangle renderRegion, String text, Position textPos, BoxStyle boxStyle, 
        TextStyle textStyle, TextStyle borderStyle
    ) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            if (!region.intersects(clip)) {
                return;
            }
        }
        
        addCommand(TerminalCommands.drawBorderedText(region,renderRegion, text,textPos, boxStyle, textStyle, borderStyle));
    }

    /**
     * Draw panel (box with filled background) - CHECKS CLIP REGION
     */
    public void drawPanel(TerminalRectangle region, TerminalRectangle renderRegion, String title, Position titlePos, BoxStyle boxStyle, 
         TextStyle borderStyle, TextStyle fillStyle
    ) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            if (!region.intersects(clip)) {
                return;
            }
        }
        
        addCommand(TerminalCommands.drawPanel(region,renderRegion, title, titlePos, boxStyle, borderStyle, fillStyle));
    }


    /**
     * Draw button component - CHECKS CLIP REGION
     */
    public void drawButton(TerminalRectangle region,TerminalRectangle renderRegion, String label, Position pos, boolean selected, TextStyle style) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            if (!region.intersects(clip)) {
                return;
            }
        }
        
        addCommand(TerminalCommands.drawButton(region,renderRegion, label, pos, selected, style));
    }


    /**
     * Draw progress bar - CHECKS CLIP REGION
     */
    public void drawProgressBar(TerminalRectangle region, TerminalRectangle renderRegion, double progress, 
                            TextStyle style, TextStyle emptyStyle) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            if (!region.intersects(clip)) {
                return;
            }
        }
        
        addCommand(TerminalCommands.drawProgressBar(region,renderRegion, progress, style, emptyStyle));
    }

    /**
     * Draw text block with word wrapping - CHECKS CLIP REGION
     */
    public void drawTextBlock(TerminalRectangle region, TerminalRectangle renderRegion, String text, 
        TextAlignment align, TextStyle style
    ) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            if (!region.intersects(clip)) {
                return;
            }
        }
        
        addCommand(TerminalCommands.drawTextBlock(region, renderRegion, text, align, style));
    }

  
    /**
     * Shade region with character pattern - CHECKS CLIP REGION
     */
    public void shadeRegion(TerminalRectangle region, TerminalRectangle renderRegion, int shadeChar, TextStyle style) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            if (!region.intersects(clip)) {
                return;
            }
        }
        
        addCommand(TerminalCommands.shadeRegion(region,renderRegion, shadeChar, style));
    }

    public void shadeRegion(TerminalRectangle region, TerminalRectangle renderRegion, String shadeChar, TextStyle style) {
        shadeRegion(region, renderRegion, shadeChar.codePointAt(0), style);
    }
}