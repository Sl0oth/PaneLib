package dev.pane.ui;

/** Typed event delivered by Pane controls. */
public record UiEvent(UiEventType type, String key, String value) {
    /** A missing type means an action; a missing key or value is empty. */
    public UiEvent {
        type = type == null ? UiEventType.ACTION : type;
        key = key == null ? "" : key;
        value = value == null ? "" : value;
    }

    /** The value read as a boolean: true only for "true". */
    public boolean booleanValue() { return Boolean.parseBoolean(value); }

    /** The value read as a number, or {@code fallback} when it is not one. */
    public double numberValue(double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
