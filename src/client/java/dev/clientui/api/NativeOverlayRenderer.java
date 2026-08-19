package dev.clientui.api;

@FunctionalInterface
public interface NativeOverlayRenderer {
    NativeOverlayRenderer NONE = context -> {};
    void render(NativeOverlayContext context);
}
