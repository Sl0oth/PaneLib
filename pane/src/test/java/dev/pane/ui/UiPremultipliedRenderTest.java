package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

final class UiPremultipliedRenderTest {
    @Test void transformedTranslucentCachePreservesEdgesTextAndGroupOpacity() throws Exception {
        var reference = render(false);
        var optimized = render(true);
        long error = 0; int maximum = 0;
        for (int y = 0; y < reference.getHeight(); y++) for (int x = 0; x < reference.getWidth(); x++) {
            int a = reference.getRGB(x, y), b = optimized.getRGB(x, y);
            assertEquals(a >>> 24, b >>> 24);
            for (int shift : new int[]{0, 8, 16}) {
                int difference = Math.abs((a >>> shift & 255) - (b >>> shift & 255));
                error += difference; maximum = Math.max(maximum, difference);
            }
        }
        double mean = error / (double)(reference.getWidth() * reference.getHeight() * 3);
        System.out.printf("Premultiplied visual comparison maxChannelError=%d meanChannelError=%.4f%n", maximum, mean);
        assertTrue(maximum <= 4, "visible channel difference: " + maximum);
        assertTrue(mean < .15, "mean visible channel difference: " + mean);
        var directory = java.nio.file.Path.of("build", "render-check");
        java.nio.file.Files.createDirectories(directory);
        javax.imageio.ImageIO.write(reference, "png", directory.resolve("reference.png").toFile());
        javax.imageio.ImageIO.write(optimized, "png", directory.resolve("optimized.png").toFile());
    }
    private static BufferedImage render(boolean optimized) {
        int type = optimized ? BufferedImage.TYPE_INT_ARGB_PRE : BufferedImage.TYPE_INT_ARGB;
        var surface = new BufferedImage(640, 260, BufferedImage.TYPE_INT_ARGB);
        var g = surface.createGraphics();
        try {
            var canvas = new UiCanvas(g, surface);
            var cache = new BufferedImage(240, 180, type);
            var child = cache.createGraphics();
            try {
                var c = new UiCanvas(child, cache);
                c.shadow(0x70000000, 9, 0, 5, 10, 10, 215, 140, 14);
                c.roundRect(0xC3163D57, 10, 10, 215, 140, 14);
                c.strokeRoundRect(0xC064D4FF, 1, 10, 10, 215, 140, 14);
                c.text("Atlas / 012345", 26, 30, 15, 0xFFFFFFFF, Font.BOLD, UiCanvas.SANS);
                c.circle(0x8064D4FF, 75, 105, 28);
                c.circle(0x90FFFFFF, 98, 105, 28);
            } finally { child.dispose(); }
            var layer = new UiLayer();
            if (optimized) {
                layer.reuse(240, 180, 0);
                var painter = layer.image().createGraphics();
                try { painter.drawImage(cache, 0, 0, null); } finally { painter.dispose(); }
                layer.finishCache();
            }
            for (int i = 0; i < 2; i++) {
                g.translate(35 + i * 310, 32);
                g.rotate(Math.toRadians(-7));
                g.scale(1.06, 1.06);
                canvas.withOpacity(.73F, () -> {
                    if (optimized) g.drawImage(layer.cachedImage(), layer.cachedX(), layer.cachedY(), null);
                    else g.drawImage(cache, 0, 0, null);
                });
                g.setTransform(new java.awt.geom.AffineTransform());
            }
        } finally { g.dispose(); }
        var output = new BufferedImage(640, 260, BufferedImage.TYPE_INT_RGB);
        var out = output.createGraphics();
        try {
            out.setColor(new Color(0x0B2032)); out.fillRect(0, 0, 320, 260);
            out.setColor(new Color(0xE8F1F8)); out.fillRect(320, 0, 320, 260);
            out.drawImage(surface, 0, 0, null);
        } finally { out.dispose(); }
        return output;
    }
}
