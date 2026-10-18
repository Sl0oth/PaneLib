package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

final class UiCanvasBlurTest {
    @Test void blurRespectsTransformedShapeClipAndPreservesGraphicsState() {
        var image = new BufferedImage(120, 100, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 100; y++) for (int x = 0; x < 120; x++)
            image.setRGB(x, y, (x + y) % 2 == 0 ? 0xFFFFFFFF : 0xFF000000);
        int[] before = image.getRGB(0, 0, 120, 100, null, 0, 120);
        var graphics = image.createGraphics();
        try {
            graphics.translate(10, 6);
            graphics.scale(2, 2);
            var clip = new Ellipse2D.Float(10, 10, 20, 20);
            graphics.clip(clip);
            AffineTransform transform = graphics.getTransform();
            var deviceClip = transform.createTransformedShape(clip);
            new UiCanvas(graphics, image).backdropBlur(3, 0, 0, 50, 40);
            assertEquals(transform, graphics.getTransform());
            assertEquals(clip.getBounds(), graphics.getClip().getBounds());
            int changedInside = 0;
            for (int y = 0; y < 100; y++) for (int x = 0; x < 120; x++) {
                if (!deviceClip.intersects(x, y, 1, 1))
                    assertEquals(before[y * 120 + x], image.getRGB(x, y), "outside clip at " + x + "," + y);
                else if (before[y * 120 + x] != image.getRGB(x, y)) changedInside++;
            }
            assertTrue(changedInside > 0, "blur must still affect the visible region");
        } finally { graphics.dispose(); }
    }

    @Test void fullyClippedBlurDoesNoWork() {
        var image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.setClip(0, 0, 10, 10);
            assertFalse(new UiCanvas(graphics, image).backdropBlur(2, 40, 40, 30, 30));
        } finally { graphics.dispose(); }
    }
}
