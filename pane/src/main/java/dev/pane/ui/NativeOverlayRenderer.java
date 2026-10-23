package dev.pane.ui;

/** Draws native Minecraft content, such as items and entities, into the slots a page reserved. */
@FunctionalInterface
public interface NativeOverlayRenderer {
    /** Draws nothing. */
    NativeOverlayRenderer NONE = context -> {};
    /** Draws this frame's native content through {@code context}. */
    void render(NativeOverlayContext context);
}
