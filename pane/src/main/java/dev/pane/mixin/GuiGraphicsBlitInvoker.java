package dev.pane.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The public texture blit is always opaque white; GPU layers need its colour argument for opacity. */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsBlitInvoker {
    @Invoker("innerBlit")
    void panelib$innerBlit(RenderPipeline pipeline, GpuTextureView texture, GpuSampler sampler, int x0, int y0, int x1, int y1,
                           float u0, float u1, float v0, float v1, int color);
}
