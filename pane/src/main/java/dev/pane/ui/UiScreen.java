package dev.pane.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Lightweight retained native screen. Static UI is rasterized once and cached on the GPU.
 * It repaints only on resize, input/state invalidation, or during a finite animation.
 * <p>
 * <b>Coordinate spaces.</b> Three are in play:
 * <ul>
 *   <li><i>GUI space</i> - what Minecraft hands us in {@link #width}/{@link #height} and mouse events.</li>
 *   <li><i>Layout space</i> - what painters draw in. One layout unit is one operating-system point
 *       divided by {@link UiTheme#density()}, so a screen keeps the same physical size on a HiDPI
 *       display as on a standard one, independent of Minecraft's GUI scale setting.</li>
 *   <li><i>Surface space</i> - the rasterized texture, layout space supersampled up to the
 *       physical framebuffer resolution and capped so a fullscreen surface stays bounded.</li>
 * </ul>
 */
public class UiScreen extends Screen {
    private static final long FRAME_NANOS = 16_000_000L;
    /** Upper bound on surface pixels so a fullscreen UI cannot allocate an unbounded texture. */
    private static final double MAX_SURFACE_WIDTH = 2560;
    private static final double MAX_SURFACE_HEIGHT = 1440;

    private final UiDocument document;
    private final NativeOverlayRenderer nativeOverlay;
    private final UiEventListener eventListener;
    private final Map<String, Object> state = new HashMap<>();
    private final Map<String, NativeBounds> nativeBounds = new LinkedHashMap<>();
    private final Map<String, NativeBounds> nativeBoundsView = Collections.unmodifiableMap(nativeBounds);
    private List<UiHitRegion> hitRegions = List.of();

    private DynamicTexture surface;
    private GpuSampler surfaceSampler;
    private BufferedImage surfaceImage;
    private int[] surfacePixels;
    private int surfaceWidth;
    private int surfaceHeight;

    private String pageId;
    private String hoveredId;
    private String pressedId;
    private UiHitRegion draggingSlider;
    private boolean dirty = true;
    private boolean hoverDirty = true;
    private long animationStart;
    private long animationEnd;
    private long lastPaint;
    private int paintCount;
    private int mouseX;
    private int mouseY;
    private float layoutScale = 1;

    public UiScreen(UiDocument document) {
        this(document, NativeOverlayRenderer.NONE, UiEventListener.NONE);
    }

    public UiScreen(UiDocument document, NativeOverlayRenderer nativeOverlay,
                    UiEventListener eventListener) {
        super(Component.literal(document.title()));
        this.document = Objects.requireNonNull(document, "document");
        this.nativeOverlay = nativeOverlay == null ? NativeOverlayRenderer.NONE : nativeOverlay;
        this.eventListener = eventListener == null ? UiEventListener.NONE : eventListener;
        pageId = document.initialPageId();
    }

    public UiDocument document() { return document; }
    public String pageId() { return pageId; }

    /** How many times the surface has been rasterized. A diagnostic, not a frame count. */
    public int paintCount() { return paintCount; }

    public Object value(String key) { return state.get(key); }

    public boolean booleanValue(String key, boolean fallback) {
        return state.get(key) instanceof Boolean bool ? bool : fallback;
    }

    public double numberValue(String key, double fallback) {
        return state.get(key) instanceof Number number ? number.doubleValue() : fallback;
    }

    public void setValue(String key, Object value) {
        state.put(key, value);
        invalidate();
    }

    /**
     * GUI-space to layout-space factor from the last paint. Multiply a Minecraft mouse
     * coordinate by this to get the layout coordinate a painter would have drawn at.
     */
    public float layoutScale() { return layoutScale; }

    /**
     * Layout-space rectangle claimed by the control with this id during the last paint.
     * Useful for anchoring tooltips, popovers or context menus to a control.
     */
    public Optional<UiRect> regionBounds(String id) {
        return region(id).map(r -> new UiRect(r.x(), r.y(), r.width(), r.height()));
    }

    /**
     * Layout-space value track of the continuous control with this id, from the last paint.
     * For a slider this is the visible rail, which is narrower than its grab rectangle.
     */
    public Optional<UiRect> trackBounds(String id) {
        return region(id).map(r -> new UiRect(r.trackX(), r.y(), r.trackWidth(), r.height()));
    }

    private Optional<UiHitRegion> region(String id) {
        if (id == null) return Optional.empty();
        for (UiHitRegion candidate : hitRegions) {
            if (candidate.id().equals(id)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public void showPage(String id) {
        if (id == null || id.equals(pageId) || document.page(id) == null) return;
        pageId = id;
        hoveredId = null;
        pressedId = null;
        draggingSlider = null;
        nativeBounds.clear();
        hitRegions = List.of();
        hoverDirty = true;
        startAnimation(210);
        eventListener.onEvent("page", pageId, "");
    }

    /** Marks the cached surface stale. The next rendered frame repaints it exactly once. */
    public void invalidate() { dirty = true; }

    @Override
    protected void init() {
        dirty = true;
        hoverDirty = true;
        startAnimation(180);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        closeSurface();
        dirty = true;
        hoverDirty = true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        // The retained surface owns the background.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long now = System.nanoTime();
        if (animationEnd > 0) {
            if (now < animationEnd) {
                if (now - lastPaint >= FRAME_NANOS) dirty = true;
            } else {
                animationEnd = 0;
                dirty = true;
            }
        }
        if (dirty) rebuildSurface(now);
        if (surface == null || surfaceSampler == null) return;
        graphics.blit(surface.getTextureView(), surfaceSampler, 0, 0, width, height, 0, 1, 0, 1);
        if (nativeOverlay == NativeOverlayRenderer.NONE) return;
        graphics.nextStratum();
        nativeOverlay.render(new NativeOverlayContext(
            graphics, pageId, nativeBoundsView, guiScale(), mouseX, mouseY
        ));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.mouseX = (int) mouseX;
        this.mouseY = (int) mouseY;
        updateHover(mouseX * layoutScale, mouseY * layoutScale, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        UiHitRegion region = regionAt(event.x() * layoutScale, event.y() * layoutScale);
        if (region == null) return false;
        pressedId = region.id();
        if (region.action().startsWith("slider:")) {
            draggingSlider = region;
            updateSlider(region, event.x() * layoutScale);
        }
        startAnimation(100);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        boolean wasInteracting = pressedId != null || draggingSlider != null;
        UiHitRegion region = regionAt(event.x() * layoutScale, event.y() * layoutScale);
        String action = region != null && region.id().equals(pressedId) ? region.action() : null;
        pressedId = null;
        draggingSlider = null;
        long animationBefore = animationEnd;
        if (action != null) runAction(action);
        // Do not cut short a longer transition the action just started, such as a page change.
        if (wasInteracting && animationEnd == animationBefore) startAnimation(150);
        return wasInteracting;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingSlider == null) return false;
        this.mouseX = (int) event.x();
        this.mouseY = (int) event.y();
        updateSlider(draggingSlider, event.x() * layoutScale);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) return true;
        if (!document.pageShortcuts() || event.modifiers() != 0) return false;
        int index = event.key() - GLFW.GLFW_KEY_1;
        if (index < 0 || index > 8 || index >= document.pages().size()) return false;
        showPage(document.pages().get(index).id());
        return true;
    }

    private void runAction(String action) {
        hoverDirty = true;
        if (action.startsWith("page:")) {
            showPage(action.substring("page:".length()));
            return;
        }
        if (action.startsWith("toggle:")) {
            String key = action.substring("toggle:".length());
            boolean value = !(state.get(key) instanceof Boolean bool && bool);
            state.put(key, value);
            dirty = true;
            eventListener.onEvent("value", key, Boolean.toString(value));
            return;
        }
        if (!action.startsWith("slider:")) eventListener.onEvent("action", action, "");
    }

    private void updateSlider(UiHitRegion region, double mouseX) {
        String key = region.action().substring("slider:".length());
        double value = region.fractionAt(mouseX);
        Object previous = state.put(key, value);
        if (previous instanceof Number number && number.doubleValue() == value) return;
        eventListener.onEvent("value", key, Double.toString(value));
        dirty = true;
    }

    private void updateHover(double layoutX, double layoutY, boolean animate) {
        UiHitRegion region = regionAt(layoutX, layoutY);
        String next = region == null ? null : region.id();
        if (Objects.equals(next, hoveredId)) return;
        hoveredId = next;
        if (animate) startAnimation(120); else dirty = true;
    }

    private UiHitRegion regionAt(double x, double y) {
        for (int i = hitRegions.size() - 1; i >= 0; i--) {
            UiHitRegion region = hitRegions.get(i);
            if (region.contains(x, y)) return region;
        }
        return null;
    }

    private void startAnimation(int milliseconds) {
        long now = System.nanoTime();
        float scale = document.theme().motionScale();
        dirty = true;
        if (scale <= 0 || milliseconds <= 0) {
            animationEnd = 0;
            return;
        }
        animationStart = now;
        animationEnd = now + (long) (milliseconds * scale * 1_000_000L);
    }

    private float motion(long now) {
        if (animationEnd == 0 || animationEnd <= animationStart) return 1;
        float linear = Math.max(0, Math.min(1, (float) (now - animationStart) / (animationEnd - animationStart)));
        return 1 - (float) Math.pow(1 - linear, 3);
    }

    private void rebuildSurface(long now) {
        dirty = false;
        lastPaint = now;
        UiPage page = document.page(pageId);
        if (width <= 0 || height <= 0 || page == null) return;

        float scale = computeLayoutScale();
        layoutScale = scale;
        int layoutWidth = Math.max(1, Math.round(width * scale));
        int layoutHeight = Math.max(1, Math.round(height * scale));
        float qualityScale = computeQualityScale(layoutWidth, layoutHeight);
        int pixelWidth = Math.max(1, Math.round(layoutWidth * qualityScale));
        int pixelHeight = Math.max(1, Math.round(layoutHeight * qualityScale));
        if (!ensureSurface(pixelWidth, pixelHeight)) return;

        Arrays.fill(surfacePixels, 0);
        Graphics2D graphics = surfaceImage.createGraphics();
        try {
            graphics.scale(qualityScale, qualityScale);
            UiCanvas canvas = new UiCanvas(graphics);
            List<UiHitRegion> nextHits = new ArrayList<>();
            Map<String, NativeBounds> nextNativeBounds = new LinkedHashMap<>();
            UiContext shellContext = new UiContext(canvas, document.theme(), layoutWidth, layoutHeight,
                page.accent(), new UiRect(0, 0, layoutWidth, layoutHeight), hoveredId, pressedId,
                motion(now), animationEnd != 0, paintCount, state, nextHits, nextNativeBounds,
                guiScale() / (double) scale);

            UiRect content = document.shell().paint(shellContext, document, page);
            if (content == null) content = new UiRect(0, 0, layoutWidth, layoutHeight);
            UiContext pageContext = shellContext.withContent(content);
            UiRect clip = content;
            canvas.withClip(clip.x(), clip.y(), clip.width(), clip.height(),
                () -> page.painter().paint(pageContext));

            hitRegions = List.copyOf(nextHits);
            nativeBounds.clear();
            nativeBounds.putAll(nextNativeBounds);
        } finally {
            graphics.dispose();
        }
        upload();
        paintCount++;

        // Hit regions may have moved under a stationary cursor (page switch, resize, state change).
        // Resolve hover once against the new layout; the guard keeps this from oscillating.
        if (hoverDirty) {
            hoverDirty = false;
            updateHover(mouseX * scale, mouseY * scale, false);
        }
    }

    private boolean ensureSurface(int pixelWidth, int pixelHeight) {
        if (surface != null && surfaceWidth == pixelWidth && surfaceHeight == pixelHeight) return true;
        closeSurface();
        try {
            surface = new DynamicTexture(() -> "pane-ui-surface", pixelWidth, pixelHeight, true);
        } catch (RuntimeException e) {
            return false;
        }
        surfaceSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        surfaceImage = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
        // Taking the backing array once lets every repaint reuse it instead of allocating a
        // fresh int[] per paint, and skips BufferedImage.getRGB's per-pixel ColorModel path.
        surfacePixels = ((DataBufferInt) surfaceImage.getRaster().getDataBuffer()).getData();
        surfaceWidth = pixelWidth;
        surfaceHeight = pixelHeight;
        return true;
    }

    private void upload() {
        if (surface == null || surfacePixels == null) return;
        // AWT gives ARGB ints; NativeImage wants RGBA bytes. Swizzle in place - the buffer is
        // fully cleared and repainted before it is read again.
        int[] pixels = surfacePixels;
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            pixels[i] = argb & 0xFF00FF00 | argb >>> 16 & 0xFF | (argb & 0xFF) << 16;
        }
        NativeImage nativeImage = surface.getPixels();
        nativeImage.getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer().put(0, pixels);
        surface.upload();
    }

    /**
     * GUI-space to layout-space factor. Layout units are operating-system points scaled by the
     * theme's density, so a screen is the same physical size regardless of Minecraft's GUI scale.
     */
    private float computeLayoutScale() {
        double density = Math.max(.1, document.theme().density());
        return (float) clamp(guiScale() / (dpiScale() * density), .5, 4);
    }

    /**
     * Layout-space to surface-space factor. Targets the physical framebuffer resolution so text
     * lands on real pixels, then clamps so a fullscreen surface stays within the memory budget.
     */
    private float computeQualityScale(int layoutWidth, int layoutHeight) {
        double target = dpiScale() * Math.max(.1, document.theme().density());
        double memoryCap = Math.min(MAX_SURFACE_WIDTH / Math.max(1, layoutWidth),
            MAX_SURFACE_HEIGHT / Math.max(1, layoutHeight));
        return (float) clamp(Math.min(target, memoryCap), 1, 2);
    }

    /** Physical framebuffer pixels per operating-system point. 2 on a HiDPI display. */
    private static double dpiScale() {
        Window window = Minecraft.getInstance().getWindow();
        int physical = window.getWidth();
        int points = window.getScreenWidth();
        return points > 0 && physical > 0 ? Math.max(1, (double) physical / points) : 1;
    }

    private static double guiScale() {
        return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void closeSurface() {
        if (surface != null) {
            surface.close();
            surface = null;
        }
        surfaceSampler = null;
        surfaceImage = null;
        surfacePixels = null;
        surfaceWidth = 0;
        surfaceHeight = 0;
    }

    @Override
    public void removed() {
        closeSurface();
        hitRegions = List.of();
        nativeBounds.clear();
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
}
