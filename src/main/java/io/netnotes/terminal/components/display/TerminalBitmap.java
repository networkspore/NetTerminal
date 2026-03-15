package io.netnotes.terminal.components.display;

import java.util.Arrays;

/**
 * TerminalBitmap - Logical pixel buffer for terminal sub-character rendering
 *
 * <p>Stores a monochrome (1-bit) or greyscale (8-bit) pixel grid that can be
 * rendered into a terminal via {@link TerminalBitmapView} using any of the
 * three sub-character encodings the framework supports:
 *
 * <table>
 *   <tr><th>Mode</th><th>Sub-pixels per cell</th><th>Pixel factor (cols×rows)</th></tr>
 *   <tr><td>QUADRANT</td><td>4</td><td>2 × 2</td></tr>
 *   <tr><td>BRAILLE</td><td>8</td><td>2 × 4</td></tr>
 *   <tr><td>SEXTANT</td><td>6</td><td>2 × 3</td></tr>
 * </table>
 *
 * <p>The bitmap is stored at a <em>logical</em> resolution chosen by the caller —
 * it may be larger or smaller than the physical pixel canvas that a given
 * component size + rendering mode produces.  {@link TerminalBitmapView} scales
 * from logical → physical using nearest-neighbour or bilinear sampling at
 * render time, so the data never needs to change when the component is resized.
 *
 * <p>Pixels are stored row-major as a {@code byte[]} where {@code 0 = off} and
 * {@code 255 = fully on}.  Values in between act as a threshold source for
 * modes that support greyscale (currently only used by BRAILLE for dithering).
 *
 * USAGE:
 * <pre>
 *   // 64×32 logical bitmap
 *   TerminalBitmap bmp = new TerminalBitmap(64, 32);
 *   bmp.fill(false);
 *   bmp.drawRect(4, 4, 56, 24, true);
 *   bmp.drawLine(0, 0, 63, 31, true);
 *
 *   TerminalBitmapView view = new TerminalBitmapView("logo", bmp);
 *   view.setRenderMode(RenderMode.SEXTANT);
 *   view.setScaleMode(ScaleMode.FIT);
 * </pre>
 */
public class TerminalBitmap {

    // ===== INNER TYPES =====

    /**
     * Sub-character pixel encoding to use when rendering.
     */
    public enum RenderMode {
        /**
         * 2×2 sub-pixels per cell using Unicode block-element quadrant characters.
         * Widest terminal support; lowest resolution.
         */
        QUADRANT(2, 2),

        /**
         * 2×4 sub-pixels per cell using Unicode Braille patterns (U+2800–U+28FF).
         * Good for line charts and sparklines; excellent vertical resolution.
         */
        BRAILLE(2, 4),

        /**
         * 2×3 sub-pixels per cell using Unicode legacy-computing sextant characters
         * (U+1FB00–U+1FB3B). Best balance of resolution and aspect ratio.
         */
        SEXTANT(2, 3),

        /**
         * Automatically choose the best available mode.  Falls back to QUADRANT.
         */
        AUTO(2, 2);

        /** Sub-pixel columns per character cell. */
        public final int subCols;
        /** Sub-pixel rows per character cell. */
        public final int subRows;

        RenderMode(int subCols, int subRows) {
            this.subCols = subCols;
            this.subRows = subRows;
        }

        /** Resolve AUTO to a concrete mode (currently always SEXTANT). */
        public RenderMode resolve() {
            return this == AUTO ? SEXTANT : this;
        }
    }

    /**
     * How the logical bitmap is mapped to the physical pixel canvas.
     */
    public enum ScaleMode {
        /**
         * Stretch to fill the exact pixel canvas — aspect ratio not preserved.
         */
        STRETCH,

        /**
         * Scale uniformly so the bitmap fits entirely within the canvas
         * (letter-box / pillar-box with empty pixels around it).
         */
        FIT,

        /**
         * Scale uniformly so the bitmap fills the canvas entirely
         * (some edges may be cropped).
         */
        FILL,

