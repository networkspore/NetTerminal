package io.netnotes.terminal.components.panels;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.netnotes.debug.RenderDiagnostics;
import io.netnotes.engine.ui.LayoutOverflowStrategy;
import io.netnotes.engine.ui.Orientation;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.renderer.LayoutGroup.LayoutDataInterface;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutGroupCallback;
import io.netnotes.terminal.layout.TerminalSizeable;

/**
 * TerminalHStack — horizontal stack layout container.
 *
 * Arranges children left-to-right with configurable spacing and sizing.
 *
 * SIZING (own axis = width, cross axis = height):
 * - Width:  default FIT_CONTENT — sums intrinsic widths of fixed children.
 * - Height: default FILL — expands to the full available height.
 * - Children implementing TerminalSizeable can override per-child.
 *
 * BORDER / SEPARATORS:
 * - setDrawBorder(true)     draws a box outline around all children.
 * - setDrawSeparators(true) draws a 1-column vertical separator between each
 *   child. Requires drawBorder=true to produce junction characters (┬ ┴) at
 *   the top and bottom edges. A TerminalDivider with Orientation.VERTICAL
 *   placed as a child always produces a junction regardless of drawSeparators.
 *
 * OVERFLOW STRATEGIES (applied to the main / horizontal axis):
 * - CLIP (default)     : children that overflow are hidden.
 * - OVERFLOW           : children render outside parent bounds without clipping.
 * - SHRINK_FILL        : FILL children receive exactly the available share.
 * - SHRINK_ALL         : all children scale proportionally if total exceeds width.
 * - DISTRIBUTE_EQUAL   : every visible child receives an equal share of width.
 */
public class TerminalHStack extends TerminalAbstractStack {

    // ── junction tracking ─────────────────────────────────────────────────────

    /**
     * X positions of gap-separator columns between children (drawSeparators=true).
     * Written by the layout callback, read by renderSelf. Both run on the UI
     * thread so no additional synchronisation is needed.
     */
    private int[] separatorXs    = new int[0];

    /**
     * X positions where a VERTICAL TerminalDivider child was placed.
     * Produces junction characters at the top/bottom border edges regardless
     * of drawSeparators.
     */
    private int[] dividerChildXs = new int[0];

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    public TerminalHStack(String name) {
        super(
            name,
            "hstack",
            SizePreference.FIT_CONTENT, // default child-width  → children use intrinsic width
            SizePreference.FILL,        // default child-height → children fill height
            VAlignment.CENTER,
            HAlignment.LEFT
        );
        // Own sizing defaults: the stack itself fits its content width, fills height.
        setWidthPreference(SizePreference.FIT_CONTENT);
        setHeightPreference(SizePreference.FILL);

    }

    // =========================================================================
    // ABSTRACT IMPLEMENTATION
    // =========================================================================

