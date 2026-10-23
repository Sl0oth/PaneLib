package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiGlassTest {
    private static final UiGlass GLASS = UiGlass.regular().withShadow(0);

    /** Vertical stripes: anything bending them shows up as a changed column pattern. */
    private static BufferedImage stripes() {
        var image = new BufferedImage(160, 100, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 100; y++) for (int x = 0; x < 160; x++)
            image.setRGB(x, y, (x / 6) % 2 == 0 ? 0xFF1060E0 : 0xFFF0A020);
        return image;
    }

    private static void paint(BufferedImage image, java.util.function.Consumer<UiCanvas> painter) {
        var graphics = image.createGraphics();
        try { painter.accept(new UiCanvas(graphics, image)); } finally { graphics.dispose(); }
    }

    @Test
    void rimRefractsWhatLiesBehindAndOutsideStaysUntouched() {
        var image = stripes();
        var before = stripes();
        paint(image, canvas -> canvas.liquidGlass(30, 20, 100, 60, 20, GLASS.withBlur(0).withTint(0)));
        int changedRim = 0;
        for (int x = 32; x < 128; x++) if (image.getRGB(x, 23) != before.getRGB(x, 23)) changedRim++;
        assertTrue(changedRim > 40, "the band near the top edge bends the stripes (" + changedRim + ")");
        for (int x = 0; x < 160; x += 7) {
            assertEquals(before.getRGB(x, 4), image.getRGB(x, 4), "above the glass");
            assertEquals(before.getRGB(x, 96), image.getRGB(x, 96), "below the glass");
        }
    }

    @Test
    void glassStaysInsideTheClip() {
        var image = stripes();
        var before = stripes();
        paint(image, canvas -> canvas.withClip(0, 0, 80, 100, () -> canvas.liquidGlass(30, 20, 100, 60, 20, GLASS)));
        for (int y = 0; y < 100; y++) for (int x = 81; x < 160; x++)
            assertEquals(before.getRGB(x, y), image.getRGB(x, y), "clipped half at " + x + "," + y);
        assertNotEquals(before.getRGB(60, 50), image.getRGB(60, 50), "visible half is glass");
    }

    @Test
    void reusesALensOnlyWhileItsBackdropIsUnchanged() {
        var cache = new UiGlassCache();
        var first = stripes();
        paint(first, canvas -> { canvas.glassCache = cache; canvas.liquidGlass(30, 20, 100, 60, 20, GLASS); });
        var second = stripes();
        paint(second, canvas -> { canvas.glassCache = cache; canvas.liquidGlass(30, 20, 100, 60, 20, GLASS); });
        // The first paint misses both the lens and its finished pixels; the second reuses the finished pixels.
        assertEquals(1, cache.hits);
        assertEquals(2, cache.misses);
        assertArrayEquals(first.getRGB(0, 0, 160, 100, null, 0, 160), second.getRGB(0, 0, 160, 100, null, 0, 160),
            "a reused lens is pixel-identical");
        var changed = stripes();
        changed.setRGB(70, 50, 0xFFFFFFFF);
        paint(changed, canvas -> { canvas.glassCache = cache; canvas.liquidGlass(30, 20, 100, 60, 20, GLASS); });
        assertEquals(4, cache.misses, "one changed backdrop pixel recomputes the lens and its finished pixels");
    }

    @Test
    void frostedBlurIsReusedLikeALens() {
        var cache = new UiGlassCache();
        var first = stripes();
        paint(first, canvas -> { canvas.glassCache = cache; canvas.backdropBlur(6, 20, 20, 80, 40); });
        var second = stripes();
        paint(second, canvas -> { canvas.glassCache = cache; canvas.backdropBlur(6, 20, 20, 80, 40); });
        assertEquals(1, cache.hits);
        assertArrayEquals(first.getRGB(0, 0, 160, 100, null, 0, 160), second.getRGB(0, 0, 160, 100, null, 0, 160));
        assertNotEquals(stripes().getRGB(50, 40), first.getRGB(50, 40), "the region is blurred");
    }

    @Test
    void glassInsideAGroupLayerSeesWhatIsBehindTheGroup() {
        var image = stripes();
        paint(image, canvas -> canvas.withLayer(new UiLayer(), 1, new UiRect(0, 0, 160, 100),
            group -> group.liquidGlass(30, 20, 100, 60, 20, GLASS.withBlur(0))));
        int a = image.getRGB(40, 50), b = image.getRGB(46, 50);
        assertNotEquals(a, b, "stripes still show through glass painted in a layer");
    }

    @Test
    void readsAreRecordedBeforeClippingSoPartialRepaintsCanCoverThem() {
        var image = stripes();
        var reads = new ArrayList<Rectangle>();
        paint(image, canvas -> {
            canvas.backdropReads = reads;
            canvas.withClip(0, 0, 10, 10, () -> {
                canvas.liquidGlass(30, 20, 100, 60, 20, GLASS);
                canvas.backdropBlur(4, 40, 40, 40, 40);
            });
        });
        assertEquals(2, reads.size(), "glass and blur outside the clip still report what they read");
        assertTrue(reads.getFirst().contains(new Rectangle(30, 20, 100, 60)), "lens read covers the lens");
    }

    @Test
    void partialRepaintGrowsOverEveryReadItTouchesAndTerminates() {
        var reads = List.of(new Rectangle(10, 10, 30, 30), new Rectangle(35, 35, 30, 30), new Rectangle(200, 200, 5, 5));
        UiRect covered = UiGlassCache.cover(new UiRect(12, 12, 2, 2), reads, 1);
        assertTrue(covered.contains(new UiRect(10, 10, 55, 55)), "chained reads merge: " + covered);
        assertFalse(covered.intersects(new UiRect(200, 200, 5, 5)), "untouched reads stay out");
        // Fractional scales produce unions that never compare as containing what they absorbed; still finite.
        UiRect odd = UiGlassCache.cover(new UiRect(1, 1, 1, 1), List.of(new Rectangle(0, 0, 7, 3), new Rectangle(5, 1, 9, 9)), 1.37F);
        assertTrue(odd.width() > 9);
    }

    @Test
    void cacheForgetsLensesAFullPaintNoLongerDraws() {
        var cache = new UiGlassCache();
        var image = stripes();
        paint(image, canvas -> { canvas.glassCache = cache; canvas.liquidGlass(30, 20, 100, 60, 20, GLASS); });
        cache.endPaint(false);
        paint(stripes(), canvas -> { canvas.glassCache = cache; canvas.liquidGlass(30, 20, 100, 60, 20, GLASS); });
        assertEquals(1, cache.hits, "a partial paint keeps the lens");
        cache.endPaint(true);
        cache.endPaint(true);
        paint(stripes(), canvas -> { canvas.glassCache = cache; canvas.liquidGlass(30, 20, 100, 60, 20, GLASS); });
        assertEquals(1, cache.hits, "a full paint without the lens drops it");
    }

    @Test
    void outsideShadowMatchesAClippedShadowAndLeavesTheShapeAlone() {
        var reference = new BufferedImage(240, 200, BufferedImage.TYPE_INT_ARGB);
        var outside = new BufferedImage(240, 200, BufferedImage.TYPE_INT_ARGB);
        var graphics = reference.createGraphics();
        try {
            var clip = new java.awt.geom.Area(new java.awt.Rectangle(0, 0, 240, 200));
            clip.subtract(new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Float(50, 40, 140, 100, 36, 36)));
            graphics.setClip(clip);
            new UiCanvas(graphics, reference).shadow(0x80000000, 18, 0, 8, 50, 40, 140, 100, 18);
        } finally { graphics.dispose(); }
        paint(outside, canvas -> canvas.shadowOutside(0x80000000, 18, 0, 8, 50, 40, 140, 100, 18));
        int worst = 0;
        for (int y = 0; y < 200; y++) for (int x = 0; x < 240; x++) {
            boolean inside = new java.awt.geom.RoundRectangle2D.Float(51, 41, 138, 98, 34, 34).contains(x + .5, y + .5);
            if (inside) { assertEquals(0, outside.getRGB(x, y) >>> 24, "inside the shape at " + x + "," + y); continue; }
            // The reference clip is aliased; the outline's own pixels are antialiased in the fast path.
            if (new java.awt.geom.RoundRectangle2D.Float(48, 38, 144, 104, 40, 40).contains(x + .5, y + .5)) continue;
            int difference = Math.abs((reference.getRGB(x, y) >>> 24) - (outside.getRGB(x, y) >>> 24));
            worst = Math.max(worst, difference);
        }
        assertTrue(worst <= 6, "outside the shape it matches the clipped shadow (worst alpha difference " + worst + ")");
        assertTrue((outside.getRGB(120, 146) >>> 24) > 20, "the shadow falls below the shape");
    }

    @Test
    void glassValuesAreValidatedAndClamped() {
        var glass = new UiGlass(200, 0, 99, 0, 3, -1, 0);
        assertEquals(64, glass.refraction());
        assertEquals(1, glass.rim());
        assertEquals(24, glass.blur());
        assertEquals(1, glass.dispersion());
        assertEquals(0, glass.highlight());
        assertThrows(IllegalArgumentException.class, () -> new UiGlass(Float.NaN, 1, 1, 0, 0, 0, 0));
    }
}
