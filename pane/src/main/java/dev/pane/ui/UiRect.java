package dev.pane.ui;

/** A rectangle in layout units. */
public record UiRect(float x, float y, float width, float height) {
    public UiRect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    public float right() { return x + width; }
    public float bottom() { return y + height; }

    public UiRect inset(float amount) { return inset(amount, amount, amount, amount); }

    public UiRect inset(float left, float top, float right, float bottom) {
        return new UiRect(x + left, y + top, width - left - right, height - top - bottom);
    }
}
