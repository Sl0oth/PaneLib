package dev.pane.ui.internal;

import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState;

/**
 * Internal extraction scope; state metadata survives deferred rendering without a side cache.
 * <p><b>Internal:</b> public only so PaneLib's mixins can reach it; not part of PaneLib's API.
 */
public final class NativeOpacity {
    private static final ThreadLocal<Float> CURRENT = ThreadLocal.withInitial(() -> 1F);
    private NativeOpacity() {}
    /** The opacity native draws on this thread are recorded with now. */
    public static float current() { return CURRENT.get(); }
    /** Runs {@code draw} with native draws recorded at the current opacity times {@code opacity}. */
    public static void with(float opacity, Runnable draw) {
        float previous = CURRENT.get();
        CURRENT.set(previous * Math.clamp(opacity, 0, 1));
        try { draw.run(); } finally { CURRENT.set(previous); }
    }
    /** The opacity a deferred Minecraft draw state was recorded with, or 1. */
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
