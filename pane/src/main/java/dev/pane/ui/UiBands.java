package dev.pane.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Splits one large Java2D draw across cores. Every worker repeats the whole call clipped to its own
 * band of device rows, so each pixel is computed exactly as a single draw would compute it. Only
 * draws whose pixels depend on nothing but their own position qualify: a solid colour or an image,
 * an axis-aligned transform, and no clip or a rectangular one. A shaped clip would be re-tessellated
 * for every band and a gradient restarts its interpolation per band, so those draw once. Band edges
 * also stay out of rows where a filled outline curves: the rasterizer re-divides a curve that a clip
 * cuts, which would move its antialiased edge by a level or two.
 */
final class UiBands {
    /** Draws smaller than this many device pixels are not worth handing to other cores. */
    static final int MIN_PIXELS = 1 << 17;
    /** Image strips need no clip of their own, so smaller batches already pay off. */
    static final int MIN_IMAGE_PIXELS = 1 << 15;
    private static final int MIN_ROWS = 24;
    private static final int WORKERS = Math.min(8, Runtime.getRuntime().availableProcessors());
    /**
     * Raster shortcuts that must leave exactly the pixels of the plain Java2D path: banding here and
     * copying opaque caches. {@code -Dpane.ui.plainRaster=true} turns them off to compare or diagnose.
     */
    static volatile boolean shortcuts = !Boolean.getBoolean("pane.ui.plainRaster");

    private UiBands() { }

    /** Fills a rectangle with the current solid colour, in bands when it is large. */
    static void fill(Graphics2D graphics, Rectangle2D rectangle) {
        fill(graphics, rectangle, rectangle.getMinY(), rectangle.getMaxY());
    }

    /**
     * Fills {@code shape} with the current paint, in bands when it is large. Its outline is straight
     * in user-space rows {@code straightTop} to {@code straightBottom}; band edges fall only there.
     * Solid colours and linear gradients qualify: those compute each row afresh from its device
     * position. A texture carries its position from row to row of each 32-row tile the rasterizer
     * paints, so a texture fills only a rectangle and its band edges land on those tile edges.
     */
    static void fill(Graphics2D graphics, Shape shape, double straightTop, double straightBottom) {
        var paint = graphics.getPaint();
        boolean texture = paint instanceof java.awt.TexturePaint;
        // A radial gradient steps its position from row to row under scaling, so it draws once.
        boolean rowwise = paint instanceof Color || paint instanceof java.awt.GradientPaint || paint instanceof java.awt.LinearGradientPaint;
        if (!rowwise && !(texture && TILE_ROWS > 0 && shape instanceof Rectangle2D) || straightBottom <= straightTop) { graphics.fill(shape); return; }
        Rectangle device = deviceBounds(graphics, shape.getBounds2D());
        Rectangle straight = deviceBounds(graphics, new Rectangle2D.Double(0, straightTop, 1, straightBottom - straightTop));
        if (device == null || straight == null) { graphics.fill(shape); return; }
        int grid = 0, origin = 0;
        if (texture) {
            // The rasterizer's first tile starts at the first row whose centre lies inside both the rectangle and the clip.
            AffineTransform m = graphics.getTransform();
            Rectangle2D bounds = shape.getBounds2D();
            double top = Math.min(bounds.getMinY() * m.getScaleY(), bounds.getMaxY() * m.getScaleY()) + m.getTranslateY();
            origin = (int) Math.floor(top);
            Shape clip = graphics.getClip();
            if (clip instanceof Rectangle2D rectangle) {
                double clipTop = Math.min(rectangle.getMinY() * m.getScaleY(), rectangle.getMaxY() * m.getScaleY()) + m.getTranslateY();
                origin = Math.max(origin, (int) Math.ceil(clipTop - .5));
            }
            grid = TILE_ROWS;
        }
        // deviceBounds grows by a pixel for antialiasing; shrink two more so a band edge never touches a curved row.
        draw(graphics, device, straight.y + 3, straight.y + straight.height - 3, origin, grid, g -> g.fill(shape));
    }

