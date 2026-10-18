package dev.pane.ui;

/** Typed alternative to the source-compatible three-string {@link UiEventListener}. */
@FunctionalInterface
public interface UiEventHandler {
    UiEventHandler NONE = event -> {};
    void onEvent(UiEvent event);
}
