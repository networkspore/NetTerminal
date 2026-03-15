package io.netnotes.terminal.components.panels;

import java.util.Map;

import io.netnotes.engine.ui.Position;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.renderer.layout.LayoutGroup.LayoutDataInterface;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.layout.TerminalGroupCallbackEntry;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutCallback;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalSizeable;

public class TerminalPanel extends TerminalRegion {
    
    public enum Axis{
        VERTICAL,
        HORIZONTAL
    }

    public enum Alignment {
        START,    // default
        CENTER,
        END,
        STRETCH   // only affects positioning if child < available cross
    }


    private boolean drawBorder = false;
    private TextStyle.LineStyle borderStyle = TextStyle.LineStyle.SINGLE;
    private String title = null;
    private Position titlePosition = Position.TOP_CENTER;
    private TextStyle borderTextStyle = TextStyle.NORMAL;
    private TextStyle focusedBorderTextStyle = TextStyle.FOCUSED;

    private final TerminalInsets padding = new TerminalInsets();

    private Axis axis = Axis.HORIZONTAL;
    private boolean wrap = false;
    private int spacing = 0;
    private Alignment crossAlignment = Alignment.START;
    private final String layoutGroupId;
    private final String layoutCallbackId;
    private TerminalGroupCallbackEntry layoutCallbackEntry = null;
    private TextStyle fillStyle = null;
    private Alignment alignment = Alignment.START;
    private int maxWidth  = Integer.MAX_VALUE;
    private int maxHeight = Integer.MAX_VALUE;
    
    public TerminalPanel(
        String name
    ) {
        super(name);
        this.layoutGroupId = "panel-" + getName();
        this.layoutCallbackId = "panel-default";
        initLayoutCallback();
    }

    protected void initLayoutCallback(){
        this.layoutCallbackEntry = new TerminalGroupCallbackEntry(
            getLayoutCallbackId(),
            this::layoutChildren
        );
        registerGroupCallback(getLayoutGroupId(), layoutCallbackEntry);
    }

    public TerminalGroupCallbackEntry getTerminalGroupCallbackEntry() { 
        return layoutCallbackEntry; 
    }

    public String getLayoutCallbackId() {
        return layoutCallbackId;
    }

    public String getLayoutGroupId() {
        return layoutGroupId;
    }

    /**
     * Override addChild to automatically add children to layout group.
     * Children that implement TerminalSizeable will be sized to respect this panel's insets.
     */
     @Override
    public void addChild(TerminalRenderable child) {
        this.addChild(child, null); 
    }

    @Override 
    public void addChild(TerminalRenderable child, TerminalLayoutCallback callback){
        super.addChild(child, null);
        addToLayoutGroup(child, layoutGroupId);
    }

