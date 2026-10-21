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

    @Test void partialBlurMatchesFullBlurAndReusesOnlyPaddedClipStorage() {
        var full = new BufferedImage(400,300,BufferedImage.TYPE_INT_ARGB);
        var clipped = new BufferedImage(400,300,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<300;y++) for(int x=0;x<400;x++) {
            int color=(x+y)%3==0?0xFFFFFFFF:0xFF223344;
            full.setRGB(x,y,color);clipped.setRGB(x,y,color);
        }
        var scratch=new UiBlurScratch();
        var a=full.createGraphics();var b=clipped.createGraphics();
        try {
            new UiCanvas(a,full).backdropBlur(2,0,0,400,300);
            b.setClip(100,100,20,20);
            new UiCanvas(b,clipped).backdropBlur(2,0,0,400,300,scratch);
            for(int y=100;y<120;y++)for(int x=100;x<120;x++) assertEquals(full.getRGB(x,y),clipped.getRGB(x,y));
            assertEquals(24L*24*2,scratch.retainedPixels());
            assertEquals(2,scratch.allocations);
            new UiCanvas(b,clipped).backdropBlur(2,0,0,400,300,scratch);
            assertEquals(2,scratch.allocations,"same-sized repaint reuses buffers across canvases");
            scratch.releaseIfIdle(System.nanoTime());assertTrue(scratch.retainedPixels()>0);
            scratch.releaseIfIdle(System.nanoTime()+600_000_000L);assertEquals(0,scratch.retainedPixels());
        } finally { a.dispose();b.dispose(); }
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
