package io.netnotes.terminal.components.panels;

import java.util.Map;

import io.netnotes.terminal.layout.TerminalGroupCallbackEntry;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutCallback;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalSizeable;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.renderer.layout.LayoutGroup.LayoutDataInterface;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.components.panels.TerminalHStack.HAlignment;

/**
 * TerminalVStack - Vertical stack layout container
 * 
 * Arranges children vertically with configurable spacing and sizing.
 * Does not render itself - purely a layout container.
 * 
 * SIZING:
 * - Width: Default is FILL (children take full width), can be set to FIT_CONTENT
 * - Height: Default is FIT_CONTENT (children use preferred height), can be set to FILL
 * - Children implementing TerminalLayoutable can override per-child
 * 
 * USAGE:
 * TerminalVStack stack = new TerminalVStack("messages");
 * stack.setSpacing(2);  // 2 rows between each child
 * stack.setPadding(1);  // 1 row padding around all children
 * stack.addChild(new TerminalLabel("msg1", "Line 1"));
 * stack.addChild(new TerminalLabel("msg2", "Line 2"));
 */
public class TerminalVStack extends TerminalRegion {

    public enum VAlignment {
        TOP,
        CENTER,
        BOTTOM
    }
    
    private int spacing = 1;  // Rows between children
    private final TerminalInsets padding = new TerminalInsets();  // Padding around all children
    private VAlignment vAlignment = VAlignment.TOP;
    private HAlignment hAlignment = HAlignment.CENTER;
    // Default sizing preferences for children that don't specify
    private SizePreference defaultWidthPreference = SizePreference.FILL;
    private SizePreference defaultHeightPreference = SizePreference.FIT_CONTENT;
    
    private final String layoutGroupId;
    private final String layoutCallbackId;
    private TerminalGroupCallbackEntry layoutCallbackEntry = null;

    public TerminalVStack(String name) {
        super(name);
        this.layoutGroupId = "vstack-" + getName();
        this.layoutCallbackId = "vstack-default";
        this.setWidthPreference(SizePreference.FILL);
        this.setHeightPreference(SizePreference.FIT_CONTENT);
        init();
    }

