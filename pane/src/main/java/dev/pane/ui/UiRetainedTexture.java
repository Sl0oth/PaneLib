package dev.pane.ui;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * A CPU ARGB raster mirrored into a GPU texture of exactly the same size. Pixels go to the GPU in
 * bands through one small buffer shared by every surface, so a surface holds its raster and its
 * texture and no third copy in between.
 */
final class UiRetainedTexture {
    /** The most pixels one band carries: four megabytes of shared upload buffer. */
    static final int BAND_PIXELS = 1 << 20;
    /** Render thread only. The GPU backends copy a band before the upload call returns. */
    private static ByteBuffer band;

    final GpuTexture texture;
    private final GpuTextureView view;
    final BufferedImage image;
    final int[] pixels;

    UiRetainedTexture(String label, int width, int height) {
        var device = RenderSystem.getDevice();
        texture = device.createTexture(() -> label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
            GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        view = device.createTextureView(texture);
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    GpuTextureView view() { return view; }

    boolean matches(int width, int height) { return image.getWidth() == width && image.getHeight() == height; }
    void clear() {
        int width = image.getWidth();
        if (pixels.length < 1 << 17) { Arrays.fill(pixels, 0); return; }
        UiParallel.forEach(image.getHeight(), row -> Arrays.fill(pixels, row * width, row * width + width, 0));
    }

    /** Sends the whole raster to the GPU; returns the pixels uploaded. */
    int upload() { return upload(0, 0, image.getWidth(), image.getHeight()); }

    /** Sends one rectangle of the raster, clamped to the surface, to the GPU; returns the pixels uploaded. */
    int upload(int x, int y, int width, int height) {
        int left = Math.max(0, x), top = Math.max(0, y);
        int right = Math.min(image.getWidth(), x + width), bottom = Math.min(image.getHeight(), y + height);
        if (right <= left || bottom <= top) return 0;
        int columns = right - left, rows = rowsPerBand(columns);
        if (band == null || band.capacity() < columns * Math.min(rows, bottom - top) * 4)
            band = ByteBuffer.allocateDirect(Math.max(BAND_PIXELS, columns) * 4).order(ByteOrder.nativeOrder());
        var packed = band.asIntBuffer();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        for (int from = top; from < bottom; from += rows) {
            int count = Math.min(rows, bottom - from);
            UiPixelTransfer.pack(pixels, image.getWidth(), left, from, columns, count, packed);
            encoder.writeToTexture(texture, band.slice(0, columns * count * 4), 0, 0, left, from, columns, count);
        }
        return columns * (bottom - top);
    }

    /** Rows of {@code columns} pixels that fit one band; at least one. */
    static int rowsPerBand(int columns) { return Math.max(1, BAND_PIXELS / Math.max(1, columns)); }

    void close() { view.close(); texture.close(); }
}
