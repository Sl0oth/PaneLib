package dev.pane.ui;

/** Semantic label and current interaction state supplied to a custom button painter. */
public record UiButtonState(String label, boolean enabled, boolean hovered, boolean pressed, boolean focused) { }
