package dev.pane.ui;

/** Receives screen events as three strings: the event type, its key and its value. */
@FunctionalInterface
public interface UiEventListener {
    /** Ignores every event. */
    UiEventListener NONE = (type, key, value) -> {};
    /** Receives one event: {@code type} such as "action" or "value", the control or state key, and the value. */
    void onEvent(String type, String key, String value);
}
