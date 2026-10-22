package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import org.junit.jupiter.api.Test;

class UiCanvasShadowTest {
    private static final int SIZE = 220;

    @Test
    void oneBlendMatchesTheStackedRings() {
        for (double scale : new double[] {1, 1.5, 2, 3}) {
            for (float[] c : new float[][] {{30, 40, 120, 80, 10, 10, 0, 3}, {20.25F, 33.5F, 90, 44, 22, 6, 1, 2},
                    {50, 50, 60, 60, 0, 14, 0, 0}, {10, 70, 160, 30, 15, 3, -2, 1}, {60, 20, 40, 120, 4, 24, 0, 5}}) {
                for (int color : new int[] {0x50000000, 0xFF000000, 0x7A5B9BE3}) {
                    for (float opacity : new float[] {1, .45F}) {
                        int[] expected = paint(scale, opacity, canvas -> rings(canvas, color, c));
                        int[] actual = paint(scale, opacity, canvas -> canvas.shadow(color, c[5], c[6], c[7], c[0], c[1], c[2], c[3], c[4]));
                        assertClose(expected, actual, "scale=" + scale + " case=" + java.util.Arrays.toString(c)
                            + " color=" + Integer.toHexString(color) + " opacity=" + opacity);
                    }
                }
            }
        }
    }

    @Test
    void clippedAndRotatedShadowsStayBounded() {
        int[] clipped = paint(2, 1, false, canvas -> canvas.withClip(0, 0, 40, 40,
            () -> canvas.shadow(0xFF000000, 12, 0, 0, 30, 30, 80, 80, 10)));
        for (int y = 0; y < SIZE * 2; y++) for (int x = 0; x < SIZE * 2; x++)
            if (x >= 80 || y >= 80) assertEquals(0, clipped[y * SIZE * 2 + x], "outside clip " + x + "," + y);
        int[] rotated = paint(2, 1, canvas -> canvas.withRotation(15, 100, 100,
            () -> canvas.shadow(0x80000000, 8, 0, 0, 60, 60, 80, 80, 10)));
        int[] reference = paint(2, 1, canvas -> canvas.withRotation(15, 100, 100,
            () -> rings(canvas, 0x80000000, new float[] {60, 60, 80, 80, 10, 8, 0, 0})));
        assertArrayEquals(reference, rotated, "rotated shadows keep the exact antialiased ring path");
    }

    private static void rings(UiCanvas canvas, int color, float[] c) {
        float spread = c[5];
        int steps = Math.max(3, Math.min(14, Math.round(spread)));
        int base = color >>> 24;
        for (int i = steps; i >= 1; i--) {
            float grow = spread * i / steps;
            int a = Math.max(1, Math.round(base * (1F - (float) i / (steps + 1)) / steps * 2.2F));
            canvas.roundRect(UiCanvas.alpha(color, a), c[0] - grow + c[6], c[1] - grow + c[7],
                c[2] + grow * 2, c[3] + grow * 2, c[4] + grow);
        }
    }

    private static int[] paint(double scale, float opacity, java.util.function.Consumer<UiCanvas> painter) {
        return paint(scale, opacity, true, painter);
    }

    private static int[] paint(double scale, float opacity, boolean background, java.util.function.Consumer<UiCanvas> painter) {
        int pixels = (int) Math.round(SIZE * scale);
        var image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            // Half opaque backdrop, half transparent surface: both retained-surface cases.
            graphics.setColor(new Color(0xFF2A3440, true));
            if (background) graphics.fillRect(0, 0, pixels, pixels / 2);
            graphics.scale(scale, scale);
            var canvas = new UiCanvas(graphics, image);
            canvas.withOpacity(opacity, () -> painter.accept(canvas));
        } finally { graphics.dispose(); }
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    private static void assertClose(int[] expected, int[] actual, String label) {
        int worst = 0;
        long total = 0;
        // Compare premultiplied channels: an almost transparent pixel's straight RGB is not visible.
        for (int i = 0; i < expected.length; i++) for (int shift = 0; shift < 32; shift += 8) {
            int difference = Math.abs(premultiplied(expected[i], shift) - premultiplied(actual[i], shift));
            worst = Math.max(worst, difference); total += difference;
        }
        // Rounding of up to fourteen 8-bit blends and analytic versus sampled edge coverage.
        assertTrue(worst <= 6, label + " worst channel difference " + worst);
        assertTrue(total / (double) (expected.length * 4) < .35, label + " mean difference " + total / (double) (expected.length * 4));
    }

    private static int premultiplied(int argb, int shift) {
        int channel = argb >>> shift & 255;
        return shift == 24 ? channel : Math.round(channel * (argb >>> 24) / 255F);
    }
}
