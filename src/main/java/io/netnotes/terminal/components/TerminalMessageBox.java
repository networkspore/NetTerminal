package io.netnotes.terminal.components;

import io.netnotes.engine.ui.Position;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.TextAlignment;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.layout.TerminalLayoutContext;

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
public class TerminalMessageBox extends TerminalRegion {
    
    public enum MessageType {
        INFO,       // Blue/Cyan - informational
        SUCCESS,    // Green - success/completion
        WARNING,    // Yellow - warning/caution
        ERROR,      // Red - error/failure
        DEFAULT     // Normal styling
    }
    
    private String title = null;
    private String[] messages = new String[0];
    private String footer = null;
    private MessageType type = MessageType.DEFAULT;
    private LineStyle borderStyle = LineStyle.SINGLE;
    private boolean showIcon = true;
    private int messageSpacing = 1;
    private int padding = 1;
    private TextAlignment messageAlignment = TextAlignment.LEFT;
    
    // Style overrides (null = use defaults based on type)
    private TextStyle titleStyle = null;
    private TextStyle messageStyle = null;
    private TextStyle footerStyle = null;
    private LineStyle boxStyle = null;
    
    public TerminalMessageBox(String name) {
        super(name);
    }
    
    public TerminalMessageBox(String name, MessageType type) {
        super(name);
        this.type = type;
    }

    public TerminalMessageBox(Builder builder){
        super(builder.name);

        this.title = builder.title;
        this.messages = builder.messages;
        this.footer = builder.footer;
        this.borderStyle = builder.borderStyle;
        this.showIcon = builder.showIcon;
        this.messageSpacing = builder.messageSpacing;
        this.padding = builder.padding;
        this.type = builder.type;

        setBounds(builder.x, builder.y, builder.width, builder.height);
    }
    
    // ===== CONFIGURATION =====
    
    public void setType(MessageType type) {
        if (this.type != type) {
            this.type = type;
            invalidate();
        }
    }
    
    public void setTitle(String title) {
        if ((this.title == null && title != null) || 
            (this.title != null && !this.title.equals(title))) {
            this.title = title;
            notifyContentChanged();
        }
    }
    
    public void setMessages(String... messages) {
        this.messages = messages != null ? messages : new String[0];
        notifyContentChanged();
    }
    
    public void setMessage(String message) {
        setMessages(message);
    }
    
    public void setFooter(String footer) {
        if ((this.footer == null && footer != null) || 
            (this.footer != null && !this.footer.equals(footer))) {
            this.footer = footer;
            notifyContentChanged();
        }
    }
    
    public void setBorderStyle(LineStyle style) {
        if (this.borderStyle != style) {
            this.borderStyle = style;
            invalidate();
        }
    }
    
    public void setShowIcon(boolean show) {
        if (this.showIcon != show) {
            this.showIcon = show;
            notifyContentChanged();
        }
    }
    
    public void setMessageAlignment(TextAlignment alignment) {
        if (this.messageAlignment != alignment) {
            this.messageAlignment = alignment;
            invalidate();
        }
    }
    
    public void setMessageSpacing(int spacing) {
        int clamped = Math.max(1, spacing);
        if (this.messageSpacing != clamped) {
            this.messageSpacing = clamped;
            notifyContentChanged();
        }
    }
    
    public void setPadding(int padding) {
        int clamped = Math.max(0, padding);
        if (this.padding != clamped) {
            this.padding = clamped;
            notifyContentChanged();
        }
    }
    
    // Style overrides
    public void setTitleStyle(TextStyle style) {
        this.titleStyle = style;
        invalidate();
    }
    
    public void setMessageStyle(TextStyle style) {
        this.messageStyle = style;
        invalidate();
    }
    
    public void setFooterStyle(TextStyle style) {
        this.footerStyle = style;
        invalidate();
    }
    
    public void setBoxStyle(LineStyle style) {
        this.boxStyle = style;
        invalidate();
    }
    
    // ===== RENDERING =====
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        int width = getWidth();
        int height = getHeight();
        
