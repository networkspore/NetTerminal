package io.netnotes.terminal.components.panels;

import java.util.Map;

import io.netnotes.engine.ui.Position;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.layout2d.AlignContent;
import io.netnotes.engine.ui.layout2d.AlignSelf;
import io.netnotes.engine.ui.layout2d.FlexBasis;
import io.netnotes.engine.ui.layout2d.FlexDirection;
import io.netnotes.engine.ui.layout2d.FlexGrow;
import io.netnotes.engine.ui.layout2d.FlexShrink;
import io.netnotes.engine.ui.layout2d.Overflow;
import io.netnotes.engine.ui.renderer.LayoutGroup.LayoutDataInterface;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutGroupCallback;
import io.netnotes.terminal.layout.TerminalSizeable;

/**
 * TerminalPanel - Versatile single-axis layout container.
 *
 * Supports HORIZONTAL and VERTICAL layout axes, optional wrapping,
 * main-axis alignment, cross-axis alignment, border, title, fill style,
 * padding, and configurable overflow handling.
 *
 * OVERFLOW STRATEGIES (applied to the main / primary axis):
 * - CLIP (default)   : children that overflow are hidden
 * - OVERFLOW         : children render outside the parent bounds without clipping
 * - SHRINK_FILL      : FILL children receive exactly the available share (no min-size inflation)
 * - SHRINK_ALL       : all children scale proportionally if total exceeds available
 * - DISTRIBUTE_EQUAL : every visible child receives an equal share of available primary space
 */
public class TerminalPanel extends TerminalGroupRegion {

    // ── Deprecated internal enums (kept for backward compatibility) ──────────
    // These are thin wrappers around Layout2D types. Internal code uses Layout2D directly.

    /**
     * @deprecated Use {@link FlexDirection} instead.
     */
    @Deprecated(since = "0.12.0", forRemoval = true)
    public enum Axis {
        VERTICAL,
        HORIZONTAL
    }

    /**
     * @deprecated Use {@link AlignContent} instead.
     */
    @Deprecated(since = "0.12.0", forRemoval = true)
    public enum Alignment {
        START,    // default
        CENTER,
        END,
        STRETCH   // only affects positioning when child < available cross
    }

    // ── border / title ────────────────────────────────────────────────────────
    private boolean drawBorder = false;
    private TextStyle.LineStyle borderStyle = TextStyle.LineStyle.SINGLE;
    private String title = null;
    private Position titlePosition = Position.TOP_CENTER;
    private TextStyle borderTextStyle = TextStyle.NORMAL;
    private TextStyle focusedBorderTextStyle = TextStyle.FOCUSED;

    // ── layout ────────────────────────────────────────────────────────────────
    private final TerminalInsets padding = new TerminalInsets();
    private final TerminalInsets borderInsets = new TerminalInsets();
    private FlexDirection axis = FlexDirection.ROW;
    private boolean wrap = false;
    private int spacing = 0;
    private AlignSelf crossAlignment = AlignSelf.AUTO;
    private AlignContent alignment = AlignContent.FLEX_START;
    private Overflow overflowStrategy = Overflow.HIDDEN;

    // ── size constraints ──────────────────────────────────────────────────────
    private int maxWidth  = Integer.MAX_VALUE;
    private int maxHeight = Integer.MAX_VALUE;

    // ── rendering ─────────────────────────────────────────────────────────────
    private TextStyle fillStyle = null;

    // ── layout group ──────────────────────────────────────────────────────────

    public TerminalPanel(String name) {
        super(name, "term-panel");

        padding.setOnChanged(insets -> {
            updateBorderInsets();
            requestLayoutUpdate();
        });
        updateBorderInsets();
        syncOverflowClipPolicy();
    }

    protected TerminalLayoutGroupCallback createLayoutCallback() {
        return this::layoutAllChildren;
    }



    // ===== CHILD MANAGEMENT =====


    // ===== LAYOUT =====

