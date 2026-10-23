package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Banded draws must produce exactly the pixels of one ordinary Java2D draw. */
class UiBandsTest {
    private static final int SIZE = 900;

    @Test
    void bandedFillsMatchOneDraw() {
        Shape[] shapes = {new Rectangle2D.Float(13.3F, 21.7F, 610.4F, 500.2F),
            new RoundRectangle2D.Float(40.25F, 33.6F, 700, 640.5F, 44, 44),
            new RoundRectangle2D.Float(10.5F, 12.1F, 760, 420.3F, 180, 180)};
        for (double scale : new double[] {1, 1.5, 2.1}) for (Shape shape : shapes)
            for (int color : new int[] {0xFFE8E2D6, 0x9F3366AA, 0x18FFFFFF}) for (int clip = 0; clip < 3; clip++)
                for (float opacity : new float[] {1, .55F}) {
                    int c = color, k = clip;
                    Consumer<Graphics2D> setup = g -> {
                        g.scale(scale, scale);
                        clip(g, k);
                        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
                        g.setColor(new Color(c, true));
                    };
                    var bounds = shape.getBounds2D();
                    double corner = shape instanceof RoundRectangle2D round ? round.getArcHeight() / 2 : 0;
                    int[] expected = paint(setup, g -> g.fill(shape));
                    int[] actual = paint(setup, g -> UiBands.fill(g, shape, bounds.getMinY() + corner, bounds.getMaxY() - corner));
                    assertArrayEquals(expected, actual, "scale=" + scale + " shape=" + bounds
                        + " color=" + Integer.toHexString(color) + " clip=" + clip + " opacity=" + opacity);
                }
    }

    @Test
    void bandedGradientAndTextureFillsMatchOneDraw() {
        var tile = noise(64, 64, 5, true, BufferedImage.TYPE_INT_ARGB);
        var random = new Random(9);
        for (double scale : new double[] {1, 1.05, 1.5, 2.1}) for (int trial = 0; trial < 6; trial++) {
            float x = random.nextFloat() * 40, y = random.nextFloat() * 40 - 10, w = 500 + random.nextFloat() * 300, h = 500 + random.nextFloat() * 300;
            java.awt.Paint[] paints = {
                new java.awt.GradientPaint(x, y, new Color(0x80FF3300, true), x + w * .3F, y + h, new Color(0xF00033FF, true)),
                new java.awt.LinearGradientPaint(x, y + 3, x + 10, y + h, new float[] {0, 1}, new Color[] {new Color(0xFFBFDDF8, true), new Color(0x6E2E6FD8, true)}),
                new java.awt.RadialGradientPaint(x + w * .2F, y - h * .05F, w * .5F, new float[] {0, 1}, new Color[] {new Color(0xCCFFFFFF, true), new Color(0x00FFFFFF, true)}),
                new java.awt.TexturePaint(tile, new Rectangle2D.Float(x + .37F, y + .61F, 64, 64))};
            for (java.awt.Paint paint : paints) for (int clip = 0; clip < 2; clip++) for (int round = 0; round < 2; round++) {
                if (round == 1 && paint instanceof java.awt.TexturePaint) continue;
                // Radial gradients always draw once; the comparison still holds.
                Shape shape = round == 1 ? new RoundRectangle2D.Float(x, y, w, h, 36, 36) : new Rectangle2D.Float(x, y, w, h);
                double corner = round == 1 ? 18 : 0;
                int k = clip;
                Consumer<Graphics2D> setup = g -> { g.scale(scale, scale); clip(g, k); g.setPaint(paint); };
                int[] expected = paint(setup, g -> g.fill(shape));
                int[] actual = paint(setup, g -> UiBands.fill(g, shape, y + corner, y + h - corner));
                assertArrayEquals(expected, actual, "scale=" + scale + " trial=" + trial + " paint=" + paint.getClass().getSimpleName()
                    + " clip=" + clip + " round=" + round);
            }
        }
    }

