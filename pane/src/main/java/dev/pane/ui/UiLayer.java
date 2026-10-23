package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/** Reusable CPU scratch surface for true group opacity. Own one per simultaneous visible layer. */
public final class UiLayer {
    /** An empty layer; it allocates its scratch storage on first use. */
    public UiLayer() { }

    private BufferedImage image;
    private int[] pixels;
    private Object revision;
    private boolean cached, opaque;
    private BufferedImage cachedImage;
    private int cachedX, cachedY;
    /** Device scale and sub-pixel offset the cached pixels were painted at. */
    float quality = 1, alignX, alignY;

    BufferedImage prepare(int width, int height) {
        cached = false;
        opaque = false;
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

    boolean cached() { return cached; }
    Object revision() { return revision; }

    void finishCache(float quality, float alignX, float alignY) {
        this.quality = quality; this.alignX = alignX; this.alignY = alignY;
        finishCache();
    }

    /** Scan once per revision. Keep a transparent guard pixel for bilinear sampling at crop edges. */
    void finishCache() {
        int width = image.getWidth(), height = image.getHeight();
        // Rows are scanned on several cores in runs; each run reports its extent and whether it is all opaque.
        int runs = Math.max(1, Math.min(32, height / 16));
        int[][] found = new int[runs][];
        boolean[] solid = new boolean[runs];
        UiParallel.forEach(runs, run -> {
            int from = (int) ((long) height * run / runs), to = (int) ((long) height * (run + 1) / runs);
            int left = width, top = height, right = -1, bottom = -1;
            boolean all = true;
            for (int y = from; y < to; y++) for (int x = 0, at = y * width; x < width; x++, at++) {
                int a = pixels[at] >>> 24;
                if (a != 255) all = false;
                if (a == 0) continue;
                if (x < left) left = x;
                if (x > right) right = x;
                if (top == height) top = y;
                bottom = y;
            }
            found[run] = new int[] {left, top, right, bottom};
            solid[run] = all;
        });
        int left = width, top = height, right = -1, bottom = -1;
        opaque = true;
        for (int run = 0; run < runs; run++) {
            int[] f = found[run];
            if (f[2] >= 0) { left = Math.min(left, f[0]); top = Math.min(top, f[1]); right = Math.max(right, f[2]); bottom = Math.max(bottom, f[3]); }
            opaque &= solid[run];
        }
        cached = true;
        if (right < 0) { cachedImage = null; opaque = false; return; }
        cachedX = Math.max(0, left - 1); cachedY = Math.max(0, top - 1);
        right = Math.min(width - 1, right + 1); bottom = Math.min(height - 1, bottom + 1);
        cachedImage = image.getSubimage(cachedX, cachedY, right - cachedX + 1, bottom - cachedY + 1);
        // A crop smaller than the layer keeps a transparent guard pixel, so only a layer opaque everywhere is opaque.
    }
    BufferedImage cachedImage() { return cachedImage; }
    /** Every cached pixel is fully opaque, so drawing it over anything leaves exactly its own pixels. */
    boolean opaque() { return opaque; }
    int cachedX() { return cachedX; }
    int cachedY() { return cachedY; }

    /** Releases scratch storage when a consumer keeps its document beyond the screen lifetime. */
    public void clear() { image = null; cachedImage = null; pixels = null; revision = null; cached = false; }
}
