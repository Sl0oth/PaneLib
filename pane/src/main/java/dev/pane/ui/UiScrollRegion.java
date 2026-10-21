package dev.pane.ui;

/** A clipped vertical scroll viewport registered while a page is painted. */
record UiScrollRegion(String id, UiRect viewport, float contentHeight, UiRect inputBounds) {
    UiScrollRegion(String id, UiRect viewport, float contentHeight) {
        this(id, viewport, contentHeight, viewport);
    }

    UiScrollRegion {
        contentHeight = Math.max(0, contentHeight);
    }

    boolean contains(double x, double y) {
        return x >= inputBounds.x() && x < inputBounds.right() && y >= inputBounds.y() && y < inputBounds.bottom();
    }

    float maxOffset() { return Math.max(0, contentHeight - viewport.height()); }
}