        /**
         * No scaling; bitmap is rendered at 1:1 logical pixels.  If the canvas
         * is smaller than the bitmap the image is cropped; if larger, pixels
         * outside the bitmap area are left dark.
         */
        NONE
    }

    // ===== STATE =====

    private final int logicalWidth;
    private final int logicalHeight;
    private final byte[] pixels; // row-major [y * logicalWidth + x], 0=off 255=on

    // ===== CONSTRUCTION =====

    /**
     * Create an all-dark bitmap of the given logical size.
     *
     * @param width  logical pixel width  (≥ 1)
     * @param height logical pixel height (≥ 1)
     */
    public TerminalBitmap(int width, int height) {
        if (width < 1 || height < 1)
            throw new IllegalArgumentException("TerminalBitmap dimensions must be ≥ 1");
        this.logicalWidth  = width;
        this.logicalHeight = height;
        this.pixels        = new byte[width * height];
    }

    /**
     * Create a bitmap from raw byte data (0 = off, non-zero = on).
     */
    public TerminalBitmap(int width, int height, byte[] data) {
        this(width, height);
        if (data != null) {
            System.arraycopy(data, 0, pixels, 0, Math.min(data.length, pixels.length));
        }
    }

    /** Copy constructor. */
    public TerminalBitmap(TerminalBitmap source) {
        this(source.logicalWidth, source.logicalHeight, source.pixels);
    }

    // ===== PROPERTIES =====

    public int getLogicalWidth()  { return logicalWidth; }
    public int getLogicalHeight() { return logicalHeight; }

    // ===== PIXEL ACCESS =====

    /**
     * Return the raw 0–255 value at (x, y).  Coordinates outside the bitmap
     * silently return 0.
     */
    public int getPixelValue(int x, int y) {
        if (x < 0 || x >= logicalWidth || y < 0 || y >= logicalHeight) return 0;
        return pixels[y * logicalWidth + x] & 0xFF;
    }

    /** Return {@code true} if the pixel at (x, y) is on (value ≥ 128). */
    public boolean isPixelOn(int x, int y) {
        return getPixelValue(x, y) >= 128;
    }

    /** Set a single pixel on (255) or off (0). */
    public void setPixel(int x, int y, boolean on) {
        setPixelValue(x, y, on ? 255 : 0);
    }

    /** Set a single pixel to an explicit 0–255 intensity. */
    public void setPixelValue(int x, int y, int value) {
        if (x < 0 || x >= logicalWidth || y < 0 || y >= logicalHeight) return;
        pixels[y * logicalWidth + x] = (byte)(value & 0xFF);
    }

    // ===== DRAWING PRIMITIVES =====

    /** Fill the entire bitmap. */
    public void fill(boolean on) {
        Arrays.fill(pixels, on ? (byte)0xFF : (byte)0);
    }

    /** Fill the entire bitmap with an intensity value (0–255). */
    public void fill(int value) {
        Arrays.fill(pixels, (byte)(value & 0xFF));
    }

    /** Fill an axis-aligned rectangle.  Coordinates are clamped to bounds. */
    public void drawRect(int x, int y, int w, int h, boolean on) {
        int x1 = Math.max(0, x);
        int y1 = Math.max(0, y);
        int x2 = Math.min(logicalWidth,  x + w);
        int y2 = Math.min(logicalHeight, y + h);
        byte v  = on ? (byte)0xFF : (byte)0;
        for (int py = y1; py < y2; py++) {
            Arrays.fill(pixels, py * logicalWidth + x1,
                                py * logicalWidth + x2, v);
        }
    }

    /** Draw the outline of an axis-aligned rectangle. */
    public void drawRectOutline(int x, int y, int w, int h, boolean on) {
        drawHorizontalLine(x, y,         w, on);
        drawHorizontalLine(x, y + h - 1, w, on);
        drawVerticalLine(x,         y, h, on);
        drawVerticalLine(x + w - 1, y, h, on);
    }

