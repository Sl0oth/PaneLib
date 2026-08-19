package dev.pane.ui;

@FunctionalInterface
public interface UiEventListener {
    UiEventListener NONE = (type, key, value) -> {};
    void onEvent(String type, String key, String value);
}
