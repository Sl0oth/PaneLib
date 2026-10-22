package dev.pane.demo;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.pane.ui.UiScreen;
import java.util.function.Consumer;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Dev-only readback gate. Reflection keeps GPU handles out of PaneLib's public consumer API. */
final class SurfaceGpuCheck {
    private SurfaceGpuCheck() { }

    static void verify(UiScreen screen, Consumer<String> result) {
        try {
            var field = UiScreen.class.getDeclaredField("surface");
            field.setAccessible(true);
            Object retained = field.get(screen);
            var texture = retained.getClass().getDeclaredField("texture");
            texture.setAccessible(true);
            var surface = (DynamicTexture) texture.get(retained);
            var source = surface.getPixels().getPixelBytes().duplicate();
            byte[] expected = new byte[source.remaining()];
            source.get(expected);
            var buffer = RenderSystem.getDevice().createBuffer(() -> "pane-test-readback",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, expected.length);
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(surface.getTexture(), buffer, 0, () -> {
                try (buffer; var mapped = buffer.map(true, false)) {
                    var actual = mapped.data();
                    int differences = 0;
                    for (int i = 0; i < expected.length; i++) if (expected[i] != actual.get(i)) differences++;
                    result.accept("GPU surface bytes match native backing: " + (differences == 0 ? "PASS" : "FAIL")
                        + " bytes=" + expected.length + " differences=" + differences);
                } catch (RuntimeException error) {
                    result.accept("GPU surface readback: FAIL " + error.getClass().getSimpleName());
                }
            }, 0);
        } catch (ReflectiveOperationException | RuntimeException error) {
            result.accept("GPU surface readback: FAIL " + error.getClass().getSimpleName());
        }
    }
}
