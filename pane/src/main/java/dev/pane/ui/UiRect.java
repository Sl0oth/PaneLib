package dev.pane.ui;

/** A rectangle in layout units. */
public record UiRect(float x, float y, float width, float height) {
    /** A negative width or height becomes zero. */
    public UiRect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    /** The right edge. */
    public float right() { return x + width; }
    /** The bottom edge. */
    public float bottom() { return y + height; }
    /** The horizontal centre. */
    public float centerX() { return x + width / 2; }
    /** The vertical centre. */
    public float centerY() { return y + height / 2; }
    /** Whether a point lies inside, edges included on the top and left. */
    public boolean contains(float pointX, float pointY) {
        return pointX >= x && pointX <= right() && pointY >= y && pointY <= bottom();
    }

    /** The same rectangle moved by an offset. */
    public UiRect translate(float offsetX, float offsetY) {
        return new UiRect(x + offsetX, y + offsetY, width, height);
    }

    /** The rectangle shrunk by {@code amount} on every side. */
    public UiRect inset(float amount) { return inset(amount, amount, amount, amount); }

    /** The rectangle shrunk by a separate amount on each side. */
    public UiRect inset(float left, float top, float right, float bottom) {
        return new UiRect(x + left, y + top, width - left - right, height - top - bottom);
    }

    /** Smallest rectangle containing both. */
    UiRect union(UiRect other) {
        float left = Math.min(x, other.x), top = Math.min(y, other.y);
        return new UiRect(left, top, Math.max(right(), other.right()) - left, Math.max(bottom(), other.bottom()) - top);
    }

    /** Overlap; zero-sized when the rectangles are disjoint. */
    UiRect intersection(UiRect other) {
        float left = Math.max(x, other.x), top = Math.max(y, other.y);
        return new UiRect(left, top, Math.min(right(), other.right()) - left, Math.min(bottom(), other.bottom()) - top);
    }

    boolean intersects(UiRect other) {
        return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
    }

    boolean contains(UiRect inner) {
        return inner.x >= x && inner.y >= y && inner.right() <= right() && inner.bottom() <= bottom();
    }

    /** Grows by {@code padding} on every side, clamped to a {@code 0..width} by {@code 0..height} surface. */
    UiRect expandAndClamp(int width, int height, float padding) {
        float left = Math.max(0, x - padding), top = Math.max(0, y - padding);
        return new UiRect(left, top, Math.min(width, right() + padding) - left, Math.min(height, bottom() + padding) - top);
    }
}
