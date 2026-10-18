package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/** Reusable CPU scratch surface for true group opacity. Own one per simultaneous visible layer. */
public final class UiLayer {
    private BufferedImage image;
    private int[] pixels;
    private Object revision;
    private boolean cached;
    private BufferedImage cachedImage;
    private int cachedX, cachedY;

    BufferedImage prepare(int width, int height) {
        cached = false;
        cachedImage = null;
        if (image == null || image.getWidth() != width || image.getHeight() != height) {
            // Java2D's transformed alpha compositing consumes premultiplied pixels.
            // Keep scratch/cache storage in that format to avoid conversion on every draw.
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        } else Arrays.fill(pixels, 0);
        return image;
    }

    boolean reuse(int width, int height, Object revision) {
        if (cached && image != null && image.getWidth() == width && image.getHeight() == height
                && java.util.Objects.equals(this.revision, revision)) return true;
        prepare(width, height);
        this.revision = revision;
        return false;
    }
    BufferedImage image() { return image; }

    /** Scan once per revision. Keep a transparent guard pixel for bilinear sampling at crop edges. */
    void finishCache() {
        int width = image.getWidth(), height = image.getHeight();
        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if ((pixels[y * width + x] >>> 24) == 0) continue;
            left = Math.min(left, x); top = Math.min(top, y);
            right = Math.max(right, x); bottom = Math.max(bottom, y);
        }
        cached = true;
        if (right < 0) { cachedImage = null; return; }
        cachedX = Math.max(0, left - 1); cachedY = Math.max(0, top - 1);
        right = Math.min(width - 1, right + 1); bottom = Math.min(height - 1, bottom + 1);
        cachedImage = image.getSubimage(cachedX, cachedY, right - cachedX + 1, bottom - cachedY + 1);
    }
    BufferedImage cachedImage() { return cachedImage; }
    int cachedX() { return cachedX; }
    int cachedY() { return cachedY; }

    /** Releases scratch storage when a consumer keeps its document beyond the screen lifetime. */
    public void clear() { image = null; cachedImage = null; pixels = null; revision = null; cached = false; }
}
