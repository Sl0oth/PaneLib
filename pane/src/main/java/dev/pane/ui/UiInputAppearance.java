package dev.pane.ui;

/** Immutable per-field paint overrides. Null values inherit the document theme. */
public record UiInputAppearance(Integer background, Integer border, Integer focus, Integer text, Integer placeholder, Integer caret, Integer selection, Float radius, Float borderWidth, Float focusWidth) {
    public UiInputAppearance {
        if (radius != null && (!Float.isFinite(radius) || radius < 0)) throw new IllegalArgumentException("radius must be finite and nonnegative");
        if (borderWidth != null && (!Float.isFinite(borderWidth) || borderWidth < 0)) throw new IllegalArgumentException("borderWidth must be finite and nonnegative");
        if (focusWidth != null && (!Float.isFinite(focusWidth) || focusWidth < 0)) throw new IllegalArgumentException("focusWidth must be finite and nonnegative");
    }
    public static UiInputAppearance defaults() { return new UiInputAppearance(null, null, null, null, null, null, null, null, null, null); }
    public UiInputAppearance background(int value) { return new UiInputAppearance(value, border, focus, text, placeholder, caret, selection, radius, borderWidth, focusWidth); }
    public UiInputAppearance border(int value) { return new UiInputAppearance(background, value, focus, text, placeholder, caret, selection, radius, borderWidth, focusWidth); }
    public UiInputAppearance focus(int value) { return new UiInputAppearance(background, border, value, text, placeholder, caret, selection, radius, borderWidth, focusWidth); }
    public UiInputAppearance text(int value) { return new UiInputAppearance(background, border, focus, value, placeholder, caret, selection, radius, borderWidth, focusWidth); }
    public UiInputAppearance placeholder(int value) { return new UiInputAppearance(background, border, focus, text, value, caret, selection, radius, borderWidth, focusWidth); }
    public UiInputAppearance caret(int value) { return new UiInputAppearance(background, border, focus, text, placeholder, value, selection, radius, borderWidth, focusWidth); }
    public UiInputAppearance selection(int value) { return new UiInputAppearance(background, border, focus, text, placeholder, caret, value, radius, borderWidth, focusWidth); }
    public UiInputAppearance radius(float value) { return new UiInputAppearance(background, border, focus, text, placeholder, caret, selection, value, borderWidth, focusWidth); }
    public UiInputAppearance borderWidth(float value) { return new UiInputAppearance(background, border, focus, text, placeholder, caret, selection, radius, value, focusWidth); }
    public UiInputAppearance focusWidth(float value) { return new UiInputAppearance(background, border, focus, text, placeholder, caret, selection, radius, borderWidth, value); }
    static int color(Integer value, int fallback) { return value == null ? fallback : value; }
    static float metric(Float value, float fallback) { return value == null ? fallback : value; }
}
