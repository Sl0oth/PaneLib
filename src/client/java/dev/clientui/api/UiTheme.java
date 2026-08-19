package dev.clientui.api;

/**
 * Immutable native-renderer design tokens. Colors use ARGB.
 * <p>
 * {@code density} is the interface scale multiplier: 1 renders a layout unit as one
 * operating-system point, higher values make the whole screen physically larger.
 * {@code motionScale} multiplies every transition duration; 0 disables animation entirely.
 */
public record UiTheme(
    int background,
    int surface,
    int raisedSurface,
    int border,
    int text,
    int mutedText,
    int accent,
    int accentText,
    float smallRadius,
    float mediumRadius,
    float largeRadius,
    float density,
    float motionScale
) {
    public UiTheme {
        smallRadius = clamp(smallRadius, 0, 40);
        mediumRadius = clamp(mediumRadius, 0, 48);
        largeRadius = clamp(largeRadius, 0, 64);
        density = clamp(density, .72F, 1.35F);
        motionScale = clamp(motionScale, 0, 2);
    }

    public static UiTheme dark() {
        return new UiTheme(
            0xFF0D0F11, 0xFF15171A, 0xFF1B1E22, 0xFF2D3137,
            0xFFF2F4F7, 0xFF929BAA, 0xFF58A6FF, 0xFF07111D,
            8, 13, 18, 1, 1
        );
    }

    public static UiTheme light() {
        return new UiTheme(
            0xFFF4F5F7, 0xFFFFFFFF, 0xFFEFF1F4, 0xFFDFE3E8,
            0xFF11161C, 0xFF5D6875, 0xFF2F6FEB, 0xFFFFFFFF,
            8, 13, 18, 1, 1
        );
    }

    public UiTheme withDensity(float newDensity) {
        return new UiTheme(background, surface, raisedSurface, border, text, mutedText,
            accent, accentText, smallRadius, mediumRadius, largeRadius, newDensity, motionScale);
    }

    public UiTheme withMotionScale(float newMotionScale) {
        return new UiTheme(background, surface, raisedSurface, border, text, mutedText,
            accent, accentText, smallRadius, mediumRadius, largeRadius, density, newMotionScale);
    }

    public UiTheme withAccent(int newAccent) {
        return new UiTheme(background, surface, raisedSurface, border, text, mutedText,
            newAccent, accentText, smallRadius, mediumRadius, largeRadius, density, motionScale);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
