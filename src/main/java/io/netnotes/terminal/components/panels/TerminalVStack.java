package io.netnotes.terminal.components.panels;

import java.util.Arrays;
import java.util.Map;

import io.netnotes.debug.RenderDiagnostics;
import io.netnotes.engine.ui.LayoutOverflowStrategy;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.renderer.layout.LayoutGroup.LayoutDataInterface;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalSizeable;

/**
 * TerminalVStack — vertical stack layout container.
 *
 * Arranges children top-to-bottom with configurable spacing and sizing.
 *
 * SIZING (own axis = height, cross axis = width):
 * - Height: default FIT_CONTENT — sums intrinsic heights of fixed children.
 * - Width:  default FILL — expands to the full available width.
 * - Children implementing TerminalSizeable can override per-child.
 *
     * BORDER / SEPARATORS:
     * - setDrawBorder(true)     draws a box outline around all children.
     * - setDrawSeparators(true) draws a 1-row horizontal separator between each
     *   child. With drawBorder=true it also produces junction characters on the
     *   left and right border edges. A TerminalDivider child placed explicitly
     *   always produces junction characters when a border is present.
 *
 * OVERFLOW STRATEGIES (applied to the main / vertical axis):
 * - CLIP (default)     : children that overflow are hidden.
 * - OVERFLOW           : children render outside parent bounds without clipping.
 * - SHRINK_FILL        : FILL children receive exactly the available share.
 * - SHRINK_ALL         : all children scale proportionally if total exceeds height.
 * - DISTRIBUTE_EQUAL   : every visible child receives an equal share of height.
 */
public class TerminalVStack extends TerminalAbstractStack {

    // ── junction tracking ─────────────────────────────────────────────────────

    /**
     * Y positions of gap-separator rows between children (drawSeparators=true).
     * Written by the layout callback, read by renderSelf. Both run on the UI
     * thread so no additional synchronisation is needed.
     */
    private int[] separatorYs    = new int[0];

    /**
     * Y positions where a TerminalDivider child was placed.
     * Produces junction characters on the left/right border edges regardless
     * of drawSeparators.
     */
    private int[] dividerChildYs = new int[0];

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    public TerminalVStack(String name) {
        super(
            name,
            "vstack",
            SizePreference.FILL,        // default child-width  → children fill width
            SizePreference.FIT_CONTENT, // default child-height → children use intrinsic height
            VAlignment.TOP,
            HAlignment.CENTER
        );
        // Own sizing defaults: the stack itself fills width, fits its content height.
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FIT_CONTENT);

