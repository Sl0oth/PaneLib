package dev.pane.ui;

/** Immutable per-field paint overrides. Null values inherit the document theme. */
public record UiInputAppearance(Integer background, Integer border, Integer focus, Integer text, Integer placeholder, Integer caret, Integer selection, Float radius, Float borderWidth, Float focusWidth) {
    /** Requires any radius and widths given to be finite and not negative. */
    public UiInputAppearance {
        if (radius != null && (!Float.isFinite(radius) || radius < 0)) throw new IllegalArgumentException("radius must be finite and nonnegative");
        if (borderWidth != null && (!Float.isFinite(borderWidth) || borderWidth < 0)) throw new IllegalArgumentException("borderWidth must be finite and nonnegative");
        if (focusWidth != null && (!Float.isFinite(focusWidth) || focusWidth < 0)) throw new IllegalArgumentException("focusWidth must be finite and nonnegative");
    }
    /** Every value unset, so each uses the theme. */
    public static UiInputAppearance defaults() { return new UiInputAppearance(null, null, null, null, null, null, null, null, null, null); }
    /** A copy with this background colour, as ARGB. */
    public UiInputAppearance background(int value) { return new UiInputAppearance(value, border, focus, text, placeholder, caret, selection, radius, borderWidth, focusWidth); }
    /** A copy with this border colour, as ARGB. */
    public UiInputAppearance border(int value) { return new UiInputAppearance(background, value, focus, text, placeholder, caret, selection, radius, borderWidth, focusWidth); }
    /** A copy with this focus ring colour, as ARGB. */
    public UiInputAppearance focus(int value) { return new UiInputAppearance(background, border, value, text, placeholder, caret, selection, radius, borderWidth, focusWidth); }
    /** A copy with this text colour, as ARGB. */
    public UiInputAppearance text(int value) { return new UiInputAppearance(background, border, focus, value, placeholder, caret, selection, radius, borderWidth, focusWidth); }
    /** A copy with this placeholder colour, as ARGB. */
    public UiInputAppearance placeholder(int value) { return new UiInputAppearance(background, border, focus, text, value, caret, selection, radius, borderWidth, focusWidth); }
    /** A copy with this caret colour, as ARGB. */
    public UiInputAppearance caret(int value) { return new UiInputAppearance(background, border, focus, text, placeholder, value, selection, radius, borderWidth, focusWidth); }
    /** A copy with this selection colour, as ARGB. */
    public UiInputAppearance selection(int value) { return new UiInputAppearance(background, border, focus, text, placeholder, caret, value, radius, borderWidth, focusWidth); }
    /** A copy with this corner radius. */
    public UiInputAppearance radius(float value) { return new UiInputAppearance(background, border, focus, text, placeholder, caret, selection, value, borderWidth, focusWidth); }
    /** A copy with this border width. */
    public UiInputAppearance borderWidth(float value) { return new UiInputAppearance(background, border, focus, text, placeholder, caret, selection, radius, value, focusWidth); }
    /** A copy with this focus ring width. */
    public UiInputAppearance focusWidth(float value) { return new UiInputAppearance(background, border, focus, text, placeholder, caret, selection, radius, borderWidth, value); }
    static int color(Integer value, int fallback) { return value == null ? fallback : value; }
    static float metric(Float value, float fallback) { return value == null ? fallback : value; }
}
