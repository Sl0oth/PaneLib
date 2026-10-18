package dev.pane.ui;

/** A short native hover description registered while a page is painted. */
record UiTooltip(String id, String text, UiRect anchor) {
    UiTooltip {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Tooltip id is required");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Tooltip text is required");
        if (anchor == null) throw new IllegalArgumentException("Tooltip anchor is required");
    }

    boolean contains(double x, double y) {
        return x >= anchor.x() && y >= anchor.y() && x <= anchor.right() && y <= anchor.bottom();
    }
}
