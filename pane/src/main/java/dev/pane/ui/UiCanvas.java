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
    /** Logical serif family, always available. */
    public static final String SERIF = Font.SERIF;
    /** Logical monospaced family, always available. */
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
    /** A group layer's parent and where the layer sits in it, so glass and blur inside the group see what lies behind it. */
    private UiCanvas backdropParent;
    private int backdropX, backdropY;
    /** Device rectangles that glass and backdrop blur read on the screen surface; partial repaints grow to cover them. */
    List<Rectangle> backdropReads;
    /** Lenses kept between paints of the screen surface. */
    UiGlassCache glassCache;

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

    /** Fills a rectangle with an ARGB colour. */
    public void fill(int color, float x, float y, float width, float height) {
        graphics.setColor(awtColor(color));
        UiBands.fill(graphics, new Rectangle2D.Float(x, y, width, height));
    }

    /** Fills a rectangle with corners rounded to {@code radius}. */
    public void roundRect(int color, float x, float y, float width, float height, float radius) {
        graphics.setColor(awtColor(color));
        fillStraightSides(roundShape(x, y, width, height, radius));
    }

    /** Strokes a rounded rectangle with the current stroke and paint; only the rows between its corners have straight sides. */
    private void strokeStraightSides(RoundRectangle2D.Float shape) {
        float corner = Math.min(shape.archeight / 2, shape.height / 2);
        UiBands.stroke(graphics, shape, shape.y + corner, shape.y + shape.height - corner);
    }

    /** Fills a rounded rectangle with the current paint; only the rows between its corners have straight sides. */
    private void fillStraightSides(RoundRectangle2D.Float shape) {
        float corner = Math.min(shape.archeight / 2, shape.height / 2);
        UiBands.fill(graphics, shape, shape.y + corner, shape.y + shape.height - corner);
    }

    /** Rounded rectangle with independent corner radii, clockwise from the top left. */
    public void roundRect(int color, float x, float y, float width, float height,
                          float topLeft, float topRight, float bottomRight, float bottomLeft) {
        graphics.setColor(awtColor(color));
        float limit = Math.min(width, height) / 2;
        UiBands.fill(graphics, cornerShape(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft),
            y + Math.clamp(Math.max(topLeft, topRight), 0, limit), y + height - Math.clamp(Math.max(bottomLeft, bottomRight), 0, limit));
    }

    /** Outlines a rounded rectangle; the stroke lies wholly inside the given bounds. */
    public void strokeRoundRect(int color, float stroke, float x, float y, float width, float height, float radius) {
        graphics.setColor(awtColor(color));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float half = stroke / 2F;
        strokeStraightSides(roundShape(x + half, y + half, width - stroke, height - stroke, Math.max(0, radius - half)));
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

    /** Focus outline following an arbitrary shape: a ring one to 2.4 units outside it. */
    void shapeFocusRing(int color, Shape outline) {
        var ring = new java.awt.geom.Area(new BasicStroke(4.8F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(outline));
        ring.subtract(new java.awt.geom.Area(new BasicStroke(2F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(outline)));
        ring.subtract(new java.awt.geom.Area(outline));
        graphics.setColor(awtColor(color));
        graphics.fill(ring);
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

    /** Draws a straight line {@code stroke} units wide. */
    public void line(int color, float stroke, float x1, float y1, float x2, float y2) {
        graphics.setColor(awtColor(color));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    /** Draws a dashed line with dashes and gaps {@code dash} units long. */
    public void dashedLine(int color, float stroke, float dash, float x1, float y1, float x2, float y2) {
        graphics.setColor(awtColor(color));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
            10, new float[]{dash, dash}, 0));
        graphics.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    /** Fills a circle. */
    public void circle(int color, float centerX, float centerY, float radius) {
        graphics.setColor(awtColor(color));
        graphics.fill(new Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2));
    }

    /** Outlines a circle. */
    public void strokeCircle(int color, float stroke, float centerX, float centerY, float radius) {
        graphics.setColor(awtColor(color));
        graphics.setStroke(new BasicStroke(stroke));
        graphics.draw(new Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2));
    }

    /** Fills a closed polygon from x, y pairs. */
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
        fillStraightSides(roundShape(x, y, width, height, radius));
        graphics.setPaint(awtColor(0xFF000000));
    }

    /** Fills a rounded rectangle with a radial ramp from its centre outwards. */
    public void radialRect(int inner, int outer, float centerX, float centerY, float radius,
                           float x, float y, float width, float height, float cornerRadius) {
        if (radius <= 0) return;
        graphics.setPaint(new RadialGradientPaint(new Point2D.Float(centerX, centerY), radius,
            new float[]{0F, 1F}, new Color[]{awtColor(inner), awtColor(outer)},
            MultipleGradientPaint.CycleMethod.NO_CYCLE));
        fillStraightSides(roundShape(x, y, width, height, cornerRadius));
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
        if (distanceShadow(color, grow, alpha, x + offsetX, y + offsetY, width, height, radius, null)) return;
        for (int i = steps - 1; i >= 0; i--) {
            graphics.setColor(awtColor(alpha(color, alpha[i])));
            graphics.fill(roundShape(x - grow[i] + offsetX, y - grow[i] + offsetY,
                width + grow[i] * 2, height + grow[i] * 2, radius + grow[i]));
        }
    }

    /**
     * {@link #shadow} drawn only outside the shape itself, for glass and for panels painted over it.
     * Large shapes are much cheaper: the covered interior is never evaluated or blended. Rotated,
     * sheared or unevenly scaled drawing clips the ordinary shadow instead.
     */
    public void shadowOutside(int color, float spread, float offsetX, float offsetY,
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
        if (distanceShadow(color, grow, alpha, x + offsetX, y + offsetY, width, height, radius,
            new Rectangle2D.Float(x, y, width, height))) return;
        var outside = new java.awt.geom.Area(new Rectangle2D.Float(x - spread * 2 + Math.min(0, offsetX), y - spread * 2 + Math.min(0, offsetY),
            width + spread * 4 + Math.abs(offsetX), height + spread * 4 + Math.abs(offsetY)));
        outside.subtract(new java.awt.geom.Area(roundShape(x, y, width, height, radius)));
        withClipShape(outside, () -> shadow(color, spread, offsetX, offsetY, x, y, width, height, radius));
    }

    /**
     * The rings share one colour and are offsets of one rounded rectangle, so stacking them is
     * equivalent to a single blend whose alpha, 1 - Π(1 - aᵢ·coverageᵢ), depends only on the
     * pixel's distance from that rectangle. Evaluates it once per visible device pixel instead of
     * antialiasing every ring. Rotated, sheared or unevenly scaled drawing keeps the ring path.
     */
    private boolean distanceShadow(int color, float[] grow, int[] alpha, float x, float y,
                                   float width, float height, float radius, Rectangle2D.Float caster) {
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
        Shape deviceClip = deviceClip(clip, matrix);
        if (target != null) area = area.intersection(new Rectangle(target.getWidth(), target.getHeight()));
        // Outside-only shadows keep whole bands, so cached bands match whatever a later paint clips to.
        if (deviceClip != null && (caster == null || glassCache == null)) area = area.intersection(deviceClip.getBounds());
        if (area.isEmpty() || deviceClip != null && !deviceClip.intersects(area)) return true;
        // Alpha by signed distance in sixteenths of a device pixel; inside the first ring it is constant.
        int samples = (int) Math.ceil((reach + .5) * 16) + 1;
        int[] lookup = new int[samples];
        for (int i = 0; i < samples; i++) {
            double distance = i / 16D - .5, clear = 1;
            for (int ring = 0; ring < grow.length; ring++)
                clear *= 1 - opacity * alpha[ring] / 255D * Math.clamp(.5 - distance + grow[ring] * scale, 0, 1);
            lookup[i] = (int) Math.round((1 - clear) * 255) << 24 | color & 0xFFFFFF;
        }
        if (caster != null) {
            outsideShadow(matrix, caster, radius * scale, area, lookup, samples, centerX, centerY, halfW - corner, halfH - corner, corner, deviceClip);
            return true;
        }
        if (shadowMask == null && glassCache != null) shadowMask = glassCache.shadowMask;
        if (shadowMask == null || shadowMask.getWidth() < area.width || shadowMask.getHeight() < area.height) {
            shadowMask = new BufferedImage(Math.max(area.width, shadowMask == null ? 0 : shadowMask.getWidth()),
                Math.max(area.height, shadowMask == null ? 0 : shadowMask.getHeight()), BufferedImage.TYPE_INT_ARGB);
            if (glassCache != null) glassCache.shadowMask = shadowMask;
        }
        int stride = shadowMask.getWidth();
        int[] pixels = ((java.awt.image.DataBufferInt) shadowMask.getRaster().getDataBuffer()).getData();
        double innerW = halfW - corner, innerH = halfH - corner;
        for (int row = 0; row < area.height; row++) {
            double offsetY = Math.abs(area.y + row + .5 - centerY), qy = offsetY - innerH, dy = Math.max(qy, 0);
            // Pixel centres this far inside every straight edge all share the first lookup entry.
            double interior = corner < .5 ? -1 : qy <= 0 ? halfW - .5 : offsetY <= halfH - .5 ? innerW : -1;
            int spanStart = interior < 0 ? area.width : Math.max(0, (int) Math.ceil(centerX - interior - area.x - .5));
            int spanEnd = interior < 0 ? -1 : Math.min(area.width - 1, (int) Math.floor(centerX + interior - area.x - .5));
            for (int column = 0; column < area.width; column++) {
                if (column == spanStart && spanEnd >= spanStart) {
                    java.util.Arrays.fill(pixels, row * stride + column, row * stride + spanEnd + 1, lookup[0]);
                    column = spanEnd;
                    continue;
                }
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
            UiBands.image(output, shadowMask.getSubimage(0, 0, area.width, area.height), area.x, area.y);
        } finally { output.dispose(); }
        return true;
    }

    /**
     * Evaluates and blends only the bands of {@code area} around the caster's surely-covered core,
     * scaling each pixel by how much of it lies outside the caster's rounded outline. On the screen
     * surface each band is kept between paints and reused while the geometry is unchanged.
     */
    private void outsideShadow(AffineTransform matrix, Rectangle2D.Float caster, double casterCorner, Rectangle area,
                               int[] lookup, int samples, double centerX, double centerY, double innerW, double innerH,
                               double corner, Shape deviceClip) {
        double scale = matrix.getScaleX();
        double cx = (caster.x + caster.width / 2) * scale + matrix.getTranslateX(), cy = (caster.y + caster.height / 2) * scale + matrix.getTranslateY();
        double chw = caster.width * scale / 2, chh = caster.height * scale / 2, cInnerW = chw - casterCorner, cInnerH = chh - casterCorner;
        // Inside the caster inset by its corner radius every pixel is covered.
        Rectangle core = new Rectangle((int) Math.ceil(cx - chw + casterCorner + 1), (int) Math.ceil(cy - chh + casterCorner + 1), 0, 0);
        core.add(Math.floor(cx + chw - casterCorner - 1), Math.floor(cy + chh - casterCorner - 1));
        core = core.intersection(area);
        List<Rectangle> bands = new ArrayList<>(4);
        if (core.isEmpty()) bands.add(area);
        else {
            bands.add(new Rectangle(area.x, area.y, area.width, core.y - area.y));
            bands.add(new Rectangle(area.x, core.y + core.height, area.width, area.y + area.height - core.y - core.height));
            bands.add(new Rectangle(area.x, core.y, core.x - area.x, core.height));
            bands.add(new Rectangle(core.x + core.width, core.y, area.x + area.width - core.x - core.width, core.height));
        }
        long ramp = glassCache == null ? 0 : UiGlassCache.hash(lookup, lookup.length);
        Graphics2D output = (Graphics2D) graphics.create();
        try {
            output.setTransform(new AffineTransform());
            output.setClip(deviceClip);
            output.setComposite(AlphaComposite.SrcOver);
            List<UiBands.Placed> placed = new ArrayList<>(bands.size());
            for (Rectangle band : bands) {
                if (band.width <= 0 || band.height <= 0 || deviceClip != null && !deviceClip.intersects(band)) continue;
                var key = glassCache == null ? null : new UiGlassCache.ShadowKey(band, cx, cy, chw, chh, casterCorner, centerX, centerY, innerW, innerH, corner, ramp);
                BufferedImage image = key == null ? null : glassCache.find(key, 0);
                if (image == null) {
                    image = new BufferedImage(band.width, band.height, BufferedImage.TYPE_INT_ARGB);
                    int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
                    int bandWidth = band.width;
                    rows(band.height, (long) band.width * band.height, row -> {
                        double py = band.y + row + .5;
                        double offsetY = Math.abs(py - centerY), qy = offsetY - innerH, dy = Math.max(qy, 0);
                        double cqy = Math.abs(py - cy) - cInnerH, cdy = Math.max(cqy, 0);
                        for (int column = 0; column < bandWidth; column++) {
                            double px = band.x + column + .5;
                            double cqx = Math.abs(px - cx) - cInnerW, cdx = Math.max(cqx, 0);
                            double casterDistance = Math.sqrt(cdx * cdx + cdy * cdy) + Math.min(Math.max(cqx, cqy), 0) - casterCorner;
                            double visible = Math.clamp(casterDistance + .5, 0, 1);
                            if (visible <= 0) continue;
                            double qx = Math.abs(px - centerX) - innerW, dx = Math.max(qx, 0);
                            double distance = Math.sqrt(dx * dx + dy * dy) + Math.min(Math.max(qx, qy), 0) - corner;
                            int value = lookup[(int) Math.clamp(Math.round((distance + .5) * 16), 0, samples - 1)];
                            pixels[row * bandWidth + column] = visible >= 1 ? value : (int) Math.round((value >>> 24) * visible) << 24 | value & 0xFFFFFF;
                        }
                    });
                    if (key != null) glassCache.store(key, 0, image);
                }
                placed.add(new UiBands.Placed(image, band.x, band.y));
            }
            UiBands.images(output, placed);
        } finally { output.dispose(); }
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
        // Recorded before clipping, so a partial repaint that touches the region repaints all it reads.
        recordBackdropRead(new Rectangle(left, top, pixelWidth, pixelHeight));
        Shape clip = graphics.getClip();
        Shape deviceClip = deviceClip(clip, graphics.getTransform());
        if (deviceClip != null && !deviceClip.intersects(left, top, pixelWidth, pixelHeight)) return false;

        int maximumRadius = Math.max(1, (Math.min(pixelWidth, pixelHeight) - 1) / 2);
        int pixelRadius = Math.max(1, Math.min(Math.min(24, maximumRadius), Math.round(radius *
            (float) Math.max(Math.abs(graphics.getTransform().getScaleX()), Math.abs(graphics.getTransform().getScaleY())))));
        if (glassCache != null && backdropParent == null) {
            // Screen surface: reuse the frost when nothing beneath it changed.
            Rectangle read = new Rectangle(left, top, pixelWidth, pixelHeight);
            int[] raster = plainRaster();
            int[] backdrop = raster == null ? readBackdrop(read) : null;
            var key = new UiGlassCache.Key(read, read, pixelRadius, 0, 0, 0, 0, null);
            long hash = raster != null ? UiGlassCache.hash(raster, target.getWidth(), read) : UiGlassCache.hash(backdrop, pixelWidth * pixelHeight);
            BufferedImage frosted = glassCache.find(key, hash);
            if (frosted == null) {
                if (backdrop == null) backdrop = readBackdrop(read);
                boxBlur(backdrop, pixelWidth, pixelHeight, pixelRadius);
                frosted = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
                frosted.setRGB(0, 0, pixelWidth, pixelHeight, backdrop, 0, pixelWidth);
                glassCache.store(key, hash, frosted);
            }
            drawDevice(frosted, left, top, deviceClip);
            return true;
        }
        if (backdropParent != null) {
            // Inside a group layer the backdrop is the group composited over its parent.
            if (deviceClip != null) {
                Rectangle clipBounds = deviceClip.getBounds().intersection(new Rectangle(left, top, pixelWidth, pixelHeight));
                if (clipBounds.isEmpty()) return false;
            }
            int[] backdrop = backdropPixels(left, top, pixelWidth, pixelHeight);
            boxBlur(backdrop, pixelWidth, pixelHeight, pixelRadius);
            var blurredLayer = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
            blurredLayer.setRGB(0, 0, pixelWidth, pixelHeight, backdrop, 0, pixelWidth);
            drawDevice(blurredLayer, left, top, deviceClip);
            return true;
        }
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

        // A running-sum box blur costs the same for any radius, unlike a convolution kernel.
        BufferedImage blurred = scratch.source;
        int[] sourcePixels = ((java.awt.image.DataBufferInt) source.getRaster().getDataBuffer()).getData();
        int[] horizontalPixels = ((java.awt.image.DataBufferInt) scratch.horizontal.getRaster().getDataBuffer()).getData();
        blurPass(sourcePixels, horizontalPixels, pixelWidth, pixelHeight, pixelRadius, 1, pixelWidth);
        blurPass(horizontalPixels, sourcePixels, pixelHeight, pixelWidth, pixelRadius, pixelWidth, 1);

        Graphics2D output = (Graphics2D) graphics.create();
        try {
            output.setTransform(new AffineTransform());
            output.setClip(deviceClip);
            output.clipRect(left, top, pixelWidth, pixelHeight);
            UiBands.image(output, blurred, left, top);
        } finally {
            output.dispose();
        }
        return true;
    }

    private void drawDevice(BufferedImage image, int x, int y, Shape deviceClip) {
        Graphics2D output = (Graphics2D) graphics.create();
        try {
            output.setTransform(new AffineTransform());
            output.setClip(deviceClip);
            UiBands.image(output, image, x, y);
        } finally { output.dispose(); }
    }

    /**
     * The current clip in device pixels, as Java2D itself holds it: a rectangle stays a rectangle
     * under an axis-aligned transform, which keeps device-space draws on the fast rectangular path.
     */
    private static Shape deviceClip(Shape clip, AffineTransform matrix) {
        if (clip == null) return null;
        if (clip instanceof Rectangle2D rectangle && matrix.getShearX() == 0 && matrix.getShearY() == 0) {
            double[] corners = {rectangle.getX(), rectangle.getY(), rectangle.getMaxX(), rectangle.getMaxY()};
            matrix.transform(corners, 0, corners, 0, 2);
            double left = Math.min(corners[0], corners[2]), top = Math.min(corners[1], corners[3]);
            return new Rectangle2D.Double(left, top, Math.max(corners[0], corners[2]) - left, Math.max(corners[1], corners[3]) - top);
        }
        return matrix.createTransformedShape(clip);
    }

    /** Glass on this canvas reads {@code parent}'s pixels beneath it, this canvas's origin lying at (x, y) there. */
    void readBackdropFrom(UiCanvas parent, int x, int y) {
        backdropParent = parent; backdropX = x; backdropY = y;
    }

    /** The surface's own pixel array when it holds straight ARGB rows with no offset, or null. */
    private int[] plainRaster() {
        if (target.getType() != BufferedImage.TYPE_INT_ARGB || !(target.getRaster().getDataBuffer() instanceof java.awt.image.DataBufferInt buffer)
                || !(target.getSampleModel() instanceof java.awt.image.SinglePixelPackedSampleModel model) || model.getScanlineStride() != target.getWidth()
                || target.getRaster().getSampleModelTranslateX() != 0 || target.getRaster().getSampleModelTranslateY() != 0) return null;
        return buffer.getData();
    }

    private int[] readBackdrop(Rectangle read) {
        int[] backdrop = glassCache.scratch(read.width * read.height);
        readStraight(target, read, backdrop, 0, read.width);
        return backdrop;
    }

    private void recordBackdropRead(Rectangle read) {
        if (backdropParent != null) backdropParent.recordBackdropRead(new Rectangle(read.x + backdropX, read.y + backdropY, read.width, read.height));
        else if (backdropReads != null) backdropReads.add(read);
    }

    /** Copies straight-alpha ARGB rows, reading packed int rasters directly rather than pixel by pixel. */
    private static void readStraight(BufferedImage image, Rectangle area, int[] out, int offset, int scan) {
        int type = image.getType();
        if ((type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_INT_ARGB_PRE)
                && image.getRaster().getDataBuffer() instanceof java.awt.image.DataBufferInt buffer
                && image.getSampleModel() instanceof java.awt.image.SinglePixelPackedSampleModel model) {
            int[] data = buffer.getData();
            int stride = model.getScanlineStride();
            int originX = -image.getRaster().getSampleModelTranslateX(), originY = -image.getRaster().getSampleModelTranslateY();
            for (int row = 0; row < area.height; row++)
                System.arraycopy(data, (originY + area.y + row) * stride + originX + area.x, out, offset + row * scan, area.width);
            if (type == BufferedImage.TYPE_INT_ARGB_PRE) for (int row = 0; row < area.height; row++) {
                for (int i = offset + row * scan, end = i + area.width; i < end; i++) {
                    int pixel = out[i], a = pixel >>> 24;
                    if (a == 0 || a == 255) continue;
                    out[i] = a << 24 | Math.min(255, (pixel >>> 16 & 255) * 255 / a) << 16
                        | Math.min(255, (pixel >>> 8 & 255) * 255 / a) << 8 | Math.min(255, (pixel & 255) * 255 / a);
                }
            }
            return;
        }
        image.getRGB(area.x, area.y, area.width, area.height, out, offset, scan);
    }

    /**
     * Straight-alpha pixels behind a device rectangle: this canvas's own, and inside a group layer
     * that group composited over everything its parents hold there. Pixels off the surface are transparent.
     */
    private int[] backdropPixels(int x, int y, int width, int height) {
        // The screen's scratch serves one read at a time; it holds the previous read's pixels until filled.
        int[] pixels = glassCache == null ? new int[width * height] : glassCache.scratch(width * height);
        Rectangle own = new Rectangle(x, y, width, height).intersection(new Rectangle(target.getWidth(), target.getHeight()));
        if (glassCache != null && !own.equals(new Rectangle(x, y, width, height))) java.util.Arrays.fill(pixels, 0, width * height, 0);
        if (!own.isEmpty()) readStraight(target, own, pixels, (own.y - y) * width + own.x - x, width);
        if (backdropParent == null) return pixels;
        int[] under = backdropParent.backdropPixels(x + backdropX, y + backdropY, width, height);
        for (int i = 0; i < pixels.length; i++) {
            int top = pixels[i], a = top >>> 24;
            if (a == 255) continue;
            int bottom = under[i];
            if (a == 0) { pixels[i] = bottom; continue; }
            int ba = (bottom >>> 24) * (255 - a) / 255, oa = a + ba;
            if (oa == 0) { pixels[i] = 0; continue; }
            int r = ((top >>> 16 & 255) * a + (bottom >>> 16 & 255) * ba) / oa;
            int g = ((top >>> 8 & 255) * a + (bottom >>> 8 & 255) * ba) / oa;
            int b = ((top & 255) * a + (bottom & 255) * ba) / oa;
            pixels[i] = oa << 24 | r << 16 | g << 8 | b;
        }
        return pixels;
    }

    /**
     * Liquid glass over everything already painted beneath it. The rim refracts the backdrop
     * outward along the edge normal (strongest at the edge, with slight colour separation), the
     * body is softened and lifted toward the tint, and a specular edge catches the light from above.
     * Like {@link #backdropBlur} it reads pixels already painted, so paint the glass after what it
     * should bend; moving glass is re-evaluated on each repaint, and a partial repaint that touches
     * the lens repaints everything the lens reads. Inside a {@link #withLayer} group it sees the group
     * over what lies behind it, so fading panels keep refracting. Cached and GPU layers are painted on
     * their own, so glass there bends only what that layer itself drew first. Rotated or sheared
     * drawing falls back to a blurred, tinted panel. Returns false without a readable surface.
     */
    public boolean liquidGlass(float x, float y, float width, float height, float radius, UiGlass glass) {
        if (target == null || glass == null || width <= 0 || height <= 0) return false;
        radius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        AffineTransform matrix = graphics.getTransform();
        double scale = matrix.getScaleX();
        if ((glass.shadow() >>> 24) != 0) shadowOutside(glass.shadow(), 14, 0, 6, x, y, width, height, radius);
        if (matrix.getShearX() != 0 || matrix.getShearY() != 0 || matrix.getScaleY() != scale || scale <= 0) {
            boolean read = backdropBlur(Math.max(1, glass.blur()), x, y, width, height);
            roundRect(glass.tint(), x, y, width, height, radius);
            return read;
        }
        double halfW = width * scale / 2, halfH = height * scale / 2, corner = radius * scale;
        double centerX = (x + width / 2) * scale + matrix.getTranslateX(), centerY = (y + height / 2) * scale + matrix.getTranslateY();
        Rectangle area = new Rectangle((int) Math.floor(centerX - halfW - 1), (int) Math.floor(centerY - halfH - 1), 0, 0);
        area.add(Math.ceil(centerX + halfW + 1), Math.ceil(centerY + halfH + 1));
        Rectangle surfaceBounds = new Rectangle(target.getWidth(), target.getHeight());
        double bend = glass.refraction() * scale, rim = glass.rim() * scale, spread = glass.dispersion() * .22;
        int blur = Math.round(glass.blur() * (float) scale);
        int margin = (int) Math.ceil(bend * (1 + spread) + blur * 1.5 + 2);
        // The whole lens is recorded before clipping, so a partial repaint that touches it repaints all it reads.
        recordBackdropRead(new Rectangle(area.x - margin, area.y - margin, area.width + margin * 2, area.height + margin * 2)
            .intersection(surfaceBounds));
        Shape clip = graphics.getClip();
        Shape deviceClip = deviceClip(clip, matrix);
        if (deviceClip != null) area = area.intersection(deviceClip.getBounds());
        area = area.intersection(surfaceBounds);
        if (area.isEmpty()) return true;
        Rectangle source = new Rectangle(area.x - margin, area.y - margin, area.width + margin * 2, area.height + margin * 2)
            .intersection(surfaceBounds);
        int sw = source.width, sh = source.height;
        // Unchanged pixels under unchanged glass give the same lens: reuse it instead of recomputing.
        var key = glassCache == null ? null : new UiGlassCache.Key(area, source, centerX, centerY, halfW, halfH, corner, glass);
        // On the screen surface the backdrop hashes in place and is copied out only to compute a new lens.
        int[] raster = key != null && backdropParent == null ? plainRaster() : null;
        int[] backdrop = raster == null ? backdropPixels(source.x, source.y, sw, sh) : null;
        long hash = key == null ? 0 : raster != null ? UiGlassCache.hash(raster, target.getWidth(), source) : UiGlassCache.hash(backdrop, sw * sh);
        var finished = finishedKey(key, deviceClip, area, source, scale);
        if (finished != null) {
            BufferedImage pixels = glassCache.find(finished, hash);
            if (pixels != null) {
                copyDevice(pixels, finished.snapshot());
                glassCache.touch(key);
                // Leave the drawing state as the drawn path does.
                graphics.setStroke(new BasicStroke(.7F));
                graphics.setPaint(awtColor(0xFF000000));
                return true;
            }
        }
        BufferedImage output = key == null ? null : glassCache.find(key, hash);
        if (output == null) {
            if (backdrop == null) backdrop = backdropPixels(source.x, source.y, sw, sh);
            output = lens(backdrop, source, area, centerX, centerY, halfW, halfH, corner, bend, rim, spread, blur, glass);
            if (key != null) glassCache.store(key, hash, output);
        }
        drawDevice(output, area.x, area.y, deviceClip);
        // Specular edge: bright along the top, fading down the sides; a hairline grounds it on light backdrops.
        float h = glass.highlight();
        graphics.setStroke(new BasicStroke(1.2F));
        graphics.setPaint(linearPaint(alpha(0xFFFFFF, Math.round(235 * h)), alpha(0xFFFFFF, Math.round(40 * h)), 90, x, y, width, height));
        strokeStraightSides(roundShape(x + .6F, y + .6F, width - 1.2F, height - 1.2F, Math.max(0, radius - .6F)));
        graphics.setPaint(linearPaint(alpha(0x000000, 0), alpha(0x000000, Math.round(60 * h)), 90, x, y, width, height));
        graphics.setStroke(new BasicStroke(.7F));
        strokeStraightSides(roundShape(x - .2F, y - .2F, width + .4F, height + .4F, radius + .2F));
        if (finished != null) glassCache.store(finished, hash, deviceSnapshot(finished.snapshot()));
        graphics.setPaint(awtColor(0xFF000000));
        return true;
    }

    /**
     * The cache key for a finished lens on the screen surface, or null when its pixels cannot be
     * reused exactly. Everything a lens and its edges draw depends only on the backdrop pixels the
     * hash covers, the lens geometry and look, the scale, the opacity and the clip; every pixel it
     * leaves untouched still holds that same backdrop. So when all of these match, copying the
     * pixels it left reproduces drawing it. Shaped clips and other composites draw every time.
     */
    private UiGlassCache.Finished finishedKey(UiGlassCache.Key key, Shape deviceClip, Rectangle area, Rectangle source, double scale) {
        if (key == null || !UiBands.shortcuts || backdropParent != null || target.getType() != BufferedImage.TYPE_INT_ARGB
                || !(graphics.getComposite() instanceof AlphaComposite alpha) || alpha.getRule() != AlphaComposite.SRC_OVER
                || deviceClip != null && !(deviceClip instanceof Rectangle2D)) return null;
        // The hairline reaches just over half a unit beyond the lens.
        int reach = (int) Math.ceil(.6 * scale) + 1;
        Rectangle snapshot = new Rectangle(area.x - reach, area.y - reach, area.width + reach * 2, area.height + reach * 2)
            .intersection(new Rectangle(target.getWidth(), target.getHeight()));
        if (!source.contains(snapshot)) return null;
        return new UiGlassCache.Finished(key, snapshot, scale, alpha.getAlpha(), (Rectangle2D) deviceClip);
    }

    private BufferedImage deviceSnapshot(Rectangle area) {
        var copy = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_ARGB);
        readStraight(target, area, ((java.awt.image.DataBufferInt) copy.getRaster().getDataBuffer()).getData(), 0, area.width);
        return copy;
    }

    /** Writes an earlier {@link #deviceSnapshot} back over the surface pixels it was taken from. */
    private void copyDevice(BufferedImage snapshot, Rectangle area) {
        int[] from = ((java.awt.image.DataBufferInt) snapshot.getRaster().getDataBuffer()).getData();
        int[] to = ((java.awt.image.DataBufferInt) target.getRaster().getDataBuffer()).getData();
        int stride = target.getWidth();
        for (int row = 0; row < area.height; row++)
            System.arraycopy(from, row * area.width, to, (area.y + row) * stride + area.x, area.width);
    }

    /** One lens over a backdrop: refracted, dispersed rim and a softened, tinted body. Pixels outside stay clear. */
    private static BufferedImage lens(int[] backdrop, Rectangle source, Rectangle area, double centerX, double centerY,
                                      double halfW, double halfH, double corner, double bend, double rim, double spread,
                                      int blur, UiGlass glass) {
        int sw = source.width, sh = source.height;
        // Frost is a low-pass filter, so blur at half resolution and sample it back bilinearly.
        boolean half = blur >= 2;
        int bw = half ? (sw + 1) / 2 : sw, bh = half ? (sh + 1) / 2 : sh;
        int[] blurred = half ? halve(backdrop, sw, sh) : backdrop;
        int radius = half ? Math.max(1, blur / 2) : blur;
        if (blur > 0) { boxBlur(blurred, bw, bh, radius); boxBlur(blurred, bw, bh, Math.max(1, radius / 2)); }
        double toBlur = half ? .5 : 1, blurOffset = half ? -.25 : 0;
        var output = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((java.awt.image.DataBufferInt) output.getRaster().getDataBuffer()).getData();
        float tintA = (glass.tint() >>> 24) / 255F;
        int tintR = glass.tint() >>> 16 & 255, tintG = glass.tint() >>> 8 & 255, tintB = glass.tint() & 255;
        double innerW = halfW - corner, innerH = halfH - corner, top = centerY - halfH;
        rows(area.height, (long) area.width * area.height, row -> {
            double py = area.y + row + .5, oy = py - centerY, qy = Math.abs(oy) - innerH, dy = Math.max(qy, 0);
            float sheen = glass.highlight() * .07F * (float) Math.max(0, 1 - (py - top) / (halfH * 2));
            double sy = (py - source.y - .5) * toBlur + blurOffset;
            for (int column = 0; column < area.width; column++) {
                double px = area.x + column + .5, ox = px - centerX, qx = Math.abs(ox) - innerW, dx = Math.max(qx, 0);
                double edge = Math.sqrt(dx * dx + dy * dy);
                double distance = edge + Math.min(Math.max(qx, qy), 0) - corner;
                if (distance > .5) continue;
                double coverage = Math.min(1, .5 - distance);
                double depth = Math.min(1, Math.max(0, 1 + distance / rim));
                double lens = depth * depth;
                double sx = (px - source.x - .5) * toBlur + blurOffset;
                int r, g, b;
                if (lens <= 0.0005) {
                    int pixel = half ? sampleRgb(blurred, bw, bh, sx, sy)
                        : blurred[clampIndex((int) py - source.y, sh) * sw + clampIndex((int) px - source.x, sw)];
                    r = pixel >>> 16 & 255; g = pixel >>> 8 & 255; b = pixel & 255;
                } else {
                    double nx, ny;
                    if (qx > 0 && qy > 0) { nx = dx / edge * Math.signum(ox); ny = dy / edge * Math.signum(oy); }
                    else if (qx > qy) { nx = Math.signum(ox); ny = 0; }
                    else { nx = 0; ny = Math.signum(oy); }
                    double shift = bend * lens * toBlur;
                    r = sample(blurred, bw, bh, sx + nx * shift * (1 + spread), sy + ny * shift * (1 + spread), 16);
                    g = sample(blurred, bw, bh, sx + nx * shift, sy + ny * shift, 8);
                    b = sample(blurred, bw, bh, sx + nx * shift * (1 - spread), sy + ny * shift * (1 - spread), 0);
                }
                float lift = tintA + (float) lens * glass.highlight() * .1F;
                r = Math.min(255, Math.round(r + (tintR - r) * tintA + (255 - r) * (lift - tintA + sheen)));
                g = Math.min(255, Math.round(g + (tintG - g) * tintA + (255 - g) * (lift - tintA + sheen)));
                b = Math.min(255, Math.round(b + (tintB - b) * tintA + (255 - b) * (lift - tintA + sheen)));
                pixels[row * area.width + column] = (int) Math.round(coverage * 255) << 24 | r << 16 | g << 8 | b;
            }
        });
        return output;
    }

    /** 2x2 box downsample of a straight-alpha raster; odd edges repeat the last pixel. */
    private static int[] halve(int[] pixels, int width, int height) {
        int hw = (width + 1) / 2, hh = (height + 1) / 2;
        int[] out = new int[hw * hh];
        for (int y = 0; y < hh; y++) {
            int y0 = y * 2 * width, y1 = Math.min(height - 1, y * 2 + 1) * width;
            for (int x = 0; x < hw; x++) {
                int x0 = x * 2, x1 = Math.min(width - 1, x * 2 + 1);
                int a = pixels[y0 + x0], b = pixels[y0 + x1], c = pixels[y1 + x0], d = pixels[y1 + x1];
                out[y * hw + x] = ((a >>> 24) + (b >>> 24) + (c >>> 24) + (d >>> 24) >> 2) << 24
                    | ((a >>> 16 & 255) + (b >>> 16 & 255) + (c >>> 16 & 255) + (d >>> 16 & 255) >> 2) << 16
                    | ((a >>> 8 & 255) + (b >>> 8 & 255) + (c >>> 8 & 255) + (d >>> 8 & 255) >> 2) << 8
                    | ((a & 255) + (b & 255) + (c & 255) + (d & 255) >> 2);
            }
        }
        return out;
    }

    /** Bilinear sample of all three colour channels at once. */
    private static int sampleRgb(int[] pixels, int width, int height, double x, double y) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = (float) (x - x0), fy = (float) (y - y0);
        int ax = clampIndex(x0, width), bx = clampIndex(x0 + 1, width), ay = clampIndex(y0, height) * width, by = clampIndex(y0 + 1, height) * width;
        int p = pixels[ay + ax], q = pixels[ay + bx], u = pixels[by + ax], v = pixels[by + bx];
        float w0 = (1 - fx) * (1 - fy), w1 = fx * (1 - fy), w2 = (1 - fx) * fy, w3 = fx * fy;
        int r = Math.round((p >>> 16 & 255) * w0 + (q >>> 16 & 255) * w1 + (u >>> 16 & 255) * w2 + (v >>> 16 & 255) * w3);
        int g = Math.round((p >>> 8 & 255) * w0 + (q >>> 8 & 255) * w1 + (u >>> 8 & 255) * w2 + (v >>> 8 & 255) * w3);
        int b = Math.round((p & 255) * w0 + (q & 255) * w1 + (u & 255) * w2 + (v & 255) * w3);
        return r << 16 | g << 8 | b;
    }

    private static int clampIndex(int value, int size) { return Math.max(0, Math.min(size - 1, value)); }

    /** Bilinear sample of one channel of a straight-alpha ARGB raster. */
    private static int sample(int[] pixels, int width, int height, double x, double y, int shift) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        int ax = clampIndex(x0, width), bx = clampIndex(x0 + 1, width), ay = clampIndex(y0, height), by = clampIndex(y0 + 1, height);
        double top = (pixels[ay * width + ax] >>> shift & 255) * (1 - fx) + (pixels[ay * width + bx] >>> shift & 255) * fx;
        double bottom = (pixels[by * width + ax] >>> shift & 255) * (1 - fx) + (pixels[by * width + bx] >>> shift & 255) * fx;
        return (int) Math.round(top * (1 - fy) + bottom * fy);
    }

    /** Separable running-sum box blur of an opaque-ish ARGB raster, in place. */
    private static void boxBlur(int[] pixels, int width, int height, int radius) {
        int[] scratch = new int[width * height];
        blurPass(pixels, scratch, width, height, radius, 1, width);
        blurPass(scratch, pixels, height, width, radius, width, 1);
    }

    /** Rasters at least this large split their rows across cores; smaller ones are not worth the hand-off. */
    private static final int PARALLEL_PIXELS = 1 << 15;

    /** Runs {@code row} for 0..count-1, in parallel when the work is large enough. Rows must be independent. */
    private static void rows(int count, long pixels, java.util.function.IntConsumer row) {
        if (pixels >= PARALLEL_PIXELS && count > 1) UiParallel.forEach(count, row);
        else for (int i = 0; i < count; i++) row.accept(i);
    }

    private static void blurPass(int[] from, int[] to, int length, int lines, int radius, int step, int lineStep) {
        int window = radius * 2 + 1;
        rows(lines, (long) length * lines, line -> {
            int base = line * lineStep, a = 0, r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int pixel = from[base + clampIndex(i, length) * step];
                a += pixel >>> 24; r += pixel >>> 16 & 255; g += pixel >>> 8 & 255; b += pixel & 255;
            }
            for (int i = 0; i < length; i++) {
                to[base + i * step] = (a / window) << 24 | (r / window) << 16 | (g / window) << 8 | (b / window);
                int out = from[base + clampIndex(i - radius, length) * step], in = from[base + clampIndex(i + radius + 1, length) * step];
                a += (in >>> 24) - (out >>> 24); r += (in >>> 16 & 255) - (out >>> 16 & 255);
                g += (in >>> 8 & 255) - (out >>> 8 & 255); b += (in & 255) - (out & 255);
            }
        });
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

    /** Draws plain text in the default family; {@code top} is the top of the line box. */
    public void text(String value, float x, float top, float size, int color) {
        text(value, x, top, size, color, Font.PLAIN);
    }

    /** Draws text in the default family's bold weight. */
    public void textMedium(String value, float x, float top, float size, int color) {
        text(value, x, top, size, color, Font.BOLD);
    }

    /** Draws text in the default family with an AWT font style such as {@code Font.BOLD}. */
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

    /** Draws text centred on a point in the default family. */
    public void centeredText(String value, float centerX, float centerY, float size, int color, int style) {
        centeredText(value, centerX, centerY, size, color, style, SANS);
    }

    /** Draws text centred on a point in a given family. */
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

    /** Draws formatted text in a given family with the default line height; returns its width. */
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

    /** Breaks text into lines no wider than {@code maxWidth} in the default family. */
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

    /** Runs {@code painter} clipped to a rectangle. */
    public void withClip(float x, float y, float width, float height, Runnable painter) {
        withClipShape(new Rectangle2D.Float(x, y, Math.max(0, width), Math.max(0, height)), painter);
    }

    /** Clips to a rounded rectangle, for masking content inside a card. */
    public void withRoundedClip(float x, float y, float width, float height, float radius, Runnable painter) {
        withClipShape(roundShape(x, y, Math.max(0, width), Math.max(0, height), radius), painter);
    }

    /** Clips to a free-form outline, such as a custom button shape. */
    public void withClip(UiPath outline, Runnable painter) {
        withClipShape(outline.shape(), painter);
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
            var group = new UiCanvas(child, image);
            if (target != null) { group.backdropParent = this; group.backdropX = pixels.x; group.backdropY = pixels.y; }
            painter.accept(group);
        } finally { child.dispose(); }
        Graphics2D output = (Graphics2D) graphics.create();
        try {
            output.setTransform(new AffineTransform());
            float inherited = graphics.getComposite() instanceof AlphaComposite alpha ? alpha.getAlpha() : 1;
            output.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                inherited * Math.max(0, Math.min(1, opacity))));
            UiBands.image(output, image, pixels.x, pixels.y);
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
        // Axis-aligned uniform drawing caches at the exact device scale and phase and is blitted 1:1.
        // A reused revision keeps the scale and sub-pixel phase it was painted at: moving content
        // snaps to the nearest device pixel and scaling or rotating content resamples, so motion
        // never repaints the cache. Other transforms cache at a whole-number quality bucket.
        double scale = matrix.getScaleX();
        boolean axisAligned = matrix.getShearX() == 0 && matrix.getShearY() == 0
            && matrix.getScaleY() == scale && scale > 0 && scale <= 4;
        double originX = bounds.x() * scale + matrix.getTranslateX(), originY = bounds.y() * scale + matrix.getTranslateY();
        boolean keep = layer.cached() && java.util.Objects.equals(layer.revision(), revision);
        float quality = keep ? layer.quality : axisAligned ? (float) scale : Math.max(1, Math.min(4, Math.round(Math.max(
            Math.hypot(matrix.getScaleX(), matrix.getShearY()), Math.hypot(matrix.getShearX(), matrix.getScaleY())))));
        float alignX = keep ? layer.alignX : axisAligned ? (float) (originX - Math.floor(originX)) : 0;
        float alignY = keep ? layer.alignY : axisAligned ? (float) (originY - Math.floor(originY)) : 0;
        int w = Math.max(1, (int) Math.ceil(bounds.width() * quality + alignX));
        int h = Math.max(1, (int) Math.ceil(bounds.height() * quality + alignY));
        if (!layer.reuse(w, h, revision)) {
            var child = layer.image().createGraphics();
            try {
                child.translate(alignX, alignY); child.scale(quality, quality); child.translate(-bounds.x(), -bounds.y());
                painter.accept(new UiCanvas(child, layer.image()));
            } finally { child.dispose(); }
            layer.finishCache(quality, alignX, alignY);
        }
        if (layer.cachedImage() == null) return;
        var previous = graphics.getTransform();
        try {
            if (axisAligned && scale == layer.quality) {
                graphics.setTransform(new AffineTransform());
                Composite composite = graphics.getComposite();
                // Over an opaque cache, source-over leaves exactly the source: copying skips the blend.
                if (UiBands.shortcuts && layer.opaque() && composite instanceof AlphaComposite alpha && alpha.getRule() == AlphaComposite.SRC_OVER && alpha.getAlpha() == 1)
                    graphics.setComposite(AlphaComposite.Src);
                try {
                    UiBands.image(graphics, layer.cachedImage(), (int) Math.round(originX - layer.alignX) + layer.cachedX(),
                        (int) Math.round(originY - layer.alignY) + layer.cachedY());
                } finally { graphics.setComposite(composite); }
            } else {
                graphics.translate(bounds.x(), bounds.y());
                graphics.scale(1 / layer.quality, 1 / layer.quality);
                graphics.translate(-layer.alignX, -layer.alignY);
                graphics.drawImage(layer.cachedImage(), layer.cachedX(), layer.cachedY(), null);
            }
        } finally { graphics.setTransform(previous); }
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

    /** The colour with its alpha replaced by {@code alpha} (0 to 255). */
    public static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    /** Same colour at a fraction of its current opacity. */
    public static int fade(int color, float amount) {
        return alpha(color, Math.round(((color >>> 24) & 0xFF) * Math.max(0F, Math.min(1F, amount))));
    }

    /** Linear mix of two ARGB colours; {@code amount} 0 gives the first, 1 the second. */
    public static int mix(int first, int second, float amount) {
        float t = Math.max(0, Math.min(1, amount));
        int a = channel(first, 24), r = channel(first, 16), g = channel(first, 8), b = channel(first, 0);
        return ((int) (a + (channel(second, 24) - a) * t) << 24)
            | ((int) (r + (channel(second, 16) - r) * t) << 16)
            | ((int) (g + (channel(second, 8) - g) * t) << 8)
            | (int) (b + (channel(second, 0) - b) * t);
    }

    /** The colour mixed toward white by {@code amount}. */
    public static int lighten(int color, float amount) { return mix(color, 0xFFFFFFFF, amount); }

    /** The colour mixed toward black by {@code amount}. */
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
        UiBands.fill(graphics, new Rectangle2D.Float(x, y, width, height));
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
