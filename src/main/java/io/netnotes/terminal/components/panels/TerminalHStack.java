package io.netnotes.terminal.components.panels;


import java.util.Map;

import io.netnotes.terminal.TerminalBatchBuilder;
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
import io.netnotes.terminal.components.panels.TerminalVStack.VAlignment;

/**
 * TerminalHStack - Horizontal stack layout container
 * 
 * Arranges children horizontally with configurable spacing and sizing.
 * Does not render itself - purely a layout container.
 * 
 * SIZING:
 * - Width: Default is FIT_CONTENT (children use preferred width), can be set to FILL
 * - Height: Default is FILL (children take full height) - good for most UI
 * - Children implementing TerminalLayoutable can override per-child
 * 
 * USAGE:
 * TerminalHStack stack = new TerminalHStack("toolbar");
 * stack.setSpacing(2);  // 2 columns between each child
 * stack.setPadding(1);  // 1 column padding around all children
 * stack.addChild(new TerminalButton("btn1", "Save"));
 * stack.addChild(new TerminalButton("btn2", "Load"));
 */
public class TerminalHStack extends TerminalRegion {
        
    public enum HAlignment {
        LEFT,
        CENTER,
        RIGHT
    }

    private int spacing = 1;  // Columns between children

    private HAlignment hAlignment = HAlignment.LEFT;
    private VAlignment vAlignment = VAlignment.CENTER;
    
    // Default sizing preferences for children that don't specify
    private SizePreference defaultWidthPreference = SizePreference.FIT_CONTENT;
    private SizePreference defaultHeightPreference = SizePreference.FILL;
    
    private final String layoutGroupId;
    private final String layoutCallbackId;
    private TerminalGroupCallbackEntry layoutCallbackEntry = null;

    
    public TerminalHStack(String name) {
        super(name);
        this.layoutGroupId = "hstack-" + getName();
        this.layoutCallbackId = "hstack-default";
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
   
        if (insets.getTop() != clamped ||
            this.insets.getRight() != clamped ||
            this.insets.getBottom() != clamped ||
            this.insets.getLeft() != clamped) {
            this.insets.setAll(clamped);
            requestLayoutUpdate();
        }
    }


    public HAlignment getHAlignment(){
        return hAlignment;
    }
    
    public void sethAlignment(HAlignment alignment) {
        if (this.hAlignment != alignment) {
            this.hAlignment = alignment;
            requestLayoutUpdate();
        }
    }

    public void setVAlignment(VAlignment alignment) {
        if (this.vAlignment != alignment) {
            this.vAlignment = alignment;
            requestLayoutUpdate();
        }
    }

