package dev.pane.ui;

/** Consumer-owned raw click handling, including right-click and true double-click presses. */
@FunctionalInterface
public interface UiClickHandler {
    UiClickHandler NONE = (screen, event) -> false;

    /** Return true when the event was handled and PaneLib should not run the target's normal action. */
    boolean mouseClicked(UiScreen screen, UiClickEvent event);
}
