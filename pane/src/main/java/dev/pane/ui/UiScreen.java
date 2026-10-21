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

    private long minecraftTextureRevision = UiCanvas.minecraftTextureRevision();
    private final UiDocument document;
    private final NativeOverlayRenderer nativeOverlay;
    private final UiEventListener eventListener;
    private final UiScreenOptions options;
    private final Map<String, Object> state = new HashMap<>();
    private final Map<String, UiAnimationClock.State> animations = new HashMap<>();
    private Set<String> rootAnimationIds=Set.of();
    /** Retained animation states for the current page and cached virtual rows; client-thread diagnostic. */
    public int animationStateCount(){return animations.size();}
    private void pruneAnimations() {
        var retained=new HashSet<>(rootAnimationIds);
        for(var layer:virtualLayers.values())for(var row:layer.rows.values())retained.addAll(row.animationIds);
        animations.keySet().retainAll(retained);
    }
    private Map<String, UiRect> customAnimationBounds = Map.of();
    private Set<String> activeCustomAnimationIds = Set.of();
    private final Map<String, NativeBounds> nativeBounds = new LinkedHashMap<>();
    private final Map<String, NativeBounds> nativeBoundsView = Collections.unmodifiableMap(nativeBounds);
    private final Map<String, UiRect> nativeOverlayLayers = new LinkedHashMap<>();
    private final Map<String, UiRect> nativeOverlayLayersView = Collections.unmodifiableMap(nativeOverlayLayers);
    private final Map<String, UiRect> toastBounds = new HashMap<>();
    private final UiInteractionState interactions = new UiInteractionState();
    private double scrollOffset(String id) { return interactions.offsets.getOrDefault(id, 0D); }
    private List<UiHitRegion> hitRegions = List.of();
    private List<UiScrollRegion> scrollRegions = List.of();
    private List<UiInputRegion> inputRegions = List.of();
    private List<UiTooltip> tooltips = List.of();
    private List<UiMenu> menus = List.of();
    private List<UiDialogRequest> dialogs = List.of();
    private final List<ActiveToast> toasts = new ArrayList<>();

    private final UiBlurScratch dialogBlurScratch = new UiBlurScratch();
    /** Temporary blur image allocations; separate from GPU texture allocations. */
    public long dialogBlurAllocations() { return dialogBlurScratch.allocations; }
    public long dialogBlurRetainedPixels() { return dialogBlurScratch.retainedPixels(); }
    private DynamicTexture popupSurface;
    private BufferedImage popupImage;
    private int[] popupPixels;
    private UiRect popupLayoutBounds;
    private int popupBackdrop;
    private long popupUploadPixels;
    private boolean popupDirty;
    private PopupPaintState popupPaintState;
    private UiDialogLayout dialogLayout;
    private DialogLayoutKey dialogLayoutKey;
    private float dialogOffset;
    private boolean dialogHitsStale;
    private record DialogLayoutKey(UiDialogRequest request,int width,int height,float quality,long fontRevision) { }
    private long dialogLayoutCount;
    /** Dialog text measurements since this screen was created; scrolling/focus do not rewrap copy. */
    public long dialogLayoutCount(){return dialogLayoutCount;}
    /** Current built-in dialog copy viewport, if a dialog is laid out. */
    public Optional<UiScrollSnapshot> dialogScrollSnapshot() {
        return openDialogId==null||dialogLayout==null?Optional.empty():Optional.of(new UiScrollSnapshot(openDialogId,
            dialogLayout.content(),dialogLayout.contentHeight(),dialogOffset,dialogOffset));
    }

    private List<UiHitRegion> popupHits = List.of();
    private record PopupPaintState(UiMenu menu, UiDialogRequest dialog, UiRect bounds, float offset, float dialogOffset,
                                   String hovered, String focused, String pressed, float quality) { }
    private String popupVisualId(String id) {
        return id != null && (menuItem(id) != null || openDialogId != null && (id.equals("dialog-confirm") || id.equals("dialog-cancel"))) ? id : null;
    }
    private boolean popupTarget(String id) {
        if (id == null) return false;
        return openDialogId != null && (id.equals("dialog-confirm") || id.equals("dialog-cancel") || id.equals("dialog-body") || id.equals("dialog-backdrop"))
            || openMenuId != null && (id.startsWith("menu:" + openMenuId + ":") || id.equals(menuBodyId()));
    }
    /** Pixels uploaded by cropped menu/dialog surfaces since this screen opened. */
    public long popupUploadPixels() { return popupUploadPixels; }
    /** Current cropped popup backing area; zero after cleanup. */
    public long popupRetainedPixels() { return popupImage == null ? 0 : (long) popupImage.getWidth() * popupImage.getHeight(); }

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
    private UiMenu activeMenu;
    private UiPopupLayout menuLayout;
    private float menuOffset;
    private boolean revealMenuFocus;
    private boolean menuHitsStale;
    private boolean menuNavigationFromHover;
    private String menuBodyId() { return "menu-body:" + openMenuId; }
    private float preferredCaretX = Float.NaN;
    private boolean menuOpenedWithPointer;
    private String openDialogId;
    private String visibleTooltipId;
    private long tooltipRevealAt;
    private UiHitRegion draggingSlider;
    private UiInputRegion draggingTextInput;
    private int textCursor;
    private boolean textUpstream;
    private int textAnchor;
    private boolean layoutHitsStale;
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
    private boolean interactionPaintsBase;
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
    private long lastCustomVirtualAnimationPaint;
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
    private int lastNativeTextCount;
    private final NativeTextRenderer nativeText = new NativeTextRenderer();
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
            scrollMotions.forEach((id, motion) -> interactions.offsets.put(id, motion.target()));
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
        this(document, nativeOverlay, eventListener, options, null);
    }

    private UiScreen(UiDocument document, NativeOverlayRenderer nativeOverlay,
                     UiEventListener eventListener, UiScreenOptions options, UiScreenEventHandler handler) {
        super(Component.literal(document.title()));
        this.document = Objects.requireNonNull(document, "document");
        this.nativeOverlay = nativeOverlay == null ? NativeOverlayRenderer.NONE : nativeOverlay;
        this.eventListener = handler == null ? (eventListener == null ? UiEventListener.NONE : eventListener)
            : (kind, key, value) -> handler.onEvent(this, new UiEvent(UiEventType.fromWire(kind), key, value));
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

    /** Creates a screen whose typed callback receives its owner. Existing constructors remain supported. */
    public static UiScreen create(UiDocument document, NativeOverlayRenderer overlay,
                                  UiScreenEventHandler handler, UiScreenOptions options) {
        return new UiScreen(document, overlay, UiEventListener.NONE, options, handler);
    }

    /** Creates an owner-aware screen with default lifecycle options. */
    public static UiScreen create(UiDocument document, NativeOverlayRenderer overlay, UiScreenEventHandler handler) {
        return create(document, overlay, handler, UiScreenOptions.defaults());
    }

    public UiDocument document() { return document; }
    public UiScreenOptions options() { return options; }
    public Optional<Screen> parentScreen() { return Optional.ofNullable(options.parent()); }
    public String pageId() { return pageId; }
    /** Semantic metadata from the last custom-button paint; does not itself speak narration. */
    public Optional<UiButtonState> buttonState(String id) {
        if (interactions.buttons.containsKey(id)) return Optional.of(interactions.buttons.get(id));
        if (virtualCompositeActive) for (var layer : virtualLayers.values()) for (var row : layer.rows.values()) {
            if (row.buttons.containsKey(id)) return Optional.of(row.buttons.get(id));
        }
        return Optional.empty();
    }

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
        if (openDialogId != null) return id.equals("dialog-cancel") || id.equals("dialog-confirm") || id.equals("dialog-backdrop") || id.equals("dialog-body");
        if (openMenuId != null && !id.equals(openMenuId) && !id.equals(menuBodyId()) && !id.startsWith("menu:" + openMenuId + ":")) return false;
        if (focusScopes.isEmpty()) return true;
        if (openMenuId != null && focusScopes.peekLast().controls().contains(openMenuId)
                && (id.startsWith("menu:" + openMenuId + ":") || id.equals(menuBodyId()))) return true;
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
    /** Last painted unclipped inner textarea viewport and independent vertical browsing offset, in layout units.
     * Read on the client thread, like regionBounds. */
    public Optional<UiScrollSnapshot> editorScrollSnapshot(String id) {
        UiEditorViewport editor=interactions.editors.get(id);
        if(editor==null||editor.viewport==null)return Optional.empty();
        UiRect viewport=editor.viewport;
        VirtualRowKey key=virtualRow(id);
        if(key!=null)viewport=viewport.translate(0,-(float)virtualLayers.get(key.listId()).offset());
        return Optional.of(new UiScrollSnapshot(id,viewport,viewport.height()+editor.maximum,editor.offset,editor.offset));
    }
    private void revealEditorCaret(String id) {
        UiEditorViewport editor=interactions.editors.get(id);
        if(editor!=null){editor.pointerPlacement=false;editor.reveal=true;}
    }
    private void pruneEditorViewports() {
        var registered=new HashSet<>(interactions.inputs.keySet());
        if(focusedId!=null)registered.add(focusedId);
        for(var layer:virtualLayers.values())for(var row:layer.rows.values())
            for(var input:row.inputs)registered.add(input.id());
        interactions.editors.keySet().retainAll(registered);
        if(focusedId==null||!registered.contains(focusedId))interactions.textLayouts.clear();
    }
    public Optional<UiScrollSnapshot> scrollSnapshot(String id) {
        UiScrollRegion region = scrollRegion(id);
        if (region == null) return Optional.empty();
        double offset = scrollOffset(id);
        UiScrollPhysics.Motion motion = scrollMotions.get(id);
        return Optional.of(new UiScrollSnapshot(id, region.viewport(), region.contentHeight(), offset, motion == null ? offset : motion.target()));
    }
    /**
     * Sets an internal scroll offset in layout units without emitting a consumer VALUE event.
     * Clamps to the current extent; before the first paint, the request is clamped when its viewport registers.
     * Safe from worker threads. Scroll ids retain their offsets across pages on this screen.
     */
    public void scrollTo(String id, double offset) {
        if (id == null || id.isBlank() || !Double.isFinite(offset)) throw new IllegalArgumentException("Finite offset and nonblank id required");
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) { client.execute(() -> scrollTo(id, offset)); return; }
        UiScrollRegion region = scrollRegion(id);
        double next = Math.max(0, offset);
        if (region != null) next = Math.min(next, region.maxOffset());
        interactions.scrollEnds.navigate(id, next);
        interactions.offsets.put(id, next);
        scrollMotions.remove(id);
        requestFullRepaint();
    }

    /**
     * Request the end of a viewport using its next registered content height, not its old extent.
     * Works before first paint and immediately after appending. Resolves before content/hits draw;
     * no consumer tick, timer or second paint is needed. An absent viewport leaves the request
     * pending without scheduling more frames. Explicit navigation, page changes or removal cancel
     * it. Use stable, content-specific IDs and UiScrollFollow.WHEN_AT_END for continuing follow.
     * Safe from worker threads, like scrollTo.
     */
    public void scrollToEnd(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Nonblank scroll id required");
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) { client.execute(() -> scrollToEnd(id)); return; }
        interactions.scrollEnds.request(id);
        scrollMotions.remove(id);
        requestFullRepaint();
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
        interactions.scrollEnds.navigate(listId, next);
        interactions.offsets.put(listId, next);
        layer.currentOffset = next;
        long now = System.nanoTime();
        ensureVirtualRows(layer, now);
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

    /**
     * Updates state on the client thread. Equal immutable scalar values avoid a repaint.
     * Mutable objects still notify the painter even when the same instance is supplied.
     */
    public void setValue(String key, Object value) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) {
            client.execute(() -> setValue(key, value));
            return;
        }
        Object stored = value instanceof String text && isMultilineKey(key) ? UiTextBoundary.normalizeLines(text) : value;
        boolean present = state.containsKey(key);
        Object previous = state.put(key, stored);
        boolean equal = Objects.equals(previous, stored);
        if (!equal) textHistories.remove(key);
        if (present && equal && stableStateScalar(stored)) return;
        markDirty();
    }

    private static boolean stableStateScalar(Object value) {
        return value == null || value instanceof String || value instanceof Boolean || value instanceof Character
            || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
            || value instanceof Float || value instanceof Double;
    }

    /**
     * Shows a short, non-blocking acknowledgement. Supplying the same id replaces an older toast
     * with that id; the document toast settings control the visible limit (1–8).
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

    private String currentMenuSelection(UiMenu menu) {
        // Reopening can happen in the same event batch as a selection, before a page repaint.
        for (UiMenuItem item : menu.items()) {
            String action = internalBody(item.action());
            if (!action.startsWith(DROPDOWN_ITEM_PREFIX)) continue;
            String body = action.substring(DROPDOWN_ITEM_PREFIX.length());
            int split = body.indexOf('=');
            if (split < 0) continue;
            String key = UiValueKey.decode(body.substring(0, split));
            if (Objects.equals(stringValue(key, ""), body.substring(split + 1))) return item.id();
        }
        return menu.selectedItemId();
    }

    private UiMenuItem menuItem(String id) {
        if (id == null || openMenuId == null || activeMenu == null) return null;
        for (UiMenuItem item : activeMenu.items()) if (id.equals("menu:" + openMenuId + ":" + item.id())) return item;
        return null;
    }

    private Optional<UiHitRegion> keyboardRegion(String id) {
        UiMenuItem item = menuItem(id);
        if (item != null) return Optional.of(new UiHitRegion(id,
            UiContext.internalAction(MENU_ITEM_PREFIX + item.action()), 0, 0, 0, 0));
        var visible = region(id);
        var target = interactions.targets.get(id);
        return visible.isPresent() ? visible : target == null ? Optional.empty() : Optional.of(target.hit());
    }

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
        interactions.scrollEnds.cancelPending();
        pageId = id;
        interactionEnd = 0;
        interactionPaintsBase = false;
        interactionRepaintBounds = null;
        focusScopes.clear(); pendingFocus = null; dialogRestore = null; textHistories.clear();
        hoveredId = null;
        pressedId = null;
        focusedId = null;
        draggingSlider = null;
        draggingTextInput = null;
        nativeBounds.clear();
        nativeText.clear(); lastNativeTextCount = 0;
        nativeOverlayLayers.clear();
        hitRegions = List.of();
        interactions.textLayouts.clear();
        interactions.editors.clear();
        interactions.beginPaint();
        scrollRegions = List.of();
        inputRegions = List.of();
        tooltips = List.of();
        menus = List.of();
        dialogs = List.of();
        animations.clear();
        rootAnimationIds=Set.of();
        customAnimationBounds = Map.of();
        activeCustomAnimationIds = Set.of();
        scrollMotions.clear();
        closeVirtualLayers();
        customAnimationsActive = false;
        openMenuId = null; activeMenu = null; menuLayout = null; menuOffset = 0;
        openDialogId = null;
        dialogLayout=null;dialogLayoutKey=null;dialogOffset=0;dialogHitsStale=false;
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
        String previousFocus=focusedId;
        int previousCursor=textCursor, previousAnchor=textAnchor;
        boolean previousUpstream=textUpstream;
        float previousPreferredX=preferredCaretX;
        FocusRestore previousPendingFocus=pendingFocus;
        super.resize(width, height);
        pendingFocus=previousPendingFocus;
        // Screen.resize clears focus through our public clearFocus override.
        if(previousFocus!=null) {
            focusedId=previousFocus;
            textCursor=previousCursor;textAnchor=previousAnchor;textUpstream=previousUpstream;
            preferredCaretX=previousPreferredX;
        }
        Screen parent = options.parent();
        if (parent != null && parent != this && (parent.width != width || parent.height != height)) {
            parent.resize(width, height);
        }
        if (changed) {
            layoutHitsStale=true;
            draggingSlider=null;draggingTextInput=null;pressedId=null;
            if (openDialogId != null) dialogHitsStale = true;
            if (openMenuId != null) {
                hitRegions = hitRegions.stream().filter(hit -> !hit.id().startsWith("menu:" + openMenuId + ":")).toList();
                revealMenuFocus = true; menuHitsStale = true; menuNavigationFromHover = false; hoveredId = null;
            }
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
        long revision = UiCanvas.minecraftTextureRevision();
        if (minecraftTextureRevision != revision) {
            minecraftTextureRevision = revision;
            markDirty();
            popupDirty = toastSurfaceDirty = tooltipSurfaceDirty = true;
        }
        synchronizeViewport(graphics);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long now = System.nanoTime();
        dialogBlurScratch.releaseIfIdle(now);
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
                }
                if (interactionPaintsBase && now - lastPaint >= FRAME_NANOS) requestInteractionRepaint();
            } else {
                interactionEnd = 0;
                if (!animatedVirtualRows.isEmpty()) {
                    markAnimatedVirtualRowsDirty();
                    animatedVirtualRows.clear();
                }
                if(interactionPaintsBase) {
                    requestInteractionRepaint();
                }
                interactionRepaintBounds = null;interactionPaintsBase=false;
            }
        }
        if (customAnimationsActive && now - lastPaint >= FRAME_NANOS) requestCustomAnimationRepaint();
        if (virtualCompositeActive && now - lastCustomVirtualAnimationPaint >= FRAME_NANOS) {
            boolean any = false;
            for (VirtualListLayer layer : virtualLayers.values()) for (VirtualRowSurface row : layer.rows.values()) {
                if (row.customAnimating) { row.dirty = true; any = true; }
            }
            if (any) lastCustomVirtualAnimationPaint = now;
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
                graphics, pageId, combinedNativeBounds(), guiScale(), mouseX, mouseY,
                openMenuId != null || openDialogId != null || !focusScopes.isEmpty(), nativeText
            );
            nativeOverlay.render(nativeContext);
            lastNativeOverlayNanos = System.nanoTime() - nativeStarted;
            lastNativeItemCount = nativeContext.renderedItemCount();
            lastNativeEntityCount = nativeContext.renderedEntityCount();
            lastNativeTextCount = nativeContext.renderedTextCount();
        } else {
            lastNativeOverlayNanos = 0;
            lastNativeItemCount = 0;
            lastNativeEntityCount = 0;
            lastNativeTextCount = 0;
        }
        if (!virtualNativeOverlayLayers.isEmpty()) {
            graphics.nextStratum();
            renderVirtualOverlayLayers(graphics, now);
        }
        if (!nativeOverlayLayers.isEmpty()) {
            graphics.nextStratum();
            renderNativeOverlayLayers(graphics);
        }
        renderPopupOverlay(graphics);
        renderToastOverlay(graphics, now);
        renderTooltipOverlay(graphics);
        lastCompositeNanos = System.nanoTime() - compositeStarted;
        publishDiagnostics(now);
    }

    /** Native text submissions in the latest rendered frame, not glyph count. */
    public int nativeTextCount() { return lastNativeTextCount; }
    /** Cumulative native line measurements; stable labels reuse cached layout. */
    public long nativeTextLayoutCount() { return nativeText.layouts(); }

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
        if (openMenuId != null && !menuHitsStale && (this.mouseX != (int)mouseX || this.mouseY != (int)mouseY))
            menuNavigationFromHover = true;
        this.mouseX = (int) mouseX;
        this.mouseY = (int) mouseY;
        float scale = eventLayoutScale();
        updateHover(mouseX * scale, mouseY * scale, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if(layoutHitsStale)return true;
        if(openDialogId!=null&&dialogHitsStale)return true;
        if (openMenuId != null && menuHitsStale) return true;
        float scale = eventLayoutScale();
        float layoutX = (float) (event.x() * scale);
        float layoutY = (float) (event.y() * scale);
        UiHitRegion region = regionAt(layoutX, layoutY);
        if(openMenuId==null&&region!=null) {
            UiEditorViewport editor=interactions.editors.get(region.id());
            if(editor!=null&&editor.stale)return true;
        }
        if (openDialogId == null && openMenuId == null && focusScopes.isEmpty() && options.clickHandler().mouseClicked(this,
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
        if ((region.id().equals("dialog-body") && openDialogId != null)
                || (openMenuId != null && region.id().equals(menuBodyId()))) return true;
        if (openMenuId != null && !isMenuRegion(region)) closeMenu(false);
        boolean extendSelection = Objects.equals(focusedId, region.id())
            && (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        int previousAnchor = textAnchor;
        focus(region.id());
        UiInputRegion clickedInput = inputRegion(region.id());
        if (clickedInput != null) {
            UiEditorViewport editor=interactions.editors.get(clickedInput.id());
            if(editor!=null)editor.pointerPlacement=true;
            preferredCaretX = Float.NaN;
            var position = clickedInput.caretPositionAt((float) (event.x() * scale), (float) (event.y() * scale));
            String value = editingValue(clickedInput);
            int caret = UiTextBoundary.floor(value, position.index());
            textUpstream = !doubleClick && position.upstream();
            if (doubleClick) {
                var selection = clickedInput.masked() ? new UiTextBoundary.Selection(0,value.length())
                    : UiTextBoundary.wordAt(value,caret);
                textAnchor = selection.start(); textCursor = selection.end();
            } else {
                textAnchor = extendSelection ? UiTextBoundary.floor(value,previousAnchor) : caret;
                textCursor = caret;
            }
            draggingTextInput = clickedInput;
            requestFullRepaint();
        }
        pressedId = region.id();
        if (internalBody(region.action()).startsWith("slider:")) {
            draggingSlider = region;
            updateSlider(region, event.x() * scale);
        }
        if(popupTarget(region.id()))popupDirty=true;
        else startInteraction(100);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if(layoutHitsStale){pressedId=null;draggingSlider=null;draggingTextInput=null;return true;}
        if(openDialogId!=null&&dialogHitsStale){pressedId=null;draggingSlider=null;draggingTextInput=null;return true;}
        if (openMenuId != null && menuHitsStale) { pressedId = null; draggingSlider = null; draggingTextInput = null; return true; }
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
        if(layoutHitsStale)return true;
        this.mouseX = (int) event.x();
        this.mouseY = (int) event.y();
        float scale = eventLayoutScale();
        if (draggingSlider != null) {
            if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
            reconcileSliderDrag();
            if (draggingSlider != null) updateSlider(draggingSlider, event.x() * scale);
            return true;
        }
        if (draggingTextInput != null) {
            UiEditorViewport editor=interactions.editors.get(draggingTextInput.id());
            if(editor!=null&&editor.stale)return true;
            UiInputRegion current=inputRegion(draggingTextInput.id());
            if(current==null){draggingTextInput=null;return true;}
            draggingTextInput=current;
            if(editor!=null)editor.pointerPlacement=true;
            preferredCaretX = Float.NaN;
            var position = draggingTextInput.caretPositionAt((float) (event.x() * scale), (float) (event.y() * scale));
            textCursor = position.index(); textUpstream = position.upstream();
            requestFullRepaint();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if(layoutHitsStale)return true;
        float scale = eventLayoutScale();
        if (openDialogId != null) {
            if(dialogLayout!=null && dialogLayout.frame().contains((float)(mouseX*scale),(float)(mouseY*scale)))
                scrollDialog(dialogOffset-(float)vertical*dialogLayout.scrollStep(dialogOffset)*3);
            return true;
        }
        if (openMenuId != null) {
            if (menuLayout != null && menuLayout.frame().contains((float)(mouseX * scale), (float)(mouseY * scale)) && vertical != 0) {
                float nextOffset = menuLayout.clamp(menuOffset - (float)vertical * menuLayout.rowHeight() * 3);
                if (nextOffset == menuOffset) return true;
                menuOffset = nextOffset;
                revealMenuFocus = false; menuHitsStale = true;
                // Old painted rows must not accept a click while their new positions await repaint.
                hitRegions = hitRegions.stream().filter(hit -> !hit.id().startsWith("menu:" + openMenuId + ":")).toList();
                hoveredId = null; hoverDirty = true;
                popupDirty = true;
            }
            return true;
        }
        UiHitRegion hit=regionAt(mouseX*scale,mouseY*scale);
        UiInputRegion input=hit==null?null:inputRegion(hit.id());
        UiEditorViewport editor=input==null?null:interactions.editors.get(input.id());
        if(editor!=null && input.multiline() && editor.maximum>0 && vertical!=0) {
            if(editor.scroll(vertical)) {
                VirtualRowKey key=virtualRow(input.id());
                if(key!=null)invalidateVirtualItem(key.listId(),key.index());
                else requestPartialRepaint(input.bounds());
            }
            return true;
        }
        UiScrollRegion region = scrollAt(mouseX * scale, mouseY * scale);
        if (region == null || region.maxOffset() <= 0 || vertical == 0) return !focusScopes.isEmpty();
        String key = region.id();
        long now = System.nanoTime();
        double displayed = scrollOffset(key);
        UiScrollPhysics.Motion active = scrollMotions.get(region.id());
        double previousTarget = active == null ? displayed : active.target();
        UiScrollSettings settings = document.scrollSettings();
        double next = Math.max(0, Math.min(region.maxOffset(), previousTarget - vertical * settings.step()));
        if (next == previousTarget) return false;
        interactions.scrollEnds.navigate(key, next);
        long duration = scaledDuration(settings.smoothingMillis());
        if (duration <= 0) {
            interactions.offsets.put(key, next);
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
        if (openDialogId != null && dialogLayout != null) {
            float line=dialogLayout.scrollStep(dialogOffset);
            float page=Math.max(1,dialogLayout.content().height()-line);
            switch(event.key()) {
                case GLFW.GLFW_KEY_DOWN -> { scrollDialog(dialogOffset+line); return true; }
                case GLFW.GLFW_KEY_UP -> { scrollDialog(dialogOffset-line); return true; }
                case GLFW.GLFW_KEY_PAGE_DOWN -> { scrollDialog(dialogOffset+page); return true; }
                case GLFW.GLFW_KEY_PAGE_UP -> { scrollDialog(dialogOffset-page); return true; }
                case GLFW.GLFW_KEY_HOME -> { scrollDialog(0); return true; }
                case GLFW.GLFW_KEY_END -> { scrollDialog(dialogLayout.maxOffset()); return true; }
            }
        }
        if (handleMenuKey(event)) return true;
        UiInputRegion input = inputRegion(focusedId);
        if (input != null && handleTextKey(input, event)) return true;
        if (focusScopes.isEmpty() && openDialogId == null && openMenuId == null && options.keyHandler().keyPressed(this, event)) return true;
        if (input == null && handleSliderKey(event)) return true;
        if (event.key() == GLFW.GLFW_KEY_TAB) return moveFocus((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
        if (focusedId != null && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER
            || event.key() == GLFW.GLFW_KEY_SPACE)) {
            keyboardRegion(focusedId).ifPresent(region -> runAction(region.action(), false));
            startInteraction(100, focusedId);
            return true;
        }
        if (input == null && focusScopes.isEmpty() && openDialogId == null && openMenuId == null && document.pageShortcuts() && event.modifiers() == 0) {
            int index = event.key() - GLFW.GLFW_KEY_1;
            if (index >= 0 && index <= 8 && index < document.pages().size()) {
                showPage(document.pages().get(index).id());
                return true;
            }
        }
        if (!focusScopes.isEmpty() || openDialogId != null || openMenuId != null) return true;
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
            dialogOffset=0;dialogLayout=null;dialogLayoutKey=null;dialogHitsStale=true;
            focus(null); pendingFocus = new FocusRestore("dialog-cancel", -1, -1);
            requestFullRepaint();
            return;
        }
        if (action.startsWith(DIALOG_CONFIRM_PREFIX)) {
            if (openDialogId == null) return;
            String eventAction = action.substring(DIALOG_CONFIRM_PREFIX.length());
            closeDialog(false);
            eventListener.onEvent("action", eventAction, "");
            return;
        }
        if (action.startsWith(DIALOG_CANCEL_PREFIX)) {
            if (openDialogId == null) return;
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
                closeMenu(false);
                openMenuId = menuId;
                activeMenu = java.util.stream.Stream.concat(menus.stream(), virtualMenus.stream())
                    .filter(menu -> menu.id().equals(menuId)).reduce((first, last) -> last).orElse(null);
                menuOffset = 0; menuLayout = null; revealMenuFocus = true; menuHitsStale = true; menuNavigationFromHover = false;
                if (activeMenu != null && currentMenuSelection(activeMenu) != null)
                    focusedId = "menu:" + menuId + ":" + currentMenuSelection(activeMenu);
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
                if (split >= 0) {
                    String key = UiValueKey.decode(body.substring(0, split));
                    String value = body.substring(split + 1);
                    Object previous = state.put(key, value);
                    requestFullRepaint();
                    if (!Objects.equals(previous, value)) eventListener.onEvent("value", key, value);
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
            if (split >= 0) {
                String key = UiValueKey.decode(body.substring(0, split));
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

    private record SliderRange(String key, double minimum, double maximum) {
        static SliderRange from(UiHitRegion region) {
            String descriptor = internalBody(region.action()).substring("slider:".length());
            int end = descriptor.lastIndexOf(':');
            int start = end < 0 ? -1 : descriptor.lastIndexOf(':', end - 1);
            return new SliderRange(start < 0 ? descriptor : descriptor.substring(0, start),
                start < 0 ? 0 : Double.parseDouble(descriptor.substring(start + 1, end)),
                end < 0 ? 1 : Double.parseDouble(descriptor.substring(end + 1)));
        }
    }

    private boolean handleSliderKey(KeyEvent event) {
        UiHitRegion target = keyboardRegion(focusedId).orElse(null);
        if (target == null || !target.action().startsWith(UiContext.INTERNAL_ACTION_PREFIX + "slider:")) return false;
        // Modified application shortcuts must not silently edit a numeric value.
        if ((event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) return false;
        SliderRange range = SliderRange.from(target);
        Object stored = state.get(range.key());
        double current = stored instanceof Number n && Double.isFinite(n.doubleValue())
            ? Math.clamp(n.doubleValue(), range.minimum(), range.maximum()) : range.minimum();
        double step = (range.maximum() - range.minimum()) / 100;
        double next;
        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> next = current - step;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> next = current + step;
            case GLFW.GLFW_KEY_PAGE_DOWN -> next = current - step * 10;
            case GLFW.GLFW_KEY_PAGE_UP -> next = current + step * 10;
            case GLFW.GLFW_KEY_HOME -> next = range.minimum();
            case GLFW.GLFW_KEY_END -> next = range.maximum();
            default -> { return false; }
        }
        setSliderValue(range, Math.clamp(next, range.minimum(), range.maximum()));
        return true;
    }

    /** A pointer capture belongs to a currently painted control, not a stale rectangle. */
    private void reconcileSliderDrag() {
        if (draggingSlider == null) return;
        UiHitRegion current = region(draggingSlider.id()).orElse(null);
        if (current == null || !Objects.equals(pressedId, current.id()) || !focusAllowed(current.id())
                || !current.action().equals(draggingSlider.action())) {
            if (Objects.equals(pressedId, draggingSlider.id())) pressedId = null;
            draggingSlider = null;
        } else draggingSlider = current;
    }

    private void updateSlider(UiHitRegion region, double mouseX) {
        SliderRange range = SliderRange.from(region);
        setSliderValue(range, range.minimum() + region.fractionAt(mouseX) * (range.maximum() - range.minimum()));
    }

    private void setSliderValue(SliderRange range, double value) {
        Object previous = state.put(range.key(), value);
        if (previous instanceof Number number && number.doubleValue() == value) return;
        eventListener.onEvent("value", range.key(), Double.toString(value));
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
        } else if ((previous == null || popupTarget(previous)) && (next == null || popupTarget(next))
                && (popupTarget(previous) || popupTarget(next))) {
            popupDirty = true;
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
        String trigger = openMenuId;
        hitRegions = hitRegions.stream().filter(hit -> !hit.id().startsWith("menu:" + trigger + ":") && !hit.id().equals(menuBodyId())).toList();
        openMenuId = null; activeMenu = null; menuLayout = null; menuOffset = 0; revealMenuFocus = false; menuHitsStale = false;
        menuOpenedWithPointer = false;
        if (clearFocus) focusedId = null;
        else focus(trigger);
        requestFullRepaint();
        hoverDirty = true;
    }

    private void closeDialog(boolean cancelled) {
        if (openDialogId == null) return;
        dialogBlurScratch.clear();
        dialogLayout=null;dialogLayoutKey=null;dialogOffset=0;dialogHitsStale=false;
        UiDialogRequest request = null;
        for (UiDialogRequest candidate : dialogs) if (openDialogId.equals(candidate.id())) request = candidate;
        if (request == null) for (UiDialogRequest candidate : virtualDialogs) {
            if (openDialogId.equals(candidate.id())) request = candidate;
        }
        openDialogId = null;
        dialogLayout=null;dialogLayoutKey=null;dialogOffset=0;dialogHitsStale=false;
        hitRegions = hitRegions.stream().filter(hit -> !java.util.Set.of("dialog-backdrop", "dialog-body", "dialog-cancel", "dialog-confirm").contains(hit.id())).toList();
        pressedId = null;
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
            if (focusAllowed(tooltip.id()) && tooltip.contains(x, y)) return tooltip.id();
        }
        for (int i = tooltips.size() - 1; i >= 0; i--) {
            UiTooltip tooltip = tooltips.get(i);
            if (focusAllowed(tooltip.id()) && tooltip.contains(x, y)) return tooltip.id();
        }
        return null;
    }

    private boolean hasTooltip(String id) {
        if (id == null) return false;
        for (UiTooltip tooltip : virtualTooltips) if (id.equals(tooltip.id())) return true;
        for (UiTooltip tooltip : tooltips) if (id.equals(tooltip.id())) return true;
        return false;
    }

    /** Focuses a registered, scope-eligible control, revealing it through ordinary scroll ancestors. */
    public boolean focus(String id) {
        if (id != null && (!focusAllowed(id) || (region(id).isEmpty() && !interactions.targets.containsKey(id) && menuItem(id) == null)
                || id.equals("dialog-backdrop") || id.equals("dialog-body") || (openMenuId != null && id.equals(menuBodyId())))) return false;
        var revealed = interactions.reveal(id);
        if (!revealed.isEmpty()) {
            revealed.forEach(scrollMotions::remove);
            requestFullRepaint();
        }
        if (menuItem(id) != null) { revealMenuFocus = true; popupDirty = true; }
        pendingFocus = null;
        if (Objects.equals(focusedId, id)) return true;
        String previous = focusedId;
        focusedId = id;
        preferredCaretX = Float.NaN;
        UiInputRegion input = inputRegion(id);
        revealEditorCaret(id);
        if(input==null)interactions.textLayouts.clear();
        String value = input == null ? "" : editingValue(input);
        textUpstream = false;
        textCursor = value.length();
        textAnchor = textCursor;
        if (id != null && id.startsWith(TOAST_DISMISS_PREFIX)) {
            toastSurfaceDirty = true;
            if (previous != null && !previous.startsWith(TOAST_DISMISS_PREFIX)) {
                if (!requestVirtualRows(previous)) requestPartialRepaint(interactionBounds(previous));
            }
        } else if (popupTarget(id) && popupTarget(previous)) popupDirty = true;
        else if (!requestVirtualRows(id)) requestFullRepaint();
        return true;
    }

    private boolean moveFocus(boolean reverse) {
        List<UiHitRegion> focusable = new ArrayList<>(hitRegions.size() + virtualHitRegions.size());
        for (var target : interactions.targets.values()) focusable.add(target.hit());
        for (var hit : hitRegions) if (!interactions.targets.containsKey(hit.id())) focusable.add(hit);
        if (openDialogId == null && openMenuId == null) focusable.addAll(toastHitRegions);
        focusable.addAll(virtualHitRegions);
        focusable.removeIf(hit -> !focusAllowed(hit.id()) || hit.id().equals("dialog-backdrop") || hit.id().equals("dialog-body")
            || (openMenuId != null && !hit.id().startsWith("menu:" + openMenuId + ":")));
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
        return interactions.inputs.get(id);
    }

    private boolean handleMenuKey(KeyEvent event) {
        if (openMenuId == null || openDialogId != null) return false;
        int key = event.key();
        if (key != GLFW.GLFW_KEY_UP && key != GLFW.GLFW_KEY_DOWN && key != GLFW.GLFW_KEY_TAB
                && key != GLFW.GLFW_KEY_HOME && key != GLFW.GLFW_KEY_END
                && key != GLFW.GLFW_KEY_PAGE_UP && key != GLFW.GLFW_KEY_PAGE_DOWN) return false;
        if ((event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) return false;
        if (activeMenu == null || activeMenu.items().isEmpty()) return true;
        var items = activeMenu.items();
        int current = -1;
        for (int i = 0; i < items.size(); i++) {
            String id = "menu:" + openMenuId + ":" + items.get(i).id();
            if (id.equals(focusedId)) current = i;
        }
        if (menuNavigationFromHover && hoveredId != null) {
            for (int i = 0; i < items.size(); i++)
                if (("menu:" + openMenuId + ":" + items.get(i).id()).equals(hoveredId)) current = i;
        }
        boolean reverse = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_UP || (key == GLFW.GLFW_KEY_TAB && (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
        boolean page = key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN;
        int step = page && menuLayout != null ? Math.max(1, (int)(menuLayout.viewport().height() / menuLayout.rowHeight())) : 1;
        int next = key == GLFW.GLFW_KEY_HOME ? 0 : key == GLFW.GLFW_KEY_END ? items.size() - 1
            : current < 0 ? (reverse ? items.size() - 1 : 0)
            : page ? Math.clamp(current + (reverse ? -step : step), 0, items.size() - 1)
            : Math.floorMod(current + (reverse ? -1 : 1), items.size());
        menuOpenedWithPointer = false; menuNavigationFromHover = false;
        focus("menu:" + openMenuId + ":" + items.get(next).id());
        return true;
    }

    private boolean isMultilineKey(String key) {
        for (UiInputRegion input : interactions.inputs.values()) if (input.multiline() && input.key().equals(key)) return true;
        for (UiInputRegion input : inputRegions) if (input.multiline() && input.key().equals(key)) return true;
        for (UiInputRegion input : virtualInputRegions) if (input.multiline() && input.key().equals(key)) return true;
        return false;
    }

    private String editingValue(UiInputRegion input) {
        String value = stringValue(input.key(), "");
        if (input.multiline()) {
            String normalized = UiTextBoundary.normalizeLines(value);
            if (!normalized.equals(value)) state.put(input.key(), normalized);
            return normalized;
        }
        return value;
    }

    private boolean handleTextKey(UiInputRegion input, KeyEvent event) {
        String value = editingValue(input);
        int length = value.length();
        textCursor = UiTextBoundary.floor(value, textCursor);
        textAnchor = UiTextBoundary.floor(value, textAnchor);
        int modifiers = event.modifiers();
        boolean shortcut = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0
            && (modifiers & GLFW.GLFW_MOD_ALT) == 0;
        boolean word = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
            && (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0
            || System.getProperty("os.name", "").startsWith("Mac") && (modifiers & GLFW.GLFW_MOD_ALT) != 0
            && (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) == 0;
        boolean extend = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (shortcut && (event.key() == GLFW.GLFW_KEY_Z || event.key() == GLFW.GLFW_KEY_Y)) {
            UiTextHistory history = textHistories.get(input.key());
            if (history != null) {
                UiTextHistory.Snapshot current = new UiTextHistory.Snapshot(value, textCursor, textAnchor);
                var restored = event.key() == GLFW.GLFW_KEY_Y || extend ? history.redo(current) : history.undo(current);
                restored.ifPresent(edit -> {
                    revealEditorCaret(input.id());
                    state.put(input.key(), edit.value()); textUpstream = false; textCursor = edit.cursor(); textAnchor = edit.anchor();
                    eventListener.onEvent("value", input.key(), edit.value()); requestFullRepaint();
                });
            }
            return true;
        }
        if (shortcut && event.key() == GLFW.GLFW_KEY_A) {
            revealEditorCaret(input.id());
            textAnchor = 0;
            textCursor = length;
            requestFullRepaint();
            return true;
        }
        if (shortcut && event.key() == GLFW.GLFW_KEY_C) {
            if (!input.masked()) copySelection(value);
            return true;
        }
        if (shortcut && event.key() == GLFW.GLFW_KEY_X) {
            if (!input.masked()) {
                copySelection(value);
                replaceTextSelection(input, "");
            }
            return true;
        }
        if (shortcut && event.key() == GLFW.GLFW_KEY_V) {
            String pasted = Minecraft.getInstance().keyboardHandler.getClipboard();
            replaceTextSelection(input, pasted);
            return true;
        }
        boolean pageKey = event.key() == GLFW.GLFW_KEY_PAGE_UP || event.key() == GLFW.GLFW_KEY_PAGE_DOWN;
        if (input.multiline() && (pageKey || event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_DOWN)) {
            if (!Float.isFinite(preferredCaretX)) preferredCaretX = input.caretX(textCursor, textUpstream);
            float column = preferredCaretX;
            int direction = event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1;
            UiEditorViewport editor = interactions.editors.get(input.id());
            float viewportHeight = editor != null && editor.viewport != null ? editor.viewport.height() : input.bounds().height();
            var target = pageKey ? input.pageCaret(textCursor, textUpstream, direction, column, viewportHeight)
                : input.verticalCaret(textCursor, textUpstream, direction, column);
            moveTextCursor(UiTextBoundary.floor(value, target.index()), extend);
            textUpstream = target.upstream();
            preferredCaretX = column;
            return true;
        }
        preferredCaretX = Float.NaN;
        if (event.key() == GLFW.GLFW_KEY_LEFT) {
            moveTextCursor(!extend && textCursor != textAnchor ? Math.min(textCursor, textAnchor)
                : word ? UiTextBoundary.previousWord(value, textCursor) : UiTextBoundary.previous(value, textCursor), extend);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT) {
            moveTextCursor(!extend && textCursor != textAnchor ? Math.max(textCursor, textAnchor)
                : word ? UiTextBoundary.nextWord(value, textCursor) : UiTextBoundary.next(value, textCursor), extend);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_HOME) {
            var target = input.multiline() && !shortcut ? input.lineEdge(textCursor, textUpstream, false)
                : new UiInputRegion.Caret(0, false);
            moveTextCursor(target.index(), extend); textUpstream = target.upstream();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_END) {
            var target = input.multiline() && !shortcut ? input.lineEdge(textCursor, textUpstream, true)
                : new UiInputRegion.Caret(length, false);
            moveTextCursor(target.index(), extend); textUpstream = target.upstream();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (textCursor != textAnchor) replaceTextSelection(input, "");
            else if (textCursor > 0) {
                UiTextHistory.Snapshot before = new UiTextHistory.Snapshot(value, textCursor, textAnchor);
                textAnchor = word ? UiTextBoundary.previousWord(value, textCursor) : UiTextBoundary.previous(value, textCursor);
                replaceTextSelection(input, "", before);
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE) {
            if (textCursor != textAnchor) replaceTextSelection(input, "");
            else if (textCursor < length) {
                UiTextHistory.Snapshot before = new UiTextHistory.Snapshot(value, textCursor, textAnchor);
                textAnchor = word ? UiTextBoundary.nextWord(value, textCursor) : UiTextBoundary.next(value, textCursor);
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
        revealEditorCaret(focusedId);
        preferredCaretX = Float.NaN;
        textCursor = next;
        textUpstream = false;
        if (!extend) textAnchor = next;
        requestFullRepaint();
    }

    private void copySelection(String value) {
        int start = Math.min(textCursor, textAnchor);
        int end = Math.max(textCursor, textAnchor);
        if (start != end) Minecraft.getInstance().keyboardHandler.setClipboard(value.substring(start, end));
    }

    private void replaceTextSelection(UiInputRegion input, String replacement) {
        replaceTextSelection(input, replacement, new UiTextHistory.Snapshot(editingValue(input), textCursor, textAnchor));
    }

    private void replaceTextSelection(UiInputRegion input, String replacement, UiTextHistory.Snapshot before) {
        revealEditorCaret(input.id());
        preferredCaretX = Float.NaN;
        String value = editingValue(input);
        int start = Math.min(UiTextBoundary.floor(value, textCursor), UiTextBoundary.floor(value, textAnchor));
        int end = Math.max(UiTextBoundary.floor(value, textCursor), UiTextBoundary.floor(value, textAnchor));
        String safe = input.multiline() ? cleanMultiline(replacement) : cleanSingleLine(replacement);
        int available = Math.max(0, input.maxLength() - (value.length() - (end - start)));
        if (safe.length() > available) safe = UiTextBoundary.truncate(safe, available);
        String next = value.substring(0, start) + safe + value.substring(end);
        textUpstream = false;
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
                scrollOffset(entry.getKey()), entry.getValue(), now);
            interactions.offsets.put(entry.getKey(), frame.displayed());
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
        boolean popupOnly = controlIds != null && controlIds.length > 0;
        boolean hasPopup = false;
        if (popupOnly) for (String id : controlIds) {
            if (id == null) continue;
            if (!popupTarget(id)) popupOnly = false;
            else hasPopup = true;
        }
        if (popupOnly && hasPopup) { popupDirty = true; return; }
        boolean toastOnly = controlIds != null && controlIds.length > 0;
        boolean hasToast = false;
        if (toastOnly) for (String id : controlIds) {
            if (id == null) continue;
            if (!id.startsWith(TOAST_DISMISS_PREFIX)) toastOnly = false;
            else hasToast = true;
        }
        if (toastOnly && hasToast) { toastSurfaceDirty = true; return; }
        long duration = scaledDuration(milliseconds);
        markAnimatedVirtualRowsDirty();animatedVirtualRows.clear();
        boolean continuingBase = interactionPaintsBase && interactionEnd > System.nanoTime();
        UiRect previousBounds = interactionRepaintBounds;
        boolean hasVirtual = false;
        boolean virtualOnly = controlIds != null && controlIds.length > 0;
        if (virtualOnly) {
            for (String id : controlIds) {
                if (id == null) continue;
                if (virtualRow(id) == null) { virtualOnly = false; break; }
                hasVirtual = true;
            }
        }
        virtualOnly &= hasVirtual;
        if (virtualOnly) {
            interactionRepaintBounds = continuingBase ? previousBounds : null;
            requestVirtualRows(controlIds);
        } else {
            interactionRepaintBounds = interactionBounds(controlIds);
            if (continuingBase) {
                interactionRepaintBounds = previousBounds == null || interactionRepaintBounds == null
                    ? null : union(previousBounds, interactionRepaintBounds);
            }
            requestInteractionRepaint();
        }
        interactionPaintsBase = !virtualOnly || continuingBase;
        if (virtualOnly && continuingBase) requestInteractionRepaint();
        if(controlIds!=null)for(String id:controlIds) {
            VirtualRowKey key=virtualRow(id);
            if(key!=null) {
                invalidateVirtualItem(key.listId(),key.index());
                if(duration>0)animatedVirtualRows.add(key);
            }
        }
        if (duration <= 0) {
            markAnimatedVirtualRowsDirty();animatedVirtualRows.clear();
            interactionEnd = 0;
            interactionPaintsBase=false;
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
            interactions.beginPaint();
            UiContext shellContext = new UiContext(canvas, document.theme(), layoutWidth, layoutHeight,
                page.accent(), new UiRect(0, 0, layoutWidth, layoutHeight), hoveredId, pressedId,
                ease(now, transitionStart, transitionEnd), ease(now, interactionStart, interactionEnd),
                transitionEnd != 0 || interactionEnd != 0, paintCount, state, nextHits, nextNativeBounds, nextNativeOverlayLayers,
                nextAnimationBounds, nextScrollRegions, nextInputRegions, nextTooltips,
                new UiTextInputState(focusedId, textCursor, textAnchor, textUpstream),
                nextMenus,
                nextDialogs,
                animationClock,
                guiScale() / (double) scale,
                compositeVirtualLists ? nextVirtualLists : null).withInteractions(interactions);

            UiRect content = document.shell().paint(shellContext, document, page);
            if (content == null) content = new UiRect(0, 0, layoutWidth, layoutHeight);
            UiContext pageContext = shellContext.withContent(content);
            UiRect clip = content;
            canvas.withClip(clip.x(), clip.y(), clip.width(), clip.height(),
                () -> page.painter().paint(pageContext));
            for (String id : interactions.scrollEnds.takeResolved()) scrollMotions.remove(id);
            rootAnimationIds=animationClock.usedIds();

            if (compositeVirtualLists && !nextVirtualLists.isEmpty()) {
                reconcileVirtualLayers(nextVirtualLists, qualityScale, now);
                virtualCompositeActive = true;
            } else {
                if (compositeVirtualLists) closeVirtualLayers();
                virtualCompositeActive = false;
                clearVirtualInteractions();
            }

            toastSurfaceDirty = true;
            rebuildPopupSurface(canvas, nextMenus, nextDialogs, nextHits, layoutWidth, layoutHeight);

            hitRegions = List.copyOf(nextHits);
            reconcileSliderDrag();
            scrollRegions = List.copyOf(nextScrollRegions);
            inputRegions = List.copyOf(nextInputRegions);
            pruneEditorViewports();
            tooltips = List.copyOf(nextTooltips);
            tooltipSurfaceDirty = true;
            menus = List.copyOf(nextMenus);
            dialogs = List.copyOf(nextDialogs);
            if(layoutHitsStale && focusedId!=null && region(focusedId).isEmpty()
                    && !interactions.targets.containsKey(focusedId) && menuItem(focusedId)==null)focus(null);
            if(layoutHitsStale) {
                UiInputRegion input=inputRegion(focusedId);
                if(input!=null) {
                    String value=stringValue(input.key(),"");
                    textCursor=UiTextBoundary.floor(value,textCursor);textAnchor=UiTextBoundary.floor(value,textAnchor);
                }
            }
            layoutHitsStale=false;
            resolvePendingFocus();
            pendingFocus = null;
            nativeBounds.clear();
            nativeBounds.putAll(nextNativeBounds);
            nativeOverlayLayers.clear();
            nativeOverlayLayers.putAll(nextNativeOverlayLayers);
            customAnimationsActive = animationClock.active();
            activeCustomAnimationIds = animationClock.activeIds();
            customAnimationBounds = Map.copyOf(nextAnimationBounds);
            pruneAnimations();
        } finally {
            graphics.dispose();
        }
        if (openDialogId == null) dialogBlurScratch.clear();
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
        if (request == null) return; // Missing sources are closed before popup layout.
        UiDialog dialog = request.dialog();
        UiDialogStyle style = dialog.style();
        popupBackdrop = toastColor(style == null ? null : style.backdrop(), 0x99000000);
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
        hits.add(new UiHitRegion("dialog-backdrop", UiContext.internalAction(DIALOG_CANCEL_PREFIX + dialog.cancelAction()),
            0, 0, layoutWidth, layoutHeight));
        if(dialogLayout==null)return;
        dialogHitsStale=false;
        UiRect card=dialogLayout.frame(), content=dialogLayout.content();
        hits.add(new UiHitRegion("dialog-body",UiContext.internalAction("dialog-body"),card.x(),card.y(),card.width(),card.height()));
        canvas.shadow(shadow,12,0,4,card.x(),card.y(),card.width(),card.height(),radius);
        canvas.roundRect(surface,card.x(),card.y(),card.width(),card.height(),radius);
        canvas.strokeRoundRect(border,.9F,card.x(),card.y(),card.width(),card.height(),radius);
        canvas.withClip(content.x(),content.y(),content.width()-Math.min(6,content.width()/4),content.height(),()->{
            for(int i=dialogLayout.firstVisible(dialogOffset);i<dialogLayout.lines().size();i++) {
                var line=dialogLayout.lines().get(i);
                if(line.top()>=dialogOffset+content.height())break;
                var typography=line.title()?dialogLayout.titleText():dialogLayout.messageText();
                canvas.text(line.text(),content.x(),content.y()+line.top()-dialogOffset,typography.size(),
                    line.title()?title:message,typography.fontStyle(),typography.family());
            }
        });
        if(dialogLayout.maxOffset()>0) {
            float thumb=Math.min(content.height(),Math.max(8,content.height()*content.height()/dialogLayout.contentHeight()));
            float y=content.y()+(content.height()-thumb)*dialogOffset/dialogLayout.maxOffset();
            canvas.roundRect(document.theme().mutedText(),content.right()-2,y,2,thumb,1);
        }
        drawDialogAction(canvas,"dialog-cancel",dialogLayout.cancelLabel(),dialogLayout.cancel(),cancelBackground,cancelBorder,cancelText,radius);
        drawDialogAction(canvas,"dialog-confirm",dialogLayout.confirmLabel(),dialogLayout.confirm(),confirmBackground,confirmBackground,confirmText,radius);
        UiRect cancel=dialogLayout.cancel(),confirm=dialogLayout.confirm();
        hits.add(new UiHitRegion("dialog-cancel",UiContext.internalAction(DIALOG_CANCEL_PREFIX+dialog.cancelAction()),cancel.x(),cancel.y(),cancel.width(),cancel.height()));
        hits.add(new UiHitRegion("dialog-confirm",UiContext.internalAction(DIALOG_CONFIRM_PREFIX+dialog.confirmAction()),confirm.x(),confirm.y(),confirm.width(),confirm.height()));
    }

    private void drawDialogAction(UiCanvas canvas,String id,String label,UiRect box,int background,int border,int text,float radius) {
        float curve=Math.min(Math.min(8,radius),Math.min(box.width(),box.height())/2);
        canvas.roundRect(background,box.x(),box.y(),box.width(),box.height(),curve);
        if(id.equals(hoveredId)||id.equals(pressedId))canvas.roundRect(id.equals(pressedId)?0x20000000:0x18FFFFFF,box.x(),box.y(),box.width(),box.height(),curve);
        canvas.strokeRoundRect(border,.8F,box.x()+1,box.y()+1,Math.max(0,box.width()-2),Math.max(0,box.height()-2),curve);
        if(id.equals(focusedId)&&box.width()>6&&box.height()>6)
            canvas.strokeRoundRect(text,1.2F,box.x()+3,box.y()+3,box.width()-6,box.height()-6,Math.max(0,curve-2));
        var typography=dialogLayout.buttonText();
        canvas.withClip(box.x(),box.y(),box.width(),box.height(),()->
            canvas.centeredText(label,box.centerX(),box.centerY(),typography.size(),text,typography.fontStyle(),typography.family()));
    }

    private void scrollDialog(float target) {
        if(dialogLayout==null)return;
        float next=dialogLayout.clamp(target);
        if(next!=dialogOffset){dialogOffset=next;popupDirty=true;}
    }

    private void layoutDialog(UiCanvas canvas,UiDialogRequest request,int width,int height) {
        DialogLayoutKey next=new DialogLayoutKey(request,width,height,surfaceQualityScale,UiCanvas.fontRevision());
        if(!next.equals(dialogLayoutKey)) {
            if(canvas!=null)dialogLayout=UiDialogLayout.measure(canvas,request.dialog(),document.theme().fontFamily(),width,height);
            else {
                Graphics2D measure=surfaceImage.createGraphics();
                try {measure.scale(surfaceQualityScale,surfaceQualityScale);dialogLayout=UiDialogLayout.measure(new UiCanvas(measure),request.dialog(),document.theme().fontFamily(),width,height);}
                finally {measure.dispose();}
            }
            dialogLayoutKey=next;
            dialogLayoutCount++;
            // A newly registered font can alter pixels without changing the request or card bounds.
            popupPaintState=null;
        }
        dialogOffset=dialogLayout.clamp(dialogOffset);
    }

    private void layoutMenu(UiCanvas canvas, List<UiMenu> candidates, int layoutWidth, int layoutHeight) {
        if (openMenuId == null) return;
        UiMenu menu = null;
        for (int i = candidates.size() - 1; i >= 0; i--) {
            if (openMenuId.equals(candidates.get(i).id())) {
                menu = candidates.get(i);
                break;
            }
        }
        if (menu == null || menu.items().isEmpty()) {
            closeMenu(false);
            return;
        }
        activeMenu = menu;
        menuHitsStale = false;
        if (menu.id().equals(focusedId) && menu.selectedItemId() != null) {
            focusedId = "menu:" + menu.id() + ":" + menu.selectedItemId();
            revealMenuFocus = true;
        }
        UiMenuStyle style = menu.style();
        String family = style.fontFamily() == null ? document.theme().fontFamily() : style.fontFamily();
        float widest = menu.anchor().width();
        for (UiMenuItem item : menu.items())
            widest = Math.max(widest, canvas.textWidth(item.label(), style.textSize(), style.fontStyle(), family) + style.paddingX() * 2);
        UiPopupLayout previousLayout = menuLayout;
        menuLayout = UiPopupLayout.menu(menu.anchor(), widest, menu.items().size(), layoutWidth, layoutHeight, style);
        if (menuLayout.maxOffset() > 0)
            menuLayout = UiPopupLayout.menu(menu.anchor(), widest + 8, menu.items().size(), layoutWidth, layoutHeight, style);
        menuOffset = menuLayout.clamp(menuOffset);
        if (revealMenuFocus || previousLayout == null || menuLayout.rowHeight() != previousLayout.rowHeight()
                || menuLayout.viewport().height() != previousLayout.viewport().height()
                || menuLayout.viewport().width() != previousLayout.viewport().width()) {
            for (int i = 0; i < menu.items().size(); i++)
                if (("menu:" + menu.id() + ":" + menu.items().get(i).id()).equals(focusedId))
                    menuOffset = menuLayout.reveal(menuOffset, i);
        }
        revealMenuFocus = false;
    }

    private void drawMenu(UiCanvas canvas, List<UiHitRegion> hits) {
        if (openMenuId == null || activeMenu == null || menuLayout == null) return;
        UiMenu menu = activeMenu;
        UiRect frame = menuLayout.frame(), viewport = menuLayout.viewport();
        UiMenuStyle style = menu.style();
        String family = style.fontFamily() == null ? document.theme().fontFamily() : style.fontFamily();
        canvas.shadow(0x72000000, 7, 0, 3, frame.x(), frame.y(), frame.width(), frame.height(), style.radius());
        canvas.roundRect(style.background() == null ? document.theme().raisedSurface() : style.background(), frame.x(), frame.y(), frame.width(), frame.height(), style.radius());
        if (style.borderWidth() > 0) canvas.strokeRoundRect(style.border() == null ? document.theme().border() : style.border(), style.borderWidth(), frame.x(), frame.y(), frame.width(), frame.height(), style.radius());
        hits.add(new UiHitRegion(menuBodyId(), UiContext.internalAction("menu-body"), frame.x(), frame.y(), frame.width(), frame.height()));
        float gutter = menuLayout.maxOffset() > 0 ? Math.min(8, viewport.width()) : 0;
        UiMenu paintedMenu = menu;
        canvas.withClip(viewport.x(), viewport.y(), viewport.width() - gutter, viewport.height(), () -> {
            for (int i = 0; i < paintedMenu.items().size(); i++) {
                UiRect hit = menuLayout.row(i, menuOffset);
                if (hit.height() <= 0) continue;
                UiMenuItem item = paintedMenu.items().get(i);
                float rowY = viewport.y() + i * menuLayout.rowHeight() - menuOffset;
                String itemId = "menu:" + paintedMenu.id() + ":" + item.id();
                if (itemId.equals(hoveredId) || itemId.equals(focusedId)) canvas.roundRect(style.highlight() == null ? UiCanvas.fade(document.theme().accent(), .18F) : style.highlight(),
                    frame.x() + style.rowInset(), rowY, Math.max(0, frame.width() - style.rowInset() * 2 - gutter), menuLayout.rowHeight(), style.rowRadius());
                float textY = rowY + Math.max(0, (menuLayout.rowHeight() - canvas.textHeight(style.textSize(), style.fontStyle(), family)) / 2);
                canvas.withClip(frame.x(), rowY, Math.max(0, frame.width() - gutter), menuLayout.rowHeight(), () ->
                    canvas.text(item.label(), frame.x() + style.paddingX(), textY, style.textSize(), style.text() == null ? document.theme().text() : style.text(), style.fontStyle(), family));
                hits.add(new UiHitRegion(itemId, UiContext.internalAction(MENU_ITEM_PREFIX + item.action()),
                    hit.x(), hit.y(), Math.max(0, hit.width() - gutter), hit.height()));
            }
        });
        if (menuLayout.maxOffset() > 0 && viewport.width() > 8) {
            float thumb = Math.max(10, viewport.height() * viewport.height() / menuLayout.contentHeight());
            thumb = Math.min(thumb, viewport.height());
            float y = viewport.y() + (viewport.height() - thumb) * menuOffset / menuLayout.maxOffset();
            canvas.roundRect(document.theme().mutedText(), viewport.right() - 4, y, 2, thumb, 1);
        }
    }

    private void rebuildPopupSurface(UiCanvas baseCanvas, List<UiMenu> nextMenus,
                                     List<UiDialogRequest> nextDialogs, List<UiHitRegion> hits,
                                     int layoutWidth, int layoutHeight) {
        if (baseCanvas != null) layoutMenu(baseCanvas, nextMenus, layoutWidth, layoutHeight);
        else {
            Graphics2D measure = surfaceImage.createGraphics();
            try {
                measure.scale(surfaceQualityScale, surfaceQualityScale);
                layoutMenu(new UiCanvas(measure), nextMenus, layoutWidth, layoutHeight);
            }
            finally { measure.dispose(); }
        }
        UiRect bounds = openMenuId != null && menuLayout != null ? menuLayout.frame() : null;
        UiDialogRequest dialog = null;
        for (UiDialogRequest candidate : nextDialogs) if (candidate.id().equals(openDialogId)) dialog = candidate;
        if (openDialogId != null && dialog == null) closeDialog(false);
        popupBackdrop = 0;
        if (dialog != null) {
            layoutDialog(baseCanvas,dialog,layoutWidth,layoutHeight);
            UiRect card=dialogLayout.frame();
            bounds = bounds == null ? card : union(bounds, card);
            // Retained page chrome is blurred once; native content is dimmed by the GPU backdrop.
            if (baseCanvas != null) baseCanvas.backdropBlur(2, 0, 0, layoutWidth, layoutHeight, dialogBlurScratch);
        }
        if (bounds == null) { closePopupSurface(); return; }
        bounds = expandAndClamp(bounds, layoutWidth, layoutHeight, 20);
        // Snap the outer surface to GUI pixels so its final blit never rounds each edge differently.
        float x = Math.max(0, (int) Math.floor(bounds.x() / layoutScale)) * layoutScale;
        float y = Math.max(0, (int) Math.floor(bounds.y() / layoutScale)) * layoutScale;
        float right = Math.min(width, (int) Math.ceil(bounds.right() / layoutScale)) * layoutScale;
        float bottom = Math.min(height, (int) Math.ceil(bounds.bottom() / layoutScale)) * layoutScale;
        bounds = new UiRect(x, y, right - x, bottom - y);
        PopupPaintState nextState = new PopupPaintState(openMenuId == null ? null : activeMenu, dialog, bounds,
            menuOffset, dialogOffset, popupVisualId(hoveredId), popupVisualId(focusedId), popupVisualId(pressedId), surfaceQualityScale);
        if (popupSurface != null && nextState.equals(popupPaintState)) {
            hits.addAll(popupHits); popupDirty = false; dialogHitsStale=false; menuHitsStale=false;
            if (dialog != null) popupBackdrop = toastColor(dialog.dialog().style() == null ? null : dialog.dialog().style().backdrop(), 0x99000000);
            return;
        }
        int w = Math.max(1, Math.round(bounds.width() * surfaceQualityScale));
        int h = Math.max(1, Math.round(bounds.height() * surfaceQualityScale));
        if (popupImage == null || popupImage.getWidth() != w || popupImage.getHeight() != h) {
            closePopupSurface();
            popupSurface = new DynamicTexture(() -> "pane-popup-overlay", w, h, true);
            retainedTextureAllocationCount++;
            popupImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            popupPixels = ((DataBufferInt) popupImage.getRaster().getDataBuffer()).getData();
        } else Arrays.fill(popupPixels, 0);
        int hitStart = hits.size();
        Graphics2D painter = popupImage.createGraphics();
        try {
            painter.scale(surfaceQualityScale, surfaceQualityScale);
            painter.translate(-bounds.x(), -bounds.y());
            UiCanvas canvas = new UiCanvas(painter, popupImage);
            drawMenu(canvas, hits);
            drawDialog(canvas, nextDialogs, hits, layoutWidth, layoutHeight);
        } finally { painter.dispose(); }
        var target = popupSurface.getPixels().getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer();
        UiPixelTransfer.copy(popupPixels, target, w, 0, 0, w, h);
        popupSurface.upload();
        popupUploadPixels += (long) w * h;
        popupLayoutBounds = bounds;
        popupHits = List.copyOf(hits.subList(hitStart, hits.size()));
        popupPaintState = nextState; popupDirty = false;
    }

    private void renderPopupOverlay(GuiGraphicsExtractor graphics) {
        if (popupDirty && surfaceImage != null) {
            var hits = new ArrayList<>(hitRegions);
            hits.removeAll(popupHits);
            rebuildPopupSurface(null, menus, dialogs, hits, surfaceLayoutWidth, surfaceLayoutHeight);
            hitRegions = List.copyOf(hits);
            if (hoverDirty) { hoverDirty = false; updateHover(mouseX * layoutScale, mouseY * layoutScale, false); }
        }
        if (popupSurface == null || popupLayoutBounds == null || openMenuId == null && openDialogId == null) return;
        graphics.nextStratum();
        if (openDialogId != null && popupBackdrop != 0) {
            graphics.fill(0, 0, width, height, popupBackdrop);
            graphics.nextStratum();
        }
        graphics.blit(popupSurface.getTextureView(), surfaceSampler,
            Math.round(popupLayoutBounds.x() / layoutScale), Math.round(popupLayoutBounds.y() / layoutScale),
            Math.round(popupLayoutBounds.right() / layoutScale), Math.round(popupLayoutBounds.bottom() / layoutScale),
            0, 1, 0, 1);
    }

    private void closePopupSurface() {
        if (popupSurface != null) popupSurface.close();
        popupSurface = null; popupImage = null; popupPixels = null; popupLayoutBounds = null;
        popupBackdrop = 0; popupDirty = false; popupPaintState = null; popupHits = List.of();
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
        if (hoveredId == null || !focusAllowed(hoveredId)) return null;
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
                                        long now) {
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
                scrollOffset(request.id)));
            ensureVirtualRows(layer, now);
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

    private boolean ensureVirtualRows(VirtualListLayer layer, long now) {
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
                renderVirtualRow(layer, row, now);
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

    private void renderVirtualRow(VirtualListLayer layer, VirtualRowSurface row, long now) {
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
        Map<String, UiButtonState> rowButtons = new HashMap<>();
        try {
            graphics.scale(layer.qualityScale, layer.qualityScale);
            float rowY = layer.request.viewport.y() + row.index * layer.request.stride();
            graphics.translate(-layer.request.viewport.x(), -rowY);
            UiCanvas canvas = new UiCanvas(graphics, row.image);
            UiAnimationClock rowClock = new UiAnimationClock(animations, now, motionScale());
            UiVirtualListRequest.DynamicState dynamic = new UiVirtualListRequest.DynamicState(
                hoveredId, pressedId, ease(now, transitionStart, transitionEnd),
                ease(now, interactionStart, interactionEnd), transitionEnd != 0 || interactionEnd != 0,
                paintCount, new UiTextInputState(focusedId, textCursor, textAnchor, textUpstream), rowClock);
            layer.request.paintRow(canvas, row.index, dynamic,
                new UiVirtualListRequest.RowOutput(hits, nativeRows, nativeOverlayRows, scrolls, inputs, tips, rowMenus, rowDialogs, rowButtons));
            row.customAnimating = rowClock.active();
            row.animationIds=rowClock.usedIds();
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
        row.buttons = Map.copyOf(rowButtons);
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
        boolean interactionsChanged = false;
        for (VirtualListLayer layer : virtualLayers.values()) {
            if (ensureVirtualRows(layer, now)) interactionsChanged = true;
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
        Map<String, UiButtonState> rowButtons = new HashMap<>();
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
                        input.multiline(), xs, ys,
                        input.lines().stream().map(line -> line.translate(0, shiftY)).toList(), input.masked()));
                }
                for (UiTooltip tooltip : row.tooltips) {
                    UiRect moved = clipped(tooltip.anchor().translate(0, shiftY), clip);
                    if (moved.width() > 0 && moved.height() > 0) tips.add(new UiTooltip(tooltip.id(), tooltip.text(), moved));
                }
                for (UiMenu menu : row.menus) {
                    UiRect moved = clipped(menu.anchor().translate(0, shiftY), clip);
                    if (moved.width() > 0 && moved.height() > 0) rowMenus.add(new UiMenu(menu.id(), moved, menu.items(), menu.selectedItemId(), menu.style()));
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
        reconcileSliderDrag();
        pruneEditorViewports();
        pruneAnimations();
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
                scrollOffset(request.id)));
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
        Set<String> animationIds=Set.of();
        Map<String, UiButtonState> buttons = Map.of();
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
            animationIds=Set.of();
            clearInteractions();
        }

        void clearInteractions() {
            buttons = Map.of();
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
        dialogBlurScratch.clear();
        closePopupSurface();
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
        pressedId = null; draggingSlider = null; draggingTextInput = null;
        nativeText.clear(); lastNativeTextCount = 0;
        interactions.scrollEnds.cancelPending();
        animations.clear();rootAnimationIds=Set.of();customAnimationsActive=false;
        interactions.editors.clear();
        interactions.textLayouts.clear();
        closeSurface();
        openMenuId = null; activeMenu = null; menuLayout = null; menuOffset = 0;
        openDialogId = null;
        dialogLayout=null;dialogLayoutKey=null;dialogOffset=0;dialogHitsStale=false;
        requestFullRepaint();
        hoverDirty = true;
        scrollMotions.clear();
        hitRegions = List.of();
        interactions.textLayouts.clear();
        interactions.beginPaint();
        scrollRegions = List.of();
        inputRegions = List.of();
        draggingTextInput = null;
        nativeBounds.clear();
        focusScopes.clear(); pendingFocus = null; dialogRestore = null; textHistories.clear();
        super.removed();
    }

    @Override public boolean isPauseScreen() { return options.pauseGame(); }
    @Override public boolean isInGameUi() { return true; }
}
