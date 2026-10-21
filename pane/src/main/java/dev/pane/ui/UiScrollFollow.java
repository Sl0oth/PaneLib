package dev.pane.ui;

/** Behavior when a vertical viewport's content or size changes. */
public enum UiScrollFollow {
    /** Keep the current offset, clamping only when necessary. */
    NONE,
    /** Keep the end visible if already there; leave readers of older content in place. */
    WHEN_AT_END
}
