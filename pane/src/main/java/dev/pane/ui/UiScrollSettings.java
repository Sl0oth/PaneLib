package dev.pane.ui;

/** Document-wide wheel/trackpad scroll feel in layout units. */
public record UiScrollSettings(float step, int smoothingMillis) {
    /** Keeps a wheel step between 1 and 400 units and smoothing between 0 and 500 milliseconds. */
    public UiScrollSettings {
        step = Math.max(1, Math.min(400, step));
        smoothingMillis = Math.max(0, Math.min(500, smoothingMillis));
    }

    /** Short finite interpolation: responsive on a wheel and fluid on a trackpad. */
    public static UiScrollSettings smooth() { return new UiScrollSettings(46, 105); }

    /** No interpolation, for accessibility or intentionally immediate interfaces. */
    public static UiScrollSettings immediate() { return new UiScrollSettings(46, 0); }
}
