package dev.pane.ui;

/** One value a native {@link UiContext#dropdown} control can select. */
public record UiSelectOption(String value, String label) {
    /** Requires a value and a label. */
    public UiSelectOption {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Option value is required");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Option label is required");
    }
}