    private void layoutChildren(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        if (contexts.length == 0) return;

        TerminalRectangle parent = contexts[0].getParentRegion();
        if (parent == null) return;

        TerminalInsets insets    = getInsets();
        int availableWidth       = parent.getWidth()  - insets.getHorizontal();
        int availableHeight      = parent.getHeight() - insets.getVertical();
        int availablePrimary     = axis == Axis.VERTICAL ? availableHeight : availableWidth;
        int availableCross       = axis == Axis.VERTICAL ? availableWidth  : availableHeight;
        int startX               = insets.getLeft();
        int startY               = insets.getTop();

        int count      = contexts.length;
        int[] widths   = new int[count];
        int[] heights  = new int[count];

        // ── Pass 1: measure, categorize, emit hidden immediately ─────────────────
        int fitPrimaryTotal  = 0;
        int fillPrimaryCount = 0;
        int fitCrossMax      = 0;
        boolean anyVisible   = false;

        for (int i = 0; i < count; i++) {
            TerminalRenderable child = contexts[i].getRenderable();

            if (child.isHidden()) {
                widths[i] = heights[i] = 0;
                dataInterfaces.get(child.getName())
                    .setLayoutData(TerminalLayoutData.getBuilder().build());
                continue;
            }

            anyVisible = true;
            int prefW  = -1;
            int prefH  = -1;

            if (child instanceof TerminalSizeable s) {
                prefW = switch (s.getWidthPreference()) {
                    case PERCENT     -> Math.max(s.getMinWidth(),
                                        (int)(availableWidth * s.getPercentWidth() / 100f));
                    case FIT_CONTENT,
                        STATIC      -> Math.min(s.getPreferredWidth(), availableWidth);
                    default          -> -1;  // FILL — resolved in pass 2
                };
                prefH = switch (s.getHeightPreference()) {
                    case PERCENT     -> Math.max(s.getMinHeight(),
                                        (int)(availableHeight * s.getPercentHeight() / 100f));
                    case FIT_CONTENT,
                        STATIC      -> Math.min(s.getPreferredHeight(), availableHeight);
                    default          -> -1;  // FILL — resolved in pass 2
                };
            } else if (child.getRequestedRegion() != null) {
                prefW = Math.min(child.getRequestedRegion().getWidth(),  availableWidth);
                prefH = Math.min(child.getRequestedRegion().getHeight(), availableHeight);
            }

            widths[i]  = prefW;
            heights[i] = prefH;

            int primary = axis == Axis.VERTICAL ? prefH : prefW;
            int cross   = axis == Axis.VERTICAL ? prefW  : prefH;

            if (primary >= 0) fitPrimaryTotal  += primary;
            else              fillPrimaryCount++;
            if (cross   >= 0) fitCrossMax = Math.max(fitCrossMax, cross);
        }

        if (!anyVisible) return;

        // ── Derive shared fill/cross constants ────────────────────────────────────
        int fillPrimary   = fillPrimaryCount > 0
            ? Math.max(1, (availablePrimary - fitPrimaryTotal) / fillPrimaryCount)
            : 0;
        int resolvedCross = fitCrossMax > 0 ? fitCrossMax : availableCross;

        // ── Pass 2: resolve FILL sizes, accumulate main-axis total ───────────────
        int totalPrimaryUsed = 0;
        int visibleCount     = 0;

        for (int i = 0; i < count; i++) {
            if (widths[i] == 0 && heights[i] == 0) continue;  // was hidden — already emitted

            if (axis == Axis.VERTICAL) {
                if (heights[i] < 0) heights[i] = fillPrimary;
                if (widths[i]  < 0) widths[i]  = resolvedCross;
                totalPrimaryUsed += heights[i];
            } else {
                if (widths[i]  < 0) widths[i]  = fillPrimary;
                if (heights[i] < 0) heights[i] = resolvedCross;
                totalPrimaryUsed += widths[i];
            }
            visibleCount++;
        }

        if (visibleCount > 1) totalPrimaryUsed += (visibleCount - 1) * spacing;

        int primaryOffset = switch (alignment) {
            case CENTER -> Math.max(0, (availablePrimary - totalPrimaryUsed) / 2);
            case END    -> Math.max(0,  availablePrimary - totalPrimaryUsed);
            default     -> 0;
        };

        // ── Pass 3: place, align, emit ────────────────────────────────────────────
        int cursorX        = startX + (axis == Axis.HORIZONTAL ? primaryOffset : 0);
        int cursorY        = startY + (axis == Axis.VERTICAL   ? primaryOffset : 0);
        int lineCrossExtent = 0;
        int maxPrimary     = axis == Axis.VERTICAL ? parent.getHeight() : parent.getWidth();

        for (int i = 0; i < count; i++) {
            if (widths[i] == 0 && heights[i] == 0) continue;  // was hidden — already emitted

            TerminalRenderable child = contexts[i].getRenderable();
            int w = widths[i];
            int h = heights[i];

            // ── wrap ──
            if (wrap) {
                int nextPrimary = axis == Axis.VERTICAL ? cursorY + h : cursorX + w;
                if (nextPrimary > maxPrimary) {
                    if (axis == Axis.VERTICAL) {
                        cursorY  = startY + primaryOffset;
                        cursorX += lineCrossExtent;
                    } else {
                        cursorX  = startX + primaryOffset;
                        cursorY += lineCrossExtent;
                    }
                    lineCrossExtent = 0;
                }
            }

            int x = cursorX;
            int y = cursorY;

            // ── cross-axis alignment ──
            int freeCross = availableCross - (axis == Axis.VERTICAL ? w : h);
            if (freeCross > 0) {
                switch (crossAlignment) {
                    case CENTER  -> { if (axis == Axis.VERTICAL) x += freeCross / 2; else y += freeCross / 2; }
                    case END     -> { if (axis == Axis.VERTICAL) x += freeCross;     else y += freeCross;     }
                    case STRETCH -> { if (axis == Axis.VERTICAL) w  = availableCross; else h = availableCross; }
                    default      -> {}
                }
            }

            // ── emit ──
            boolean inBounds = isWithinParentBounds(x, y, w, h, parent);
            TerminalLayoutData.TerminalLayoutDataBuilder b = TerminalLayoutData.getBuilder()
                .setX(x).setY(y).setWidth(w).setHeight(h);

            if (!inBounds) {
                b.hidden(true);
            } else if (shouldManageHidden(child)) {
                b.hidden(false);
            }

            dataInterfaces.get(child.getName()).setLayoutData(b.build());

            // ── advance cursor ──
            if (axis == Axis.VERTICAL) {
                cursorY        += h + spacing;
                lineCrossExtent = Math.max(lineCrossExtent, w);
            } else {
                cursorX        += w + spacing;
                lineCrossExtent = Math.max(lineCrossExtent, h);
            }
        }
    }

