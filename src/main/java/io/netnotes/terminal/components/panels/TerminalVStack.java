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
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.TerminalBatchBuilder;
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
    private boolean drawBorder          = false;
    private boolean drawSeparators      = false;   // requires drawBorder = true
    private LineStyle borderStyle        = TextStyle.LineStyle.SINGLE;
    private TextStyle borderTextStyle   = TextStyle.NORMAL;

    private int[] separatorYs = new int[0];
    
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

    @Override
    public TerminalInsets getInsets() {
        if (drawBorder) {
            return new TerminalInsets(
                Math.max(1, padding.getTop()),
                Math.max(1, padding.getRight()),
                Math.max(1, padding.getBottom()),
                Math.max(1, padding.getLeft())
            );
        }
        return padding;
    }

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
        if (parentRegion == null) return;

        TerminalInsets insets = getInsets();   // respects border minimum
        int availableWidth  = parentRegion.getWidth()  - insets.getHorizontal();
        int availableHeight = parentRegion.getHeight() - insets.getVertical();

        // ── collect visible indices ────────────────────────────────────────────
        int[] layoutIndices = new int[contexts.length];
        int layoutCount = 0;
        for (int i = 0; i < contexts.length; i++) {
            if (shouldIncludeInLayout(contexts[i].getRenderable())) {
                layoutIndices[layoutCount++] = i;
            }
        }
        if (layoutCount == 0) {
            separatorYs = new int[0];
            return;
        }

        // ── separator accounting ───────────────────────────────────────────────
        // Each separator between children costs 1 row. When drawSeparators is
        // false the existing spacing field is used as usual.
        int gapSize           = drawSeparators ? 1 : spacing;
        int separatorRowCount = drawSeparators ? Math.max(0, layoutCount - 1) : 0;
        int totalGapHeight    = Math.max(0, layoutCount - 1) * gapSize;

        // Available height after reserving all gap / separator rows
        int availableForChildren = availableHeight - totalGapHeight;

        // ── pass 1: measure ────────────────────────────────────────────────────
        int[] widths      = new int[layoutCount];
        int[] heights     = new int[layoutCount];
        SizePreference[] widthPrefs  = new SizePreference[layoutCount];
        SizePreference[] heightPrefs = new SizePreference[layoutCount];

        int totalFitHeight  = 0;
        int fillHeightCount = 0;

        for (int i = 0; i < layoutCount; i++) {
            TerminalRenderable child = contexts[layoutIndices[i]].getRenderable();

            widthPrefs[i]  = resolvePreference(child, true);
            heightPrefs[i] = resolvePreference(child, false);
            widths[i]      = calculateWidth(child, widthPrefs[i], availableWidth);

            if (heightPrefs[i] == SizePreference.FILL) {
                heights[i] = -1;
                fillHeightCount++;
            } else if (heightPrefs[i] == SizePreference.PERCENT) {
                heights[i] = (child instanceof TerminalSizeable s)
                    ? Math.max(s.getMinHeight(), (int)(availableForChildren * s.getPercentHeight() / 100f))
                    : calculateFitHeight(child, widths[i]);
                totalFitHeight += heights[i];
            } else {
                heights[i] = calculateFitHeight(child, widths[i]);
                totalFitHeight += heights[i];
            }
        }

        // ── resolve FILL heights ───────────────────────────────────────────────
        int remaining  = availableForChildren - totalFitHeight;
        int fillHeight = fillHeightCount > 0 ? Math.max(0, remaining / fillHeightCount) : 0;

        int totalHeight = totalGapHeight;
        for (int i = 0; i < layoutCount; i++) {
            if (heights[i] == -1) {
                heights[i] = (contexts[layoutIndices[i]].getRenderable() instanceof TerminalSizeable s)
                    ? Math.max(s.getMinHeight(), fillHeight)
                    : fillHeight;
            }
            totalHeight += heights[i];
        }

        // ── starting Y (vertical alignment) ───────────────────────────────────
        int startY = switch (vAlignment) {
            case TOP    -> insets.getTop();
            case CENTER -> insets.getTop() + Math.max(0, (availableHeight - totalHeight) / 2);
            case BOTTOM -> insets.getTop() + Math.max(0, availableHeight - totalHeight);
        };

        // ── pass 2: place + record separator positions ─────────────────────────
        separatorYs = new int[separatorRowCount];
        int sepIdx  = 0;
        int currentY = startY;

        for (int i = 0; i < layoutCount; i++) {
            TerminalRenderable r = contexts[layoutIndices[i]].getRenderable();
            int x;

            if (widthPrefs[i] == SizePreference.FILL) {
                x = insets.getLeft();
            } else {
                int remaining2 = Math.max(0, availableWidth - widths[i]);
                x = switch (hAlignment) {
                    case LEFT   -> insets.getLeft();
                    case RIGHT  -> insets.getLeft() + remaining2;
                    default     -> insets.getLeft() + remaining2 / 2;
                };
            }

            boolean inBounds = isWithinParentBounds(x, currentY, widths[i], heights[i], parentRegion);

            TerminalLayoutData.TerminalLayoutDataBuilder b = TerminalLayoutData.getBuilder()
                .setX(x).setY(currentY).setWidth(widths[i]).setHeight(heights[i]);

            if (!inBounds) {
                b.hidden(true);
            } else if (shouldManageHidden(r)) {
                b.hidden(false);
            }

            dataInterfaces.get(r.getName()).setLayoutData(b.build());

            currentY += heights[i];

            // Record separator position in the gap row immediately after this child
            if (drawSeparators && i < layoutCount - 1) {
                separatorYs[sepIdx++] = currentY;  // gap row Y
            }

            currentY += gapSize;
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

    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (!drawBorder) return;
        drawTableRowBorder(batch, 0, 0, getWidth(), getHeight(), borderStyle, borderTextStyle, separatorYs);
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
