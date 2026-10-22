package dev.pane.ui;

import com.mojang.blaze3d.textures.GpuSampler;
import dev.pane.mixin.GuiGraphicsBlitInvoker;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Retained groups composited by the GPU. Each keeps its own texture, rasterized only when its
 * revision, size or quality changes; translation, scale, rotation and opacity animate every frame
 * without repainting the page surface.
 */
final class UiGpuLayers {
    record Request(String id, UiRect local, UiRect screen, Object revision, UiTransform target, int durationMillis,
                   UiEasing easing, UiRect clip, Consumer<UiCanvas> painter) { }

    /** Accepts requests painted directly on one screen canvas, under nothing but context translations. */
    static final class Collector {
        private final UiCanvas canvas;
        private final AffineTransform base;
        final List<Request> requests = new ArrayList<>();

        Collector(UiCanvas canvas) { this.canvas = canvas; base = canvas.transform(); }

        boolean accepts(UiCanvas candidate, float offsetX, float offsetY) {
            if (candidate != canvas) return false;
            var expected = new AffineTransform(base);
            expected.translate(offsetX, offsetY);
            double[] a = new double[6], b = new double[6];
            expected.getMatrix(a);
            candidate.transform().getMatrix(b);
            for (int i = 0; i < 6; i++) if (Math.abs(a[i] - b[i]) > 1e-4) return false;
            return true;
        }
    }

    private static final class Layer {
        UiRetainedTexture surface;
        Object revision;
        UiRect local, screen, clip;
        float quality;
        UiTransform from, to;
        UiEasing easing = UiEasing.EASE_OUT;
        long start, end;

        UiTransform at(long now) {
            if (now >= end || end <= start) return to;
            float t = easing.apply((float) (now - start) / (end - start));
            return new UiTransform(lerp(from.x(), to.x(), t), lerp(from.y(), to.y(), t),
                lerp(from.scaleX(), to.scaleX(), t), lerp(from.scaleY(), to.scaleY(), t),
                lerp(from.rotationDegrees(), to.rotationDegrees(), t), lerp(from.opacity(), to.opacity(), t));
        }
    }

    private final Map<String, Layer> layers = new LinkedHashMap<>();
    long rasterCount, allocations;

    boolean isEmpty() { return layers.isEmpty(); }

    boolean animating(long now) {
        for (Layer layer : layers.values()) if (now < layer.end) return true;
        return false;
    }

    /** Adopts one paint's requests: rasterizes changed content, retargets motion, drops absent ids. */
    void reconcile(List<Request> requests, float quality, float motionScale, long now) {
        var seen = new HashSet<String>();
        for (Request request : requests) {
            seen.add(request.id());
            Layer layer = layers.get(request.id());
            if (layer == null) {
                layer = new Layer();
                layer.from = layer.to = request.target();
                layers.put(request.id(), layer);
            } else if (!request.target().equals(layer.to)) {
                layer.from = layer.at(now);
                layer.to = request.target();
                layer.start = now;
                layer.end = now + Math.round(Math.max(0, request.durationMillis()) * Math.max(0, motionScale) * 1_000_000D);
                layer.easing = request.easing() == null ? UiEasing.EASE_OUT : request.easing();
            }
            layer.screen = request.screen();
            layer.clip = request.clip();
            int width = Math.max(1, (int) Math.ceil(request.local().width() * quality));
            int height = Math.max(1, (int) Math.ceil(request.local().height() * quality));
            if (layer.surface != null && layer.surface.matches(width, height) && layer.quality == quality
                    && request.local().equals(layer.local) && Objects.equals(request.revision(), layer.revision)) continue;
            if (layer.surface == null || !layer.surface.matches(width, height)) {
                if (layer.surface != null) layer.surface.close();
                layer.surface = new UiRetainedTexture("pane-gpu-layer-" + request.id(), width, height);
                allocations++;
            } else layer.surface.clear();
            Graphics2D graphics = layer.surface.image.createGraphics();
            try {
                graphics.scale(quality, quality);
                graphics.translate(-request.local().x(), -request.local().y());
                request.painter().accept(new UiCanvas(graphics, layer.surface.image));
            } finally { graphics.dispose(); }
            layer.surface.upload();
            layer.local = request.local();
            layer.revision = request.revision();
            layer.quality = quality;
            rasterCount++;
        }
        var iterator = layers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (seen.contains(entry.getKey())) continue;
            if (entry.getValue().surface != null) entry.getValue().surface.close();
            iterator.remove();
        }
    }

    /** Draws every layer at its current animated transform, pivoting on the centre of its bounds. */
    void render(GuiGraphicsExtractor graphics, GpuSampler sampler, float layoutScale, long now) {
        for (Layer layer : layers.values()) {
            UiTransform transform = layer.at(now);
            if (layer.surface == null || transform.opacity() <= 0) continue;
            if (layer.clip != null) {
                int x0 = (int) Math.floor(layer.clip.x() / layoutScale), y0 = (int) Math.floor(layer.clip.y() / layoutScale);
                int x1 = (int) Math.ceil(layer.clip.right() / layoutScale), y1 = (int) Math.ceil(layer.clip.bottom() / layoutScale);
                if (x1 <= x0 || y1 <= y0) continue;
                graphics.enableScissor(x0, y0, x1, y1);
            }
            var pose = graphics.pose();
            pose.pushMatrix();
            try {
                float pivotX = layer.screen.centerX(), pivotY = layer.screen.centerY();
                pose.scale(1 / layoutScale, 1 / layoutScale);
                pose.translate(transform.x() + pivotX, transform.y() + pivotY);
                pose.rotate((float) Math.toRadians(transform.rotationDegrees()));
                pose.scale(transform.scaleX(), transform.scaleY());
                pose.translate(layer.screen.x() - pivotX, layer.screen.y() - pivotY);
                pose.scale(1 / layer.quality, 1 / layer.quality);
                int alpha = Math.round(transform.opacity() * 255);
                ((GuiGraphicsBlitInvoker) graphics).panelib$innerBlit(RenderPipelines.GUI_TEXTURED,
                    layer.surface.texture.getTextureView(), sampler, 0, 0, layer.surface.image.getWidth(),
                    layer.surface.image.getHeight(), 0, 1, 0, 1, alpha << 24 | 0xFFFFFF);
            } finally {
                pose.popMatrix();
                if (layer.clip != null) graphics.disableScissor();
            }
        }
    }

    void close() {
        for (Layer layer : layers.values()) if (layer.surface != null) layer.surface.close();
        layers.clear();
    }

    private static float lerp(float from, float to, float t) { return from + (to - from) * t; }
}
