package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteOrder;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** A CPU ARGB raster mirrored into a GPU texture of exactly the same size. */
final class UiRetainedTexture {
    final DynamicTexture texture;
    final BufferedImage image;
    final int[] pixels;

    UiRetainedTexture(String label, int width, int height) {
        texture = new DynamicTexture(() -> label, width, height, true);
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    boolean matches(int width, int height) { return image.getWidth() == width && image.getHeight() == height; }
    void clear() {
        int width = image.getWidth();
        if (pixels.length < 1 << 17) { Arrays.fill(pixels, 0); return; }
        java.util.stream.IntStream.range(0, image.getHeight()).parallel().forEach(row -> Arrays.fill(pixels, row * width, row * width + width, 0));
    }
    void upload() {
        var target = texture.getPixels().getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer();
        UiPixelTransfer.copy(pixels, target, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        texture.upload();
    }
    void close() { texture.close(); }
}
