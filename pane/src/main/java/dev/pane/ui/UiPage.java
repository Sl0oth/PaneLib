package dev.pane.ui;

/** One route in a native UI document. */
public record UiPage(String id, String navigationLabel, String navigationIcon, int accent, UiPainter painter) {
    /** Requires a lowercase id of up to 32 letters, digits, - and _, and a painter; a missing label or icon is empty. */
    public UiPage {
        if (id == null || !id.matches("[a-z][a-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("Page ids must be lowercase identifiers");
        }
        navigationLabel = navigationLabel == null ? "" : navigationLabel;
        navigationIcon = navigationIcon == null ? "" : navigationIcon;
        if (painter == null) throw new IllegalArgumentException("A page needs a painter");
    }
}
