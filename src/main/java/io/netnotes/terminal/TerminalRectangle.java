package io.netnotes.terminal;
import java.util.Objects;

import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.ui.Point2D;
import io.netnotes.engine.ui.SpatialRegion;

/**
 * 2D rectangular region
 */
public class TerminalRectangle extends SpatialRegion<
    Point2D, 
    TerminalRectangle
> {

    // Constants for serialization
    public static final NoteBytesReadOnly PARENT_ABS_X = 
        new NoteBytesReadOnly("parent_abs_x");
    public static final NoteBytesReadOnly PARENT_ABS_Y = 
        new NoteBytesReadOnly("parent_abs_y");

    private int parentAbsoluteX = 0;
    private int parentAbsoluteY = 0;
    
    private int x;
    private int y;
    private int width;
    private int height;
    
    /**
     * Default constructor - creates empty rectangle at origin
     */
    public TerminalRectangle() {
        this(0, 0, 0, 0);
    }
    
    /**
     * Constructor with bounds
     */
    public TerminalRectangle(int x, int y, int width, int height) {
        this(x, y, width, height, 0, 0);
    }


    
    public TerminalRectangle(int x, int y, int width, int height, int parentAbsoluteX, int parentAbsoluteY) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.parentAbsoluteX = parentAbsoluteX;
        this.parentAbsoluteY = parentAbsoluteY;
    }
    
    // ===== SPATIAL REGION INTERFACE =====
    
    @Override
    public void setToIdentity() {
        x = 0;
        y = 0;
        width = 0;
        height = 0;
        parentAbsoluteX = 0;
        parentAbsoluteY = 0;
    }
    
    @Override
    public void collapse() {
        // Hdden means zero size, preserve position
        width = 0;
        height = 0;
    }
    
    @Override
    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }
    
    @Override
    public void copyFrom(TerminalRectangle other) {
        this.x = other.x;
        this.y = other.y;
        this.width = other.width;
        this.height = other.height;
        this.parentAbsoluteX = other.parentAbsoluteX;
        this.parentAbsoluteY = other.parentAbsoluteY;
    }
    

    public int getAbsoluteX() {
        return parentAbsoluteX + x;
    }
    
    public int getAbsoluteY() {
        return parentAbsoluteY + y;
    }


    public int getAbsoluteRight() {
        return getAbsoluteX() + width;
    }
    
    public int getAbsoluteBottom() {
        return getAbsoluteY() + height;
    }

    @Override
    public boolean equals(TerminalRectangle other) {
        if (other == null) return false;
        return this.x == other.x &&
            this.y == other.y &&
            this.width == other.width &&
            this.height == other.height;
    }

    public boolean absEquals(TerminalRectangle other) {
        if (other == null) return false;
        return this.x == other.x &&
            this.y == other.y &&
            this.width == other.width &&
            this.height == other.height &&
            this.parentAbsoluteX == other.parentAbsoluteX &&
            this.parentAbsoluteY == other.parentAbsoluteY;
    }
    
    @Override
    public boolean containsPoint(Point2D point) {
        if (point == null) return false;
        return contains(point.getX(), point.getY());
    }
    
    @Override
    public boolean contains(TerminalRectangle other) {
        if (other == null || other.isEmpty()) return false;
        return other.x >= this.x &&
               other.y >= this.y &&
               other.x + other.width <= this.x + this.width &&
               other.y + other.height <= this.y + this.height;
    }
    
    @Override
    public boolean intersects(TerminalRectangle other) {
        if (other == null) return false;
        return !(other.x >= this.x + this.width ||
                 other.x + other.width <= this.x ||
                 other.y >= this.y + this.height ||
                 other.y + other.height <= this.y);
    }

    /**
 * Calculate intersection with another rectangle
 * @param other rectangle to intersect with
 * @param out output rectangle to store result (reused for efficiency)
 * @return true if rectangles intersect, false if no overlap
 */
