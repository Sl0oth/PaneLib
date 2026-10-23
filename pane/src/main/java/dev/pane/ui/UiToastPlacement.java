package dev.pane.ui;

/** Screen edge where a toast stack is drawn. {@link #AUTO} avoids the focused control when possible. */
public enum UiToastPlacement {
    /** A corner chosen to keep clear of the focused control. */
    AUTO,
    /** The top-left corner. */
    TOP_LEFT,
    /** The top-right corner. */
    TOP_RIGHT,
    /** The bottom-left corner. */
    BOTTOM_LEFT,
    /** The bottom-right corner. */
    BOTTOM_RIGHT
}