    @Test
    void opaqueFillsMatchOneAntialiasedDraw() {
        var random = new Random(33);
        for (double scale : new double[] {1, 1.05, 1.5, 2, 2.1}) for (int trial = 0; trial < 8; trial++) {
            float x = random.nextFloat() * 40 - 5, y = random.nextFloat() * 40 - 5, w = 200 + random.nextFloat() * 600, h = 150 + random.nextFloat() * 600;
            float arc = trial % 3 == 0 ? 0 : 4 + random.nextFloat() * 80;
            for (int clip = 0; clip < 3; clip++) for (int round = 0; round < 2; round++) {
                Shape shape = round == 1 ? new RoundRectangle2D.Float(x, y, w, h, arc, arc) : new Rectangle2D.Float(x, y, w, h);
                double corner = round == 1 ? Math.min(arc / 2, h / 2) : 0;
                int k = clip;
                Consumer<Graphics2D> setup = g -> { g.scale(scale, scale); clip(g, k); g.setColor(new Color(0xFF1C1C1E)); };
                int[] expected = paint(setup, g -> g.fill(shape));
                int[] actual = paint(setup, g -> UiBands.fill(g, shape, y + corner, y + h - corner));
                assertArrayEquals(expected, actual, "scale=" + scale + " trial=" + trial + " clip=" + clip + " round=" + round + " " + shape.getBounds2D());
            }
        }
    }

    @Test
    void bandedStrokesMatchOneDraw() {
        var random = new Random(21);
        for (double scale : new double[] {1, 1.05, 1.5, 2.1}) for (int trial = 0; trial < 5; trial++) {
            float x = random.nextFloat() * 30, y = random.nextFloat() * 30, w = 400 + random.nextFloat() * 400, h = 500 + random.nextFloat() * 300;
            float arc = 8 + random.nextFloat() * 70;
            for (float width : new float[] {.7F, .8F, 1.2F, 3}) for (int clip = 0; clip < 2; clip++) for (int gradient = 0; gradient < 2; gradient++) {
                var shape = new RoundRectangle2D.Float(x, y, w, h, arc, arc);
                java.awt.Paint paint = gradient == 1
                    ? new java.awt.LinearGradientPaint(x, y, x, y + h, new float[] {0, 1}, new Color[] {new Color(0xEBFFFFFF, true), new Color(0x28FFFFFF, true)})
                    : new Color(0x24000000, true);
                int k = clip;
                Consumer<Graphics2D> setup = g -> { g.scale(scale, scale); clip(g, k); g.setPaint(paint); g.setStroke(new java.awt.BasicStroke(width)); };
                int[] expected = paint(setup, g -> g.draw(shape));
                int[] actual = paint(setup, g -> UiBands.stroke(g, shape, y + arc / 2, y + h - arc / 2));
                assertArrayEquals(expected, actual, "scale=" + scale + " trial=" + trial + " width=" + width + " clip=" + clip + " gradient=" + gradient);
            }
        }
    }

    @Test
    void bandedImagesMatchOneDraw() {
        BufferedImage translucent = noise(SIZE - 40, SIZE - 70, 3, true, BufferedImage.TYPE_INT_ARGB);
        BufferedImage premultiplied = noise(SIZE - 10, SIZE - 30, 4, false, BufferedImage.TYPE_INT_ARGB_PRE);
        for (BufferedImage image : new BufferedImage[] {translucent, premultiplied}) for (int clip = 0; clip < 3; clip++)
            for (float opacity : new float[] {1, .4F}) {
                int k = clip;
                Consumer<Graphics2D> setup = g -> {
                    clip(g, k);
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
                };
                int[] expected = paint(setup, g -> { g.drawImage(image, 7, -3, null); g.drawImage(translucent, 7, SIZE + 40, null); });
                int[] actual = paint(setup, g -> UiBands.images(g, java.util.List.of(new UiBands.Placed(image, 7, -3), new UiBands.Placed(translucent, 7, SIZE + 40))));
                assertArrayEquals(expected, actual, "type=" + image.getType() + " clip=" + clip + " opacity=" + opacity);
            }
    }

