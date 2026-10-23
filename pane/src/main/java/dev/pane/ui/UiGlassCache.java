package dev.pane.ui;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Liquid glass, frost and shadow results kept between paints of one surface. A lens is reused only when its geometry,
 * appearance and every backdrop pixel it reads (by 64-bit hash) are unchanged, so a repaint that
 * leaves the dock's backdrop alone does not recompute the dock.
 */
final class UiGlassCache {
    private static final long MAX_PIXELS = 24L << 20;
    record Key(Rectangle area, Rectangle source, double centerX, double centerY, double halfW, double halfH,
               double corner, UiGlass glass) {
        Key { area = new Rectangle(area); source = new Rectangle(source); }
    }
    /**
     * A finished lens: the surface pixels around it once the lens and its specular edges were drawn
     * at {@code opacity} under the rectangular device {@code clip} (null when unclipped). Over a
     * backdrop with the same hash, drawing it again would leave exactly these pixels.
     */
    record Finished(Key lens, Rectangle snapshot, double scale, float opacity, java.awt.geom.Rectangle2D clip) {
        Finished { snapshot = new Rectangle(snapshot); clip = clip == null ? null : (java.awt.geom.Rectangle2D) clip.clone(); }
    }
    private record Entry(long hash, BufferedImage image) { }
    /** One band of an outside-only shadow: its device rectangle, the caster and shadow geometry, and its alpha ramp. */
    record ShadowKey(Rectangle band, double casterX, double casterY, double casterHalfW, double casterHalfH, double casterCorner,
                     double centerX, double centerY, double innerW, double innerH, double corner, long ramp) {
        ShadowKey { band = new Rectangle(band); }
    }
    private final Map<Object, Entry> entries = new HashMap<>();
    /** Backdrop pixels of the latest read, reused so each paint does not allocate them again. */
    private int[] scratch = new int[0];
    /** Distance-shadow mask kept between paints. */
    BufferedImage shadowMask;

    /** An array of at least {@code size} pixels for one backdrop read; the next call may hand out the same array. */
    int[] scratch(int size) {
        if (scratch.length < size) scratch = new int[Math.max(size, scratch.length + scratch.length / 4)];
        return scratch;
    }
    private final Set<Object> used = new HashSet<>();
    private long pixels;
    long hits, misses;

    BufferedImage find(Object key, long hash) {
        used.add(key);
        Entry entry = entries.get(key);
        if (entry != null && entry.hash == hash) { hits++; return entry.image; }
        misses++;
        return null;
    }

    /** Keeps an entry through the next full paint without looking it up. */
    void touch(Object key) { used.add(key); }

    void store(Object key, long hash, BufferedImage image) {
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

    void clear() { entries.clear(); used.clear(); pixels = 0; scratch = new int[0]; shadowMask = null; }

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

    /**
     * {@link #hash(int[], int)} of the rectangle {@code area} of a raster {@code stride} pixels wide,
     * as if its rows were first copied out one after another; the result is the same number.
     */
    static long hash(int[] raster, int stride, Rectangle area) {
        int length = area.width * area.height, width = area.width;
        if (width <= 0 || length <= 0) return hash(new int[0], 0);
        int chunks = length < 1 << 16 ? 1 : 16, size = (length + chunks - 1) / chunks;
        long[] parts = new long[chunks];
        UiParallel.forEach(chunks, chunk -> {
            long hash = 0xCBF29CE484222325L;
            for (int i = chunk * size, end = Math.min(length, i + size); i < end; ) {
                int row = i / width, column = i % width, run = Math.min(end - i, width - column);
                for (int at = (area.y + row) * stride + area.x + column, stop = at + run; at < stop; at++) hash = (hash ^ raster[at]) * 0x100000001B3L;
                i += run;
            }
            parts[chunk] = hash;
        });
        long hash = length;
        for (long part : parts) hash = (hash ^ part) * 0x9E3779B97F4A7C15L;
        return hash;
    }

    /** Hash of the first {@code length} pixels. */
    static long hash(int[] pixels, int length) {
        // Large backdrops hash in independent chunks across cores, then combine in order.
        int chunks = length < 1 << 16 ? 1 : 16, size = (length + chunks - 1) / chunks;
        long[] parts = new long[chunks];
        UiParallel.forEach(chunks, chunk -> {
            long hash = 0xCBF29CE484222325L;
            for (int i = chunk * size, end = Math.min(length, i + size); i < end; i++) hash = (hash ^ pixels[i]) * 0x100000001B3L;
            parts[chunk] = hash;
        });
        long hash = length;
        for (long part : parts) hash = (hash ^ part) * 0x9E3779B97F4A7C15L;
        return hash;
    }
}
