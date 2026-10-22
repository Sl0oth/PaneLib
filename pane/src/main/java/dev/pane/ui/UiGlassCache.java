package dev.pane.ui;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Liquid glass results kept between paints of one surface. A lens is reused only when its geometry,
 * appearance and every backdrop pixel it reads (by 64-bit hash) are unchanged, so a repaint that
 * leaves the dock's backdrop alone does not recompute the dock.
 */
final class UiGlassCache {
    private static final long MAX_PIXELS = 12L << 20;
    record Key(Rectangle area, Rectangle source, double centerX, double centerY, double halfW, double halfH,
               double corner, UiGlass glass) {
        Key { area = new Rectangle(area); source = new Rectangle(source); }
    }
    private record Entry(long hash, BufferedImage image) { }
    private final Map<Key, Entry> entries = new HashMap<>();
    private final Set<Key> used = new HashSet<>();
    private long pixels;
    long hits, misses;

    BufferedImage find(Key key, long hash) {
        used.add(key);
        Entry entry = entries.get(key);
        if (entry != null && entry.hash == hash) { hits++; return entry.image; }
        misses++;
        return null;
    }

    void store(Key key, long hash, BufferedImage image) {
        long size = (long) image.getWidth() * image.getHeight();
        if (size > MAX_PIXELS) return;
        Entry previous = entries.put(key, new Entry(hash, image));
        if (previous != null) pixels -= (long) previous.image.getWidth() * previous.image.getHeight();
        pixels += size;
        if (pixels > MAX_PIXELS) { entries.clear(); entries.put(key, new Entry(hash, image)); pixels = size; }
    }

    /** After a full paint, forgets lenses that paint no longer drew. Partial paints skip clipped glass, so they keep all. */
    void endPaint(boolean full) {
        if (full) {
            entries.keySet().removeIf(key -> {
                if (used.contains(key)) return false;
                Entry entry = entries.get(key);
                pixels -= (long) entry.image.getWidth() * entry.image.getHeight();
                return true;
            });
        }
        used.clear();
    }

    void clear() { entries.clear(); used.clear(); pixels = 0; }

    /**
     * Glass and backdrop blur read pixels around what they draw. A partial repaint touching what
     * one reads grows to all of it, or the lens would sample its own stale output beyond the clip.
     */
    static UiRect cover(UiRect region, java.util.List<Rectangle> backdropReads, float qualityScale) {
        // Each read merges at most once: float unions need not compare as containing what they absorbed.
        boolean[] merged = new boolean[backdropReads.size()];
        for (boolean grew = true; grew; ) {
            grew = false;
            for (int i = 0; i < merged.length; i++) {
                if (merged[i]) continue;
                Rectangle read = backdropReads.get(i);
                UiRect bounds = new UiRect(read.x / qualityScale, read.y / qualityScale,
                    read.width / qualityScale, read.height / qualityScale);
                if (!region.intersects(bounds)) continue;
                merged[i] = true;
                if (!region.contains(bounds)) { region = region.union(bounds); grew = true; }
            }
        }
        return region;
    }

    static long hash(int[] pixels) {
        long hash = 0xCBF29CE484222325L;
        for (int pixel : pixels) hash = (hash ^ pixel) * 0x100000001B3L;
        return hash ^ pixels.length;
    }
}
