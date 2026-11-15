package dev.pane.ui;

/**
 * A clipped scroll viewport registered while a page is painted. {@code contentHeight} is the content's
 * extent along the scroll axis: its height, or its width when {@code horizontal}.
 */
record UiScrollRegion(String id, UiRect viewport, float contentHeight, UiRect inputBounds, boolean horizontal) {
    UiScrollRegion(String id, UiRect viewport, float contentHeight) {
        this(id, viewport, contentHeight, viewport, false);
    }

    UiScrollRegion(String id, UiRect viewport, float contentHeight, UiRect inputBounds) {
        this(id, viewport, contentHeight, inputBounds, false);
    }

    UiScrollRegion {
        contentHeight = Math.max(0, contentHeight);
    }

    boolean contains(double x, double y) {
        return x >= inputBounds.x() && x < inputBounds.right() && y >= inputBounds.y() && y < inputBounds.bottom();
    }

    float maxOffset() { return Math.max(0, contentHeight - (horizontal ? viewport.width() : viewport.height())); }
}
