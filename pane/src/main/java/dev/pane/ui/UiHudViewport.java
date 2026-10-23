package dev.pane.ui;

/** Available HUD area in logical UI points, independent of Minecraft GUI scale. */
public record UiHudViewport(float width, float height) {
    /** Requires finite, positive dimensions. */
    public UiHudViewport {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0)
            throw new IllegalArgumentException("HUD viewport dimensions must be finite and positive");
    }
}
