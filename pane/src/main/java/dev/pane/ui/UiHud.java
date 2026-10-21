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
    private long minecraftTextureRevision = UiCanvas.minecraftTextureRevision();
    private final int width, height;
    private final UiTheme theme;
    private final UiPainter painter;
    private final NativeOverlayRenderer nativeOverlay;
    private final Map<String, NativeBounds> nativeSlots = new LinkedHashMap<>();
    private final UiHudNativeCache nativePlacement = new UiHudNativeCache();
    private final NativeTextRenderer nativeText = new NativeTextRenderer();
    private int nativeTextCount;
    private int nativeItemCount, nativeEntityCount;
    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, UiAnimationClock.State> animations = new HashMap<>();
    private final UiLayer opacityLayer = new UiLayer();
    private Set<Identifier> replacedVanillaElements = Set.of();
    private final UiHudPlacementResolver placement = new UiHudPlacementResolver();
    private UiRenderSettings renderSettings = UiRenderSettings.hud(), measuredSettings;
    private UiRasterQuality raster;
    private boolean enabled = true, visible = true, showOnScreens, dirty = true, animating, presentationAnimating, closed;
    private float motionScale, lastOpacity = -1;
    private Identifier id;
    private DynamicTexture texture;
    private BufferedImage image;
    private int[] pixels;
    private long frames, paints, uploadedPixels, allocations, lastPaint, lastRasterNanos, lastUploadNanos;
    private long totalRasterNanos, totalUploadNanos, maximumRasterNanos;
    private UiRect lastBounds = new UiRect(0, 0, 0, 0);

    public UiHud(int width, int height, UiTheme theme, UiPainter painter) {
        this(width, height, theme, painter, NativeOverlayRenderer.NONE);
    }

    /** Native content uses the same slot IDs/helpers as UiScreen and follows this HUD's lifecycle. */
    public UiHud(int width, int height, UiTheme theme, UiPainter painter, NativeOverlayRenderer nativeOverlay) {
        this.nativeOverlay = Objects.requireNonNull(nativeOverlay, "nativeOverlay");
        if (width < 1 || height < 1 || width > 1024 || height > 1024)
            throw new IllegalArgumentException("A HUD must be 1..1024 UI points on each axis");
        this.width = width; this.height = height;
        raster = new UiRasterQuality(width,height,0,0,1,0);
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
    public boolean enabled() { return enabled && !closed; }
    /** Immediately deactivate rendering and replacement claims. Values and visible preference survive. */
    public void setEnabled(boolean value) {
        requireClientThread();
        if (closed || enabled == value) return;
        enabled = value;
        if (!value) { releaseSurface(); animations.clear(); animating = presentationAnimating = false; }
        dirty = true;
    }
    /**
     * Replace these Fabric HUD elements only while this attached HUD is presented. Calling again
     * replaces the declaration; no arguments releases every claim. No vanilla changes by default.
     * Hide retains claims through its exit fade; disable/close restores immediately. Other active
     * PaneLib owners may still claim the same element. Original callbacks are delegated to, never
     * reinstalled over another mod's later wrapper. Call only from client lifecycle code.
     */
    public UiHud replaceVanilla(Identifier... elements) {
        requireClientThread();
        if (closed) throw new IllegalStateException("HUD is closed");
        Objects.requireNonNull(elements, "elements");
        Set<Identifier> next = new LinkedHashSet<>();
        for (Identifier element : elements) next.add(Objects.requireNonNull(element, "element"));
        Registry.replacements.replace(this, next, this::replacementActive);
        replacedVanillaElements = Collections.unmodifiableSet(next);
        return this;
    }
    public Set<Identifier> replacedVanillaElements() { return replacedVanillaElements; }

    private boolean eligible(Minecraft client) {
        return !closed && enabled && id != null && client.level != null && client.player != null
            && !client.gui.hud.isHidden() && (showOnScreens || client.gui.screen() == null);
    }
    private boolean replacementActive() {
        if (!eligible(Minecraft.getInstance())) return false;
        if (visible) return true;
        // The vanilla callbacks may run before our renderer. Evaluate the same visibility track
        // here so restoration does not depend on last frame's texture or callback ordering.
        return texture != null && visibilityOpacity(System.nanoTime()) > 0;
    }
    private float visibilityOpacity(long now) {
        return new UiAnimationClock(animations, now, motionScale)
            .value("\0hud:visible", 0, visible ? 1 : 0, 220, 0, UiEasing.EASE_OUT);
    }
    /** Set fixed placement and remove any responsive policy. Position-only changes reuse pixels. */
    public void setPlacement(UiHudPlacement value) { requireClientThread(); placement.setPlacement(value); }
    /** Fixed or most recently resolved placement; before first policy resolution, the prior value. */
    public UiHudPlacement placement() { return placement.placement(); }
    /**
     * Install/refresh responsive placement. Resolves on the next visible frame, then only when
     * logical viewport dimensions change. Reinstall after changing captured settings. Position-only
     * changes reuse pixels; size changes update resolution within budget. setPlacement restores fixed placement.
     */
    public void setPlacementPolicy(UiHudPlacementPolicy policy) { requireClientThread(); placement.setPolicy(policy); }
    /** Per-widget raster ceilings. Resolution follows effective displayed scale within this budget. */
    public void setRenderSettings(UiRenderSettings settings) { requireClientThread(); renderSettings=Objects.requireNonNull(settings); }
    public UiRenderSettings renderSettings() { return renderSettings; }
    /** Latest displayed target and retained raster dimensions; pixel dimensions are zero without a surface. */
    public UiRasterQuality rasterQuality() { return raster; }
    /** Bytes in one retained RGBA raster; CPU/GPU copies and layer scratch buffers are additional. */
    public long rasterBytes() { return (long)raster.pixelWidth()*raster.pixelHeight()*4; }
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

    /** Native submissions in the latest visible frame; zero while suppressed or closed. */
    public int nativeItemCount() { return nativeItemCount; }
    public int nativeEntityCount() { return nativeEntityCount; }
    /** Native slot-coordinate rebuilds; unchanged presented frames reuse the mapping. */
    public long nativePlacementCount() { return nativePlacement.builds(); }
    /** Native text submissions in the latest visible frame, not glyph count. */
    public int nativeTextCount() { return nativeTextCount; }
    /** Cumulative native line measurements; stable labels reuse cached layout. */
    public long nativeTextLayoutCount() { return nativeText.layouts(); }
    private void clearNativeCounts() { nativeItemCount = nativeEntityCount = nativeTextCount = 0; }

    private void extract(GuiGraphicsExtractor graphics) {
        clearNativeCounts();
        Minecraft client = Minecraft.getInstance();
        if (!eligible(client)) return;
        long revision = UiCanvas.minecraftTextureRevision();
        if (minecraftTextureRevision != revision) {
            minecraftTextureRevision = revision;
            dirty = true;
        }
        long now = System.nanoTime();
        frames++;
        var clock = new UiAnimationClock(animations, now, motionScale);
        float opacity = clock.value("\0hud:visible", 0, visible ? 1 : 0, 220, 0, UiEasing.EASE_OUT);
        if (opacity <= 0 && !visible) { releaseSurface(); presentationAnimating = false; return; }
        float dpi = Math.max(1, (float) client.getWindow().getWidth() / client.getWindow().getScreenWidth());
        float pointsPerGui = (float) client.getWindow().getGuiScale() / dpi;
        UiHudPlacement resolved = placement.resolve(graphics.guiWidth() * pointsPerGui, graphics.guiHeight() * pointsPerGui);
        UiRect target = resolved.bounds(graphics.guiWidth(), graphics.guiHeight(), width, height, pointsPerGui);
        float desired = (float)Math.max(target.width()*client.getWindow().getGuiScale()/width,
            target.height()*client.getWindow().getGuiScale()/height);
        UiRasterQuality nextRaster=raster;
        if(texture==null||desired!=raster.desiredScale()||!renderSettings.equals(measuredSettings))
            nextRaster=UiHudRaster.resolve(width,height,desired,renderSettings);
        int pixelWidth=nextRaster.pixelWidth(),pixelHeight=nextRaster.pixelHeight();
        if (texture == null || image.getWidth() != pixelWidth || image.getHeight() != pixelHeight) {
            releaseSurface();
            texture = new DynamicTexture(() -> "pane-hud", pixelWidth, pixelHeight, true);
            image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
            pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            dirty = true; allocations++;
        }
        raster=nextRaster;measuredSettings=renderSettings;
        if (dirty || ((animating || opacity != lastOpacity) && now - lastPaint >= 16_000_000L)) paint(now, opacity);
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
        if (nativeOverlay != NativeOverlayRenderer.NONE && !nativeSlots.isEmpty()) {
            double guiScale = client.getWindow().getGuiScale();
            UiRect presented = new UiRect(x, y + (1 - opacity) * 6 / pointsPerGui, target.width(), target.height());
            Map<String, NativeBounds> slots = nativePlacement.resolve(nativeSlots, presented, width, height, guiScale);
            var context = new NativeOverlayContext(graphics, "hud", slots, guiScale,
                (int) client.mouseHandler.getScaledXPos(client.getWindow()),
                (int) client.mouseHandler.getScaledYPos(client.getWindow()), client.gui.screen() != null, nativeText);
            graphics.nextStratum();
            graphics.enableScissor((int) Math.floor(presented.x()), (int) Math.floor(presented.y()),
                (int) Math.ceil(presented.right()), (int) Math.ceil(presented.bottom()));
            try {
                dev.pane.ui.internal.NativeOpacity.with(lastOpacity, () -> nativeOverlay.render(context));
            } finally { graphics.disableScissor(); }
            nativeItemCount = context.renderedItemCount();
            nativeEntityCount = context.renderedEntityCount();
            nativeTextCount = context.renderedTextCount();
        }
    }

    private void paint(long now, float opacity) {
        long started = System.nanoTime();
        Arrays.fill(pixels, 0);
        var graphics = image.createGraphics();
        var clock = new UiAnimationClock(animations, now, motionScale);
        var nextSlots = new LinkedHashMap<String, NativeBounds>();
        try {
            graphics.scale(image.getWidth()/(double)width, image.getHeight()/(double)height);
            UiCanvas canvas = new UiCanvas(graphics, image);
            canvas.withLayer(opacityLayer, opacity, new UiRect(0, 0, width, height), layer -> {
                var context = new UiContext(layer, theme, width, height, theme.accent(), new UiRect(0, 0, width, height),
                    null, null, 1, 1, false, (int) paints, values, new ArrayList<>(), nextSlots,
                    new LinkedHashMap<>(), new HashMap<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                    new UiTextInputState(null, 0, 0), new ArrayList<>(), new ArrayList<>(), clock, 1, null);
                painter.paint(context);
            });
        } finally { graphics.dispose(); }
        if(!nativeSlots.equals(nextSlots)) {
            nativeSlots.clear(); nativeSlots.putAll(nextSlots);nativePlacement.clear();
        }
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
        texture = null; image = null; pixels = null; nativeSlots.clear(); nativePlacement.clear(); nativeText.clear(); clearNativeCounts(); opacityLayer.clear(); lastOpacity = -1;
        if(raster.pixelWidth()!=0)raster=new UiRasterQuality(width,height,0,0,raster.desiredScale(),0);
    }
    @Override public void close() {
        requireClientThread();
        if (closed) return;
        closed = true;
        Registry.replacements.remove(this);
        replacedVanillaElements = Set.of();
        if (id != null) Registry.huds.remove(id, this);
        releaseSurface(); values.clear(); animations.clear();
    }
    private static void requireClientThread() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread() && !RenderSystem.isOnRenderThread()) throw new IllegalStateException("HUD updates belong on the Minecraft client thread");
    }

    private static final class Registry {
        static final Map<Identifier, UiHud> huds = new LinkedHashMap<>();
        static final UiHudReplacements replacements = new UiHudReplacements(HudElementRegistry::replaceElement);
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
                for (UiHud hud : huds.values()) {
                    if (!hud.eligible(client)) hud.clearNativeCounts();
                }
                if (world == client.level) return;
                world = client.level;
                for (UiHud hud : huds.values()) hud.worldChanged();
            });
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
                for (UiHud hud : huds.values()) hud.worldChanged();
                huds.clear(); replacements.clear(); world = null;
            });
        }
    }
}
