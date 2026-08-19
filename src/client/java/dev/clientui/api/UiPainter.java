package dev.clientui.api;

@FunctionalInterface
public interface UiPainter {
    void paint(UiContext context);
}
