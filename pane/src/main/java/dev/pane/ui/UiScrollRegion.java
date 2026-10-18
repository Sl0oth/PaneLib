package dev.pane.ui;

/** A clipped vertical scroll viewport registered while a page is painted. */
record UiScrollRegion(String id, UiRect viewport, float contentHeight) {
    UiScrollRegion {
        contentHeight = Math.max(0, contentHeight);
    }

    boolean contains(double x, double y) {
        return x >= viewport.x() && x < viewport.right() && y >= viewport.y() && y < viewport.bottom();
    }

    float maxOffset() { return Math.max(0, contentHeight - viewport.height()); }
}
