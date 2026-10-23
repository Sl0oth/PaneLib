package dev.pane.ui;

/** Stable event categories emitted by a {@link UiScreen}. */
public enum UiEventType {
    /** A control's action ran, such as a button being clicked. */
    ACTION,
    /** A state value changed, such as a field's text. */
    VALUE,
    /** A field was submitted with Enter. */
    SUBMIT,
    /** The screen switched pages. */
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
