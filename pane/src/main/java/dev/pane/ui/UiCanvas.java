package dev.pane.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Code-only raster canvas for the retained renderer.
 * <p>
 * Everything here is drawn from geometry and colour, never from a bitmap asset. Beyond flat
 * shapes it offers gradients, per-corner radii, shadows, shape clipping, transforms, free-form
 * paths, typography control, and procedural textures, which together are what let two screens
 * built on the same library look nothing alike.
 */
public final class UiCanvas {
    /** Logical font families, always available regardless of what the system has installed. */
    public static final String SANS = Font.SANS_SERIF;
    public static final String SERIF = Font.SERIF;
    public static final String MONO = Font.MONOSPACED;

    private static final int FONT_CACHE_LIMIT = 512;
    private static final Map<String, Font> FONTS = new ConcurrentHashMap<>();
    private static final Map<String, BufferedImage> TEXTURES = new ConcurrentHashMap<>();
    private static final int TEXTURE_CACHE_LIMIT = 64;

    static {
        // We only ever rasterise into a BufferedImage. Declaring headless keeps AWT from opening
        // a display connection, which on macOS would otherwise add a Dock icon and steal focus.
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }
    }

    private final Graphics2D graphics;

    UiCanvas(Graphics2D graphics) {
        this.graphics = graphics;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    // ---------------------------------------------------------------- flat shapes

    public void fill(int color, float x, float y, float width, float height) {
        graphics.setColor(awtColor(color));
        graphics.fill(new Rectangle2D.Float(x, y, width, height));
    }

    public void roundRect(int color, float x, float y, float width, float height, float radius) {
        graphics.setColor(awtColor(color));
        graphics.fill(roundShape(x, y, width, height, radius));
    }

    /** Rounded rectangle with independent corner radii, clockwise from the top left. */
    public void roundRect(int color, float x, float y, float width, float height,
                          float topLeft, float topRight, float bottomRight, float bottomLeft) {
        graphics.setColor(awtColor(color));
        graphics.fill(cornerShape(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft));
    }

    public void strokeRoundRect(int color, float stroke, float x, float y, float width, float height, float radius) {
        graphics.setColor(awtColor(color));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float half = stroke / 2F;
        graphics.draw(roundShape(x + half, y + half, width - stroke, height - stroke, Math.max(0, radius - half)));
    }

    /** Outlined rectangle with independent corner radii, clockwise from the top left. */
    public void strokeRoundRect(int color, float stroke, float x, float y, float width, float height,
                                float topLeft, float topRight, float bottomRight, float bottomLeft) {
        graphics.setColor(awtColor(color));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float h = stroke / 2F;
        graphics.draw(cornerShape(x + h, y + h, width - stroke, height - stroke,
            Math.max(0, topLeft - h), Math.max(0, topRight - h),
            Math.max(0, bottomRight - h), Math.max(0, bottomLeft - h)));
    }

    public void line(int color, float stroke, float x1, float y1, float x2, float y2) {
        graphics.setColor(awtColor(color));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    public void dashedLine(int color, float stroke, float dash, float x1, float y1, float x2, float y2) {
        graphics.setColor(awtColor(color));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
            10, new float[]{dash, dash}, 0));
        graphics.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    public void circle(int color, float centerX, float centerY, float radius) {
        graphics.setColor(awtColor(color));
        graphics.fill(new Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2));
    }

    public void strokeCircle(int color, float stroke, float centerX, float centerY, float radius) {
        graphics.setColor(awtColor(color));
        graphics.setStroke(new BasicStroke(stroke));
        graphics.draw(new Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2));
    }

    public void polygon(int color, float... points) {
        if (points.length < 6 || points.length % 2 != 0) return;
        Path2D.Float shape = new Path2D.Float();
        shape.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) shape.lineTo(points[i], points[i + 1]);
        shape.closePath();
        graphics.setColor(awtColor(color));
        graphics.fill(shape);
    }

    /** A free-form shape built from lines and curves. */
    public UiPath path() {
        return new UiPath(graphics);
    }

    // ---------------------------------------------------------------- gradients

    /**
     * Fills a rounded rectangle with a linear ramp between two colours.
     *
     * @param degrees direction of the ramp, clockwise from east: 90 runs top to bottom
     */
    public void gradientRect(int from, int to, float degrees,
                             float x, float y, float width, float height, float radius) {
        graphics.setPaint(linearPaint(from, to, degrees, x, y, width, height));
        graphics.fill(roundShape(x, y, width, height, radius));
        graphics.setPaint(awtColor(0xFF000000));
    }

    /** Fills a rounded rectangle with a radial ramp from its centre outwards. */
    public void radialRect(int inner, int outer, float centerX, float centerY, float radius,
                           float x, float y, float width, float height, float cornerRadius) {
        if (radius <= 0) return;
        graphics.setPaint(new RadialGradientPaint(new Point2D.Float(centerX, centerY), radius,
            new float[]{0F, 1F}, new Color[]{awtColor(inner), awtColor(outer)},
            MultipleGradientPaint.CycleMethod.NO_CYCLE));
        graphics.fill(roundShape(x, y, width, height, cornerRadius));
        graphics.setPaint(awtColor(0xFF000000));
    }

    /** Darkens the edges of a region, useful for grounding a full-bleed background. */
    public void vignette(int edgeColor, float x, float y, float width, float height) {
        float cx = x + width / 2, cy = y + height / 2;
        float radius = Math.max(width, height) * .75F;
        radialRect(alpha(edgeColor, 0), edgeColor, cx, cy, radius, x, y, width, height, 0);
    }

    // ---------------------------------------------------------------- depth

    /**
     * Soft shadow behind a rounded rectangle, approximated with stacked translucent rings so it
     * stays cheap enough to run inside a repaint.
     *
     * @param spread how far the shadow reaches beyond the shape
     */
    public void shadow(int color, float spread, float offsetX, float offsetY,
                       float x, float y, float width, float height, float radius) {
        if (spread <= 0) return;
        int steps = Math.max(3, Math.min(14, Math.round(spread)));
        int baseAlpha = (color >>> 24) & 0xFF;
        for (int i = steps; i >= 1; i--) {
            float grow = spread * i / steps;
            int stepAlpha = Math.max(1, Math.round(baseAlpha * (1F - (float) i / (steps + 1)) / steps * 2.2F));
            graphics.setColor(awtColor(alpha(color, stepAlpha)));
            graphics.fill(roundShape(x - grow + offsetX, y - grow + offsetY,
                width + grow * 2, height + grow * 2, radius + grow));
        }
    }

    /** Coloured glow centred on a shape. Reads as light rather than shade. */
    public void glow(int color, float spread, float x, float y, float width, float height, float radius) {
        shadow(color, spread, 0, 0, x, y, width, height, radius);
    }

    // ---------------------------------------------------------------- procedural texture

    /**
     * Film-grain speckle. Rendered from a cached tile, so a full-page wash costs one fill.
     *
     * @param density 0..1 fraction of pixels that carry a speck
     */
    public void grain(int color, float density, float x, float y, float width, float height) {
        int d = Math.max(1, Math.min(100, Math.round(density * 100)));
        BufferedImage tile = texture("grain:" + color + ":" + d, 64, 64, g -> {
            java.util.Random random = new java.util.Random(0x5EED4711L);
            g.setColor(awtColor(color));
            int specks = 64 * 64 * d / 100;
            for (int i = 0; i < specks; i++) {
                g.fillRect(random.nextInt(64), random.nextInt(64), 1, 1);
            }
        });
        paintTexture(tile, x, y, width, height);
    }

    /** Diagonal hatching. {@code degrees} is clockwise from east. */
    public void stripes(int color, float thickness, float gap, float degrees,
                        float x, float y, float width, float height) {
        int t = Math.max(1, Math.round(thickness));
        int step = Math.max(t + 1, Math.round(thickness + gap));
        int deg = Math.round(degrees);
        int size = step * 4;
        BufferedImage tile = texture("stripes:" + color + ":" + t + ":" + step + ":" + deg, size, size, g -> {
            g.setColor(awtColor(color));
            g.rotate(Math.toRadians(deg), size / 2.0, size / 2.0);
            for (int i = -size; i < size * 2; i += step) {
                g.fillRect(i, -size, t, size * 3);
            }
        });
        paintTexture(tile, x, y, width, height);
    }

    /** Evenly spaced dots. */
    public void dots(int color, float radius, float spacing, float x, float y, float width, float height) {
        int step = Math.max(2, Math.round(spacing));
        int r = Math.max(1, Math.round(radius * 2));
        BufferedImage tile = texture("dots:" + color + ":" + r + ":" + step, step, step, g -> {
            g.setColor(awtColor(color));
            g.fill(new Ellipse2D.Float((step - r) / 2F, (step - r) / 2F, r, r));
        });
        paintTexture(tile, x, y, width, height);
    }

    /** Ruled grid, drawn as lines so the spacing stays exact at any size. */
    public void grid(int color, float stroke, float cell, float x, float y, float width, float height) {
        if (cell <= 0) return;
        graphics.setColor(awtColor(color));
        graphics.setStroke(new BasicStroke(stroke));
        for (float gx = x; gx <= x + width; gx += cell) {
            graphics.draw(new Line2D.Float(gx, y, gx, y + height));
        }
        for (float gy = y; gy <= y + height; gy += cell) {
            graphics.draw(new Line2D.Float(x, gy, x + width, gy));
        }
    }

    /** Horizontal scanlines, for terminal and CRT treatments. */
    public void scanlines(int color, float thickness, float gap, float x, float y, float width, float height) {
        stripes(color, thickness, gap, 0, x, y, width, height);
    }

    // ---------------------------------------------------------------- text

    public void text(String value, float x, float top, float size, int color) {
        text(value, x, top, size, color, Font.PLAIN);
    }

    public void textMedium(String value, float x, float top, float size, int color) {
        text(value, x, top, size, color, Font.BOLD);
    }

    public void text(String value, float x, float top, float size, int color, int style) {
        text(value, x, top, size, color, style, SANS);
    }

    /** Draws text in a specific family: {@link #SANS}, {@link #SERIF}, {@link #MONO}, or a system font. */
    public void text(String value, float x, float top, float size, int color, int style, String family) {
        if (value == null || value.isEmpty()) return;
        Font font = font(size, style, family);
        graphics.setFont(font);
        graphics.setColor(awtColor(color));
        graphics.drawString(value, x, top + graphics.getFontMetrics(font).getAscent());
    }

    /**
     * Text with extra space between characters. Positive tracking suits small uppercase labels;
     * it is what gives a typographic style its voice as much as the family does.
     */
    public void trackedText(String value, float x, float top, float size, int color, int style,
                            String family, float tracking) {
        if (value == null || value.isEmpty()) return;
        Font font = font(size, style, family);
        graphics.setFont(font);
        graphics.setColor(awtColor(color));
        FontMetrics metrics = graphics.getFontMetrics(font);
        float baseline = top + metrics.getAscent();
        float cursor = x;
        for (int i = 0; i < value.length(); i++) {
            String glyph = String.valueOf(value.charAt(i));
            graphics.drawString(glyph, cursor, baseline);
            cursor += metrics.stringWidth(glyph) + tracking;
        }
    }

    public void centeredText(String value, float centerX, float centerY, float size, int color, int style) {
        centeredText(value, centerX, centerY, size, color, style, SANS);
    }

    public void centeredText(String value, float centerX, float centerY, float size, int color,
                             int style, String family) {
        if (value == null || value.isEmpty()) return;
        Font font = font(size, style, family);
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics(font);
        graphics.setColor(awtColor(color));
        graphics.drawString(value, centerX - metrics.stringWidth(value) / 2F,
            centerY + (metrics.getAscent() - metrics.getDescent()) / 2F);
    }

    public float textWidth(String value, float size, int style) {
        return textWidth(value, size, style, SANS);
    }

    public float textWidth(String value, float size, int style, String family) {
        return graphics.getFontMetrics(font(size, style, family)).stringWidth(value == null ? "" : value);
    }

    public float trackedTextWidth(String value, float size, int style, String family, float tracking) {
        if (value == null || value.isEmpty()) return 0;
        return textWidth(value, size, style, family) + tracking * (value.length() - 1);
    }

    /** Truncates with an ellipsis so a label never overruns its box. */
    public String ellipsize(String value, float maxWidth, float size, int style, String family) {
        if (value == null || value.isEmpty() || textWidth(value, size, style, family) <= maxWidth) return value;
        String suffix = "…";
        int end = value.length();
        while (end > 0 && textWidth(value.substring(0, end) + suffix, size, style, family) > maxWidth) end--;
        return end <= 0 ? suffix : value.substring(0, end) + suffix;
    }

    // ---------------------------------------------------------------- scoped state

    public void withClip(float x, float y, float width, float height, Runnable painter) {
        Shape old = graphics.getClip();
        graphics.clip(new Rectangle2D.Float(x, y, Math.max(0, width), Math.max(0, height)));
        try {
            painter.run();
        } finally {
            graphics.setClip(old);
        }
    }

    /** Clips to a rounded rectangle, for masking content inside a card. */
    public void withRoundedClip(float x, float y, float width, float height, float radius, Runnable painter) {
        Shape old = graphics.getClip();
        graphics.clip(roundShape(x, y, Math.max(0, width), Math.max(0, height), radius));
        try {
            painter.run();
        } finally {
            graphics.setClip(old);
        }
    }

    /** Clips to a circle, for avatars and badges. */
    public void withCircleClip(float centerX, float centerY, float radius, Runnable painter) {
        Shape old = graphics.getClip();
        graphics.clip(new Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2));
        try {
            painter.run();
        } finally {
            graphics.setClip(old);
        }
    }

    /** Rotates everything drawn inside, in degrees clockwise about a pivot. */
    public void withRotation(float degrees, float pivotX, float pivotY, Runnable painter) {
        AffineTransform old = graphics.getTransform();
        graphics.rotate(Math.toRadians(degrees), pivotX, pivotY);
        try {
            painter.run();
        } finally {
            graphics.setTransform(old);
        }
    }

    /** Scales everything drawn inside about a pivot. */
    public void withScale(float scaleX, float scaleY, float pivotX, float pivotY, Runnable painter) {
        AffineTransform old = graphics.getTransform();
        graphics.translate(pivotX, pivotY);
        graphics.scale(scaleX, scaleY);
        graphics.translate(-pivotX, -pivotY);
        try {
            painter.run();
        } finally {
            graphics.setTransform(old);
        }
    }

    /** Fades everything drawn inside as one group, rather than per shape. */
    public void withOpacity(float opacity, Runnable painter) {
        Composite old = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
            Math.max(0F, Math.min(1F, opacity))));
        try {
            painter.run();
        } finally {
            graphics.setComposite(old);
        }
    }

    // ---------------------------------------------------------------- colour helpers

    public static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    /** Same colour at a fraction of its current opacity. */
    public static int fade(int color, float amount) {
        return alpha(color, Math.round(((color >>> 24) & 0xFF) * Math.max(0F, Math.min(1F, amount))));
    }

    public static int mix(int first, int second, float amount) {
        float t = Math.max(0, Math.min(1, amount));
        int a = channel(first, 24), r = channel(first, 16), g = channel(first, 8), b = channel(first, 0);
        return ((int) (a + (channel(second, 24) - a) * t) << 24)
            | ((int) (r + (channel(second, 16) - r) * t) << 16)
            | ((int) (g + (channel(second, 8) - g) * t) << 8)
            | (int) (b + (channel(second, 0) - b) * t);
    }

    public static int lighten(int color, float amount) { return mix(color, 0xFFFFFFFF, amount); }

    public static int darken(int color, float amount) { return mix(color, 0xFF000000, amount); }

    // ---------------------------------------------------------------- internals

    static Color awtColor(int value) { return new Color(value, true); }

    static Paint linearPaint(int from, int to, float degrees, float x, float y, float width, float height) {
        double radians = Math.toRadians(degrees);
        float halfW = Math.max(.5F, width / 2), halfH = Math.max(.5F, height / 2);
        float cx = x + halfW, cy = y + halfH;
        float dx = (float) Math.cos(radians) * halfW, dy = (float) Math.sin(radians) * halfH;
        Point2D start = new Point2D.Float(cx - dx, cy - dy);
        Point2D end = new Point2D.Float(cx + dx, cy + dy);
        if (start.equals(end)) return awtColor(from);
        return new java.awt.LinearGradientPaint(start, end, new float[]{0F, 1F},
            new Color[]{awtColor(from), awtColor(to)});
    }

    private void paintTexture(BufferedImage tile, float x, float y, float width, float height) {
        graphics.setPaint(new TexturePaint(tile, new Rectangle2D.Float(x, y, tile.getWidth(), tile.getHeight())));
        graphics.fill(new Rectangle2D.Float(x, y, width, height));
        graphics.setPaint(awtColor(0xFF000000));
    }

    private static BufferedImage texture(String key, int width, int height, java.util.function.Consumer<Graphics2D> draw) {
        BufferedImage cached = TEXTURES.get(key);
        if (cached != null) return cached;
        if (TEXTURES.size() >= TEXTURE_CACHE_LIMIT) TEXTURES.clear();
        BufferedImage tile = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tile.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        try {
            draw.accept(g);
        } finally {
            g.dispose();
        }
        TEXTURES.put(key, tile);
        return tile;
    }

    private static RoundRectangle2D.Float roundShape(float x, float y, float width, float height, float radius) {
        float arc = Math.max(0, radius * 2);
        return new RoundRectangle2D.Float(x, y, width, height, arc, arc);
    }

    private static Path2D.Float cornerShape(float x, float y, float width, float height,
                                            float topLeft, float topRight, float bottomRight, float bottomLeft) {
        float limit = Math.min(width, height) / 2;
        float tl = Math.max(0, Math.min(limit, topLeft));
        float tr = Math.max(0, Math.min(limit, topRight));
        float br = Math.max(0, Math.min(limit, bottomRight));
        float bl = Math.max(0, Math.min(limit, bottomLeft));
        Path2D.Float shape = new Path2D.Float();
        shape.moveTo(x + tl, y);
        shape.lineTo(x + width - tr, y);
        if (tr > 0) shape.quadTo(x + width, y, x + width, y + tr);
        shape.lineTo(x + width, y + height - br);
        if (br > 0) shape.quadTo(x + width, y + height, x + width - br, y + height);
        shape.lineTo(x + bl, y + height);
        if (bl > 0) shape.quadTo(x, y + height, x, y + height - bl);
        shape.lineTo(x, y + tl);
        if (tl > 0) shape.quadTo(x, y, x + tl, y);
        shape.closePath();
        return shape;
    }

    private static Font font(float size, int style, String family) {
        float quantised = Math.max(1F, Math.min(512F, Math.round(size * 16F) / 16F));
        String resolved = family == null || family.isEmpty() ? SANS : family;
        String key = resolved + '|' + style + '|' + Math.round(quantised * 16F);
        Font cached = FONTS.get(key);
        if (cached != null) return cached;
        if (FONTS.size() >= FONT_CACHE_LIMIT) FONTS.clear();
        Font font = new Font(resolved, style, 1).deriveFont(quantised);
        FONTS.put(key, font);
        return font;
    }

    private static int channel(int value, int shift) { return value >>> shift & 0xFF; }
}
