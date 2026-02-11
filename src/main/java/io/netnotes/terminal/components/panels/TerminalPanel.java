package io.netnotes.terminal.components.panels;

import java.util.Map;

import io.netnotes.engine.ui.Position;
import io.netnotes.engine.ui.layout.LayoutGroup.LayoutDataInterface;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.layout.TerminalGroupCallbackEntry;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutCallback;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutable;
import io.netnotes.terminal.layout.TerminalSizeable;

public class TerminalPanel extends TerminalRegion {
    
    public enum Axis{
        VERTICAL,
        HORIZONTAL
    }

    public enum CrossAlignment {
        START,    // default
        CENTER,
        END,
        STRETCH   // only affects positioning if child < available cross
    }

    private boolean drawBorder = false;
    private TextStyle.BoxStyle borderStyle = TextStyle.BoxStyle.SINGLE;
    private String title = null;
    private Position titlePosition = Position.TOP_CENTER;
    private TextStyle textStyle = TextStyle.NORMAL;

    private final TerminalInsets padding = new TerminalInsets();

    private Axis axis = Axis.HORIZONTAL;
    private boolean wrap = false;
    private int spacing = 0;
    private CrossAlignment crossAlignment = CrossAlignment.START;
    private final String layoutGroupId;
    private final String layoutCallbackId;
    private TerminalGroupCallbackEntry layoutCallbackEntry = null;
    
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
        super.addChild(child);
        addToLayoutGroup(child, layoutGroupId);
    }

    /**
     * Individual callbacks are disabled for this panel
     */
    @Override
    public void addChild(TerminalRenderable child, TerminalLayoutCallback callback){
        this.addChild(child);
    }


    private void layoutChildren(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        if (contexts.length == 0) return;

        TerminalRectangle parent = contexts[0].getParentRegion();
        if (parent == null) return;

        TerminalInsets insets = getInsets();
        int startX = insets.getLeft();
        int startY = insets.getTop();

        int availableWidth  = parent.getWidth()  - insets.getHorizontal();
        int availableHeight = parent.getHeight() - insets.getVertical();

        int count = contexts.length;
        int[] widths  = new int[count];
        int[] heights = new int[count];

        int fitPrimaryTotal = 0;
        int fillPrimaryCount = 0;
        int fitCrossMax = 0;

        boolean anyVisible = false;

        // 1. Constraint negotiation (unchanged)
        for (int i = 0; i < count; i++) {
            TerminalRenderable child = contexts[i].getRenderable();

            if (child.isHidden()) {
                widths[i] = heights[i] = 0;
                continue;
            }

            anyVisible = true;

            int prefW = -1;
            int prefH = -1;

            if (child instanceof TerminalSizeable s) {
                SizePreference wPref = s.getWidthPreference();
                if (wPref == SizePreference.PERCENT) {
                    int percentW = (int) (availableWidth * s.getPercentWidth() / 100.0f);
                    prefW = Math.max(s.getMinWidth(), percentW);
                } else if (wPref == SizePreference.FIT_CONTENT || wPref == SizePreference.STATIC) {
                    prefW = Math.min(s.getPreferredWidth(), availableWidth);
                }
                SizePreference hPref = s.getHeightPreference();
                if (hPref == SizePreference.PERCENT) {
                    int percentH = (int) (availableHeight * s.getPercentHeight() / 100.0f);
                    prefH = Math.max(s.getMinHeight(), percentH);
                } else if (hPref == SizePreference.FIT_CONTENT || hPref == SizePreference.STATIC) {
                    prefH = Math.min(s.getPreferredHeight(), availableHeight);
                }
            } else if (child.getRequestedRegion() != null) {
                prefW = Math.min(child.getRequestedRegion().getWidth(),  availableWidth);
                prefH = Math.min(child.getRequestedRegion().getHeight(), availableHeight);
            }

            widths[i]  = prefW;
            heights[i] = prefH;

            int primary = axis == Axis.VERTICAL ? prefH : prefW;
            int cross   = axis == Axis.VERTICAL ? prefW : prefH;

            if (primary >= 0) {
                fitPrimaryTotal += primary;
            } else {
                fillPrimaryCount++;
            }

            if (cross >= 0) {
                fitCrossMax = Math.max(fitCrossMax, cross);
            }
        }

        if (!anyVisible) return;

        int availablePrimary =
            axis == Axis.VERTICAL ? availableHeight : availableWidth;

        int availableCross =
            axis == Axis.VERTICAL ? availableWidth : availableHeight;

        int remainingPrimary = availablePrimary - fitPrimaryTotal;
        int fillPrimary =
            fillPrimaryCount > 0 ? Math.max(1, remainingPrimary / fillPrimaryCount) : 0;

        int resolvedCross =
            fitCrossMax > 0 ? fitCrossMax : availableCross;

        // 2. Apply resolved constraints
        for (int i = 0; i < count; i++) {
            if (axis == Axis.VERTICAL) {
                if (heights[i] < 0) heights[i] = fillPrimary;
                if (widths[i]  < 0) widths[i]  = resolvedCross;
            } else {
                if (widths[i]  < 0) widths[i]  = fillPrimary;
                if (heights[i] < 0) heights[i] = resolvedCross;
            }
        }

        // 3. Ordering + wrap + cross-axis alignment
        int cursorX = startX;
        int cursorY = startY;

        int lineCrossExtent = 0;
        int maxPrimary =
            axis == Axis.VERTICAL ? parent.getHeight() : parent.getWidth();

        for (int i = 0; i < count; i++) {
            TerminalRenderable child = contexts[i].getRenderable();
            LayoutDataInterface<TerminalLayoutData> iface =
                dataInterfaces.get(child.getName());

            if (child.isHidden()) {
                iface.setLayoutData(TerminalLayoutData.getBuilder().build());
                continue;
            }

            int w = widths[i];
            int h = heights[i];

            int nextPrimary =
                axis == Axis.VERTICAL ? cursorY + h : cursorX + w;

            // ---- wrap ----
            if (wrap && nextPrimary > maxPrimary) {
                if (axis == Axis.VERTICAL) {
                    cursorY = startY;
                    cursorX += lineCrossExtent;
                } else {
                    cursorX = startX;
                    cursorY += lineCrossExtent;
                }
                lineCrossExtent = 0;
            }

            int x = cursorX;
            int y = cursorY;

            // ---- cross-axis alignment ----
            int freeCross = availableCross - (axis == Axis.VERTICAL ? w : h);

            if (freeCross > 0) {
                switch (crossAlignment) {
                    case CENTER -> {
                        if (axis == Axis.VERTICAL) x += freeCross / 2;
                        else y += freeCross / 2;
                    }
                    case END -> {
                        if (axis == Axis.VERTICAL) x += freeCross;
                        else y += freeCross;
                    }
                    case STRETCH -> {
                        if (axis == Axis.VERTICAL) w = availableCross;
                        else h = availableCross;
                    }
                    default -> {
                        // START → no offset
                    }
                }
            }

            boolean inBounds = isWithinParentBounds(x, y, w, h, parent);

            TerminalLayoutData.TerminalLayoutDataBuilder b =
                TerminalLayoutData.getBuilder()
                    .setX(x)
                    .setY(y)
                    .setWidth(w)
                    .setHeight(h);

            if (!inBounds) {
                b.hidden(true);
            } else if (shouldManageHidden(child)) {
                b.hidden(false);
            }

            iface.setLayoutData(b.build());

            if (axis == Axis.VERTICAL) {
                cursorY += h + spacing;
                lineCrossExtent = Math.max(lineCrossExtent, w);
            } else {
                cursorX += w + spacing;
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
        if (child instanceof TerminalLayoutable) {
            return ((TerminalLayoutable) child).isHiddenManaged();
        }
        return true;
    }

    
        
    public Axis getAxis() {
        return axis;
    }

    public void setAxis(Axis axis) {
        this.axis = axis;
        invalidate();
    }

    public boolean isWrap() {
        return wrap;
    }

    public void setWrap(boolean wrap) {
        this.wrap = wrap;
        invalidate();
    }

    public CrossAlignment getCrossAlignment() {
        return crossAlignment;
    }

    public void setCrossAlignment(CrossAlignment crossAlignment) {
        this.crossAlignment = crossAlignment;
        invalidate();
    }

    public void setBorder(boolean enabled) {
        if (this.drawBorder != enabled) {
            this.drawBorder = enabled;
            invalidate();
        }
    }
    
    public void setBorderStyle(TextStyle.BoxStyle style) {
        if (this.borderStyle != style) {
            this.borderStyle = style;
            if (drawBorder) {
                invalidate();
            }
        }
    }
    
    public void setTitle(String title) {
        if ((this.title == null && title != null) || 
            (this.title != null && !this.title.equals(title))) {
            this.title = title;
            if (drawBorder) {
                invalidate();
            }
        }
    }
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (drawBorder || title != null){
            
            // ✓ Use helper methods for dimensions
            int width = getWidth();
            int height = getHeight();
            TextStyle textStyle = hasFocus() ? TextStyle.FOCUSED : this.textStyle;
            
            drawBox(batch, 0, 0, width, height, title, titlePosition, borderStyle, textStyle);
        }
    }

    
    public Position getTitlePosition() {
        return titlePosition;
    }

    public void setTitlePosition(Position titlePosition) {
        this.titlePosition = titlePosition;
        invalidate();
    }

    @Override
    public int getPreferredWidth() {
        SizePreference pref = getWidthPreference();
        if(pref == SizePreference.STATIC){
            return region.getWidth();
        }
        if(pref == SizePreference.PERCENT){
            return getMinWidth();
        }
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
        return Math.max(getMinWidth(), widthCalc + padding.getHorizontal());
    }

    @Override
    public int getPreferredHeight() {
        SizePreference pref = getHeightPreference();
        if(pref == SizePreference.STATIC){
            return region.getHeight();
        }
        if(pref == SizePreference.PERCENT){
            return getMinHeight();
        }
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
        return Math.max(getMinHeight(), heightCalc + padding.getVertical());
    }

    public int getSpacing() {
        return spacing;
    }

    public void setSpacing(int spacing) {
        this.spacing = spacing;
        invalidate();
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
    protected void onCleanup() {
        destroyLayoutGroup(layoutGroupId);
        layoutCallbackEntry = null;
    }
    
}
