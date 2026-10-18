package dev.pane.ui;

/** One visible row supplied by {@link UiContext#virtualList}. */
public record UiVirtualItem(int index, UiRect bounds) { }
