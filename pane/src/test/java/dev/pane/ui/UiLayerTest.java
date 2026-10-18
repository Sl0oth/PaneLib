package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

final class UiLayerTest {
    @Test void trimmedCacheKeepsTransparentSamplingMarginAndOriginalCoordinates() {
        var layer = new UiLayer();
        assertFalse(layer.reuse(100, 80, 1));
        var g = layer.image().createGraphics();
        try { g.setColor(java.awt.Color.CYAN); g.fillRect(20, 30, 10, 8); }
        finally { g.dispose(); }
        layer.finishCache();
        assertEquals(19, layer.cachedX()); assertEquals(29, layer.cachedY());
        assertEquals(12, layer.cachedImage().getWidth()); assertEquals(10, layer.cachedImage().getHeight());
        assertEquals(0, layer.cachedImage().getRGB(0, 0));
        assertEquals(0xFF00FFFF, layer.cachedImage().getRGB(1, 1));
        assertSame(layer.image().getRaster().getDataBuffer(), layer.cachedImage().getRaster().getDataBuffer(),
            "crop must share existing storage");
        assertTrue(layer.reuse(100, 80, 1));
        assertFalse(layer.reuse(100, 80, 2)); layer.finishCache();
        assertNull(layer.cachedImage(), "empty revisions skip composition");
        layer.clear(); assertNull(layer.image()); assertNull(layer.cachedImage());
    }

    @Test void cachedDrawingReusesContentAcrossTransformsAndKeepsOpacityOutsideTheCache() {
        var image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        int[] calls = {0};
        try {
            var canvas = new UiCanvas(graphics, image);
            var cache = new UiLayer();
            canvas.withOpacity(.5F, () -> canvas.cachedLayer(cache, 1, new UiRect(0, 0, 20, 20), child -> {
                calls[0]++; child.fill(0xFFFFFFFF, 0, 0, 20, 20);
            }));
            canvas.withTransform(UiTransform.identity().translate(30, 0), 0, 0,
                () -> canvas.cachedLayer(cache, 1, new UiRect(0, 0, 20, 20), child -> calls[0]++));
            assertEquals(1, calls[0]);
            assertEquals(128, image.getRGB(5, 5) >>> 24, 1);
            assertEquals(255, image.getRGB(35, 5) >>> 24);
            canvas.cachedLayer(cache, 2, new UiRect(60, 0, 20, 20), child -> {
                calls[0]++; child.fill(0xFFFF0000, 60, 0, 20, 20);
            });
            assertEquals(2, calls[0]);
            assertEquals(0xFFFF0000, image.getRGB(65, 5));
            var tall = new UiLayer();
            canvas.withTransform(new UiTransform(0, 0, 1, 2, 0, 1), 0, 0,
                () -> canvas.cachedLayer(tall, 0, new UiRect(0, 0, 10, 10), child -> { }));
            assertEquals(20, tall.image().getHeight(), "vertical scale contributes to raster quality");
        } finally { graphics.dispose(); }
    }
    @Test void nestedOpacityMultipliesAndRestores() {
        var image = new BufferedImage(50, 40, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var canvas = new UiCanvas(graphics, image);
            canvas.withOpacity(.5F, () -> canvas.withOpacity(.5F,
                () -> canvas.fill(0xFFFFFFFF, 2, 2, 10, 10)));
            assertEquals(64, image.getRGB(4, 4) >>> 24, 1);
            canvas.fill(0xFFFFFFFF, 22, 2, 10, 10);
            assertEquals(255, image.getRGB(24, 4) >>> 24);
        } finally { graphics.dispose(); }
    }

    @Test void groupOpacityCompositesOverlapsOnceAndReusesStorage() {
        var image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.translate(10, 8);
            graphics.scale(2, 2);
            graphics.setClip(0, 0, 20, 20);
            var canvas = new UiCanvas(graphics, image);
            var layer = new UiLayer();
            canvas.withLayer(layer, .5F, new UiRect(0, 0, 30, 20), group -> {
                group.fill(0xFFFFFFFF, 0, 0, 20, 20);
                group.fill(0xFF00FF00, 5, 0, 20, 20);
            });
            assertEquals(128, image.getRGB(12, 12) >>> 24, 1);
            assertEquals(128, image.getRGB(24, 12) >>> 24, 1);
            assertEquals(0, image.getRGB(54, 12));
            assertSame(layer.prepare(60, 40), layer.prepare(60, 40));
        } finally { graphics.dispose(); }
    }
}
