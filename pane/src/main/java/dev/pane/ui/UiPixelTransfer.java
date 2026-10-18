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
                rgba.put(i, pixel & 0xFF00FF00 | pixel >>> 16 & 0xFF | (pixel & 0xFF) << 16);
            }
        }
    }
}
