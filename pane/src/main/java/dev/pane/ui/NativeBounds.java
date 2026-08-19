package dev.pane.ui;

/** A rectangle reserved for Minecraft's own renderer, measured in physical backing pixels. */
public record NativeBounds(double x, double y, double width, double height) {
    public int logicalX(double scale) { return (int) Math.round(x / scale); }
    public int logicalY(double scale) { return (int) Math.round(y / scale); }
    public int logicalWidth(double scale) { return (int) Math.round(width / scale); }
    public int logicalHeight(double scale) { return (int) Math.round(height / scale); }
}
