package dev.pane.ui;

/**
 * Last painted viewport in layout units and current/target offsets. For a region made by
 * {@link UiContext#scrollHorizontal}, {@code horizontal} is true and {@code contentHeight} is the
 * content's width.
 */
public record UiScrollSnapshot(String id, UiRect viewport, float contentHeight, double offset, double targetOffset, boolean horizontal) {
    /** A vertical region's snapshot. */
    public UiScrollSnapshot(String id, UiRect viewport, float contentHeight, double offset, double targetOffset) {
        this(id, viewport, contentHeight, offset, targetOffset, false);
    }

    /** The largest scroll offset the content allows. */
    public double maximumOffset() { return Math.max(0, contentHeight - (horizontal ? viewport.width() : viewport.height())); }
}
