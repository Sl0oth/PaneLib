package dev.pane.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteOrder;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Retained, input-free in-game HUD. Attach once on the client thread; update values only when
 * their displayed representation changes. World changes release textures and clear world values.
 * No screen, mouse listener, or key listener is installed by this class.
 */
public final class UiHud implements AutoCloseable {
    private final int width, height;
    private final UiTheme theme;
    private final UiPainter painter;
    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, UiAnimationClock.State> animations = new HashMap<>();
    private final UiLayer opacityLayer = new UiLayer();
    private UiHudPlacement placement = UiHudPlacement.topLeft();
    private boolean visible = true, showOnScreens, dirty = true, animating, presentationAnimating, closed;
    private float motionScale, lastOpacity = -1;
    private Identifier id;
    private DynamicTexture texture;
    private BufferedImage image;
    private int[] pixels;
    private long frames, paints, uploadedPixels, allocations, lastPaint, lastRasterNanos, lastUploadNanos;
    private long totalRasterNanos, totalUploadNanos, maximumRasterNanos;
    private UiRect lastBounds = new UiRect(0, 0, 0, 0);

    public UiHud(int width, int height, UiTheme theme, UiPainter painter) {
        if (width < 1 || height < 1 || width > 1024 || height > 1024)
            throw new IllegalArgumentException("A HUD must be 1..1024 UI points on each axis");
        this.width = width; this.height = height;
        this.theme = theme == null ? UiTheme.dark() : theme;
        this.painter = Objects.requireNonNull(painter, "painter");
        motionScale = this.theme.motionScale();
    }

    public UiHud attach(Identifier id) {
        requireClientThread();
        if (closed || this.id != null) throw new IllegalStateException("HUD is closed or already attached");
        Registry.install();
        if (Registry.huds.containsKey(id)) throw new IllegalArgumentException("HUD id already attached: " + id);
        this.id = Objects.requireNonNull(id);
        Registry.huds.put(id, this);
        return this;
    }
    public void setValue(String key, Object value) {
        requireClientThread();
        if (closed) return;
        if (!Objects.equals(values.put(key, value), value)) dirty = true;
    }
    public void invalidate() { requireClientThread(); if (!closed) dirty = true; }
    public boolean visible() { return visible; }
    public void setVisible(boolean value) { requireClientThread(); visible = value; }
    public void setPlacement(UiHudPlacement placement) { requireClientThread(); this.placement = Objects.requireNonNull(placement); }
    public UiHudPlacement placement() { return placement; }
    public void setShowOnScreens(boolean value) { requireClientThread(); showOnScreens = value; }
    public void setMotionScale(float value) {
        requireClientThread();
        if (Float.isFinite(value)) { motionScale = Math.max(0, Math.min(2, value)); dirty = true; }
    }
    public Snapshot snapshot() {
        return new Snapshot(frames, paints, uploadedPixels, allocations, texture != null, animating || presentationAnimating,
            lastBounds, lastRasterNanos, lastUploadNanos, totalRasterNanos, totalUploadNanos, maximumRasterNanos);
    }
    public record Snapshot(long frames, long paints, long uploadedPixels, long allocations, boolean hasSurface,
                           boolean animating, UiRect bounds, long lastRasterNanos, long lastUploadNanos,
                           long totalRasterNanos, long totalUploadNanos, long maximumRasterNanos) { }

