package dev.pane.ui;

import java.util.List;

/** A pending native popup menu registered while a page is painted. */
record UiMenu(String id, UiRect anchor, List<UiMenuItem> items, String selectedItemId, UiMenuStyle style) {
    UiMenu(String id, UiRect anchor, List<UiMenuItem> items) {
        this(id, anchor, items, null, UiMenuStyle.defaults());
    }

    UiMenu(String id, UiRect anchor, List<UiMenuItem> items, String selectedItemId) {
        this(id, anchor, items, selectedItemId, UiMenuStyle.defaults());
    }

    UiMenu {
        style = java.util.Objects.requireNonNull(style, "style");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Menu id is required");
        if (anchor == null) throw new IllegalArgumentException("Menu anchor is required");
        items = items == null ? List.of() : List.copyOf(items);
    }
}
