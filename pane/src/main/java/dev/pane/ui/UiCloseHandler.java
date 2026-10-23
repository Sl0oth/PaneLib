package dev.pane.ui;

/** Consumer hook for Escape/back navigation. */
@FunctionalInterface
public interface UiCloseHandler {
    /** Never stops the screen closing. */
    UiCloseHandler NONE = screen -> false;

    /** Return true after handling the close. False restores the configured parent or closes normally. */
    boolean onClose(UiScreen screen);
}