    private void layoutAllChildren(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        if (contexts.length == 0) return;

        TerminalRectangle parent = contexts[0].getParentRegion();
        if (parent == null) return;

        TerminalInsets ins   = getInsets();
        int effectiveWidth   = parent.getWidth();
        int effectiveHeight  = parent.getHeight();
        int availableWidth   = effectiveWidth  - ins.getHorizontal();
        int availableHeight  = effectiveHeight - ins.getVertical();
        int availablePrimary = axis == FlexDirection.COLUMN ? availableHeight : availableWidth;
        int availableCross   = axis == FlexDirection.COLUMN ? availableWidth  : availableHeight;
        int startX           = ins.getLeft();
        int startY           = ins.getTop();

        int count = contexts.length;
        int[] widths = new int[count];
        int[] heights = new int[count];
        FlexGrow[] widthGrow = new FlexGrow[count];
        FlexBasis[] widthBasis = new FlexBasis[count];
        FlexGrow[] heightGrow = new FlexGrow[count];
        FlexBasis[] heightBasis = new FlexBasis[count];

        boolean[] inFlow = new boolean[count];

        int visibleCount = 0;

        // ── Pass 1: collect Layout2D metadata + inclusion rules ──────────────
        for (int i = 0; i < count; i++) {
            TerminalLayoutContext childContext = contexts[i];
            TerminalRegion child = checkTerminalRegion(childContext.getRenderable());


            if (!canUnhide(child)) {
                widths[i] = 0;
                heights[i] = 0;
                dataInterfaces.get(child.getName())
                    .setLayoutData(TerminalLayoutData.getBuilder().build());
                continue;
            }

            inFlow[i] = true;
            visibleCount++;

         
            SizePreference childWidthPref = child.getWidthPreference();
            SizePreference childHeightPref = child.getHeightPreference();
            widthGrow[i]  = growFor(childWidthPref, this);
            widthBasis[i] = basisFor(childWidthPref, child.getPercentWidth(), child.getMinWidth());
            heightGrow[i] = growFor(childHeightPref, this);
            heightBasis[i] = basisFor(childHeightPref, child.getPercentHeight(), child.getMinHeight());
        
    }

        if (visibleCount == 0) {
            return;
        }

        int gapTotal = visibleCount > 1 ? (visibleCount - 1) * spacing : 0;
        int availableForChildren = availablePrimary - gapTotal;

        // ── Pass 2: resolve raw sizes once ────────────────────────────────────
        int totalResolvedPrimary = 0;
        int fillPrimaryCount = 0;

        for (int i = 0; i < count; i++) {
            if (!inFlow[i]) continue;

            TerminalLayoutContext childContext = contexts[i];
            TerminalRegion child = checkTerminalRegion(childContext.getRenderable());
       

            widths[i] = resolveWidth(widthGrow[i], widthBasis[i], child, childContext, availableCross, axis, availableForChildren);
            heights[i] = resolveHeight(heightGrow[i], heightBasis[i], child, childContext, availableCross, axis, availableForChildren);

            int primary = axis == FlexDirection.COLUMN ? heights[i] : widths[i];
            if (primary < 0) {
                fillPrimaryCount++;
            } else {
                totalResolvedPrimary += primary;
            }
        }

        int rawFillPrimary = fillPrimaryCount > 0
            ? Math.max(0, (availableForChildren - totalResolvedPrimary) / fillPrimaryCount)
            : 0;

        // ── Pass 3: resolve main-axis FILL from the chosen overflow policy ───
        int totalPrimaryUsed = 0;

        switch (overflowStrategy) {

            case SHRINK_FILL -> {
                for (int i = 0; i < count; i++) {
                    if (!inFlow[i]) continue;
                    if (axis == FlexDirection.COLUMN) {
                        if (heights[i] < 0) {
                            TerminalRegion child = checkTerminalRegion(contexts[i].getRenderable());
                            heights[i] = clampDimension(child, rawFillPrimary, false);
                        }
                        totalPrimaryUsed += heights[i];
                    } else {
                        if (widths[i] < 0) {
                            TerminalRegion child = checkTerminalRegion(contexts[i].getRenderable());
                            widths[i] = clampDimension(child, rawFillPrimary, true);
                        }
                        totalPrimaryUsed += widths[i];
                    }
                }
            }

            case SHRINK_ALL -> {
                for (int i = 0; i < count; i++) {
                    if (!inFlow[i]) continue;

                    TerminalRegion child = checkTerminalRegion(contexts[i].getRenderable());

                    if (axis == FlexDirection.COLUMN) {
                        if (heights[i] < 0) {
                            heights[i] = readContentDimension(child, contexts[i], false);
                        }
                        totalPrimaryUsed += heights[i];
                    } else {
                        if (widths[i] < 0) {
                            widths[i] = readContentDimension(child, contexts[i], true);
                        }
                        totalPrimaryUsed += widths[i];
                    }
                }

                if (totalPrimaryUsed > availableForChildren && totalPrimaryUsed > 0) {
                    float scale = (float) Math.max(0, availableForChildren) / totalPrimaryUsed;
                    totalPrimaryUsed = 0;

                    for (int i = 0; i < count; i++) {
                        if (!inFlow[i]) continue;

                        TerminalRegion child = checkTerminalRegion(contexts[i].getRenderable());

                        if (axis == FlexDirection.COLUMN) {
                            heights[i] = clampDimension(child, (int) (heights[i] * scale), false);
                            totalPrimaryUsed += heights[i];
                        } else {
                            widths[i] = clampDimension(child, (int) (widths[i] * scale), true);
                            totalPrimaryUsed += widths[i];
                        }
                    }
                }
            }

            case DISTRIBUTE_EQUAL -> {
                int equalShare = visibleCount > 0
                    ? Math.max(0, availableForChildren / visibleCount)
                    : 0;

                for (int i = 0; i < count; i++) {
                    if (!inFlow[i]) continue;

                    TerminalRegion child = checkTerminalRegion(contexts[i].getRenderable());

                    if (axis == FlexDirection.COLUMN) {
                        heights[i] = clampDimension(child, equalShare, false);
                        totalPrimaryUsed += heights[i];
                    } else {
                        widths[i] = clampDimension(child, equalShare, true);
                        totalPrimaryUsed += widths[i];
                    }
                }
            }

            default -> {
                for (int i = 0; i < count; i++) {
                    if (!inFlow[i]) continue;

                    TerminalRegion child = checkTerminalRegion(contexts[i].getRenderable());

                    if (axis == FlexDirection.COLUMN) {
                        if (heights[i] < 0) {
                            heights[i] = clampDimension(child, rawFillPrimary, false);
                        }
                        totalPrimaryUsed += heights[i];
                    } else {
                        if (widths[i] < 0) {
                            widths[i] = clampDimension(child, rawFillPrimary, true);
                        }
                        totalPrimaryUsed += widths[i];
                    }
                }
            }
        }

        if (visibleCount > 1) {
            totalPrimaryUsed += gapTotal;
        }

        int primaryOffset = switch (alignment) {
            case FLEX_CENTER -> Math.max(0, (availablePrimary - totalPrimaryUsed) / 2);
            case FLEX_END -> Math.max(0, availablePrimary - totalPrimaryUsed);
            default -> 0;
        };

        // ── Pass 4: place once and apply overflow rules only here ─────────────
        int cursorX = startX + (axis == FlexDirection.ROW ? primaryOffset : 0);
        int cursorY = startY + (axis == FlexDirection.COLUMN ? primaryOffset : 0);
        int lineCrossExtent = 0;
        int wrapPrimaryLimit = axis == FlexDirection.COLUMN
            ? startY + Math.max(0, availableHeight)
            : startX + Math.max(0, availableWidth);

        for (int i = 0; i < count; i++) {
            if (!inFlow[i]) continue;

            TerminalRenderable child = contexts[i].getRenderable();
            int width = widths[i];
            int height = heights[i];

            if (wrap) {
                int nextPrimary = axis == FlexDirection.COLUMN ? cursorY + height : cursorX + width;
                if (lineCrossExtent > 0 && nextPrimary > wrapPrimaryLimit) {
                    if (axis == FlexDirection.COLUMN) {
                        cursorY = startY + primaryOffset;
                        cursorX += lineCrossExtent;
                    } else {
                        cursorX = startX + primaryOffset;
                        cursorY += lineCrossExtent;
                    }
                    lineCrossExtent = 0;
                }
            }

            int x = cursorX;
            int y = cursorY;
            int availableCrossAtCursor = axis == FlexDirection.COLUMN
                ? Math.max(0, effectiveWidth - ins.getRight() - cursorX)
                : Math.max(0, effectiveHeight - ins.getBottom() - cursorY);

            if (crossAlignment == AlignSelf.STRETCH) {
                if (axis == FlexDirection.COLUMN) width = availableCrossAtCursor;
                else height = availableCrossAtCursor;
            }

            int freeCross = availableCrossAtCursor - (axis == FlexDirection.COLUMN ? width : height);
            if (freeCross > 0) {
                switch (crossAlignment) {
                    case CENTER -> {
                        if (axis == FlexDirection.COLUMN) x += freeCross / 2;
                        else y += freeCross / 2;
                    }
                    case END -> {
                        if (axis == FlexDirection.COLUMN) x += freeCross;
                        else y += freeCross;
                    }
                    default -> {}
                }
            }

            int remainingWidth = Math.max(0, effectiveWidth - ins.getRight() - x);
            int remainingHeight = Math.max(0, effectiveHeight - ins.getBottom() - y);

            int allocatedWidth;
            int allocatedHeight;
            boolean inBounds;

            if (overflowStrategy == Overflow.VISIBLE) {
                allocatedWidth = axis == FlexDirection.ROW
                    ? Math.max(0, width)
                    : Math.min(Math.max(0, width), remainingWidth);
                allocatedHeight = axis == FlexDirection.COLUMN
                    ? Math.max(0, height)
                    : Math.min(Math.max(0, height), remainingHeight);

                boolean hasSpace = allocatedWidth > 0 && allocatedHeight > 0;
                inBounds = hasSpace && (axis == FlexDirection.COLUMN
                    ? x >= 0 && x + allocatedWidth <= parent.getWidth()
                    : y >= 0 && y + allocatedHeight <= parent.getHeight());
            } else {
                allocatedWidth = Math.min(Math.max(0, width), remainingWidth);
                allocatedHeight = Math.min(Math.max(0, height), remainingHeight);

                boolean hasSpace = allocatedWidth > 0 && allocatedHeight > 0;
                inBounds = hasSpace && isWithinParentBounds(
                    x, y, allocatedWidth, allocatedHeight, parent);
            }

            TerminalLayoutData.TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder()
                .setX(x)
                .setY(y)
                .setWidth(Math.max(0, allocatedWidth))
                .setHeight(Math.max(0, allocatedHeight));

            if (!inBounds) {
                builder.hidden(overflowStrategy != Overflow.VISIBLE);
            } else {
                builder.hidden(false);
            }

            dataInterfaces.get(child.getName()).setLayoutData(builder.build());

            if (axis == FlexDirection.COLUMN) {
                cursorY += Math.max(0, allocatedHeight) + spacing;
                lineCrossExtent = Math.max(lineCrossExtent, allocatedWidth);
            } else {
                cursorX += Math.max(0, allocatedWidth) + spacing;
                lineCrossExtent = Math.max(lineCrossExtent, allocatedHeight);
            }
        }
    }