        // Must be last — all fields must be initialised before the layout group
        // is registered with the rendering system.
        initLayoutCallback();
    }

    // =========================================================================
    // ABSTRACT IMPLEMENTATION
    // =========================================================================

    @Override
    protected void initLayoutCallback() {
        this.layoutCallback = this::layoutAllChildren;
        registerChildGroupCallback(layoutGroupId, layoutCallback);
    }

    // =========================================================================
    // ACCESSORS — layout group identity
    // =========================================================================

    public String getLayoutGroupId()    { return layoutGroupId;    }
    public String getLayoutCallbackId() { return layoutCallbackId; }

    // =========================================================================
    // LAYOUT CALCULATION
    // =========================================================================

    private void layoutAllChildren(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        if (contexts.length == 0) return;

        TerminalRectangle parentRegion = contexts[0].getParentRegion();
        if (parentRegion == null) return;

        TerminalInsets ins  = getInsets();   // border-enforced when drawBorder=true
        int effectiveW      = parentRegion.getWidth();
        int effectiveH      = parentRegion.getHeight();
        int availableWidth  = effectiveW - ins.getHorizontal();
        int availableHeight = effectiveH - ins.getVertical();

        // ── collect visible indices ────────────────────────────────────────────
        int[] layoutIndices = new int[contexts.length];
        int layoutCount = 0;
        for (int i = 0; i < contexts.length; i++) {
            if (shouldIncludeInLayout(contexts[i].getRenderable())) {
                layoutIndices[layoutCount++] = i;
            }
        }
        if (layoutCount == 0) {
            separatorYs    = new int[0];
            dividerChildYs = new int[0];
            return;
        }

        // ── gap accounting ─────────────────────────────────────────────────────
        // drawSeparators=true: each gap is exactly 1 row (the separator row).
        // drawSeparators=false: each gap is the spacing value.
        int gapSize           = drawSeparators ? 1 : spacing;
        int separatorRowCount = drawSeparators ? Math.max(0, layoutCount - 1) : 0;
        int totalGapHeight    = Math.max(0, layoutCount - 1) * gapSize;
        int availableForChildren = availableHeight - totalGapHeight;

        if (availableWidth <= 0 || availableHeight <= 0 || availableForChildren < 0) {
            RenderDiagnostics.logRenderBlocker(
                "vstack-no-space:" + getName(),
                "TerminalVStack.layoutAllChildren",
                "non-positive-child-space",
                () -> "stack=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\tstackSizing=" + RenderDiagnostics.summarizeSizing(this)
                    + "\n\tparentRegion=" + RenderDiagnostics.summarizeRegion(parentRegion)
                    + "\n\tavailableWidth=" + availableWidth
                    + "\n\tavailableHeight=" + availableHeight
                    + "\n\tavailableForChildren=" + availableForChildren
                    + "\n\tspacing=" + spacing
                    + "\n\tgapSize=" + gapSize
                    + "\n\tinsets=" + ins
                    + "\n\tchildren=" + RenderDiagnostics.summarizeRenderables(getChildren(), 10)
            );
        }

        // ── pass 1: measure ────────────────────────────────────────────────────
        int[] widths      = new int[layoutCount];
        int[] heights     = new int[layoutCount];
        SizePreference[] widthPrefs  = new SizePreference[layoutCount];
        SizePreference[] heightPrefs = new SizePreference[layoutCount];

        int totalResolvedHeight = 0;
        int fillHeightCount = 0;

        for (int i = 0; i < layoutCount; i++) {
            TerminalLayoutContext childContext = contexts[layoutIndices[i]];
            TerminalRenderable child = childContext.getRenderable();
            boolean manageHidden = shouldManageHidden(child);
            TerminalSizeable s = (child instanceof TerminalSizeable) ? (TerminalSizeable) child : null;

            if (childContext.isHidden() && !manageHidden) {
                widths[i] = 0;
                heights[i] = 0;
                continue;
            }

            if (s != null) {
                widthPrefs[i] = s.getWidthPreference() == SizePreference.INHERIT
                    ? getWidthPreference()
                    : s.getWidthPreference();
                heightPrefs[i] = s.getHeightPreference() == SizePreference.INHERIT
                    ? getHeightPreference()
                    : s.getHeightPreference();
            } else {
                widthPrefs[i] = SizePreference.STATIC;
                heightPrefs[i] = SizePreference.STATIC;
            }

            switch (widthPrefs[i]) {
                case FILL:
                    widths[i] = availableWidth;
                    break;
                case FIT_CONTENT:
                    widths[i] = childContext.getMeasuredContentBounds() != null
                        ? childContext.getMeasuredContentBounds().getWidth() : -1;
                    if (widths[i] == -1) {
                        throw new IllegalStateException(
                            "FIT_CONTENT width preference requires measured content bounds. Missing for child: "
                                + child.getName());
                    }
                    break;
                case PERCENT:
                    widths[i] = Math.max(s.getMinWidth(),
                        (int) (availableWidth * s.getPercentWidth()));
                    break;
                case STATIC:
                    widths[i] = childContext.getRequestedRegion() != null
                        ? childContext.getRequestedRegion().getWidth()
                        : childContext.getCurrentRegion().getWidth();
                    break;
                default:
                    widths[i] = childContext.getCurrentRegion().getWidth();
                    break;
            }

            switch (heightPrefs[i]) {
                case FILL:
                    heights[i] = -1;   // resolved after all fixed sizes are known
                    fillHeightCount++;
                    break;
                case FIT_CONTENT:
                    heights[i] = childContext.getMeasuredContentBounds() != null
                        ? childContext.getMeasuredContentBounds().getHeight() : -1;
                    if (heights[i] == -1) {
                        throw new IllegalStateException(
                            "FIT_CONTENT height preference requires measured content bounds. Missing for child: "
                                + child.getName());
                    }
                    break;
                case PERCENT:
                    heights[i] = Math.max(s.getMinHeight(),
                        (int) (availableForChildren * s.getPercentHeight()));
                    break;
                case STATIC:
                    heights[i] = childContext.getRequestedRegion() != null
                        ? childContext.getRequestedRegion().getHeight()
                        : childContext.getCurrentRegion().getHeight();
                    break;
                default:
                    heights[i] = childContext.getCurrentRegion().getHeight();
                    break;
            }

            if (heights[i] >= 0) {
                totalResolvedHeight += heights[i];
            }
        }

        // ── resolve FILL heights ───────────────────────────────────────────────
        int remaining  = availableForChildren - totalResolvedHeight;
        int fillHeight = fillHeightCount > 0 ? Math.max(0, remaining / fillHeightCount) : 0;

        int totalHeight = totalGapHeight;

        switch (overflowStrategy) {

            case SHRINK_FILL -> {
                // Give FILL children exactly the available share; do not inflate
                // to minHeight, so a space-starved stack shrinks gracefully.
                for (int i = 0; i < layoutCount; i++) {
                    if (heights[i] == -1) heights[i] = Math.max(0, fillHeight);
                    totalHeight += heights[i];
                }
            }

            case SHRINK_ALL -> {
                // Use each child's hint height, then scale everyone down
                // proportionally if the total exceeds the available space.
                for (int i = 0; i < layoutCount; i++) {
                    if (heights[i] == -1) heights[i] = getLayoutHeightHint(contexts[layoutIndices[i]]);
                    totalHeight += heights[i];
                }
                int totalRequested = totalHeight - totalGapHeight;
                if (totalRequested > availableForChildren && totalRequested > 0) {
                    float scale = (float) availableForChildren / totalRequested;
                    totalHeight = totalGapHeight;
                    for (int i = 0; i < layoutCount; i++) {
                        TerminalRenderable child = contexts[layoutIndices[i]].getRenderable();
                        int min = (child instanceof TerminalSizeable s) ? s.getMinHeight() : 0;
                        heights[i] = Math.max(min, (int)(heights[i] * scale));
                        totalHeight += heights[i];
                    }
                }
            }

            case DISTRIBUTE_EQUAL -> {
                int equalShare = layoutCount > 0
                    ? Math.max(0, availableForChildren / layoutCount) : 0;
                for (int i = 0; i < layoutCount; i++) {
                    TerminalRenderable child = contexts[layoutIndices[i]].getRenderable();
                    int min = (child instanceof TerminalSizeable s) ? s.getMinHeight() : 0;
                    heights[i] = Math.max(min, equalShare);
                    totalHeight += heights[i];
                }
            }

            // SCROLL falls through to CLIP until scroll support is implemented.
            default -> {
                for (int i = 0; i < layoutCount; i++) {
                    if (heights[i] == -1) {
                        TerminalRenderable child = contexts[layoutIndices[i]].getRenderable();
                        heights[i] = (child instanceof TerminalSizeable s)
                            ? Math.max(s.getMinHeight(), fillHeight)
                            : Math.max(0, fillHeight);
                    }
                    totalHeight += heights[i];
                }
            }
        }

        // ── starting Y (vertical alignment) ───────────────────────────────────
        int startY = switch (vAlignment) {
            case TOP    -> ins.getTop();
            case CENTER -> ins.getTop() + Math.max(0, (availableHeight - totalHeight) / 2);
            case BOTTOM -> ins.getTop() + Math.max(0, availableHeight - totalHeight);
        };

        // ── pass 2: place + record junction positions ──────────────────────────
        separatorYs    = new int[separatorRowCount];
        dividerChildYs = new int[0];
        int sepIdx   = 0;
        int currentY = startY;

        for (int i = 0; i < layoutCount; i++) {
            final int childIndex = i;
            TerminalRenderable r = contexts[layoutIndices[childIndex]].getRenderable();

            int x;
            if (widthPrefs[childIndex] == SizePreference.FILL) {
                x = ins.getLeft();
            } else {
                int remaining2 = Math.max(0, availableWidth - widths[childIndex]);
                x = switch (hAlignment) {
                    case LEFT   -> ins.getLeft();
                    case RIGHT  -> ins.getLeft() + remaining2;
                    default     -> ins.getLeft() + remaining2 / 2;
                };
            }

            int remainingWidth  = Math.max(0, effectiveW - ins.getRight() - x);
            int remainingHeight = Math.max(0, effectiveH - ins.getBottom() - currentY);
            int allocatedWidth  = Math.min(widths[childIndex],  remainingWidth);
            int allocatedHeight = overflowStrategy == LayoutOverflowStrategy.OVERFLOW
                ? Math.max(0, heights[childIndex])
                : Math.min(heights[childIndex], remainingHeight);
            boolean hasSpace  = allocatedWidth > 0 && allocatedHeight > 0;
            boolean inBounds  = overflowStrategy == LayoutOverflowStrategy.OVERFLOW
                ? hasSpace && x >= 0 && x + allocatedWidth <= parentRegion.getWidth()
                : hasSpace && isWithinParentBounds(
                    x, currentY, allocatedWidth, allocatedHeight, parentRegion);
            boolean manageHidden = shouldManageHidden(r);

            TerminalLayoutData.TerminalLayoutDataBuilder b = TerminalLayoutData.getBuilder()
                .setX(x)
                .setY(currentY)
                .setWidth(Math.max(0, allocatedWidth))
                .setHeight(Math.max(0, allocatedHeight));

            if (!inBounds) {
                final int childX      = x;
                final int childY      = currentY;
                final int childWidth  = allocatedWidth;
                final int childHeight = allocatedHeight;
                if (overflowStrategy == LayoutOverflowStrategy.OVERFLOW) {
                    if (manageHidden) b.hidden(false);
                } else {
                    RenderDiagnostics.logRenderBlocker(
                        "vstack-child-oob:" + getName() + ":" + r.getName(),
                        "TerminalVStack.layoutAllChildren",
                        hasSpace ? "child-hidden-out-of-parent-bounds" : "child-hidden-no-space-remaining",
                        () -> "stack=" + RenderDiagnostics.summarizeRenderable(this)
                            + "\n\tstackSizing=" + RenderDiagnostics.summarizeSizing(this)
                            + "\n\tchild=" + RenderDiagnostics.summarizeRenderable(r)
                            + "\n\tchildSizing=" + RenderDiagnostics.summarizeSizing(r)
                            + "\n\twidthPref=" + widthPrefs[childIndex]
                            + "\n\theightPref=" + heightPrefs[childIndex]
                            + "\n\tmeasuredSize=" + widths[childIndex] + "x" + heights[childIndex]
                            + "\n\tallocatedSize=" + childWidth + "x" + childHeight
                            + "\n\tcomputedBounds="
                            + RenderDiagnostics.summarizeRegion(
                                new TerminalRectangle(childX, childY, childWidth, childHeight))
                            + "\n\tparentRegion=" + RenderDiagnostics.summarizeRegion(parentRegion)
                    );
                    b.hidden(true);
                }
            } else if (manageHidden) {
                b.hidden(false);
            }

            dataInterfaces.get(r.getName()).setLayoutData(b.build());

            int childStartY = currentY;
            currentY += Math.max(0, allocatedHeight);

            // Gap separator: record the row immediately below this child where the
            // 1-row separator will be drawn (drawSeparators=true only).
            if (drawSeparators && i < layoutCount - 1
                    && currentY < effectiveH - ins.getBottom()) {
                separatorYs[sepIdx++] = currentY;
            }

            // Divider child junction: a TerminalDivider placed as a child always
            // produces left/right junction characters at its own row.
            if (drawBorder && r instanceof TerminalDivider) {
                dividerChildYs = appendInt(dividerChildYs, childStartY);
            }

            currentY += gapSize;
        }

        if (sepIdx != separatorYs.length) {
            separatorYs = Arrays.copyOf(separatorYs, sepIdx);
        }
    }

    /**
     * Height hint for SHRINK_ALL: measured content height → minHeight → requestedRegion height.
     * Returns 0 when no sizing information is available.
     */
    private int getLayoutHeightHint(TerminalLayoutContext ctx) {
        return Math.max(0, readDimension(ctx, false));
    }



    // =========================================================================
    // RENDERING
    // =========================================================================

    /**
     * Renders the border box with horizontal junction characters at each separator
     * row and each TerminalDivider child row.
     *
     * Junction arrays are populated by the layout callback so they always reflect
     * positions committed in the most recent layout pass.
     */
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (drawBorder) {
            int totalJunctions = separatorYs.length + dividerChildYs.length;
            int[] junctions;
            if (totalJunctions == 0) {
                junctions = new int[0];
            } else {
                junctions = new int[totalJunctions];
                System.arraycopy(separatorYs,    0, junctions, 0,                  separatorYs.length);
                System.arraycopy(dividerChildYs, 0, junctions, separatorYs.length, dividerChildYs.length);
                Arrays.sort(junctions);
            }

            drawTableRowBorder(batch, 0, 0, getWidth(), getHeight(), borderStyle, borderTextStyle, junctions);
        }

        if (drawSeparators && !drawBorder) {
            renderStandaloneSeparators(batch);
        }
    }

    private void renderStandaloneSeparators(TerminalBatchBuilder batch) {
        if (separatorYs.length == 0) {
            return;
        }

        TerminalInsets ins = getInsets();
        int lineX = ins.getLeft();
        int lineWidth = Math.max(0, getWidth() - ins.getHorizontal());
        if (lineWidth <= 0) {
            return;
        }

        for (int separatorY : separatorYs) {
            drawHLine(batch, lineX, separatorY, lineWidth, borderStyle, borderTextStyle);
        }
    }

    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        SizePreference ownWidthPref  = getWidthPreference();
        SizePreference ownHeightPref = getHeightPreference();
 
        int maxWidth     = 0;
        int totalHeight  = 0;
        int visibleCount = 0;
 
        if (childContexts != null) {
            for (TerminalLayoutContext ctx : childContexts) {
                if (ctx == null) continue;
                TerminalRenderable child = ctx.getRenderable();
                if (!shouldIncludeInLayout(child)) continue;
                visibleCount++;
 
                TerminalSizeable s = (child instanceof TerminalSizeable) ? (TerminalSizeable) child : null;
                SizePreference childWidthPref  = s != null
                    ? (s.getWidthPreference() == SizePreference.INHERIT
                        ? getWidthPreference()
                        : s.getWidthPreference())
                    : SizePreference.STATIC;
                SizePreference childHeightPref = s != null
                    ? (s.getHeightPreference() == SizePreference.INHERIT
                        ? getHeightPreference()
                        : s.getHeightPreference())
                    : SizePreference.STATIC;
 
                if (ownWidthPref == SizePreference.FIT_CONTENT
                 && (childWidthPref == SizePreference.FIT_CONTENT
                  || childWidthPref == SizePreference.STATIC)) {
                    maxWidth = Math.max(maxWidth, readDimension(ctx, true));
                }
                if (ownHeightPref == SizePreference.FIT_CONTENT
                 && (childHeightPref == SizePreference.FIT_CONTENT
                  || childHeightPref == SizePreference.STATIC)) {
                    int ch = readDimension(ctx, false);
                    if (ch > 0) totalHeight += ch;
                }
            }
        }
 
        if (ownHeightPref == SizePreference.FIT_CONTENT && visibleCount > 1) {
            totalHeight += (visibleCount - 1) * (drawSeparators ? 1 : spacing);
        }
 
        int w = switch (ownWidthPref) {
            case STATIC      -> region.getWidth();
            case FIT_CONTENT -> Math.max(getMinWidth(), maxWidth + getInsets().getHorizontal());
            default          -> getMinWidth();
        };
        int h = switch (ownHeightPref) {
            case STATIC      -> region.getHeight();
            case FIT_CONTENT -> Math.max(getMinHeight(), totalHeight + getInsets().getVertical());
            default          -> getMinHeight();
        };
 
        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, w, h);
        return measured;
    }

    private int readDimension(TerminalLayoutContext ctx, boolean isWidth) {
        TerminalRectangle bounds = ctx.getMeasuredContentBounds();
        if (bounds != null) return isWidth ? bounds.getWidth() : bounds.getHeight();

        TerminalRenderable child = ctx.getRenderable();
        TerminalRectangle requested = child.getRequestedRegion();
        if (requested != null) return isWidth ? requested.getWidth() : requested.getHeight();

        return isWidth ? child.getRegion().getWidth() : child.getRegion().getHeight();
    }

  
}
