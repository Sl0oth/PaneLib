package dev.pane.ui;

import java.util.List;

/** A pending native popup menu registered while a page is painted. */
record UiMenu(String id, UiRect anchor, List<UiMenuItem> items) {
    UiMenu {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Menu id is required");
        if (anchor == null) throw new IllegalArgumentException("Menu anchor is required");
        items = items == null ? List.of() : List.copyOf(items);
    }
}
