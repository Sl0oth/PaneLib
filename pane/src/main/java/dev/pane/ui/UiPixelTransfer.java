package dev.pane.ui;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Copies into the native texture without changing the AWT surface needed by later partial paints. */
final class UiPixelTransfer {
    private UiPixelTransfer() { }

    /** Rows are shared out between cores once a copy is this many pixels. */
    private static final int PARALLEL_PIXELS = 1 << 16;

    static void copy(int[] argb, IntBuffer rgba, int stride, int x, int y, int width, int height) {
        java.util.function.IntConsumer row = r -> {
            int start = (y + r) * stride + x;
            for (int i = start; i < start + width; i++) rgba.put(i, swapRedBlue(argb[i]));
        };
        // Absolute puts on disjoint rows never share a buffer position.
        if ((long) width * height >= PARALLEL_PIXELS && height > 1 && UiBands.shortcuts) UiParallel.forEach(height, row);
        else for (int r = 0; r < height; r++) row.accept(r);
    }

    /** Copies a rectangle of 4-byte pixels from a raster {@code stride} pixels wide into {@code packed}, rows back to back. */
    static void pack(ByteBuffer raster, int stride, int x, int y, int width, int height, ByteBuffer packed) {
        for (int row = 0; row < height; row++)
            packed.put(row * width * 4, raster, ((y + row) * stride + x) * 4, width * 4);
    }

    /** ARGB to ABGR and back. A R G B -> R G B A -> A B G R; both steps are JIT intrinsics. */
    static int swapRedBlue(int pixel) {
        return Integer.reverseBytes(Integer.rotateLeft(pixel, 8));
    }
}
