package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

final class UiPixelTransferTest {
    @Test void packingSwapsRedAndBlueAndLeavesTheRasterAsItWas() {
        int[] source = {0xFF123456, 0x80123456, 0xFFABCDEF, 0x00010203, 0xFF0000FF, 0xFFFF0000};
        int[] original = source.clone();
        var packed = ByteBuffer.allocateDirect(source.length * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        UiPixelTransfer.pack(source, 3, 0, 0, 3, 2, packed);
        assertArrayEquals(original, source, "AWT backing must remain ARGB for blur and partial paints");
        assertEquals(0xFF563412, packed.get(0));
        assertEquals(0x80563412, packed.get(1));
        UiPixelTransfer.pack(source, 3, 0, 0, 3, 2, packed);
        assertEquals(0xFF563412, packed.get(0), "a second pack must not swap colors back");
        source[4] = 0x80224466;
        UiPixelTransfer.pack(source, 3, 1, 1, 1, 1, packed);
        assertEquals(0x80664422, packed.get(0), "one pixel packs to the start of the buffer");
    }

    @Test void largeRectanglesMatchTheirPixelByPixelResult() {
        int stride = 700, rows = 400;
        int[] source = new int[stride * rows];
        var random = new java.util.Random(3);
        for (int i = 0; i < source.length; i++) source[i] = random.nextInt();
        var packed = ByteBuffer.allocateDirect(650 * 380 * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        UiPixelTransfer.pack(source, stride, 13, 7, 650, 380, packed);
        for (int y = 0; y < 380; y++) for (int x = 0; x < 650; x++)
            assertEquals(UiPixelTransfer.swapRedBlue(source[(y + 7) * stride + x + 13]), packed.get(y * 650 + x));
    }

    @Test void randomRectanglesPackRowsBackToBackAndKeepTheBufferPosition() {
        var random = new java.util.Random(0x50495845);
        for (boolean direct : new boolean[]{false, true}) for (int sample = 0; sample < 180; sample++) {
            int stride = 1 + random.nextInt(90), rows = 1 + random.nextInt(40);
            int x = random.nextInt(stride), y = random.nextInt(rows);
            int width = random.nextInt(stride - x + 1), height = random.nextInt(rows - y + 1);
            int[] source = new int[stride * rows];
            for (int i = 0; i < source.length; i++) source[i] = random.nextInt();
            int[] original = source.clone();
            var packed = direct ? ByteBuffer.allocateDirect(source.length * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
                : java.nio.IntBuffer.allocate(source.length);
            for (int i = 0; i < source.length; i++) packed.put(i, 0x13579BDF);
            int position = random.nextInt(source.length + 1);
            packed.position(position);
            UiPixelTransfer.pack(source, stride, x, y, width, height, packed);
            assertArrayEquals(original, source);
            assertEquals(position, packed.position());
            assertEquals(source.length, packed.limit());
            for (int index = 0; index < source.length; index++) {
                int expected = 0x13579BDF;
                if (index < width * height) {
                    int pixel = source[(y + index / width) * stride + x + index % width];
                    expected = pixel & 0xFF00FF00 | pixel >>> 16 & 0xFF | (pixel & 0xFF) << 16;
                }
                assertEquals(expected, packed.get(index), "sample=" + sample + " pixel=" + index);
            }
        }
    }

    @Test void bandsNeverHoldMoreThanTheSharedBufferAndAlwaysHoldARow() {
        assertEquals(UiRetainedTexture.BAND_PIXELS, UiRetainedTexture.rowsPerBand(1));
        assertEquals(409, UiRetainedTexture.rowsPerBand(2560));
        assertEquals(1, UiRetainedTexture.rowsPerBand(UiRetainedTexture.BAND_PIXELS * 2));
        for (int columns : new int[]{1, 7, 640, 1920, 2560, 5120, 8192})
            assertTrue((long) columns * UiRetainedTexture.rowsPerBand(columns) <= Math.max(columns, UiRetainedTexture.BAND_PIXELS));
    }
}
