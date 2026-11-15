package dev.pane.ui;

/**
 * Display-text treatments for titles and game-style labels: an outline, a drop shadow, a
 * top-to-bottom colour ramp and a forward lean, in any combination. Pass one to
 * {@link UiCanvas#text(String, float, float, float, int, int, String, UiTextEffect)}.
 * <pre>{@code
 * var title = UiTextEffect.outline(0xFF10203A, 3).withShadow(0x80000000, 0, 4).withGradient(0xFFFFC53D);
 * canvas.text("VICTORY", 40, 30, 64, 0xFFFFF3B0, Font.BOLD, family, title);
 * }</pre>
 * Effects are immutable; each {@code with...} method returns a changed copy.
 */
public final class UiTextEffect {
    final Integer outline, shadow, gradient;
    final float outlineWidth, shadowX, shadowY, lean;

    private UiTextEffect(Integer outline, float outlineWidth, Integer shadow, float shadowX, float shadowY, Integer gradient, float lean) {
        if (!Float.isFinite(outlineWidth) || !Float.isFinite(shadowX) || !Float.isFinite(shadowY) || !Float.isFinite(lean) || Math.abs(lean) >= 89)
            throw new IllegalArgumentException("Text effects require finite sizes and a lean between -89 and 89 degrees");
        this.lean = lean;
        this.outline = outline; this.outlineWidth = Math.max(0, outlineWidth);
        this.shadow = shadow; this.shadowX = shadowX; this.shadowY = shadowY; this.gradient = gradient;
    }

    /** An outline of {@code width} around every glyph, drawn outside the letter so the fill keeps its weight. */
    public static UiTextEffect outline(int color, float width) { return new UiTextEffect(color, width, null, 0, 0, null, 0); }
    /** A hard drop shadow: the text's silhouette, outline included, drawn again at an offset. */
    public static UiTextEffect shadow(int color, float offsetX, float offsetY) { return new UiTextEffect(null, 0, color, offsetX, offsetY, null, 0); }
    /** A ramp from the text's own colour at the top of the line to {@code bottomColor} at the bottom. */
    public static UiTextEffect gradient(int bottomColor) { return new UiTextEffect(null, 0, null, 0, 0, bottomColor, 0); }
    /** Text leaning forward by {@code degrees} about its baseline, like heavy italic display type; negative leans back. */
    public static UiTextEffect lean(float degrees) { return new UiTextEffect(null, 0, null, 0, 0, null, degrees); }

    /** A copy with this outline. */
    public UiTextEffect withOutline(int color, float width) { return new UiTextEffect(color, width, shadow, shadowX, shadowY, gradient, lean); }
    /** A copy with this drop shadow. */
    public UiTextEffect withShadow(int color, float offsetX, float offsetY) { return new UiTextEffect(outline, outlineWidth, color, offsetX, offsetY, gradient, lean); }
    /** A copy whose fill ramps to {@code bottomColor}. */
    public UiTextEffect withGradient(int bottomColor) { return new UiTextEffect(outline, outlineWidth, shadow, shadowX, shadowY, bottomColor, lean); }
    /** A copy leaning forward by {@code degrees} about its baseline. */
    public UiTextEffect withLean(float degrees) { return new UiTextEffect(outline, outlineWidth, shadow, shadowX, shadowY, gradient, degrees); }
}