    /**
     * Strokes {@code shape} with the current stroke and paint, in bands when it is large. The outline
     * is straight in user-space rows {@code straightTop} to {@code straightBottom}; band edges fall
     * only there, far enough from a curve that the stroke's own reach never meets one.
     */
    static void stroke(Graphics2D graphics, Shape shape, double straightTop, double straightBottom) {
        var paint = graphics.getPaint();
        float width = graphics.getStroke() instanceof java.awt.BasicStroke basic ? basic.getLineWidth() : -1;
        if (width < 0 || !(paint instanceof Color || paint instanceof java.awt.GradientPaint || paint instanceof java.awt.LinearGradientPaint)
                || straightBottom - straightTop <= width * 2) { graphics.draw(shape); return; }
        Rectangle2D bounds = shape.getBounds2D();
        Rectangle device = deviceBounds(graphics, new Rectangle2D.Double(bounds.getX() - width, bounds.getY() - width,
            bounds.getWidth() + width * 2, bounds.getHeight() + width * 2));
        Rectangle straight = deviceBounds(graphics, new Rectangle2D.Double(0, straightTop + width, 1, straightBottom - straightTop - width * 2));
        if (device == null || straight == null) { graphics.draw(shape); return; }
        draw(graphics, device, straight.y + 3, straight.y + straight.height - 3, 0, 0, g -> g.draw(shape));
    }

    /** Height of the rasterizer's antialiasing tiles, or 0 when it is configured in a way not known here. */
    private static final int TILE_ROWS = tileRows();

    private static int tileRows() {
        String engine = System.getProperty("sun.java2d.renderer"), size = System.getProperty("sun.java2d.renderer.tileSize_log2");
        if (engine != null && !engine.contains("Marlin") || size != null && !size.equals("5")) return 0;
        return 32;
    }

    /** One image drawn at a device position. */
    record Placed(BufferedImage image, int x, int y) { }

    /** Draws an image at device position (x, y) under an identity transform, on several cores when it is large. */
    static void image(Graphics2D graphics, BufferedImage image, int x, int y) {
        images(graphics, List.of(new Placed(image, x, y)));
    }

    /**
     * Draws images that do not overlap at device positions under an identity transform. A large batch
     * splits into strips of rows shared out between cores; a strip puts the same pixels in the same
     * place, so under any clip, shaped or not, the result is exactly that of drawing them in turn.
     */
    static void images(Graphics2D graphics, List<Placed> images) {
        long pixels = 0;
        for (Placed placed : images) pixels += (long) placed.image().getWidth() * placed.image().getHeight();
        int workers = !shortcuts || WORKERS < 2 || pixels < MIN_IMAGE_PIXELS || !(graphics.getComposite() instanceof AlphaComposite)
            || !graphics.getTransform().isIdentity() ? 1 : (int) Math.min(WORKERS, pixels / MIN_IMAGE_PIXELS + 1);
        if (workers <= 1) {
            for (Placed placed : images) graphics.drawImage(placed.image(), placed.x(), placed.y(), null);
            return;
        }
        // Consecutive rows go to each worker in equal shares of pixels, so every worker clips once.
        List<List<Placed>> shares = new ArrayList<>(workers);
        for (int i = 0; i < workers; i++) shares.add(new ArrayList<>());
        long share = (pixels + workers - 1) / workers, filled = 0;
        for (Placed placed : images) {
            BufferedImage image = placed.image();
            int width = image.getWidth(), row = 0;
            while (row < image.getHeight()) {
                int worker = (int) Math.min(workers - 1, filled / share);
                long room = (worker + 1) * share - filled;
                int rows = (int) Math.max(1, Math.min(image.getHeight() - row, room / Math.max(1, width)));
                shares.get(worker).add(new Placed(image.getSubimage(0, row, width, rows), placed.x(), placed.y() + row));
                filled += (long) rows * width;
                row += rows;
            }
        }
        Graphics2D[] copies = new Graphics2D[workers];
        try {
            for (int i = 0; i < workers; i++) copies[i] = (Graphics2D) graphics.create();
            java.util.stream.IntStream.range(0, workers).parallel().forEach(i -> {
                for (Placed strip : shares.get(i)) copies[i].drawImage(strip.image(), strip.x(), strip.y(), null);
            });
        } finally {
            for (Graphics2D copy : copies) if (copy != null) copy.dispose();
        }
    }

