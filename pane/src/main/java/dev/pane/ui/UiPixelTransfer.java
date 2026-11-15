package dev.pane.ui;

import java.nio.IntBuffer;

/** Packs raster pixels for the GPU without changing the AWT surface needed by later partial paints. */
final class UiPixelTransfer {
    private UiPixelTransfer() { }

    /** Rows are shared out between cores once a copy is this many pixels. */
    private static final int PARALLEL_PIXELS = 1 << 16;

    /**
     * Copies a rectangle of a raster {@code stride} pixels wide into {@code packed} from its start,
     * rows back to back, turning each ARGB pixel into the texture's byte order. The buffer's own
     * position is not used or moved.
     */
    static void pack(int[] argb, int stride, int x, int y, int width, int height, IntBuffer packed) {
        java.util.function.IntConsumer row = r -> {
            int from = (y + r) * stride + x, to = r * width;
            for (int i = 0; i < width; i++) packed.put(to + i, swapRedBlue(argb[from + i]));
        };
        // Absolute puts on disjoint rows never share a buffer position.
        if ((long) width * height >= PARALLEL_PIXELS && height > 1 && UiBands.shortcuts) UiParallel.forEach(height, row);
        else for (int r = 0; r < height; r++) row.accept(r);
    }

    /** ARGB to ABGR and back. A R G B -> R G B A -> A B G R; both steps are JIT intrinsics. */
    static int swapRedBlue(int pixel) {
        return Integer.reverseBytes(Integer.rotateLeft(pixel, 8));
    }
}
