package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

final class UiPixelTransferTest {
    @Test void fullThenPartialUploadPreservesBackingColorsAndUntouchedNativePixels() {
        int[] source = {0xFF123456, 0x80123456, 0xFFABCDEF, 0x00010203, 0xFF0000FF, 0xFFFF0000};
        int[] original = source.clone();
        var bytes = ByteBuffer.allocateDirect(source.length * 4).order(ByteOrder.nativeOrder());
        var target = bytes.asIntBuffer();
        UiPixelTransfer.copy(source, target, 3, 0, 0, 3, 2);
        assertArrayEquals(original, source, "AWT backing must remain ARGB for blur and partial paints");
        assertEquals(0xFF563412, target.get(0));
        assertEquals(0x80563412, target.get(1));
        int[] uploaded = new int[6];
        target.get(0, uploaded);
        source[4] = 0x80224466;
        UiPixelTransfer.copy(source, target, 3, 1, 1, 1, 1);
        assertEquals(0x80224466, source[4]);
        assertEquals(0x80664422, target.get(4));
        for (int i = 0; i < 6; i++) if (i != 4) assertEquals(uploaded[i], target.get(i));
        UiPixelTransfer.copy(source, target, 3, 0, 0, 3, 2);
        assertEquals(0xFF563412, target.get(0), "a second full copy must not swap colors back");
    }
    @Test void packedRectanglesHoldExactlyTheirRowsBackToBack() {
        int stride = 37, rows = 23;
        var raster = ByteBuffer.allocateDirect(stride * rows * 4).order(ByteOrder.nativeOrder());
        for (int i = 0; i < stride * rows; i++) raster.putInt(i * 4, i * 7919);
        var packed = ByteBuffer.allocateDirect(stride * rows * 4).order(ByteOrder.nativeOrder());
        UiPixelTransfer.pack(raster, stride, 5, 3, 11, 9, packed);
        for (int row = 0; row < 9; row++) for (int column = 0; column < 11; column++)
            assertEquals(raster.getInt(((3 + row) * stride + 5 + column) * 4), packed.getInt((row * 11 + column) * 4));
        assertEquals(0, raster.position());
        assertEquals(0, packed.position());
    }

    @Test void largeCopiesMatchTheirRowByRowResult() {
        int stride = 700, rows = 400;
        int[] source = new int[stride * rows];
        var random = new java.util.Random(3);
        for (int i = 0; i < source.length; i++) source[i] = random.nextInt();
        var target = ByteBuffer.allocateDirect(source.length * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        UiPixelTransfer.copy(source, target, stride, 13, 7, 650, 380);
        for (int y = 0; y < rows; y++) for (int x = 0; x < stride; x++) {
            boolean inside = x >= 13 && x < 663 && y >= 7 && y < 387;
            assertEquals(inside ? UiPixelTransfer.swapRedBlue(source[y * stride + x]) : 0, target.get(y * stride + x));
        }
    }

    @Test void randomRectanglesPreserveChannelsUntouchedPixelsAndBufferPosition() {
        var random = new java.util.Random(0x50495845);
        for (boolean direct : new boolean[]{false, true}) for (int sample = 0; sample < 180; sample++) {
            int stride = 1 + random.nextInt(90), rows = 1 + random.nextInt(40);
            int x = random.nextInt(stride), y = random.nextInt(rows);
            int width = random.nextInt(stride - x + 1), height = random.nextInt(rows - y + 1);
            int[] source = new int[stride * rows];
            for (int i = 0; i < source.length; i++) source[i] = random.nextInt();
            int[] original = source.clone();
            var target = direct ? ByteBuffer.allocateDirect(source.length * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
                : java.nio.IntBuffer.allocate(source.length);
            for (int i = 0; i < source.length; i++) target.put(i, 0x13579BDF);
            int position = random.nextInt(source.length + 1);
            target.position(position);
            UiPixelTransfer.copy(source, target, stride, x, y, width, height);
            assertArrayEquals(original, source);
            assertEquals(position, target.position());
            assertEquals(source.length, target.limit());
            for (int row = 0; row < rows; row++) for (int column = 0; column < stride; column++) {
                int index = row * stride + column, pixel = source[index];
                int expected = row >= y && row < y + height && column >= x && column < x + width
                    ? pixel & 0xFF00FF00 | pixel >>> 16 & 0xFF | (pixel & 0xFF) << 16 : 0x13579BDF;
                assertEquals(expected, target.get(index), "sample=" + sample + " pixel=" + index);
            }
        }
    }
}
