package dev.pane.ui;

@FunctionalInterface
public interface NativeOverlayRenderer {
    NativeOverlayRenderer NONE = context -> {};
    void render(NativeOverlayContext context);
}