    /**
     * Resolve a child's measured size from SizePreference (used in measureContent pre-pass).
     * Used by measureContent to compute content dimensions before layout.
     */
    private int resolveMeasureSize(SizePreference pref, double percent, TerminalRegion child,
            TerminalLayoutContext childContext, boolean isWidth) {
        return switch (pref) {
            case FIT_CONTENT -> clampDimension(child, readContentDimension(child, childContext, isWidth), isWidth);
            case PERCENT, FILL -> isWidth ? child.getMinWidth() : child.getMinHeight();
            case STATIC -> clampDimension(child, childContext.getRequestedRegion() != null
                ? childContext.getRequestedRegion().getDimension(isWidth)
                : childContext.getCurrentRegion().getDimension(isWidth), isWidth);
            default -> isWidth ? child.getMinWidth() : child.getMinHeight();
        };
    }

    // ===== HELPERS =====

    /**
     * Map deprecated SizePreference.INHERIT to parent's Layout2D values.
     * Direct SizePreference values (FILL, PERCENT, etc.) are mapped via
     * the deprecated TerminalRegion bridge methods.
     */
    private static FlexGrow growFor(SizePreference pref, TerminalPanel parent) {
        return switch (pref) {
            case INHERIT -> parent.getWidthGrow();
            case FILL -> FlexGrow.FULL;
            default -> FlexGrow.NONE;
        };
    }




