package dev.pane.ui;

/** Consumer hook for Escape/back navigation. */
@FunctionalInterface
public interface UiCloseHandler {
    UiCloseHandler NONE = screen -> false;

    /** Return true after handling the close. False restores the configured parent or closes normally. */
    boolean onClose(UiScreen screen);
}