        if (width < 3 || height < 3) return;
        
        LineStyle boxStyle = getBoxStyle();
        TextStyle titleStyle = getTitleStyle();
        TextStyle msgStyle = getMessageStyle();
        TextStyle footStyle = getFooterStyle();

        fillRegion(batch, 1, 1, Math.max(0, width - 2), Math.max(0, height - 2), ' ', TextStyle.NORMAL);
        
        // Draw border with title
        String titleText = getRenderedTitleText();
        drawBox(batch, 0, 0, width, height, titleText, Position.TOP_CENTER, boxStyle, titleStyle);
        
        // Calculate content area
        int contentX = padding + 1;
        int contentY = padding + 1;
        int contentWidth = width - (2 * padding) - 2;
        int contentHeight = height - (2 * padding) - 2;
        
        if (contentWidth <= 0 || contentHeight <= 0) return;
        
        // Render messages
        int currentY = contentY;
        for (int i = 0; i < messages.length && currentY < contentY + contentHeight; i++) {
            String msg = messages[i];
            if (msg == null || msg.isEmpty()) continue;
            
            if (msg.length() > contentWidth) {
                // Word wrap long messages
                int linesUsed = renderWrappedText(batch, contentX, currentY, 
                    contentWidth, contentHeight - (currentY - contentY), msg, msgStyle);
                currentY += linesUsed + messageSpacing;
            } else {
                int x = switch (messageAlignment) {
                    case CENTER -> contentX + (contentWidth - msg.length()) / 2;
                    case RIGHT -> contentX + contentWidth - msg.length();
                    default -> contentX;
                };
                printAt(batch, x, currentY, msg, msgStyle);
                currentY += messageSpacing;
            }
        }
        