    private boolean isWithinParentBounds(int x, int y, int width, int height,
            TerminalRectangle parentRegion) {
        return x >= 0 &&
            y >= 0 &&
            x + width  <= parentRegion.getWidth() &&
            y + height <= parentRegion.getHeight();
    }

    /**
     * Resolve a child's width from Layout2D enums. Maps to the old SizePreference
     * behavior: FILL→-1 (grow), PERCENT→% of available, FIT_CONTENT→content, STATIC→requested.
     */
    private int resolveWidth(FlexGrow grow, FlexBasis basis, TerminalRegion child,
            TerminalLayoutContext childContext, int availableCross, Axis axis, int availableForChildren) {
        if (grow.isNonZero()) {
            return axis == FlexDirection.ROW ? -1
                : clampDimension(child, availableCross, true);
        }
        if (basis.isPercent()) {
            return clampDimension(child,
                (int) (Math.max(0, axis == FlexDirection.ROW ? availableForChildren : availableCross)
                    * basis.getPercent()), true);
        }
        if (basis.isContent()) {
            return readContentDimension(child, childContext, true);
        }
        // FlexBasis.pixels() or auto with no grow → use requested size
        return clampDimension(child, childContext.getRequestedRegion() != null
            ? childContext.getRequestedRegion().getWidth()
            : childContext.getCurrentRegion().getWidth(), true);
    }

