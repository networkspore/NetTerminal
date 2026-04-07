package io.netnotes.terminal;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.debug.RenderDiagnostics;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalLayoutCallback;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutGroup;
import io.netnotes.terminal.layout.TerminalLayoutGroupCallback;
import io.netnotes.terminal.layout.TerminalLayoutNode;
import io.netnotes.terminal.layout.TerminalSizeable;
import io.netnotes.engine.ui.Point2D;
import io.netnotes.engine.ui.Position;
import io.netnotes.engine.ui.SpatialRegionPool;
import io.netnotes.engine.ui.TextAlignment;
import io.netnotes.engine.ui.renderer.Renderable;
import io.netnotes.engine.ui.renderer.RenderableStates;

/**
 * TerminalRenderable - Abstract base class for terminal renderables
 * 
 * Provides terminal-specific rendering capabilities on top of base Renderable
 * 
 * HIERARCHY:
 * - Renderable<B, R, S> - Base class with state machine, events, invalidation
 * - TerminalRenderable - Terminal-specific rendering (this class)
 * - TerminalScreen - Application screens (menus, forms, etc.)
 * - Terminal UI Components - Buttons, text boxes, etc.
 * 
 * ARCHITECTURE:
 * - Uses TerminalRectangle (2D) as spatial region type
 * - Integrates with TerminalBatchBuilder for rendering
 * - Provides terminal-specific helper methods (x,y operations)
 * - Maintains consistency with base Renderable damage tracking
 * 
 * COORDINATE SYSTEM:
 * - x = horizontal position (0 = left)
 * - y = vertical position (0 = top)
 * - All methods use (x, y) parameter ordering
 * 
 * BOUNDARY ENFORCEMENT:
 * - All rendering operations respect component bounds
 * - Operations are automatically clipped/clamped based on mode
 * - Prevents components from affecting areas outside their bounds
    //<B,P,S,LC,LD,LCB,GCB,G,R>
*/
public abstract class TerminalRenderable extends Renderable<
    TerminalBatchBuilder,
    Point2D,
    TerminalRectangle,
    TerminalLayoutNode,
    TerminalLayoutContext,
    TerminalLayoutData,
    TerminalLayoutCallback,
    TerminalLayoutGroupCallback,
    TerminalRenderable.TerminalGroupStateEntry,
    TerminalLayoutGroup,
    TerminalRenderable
