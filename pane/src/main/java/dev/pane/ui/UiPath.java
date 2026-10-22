package dev.pane.ui;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;

/**
 * A free-form shape: straight segments, quadratic and cubic curves, filled or stroked.
 * <p>
 * Obtain one from {@link UiCanvas#path()}, describe the outline, then call {@link #fill} or
 * {@link #stroke}. A path may be filled and then stroked to get an outlined shape.
 */
public final class UiPath {
    private final Graphics2D graphics;
    private final Path2D.Float path = new Path2D.Float();
    private boolean started;

    UiPath(Graphics2D graphics) {
        this.graphics = graphics;
    }

    public UiPath moveTo(float x, float y) {
        path.moveTo(x, y);
        started = true;
        return this;
    }

    public UiPath lineTo(float x, float y) {
        if (!started) return moveTo(x, y);
        path.lineTo(x, y);
        return this;
    }

    /** Quadratic curve through one control point. */
    public UiPath curveTo(float controlX, float controlY, float x, float y) {
        if (!started) return moveTo(x, y);
        path.quadTo(controlX, controlY, x, y);
        return this;
    }

    /** Cubic curve through two control points. */
    public UiPath curveTo(float controlX1, float controlY1, float controlX2, float controlY2, float x, float y) {
        if (!started) return moveTo(x, y);
        path.curveTo(controlX1, controlY1, controlX2, controlY2, x, y);
        return this;
    }

    /**
     * Continues along a circular arc, connecting from the current point with a straight segment.
     * Angles are clockwise from east, like gradients; a negative sweep runs counter-clockwise.
     * Two arcs of opposite sweep form a ring segment for radial menus.
     */
    public UiPath arc(float centerX, float centerY, float radius, float startDegrees, float sweepDegrees) {
        var arc = new java.awt.geom.Arc2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2,
            -startDegrees, -sweepDegrees, java.awt.geom.Arc2D.OPEN);
        path.append(arc, started);
        started = true;
        return this;
    }

    public UiPath close() {
        if (started) path.closePath();
        return this;
    }

    /** The outline described so far, in the coordinates it was built with. */
    java.awt.Shape shape() { return path; }

    public UiPath fill(int color) {
        if (!started) return this;
        graphics.setColor(UiCanvas.awtColor(color));
        graphics.fill(path);
        return this;
    }

    /** Fills with a linear ramp between two colours, angled in degrees clockwise from east. */
    public UiPath fillGradient(int from, int to, float degrees) {
        if (!started) return this;
        java.awt.geom.Rectangle2D bounds = path.getBounds2D();
        graphics.setPaint(UiCanvas.linearPaint(from, to, degrees,
            (float) bounds.getX(), (float) bounds.getY(),
            (float) bounds.getWidth(), (float) bounds.getHeight()));
        graphics.fill(path);
        graphics.setPaint(UiCanvas.awtColor(0xFF000000));
        return this;
    }

    public UiPath stroke(int color, float width) {
        return stroke(color, width, true);
    }

    public UiPath stroke(int color, float width, boolean rounded) {
        if (!started) return this;
        graphics.setColor(UiCanvas.awtColor(color));
        graphics.setStroke(rounded
            ? new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            : new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        graphics.draw(path);
        return this;
    }
}