    /**
     * Resolve a child's height from Layout2D enums. Maps to the old SizePreference
     * behavior: FILL→-1 (grow), PERCENT→% of available, FIT_CONTENT→content, STATIC→requested.
     */
    private int resolveHeight(FlexGrow grow, FlexBasis basis, TerminalRegion child,
            TerminalLayoutContext childContext, int availableCross, Axis axis, int availableForChildren) {
        if (grow.isNonZero()) {
            return axis == FlexDirection.COLUMN ? -1
                : clampDimension(child, availableCross, false);
        }
        if (basis.isPercent()) {
            return clampDimension(child,
                (int) (Math.max(0, axis == FlexDirection.COLUMN ? availableForChildren : availableCross)
                    * basis.getPercent()), false);
        }
        if (basis.isContent()) {
            return readContentDimension(child, childContext, false);
        }
        return clampDimension(child, childContext.getRequestedRegion() != null
            ? childContext.getRequestedRegion().getHeight()
            : childContext.getCurrentRegion().getHeight(), false);
    }



    // ===== MEASURE CONTENT (pre-pass for FIT_CONTENT sizing) =====

    /**
     * Pre-pass that runs before the layout callback. Computes the panel's own
     * content dimensions from child contexts so that a parent whose panel has
     * FIT_CONTENT sizing can read back the correct size via
     * {@link TerminalLayoutContext#getMeasuredContentBounds()}.
     *
     * <p>Width/height contributions depend on the panel's axis:
     * <ul>
     *   <li>HORIZONTAL panel: width = sum of FIT/STATIC child widths + gaps;
     *       height = max of FIT/STATIC child heights.</li>
     *   <li>VERTICAL panel:   width = max of FIT/STATIC child widths;
     *       height = sum of FIT/STATIC child heights + gaps.</li>
     * </ul>
     * Parent-dependent children (FILL/PERCENT) contribute their minimum size
     * floor when no in-flight measurement is available.
     */
   @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        // Calculate content dimensions based on axis
        int totalPrimary = 0;  // sum for primary axis
        int maxCross = 0;       // max for cross axis

        // Count visible children
        int visibleCount = 0;
        for (TerminalRenderable child : getChildren()) {
            //TODO: count the children that are not hidden on the measred axis
            visibleCount++;
        }

