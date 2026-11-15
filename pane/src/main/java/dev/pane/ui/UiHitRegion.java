package dev.pane.ui;

/**
 * An interactive rectangle in layout units.
 * <p>
 * {@code trackX}/{@code trackWidth} describe the value axis of a continuous control. They
 * are normally identical to {@code x}/{@code width}, but a slider deliberately claims a
 * larger hit rectangle than its visible track so it stays easy to grab; the value must
 * still be mapped against the track, not the padded rectangle.
 */
record UiHitRegion(String id, String action, float x, float y, float width, float height,
                   float trackX, float trackWidth, java.awt.Shape hitShape, java.awt.geom.AffineTransform toLocal) {

    /** A region whose track is in the same coordinates as its bounds. */
    UiHitRegion(String id, String action, float x, float y, float width, float height,
                float trackX, float trackWidth, java.awt.Shape hitShape) {
        this(id, action, x, y, width, height, trackX, trackWidth, hitShape, null);
    }

    UiHitRegion(String id, String action, float x, float y, float width, float height,
                float trackX, float trackWidth) {
        this(id, action, x, y, width, height, trackX, trackWidth, null);
    }

    UiHitRegion(String id, String action, float x, float y, float width, float height) {
        this(id, action, x, y, width, height, x, width);
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
            && (hitShape == null || hitShape.contains(mouseX, mouseY));
    }

    /**
     * Fraction of the value track at a layout-space point, clamped to 0..1. Inside a rotated or scaled
     * group the track is in the group's own coordinates and {@code toLocal} maps the point into them.
     */
    double fractionAt(double mouseX, double mouseY) {
        if (trackWidth <= 0) return 0;
        if (toLocal != null) mouseX = toLocal.transform(new java.awt.geom.Point2D.Double(mouseX, mouseY), null).getX();
        return Math.max(0, Math.min(1, (mouseX - trackX) / trackWidth));
    }
}
