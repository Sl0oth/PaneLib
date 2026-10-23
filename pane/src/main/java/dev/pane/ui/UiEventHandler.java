package dev.pane.ui;

/** Typed alternative to the source-compatible three-string {@link UiEventListener}. */
@FunctionalInterface
public interface UiEventHandler {
    /** Ignores every event. */
    UiEventHandler NONE = event -> {};
    /** Receives one event from the screen. */
    void onEvent(UiEvent event);
}
