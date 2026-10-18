package dev.pane.ui;

/** Stable event categories emitted by a {@link UiScreen}. */
public enum UiEventType {
    ACTION,
    VALUE,
    SUBMIT,
    PAGE;

    static UiEventType fromWire(String value) {
        if (value == null) return ACTION;
        try {
            return valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ACTION;
        }
    }
}
