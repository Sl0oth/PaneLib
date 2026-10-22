package dev.pane.ui;

/**
 * Appearance of {@link UiCanvas#liquidGlass}. Lengths are layout units: {@code refraction} is how
 * far the rim bends what lies behind it, {@code rim} how deep that bending band reaches inward and
 * {@code blur} how much the body softens. {@code tint} lifts the body toward a colour by its alpha,
 * {@code dispersion} (0..1) separates colour at the rim, {@code highlight} (0..1) sets the specular
 * edge and {@code shadow} is a soft drop shadow drawn only outside the glass (0 for none).
 */
public record UiGlass(float refraction, float rim, float blur, int tint, float dispersion, float highlight, int shadow) {
    public UiGlass {
        if (!Float.isFinite(refraction) || !Float.isFinite(rim) || !Float.isFinite(blur)
                || !Float.isFinite(dispersion) || !Float.isFinite(highlight))
            throw new IllegalArgumentException("Glass values must be finite");
        refraction = Math.max(0, Math.min(64, refraction));
        rim = Math.max(1, Math.min(128, rim));
        blur = Math.max(0, Math.min(24, blur));
        dispersion = Math.max(0, Math.min(1, dispersion));
        highlight = Math.max(0, Math.min(1, highlight));
    }

    /** Panels, docks and sheets: a clearly bending rim over a softened, lifted body. */
    public static UiGlass regular() { return new UiGlass(14, 24, 6, 0x33FFFFFF, .55F, .75F, 0x38000000); }

    /** Controls over busy content: thinner rim, almost no blur. */
    public static UiGlass clear() { return new UiGlass(9, 14, 1.2F, 0x18FFFFFF, .4F, .85F, 0x26000000); }

    public UiGlass withRefraction(float value) { return new UiGlass(value, rim, blur, tint, dispersion, highlight, shadow); }
    public UiGlass withBlur(float value) { return new UiGlass(refraction, rim, value, tint, dispersion, highlight, shadow); }
    public UiGlass withTint(int value) { return new UiGlass(refraction, rim, blur, value, dispersion, highlight, shadow); }
    public UiGlass withShadow(int value) { return new UiGlass(refraction, rim, blur, tint, dispersion, highlight, value); }
}
