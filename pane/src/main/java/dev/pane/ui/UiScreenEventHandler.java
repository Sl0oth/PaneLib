package dev.pane.ui;

/** Typed events with the owning screen, avoiding holder arrays in screen factories. */
@FunctionalInterface
public interface UiScreenEventHandler {
    void onEvent(UiScreen screen, UiEvent event);
}
