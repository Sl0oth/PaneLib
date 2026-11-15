package dev.pane.ui;

/**
 * Immutable native-renderer design tokens. Colors use ARGB.
 * <p>
 * {@code density} is the interface scale multiplier: 1 renders a layout unit as one
 * operating-system point, higher values make the whole screen physically larger.
 * {@code motionScale} multiplies every transition duration; 0 disables animation entirely.
 * {@code danger}, {@code success} and {@code warning} are status colours for painters to draw with.
 * <p>
 * Start from {@link #dark()} or {@link #light()} and change what differs, one token per call:
 * {@code UiTheme.dark().withBackground(0xFF101418).withAccent(0xFF9BE564)}.
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
    float motionScale,
    String fontFamily,
    int danger,
    int success,
    int warning
) {
    private static final int DANGER = 0xFFF2666D, SUCCESS = 0xFF4CC38A, WARNING = 0xFFE5B454;

    /** Keeps radii, density and motion scale within their working ranges; a missing font family means sans serif. */
    public UiTheme {
        smallRadius = clamp(smallRadius, 0, 40);
        mediumRadius = clamp(mediumRadius, 0, 48);
        largeRadius = clamp(largeRadius, 0, 64);
        density = clamp(density, .72F, 1.35F);
        motionScale = clamp(motionScale, 0, 2);
        fontFamily = fontFamily == null || fontFamily.isBlank() ? UiCanvas.SANS : fontFamily;
    }

    /** Source-compatible constructor from before the status colours, which take the dark theme's. */
    public UiTheme(int background, int surface, int raisedSurface, int border, int text, int mutedText,
                   int accent, int accentText, float smallRadius, float mediumRadius, float largeRadius,
                   float density, float motionScale, String fontFamily) {
        this(background, surface, raisedSurface, border, text, mutedText, accent, accentText, smallRadius,
            mediumRadius, largeRadius, density, motionScale, fontFamily, DANGER, SUCCESS, WARNING);
    }

    /** Source-compatible constructor using Pane's dependable native sans-serif default. */
    public UiTheme(int background, int surface, int raisedSurface, int border, int text, int mutedText,
                   int accent, int accentText, float smallRadius, float mediumRadius, float largeRadius,
                   float density, float motionScale) {
        this(background, surface, raisedSurface, border, text, mutedText, accent, accentText, smallRadius,
            mediumRadius, largeRadius, density, motionScale, UiCanvas.SANS);
    }

    /** The default dark theme. */
    public static UiTheme dark() {
        return new UiTheme(
            0xFF0D0F11, 0xFF15171A, 0xFF1B1E22, 0xFF2D3137,
            0xFFF2F4F7, 0xFF929BAA, 0xFF58A6FF, 0xFF07111D,
            8, 13, 18, 1, 1, UiCanvas.SANS, DANGER, SUCCESS, WARNING
        );
    }
    /** The default light theme. */
    public static UiTheme light() {
        return new UiTheme(
            0xFFF4F5F7, 0xFFFFFFFF, 0xFFEFF1F4, 0xFFDFE3E8,
            0xFF11161C, 0xFF5D6875, 0xFF2F6FEB, 0xFFFFFFFF,
            8, 13, 18, 1, 1, UiCanvas.SANS, 0xFFD1343E, 0xFF1F8A55, 0xFFB7791F
        );
    }
    /** A copy with {@code background}, the page background colour, replaced. */
    public UiTheme withBackground(int newBackground) {
        return new UiTheme(newBackground, surface, raisedSurface, border, text, mutedText, accent, accentText, smallRadius, mediumRadius, largeRadius, density, motionScale, fontFamily, danger, success, warning);
    }
    /** A copy with {@code surface}, the colour of cards and panels, replaced. */
    public UiTheme withSurface(int newSurface) {
        return new UiTheme(background, newSurface, raisedSurface, border, text, mutedText, accent, accentText, smallRadius, mediumRadius, largeRadius, density, motionScale, fontFamily, danger, success, warning);
    }
    /** A copy with {@code raisedSurface}, the colour of raised controls such as neutral buttons, replaced. */
    public UiTheme withRaisedSurface(int newRaisedSurface) {
        return new UiTheme(background, surface, newRaisedSurface, border, text, mutedText, accent, accentText, smallRadius, mediumRadius, largeRadius, density, motionScale, fontFamily, danger, success, warning);
    }
    /** A copy with {@code border}, the colour of outlines and dividers, replaced. */
    public UiTheme withBorder(int newBorder) {
        return new UiTheme(background, surface, raisedSurface, newBorder, text, mutedText, accent, accentText, smallRadius, mediumRadius, largeRadius, density, motionScale, fontFamily, danger, success, warning);
    }
    /** A copy with {@code text}, the main text colour, replaced. */
    public UiTheme withText(int newText) {
        return new UiTheme(background, surface, raisedSurface, border, newText, mutedText, accent, accentText, smallRadius, mediumRadius, largeRadius, density, motionScale, fontFamily, danger, success, warning);
    }
    /** A copy with {@code mutedText}, the colour of secondary text, replaced. */
    public UiTheme withMutedText(int newMutedText) {
        return new UiTheme(background, surface, raisedSurface, border, text, newMutedText, accent, accentText, smallRadius, mediumRadius, largeRadius, density, motionScale, fontFamily, danger, success, warning);
    }
    /** A copy with {@code accent} replaced. */
    public UiTheme withAccent(int newAccent) {
        return new UiTheme(background, surface, raisedSurface, border, text, mutedText, newAccent, accentText, smallRadius, mediumRadius, largeRadius, density, motionScale, fontFamily, danger, success, warning);
    }
    /** A copy with {@code accentText}, the colour of text drawn on the accent, replaced. */
    public UiTheme withAccentText(int newAccentText) {
        return new UiTheme(background, surface, raisedSurface, border, text, mutedText, accent, newAccentText, smallRadius, mediumRadius, largeRadius, density, motionScale, fontFamily, danger, success, warning);
    }
    /** A copy with {@code density} replaced. */
    public UiTheme withDensity(float newDensity) {
        return new UiTheme(background, surface, raisedSurface, border, text, mutedText, accent, accentText, smallRadius, mediumRadius, largeRadius, newDensity, motionScale, fontFamily, danger, success, warning);
    }
    /** A copy with {@code motionScale} replaced. */
    public UiTheme withMotionScale(float newMotionScale) {
        return new UiTheme(background, surface, raisedSurface, border, text, mutedText, accent, accentText, smallRadius, mediumRadius, largeRadius, density, newMotionScale, fontFamily, danger, success, warning);
    }
    /** Sets the family used by Pane's built-in controls and overlay chrome. */
    public UiTheme withFontFamily(String newFontFamily) {
        return new UiTheme(background, surface, raisedSurface, border, text, mutedText, accent, accentText, smallRadius, mediumRadius, largeRadius, density, motionScale, newFontFamily, danger, success, warning);
    }
    /** A copy with {@code danger}, the colour for errors and destructive actions, replaced. */
    public UiTheme withDanger(int newDanger) {
        return new UiTheme(background, surface, raisedSurface, border, text, mutedText, accent, accentText, smallRadius, mediumRadius, largeRadius, density, motionScale, fontFamily, newDanger, success, warning);
    }
    /** A copy with {@code success}, the colour for confirmations and healthy states, replaced. */
    public UiTheme withSuccess(int newSuccess) {
        return new UiTheme(background, surface, raisedSurface, border, text, mutedText, accent, accentText, smallRadius, mediumRadius, largeRadius, density, motionScale, fontFamily, danger, newSuccess, warning);
    }
    /** A copy with {@code warning}, the colour for cautions, replaced. */
    public UiTheme withWarning(int newWarning) {
        return new UiTheme(background, surface, raisedSurface, border, text, mutedText, accent, accentText, smallRadius, mediumRadius, largeRadius, density, motionScale, fontFamily, danger, success, newWarning);
    }
    /** A copy with the small, medium and large corner radii replaced. */
    public UiTheme withRadii(float small, float medium, float large) {
        return new UiTheme(background, surface, raisedSurface, border, text, mutedText, accent, accentText, small, medium, large, density, motionScale, fontFamily, danger, success, warning);
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
