package dev.clientui.api;

/**
 * An interactive rectangle in layout units.
 * <p>
 * {@code trackX}/{@code trackWidth} describe the value axis of a continuous control. They
 * are normally identical to {@code x}/{@code width}, but a slider deliberately claims a
 * larger hit rectangle than its visible track so it stays easy to grab; the value must
 * still be mapped against the track, not the padded rectangle.
 */
record UiHitRegion(String id, String action, float x, float y, float width, float height,
                   float trackX, float trackWidth) {

    UiHitRegion(String id, String action, float x, float y, float width, float height) {
        this(id, action, x, y, width, height, x, width);
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** Fraction of the value track at the given layout-space x, clamped to 0..1. */
    double fractionAt(double mouseX) {
        if (trackWidth <= 0) return 0;
        return Math.max(0, Math.min(1, (mouseX - trackX) / trackWidth));
    }
}
