package dev.pane.ui;

/** Last painted raster dimensions and physical-resolution target; no GPU timing is implied. */
public record UiRasterQuality(int layoutWidth, int layoutHeight, int pixelWidth, int pixelHeight,
                              float desiredScale, float actualScale) {
    /** True when a configured raster ceiling forces the cached texture to be enlarged on screen. */
    public boolean upscaled() { return pixelWidth > 0 && actualScale + .01F < desiredScale; }
}
