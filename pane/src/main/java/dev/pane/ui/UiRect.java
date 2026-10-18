package dev.pane.ui;

/** A rectangle in layout units. */
public record UiRect(float x, float y, float width, float height) {
    public UiRect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    public float right() { return x + width; }
    public float bottom() { return y + height; }
    public float centerX() { return x + width / 2; }
    public float centerY() { return y + height / 2; }
    public boolean contains(float pointX, float pointY) {
        return pointX >= x && pointX <= right() && pointY >= y && pointY <= bottom();
    }

    public UiRect translate(float offsetX, float offsetY) {
        return new UiRect(x + offsetX, y + offsetY, width, height);
    }

    public UiRect inset(float amount) { return inset(amount, amount, amount, amount); }

    public UiRect inset(float left, float top, float right, float bottom) {
        return new UiRect(x + left, y + top, width - left - right, height - top - bottom);
    }
}