        // Measure each child based on Layout2D enums (mapped from SizePreference)
        for (int i = 0; i < childContexts.length; i++) {
            TerminalLayoutContext childContext = childContexts[i];
            TerminalRenderable renderable = childContext.getRenderable();


            TerminalRegion child = checkTerminalRegion(renderable);

            // Get child's Layout2D preferences (respecting INHERIT)
            SizePreference childWidthPref = child.getWidthPreference() == SizePreference.INHERIT
                ? getWidthPreference()
                : child.getWidthPreference();
            SizePreference childHeightPref = child.getHeightPreference() == SizePreference.INHERIT
                ? getHeightPreference()
                : child.getHeightPreference();

            int childWidth = resolveMeasureSize(childWidthPref, child.getPercentWidth(), child, childContext, true);
            int childHeight = resolveMeasureSize(childHeightPref, child.getPercentHeight(), child, childContext, false);

            // Accumulate based on axis
            if (axis == FlexDirection.ROW) {
                totalPrimary += childWidth;
                maxCross = Math.max(maxCross, childHeight);
            } else {
                totalPrimary += childHeight;
                maxCross = Math.max(maxCross, childWidth);
            }
        }

        // Add spacing for multiple children
        if (visibleCount > 1) {
            totalPrimary += (visibleCount - 1) * spacing;
        }

        // Calculate final dimensions
        int contentW = axis == FlexDirection.ROW ? totalPrimary : maxCross;
        int contentH = axis == FlexDirection.COLUMN ? totalPrimary : maxCross;

        SizePreference ownWidthPref = getWidthPreference();
        SizePreference ownHeightPref = getHeightPreference();

        TerminalInsets ins = getInsets();

