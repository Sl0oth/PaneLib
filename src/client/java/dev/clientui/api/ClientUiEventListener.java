package dev.clientui.api;

@FunctionalInterface
public interface ClientUiEventListener {
    ClientUiEventListener NONE = (type, key, value) -> {};
    void onEvent(String type, String key, String value);
}
