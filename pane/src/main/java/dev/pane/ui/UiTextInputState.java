package dev.pane.ui;

/** Cursor and selection information owned by {@link UiScreen} and read by a text field painter. */
record UiTextInputState(String focusedId, int cursor, int anchor, boolean upstream) {
    UiTextInputState(String focusedId, int cursor, int anchor) { this(focusedId, cursor, anchor, false); }
    boolean focused(String id) { return id != null && id.equals(focusedId); }
    int selectionStart() { return Math.min(cursor, anchor); }
    int selectionEnd() { return Math.max(cursor, anchor); }
}
