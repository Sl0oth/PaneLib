package dev.pane.ui;

/** The pointer over a tracked region, in the painter's local layout units; see {@link UiContext#pointer}. */
public record UiPointer(boolean inside, float x, float y) {
    public static final UiPointer OUTSIDE = new UiPointer(false, Float.NaN, Float.NaN);
}