    /** Draw a horizontal line of pixels. */
    public void drawHorizontalLine(int x, int y, int length, boolean on) {
        if (y < 0 || y >= logicalHeight) return;
        byte v = on ? (byte)0xFF : (byte)0;
        int x1 = Math.max(0, x);
        int x2 = Math.min(logicalWidth, x + length);
        if (x2 > x1) Arrays.fill(pixels, y * logicalWidth + x1, y * logicalWidth + x2, v);
    }

    /** Draw a vertical line of pixels. */
    public void drawVerticalLine(int x, int y, int length, boolean on) {
        if (x < 0 || x >= logicalWidth) return;
        byte v = on ? (byte)0xFF : (byte)0;
        int y1 = Math.max(0, y);
        int y2 = Math.min(logicalHeight, y + length);
        for (int py = y1; py < y2; py++) pixels[py * logicalWidth + x] = v;
    }

    /** Bresenham line from (x0,y0) to (x1,y1). */
    public void drawLine(int x0, int y0, int x1, int y1, boolean on) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            setPixel(x0, y0, on);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    /** Draw a circle outline using the Midpoint circle algorithm. */
    public void drawCircle(int cx, int cy, int radius, boolean on) {
        int x = 0, y = radius, d = 3 - 2 * radius;
        while (y >= x) {
            setOctant(cx, cy, x, y, on);
            x++;
            if (d > 0) { y--; d += 4 * (x - y) + 10; }
            else         d += 4 * x + 6;
        }
    }

    private void setOctant(int cx, int cy, int x, int y, boolean on) {
        setPixel(cx+x, cy+y, on); setPixel(cx-x, cy+y, on);
        setPixel(cx+x, cy-y, on); setPixel(cx-x, cy-y, on);
        setPixel(cx+y, cy+x, on); setPixel(cx-y, cy+x, on);
        setPixel(cx+y, cy-x, on); setPixel(cx-y, cy-x, on);
    }

    /**
     * Plot a bar chart into the bitmap.
     *
     * @param values     normalised values 0.0–1.0; one bar per entry
     * @param barWidthPx width of each bar in logical pixels
     * @param gapPx      gap between bars in logical pixels
     */
    public void plotBars(float[] values, int barWidthPx, int gapPx) {
        if (values == null || values.length == 0) return;
        int x = 0;
        for (float v : values) {
            float clamped = Math.max(0f, Math.min(1f, v));
            int barH = Math.round(clamped * logicalHeight);
            drawRect(x, logicalHeight - barH, barWidthPx, barH, true);
            x += barWidthPx + gapPx;
            if (x >= logicalWidth) break;
        }
    }

    /**
     * Plot a polyline (sparkline) from a series of normalised values.
     *
     * @param values normalised 0.0–1.0
     */
    public void plotLine(float[] values) {
        if (values == null || values.length < 2) return;
        float xStep = (float)(logicalWidth - 1) / (values.length - 1);
        for (int i = 0; i < values.length - 1; i++) {
            int x0 = Math.round(i * xStep);
            int x1 = Math.round((i + 1) * xStep);
            int y0 = logicalHeight - 1 - Math.round(Math.max(0f, Math.min(1f, values[i]))   * (logicalHeight - 1));
            int y1 = logicalHeight - 1 - Math.round(Math.max(0f, Math.min(1f, values[i+1])) * (logicalHeight - 1));
            drawLine(x0, y0, x1, y1, true);
        }
    }

    // ===== SCALING =====