    private boolean isWithinParentBounds(
        int x,
        int y,
        int width,
        int height,
        TerminalRectangle parentRegion
    ) {
        return x >= 0 &&
            y >= 0 &&
            x + width <= parentRegion.getWidth() &&
            y + height <= parentRegion.getHeight();
    }
      


    private boolean shouldManageHidden(TerminalRenderable child) {
        if (child instanceof TerminalSizeable sizable) {
            return sizable.isHiddenManaged();
        }
        return true;
    }

    
        
    public Axis getAxis() {
        return axis;
    }

    public void setAxis(Axis axis) {
        if (this.axis != axis) {
            this.axis = axis;
            requestLayoutUpdate();
        }
    }

    public boolean isWrap() {
        return wrap;
    }

    public void setWrap(boolean wrap) {
        if (this.wrap != wrap) {
            this.wrap = wrap;
            requestLayoutUpdate();
        }
    }

    public Alignment getCrossAlignment() {
        return crossAlignment;
    }

    public void setCrossAlignment(Alignment crossAlignment) {
        if (this.crossAlignment != crossAlignment) {
            this.crossAlignment = crossAlignment;
            requestLayoutUpdate();
        }
    }

    public void setEnableBorder(boolean enabled) {
        if (this.drawBorder != enabled) {
            this.drawBorder = enabled;
            requestLayoutUpdate();
            invalidate();
        }
    }
    
    public void setBorderStyle(LineStyle style) {
        if (this.borderStyle != style) {
            this.borderStyle = style;
            invalidate();
        }
    }
    
    public void setTitle(String title) {
        if ((this.title == null && title != null) || 
            (this.title != null && !this.title.equals(title))) {
            this.title = title;
            invalidate();
        }
    }
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        int width  = getWidth();
        int height = getHeight();

        // Fill background first so border draws cleanly on top
        if (fillStyle != null) {
            fillRegion(batch, 0, 0, width, height, ' ', fillStyle);
        }

