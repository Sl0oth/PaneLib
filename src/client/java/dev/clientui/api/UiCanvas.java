package dev.clientui.api;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** High-quality code-only raster canvas used by the native retained renderer. */
public final class UiCanvas {
    /**
     * Font sizes are quantised before caching so an animated size cannot grow this map without
     * bound, and the cache is dropped wholesale if a consumer still manages to fill it.
     */
    private static final int FONT_CACHE_LIMIT = 512;
    private static final Map<Long, Font> FONTS = new ConcurrentHashMap<>();

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
    }

    public void fill(int color, float x, float y, float width, float height) {
        graphics.setColor(color(color));
        graphics.fill(new java.awt.geom.Rectangle2D.Float(x, y, width, height));
    }

    public void roundRect(int color, float x, float y, float width, float height, float radius) {
        graphics.setColor(color(color));
        graphics.fill(roundShape(x, y, width, height, radius));
    }

    public void strokeRoundRect(int color, float stroke, float x, float y, float width, float height, float radius) {
        graphics.setColor(color(color));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float half = stroke / 2F;
        graphics.draw(roundShape(x + half, y + half, width - stroke, height - stroke, Math.max(0, radius - half)));
    }

    public void line(int color, float stroke, float x1, float y1, float x2, float y2) {
        graphics.setColor(color(color));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new java.awt.geom.Line2D.Float(x1, y1, x2, y2));
    }

    public void dashedLine(int color, float stroke, float dash, float x1, float y1, float x2, float y2) {
        graphics.setColor(color(color));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
            10, new float[]{dash, dash}, 0));
        graphics.draw(new java.awt.geom.Line2D.Float(x1, y1, x2, y2));
    }

    public void circle(int color, float centerX, float centerY, float radius) {
        graphics.setColor(color(color));
        graphics.fill(new java.awt.geom.Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2));
    }

    public void strokeCircle(int color, float stroke, float centerX, float centerY, float radius) {
        graphics.setColor(color(color));
        graphics.setStroke(new BasicStroke(stroke));
        graphics.draw(new java.awt.geom.Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2));
    }

    public void polygon(int color, float... points) {
        if (points.length < 6 || points.length % 2 != 0) return;
        Path2D.Float path = new Path2D.Float();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        path.closePath();
        graphics.setColor(color(color));
        graphics.fill(path);
    }

    public void text(String value, float x, float top, float size, int color) {
        text(value, x, top, size, color, Font.PLAIN);
    }

    public void textMedium(String value, float x, float top, float size, int color) {
        text(value, x, top, size, color, Font.BOLD);
    }

    public void text(String value, float x, float top, float size, int color, int style) {
        if (value == null || value.isEmpty()) return;
        Font font = font(size, style);
        graphics.setFont(font);
        graphics.setColor(color(color));
        FontMetrics metrics = graphics.getFontMetrics(font);
        graphics.drawString(value, x, top + metrics.getAscent());
    }

    public void centeredText(String value, float centerX, float centerY, float size, int color, int style) {
        Font font = font(size, style);
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics(font);
        graphics.setColor(color(color));
        graphics.drawString(value, centerX - metrics.stringWidth(value) / 2F,
            centerY + (metrics.getAscent() - metrics.getDescent()) / 2F);
    }

    public float textWidth(String value, float size, int style) {
        Font font = font(size, style);
        return graphics.getFontMetrics(font).stringWidth(value == null ? "" : value);
    }

    public void withClip(float x, float y, float width, float height, Runnable painter) {
        Shape old = graphics.getClip();
        graphics.clip(new java.awt.geom.Rectangle2D.Float(x, y, Math.max(0, width), Math.max(0, height)));
        try {
            painter.run();
        } finally {
            graphics.setClip(old);
        }
    }

    public static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    public static int mix(int first, int second, float amount) {
        float t = Math.max(0, Math.min(1, amount));
        int a = channel(first, 24), r = channel(first, 16), g = channel(first, 8), b = channel(first, 0);
        return ((int) (a + (channel(second, 24) - a) * t) << 24)
            | ((int) (r + (channel(second, 16) - r) * t) << 16)
            | ((int) (g + (channel(second, 8) - g) * t) << 8)
            | (int) (b + (channel(second, 0) - b) * t);
    }

    private static RoundRectangle2D.Float roundShape(float x, float y, float width, float height, float radius) {
        float arc = Math.max(0, radius * 2);
        return new RoundRectangle2D.Float(x, y, width, height, arc, arc);
    }

    private static Font font(float size, int style) {
        // Sixteenth-point steps are visually indistinguishable and bound the key space.
        float quantised = Math.max(1F, Math.min(512F, Math.round(size * 16F) / 16F));
        long key = ((long) style << 32) | (Math.round(quantised * 16F) & 0xFFFFFFFFL);
        Font cached = FONTS.get(key);
        if (cached != null) return cached;
        if (FONTS.size() >= FONT_CACHE_LIMIT) FONTS.clear();
        Font font = new Font(Font.SANS_SERIF, style, 1).deriveFont(quantised);
        FONTS.put(key, font);
        return font;
    }

    private static Color color(int value) { return new Color(value, true); }
    private static int channel(int value, int shift) { return value >>> shift & 0xFF; }
}