    public VAlignment getVAlignment(VAlignment alignment){
        return vAlignment;
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

    public TerminalInsets getInsets() { return insets; }
    public HAlignment getvAlignment() { return hAlignment; }
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
        
        int horizontalPadding = insets.getHorizontal();
        int verticalPadding = insets.getVertical();

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
        
        int totalFitWidth = 0;
        int fillWidthCount = 0;
        
        for (int i = 0; i < layoutCount; i++) {
            int contextIndex = layoutIndices[i];
            TerminalRenderable child = contexts[contextIndex].getRenderable();
            
            widthPrefs[i] = resolvePreference(child, true);
            heightPrefs[i] = resolvePreference(child, false);
            
            heights[i] = calculateHeight(child, heightPrefs[i], availableHeight);

            if (widthPrefs[i] == SizePreference.FILL) {
                widths[i] = -1; // Mark for later calculation
                fillWidthCount++;
            } else if (widthPrefs[i] == SizePreference.PERCENT) {
                if (child instanceof TerminalSizeable s) {
                    int percentW = (int) (availableWidth * s.getPercentWidth() / 100.0f);
                    widths[i] = Math.max(s.getMinWidth(), percentW);
                } else {
                    widths[i] = calculateFitWidth(child, heights[i]);
                }
                totalFitWidth += widths[i];
            } else {
                widths[i] = calculateFitWidth(child, heights[i]);
                totalFitWidth += widths[i];
            }
        }
        
        // Calculate total spacing
        int totalSpacing = Math.max(0, layoutCount - 1) * spacing;
        
        // Distribute remaining width among FILL children
        int remainingWidth = availableWidth - totalFitWidth - totalSpacing;
        int fillWidth = fillWidthCount > 0 ? Math.max(0, remainingWidth / fillWidthCount) : 0;
        
        // Update FILL children widths and calculate total
        int totalWidth = totalSpacing;
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] == -1) {
                TerminalRenderable child = contexts[layoutIndices[i]].getRenderable();

                if (child instanceof TerminalSizeable s) {
                    widths[i] = Math.max(s.getMinWidth(), fillWidth);
                } else {
                    widths[i] = fillWidth;
                }
            }
            totalWidth += widths[i];
        }
        
        // Determine starting X based on horizontal alignment
        int startX = switch (hAlignment) {
            case LEFT -> insets.getLeft();
            case CENTER -> insets.getLeft() + Math.max(0, (availableWidth - totalWidth) / 2);
            case RIGHT -> insets.getLeft() + Math.max(0, availableWidth - totalWidth);
        };
        
        // Second pass: assign positions using pooled builders
        int currentX = startX;
        for (int i = 0; i < layoutCount; i++) {
            int contextIndex = layoutIndices[i];
            TerminalRenderable r = contexts[contextIndex].getRenderable();
            String renderableName = r.getName();
            int y;

            if (heightPrefs[i] == SizePreference.FILL) {
                y = insets.getTop();
            } else {
                int remaining = Math.max(0, availableHeight - heights[i]);

                switch (vAlignment) {
                    case TOP -> y = insets.getTop();
                    case CENTER -> y = insets.getTop() + (remaining / 2);
                    case BOTTOM -> y = insets.getTop() + remaining;
                    default -> y = insets.getTop() + (remaining / 2);
                }
            }

            boolean inBounds = isWithinParentBounds(
                currentX, y, widths[i], heights[i], parentRegion
            );
            boolean manageHidden = shouldManageHidden(r);

            TerminalLayoutData.TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder()
                .setX(currentX)
                .setY(y)
                .setWidth(widths[i])
                .setHeight(heights[i]);

            if (!inBounds) {
                builder.hidden(true);
            } else if (manageHidden) {
                builder.hidden(false);
            }

            TerminalLayoutData layout = builder.build();
            
            dataInterfaces.get(renderableName).setLayoutData(layout);
            
            currentX += widths[i] + spacing;
        }
    }
    
    /**
     * Resolve sizing preference for a child
     * Checks if child implements TerminalLayoutable, otherwise uses stack default
     */
    private SizePreference resolvePreference(TerminalRenderable child, boolean isWidth) {
        if (child instanceof TerminalSizeable) {
            TerminalSizeable sizeable = (TerminalSizeable) child;
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

    private boolean shouldManageHidden(TerminalRenderable child) {
        if (child instanceof TerminalSizeable sizeable) {
            return sizeable.isHiddenManaged();
        }
        return true;
    }

    private boolean shouldIncludeInLayout(TerminalRenderable child) {
        // Hidden children do NOT participate in layout - they don't affect spacing
        if (child.isHidden()) {
            return false;
        }

        // Invisible children DO participate - they take space but don't render
        if (child.isInvisible()) {
            return true;
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
     * Calculate child height based on preference
     */
    private int calculateHeight(
        TerminalRenderable child,
        SizePreference pref,
        int available
    ) {
        if (pref == SizePreference.FILL) {
            return available;
        }

        if (pref == SizePreference.PERCENT && child instanceof TerminalSizeable s) {
            int percent = (int) (available * s.getPercentHeight() / 100.0f);
            return Math.max(s.getMinHeight(), percent);
        }

        if (child instanceof TerminalSizeable s) {
            return Math.min(s.getPreferredHeight(), available);
        }

        if (child.getRequestedRegion() != null) {
            return Math.min(child.getRequestedRegion().getHeight(), available);
        }

        return available;
    }
    
    /**
     * Calculate child width for FIT_CONTENT preference
     * Override this in subclasses for custom width calculation
     */
    protected int calculateFitWidth(TerminalRenderable child, int availableHeight) {
        if (child.getRequestedRegion() != null) {
            return child.getRequestedRegion().getWidth();
        }
        return 10;  // Default to reasonable width for buttons/labels
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

        int totalWidth = 0;
        int count = 0;

        if (pref == SizePreference.FIT_CONTENT) {
            for (TerminalRenderable child : getChildren()) {
                if (!shouldIncludeInLayout(child)) continue;

                int childWidth = 0;

                if (child instanceof TerminalSizeable s) {
                    SizePreference childPref = s.getWidthPreference();

                    if (childPref == SizePreference.FIT_CONTENT ||
                        childPref == SizePreference.STATIC) {

                        childWidth = s.getPreferredWidth();
                    }
                } 
                else if (child.getRequestedRegion() != null) {
                    childWidth = child.getRequestedRegion().getWidth();
                }

                if (childWidth > 0) {
                    totalWidth += childWidth;
                    count++;
                }
            }

            if (count > 0) {
                totalWidth += (count - 1) * spacing;
            }
        }

        return Math.max(
            getMinWidth(),
            totalWidth + insets.getHorizontal()
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

        int maxHeight = 0;

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
                }
                else if (child.getRequestedRegion() != null) {
                    childHeight = child.getRequestedRegion().getHeight();
                }

                maxHeight = Math.max(maxHeight, childHeight);
            }
        }

        return Math.max(
            getMinHeight(),
            maxHeight + insets.getVertical()
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
    protected void renderSelf(TerminalBatchBuilder batch) {
        // HStack is a pure layout container - no rendering
    }
    
    // ===== ENUMS =====

    @Override
    protected void onDestroying(){
        destroyLayoutGroup(layoutGroupId);   
    }
}