    private void init() {
        this.layoutCallbackEntry = new TerminalGroupCallbackEntry(
            getLayoutCallbackId(),
            this::layoutAllChildren
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
    
    // ===== CONFIGURATION =====
    
    public void setSpacing(int spacing) {
        if (this.spacing != spacing) {
            this.spacing = Math.max(0, spacing);
            requestLayoutUpdate();
        }
    }
    
    public void setPadding(int padding) {
        int clamped = Math.max(0, padding);
        if (this.padding.getTop() != clamped ||
            this.padding.getRight() != clamped ||
            this.padding.getBottom() != clamped ||
            this.padding.getLeft() != clamped) {
            this.padding.setAll(clamped);
            requestLayoutUpdate();
        }
    }

    public void setInsets(TerminalInsets padding) {
        if (padding == null) {
            if (!this.padding.isZero()) {
                this.padding.clear();
                requestLayoutUpdate();
            }
            return;
        }

        if (!this.padding.equals(padding)) {
            this.padding.copyFrom(padding);
            requestLayoutUpdate();
        }
    }
    
    public void setVAlignment(VAlignment vAlignment) {
        if (this.vAlignment != vAlignment && vAlignment != null) {
            this.vAlignment = vAlignment;
            requestLayoutUpdate();
        }
    }
    public VAlignment getVAlignment(){
        return vAlignment;
    }

    public void setHAlignment(HAlignment hAlignment){
        if(this.hAlignment != hAlignment && hAlignment != null){
            this.hAlignment = hAlignment;
            requestLayoutUpdate();
        }
    }

    public HAlignment getHAlignment() {
        return hAlignment;
    }
    
    public void setDefaultWidthPreference(SizePreference pref) {
        if (this.defaultWidthPreference != pref) {
            this.defaultWidthPreference = pref;
            requestLayoutUpdate();
        }
    }
    
    public void setDefaultHeightPreference(SizePreference pref) {
        if (this.defaultHeightPreference != pref) {
            this.defaultHeightPreference = pref;
            requestLayoutUpdate();
        }
    }
    
    public int getSpacing() { return spacing; }

    @Override
    public void setPercentWidth(float percent) {
        super.setPercentWidth(percent);
        requestLayoutUpdate();
    }

    @Override
    public void setPercentHeight(float percent) {
        super.setPercentHeight(percent);
        requestLayoutUpdate();
    }

    public TerminalInsets getInsets() { return padding; }
    public VAlignment getvAlignment() { return vAlignment; }
    public SizePreference getDefaultWidthPreference() { return defaultWidthPreference; }
    public SizePreference getDefaultHeightPreference() { return defaultHeightPreference; }
    
    // ===== CHILD MANAGEMENT =====
    
    @Override
    public void addChild(TerminalRenderable child) {
        this.addChild(child, null); 
    }

    @Override 
    public void addChild(TerminalRenderable child, TerminalLayoutCallback callback){
        super.addChild(child, null);
        addToLayoutGroup(child, layoutGroupId);
    }

    // ===== LAYOUT CALCULATION =====
    
    private void layoutAllChildren(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        if (contexts.length == 0) return;

        TerminalRectangle parentRegion = contexts[0].getParentRegion();
        if (parentRegion == null) return; // Parent is hidden
        
        int horizontalPadding = padding.getHorizontal();
        int verticalPadding = padding.getVertical();

        int availableWidth = parentRegion.getWidth() - horizontalPadding;
        int availableHeight = parentRegion.getHeight() - verticalPadding;
        
        int[] layoutIndices = new int[contexts.length];
        int layoutCount = 0;

        for (int i = 0; i < contexts.length; i++) {
            TerminalRenderable child = contexts[i].getRenderable();
            if (!shouldIncludeInLayout(child)) {
                continue;
            }
            layoutIndices[layoutCount++] = i;
        }

        if (layoutCount == 0) {
            return;
        }

        // First pass: calculate sizes and count FILL children
        int[] widths = new int[layoutCount];
        int[] heights = new int[layoutCount];
        SizePreference[] widthPrefs = new SizePreference[layoutCount];
        SizePreference[] heightPrefs = new SizePreference[layoutCount];
        
        int totalFitHeight = 0;
        int fillHeightCount = 0;
        
        for (int i = 0; i < layoutCount; i++) {
            int contextIndex = layoutIndices[i];
            TerminalRenderable child = contexts[contextIndex].getRenderable();
            
            widthPrefs[i] = resolvePreference(child, true);
            heightPrefs[i] = resolvePreference(child, false);
            
            widths[i] = calculateWidth(child, widthPrefs[i], availableWidth);

            if (heightPrefs[i] == SizePreference.FILL) {
                heights[i] = -1; // Mark for later calculation
                fillHeightCount++;
            } else if (heightPrefs[i] == SizePreference.PERCENT) {
                if (child instanceof TerminalSizeable s) {
                    int percentH = (int) (availableHeight * s.getPercentHeight() / 100.0f);
                    heights[i] = Math.max(s.getMinHeight(), percentH);
                } else {
                    heights[i] = calculateFitHeight(child, widths[i]);
                }
                totalFitHeight += heights[i];
            } else {
                heights[i] = calculateFitHeight(child, widths[i]);
                totalFitHeight += heights[i];
            }
        }
        
        // Calculate total spacing
        int totalSpacing = Math.max(0, layoutCount - 1) * spacing;
        
        // Distribute remaining height among FILL children
        int remainingHeight = availableHeight - totalFitHeight - totalSpacing;
        int fillHeight = fillHeightCount > 0 ? Math.max(0, remainingHeight / fillHeightCount) : 0;
        
        // Update FILL children heights and calculate total
        int totalHeight = totalSpacing;
        for (int i = 0; i < heights.length; i++) {
            if (heights[i] == -1) {
                if (contexts[i].getRenderable() instanceof TerminalSizeable s) {
                    heights[i] = Math.max(s.getMinHeight(), fillHeight);
                } else {
                    heights[i] = fillHeight;
                }
            }
            totalHeight += heights[i];
        }
        
        // Determine starting Y based on vertical alignment
        int startY = switch (vAlignment) {
            case TOP -> padding.getTop();
            case CENTER -> padding.getTop() + Math.max(0, (availableHeight - totalHeight) / 2);
            case BOTTOM -> padding.getTop() + Math.max(0, availableHeight - totalHeight);
        };
        
        // Second pass: assign positions using pooled builders
        int currentY = startY;
        for (int i = 0; i < layoutCount; i++) {
            int contextIndex = layoutIndices[i];
            TerminalRenderable r = contexts[contextIndex].getRenderable();
            String renderableName = r.getName();
            int x;

            if (widthPrefs[i] == SizePreference.FILL) {
                x = padding.getLeft();
            } else {
                int remaining = Math.max(0, availableWidth - widths[i]);

                switch (hAlignment) {
                    case LEFT -> x = padding.getLeft();
                    case CENTER -> x = padding.getLeft() + (remaining / 2);
                    case RIGHT -> x = padding.getLeft() + remaining;
                    default -> x = padding.getLeft() + (remaining / 2);
                }
            }

            boolean inBounds = isWithinParentBounds(
                x, currentY, widths[i], heights[i], parentRegion
            );

            boolean manageHidden = shouldManageHidden(r);

            TerminalLayoutData.TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder()
                .setX(x)
                .setY(currentY)
                .setWidth(widths[i])
                .setHeight(heights[i]);

            if (!inBounds) {
                builder.hidden(true);
            } else if (manageHidden) {
                builder.hidden(false);
            }

            TerminalLayoutData layout = builder.build();
         
            dataInterfaces.get(renderableName).setLayoutData(layout);
            
            currentY += heights[i] + spacing;
        }
    }
        
    /**
     * Resolve sizing preference for a child
     * Checks if child implements TerminalLayoutable, otherwise uses stack default
     */
    private SizePreference resolvePreference(TerminalRenderable child, boolean isWidth) {
        if (child instanceof TerminalSizeable sizeable) {
    
            SizePreference pref = isWidth 
                ? sizeable.getWidthPreference()
                : sizeable.getHeightPreference();

            if (pref != null && pref != SizePreference.INHERIT) {
                return pref;
            }
        }
        
        // Fall back to stack's default
        return isWidth ? defaultWidthPreference : defaultHeightPreference;
    }

    /**
     * Determine if a child should participate in layout
     * 
     * SEMANTICS:
     * - Hidden children: Do NOT participate in layout (do not affect spacing)
     * - Invisible children: DO participate in layout (take space but don't render)
     * - Visible children: Normal participation
     */
    private boolean shouldIncludeInLayout(TerminalRenderable child) {
        // Hidden children do NOT participate in layout - they don't affect spacing
        if (child.isHidden()) {
            return false;
        }
        
        return true;
    }

    private boolean shouldManageHidden(TerminalRenderable child) {
        if (child instanceof TerminalSizeable sizeable) {
            return sizeable.isHiddenManaged();
        }
        return true;
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
    
    /**
     * Calculate child width based on preference
     */
    private int calculateWidth(TerminalRenderable child, SizePreference pref, int available) {
        if (pref == SizePreference.FILL) {
            return available;
        }

        if (pref == SizePreference.PERCENT && child instanceof TerminalSizeable s) {
            int percent = (int) (available * s.getPercentWidth() / 100.0f);
            return Math.max(s.getMinWidth(), percent);
        }

        if (child instanceof TerminalSizeable s) {
            return Math.min(s.getPreferredWidth(), available);
        }

        if (child.getRequestedRegion() != null) {
            return Math.min(child.getRequestedRegion().getWidth(), available);
        }

        return available;
    }
    
    /**
     * Calculate child height for FIT_CONTENT preference
     * Override this in subclasses for custom height calculation
     */
    protected int calculateFitHeight(TerminalRenderable child, int availableWidth) {
        if (child instanceof TerminalSizeable s) {
            return s.getPreferredHeight();
        }

        if (child.getRequestedRegion() != null) {
            return child.getRequestedRegion().getHeight();
        }

        return 1;
    }


  
    @Override
    public int getPreferredWidth() {
        SizePreference pref = getWidthPreference();

        if (pref == SizePreference.STATIC) {
            return region.getWidth();
        }

        if (pref == SizePreference.PERCENT) {
            return getMinWidth();
        }

        int maxWidth = 0;

        if (pref == SizePreference.FIT_CONTENT) {
            for (TerminalRenderable child : getChildren()) {
                if (!shouldIncludeInLayout(child)) continue;

                if (child instanceof TerminalSizeable s) {
                    SizePreference childPref = s.getWidthPreference();

                    if (childPref == SizePreference.FIT_CONTENT ||
                        childPref == SizePreference.STATIC) {

                        maxWidth = Math.max(maxWidth, s.getPreferredWidth());
                    }
                } else if (child.getRequestedRegion() != null) {
                    maxWidth = Math.max(maxWidth,
                        child.getRequestedRegion().getWidth());
                }
            }
        }

        return Math.max(
            getMinWidth(),
            maxWidth + padding.getHorizontal()
        );
    }
    
   
    @Override
    public int getPreferredHeight() {
        SizePreference pref = getHeightPreference();

        if (pref == SizePreference.STATIC) {
            return region.getHeight();
        }

        if (pref == SizePreference.PERCENT) {
            return getMinHeight();
        }

        int totalHeight = 0;
        int count = 0;

        if (pref == SizePreference.FIT_CONTENT) {
            for (TerminalRenderable child : getChildren()) {
                if (!shouldIncludeInLayout(child)) continue;

                int childHeight = 0;

                if (child instanceof TerminalSizeable s) {
                    SizePreference childPref = s.getHeightPreference();

                    if (childPref == SizePreference.FIT_CONTENT ||
                        childPref == SizePreference.STATIC) {

                        childHeight = s.getPreferredHeight();
                    }
                } else if (child.getRequestedRegion() != null) {
                    childHeight = child.getRequestedRegion().getHeight();
                }

                if (childHeight > 0) {
                    totalHeight += childHeight;
                    count++;
                }
            }
        }

        if (count > 0) {
            totalHeight += (count - 1) * spacing;
        }

        return Math.max(
            getMinHeight(),
            totalHeight + padding.getVertical()
        );
    }

    

    public void setWidthPreference(SizePreference widthPreference) {
        super.setWidthPreference(widthPreference);
        requestLayoutUpdate();
    }

    public void setHeightPreference(SizePreference heightPreference) {
        super.setHeightPreference(heightPreference);
        requestLayoutUpdate();
    }

    @Override
    protected void onDestroying(){
        destroyLayoutGroup(layoutGroupId);   
    }
 
}