        if (drawBorder || title != null) {
            TextStyle borderTextStyle = hasFocus() ? this.focusedBorderTextStyle : this.borderTextStyle;
            drawBox(batch, 0, 0, width, height, title, titlePosition, borderStyle, borderTextStyle);
        }
    }

    

    public TextStyle getFocusedBorderTextStyle() {
        return focusedBorderTextStyle;
    }

    public void setFocusedBorderTextStyle(TextStyle focusedTextStyle) {
        this.focusedBorderTextStyle = focusedTextStyle;
        invalidate();
    }

    public TextStyle getBorderTextStyle() {
        return borderTextStyle;
    }

    public void setBorderTextStyle(TextStyle textStyle) {
        this.borderTextStyle = textStyle;
        invalidate();
    }

    public Position getTitlePosition() {
        return titlePosition;
    }

    public void setTitlePosition(Position titlePosition) {
        if (this.titlePosition != titlePosition) {
            this.titlePosition = titlePosition;
            invalidate();
        }
    }

    @Override
    public int getPreferredWidth() {
        SizePreference pref = getWidthPreference();
        if (pref == SizePreference.STATIC)  return Math.min(maxWidth, region.getWidth());
        if (pref == SizePreference.PERCENT) return Math.min(maxWidth, getMinWidth());
        int widthCalc = 0;
        
        if(pref == SizePreference.FIT_CONTENT){
            if(Axis.VERTICAL == axis){
                for (TerminalRenderable child : getChildren()) {
                    if (child.isHidden()) continue;
                    
                    if (child instanceof TerminalSizeable) {
                        widthCalc = Math.max(widthCalc, ((TerminalSizeable) child).getPreferredWidth());
                    } else if (child.getRequestedRegion() != null) {
                        widthCalc = Math.max(widthCalc, child.getRequestedRegion().getWidth());
                    }
                }
            }else{
                int count = 0;
                for (TerminalRenderable child : getChildren()) {
                    if (child.isHidden()) continue;
                    
                    if (child instanceof TerminalSizeable) {
                        widthCalc += ((TerminalSizeable) child).getPreferredWidth();
                    } else if (child.getRequestedRegion() != null) {
                        widthCalc += child.getRequestedRegion().getWidth();
                    } else {
                        widthCalc += child.getRegion().getWidth(); 
                    }
                    count++;
                }
                
                if (count > 0) {
                    widthCalc += (count - 1) * spacing; // Add spacing
                }
            }
        }
        return Math.min(maxWidth, Math.max(getMinWidth(), widthCalc + getInsets().getHorizontal()));
    }

    @Override
    public int getPreferredHeight() {
        SizePreference pref = getHeightPreference();
        if (pref == SizePreference.STATIC)  return Math.min(maxHeight, region.getHeight());
        if (pref == SizePreference.PERCENT) return Math.min(maxHeight, getMinHeight());

        int heightCalc = 0;
        
        if(pref == SizePreference.FIT_CONTENT){
            if(Axis.HORIZONTAL == axis){
                for (TerminalRenderable child : getChildren()) {
                    if (child.isHidden()) continue;
                    
                    if (child instanceof TerminalSizeable) {
                        heightCalc = Math.max(heightCalc, ((TerminalSizeable) child).getPreferredHeight());
                    } else if (child.getRequestedRegion() != null) {
                        heightCalc = Math.max(heightCalc, child.getRequestedRegion().getHeight());
                    }
                }
            }else{
                int count = 0;
                for (TerminalRenderable child : getChildren()) {
                    if (child.isHidden()) continue;
                    
                    if (child instanceof TerminalSizeable) {
                        heightCalc += ((TerminalSizeable) child).getPreferredHeight();
                    } else if (child.getRequestedRegion() != null) {
                        heightCalc += child.getRequestedRegion().getHeight();
                    } else {
                        heightCalc += child.getRegion().getHeight(); 
                    }
                    count++;
                }
                
                if (count > 0) {
                    heightCalc += (count - 1) * spacing; // Add spacing
                }
            }
        }
         return Math.min(maxHeight, Math.max(getMinHeight(), heightCalc + getInsets().getVertical()));
    }

    

    public int getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        requestLayoutUpdate();
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
        requestLayoutUpdate();
    }

    public int getSpacing() {
        return spacing;
    }

    public void setSpacing(int spacing) {
        if (this.spacing != spacing) {
            this.spacing = spacing;
            requestLayoutUpdate();
        }
    }

    public TextStyle getFillStyle() {
        return fillStyle;
    }

    public void setFillStyle(TextStyle fillStyle) {
        if (this.fillStyle != fillStyle) {
            this.fillStyle = fillStyle;
            invalidate();
        }
    }

    public void setPadding(int all) {
        if (!padding.equals(all)) {
            padding.set(all, all, all, all);
            requestLayoutUpdate();
        }
    }

    public void setPadding(int vertical, int horizontal) {
        if (padding.getTop() != vertical ||
            padding.getRight() != horizontal ||
            padding.getBottom() != vertical ||
            padding.getLeft() != horizontal) {
            padding.set(vertical, horizontal, vertical, horizontal);
            requestLayoutUpdate();
        }
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        if (this.alignment != alignment) {
            this.alignment = alignment;
            requestLayoutUpdate();
        }
    }

    public boolean isPaddingLessThan1(){
        return padding.getBottom() < 1 || padding.getTop() < 1 || padding.getLeft() < 1 || padding.getRight() < 1;
    }

    @Override
    public TerminalInsets getInsets() {
        if (drawBorder && isPaddingLessThan1()) {
            return new TerminalInsets(Math.max(1, padding.getTop()), Math.max(1, padding.getRight()), Math.max(1, padding.getBottom()), Math.max(1, padding.getLeft()));
        }
        return padding;
    }

    @Override
    protected void onDestroying() {
        destroyLayoutGroup(layoutGroupId);
        layoutCallbackEntry = null;
    }
    
}
