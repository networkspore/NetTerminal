package io.netnotes.terminal.components.panels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * TerminalFlexLayout — flexbox‑like layout container.
 *
 * <p>Supports:
 * <ul>
 *   <li>Direction: {@code ROW} (left‑to‑right) or {@code COLUMN} (top‑to‑bottom)</li>
 *   <li>Wrapping: enabled with {@link #setWrap(boolean)} – children flow onto
 *       multiple lines when the main‑axis space is exhausted.</li>
 *   <li>Main‑axis distribution: {@code JustifyContent START | CENTER | END |
 *       SPACE_BETWEEN | SPACE_AROUND | SPACE_EVENLY}</li>
 *   <li>Cross‑axis alignment: {@code AlignItems START | CENTER | END | STRETCH}
 *       (applies to each line individually when wrapping)</li>
 *   <li>Uniform gap between items (main‑axis and cross‑axis simultaneously)</li>
 *   <li>Child sizing: respect {@link SizePreference} (FILL = flex‑grow 1,
 *       FIT_CONTENT, STATIC, PERCENT) with min/max clamping</li>
 *   <li>Overflow: CLIP (default) or OVERFLOW per axis</li>
 * </ul>
 *
 * <p>Architecture mirrors {@link TerminalPanel}: extends {@link TerminalGroupRegion},
 * implements {@code measureContent()} for bottom‑up content sizing, and a
 * layout callback that places children.
 */
public class TerminalPanel extends TerminalGroupRegion {

    // ── Enums ──────────────────────────────────────────────────────────────────
    public enum FlexDirection { ROW, COLUMN }
    public enum JustifyContent { START, CENTER, END, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY }
    public enum AlignItems { START, CENTER, END, STRETCH }

    // ── State ──────────────────────────────────────────────────────────────────
    private FlexDirection direction = FlexDirection.ROW;
    private boolean wrap = false;
    private JustifyContent justifyContent = JustifyContent.START;
    private AlignItems alignItems = AlignItems.START;
    private int gap = 0;
    private LayoutOverflowStrategy overflowStrategy = LayoutOverflowStrategy.CLIP;
    private TerminalInsets padding = new TerminalInsets();

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================
    public TerminalPanel(String name) {
        super(name, "flex-");
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FILL);
        padding.setOnChanged(insets -> requestLayoutUpdate());
        syncOverflowClipPolicy();
    }

    @Override
    protected TerminalLayoutGroupCallback createLayoutCallback() {
        return this::layoutChildren;
    }

    // =========================================================================
    // CONFIGURATION
    // =========================================================================
    public void setDirection(FlexDirection d) {
        if (d != null && direction != d) { direction = d; requestLayoutUpdate(); }
    }
    public FlexDirection getDirection() { return direction; }

    public void setWrap(boolean w) {
        if (wrap != w) { wrap = w; requestLayoutUpdate(); }
    }
    public boolean isWrap() { return wrap; }

    public void setJustifyContent(JustifyContent jc) {
        if (jc != null && justifyContent != jc) { justifyContent = jc; requestLayoutUpdate(); }
    }
    public JustifyContent getJustifyContent() { return justifyContent; }

    public void setAlignItems(AlignItems ai) {
        if (ai != null && alignItems != ai) { alignItems = ai; requestLayoutUpdate(); }
    }
    public AlignItems getAlignItems() { return alignItems; }

    public void setSpacing(int s){
        setGap(s);
    }

    public void setGap(int g) {
        int clamped = Math.max(0, g);
        if (gap != clamped) { gap = clamped; requestLayoutUpdate(); }
    }
    public int getGap() { return gap; }

    public void setOverflowStrategy(LayoutOverflowStrategy s) {
        if (s != null && overflowStrategy != s) { overflowStrategy = s; syncOverflowClipPolicy(); requestLayoutUpdate(); }
    }
    public LayoutOverflowStrategy getOverflowStrategy() { return overflowStrategy; }

    public void setPadding(int all) {
        int c = Math.max(0, all);
        if (padding.getTop() != c || padding.getRight() != c || padding.getBottom() != c || padding.getLeft() != c) {
            padding.setAll(c);
        }
    }
    public void setPadding(int vertical, int horizontal) {
        if (padding.getTop() != vertical || padding.getRight() != horizontal ||
            padding.getBottom() != vertical || padding.getLeft() != horizontal) {
            padding.set(vertical, horizontal, vertical, horizontal);
        }
    }
    @Override public TerminalInsets getInsets() { return padding; }

    private void syncOverflowClipPolicy() {
        setOverflowClipPolicy(
            overflowStrategy == LayoutOverflowStrategy.OVERFLOW
                ? TerminalRenderable.OverflowClipPolicy.INHERIT_PARENT_CLIP
                : TerminalRenderable.OverflowClipPolicy.CLIP_TO_SELF_BOUNDS
        );
    }


    // =========================================================================
    // LAYOUT CALLBACK
    // =========================================================================
    private void layoutChildren(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        if (contexts.length == 0) return;
        TerminalRectangle parent = contexts[0].getParentRegion();
        if (parent == null) return;

        TerminalInsets ins = getInsets();
        // available main-axis and cross-axis dimensions
        int availMain, availCross;
        if (direction == FlexDirection.ROW) {
            availMain = parent.getWidth() - ins.getHorizontal();
            availCross = parent.getHeight() - ins.getVertical();
        } else {
            availMain = parent.getHeight() - ins.getVertical();
            availCross = parent.getWidth() - ins.getHorizontal();
        }

        // ── gather visible children & measure each axis independently ────────
        List<ChildInfo> children = new ArrayList<>();
        for (int i = 0; i < contexts.length; i++) {
            TerminalRenderable r = contexts[i].getRenderable();
            if (!canUnhide(r)) continue;
            TerminalRegion child = checkTerminalRegion(r);
            TerminalLayoutContext ctx = contexts[i];

            SizePreference mainPref = childSizePref(child, true);
            SizePreference crossPref = childSizePref(child, false);

            // raw sizes (may be -1 for FILL)
            int rawMain = resolveChildSize(child, ctx, true, availMain);
            int rawCross = resolveChildSize(child, ctx, false, availCross);

            // min/max clamping using the actual width/height values
            int minMain, maxMain, minCross, maxCross;
            if (direction == FlexDirection.ROW) {
                minMain = child.getMinWidth();
                maxMain = child.getMaxWidth();
                minCross = child.getMinHeight();
                maxCross = child.getMaxHeight();
            } else {
                minMain = child.getMinHeight();
                maxMain = child.getMaxHeight();
                minCross = child.getMinWidth();
                maxCross = child.getMaxWidth();
            }

            int mainSize = (rawMain < 0) ? rawMain : clampDimension(child, rawMain, direction == FlexDirection.ROW ? true : false);
            int crossSize = clampDimension(child, rawCross, direction == FlexDirection.ROW ? false : true);

            children.add(new ChildInfo(child, ctx, mainPref, crossPref, mainSize, crossSize, minMain, maxMain, minCross, maxCross));
        }

        if (children.isEmpty()) return;

        // ── wrap into lines ──────────────────────────────────────────────────
        List<List<ChildInfo>> lines;
        if (!wrap) {
            lines = List.of(children);
        } else {
            lines = wrapIntoLines(children, availMain, gap);
        }

        boolean isMainClipped = overflowStrategy != LayoutOverflowStrategy.OVERFLOW
            && (direction == FlexDirection.ROW ? getWidthPreference() != SizePreference.FIT_CONTENT
                                               : getHeightPreference() != SizePreference.FIT_CONTENT);

        // ── compute line cross sizes and total cross extent ──────────────────
        List<Integer> lineCrossSizes = new ArrayList<>();
        int totalCrossExtent = 0;
        for (int li = 0; li < lines.size(); li++) {
            List<ChildInfo> line = lines.get(li);
            int lineCross = 0;
            for (ChildInfo c : line) {
                lineCross = Math.max(lineCross, c.crossSize);
            }
            lineCrossSizes.add(lineCross);
            totalCrossExtent += lineCross;
            if (li < lines.size() - 1) totalCrossExtent += gap;
        }

        // cross‑axis offset (layout of the whole flex container inside its content box)
        int crossStart = ins.getTop(); // for ROW; for COLUMN it's ins.getLeft()
        if (direction == FlexDirection.COLUMN) crossStart = ins.getLeft();
        int crossSpace = availCross - totalCrossExtent;
        int crossOffset = crossStart;
        if (crossSpace > 0) {
            // treat AlignItems as overall line‑alignment on the cross axis (simplified: center/end the block)
            if (alignItems == AlignItems.CENTER || alignItems == AlignItems.START || alignItems == AlignItems.END) {
                if (alignItems == AlignItems.CENTER) crossOffset += crossSpace / 2;
                else if (alignItems == AlignItems.END) crossOffset += crossSpace;
            }
        }

        int crossCursor = crossOffset;

        // ── layout each line ─────────────────────────────────────────────────
        for (int li = 0; li < lines.size(); li++) {
            List<ChildInfo> line = lines.get(li);
            int lineCrossSize = lineCrossSizes.get(li);

            // resolve FILL children in main axis (jsut after line break)
            int usedFixedMain = 0;
            int fillCount = 0;
            for (ChildInfo c : line) {
                if (c.mainSize >= 0) usedFixedMain += c.mainSize;
                else fillCount++;
            }
            int totalGapsMain = (line.size() - 1) * gap;
            int freeMain = availMain - usedFixedMain - totalGapsMain;
            int fillSize = 0;
            if (fillCount > 0 && freeMain > 0 && justifyContent != JustifyContent.SPACE_BETWEEN
                    && justifyContent != JustifyContent.SPACE_AROUND && justifyContent != JustifyContent.SPACE_EVENLY) {
                fillSize = freeMain / fillCount;
            } else if (fillCount > 0) {
                fillSize = 0; // space distributions don't expand FILL items
            }

            for (ChildInfo c : line) {
                if (c.mainSize < 0) {
                    c.finalMain = isMainClipped ? Math.max(0, fillSize) : Math.max(0, c.minMain);
                } else {
                    c.finalMain = c.mainSize;
                }
                c.finalMain = clampDimension(c.child, c.finalMain, direction == FlexDirection.ROW ? true : false);
            }

            // stretch cross size if needed
            for (ChildInfo c : line) {
                if (alignItems == AlignItems.STRETCH && c.crossPref == SizePreference.FILL) {
                    c.finalCross = lineCrossSize;
                } else {
                    c.finalCross = c.crossSize;
                }
                c.finalCross = clampDimension(c.child, c.finalCross, direction == FlexDirection.ROW ? false : true);
            }

            // main‑axis distribution
            int totalMain = 0;
            for (ChildInfo c : line) totalMain += c.finalMain;
            totalMain += totalGapsMain;

            int mainStartOffset = ins.getLeft(); // for ROW; for COLUMN it's ins.getTop()
            if (direction == FlexDirection.COLUMN) mainStartOffset = ins.getTop();

            int remainingMain = availMain - totalMain;
            int mainCursor = mainStartOffset;
            int between = 0, around = 0, evenly = 0;

            if (justifyContent == JustifyContent.CENTER) {
                mainCursor += remainingMain / 2;
            } else if (justifyContent == JustifyContent.END) {
                mainCursor += remainingMain;
            } else if (justifyContent == JustifyContent.SPACE_BETWEEN && line.size() > 1) {
                between = remainingMain / (line.size() - 1);
            } else if (justifyContent == JustifyContent.SPACE_AROUND) {
                around = remainingMain / line.size();
                mainCursor += around / 2;
            } else if (justifyContent == JustifyContent.SPACE_EVENLY) {
                evenly = remainingMain / (line.size() + 1);
                mainCursor += evenly;
            }

            for (int ci = 0; ci < line.size(); ci++) {
                ChildInfo c = line.get(ci);
                int childMain = c.finalMain;
                int childCross = c.finalCross;

                // cross‑position within line
                int crossPos = crossCursor;
                if (alignItems == AlignItems.CENTER) crossPos += (lineCrossSize - childCross) / 2;
                else if (alignItems == AlignItems.END) crossPos += lineCrossSize - childCross;
                // START/STRETCH stay at crossCursor

                int x, y, w, h;
                if (direction == FlexDirection.ROW) {
                    x = mainCursor;
                    y = crossPos;
                    w = childMain;
                    h = childCross;
                } else {
                    x = crossPos;
                    y = mainCursor;
                    w = childCross;
                    h = childMain;
                }

                boolean inBounds = isWithinParentBounds(x, y, w, h, parent);
                boolean hidden = false;
                if (overflowStrategy != LayoutOverflowStrategy.OVERFLOW && !inBounds) {
                    hidden = true;
                } else if (w <= 0 || h <= 0) {
                    hidden = true;
                }

                dataInterfaces.get(c.child.getName()).setLayoutData(
                    TerminalLayoutData.getBuilder()
                        .setX(x)
                        .setY(y)
                        .setWidth(Math.max(0, w))
                        .setHeight(Math.max(0, h))
                        .hidden(hidden)
                        .build()
                );

                // advance main cursor
                if (ci < line.size() - 1) {
                    int space = 0;
                    if (justifyContent == JustifyContent.SPACE_BETWEEN) space = between;
                    else if (justifyContent == JustifyContent.SPACE_AROUND) space = around;
                    else if (justifyContent == JustifyContent.SPACE_EVENLY) space = evenly;
                    mainCursor += childMain + gap + space;
                } else {
                    mainCursor += childMain;
                }
            }
            crossCursor += lineCrossSize + gap;
        }
    }

    // ── measurement pre‑pass ──────────────────────────────────────────────────
  
    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        SizePreference ownMainPref = (direction == FlexDirection.ROW) ? getWidthPreference() : getHeightPreference();
        SizePreference ownCrossPref = (direction == FlexDirection.ROW) ? getHeightPreference() : getWidthPreference();
        TerminalInsets ins = getInsets();

        int contentMain = 0;
        int contentCross = 0;

        // Only perform intrinsic calculation if the container is content-dependent on at least one axis.
        if (ownMainPref == SizePreference.FIT_CONTENT || ownCrossPref == SizePreference.FIT_CONTENT) {

            List<ChildInfo> participating = new ArrayList<>();
            for (int i = 0; i < childContexts.length; i++) {
                TerminalLayoutContext ctx = childContexts[i];
                TerminalRenderable child = ctx.getRenderable();

                // Force‑hidden children are completely excluded.
                if (!canUnhide(child)) continue;

                // Determine if the child should be counted for measurement.
                // It counts if:
                //  - the container is FIT_CONTENT on the main axis (will grow) or OVERFLOW allows overflow → all unhidden children fit
                //  - otherwise, only count children that are currently visible (not hidden).
                boolean include = false;
                if (ownMainPref == SizePreference.FIT_CONTENT || overflowStrategy == LayoutOverflowStrategy.OVERFLOW) {
                    include = true;   // container will resize/overflow → child will be shown eventually
                } else {
                    include = !child.isHidden();
                }

                if (include) {
                    TerminalRegion tr = checkTerminalRegion(child);
                    int mainSize = resolveChildMeasureSize(tr, ctx, true);
                    int crossSize = resolveChildMeasureSize(tr, ctx, false);
                    participating.add(new ChildInfo(tr, ctx, null, null, mainSize, crossSize, 0, 0, 0, 0));
                }
            }

            if (!participating.isEmpty()) {
                if (wrap) {
                    int maxLineMain = 0;
                    int curLineMain = 0;
                    int curLineCross = 0;
                    int totalCross = 0;
                    boolean firstInLine = true;

                    for (ChildInfo c : participating) {
                        int needed = c.mainSize + (firstInLine ? 0 : gap);
                        curLineMain += needed;
                        curLineCross = Math.max(curLineCross, c.crossSize);
                        firstInLine = false;
                    }
                    maxLineMain = curLineMain;
                    totalCross = curLineCross;

                    if (direction == FlexDirection.ROW) {
                        contentMain = maxLineMain;
                        contentCross = totalCross;
                    } else {
                        contentCross = maxLineMain;
                        contentMain = totalCross;
                    }
                } else {
                    // Unwrapped: sum main sizes, max cross.
                    int sumMain = 0;
                    int maxCross = 0;
                    for (int i = 0; i < participating.size(); i++) {
                        if (i > 0) sumMain += gap;
                        sumMain += participating.get(i).mainSize;
                        maxCross = Math.max(maxCross, participating.get(i).crossSize);
                    }
                    if (direction == FlexDirection.ROW) {
                        contentMain = sumMain;
                        contentCross = maxCross;
                    } else {
                        contentCross = sumMain;
                        contentMain = maxCross;
                    }
                }

                contentMain = clampDimension(this, contentMain + (direction == FlexDirection.ROW ? ins.getHorizontal() : ins.getVertical()), direction == FlexDirection.ROW);
                contentCross = clampDimension(this, contentCross + (direction == FlexDirection.ROW ? ins.getVertical() : ins.getHorizontal()), direction == FlexDirection.COLUMN);
            }
        }

        int finalW, finalH;
        if (direction == FlexDirection.ROW) {
            finalW = switch (getWidthPreference()) {
                case STATIC      -> region.getWidth();
                case FIT_CONTENT -> Math.max(getMinWidth(), contentMain);
                default          -> getMinWidth();
            };
            finalH = switch (getHeightPreference()) {
                case STATIC      -> region.getHeight();
                case FIT_CONTENT -> Math.max(getMinHeight(), contentCross);
                default          -> getMinHeight();
            };
        } else {
            finalW = switch (getWidthPreference()) {
                case STATIC      -> region.getWidth();
                case FIT_CONTENT -> Math.max(getMinWidth(), contentCross);
                default          -> getMinWidth();
            };
            finalH = switch (getHeightPreference()) {
                case STATIC      -> region.getHeight();
                case FIT_CONTENT -> Math.max(getMinHeight(), contentMain);
                default          -> getMinHeight();
            };
        }

        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0, finalW, finalH);
        return measured;
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private List<List<ChildInfo>> wrapIntoLines(List<ChildInfo> children, int availMain, int gap) {
        List<List<ChildInfo>> lines = new ArrayList<>();
        List<ChildInfo> curLine = new ArrayList<>();
        int curMain = 0;
        for (ChildInfo c : children) {
            int need = c.mainSize + (curLine.isEmpty() ? 0 : gap);
            if (!curLine.isEmpty() && curMain + need > availMain) {
                lines.add(curLine);
                curLine = new ArrayList<>();
                curMain = 0;
            }
            curLine.add(c);
            curMain += c.mainSize + (curLine.size() > 1 ? gap : 0);
        }
        if (!curLine.isEmpty()) lines.add(curLine);
        return lines;
    }


    private SizePreference childSizePref(TerminalRegion child, boolean mainAxis) {
        SizePreference pref;
        if (direction == FlexDirection.ROW) {
            pref = mainAxis ? child.getWidthPreference() : child.getHeightPreference();
        } else {
            pref = mainAxis ? child.getHeightPreference() : child.getWidthPreference();
        }
        if (pref == SizePreference.INHERIT) {
            pref = mainAxis ? (direction == FlexDirection.ROW ? getWidthPreference() : getHeightPreference())
                            : (direction == FlexDirection.ROW ? getHeightPreference() : getWidthPreference());
        }
        return pref;
    }


    private int resolveChildSize(TerminalRegion child, TerminalLayoutContext ctx, boolean mainAxis, int avail) {
        SizePreference pref = childSizePref(child, mainAxis);
        boolean isWidth = (direction == FlexDirection.ROW) ? mainAxis : !mainAxis; // true if this axis maps to width
        switch (pref) {
            case FILL: return -1;
            case FIT_CONTENT: return readContentDimension(child, ctx, isWidth);
            case PERCENT:
                double pc = child.getPercent(isWidth ? TerminalRegion.AXIS_W : TerminalRegion.AXIS_H);
                return (int)(avail * pc);
            default: // STATIC
                return readContentDimension(child, ctx, isWidth);
        }
    }

    // For measurement (no parent avail)
    private int resolveChildMeasureSize(TerminalRegion child, TerminalLayoutContext ctx, boolean mainAxis) {
        SizePreference pref = childSizePref(child, mainAxis);
        boolean isWidth = (direction == FlexDirection.ROW) ? mainAxis : !mainAxis;
        if (pref == SizePreference.FILL) {
            // return min size as floor
            return isWidth ? child.getMinWidth() : child.getMinHeight();
        } else if (pref == SizePreference.FIT_CONTENT || pref == SizePreference.STATIC) {
            return readContentDimension(child, ctx, isWidth);
        } else if (pref == SizePreference.PERCENT) {
            // percent needs parent; return 0 (will be ignored in content measurement)
            return 0;
        }
        return 0;
    }



    // isWithinParentBounds
    private boolean isWithinParentBounds(int x, int y, int w, int h, TerminalRectangle parent) {
        return x >= 0 && y >= 0 && x + w <= parent.getWidth() && y + h <= parent.getHeight();
    }

    


    // Override getMaxWidth/Height to use TerminalRegion's getMaxWidth?
    // Not needed, we can directly access max via child.getMaxWidth().
    // For clampDimension, we need the child and value.
    // We'll use TerminalRegion.clampDimension(child, value, isWidth).

    // ── inner class for info ──────────────────────────────────────────────────
    private static class ChildInfo {
            final TerminalRegion child;
            final TerminalLayoutContext ctx;
            SizePreference mainPref, crossPref;
            int mainSize, crossSize;           // raw sizes (may be negative for FILL)
            int minMain, maxMain, minCross, maxCross;
            int finalMain, finalCross;

            ChildInfo(TerminalRegion child, TerminalLayoutContext ctx,
                    SizePreference mainPref, SizePreference crossPref,
                    int mainSize, int crossSize, int minMain, int maxMain,
                    int minCross, int maxCross) {
                this.child = child;
                this.ctx = ctx;
                this.mainPref = mainPref;
                this.crossPref = crossPref;
                this.mainSize = mainSize;
                this.crossSize = crossSize;
                this.minMain = minMain;
                this.maxMain = maxMain;
                this.minCross = minCross;
                this.maxCross = maxCross;
            }
        }

    // =========================================================================
    // RENDERING (nothing for now)
    // =========================================================================
    @Override protected void renderSelf(TerminalBatchBuilder batch) { }
}