package dev.pane.ui;

/** Reusable scalable icon contract, shared by the built-in set and consumer-authored vectors. */
@FunctionalInterface
public interface UiSymbol {
    void draw(UiCanvas canvas, int color, float x, float y, float size, float stroke);

    /** Author geometry once in a viewbox; drawing fits and centers it in the requested square.
     * Construct and retain symbols outside paint loops. This is vector drawing, not an SVG parser. */
    static UiSymbol vector(float width, float height, Painter painter) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Icon viewbox must be finite and positive");
        java.util.Objects.requireNonNull(painter, "painter");
        return (canvas, color, x, y, size, stroke) -> {
            if (!Float.isFinite(size) || !Float.isFinite(stroke) || size <= 0 || stroke < 0)
                throw new IllegalArgumentException("Invalid icon size or stroke");
            float scale = size / Math.max(width, height);
            canvas.withTranslation(x + (size - width * scale) / 2, y + (size - height * scale) / 2,
                () -> canvas.withScale(scale, scale, 0, 0, () -> painter.paint(canvas, color, stroke / scale)));
        };
    }

    @FunctionalInterface
    interface Painter { void paint(UiCanvas canvas, int color, float stroke); }
}