public boolean intersect(TerminalRectangle other, TerminalRectangle out) {
    int left = Math.max(this.x, other.x);
    int top = Math.max(this.y, other.y);
    int right = Math.min(this.x + this.width, other.x + other.width);
    int bottom = Math.min(this.y + this.height, other.y + other.height);
    
    if (right <= left || bottom <= top) {
        return false;
    }
    
    out.set(left, top, right - left, bottom - top, 0, 0);
    return true;
}
    
    @Override
    public void transformByParent(TerminalRectangle parentRegion) {
        this.x += parentRegion.x;
        this.y += parentRegion.y;
    }
    
    @Override
    public TerminalRectangle intersection(TerminalRectangle other) {
        if (!intersects(other)) {
            TerminalRectangle empty = TerminalRectanglePool.getInstance().obtain();
            empty.set(0, 0, 0, 0);
            return empty;
        }
        int x1 = Math.max(this.x, other.x);
        int y1 = Math.max(this.y, other.y);
        int x2 = Math.min(this.x + this.width, other.x + other.width);
        int y2 = Math.min(this.y + this.height, other.y + other.height);
        TerminalRectangle intersection = TerminalRectanglePool.getInstance().obtain();
        intersection.set(x1, y1, x2 - x1, y2 - y1);
        return intersection;
    }
    
    @Override
    public void intersectInPlace(TerminalRectangle other) {
        if (!intersects(other)) {
            setToIdentity();
            return;
        }
        
        int x1 = Math.max(this.x, other.x);
        int y1 = Math.max(this.y, other.y);
        int x2 = Math.min(this.x + this.width, other.x + other.width);
        int y2 = Math.min(this.y + this.height, other.y + other.height);
        
        this.x = x1;
        this.y = y1;
        this.width = x2 - x1;
        this.height = y2 - y1;
    }
    
    @Override
    public void unionInPlace(TerminalRectangle other) {
        if (other == null || other.isEmpty()) return;
        
        if (this.isEmpty()) {
            copyFrom(other);
            return;
        }
        
        int x1 = Math.min(this.x, other.x);
        int y1 = Math.min(this.y, other.y);
        int x2 = Math.max(this.x + this.width, other.x + other.width);
        int y2 = Math.max(this.y + this.height, other.y + other.height);
        
        this.x = x1;
        this.y = y1;
        this.width = x2 - x1;
        this.height = y2 - y1;
    }
    
    // ===== CONVENIENCE METHODS =====
    
    /**
     * Get X coordinate (left edge)
     */
    public int getX() {
        return x;
    }
    
    /**
     * Get Y coordinate (top edge)
     */
    public int getY() {
        return y;
    }
    
    public Point2D getPosition(){
        return new Point2D(getX(), getY());
    }
    
    /**
     * Get width
     */
    public int getWidth() {
        return width;
    }
    
    /**
     * Get height
     */
    public int getHeight() {
        return height;
    }
    
    /**
     * Get right edge (x + width)
     */
    public int getRight() {
        return x + width;
    }
    
    /**
     * Get bottom edge (y + height)
     */
    public int getBottom() {
        return y + height;
    }
    
    /**
     * Get center X
     */
    public int getCenterX() {
        return x + width / 2;
    }
    
    /**
     * Get center Y
     */
    public int getCenterY() {
        return y + height / 2;
    }
    
    // ===== LOCAL COORDINATE HELPERS =====
    // These methods return coordinates in local (0-based) space,
    // useful for rendering operations within a component
    
    /**
     * Get local center X offset (0-based within bounds)
     * Use this for rendering calculations that will be translated to absolute coordinates.
     * 
     * @return Center X in local coordinate space (width/2)
     */
    public int getLocalCenterX() {
        return width / 2;
    }
    
    /**
     * Get local center Y offset (0-based within bounds)
     * Use this for rendering calculations that will be translated to absolute coordinates.
     * 
     * @return Center Y in local coordinate space (height/2)
     */
    public int getLocalCenterY() {
        return height / 2;
    }
    
    /**
     * Get local right edge (equals width in 0-based coordinates)
     * 
     * @return Right edge in local coordinate space (width)
     */
    public int getLocalRight() {
        return width;
    }
    
    /**
     * Get local bottom edge (equals height in 0-based coordinates)
     * 
     * @return Bottom edge in local coordinate space (height)
     */
    public int getLocalBottom() {
        return height;
    }
    
    /**
     * Get local center point (0-based within bounds)
     * 
     * @return Center point in local coordinate space
     */
    public Point2D getLocalCenter() {
        return new Point2D(width / 2, height / 2);
    }
    
    // ===== ORIGINAL PARENT-RELATIVE HELPERS =====
    
    /**
     * Set X coordinate
     */
    public void setX(int x) {
        this.x = x;
    }
    
    /**
     * Set Y coordinate
     */
    public void setY(int y) {
        this.y = y;
    }
    
    /**
     * Set position
     */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }


    @Override
    public void setPosition(Point2D point) {
        this.x = point.getX();
        this.y = point.getY();
    }
    
    /**
     * Set width
     */
    public void setWidth(int width) {
        this.width = Math.max(0, width);
    }
    
    /**
     * Set height
     */
    public void setHeight(int height) {
        this.height = Math.max(0, height);
    }
    
    /**
     * Set size
     */
    public void setSize(int width, int height) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }
    
    /**
     * Set complete bounds
     */
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }
    
    /**
     * Translate by offset
     */
    public void translate(int dx, int dy) {
        this.x += dx;
        this.y += dy;
    }
    
    /**
     * Expand outward by amount (negative to shrink)
     * Expands in all directions from center
     */
    public void expand(int dx, int dy) {
        this.x -= dx;
        this.y -= dy;
        this.width += 2 * dx;
        this.height += 2 * dy;
        
        // Clamp to non-negative
        if (this.width < 0) {
            this.x += this.width / 2;
            this.width = 0;
        }
        if (this.height < 0) {
            this.y += this.height / 2;
            this.height = 0;
        }
    }

    public void expand(Point2D dimensionXY) {
        expand(dimensionXY.getX(), dimensionXY.getY());
    }

    /**
     * Create an inflated rectangle (expanded from center).
     * Returns a pooled instance.
     */
    public TerminalRectangle inflate(int dx, int dy) {
        return inflateClamped(dx, dy);
    }

    /**
     * Create a deflated rectangle (shrunk from center).
     * Returns a pooled instance.
     */
    public TerminalRectangle deflate(int dx, int dy) {
        return deflateClamped(dx, dy);
    }

    /**
     * Create an inflated rectangle using per-side insets.
     * Returns a pooled instance.
     */
    public TerminalRectangle inflate(TerminalInsets insets) {
        return inflateClamped(insets);
    }

    /**
     * Create a deflated rectangle using per-side insets.
     * Returns a pooled instance.
     */
    public TerminalRectangle deflate(TerminalInsets insets) {
        return deflateClamped(insets);
    }

    /**
     * Inflate with clamp-to-zero sizing. If size underflows, position shifts by half overflow.
     * Returns a pooled instance.
     */
    public TerminalRectangle inflateClamped(int dx, int dy) {
        TerminalRectangle inflated = TerminalRectanglePool.getInstance().obtain();
        int nx = this.x - dx;
        int ny = this.y - dy;
        int nw = this.width + 2 * dx;
        int nh = this.height + 2 * dy;

        if (nw < 0) {
            nx += nw / 2;
            nw = 0;
        }
        if (nh < 0) {
            ny += nh / 2;
            nh = 0;
        }

        inflated.set(nx, ny, nw, nh, parentAbsoluteX, parentAbsoluteY);
        return inflated;
    }

    /**
     * Deflate with clamp-to-zero sizing. If size underflows, position shifts by half overflow.
     * Returns a pooled instance.
     */
    public TerminalRectangle deflateClamped(int dx, int dy) {
        return inflateClamped(-dx, -dy);
    }

    /**
     * Inflate with per-side insets, clamped to zero sizing.
     * Returns a pooled instance.
     */
    public TerminalRectangle inflateClamped(TerminalInsets insets) {
        if (insets == null) {
            return copy();
        }

        TerminalRectangle inflated = TerminalRectanglePool.getInstance().obtain();
        int nx = this.x - insets.getLeft();
        int ny = this.y - insets.getTop();
        int nw = this.width + insets.getLeft() + insets.getRight();
        int nh = this.height + insets.getTop() + insets.getBottom();

        if (nw < 0) {
            nx += nw / 2;
            nw = 0;
        }
        if (nh < 0) {
            ny += nh / 2;
            nh = 0;
        }

        inflated.set(nx, ny, nw, nh, parentAbsoluteX, parentAbsoluteY);
        return inflated;
    }

    /**
     * Deflate with per-side insets, clamped to zero sizing.
     * Returns a pooled instance.
     */
    public TerminalRectangle deflateClamped(TerminalInsets insets) {
        if (insets == null) {
            return copy();
        }
        TerminalRectangle deflated = TerminalRectanglePool.getInstance().obtain();
        int nx = this.x + insets.getLeft();
        int ny = this.y + insets.getTop();
        int nw = this.width - (insets.getLeft() + insets.getRight());
        int nh = this.height - (insets.getTop() + insets.getBottom());

        if (nw < 0) {
            nx += nw / 2;
            nw = 0;
        }
        if (nh < 0) {
            ny += nh / 2;
            nh = 0;
        }

        deflated.set(nx, ny, nw, nh, parentAbsoluteX, parentAbsoluteY);
        return deflated;
    }

    /**
     * Inflate while preserving origin (x/y) if size underflows.
     * Returns a pooled instance.
     */
    public TerminalRectangle inflatePreserveOrigin(int dx, int dy) {
        TerminalRectangle inflated = TerminalRectanglePool.getInstance().obtain();
        int nx = this.x - dx;
        int ny = this.y - dy;
        int nw = this.width + 2 * dx;
        int nh = this.height + 2 * dy;

        if (nw < 0) {
            nw = 0;
        }
        if (nh < 0) {
            nh = 0;
        }

        inflated.set(nx, ny, nw, nh, parentAbsoluteX, parentAbsoluteY);
        return inflated;
    }

    /**
     * Deflate while preserving origin (x/y) if size underflows.
     * Returns a pooled instance.
     */
    public TerminalRectangle deflatePreserveOrigin(int dx, int dy) {
        return inflatePreserveOrigin(-dx, -dy);
    }

    /**
     * Inflate while preserving origin (x/y) for per-side insets.
     * Returns a pooled instance.
     */
    public TerminalRectangle inflatePreserveOrigin(TerminalInsets insets) {
        if (insets == null) {
            return copy();
        }

        TerminalRectangle inflated = TerminalRectanglePool.getInstance().obtain();
        int nx = this.x - insets.getLeft();
        int ny = this.y - insets.getTop();
        int nw = this.width + insets.getLeft() + insets.getRight();
        int nh = this.height + insets.getTop() + insets.getBottom();

        if (nw < 0) {
            nw = 0;
        }
        if (nh < 0) {
            nh = 0;
        }

        inflated.set(nx, ny, nw, nh, parentAbsoluteX, parentAbsoluteY);
        return inflated;
    }

    /**
     * Deflate while preserving origin (x/y) for per-side insets.
     * Returns a pooled instance.
     */
    public TerminalRectangle deflatePreserveOrigin(TerminalInsets insets) {
        if (insets == null) {
            return copy();
        }
        TerminalRectangle deflated = TerminalRectanglePool.getInstance().obtain();
        int nx = this.x + insets.getLeft();
        int ny = this.y + insets.getTop();
        int nw = this.width - (insets.getLeft() + insets.getRight());
        int nh = this.height - (insets.getTop() + insets.getBottom());

        if (nw < 0) {
            nw = 0;
        }
        if (nh < 0) {
            nh = 0;
        }

        deflated.set(nx, ny, nw, nh, parentAbsoluteX, parentAbsoluteY);
        return deflated;
    }

    /**
     * Inflate while preserving the rectangle center if size underflows.
     * Returns a pooled instance.
     */
    public TerminalRectangle inflatePreserveCenter(int dx, int dy) {
        TerminalRectangle inflated = TerminalRectanglePool.getInstance().obtain();
        int centerX = this.x + (this.width / 2);
        int centerY = this.y + (this.height / 2);
        int nw = this.width + 2 * dx;
        int nh = this.height + 2 * dy;

        if (nw < 0) {
            nw = 0;
        }
        if (nh < 0) {
            nh = 0;
        }

        int nx = centerX - (nw / 2);
        int ny = centerY - (nh / 2);
        inflated.set(nx, ny, nw, nh, parentAbsoluteX, parentAbsoluteY);
        return inflated;
    }

    /**
     * Deflate while preserving the rectangle center if size underflows.
     * Returns a pooled instance.
     */
    public TerminalRectangle deflatePreserveCenter(int dx, int dy) {
        return inflatePreserveCenter(-dx, -dy);
    }

    /**
     * Inflate while preserving the rectangle center for per-side insets.
     * Returns a pooled instance.
     */
    public TerminalRectangle inflatePreserveCenter(TerminalInsets insets) {
        if (insets == null) {
            return copy();
        }

        TerminalRectangle inflated = TerminalRectanglePool.getInstance().obtain();
        int centerX = this.x + (this.width / 2);
        int centerY = this.y + (this.height / 2);
        int nw = this.width + insets.getLeft() + insets.getRight();
        int nh = this.height + insets.getTop() + insets.getBottom();

        if (nw < 0) {
            nw = 0;
        }
        if (nh < 0) {
            nh = 0;
        }

        int nx = centerX - (nw / 2);
        int ny = centerY - (nh / 2);
        inflated.set(nx, ny, nw, nh, parentAbsoluteX, parentAbsoluteY);
        return inflated;
    }

    /**
     * Deflate while preserving the rectangle center for per-side insets.
     * Returns a pooled instance.
     */
    public TerminalRectangle deflatePreserveCenter(TerminalInsets insets) {
        if (insets == null) {
            return copy();
        }
        TerminalRectangle deflated = TerminalRectanglePool.getInstance().obtain();
        int centerX = this.x + (this.width / 2);
        int centerY = this.y + (this.height / 2);
        int nw = this.width - (insets.getLeft() + insets.getRight());
        int nh = this.height - (insets.getTop() + insets.getBottom());

        if (nw < 0) {
            nw = 0;
        }
        if (nh < 0) {
            nh = 0;
        }

        int nx = centerX - (nw / 2);
        int ny = centerY - (nh / 2);
        deflated.set(nx, ny, nw, nh, parentAbsoluteX, parentAbsoluteY);
        return deflated;
    }
    
    /**
     * Get area
     */
    public int getArea() {
        return width * height;
    }
    
    /**
     * Check if contains point
     */
    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
    
    /**
     * Create union with other rectangle (returns new instance)
     */
    public TerminalRectangle union(TerminalRectangle other) {
        if (other == null || other.isEmpty()) {
            TerminalRectangle union = TerminalRectanglePool.getInstance().obtain();
            union.set(x, y, width, height);
            return union;
        }
        
        if (this.isEmpty()) {
            TerminalRectangle union = TerminalRectanglePool.getInstance().obtain();
            union.set(other.x, other.y, other.width, other.height);
            return union;
        }
        
        int x1 = Math.min(this.x, other.x);
        int y1 = Math.min(this.y, other.y);
        int x2 = Math.max(this.x + this.width, other.x + other.width);
        int y2 = Math.max(this.y + this.height, other.y + other.height);
        
        TerminalRectangle union = TerminalRectanglePool.getInstance().obtain();
        union.set(x1, y1, x2 - x1, y2 - y1);
        return union;
    }
    
    // ===== COMPATIBILITY HELPERS =====
    
  
    /**
     * Get extents as array [width, height]
     * Creates new array - not zero-allocation
     */
    public int[] getExtentsArray() {
        return new int[] { width, height };
    }
    
    /**
     * Set from extents array
     */
    public void setExtentsArray(int[] extents) {
        if (extents != null && extents.length >= 2) {
            this.width = Math.max(0, extents[0]);
            this.height = Math.max(0, extents[1]);
        }
    }
    
    // ===== OBJECT OVERRIDES =====
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TerminalRectangle)) return false;
        return equals((TerminalRectangle) obj);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(parentAbsoluteX, parentAbsoluteY, x, y, width, height);
    }
    
    @Override
    public String toString() {
        return String.format("TerminalRectangle[x=%d, y=%d, w=%d, h=%d, pAbsX=%d, pAbsY=%d]", x, y, width, height, parentAbsoluteX, parentAbsoluteY);
    }
    
    /**
     * Create a copy of this rectangle
     */
    public TerminalRectangle copy() {
        TerminalRectangle copy = TerminalRectanglePool.getInstance().obtain();
        copy.set(x, y, width, height, parentAbsoluteX, parentAbsoluteY);
        return copy;
    }

    public static TerminalRectangle fromNoteBytes(NoteBytesMap map){

        NoteBytes xBytes = map.get(Keys.X);
        NoteBytes yBytes = map.get(Keys.Y);
        NoteBytes widthBytes = map.get(Keys.WIDTH);
        NoteBytes heightBytes = map.get(Keys.HEIGHT);
        NoteBytes parentXBytes = map.get(PARENT_ABS_X);
        NoteBytes parentYBytes = map.get(PARENT_ABS_Y);

        int x = xBytes != null ? xBytes.getAsInt() : 0;
        int y = yBytes != null ? yBytes.getAsInt() : 0;
        int width = widthBytes != null ? widthBytes.getAsInt() : 0;
        int height = heightBytes != null ? heightBytes.getAsInt() : 0;
        int parentAbsX = parentXBytes != null ? parentXBytes.getAsInt() : 0;
        int parentAbsY = parentYBytes != null ? parentYBytes.getAsInt() : 0;

        TerminalRectangle rect = TerminalRectanglePool.getInstance().obtain();
        rect.set(x, y, width, height, parentAbsX, parentAbsY);
        return rect;
    }

    public static TerminalRectangle fromNoteBytes(NoteBytes noteBytes){
        if(noteBytes.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
            throw new IllegalArgumentException("TerminalRectangle.fromNoteBytes is not an object");
        }
        return fromNoteBytes(noteBytes.getAsNoteBytesMap());        
    }

    public NoteBytesObject toNoteBytes(){
        NoteBytesMap map = new NoteBytesMap();

        map.put(Keys.X, x);
        map.put(Keys.Y, y);
        map.put(Keys.WIDTH, width);
        map.put(Keys.HEIGHT, height);
        if(parentAbsoluteX != 0){
            map.put(PARENT_ABS_X, parentAbsoluteX);
        }
        if(parentAbsoluteY != 0){
            map.put(PARENT_ABS_Y, parentAbsoluteY);
        }
        
        return map.toNoteBytes();
    }

   

    @Override
    public void set(TerminalRectangle other) {
        set(other.x, other.y, other.width, other.height);
    }

    public void set(int x, int y, int width, int height){
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void set(int x, int y, int width, int height, int parentAbsX, int parentAbsY){
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.parentAbsoluteX = parentAbsX;
        this.parentAbsoluteY = parentAbsY;
    }
 

    @Override
    public TerminalRectangle createEmpty() {
        return TerminalRectanglePool.getInstance().obtain();
    }


    @Override
    public void translate(Point2D point) {
        translate(point.getX(), point.getY());

    }

    public void setParentAbsoluteX(int parentAbsoluteX) {
        this.parentAbsoluteX = parentAbsoluteX;
    }
    
    public void setParentAbsoluteY(int parentAbsoluteY) {
        this.parentAbsoluteY = parentAbsoluteY;
    }
    
    public void setParentAbsolute(int parentAbsoluteX, int parentAbsoluteY) {
        this.parentAbsoluteX = parentAbsoluteX;
        this.parentAbsoluteY = parentAbsoluteY;
    }
    
    public int getParentAbsoluteX() {
        return parentAbsoluteX;
    }
    
    public int getParentAbsoluteY() {
        return parentAbsoluteY;
    }


    @Override
    public boolean contains(Point2D point) {
        return contains(point.getX(), point.getY());
    }



    @Override
    public Point2D getParentAbsolutePosition() {
        return new Point2D(parentAbsoluteX, parentAbsoluteY);
    }

    @Override
    public void setParentAbsolutePosition(Point2D point) {
        parentAbsoluteX = point.getX();
        parentAbsoluteY = point.getY();
    }

    @Override
    public Point2D getAbsolutePosition() {
        return new Point2D(getAbsoluteX(), getAbsoluteY());
    }

   
    public void clear(){
        this.x = 0;
        this.y = 0;
        this.width = 0;
        this.height = 0;
        this.parentAbsoluteX = 0;
        this.parentAbsoluteY = 0;
    }

    @Override
    public void createAbsoluteFrom(TerminalRectangle region) {
        this.x = region.getAbsoluteX();
        this.y = region.getAbsoluteY();
        this.width = region.width;
        this.height = region.height;
        this.parentAbsoluteX = region.parentAbsoluteX;
        this.parentAbsoluteY = region.parentAbsoluteY;
    }
}