    @Override
    protected TerminalLayoutGroupCallback createLayoutCallback() {
        return this::layoutAllChildren;
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

        TerminalInsets ins  = getInsets();   // border-enforced when drawBorder=true
        int effectiveW      = parentRegion.getWidth();
        int effectiveH      = parentRegion.getHeight();
        int availableWidth  = effectiveW - ins.getHorizontal();
        int availableHeight = effectiveH - ins.getVertical();

        // ── collect visible indices ────────────────────────────────────────────
        int[] layoutIndices = new int[contexts.length];
        int layoutCount = 0;
        for (int i = 0; i < contexts.length; i++) {
            if (!renderableIsExcluded(contexts[i].getRenderable())) {
                layoutIndices[layoutCount++] = i;
            }
        }
        if (layoutCount == 0) {
            separatorXs    = new int[0];
            dividerChildXs = new int[0];
            return;
        }

        // ── gap accounting ─────────────────────────────────────────────────────
        // drawSeparators=true: each gap is exactly 1 column (the separator column).
        // drawSeparators=false: each gap is the spacing value.
        int gapSize           = drawSeparators ? 1 : spacing;
        int separatorColCount = drawSeparators ? Math.max(0, layoutCount - 1) : 0;
        int totalGapWidth     = Math.max(0, layoutCount - 1) * gapSize;
        int availableForChildren = availableWidth - totalGapWidth;

        if (availableWidth <= 0 || availableHeight <= 0 || availableForChildren < 0) {
            RenderDiagnostics.logRenderBlocker(
                "hstack-no-space:" + getName(),
                "TerminalHStack.layoutAllChildren",
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
        Arrays.fill(widthPrefs,  SizePreference.STATIC);
        Arrays.fill(heightPrefs, SizePreference.STATIC);

        int totalFitWidth  = 0;
        int fillWidthCount = 0;

        for (int i = 0; i < layoutCount; i++) {
            TerminalLayoutContext childContext = contexts[layoutIndices[i]];
            TerminalRenderable child = childContext.getRenderable();

            TerminalSizeable s = (child instanceof TerminalSizeable) ? (TerminalSizeable) child : null;
            if (s != null) {
                widthPrefs[i] = s.getWidthPreference() == SizePreference.INHERIT
                    ? getWidthPreference()
                    : s.getWidthPreference();
                heightPrefs[i] = s.getHeightPreference() == SizePreference.INHERIT
                    ? getHeightPreference()
                    : s.getHeightPreference();
            }else{
                widthPrefs[i] = SizePreference.STATIC;
                heightPrefs[i] = SizePreference.STATIC;
            }

            switch(heightPrefs[i]){
                case FILL:
                    heights[i] = availableHeight; // fills height regardless of own minHeight
                    break;
                case FIT_CONTENT:
                    heights[i] = readDimension(childContext, false);
                    break;
                case PERCENT:
                    heights[i] = Math.max(s.getMinHeight(),
                        (int)(availableHeight * s.getPercentHeight()));
                    break;
                case STATIC:
                    heights[i] = childContext.getRequestedRegion() != null ?
                        childContext.getRequestedRegion().getHeight() : childContext.getCurrentRegion().getHeight();
                    break;
                default:
                    heights[i] = childContext.getCurrentRegion().getHeight();
                    break;
            }

            switch(widthPrefs[i]){
                case FILL:
                    widths[i] = -1;   // resolved after all FIT sizes are known
                    fillWidthCount++;
                    break;
                case FIT_CONTENT:
                    widths[i] = readDimension(childContext, true);
                    break;
                case PERCENT:
                    widths[i] = Math.max(s.getMinWidth(),
                        (int)(availableForChildren * s.getPercentWidth()));
                    totalFitWidth += widths[i];
                    break;
                case STATIC:
                    widths[i] = childContext.getRequestedRegion() != null ?
                        childContext.getRequestedRegion().getWidth() : childContext.getCurrentRegion().getWidth();
                    break;
                default:
                    widths[i] = childContext.getCurrentRegion().getWidth();
                    break;

            }

        }

        // ── resolve FILL widths ────────────────────────────────────────────────
        int distributable = availableForChildren - totalFitWidth;
        int fillWidth     = fillWidthCount > 0 ? Math.max(0, distributable / fillWidthCount) : 0;

        int totalWidth = totalGapWidth;

        switch (overflowStrategy) {

            case SHRINK_FILL -> {
                // Give FILL children exactly the available share; do not inflate
                // to minWidth, so a space-starved stack shrinks gracefully.
                for (int i = 0; i < layoutCount; i++) {
                    if (widths[i] == -1) widths[i] = Math.max(0, fillWidth);
                    totalWidth += widths[i];
                }
            }

            case SHRINK_ALL -> {
                // Use each child's hint width, then scale everyone down
                // proportionally if the total exceeds the available space.
                for (int i = 0; i < layoutCount; i++) {
                    if (widths[i] == -1) widths[i] = getLayoutWidthHint(contexts[layoutIndices[i]]);
                    totalWidth += widths[i];
                }
                int totalRequested = totalWidth - totalGapWidth;
                if (totalRequested > availableForChildren && totalRequested > 0) {
                    float scale = (float) availableForChildren / totalRequested;
                    totalWidth = totalGapWidth;
                    for (int i = 0; i < layoutCount; i++) {
                        TerminalRenderable child = contexts[layoutIndices[i]].getRenderable();
                        int min = (child instanceof TerminalSizeable s) ? s.getMinWidth() : 0;
                        widths[i] = Math.max(min, (int)(widths[i] * scale));
                        totalWidth += widths[i];
                    }
                }
            }

            case DISTRIBUTE_EQUAL -> {
                int equalShare = layoutCount > 0
                    ? Math.max(0, availableForChildren / layoutCount) : 0;
                for (int i = 0; i < layoutCount; i++) {
                    TerminalRenderable child = contexts[layoutIndices[i]].getRenderable();
                    int min = (child instanceof TerminalSizeable s) ? s.getMinWidth() : 0;
                    widths[i] = Math.max(min, equalShare);
                    totalWidth += widths[i];
                }
            }

            // SCROLL falls through to CLIP until scroll support is implemented.
            default -> {
                for (int i = 0; i < layoutCount; i++) {
                    if (widths[i] == -1) {
                        TerminalRenderable child = contexts[layoutIndices[i]].getRenderable();
                        widths[i] = (child instanceof TerminalSizeable s)
                            ? Math.max(s.getMinWidth(), fillWidth)
                            : Math.max(0, fillWidth);
                    }
                    totalWidth += widths[i];
                }
            }
        }

        // ── starting X (horizontal alignment) ─────────────────────────────────
        int startX = switch (hAlignment) {
            case LEFT   -> ins.getLeft();
            case CENTER -> ins.getLeft() + Math.max(0, (availableWidth - totalWidth) / 2);
            case RIGHT  -> ins.getLeft() + Math.max(0, availableWidth - totalWidth);
        };

        // ── pass 2: place + record junction positions ──────────────────────────
        separatorXs    = new int[separatorColCount];
        dividerChildXs = new int[0];
        int sepIdx   = 0;
        int currentX = startX;

        for (int i = 0; i < layoutCount; i++) {
            final int index = i;
            TerminalRenderable r = contexts[layoutIndices[index]].getRenderable();

            int y;
            if (heightPrefs[index] == SizePreference.FILL) {
                y = ins.getTop();
            } else {
                int remaining = Math.max(0, availableHeight - heights[index]);
                y = switch (vAlignment) {
                    case TOP    -> ins.getTop();
                    case BOTTOM -> ins.getTop() + remaining;
                    default     -> ins.getTop() + (remaining / 2);
                };
            }

            int remainingChildWidth  = Math.max(0, effectiveW - ins.getRight() - currentX);
            int remainingChildHeight = Math.max(0, effectiveH - ins.getBottom() - y);
            int allocatedWidth  = Math.min(widths[index],  remainingChildWidth);
            int allocatedHeight = Math.min(heights[index], remainingChildHeight);
            boolean hasSpace  = allocatedWidth > 0 && allocatedHeight > 0;
            boolean inBounds  = hasSpace && isWithinParentBounds(
                currentX, y, allocatedWidth, allocatedHeight, parentRegion);
            boolean manageHidden = shouldManageHidden(r);

            TerminalLayoutData.TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder()
                .setX(currentX)
                .setY(y)
                .setWidth(Math.max(0, allocatedWidth))
                .setHeight(Math.max(0, allocatedHeight));

            if (!inBounds) {
                final int childX      = currentX;
                final int childY      = y;
                final int childWidth  = allocatedWidth;
                final int childHeight = allocatedHeight;
                if (overflowStrategy == LayoutOverflowStrategy.OVERFLOW) {
                    if (manageHidden) builder.hidden(false);
                } else {
                    RenderDiagnostics.logRenderBlocker(
                        "hstack-child-oob:" + getName() + ":" + r.getName(),
                        "TerminalHStack.layoutAllChildren",
                        hasSpace ? "child-hidden-out-of-parent-bounds" : "child-hidden-no-space-remaining",
                        () -> "stack=" + RenderDiagnostics.summarizeRenderable(this)
                            + "\n\tstackSizing=" + RenderDiagnostics.summarizeSizing(this)
                            + "\n\tchild=" + RenderDiagnostics.summarizeRenderable(r)
                            + "\n\tchildSizing=" + RenderDiagnostics.summarizeSizing(r)
                            + "\n\twidthPref=" + widthPrefs[index]
                            + "\n\theightPref=" + heightPrefs[index]
                            + "\n\tmeasuredSize=" + widths[index] + "x" + heights[index]
                            + "\n\tallocatedSize=" + childWidth + "x" + childHeight
                            + "\n\tcomputedBounds="
                            + RenderDiagnostics.summarizeRegion(
                                new TerminalRectangle(childX, childY, childWidth, childHeight))
                            + "\n\tparentRegion=" + RenderDiagnostics.summarizeRegion(parentRegion)
                    );
                    builder.hidden(true);
                }
            } else if (manageHidden) {
                builder.hidden(false);
            }

            dataInterfaces.get(r.getName()).setLayoutData(builder.build());

            int childStartX = currentX;
            currentX += Math.max(0, allocatedWidth);

            // Gap separator: record the column immediately after this child where
            // the 1-column separator will be drawn (drawSeparators=true only).
            if (drawSeparators && i < layoutCount - 1
                    && currentX < effectiveW - ins.getRight()) {
                separatorXs[sepIdx++] = currentX;
            }

            // Divider child junction: a VERTICAL TerminalDivider placed as a child
            // always produces top/bottom junction characters at its own column.
            if (drawBorder && r instanceof TerminalDivider divider
                    && divider.getOrientation() == Orientation.VERTICAL) {
                dividerChildXs = appendInt(dividerChildXs, childStartX);
            }

            currentX += gapSize;
        }

        if (sepIdx != separatorXs.length) {
            separatorXs = Arrays.copyOf(separatorXs, sepIdx);
        }
    }




    /**
     * Width hint for SHRINK_ALL: measured content → minWidth → requestedRegion width.
     * Returns 0 when no sizing information is available.
     */
    private int getLayoutWidthHint(TerminalLayoutContext ctx) {
        return Math.max(0, readDimension(ctx, true));
    }


    // =========================================================================
    // RENDERING
    // =========================================================================

    /**
     * Renders the border box with vertical junction characters at each separator
     * column and each VERTICAL TerminalDivider child column.
     *
     * Junction arrays are populated by the layout callback so they always reflect
     * positions committed in the most recent layout pass.
     */
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (!drawBorder) return;

        int totalJunctions = separatorXs.length + dividerChildXs.length;
        int[] junctions;
        if (totalJunctions == 0) {
            junctions = new int[0];
        } else {
            junctions = new int[totalJunctions];
            System.arraycopy(separatorXs,    0, junctions, 0,                  separatorXs.length);
            System.arraycopy(dividerChildXs, 0, junctions, separatorXs.length, dividerChildXs.length);
            Arrays.sort(junctions);
        }

        drawTableColBorder(batch, 0, 0, getWidth(), getHeight(), borderStyle, borderTextStyle, junctions);
    }

    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        SizePreference ownWidthPref  = getWidthPreference();
        SizePreference ownHeightPref = getHeightPreference();

        List<TerminalRenderable> children = getChildren();

        // Calculate content dimensions
        int totalWidth = 0;
        int maxHeight = 0;

        if (ownWidthPref == SizePreference.FIT_CONTENT) {
            totalWidth = calculateTotalWidth(children, childContexts, ownWidthPref);
        }

        if (ownHeightPref == SizePreference.FIT_CONTENT) {
            maxHeight = calculateMaxHeight(children, childContexts, ownHeightPref);
        }

        // Add spacing for multiple children
        int visibleCount = 0;
        for (TerminalRenderable child : children) {
            if (!renderableIsExcluded(child)) visibleCount++;
        }

        // Gap columns between all visible children — same rule as the layout pass.
        if (ownWidthPref == SizePreference.FIT_CONTENT && visibleCount > 1) {
            totalWidth += (visibleCount - 1) * (drawSeparators ? 1 : spacing);
        }

        // Calculate final dimensions
        int w = switch (ownWidthPref) {
            case STATIC      -> region.getWidth();
            case FIT_CONTENT -> Math.max(getMinWidth(), totalWidth + getInsets().getHorizontal());
            default          -> getMinWidth(); // FILL/PERCENT — floor only
        };
        int h = switch (ownHeightPref) {
            case STATIC      -> region.getHeight();
            case FIT_CONTENT -> Math.max(getMinHeight(), maxHeight + getInsets().getVertical());
            default          -> getMinHeight();
        };

        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, w, h);
        return measured;
    }

    }
