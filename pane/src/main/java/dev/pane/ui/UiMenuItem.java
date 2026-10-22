package dev.pane.ui;

/**
 * One row in a native popup menu. {@code shortcut} is right-aligned hint text such as "⌘W" (empty for
 * none), a disabled row is shown dimmed and cannot be chosen, and {@code separated} draws a divider
 * above the row to start a new group.
 */
public record UiMenuItem(String id, String label, String action, String shortcut, boolean enabled, boolean separated) {
    public UiMenuItem {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Menu item id is required");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Menu item label is required");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("Menu item action is required");
        shortcut = shortcut == null ? "" : shortcut;
    }

    public UiMenuItem(String id, String label, String action) { this(id, label, action, "", true, false); }

    public UiMenuItem shortcut(String hint) { return new UiMenuItem(id, label, action, hint, enabled, separated); }
    public UiMenuItem enabled(boolean value) { return new UiMenuItem(id, label, action, shortcut, value, separated); }
    public UiMenuItem startsGroup() { return new UiMenuItem(id, label, action, shortcut, enabled, true); }
}