> {
    
    private boolean clampCursor = true;          // Default to clamping cursor
    
    /**
     * Constructor
     * 
     * @param name Renderable name for debugging
     */
    protected TerminalRenderable(String name) {
        super(name, TerminalRectanglePool.getInstance());  
    }

    public boolean isLayoutExcluded() {
        return hasState(RenderableStates.STATE_HIDDEN_DESIRED);
    }

    @Override
    public SpatialRegionPool<TerminalRectangle> getRegionPool() {
        return regionPool;
    }

    @Override
    protected void setupEventHandlers() { }
    
    @Override
    protected void setupStateTransitions() {
        // No custom transitions
    }
    
    // ===== CLIP MODE CONTROL =====
    

    public void setClampCursor(boolean clamp) {
        this.clampCursor = clamp;
    }
    
    public boolean isClampCursor() {
        return clampCursor;
    }
    
    // ===== TERMINAL-SPECIFIC HELPERS (x,y convention) =====
  
    /**
     * Get x coordinate (left edge) - local to this renderable
     */
    protected int getX() {
        return region.getX();
    }
    
    /**
     * Get y coordinate (top edge) - local to this renderable
     */
    protected int getY() {
        return region.getY();
    }
    
    /**
     * Get center y offset within this renderable's bounds (half-height)
     */
    protected int getCenterYOffset() {
        return region.getHeight() / 2;
    }
    
    /**
     * Get center x offset within this renderable's bounds (half-width)
     */
    protected int getCenterXOffset() {
        return region.getWidth() / 2;
    }
    
    /**
     * Center text horizontally
     * 
     * @param text Text to center
     * @return X position for centered text
     */
    protected int centerTextHorizontal(String text) {
        return Math.max(0, (region.getWidth() - text.length()) / 2);
    }
    
    /**
     * Check if position is within bounds (local coordinates)
     * 
     * @param x horizontal position (relative to this renderable)
     * @param y vertical position (relative to this renderable)
     */
    protected boolean isInBounds(int x, int y) {
        return x >= 0 && x < region.getWidth() && 
               y >= 0 && y < region.getHeight();
    }
    
    /**
     * Clamp x to valid range
     */
    protected int clampX(int x) {
        return Math.max(0, Math.min(x, region.getWidth() - 1));
    }
    
    /**
     * Clamp y to valid range
     */
    protected int clampY(int y) {
        return Math.max(0, Math.min(y, region.getHeight() - 1));
    }
    
    /**
     * Convert local x,y to absolute coordinates
     * Returns Point2D in absolute screen space
     */
    protected Point2D toAbsolute(int x, int y) {
        TerminalRectangle absReg = getAbsoluteRegion();
        Point2D result = new Point2D(absReg.getX() + x, absReg.getY() + y);
        regionPool.recycle(absReg);
        return result;
    }

    public int getAbsoluteX(){
        return getRegion().getAbsoluteX();
    }

    public int getAbsoluteY(){
        return getRegion().getAbsoluteY();
    }

    
    /**
     * Convert local x to absolute coordinate
     */
    private int toAbsoluteX(int localX) {
        return localX + getAbsoluteX();
    }
    
    /**
     * Convert local y to absolute coordinate
     */
    private int toAbsoluteY(int localY) {
        return localY + getAbsoluteY();
    }
    
    /**
     * Invalidate a specific region in terminal coordinates
     * 
     * @param x Starting x (relative to this renderable)
     * @param y Starting y (relative to this renderable)
     * @param width Width in columns
     * @param height Height in rows
     */
    protected void invalidateRegion(int x, int y, int width, int height) {
        TerminalRectangle region = regionPool.obtain();
        region.set(x, y, width, height);
        invalidate(region);
        regionPool.recycle(region);
    }
    
    /**
     * Invalidate a single cell
     * 
     * @param x horizontal position
     * @param y vertical position
     */
    protected void invalidateCell(int x, int y) {
        invalidateRegion(x, y, 1, 1);
    }
    
    /**
     * Invalidate a horizontal line
     * 
     * @param x Starting x
     * @param y vertical position
     * @param length Number of columns
     */
    protected void invalidateHLine(int x, int y, int length) {
        invalidateRegion(x, y, length, 1);
    }
    
    /**
     * Invalidate a vertical line
     * 
     * @param x horizontal position
     * @param y Starting y
     * @param length Number of rows
     */
    protected void invalidateVLine(int x, int y, int length) {
        invalidateRegion(x, y, 1, length);
    }
    
    // ===== 2D SPATIAL PROPERTIES =====
    
    /**
     * Get width
     */
    public int getWidth() {
        return region.getWidth();
    }
    
    /**
     * Get height
     */
    public int getHeight() {
        return region.getHeight();
    }
    
    /**
     * Get right edge (x + width)
     */
    public int getRight() {
        TerminalRectangle rect = region;
        return rect.getRight();
    }
    
    /**
     * Get bottom edge (y + height)
     */
    public int getBottom() {
        TerminalRectangle rect = region;
        return rect.getBottom();
    }
    
    /**
     * Get center X
     */
    public int getCenterX() {
        TerminalRectangle rect = region;
        return rect.getCenterX();
    }
    
    /**
     * Get center Y
     */
    public int getCenterY() {
        TerminalRectangle rect = region;
        return rect.getCenterY();
    }
    
    // ===== 2D CONVENIENCE SETTERS =====
    private TerminalRectangle getMutationBase() {
        TerminalRectangle base = regionPool.obtain();
        base.copyFrom(requestedRegion != null ? requestedRegion : region);
        return base;
    }

    public void setX(int x) {
        ensureRequestedRegion();
        requestedRegion.setX(x);
        requestLayoutUpdate();
    }
    
    public void setY(int y) {
        ensureRequestedRegion();
        requestedRegion.setY(y);
        requestLayoutUpdate();
    }

        
    public void setPosition(int x, int y) {
        ensureRequestedRegion();
        requestedRegion.setPosition(x, y);
        requestLayoutUpdate();
    }
    
    /**
     * Set width
     * Convenience method - creates temporary region
     */
    public void setWidth(int width) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.setWidth(width);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    /**
     * Set height
     * Convenience method - creates temporary region
     */
    public void setHeight(int height) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.setHeight(height);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    /**
     * Set size (width, height)
     * Convenience method - creates temporary region
     */
    public void setSize(int width, int height) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.setSize(width, height);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    /**
     * Set complete bounds
     * Convenience method - creates temporary region
     */
    public void setRegion(int x, int y, int width, int height) {
        if (isHidden()) return;
        ensureRequestedRegion();
        requestedRegion.set(x, y, width, height);
        requestLayoutUpdate();
    }

    public void setBounds(int x, int y, int width, int height){
        setRegion(x, y, width, height);
    }

  
    /**
     * Translate by offset
     * Convenience method - creates temporary region
     */
    public void translate(int dx, int dy) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.translate(dx, dy);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    /**
     * Expand outward by amount (negative to shrink)
     * Convenience method - creates temporary region
     */
    public void expand(int dx, int dy) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.expand(dx, dy);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    // ===== STRING REPRESENTATION =====
    
    /**
     * Get string representation with bounds
     */
    public String getBoundsString() {
        return String.format(
            "%s[x=%d, y=%d, w=%d, h=%d]",
            getName(), getX(), getY(), getWidth(), getHeight()
        );
    }
    
    /**
     * Get string representation with absolute bounds
     */
    public String getAbsoluteBoundsString() {

        String result = String.format("%s[absX=%d, absY=%d, w=%d, h=%d]",
            getName(), getAbsoluteX(), getAbsoluteY(), getWidth(), getHeight()
        );

        return result;
    }

    @Override
    protected TerminalGroupStateEntry createGroupStateEntry() {
        return new TerminalGroupStateEntry();
    }


    // ===== RENDERING COMMANDS WITH BOUNDARY ENFORCEMENT =====
    
    /**
     * Print text at position (local coordinates)
     * Automatically enforces boundaries based on clip mode
     */
    protected void printAt(TerminalBatchBuilder batch, int x, int y, String text) {
        printAt(batch, x, y, text, TextStyle.NORMAL);
    }
    
    protected void printAt(TerminalBatchBuilder batch, int x, int y, String text, TextStyle style) {
        if (text.isEmpty()) {
            return;
        }
        if (!isEffectivelyVisible()) {
            RenderDiagnostics.logRenderDrop(
                "printAt-hidden:" + getName(),
                "TerminalRenderable.printAt",
                "not-effectively-visible",
                () -> "renderable=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\ttext=" + RenderDiagnostics.summarizeText(text, 48)
            );
            return;
        }
        if (y < 0 || y >= getHeight()) {
            RenderDiagnostics.logRenderDrop(
                "printAt-y-oob:" + getName(),
                "TerminalRenderable.printAt",
                "y-out-of-bounds",
                () -> "renderable=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\ttext=" + RenderDiagnostics.summarizeText(text, 48)
                    + "\n\tlocalY=" + y
                    + "\n\theight=" + getHeight()
            );
            return;
        }
        
        int absY = toAbsoluteY(y);
        int absX = toAbsoluteX(x);
        int left = toAbsoluteX(Math.max(0, x));
        int right = toAbsoluteX(Math.min(getWidth(), x + text.length()));

        if (right <= left) {
            RenderDiagnostics.logRenderDrop(
                "printAt-x-oob:" + getName(),
                "TerminalRenderable.printAt",
                "x-outside-renderable-bounds",
                () -> "renderable=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\ttext=" + RenderDiagnostics.summarizeText(text, 48)
                    + "\n\tlocalX=" + x
                    + "\n\twidth=" + getWidth()
                    + "\n\tabsRange=[" + left + "," + right + ")"
            );
            return;
        }
        
        TerminalRectangle clip = batch.getCurrentClipRegion();
        final int clippedLeft;
        final int clippedRight;
        if (clip != null) {
            if (absY < clip.getY() || absY >= clip.getY() + clip.getHeight()) {
                RenderDiagnostics.logRenderDrop(
                    "printAt-clip-y:" + getName(),
                    "TerminalRenderable.printAt",
                    "clip-excluded-y",
                    () -> "renderable=" + RenderDiagnostics.summarizeRenderable(this)
                        + "\n\ttext=" + RenderDiagnostics.summarizeText(text, 48)
                        + "\n\tabsY=" + absY
                        + "\n\tclip=" + RenderDiagnostics.summarizeRegion(clip)
                );
                return;
            }
            clippedLeft = Math.max(left, clip.getX());
            clippedRight = Math.min(right, clip.getX() + clip.getWidth());
        } else {
            clippedLeft = left;
            clippedRight = right;
        }
        
        if (clippedRight <= clippedLeft) {
            RenderDiagnostics.logRenderDrop(
                "printAt-clipped-away:" + getName(),
                "TerminalRenderable.printAt",
                "clip-excluded-x",
                () -> "renderable=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\ttext=" + RenderDiagnostics.summarizeText(text, 48)
                    + "\n\tabsRange=[" + left + "," + right + ")"
                    + "\n\tclippedRange=[" + clippedLeft + "," + clippedRight + ")"
                    + "\n\tclip=" + RenderDiagnostics.summarizeRegion(clip)
            );
            return;
        }
        
        int startIdx = clippedLeft - absX;
        int endIdx = clippedRight - absX;

        batch.printAt(clippedLeft, absY, text.substring(Math.max(0, startIdx), Math.min(text.length(), endIdx)), style);
    }

    @Override
    public void requestLayoutUpdate() {
        RenderDiagnostics.logSwapTrace(
            "TerminalRenderable.requestLayoutUpdate",
            this,
            () -> "parent=" + RenderDiagnostics.summarizeRenderable(getParent())
                + "\n\trenderPhase=" + getRenderPhase()
        );
        super.requestLayoutUpdate();
    }

    @Override
    public void setVisible(boolean visible) {
        RenderDiagnostics.logSwapTrace(
            "TerminalRenderable.setVisible:before",
            this,
            () -> "requestedVisible=" + visible
                + "\n\tparent=" + RenderDiagnostics.summarizeRenderable(getParent())
                + "\n\trenderPhase=" + getRenderPhase()
        );
        super.setVisible(visible);
        RenderDiagnostics.logSwapTrace(
            "TerminalRenderable.setVisible:after",
            this,
            () -> "requestedVisible=" + visible
                + "\n\tparent=" + RenderDiagnostics.summarizeRenderable(getParent())
                + "\n\trenderPhase=" + getRenderPhase()
        );
    }

    @Override
    public void invalidate(TerminalRectangle damageRegion) {
        RenderDiagnostics.logSwapTrace(
            "TerminalRenderable.invalidate",
            this,
            () -> "damage=" + (damageRegion != null
                ? RenderDiagnostics.summarizeRegion(damageRegion)
                : "FULL")
                + "\n\tparent=" + RenderDiagnostics.summarizeRenderable(getParent())
                + "\n\trenderPhase=" + getRenderPhase()
        );
        super.invalidate(damageRegion);
    }

    @Override
    public void toBatch(TerminalBatchBuilder batch, TerminalRectangle clipRegion) {
        TerminalRectangle region = getRegion();
        if (isVisible() && (region == null || region.getWidth() <= 0 || region.getHeight() <= 0)) {
            RenderDiagnostics.logRenderDrop(
                "to-batch-empty-region:" + getName(),
                "TerminalRenderable.toBatch",
                "empty-region",
                () -> "renderable=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\tclipRegion=" + RenderDiagnostics.summarizeRegion(clipRegion)
            );
        }
        if (isEffectivelyVisible()
            && region != null
            && clipRegion != null
            && region.getWidth() > 0
            && region.getHeight() > 0
            && (clipRegion.getWidth() < region.getWidth() || clipRegion.getHeight() < region.getHeight())) {
            RenderDiagnostics.logRenderBlocker(
                "to-batch-tight-clip:" + getName(),
                250_000_000L,
                "TerminalRenderable.toBatch",
                "clip-smaller-than-region",
                () -> "renderable=" + RenderDiagnostics.summarizeRenderable(this)
                    + "\n\tclipRegion=" + RenderDiagnostics.summarizeRegion(clipRegion)
            );
        }
        super.toBatch(batch, clipRegion);
    }

    @Override
    protected void renderChildrenByLayer(
        TerminalBatchBuilder batch,
        TerminalRectangle visibleClip,
        TerminalRectangle forcedRegion
    ) {
        TerminalRectangle childClip = createChildRenderClip(visibleClip);
        if (childClip == null || childClip.isEmpty()) {
            if (childClip != null) {
                regionPool.recycle(childClip);
            }
            return;
        }

        TerminalRectangle childForced = null;
        if (forcedRegion != null) {
            childForced = regionPool.obtain();
            if (!forcedRegion.intersect(childClip, childForced)) {
                regionPool.recycle(childForced);
                childForced = null;
            }
        }

        try {
            super.renderChildrenByLayer(batch, childClip, childForced);
        } finally {
            if (childForced != null) {
                regionPool.recycle(childForced);
            }
            regionPool.recycle(childClip);
        }
    }

    protected TerminalInsets getChildRenderInsets() {
        if (this instanceof TerminalSizeable sizeable) {
            TerminalInsets insets = sizeable.getInsets();
            if (insets != null && !insets.isZero()) {
                return insets;
            }
        }
        return null;
    }

    private TerminalRectangle createChildRenderClip(TerminalRectangle visibleClip) {
        if (visibleClip == null || visibleClip.isEmpty()) {
            return null;
        }

        TerminalInsets childInsets = getChildRenderInsets();
        if (childInsets == null || childInsets.isZero()) {
            TerminalRectangle clipCopy = regionPool.obtain();
            clipCopy.copyFrom(visibleClip);
            return clipCopy;
        }

        TerminalRectangle absoluteRegion = getAbsoluteRegion();
        TerminalRectangle innerClip = absoluteRegion.deflateClamped(childInsets);
        TerminalRectangle clipped = regionPool.obtain();

        try {
            if (!innerClip.intersect(visibleClip, clipped)) {
                regionPool.recycle(clipped);
                return null;
            }
            return clipped;
        } finally {
            regionPool.recycle(innerClip);
            regionPool.recycle(absoluteRegion);
        }
    }

    @Override
    protected void onRegionChanged(TerminalRectangle oldRegion, TerminalRectangle newRegion) {
        super.onRegionChanged(oldRegion, newRegion);
    }
    
    /**
     * Clear a line at specified y position
     * NOTE: Converted to clearRegion to respect bounds
     */
    protected void clearLineAt(TerminalBatchBuilder batch, int y) {
        // Convert to clearRegion to properly enforce bounds
        clearRegion(batch, 0, y, getWidth(), 1);
    }

     protected void clear(TerminalBatchBuilder batch) {
        // Convert to clearRegion to properly enforce bounds
        clearRegion(batch, 0, 0, getWidth(), getHeight());
    }
    
    /**
     * Clear a rectangular region (local coordinates)
     */
    protected void clearRegion(TerminalBatchBuilder batch, int x, int y, int width, int height) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;
    
        int left = toAbsoluteX(Math.max(0, x));
        int top = toAbsoluteY(Math.max(0, y));
        int right = toAbsoluteX(Math.min(getWidth(), x + width));
        int bottom = toAbsoluteY(Math.min(getHeight(), y + height));
        
        TerminalRectangle clip = batch.getCurrentClipRegion();
        if (clip != null) {
            left = Math.max(left, clip.getX());
            top = Math.max(top, clip.getY());
            right = Math.min(right, clip.getX() + clip.getWidth());
            bottom = Math.min(bottom, clip.getY() + clip.getHeight());
        }
        
        if (right <= left || bottom <= top) return;
        
        TerminalRectangle region = regionPool.obtain();
        region.set(left, top, right - left, bottom - top, 0, 0);
        batch.clearRegion(region);
        regionPool.recycle(region);
    }
    
    /**
     * Clear a rectangular region using TerminalRectangle (local coordinates)
     */
    protected void clearRegion(TerminalBatchBuilder batch, TerminalRectangle region) {
        clearRegion(batch, region.getX(), region.getY(), region.getWidth(), region.getHeight());
    }
    
    /**
     * Draw box (local coordinates)
     */
    protected void drawBox(TerminalBatchBuilder batch, int x, int y, int width, int height, 
        String title, Position titlePos, LineStyle boxStyle, TextStyle textStyle
    ) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;
        
        int absX = toAbsoluteX(x);
        int absY = toAbsoluteY(y);
        
        TerminalRectangle boxRegion = regionPool.obtain();
        boxRegion.set(absX, absY, width, height, 0, 0);
        
        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();
        
        if (clip != null) {
            if (!boxRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(boxRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.drawBox(boxRegion, renderRegion, title, titlePos, boxStyle, textStyle);
        } else {
            batch.drawBox(boxRegion, null, title, titlePos, boxStyle, textStyle);
        }
        
        regionPool.recycle(boxRegion);
        regionPool.recycle(renderRegion);
    }
    protected void drawBox(TerminalBatchBuilder batch, TerminalRectangle region, String title, 
                        Position titlePos, LineStyle boxStyle) {
        drawBox(batch, region.getX(), region.getY(), region.getWidth(), region.getHeight(), title, titlePos, boxStyle, null);
    }

    protected void drawBox(TerminalBatchBuilder batch, int x, int y, int width, int height, LineStyle boxStyle, TextStyle textStyle) {
        drawBox(batch, x, y, width, height,null, null, boxStyle, textStyle);
    }

    protected void drawBox(TerminalBatchBuilder batch, int x, int y, int width, int height, LineStyle boxStyle) {
        drawBox(batch, x, y, width, height,null, null, boxStyle, null);
    }

    /**
     * Draw box using TerminalRectangle (local coordinates)
     */
    protected void drawBox(TerminalBatchBuilder batch, TerminalRectangle region, String title,Position titlePosition,  LineStyle boxStyle, TextStyle textStyle) {
         drawBox(batch, region.getX(), region.getY(), region.getWidth(), region.getHeight(),title, titlePosition, boxStyle, textStyle);

    }
    
    protected void drawBox(TerminalBatchBuilder batch, TerminalRectangle region, LineStyle boxStyle) {
        drawBox(batch, region, null,null, boxStyle, null);
    }

    /**
     * Draw table border (local coordinates) with clipping.
     */
    protected void drawTableBorder(
        TerminalBatchBuilder batch,
        int x,
        int y,
        int width,
        int height,
        LineStyle boxStyle,
        TextStyle textStyle,
        int[] hSeparators,
        int[] vSeparators,
        String title,
        Position titlePos
    ) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;

        int absX = toAbsoluteX(x);
        int absY = toAbsoluteY(y);

        int[] absHSeps = hSeparators != null ? translateSeparators(hSeparators, getAbsoluteY()) : null;
        int[] absVSeps = vSeparators != null  ? translateSeparators(vSeparators, getAbsoluteX()) : null;

        TerminalRectangle boxRegion = regionPool.obtain();
        boxRegion.set(absX, absY, width, height, 0, 0);

        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();

        if (clip != null) {
            if (!boxRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(boxRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.drawTableBorder(
                boxRegion, renderRegion, boxStyle, textStyle,
                absHSeps, absVSeps, title, titlePos
            );
        } else {
            batch.drawTableBorder(
                boxRegion, null, boxStyle, textStyle,
                absHSeps, absVSeps, title, titlePos
            );
        }

        regionPool.recycle(boxRegion);
        regionPool.recycle(renderRegion);
    }

    protected void drawTableBorder(
        TerminalBatchBuilder batch,
        TerminalRectangle region,
        LineStyle boxStyle,
        TextStyle textStyle,
        int[] hSeparators,
        int[] vSeparators,
        String title,
        Position titlePos
    ) {
        drawTableBorder(batch,
            region.getX(), region.getY(), region.getWidth(), region.getHeight(),
            boxStyle, textStyle, hSeparators, vSeparators, title, titlePos
        );
    }

    protected void drawTableRowBorder(
        TerminalBatchBuilder batch,
        int x,
        int y,
        int width,
        int height,
        LineStyle boxStyle,
        TextStyle textStyle,
        int... hSeparators
    ) {
        drawTableBorder(batch, x, y, width, height, boxStyle, textStyle, hSeparators, null, null, null);
    }

    protected void drawTableColBorder(
        TerminalBatchBuilder batch,
        int x,
        int y,
        int width,
        int height,
        LineStyle boxStyle,
        TextStyle textStyle,
        int... vSeparators
    ) {
        drawTableBorder(batch, x, y, width, height, boxStyle, textStyle, null, vSeparators, null, null);
    }

    private static int[] translateSeparators(int[] seps, int offset) {
        if (seps == null || seps.length == 0) return seps;
        int[] out = new int[seps.length];
        for (int i = 0; i < seps.length; i++) out[i] = seps[i] + offset;
        return out;
    }
    
    /**
     * Draw horizontal line (local coordinates)
     */
    protected void drawHLine(TerminalBatchBuilder batch, int x, int y, int length, LineStyle lineStyle, TextStyle style) {
        if (!isEffectivelyVisible() || length <= 0 || y < 0 || y >= getHeight()) return;

        int absX = toAbsoluteX(x);
        int absY = toAbsoluteY(y);

        TerminalRectangle lineRegion = regionPool.obtain();
        lineRegion.set(absX, absY, length, 1, 0, 0);

        TerminalRectangle clip = batch.getCurrentClipRegion();
        if (clip != null) {
            TerminalRectangle renderRegion = regionPool.obtain();
            if (!lineRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(lineRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            // renderRegion is the clipped 1-row region — emit as x/y/length
            batch.drawHLine(renderRegion.getX(), renderRegion.getY(),
                            renderRegion.getWidth(), style, lineStyle);
            regionPool.recycle(renderRegion);
        } else {
            batch.drawHLine(absX, absY, length, style, lineStyle);
        }
        regionPool.recycle(lineRegion);
    }
    
    /**
     * Draw vertical line (local coordinates)
     */
    protected void drawVLine(TerminalBatchBuilder batch, int x, int y, int length, LineStyle lineStyle, TextStyle style) {
        if (!isEffectivelyVisible() || length <= 0 || x < 0 || x >= getWidth()) return;
        
        int absX = toAbsoluteX(x);
        int absY = toAbsoluteY(Math.max(0, y));    
        
        TerminalRectangle lineRegion = regionPool.obtain();
        lineRegion.set(absX, absY, 1, length, 0, 0);

        TerminalRectangle clip = batch.getCurrentClipRegion();
        if (clip != null) {
            TerminalRectangle renderRegion = regionPool.obtain();
            if (!lineRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(lineRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            // renderRegion is the clipped 1-column region — emit as x/y/length
            // NOTE: use getHeight(), not getWidth().  lineRegion is (1 × length),
            // so after intersection the clipped length is renderRegion.getHeight().
            // getWidth() is always 1 and would render only a single character.
            batch.drawVLine(renderRegion.getX(), renderRegion.getY(),
                            renderRegion.getHeight(), style, lineStyle);
            regionPool.recycle(renderRegion);
        } else {
            batch.drawVLine(absX, absY, length, style, lineStyle);
        }
        regionPool.recycle(lineRegion);
        
    }
    
    /**
     * Fill region with character (local coordinates)
     */
    protected void fillRegion(TerminalBatchBuilder batch, int x, int y, int width, int height, 
                         int fillChar, TextStyle style) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;

        int absX = toAbsoluteX(x);
        int absY = toAbsoluteY(y);
        
        TerminalRectangle fillRegion = regionPool.obtain();
        fillRegion.set(absX, absY, width, height, 0, 0);
        
        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();
        
        if (clip != null) {
            if (!fillRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(fillRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.fillRegion(fillRegion, renderRegion, fillChar, style);
        } else {
            batch.fillRegion(fillRegion, null, fillChar, style);
        }
        
        regionPool.recycle(fillRegion);
        regionPool.recycle(renderRegion);
    }


    
    /**
     * Fill region with character using TerminalRectangle (local coordinates)
     */
    protected void fillRegion(TerminalBatchBuilder batch, TerminalRectangle region, int fillChar, TextStyle style) {
        fillRegion(batch, region.getX(), region.getY(), region.getWidth(), region.getHeight(), fillChar, style);
    }
    
    // ===== SPARKLINE =====

    protected void drawSparkline(
        TerminalBatchBuilder batch,
        int x, int y, int width, int height,
        double[] values, TextStyle style, TextStyle peakStyle
    ) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;

        TerminalRectangle sparkRegion = regionPool.obtain();
        sparkRegion.set(toAbsoluteX(x), toAbsoluteY(y), width, height, 0, 0);

        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();

        if (clip != null) {
            if (!sparkRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(sparkRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.drawSparkline(
                sparkRegion, renderRegion, values, style, peakStyle);
        } else {
            batch.drawSparkline(
                sparkRegion, null, values, style, peakStyle);
        }

        regionPool.recycle(sparkRegion);
        regionPool.recycle(renderRegion);
    }

    protected void drawSparkline(
        TerminalBatchBuilder batch, TerminalRectangle region,
        double[] values, TextStyle style, TextStyle peakStyle
    ) {
        drawSparkline(batch,
            region.getX(), region.getY(), region.getWidth(), region.getHeight(),
            values, style, peakStyle);
    }

    protected void drawSparkline(
        TerminalBatchBuilder batch, TerminalRectangle region, double[] values
    ) {
        drawSparkline(batch, region, values, null, null);
    }

    // ===== SCROLLBAR =====

    protected void drawScrollbar(
        TerminalBatchBuilder batch,
        int x, int y, int width, int height,
        int scrollPos, int totalItems, int visibleItems,
        boolean showArrows, TextStyle trackStyle, TextStyle thumbStyle
    ) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;

        TerminalRectangle scrollRegion = regionPool.obtain();
        scrollRegion.set(toAbsoluteX(x), toAbsoluteY(y), width, height, 0, 0);

        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();

        if (clip != null) {
            if (!scrollRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(scrollRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.drawScrollbar(
                scrollRegion, renderRegion,
                scrollPos, totalItems, visibleItems,
                showArrows, trackStyle, thumbStyle);
        } else {
            batch.drawScrollbar(
                scrollRegion, null,
                scrollPos, totalItems, visibleItems,
                showArrows, trackStyle, thumbStyle);
        }

        regionPool.recycle(scrollRegion);
        regionPool.recycle(renderRegion);
    }

    protected void drawScrollbar(
        TerminalBatchBuilder batch, TerminalRectangle region,
        int scrollPos, int totalItems, int visibleItems,
        boolean showArrows, TextStyle trackStyle, TextStyle thumbStyle
    ) {
        drawScrollbar(batch,
            region.getX(), region.getY(), region.getWidth(), region.getHeight(),
            scrollPos, totalItems, visibleItems, showArrows, trackStyle, thumbStyle);
    }

    // ===== QUADRANT BITMAP (2×2 sub-pixels) =====

    protected void drawBitmap(
        TerminalBatchBuilder batch,
        int x, int y, int width, int height,
        int pixelWidth, int pixelHeight,
        byte[] pixels, TextStyle style
    ) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;

        TerminalRectangle bitmapRegion = regionPool.obtain();
        bitmapRegion.set(toAbsoluteX(x), toAbsoluteY(y), width, height, 0, 0);

        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();

        if (clip != null) {
            if (!bitmapRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(bitmapRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.drawBitmap(
                bitmapRegion, renderRegion, pixelWidth, pixelHeight, pixels, style);
        } else {
            batch.drawBitmap(
                bitmapRegion, null, pixelWidth, pixelHeight, pixels, style);
        }

        regionPool.recycle(bitmapRegion);
        regionPool.recycle(renderRegion);
    }

    protected void drawBitmap(
        TerminalBatchBuilder batch, TerminalRectangle region,
        int pixelWidth, int pixelHeight,
        byte[] pixels, TextStyle style
    ) {
        drawBitmap(batch,
            region.getX(), region.getY(), region.getWidth(), region.getHeight(),
            pixelWidth, pixelHeight, pixels, style);
    }

    // ===== BRAILLE BITMAP (2×4 sub-pixels) =====

    protected void drawBrailleBitmap(
        TerminalBatchBuilder batch,
        int x, int y, int width, int height,
        int pixelWidth, int pixelHeight,
        byte[] pixels, TextStyle style
    ) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;

        TerminalRectangle brailleRegion = regionPool.obtain();
        brailleRegion.set(toAbsoluteX(x), toAbsoluteY(y), width, height, 0, 0);

        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();

        if (clip != null) {
            if (!brailleRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(brailleRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.drawBrailleBitmap(
                brailleRegion, renderRegion, pixelWidth, pixelHeight, pixels, style);
        } else {
            batch.drawBrailleBitmap(
                brailleRegion, null, pixelWidth, pixelHeight, pixels, style);
        }

        regionPool.recycle(brailleRegion);
        regionPool.recycle(renderRegion);
    }

    protected void drawBrailleBitmap(
        TerminalBatchBuilder batch, TerminalRectangle region,
        int pixelWidth, int pixelHeight,
        byte[] pixels, TextStyle style
    ) {
        drawBrailleBitmap(batch,
            region.getX(), region.getY(), region.getWidth(), region.getHeight(),
            pixelWidth, pixelHeight, pixels, style);
    }

    // ===== SEXTANT BITMAP (2×3 sub-pixels) =====
    //
    // Width-safety note: sextant glyphs (U+1FB00–U+1FB3B) may render as double-width
    // in some terminals. The two special-case masks are handled by ConsoleContainer:
    //   BLANK_MASK (0b000000)  → transparent cell, left unchanged
    //   FULL_MASK  (0b111111)  → background-filled space, no glyph emitted
    // Avoid mixing 0b010101 (▌) and 0b101010 (▐) into sextant grids — they are
    // single-width block-element characters and will break column alignment.

    protected void drawSextantBitmap(
        TerminalBatchBuilder batch,
        int x, int y, int width, int height,
        int pixelWidth, int pixelHeight,
        byte[] pixels, TextStyle style
    ) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;

        TerminalRectangle sextantRegion = regionPool.obtain();
        sextantRegion.set(toAbsoluteX(x), toAbsoluteY(y), width, height, 0, 0);

        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();

        if (clip != null) {
            if (!sextantRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(sextantRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.drawSextantBitmap(
                sextantRegion, renderRegion, pixelWidth, pixelHeight, pixels, style);
        } else {
            batch.drawSextantBitmap(
                sextantRegion, null, pixelWidth, pixelHeight, pixels, style);
        }

        regionPool.recycle(sextantRegion);
        regionPool.recycle(renderRegion);
    }

    protected void drawSextantBitmap(
        TerminalBatchBuilder batch, TerminalRectangle region,
        int pixelWidth, int pixelHeight,
        byte[] pixels, TextStyle style
    ) {
        drawSextantBitmap(batch,
            region.getX(), region.getY(), region.getWidth(), region.getHeight(),
            pixelWidth, pixelHeight, pixels, style);
    }
    
    /**
     * Move cursor (local coordinates)
     * Cursor is clamped to bounds if clampCursor is true
     */
    protected void moveCursor(TerminalBatchBuilder batch, int x, int y) {
        // Only the focused component should move the cursor — a non-focused component
        // repositioning the cursor would corrupt the terminal state for the focused one.
        if (!hasFocus()) return;
        if (clampCursor) { x = clampX(x); y = clampY(y); }
        x = Math.max(0, Math.min(x, getWidth() - 1));
        y = Math.max(0, Math.min(y, getHeight() - 1));
        batch.moveCursor(toAbsoluteX(x), toAbsoluteY(y));
    }

    /**
     * Push clip region for nested clipping (local coordinates)
     */
    protected void pushClipRegion(TerminalBatchBuilder batch, int x, int y, int width, int height) {
        TerminalRectangle region = regionPool.obtain();
        region.set(toAbsoluteX(x), toAbsoluteY(y), width, height, 0, 0);
        TerminalRectangle currentClip = batch.getCurrentClipRegion();
        if (currentClip != null) {
            TerminalRectangle clipped = regionPool.obtain();
            if (region.intersect(currentClip, clipped)) {
                regionPool.recycle(region);
                region = clipped;
            } else {
                regionPool.recycle(clipped);
                region.setToIdentity();
            }
        }
        batch.pushClipRegion(region);
    }

    /**
     * Pop clip region
     */
    protected void popClipRegion(TerminalBatchBuilder batch) {
        TerminalRectangle popped = batch.popClipRegion();
        if (popped != null) {
            regionPool.recycle(popped);
        }
    }
    
    // ===== NON-POSITIONED OPERATIONS (pass-through) =====
    // These operations are not position-based and don't need boundary enforcement
    
    protected void print(TerminalBatchBuilder batch, String text) {
        batch.print(text);
    }
    
    protected void print(TerminalBatchBuilder batch, String text, TextStyle style) {
        batch.print(text, style);
    }
    
    protected void println(TerminalBatchBuilder batch, String text) {
        batch.println(text);
    }
    
    protected void println(TerminalBatchBuilder batch, String text, TextStyle style) {
        batch.println(text, style);
    }
    
    protected void showCursor(TerminalBatchBuilder batch) {
        if (!hasFocus()) return;
        batch.showCursor();
    }
        
    protected void hideCursor(TerminalBatchBuilder batch) {
        batch.hideCursor();
    }
    

    protected void clearLine(TerminalBatchBuilder batch) {
        clearRegion(batch, 0, 0, getWidth(), 1);
    }

    /**
     * Draw bordered text box (local coordinates)
     * Combines box drawing with centered text
     */
    protected void drawBorderedText(TerminalBatchBuilder batch, int x, int y, int width, int height,
        String text, Position textPos, LineStyle boxStyle, 
        TextStyle textStyle, TextStyle borderStyle
    ) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;
        
        int absX = toAbsoluteX(x);
        int absY = toAbsoluteY(y);
        
        TerminalRectangle textRegion = regionPool.obtain();
        textRegion.set(absX, absY, width, height, 0, 0);
        
        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();
        
        if (clip != null) {
            if (!textRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(textRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.drawBorderedText(textRegion, renderRegion, text, textPos, boxStyle, textStyle, borderStyle);
        } else {
            batch.drawBorderedText(textRegion, null, text, textPos, boxStyle, textStyle, borderStyle);
        }
        
        regionPool.recycle(textRegion);
        regionPool.recycle(renderRegion);
    }

    protected void drawBorderedText(TerminalBatchBuilder batch, TerminalRectangle region, String text,
                                    LineStyle boxStyle, TextStyle textStyle, TextStyle borderStyle) {
        drawBorderedText(batch, region.getX(), region.getY(), region.getWidth(), region.getHeight(),
                        text,null, boxStyle, textStyle, borderStyle);
    }

    protected void drawBorderedText(TerminalBatchBuilder batch, TerminalRectangle region, String text, LineStyle boxStyle) {
        drawBorderedText(batch, region, text, boxStyle, null, null);
    }

    /**
     * Draw panel - box with filled background (local coordinates)
     */
   protected void drawPanel(TerminalBatchBuilder batch, int x, int y, int width, int height,
        String title, Position titlePos, LineStyle boxStyle, 
        TextStyle borderStyle, TextStyle fillStyle
    ) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;
        
        int absX = toAbsoluteX(x);
        int absY = toAbsoluteY(y);
        
        TerminalRectangle panelRegion = regionPool.obtain();
        panelRegion.set(absX, absY, width, height, 0, 0);
        
        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();
        
        if (clip != null) {
            if (!panelRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(panelRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.drawPanel(panelRegion, renderRegion, title, titlePos, boxStyle, borderStyle, fillStyle);
        } else {
            batch.drawPanel(panelRegion, null, title, titlePos, boxStyle, borderStyle, fillStyle);
        }
        
        regionPool.recycle(panelRegion);
        regionPool.recycle(renderRegion);
    }


    protected void drawPanel(TerminalBatchBuilder batch, TerminalRectangle region, String title, LineStyle boxStyle, TextStyle borderStyle, TextStyle fillStyle) {
        drawPanel(batch, region.getX(), region.getY(), region.getWidth(), region.getHeight(), title, null, boxStyle, borderStyle, fillStyle);
    }

    protected void drawPanel(TerminalBatchBuilder batch, TerminalRectangle region, LineStyle boxStyle) {
        drawPanel(batch, region, null, boxStyle, TextStyle.NORMAL, TextStyle.NORMAL);
    }

    /**
     * Draw button component (local coordinates)
     */
    protected void drawButton(TerminalBatchBuilder batch, int x, int y, int width, int height,
                         String label, Position labelPos, boolean selected, TextStyle style) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;
        
        int absX = toAbsoluteX(x);
        int absY = toAbsoluteY(y);
        
        TerminalRectangle buttonRegion = regionPool.obtain();
        buttonRegion.set(absX, absY, width, height, 0, 0);
        
        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();
        
        if (clip != null) {
            if (!buttonRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(buttonRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.drawButton(buttonRegion, renderRegion, label, labelPos, selected, style);
        } else {
            batch.drawButton(buttonRegion, null, label, labelPos, selected, style);
        }
        
        regionPool.recycle(buttonRegion);
        regionPool.recycle(renderRegion);
    }

    protected void drawButton(TerminalBatchBuilder batch, TerminalRectangle region, String label,
        boolean selected, TextStyle style
    ) {
        drawButton(batch, region.getX(), region.getY(), region.getWidth(), region.getHeight(),
                label,null, selected, style);
    }

    protected void drawButton(TerminalBatchBuilder batch, TerminalRectangle region, String label, boolean selected) {
        drawButton(batch, region, label, selected, TextStyle.NORMAL);
    }

    /**
     * Draw progress bar (local coordinates)
     */
    protected void drawProgressBar(TerminalBatchBuilder batch, int x, int y, int width, int height,
        double progress, TextStyle style, TextStyle emptyStyle
    ) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;
        
        int absX = toAbsoluteX(x);
        int absY = toAbsoluteY(y);
        
        TerminalRectangle barRegion = regionPool.obtain();
        barRegion.set(absX, absY, width, height, 0, 0);
        
        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();
        
        if (clip != null) {
            if (!barRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(barRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.drawProgressBar(barRegion, renderRegion, progress, style, emptyStyle);
        } else {
            batch.drawProgressBar(barRegion, null, progress, style, emptyStyle);
        }
        
        regionPool.recycle(barRegion);
        regionPool.recycle(renderRegion);
    }

    protected void drawProgressBar(TerminalBatchBuilder batch, TerminalRectangle region, double progress,
                                TextStyle style, TextStyle emptyStyle) {
        drawProgressBar(batch, region.getX(), region.getY(), region.getWidth(), region.getHeight(),
                    progress, style, emptyStyle);
    }

    protected void drawProgressBar(TerminalBatchBuilder batch, TerminalRectangle region, double progress) {
        drawProgressBar(batch, region, progress, TextStyle.NORMAL, TextStyle.NORMAL);
    }

    /**
     * Draw text block with word wrapping (local coordinates)
     */
    protected void drawTextBlock(TerminalBatchBuilder batch, int x, int y, int width, int height,
        String text, TextAlignment align, TextStyle style
    ) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;
        
        int absX = toAbsoluteX(x);
        int absY = toAbsoluteY(y);
        
        TerminalRectangle blockRegion = regionPool.obtain();
        blockRegion.set(absX, absY, width, height, 0, 0);
        
        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();
        
        if (clip != null) {
            if (!blockRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(blockRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.drawTextBlock(blockRegion, renderRegion, text, align, style);
        } else {
            batch.drawTextBlock(blockRegion, null, text, align, style);
        }
        
        regionPool.recycle(blockRegion);
        regionPool.recycle(renderRegion);
    }

    protected void drawTextBlock(TerminalBatchBuilder batch, TerminalRectangle region, String text,
                               TextAlignment align, TextStyle style) {
        drawTextBlock(batch, region.getX(), region.getY(), region.getWidth(), region.getHeight(),
                    text, align, style);
    }

    protected void drawTextBlock(TerminalBatchBuilder batch, TerminalRectangle region, String text,
                                TextAlignment align) {
        drawTextBlock(batch, region, text, align, TextStyle.NORMAL);
    }

    protected void drawTextBlock(TerminalBatchBuilder batch, TerminalRectangle region, String text) {
        drawTextBlock(batch, region, text, TextAlignment.LEFT, TextStyle.NORMAL);
    }

    /**
     * Shade region with character pattern (local coordinates)
     */
    protected void shadeRegion(TerminalBatchBuilder batch, int x, int y, int width, int height,
        char shadeChar, TextStyle style
    ) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;
        
        int absX = toAbsoluteX(x);
        int absY = toAbsoluteY(y);
        
        TerminalRectangle shadeRegion = regionPool.obtain();
        shadeRegion.set(absX, absY, width, height, 0, 0);
        
        TerminalRectangle renderRegion = regionPool.obtain();
        TerminalRectangle clip = batch.getCurrentClipRegion();
        
        if (clip != null) {
            if (!shadeRegion.intersect(clip, renderRegion)) {
                regionPool.recycle(shadeRegion);
                regionPool.recycle(renderRegion);
                return;
            }
            batch.shadeRegion(shadeRegion, renderRegion, shadeChar, style);
        } else {
            batch.shadeRegion(shadeRegion, null, shadeChar, style);
        }
        
        regionPool.recycle(shadeRegion);
        regionPool.recycle(renderRegion);
    }

    protected void shadeRegion(TerminalBatchBuilder batch, TerminalRectangle region, char shadeChar, TextStyle style) {
        shadeRegion(batch, region.getX(), region.getY(), region.getWidth(), region.getHeight(),
                shadeChar, style);
    }

    protected void shadeRegion(TerminalBatchBuilder batch, TerminalRectangle region, char shadeChar) {
        shadeRegion(batch, region, shadeChar, TextStyle.NORMAL);
    }

    // ===== CENTERED PRINTING HELPERS (using TerminalCommands helper methods) =====

    /**
     * Print text centered vertically within bounds (local coordinates)
     */
    protected void printAtCenterY(TerminalBatchBuilder batch, int x, String text, TextStyle style) {
        int y = getCenterYOffset();
        printAt(batch, x, y, text, style);
    }

    protected void printAtCenterY(TerminalBatchBuilder batch, int x, String text) {
        printAtCenterY(batch, x, text, TextStyle.NORMAL);
    }

    /**
     * Print text centered horizontally within bounds (local coordinates)
     */
    protected void printAtCenterX(TerminalBatchBuilder batch, int y, String text, TextStyle style) {
        int halfText = text.length() / 2;
        int centerX = getCenterXOffset() - halfText;
        printAt(batch, Math.max(0, centerX), y, text, style);
    }

    protected void printAtCenterX(TerminalBatchBuilder batch, int y, String text) {
        printAtCenterX(batch, y, text, TextStyle.NORMAL);
    }

    /**
     * Print text centered both horizontally and vertically (local coordinates)
     */
    protected void printAtCenter(TerminalBatchBuilder batch, String text, TextStyle style) {
        int halfText = text.length() / 2;
        int centerX = getCenterXOffset() - halfText;
        int centerY = getCenterYOffset();
        printAt(batch, Math.max(0, centerX), centerY, text, style);
    }

    protected void printAtCenter(TerminalBatchBuilder batch, String text) {
        printAtCenter(batch, text, TextStyle.NORMAL);
    }

    protected void printAtPosition(TerminalBatchBuilder builder, String text, Position pos, TextStyle style){
        int[] coords = calculateLocalPosition(text, pos);
        printAt(builder, coords[0], coords[1], text, style);
    }

    public int[] calculateLocalPosition(String text, Position pos) {
        int textLen = text.length();
        int x = 0;
        int y = 0;
        int width = getWidth();
        int height = getHeight();
        
        // Horizontal
        switch (pos) {
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> 
                x = ((width / 2) - (textLen / 2));
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> 
                x = width - textLen;
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> 
                x = 0;
        }
        
        // Vertical
        switch (pos) {
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> 
                y = (height / 2);
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> 
                y = height - 1;
            case TOP_CENTER, TOP_LEFT, TOP_RIGHT-> 
                y = 0;
        }
        
        return new int[] { Math.max(0, x), Math.max(0, y) };
    }
   
    
    
    /**
     * Subclasses implement to render their 2D content
     * Called with clip region already set in batch if using CLIP_REGION mode
     * 
     * The batch's coordinate system is in absolute screen space
     * Use the provided rendering methods which handle coordinate translation
     * 
     * All rendering coordinates are LOCAL to this renderable (0,0 = top-left of component)
     */
    @Override
    protected void renderSelf(TerminalBatchBuilder batch){}

    public class TerminalGroupStateEntry extends Renderable.GroupStateEntry<
        TerminalRenderable,
        TerminalLayoutGroupCallback,
        TerminalGroupStateEntry
    >{}
}
