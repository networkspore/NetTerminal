package io.netnotes.terminal.components.panels;

import java.util.Arrays;
import java.util.Map;

import io.netnotes.debug.RenderDiagnostics;
import io.netnotes.engine.ui.LayoutOverflowStrategy;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.renderer.LayoutGroup.LayoutDataInterface;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutGroupCallback;

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

    }

    // =========================================================================
    // ABSTRACT IMPLEMENTATION
    // =========================================================================

    @Override
    protected TerminalLayoutGroupCallback createLayoutCallback() {
        return this.layoutCallback = this::layoutAllChildren;
    }

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

        TerminalInsets ins       = getInsets();
        int effectiveW           = parentRegion.getWidth();
        int effectiveH           = parentRegion.getHeight();
        int availableWidth       = effectiveW - ins.getHorizontal();
        int availableHeight      = effectiveH - ins.getVertical();

        // Whether this stack hides children that don't fit in the available height.
        // FIT_CONTENT and OVERFLOW never hide — FIT_CONTENT because the stack grows
        // to fit its children, OVERFLOW because children intentionally exceed bounds.
        boolean clipsChildren = getHeightPreference() != SizePreference.FIT_CONTENT
                            && overflowStrategy != LayoutOverflowStrategy.OVERFLOW;

        // ── separate participating children from layout-hidden ones ───────────────
        // Layout-hidden children (isHidden()) need another pass to become visible.
        // They get a zero-size placeholder and are otherwise ignored this pass.
        // Force-hidden children are not in contexts[] at all — the manager excluded them.
        int[] layoutIndices = new int[contexts.length];
        int layoutCount = 0;
        for (int i = 0; i < contexts.length; i++) {
            if(!contexts[i].getRenderable().isHiddenForced()){
                layoutIndices[layoutCount++] = i;
            }
        }

        if (layoutCount == 0) {
            separatorYs    = new int[0];
            dividerChildYs = new int[0];
            return;
        }

        // ── gap accounting ─────────────────────────────────────────────────────────
        int gapSize           = drawSeparators ? 1 : spacing;
        int separatorRowCount = drawSeparators ? layoutCount - 1 : 0;
        int totalGapHeight    = (layoutCount - 1) * gapSize;
        int availableForChildren = Math.max(0, availableHeight - totalGapHeight);

        if (availableWidth <= 0 || (clipsChildren && availableHeight <= 0)) {
            RenderDiagnostics.logRenderBlocker(
                "vstack-no-space:" + getName(),
                "TerminalVStack.layoutAllChildren",
                "non-positive-child-space",
                () -> "stack=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\tstackSizing=" + RenderDiagnostics.summarizeSizing(this)
                    + "\n\tparentRegion=" + RenderDiagnostics.summarizeRegion(parentRegion)
                    + "\n\tavailableWidth=" + availableWidth
                    + "\n\tavailableHeight=" + availableHeight
                    + "\n\tclipsChildren=" + clipsChildren
                    + "\n\tinsets=" + ins
                    + "\n\tchildren=" + RenderDiagnostics.summarizeRenderables(getChildren(), 10)
            );
        }

        // ── pass 1: measure each child's width and height ─────────────────────────
        int[] widths      = new int[layoutCount];
        int[] heights     = new int[layoutCount];
        SizePreference[] widthPrefs  = new SizePreference[layoutCount];
        SizePreference[] heightPrefs = new SizePreference[layoutCount];
        int totalResolvedHeight = 0;
        int fillHeightCount     = 0;

        for (int i = 0; i < layoutCount; i++) {
            TerminalLayoutContext ctx   = contexts[layoutIndices[i]];
            TerminalRegion        child = checkTerminalRegion(ctx.getRenderable());

            widthPrefs[i]  = child.getWidthPreference()  == SizePreference.INHERIT ? getWidthPreference()  : child.getWidthPreference();
            heightPrefs[i] = child.getHeightPreference() == SizePreference.INHERIT ? getHeightPreference() : child.getHeightPreference();

            widths[i] = switch (widthPrefs[i]) {
                case FILL        -> clampDimension(child, availableWidth, true);
                case FIT_CONTENT -> readContentDimension(child, ctx, true);
                case PERCENT     -> clampDimension(child, (int)(availableWidth * child.getPercentWidth()), true);
                default          -> clampDimension(child, ctx.getRequestedRegion() != null
                                        ? ctx.getRequestedRegion().getWidth()
                                        : ctx.getCurrentRegion().getWidth(), true);
            };

            heights[i] = switch (heightPrefs[i]) {
                case FILL        -> { fillHeightCount++; yield -1; } // resolved after fixed pass
                case FIT_CONTENT -> readContentDimension(child, ctx, false);
                case PERCENT     -> clampDimension(child, (int)(availableForChildren * child.getPercentHeight()), false);
                default          -> clampDimension(child, ctx.getRequestedRegion() != null
                                        ? ctx.getRequestedRegion().getHeight()
                                        : ctx.getCurrentRegion().getHeight(), false);
            };

            if (heights[i] >= 0) totalResolvedHeight += heights[i];
        }

        // ── resolve FILL heights ───────────────────────────────────────────────────
        // For clipping stacks, FILL children split the remaining space after fixed
        // children are placed. For non-clipping stacks (FIT_CONTENT, OVERFLOW),
        // there is no fixed budget so FILL children fall back to their minHeight.
        int remaining  = availableForChildren - totalResolvedHeight;
        int fillHeight = (clipsChildren && fillHeightCount > 0)
                    ? Math.max(0, remaining / fillHeightCount)
                    : 0;

        // ── resolve per-strategy final heights and totalHeight ────────────────────
        int totalHeight = totalGapHeight;

        switch (overflowStrategy) {

            case SHRINK_FILL -> {
                // FILL children get exactly the available share, no minHeight inflation.
                for (int i = 0; i < layoutCount; i++) {
                    if (heights[i] == -1) {
                        TerminalRegion child = checkTerminalRegion(contexts[layoutIndices[i]].getRenderable());
                        heights[i] = clampDimension(child, fillHeight, false);
                    }
                    totalHeight += heights[i];
                }
            }

            case SHRINK_ALL -> {
                // Resolve FILL children at their content/min size first, then scale
                // everyone proportionally if the total exceeds the available space.
                for (int i = 0; i < layoutCount; i++) {
                    if (heights[i] == -1) {
                        TerminalRegion child = checkTerminalRegion(contexts[layoutIndices[i]].getRenderable());
                        heights[i] = readContentDimension(child, contexts[layoutIndices[i]], false);
                    }
                    totalHeight += heights[i];
                }
                int totalContent = totalHeight - totalGapHeight;
                if (totalContent > availableForChildren && totalContent > 0) {
                    float scale = (float) availableForChildren / totalContent;
                    totalHeight = totalGapHeight;
                    for (int i = 0; i < layoutCount; i++) {
                        TerminalRegion child = checkTerminalRegion(contexts[layoutIndices[i]].getRenderable());
                        heights[i] = clampDimension(child, (int)(heights[i] * scale), false);
                        totalHeight += heights[i];
                    }
                }
            }

            case DISTRIBUTE_EQUAL -> {
                // Every child gets an equal share of the available height.
                int share = Math.max(0, availableForChildren / layoutCount);
                for (int i = 0; i < layoutCount; i++) {
                    TerminalRegion child = checkTerminalRegion(contexts[layoutIndices[i]].getRenderable());
                    heights[i] = clampDimension(child, share, false);
                    totalHeight += heights[i];
                }
            }

            default -> {
                // CLIP, OVERFLOW, SCROLL (not yet implemented), FIT_CONTENT:
                // FILL children get fillHeight (0 for non-clipping stacks → minHeight).
                for (int i = 0; i < layoutCount; i++) {
                    if (heights[i] == -1) {
                        TerminalRegion child = checkTerminalRegion(contexts[layoutIndices[i]].getRenderable());
                        heights[i] = clampDimension(child, fillHeight, false);
                    }
                    totalHeight += heights[i];
                }
            }
        }

        // ── starting Y (vertical alignment) ───────────────────────────────────────
        int startY = switch (vAlignment) {
            case TOP    -> ins.getTop();
            case CENTER -> ins.getTop() + Math.max(0, (availableHeight - totalHeight) / 2);
            case BOTTOM -> ins.getTop() + Math.max(0, availableHeight - totalHeight);
        };

        // ── pass 2: place children ─────────────────────────────────────────────────
        separatorYs    = new int[separatorRowCount];
        dividerChildYs = new int[0];
        int sepIdx   = 0;
        int currentY = startY;

        for (int i = 0; i < layoutCount; i++) {
            TerminalRenderable r = contexts[layoutIndices[i]].getRenderable();

            int x = widthPrefs[i] == SizePreference.FILL
                ? ins.getLeft()
                : ins.getLeft() + switch (hAlignment) {
                    case LEFT  -> 0;
                    case RIGHT -> Math.max(0, availableWidth - widths[i]);
                    default    -> Math.max(0, availableWidth - widths[i]) / 2;
                };

            // For clipping stacks, clamp to remaining space so a child never
            // overlaps the inset boundary. For non-clipping stacks, use the
            // natural size directly — the child is allowed to exceed the parent.
            int allocatedWidth  = clipsChildren
                ? Math.min(widths[i],  Math.max(0, effectiveW - ins.getRight() - x))
                : Math.max(0, widths[i]);
            int allocatedHeight = clipsChildren
                ? Math.min(heights[i], Math.max(0, effectiveH - ins.getBottom() - currentY))
                : Math.max(0, heights[i]);

            TerminalLayoutData.TerminalLayoutDataBuilder b = TerminalLayoutData.getBuilder()
                .setX(x)
                .setY(currentY)
                .setWidth(allocatedWidth)
                .setHeight(allocatedHeight);

            // Clipping stacks hide children that no longer fit in the remaining space.
            // Non-clipping stacks (FIT_CONTENT, OVERFLOW) never hide a placed child.
            if (clipsChildren && !isWithinParentBounds(x, currentY, allocatedWidth, allocatedHeight, parentRegion)) {
                RenderDiagnostics.logRenderBlocker(
                    "vstack-child-oob:" + getName() + ":" + r.getName(),
                    "TerminalVStack.layoutAllChildren",
                    allocatedWidth > 0 && allocatedHeight > 0
                        ? "child-hidden-out-of-parent-bounds"
                        : "child-hidden-no-space-remaining",
                    () -> "stack=" + RenderDiagnostics.summarizeRenderable(this)
                        + "\n\tchild=" + RenderDiagnostics.summarizeRenderable(r)
                        + "\n\tallocated=" + allocatedWidth + "x" + allocatedHeight
                        + "\n\tparentRegion=" + RenderDiagnostics.summarizeRegion(parentRegion)
                );
                b.hidden(true);
            }else{
                b.hidden(false);
            }

            dataInterfaces.get(r.getName()).setLayoutData(b.build());

            int childStartY = currentY;
            currentY += allocatedHeight;

            if (drawSeparators && i < layoutCount - 1) {
                separatorYs[sepIdx++] = currentY;
            }
            if (drawBorder && r instanceof TerminalDivider) {
                dividerChildYs = appendInt(dividerChildYs, childStartY);
            }

            currentY += gapSize;
        }

        if (sepIdx != separatorYs.length) {
            separatorYs = Arrays.copyOf(separatorYs, sepIdx);
        }
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

    /***
     *  Hidden children are not provided for measurement, must measure when they become visible.
     */
    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        System.out.println("[VSTACK:" + getName() + "] measureContent called, childContexts=" + childContexts.length);
        System.out.println("[VSTACK:" + getName() + "]   own region=" + getRegion() + " widthPref=" + getWidthPreference() + " heightPref=" + getHeightPreference());

        SizePreference ownWidthPref  = getWidthPreference();
        SizePreference ownHeightPref = getHeightPreference();

        int maxWidth    = 0;
        int totalHeight = 0;
        int visibleCount = 0;

        for (TerminalLayoutContext childContext : childContexts) {
            
            TerminalRegion child = checkTerminalRegion(childContext.getRenderable());

            if(ownHeightPref == SizePreference.FIT_CONTENT || overflowStrategy == LayoutOverflowStrategy.OVERFLOW){
                visibleCount++;
            }else if(!child.isHidden()){
                visibleCount++;
            }
            

            SizePreference childWidthPref = child.getWidthPreference() == SizePreference.INHERIT
                ? ownWidthPref : child.getWidthPreference();
            SizePreference childHeightPref = child.getHeightPreference() == SizePreference.INHERIT
                ? ownHeightPref : child.getHeightPreference();

            int minWidth  = child.getMinWidth();
            int minHeight = child.getMinHeight();

            // width contribution
            maxWidth = Math.max(maxWidth, switch (childWidthPref) {
                case FIT_CONTENT -> clampDimension(child, readContentDimension(child, childContext, true), true);
                case PERCENT, FILL -> minWidth;
                default -> clampDimension(child, childContext.getRequestedRegion() != null
                    ? childContext.getRequestedRegion().getWidth()
                    : childContext.getCurrentRegion().getWidth(), true);
            });


            // height contribution
            totalHeight += switch (childHeightPref) {
                case FIT_CONTENT -> clampDimension(child, readContentDimension(child, childContext, false), false);
                case FILL, PERCENT -> minHeight;
                default -> clampDimension(child, childContext.getRequestedRegion() != null
                    ? childContext.getRequestedRegion().getHeight()
                    : childContext.getCurrentRegion().getHeight(), false);
            };
        }

        if (visibleCount > 1) {
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
        System.out.println("[VSTACK:" + getName() + "] measureContent returning w=" + w + " h=" + h);
        return measured;
    }

 }
