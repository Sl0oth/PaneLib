package dev.pane.ui;

/**
 * HUD placement in logical UI points, with bounded viewport fitting. Margins apply only on
 * edge-aligned axes; a centered axis ignores its margin. Scale changes size about the chosen anchor.
 */
public record UiHudPlacement(Anchor anchor, float marginX, float marginY, float scale) {
    /** Corner, edge midpoint or centre of the screen a HUD is placed against. */
    public enum Anchor {
        /** The top-left corner. */
        TOP_LEFT,
        /** The top-right corner. */
        TOP_RIGHT,
        /** The bottom-left corner. */
        BOTTOM_LEFT,
        /** The bottom-right corner. */
        BOTTOM_RIGHT,
        /** The centre of the screen. */
        CENTER,
        /** The middle of the top edge. */
        TOP_CENTER,
        /** The middle of the bottom edge. */
        BOTTOM_CENTER,
        /** The middle of the left edge. */
        CENTER_LEFT,
        /** The middle of the right edge. */
        CENTER_RIGHT
    }
    /** A missing anchor means the top left; margins below zero become zero and the scale is kept between 0.5 and 2. */
    public UiHudPlacement {
        anchor = anchor == null ? Anchor.TOP_LEFT : anchor;
        if (!Float.isFinite(marginX) || !Float.isFinite(marginY) || !Float.isFinite(scale))
            throw new IllegalArgumentException("HUD placement must be finite");
        marginX = Math.max(0, marginX); marginY = Math.max(0, marginY);
        scale = Math.max(.5F, Math.min(2, scale));
    }
    /** The top-left corner, 18 points in, at normal size. */
    public static UiHudPlacement topLeft() { return new UiHudPlacement(Anchor.TOP_LEFT, 18, 18, 1); }

    /** Converts point-sized content/margins to GUI coordinates while fitting even small viewports. */
    public UiRect bounds(float viewportWidth, float viewportHeight, float width, float height, float pointsPerGui) {
        float unit = Math.max(.01F, pointsPerGui);
        float w = width * scale / unit, h = height * scale / unit;
        float fit = Math.min(1, Math.min(viewportWidth / Math.max(1, w), viewportHeight / Math.max(1, h)));
        w *= fit; h *= fit;
        float mx = marginX / unit, my = marginY / unit;
        float x = switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT, CENTER_LEFT -> mx;
            case TOP_RIGHT, BOTTOM_RIGHT, CENTER_RIGHT -> viewportWidth - w - mx;
            case CENTER, TOP_CENTER, BOTTOM_CENTER -> (viewportWidth - w) / 2;
        };
        float y = switch (anchor) {
            case TOP_LEFT, TOP_RIGHT, TOP_CENTER -> my;
            case BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM_CENTER -> viewportHeight - h - my;
            case CENTER, CENTER_LEFT, CENTER_RIGHT -> (viewportHeight - h) / 2;
        };
        return new UiRect(Math.max(0, Math.min(viewportWidth - w, x)),
            Math.max(0, Math.min(viewportHeight - h, y)), w, h);
    }
}
