package dev.pane.mixin;

import dev.pane.ui.internal.NativeOpacity;
import dev.pane.ui.internal.NativeOpacityAccess;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GuiItemRenderState.class, GuiEntityRenderState.class})
abstract class NativeDrawOpacityMixin implements NativeOpacityAccess {
    @Unique private float panelib$opacity;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void panelib$captureOpacity(CallbackInfo ci) { panelib$opacity = NativeOpacity.current(); }
    @Override public float panelibOpacity() { return panelib$opacity; }
}