    /**
     * Runs {@code draw} once, or once per band of {@code device} rows when the draw qualifies.
     * {@code device} bounds every pixel the draw can touch; null always draws once.
     */
    static void draw(Graphics2D graphics, Rectangle device, Consumer<Graphics2D> draw) {
        if (device == null) { draw.accept(graphics); return; }
        draw(graphics, device, device.y, device.y + device.height, 0, 0, draw);
    }

    /**
     * As {@link #draw(Graphics2D, Rectangle, Consumer)}, with band edges only between device rows
     * {@code splitTop} and {@code splitBottom}, and with a nonzero {@code grid} only on rows {@code origin + k * grid}.
     */
    private static void draw(Graphics2D graphics, Rectangle device, int splitTop, int splitBottom, int origin, int grid, Consumer<Graphics2D> draw) {
        int top = Math.max(device.y, splitTop), bottom = Math.min(device.y + device.height, splitBottom);
        int bands = device.isEmpty() || bottom - top < MIN_ROWS ? 1 : Math.min(bands(graphics, device), (bottom - top) / MIN_ROWS + 1);
        if (bands <= 1) { draw.accept(graphics); return; }
        int[] edges = new int[bands + 1];
        edges[0] = device.y; edges[bands] = device.y + device.height;
        for (int i = 1; i < bands; i++) {
            int edge = top + (int) ((long) (bottom - top) * i / bands);
            if (grid > 0) edge = origin + Math.floorDiv(edge - origin, grid) * grid;
            edges[i] = Math.clamp(edge, edges[i - 1], edges[bands]);
        }
        Graphics2D[] workers = new Graphics2D[bands];
        try {
            for (int i = 0; i < bands; i++) {
                int bandTop = edges[i], bandBottom = edges[i + 1];
                if (bandBottom <= bandTop) continue;
                Graphics2D band = (Graphics2D) graphics.create();
                AffineTransform transform = band.getTransform();
                band.setTransform(new AffineTransform());
                // Integer device edges meet pixel centres nowhere, so neighbouring bands share no pixel.
                band.clipRect(device.x, bandTop, device.width, bandBottom - bandTop);
                band.setTransform(transform);
                workers[i] = band;
            }
            java.util.stream.IntStream.range(0, bands).parallel().forEach(i -> { if (workers[i] != null) draw.accept(workers[i]); });
        } finally {
            for (Graphics2D band : workers) if (band != null) band.dispose();
        }
    }

    private static int bands(Graphics2D graphics, Rectangle device) {
        if (!shortcuts || WORKERS < 2 || !(graphics.getComposite() instanceof AlphaComposite)) return 1;
        AffineTransform matrix = graphics.getTransform();
        if (matrix.getShearX() != 0 || matrix.getShearY() != 0) return 1;
        Shape clip = graphics.getClip();
        Rectangle area = device;
        if (clip != null) {
            if (!(clip instanceof Rectangle2D rectangle)) return 1;
            Rectangle clipped = deviceBounds(graphics, rectangle);
            area = clipped == null ? area : area.intersection(clipped);
        }
        if (area.isEmpty()) return 1;
        long pixels = (long) area.width * area.height;
        if (pixels < MIN_PIXELS) return 1;
        return (int) Math.max(1, Math.min(Math.min(WORKERS, area.height / MIN_ROWS), pixels * 2 / MIN_PIXELS));
    }

    /** Device pixels covered by user-space {@code bounds} under an axis-aligned transform, grown one pixel for antialiasing. */
    private static Rectangle deviceBounds(Graphics2D graphics, Rectangle2D bounds) {
        AffineTransform m = graphics.getTransform();
        if (m.getShearX() != 0 || m.getShearY() != 0 || bounds.isEmpty()) return null;
        double x0 = bounds.getMinX() * m.getScaleX() + m.getTranslateX(), x1 = bounds.getMaxX() * m.getScaleX() + m.getTranslateX();
        double y0 = bounds.getMinY() * m.getScaleY() + m.getTranslateY(), y1 = bounds.getMaxY() * m.getScaleY() + m.getTranslateY();
        int left = (int) Math.floor(Math.min(x0, x1)) - 1, top = (int) Math.floor(Math.min(y0, y1)) - 1;
        int right = (int) Math.ceil(Math.max(x0, x1)) + 1, bottom = (int) Math.ceil(Math.max(y0, y1)) + 1;
        return new Rectangle(left, top, right - left, bottom - top);
    }
}
