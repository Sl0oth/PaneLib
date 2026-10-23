package dev.pane.ui;

/** The type a text input painted its text with, so composition from an input method can match it. */
record UiInputTypography(float size, int style, String family, int text, int accent, int surface) { }
