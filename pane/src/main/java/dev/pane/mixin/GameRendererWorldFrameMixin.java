package dev.pane.mixin;

import dev.pane.ui.internal.WorldFrameCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererWorldFrameMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void panelib$captureWorld(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo info) {
        WorldFrameCapture.beforeGui(((GameRenderer) (Object) this).mainRenderTarget());
    }
}
