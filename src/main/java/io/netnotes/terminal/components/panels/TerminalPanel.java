package io.netnotes.terminal.components.panels;

import java.util.Map;

import io.netnotes.engine.ui.LayoutOverflowStrategy;
import io.netnotes.engine.ui.Position;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.renderer.LayoutGroup.LayoutDataInterface;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;
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

    public enum Axis {
        VERTICAL,
        HORIZONTAL
    }

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
    private Axis axis = Axis.HORIZONTAL;
    private boolean wrap = false;
    private int spacing = 0;
    private Alignment crossAlignment = Alignment.START;
    private Alignment alignment = Alignment.START;
    private LayoutOverflowStrategy overflowStrategy = LayoutOverflowStrategy.CLIP;

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
    }

    protected TerminalLayoutGroupCallback createLayoutCallback() {
        return this::layoutChildren;
    }



    // ===== CHILD MANAGEMENT =====


    // ===== LAYOUT =====

    private void layoutChildren(
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
        int availablePrimary = axis == Axis.VERTICAL ? availableHeight : availableWidth;
        int availableCross   = axis == Axis.VERTICAL ? availableWidth  : availableHeight;
        int startX           = ins.getLeft();
        int startY           = ins.getTop();

        int count = contexts.length;
        int[] widths = new int[count];
        int[] heights = new int[count];
        SizePreference[] widthPrefs = new SizePreference[count];
        SizePreference[] heightPrefs = new SizePreference[count];
        boolean[] managedHidden = new boolean[count];
        boolean[] inFlow = new boolean[count];

        int visibleCount = 0;

        // ── Pass 1: collect metadata + inclusion rules ───────────────────────
        for (int i = 0; i < count; i++) {
            TerminalLayoutContext childContext = contexts[i];
            TerminalRenderable child = childContext.getRenderable();
            managedHidden[i] = shouldManageHidden(child);

            if (renderableIsExcluded(child)) {
                widths[i] = 0;
                heights[i] = 0;
                dataInterfaces.get(child.getName())
                    .setLayoutData(TerminalLayoutData.getBuilder().build());
                continue;
            }

            inFlow[i] = true;
            visibleCount++;

            if (child instanceof TerminalSizeable sizeable) {
                widthPrefs[i] = sizeable.getWidthPreference() == SizePreference.INHERIT
                    ? getWidthPreference()
                    : sizeable.getWidthPreference();
                heightPrefs[i] = sizeable.getHeightPreference() == SizePreference.INHERIT
                    ? getHeightPreference()
                    : sizeable.getHeightPreference();
            } else {
                widthPrefs[i] = SizePreference.STATIC;
                heightPrefs[i] = SizePreference.STATIC;
            }
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
            TerminalRenderable child = childContext.getRenderable();
            TerminalSizeable sizeable = child instanceof TerminalSizeable ts ? ts : null;

            int minWidth = sizeable != null ? sizeable.getMinWidth() : 0;
            int minHeight = sizeable != null ? sizeable.getMinHeight() : 0;

            switch (widthPrefs[i]) {
                case FILL -> widths[i] = axis == Axis.HORIZONTAL
                    ? -1
                    : Math.max(0, availableCross);
                case FIT_CONTENT -> {
                    TerminalRectangle measured = childContext.getMeasuredContentBounds();
                    if (measured == null) {
                        throw new IllegalStateException(
                            "FIT_CONTENT width requires measured content bounds for child: "
                                + child.getName());
                    }
                    widths[i] = measured.getWidth();
                }
                case PERCENT -> widths[i] = Math.max(
                    minWidth,
                    (int) (Math.max(0, axis == Axis.HORIZONTAL ? availableForChildren : availableCross)
                        * sizeable.getPercentWidth())
                );
                case STATIC -> widths[i] = childContext.getRequestedRegion() != null
                    ? childContext.getRequestedRegion().getWidth()
                    : childContext.getCurrentRegion().getWidth();
                default -> widths[i] = childContext.getCurrentRegion().getWidth();
            }

            switch (heightPrefs[i]) {
                case FILL -> heights[i] = axis == Axis.VERTICAL
                    ? -1
                    : Math.max(0, availableCross);
                case FIT_CONTENT -> {
                    TerminalRectangle measured = childContext.getMeasuredContentBounds();
                    if (measured == null) {
                        throw new IllegalStateException(
                            "FIT_CONTENT height requires measured content bounds for child: "
                                + child.getName());
                    }
                    heights[i] = measured.getHeight();
                }
                case PERCENT -> heights[i] = Math.max(
                    minHeight,
                    (int) (Math.max(0, axis == Axis.VERTICAL ? availableForChildren : availableCross)
                        * sizeable.getPercentHeight())
                );
                case STATIC -> heights[i] = childContext.getRequestedRegion() != null
                    ? childContext.getRequestedRegion().getHeight()
                    : childContext.getCurrentRegion().getHeight();
                default -> heights[i] = childContext.getCurrentRegion().getHeight();
            }

            int primary = axis == Axis.VERTICAL ? heights[i] : widths[i];
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
                    if (axis == Axis.VERTICAL) {
                        if (heights[i] < 0) heights[i] = Math.max(0, rawFillPrimary);
                        totalPrimaryUsed += heights[i];
                    } else {
                        if (widths[i] < 0) widths[i] = Math.max(0, rawFillPrimary);
                        totalPrimaryUsed += widths[i];
                    }
                }
            }

            case SHRINK_ALL -> {
                for (int i = 0; i < count; i++) {
                    if (!inFlow[i]) continue;

                    TerminalRenderable child = contexts[i].getRenderable();
                    int minPrimary = axis == Axis.VERTICAL
                        ? (child instanceof TerminalSizeable sizeable ? sizeable.getMinHeight() : 0)
                        : (child instanceof TerminalSizeable sizeable ? sizeable.getMinWidth() : 0);

                    if (axis == Axis.VERTICAL) {
                        if (heights[i] < 0) {
                            heights[i] = Math.max(minPrimary, readDimension(contexts[i], false));
                        }
                        totalPrimaryUsed += heights[i];
                    } else {
                        if (widths[i] < 0) {
                            widths[i] = Math.max(minPrimary, readDimension(contexts[i], true));
                        }
                        totalPrimaryUsed += widths[i];
                    }
                }

                if (totalPrimaryUsed > availableForChildren && totalPrimaryUsed > 0) {
                    float scale = (float) Math.max(0, availableForChildren) / totalPrimaryUsed;
                    totalPrimaryUsed = 0;

                    for (int i = 0; i < count; i++) {
                        if (!inFlow[i]) continue;

                        TerminalRenderable child = contexts[i].getRenderable();
                        int minPrimary = axis == Axis.VERTICAL
                            ? (child instanceof TerminalSizeable sizeable ? sizeable.getMinHeight() : 0)
                            : (child instanceof TerminalSizeable sizeable ? sizeable.getMinWidth() : 0);

                        if (axis == Axis.VERTICAL) {
                            heights[i] = Math.max(minPrimary, (int) (heights[i] * scale));
                            totalPrimaryUsed += heights[i];
                        } else {
                            widths[i] = Math.max(minPrimary, (int) (widths[i] * scale));
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

                    TerminalRenderable child = contexts[i].getRenderable();
                    int minPrimary = axis == Axis.VERTICAL
                        ? (child instanceof TerminalSizeable sizeable ? sizeable.getMinHeight() : 0)
                        : (child instanceof TerminalSizeable sizeable ? sizeable.getMinWidth() : 0);

                    if (axis == Axis.VERTICAL) {
                        heights[i] = Math.max(minPrimary, equalShare);
                        totalPrimaryUsed += heights[i];
                    } else {
                        widths[i] = Math.max(minPrimary, equalShare);
                        totalPrimaryUsed += widths[i];
                    }
                }
            }

            default -> {
                for (int i = 0; i < count; i++) {
                    if (!inFlow[i]) continue;

                    TerminalRenderable child = contexts[i].getRenderable();
                    int minPrimary = axis == Axis.VERTICAL
                        ? (child instanceof TerminalSizeable sizeable ? sizeable.getMinHeight() : 0)
                        : (child instanceof TerminalSizeable sizeable ? sizeable.getMinWidth() : 0);

                    if (axis == Axis.VERTICAL) {
                        if (heights[i] < 0) heights[i] = Math.max(minPrimary, rawFillPrimary);
                        totalPrimaryUsed += heights[i];
                    } else {
                        if (widths[i] < 0) widths[i] = Math.max(minPrimary, rawFillPrimary);
                        totalPrimaryUsed += widths[i];
                    }
                }
            }
        }

        if (visibleCount > 1) {
            totalPrimaryUsed += gapTotal;
        }

        int primaryOffset = switch (alignment) {
            case CENTER -> Math.max(0, (availablePrimary - totalPrimaryUsed) / 2);
            case END -> Math.max(0, availablePrimary - totalPrimaryUsed);
            default -> 0;
        };

        // ── Pass 4: place once and apply overflow rules only here ─────────────
        int cursorX = startX + (axis == Axis.HORIZONTAL ? primaryOffset : 0);
        int cursorY = startY + (axis == Axis.VERTICAL ? primaryOffset : 0);
        int lineCrossExtent = 0;
        int wrapPrimaryLimit = axis == Axis.VERTICAL
            ? startY + Math.max(0, availableHeight)
            : startX + Math.max(0, availableWidth);

        for (int i = 0; i < count; i++) {
            if (!inFlow[i]) continue;

            TerminalRenderable child = contexts[i].getRenderable();
            int width = widths[i];
            int height = heights[i];

            if (wrap) {
                int nextPrimary = axis == Axis.VERTICAL ? cursorY + height : cursorX + width;
                if (lineCrossExtent > 0 && nextPrimary > wrapPrimaryLimit) {
                    if (axis == Axis.VERTICAL) {
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
            int availableCrossAtCursor = axis == Axis.VERTICAL
                ? Math.max(0, effectiveWidth - ins.getRight() - cursorX)
                : Math.max(0, effectiveHeight - ins.getBottom() - cursorY);

            if (crossAlignment == Alignment.STRETCH) {
                if (axis == Axis.VERTICAL) width = availableCrossAtCursor;
                else height = availableCrossAtCursor;
            }

            int freeCross = availableCrossAtCursor - (axis == Axis.VERTICAL ? width : height);
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
                    default -> {}
                }
            }

            int remainingWidth = Math.max(0, effectiveWidth - ins.getRight() - x);
            int remainingHeight = Math.max(0, effectiveHeight - ins.getBottom() - y);

            int allocatedWidth;
            int allocatedHeight;
            boolean inBounds;

            if (overflowStrategy == LayoutOverflowStrategy.OVERFLOW) {
                allocatedWidth = axis == Axis.HORIZONTAL
                    ? Math.max(0, width)
                    : Math.min(Math.max(0, width), remainingWidth);
                allocatedHeight = axis == Axis.VERTICAL
                    ? Math.max(0, height)
                    : Math.min(Math.max(0, height), remainingHeight);

                boolean hasSpace = allocatedWidth > 0 && allocatedHeight > 0;
                inBounds = hasSpace && (axis == Axis.VERTICAL
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
                if (managedHidden[i]) {
                    builder.hidden(overflowStrategy != LayoutOverflowStrategy.OVERFLOW);
                }
            } else if (managedHidden[i]) {
                builder.hidden(false);
            }

            dataInterfaces.get(child.getName()).setLayoutData(builder.build());

            if (axis == Axis.VERTICAL) {
                cursorY += Math.max(0, allocatedHeight) + spacing;
                lineCrossExtent = Math.max(lineCrossExtent, allocatedWidth);
            } else {
                cursorX += Math.max(0, allocatedWidth) + spacing;
                lineCrossExtent = Math.max(lineCrossExtent, allocatedHeight);
            }
        }
    }

    // ===== HELPERS =====

    private boolean isWithinParentBounds(int x, int y, int width, int height,
            TerminalRectangle parentRegion) {
        return x >= 0 &&
            y >= 0 &&
            x + width  <= parentRegion.getWidth() &&
            y + height <= parentRegion.getHeight();
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
     * FILL and PERCENT children are excluded from FIT_CONTENT measurement
     * because their size depends on available space, not intrinsic content.
     */
    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        SizePreference ownWidthPref  = getWidthPreference();
        SizePreference ownHeightPref = getHeightPreference();

        int sumW = 0;
        int maxW = 0;
        int sumH = 0;
        int maxH = 0;
        int visibleCount = 0;

        if (childContexts != null) {
            for (TerminalLayoutContext ctx : childContexts) {
                if (ctx == null) continue;
                TerminalRenderable child = ctx.getRenderable();

                if (renderableIsExcluded(child)) continue;
                visibleCount++;

                TerminalSizeable s = (child instanceof TerminalSizeable ts) ? ts : null;
                SizePreference childWP = s != null
                    ? (s.getWidthPreference()  == SizePreference.INHERIT ? ownWidthPref  : s.getWidthPreference())
                    : SizePreference.STATIC;
                SizePreference childHP = s != null
                    ? (s.getHeightPreference() == SizePreference.INHERIT ? ownHeightPref : s.getHeightPreference())
                    : SizePreference.STATIC;

                // Width contribution — only count intrinsically-sized children.
                if (ownWidthPref == SizePreference.FIT_CONTENT
                        && (childWP == SizePreference.FIT_CONTENT || childWP == SizePreference.STATIC)) {
                    int cw = readDimension(ctx, true);
                    if (axis == Axis.HORIZONTAL) { if (cw > 0) sumW += cw; }
                    else                         { maxW = Math.max(maxW, cw); }
                }

                // Height contribution — only count intrinsically-sized children.
                if (ownHeightPref == SizePreference.FIT_CONTENT
                        && (childHP == SizePreference.FIT_CONTENT || childHP == SizePreference.STATIC)) {
                    int ch = readDimension(ctx, false);
                    if (axis == Axis.VERTICAL)   { if (ch > 0) sumH += ch; }
                    else                         { maxH = Math.max(maxH, ch); }
                }
            }
        }

        if (visibleCount > 1) {
            if (axis == Axis.HORIZONTAL && ownWidthPref == SizePreference.FIT_CONTENT) {
                sumW += (visibleCount - 1) * spacing;
            }
            if (axis == Axis.VERTICAL && ownHeightPref == SizePreference.FIT_CONTENT) {
                sumH += (visibleCount - 1) * spacing;
            }
        }

        TerminalInsets ins = getInsets();
        int contentW = (axis == Axis.HORIZONTAL) ? sumW : maxW;
        int contentH = (axis == Axis.VERTICAL)   ? sumH : maxH;

        int w = switch (ownWidthPref) {
            case STATIC      -> region.getWidth();
            case FIT_CONTENT -> Math.min(maxWidth,  Math.max(getMinWidth(),  contentW + ins.getHorizontal()));
            default          -> Math.min(maxWidth,  getMinWidth());
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

    // ===== HELPERS =====

    /**
     * Reads a single dimension (width or height) for a child purely from its
     * context — never calls {@code getPreferredWidth/Height()} on the child.
     * Priority: measuredContentBounds → requestedRegion → currentRegion.
     */
    private int readDimension(TerminalLayoutContext ctx, boolean isWidth) {
        TerminalRectangle bounds = ctx.getMeasuredContentBounds();
        if (bounds != null) return isWidth ? bounds.getWidth() : bounds.getHeight();

        TerminalRenderable child = ctx.getRenderable();
        TerminalRectangle requested = child.getRequestedRegion();
        if (requested != null) return isWidth ? requested.getWidth() : requested.getHeight();

        return isWidth ? child.getRegion().getWidth() : child.getRegion().getHeight();
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

    public Axis getAxis() { return axis; }

    public void setAxis(Axis axis) {
        if (this.axis != axis) {
            this.axis = axis;
            requestLayoutUpdate();
        }
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

    public Alignment getAlignment() { return alignment; }

    public void setAlignment(Alignment alignment) {
        if (this.alignment != alignment) {
            this.alignment = alignment;
            requestLayoutUpdate();
        }
    }

    public Alignment getCrossAlignment() { return crossAlignment; }

    public void setCrossAlignment(Alignment crossAlignment) {
        if (this.crossAlignment != crossAlignment) {
            this.crossAlignment = crossAlignment;
            requestLayoutUpdate();
        }
    }

    public LayoutOverflowStrategy getOverflowStrategy() { return overflowStrategy; }

    public void setOverflowStrategy(LayoutOverflowStrategy strategy) {
        if (strategy != null && this.overflowStrategy != strategy) {
            this.overflowStrategy = strategy;
            requestLayoutUpdate();
        }
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
