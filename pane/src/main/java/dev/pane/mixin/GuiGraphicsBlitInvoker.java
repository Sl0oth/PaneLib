package dev.pane.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The public texture blit is always opaque white; GPU layers need its colour argument for opacity.
 * <p><b>Internal:</b> public only so Minecraft's mixin loader can apply it; not part of PaneLib's API.
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsBlitInvoker {
    /** Minecraft's own blit with an explicit colour; internal. */
    @Invoker("innerBlit")
    void panelib$innerBlit(RenderPipeline pipeline, GpuTextureView texture, GpuSampler sampler, int x0, int y0, int x1, int y1,
                           float u0, float u1, float v0, float v1, int color);
}
