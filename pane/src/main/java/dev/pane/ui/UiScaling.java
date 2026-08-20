package dev.pane.ui;

/**
 * How a screen's layout responds to window size.
 * <p>
 * Sizing purely in operating-system points keeps a screen physically consistent across
 * displays, but it means a maximised window just gets more empty space and proportionally
 * smaller text. Scaling in direct proportion to the window is the opposite mistake: the
 * design stops being a design and becomes a zoom level.
 * <p>
 * This applies a damped middle. {@code adaptivity} is how much of the extra room is spent
 * making the interface bigger rather than fitting more in: {@code 0} is strict point sizing,
 * {@code 1} scales in lockstep with the window, and the default spends a little over half.
 *
 * @param referenceWidth  window width, in points, the design was authored for
 * @param referenceHeight window height, in points, the design was authored for
 * @param adaptivity      0..1, how strongly the interface grows with the window
 * @param minScale        lower clamp on the resulting interface scale
 * @param maxScale        upper clamp on the resulting interface scale
 */
public record UiScaling(float referenceWidth, float referenceHeight, float adaptivity,
                        float minScale, float maxScale) {

    public UiScaling {
        referenceWidth = Math.max(120, referenceWidth);
        referenceHeight = Math.max(90, referenceHeight);
        adaptivity = clamp(adaptivity, 0, 1);
        minScale = clamp(minScale, .25F, 8);
        maxScale = clamp(maxScale, minScale, 8);
    }

    /** Damped growth from a 860x480 point reference. Suits full-window application screens. */
    public static UiScaling standard() {
        return new UiScaling(860, 480, .6F, .8F, 2.5F);
    }

    /** Strict point sizing: the interface is the same physical size in every window. */
    public static UiScaling fixed() {
        return new UiScaling(860, 480, 0, 1, 1);
    }

    public UiScaling reference(float width, float height) {
        return new UiScaling(width, height, adaptivity, minScale, maxScale);
    }

    public UiScaling adaptivity(float adaptivity) {
        return new UiScaling(referenceWidth, referenceHeight, adaptivity, minScale, maxScale);
    }

    public UiScaling range(float minScale, float maxScale) {
        return new UiScaling(referenceWidth, referenceHeight, adaptivity, minScale, maxScale);
    }

    /** Interface scale for a window of the given size in operating-system points. */
    public float scaleFor(double pointsWidth, double pointsHeight) {
        double room = Math.min(pointsWidth / referenceWidth, pointsHeight / referenceHeight);
        if (!Double.isFinite(room) || room <= 0) return 1;
        return (float) clamp((float) (1 + (room - 1) * adaptivity), minScale, maxScale);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