        // Render footer at bottom
        if (footer != null && !footer.isEmpty()) {
            int footerY = height - padding - 2;
            if (footerY >= contentY) {
                int x = switch (messageAlignment) {
                    case CENTER -> contentX + (contentWidth - footer.length()) / 2;
                    case RIGHT -> contentX + contentWidth - footer.length();
                    default -> contentX;
                };
                printAt(batch, x, footerY, footer, footStyle);
            }
        }
    }
    
    private int renderWrappedText(TerminalBatchBuilder batch, int x, int y, 
                                   int width, int maxHeight, String text, TextStyle style) {
        String[] words = text.split("\\s+");
        int linesRendered = 0;
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (linesRendered >= maxHeight) break;
            
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            
            if (testLine.length() > width) {
                if (currentLine.length() > 0) {
                    printAt(batch, x, y + linesRendered, currentLine.toString(), style);
                    linesRendered++;
                    currentLine = new StringBuilder(word);
                } else {
                    // Single word too long, truncate it
                    printAt(batch, x, y + linesRendered, word.substring(0, width), style);
                    linesRendered++;
                }
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        
        if (currentLine.length() > 0 && linesRendered < maxHeight) {
            printAt(batch, x, y + linesRendered, currentLine.toString(), style);
            linesRendered++;
        }
        
        return linesRendered;
    }
    
    // ===== STYLE HELPERS =====
    
    private String getIconForType() {
        if (!showIcon) return "";
        
        return switch (type) {
            case INFO -> "ℹ";
            case SUCCESS -> "✓";
            case WARNING -> "⚠";
            case ERROR -> "✗";
            default -> "";
        };
    }

    private String getRenderedTitleText() {
        return (title != null && !title.isEmpty()) ? getIconForType() + " " + title : null;
    }
    
    private LineStyle getBoxStyle() {
        return boxStyle != null ? boxStyle : borderStyle;
    }
    
    private TextStyle getTitleStyle() {
        if (titleStyle != null) return titleStyle;
        
        return switch (type) {
            case ERROR -> TextStyle.STATUS_ERROR;
            case WARNING -> TextStyle.STATUS_WARN;
            case SUCCESS -> TextStyle.STATUS_OK;
            case INFO -> TextStyle.STATUS_INFO;
            default -> TextStyle.HEADER;
        };
    }
    
    private TextStyle getMessageStyle() {
        if (messageStyle != null) return messageStyle;
        
        return switch (type) {
            case ERROR -> TextStyle.RED;
            case WARNING -> TextStyle.YELLOW;
            case SUCCESS -> TextStyle.GREEN;
            case INFO -> TextStyle.CYAN;
            default -> TextStyle.NORMAL;
        };
    }
    
    private TextStyle getFooterStyle() {
        if (footerStyle != null) return footerStyle;
        return TextStyle.INFO;
    }
    
    // ===== SIZING HELPERS =====
    
    public int getMinimumHeight() {
        int messageRows = messages.length;
        int spacingRows = Math.max(0, messages.length - 1) * (messageSpacing - 1);
        int footerRows = footer != null && !footer.isEmpty() ? 2 : 0;
        int borders = 2;
        int paddingRows = 2 * padding;
        
        return borders + paddingRows + messageRows + spacingRows + footerRows;
    }
    
    // ===== SIZEABLE IMPLEMENTATION (FIXED) =====
    
   

    @Override
    public int getMinWidth() {
        int paddedMin = (2 * padding) + 2 + getInsets().getHorizontal();
        return Math.max(super.getMinWidth(), Math.max(3, paddedMin));
    }

    @Override
    public int getMinHeight() {
        int paddedMin = (2 * padding) + 2 + getInsets().getVertical();
        return Math.max(super.getMinHeight(), Math.max(3, paddedMin));
    }
    
    // ===== GETTERS =====
    
    public MessageType getType() { return type; }
    public String getTitle() { return title; }
    public String[] getMessages() { return messages; }
    public String getFooter() { return footer; }
    
    // ===== BUILDER =====
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name = "messagebox";
        private MessageType type = MessageType.DEFAULT;
        private String title = null;
        private String[] messages = new String[0];
        private String footer = null;
        private LineStyle borderStyle = LineStyle.SINGLE;
        private boolean showIcon = true;
        private int messageSpacing = 1;
        private int padding = 1;
        private int x = 0, y = 0, width = 40, height = 10;
        
        public Builder name(String name) { this.name = name; return this; }
        public Builder type(MessageType type) { this.type = type; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder messages(String... messages) { this.messages = messages; return this; }
        public Builder message(String message) { return messages(message); }
        public Builder footer(String footer) { this.footer = footer; return this; }
        public Builder borderStyle(LineStyle style) { this.borderStyle = style; return this; }
        public Builder showIcon(boolean show) { this.showIcon = show; return this; }
        public Builder messageSpacing(int spacing) { this.messageSpacing = spacing; return this; }
        public Builder padding(int padding) { this.padding = padding; return this; }
        public Builder position(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }
        public Builder bounds(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            return this;
        }
        
        public TerminalMessageBox build() {
            return new TerminalMessageBox(this);
        }
    }

    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        int measuredWidth = getWidthPreference() == SizePreference.FIT_CONTENT
            ? measureContentWidth()
            : getMinWidth();

        int measuredHeight = getHeightPreference() == SizePreference.FIT_CONTENT
            ? Math.max(getMinHeight(), getMinimumHeight() + getInsets().getVertical())
            : getMinHeight();

        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, measuredWidth, measuredHeight);
        return measured;
    }

    private int measureContentWidth() {
        int maxContentWidth = 0;

        if (messages != null) {
            for (String message : messages) {
                if (message != null && !message.isEmpty()) {
                    maxContentWidth = Math.max(maxContentWidth, message.length());
                }
            }
        }

        if (footer != null && !footer.isEmpty()) {
            maxContentWidth = Math.max(maxContentWidth, footer.length());
        }

        int measuredWidth = maxContentWidth + (2 * padding) + 2 + getInsets().getHorizontal();

        String titleText = getRenderedTitleText();
        if (titleText != null && !titleText.isEmpty()) {
            measuredWidth = Math.max(measuredWidth, titleText.length() + 4 + getInsets().getHorizontal());
        }

        return Math.max(getMinWidth(), measuredWidth);
    }
}
