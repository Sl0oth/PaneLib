package dev.pane.ui;

/** Built-in easing curves for finite retained-mode animations. */
public enum UiEasing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    PAPER,
    /** Duration-bounded damped spring with restrained overshoot and an exact settled endpoint. */
    SPRING;

    float apply(float value) {
        float t = Math.max(0, Math.min(1, value));
        return switch (this) {
            case SPRING -> t <= 0 ? 0 : t >= 1 ? 1
                : 1 - (float) (Math.exp(-6 * t) * (Math.cos(10 * t) + .6 * Math.sin(10 * t)));
            case LINEAR -> t;
            case EASE_IN -> t * t * t;
            case EASE_OUT -> 1 - (float) Math.pow(1 - t, 3);
            case EASE_IN_OUT -> t < .5F ? 4 * t * t * t
                : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
            // A quick, controlled curve that feels physical without overshoot or bounce.
            case PAPER -> 1 - (float) Math.pow(1 - t, 4);
        };
    }
}