    private void extract(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (closed || client.level == null || client.player == null
                || (!showOnScreens && client.gui.screen() != null)) return;
        long now = System.nanoTime();
        frames++;
        var clock = new UiAnimationClock(animations, now, motionScale);
        float opacity = clock.value("\0hud:visible", 0, visible ? 1 : 0, 220, 0, UiEasing.EASE_OUT);
        if (opacity <= 0 && !visible) { releaseSurface(); presentationAnimating = false; return; }
        float quality = Math.min(2, Math.max(1, (float) client.getWindow().getWidth() / client.getWindow().getScreenWidth()));
        int pixelWidth = Math.max(1, Math.round(width * quality)), pixelHeight = Math.max(1, Math.round(height * quality));
        if (texture == null || image.getWidth() != pixelWidth || image.getHeight() != pixelHeight) {
            releaseSurface();
            texture = new DynamicTexture(() -> "pane-hud", pixelWidth, pixelHeight, true);
            image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
            pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            dirty = true; allocations++;
        }
        if (dirty || ((animating || opacity != lastOpacity) && now - lastPaint >= 16_000_000L)) paint(now, quality, opacity);
        float dpi = Math.max(1, (float) client.getWindow().getWidth() / client.getWindow().getScreenWidth());
        float pointsPerGui = (float) client.getWindow().getGuiScale() / dpi;
        UiRect target = placement.bounds(graphics.guiWidth(), graphics.guiHeight(), width, height, pointsPerGui);
        float x = Math.max(0, Math.min(graphics.guiWidth() - target.width(), clock.value("\0hud:x", target.x(), 320, UiEasing.SPRING)));
        float y = Math.max(0, Math.min(graphics.guiHeight() - target.height(), clock.value("\0hud:y", target.y(), 320, UiEasing.SPRING)));
        presentationAnimating = clock.active();
        lastBounds = new UiRect(x, y, target.width(), target.height());
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + (1 - opacity) * 6 / pointsPerGui);
        graphics.pose().scale(target.width() / width, target.height() / height);
        graphics.blit(texture.getTextureView(), sampler, 0, 0, width, height, 0, 1, 0, 1);
        graphics.pose().popMatrix();
    }

    private void paint(long now, float quality, float opacity) {
        long started = System.nanoTime();
        Arrays.fill(pixels, 0);
        var graphics = image.createGraphics();
        var clock = new UiAnimationClock(animations, now, motionScale);
        try {
            graphics.scale(quality, quality);
            UiCanvas canvas = new UiCanvas(graphics, image);
            canvas.withLayer(opacityLayer, opacity, new UiRect(0, 0, width, height), layer -> {
                var context = new UiContext(layer, theme, width, height, theme.accent(), new UiRect(0, 0, width, height),
                    null, null, 1, 1, false, (int) paints, values, new ArrayList<>(), new LinkedHashMap<>(),
                    new LinkedHashMap<>(), new HashMap<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                    new UiTextInputState(null, 0, 0), new ArrayList<>(), new ArrayList<>(), clock, 1, null);
                painter.paint(context);
            });
        } finally { graphics.dispose(); }
        animating = clock.active();
        lastRasterNanos = System.nanoTime() - started;
        long uploadStarted = System.nanoTime();
        var target = texture.getPixels().getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer();
        UiPixelTransfer.copy(pixels, target, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        texture.upload();
        lastUploadNanos = System.nanoTime() - uploadStarted;
        totalRasterNanos += lastRasterNanos; totalUploadNanos += lastUploadNanos;
        maximumRasterNanos = Math.max(maximumRasterNanos, lastRasterNanos);
        uploadedPixels += pixels.length; paints++; lastPaint = now; lastOpacity = opacity; dirty = false;
    }

    private void worldChanged() {
        releaseSurface(); values.clear(); animations.clear(); animating = presentationAnimating = false; dirty = true;
    }
    private void releaseSurface() {
        if (texture != null) texture.close();
        texture = null; image = null; pixels = null; opacityLayer.clear(); lastOpacity = -1;
    }
    @Override public void close() {
        requireClientThread();
        if (closed) return;
        closed = true;
        if (id != null) Registry.huds.remove(id, this);
        releaseSurface(); values.clear(); animations.clear();
    }
    private static void requireClientThread() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread() && !RenderSystem.isOnRenderThread()) throw new IllegalStateException("HUD updates belong on the Minecraft client thread");
    }

    private static final class Registry {
        static final Map<Identifier, UiHud> huds = new LinkedHashMap<>();
        static boolean installed;
        static Object world;
        static void install() {
            if (installed) return;
            installed = true;
            HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("panelib", "retained_huds"), (graphics, delta) -> {
                    for (UiHud hud : huds.values()) hud.extract(graphics);
                });
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (world == client.level) return;
                world = client.level;
                for (UiHud hud : huds.values()) hud.worldChanged();
            });
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
                for (UiHud hud : huds.values()) hud.worldChanged();
                huds.clear(); world = null;
            });
        }
    }
}