    @Test
    void largeDrawsReallySplitAndSmallOnesDoNot() {
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            var threads = java.util.concurrent.ConcurrentHashMap.<Thread>newKeySet();
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            UiBands.draw(graphics, new java.awt.Rectangle(0, 0, SIZE, SIZE), g -> { calls.incrementAndGet(); threads.add(Thread.currentThread()); });
            if (Runtime.getRuntime().availableProcessors() > 1) assertTrue(calls.get() > 1, "a full-surface draw splits into bands");
            calls.set(0);
            UiBands.draw(graphics, new java.awt.Rectangle(0, 0, 100, 100), g -> calls.incrementAndGet());
            assertEquals(1, calls.get(), "a small draw stays on the calling thread as one call");
            calls.set(0);
            graphics.clip(new Ellipse2D.Float(0, 0, SIZE, SIZE));
            UiBands.draw(graphics, new java.awt.Rectangle(0, 0, SIZE, SIZE), g -> calls.incrementAndGet());
            assertEquals(1, calls.get(), "a shaped clip draws once");
        } finally { graphics.dispose(); }
    }

    @Test
    void opaqueCachedLayersCopyExactlyWhatBlendingWouldLeave() {
        var layer = new UiLayer();
        var reference = new UiLayer();
        for (double scale : new double[] {1, 2.1}) {
            Consumer<UiCanvas> wallpaper = c -> {
                c.gradientRect(0xFF203040, 0xFF805020, 30, 0, 0, 400, 400, 0);
                c.circle(0xFFE0C080, 200, 200, 90);
            };
            int[] copied = canvas(scale, c -> c.cachedLayer(layer, "wall", new UiRect(0, 0, 400, 400), wallpaper));
            assertTrue(layer.opaque(), "a wallpaper that fills its bounds is opaque");
            // A clear right edge keeps the reference on the blending path.
            int[] blended = canvas(scale, c -> c.cachedLayer(reference, "wall", new UiRect(0, 0, 400, 400),
                r -> r.withClip(0, 0, 399, 400, () -> wallpaper.accept(r))));
            assertFalse(reference.opaque());
            for (int i = 0; i < copied.length; i++) {
                int x = i % (int) Math.round(SIZE * scale), y = i / (int) Math.round(SIZE * scale);
                if (x >= Math.floor(399 * scale) - 1) continue;
                assertEquals(blended[i], copied[i], "pixel " + x + "," + y + " at scale " + scale);
            }
            layer.clear(); reference.clear();
        }
    }

    private static void clip(Graphics2D g, int kind) {
        if (kind == 1) g.clip(new Rectangle2D.Float(31.4F, 47.2F, 520.7F, 430.3F));
        else if (kind == 2) g.clip(new RoundRectangle2D.Float(25.5F, 19.25F, 600, 560, 60, 60));
    }

    private static int[] paint(Consumer<Graphics2D> setup, Consumer<Graphics2D> draw) {
        BufferedImage image = noise(SIZE * 2, SIZE * 2, 11, true, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            setup.accept(graphics);
            draw.accept(graphics);
        } finally { graphics.dispose(); }
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    private static int[] canvas(double scale, Consumer<UiCanvas> painter) {
        int pixels = (int) Math.round(SIZE * scale);
        var image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.scale(scale, scale);
            painter.accept(new UiCanvas(graphics, image));
        } finally { graphics.dispose(); }
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    private static BufferedImage noise(int width, int height, long seed, boolean alpha, int type) {
        var image = new BufferedImage(width, height, type);
        int[] data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        var random = new Random(seed);
        for (int i = 0; i < data.length; i++) {
            int pixel = random.nextInt();
            if (!alpha) pixel |= 0xFF000000;
            if (type == BufferedImage.TYPE_INT_ARGB_PRE) {
                int a = pixel >>> 24;
                pixel = a << 24 | (pixel >>> 16 & 255) * a / 255 << 16 | (pixel >>> 8 & 255) * a / 255 << 8 | (pixel & 255) * a / 255;
            }
            data[i] = pixel;
        }
        return image;
    }
}
