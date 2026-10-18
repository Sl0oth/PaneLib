package dev.pane.ui;

/** Typed event delivered by Pane controls. */
public record UiEvent(UiEventType type, String key, String value) {
    public UiEvent {
        type = type == null ? UiEventType.ACTION : type;
        key = key == null ? "" : key;
        value = value == null ? "" : value;
    }

    public boolean booleanValue() { return Boolean.parseBoolean(value); }

    public double numberValue(double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
