package dev.pane.ui;

import java.awt.image.BufferedImage;

/** One screen-owned blur workspace; exact-sized and released when its dialog closes. */
final class UiBlurScratch {
    BufferedImage source, horizontal;
    long allocations;
    private long lastUsed;
    void ensure(int width, int height) {
        lastUsed = System.nanoTime();
        if (source != null && source.getWidth() == width && source.getHeight() == height) return;
        source = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        horizontal = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        allocations += 2;
    }
    long retainedPixels() { return source == null ? 0 : (long) source.getWidth() * source.getHeight() * 2; }
    void releaseIfIdle(long now) {
        if (source != null && now - lastUsed >= 500_000_000L) clear();
    }
    void clear() { source = null; horizontal = null; }
}
