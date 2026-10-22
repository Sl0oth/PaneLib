package dev.pane.ui;

import java.nio.IntBuffer;

/** Copies into the native texture without changing the AWT surface needed by later partial paints. */
final class UiPixelTransfer {
    private UiPixelTransfer() { }

    static void copy(int[] argb, IntBuffer rgba, int stride, int x, int y, int width, int height) {
        for (int row = y; row < y + height; row++) {
            int start = row * stride + x;
            for (int i = start; i < start + width; i++) rgba.put(i, swapRedBlue(argb[i]));
        }
    }

    /** ARGB to ABGR and back. A R G B -> R G B A -> A B G R; both steps are JIT intrinsics. */
    static int swapRedBlue(int pixel) {
        return Integer.reverseBytes(Integer.rotateLeft(pixel, 8));
    }
}
