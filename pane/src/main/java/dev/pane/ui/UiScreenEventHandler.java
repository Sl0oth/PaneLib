package dev.pane.ui;

/** Typed events with the owning screen, avoiding holder arrays in screen factories. */
@FunctionalInterface
public interface UiScreenEventHandler {
    /** Receives one event together with the screen it came from. */
    void onEvent(UiScreen screen, UiEvent event);
}