        int w = switch (ownWidthPref) {
            case STATIC      -> region.getWidth();
            case FIT_CONTENT -> Math.min(maxWidth, Math.max(getMinWidth(), contentW + ins.getHorizontal()));
            default          -> Math.min(maxWidth, getMinWidth());
        };
        int h = switch (ownHeightPref) {
            case STATIC      -> region.getHeight();
            case FIT_CONTENT -> Math.min(maxHeight, Math.max(getMinHeight(), contentH + ins.getVertical()));
            default          -> Math.min(maxHeight, getMinHeight());
        };

        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, w, h);
        return measured;
    }


    // ===== RENDERING =====

    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        int width  = getWidth();
        int height = getHeight();

        if (fillStyle != null) {
            fillRegion(batch, 0, 0, width, height, ' ', fillStyle);
        }

        if (drawBorder || title != null) {
            TextStyle style = hasFocus() ? focusedBorderTextStyle : borderTextStyle;
            drawBox(batch, 0, 0, width, height, title, titlePosition, borderStyle, style);
        }
    }

    // ===== CONFIGURATION GETTERS / SETTERS =====

    public Axis getAxis() {
        return axis == FlexDirection.ROW ? Axis.HORIZONTAL : Axis.VERTICAL;
    }

    public void setAxis(Axis axis) {
        this.axis = axis == Axis.HORIZONTAL ? FlexDirection.ROW : FlexDirection.COLUMN;
        requestLayoutUpdate();
    }

    public boolean isWrap() { return wrap; }

    public void setWrap(boolean wrap) {
        if (this.wrap != wrap) {
            this.wrap = wrap;
            requestLayoutUpdate();
        }
    }

    public int getSpacing() { return spacing; }

    public void setSpacing(int spacing) {
        if (this.spacing != spacing) {
            this.spacing = spacing;
            requestLayoutUpdate();
        }
    }

    public Alignment getAlignment() {
        return switch (alignment) {
            case FLEX_START -> Alignment.START;
            case FLEX_CENTER -> Alignment.CENTER;
            case FLEX_END -> Alignment.END;
            case STRETCH -> Alignment.STRETCH;
            default -> Alignment.START;
        };
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = switch (alignment) {
            case START -> AlignContent.FLEX_START;
            case CENTER -> AlignContent.FLEX_CENTER;
            case END -> AlignContent.FLEX_END;
            case STRETCH -> AlignContent.STRETCH;
        };
        requestLayoutUpdate();
    }

    public Alignment getCrossAlignment() {
        return switch (crossAlignment) {
            case START, AUTO -> Alignment.START;
            case CENTER -> Alignment.CENTER;
            case END -> Alignment.END;
            case STRETCH -> Alignment.STRETCH;
            default -> Alignment.START;
        };
    }

    public void setCrossAlignment(Alignment crossAlignment) {
        this.crossAlignment = switch (crossAlignment) {
            case START -> AlignSelf.FLEX_START;
            case CENTER -> AlignSelf.CENTER;
            case END -> AlignSelf.FLEX_END;
            case STRETCH -> AlignSelf.STRETCH;
        };
        requestLayoutUpdate();
        }
    }

    public LayoutOverflowStrategy getOverflowStrategy() { return overflowStrategy.toLayoutOverflowStrategy(); }

    public void setOverflowStrategy(LayoutOverflowStrategy strategy) {
        if (strategy != null) {
            this.overflowStrategy = strategy.toLayout2DOverflow();
            syncOverflowClipPolicy();
            requestLayoutUpdate();
        }
    }

    private void syncOverflowClipPolicy() {
        setOverflowClipPolicy(
            overflowStrategy == Overflow.VISIBLE
                ? TerminalRenderable.OverflowClipPolicy.INHERIT_PARENT_CLIP
                : TerminalRenderable.OverflowClipPolicy.CLIP_TO_SELF_BOUNDS
        );
    }

    public int getMaxWidth() { return maxWidth; }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        requestLayoutUpdate();
    }

    public int getMaxHeight() { return maxHeight; }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
        requestLayoutUpdate();
    }

    public TextStyle getFillStyle() { return fillStyle; }

    public void setFillStyle(TextStyle fillStyle) {
        if (this.fillStyle != fillStyle) {
            this.fillStyle = fillStyle;
            invalidate();
        }
    }

    // ── padding / insets ──────────────────────────────────────────────────────

    public void setPadding(int all) {
        if (!padding.equals(all)) {
            padding.set(all, all, all, all);
        }
    }

    public void setPadding(int vertical, int horizontal) {
        if (padding.getTop() != vertical ||
            padding.getRight() != horizontal ||
            padding.getBottom() != vertical ||
            padding.getLeft() != horizontal) {
            padding.set(vertical, horizontal, vertical, horizontal);
        }
    }

    /**
     * Set insets from a TerminalInsets instance (all four sides independently).
     */
    public void setInsets(TerminalInsets newInsets) {
        if (newInsets == null) {
            if (!padding.isZero()) {
                padding.clear();
            }
            return;
        }
        if (!padding.equals(newInsets)) {
            padding.copyFrom(newInsets);
        }
    }

    @Override
    public TerminalInsets getInsets() {
        return drawBorder ? borderInsets : padding;
    }

    // ── border / title ────────────────────────────────────────────────────────

    public void setEnableBorder(boolean enabled) {
        if (this.drawBorder != enabled) {
            this.drawBorder = enabled;
            updateBorderInsets();
            requestLayoutUpdate();
            invalidate();
        }
    }

    private void updateBorderInsets() {
        borderInsets.set(
            Math.max(1, padding.getTop()),
            Math.max(1, padding.getRight()),
            Math.max(1, padding.getBottom()),
            Math.max(1, padding.getLeft())
        );
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

    public String getTitle() { return title; }

    public Position getTitlePosition() { return titlePosition; }

    public void setTitlePosition(Position titlePosition) {
        if (this.titlePosition != titlePosition) {
            this.titlePosition = titlePosition;
            invalidate();
        }
    }

    public TextStyle getBorderTextStyle() { return borderTextStyle; }

    public void setBorderTextStyle(TextStyle textStyle) {
        this.borderTextStyle = textStyle;
        invalidate();
    }

    public TextStyle getFocusedBorderTextStyle() { return focusedBorderTextStyle; }

    public void setFocusedBorderTextStyle(TextStyle focusedTextStyle) {
        this.focusedBorderTextStyle = focusedTextStyle;
        invalidate();
    }

}
