package dev.pane.ui;

/** A rectangle reserved for Minecraft's own renderer, measured in physical backing pixels. */
public record NativeBounds(double x, double y, double width, double height) {
    /** Left edge in GUI pixels at {@code scale} layout units per GUI pixel. */
    public int logicalX(double scale) { return (int) Math.round(x / scale); }
    /** Top edge in GUI pixels at {@code scale} layout units per GUI pixel. */
    public int logicalY(double scale) { return (int) Math.round(y / scale); }
    /** Width in GUI pixels at {@code scale} layout units per GUI pixel. */
    public int logicalWidth(double scale) { return (int) Math.round(width / scale); }
    /** Height in GUI pixels at {@code scale} layout units per GUI pixel. */
    public int logicalHeight(double scale) { return (int) Math.round(height / scale); }
}
