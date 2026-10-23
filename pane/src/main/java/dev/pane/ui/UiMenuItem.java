package dev.pane.ui;

/**
 * One row in a native popup menu. {@code shortcut} is right-aligned hint text such as "⌘W" (empty for
 * none), a disabled row is shown dimmed and cannot be chosen, and {@code separated} draws a divider
 * above the row to start a new group.
 */
public record UiMenuItem(String id, String label, String action, String shortcut, boolean enabled, boolean separated) {
    /** Requires an id, a label and an action; a missing shortcut is empty. */
    public UiMenuItem {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Menu item id is required");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Menu item label is required");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("Menu item action is required");
        shortcut = shortcut == null ? "" : shortcut;
    }

    /** An enabled item with no shortcut hint that does not start a group. */
    public UiMenuItem(String id, String label, String action) { this(id, label, action, "", true, false); }

    /** A copy showing {@code hint}, such as "⌘N", at the end of its row. */
    public UiMenuItem shortcut(String hint) { return new UiMenuItem(id, label, action, hint, enabled, separated); }
    /** A copy that is enabled or greyed out. */
    public UiMenuItem enabled(boolean value) { return new UiMenuItem(id, label, action, shortcut, value, separated); }
    /** A copy with a divider above it. */
    public UiMenuItem startsGroup() { return new UiMenuItem(id, label, action, shortcut, enabled, true); }
}
