package dev.pane.ui;

/** HUD placement in logical UI points, with bounded viewport fitting. */
public record UiHudPlacement(Anchor anchor, float marginX, float marginY, float scale) {
    public enum Anchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER }
    public UiHudPlacement {
        anchor = anchor == null ? Anchor.TOP_LEFT : anchor;
        if (!Float.isFinite(marginX) || !Float.isFinite(marginY) || !Float.isFinite(scale))
            throw new IllegalArgumentException("HUD placement must be finite");
        marginX = Math.max(0, marginX); marginY = Math.max(0, marginY);
        scale = Math.max(.5F, Math.min(2, scale));
    }
    public static UiHudPlacement topLeft() { return new UiHudPlacement(Anchor.TOP_LEFT, 18, 18, 1); }

    /** Converts point-sized content/margins to GUI coordinates while fitting even small viewports. */
    public UiRect bounds(float viewportWidth, float viewportHeight, float width, float height, float pointsPerGui) {
        float unit = Math.max(.01F, pointsPerGui);
        float w = width * scale / unit, h = height * scale / unit;
        float fit = Math.min(1, Math.min(viewportWidth / Math.max(1, w), viewportHeight / Math.max(1, h)));
        w *= fit; h *= fit;
        float mx = marginX / unit, my = marginY / unit;
        float x = switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> mx;
            case TOP_RIGHT, BOTTOM_RIGHT -> viewportWidth - w - mx;
            case CENTER -> (viewportWidth - w) / 2;
        };
        float y = switch (anchor) {
            case TOP_LEFT, TOP_RIGHT -> my;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> viewportHeight - h - my;
            case CENTER -> (viewportHeight - h) / 2;
        };
        return new UiRect(Math.max(0, Math.min(viewportWidth - w, x)),
            Math.max(0, Math.min(viewportHeight - h, y)), w, h);
    }
}
