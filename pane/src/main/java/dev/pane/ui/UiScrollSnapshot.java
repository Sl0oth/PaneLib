package dev.pane.ui;

/** Last painted viewport in layout units and current/target vertical offsets. */
public record UiScrollSnapshot(String id, UiRect viewport, float contentHeight, double offset, double targetOffset) {
    /** The largest scroll offset the content allows. */
    public double maximumOffset() { return Math.max(0, contentHeight - viewport.height()); }
}
