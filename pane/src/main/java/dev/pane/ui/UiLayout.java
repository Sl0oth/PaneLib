package dev.pane.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A small sequential layout helper for painters.
 * <p>
 * Use a column or row to reserve rectangles in order, then paint those rectangles however you
 * like. The layout reports its natural content size, which makes it useful for scrollable
 * content as well as ordinary responsive screens.
 */
public final class UiLayout {
    private enum Axis { HORIZONTAL, VERTICAL, STACK }

    private final UiRect bounds;
    private final Axis axis;
    private float gap;
    private float cursor;
    private boolean hasItems;

    private UiLayout(UiRect bounds, Axis axis) {
        this.bounds = bounds == null ? new UiRect(0, 0, 0, 0) : bounds;
        this.axis = axis;
    }

    /** Reserves full-width rectangles from top to bottom. */
    public static UiLayout column(UiRect bounds) { return new UiLayout(bounds, Axis.VERTICAL); }

    /** Reserves full-height rectangles from left to right. */
    public static UiLayout row(UiRect bounds) { return new UiLayout(bounds, Axis.HORIZONTAL); }

    /** Overlays layers in the same rectangle. Call {@link #layer()} for each layer. */
    public static UiLayout stack(UiRect bounds) { return new UiLayout(bounds, Axis.STACK); }

    /** Gap inserted between consecutive {@link #next(float)} calls. */
    public UiLayout gap(float amount) {
        gap = Math.max(0, amount);
        return this;
    }

    /**
     * Reserves the next rectangle along the layout's main axis.
     * For a column, {@code size} is height; for a row, it is width.
     */
    public UiRect next(float size) {
        if (axis == Axis.STACK) return layer();
        if (hasItems) cursor += gap;
        float extent = Math.max(0, size);
        UiRect result = axis == Axis.VERTICAL
            ? new UiRect(bounds.x(), bounds.y() + cursor, bounds.width(), extent)
            : new UiRect(bounds.x() + cursor, bounds.y(), extent, bounds.height());
        cursor += extent;
        hasItems = true;
        return result;
    }

    /** Reserves all remaining room and advances this layout to its end. */
    public UiRect rest() {
        UiRect result = remaining();
        if (axis == Axis.VERTICAL) cursor = bounds.height();
        else if (axis == Axis.HORIZONTAL) cursor = bounds.width();
        hasItems = true;
        return result;
    }

    /**
     * Divides the remaining room by weights, applying the configured gap between cells.
     * For example, {@code row.distribute(1, 2, 1)} creates a 25/50/25 split.
     */
    public List<UiRect> distribute(float... weights) {
        if (axis == Axis.STACK || weights == null || weights.length == 0) return List.of();
        UiRect room = remaining();
        float total = 0;
        for (float weight : weights) total += Math.max(0, weight);
        if (total <= 0) return List.of();
        float available = (axis == Axis.VERTICAL ? room.height() : room.width()) - gap * (weights.length - 1);
        available = Math.max(0, available);
        List<UiRect> result = new ArrayList<>(weights.length);
        float position = axis == Axis.VERTICAL ? room.y() : room.x();
        for (float weight : weights) {
            float size = available * Math.max(0, weight) / total;
            result.add(axis == Axis.VERTICAL
                ? new UiRect(room.x(), position, room.width(), size)
                : new UiRect(position, room.y(), size, room.height()));
            position += size + gap;
        }
        cursor = axis == Axis.VERTICAL ? bounds.height() : bounds.width();
        hasItems = true;
        return List.copyOf(result);
    }

    /** Adds exact blank space without applying the configured item gap. */
    public UiLayout spacer(float amount) {
        if (axis != Axis.STACK) cursor += Math.max(0, amount);
        return this;
    }

    /** The unused portion of the original bounds. */
    public UiRect remaining() {
        if (axis == Axis.VERTICAL) {
            return new UiRect(bounds.x(), bounds.y() + cursor, bounds.width(), Math.max(0, bounds.height() - cursor));
        }
        if (axis == Axis.HORIZONTAL) {
            return new UiRect(bounds.x() + cursor, bounds.y(), Math.max(0, bounds.width() - cursor), bounds.height());
        }
        return bounds;
    }

    /** Returns the layout's full bounds for an overlay layer. */
    public UiRect layer() { return bounds; }

    /** Natural content size along the main axis, including inter-item gaps and spacers. */
    public float used() { return cursor; }

    /** Natural height. This is the value a vertical scroll region needs. */
    public float contentHeight() { return axis == Axis.VERTICAL ? cursor : bounds.height(); }

    /** Natural width. This is the value a horizontal scroll region needs. */
    public float contentWidth() { return axis == Axis.HORIZONTAL ? cursor : bounds.width(); }
}
