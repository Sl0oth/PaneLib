package dev.pane.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletionStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
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
    private static final int VIRTUAL_ATLAS_GUTTER = 1;
    private static final String MENU_PREFIX = "menu:";
    private static final String MENU_ITEM_PREFIX = "menu-item:";
    private static final String DROPDOWN_ITEM_PREFIX = "dropdown:";
    private static final String DIALOG_PREFIX = "dialog:";
    private static final String DIALOG_CONFIRM_PREFIX = "dialog-confirm:";
    private static final String DIALOG_CANCEL_PREFIX = "dialog-cancel:";
    private static final String TOAST_DISMISS_PREFIX = "toast-dismiss:";

    private final UiDocument document;
    private final NativeOverlayRenderer nativeOverlay;
    private final UiEventListener eventListener;
    private final UiScreenOptions options;
    private final Map<String, Object> state = new HashMap<>();
    private final Map<String, UiAnimationClock.State> animations = new HashMap<>();
    private Map<String, UiRect> customAnimationBounds = Map.of();
    private Set<String> activeCustomAnimationIds = Set.of();
    private final Map<String, NativeBounds> nativeBounds = new LinkedHashMap<>();
    private final Map<String, NativeBounds> nativeBoundsView = Collections.unmodifiableMap(nativeBounds);
    private final Map<String, UiRect> nativeOverlayLayers = new LinkedHashMap<>();
    private final Map<String, UiRect> nativeOverlayLayersView = Collections.unmodifiableMap(nativeOverlayLayers);
    private final Map<String, UiRect> toastBounds = new HashMap<>();
    private List<UiHitRegion> hitRegions = List.of();
    private List<UiScrollRegion> scrollRegions = List.of();
    private List<UiInputRegion> inputRegions = List.of();
    private List<UiTooltip> tooltips = List.of();
    private List<UiMenu> menus = List.of();
    private List<UiDialogRequest> dialogs = List.of();
    private final List<ActiveToast> toasts = new ArrayList<>();

    private DynamicTexture toastSurface;
    private BufferedImage toastImage;
    private int[] toastPixels;
    private UiRect toastLayoutBounds;
    private boolean toastSurfaceDirty = true;
    private long lastToastPaint;
    private List<UiHitRegion> toastHitRegions = List.of();
    private long toastUploadPixels;

    /** Pixels uploaded by the independent toast overlay since this screen opened. */
    public long toastUploadPixels() { return toastUploadPixels; }

    private DynamicTexture surface;
    private GpuSampler surfaceSampler;
    private BufferedImage surfaceImage;
    private int[] surfacePixels;
    private int surfaceWidth;
    private int surfaceHeight;
    private DynamicTexture tooltipSurface;
    private GpuSampler tooltipSampler;
    private BufferedImage tooltipImage;
    private int[] tooltipPixels;
    private int tooltipPixelWidth;
    private int tooltipPixelHeight;
    private UiRect tooltipLayoutBounds;
    private boolean tooltipSurfaceDirty = true;
    private float surfaceQualityScale = 1;
    private int surfaceLayoutWidth;
    private int surfaceLayoutHeight;

    private String pageId;
    private String hoveredId;
    private String pressedId;
    private String focusedId;
    private final java.util.ArrayDeque<FocusScope> focusScopes = new java.util.ArrayDeque<>();
    private FocusRestore pendingFocus, dialogRestore;
    private record FocusRestore(String id, int cursor, int anchor) { }
    private record FocusScope(String id, Set<String> controls, FocusRestore restore, Runnable onEscape) { }
    private final Map<String, UiTextHistory> textHistories = new java.util.LinkedHashMap<>(16, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, UiTextHistory> entry) { return size() > 16; }
    };

    private String openMenuId;
    private boolean menuOpenedWithPointer;
    private String openDialogId;
    private String visibleTooltipId;
    private long tooltipRevealAt;
    private UiHitRegion draggingSlider;
    private UiInputRegion draggingTextInput;
    private int textCursor;
    private int textAnchor;
    private boolean dirty = true;
    private UiRect partialDirtyRegion;
    private boolean hoverDirty = true;
    private boolean customAnimationsActive;
    private final Map<String, UiScrollPhysics.Motion> scrollMotions = new HashMap<>();
    // Two independent clocks. Page transitions drive the entrance motion painters read from
    // motion(); hover and press drive interactionMotion(). Sharing one clock made every hover
    // restart the entrance animation, so the whole page appeared to jump on mouse-over.
    private long transitionStart;
    private long transitionEnd;
    private long interactionStart;
    private long interactionEnd;
    private UiRect interactionRepaintBounds;
    private boolean opened;
    private long lastPaint;
    private int paintCount;
    private int partialPaintCount;
    private long lastPaintDurationNanos;
    private int lastUploadPixelCount;
    private int mouseX;
    private int mouseY;
    private float layoutScale = 1;
    private final Map<String, VirtualListLayer> virtualLayers = new LinkedHashMap<>();
    private List<UiHitRegion> virtualHitRegions = List.of();
    private List<UiInputRegion> virtualInputRegions = List.of();
    private List<UiTooltip> virtualTooltips = List.of();
    private List<UiMenu> virtualMenus = List.of();
    private List<UiDialogRequest> virtualDialogs = List.of();
    private final Map<String, NativeBounds> virtualNativeBounds = new LinkedHashMap<>();
    private final Map<String, UiRect> virtualNativeOverlayLayers = new LinkedHashMap<>();
    private final Map<String, NativeBounds> combinedNativeBounds = new LinkedHashMap<>();
    private final Set<VirtualRowKey> animatedVirtualRows = new HashSet<>();
    private boolean virtualCompositeActive;
    private long gpuScrollFrameCount;
    private long virtualRowRasterCount;
    private long virtualRowUploadPixels;
    private long retainedTextureAllocationCount;
    private long virtualAtlasAllocationCount;
    private long virtualRowBufferAllocationCount;
    private long lastVirtualRowPaintNanos;
    private int lastVirtualRowNonTransparentPixels;
    private long lastVirtualAnimationPaint;
    private final long createdAtNanos;
    private long firstPaintNanos = -1;
    private long frameCount;
    private long idleRepaintCount;
    private long dirtyRequestCount;
    private long dirtyRectangleCount;
    private long dirtyRequestedPixels;
    private int lastDirtyPixels;
    private long lastRasterNanos;
    private long lastUploadNanos;
    private long lastCompositeNanos;
    private long lastNativeOverlayNanos;
    private int lastNativeItemCount;
    private int lastNativeEntityCount;
    private long lastDiagnosticsSampleNanos;
    private float motionScaleOverride = Float.NaN;

    /** Effective motion multiplier. Zero disables ongoing and future retained animations. */
    public float motionScale() {
        return Float.isNaN(motionScaleOverride) ? document.theme().motionScale() : motionScaleOverride;
    }

    /** Runtime accessibility control; changing it invalidates the screen once. */
    public void setMotionScale(float value) {
        if (!Float.isFinite(value)) return;
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) { client.execute(() -> setMotionScale(value)); return; }
        motionScaleOverride = Math.max(0, Math.min(2, value));
        if (motionScaleOverride == 0) {
            transitionEnd = interactionEnd = 0;
            scrollMotions.forEach((id, motion) -> state.put("scroll:" + id, motion.target()));
            scrollMotions.clear();
            markAnimatedVirtualRowsDirty();
            animatedVirtualRows.clear();
        }
        invalidate();
    }

    public UiScreen(UiDocument document) {
        this(document, NativeOverlayRenderer.NONE, UiEventListener.NONE, UiScreenOptions.defaults());
    }

    public UiScreen(UiDocument document, NativeOverlayRenderer nativeOverlay,
                    UiEventListener eventListener) {
        this(document, nativeOverlay, eventListener, UiScreenOptions.defaults());
    }

    public UiScreen(UiDocument document, NativeOverlayRenderer nativeOverlay,
                    UiEventListener eventListener, UiScreenOptions options) {
        super(Component.literal(document.title()));
        this.document = Objects.requireNonNull(document, "document");
        this.nativeOverlay = nativeOverlay == null ? NativeOverlayRenderer.NONE : nativeOverlay;
        this.eventListener = eventListener == null ? UiEventListener.NONE : eventListener;
        this.options = options == null ? UiScreenOptions.defaults() : options;
        createdAtNanos = System.nanoTime();
        pageId = document.initialPageId();
    }

    /** Creates a screen with a typed event callback. */
    public UiScreen(UiDocument document, NativeOverlayRenderer nativeOverlay,
                    UiEventHandler eventHandler) {
        this(document, nativeOverlay, eventHandler, UiScreenOptions.defaults());
    }

    /** Creates a screen with typed events plus parent, backdrop, raw-input, and close options. */
    public UiScreen(UiDocument document, NativeOverlayRenderer nativeOverlay,
                    UiEventHandler eventHandler, UiScreenOptions options) {
        this(document, nativeOverlay, eventHandler == null ? UiEventListener.NONE
            : (kind, key, value) -> eventHandler.onEvent(new UiEvent(UiEventType.fromWire(kind), key, value)), options);
    }

    public UiDocument document() { return document; }
    public UiScreenOptions options() { return options; }
    public Optional<Screen> parentScreen() { return Optional.ofNullable(options.parent()); }
    public String pageId() { return pageId; }
    /** The id of the control that will receive keyboard input, if any. */
    public Optional<String> focusedId() { return Optional.ofNullable(focusedId); }

    /** Clears keyboard focus without changing field contents. Call on the client thread. */
    public void clearFocus() {
        pendingFocus = null; focus(null);
    }

    /**
     * Captures focus for a custom modal. Only these ids can receive pointer, keyboard, or scroll
     * input. Initial focus is applied after its first paint. Escape is consumed and invokes onEscape.
     * The raw screen key/click handlers are bypassed while a scope is active. Tab follows paint order.
     * Nest scopes in LIFO order. Call on the client thread, outside the page painter.
     */
    public void pushFocusScope(String id, List<String> controls, String initialFocus, Runnable onEscape) {
        Objects.requireNonNull(id); Objects.requireNonNull(controls);
        if (openDialogId != null) throw new IllegalStateException("Close the built-in dialog before opening a custom scope");
        if (id.isBlank() || controls.isEmpty() || controls.stream().anyMatch(x -> x == null || x.isBlank())
                || focusScopes.stream().anyMatch(x -> x.id().equals(id))) throw new IllegalArgumentException("Invalid or duplicate focus scope");
        Set<String> allowed = Set.copyOf(controls);
        if (initialFocus != null && !allowed.contains(initialFocus)) throw new IllegalArgumentException("Initial focus must belong to scope");
        FocusRestore restore = new FocusRestore(focusedId, textCursor, textAnchor);
        closeMenu(false);
        focusScopes.addLast(new FocusScope(id, allowed, restore, onEscape == null ? () -> { } : onEscape));
        pressedId = null; draggingSlider = null; draggingTextInput = null;
        focus(null);
        pendingFocus = new FocusRestore(initialFocus == null ? controls.getFirst() : initialFocus, -1, -1);
        requestFullRepaint();
    }
    public void pushFocusScope(String id, List<String> controls, String initialFocus) {
        pushFocusScope(id, controls, initialFocus, null);
    }
    /** Closes only the top scope and restores its previous control/selection when still available. */
    public void popFocusScope(String id) {
        if (openDialogId != null) throw new IllegalStateException("Close the built-in dialog before closing its custom scope");
        if (focusScopes.isEmpty() || !focusScopes.peekLast().id().equals(id)) throw new IllegalStateException("Close focus scopes in reverse order");
        FocusScope scope = focusScopes.removeLast();
        closeMenu(false); pressedId = null; draggingSlider = null; draggingTextInput = null;
        focus(null); pendingFocus = scope.restore(); resolvePendingFocus(); requestFullRepaint();
    }
    public Optional<String> focusScopeId() { return focusScopes.isEmpty() ? Optional.empty() : Optional.of(focusScopes.peekLast().id()); }
    private boolean focusAllowed(String id) {
        if (id == null) return false;
        if (openDialogId != null) return id.equals("dialog-cancel") || id.equals("dialog-confirm") || id.equals("dialog-backdrop");
        if (focusScopes.isEmpty()) return true;
        if (openMenuId != null && focusScopes.peekLast().controls().contains(openMenuId)
                && id.startsWith("menu:" + openMenuId + ":")) return true;
        return focusScopes.peekLast().controls().contains(id);
    }
    private void resolvePendingFocus() {
        FocusRestore restore = pendingFocus;
        if (restore == null || restore.id() == null) { pendingFocus = null; return; }
        if (focus(restore.id())) {
            UiInputRegion input = inputRegion(restore.id());
            if (input != null && restore.cursor() >= 0) {
                String value = stringValue(input.key(), "");
                textCursor = UiTextBoundary.floor(value, restore.cursor()); textAnchor = UiTextBoundary.floor(value, restore.anchor());
            }
            pendingFocus = null;
        }
    }

    /** Public last-paint diagnostics, all bounds/offsets in layout units. */
    public Optional<UiRect> scrollViewport(String id) { return scrollSnapshot(id).map(UiScrollSnapshot::viewport); }
    public Optional<UiScrollSnapshot> scrollSnapshot(String id) {
        UiScrollRegion region = scrollRegion(id);
        if (region == null) return Optional.empty();
        double offset = numberValue("scroll:" + id, 0);
        UiScrollPhysics.Motion motion = scrollMotions.get(id);
        return Optional.of(new UiScrollSnapshot(id, region.viewport(), region.contentHeight(), offset, motion == null ? offset : motion.target()));
    }
    public UiRasterQuality rasterQuality() {
        return new UiRasterQuality(surfaceLayoutWidth, surfaceLayoutHeight, surfaceWidth, surfaceHeight,
            (float)(dpiScale() * uiScale()), surfaceQualityScale);
    }

    /** The menu currently open on this screen, if any. */
    public Optional<String> openMenuId() { return Optional.ofNullable(openMenuId); }

    /** The confirmation dialog currently open on this screen, if any. */
    public Optional<String> openDialogId() { return Optional.ofNullable(openDialogId); }

    /** The tooltip currently painted after a short hover delay, if any. */
    public Optional<String> visibleTooltipId() { return Optional.ofNullable(visibleTooltipId); }

    /** How many times the surface has been rasterized. A diagnostic, not a frame count. */
    public int paintCount() { return paintCount; }

    /** CPU raster plus upload time for the latest retained repaint. */
    public long lastPaintDurationNanos() { return lastPaintDurationNanos; }

    /** Viewport-only retained repaints completed by the screen. */
    public int partialPaintCount() { return partialPaintCount; }

    /** Number of texture pixels uploaded by the latest repaint. */
    public int lastUploadPixelCount() { return lastUploadPixelCount; }

    /** Current retained texture width in physical pixels. A resize diagnostic. */
    public int surfacePixelWidth() { return surfaceWidth; }

    /** Current retained texture height in physical pixels. A resize diagnostic. */
    public int surfacePixelHeight() { return surfaceHeight; }

    /** Frames where a virtual list moved entirely by GPU composition, without repainting the page surface. */
    public long gpuScrollFrameCount() { return gpuScrollFrameCount; }

    /** Whether virtual rows are currently being composed independently from the retained page. */
    public boolean virtualCompositionActive() { return virtualCompositeActive; }

    /** Individual virtual rows rasterized since this screen opened. */
    public long virtualRowRasterCount() { return virtualRowRasterCount; }

    /** Total row-texture pixels uploaded since this screen opened. */
    public long virtualRowUploadPixels() { return virtualRowUploadPixels; }

    /** CPU raster plus upload time of the most recently refreshed virtual row. */
    public long lastVirtualRowPaintNanos() { return lastVirtualRowPaintNanos; }

    /** Diagnostic count of non-transparent pixels produced by the most recently rasterized row. */
    public int lastVirtualRowNonTransparentPixels() { return lastVirtualRowNonTransparentPixels; }

    /** Retained page and tooltip GPU textures allocated since this screen opened. */
    public long retainedTextureAllocationCount() { return retainedTextureAllocationCount; }

    /** Virtual-list GPU atlases allocated since this screen opened. */
    public long virtualAtlasAllocationCount() { return virtualAtlasAllocationCount; }

    /** Reusable CPU row buffers allocated since this screen opened. */
    public long virtualRowBufferAllocationCount() { return virtualRowBufferAllocationCount; }

    /** First row currently intersecting a composited virtual-list viewport. */
    public int virtualFirstVisible(String listId) {
        VirtualListLayer layer = virtualLayers.get(listId);
        return layer == null ? -1 : layer.request.firstVisible(layer.offset());
    }

    /** Number of rows currently cached for a composited virtual list, including bounded overscan. */
    public int virtualCachedRows(String listId) {
        VirtualListLayer layer = virtualLayers.get(listId);
        return layer == null ? 0 : layer.rows.size();
    }

    /**
     * Scrolls the smallest possible distance needed to reveal a virtual row. This supports
     * consumer-owned arrow-key selection without duplicating PaneLib's viewport calculations.
     * Returns {@code false} only when the list or index is not currently valid.
     */
    public boolean scrollVirtualItemIntoView(String listId, int index) {
        VirtualListLayer layer = virtualLayers.get(listId);
        if (layer == null || index < 0 || index >= layer.request.itemCount) return false;
        double current = layer.offset();
        double top = index * (double) layer.request.stride();
        double bottom = top + layer.request.rowHeight;
        double next = current;
        if (top < current) next = top;
        else if (bottom > current + layer.request.viewport.height()) {
            next = bottom - layer.request.viewport.height();
        }
        next = Math.max(0, Math.min(layer.request.maxOffset(), next));
        scrollMotions.remove(listId);
        state.put("scroll:" + listId, next);
        layer.currentOffset = next;
        long now = System.nanoTime();
        ensureVirtualRows(layer, now,
            new UiAnimationClock(animations, now, motionScale()));
        refreshVirtualInteractions();
        updateHover(mouseX * layoutScale, mouseY * layoutScale, true);
        return true;
    }

    /** Repaints one data row without invalidating the page chrome or neighboring rows. */
    public void invalidateVirtualItem(String listId, int index) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) {
            client.execute(() -> invalidateVirtualItem(listId, index));
            return;
        }
        VirtualListLayer layer = virtualLayers.get(listId);
        if (layer == null || index < 0 || index >= layer.request.itemCount) {
            markDirty();
            return;
        }
        VirtualRowSurface row = layer.rows.get(index);
        if (row != null) row.dirty = true;
    }

    public Object value(String key) { return state.get(key); }

    public boolean booleanValue(String key, boolean fallback) {
        return state.get(key) instanceof Boolean bool ? bool : fallback;
    }

    public double numberValue(String key, double fallback) {
        return state.get(key) instanceof Number number ? number.doubleValue() : fallback;
    }

    public String stringValue(String key, String fallback) {
        return state.get(key) instanceof String text ? text : fallback;
    }

    public void setValue(String key, Object value) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) {
            client.execute(() -> setValue(key, value));
            return;
        }
        Object previous = state.put(key, value);
        if (!Objects.equals(previous, value)) textHistories.remove(key);
        markDirty();
    }

    /**
     * Shows a short, non-blocking acknowledgement. Supplying the same id replaces an older toast
     * with that id; at most four can be visible at once.
     */
    public void toast(UiToast toast) {
        if (toast == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) {
            client.execute(() -> toast(toast));
            return;
        }
        toasts.removeIf(candidate -> candidate.toast().id().equals(toast.id()));
        while (toasts.size() >= document.toastSettings().maxVisible()) toasts.removeFirst();
        toasts.add(new ActiveToast(toast, System.nanoTime()));
        toastSurfaceDirty = true;
    }

    /** Number of currently visible acknowledgements. Useful for development diagnostics. */
    public int activeToastCount() {
        pruneToasts(System.nanoTime());
        return toasts.size();
    }

    /**
     * GUI-space to layout-space factor from the last paint. Multiply a Minecraft mouse
     * coordinate by this to get the layout coordinate a painter would have drawn at.
     */
    public float layoutScale() { return layoutScale; }

    /**
     * Layout-space rectangle claimed by the control or passive tooltip target with this id during
     * the last paint. Useful for anchoring tooltips, popovers or context menus to a painted target.
     */
    public Optional<UiRect> regionBounds(String id) {
        Optional<UiRect> hit = region(id).map(r -> new UiRect(r.x(), r.y(), r.width(), r.height()));
        if (hit.isPresent()) return hit;
        for (UiTooltip tooltip : virtualTooltips) {
            if (id.equals(tooltip.id())) return Optional.of(tooltip.anchor());
        }
        for (UiTooltip tooltip : tooltips) {
            if (id.equals(tooltip.id())) return Optional.of(tooltip.anchor());
        }
        return Optional.empty();
    }

    /**
     * Layout-space value track of the continuous control with this id, from the last paint.
     * For a slider this is the visible rail, which is narrower than its grab rectangle.
     */
    public Optional<UiRect> trackBounds(String id) {
        return region(id).map(r -> new UiRect(r.trackX(), r.y(), r.trackWidth(), r.height()));
    }

    /** Layout-space bounds of a visible toast, useful for development diagnostics. */
    public Optional<UiRect> toastBounds(String id) { return Optional.ofNullable(toastBounds.get(id)); }

    private Optional<UiHitRegion> region(String id) {
        if (id == null) return Optional.empty();
        for (UiHitRegion candidate : toastHitRegions) if (candidate.id().equals(id)) return Optional.of(candidate);
        for (UiHitRegion candidate : hitRegions) {
            if (candidate.id().equals(id)) return Optional.of(candidate);
        }
        for (UiHitRegion candidate : virtualHitRegions) {
            if (candidate.id().equals(id)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public void showPage(String id) {
        if (id == null || id.equals(pageId) || document.page(id) == null) return;
        pageId = id;
        focusScopes.clear(); pendingFocus = null; dialogRestore = null; textHistories.clear();
        hoveredId = null;
        pressedId = null;
        focusedId = null;
        draggingSlider = null;
        draggingTextInput = null;
        nativeBounds.clear();
        nativeOverlayLayers.clear();
        hitRegions = List.of();
        scrollRegions = List.of();
        inputRegions = List.of();
        tooltips = List.of();
        menus = List.of();
        dialogs = List.of();
        animations.clear();
        customAnimationBounds = Map.of();
        activeCustomAnimationIds = Set.of();
        scrollMotions.clear();
        closeVirtualLayers();
        customAnimationsActive = false;
        openMenuId = null;
        openDialogId = null;
        menuOpenedWithPointer = false;
        visibleTooltipId = null;
        tooltipRevealAt = 0;
        tooltipSurfaceDirty = true;
        hoverDirty = true;
        startTransition(210);
        eventListener.onEvent("page", pageId, "");
    }

    /**
     * Marks the cached surface stale. Safe to call from a network/image-loading thread; off-thread
     * calls are queued onto Minecraft's render thread and the next frame repaints exactly once.
     */
    public void invalidate() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) {
            client.execute(this::markDirty);
            return;
        }
        markDirty();
    }

    /** Registers thread-safe invalidation when an asynchronous request succeeds or fails. */
    public <T> CompletionStage<T> invalidateWhenComplete(CompletionStage<T> request) {
        Objects.requireNonNull(request, "request").whenComplete((ignored, error) -> invalidate());
        return request;
    }

    private void markDirty() {
        requestFullRepaint();
        hoverDirty = true;
    }

    private void requestFullRepaint() {
        recordDirtyRequest(null);
        dirty = true;
        partialDirtyRegion = null;
    }

    private void requestPartialRepaint(UiRect region) {
        if (region == null) {
            requestFullRepaint();
            return;
        }
        recordDirtyRequest(region);
        // A full repaint already queued always wins. Otherwise merge overlapping/nested scroll
        // viewports so several trackpad events still produce one upload on the next frame.
        if (dirty && partialDirtyRegion == null) return;
        dirty = true;
        partialDirtyRegion = partialDirtyRegion == null ? region : union(partialDirtyRegion, region);
    }

    private void recordDirtyRequest(UiRect region) {
        if (options.performanceDiagnostics() == UiPerformanceDiagnostics.NONE) return;
        dirtyRequestCount++;
        dirtyRectangleCount++;
        if (region == null) {
            int pixels = Math.max(1, surfaceWidth * surfaceHeight);
            dirtyRequestedPixels += pixels;
        } else {
            dirtyRequestedPixels += Math.max(1L, Math.round(region.width() * surfaceQualityScale)
                * Math.round(region.height() * surfaceQualityScale));
        }
    }

    private void requestCustomAnimationRepaint() {
        UiRect region = null;
        for (String id : activeCustomAnimationIds) {
            UiRect candidate = customAnimationBounds.get(id);
            if (candidate == null) {
                requestFullRepaint();
                return;
            }
            region = region == null ? candidate : union(region, candidate);
        }
        if (region == null) requestFullRepaint();
        else requestPartialRepaint(expandAndClamp(region, surfaceLayoutWidth, surfaceLayoutHeight, 2));
    }

    @Override
    protected void init() {
        requestFullRepaint();
        hoverDirty = true;
        // Minecraft also calls init() on resize; only the first one is an entrance.
        if (!opened) {
            opened = true;
            startTransition(180);
        }
    }

    @Override
    public void resize(int width, int height) {
        boolean changed = this.width != width || this.height != height;
        super.resize(width, height);
        Screen parent = options.parent();
        if (parent != null && parent != this && (parent.width != width || parent.height != height)) {
            parent.resize(width, height);
        }
        if (changed) {
            closeSurface();
            markDirty();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        synchronizeViewport(graphics);
        Screen parent = options.parent();
        switch (options.backdrop()) {
            case NONE -> { }
            case MINECRAFT -> extractMinecraftBackdrop(graphics, tickDelta);
            case PARENT_BACKGROUND -> {
                if (parent == null || parent == this) extractMinecraftBackdrop(graphics, tickDelta);
                else extractParentBackground(parent, graphics, mouseX, mouseY, tickDelta);
            }
            case PARENT_SCREEN -> {
                if (parent == null || parent == this) {
                    extractMinecraftBackdrop(graphics, tickDelta);
                } else {
                    extractParentBackground(parent, graphics, mouseX, mouseY, tickDelta);
                    graphics.nextStratum();
                    parent.extractRenderState(graphics, mouseX, mouseY, tickDelta);
                }
            }
        }
    }

    private void extractParentBackground(Screen parent, GuiGraphicsExtractor graphics,
                                         int mouseX, int mouseY, float tickDelta) {
        // TitleScreen draws its panorama from extractRenderState and intentionally leaves
        // extractBackground empty. Asking for the whole render state would also copy its logo,
        // splash, buttons, and footer, so use Minecraft's panorama-only path for this one screen.
        if (parent instanceof TitleScreen) extractPanorama(graphics, tickDelta);
        else parent.extractBackground(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        frameCount++;
        synchronizeViewport(graphics);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long now = System.nanoTime();
        if (transitionEnd > 0) {
            if (now < transitionEnd) {
                if (now - lastPaint >= FRAME_NANOS) requestFullRepaint();
            } else {
                transitionEnd = 0;
                requestFullRepaint();
            }
        }
        if (interactionEnd > 0) {
            if (now < interactionEnd) {
                if (!animatedVirtualRows.isEmpty()) {
                    if (now - lastVirtualAnimationPaint >= FRAME_NANOS) {
                        markAnimatedVirtualRowsDirty();
                        lastVirtualAnimationPaint = now;
                    }
                } else if (now - lastPaint >= FRAME_NANOS) requestInteractionRepaint();
            } else {
                interactionEnd = 0;
                if (!animatedVirtualRows.isEmpty()) {
                    markAnimatedVirtualRowsDirty();
                    animatedVirtualRows.clear();
                } else {
                    requestInteractionRepaint();
                    interactionRepaintBounds = null;
                }
            }
        }
        if (customAnimationsActive && now - lastPaint >= FRAME_NANOS) requestCustomAnimationRepaint();
        if (virtualCompositeActive && now - lastVirtualAnimationPaint >= FRAME_NANOS) {
            boolean any = false;
            for (VirtualListLayer layer : virtualLayers.values()) for (VirtualRowSurface row : layer.rows.values()) {
                if (row.customAnimating) { row.dirty = true; any = true; }
            }
            if (any) lastVirtualAnimationPaint = now;
        }
        if (tooltipRevealAt > 0 && now >= tooltipRevealAt) {
            tooltipRevealAt = 0;
            tooltipSurfaceDirty = true;
        }
        if (!toasts.isEmpty()) {
            pruneToasts(now);
            if (now - lastToastPaint >= FRAME_NANOS && toastAppearanceChanged(now)) toastSurfaceDirty = true;
        }
        if (!scrollMotions.isEmpty() && (dirty || now - lastPaint >= FRAME_NANOS)) updateScrollMotions(now);
        boolean repainting = dirty;
        if (repainting) {
            boolean active = transitionEnd > now || interactionEnd > now || customAnimationsActive
                || !scrollMotions.isEmpty() || !toasts.isEmpty();
            if (!active) idleRepaintCount++;
            rebuildSurface(now);
        }
        if (surface == null || surfaceSampler == null) {
            publishDiagnostics(now);
            return;
        }
        long compositeStarted = System.nanoTime();
        graphics.blit(surface.getTextureView(), surfaceSampler, 0, 0, width, height, 0, 1, 0, 1);
        if (virtualCompositeActive && !virtualLayers.isEmpty()) {
            graphics.nextStratum();
            renderVirtualLayers(graphics, now);
        }
        if (nativeOverlay != NativeOverlayRenderer.NONE) {
            graphics.nextStratum();
            long nativeStarted = System.nanoTime();
            NativeOverlayContext nativeContext = new NativeOverlayContext(
                graphics, pageId, combinedNativeBounds(), guiScale(), mouseX, mouseY
            );
            nativeOverlay.render(nativeContext);
            lastNativeOverlayNanos = System.nanoTime() - nativeStarted;
            lastNativeItemCount = nativeContext.renderedItemCount();
            lastNativeEntityCount = nativeContext.renderedEntityCount();
        } else {
            lastNativeOverlayNanos = 0;
            lastNativeItemCount = 0;
            lastNativeEntityCount = 0;
        }
        if (!virtualNativeOverlayLayers.isEmpty()) {
            graphics.nextStratum();
            renderVirtualOverlayLayers(graphics, now);
        }
        if (!nativeOverlayLayers.isEmpty()) {
            graphics.nextStratum();
            renderNativeOverlayLayers(graphics);
        }
        renderToastOverlay(graphics, now);
        renderTooltipOverlay(graphics);
        lastCompositeNanos = System.nanoTime() - compositeStarted;
        publishDiagnostics(now);
    }

    /** Latest render counters and timings without enabling listener callbacks. Dirty-request counters require profiling. */
    public UiPerformanceSnapshot performanceSnapshot() {
        return snapshot(System.nanoTime());
    }

    private void publishDiagnostics(long now) {
        if (options.performanceDiagnostics() == UiPerformanceDiagnostics.NONE) return;
        // Bound callbacks even during continuous repainting; logging must not amplify a stall.
        if (now - lastDiagnosticsSampleNanos < 250_000_000L) return;
        lastDiagnosticsSampleNanos = now;
        options.performanceDiagnostics().onSample(snapshot(now));
    }

    private UiPerformanceSnapshot snapshot(long now) {
        int surfacePixels = Math.max(0, surfaceWidth * surfaceHeight);
        return new UiPerformanceSnapshot(document.title(), pageId, Math.max(0, now - createdAtNanos),
            firstPaintNanos < 0 ? -1 : firstPaintNanos - createdAtNanos, frameCount, paintCount,
            idleRepaintCount, dirtyRequestCount, dirtyRectangleCount, dirtyRequestedPixels,
            lastDirtyPixels, surfacePixels, lastRasterNanos, lastUploadNanos, lastCompositeNanos,
            lastNativeOverlayNanos, lastNativeItemCount, lastNativeEntityCount,
            activeCustomAnimationIds.size(), retainedTextureAllocationCount, virtualAtlasAllocationCount,
            virtualRowBufferAllocationCount);
    }

    /** Reblits named retained regions after native Minecraft rendering has completed. */
    private void renderNativeOverlayLayers(GuiGraphicsExtractor graphics) {
        if (surface == null || surfaceSampler == null || width <= 0 || height <= 0) return;
        for (UiRect layer : nativeOverlayLayersView.values()) {
            UiRect guiBounds = nativeOverlayGuiBounds(layer, layoutScale, width, height);
            int x = (int) guiBounds.x();
            int y = (int) guiBounds.y();
            int right = (int) guiBounds.right();
            int bottom = (int) guiBounds.bottom();
            if (right <= x || bottom <= y) continue;
            graphics.enableScissor(x, y, right, bottom);
            graphics.blit(surface.getTextureView(), surfaceSampler, 0, 0, width, height, 0, 1, 0, 1);
            graphics.disableScissor();
        }
    }

    /**
     * Converts a retained Pane rectangle to the extractor's GUI coordinate space. Kept separate
     * from {@link NativeBounds}: native slots have to use backing-pixel coordinates for Minecraft
     * models, while a cover layer is a direct re-composite of this screen's retained surface.
     */
    static UiRect nativeOverlayGuiBounds(UiRect layoutBounds, float layoutScale, int guiWidth, int guiHeight) {
        if (layoutBounds == null || layoutScale <= 0 || guiWidth <= 0 || guiHeight <= 0) {
            return new UiRect(0, 0, 0, 0);
        }
        int x = clampGui((int) Math.floor(layoutBounds.x() / layoutScale), guiWidth);
        int y = clampGui((int) Math.floor(layoutBounds.y() / layoutScale), guiHeight);
        int right = Math.max(x, clampGui((int) Math.ceil(layoutBounds.right() / layoutScale), guiWidth));
        int bottom = Math.max(y, clampGui((int) Math.ceil(layoutBounds.bottom() / layoutScale), guiHeight));
        return new UiRect(x, y, right - x, bottom - y);
    }

    private static int clampGui(int value, int limit) {
        return Math.max(0, Math.min(limit, value));
    }

    private void extractMinecraftBackdrop(GuiGraphicsExtractor graphics, float tickDelta) {
        if (minecraft != null && minecraft.level == null) {
            extractPanorama(graphics, tickDelta);
            extractBlurredBackground(graphics);
            extractMenuBackground(graphics);
        } else {
            extractTransparentBackground(graphics);
        }
    }

    /**
     * macOS can expose a new drawable before Minecraft delivers the matching Screen.resize call.
     * Trust the extractor's active GUI viewport so the retained texture always covers that frame.
     */
    private void synchronizeViewport(GuiGraphicsExtractor graphics) {
        int viewportWidth = Math.max(1, graphics.guiWidth());
        int viewportHeight = Math.max(1, graphics.guiHeight());
        if (width != viewportWidth || height != viewportHeight) resize(viewportWidth, viewportHeight);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.mouseX = (int) mouseX;
        this.mouseY = (int) mouseY;
        float scale = eventLayoutScale();
        updateHover(mouseX * scale, mouseY * scale, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        float scale = eventLayoutScale();
        float layoutX = (float) (event.x() * scale);
        float layoutY = (float) (event.y() * scale);
        UiHitRegion region = regionAt(layoutX, layoutY);
        if (openDialogId == null && focusScopes.isEmpty() && options.clickHandler().mouseClicked(this,
            new UiClickEvent(region == null ? null : region.id(), event, doubleClick, layoutX, layoutY))) {
            return true;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (region == null) {
            if (!focusScopes.isEmpty() || openDialogId != null) return true;
            if (openMenuId != null) {
                closeMenu(true);
                return true;
            }
            return false;
        }
        if (openMenuId != null && !isMenuRegion(region)) closeMenu(false);
        focus(region.id());
        UiInputRegion clickedInput = inputRegion(region.id());
        if (clickedInput != null) {
            int caret = clickedInput.caretAt((float) (event.x() * scale),
                (float) (event.y() * scale));
            textAnchor = doubleClick ? 0 : caret;
            textCursor = doubleClick ? stringValue(clickedInput.key(), "").length() : caret;
            draggingTextInput = clickedInput;
            requestFullRepaint();
        }
        pressedId = region.id();
        if (internalBody(region.action()).startsWith("slider:")) {
            draggingSlider = region;
            updateSlider(region, event.x() * scale);
        }
        startInteraction(100);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        boolean wasInteracting = pressedId != null || draggingSlider != null || draggingTextInput != null;
        String releasedId = pressedId;
        float scale = eventLayoutScale();
        UiHitRegion region = regionAt(event.x() * scale, event.y() * scale);
        String action = region != null && region.id().equals(pressedId) ? region.action() : null;
        pressedId = null;
        draggingSlider = null;
        draggingTextInput = null;
        if (action != null) runAction(action, true);
        if (wasInteracting) startInteraction(150, releasedId);
        return wasInteracting;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        this.mouseX = (int) event.x();
        this.mouseY = (int) event.y();
        float scale = eventLayoutScale();
        if (draggingSlider != null) {
            updateSlider(draggingSlider, event.x() * scale);
            return true;
        }
        if (draggingTextInput != null) {
            textCursor = draggingTextInput.caretAt((float) (event.x() * scale),
                (float) (event.y() * scale));
            requestFullRepaint();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float scale = eventLayoutScale();
        if (openDialogId != null) return true;
        UiScrollRegion region = scrollAt(mouseX * scale, mouseY * scale);
        if (region == null || region.maxOffset() <= 0 || vertical == 0) return !focusScopes.isEmpty();
        String key = "scroll:" + region.id();
        long now = System.nanoTime();
        double displayed = numberValue(key, 0);
        UiScrollPhysics.Motion active = scrollMotions.get(region.id());
        double previousTarget = active == null ? displayed : active.target();
        UiScrollSettings settings = document.scrollSettings();
        double next = Math.max(0, Math.min(region.maxOffset(), previousTarget - vertical * settings.step()));
        if (next == previousTarget) return false;
        long duration = scaledDuration(settings.smoothingMillis());
        if (duration <= 0) {
            state.put(key, next);
            scrollMotions.remove(region.id());
        } else {
            scrollMotions.put(region.id(), UiScrollPhysics.retarget(active, next, now, duration));
        }
        VirtualListLayer virtual = virtualCompositeActive ? virtualLayers.get(region.id()) : null;
        if (virtual == null) requestPartialRepaint(region.viewport());
        else {
            // Rows passing beneath a stationary pointer should not each start a hover animation.
            // Hit regions still track the moving atlas; the visual hover returns on settle.
            if (active == null && hoveredId != null) {
                String previousHover = hoveredId;
                hoveredId = null;
                visibleTooltipId = null;
                tooltipRevealAt = 0;
                tooltipSurfaceDirty = true;
                requestVirtualRows(previousHover);
            }
            virtual.currentOffset = duration <= 0 ? next : displayed;
            refreshVirtualInteractions();
            if (duration <= 0) updateHover(mouseX * scale, mouseY * scale, true);
        }
        hoverDirty = true;
        eventListener.onEvent("value", key, Double.toString(next));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && openDialogId != null) {
            closeDialog(true);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && openMenuId != null) {
            closeMenu(menuOpenedWithPointer);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && !focusScopes.isEmpty()) {
            focusScopes.peekLast().onEscape().run(); return true;
        }
        UiInputRegion input = inputRegion(focusedId);
        if (input != null && handleTextKey(input, event)) return true;
        if (focusScopes.isEmpty() && openDialogId == null && options.keyHandler().keyPressed(this, event)) return true;
        if (event.key() == GLFW.GLFW_KEY_TAB) return moveFocus((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
        if (focusedId != null && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER
            || event.key() == GLFW.GLFW_KEY_SPACE)) {
            region(focusedId).ifPresent(region -> runAction(region.action(), false));
            startInteraction(100, focusedId);
            return true;
        }
        if (input == null && focusScopes.isEmpty() && openDialogId == null && document.pageShortcuts() && event.modifiers() == 0) {
            int index = event.key() - GLFW.GLFW_KEY_1;
            if (index >= 0 && index <= 8 && index < document.pages().size()) {
                showPage(document.pages().get(index).id());
                return true;
            }
        }
        if (!focusScopes.isEmpty() || openDialogId != null) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        UiInputRegion input = inputRegion(focusedId);
        if (input == null || !event.isAllowedChatCharacter()) return !focusScopes.isEmpty() || openDialogId != null;
        replaceTextSelection(input, event.codepointAsString());
        return true;
    }

    private void runAction(String action, boolean fromPointer) {
        hoverDirty = true;
        if (action == null) return;
        if (!action.startsWith(UiContext.INTERNAL_ACTION_PREFIX)) {
            eventListener.onEvent("action", action, "");
            return;
        }
        action = internalBody(action);
        if (action.startsWith("input:")) return;
        if (action.startsWith(DIALOG_PREFIX)) {
            dialogRestore = new FocusRestore(focusedId, textCursor, textAnchor);
            openDialogId = action.substring(DIALOG_PREFIX.length());
            focus(null); pendingFocus = new FocusRestore("dialog-cancel", -1, -1);
            requestFullRepaint();
            return;
        }
        if (action.startsWith(DIALOG_CONFIRM_PREFIX)) {
            String eventAction = action.substring(DIALOG_CONFIRM_PREFIX.length());
            closeDialog(false);
            eventListener.onEvent("action", eventAction, "");
            return;
        }
        if (action.startsWith(DIALOG_CANCEL_PREFIX)) {
            String eventAction = action.substring(DIALOG_CANCEL_PREFIX.length());
            closeDialog(false);
            eventListener.onEvent("action", eventAction, "");
            return;
        }
        if (action.startsWith(TOAST_DISMISS_PREFIX)) {
            dismissToast(action.substring(TOAST_DISMISS_PREFIX.length()));
            return;
        }
        if (action.startsWith(MENU_PREFIX)) {
            String menuId = action.substring(MENU_PREFIX.length());
            if (menuId.equals(openMenuId)) {
                closeMenu(fromPointer);
            } else {
                openMenuId = menuId;
                menuOpenedWithPointer = fromPointer;
                requestFullRepaint();
            }
            return;
        }
        if (action.startsWith(MENU_ITEM_PREFIX)) {
            String itemAction = action.substring(MENU_ITEM_PREFIX.length());
            closeMenu(fromPointer);
            if (itemAction.startsWith(UiContext.INTERNAL_ACTION_PREFIX)
                && internalBody(itemAction).startsWith(DROPDOWN_ITEM_PREFIX)) {
                String body = internalBody(itemAction).substring(DROPDOWN_ITEM_PREFIX.length());
                int split = body.indexOf('=');
                if (split > 0) {
                    String key = body.substring(0, split);
                    String value = body.substring(split + 1);
                    state.put(key, value);
                    requestFullRepaint();
                    eventListener.onEvent("value", key, value);
                }
                return;
            }
            eventListener.onEvent("action", itemAction, "");
            return;
        }
        if (action.startsWith("page:")) {
            showPage(action.substring("page:".length()));
            return;
        }
        if (action.startsWith("select:")) {
            String body = action.substring("select:".length());
            int split = body.indexOf('=');
            if (split > 0) {
                String key = body.substring(0, split);
                String value = body.substring(split + 1);
                state.put(key, value);
                requestFullRepaint();
                eventListener.onEvent("value", key, value);
            }
            return;
        }
        if (action.startsWith("toggle:")) {
            String key = action.substring("toggle:".length());
            boolean value = !(state.get(key) instanceof Boolean bool && bool);
            state.put(key, value);
            requestFullRepaint();
            eventListener.onEvent("value", key, Boolean.toString(value));
            return;
        }
    }

    private void updateSlider(UiHitRegion region, double mouseX) {
        String descriptor = internalBody(region.action()).substring("slider:".length());
        int maximumSeparator = descriptor.lastIndexOf(':');
        int minimumSeparator = maximumSeparator < 0 ? -1 : descriptor.lastIndexOf(':', maximumSeparator - 1);
        String key = minimumSeparator < 0 ? descriptor : descriptor.substring(0, minimumSeparator);
        double minimum = minimumSeparator < 0 ? 0
            : Double.parseDouble(descriptor.substring(minimumSeparator + 1, maximumSeparator));
        double maximum = maximumSeparator < 0 ? 1
            : Double.parseDouble(descriptor.substring(maximumSeparator + 1));
        double value = minimum + region.fractionAt(mouseX) * (maximum - minimum);
        Object previous = state.put(key, value);
        if (previous instanceof Number number && number.doubleValue() == value) return;
        eventListener.onEvent("value", key, Double.toString(value));
        requestFullRepaint();
    }

    private void updateHover(double layoutX, double layoutY, boolean animate) {
        UiHitRegion region = regionAt(layoutX, layoutY);
        String next = region == null ? tooltipAt(layoutX, layoutY) : region.id();
        if (Objects.equals(next, hoveredId)) return;
        String previous = hoveredId;
        hoveredId = next;
        visibleTooltipId = null;
        tooltipSurfaceDirty = true;
        long delayNanos = document.tooltipDelayMillis() * 1_000_000L;
        tooltipRevealAt = next == null || delayNanos == 0 || !hasTooltip(next)
            ? 0 : System.nanoTime() + delayNanos;
        boolean previousInteractive = isInteractiveId(previous);
        boolean nextInteractive = isInteractiveId(next);
        if (animate) {
            String previousInteraction = previousInteractive ? previous : null;
            String nextInteraction = nextInteractive ? next : null;
            if (previousInteraction != null || nextInteraction != null) {
                startInteraction(120, previousInteraction, nextInteraction);
            }
        } else if (!requestVirtualRows(previous, next)) {
            requestPartialRepaint(interactionBounds(previous, next));
        }
    }

    private boolean isInteractiveId(String controlId) {
        if (controlId == null) return false;
        for (UiHitRegion candidate : hitRegions) {
            if (controlId.equals(candidate.id())) return true;
        }
        for (UiHitRegion candidate : virtualHitRegions) {
            if (controlId.equals(candidate.id())) return true;
        }
        return false;
    }

    private UiHitRegion regionAt(double x, double y) {
        if (openDialogId == null && openMenuId == null) for (UiHitRegion toast : toastHitRegions) {
            if (focusAllowed(toast.id()) && toast.contains(x, y)) return toast;
        }
        for (int i = virtualHitRegions.size() - 1; i >= 0; i--) {
            UiHitRegion region = virtualHitRegions.get(i);
            if (focusAllowed(region.id()) && region.contains(x, y)) return region;
        }
        for (int i = hitRegions.size() - 1; i >= 0; i--) {
            UiHitRegion region = hitRegions.get(i);
            if (focusAllowed(region.id()) && region.contains(x, y)) return region;
        }
        return null;
    }

    private static boolean isMenuRegion(UiHitRegion region) {
        String action = internalBody(region.action());
        return action.startsWith(MENU_PREFIX) || action.startsWith(MENU_ITEM_PREFIX);
    }

    private static String internalBody(String action) {
        return action != null && action.startsWith(UiContext.INTERNAL_ACTION_PREFIX)
            ? action.substring(UiContext.INTERNAL_ACTION_PREFIX.length()) : "";
    }

    private void closeMenu(boolean clearFocus) {
        if (openMenuId == null) return;
        openMenuId = null;
        menuOpenedWithPointer = false;
        if (clearFocus) focusedId = null;
        requestFullRepaint();
        hoverDirty = true;
    }

    private void closeDialog(boolean cancelled) {
        if (openDialogId == null) return;
        UiDialogRequest request = null;
        for (UiDialogRequest candidate : dialogs) if (openDialogId.equals(candidate.id())) request = candidate;
        if (request == null) for (UiDialogRequest candidate : virtualDialogs) {
            if (openDialogId.equals(candidate.id())) request = candidate;
        }
        openDialogId = null;
        focus(null); pendingFocus = dialogRestore; dialogRestore = null; resolvePendingFocus();
        requestFullRepaint();
        hoverDirty = true;
        if (cancelled && request != null) eventListener.onEvent("action", request.dialog().cancelAction(), "");
    }

    private void dismissToast(String id) {
        if (id == null || id.isBlank()) return;
        if (toasts.removeIf(candidate -> candidate.toast().id().equals(id))) {
            if (TOAST_DISMISS_PREFIX.concat(id).equals(focusedId)) focusedId = null;
            toastSurfaceDirty = true;
            hoverDirty = true;
        }
    }

    private void pruneToasts(long now) {
        if (toasts.removeIf(candidate -> candidate.expiresAt() <= now)) {
            toastSurfaceDirty = true;
            hoverDirty = true;
        }
    }

    private boolean toastAppearanceChanged(long now) {
        long fadeNanos = scaledDuration(document.toastSettings().fadeMillis());
        for (ActiveToast toast : toasts) {
            // Compare with the last painted opacity, not just the current animation window.
            // This paints the final fully opaque frame even if a slow frame skips the boundary.
            if (UiToastTiming.opacity(now, toast.shownAt(), toast.expiresAt(), fadeNanos)
                    != UiToastTiming.opacity(lastToastPaint, toast.shownAt(), toast.expiresAt(), fadeNanos)) return true;
        }
        return false;
    }

    private String tooltipAt(double x, double y) {
        for (int i = virtualTooltips.size() - 1; i >= 0; i--) {
            UiTooltip tooltip = virtualTooltips.get(i);
            if (tooltip.contains(x, y)) return tooltip.id();
        }
        for (int i = tooltips.size() - 1; i >= 0; i--) {
            UiTooltip tooltip = tooltips.get(i);
            if (tooltip.contains(x, y)) return tooltip.id();
        }
        return null;
    }

    private boolean hasTooltip(String id) {
        if (id == null) return false;
        for (UiTooltip tooltip : virtualTooltips) if (id.equals(tooltip.id())) return true;
        for (UiTooltip tooltip : tooltips) if (id.equals(tooltip.id())) return true;
        return false;
    }

    /** Focuses a currently painted, scope-eligible control. Returns false for unknown/blocked ids. */
    public boolean focus(String id) {
        if (id != null && (!focusAllowed(id) || region(id).isEmpty() || id.equals("dialog-backdrop"))) return false;
        pendingFocus = null;
        if (Objects.equals(focusedId, id)) return true;
        String previous = focusedId;
        focusedId = id;
        UiInputRegion input = inputRegion(id);
        String value = input == null ? "" : stringValue(input.key(), "");
        textCursor = value.length();
        textAnchor = textCursor;
        if (id != null && id.startsWith(TOAST_DISMISS_PREFIX)) {
            toastSurfaceDirty = true;
            if (previous != null && !previous.startsWith(TOAST_DISMISS_PREFIX)) {
                if (!requestVirtualRows(previous)) requestPartialRepaint(interactionBounds(previous));
            }
        } else if (!requestVirtualRows(id)) requestFullRepaint();
        return true;
    }

    private boolean moveFocus(boolean reverse) {
        List<UiHitRegion> focusable = new ArrayList<>(hitRegions.size() + virtualHitRegions.size());
        focusable.addAll(hitRegions);
        if (openDialogId == null && openMenuId == null) focusable.addAll(toastHitRegions);
        focusable.addAll(virtualHitRegions);
        focusable.removeIf(hit -> !focusAllowed(hit.id()) || hit.id().equals("dialog-backdrop"));
        if (focusable.isEmpty()) return !focusScopes.isEmpty() || openDialogId != null;
        int current = -1;
        for (int i = 0; i < focusable.size(); i++) {
            if (focusable.get(i).id().equals(focusedId)) {
                current = i;
                break;
            }
        }
        int next = current < 0 ? (reverse ? focusable.size() - 1 : 0)
            : Math.floorMod(current + (reverse ? -1 : 1), focusable.size());
        focus(focusable.get(next).id());
        return true;
    }

    private UiInputRegion inputRegion(String id) {
        if (id == null || !focusAllowed(id) || openDialogId != null) return null;
        for (UiInputRegion input : inputRegions) {
            if (input.id().equals(id)) return input;
        }
        for (UiInputRegion input : virtualInputRegions) {
            if (input.id().equals(id)) return input;
        }
        return null;
    }

    private boolean handleTextKey(UiInputRegion input, KeyEvent event) {
        String value = stringValue(input.key(), "");
        int length = value.length();
        textCursor = UiTextBoundary.floor(value, textCursor);
        textAnchor = UiTextBoundary.floor(value, textAnchor);
        int modifiers = event.modifiers();
        boolean shortcut = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean extend = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (shortcut && (event.key() == GLFW.GLFW_KEY_Z || event.key() == GLFW.GLFW_KEY_Y)) {
            UiTextHistory history = textHistories.get(input.key());
            if (history != null) {
                UiTextHistory.Snapshot current = new UiTextHistory.Snapshot(value, textCursor, textAnchor);
                var restored = event.key() == GLFW.GLFW_KEY_Y || extend ? history.redo(current) : history.undo(current);
                restored.ifPresent(edit -> {
                    state.put(input.key(), edit.value()); textCursor = edit.cursor(); textAnchor = edit.anchor();
                    eventListener.onEvent("value", input.key(), edit.value()); requestFullRepaint();
                });
            }
            return true;
        }
        if (shortcut && event.key() == GLFW.GLFW_KEY_A) {
            textAnchor = 0;
            textCursor = length;
            requestFullRepaint();
            return true;
        }
        if (shortcut && event.key() == GLFW.GLFW_KEY_C) {
            copySelection(value);
            return true;
        }
        if (shortcut && event.key() == GLFW.GLFW_KEY_X) {
            copySelection(value);
            replaceTextSelection(input, "");
            return true;
        }
        if (shortcut && event.key() == GLFW.GLFW_KEY_V) {
            String pasted = Minecraft.getInstance().keyboardHandler.getClipboard();
            replaceTextSelection(input, pasted);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_LEFT) {
            moveTextCursor(UiTextBoundary.previous(value, textCursor), extend);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT) {
            moveTextCursor(UiTextBoundary.next(value, textCursor), extend);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_HOME) {
            moveTextCursor(0, extend);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_END) {
            moveTextCursor(length, extend);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (textCursor != textAnchor) replaceTextSelection(input, "");
            else if (textCursor > 0) {
                UiTextHistory.Snapshot before = new UiTextHistory.Snapshot(value, textCursor, textAnchor);
                textAnchor = UiTextBoundary.previous(value, textCursor);
                replaceTextSelection(input, "", before);
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE) {
            if (textCursor != textAnchor) replaceTextSelection(input, "");
            else if (textCursor < length) {
                UiTextHistory.Snapshot before = new UiTextHistory.Snapshot(value, textCursor, textAnchor);
                textAnchor = UiTextBoundary.next(value, textCursor);
                replaceTextSelection(input, "", before);
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (input.multiline()) {
                replaceTextSelection(input, "\n");
                return true;
            }
            eventListener.onEvent("submit", input.key(), value);
            return true;
        }
        return false;
    }

    private void moveTextCursor(int next, boolean extend) {
        textCursor = next;
        if (!extend) textAnchor = next;
        requestFullRepaint();
    }

    private void copySelection(String value) {
        int start = Math.min(textCursor, textAnchor);
        int end = Math.max(textCursor, textAnchor);
        if (start != end) Minecraft.getInstance().keyboardHandler.setClipboard(value.substring(start, end));
    }

    private void replaceTextSelection(UiInputRegion input, String replacement) {
        replaceTextSelection(input, replacement, new UiTextHistory.Snapshot(stringValue(input.key(), ""), textCursor, textAnchor));
    }

    private void replaceTextSelection(UiInputRegion input, String replacement, UiTextHistory.Snapshot before) {
        String value = stringValue(input.key(), "");
        int start = Math.min(UiTextBoundary.floor(value, textCursor), UiTextBoundary.floor(value, textAnchor));
        int end = Math.max(UiTextBoundary.floor(value, textCursor), UiTextBoundary.floor(value, textAnchor));
        String safe = input.multiline() ? cleanMultiline(replacement) : cleanSingleLine(replacement);
        int available = Math.max(0, input.maxLength() - (value.length() - (end - start)));
        if (safe.length() > available) safe = UiTextBoundary.truncate(safe, available);
        String next = value.substring(0, start) + safe + value.substring(end);
        textCursor = start + safe.length();
        textAnchor = textCursor;
        textHistories.computeIfAbsent(input.key(), ignored -> new UiTextHistory())
            .record(before, new UiTextHistory.Snapshot(next, textCursor, textAnchor));
        state.put(input.key(), next);
        eventListener.onEvent("value", input.key(), next);
        requestFullRepaint();
    }

    private static String cleanSingleLine(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replaceAll("[\\r\\n\\t]+", " ");
    }

    private static String cleanMultiline(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
    }

    private static int clampIndex(int index, int length) { return Math.max(0, Math.min(length, index)); }

    private UiScrollRegion scrollAt(double x, double y) {
        for (int i = scrollRegions.size() - 1; i >= 0; i--) {
            UiScrollRegion region = scrollRegions.get(i);
            if (focusAllowed(region.id()) && region.contains(x, y)) return region;
        }
        return null;
    }

    private UiScrollRegion scrollRegion(String id) {
        for (UiScrollRegion region : scrollRegions) if (region.id().equals(id)) return region;
        return null;
    }

    private void updateScrollMotions(long now) {
        var iterator = scrollMotions.entrySet().iterator();
        boolean settledVirtualScroll = false;
        while (iterator.hasNext()) {
            Map.Entry<String, UiScrollPhysics.Motion> entry = iterator.next();
            UiScrollRegion region = scrollRegion(entry.getKey());
            if (region == null) {
                iterator.remove();
                continue;
            }
            UiScrollPhysics.Frame frame = UiScrollPhysics.advance(
                numberValue("scroll:" + entry.getKey(), 0), entry.getValue(), now);
            state.put("scroll:" + entry.getKey(), frame.displayed());
            VirtualListLayer layer = virtualCompositeActive ? virtualLayers.get(entry.getKey()) : null;
            if (layer == null) requestPartialRepaint(region.viewport());
            else {
                layer.currentOffset = frame.displayed();
                gpuScrollFrameCount++;
                refreshVirtualInteractions();
            }
            if (frame.settled()) {
                iterator.remove();
                hoverDirty = true;
                settledVirtualScroll |= layer != null;
            } else entry.setValue(frame.motion());
        }
        if (settledVirtualScroll) updateHover(mouseX * layoutScale, mouseY * layoutScale, true);
    }

    private static UiRect union(UiRect first, UiRect second) {
        float x = Math.min(first.x(), second.x());
        float y = Math.min(first.y(), second.y());
        float right = Math.max(first.right(), second.right());
        float bottom = Math.max(first.bottom(), second.bottom());
        return new UiRect(x, y, right - x, bottom - y);
    }

    private void startTransition(int milliseconds) {
        long duration = scaledDuration(milliseconds);
        requestFullRepaint();
        if (duration <= 0) {
            transitionEnd = 0;
            return;
        }
        transitionStart = System.nanoTime();
        transitionEnd = transitionStart + duration;
    }

    private void startInteraction(int milliseconds, String... controlIds) {
        boolean toastOnly = controlIds != null && controlIds.length > 0;
        boolean hasToast = false;
        if (toastOnly) for (String id : controlIds) {
            if (id == null) continue;
            if (!id.startsWith(TOAST_DISMISS_PREFIX)) toastOnly = false;
            else hasToast = true;
        }
        if (toastOnly && hasToast) { toastSurfaceDirty = true; return; }
        long duration = scaledDuration(milliseconds);
        boolean virtualOnly = controlIds != null && controlIds.length > 0;
        if (virtualOnly) {
            for (String id : controlIds) {
                if (id != null && virtualRow(id) == null) { virtualOnly = false; break; }
            }
        }
        if (virtualOnly) {
            interactionRepaintBounds = null;
            requestVirtualRows(controlIds);
        } else {
            interactionRepaintBounds = interactionBounds(controlIds);
            requestInteractionRepaint();
        }
        if (duration <= 0) {
            interactionEnd = 0;
            return;
        }
        interactionStart = System.nanoTime();
        interactionEnd = interactionStart + duration;
    }

    private void requestInteractionRepaint() {
        if (interactionRepaintBounds == null) requestFullRepaint();
        else requestPartialRepaint(interactionRepaintBounds);
    }

    /**
     * Hover and press animations only change their old and new controls. Keep a conservative
     * margin for the normal two-pixel lift, focus ring, and card shadow without redrawing the
     * screen's retained surface.
     */
    private UiRect interactionBounds(String... controlIds) {
        UiRect result = null;
        if (controlIds == null) return null;
        for (String id : controlIds) {
            if (id == null) continue;
            boolean found = false;
            for (UiHitRegion region : hitRegions) {
                if (!id.equals(region.id())) continue;
                UiRect bounds = new UiRect(region.x(), region.y(), region.width(), region.height());
                result = result == null ? bounds : union(result, bounds);
                found = true;
                break;
            }
            if (!found) {
                for (UiTooltip tooltip : tooltips) {
                    if (!id.equals(tooltip.id())) continue;
                    UiRect bounds = tooltip.anchor();
                    result = result == null ? bounds : union(result, bounds);
                    found = true;
                    break;
                }
            }
            if (!found) {
                for (UiTooltip tooltip : virtualTooltips) {
                    if (!id.equals(tooltip.id())) continue;
                    UiRect bounds = tooltip.anchor();
                    result = result == null ? bounds : union(result, bounds);
                    break;
                }
            }
        }
        return result == null ? null : expandAndClamp(result, surfaceLayoutWidth, surfaceLayoutHeight, 20);
    }

    private void startInteraction(int milliseconds) {
        startInteraction(milliseconds, hoveredId, pressedId);
    }

    private long scaledDuration(int milliseconds) {
        float scale = motionScale();
        if (scale <= 0 || milliseconds <= 0) return 0;
        return (long) (milliseconds * scale * 1_000_000L);
    }

    private static float ease(long now, long start, long end) {
        if (end == 0 || end <= start) return 1;
        float linear = Math.max(0, Math.min(1, (float) (now - start) / (end - start)));
        return 1 - (float) Math.pow(1 - linear, 3);
    }

    private void rebuildSurface(long now) {
        long paintStarted = System.nanoTime();
        UiRect requestedPartial = partialDirtyRegion;
        dirty = false;
        partialDirtyRegion = null;
        lastPaint = now;
        UiPage page = document.page(pageId);
        if (width <= 0 || height <= 0 || page == null) return;

        float scale = computeLayoutScale();
        layoutScale = scale;
        int layoutWidth = Math.max(1, Math.round(width * scale));
        int layoutHeight = Math.max(1, Math.round(height * scale));
        float qualityScale = computeQualityScale(layoutWidth, layoutHeight);
        surfaceQualityScale = qualityScale;
        surfaceLayoutWidth = layoutWidth;
        surfaceLayoutHeight = layoutHeight;
        int pixelWidth = Math.max(1, Math.round(layoutWidth * qualityScale));
        int pixelHeight = Math.max(1, Math.round(layoutHeight * qualityScale));
        boolean partial = requestedPartial != null && surface != null
            && surfaceWidth == pixelWidth && surfaceHeight == pixelHeight;
        if (!ensureSurface(pixelWidth, pixelHeight)) return;

        UiRect repaintRegion = partial ? expandAndClamp(requestedPartial, layoutWidth, layoutHeight, 2) : null;
        PixelRect pixelRegion = partial ? toPixelRect(repaintRegion, qualityScale, pixelWidth, pixelHeight) : null;
        lastDirtyPixels = partial ? pixelRegion.width() * pixelRegion.height() : pixelWidth * pixelHeight;
        if (partial) clearPixels(pixelRegion);
        else Arrays.fill(surfacePixels, 0);
        Graphics2D graphics = surfaceImage.createGraphics();
        try {
            graphics.scale(qualityScale, qualityScale);
            if (partial) {
                // Clear and paint must share one exact physical boundary. Rounding the layout clip
                // independently from floor/ceil pixel clearing left a one-pixel transparent right
                // and bottom edge at non-unit surface scales, visible as a long scissor line.
                graphics.clip(new java.awt.geom.Rectangle2D.Float(pixelRegion.x() / qualityScale,
                    pixelRegion.y() / qualityScale, pixelRegion.width() / qualityScale,
                    pixelRegion.height() / qualityScale));
            }
            UiCanvas canvas = new UiCanvas(graphics, surfaceImage);
            List<UiHitRegion> nextHits = new ArrayList<>();
            Map<String, NativeBounds> nextNativeBounds = new LinkedHashMap<>();
            Map<String, UiRect> nextNativeOverlayLayers = new LinkedHashMap<>();
            Map<String, UiRect> nextAnimationBounds = new HashMap<>();
            List<UiScrollRegion> nextScrollRegions = new ArrayList<>();
            List<UiInputRegion> nextInputRegions = new ArrayList<>();
            List<UiTooltip> nextTooltips = new ArrayList<>();
            List<UiMenu> nextMenus = new ArrayList<>();
            List<UiDialogRequest> nextDialogs = new ArrayList<>();
            List<UiVirtualListRequest> nextVirtualLists = new ArrayList<>();
            UiAnimationClock animationClock = new UiAnimationClock(animations, now, motionScale());
            boolean compositeVirtualLists = canCompositeVirtualLists();
            UiContext shellContext = new UiContext(canvas, document.theme(), layoutWidth, layoutHeight,
                page.accent(), new UiRect(0, 0, layoutWidth, layoutHeight), hoveredId, pressedId,
                ease(now, transitionStart, transitionEnd), ease(now, interactionStart, interactionEnd),
                transitionEnd != 0 || interactionEnd != 0, paintCount, state, nextHits, nextNativeBounds, nextNativeOverlayLayers,
                nextAnimationBounds, nextScrollRegions, nextInputRegions, nextTooltips,
                new UiTextInputState(focusedId, textCursor, textAnchor),
                nextMenus,
                nextDialogs,
                animationClock,
                guiScale() / (double) scale,
                compositeVirtualLists ? nextVirtualLists : null);

            UiRect content = document.shell().paint(shellContext, document, page);
            if (content == null) content = new UiRect(0, 0, layoutWidth, layoutHeight);
            UiContext pageContext = shellContext.withContent(content);
            UiRect clip = content;
            canvas.withClip(clip.x(), clip.y(), clip.width(), clip.height(),
                () -> page.painter().paint(pageContext));

            if (compositeVirtualLists && !nextVirtualLists.isEmpty()) {
                reconcileVirtualLayers(nextVirtualLists, qualityScale, now, animationClock);
                virtualCompositeActive = true;
            } else {
                virtualCompositeActive = false;
                clearVirtualInteractions();
            }

            toastSurfaceDirty = true;
            drawMenu(canvas, nextMenus, nextHits, layoutWidth, layoutHeight);
            drawDialog(canvas, nextDialogs, nextHits, layoutWidth, layoutHeight);

            hitRegions = List.copyOf(nextHits);
            scrollRegions = List.copyOf(nextScrollRegions);
            inputRegions = List.copyOf(nextInputRegions);
            tooltips = List.copyOf(nextTooltips);
            tooltipSurfaceDirty = true;
            menus = List.copyOf(nextMenus);
            dialogs = List.copyOf(nextDialogs);
            resolvePendingFocus();
            pendingFocus = null;
            nativeBounds.clear();
            nativeBounds.putAll(nextNativeBounds);
            nativeOverlayLayers.clear();
            nativeOverlayLayers.putAll(nextNativeOverlayLayers);
            customAnimationsActive = animationClock.active();
            activeCustomAnimationIds = animationClock.activeIds();
            customAnimationBounds = Map.copyOf(nextAnimationBounds);
        } finally {
            graphics.dispose();
        }
        lastRasterNanos = System.nanoTime() - paintStarted;
        long uploadStarted = System.nanoTime();
        if (partial) upload(pixelRegion);
        else upload();
        lastUploadNanos = System.nanoTime() - uploadStarted;
        paintCount++;
        if (partial) partialPaintCount++;
        lastPaintDurationNanos = System.nanoTime() - paintStarted;
        if (firstPaintNanos < 0) firstPaintNanos = System.nanoTime();

        // Hit regions may have moved under a stationary cursor (page switch, resize, state change).
        // Resolve hover once against the new layout; the guard keeps this from oscillating.
        if (hoverDirty) {
            hoverDirty = false;
            updateHover(mouseX * scale, mouseY * scale, false);
        }
    }

    private float eventLayoutScale() {
        float scale = computeLayoutScale();
        return scale > 0 ? scale : layoutScale;
    }

    private static UiRect expandAndClamp(UiRect region, int width, int height, float padding) {
        float x = Math.max(0, region.x() - padding);
        float y = Math.max(0, region.y() - padding);
        float right = Math.min(width, region.right() + padding);
        float bottom = Math.min(height, region.bottom() + padding);
        return new UiRect(x, y, right - x, bottom - y);
    }

    private static PixelRect toPixelRect(UiRect region, float scale, int width, int height) {
        int x = Math.max(0, Math.min(width, (int) Math.floor(region.x() * scale)));
        int y = Math.max(0, Math.min(height, (int) Math.floor(region.y() * scale)));
        int right = Math.max(x, Math.min(width, (int) Math.ceil(region.right() * scale)));
        int bottom = Math.max(y, Math.min(height, (int) Math.ceil(region.bottom() * scale)));
        return new PixelRect(x, y, right - x, bottom - y);
    }

    private void clearPixels(PixelRect region) {
        for (int y = region.y(); y < region.bottom(); y++) {
            int start = y * surfaceWidth + region.x();
            Arrays.fill(surfacePixels, start, start + region.width(), 0);
        }
    }

    private void drawToasts(UiCanvas canvas, List<UiHitRegion> hits, int layoutWidth, int layoutHeight,
                            long now) {
        pruneToasts(now);
        toastBounds.clear();
        if (toasts.isEmpty()) return;
        UiToastSettings settings = document.toastSettings();
        Map<UiToastPlacement, List<ActiveToast>> stacks = new EnumMap<>(UiToastPlacement.class);
        for (ActiveToast active : toasts) {
            UiToastPlacement placement = active.toast().placement();
            if (placement == null) placement = settings.placement();
            stacks.computeIfAbsent(placement, ignored -> new ArrayList<>()).add(active);
        }
        UiRect protectedControl = protectedToastControl(hits);
        for (Map.Entry<UiToastPlacement, List<ActiveToast>> entry : stacks.entrySet()) {
            drawToastStack(canvas, hits, entry.getValue(), entry.getKey(), protectedControl,
                layoutWidth, layoutHeight, settings, now);
        }
    }

    private void drawToastStack(UiCanvas canvas, List<UiHitRegion> hits, List<ActiveToast> stack,
                                UiToastPlacement requestedPlacement, UiRect protectedControl,
                                int layoutWidth, int layoutHeight, UiToastSettings settings, long now) {
        float width = Math.min(286, Math.max(170, layoutWidth - settings.margin() * 2));
        float height = 54, blockHeight = stack.size() * height + (stack.size() - 1) * settings.gap();
        UiToastPlacement placement = requestedPlacement == UiToastPlacement.AUTO
            ? autoToastPlacement(width, blockHeight, protectedControl, layoutWidth, layoutHeight, settings)
            : requestedPlacement;
        boolean left = placement == UiToastPlacement.TOP_LEFT || placement == UiToastPlacement.BOTTOM_LEFT;
        boolean top = placement == UiToastPlacement.TOP_LEFT || placement == UiToastPlacement.TOP_RIGHT;
        float x = left ? settings.margin() : layoutWidth - settings.margin() - width;
        float y = top ? settings.margin() : layoutHeight - settings.margin() - blockHeight;
        for (ActiveToast active : stack) {
            UiToast toast = active.toast();
            float opacity = UiToastTiming.opacity(now, active.shownAt(), active.expiresAt(),
                scaledDuration(settings.fadeMillis()));
            float toastY = y;
            toastBounds.put(toast.id(), new UiRect(x, toastY, width, height));
            UiToastStyle style = toast.style() == null ? settings.defaultStyle() : toast.style();
            int background = toastColor(style == null ? null : style.background(), document.theme().raisedSurface());
            int border = toastColor(style == null ? null : style.border(), document.theme().border());
            int title = toastColor(style == null ? null : style.titleColor(), document.theme().text());
            int message = toastColor(style == null ? null : style.messageColor(), document.theme().mutedText());
            int iconBackground = toastColor(style == null ? null : style.iconBackground(), UiCanvas.fade(document.theme().accent(), .22F));
            int iconColor = toastColor(style == null ? null : style.iconColor(), document.theme().accent());
            int closeBackground = toastColor(style == null ? null : style.closeBackground(), UiCanvas.fade(document.theme().border(), .6F));
            int closeColor = toastColor(style == null ? null : style.closeColor(), document.theme().text());
            float radius = style == null || style.radius() == null ? 10 : style.radius();
            canvas.withOpacity(opacity, () -> {
                canvas.shadow(0x8A000000, 8, 0, 3, x, toastY, width, height, 10);
                canvas.roundRect(background, x, toastY, width, height, radius);
                canvas.strokeRoundRect(border, .8F, x, toastY, width, height, radius);
                float textX = x + 13;
                if (!toast.icon().isBlank()) {
                    canvas.circle(iconBackground, x + 23, toastY + height / 2, 11);
                    canvas.centeredText(canvas.ellipsize(toast.icon(), 15, 8, java.awt.Font.BOLD, document.theme().fontFamily()),
                        x + 23, toastY + height / 2, 8, iconColor, java.awt.Font.BOLD, document.theme().fontFamily());
                    textX += 28;
                }
                float textWidth = width - (textX - x) - (toast.dismissible() ? 32 : 13);
                canvas.text(canvas.ellipsize(toast.title(), textWidth, 9, java.awt.Font.BOLD, document.theme().fontFamily()),
                    textX, toastY + 9, 9, title, java.awt.Font.BOLD, document.theme().fontFamily());
                if (!toast.message().isBlank()) {
                    canvas.text(canvas.ellipsize(toast.message(), textWidth, 8, java.awt.Font.PLAIN, document.theme().fontFamily()),
                        textX, toastY + 27, 8, message, java.awt.Font.PLAIN, document.theme().fontFamily());
                }
                if (toast.dismissible()) {
                    float closeX = x + width - 26, closeY = toastY + 11;
                    canvas.roundRect(closeBackground, closeX, closeY, 16, 16, 5);
                    canvas.centeredText("×", closeX + 8, closeY + 8, 10, closeColor, java.awt.Font.PLAIN);
                    hits.add(new UiHitRegion(TOAST_DISMISS_PREFIX + toast.id(), UiContext.internalAction(TOAST_DISMISS_PREFIX + toast.id()),
                        closeX, closeY, 16, 16));
                }
            });
            y += height + settings.gap();
        }
    }

    private static int toastColor(Integer override, int fallback) { return override == null ? fallback : override; }

    private static UiToastPlacement autoToastPlacement(float width, float height, UiRect protectedControl,
                                                        int layoutWidth, int layoutHeight, UiToastSettings settings) {
        UiToastPlacement[] candidates = {UiToastPlacement.BOTTOM_RIGHT, UiToastPlacement.TOP_RIGHT,
            UiToastPlacement.BOTTOM_LEFT, UiToastPlacement.TOP_LEFT};
        for (UiToastPlacement candidate : candidates) {
            boolean left = candidate == UiToastPlacement.TOP_LEFT || candidate == UiToastPlacement.BOTTOM_LEFT;
            boolean top = candidate == UiToastPlacement.TOP_LEFT || candidate == UiToastPlacement.TOP_RIGHT;
            float x = left ? settings.margin() : layoutWidth - settings.margin() - width;
            float y = top ? settings.margin() : layoutHeight - settings.margin() - height;
            if (protectedControl == null || !intersects(new UiRect(x, y, width, height), protectedControl)) return candidate;
        }
        return UiToastPlacement.BOTTOM_RIGHT;
    }

    private UiRect protectedToastControl(List<UiHitRegion> candidates) {
        String id = pressedId != null ? pressedId : focusedId;
        if (id == null) return null;
        for (UiHitRegion candidate : candidates) {
            if (id.equals(candidate.id())) return new UiRect(candidate.x(), candidate.y(), candidate.width(), candidate.height());
        }
        return null;
    }

    private static boolean intersects(UiRect first, UiRect second) {
        return first.x() < second.right() && first.right() > second.x()
            && first.y() < second.bottom() && first.bottom() > second.y();
    }

    private void drawDialog(UiCanvas canvas, List<UiDialogRequest> candidates, List<UiHitRegion> hits,
                            int layoutWidth, int layoutHeight) {
        if (openDialogId == null) return;
        UiDialogRequest request = null;
        for (UiDialogRequest candidate : candidates) if (openDialogId.equals(candidate.id())) request = candidate;
        if (request == null) { openDialogId = null; return; }
        UiDialog dialog = request.dialog();
        UiDialogStyle style = dialog.style();
        int backdrop = toastColor(style == null ? null : style.backdrop(), 0x99000000);
        int shadow = toastColor(style == null ? null : style.shadow(), 0x88000000);
        int surface = toastColor(style == null ? null : style.surface(), document.theme().raisedSurface());
        int border = toastColor(style == null ? null : style.border(), document.theme().border());
        int title = toastColor(style == null ? null : style.titleColor(), document.theme().text());
        int message = toastColor(style == null ? null : style.messageColor(), document.theme().mutedText());
        int cancelBackground = toastColor(style == null ? null : style.cancelBackground(), document.theme().surface());
        int cancelBorder = toastColor(style == null ? null : style.cancelBorder(), document.theme().border());
        int cancelText = toastColor(style == null ? null : style.cancelText(), document.theme().text());
        int confirmBackground = toastColor(style == null ? null : style.confirmBackground(), document.theme().accent());
        int confirmText = toastColor(style == null ? null : style.confirmText(), document.theme().accentText());
        float radius = style == null || style.radius() == null ? 12 : style.radius();
        canvas.backdropBlur(2, 0, 0, layoutWidth, layoutHeight);
        canvas.fill(backdrop, 0, 0, layoutWidth, layoutHeight);
        hits.add(new UiHitRegion("dialog-backdrop", UiContext.internalAction(DIALOG_CANCEL_PREFIX + dialog.cancelAction()),
            0, 0, layoutWidth, layoutHeight));
        float width = Math.min(330, layoutWidth - 24), height = 150;
        float x = (layoutWidth - width) / 2, y = (layoutHeight - height) / 2;
        canvas.shadow(shadow, 12, 0, 4, x, y, width, height, radius);
        canvas.roundRect(surface, x, y, width, height, radius);
        canvas.strokeRoundRect(border, .9F, x, y, width, height, radius);
        canvas.text(dialog.title(), x + 16, y + 17, 12, title, java.awt.Font.BOLD, document.theme().fontFamily());
        canvas.text(dialog.message(), x + 16, y + 45, 8, message, java.awt.Font.PLAIN, document.theme().fontFamily());
        float cancelW = Math.max(70, canvas.textWidth(dialog.cancelLabel(), 8, java.awt.Font.BOLD, document.theme().fontFamily()) + 22);
        float confirmW = Math.max(76, canvas.textWidth(dialog.confirmLabel(), 8, java.awt.Font.BOLD, document.theme().fontFamily()) + 22);
        float buttonsY = y + height - 42, confirmX = x + width - 16 - confirmW, cancelX = confirmX - 7 - cancelW;
        canvas.roundRect(cancelBackground, cancelX, buttonsY, cancelW, 28, Math.min(8, radius));
        canvas.strokeRoundRect(cancelBorder, .8F, cancelX, buttonsY, cancelW, 28, Math.min(8, radius));
        canvas.centeredText(dialog.cancelLabel(), cancelX + cancelW / 2, buttonsY + 14, 8, cancelText, java.awt.Font.BOLD, document.theme().fontFamily());
        canvas.roundRect(confirmBackground, confirmX, buttonsY, confirmW, 28, Math.min(8, radius));
        canvas.centeredText(dialog.confirmLabel(), confirmX + confirmW / 2, buttonsY + 14, 8, confirmText, java.awt.Font.BOLD, document.theme().fontFamily());
        hits.add(new UiHitRegion("dialog-cancel", UiContext.internalAction(DIALOG_CANCEL_PREFIX + dialog.cancelAction()),
            cancelX, buttonsY, cancelW, 28));
        hits.add(new UiHitRegion("dialog-confirm", UiContext.internalAction(DIALOG_CONFIRM_PREFIX + dialog.confirmAction()),
            confirmX, buttonsY, confirmW, 28));
    }

    private void drawMenu(UiCanvas canvas, List<UiMenu> candidates, List<UiHitRegion> hits,
                          int layoutWidth, int layoutHeight) {
        if (openMenuId == null) return;
        UiMenu menu = null;
        for (int i = candidates.size() - 1; i >= 0; i--) {
            if (openMenuId.equals(candidates.get(i).id())) {
                menu = candidates.get(i);
                break;
            }
        }
        if (menu == null || menu.items().isEmpty()) {
            openMenuId = null;
            menuOpenedWithPointer = false;
            return;
        }
        float rowHeight = 27, pad = 5;
        float widest = menu.anchor().width();
        for (UiMenuItem item : menu.items()) {
            widest = Math.max(widest, canvas.textWidth(item.label(), 8, java.awt.Font.PLAIN, document.theme().fontFamily()) + 24);
        }
        float width = Math.min(widest, Math.max(64, layoutWidth - 12));
        float height = menu.items().size() * rowHeight + pad * 2;
        float x = Math.max(6, Math.min(menu.anchor().x(), layoutWidth - width - 6));
        float below = menu.anchor().bottom() + 5;
        float above = menu.anchor().y() - height - 5;
        float y = below + height <= layoutHeight - 6 ? below : Math.max(6, above);
        canvas.shadow(0x72000000, 7, 0, 3, x, y, width, height, 8);
        canvas.roundRect(document.theme().raisedSurface(), x, y, width, height, 8);
        canvas.strokeRoundRect(document.theme().border(), .8F, x, y, width, height, 8);
        for (int i = 0; i < menu.items().size(); i++) {
            UiMenuItem item = menu.items().get(i);
            float rowY = y + pad + i * rowHeight;
            String itemId = "menu:" + menu.id() + ":" + item.id();
            if (itemId.equals(hoveredId)) canvas.roundRect(UiCanvas.fade(document.theme().accent(), .18F),
                x + 3, rowY, width - 6, rowHeight, 5);
            canvas.text(item.label(), x + 12, rowY + 8, 8, document.theme().text(), java.awt.Font.PLAIN, document.theme().fontFamily());
            hits.add(new UiHitRegion(itemId, UiContext.internalAction(MENU_ITEM_PREFIX + item.action()),
                x, rowY, width, rowHeight));
        }
    }

    private void renderToastOverlay(GuiGraphicsExtractor extractor, long now) {
        if (toastSurfaceDirty) {
            rebuildToastSurface(now);
            // Expiry removes the top hit target even when page chrome stays cached.
            if (hoveredId != null && hoveredId.startsWith(TOAST_DISMISS_PREFIX)
                    && !hasToastHit(hoveredId)) {
                updateHover(mouseX * layoutScale, mouseY * layoutScale, true);
            }
        }
        if (toasts.isEmpty() || toastSurface == null || toastLayoutBounds == null
                || openDialogId != null || openMenuId != null) return;
        extractor.nextStratum();
        int x = Math.round(toastLayoutBounds.x() / layoutScale);
        int y = Math.round(toastLayoutBounds.y() / layoutScale);
        int right = Math.round(toastLayoutBounds.right() / layoutScale);
        int bottom = Math.round(toastLayoutBounds.bottom() / layoutScale);
        extractor.blit(toastSurface.getTextureView(), surfaceSampler, x, y, right, bottom, 0, 1, 0, 1);
    }

    private boolean hasToastHit(String id) {
        for (UiHitRegion hit : toastHitRegions) if (hit.id().equals(id)) return true;
        return false;
    }

    private void rebuildToastSurface(long now) {
        toastSurfaceDirty = false;
        lastToastPaint = now;
        toastHitRegions = List.of();
        if (toasts.isEmpty() || surfaceImage == null) { toastLayoutBounds = null; return; }
        var hits = new ArrayList<>(hitRegions);
        hits.addAll(virtualHitRegions);
        // Layout uses text metrics and protected-control bounds, not a full-page paint.
        Graphics2D measure = surfaceImage.createGraphics();
        try {
            measure.setClip(0, 0, 0, 0);
            drawToasts(new UiCanvas(measure), hits, surfaceLayoutWidth, surfaceLayoutHeight, now);
        } finally { measure.dispose(); }
        UiRect union = null;
        for (UiRect rect : toastBounds.values()) union = union == null ? rect : union(union, rect);
        if (union == null) return;
        UiRect bounds = expandAndClamp(union, surfaceLayoutWidth, surfaceLayoutHeight, 14);
        int w = Math.max(1, (int) Math.ceil(bounds.width() * surfaceQualityScale));
        int h = Math.max(1, (int) Math.ceil(bounds.height() * surfaceQualityScale));
        if (toastImage == null || toastImage.getWidth() != w || toastImage.getHeight() != h) {
            closeToastSurface();
            toastSurface = new DynamicTexture(() -> "pane-toast-overlay", w, h, true);
            retainedTextureAllocationCount++;
            toastImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            toastPixels = ((DataBufferInt) toastImage.getRaster().getDataBuffer()).getData();
        } else Arrays.fill(toastPixels, 0);
        hits = new ArrayList<>(hitRegions);
        hits.addAll(virtualHitRegions);
        int baseHits = hits.size();
        Graphics2D painter = toastImage.createGraphics();
        try {
            painter.scale(surfaceQualityScale, surfaceQualityScale);
            painter.translate(-bounds.x(), -bounds.y());
            drawToasts(new UiCanvas(painter, toastImage), hits, surfaceLayoutWidth, surfaceLayoutHeight, now);
        } finally { painter.dispose(); }
        toastHitRegions = List.copyOf(hits.subList(baseHits, hits.size()));
        var target = toastSurface.getPixels().getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer();
        UiPixelTransfer.copy(toastPixels, target, w, 0, 0, w, h);
        toastSurface.upload();
        toastUploadPixels += (long) w * h;
        toastLayoutBounds = new UiRect(bounds.x(), bounds.y(), w / surfaceQualityScale, h / surfaceQualityScale);
        toastSurfaceDirty = false;
    }

    private void closeToastSurface() {
        if (toastSurface != null) toastSurface.close();
        toastSurface = null; toastImage = null; toastPixels = null; toastLayoutBounds = null;
        toastHitRegions = List.of(); toastSurfaceDirty = true;
    }

    /**
     * Tooltips live in a small independent texture. Baking one into the full page used to switch
     * virtual rows out of their atlas and back through the page rasterizer, whose pixel rounding
     * differed by one or two pixels and made every line of text appear to jump.
     */
    private void renderTooltipOverlay(GuiGraphicsExtractor graphics) {
        UiTooltip tooltip = hoveredTooltip();
        if (tooltipRevealAt > 0 || tooltip == null || surfaceLayoutWidth <= 0 || surfaceLayoutHeight <= 0) {
            visibleTooltipId = null;
            return;
        }
        if (tooltipSurfaceDirty || tooltipSurface == null) rebuildTooltipSurface(tooltip);
        if (tooltipSurface == null || tooltipSampler == null || tooltipLayoutBounds == null) {
            visibleTooltipId = null;
            return;
        }
        int x0 = Math.round(tooltipLayoutBounds.x() / layoutScale);
        int y0 = Math.round(tooltipLayoutBounds.y() / layoutScale);
        int x1 = Math.round(tooltipLayoutBounds.right() / layoutScale);
        int y1 = Math.round(tooltipLayoutBounds.bottom() / layoutScale);
        graphics.nextStratum();
        graphics.blit(tooltipSurface.getTextureView(), tooltipSampler,
            x0, y0, x1, y1, 0, 1, 0, 1);
        visibleTooltipId = tooltip.id();
    }

    private UiTooltip hoveredTooltip() {
        if (hoveredId == null) return null;
        for (int i = virtualTooltips.size() - 1; i >= 0; i--) {
            UiTooltip tooltip = virtualTooltips.get(i);
            if (hoveredId.equals(tooltip.id())) return tooltip;
        }
        for (int i = tooltips.size() - 1; i >= 0; i--) {
            UiTooltip tooltip = tooltips.get(i);
            if (hoveredId.equals(tooltip.id())) return tooltip;
        }
        return null;
    }

    private void rebuildTooltipSurface(UiTooltip tooltip) {
        final float outerPad = 7, padX = 9, height = 23;
        // Do not leave stale bounds drawable if measuring or allocating the replacement fails.
        tooltipLayoutBounds = null;
        float width;
        Graphics2D measure = surfaceImage == null ? null : surfaceImage.createGraphics();
        if (measure == null) return;
        try {
            measure.scale(surfaceQualityScale, surfaceQualityScale);
            UiCanvas canvas = new UiCanvas(measure, surfaceImage);
            width = Math.min(Math.max(54, canvas.textWidth(tooltip.text(), 8,
                java.awt.Font.PLAIN, document.theme().fontFamily()) + padX * 2),
                Math.max(54, surfaceLayoutWidth - 12));
        } finally {
            measure.dispose();
        }
        UiRect anchor = tooltip.anchor();
        float x = Math.max(6, Math.min(anchor.x() + anchor.width() / 2 - width / 2,
            surfaceLayoutWidth - width - 6));
        float below = anchor.bottom() + 7;
        float above = anchor.y() - height - 7;
        float y = below + height <= surfaceLayoutHeight - 6 ? below : Math.max(6, above);
        float textureWidth = width + outerPad * 2;
        float textureHeight = height + outerPad * 2;
        int pixelWidth = Math.max(1, Math.round(textureWidth * surfaceQualityScale));
        int pixelHeight = Math.max(1, Math.round(textureHeight * surfaceQualityScale));
        ensureTooltipSurface(pixelWidth, pixelHeight);
        if (tooltipSurface == null) return;
        Arrays.fill(tooltipPixels, 0);
        Graphics2D painter = tooltipImage.createGraphics();
        try {
            painter.scale(surfaceQualityScale, surfaceQualityScale);
            UiCanvas canvas = new UiCanvas(painter, tooltipImage);
            canvas.shadow(0x72000000, 5, 0, 2, outerPad, outerPad, width, height, 6);
            canvas.roundRect(0xFF111419, outerPad, outerPad, width, height, 6);
            canvas.strokeRoundRect(document.theme().border(), .8F,
                outerPad, outerPad, width, height, 6);
            canvas.centeredText(tooltip.text(), outerPad + width / 2, outerPad + height / 2,
                8, document.theme().text(), java.awt.Font.PLAIN, document.theme().fontFamily());
        } finally {
            painter.dispose();
        }
        NativeImage nativeImage = tooltipSurface.getPixels();
        var target = nativeImage.getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer();
        for (int i = 0; i < tooltipPixels.length; i++) {
            int argb = tooltipPixels[i];
            target.put(i, argb & 0xFF00FF00 | argb >>> 16 & 0xFF | (argb & 0xFF) << 16);
        }
        tooltipSurface.upload();
        tooltipLayoutBounds = new UiRect(x - outerPad, y - outerPad, textureWidth, textureHeight);
        tooltipSurfaceDirty = false;
    }

    private void ensureTooltipSurface(int pixelWidth, int pixelHeight) {
        if (tooltipSurface != null && tooltipPixelWidth == pixelWidth
            && tooltipPixelHeight == pixelHeight) return;
        closeTooltipSurface();
        tooltipSurface = new DynamicTexture(() -> "pane-tooltip-surface",
            pixelWidth, pixelHeight, true);
        retainedTextureAllocationCount++;
        tooltipSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        tooltipImage = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
        tooltipPixels = ((DataBufferInt) tooltipImage.getRaster().getDataBuffer()).getData();
        tooltipPixelWidth = pixelWidth;
        tooltipPixelHeight = pixelHeight;
    }

    private void closeTooltipSurface() {
        if (tooltipSurface != null) tooltipSurface.close();
        tooltipSurface = null;
        tooltipSampler = null;
        tooltipImage = null;
        tooltipPixels = null;
        tooltipPixelWidth = 0;
        tooltipPixelHeight = 0;
        tooltipLayoutBounds = null;
        tooltipSurfaceDirty = true;
    }

    private boolean ensureSurface(int pixelWidth, int pixelHeight) {
        if (surface != null && surfaceWidth == pixelWidth && surfaceHeight == pixelHeight) return true;
        closeSurface();
        try {
            surface = new DynamicTexture(() -> "pane-ui-surface", pixelWidth, pixelHeight, true);
            retainedTextureAllocationCount++;
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
        NativeImage nativeImage = surface.getPixels();
        var target = nativeImage.getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer();
        UiPixelTransfer.copy(surfacePixels, target, surfaceWidth, 0, 0, surfaceWidth, surfaceHeight);
        surface.upload();
        lastUploadPixelCount = surfaceWidth * surfaceHeight;
    }

    private void upload(PixelRect region) {
        if (surface == null || surfacePixels == null || region.width() <= 0 || region.height() <= 0) return;
        NativeImage nativeImage = surface.getPixels();
        var bytes = nativeImage.getPixelBytes().order(ByteOrder.nativeOrder());
        UiPixelTransfer.copy(surfacePixels, bytes.asIntBuffer(), surfaceWidth,
            region.x(), region.y(), region.width(), region.height());
        // Full-width scanlines avoid subrectangle row-packing differences. Guard rows are copied
        // from the unchanged native backing; they never rerasterize neighboring content.
        int top = Math.max(0, region.y() - 1);
        int bottom = Math.min(surfaceHeight, region.bottom() + 1);
        ByteBuffer band = bytes.slice(top * surfaceWidth * 4, (bottom - top) * surfaceWidth * 4);
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(
            surface.getTexture(), band, 0, 0, 0, top, surfaceWidth, bottom - top);
        lastUploadPixelCount = surfaceWidth * (bottom - top);
    }

    /**
     * GUI-space to layout-space factor. Layout units are operating-system points scaled by the
     * theme's density, so a screen is the same physical size regardless of Minecraft's GUI scale.
     */
    private float computeLayoutScale() {
        return (float) clamp(guiScale() / (dpiScale() * uiScale()), .25, 8);
    }

    /**
     * How physically large the interface is: the document's adaptive response to window size,
     * multiplied by the theme's density as a straight user preference.
     */
    private double uiScale() {
        Window window = Minecraft.getInstance().getWindow();
        double scale = document.scaling().scaleFor(window.getScreenWidth(), window.getScreenHeight());
        return Math.max(.1, scale * Math.max(.1F, document.theme().density()));
    }

    /**
     * Layout-space to surface-space factor. Targets the physical framebuffer resolution so text
     * lands on real pixels, then clamps so a fullscreen surface stays within the memory budget.
     */
    private float computeQualityScale(int layoutWidth, int layoutHeight) {
        // Target the physical framebuffer so text lands on real pixels.
        double target = dpiScale() * uiScale();
        UiRenderSettings settings = document.renderSettings();
        double memoryCap = Math.min(settings.maximumWidth() / (double) Math.max(1, layoutWidth),
            settings.maximumHeight() / (double) Math.max(1, layoutHeight));
        return (float) clamp(Math.min(target, memoryCap), 1, settings.maximumScale());
    }

    private boolean canCompositeVirtualLists() {
        // Tooltips are composited from their own retained surface. They must never force virtual
        // rows back through the full-page raster path, because that changes text pixel rounding.
        return openMenuId == null && openDialogId == null;
    }

    private void reconcileVirtualLayers(List<UiVirtualListRequest> requests, float qualityScale,
                                        long now, UiAnimationClock animationClock) {
        Set<String> retained = new HashSet<>();
        for (UiVirtualListRequest request : requests) {
            retained.add(request.id);
            VirtualListLayer layer = virtualLayers.get(request.id);
            if (layer == null || !layer.matches(request, qualityScale)) {
                if (layer != null) layer.close();
                layer = new VirtualListLayer(request, qualityScale);
                virtualLayers.put(request.id, layer);
            } else {
                layer.request = request;
                layer.rows.values().forEach(row -> row.dirty = true);
            }
            layer.currentOffset = Math.max(0, Math.min(request.maxOffset(),
                numberValue("scroll:" + request.id, 0)));
            ensureVirtualRows(layer, now, animationClock);
        }
        var iterator = virtualLayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, VirtualListLayer> entry = iterator.next();
            if (retained.contains(entry.getKey())) continue;
            entry.getValue().close();
            iterator.remove();
        }
        refreshVirtualInteractions();
    }

    private boolean ensureVirtualRows(VirtualListLayer layer, long now, UiAnimationClock animationClock) {
        int pixelWidth = Math.max(1, Math.round(layer.request.viewport.width() * layer.qualityScale));
        int pixelHeight = Math.max(1, Math.round(layer.request.rowHeight * layer.qualityScale));
        layer.ensureAtlas(pixelWidth, pixelHeight);
        int firstVisible = layer.request.firstVisible(layer.offset());
        int lastVisible = layer.request.lastVisible(layer.offset());
        int first = Math.max(0, firstVisible - 4);
        int last = Math.min(layer.request.itemCount, lastVisible + 4);
        boolean changed = false;
        // Recycle rows that left overscan before admitting new rows. Their BufferedImage, pixel
        // array, and direct upload buffer are hot-path storage, not per-dataset-item allocations.
        var iterator = layer.rows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, VirtualRowSurface> entry = iterator.next();
            if (entry.getKey() >= first && entry.getKey() < last) continue;
            layer.recycle(entry.getValue());
            iterator.remove();
            changed = true;
        }
        for (int index = first; index < last; index++) {
            VirtualRowSurface row = layer.rows.get(index);
            if (row == null) {
                row = layer.acquire(index);
                layer.rows.put(index, row);
            }
            if (row.dirty || row.image == null) {
                renderVirtualRow(layer, row, now, animationClock);
                changed = true;
            }
        }
        // At the top/bottom only one side of overscan is populated. Warm enough reusable storage
        // for the largest two-sided window now, before motion begins, so the scroll hot path never
        // allocates images, pixel arrays, or direct upload buffers.
        int maximumBuffers = Math.min(layer.request.itemCount,
            (int) Math.ceil(layer.request.viewport.height() / layer.request.stride()) + 10);
        int uploadHeight = pixelHeight + VIRTUAL_ATLAS_GUTTER * 2;
        int uploadBytes = Math.multiplyExact(Math.multiplyExact(pixelWidth, uploadHeight), Integer.BYTES);
        while (layer.rows.size() + layer.recycledRows.size() < maximumBuffers) {
            VirtualRowSurface spare = new VirtualRowSurface(-1);
            spare.ensure(pixelWidth, pixelHeight);
            spare.uploadBuffer(uploadBytes);
            virtualRowBufferAllocationCount++;
            layer.recycledRows.addLast(spare);
        }
        return changed;
    }

    private void renderVirtualRow(VirtualListLayer layer, VirtualRowSurface row, long now,
                                  UiAnimationClock animationClock) {
        long started = System.nanoTime();
        int pixelWidth = Math.max(1, Math.round(layer.request.viewport.width() * layer.qualityScale));
        int pixelHeight = Math.max(1, Math.round(layer.request.rowHeight * layer.qualityScale));
        boolean allocateRowBuffer = row.image == null || row.width != pixelWidth || row.height != pixelHeight;
        row.ensure(pixelWidth, pixelHeight);
        if (allocateRowBuffer) virtualRowBufferAllocationCount++;
        Arrays.fill(row.pixels, 0);
        Graphics2D graphics = row.image.createGraphics();
        List<UiHitRegion> hits = new ArrayList<>();
        Map<String, NativeBounds> nativeRows = new LinkedHashMap<>();
        Map<String, UiRect> nativeOverlayRows = new LinkedHashMap<>();
        List<UiScrollRegion> scrolls = new ArrayList<>();
        List<UiInputRegion> inputs = new ArrayList<>();
        List<UiTooltip> tips = new ArrayList<>();
        List<UiMenu> rowMenus = new ArrayList<>();
        List<UiDialogRequest> rowDialogs = new ArrayList<>();
        try {
            graphics.scale(layer.qualityScale, layer.qualityScale);
            float rowY = layer.request.viewport.y() + row.index * layer.request.stride();
            graphics.translate(-layer.request.viewport.x(), -rowY);
            UiCanvas canvas = new UiCanvas(graphics, row.image);
            UiAnimationClock rowClock = new UiAnimationClock(animations, now, motionScale());
            UiVirtualListRequest.DynamicState dynamic = new UiVirtualListRequest.DynamicState(
                hoveredId, pressedId, ease(now, transitionStart, transitionEnd),
                ease(now, interactionStart, interactionEnd), transitionEnd != 0 || interactionEnd != 0,
                paintCount, new UiTextInputState(focusedId, textCursor, textAnchor), rowClock);
            layer.request.paintRow(canvas, row.index, dynamic,
                new UiVirtualListRequest.RowOutput(hits, nativeRows, nativeOverlayRows, scrolls, inputs, tips, rowMenus, rowDialogs));
            row.customAnimating = rowClock.active();
        } finally {
            graphics.dispose();
        }
        // Duplicate the first and last source scanline into a one-pixel atlas gutter. Linear
        // filtering can then move the quad by fractions of a GUI pixel without sampling the
        // neighboring row slot and producing a crawling separator or text ghost.
        int uploadHeight = pixelHeight + VIRTUAL_ATLAS_GUTTER * 2;
        int bytes = Math.multiplyExact(Math.multiplyExact(pixelWidth, uploadHeight), Integer.BYTES);
        ByteBuffer upload = row.uploadBuffer(bytes);
        int nonTransparent = 0;
        for (int x = 0; x < pixelWidth; x++) upload.putInt(toRgba(row.pixels[x]));
        for (int i = 0; i < row.pixels.length; i++) {
            int argb = row.pixels[i];
            if ((argb >>> 24) != 0) nonTransparent++;
            upload.putInt(toRgba(argb));
        }
        int lastScanline = (pixelHeight - 1) * pixelWidth;
        for (int x = 0; x < pixelWidth; x++) upload.putInt(toRgba(row.pixels[lastScanline + x]));
        upload.flip();
        row.atlasSlot = Math.floorMod(row.index, layer.atlasRows);
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(
            layer.atlas.getTexture(), upload, 0, 0, 0, row.atlasSlot * layer.atlasSlotHeight,
            pixelWidth, uploadHeight
        );
        row.hits = List.copyOf(hits);
        row.nativeBounds = Map.copyOf(nativeRows);
        row.nativeOverlayLayers = Map.copyOf(nativeOverlayRows);
        row.inputs = List.copyOf(inputs);
        row.tooltips = List.copyOf(tips);
        row.menus = List.copyOf(rowMenus);
        row.dialogs = List.copyOf(rowDialogs);
        row.dirty = false;
        virtualRowRasterCount++;
        virtualRowUploadPixels += (long) pixelWidth * uploadHeight;
        lastVirtualRowNonTransparentPixels = nonTransparent;
        lastVirtualRowPaintNanos = System.nanoTime() - started;
    }

    private void renderVirtualOverlayLayers(GuiGraphicsExtractor graphics, long now) {
        for (UiRect cover : virtualNativeOverlayLayers.values()) {
            renderVirtualLayers(graphics, now, cover);
        }
    }

    private void renderVirtualLayers(GuiGraphicsExtractor graphics, long now) {
        renderVirtualLayers(graphics, now, null);
    }

    private void renderVirtualLayers(GuiGraphicsExtractor graphics, long now, UiRect overlayClip) {
        UiAnimationClock animationClock = new UiAnimationClock(animations, now, motionScale());
        boolean interactionsChanged = false;
        for (VirtualListLayer layer : virtualLayers.values()) {
            if (ensureVirtualRows(layer, now, animationClock)) interactionsChanged = true;
            UiRect viewport = layer.request.viewport;
            UiRect visible = overlayClip == null ? viewport : clipped(viewport, overlayClip);
            if (visible.width() <= 0 || visible.height() <= 0) continue;
            int sx = (int) Math.floor(visible.x() / layoutScale);
            int sy = (int) Math.floor(visible.y() / layoutScale);
            int sr = (int) Math.ceil(visible.right() / layoutScale);
            int sb = (int) Math.ceil(visible.bottom() / layoutScale);
            graphics.enableScissor(sx, sy, sr, sb);
            int first = Math.max(0, layer.request.firstVisible(layer.offset()) - 1);
            int last = Math.min(layer.request.itemCount, layer.request.lastVisible(layer.offset()) + 1);
            float guiWidth = viewport.width() / layoutScale;
            float guiHeight = layer.request.rowHeight / layoutScale;
            int drawWidth = Math.max(1, Math.round(guiWidth));
            int drawHeight = Math.max(1, Math.round(guiHeight));
            // Scroll once at the parent transform. Previously each row independently rounded
            // (baseY - offset), so adjacent rows changed pixel spacing on different frames and
            // appeared to move in a wave. Float transforms preserve one coherent list plane.
            graphics.pose().pushMatrix();
            graphics.pose().translate(0, -(float) (layer.offset() / layoutScale));
            try {
                for (int index = first; index < last; index++) {
                    VirtualRowSurface row = layer.rows.get(index);
                    if (row == null || layer.atlas == null) continue;
                    UiRect destination = virtualRowGuiRect(layer.request, index, layoutScale);
                    float atlasHeight = layer.atlasSlotHeight * (float) layer.atlasRows;
                    float sourceTop = row.atlasSlot * layer.atlasSlotHeight + VIRTUAL_ATLAS_GUTTER;
                    float v0 = sourceTop / atlasHeight;
                    float v1 = (sourceTop + layer.atlasRowHeight) / atlasHeight;
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(destination.x(), destination.y());
                    graphics.pose().scale(destination.width() / drawWidth, destination.height() / drawHeight);
                    try {
                        graphics.blit(layer.atlas.getTextureView(), layer.sampler,
                            0, 0, drawWidth, drawHeight, 0, 1, v0, v1);
                    } finally {
                        graphics.pose().popMatrix();
                    }
                }
            } finally {
                graphics.pose().popMatrix();
            }
            if (overlayClip == null) drawVirtualScrollbar(graphics, layer);
            graphics.disableScissor();
        }
        if (interactionsChanged) refreshVirtualInteractions();
        customAnimationsActive |= animationClock.active();
    }

    static UiRect virtualRowGuiRect(UiVirtualListRequest request, int index, float layoutScale) {
        return new UiRect(
            request.viewport.x() / layoutScale,
            (request.viewport.y() + index * request.stride()) / layoutScale,
            request.viewport.width() / layoutScale,
            request.rowHeight / layoutScale
        );
    }

    private static int toRgba(int argb) {
        return argb & 0xFF00FF00 | argb >>> 16 & 0xFF | (argb & 0xFF) << 16;
    }

    private void drawVirtualScrollbar(GuiGraphicsExtractor graphics, VirtualListLayer layer) {
        UiRect viewport = layer.request.viewport;
        if (layer.request.maxOffset() <= 0) return;
        float trackHeight = Math.max(0, viewport.height() - 8);
        float thumbHeight = Math.max(18, trackHeight * viewport.height()
            / Math.max(viewport.height(), layer.request.contentHeight));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        float travel = Math.max(0, trackHeight - thumbHeight);
        float thumbY = viewport.y() + 4 + travel * (float) layer.offset()
            / Math.max(1, layer.request.maxOffset());
        int x = Math.round((viewport.right() - 4) / layoutScale);
        int top = Math.round((viewport.y() + 4) / layoutScale);
        int bottom = Math.round((viewport.y() + 4 + trackHeight) / layoutScale);
        int thumbTop = Math.round(thumbY / layoutScale);
        int thumbBottom = Math.round((thumbY + thumbHeight) / layoutScale);
        graphics.fill(x, top, x + Math.max(1, Math.round(2 / layoutScale)), bottom,
            UiCanvas.fade(layer.request.theme.border(), .45F));
        graphics.fill(x, thumbTop, x + Math.max(1, Math.round(2 / layoutScale)), thumbBottom,
            UiCanvas.fade(layer.request.theme.mutedText(), .65F));
    }

    private void refreshVirtualInteractions() {
        if (!virtualCompositeActive && virtualLayers.isEmpty()) {
            clearVirtualInteractions();
            return;
        }
        List<UiHitRegion> hits = new ArrayList<>();
        List<UiInputRegion> inputs = new ArrayList<>();
        List<UiTooltip> tips = new ArrayList<>();
        List<UiMenu> rowMenus = new ArrayList<>();
        List<UiDialogRequest> rowDialogs = new ArrayList<>();
        virtualNativeBounds.clear();
        virtualNativeOverlayLayers.clear();
        for (VirtualListLayer layer : virtualLayers.values()) {
            float shiftY = -(float) layer.offset();
            UiRect clip = layer.request.viewport;
            int first = Math.max(0, layer.request.firstVisible(layer.offset()) - 1);
            int last = Math.min(layer.request.itemCount, layer.request.lastVisible(layer.offset()) + 1);
            for (int index = first; index < last; index++) {
                VirtualRowSurface row = layer.rows.get(index);
                if (row == null) continue;
                for (UiHitRegion hit : row.hits) {
                    UiRect moved = clipped(new UiRect(hit.x(), hit.y() + shiftY, hit.width(), hit.height()), clip);
                    if (moved.width() <= 0 || moved.height() <= 0) continue;
                    hits.add(new UiHitRegion(hit.id(), hit.action(), moved.x(), moved.y(), moved.width(), moved.height(),
                        hit.trackX(), hit.trackWidth(), hit.hitShape() == null ? null
                            : java.awt.geom.AffineTransform.getTranslateInstance(0, shiftY).createTransformedShape(hit.hitShape())));
                }
                for (UiInputRegion input : row.inputs) {
                    UiRect moved = clipped(input.bounds().translate(0, shiftY), clip);
                    if (moved.width() <= 0 || moved.height() <= 0) continue;
                    float[] xs = input.caretXs();
                    float[] ys = input.caretYs();
                    for (int i = 0; i < ys.length; i++) ys[i] += shiftY;
                    inputs.add(new UiInputRegion(input.id(), input.key(), moved, input.maxLength(),
                        input.multiline(), xs, ys));
                }
                for (UiTooltip tooltip : row.tooltips) {
                    UiRect moved = clipped(tooltip.anchor().translate(0, shiftY), clip);
                    if (moved.width() > 0 && moved.height() > 0) tips.add(new UiTooltip(tooltip.id(), tooltip.text(), moved));
                }
                for (UiMenu menu : row.menus) {
                    UiRect moved = clipped(menu.anchor().translate(0, shiftY), clip);
                    if (moved.width() > 0 && moved.height() > 0) rowMenus.add(new UiMenu(menu.id(), moved, menu.items()));
                }
                rowDialogs.addAll(row.dialogs);
                double nativeShift = shiftY * layer.request.nativeScale;
                row.nativeBounds.forEach((id, bounds) -> virtualNativeBounds.put(id,
                    new NativeBounds(bounds.x(), bounds.y() + nativeShift, bounds.width(), bounds.height())));
                row.nativeOverlayLayers.forEach((id, bounds) -> virtualNativeOverlayLayers.put(id,
                    bounds.translate(0, shiftY)));
            }
        }
        virtualHitRegions = List.copyOf(hits);
        virtualInputRegions = List.copyOf(inputs);
        List<UiTooltip> nextTooltips = List.copyOf(tips);
        if (!virtualTooltips.equals(nextTooltips)) tooltipSurfaceDirty = true;
        virtualTooltips = nextTooltips;
        virtualMenus = List.copyOf(rowMenus);
        virtualDialogs = List.copyOf(rowDialogs);
    }

    private static UiRect clipped(UiRect bounds, UiRect clip) {
        float left = Math.max(bounds.x(), clip.x());
        float top = Math.max(bounds.y(), clip.y());
        float right = Math.min(bounds.right(), clip.right());
        float bottom = Math.min(bounds.bottom(), clip.bottom());
        return new UiRect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    private void clearVirtualInteractions() {
        virtualHitRegions = List.of();
        virtualInputRegions = List.of();
        virtualTooltips = List.of();
        tooltipSurfaceDirty = true;
        virtualMenus = List.of();
        virtualDialogs = List.of();
        virtualNativeBounds.clear();
        virtualNativeOverlayLayers.clear();
    }

    private VirtualRowKey virtualRow(String controlId) {
        if (controlId == null || !virtualCompositeActive) return null;
        for (Map.Entry<String, VirtualListLayer> layerEntry : virtualLayers.entrySet()) {
            for (Map.Entry<Integer, VirtualRowSurface> rowEntry : layerEntry.getValue().rows.entrySet()) {
                VirtualRowSurface row = rowEntry.getValue();
                for (UiHitRegion hit : row.hits) if (controlId.equals(hit.id())) {
                    return new VirtualRowKey(layerEntry.getKey(), rowEntry.getKey());
                }
                for (UiTooltip tooltip : row.tooltips) if (controlId.equals(tooltip.id())) {
                    return new VirtualRowKey(layerEntry.getKey(), rowEntry.getKey());
                }
            }
        }
        return null;
    }

    private boolean requestVirtualRows(String... controlIds) {
        if (controlIds == null || controlIds.length == 0 || !virtualCompositeActive) return false;
        boolean found = false;
        for (String id : controlIds) {
            if (id == null) continue;
            VirtualRowKey key = virtualRow(id);
            if (key == null) return false;
            VirtualListLayer layer = virtualLayers.get(key.listId());
            VirtualRowSurface row = layer == null ? null : layer.rows.get(key.index());
            if (row != null) {
                row.dirty = true;
                animatedVirtualRows.add(key);
                found = true;
            }
        }
        return found;
    }

    private void markAnimatedVirtualRowsDirty() {
        for (VirtualRowKey key : animatedVirtualRows) {
            VirtualListLayer layer = virtualLayers.get(key.listId());
            VirtualRowSurface row = layer == null ? null : layer.rows.get(key.index());
            if (row != null) row.dirty = true;
        }
    }

    private Map<String, NativeBounds> combinedNativeBounds() {
        if (virtualNativeBounds.isEmpty()) return nativeBoundsView;
        combinedNativeBounds.clear();
        combinedNativeBounds.putAll(nativeBounds);
        combinedNativeBounds.putAll(virtualNativeBounds);
        return Collections.unmodifiableMap(combinedNativeBounds);
    }


    private void closeVirtualLayers() {
        virtualLayers.values().forEach(VirtualListLayer::close);
        virtualLayers.clear();
        animatedVirtualRows.clear();
        virtualCompositeActive = false;
        clearVirtualInteractions();
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

    private record ActiveToast(UiToast toast, long shownAt) {
        long expiresAt() { return shownAt + toast.durationMillis() * 1_000_000L; }
    }

    private record PixelRect(int x, int y, int width, int height) {
        int bottom() { return y + height; }
    }

    private record VirtualRowKey(String listId, int index) { }

    private final class VirtualListLayer implements AutoCloseable {
        UiVirtualListRequest request;
        final float qualityScale;
        final Map<Integer, VirtualRowSurface> rows = new LinkedHashMap<>();
        final ArrayDeque<VirtualRowSurface> recycledRows = new ArrayDeque<>();
        DynamicTexture atlas;
        GpuSampler sampler;
        int atlasWidth;
        int atlasRowHeight;
        int atlasSlotHeight;
        int atlasRows;
        double currentOffset;

        VirtualListLayer(UiVirtualListRequest request, float qualityScale) {
            this.request = request;
            this.qualityScale = qualityScale;
        }

        double offset() {
            currentOffset = Math.max(0, Math.min(request.maxOffset(),
                numberValue("scroll:" + request.id, currentOffset)));
            return currentOffset;
        }

        boolean matches(UiVirtualListRequest next, float nextQualityScale) {
            return request.itemCount == next.itemCount
                && Float.compare(request.rowHeight, next.rowHeight) == 0
                && Float.compare(request.gap, next.gap) == 0
                && request.viewport.equals(next.viewport)
                && Float.compare(qualityScale, nextQualityScale) == 0;
        }

        void ensureAtlas(int pixelWidth, int pixelRowHeight) {
            int capacity = Math.max(1, Math.min(request.itemCount,
                (int) Math.ceil(request.viewport.height() / request.stride()) + 18));
            if (atlas != null && atlasWidth == pixelWidth && atlasRowHeight == pixelRowHeight
                && atlasRows == capacity) return;
            if (atlas != null) atlas.close();
            atlasSlotHeight = pixelRowHeight + VIRTUAL_ATLAS_GUTTER * 2;
            atlas = new DynamicTexture(() -> "pane-virtual-list-" + request.id,
                pixelWidth, Math.multiplyExact(atlasSlotHeight, capacity), true);
            virtualAtlasAllocationCount++;
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            atlasWidth = pixelWidth;
            atlasRowHeight = pixelRowHeight;
            atlasRows = capacity;
            rows.values().forEach(row -> row.dirty = true);
        }

        VirtualRowSurface acquire(int index) {
            VirtualRowSurface row = recycledRows.pollFirst();
            if (row == null) return new VirtualRowSurface(index);
            row.rebind(index);
            return row;
        }

        void recycle(VirtualRowSurface row) {
            row.clearInteractions();
            recycledRows.addLast(row);
        }

        @Override public void close() {
            rows.values().forEach(VirtualRowSurface::close);
            rows.clear();
            recycledRows.forEach(VirtualRowSurface::close);
            recycledRows.clear();
            if (atlas != null) atlas.close();
            atlas = null;
            sampler = null;
        }
    }

    private static final class VirtualRowSurface implements AutoCloseable {
        int index;
        BufferedImage image;
        int[] pixels;
        ByteBuffer uploadBuffer;
        int width;
        int height;
        int atlasSlot;
        boolean dirty = true;
        boolean customAnimating;
        List<UiHitRegion> hits = List.of();
        Map<String, NativeBounds> nativeBounds = Map.of();
        Map<String, UiRect> nativeOverlayLayers = Map.of();
        List<UiInputRegion> inputs = List.of();
        List<UiTooltip> tooltips = List.of();
        List<UiMenu> menus = List.of();
        List<UiDialogRequest> dialogs = List.of();

        VirtualRowSurface(int index) { this.index = index; }

        void rebind(int index) {
            this.index = index;
            dirty = true;
            customAnimating = false;
            clearInteractions();
        }

        void clearInteractions() {
            hits = List.of();
            nativeBounds = Map.of();
            nativeOverlayLayers = Map.of();
            inputs = List.of();
            tooltips = List.of();
            menus = List.of();
            dialogs = List.of();
        }

        void ensure(int width, int height) {
            if (image != null && this.width == width && this.height == height) return;
            close();
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            this.width = width;
            this.height = height;
        }

        ByteBuffer uploadBuffer(int bytes) {
            if (uploadBuffer == null || uploadBuffer.capacity() < bytes) {
                uploadBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
            }
            uploadBuffer.clear();
            uploadBuffer.limit(bytes);
            return uploadBuffer;
        }

        @Override public void close() {
            image = null;
            pixels = null;
            uploadBuffer = null;
            width = 0;
            height = 0;
        }
    }

    private void closeSurface() {
        closeToastSurface();
        closeVirtualLayers();
        closeTooltipSurface();
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

    /** Restores the configured parent, or closes to the game when no parent was supplied. */
    public void closeToParent() {
        if (minecraft != null) minecraft.gui.setScreen(options.parent());
    }

    /** Escape/back entry point. Consumer handling runs first, then the configured parent fallback. */
    @Override
    public void onClose() {
        if (options.closeHandler().onClose(this)) return;
        if (options.parent() != null) closeToParent();
        else super.onClose();
    }

    @Override
    public void removed() {
        closeSurface();
        requestFullRepaint();
        hoverDirty = true;
        scrollMotions.clear();
        hitRegions = List.of();
        scrollRegions = List.of();
        inputRegions = List.of();
        draggingTextInput = null;
        nativeBounds.clear();
        focusScopes.clear(); pendingFocus = null; dialogRestore = null; textHistories.clear();
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
}