    /**
     * Produce a scaled byte[] suitable for passing directly to the terminal
     * draw methods.  The output is sized {@code targetW × targetH} pixels,
     * sampled from this bitmap according to {@code scaleMode}.
     *
     * @param targetW target pixel width  (component charWidth  × mode.subCols)
     * @param targetH target pixel height (component charHeight × mode.subRows)
     * @param mode    scale mode
     * @param bilinear use bilinear sampling (smoother); false = nearest-neighbour
     * @return row-major {@code byte[targetH * targetW]}, 0 or 0xFF per pixel
     */
    public byte[] scaleToTarget(int targetW, int targetH, ScaleMode mode, boolean bilinear) {
        if (targetW <= 0 || targetH <= 0) return new byte[0];

        // Compute the effective source rectangle based on scale mode
        float srcX0 = 0, srcY0 = 0;
        float srcW  = logicalWidth, srcH = logicalHeight;

        if (mode == ScaleMode.FIT || mode == ScaleMode.FILL) {
            float scaleX = (float)targetW / logicalWidth;
            float scaleY = (float)targetH / logicalHeight;
            float scale  = (mode == ScaleMode.FIT) ? Math.min(scaleX, scaleY)
                                                    : Math.max(scaleX, scaleY);
            // Centre the source within the target
            float scaledW = logicalWidth  * scale;
            float scaledH = logicalHeight * scale;
            srcX0 = -(targetW - scaledW) / (2f * scale);
            srcY0 = -(targetH - scaledH) / (2f * scale);
            srcW  = targetW / scale;
            srcH  = targetH / scale;
        } else if (mode == ScaleMode.NONE) {
            // 1:1 — source and target use same coordinates; just clamp
            srcW = targetW;
            srcH = targetH;
        }
        // STRETCH: srcX0=0, srcY0=0, srcW=logicalWidth, srcH=logicalHeight (defaults)

        byte[] out = new byte[targetW * targetH];
        for (int ty = 0; ty < targetH; ty++) {
            for (int tx = 0; tx < targetW; tx++) {
                float sx = srcX0 + ((float)tx / targetW) * srcW;
                float sy = srcY0 + ((float)ty / targetH) * srcH;

                int v;
                if (bilinear) {
                    v = sampleBilinear(sx, sy);
                } else {
                    v = getPixelValue(Math.round(sx), Math.round(sy));
                }
                out[ty * targetW + tx] = (byte)(v & 0xFF);
            }
        }
        return out;
    }

    private int sampleBilinear(float sx, float sy) {
        int x0 = (int)sx, y0 = (int)sy;
        int x1 = x0 + 1, y1 = y0 + 1;
        float fx = sx - x0, fy = sy - y0;

        float v00 = getPixelValue(x0, y0);
        float v10 = getPixelValue(x1, y0);
        float v01 = getPixelValue(x0, y1);
        float v11 = getPixelValue(x1, y1);

        return Math.round(
            v00 * (1-fx) * (1-fy) +
            v10 *    fx  * (1-fy) +
            v01 * (1-fx) *    fy  +
            v11 *    fx  *    fy
        );
    }

    // ===== FACTORY HELPERS =====

    /**
     * Create a bitmap from a 2-D boolean array ({@code [row][col]}).
     */
    public static TerminalBitmap fromBooleanGrid(boolean[][] grid) {
        if (grid == null || grid.length == 0) return new TerminalBitmap(1, 1);
        int h = grid.length, w = grid[0].length;
        TerminalBitmap bmp = new TerminalBitmap(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < Math.min(w, grid[y].length); x++) {
                bmp.setPixel(x, y, grid[y][x]);
            }
        }
        return bmp;
    }

    /**
     * Create a bitmap from ASCII art where {@code fillChar} is treated as
     * a lit pixel and every other character is dark.
     *
     * <pre>
     *   TerminalBitmap bmp = TerminalBitmap.fromAsciiArt(new String[]{
     *       " XX ",
     *       "XXXX",
     *       " XX "
     *   }, 'X');
     * </pre>
     */
    public static TerminalBitmap fromAsciiArt(String[] rows, char fillChar) {
        if (rows == null || rows.length == 0) return new TerminalBitmap(1, 1);
        int h = rows.length;
        int w = 0;
        for (String r : rows) w = Math.max(w, r.length());
        TerminalBitmap bmp = new TerminalBitmap(Math.max(1, w), h);
        for (int y = 0; y < h; y++) {
            String row = rows[y];
            for (int x = 0; x < row.length(); x++) {
                bmp.setPixel(x, y, row.charAt(x) == fillChar);
            }
        }
        return bmp;
    }

    @Override
    public String toString() {
        return "TerminalBitmap[" + logicalWidth + "×" + logicalHeight + "]";
    }
}