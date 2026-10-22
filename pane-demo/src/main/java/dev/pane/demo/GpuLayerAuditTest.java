package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

/** GPU-composited layer acceptance in an isolated title client; no world or personal data. */
final class GpuLayerAuditTest {
    private static final UiTransform MOVED = new UiTransform(260, 40, 1.15F, 1.15F, 6, .55F);
    private UiScreen screen;
    private int tick, checks, paints;
    private long rasters, frames;
    private boolean failed, finished;

    void tick(Minecraft client) {
        if (finished) return;
        try {
            if (screen == null) {
                if (!(client.gui.screen() instanceof TitleScreen)) return;
                screen = new UiScreen(UiDocument.builder("GPU layer audit").scaling(UiScaling.fixed())
                    .page(new UiPage("main", "Main", "", 0xFF5B9BE3, ui -> {
                        var c = ui.canvas();
                        c.fill(0xFF101418, 0, 0, ui.width(), ui.height());
                        c.text("Retained page text stays still", 20, 20, 14, 0xFFE8EEF4, Font.BOLD);
                        String label = ui.stringValue("label", "GPU layer");
                        // The bounds include the card's shadow and a transparent margin.
                        ui.gpuLayer("card", new UiRect(46, 56, 208, 120), label,
                            ui.booleanValue("moved", false) ? MOVED : UiTransform.identity(), 600, UiEasing.EASE_OUT, g -> {
                                g.shadow(0x60000000, 10, 0, 3, 60, 70, 180, 90, 12);
                                g.roundRect(0xFF5B9BE3, 60, 70, 180, 90, 12);
                                g.text(label, 78, 100, 16, 0xFFFFFFFF, Font.BOLD);
                            });
                        ui.virtualList("rows", new UiRect(20, 200, 260, 120), 6, 36, 4, (row, item) ->
                            row.gpuLayer("row-" + item.index(), item.bounds(), 1, UiTransform.identity(), 200, UiEasing.EASE_OUT,
                                g -> g.roundRect(0xFF2A3440, item.bounds().x(), item.bounds().y(), item.bounds().width(), item.bounds().height(), 6)));
                    })).build());
                client.gui.setScreen(screen);
                return;
            }
            if (screen.paintCount() == 0) return;
            tick++;
            if (tick == 20) {
                rasters = screen.gpuLayerRasterCount();
                paints = screen.paintCount();
                check(rasters == 1, "layer content rasterized once; virtual-row layers draw in place");
                screen.setValue("moved", true);
            }
            if (tick == 22) {
                check(screen.paintCount() == paints + 1, "new target costs exactly one page paint");
                check(screen.gpuLayerRasterCount() == rasters, "retargeting does not re-rasterize content");
                frames = screen.performanceSnapshot().frameCount();
                capture(client, "gpu-layer-moving");
            }
            if (tick == 30) {
                check(screen.paintCount() == paints + 1, "animation frames repaint nothing on the CPU");
                check(screen.performanceSnapshot().frameCount() > frames, "frames kept presenting during the animation");
            }
            if (tick == 40) {
                check(screen.paintCount() == paints + 1, "settled layer stays cached");
                check(screen.gpuLayerRasterCount() == rasters, "settled layer kept its texture");
                capture(client, "gpu-layer-moved");
                screen.setValue("label", "Updated");
            }
            if (tick == 44) {
                check(screen.gpuLayerRasterCount() == rasters + 1, "new revision re-rasterizes once");
                screen.setValue("moved", false);
            }
            if (tick == 60) {
                check(screen.gpuLayerRasterCount() == rasters + 1, "returning home does not re-rasterize");
                capture(client, "gpu-layer-home");
                finished = true;
                System.out.println("[GpuLayerTest] complete " + (failed ? "FAIL" : "PASS") + " checks=" + checks);
                client.stop();
            }
        } catch (Throwable error) {
            finished = true;
            error.printStackTrace();
            System.out.println("[GpuLayerTest] complete FAIL " + error);
            client.stop();
        }
    }

    private void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> { });
    }

    private void check(boolean passed, String message) {
        checks++;
        failed |= !passed;
        System.out.println("[GpuLayerTest] " + message + ": " + (passed ? "PASS" : "FAIL"));
    }
}
