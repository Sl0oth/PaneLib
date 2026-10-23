package dev.pane.ui;

/** Paints one page, HUD or group each time it repaints. */
@FunctionalInterface
public interface UiPainter {
    /** Paints with {@code context}. */
    void paint(UiContext context);
}
