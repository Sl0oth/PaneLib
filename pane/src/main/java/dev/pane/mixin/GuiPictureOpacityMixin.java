package dev.pane.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.pane.ui.internal.NativeOpacity;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PictureInPictureRenderer.class)
abstract class GuiPictureOpacityMixin {
    @ModifyArg(method = "blitTexture", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/state/gui/BlitRenderState;<init>(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V"), index = 11)
    private int panelib$tint(int color, @Local(argsOnly = true) PictureInPictureRenderState state) {
        return NativeOpacity.tint(color, NativeOpacity.of(state));
    }
}
