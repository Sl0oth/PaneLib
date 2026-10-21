package dev.pane.ui.internal;

import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState;

/** Internal extraction scope; state metadata survives deferred rendering without a side cache. */
public final class NativeOpacity {
    private static final ThreadLocal<Float> CURRENT = ThreadLocal.withInitial(() -> 1F);
    private NativeOpacity() {}
    public static float current() { return CURRENT.get(); }
    public static void with(float opacity, Runnable draw) {
        float previous = CURRENT.get();
        CURRENT.set(previous * Math.clamp(opacity, 0, 1));
        try { draw.run(); } finally { CURRENT.set(previous); }
    }
    public static float of(Object state) {
        if (state instanceof OversizedItemRenderState oversized) return of(oversized.guiItemRenderState());
        return state instanceof NativeOpacityAccess tagged ? tagged.panelibOpacity() : 1;
    }
    /** Both RGB and alpha must be attenuated for Minecraft's premultiplied GUI pipelines. */
    public static int tint(int color, float opacity) {
        if (opacity >= 1) return color;
        return Math.round((color >>> 24) * opacity) << 24
            | Math.round((color >>> 16 & 255) * opacity) << 16
            | Math.round((color >>> 8 & 255) * opacity) << 8
            | Math.round((color & 255) * opacity);
    }
}
