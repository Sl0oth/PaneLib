package dev.pane.ui;

import java.awt.geom.AffineTransform;

/** A finite UI transform sampled from any animation tracks. Opacity is multiplied per drawing operation. */
public record UiTransform(float x, float y, float scaleX, float scaleY, float rotationDegrees, float opacity) {
    public UiTransform {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(scaleX) || !Float.isFinite(scaleY)
                || !Float.isFinite(rotationDegrees) || !Float.isFinite(opacity) || scaleX <= 0 || scaleY <= 0)
            throw new IllegalArgumentException("UI transforms require finite values and positive scale");
        opacity = Math.max(0, Math.min(1, opacity));
    }
    public static UiTransform identity() { return new UiTransform(0, 0, 1, 1, 0, 1); }
    public UiTransform translate(float x, float y) { return new UiTransform(x, y, scaleX, scaleY, rotationDegrees, opacity); }
    public UiTransform scale(float scale) { return new UiTransform(x, y, scale, scale, rotationDegrees, opacity); }
    public UiTransform rotate(float degrees) { return new UiTransform(x, y, scaleX, scaleY, degrees, opacity); }
    public UiTransform opacity(float alpha) { return new UiTransform(x, y, scaleX, scaleY, rotationDegrees, alpha); }

    AffineTransform matrix(float pivotX, float pivotY) {
        var matrix = new AffineTransform();
        matrix.translate(x, y); matrix.translate(pivotX, pivotY);
        matrix.rotate(Math.toRadians(rotationDegrees)); matrix.scale(scaleX, scaleY);
        matrix.translate(-pivotX, -pivotY);
        return matrix;
    }
}
