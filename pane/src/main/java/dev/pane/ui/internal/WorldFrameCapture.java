package dev.pane.ui.internal;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;

/**
 * Reads the game frame back after the world is drawn and before any GUI, for screens that asked.
 * <p><b>Internal:</b> public only so PaneLib's mixins can reach it; not part of PaneLib's API.
 */
public final class WorldFrameCapture {
    private static final List<Consumer<BufferedImage>> PENDING = new ArrayList<>();
    private WorldFrameCapture() {}

    /** Delivers the next world frame to {@code receiver} on the render thread. */
    public static void request(Consumer<BufferedImage> receiver) { PENDING.add(receiver); }

    /** Called between the level and GUI passes with the main render target. */
    public static void beforeGui(RenderTarget target) {
        if (PENDING.isEmpty()) return;
        var receivers = List.copyOf(PENDING);
        PENDING.clear();
        Screenshot.takeScreenshot(target, frame -> {
            try (frame) {
                int w = frame.getWidth(), h = frame.getHeight();
                int[] argb = frame.getPixels();
                // The world target's alpha is not coverage; the frame is opaque.
                for (int i = 0; i < argb.length; i++) argb[i] |= 0xFF000000;
                var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0, 0, w, h, argb, 0, w);
                for (var receiver : receivers) receiver.accept(image);
            }
        });
    }
}
