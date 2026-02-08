package io.netnotes.terminal.components;

import io.netnotes.terminal.Position;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.BoxStyle;

/**
 * TerminalMessageBox - Self-contained message display with border
 * 
 * A simpler alternative to Panel+VStack+Labels for displaying messages.
 * Handles its own layout and rendering of text lines.
 * 
 * USAGE:
 * TerminalMessageBox box = new TerminalMessageBox("msg");
 * box.setTitle("Notice");
 * box.setMessages("Line 1", "Line 2", "Line 3");
 * box.setFooter("Press any key to continue...");
 * box.setBorderStyle(BoxStyle.DOUBLE);
 */
public class TerminalMessageBox extends TerminalRenderable {
    
    private String title = null;
    private String[] messages = new String[0];
    private String footer = null;
    private BoxStyle borderStyle = BoxStyle.SINGLE;
    private TextStyle messageStyle = TextStyle.NORMAL;
    private TextStyle footerStyle = TextStyle.INFO;
    private int messageSpacing = 2;  // Rows between messages
    private int padding = 1;  // Padding inside border
    
    public TerminalMessageBox(String name) {
        super(name);
    }
    
    // ===== CONFIGURATION =====
    
    public void setTitle(String title) {
        if ((this.title == null && title != null) || 
            (this.title != null && !this.title.equals(title))) {
            this.title = title;
            invalidate();
        }
    }
    
    public void setMessages(String... messages) {
        this.messages = messages != null ? messages : new String[0];
        invalidate();
    }
    
    public void setFooter(String footer) {
        if ((this.footer == null && footer != null) || 
            (this.footer != null && !this.footer.equals(footer))) {
            this.footer = footer;
            invalidate();
        }
    }
    
    public void setBorderStyle(BoxStyle style) {
        if (this.borderStyle != style) {
            this.borderStyle = style;
            invalidate();
        }
    }
    
    public void setMessageStyle(TextStyle style) {
        if (this.messageStyle != style) {
            this.messageStyle = style;
            invalidate();
        }
    }
    
    public void setFooterStyle(TextStyle style) {
        if (this.footerStyle != style) {
            this.footerStyle = style;
            invalidate();
        }
    }
    
    public void setMessageSpacing(int spacing) {
        if (this.messageSpacing != spacing) {
            this.messageSpacing = Math.max(1, spacing);
            invalidate();
        }
    }
    
    public void setPadding(int padding) {
        if (this.padding != padding) {
            this.padding = Math.max(0, padding);
            invalidate();
        }
    }
    
    // ===== RENDERING =====

    protected void drawTitle(TerminalBatchBuilder batch){
        if (title != null && !title.isEmpty()) {
            int x =  centerTextHorizontal(title);
            printAt(batch, x, 0, title);
        } 
    }
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        int width = getWidth();
        int height = getHeight();
        
        if (width < 3) { return; }
        if (height < 3){
            drawHLine(batch, 0, 0, width);
            drawTitle(batch);
            return;
        }

        TextStyle textStyle = hasFocus() ? TextStyle.NORMAL : TextStyle.DIM;
        
        drawBox(batch, 0, 0, width, height, title, Position.TOP_CENTER, borderStyle, textStyle);
        
        // Calculate content area
        int contentX = padding + 1;  // +1 for border
        int contentY = padding + 1;
        int contentWidth = width - (2 * padding) - 2;  // -2 for borders
        int contentHeight = height - (2 * padding) - 2;
        
        if (contentWidth <= 0 || contentHeight <= 0) return;
        
        // Render messages
        int currentY = contentY;
        for (int i = 0; i < messages.length && currentY < contentY + contentHeight; i++) {
            String msg = messages[i];
            if (msg == null || msg.isEmpty()) continue;
            
            printAt(batch, contentX, currentY, msg, messageStyle);
            currentY += messageSpacing;
        }
        
        // Render footer at bottom if present
        if (footer != null && !footer.isEmpty()) {
            int footerY = height - padding - 2;  // -2 for border and position
            if (footerY >= contentY) {
                printAt(batch, contentX, footerY, footer, footerStyle);
            }
        }
    }
    
    // ===== SIZING HELPERS =====
    
    /**
     * Calculate minimum height needed for current content
     */
    public int getMinimumHeight() {
        int messageRows = messages.length;
        int spacingRows = Math.max(0, messages.length - 1) * (messageSpacing - 1);
        int footerRows = footer != null && !footer.isEmpty() ? 2 : 0;
        int borders = 2;
        int paddingRows = 2 * padding;
        
        return borders + paddingRows + messageRows + spacingRows + footerRows;
    }
    
    /**
     * Calculate preferred width based on longest message
     */
    public int getPreferredWidth() {
        int maxLength = 0;
        
        if (title != null) {
            maxLength = Math.max(maxLength, title.length() + 4);
        }
        
        for (String msg : messages) {
            if (msg != null) {
                maxLength = Math.max(maxLength, msg.length());
            }
        }
        
        if (footer != null) {
            maxLength = Math.max(maxLength, footer.length());
        }
        
        return maxLength + (2 * padding) + 2;  // +2 for borders
    }
    
    // ===== GETTERS =====
    
    public String getTitle() { return title; }
    public String[] getMessages() { return messages; }
    public String getFooter() { return footer; }
    public BoxStyle getBorderStyle() { return borderStyle; }
    public int getMessageSpacing() { return messageSpacing; }
    public int getPadding() { return padding; }
}