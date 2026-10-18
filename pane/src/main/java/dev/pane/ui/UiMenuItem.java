package dev.pane.ui;

/** One selectable row in a native {@link UiContext#menuButton} popup. */
public record UiMenuItem(String id, String label, String action) {
    public UiMenuItem {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Menu item id is required");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Menu item label is required");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("Menu item action is required");
    }
}
