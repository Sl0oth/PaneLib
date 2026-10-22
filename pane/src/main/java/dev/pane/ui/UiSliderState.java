package dev.pane.ui;

/** What a {@link UiContext#paintedSlider} painter draws: the 0..1 {@code position}, the stored value and interaction state. */
public record UiSliderState(float position, double value, boolean hovered, boolean pressed, boolean focused) { }
