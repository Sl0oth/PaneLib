package dev.pane.ui;

import java.nio.IntBuffer;

/** Copies into the native texture without changing the AWT surface needed by later partial paints. */
final class UiPixelTransfer {
    private UiPixelTransfer() { }

    static void copy(int[] argb, IntBuffer rgba, int stride, int x, int y, int width, int height) {
        for (int row = y; row < y + height; row++) {
            int start = row * stride + x;
            for (int i = start; i < start + width; i++) {
                int pixel = argb[i];
                // A R G B -> R G B A -> A B G R. Rotation/reversal use JIT intrinsics;
                // the source stays ARGB, with no scratch array or extra copy.
                rgba.put(i, Integer.reverseBytes(Integer.rotateLeft(pixel, 8)));
            }
        }
    }
}
