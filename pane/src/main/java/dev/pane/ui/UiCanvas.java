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
import java.awt.Rectangle;
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
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

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
    private static final java.util.concurrent.atomic.AtomicLong FONT_REVISION=new java.util.concurrent.atomic.AtomicLong();
    private record TextLayoutKey(String value,Font font,java.awt.font.FontRenderContext context,float width,long revision) { }
    private record FontKey(String family, int style, int sixteenths) { }
    private static final Map<FontKey, Font> FONTS = new ConcurrentHashMap<>();
    private static final Map<String, Font> REGISTERED_FONTS = new ConcurrentHashMap<>();
    private static final Map<String, BufferedImage> TEXTURES = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong MINECRAFT_TEXTURE_REVISION = new java.util.concurrent.atomic.AtomicLong();
    private static final Map<Identifier, BufferedImage> MINECRAFT_TEXTURES = new ConcurrentHashMap<>();
    private static final int TEXTURE_CACHE_LIMIT = 64;

    private final Graphics2D graphics;
    private final BufferedImage target;
    /** Scratch for distance shadows, reused by every shadow in this paint. */
    private BufferedImage shadowMask;

    /**
     * Registers a TrueType or OpenType font under a stable family name. The font stays local to
     * Pane's rasterizer; it is not installed into the operating system. Passing that name to any
     * text method uses it on every platform, with {@link #SANS} as the safe fallback on failure.
     * Register during client setup before constructing retained UI. When replacing a font on the
     * client thread later, invalidate affected screens/HUDs; dialog measurement caches then refresh.
     */
    public static boolean registerFont(String family, InputStream source) {
        if (family == null || family.isBlank() || source == null) return false;
        try (InputStream input = source) {
            Font loaded = Font.createFont(Font.TRUETYPE_FONT, input);
            REGISTERED_FONTS.put(family, loaded);
            FONTS.clear();FONT_REVISION.incrementAndGet();
            return true;
        } catch (IOException | java.awt.FontFormatException ignored) {
            return false;
        }
    }

    /** Internal retained text-layout dependency; consumers invalidate after changing registered fonts. */
    static long fontRevision(){return FONT_REVISION.get();}

    /**
     * Optional CPU-only warmup for cold font initialization. Call on a worker during startup,
     * after registering custom fonts, rather than on the first visible HUD frame. No GPU access.
     */
    public static void prewarmFonts(String... families) {
        String[] selected = families == null || families.length == 0 ? new String[] {SANS, MONO} : families;
        var image = new BufferedImage(300, 80, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var canvas = new UiCanvas(graphics, image);
            for (String family : selected) for (int style : new int[] {Font.PLAIN, Font.BOLD})
                for (int size : new int[] {7, 9, 12, 18, 28})
                    canvas.text("Atlas 0123456789 / Ready", 0, 0, size, 0xFFFFFFFF, style, family);
        } finally { graphics.dispose(); }
    }

    UiCanvas(Graphics2D graphics) {
        this(graphics, null);
    }

    UiCanvas(Graphics2D graphics, BufferedImage target) {
        this.graphics = graphics;
        this.target = target;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // This image becomes a GPU texture and may be scaled by Minecraft. LCD sub-pixel text
        // assumes a final red/green/blue pixel layout, so it turns soft or colour-fringed after
        // that sampling step. Grayscale antialiasing remains sharp and neutral in the texture.
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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

    /** Outline the visible portion using a logical clip already mapped into drawing coordinates. */
    void clippedFocusRing(int color, float x, float y, float width, float height, float radius, Shape clip) {
        if (width <= 0 || height <= 0) return;
        var visible = new java.awt.geom.Area(roundShape(x + .6F, y + .6F,
            Math.max(0, width - 1.2F), Math.max(0, height - 1.2F), Math.max(0, radius - .6F)));
        var interior = new java.awt.geom.Area(clip);
        // Reserve a unit inside the polygon, including rotated viewport edges.
        interior.subtract(new java.awt.geom.Area(new BasicStroke(2F).createStrokedShape(clip)));
        visible.intersect(interior);
        graphics.setColor(awtColor(color));
        graphics.setStroke(new BasicStroke(1.2F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(visible);
    }

    /** Dashed rounded outline, useful for focus guides, drop targets, and cut-paper treatments. */
    public void dashedRoundRect(int color, float stroke, float dash, float x, float y,
                                float width, float height, float radius) {
        graphics.setColor(awtColor(color));
        float safeDash = Math.max(.5F, dash);
        graphics.setStroke(new BasicStroke(Math.max(.25F, stroke), BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_ROUND, 10, new float[]{safeDash, safeDash}, 0));
        float half = stroke / 2F;
        graphics.draw(roundShape(x + half, y + half, Math.max(0, width - stroke),
            Math.max(0, height - stroke), Math.max(0, radius - half)));
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

    /** Current device transform; GPU layers accept only plain context translations. */
    AffineTransform transform() { return graphics.getTransform(); }

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
        float[] grow = new float[steps];
        int[] alpha = new int[steps];
        for (int i = 1; i <= steps; i++) {
            grow[i - 1] = spread * i / steps;
            alpha[i - 1] = Math.max(1, Math.round(baseAlpha * (1F - (float) i / (steps + 1)) / steps * 2.2F));
        }
        if (distanceShadow(color, grow, alpha, x + offsetX, y + offsetY, width, height, radius)) return;
        for (int i = steps - 1; i >= 0; i--) {
            graphics.setColor(awtColor(alpha(color, alpha[i])));
            graphics.fill(roundShape(x - grow[i] + offsetX, y - grow[i] + offsetY,
                width + grow[i] * 2, height + grow[i] * 2, radius + grow[i]));
        }
    }

    /**
     * The rings share one colour and are offsets of one rounded rectangle, so stacking them is
     * equivalent to a single blend whose alpha, 1 - Π(1 - aᵢ·coverageᵢ), depends only on the
     * pixel's distance from that rectangle. Evaluates it once per visible device pixel instead of
     * antialiasing every ring. Rotated, sheared or unevenly scaled drawing keeps the ring path.
     */
    private boolean distanceShadow(int color, float[] grow, int[] alpha, float x, float y,
                                   float width, float height, float radius) {
        AffineTransform matrix = graphics.getTransform();
        double scale = matrix.getScaleX();
        // Group opacity applies per ring in the reference path, so it joins each ring's alpha here.
        if (!(graphics.getComposite() instanceof AlphaComposite composite) || composite.getRule() != AlphaComposite.SRC_OVER) return false;
        double opacity = composite.getAlpha();
        if (matrix.getShearX() != 0 || matrix.getShearY() != 0 || scale <= 0 || matrix.getScaleY() != scale
                || width <= 0 || height <= 0 || radius < 0 || radius > Math.min(width, height) / 2) return false;
        double halfW = width * scale / 2, halfH = height * scale / 2, corner = radius * scale;
        double centerX = (x + width / 2) * scale + matrix.getTranslateX();
        double centerY = (y + height / 2) * scale + matrix.getTranslateY();
        double reach = grow[grow.length - 1] * scale + 1;
        Rectangle area = new Rectangle((int) Math.floor(centerX - halfW - reach), (int) Math.floor(centerY - halfH - reach), 0, 0);
        area.add(Math.ceil(centerX + halfW + reach), Math.ceil(centerY + halfH + reach));
        Shape clip = graphics.getClip();
        Shape deviceClip = clip == null ? null : matrix.createTransformedShape(clip);
        if (deviceClip != null) area = area.intersection(deviceClip.getBounds());
        if (target != null) area = area.intersection(new Rectangle(target.getWidth(), target.getHeight()));
        if (area.isEmpty()) return true;
        // Alpha by signed distance in sixteenths of a device pixel; inside the first ring it is constant.
        int samples = (int) Math.ceil((reach + .5) * 16) + 1;
        int[] lookup = new int[samples];
        for (int i = 0; i < samples; i++) {
            double distance = i / 16D - .5, clear = 1;
            for (int ring = 0; ring < grow.length; ring++)
                clear *= 1 - opacity * alpha[ring] / 255D * Math.clamp(.5 - distance + grow[ring] * scale, 0, 1);
            lookup[i] = (int) Math.round((1 - clear) * 255) << 24 | color & 0xFFFFFF;
        }
        if (shadowMask == null || shadowMask.getWidth() < area.width || shadowMask.getHeight() < area.height)
            shadowMask = new BufferedImage(Math.max(area.width, shadowMask == null ? 0 : shadowMask.getWidth()),
                Math.max(area.height, shadowMask == null ? 0 : shadowMask.getHeight()), BufferedImage.TYPE_INT_ARGB);
        int stride = shadowMask.getWidth();
        int[] pixels = ((java.awt.image.DataBufferInt) shadowMask.getRaster().getDataBuffer()).getData();
        double innerW = halfW - corner, innerH = halfH - corner;
        for (int row = 0; row < area.height; row++) {
            double qy = Math.abs(area.y + row + .5 - centerY) - innerH, dy = Math.max(qy, 0);
            for (int column = 0; column < area.width; column++) {
                double qx = Math.abs(area.x + column + .5 - centerX) - innerW, dx = Math.max(qx, 0);
                double distance = Math.sqrt(dx * dx + dy * dy) + Math.min(Math.max(qx, qy), 0) - corner;
                pixels[row * stride + column] = lookup[(int) Math.clamp(Math.round((distance + .5) * 16), 0, samples - 1)];
            }
        }
        Graphics2D output = (Graphics2D) graphics.create();
        try {
            output.setTransform(new AffineTransform());
            output.setClip(deviceClip);
            output.setComposite(AlphaComposite.SrcOver);
            output.drawImage(shadowMask, area.x, area.y, area.x + area.width, area.y + area.height,
                0, 0, area.width, area.height, null);
        } finally { output.dispose(); }
        return true;
    }

    /** Coloured glow centred on a shape. Reads as light rather than shade. */
    public void glow(int color, float spread, float x, float y, float width, float height, float radius) {
        shadow(color, spread, 0, 0, x, y, width, height, radius);
    }

    /**
     * Blurs pixels already painted behind a region. This is intended for modal scrims and small
     * translucent overlays, not every card on a page. It is evaluated only when the retained
     * surface repaints and therefore has no idle-frame cost.
     *
     * @return false when no readable backing surface or visible blur region is available
     */
    public boolean backdropBlur(float radius, float x, float y, float width, float height) {
        return backdropBlur(radius, x, y, width, height, null);
    }

    boolean backdropBlur(float radius, float x, float y, float width, float height, UiBlurScratch blurScratch) {
        if (target == null || radius <= 0 || width <= 0 || height <= 0) return false;
        Rectangle pixels = graphics.getTransform().createTransformedShape(
            new Rectangle2D.Float(x, y, width, height)).getBounds();
        int left = Math.max(0, pixels.x), top = Math.max(0, pixels.y);
        int right = Math.min(target.getWidth(), pixels.x + pixels.width);
        int bottom = Math.min(target.getHeight(), pixels.y + pixels.height);
        int pixelWidth = right - left, pixelHeight = bottom - top;
        if (pixelWidth <= 0 || pixelHeight <= 0) return false;
        Shape clip = graphics.getClip();
        Shape deviceClip = clip == null ? null : graphics.getTransform().createTransformedShape(clip);
        if (deviceClip != null && !deviceClip.intersects(left, top, pixelWidth, pixelHeight)) return false;

        int maximumRadius = Math.max(1, (Math.min(pixelWidth, pixelHeight) - 1) / 2);
        int pixelRadius = Math.max(1, Math.min(Math.min(24, maximumRadius), Math.round(radius *
            (float) Math.max(Math.abs(graphics.getTransform().getScaleX()), Math.abs(graphics.getTransform().getScaleY())))));
        if (deviceClip != null) {
            Rectangle clipBounds = deviceClip.getBounds();
            left = Math.max(left, clipBounds.x - pixelRadius);
            top = Math.max(top, clipBounds.y - pixelRadius);
            right = Math.min(right, clipBounds.x + clipBounds.width + pixelRadius);
            bottom = Math.min(bottom, clipBounds.y + clipBounds.height + pixelRadius);
            pixelWidth = right - left; pixelHeight = bottom - top;
        }
        UiBlurScratch scratch = blurScratch == null ? new UiBlurScratch() : blurScratch;
        scratch.ensure(pixelWidth, pixelHeight);
        BufferedImage source = scratch.source;
        Graphics2D copyGraphics = source.createGraphics();
        try {
            copyGraphics.setComposite(AlphaComposite.Src);
            copyGraphics.drawImage(target, 0, 0, pixelWidth, pixelHeight, left, top, right, bottom, null);
        } finally { copyGraphics.dispose(); }

        int kernelSize = pixelRadius * 2 + 1;
        float[] weights = new float[kernelSize];
        java.util.Arrays.fill(weights, 1F / kernelSize);
        BufferedImage horizontal = scratch.horizontal;
        BufferedImage blurred = scratch.source;
        new ConvolveOp(new Kernel(kernelSize, 1, weights), ConvolveOp.EDGE_NO_OP, null).filter(source, horizontal);
        new ConvolveOp(new Kernel(1, kernelSize, weights), ConvolveOp.EDGE_NO_OP, null).filter(horizontal, blurred);

        Graphics2D output = (Graphics2D) graphics.create();
        try {
            output.setTransform(new AffineTransform());
            output.setClip(deviceClip);
            output.clipRect(left, top, pixelWidth, pixelHeight);
            output.drawImage(blurred, left, top, null);
        } finally {
            output.dispose();
        }
        return true;
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
        var boundaries = UiTextBoundary.index(value);
        for (int start = 0; start < value.length();) {
            int end = boundaries.next(start);
            String glyph = value.substring(start, end);
            graphics.drawString(glyph, cursor, baseline);
            cursor += metrics.stringWidth(glyph) + tracking;
            start = end;
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

    /**
     * Measures the horizontal advance of text drawn with {@link #SANS}. The result uses the same
     * size, style, antialiasing metrics, and one-sixteenth-pixel size quantisation as
     * {@link #text(String, float, float, float, int, int)}; {@code null} measures as zero.
     */
    public float textWidth(String value, float size, int style) {
        return textWidth(value, size, style, SANS);
    }

    /**
     * Measures the horizontal advance of text using the exact font selection used by
     * {@link #text(String, float, float, float, int, int, String)}. Pass the same {@code size},
     * {@code style}, and {@code family} to both calls when aligning a label. {@code family} may
     * be {@link #SANS}, {@link #SERIF}, {@link #MONO}, a system family, or a name registered with
     * {@link #registerFont(String, InputStream)}; a null or empty family falls back to
     * {@link #SANS}. {@code null} text measures as zero.
     */
    public float textWidth(String value, float size, int style, String family) {
        return graphics.getFontMetrics(font(size, style, family)).stringWidth(value == null ? "" : value);
    }

    UiShapedText shapeText(String value,float size,int style,String family,UiTextLayoutCache cache) {
        if(cache==null)return shapeText(value,size,style,family);
        return cache.get(new TextLayoutKey(value,font(size,style,family),graphics.getFontRenderContext(),-1,FONT_REVISION.get()),
            value.length(),()->shapeText(value,size,style,family));
    }
    List<UiShapedText.Line> wrapShapedText(String value,float width,float size,int style,String family,UiTextLayoutCache cache) {
        if(cache==null)return wrapShapedText(value,width,size,style,family);
        return cache.get(new TextLayoutKey(value,font(size,style,family),graphics.getFontRenderContext(),width,FONT_REVISION.get()),
            value.length(),()->wrapShapedText(value,width,size,style,family));
    }

    UiShapedText shapeText(String value,float size,int style,String family) {
        Font selected=font(size,style,family);
        float ascent=graphics.getFontMetrics(selected).getAscent();
        UiShapedText simple=UiShapedText.simpleRun(value,0,value.length(),selected,graphics.getFontRenderContext(),ascent);
        if(simple!=null)return simple;
        var layout=value.isEmpty()?null:new java.awt.font.TextLayout(value,selected,graphics.getFontRenderContext());
        return new UiShapedText(layout,value,0,value.length(),ascent);
    }

    List<UiShapedText.Line> wrapShapedText(String value,float width,float size,int style,String family) {
        Font selected=font(size,style,family);
        float ascent=graphics.getFontMetrics(selected).getAscent();
        var result=new ArrayList<UiShapedText.Line>();
        UiTextBoundary.Index boundaries=UiTextBoundary.index(value);
        int paragraphStart=0;
        while(paragraphStart<=value.length()) {
            int end=paragraphStart;
            while(end<value.length()&&value.charAt(end)!='\n'&&value.charAt(end)!='\r')end++;
            if(end==paragraphStart)result.add(new UiShapedText.Line(end,end,new UiShapedText(null,value,end,end,ascent,boundaries)));
            else {
                var attributed=new java.text.AttributedString(value.substring(paragraphStart,end));
                attributed.addAttribute(java.awt.font.TextAttribute.FONT,selected);
                var measurer=new java.awt.font.LineBreakMeasurer(attributed.getIterator(),
                    java.text.BreakIterator.getLineInstance(java.util.Locale.ROOT),graphics.getFontRenderContext());
                while(measurer.getPosition()<end-paragraphStart) {
                    int start=paragraphStart+measurer.getPosition();
                    var layout=measurer.nextLayout(Math.max(.01F,width));
                    int next=paragraphStart+measurer.getPosition();
                    // Even a tiny viewport must consume one complete grapheme, never half an emoji/mark sequence.
                    if(!boundaries.contains(next)) {
                        next=boundaries.ceil(next);
                        measurer.setPosition(next-paragraphStart);
                        layout=new java.awt.font.TextLayout(value.substring(start,next),selected,graphics.getFontRenderContext());
                    }
                    // Reuse the layout that chose the line break; do not shape each line twice.
                    result.add(new UiShapedText.Line(start,next,new UiShapedText(layout,value,start,next,ascent,boundaries)));
                }
            }
            if(end==value.length())break;
            paragraphStart=boundaries.next(end);
        }
        return List.copyOf(result);
    }

    void shapedText(UiShapedText text,float x,float top,int color) {
        graphics.setColor(awtColor(color));
        if(text.glyphs!=null)graphics.drawGlyphVector(text.glyphs,x,top+text.ascent);
        else if(text.layout!=null)text.layout.draw(graphics,x,top+text.ascent);
    }

    void shapedSelection(UiShapedText text,int from,int to,float x,float top,float height,int color) {
        if((text.layout==null&&text.glyphs==null)||to<=from)return;
        Graphics2D painter=(Graphics2D)graphics.create();
        try {
            painter.clip(new Rectangle2D.Float(x,top,Math.max(1,text.width()),height));
            painter.translate(x,top+text.ascent);painter.setColor(awtColor(color));
            if(text.glyphs!=null) {
                var logical=text.glyphs.getLogicalBounds().getBounds2D();
                painter.fill(new Rectangle2D.Float(text.x(from),(float)logical.getY(),Math.max(0,text.x(to)-text.x(from)),(float)logical.getHeight()));
            } else painter.fill(text.layout.getLogicalHighlightShape(from,to));
        } finally {painter.dispose();}
    }

    /** Height of the same font metrics used by text(), in layout units. */
    public float textHeight(float size, int style, String family) {
        return graphics.getFontMetrics(font(size, style, family)).getHeight();
    }

    /** Advance of {@link #trackedText}: each grapheme is measured exactly as it is drawn. */
    public float trackedTextWidth(String value, float size, int style, String family, float tracking) {
        if (value == null || value.isEmpty()) return 0;
        FontMetrics metrics = graphics.getFontMetrics(font(size, style, family));
        var boundaries = UiTextBoundary.index(value);
        float width = -tracking;
        for (int start = 0; start < value.length();) {
            int end = boundaries.next(start);
            width += metrics.stringWidth(value.substring(start, end)) + tracking;
            start = end;
        }
        return width;
    }

    /**
     * Draws prepared Minecraft-style text while keeping ordinary {@link #text} calls literal.
     * Colors, bold, italic, underline, strikethrough, obfuscation, newlines, and Unicode code
     * points are preserved. The returned value is the widest painted line.
     */
    public float formattedText(UiRichText value, float x, float top, float size, int fallbackColor) {
        return formattedText(value, x, top, size, size * 1.25F, fallbackColor, SANS);
    }

    public float formattedText(UiRichText value, float x, float top, float size,
                               int fallbackColor, String family) {
        return formattedText(value, x, top, size, size * 1.25F, fallbackColor, family);
    }

    /** Draws explicit newlines with a caller-selected line height. */
    public float formattedText(UiRichText value, float x, float top, float size, float lineHeight,
                               int fallbackColor, String family) {
        if (value == null || value.isEmpty()) return 0;
        float step = Math.max(size, lineHeight);
        float lineTop = top;
        float cursor = x;
        float widest = 0;
        for (UiRichText.Span span : value.spans()) {
            String[] lines = span.text().split("\\R", -1);
            for (int line = 0; line < lines.length; line++) {
                if (line > 0) {
                    widest = Math.max(widest, cursor - x);
                    cursor = x;
                    lineTop += step;
                }
                if (!lines[line].isEmpty()) {
                    cursor += drawFormattedSpan(lines[line], span, cursor, lineTop, size,
                        fallbackColor, family);
                }
            }
        }
        return Math.max(widest, cursor - x);
    }

    /** Measures the widest explicit line using the same fonts as {@link #formattedText}. */
    public float formattedTextWidth(UiRichText value, float size, String family) {
        if (value == null || value.isEmpty()) return 0;
        float lineWidth = 0;
        float widest = 0;
        for (UiRichText.Span span : value.spans()) {
            String[] lines = span.text().split("\\R", -1);
            for (int line = 0; line < lines.length; line++) {
                if (line > 0) {
                    widest = Math.max(widest, lineWidth);
                    lineWidth = 0;
                }
                lineWidth += formattedSpanWidth(lines[line], span, size, family);
            }
        }
        return Math.max(widest, lineWidth);
    }

    /**
     * Returns a one-line styled value shortened with an ellipsis. Cache the result with prepared
     * row data when the width is stable; it does not need to be recomputed during scrolling.
     */
    public UiRichText ellipsizeFormatted(UiRichText value, float maxWidth, float size, String family) {
        if (value == null || value.isEmpty()) return UiRichText.plain("");
        if (formattedTextWidth(value, size, family) <= maxWidth && !value.plainText().contains("\n")
                && !value.plainText().contains("\r")) return value;
        List<UiRichText.Span> kept = new ArrayList<>();
        float width = 0;
        boolean cut = false;
        outer:
        for (UiRichText.Span span : value.spans()) {
            StringBuilder run = new StringBuilder();
            UiRichText.Span ellipsisStyle = new UiRichText.Span("…", span.color(), span.bold(),
                span.italic(), span.underlined(), span.strikethrough(), false);
            float ellipsisWidth = formattedSpanWidth("…", ellipsisStyle, size, family);
            for (int offset = 0; offset < span.text().length();) {
                int codePoint = span.text().codePointAt(offset);
                if (codePoint == '\n' || codePoint == '\r') {
                    cut = true;
                    break outer;
                }
                String glyph = new String(Character.toChars(codePoint));
                float glyphWidth = formattedSpanWidth(glyph, span, size, family);
                if (ellipsisWidth > Math.max(0, maxWidth)) return UiRichText.plain("");
                if (width + glyphWidth + ellipsisWidth > Math.max(0, maxWidth)) {
                    if (!run.isEmpty()) kept.add(copySpan(span, run.toString()));
                    kept.add(ellipsisStyle);
                    cut = true;
                    break outer;
                }
                run.append(glyph);
                width += glyphWidth;
                offset += Character.charCount(codePoint);
            }
            if (!run.isEmpty()) kept.add(copySpan(span, run.toString()));
        }
        if (!cut) return value;
        return UiRichText.of(kept);
    }

    private float drawFormattedSpan(String text, UiRichText.Span span, float x, float top,
                                    float size, int fallbackColor, String family) {
        int style = (span.bold() ? Font.BOLD : Font.PLAIN) | (span.italic() ? Font.ITALIC : Font.PLAIN);
        Font font = font(size, style, family);
        FontMetrics metrics = graphics.getFontMetrics(font);
        float width = metrics.stringWidth(text);
        int color = span.color() == null ? fallbackColor
            : (fallbackColor & 0xFF000000) | (span.color() & 0x00FFFFFF);
        float baseline = top + metrics.getAscent();
        graphics.setFont(font);
        graphics.setColor(awtColor(color));
        graphics.drawString(text, x, baseline);
        float stroke = Math.max(.65F, size / 14F);
        graphics.setStroke(new BasicStroke(stroke));
        if (span.underlined()) {
            float underline = baseline + Math.max(1, metrics.getDescent() * .2F);
            graphics.draw(new Line2D.Float(x, underline, x + width, underline));
        }
        if (span.strikethrough()) {
            float strike = baseline - metrics.getAscent() * .32F;
            graphics.draw(new Line2D.Float(x, strike, x + width, strike));
        }
        return width;
    }

    private float formattedSpanWidth(String text, UiRichText.Span span, float size, String family) {
        int style = (span.bold() ? Font.BOLD : Font.PLAIN) | (span.italic() ? Font.ITALIC : Font.PLAIN);
        return graphics.getFontMetrics(font(size, style, family)).stringWidth(text);
    }

    private static UiRichText.Span copySpan(UiRichText.Span span, String text) {
        return new UiRichText.Span(text, span.color(), span.bold(), span.italic(), span.underlined(),
            span.strikethrough(), span.obfuscated());
    }

    /** Truncates with an ellipsis so a label never overruns its box. */
    public String ellipsize(String value, float maxWidth, float size, int style, String family) {
        if (value == null || value.isEmpty() || textWidth(value, size, style, family) <= maxWidth) return value;
        String suffix = "…";
        var boundaries = UiTextBoundary.index(value);
        int end = boundaries.previous(value.length());
        while (end > 0 && textWidth(value.substring(0, end) + suffix, size, style, family) > maxWidth) end = boundaries.previous(end);
        return end <= 0 ? suffix : value.substring(0, end) + suffix;
    }

    /**
     * Wraps text at words while preserving explicit newlines. A word wider than the measure is
     * split safely, so user-provided text can never force a layout wider than its container.
     */
    public List<String> wrapText(String value, float maxWidth, float size, int style, String family) {
        List<String> lines = new ArrayList<>();
        if (value == null || value.isEmpty()) return lines;
        float width = Math.max(1, maxWidth);
        for (String paragraph : value.split("\\R", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (textWidth(candidate, size, style, family) <= width) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (textWidth(word, size, style, family) <= width) {
                    line.append(word);
                    continue;
                }
                StringBuilder piece = new StringBuilder();
                for (int offset = 0; offset < word.length();) {
                    int codePoint = word.codePointAt(offset);
                    String glyph = new String(Character.toChars(codePoint));
                    if (!piece.isEmpty() && textWidth(piece + glyph, size, style, family) > width) {
                        lines.add(piece.toString());
                        piece.setLength(0);
                    }
                    piece.append(glyph);
                    offset += Character.charCount(codePoint);
                }
                line.append(piece);
            }
            if (!line.isEmpty()) lines.add(line.toString());
        }
        return List.copyOf(lines);
    }

    public List<String> wrapText(String value, float maxWidth, float size, int style) {
        return wrapText(value, maxWidth, size, style, SANS);
    }

    /** Draws wrapped text and returns the height consumed. */
    public float wrappedText(String value, float x, float top, float maxWidth, float size,
                             float lineHeight, int color, int style, String family) {
        List<String> lines = wrapText(value, maxWidth, size, style, family);
        float step = Math.max(size, lineHeight);
        for (int i = 0; i < lines.size(); i++) text(lines.get(i), x, top + i * step, size, color, style, family);
        return lines.isEmpty() ? 0 : lines.size() * step;
    }

    // ---------------------------------------------------------------- scoped state

    public void withClip(float x, float y, float width, float height, Runnable painter) {
        withClipShape(new Rectangle2D.Float(x, y, Math.max(0, width), Math.max(0, height)), painter);
    }

    /** Clips to a rounded rectangle, for masking content inside a card. */
    public void withRoundedClip(float x, float y, float width, float height, float radius, Runnable painter) {
        withClipShape(roundShape(x, y, Math.max(0, width), Math.max(0, height), radius), painter);
    }

    /** Clips to a circle, for avatars and badges. */
    public void withCircleClip(float centerX, float centerY, float radius, Runnable painter) {
        withClipShape(new Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2), painter);
    }

    private void withClipShape(Shape shape, Runnable painter) {
        Shape old = graphics.getClip();
        graphics.clip(shape);
        try { painter.run(); } finally { graphics.setClip(old); }
    }

    /** Rotates everything drawn inside, in degrees clockwise about a pivot. */
    public void withRotation(float degrees, float pivotX, float pivotY, Runnable painter) {
        withMatrix(g -> g.rotate(Math.toRadians(degrees), pivotX, pivotY), painter);
    }

    /** Scales everything drawn inside about a pivot. */
    public void withScale(float scaleX, float scaleY, float pivotX, float pivotY, Runnable painter) {
        withMatrix(g -> { g.translate(pivotX, pivotY); g.scale(scaleX, scaleY); g.translate(-pivotX, -pivotY); }, painter);
    }

    /** Translates everything drawn inside. Used by scroll containers and custom viewports. */
    public void withTranslation(float offsetX, float offsetY, Runnable painter) {
        withMatrix(g -> g.translate(offsetX, offsetY), painter);
    }

    private void withMatrix(java.util.function.Consumer<Graphics2D> change, Runnable painter) {
        AffineTransform old = graphics.getTransform();
        change.accept(graphics);
        try { painter.run(); } finally { graphics.setTransform(old); }
    }

    /** Multiplies opacity per drawing operation. Use withLayer for overlapping shapes as one group. */
    public void withOpacity(float opacity, Runnable painter) {
        Composite old = graphics.getComposite();
        float inherited = old instanceof AlphaComposite alpha ? alpha.getAlpha() : 1;
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
            inherited * Math.max(0F, Math.min(1F, opacity))));
        try {
            painter.run();
        } finally {
            graphics.setComposite(old);
        }
    }

    /**
     * Paints overlapping shapes into a reusable bounded layer, then fades the result once.
     * Bounds include shadows; coordinates and clipping match this canvas. No blur is implied.
     */
    public void withLayer(UiLayer layer, float opacity, UiRect bounds, java.util.function.Consumer<UiCanvas> painter) {
        if (layer == null || painter == null || opacity <= 0 || bounds.width() <= 0 || bounds.height() <= 0) return;
        Rectangle pixels = graphics.getTransform().createTransformedShape(
            new Rectangle2D.Float(bounds.x(), bounds.y(), bounds.width(), bounds.height())).getBounds();
        if (target != null) pixels = pixels.intersection(new Rectangle(0, 0, target.getWidth(), target.getHeight()));
        if (pixels.width <= 0 || pixels.height <= 0) return;
        Shape existingClip = graphics.getClip();
        if (existingClip != null && !graphics.getTransform().createTransformedShape(existingClip).intersects(pixels)) return;
        var image = layer.prepare(pixels.width, pixels.height);
        Graphics2D child = image.createGraphics();
        try {
            child.translate(-pixels.x, -pixels.y);
            child.transform(graphics.getTransform());
            Shape clip = graphics.getClip();
            if (clip != null) child.clip(clip);
            painter.accept(new UiCanvas(child, image));
        } finally { child.dispose(); }
        Graphics2D output = (Graphics2D) graphics.create();
        try {
            output.setTransform(new AffineTransform());
            float inherited = graphics.getComposite() instanceof AlphaComposite alpha ? alpha.getAlpha() : 1;
            output.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                inherited * Math.max(0, Math.min(1, opacity))));
            output.drawImage(image, pixels.x, pixels.y, null);
        } finally { output.dispose(); }
    }

    // ---------------------------------------------------------------- Minecraft textures

    /** Draw caller-owned pixels with explicit fit/filter and return the exact source-point mapping. */
    public UiImagePlacement image(BufferedImage image,UiRect viewport,UiImageOptions options) {
        java.util.Objects.requireNonNull(image,"image");
        var placement=UiImagePlacement.fit(image.getWidth(),image.getHeight(),viewport,options);
        var output=(Graphics2D)graphics.create();
        try {
            output.clip(new Rectangle2D.Float(viewport.x(),viewport.y(),viewport.width(),viewport.height()));
            output.setRenderingHint(RenderingHints.KEY_INTERPOLATION,interpolation(options.sampling()));
            var box=placement.imageBounds();
            var transform=new AffineTransform();transform.translate(box.x(),box.y());
            transform.scale(box.width()/image.getWidth(),box.height()/image.getHeight());
            output.drawImage(image,transform,null);
        } finally {output.dispose();}
        return placement;
    }

    /** Resource/dynamic texture version of image; missing assets return empty using the existing cache. */
    public java.util.Optional<UiImagePlacement> minecraftImage(Identifier texture,UiRect viewport,UiImageOptions options) {
        var image=minecraftTextureImage(texture);
        return image==null?java.util.Optional.empty():java.util.Optional.of(image(image,viewport,options));
    }

    private static Object interpolation(UiImageOptions.Sampling sampling) {
        return switch(java.util.Objects.requireNonNull(sampling)) {
            case NEAREST->RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
            case BILINEAR->RenderingHints.VALUE_INTERPOLATION_BILINEAR;
            case BICUBIC->RenderingHints.VALUE_INTERPOLATION_BICUBIC;
        };
    }

    /** Transforms drawing around a pivot. Interactive consumers should use UiContext.withTransform. */
    public void withTransform(UiTransform transform, float pivotX, float pivotY, Runnable painter) {
        withMatrix(g -> g.transform(transform.matrix(pivotX, pivotY)), () -> withOpacity(transform.opacity(), painter));
    }

    /**
     * Reuses drawing at a caller-owned revision, transforming the cached pixels on later paints.
     * Keep bounds size stable and change revision whenever content changes. Drawing only: register
     * interactive targets through the surrounding UiContext on every paint.
     * Transparent margins are trimmed internally; drawing coordinates and caller bounds stay fixed.
     */
    public void cachedLayer(UiLayer layer, Object revision, UiRect bounds, java.util.function.Consumer<UiCanvas> painter) {
        if (layer == null || painter == null || bounds.width() <= 0 || bounds.height() <= 0) return;
        var matrix = graphics.getTransform();
        float quality = Math.max(1, Math.min(4, Math.round(Math.max(
            Math.hypot(matrix.getScaleX(), matrix.getShearY()), Math.hypot(matrix.getShearX(), matrix.getScaleY())))));
        int w = Math.max(1, (int) Math.ceil(bounds.width() * quality));
        int h = Math.max(1, (int) Math.ceil(bounds.height() * quality));
        if (!layer.reuse(w, h, revision)) {
            var child = layer.image().createGraphics();
            try {
                child.scale(quality, quality); child.translate(-bounds.x(), -bounds.y());
                painter.accept(new UiCanvas(child, layer.image()));
            } finally { child.dispose(); }
            layer.finishCache();
        }
        if (layer.cachedImage() == null) return;
        var previous = graphics.getTransform();
        graphics.translate(bounds.x(), bounds.y());
        graphics.scale(bounds.width() / w, bounds.height() / h);
        try { graphics.drawImage(layer.cachedImage(), layer.cachedX(), layer.cachedY(), null); }
        finally { graphics.setTransform(previous); }
    }

    /**
     * Paints a Minecraft texture as a cover fill. The image keeps its aspect ratio and crops at
     * the edges if needed; wrap this in {@link #withRoundedClip} or {@link #withCircleClip} to
     * make a rounded texture card or circular avatar.
     *
     * @return {@code true} when the texture was available, or {@code false} when it could not be
     * read (for example, while a dynamic skin is still downloading).
     */
    public boolean minecraftTexture(Identifier texture, float x, float y, float width, float height) {
        BufferedImage image = minecraftTextureImage(texture);
        if (image == null || width <= 0 || height <= 0) return false;
        float sourceAspect = image.getWidth() / (float) image.getHeight();
        float targetAspect = width / height;
        int sourceX = 0, sourceY = 0, sourceW = image.getWidth(), sourceH = image.getHeight();
        if (sourceAspect > targetAspect) {
            sourceW = Math.max(1, Math.round(sourceH * targetAspect));
            sourceX = (image.getWidth() - sourceW) / 2;
        } else if (sourceAspect < targetAspect) {
            sourceH = Math.max(1, Math.round(sourceW / targetAspect));
            sourceY = (image.getHeight() - sourceH) / 2;
        }
        drawMinecraftImage(image, sourceX, sourceY, sourceW, sourceH, x, y, width, height, UiImageOptions.Sampling.NEAREST);
        return true;
    }

    /**
     * Paints an exact rectangular crop from a Minecraft texture. It is useful for skin faces:
     * vanilla player skins use {@code (8, 8, 8, 8)} for the base head and {@code (40, 8, 8, 8)}
     * for its outer hat layer.
     */
    public boolean minecraftTextureRegion(Identifier texture, int sourceX, int sourceY, int sourceWidth,
                                          int sourceHeight, float x, float y, float width, float height) {
        return minecraftTextureRegion(texture,sourceX,sourceY,sourceWidth,sourceHeight,x,y,width,height,UiImageOptions.Sampling.NEAREST);
    }

    /** Exact source crop with explicit filtering; the existing overload remains nearest-neighbor. */
    public boolean minecraftTextureRegion(Identifier texture, int sourceX, int sourceY, int sourceWidth,
                                          int sourceHeight, float x, float y, float width, float height,UiImageOptions.Sampling sampling) {
        BufferedImage image = minecraftTextureImage(texture);
        if (image == null || sourceWidth <= 0 || sourceHeight <= 0 || width <= 0 || height <= 0) return false;
        int safeX = Math.max(0, Math.min(sourceX, image.getWidth() - 1));
        int safeY = Math.max(0, Math.min(sourceY, image.getHeight() - 1));
        int safeW = Math.max(1, Math.min(sourceWidth, image.getWidth() - safeX));
        int safeH = Math.max(1, Math.min(sourceHeight, image.getHeight() - safeY));
        drawMinecraftImage(image, safeX, safeY, safeW, safeH, x, y, width, height,sampling);
        return true;
    }

    /** Drops decoded resource/skin pixels and refreshes retained screens/HUDs on their next frame.
     * Resource-pack reloads call this automatically. */
    public static void clearMinecraftTextureCache() {
        MINECRAFT_TEXTURES.clear();
        MINECRAFT_TEXTURE_REVISION.incrementAndGet();
    }

    /** Call after updating a dynamic texture under the same identifier. Does not re-upload it. */
    public static void invalidateMinecraftTexture(Identifier texture) {
        if (texture == null) return;
        MINECRAFT_TEXTURES.remove(texture);
        MINECRAFT_TEXTURE_REVISION.incrementAndGet();
    }

    /** Include this revision in consumer-owned cached-layer keys when they contain Minecraft images. */
    public static long minecraftTextureRevision() { return MINECRAFT_TEXTURE_REVISION.get(); }

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

    private void drawMinecraftImage(BufferedImage image, int sourceX, int sourceY, int sourceWidth,
                                    int sourceHeight, float x, float y, float width, float height,UiImageOptions.Sampling sampling) {
        Object old = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation(sampling));
        try {
            graphics.drawImage(image, Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height),
                sourceX, sourceY, sourceX + sourceWidth, sourceY + sourceHeight, null);
        } finally {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                old == null ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : old);
        }
    }

    private static BufferedImage minecraftTextureImage(Identifier texture) {
        if (texture == null) return null;
        BufferedImage cached = MINECRAFT_TEXTURES.get(texture);
        if (cached != null) return cached;
        BufferedImage image = readMinecraftResource(texture);
        if (image == null) image = readDynamicMinecraftTexture(texture);
        if (image != null) {
            if (MINECRAFT_TEXTURES.size() >= TEXTURE_CACHE_LIMIT) MINECRAFT_TEXTURES.clear();
            MINECRAFT_TEXTURES.put(texture, image);
        }
        return image;
    }

    private static BufferedImage readMinecraftResource(Identifier texture) {
        try (InputStream input = Minecraft.getInstance().getResourceManager().open(texture)) {
            return ImageIO.read(input);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static BufferedImage readDynamicMinecraftTexture(Identifier texture) {
        try {
            if (!(Minecraft.getInstance().getTextureManager().getTexture(texture) instanceof DynamicTexture dynamic)) return null;
            var pixels = dynamic.getPixels();
            if (pixels == null || pixels.isClosed()) return null;
            int width = pixels.getWidth(), height = pixels.getHeight();
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int[] target = pixels.getPixelsABGR();
            for (int i = 0; i < target.length; i++) target[i] = UiPixelTransfer.swapRedBlue(target[i]);
            image.setRGB(0, 0, width, height, target, 0, width);
            return image;
        } catch (RuntimeException ignored) {
            return null;
        }
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
        var key = new FontKey(resolved, style, Math.round(quantised * 16F));
        Font cached = FONTS.get(key);
        if (cached != null) return cached;
        if (FONTS.size() >= FONT_CACHE_LIMIT) FONTS.clear();
        Font registered = REGISTERED_FONTS.get(resolved);
        Font font = registered == null ? new Font(resolved, style, 1).deriveFont(quantised)
            : registered.deriveFont(style, quantised);
        FONTS.put(key, font);
        return font;
    }

    private static int channel(int value, int shift) { return value >>> shift & 0xFF; }
}
