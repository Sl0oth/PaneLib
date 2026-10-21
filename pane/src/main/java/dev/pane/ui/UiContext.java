package dev.pane.ui;

import java.awt.Font;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/** Drawing, state, hit testing, and native-overlay registration for one page paint. */
public final class UiContext {
    static final String INTERNAL_ACTION_PREFIX = "\u0000pane:";
    public enum ButtonStyle { PRIMARY, SECONDARY, GHOST, DANGER }
    public enum Shape { SQUARE, ROUNDED, SOFT, PILL }

    private static final UiButtonStyle DEFAULT_BUTTON_STYLE = UiButtonStyle.builder().build();

    private UiInteractionState interactions = new UiInteractionState();
    private UiRect focusClip;
    private java.awt.Shape affineFocusClip;
    private final UiCanvas canvas;
    private final UiTheme theme;
    private final int width;
    private final int height;
    private final int accent;
    private final UiRect content;
    private final String hoveredId;
    private final String pressedId;
    private final float motion;
    private final float interactionMotion;
    private final boolean animating;
    private final int paintCount;
    private final Map<String, Object> state;
    private final List<UiHitRegion> hitRegions;
    private final Map<String, NativeBounds> nativeBounds;
    private final Map<String, UiRect> nativeOverlayLayers;
    private final Map<String, UiRect> animationBounds;
    private final List<UiScrollRegion> scrollRegions;
    private final List<UiInputRegion> inputRegions;
    private final List<UiTooltip> tooltips;
    private final List<UiMenu> menus;
    private final List<UiDialogRequest> dialogs;
    private final UiTextInputState textInput;
    private final UiAnimationClock animationClock;
    private final double nativeScale;
    private final float offsetX;
    private final float offsetY;
    private final UiRect inputClip;
    private final List<UiVirtualListRequest> virtualLists;

    UiContext(UiCanvas canvas, UiTheme theme, int width, int height, int accent, UiRect content,
              String hoveredId, String pressedId, float motion, float interactionMotion,
              boolean animating, int paintCount, Map<String, Object> state, List<UiHitRegion> hitRegions,
              Map<String, NativeBounds> nativeBounds, Map<String, UiRect> nativeOverlayLayers,
              Map<String, UiRect> animationBounds,
              List<UiScrollRegion> scrollRegions,
              List<UiInputRegion> inputRegions, List<UiTooltip> tooltips, UiTextInputState textInput,
              List<UiMenu> menus, List<UiDialogRequest> dialogs, UiAnimationClock animationClock,
              double nativeScale, List<UiVirtualListRequest> virtualLists) {
        this(canvas, theme, width, height, accent, content, hoveredId, pressedId, motion, interactionMotion,
            animating, paintCount, state, hitRegions, nativeBounds, nativeOverlayLayers, animationBounds, scrollRegions, inputRegions, textInput,
            tooltips, menus, dialogs, animationClock, nativeScale, 0, 0, null, virtualLists);
    }

    UiContext(UiCanvas canvas, UiTheme theme, int width, int height, int accent, UiRect content,
                      String hoveredId, String pressedId, float motion, float interactionMotion,
                      boolean animating, int paintCount, Map<String, Object> state, List<UiHitRegion> hitRegions,
                      Map<String, NativeBounds> nativeBounds, Map<String, UiRect> nativeOverlayLayers,
                      Map<String, UiRect> animationBounds,
                      List<UiScrollRegion> scrollRegions,
                      List<UiInputRegion> inputRegions, UiTextInputState textInput, List<UiTooltip> tooltips,
                      List<UiMenu> menus, List<UiDialogRequest> dialogs, UiAnimationClock animationClock,
                      double nativeScale,
                      float offsetX, float offsetY, UiRect inputClip,
                      List<UiVirtualListRequest> virtualLists) {
        this.canvas = canvas;
        this.theme = theme;
        this.width = width;
        this.height = height;
        this.accent = accent;
        this.content = content;
        this.hoveredId = hoveredId;
        this.pressedId = pressedId;
        this.motion = motion;
        this.interactionMotion = interactionMotion;
        this.animating = animating;
        this.paintCount = paintCount;
        this.state = state;
        this.hitRegions = hitRegions;
        this.nativeBounds = nativeBounds;
        this.nativeOverlayLayers = nativeOverlayLayers;
        this.animationBounds = animationBounds;
        this.scrollRegions = scrollRegions;
        this.inputRegions = inputRegions;
        this.tooltips = tooltips;
        this.menus = menus;
        this.dialogs = dialogs;
        this.textInput = textInput;
        this.animationClock = animationClock;
        this.nativeScale = nativeScale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.inputClip = inputClip;
        this.virtualLists = virtualLists;
    }

    UiContext withInteractions(UiInteractionState shared) { interactions = shared; return this; }
    private UiContext inherit(UiContext child) {
        child.interactions = interactions; child.focusClip = focusClip; child.affineFocusClip = affineFocusClip; return child;
    }

    /** Same drawing surface and state, re-aimed at a different content rectangle. */
    UiContext withContent(UiRect newContent) {
        return inherit(new UiContext(canvas, theme, width, height, accent, newContent, hoveredId, pressedId,
            motion, interactionMotion, animating, paintCount, state, hitRegions, nativeBounds, nativeOverlayLayers, animationBounds, scrollRegions,
            inputRegions, textInput, tooltips, menus, dialogs, animationClock, nativeScale,
            offsetX, offsetY, inputClip, virtualLists));
    }

    public UiCanvas canvas() { return canvas; }
    public UiTheme theme() { return theme; }
    public int width() { return width; }
    public int height() { return height; }
    public int accent() { return accent; }
    public UiRect content() { return content; }
    public float contentX() { return content.x(); }
    public float contentY() { return content.y(); }
    public float contentWidth() { return content.width(); }
    public float contentHeight() { return content.height(); }

    /**
     * Eased 0..1 progress of the current <em>page</em> transition, or 1 when none is running.
     * Use this for entrance motion. It deliberately does not restart on hover, so pointing at
     * a control cannot replay a page-level animation.
     */
    public float motion() { return motion; }

    /**
     * Eased 0..1 progress of the current hover or press transition. Use this for control-level
     * feedback such as hover tints.
     */
    public float interactionMotion() { return interactionMotion; }

    /** True while a finite animation is still scheduling repaints. */
    public boolean animating() { return animating || animationClock != null && animationClock.active(); }

    /** How many times this screen has rasterized its surface. A diagnostic, not a frame count. */
    public int paintCount() { return paintCount; }

    public boolean hovered(String id) { return id != null && id.equals(hoveredId); }
    public boolean pressed(String id) { return id != null && id.equals(pressedId); }
    /** True when this control has keyboard focus. */
    public boolean focused(String id) { return textInput != null && textInput.focused(id); }

    /**
     * Smoothly approaches {@code target} and schedules repaints only until the transition ends.
     * Reuse a stable id on every paint. The first value is adopted immediately; later target
     * changes animate from the value currently on screen.
     * State is retained while the id is painted or belongs to a retained virtual row.
     */
    public float animate(String id, float target, int durationMillis, UiEasing easing) {
        return animationClock == null ? target : animationClock.value(id, target, durationMillis, easing);
    }

    /**
     * Smooth shortest-path angular motion (degrees), normalized to [0, 360). Suitable for a
     * scrolling compass: 359 to 1 crosses north instead of spinning backward through 358 degrees.
     * Uses the same finite motion clock and reduced-motion policy as animate.
     */
    public float animateAngle(String id, float targetDegrees, int durationMillis, UiEasing easing) {
        if (!Float.isFinite(targetDegrees)) throw new IllegalArgumentException("Angle must be finite");
        return animationClock == null ? ((targetDegrees % 360) + 360) % 360
            : animationClock.angle(id, targetDegrees, durationMillis, easing);
    }

    /** Initial entrance plus delayed/staggered start. Later interruptions retarget without delay.
     * An id removed from the page or an evicted virtual row starts a fresh entrance when reintroduced. */
    public float animateFrom(String id, float initial, float target, int durationMillis, int delayMillis,
                             UiEasing easing, UiRect repaintBounds) {
        registerAnimationBounds(id, repaintBounds);
        return animationClock == null ? target : animationClock.value(id, initial, target, durationMillis, delayMillis, easing);
    }

    /** Plays sequential scalar keyframes; change revision to replay, continuing from the sampled value.
     * State belongs to painted controls/retained rows; reintroducing an evicted id starts fresh even at the same revision. */
    public float sequence(String id, long revision, UiTimeline timeline, UiRect repaintBounds) {
        if (timeline == null) throw new IllegalArgumentException("Timeline is required");
        registerAnimationBounds(id, repaintBounds);
        return animationClock == null ? timeline.endValue() : animationClock.sequence(id, revision, timeline);
    }

    /**
     * Transforms a drawing/button group and its exact hit polygons. Text editors, sliders,
     * scroll viewports and native overlays belong outside affine groups; use withTranslation there.
     * Compose with withLayer for one group opacity rather than per-operation opacity.
     */
    public void withTransform(UiTransform transform, float pivotX, float pivotY, Consumer<UiContext> painter) {
        if (transform == null || painter == null || transform.opacity() <= 0) return;
        var hits = new java.util.ArrayList<UiHitRegion>();
        var natives = new java.util.LinkedHashMap<String, NativeBounds>();
        var covers = new java.util.LinkedHashMap<String, UiRect>();
        var bounds = new java.util.HashMap<String, UiRect>();
        var scrolls = new java.util.ArrayList<UiScrollRegion>();
        var inputs = new java.util.ArrayList<UiInputRegion>();
        var tips = new java.util.ArrayList<UiTooltip>();
        var nextMenus = new java.util.ArrayList<UiMenu>();
        var nextDialogs = new java.util.ArrayList<UiDialogRequest>();
        UiContext child = new UiContext(canvas, theme, width, height, accent, content, hoveredId, pressedId,
            motion, interactionMotion, animating, paintCount, state, hits, natives, covers, bounds, scrolls,
            inputs, tips, textInput, nextMenus, nextDialogs, animationClock, nativeScale, null);
        java.awt.Shape ringClip = focusRingClip();
        if (ringClip != null) {
            try {
                child.affineFocusClip = transform.matrix(pivotX, pivotY).createInverse().createTransformedShape(ringClip);
            } catch (java.awt.geom.NoninvertibleTransformException exception) {
                throw new IllegalArgumentException("Focus transform must be invertible", exception);
            }
        }
        canvas.withTransform(transform, pivotX, pivotY, () -> painter.accept(child));
        if (!inputs.isEmpty() || !scrolls.isEmpty() || !natives.isEmpty() || !covers.isEmpty()
                || hits.stream().anyMatch(hit -> hit.action().startsWith(INTERNAL_ACTION_PREFIX + "slider:")))
            throw new IllegalArgumentException("Affine groups support drawing/buttons; keep editors, sliders, scrolls and native slots outside");
        var matrix = java.awt.geom.AffineTransform.getTranslateInstance(offsetX, offsetY);
        matrix.concatenate(transform.matrix(pivotX, pivotY));
        for (UiHitRegion hit : hits) {
            java.awt.Shape shape = hit.hitShape() == null
                ? new java.awt.geom.Rectangle2D.Float(hit.x(), hit.y(), hit.width(), hit.height()) : hit.hitShape();
            shape = matrix.createTransformedShape(shape);
            var rect = shape.getBounds2D();
            UiRect box = new UiRect((float) rect.getX(), (float) rect.getY(), (float) rect.getWidth(), (float) rect.getHeight());
            UiRect keyboardBox = focusClip == null ? box : intersect(box, focusClip);
            if (keyboardBox.width() > 0 && keyboardBox.height() > 0)
                interactions.register(new UiHitRegion(hit.id(), hit.action(), keyboardBox.x(), keyboardBox.y(), keyboardBox.width(), keyboardBox.height()));
            if (inputClip != null) box = intersect(box, inputClip);
            if (box.width() > 0 && box.height() > 0) hitRegions.add(new UiHitRegion(hit.id(), hit.action(),
                box.x(), box.y(), box.width(), box.height(), box.x(), box.width(), shape));
        }
        for (UiTooltip tip : tips) {
            UiRect anchor = transformedRect(matrix, tip.anchor());
            if (inputClip != null) anchor = intersect(anchor, inputClip);
            if (anchor.width() > 0 && anchor.height() > 0) tooltips.add(new UiTooltip(tip.id(), tip.text(), anchor));
        }
        for (UiMenu menu : nextMenus) {
            UiRect anchor = transformedRect(matrix, menu.anchor());
            if (inputClip != null) anchor = intersect(anchor, inputClip);
            if (anchor.width() > 0 && anchor.height() > 0) menus.add(new UiMenu(menu.id(), anchor, menu.items(), menu.selectedItemId(), menu.style()));
        }
        interactions.buttons.putAll(child.interactions.buttons);
        dialogs.addAll(nextDialogs);
        // Bounds supplied inside the group are transformed back to document coordinates.
        for (var entry : bounds.entrySet()) animationBounds.put(entry.getKey(), transformedRect(matrix, entry.getValue()));
    }

    private static UiRect transformedRect(java.awt.geom.AffineTransform matrix, UiRect source) {
        var rect = matrix.createTransformedShape(new java.awt.geom.Rectangle2D.Float(
            source.x(), source.y(), source.width(), source.height())).getBounds2D();
        return new UiRect((float) rect.getX(), (float) rect.getY(), (float) rect.getWidth(), (float) rect.getHeight());
    }

    /**
     * Smoothly approaches {@code target} and records the only region this animation changes.
     * PaneLib uses that bound to limit repaint work while the animation runs. Include any
     * shadow, glow, or translation the painter applies in {@code repaintBounds}.
     */
    public float animate(String id, float target, int durationMillis, UiEasing easing, UiRect repaintBounds) {
        registerAnimationBounds(id, repaintBounds);
        return animate(id, target, durationMillis, easing);
    }

    /** Bounded boolean convenience variant of {@link #animate(String, float, int, UiEasing, UiRect)}. */
    public float animate(String id, boolean active, int durationMillis, UiEasing easing, UiRect repaintBounds) {
        return animate(id, active ? 1F : 0F, durationMillis, easing, repaintBounds);
    }

    /** Convenience for a 0..1 state transition using Pane's restrained default curve. */
    public float animate(String id, boolean active, int durationMillis) {
        return animate(id, active ? 1F : 0F, durationMillis, UiEasing.PAPER);
    }

    /** Convenience for a bounded 0..1 state transition using Pane's restrained default curve. */
    public float animate(String id, boolean active, int durationMillis, UiRect repaintBounds) {
        return animate(id, active ? 1F : 0F, durationMillis, UiEasing.PAPER, repaintBounds);
    }

    /**
     * Paints a translated group while moving its hit regions, tooltips, text inputs, and native
     * overlay slots by the same amount. Use this instead of translating only {@link #canvas()} when
     * a group contains anything interactive or rendered by Minecraft itself.
     */
    public void withTranslation(float x, float y, Consumer<UiContext> painter) {
        if (painter == null) return;
        UiContext translated = inherit(new UiContext(canvas, theme, width, height, accent, content,
            hoveredId, pressedId, motion, interactionMotion, animating, paintCount, state, hitRegions,
            nativeBounds, nativeOverlayLayers, animationBounds, scrollRegions, inputRegions, textInput, tooltips, menus, dialogs,
            animationClock, nativeScale, offsetX + x, offsetY + y, inputClip, virtualLists));
        canvas.withTranslation(x, y, () -> painter.accept(translated));
    }

    /** True group fade with shared input/layout state. A fully transparent group has no hit targets. */
    public void withLayer(UiLayer layer, float opacity, UiRect bounds, Consumer<UiContext> painter) {
        if (painter == null) return;
        canvas.withLayer(layer, opacity, bounds, group -> {
            UiContext child = inherit(new UiContext(group, theme, width, height, accent, content,
                hoveredId, pressedId, motion, interactionMotion, animating, paintCount, state, hitRegions,
                nativeBounds, nativeOverlayLayers, animationBounds, scrollRegions, inputRegions, textInput,
                tooltips, menus, dialogs, animationClock, nativeScale, offsetX, offsetY,
                intersect(inputClip, bounds.translate(offsetX, offsetY)), null));
            child.focusClip = intersect(focusClip, bounds.translate(offsetX, offsetY));
            painter.accept(child);
        });
    }

    public Object value(String key) { return state.get(key); }

    public boolean booleanValue(String key, boolean fallback) {
        Object value = state.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    public String stringValue(String key, String fallback) {
        Object value = state.get(key);
        return value instanceof String text ? text : fallback;
    }

    /** True when {@code key} currently holds {@code value}. For radio groups and tabs. */
    public boolean selected(String key, String value) {
        return value != null && value.equals(stringValue(key, null));
    }

    public double numberValue(String key, double fallback) {
        Object value = state.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    /**
     * Writes screen state. Painters may call this to seed defaults; it does not schedule a
     * repaint, because the value is already visible in the paint that is running.
     */
    public void setValue(String key, Object value) { state.put(key, value); }

    /** Registers a consumer control. Built-in popup/toast ID namespaces are reserved. */
    public void hit(String id, String action, float x, float y, float width, float height) {
        addHit(id, action, x, y, width, height, x, width);
    }

    void internalHit(String id, String action, float x, float y, float width, float height) {
        addHit(id, internalAction(action), x, y, width, height, x, width);
    }

    static String internalAction(String action) {
        return INTERNAL_ACTION_PREFIX + (action == null ? "" : action);
    }

    /**
     * Registers a region that sets {@code key} to {@code value} when clicked. This is the
     * primitive behind radio groups, tabs, segmented controls and filter chips: read the
     * result back with {@link #selected} or {@link #stringValue}.
     */
    public void selectable(String id, String key, String value,
                           float x, float y, float width, float height) {
        internalHit(id, "select:" + UiValueKey.encode(key) + "=" + value, x, y, width, height);
    }

    void hitTracked(String id, String action, float x, float y, float width, float height,
                    float trackX, float trackWidth) {
        addHit(id, action, x, y, width, height, trackX, trackWidth);
    }

    /**
     * Reserves a rectangle for Minecraft's own renderer. The bounds are converted to the
     * physical pixels a {@link NativeOverlayRenderer} needs and published under {@code id}.
     */
    public void nativeSlot(String id, float x, float y, float width, float height) {
        UiRect bounds = new UiRect(x + offsetX, y + offsetY, width, height);
        // Native overlays render after the cached surface and cannot be clipped by AWT. Hide a
        // partially scrolled slot rather than letting an item or entity escape its viewport.
        if (inputClip != null && !contains(inputClip, bounds)) return;
        nativeBounds.put(id, new NativeBounds(bounds.x() * nativeScale, bounds.y() * nativeScale,
            bounds.width() * nativeScale, bounds.height() * nativeScale));
    }

    /**
     * Marks a named rectangular Pane region to be composited again after Minecraft's native item
     * and entity overlays. Use this for a detail sheet, modal, drawer, popover, or overlapping
     * card that should visually cover any native slot beneath it.
     *
     * <p>Slots remain always on top unless they intersect a registered layer, preserving the
     * existing {@link #nativeSlot(String, float, float, float, float)} behaviour. The layer does
     * not draw anything by itself: paint the Pane content normally, then register the final
     * bounds of the opaque or translucent region that should sit above native rendering.</p>
     *
     * <p>Bounds use layout coordinates and follow {@link #withTranslation}. Within a scroll
     * viewport, Pane clips the composited layer to the visible viewport. Register stable ids on
     * every paint; a duplicate id replaces that layer's previous bounds for the current surface.</p>
     */
    public void nativeOverlayLayer(String id, float x, float y, float width, float height) {
        if (id == null || id.isBlank() || width <= 0 || height <= 0) return;
        UiRect bounds = new UiRect(x + offsetX, y + offsetY, width, height);
        if (inputClip != null) bounds = intersect(inputClip, bounds);
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        // This is a retained Pane rectangle, not a native-renderer rectangle. Preserve its
        // layout coordinates until the final GUI composite so a GUI-scale or backing-scale
        // conversion cannot shift the visible cover away from the Pane content it represents.
        nativeOverlayLayers.put(id, bounds);
    }

    public UiLayout column(UiRect bounds) { return UiLayout.column(bounds); }
    public UiLayout row(UiRect bounds) { return UiLayout.row(bounds); }
    public UiLayout stack(UiRect bounds) { return UiLayout.stack(bounds); }
    public UiGrid grid(UiRect bounds, int columns, float rowHeight, float gap) {
        return UiGrid.columns(bounds, columns, rowHeight, gap);
    }
    public UiGrid responsiveGrid(UiRect bounds, float minimumWidth, float rowHeight, float gap) {
        return UiGrid.responsive(bounds, minimumWidth, rowHeight, gap);
    }

    /**
     * Paints vertically scrollable content clipped to {@code viewport}.
     * <p>
     * Build a {@link UiLayout} first, then pass its {@link UiLayout#contentHeight()} here so the
     * scroll range exactly matches the rectangles the painter reserved.
     */
    public void scroll(String id, UiRect viewport, float contentHeight, Consumer<UiContext> painter) {
        scroll(id, viewport, contentHeight, UiScrollFollow.NONE, painter);
    }

    /** Optional end following, resolved against this paint's extent before drawing or registering hits. */
    public void scroll(String id, UiRect viewport, float contentHeight, UiScrollFollow follow, Consumer<UiContext> painter) {
        java.util.Objects.requireNonNull(follow, "follow");
        if (id == null || id.isBlank() || viewport == null || painter == null) return;
        float maxOffset = Math.max(0, contentHeight - viewport.height());
        float offset = (float) interactions.scrollEnds.resolve(id, interactions.offsets.getOrDefault(id, 0D), maxOffset, follow);
        interactions.offsets.put(id, (double) offset);
        UiRect actualViewport = viewport.translate(offsetX, offsetY);
        UiRect visibleViewport = intersect(inputClip, actualViewport);
        scrollRegions.add(new UiScrollRegion(id, actualViewport, contentHeight, visibleViewport));

        UiContext scrolled = inherit(new UiContext(canvas, theme, width, height, accent,
            new UiRect(viewport.x(), viewport.y(), viewport.width(), contentHeight), hoveredId, pressedId,
            motion, interactionMotion, animating, paintCount, state, hitRegions, nativeBounds, nativeOverlayLayers, animationBounds, scrollRegions,
            inputRegions, textInput, tooltips, menus, dialogs, animationClock, nativeScale,
            offsetX, offsetY - offset, visibleViewport, virtualLists));
        canvas.withClip(viewport.x(), viewport.y(), viewport.width(), viewport.height(), () ->
            canvas.withTranslation(0, -offset, () -> interactions.inScroll(
                new UiScrollRegion(id, actualViewport, contentHeight, visibleViewport), () -> painter.accept(scrolled))));

        if (maxOffset > 0) drawScrollBar(viewport, contentHeight, offset);
    }

    /**
     * Paints only a bounded visible/overscan window of a large fixed-height list. PaneLib retains
     * those rows in a GPU atlas, moves them without repainting page chrome, and uploads only rows
     * that enter the cache or are explicitly invalidated.
     */
    public void virtualList(String id, UiRect viewport, int itemCount, float rowHeight, float gap,
                            BiConsumer<UiContext, UiVirtualItem> painter) {
        virtualList(id, viewport, itemCount, rowHeight, gap, UiScrollFollow.NONE, painter);
    }

    /** Virtual-list equivalent of scroll's end-follow policy; empty lists register a zero extent. */
    public void virtualList(String id, UiRect viewport, int itemCount, float rowHeight, float gap,
                            UiScrollFollow follow, BiConsumer<UiContext, UiVirtualItem> painter) {
        java.util.Objects.requireNonNull(follow, "follow");
        if (id == null || id.isBlank() || viewport == null || painter == null || itemCount < 0) return;
        rowHeight = Math.max(1, rowHeight);
        gap = Math.max(0, gap);
        float stride = rowHeight + gap;
        float contentHeight = itemCount * rowHeight + Math.max(0, itemCount - 1) * gap;
        float maxOffset = Math.max(0, contentHeight - viewport.height());
        float offset = (float) interactions.scrollEnds.resolve(id, interactions.offsets.getOrDefault(id, 0D), maxOffset, follow);
        interactions.offsets.put(id, (double) offset);
        UiRect actualViewport = viewport.translate(offsetX, offsetY);
        scrollRegions.add(new UiScrollRegion(id, actualViewport, contentHeight, intersect(inputClip, actualViewport)));

        if (itemCount == 0) return;

        if (virtualLists != null) {
            virtualLists.add(new UiVirtualListRequest(id, viewport, itemCount, rowHeight, gap,
                contentHeight, theme, width, height, accent, content, state, nativeScale,
                offsetX, offsetY, painter, interactions.offsets).withEditors(interactions));
            return;
        }

        int first = Math.max(0, (int) Math.floor(offset / stride) - 1);
        int last = Math.min(itemCount, (int) Math.ceil((offset + viewport.height()) / stride) + 1);
        UiContext scrolled = inherit(new UiContext(canvas, theme, width, height, accent,
            new UiRect(viewport.x(), viewport.y(), viewport.width(), contentHeight), hoveredId, pressedId,
            motion, interactionMotion, animating, paintCount, state, hitRegions, nativeBounds, nativeOverlayLayers, animationBounds, scrollRegions,
            inputRegions, textInput, tooltips, menus, dialogs, animationClock, nativeScale,
            offsetX, offsetY - offset, intersect(inputClip, actualViewport), null));
        float finalRowHeight = rowHeight;
        canvas.withClip(viewport.x(), viewport.y(), viewport.width(), viewport.height(), () ->
            canvas.withTranslation(0, -offset, () -> {
                for (int index = first; index < last; index++) {
                    UiRect bounds = new UiRect(viewport.x(), viewport.y() + index * stride,
                        viewport.width(), finalRowHeight);
                    painter.accept(scrolled, new UiVirtualItem(index, bounds));
                }
            }));

        if (maxOffset > 0) drawScrollBar(viewport, contentHeight, offset);
    }

    /** Logical clip only: a transient dirty-paint clip must never become a focus outline. */
    private java.awt.Shape focusRingClip() {
        java.awt.geom.Area clip = affineFocusClip == null ? null : new java.awt.geom.Area(affineFocusClip);
        if (inputClip != null) {
            var rectangle = new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Float(
                inputClip.x(), inputClip.y(), inputClip.width(), inputClip.height()));
            if (clip == null) clip = rectangle; else clip.intersect(rectangle);
        }
        return clip == null ? null : java.awt.geom.AffineTransform.getTranslateInstance(-offsetX, -offsetY)
            .createTransformedShape(clip);
    }

    /** Keep the outside focus stroke visible even at a scroll's hard content boundary. */
    private void drawFocusRing(int color, float x, float y, float width, float height, float radius) {
        if (affineFocusClip != null) {
            canvas.clippedFocusRing(color, x, y, width, height, radius, focusRingClip());
            return;
        }
        if (inputClip != null) {
            // Clip coordinates are screen-local; drawing coordinates include the context translation.
            float left = Math.max(x, inputClip.x() - offsetX + 1);
            float top = Math.max(y, inputClip.y() - offsetY + 1);
            float right = Math.min(x + width, inputClip.right() - offsetX - 1);
            float bottom = Math.min(y + height, inputClip.bottom() - offsetY - 1);
            x = left; y = top; width = right - left; height = bottom - top;
        }
        if (width > 0 && height > 0) canvas.strokeRoundRect(color, 1.2F, x, y, width, height,
            Math.min(radius, Math.min(width, height) / 2));
    }

    private void addHit(String id, String action, float x, float y, float width, float height,
                        float trackX, float trackWidth) {
        requireConsumerInteractionId(id);
        UiRect bounds = new UiRect(x + offsetX, y + offsetY, width, height);
        UiRect keyboardBounds = focusClip == null ? bounds : intersect(bounds, focusClip);
        if (keyboardBounds.width() > 0 && keyboardBounds.height() > 0) {
            interactions.register(new UiHitRegion(id, action, keyboardBounds.x(), keyboardBounds.y(),
                keyboardBounds.width(), keyboardBounds.height(), trackX + offsetX, trackWidth));
        }
        if (inputClip != null) bounds = intersect(bounds, inputClip);
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        hitRegions.add(new UiHitRegion(id, action, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
            trackX + offsetX, trackWidth));
    }

    private static void requireConsumerInteractionId(String id) {
        if (id != null && (id.startsWith("menu:") && id.indexOf(':', 5) >= 0 || id.startsWith("menu-body:")
                || id.startsWith("toast-dismiss:") || id.equals("dialog-confirm")
                || id.equals("dialog-cancel") || id.equals("dialog-body") || id.equals("dialog-backdrop"))) {
            throw new IllegalArgumentException("Control ID '" + id
                + "' is reserved for PaneLib popup/toast interactions; choose an application-specific ID");
        }
    }

    private static UiRect controlRepaintBounds(float x, float y, float width, float height, float padding) {
        float safePadding = Math.max(0, padding);
        return new UiRect(x - safePadding, y - safePadding,
            width + safePadding * 2, height + safePadding * 2);
    }

    private void registerAnimationBounds(String id, UiRect bounds) {
        if (animationBounds == null || id == null || id.isBlank() || bounds == null) return;
        UiRect translated = bounds.translate(offsetX, offsetY);
        UiRect previous = animationBounds.get(id);
        if (previous == null) {
            animationBounds.put(id, translated);
            return;
        }
        float x = Math.min(previous.x(), translated.x());
        float y = Math.min(previous.y(), translated.y());
        float right = Math.max(previous.right(), translated.right());
        float bottom = Math.max(previous.bottom(), translated.bottom());
        animationBounds.put(id, new UiRect(x, y, right - x, bottom - y));
    }

    private void addInput(String id, String key, float x, float y, float width, float height,
                          int maxLength, boolean multiline, float[] caretXs, float[] caretYs) {
        addInput(id, key, x, y, width, height, maxLength, multiline, caretXs, caretYs, List.of());
    }

    private void addInput(String id, String key, float x, float y, float width, float height,
                          int maxLength, boolean multiline, float[] caretXs, float[] caretYs, List<UiTextLine> lines) {
        addInput(id, key, x, y, width, height, maxLength, multiline, caretXs, caretYs, lines, false);
    }

    private void addInput(String id, String key, float x, float y, float width, float height,
                          int maxLength, boolean multiline, float[] caretXs, float[] caretYs, List<UiTextLine> lines, boolean masked) {
        UiRect bounds = new UiRect(x + offsetX, y + offsetY, width, height);
        float[] movedXs = caretXs == null ? null : caretXs.clone();
        float[] movedYs = caretYs == null ? null : caretYs.clone();
        if (movedXs != null) for (int i = 0; i < movedXs.length; i++) movedXs[i] += offsetX;
        if (movedYs != null) for (int i = 0; i < movedYs.length; i++) movedYs[i] += offsetY;
        var movedLines = lines.stream().map(line -> line.translate(offsetX, offsetY)).toList();
        interactions.inputs.put(id, new UiInputRegion(id, key, bounds, maxLength, multiline, movedXs, movedYs, movedLines, masked));
        if (inputClip != null) bounds = intersect(bounds, inputClip);
        if (bounds.width() > 0 && bounds.height() > 0)
            inputRegions.add(new UiInputRegion(id, key, bounds, maxLength, multiline, movedXs, movedYs, movedLines, masked));
    }

    private void drawScrollBar(UiRect viewport, float contentHeight, float offset) {
        float trackHeight = Math.max(0, viewport.height() - 8);
        float thumbHeight = Math.max(18, trackHeight * viewport.height() / Math.max(viewport.height(), contentHeight));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        float travel = Math.max(0, trackHeight - thumbHeight);
        float maxOffset = Math.max(1, contentHeight - viewport.height());
        float thumbY = viewport.y() + 4 + travel * offset / maxOffset;
        canvas.roundRect(UiCanvas.fade(theme.border(), .45F), viewport.right() - 4, viewport.y() + 4, 2, trackHeight, 1);
        canvas.roundRect(UiCanvas.fade(theme.mutedText(), .65F), viewport.right() - 4, thumbY, 2, thumbHeight, 1);
    }


    private static UiRect intersect(UiRect first, UiRect second) {
        if (first == null) return second;
        if (second == null) return first;
        float left = Math.max(first.x(), second.x());
        float top = Math.max(first.y(), second.y());
        float right = Math.min(first.right(), second.right());
        float bottom = Math.min(first.bottom(), second.bottom());
        return new UiRect(left, top, right - left, bottom - top);
    }

    private static boolean contains(UiRect outer, UiRect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
            && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }

    public void card(float x, float y, float width, float height) {
        card(x, y, width, height, theme.mediumRadius(), theme.surface());
    }

    public void card(float x, float y, float width, float height, float radius, int color) {
        canvas.roundRect(color, x, y, width, height, radius);
        canvas.strokeRoundRect(theme.border(), .75F, x, y, width, height, radius);
    }

    public void button(String id, String label, String action, float x, float y, float width, float height,
                       ButtonStyle style, Shape shape) {
        boolean hover = hovered(id);
        boolean down = pressed(id);
        UiRect repaintBounds = controlRepaintBounds(x, y, width, height, 4);
        float hoverAmount = animate("button:" + id + ":hover", hover, 120, UiEasing.PAPER, repaintBounds);
        float pressAmount = animate("button:" + id + ":press", down, 90, UiEasing.PAPER, repaintBounds);
        float radius = switch (shape) {
            case SQUARE -> 2;
            case ROUNDED -> theme.smallRadius();
            case SOFT -> theme.mediumRadius();
            case PILL -> height / 2;
        };
        int fill = switch (style) {
            case PRIMARY -> accent;
            case SECONDARY -> theme.raisedSurface();
            case GHOST -> UiCanvas.alpha(theme.background(), 0);
            case DANGER -> 0xFF3A1D20;
        };
        int border = switch (style) {
            case PRIMARY -> UiCanvas.mix(accent, 0xFFFFFFFF, .15F);
            case DANGER -> 0xFF7B3439;
            default -> theme.border();
        };
        if (hoverAmount > 0) fill = UiCanvas.mix(fill, 0xFFFFFFFF,
            (style == ButtonStyle.GHOST ? .05F : .08F) * hoverAmount);
        if (pressAmount > 0) fill = UiCanvas.mix(fill, 0xFF000000, .12F * pressAmount);
        canvas.roundRect(fill, x, y, width, height, radius);
        canvas.strokeRoundRect(border, .8F, x, y, width, height, radius);
        if (focused(id)) drawFocusRing(accent, x - 2, y - 2, width + 4, height + 4, radius + 2);
        int textColor = style == ButtonStyle.PRIMARY ? theme.accentText()
            : style == ButtonStyle.DANGER ? 0xFFFFB9BD : theme.text();
        canvas.centeredText(label, x + width / 2, y + height / 2, 9, textColor, Font.BOLD, theme.fontFamily());
        hit(id, action, x, y, width, height);
    }

    /** A completely restylable button for design systems that do not use Pane's defaults. */
    public void button(String id, String label, String action, float x, float y, float width, float height,
                       UiButtonStyle style) {
        button(id, label, action, x, y, width, height, style, true);
    }

    /** A completely restylable button with an explicit enabled state. */
    public void button(String id, String label, String action, float x, float y, float width, float height,
                       UiButtonStyle style, boolean enabled) {
        paintStyledButton(id, label, x, y, width, height, style, enabled, false);
        if (enabled) hit(id, action, x, y, width, height);
    }

    private void paintStyledButton(String id, String label, float x, float y, float width, float height,
                                   UiButtonStyle style, boolean enabled, boolean selector) {
        UiButtonStyle paint = style == null ? DEFAULT_BUTTON_STYLE : style;
        float shadowPadding = paint.shadowSpread() == null ? 4 : Math.max(4, paint.shadowSpread() + 4);
        UiRect repaintBounds = controlRepaintBounds(x, y, width, height, shadowPadding);
        float hover = animate("custom-button:" + id + ":hover", enabled && hovered(id), 120, UiEasing.PAPER, repaintBounds);
        float down = animate("custom-button:" + id + ":press", enabled && pressed(id), 90, UiEasing.PAPER, repaintBounds);
        int base = paint.background() == null ? (selector ? theme.raisedSurface() : accent) : paint.background();
        int fill = hover > 0
            ? UiCanvas.mix(base, paint.hoverBackground() == null ? UiCanvas.lighten(base, .08F) : paint.hoverBackground(), hover)
            : base;
        if (down > 0) fill = UiCanvas.mix(fill,
            paint.pressedBackground() == null ? UiCanvas.darken(base, .14F) : paint.pressedBackground(), down);
        final int resolvedFill = fill;
        int border = paint.border() == null ? theme.border() : paint.border();
        int text = paint.text() == null ? (selector ? theme.text() : theme.accentText()) : paint.text();
        int focus = paint.focus() == null ? accent : paint.focus();
        float radius = paint.radius() == null ? theme.smallRadius() : paint.radius();
        float borderWidth = paint.borderWidth() == null ? .8F : paint.borderWidth();
        float textSize = paint.textSize() == null ? 9 : paint.textSize();
        int fontStyle = paint.fontStyle() == null ? (selector ? Font.PLAIN : Font.BOLD) : paint.fontStyle();
        String family = paint.fontFamily() == null ? theme.fontFamily() : paint.fontFamily();
        if (paint.shadow() != null && paint.shadowSpread() != null && paint.shadowSpread() > 0 && down < .5F) {
            canvas.shadow(paint.shadow(), paint.shadowSpread(),
                paint.shadowOffsetX() == null ? 0 : paint.shadowOffsetX(),
                paint.shadowOffsetY() == null ? 0 : paint.shadowOffsetY(), x, y, width, height, radius);
        }
        canvas.withOpacity(enabled ? 1 : .42F, () -> {
            canvas.roundRect(resolvedFill, x, y, width, height, radius);
            if (borderWidth > 0) canvas.strokeRoundRect(border, borderWidth, x, y, width, height, radius);
            if (selector) {
                float padding = Math.min(10, Math.max(0, width / 2));
                float arrowWidth = Math.min(8, Math.max(0, width - padding * 2));
                float arrowX = x + width - padding - arrowWidth;
                float labelWidth = Math.max(0, arrowX - x - padding - 6);
                String display = canvas.ellipsize(label, labelWidth, textSize, fontStyle, family);
                canvas.withClip(x + padding, y, labelWidth, Math.max(0, height), () ->
                    canvas.text(display, x + padding, y + (height - canvas.textHeight(textSize, fontStyle, family)) / 2,
                        textSize, text, fontStyle, family));
                float arrowY = y + height / 2 - 2;
                canvas.polygon(text, arrowX, arrowY, arrowX + arrowWidth, arrowY, arrowX + arrowWidth / 2, arrowY + 5);
            } else {
                canvas.centeredText(label, x + width / 2, y + height / 2, textSize, text, fontStyle, family);
            }
            if (enabled && focused(id)) drawFocusRing(focus,
                x - 2, y - 2, width + 4, height + 4, radius + 2);
        });
    }

    /** Compact code-drawn icon button with the same input and focus behavior as a text button. */
    public void iconButton(String id, UiIcon icon, String action, float x, float y, float size,
                           ButtonStyle style, Shape shape) {
        boolean hover = hovered(id), down = pressed(id);
        UiRect repaintBounds = controlRepaintBounds(x, y, size, size, 4);
        float hoverAmount = animate("icon-button:" + id + ":hover", hover, 120, UiEasing.PAPER, repaintBounds);
        float pressAmount = animate("icon-button:" + id + ":press", down, 90, UiEasing.PAPER, repaintBounds);
        float radius = switch (shape) {
            case SQUARE -> 2;
            case ROUNDED -> theme.smallRadius();
            case SOFT -> theme.mediumRadius();
            case PILL -> size / 2;
        };
        int fill = switch (style) {
            case PRIMARY -> accent;
            case SECONDARY -> theme.raisedSurface();
            case GHOST -> UiCanvas.alpha(theme.background(), 0);
            case DANGER -> 0xFF3A1D20;
        };
        fill = UiCanvas.mix(fill, 0xFFFFFFFF, (style == ButtonStyle.GHOST ? .06F : .08F) * hoverAmount);
        fill = UiCanvas.mix(fill, 0xFF000000, .12F * pressAmount);
        int iconColor = style == ButtonStyle.PRIMARY ? theme.accentText()
            : style == ButtonStyle.DANGER ? 0xFFFFB9BD : theme.text();
        canvas.roundRect(fill, x, y, size, size, radius);
        if (style != ButtonStyle.GHOST) canvas.strokeRoundRect(theme.border(), .8F, x, y, size, size, radius);
        if (icon != null) icon.draw(canvas, iconColor, x + size * .25F, y + size * .25F, size * .5F, 1.6F);
        if (focused(id)) drawFocusRing(accent, x - 2, y - 2, size + 4, size + 4, radius + 2);
        hit(id, action, x, y, size, size);
    }

    public void toggle(String id, String key, float x, float y, boolean fallback) {
        // Seed the fallback just like text fields and dropdowns. Without this, a true fallback
        // looked enabled on first paint but the first click wrote true again instead of toggling.
        state.putIfAbsent(key, fallback);
        boolean enabled = booleanValue(key, fallback);
        float enabledMotion = animate("toggle:" + id, enabled, 150, UiEasing.PAPER,
            controlRepaintBounds(x, y, 31, 18, 4));
        canvas.roundRect(UiCanvas.mix(0xFF343941, accent, enabledMotion), x, y, 31, 18, 9);
        canvas.circle(0xFFF0F3F7, x + 9 + 13 * enabledMotion, y + 9, 6.5F);
        if (focused(id)) drawFocusRing(accent, x - 2, y - 2, 35, 22, 11);
        internalHit(id, "toggle:" + key, x, y, 31, 18);
    }

    /** A normal button whose click toggles a boolean state value. */
    public void toggleButton(String id, String key, String offLabel, String onLabel,
                             float x, float y, float width, float height, boolean fallback,
                             ButtonStyle offStyle, ButtonStyle onStyle, Shape shape) {
        state.putIfAbsent(key, fallback);
        boolean enabled = booleanValue(key, fallback);
        drawDefaultButton(id, enabled ? onLabel : offLabel, x, y, width, height,
            enabled ? onStyle : offStyle, shape);
        internalHit(id, "toggle:" + key, x, y, width, height);
    }

    /** Checkbox with a native label, state binding, focus ring, and finite check animation. */
    public boolean checkbox(String id, String key, String label, float x, float y, float width,
                            boolean fallback) {
        state.putIfAbsent(key, fallback);
        boolean checked = booleanValue(key, fallback);
        float amount = animate("checkbox:" + id, checked, 130, UiEasing.PAPER,
            controlRepaintBounds(x, y - 1, Math.max(16, width), 20, 3));
        float size = 16, cy = y + 9;
        int fill = UiCanvas.mix(theme.raisedSurface(), accent, amount);
        canvas.roundRect(fill, x, y + 1, size, size, Math.min(4, theme.smallRadius()));
        canvas.strokeRoundRect(checked ? UiCanvas.lighten(accent, .15F) : theme.border(), .9F,
            x, y + 1, size, size, Math.min(4, theme.smallRadius()));
        if (amount > .05F) {
            canvas.withOpacity(amount, () -> {
                canvas.line(theme.accentText(), 1.7F, x + 4, cy, x + 7, cy + 3);
                canvas.line(theme.accentText(), 1.7F, x + 7, cy + 3, x + 13, cy - 4);
            });
        }
        canvas.text(label, x + 24, y + 3, 9, theme.text(), Font.PLAIN, theme.fontFamily());
        if (focused(id)) drawFocusRing(accent, x - 2, y - 1, width + 4, 20, 6);
        internalHit(id, "toggle:" + key, x, y - 1, Math.max(size, width), 20);
        return checked;
    }

    /** One option in a native radio group. Options sharing {@code key} are mutually exclusive. */
    public boolean radio(String id, String key, String value, String label,
                         float x, float y, float width, boolean selectedByDefault) {
        if (selectedByDefault) state.putIfAbsent(key, value);
        boolean selected = selected(key, value);
        float amount = animate("radio:" + id, selected, 130, UiEasing.PAPER,
            controlRepaintBounds(x, y - 1, Math.max(16, width), 20, 3));
        float cx = x + 8, cy = y + 9;
        canvas.circle(theme.raisedSurface(), cx, cy, 8);
        canvas.strokeCircle(selected ? accent : theme.border(), 1.1F, cx, cy, 7.4F);
        if (amount > .02F) canvas.circle(accent, cx, cy, 4.5F * amount);
        canvas.text(label, x + 22, y + 3, 9, theme.text(), Font.PLAIN, theme.fontFamily());
        if (focused(id)) drawFocusRing(accent, x - 2, y - 1, width + 4, 20, 10);
        selectable(id, key, value, x, y - 1, Math.max(16, width), 20);
        return selected;
    }

    /** Equal-width segmented tabs backed by a normal string state value. */
    public String tabs(String id, String key, List<UiSelectOption> items, float x, float y,
                       float width, float height, String fallback) {
        if (items == null || items.isEmpty()) return fallback == null ? "" : fallback;
        String selected = stringValue(key, fallback == null ? items.getFirst().value() : fallback);
        state.putIfAbsent(key, selected);
        float itemWidth = width / items.size();
        canvas.roundRect(theme.surface(), x, y, width, height, Math.min(theme.smallRadius(), height / 2));
        canvas.strokeRoundRect(theme.border(), .8F, x, y, width, height, Math.min(theme.smallRadius(), height / 2));
        for (int i = 0; i < items.size(); i++) {
            UiSelectOption item = items.get(i);
            float itemX = x + i * itemWidth;
            String itemId = id + "-" + i;
            float amount = animate("tabs:" + itemId, item.value().equals(selected), 150, UiEasing.PAPER,
                controlRepaintBounds(itemX, y, itemWidth, height, 2));
            if (amount > .01F) canvas.roundRect(UiCanvas.fade(accent, .16F + .14F * amount),
                itemX + 3, y + 3, itemWidth - 6, height - 6,
                Math.max(0, Math.min(theme.smallRadius() - 2, (height - 6) / 2)));
            int text = UiCanvas.mix(theme.mutedText(), item.value().equals(selected) ? accent : theme.text(), amount);
            canvas.centeredText(item.label(), itemX + itemWidth / 2, y + height / 2, 8, text,
                item.value().equals(selected) ? Font.BOLD : Font.PLAIN, theme.fontFamily());
            selectable(itemId, key, item.value(), itemX, y, itemWidth, height);
        }
        return selected;
    }

    /** Non-interactive compact status label. Returns its measured width for adjacent layout. */
    public float badge(String label, float x, float y, float height, int background, int textColor) {
        float width = canvas.textWidth(label, 7, Font.BOLD, theme.fontFamily()) + 16;
        canvas.roundRect(background, x, y, width, height, height / 2);
        canvas.centeredText(label, x + width / 2, y + height / 2, 7, textColor, Font.BOLD, theme.fontFamily());
        return width;
    }

    /** Compact removable tag. Returns its measured width for adjacent flow layout. */
    public float tag(String id, String label, String removeAction, float x, float y, float height,
                     int background, int textColor) {
        float labelWidth = canvas.textWidth(label, 8, Font.PLAIN, theme.fontFamily());
        float width = labelWidth + (removeAction == null ? 18 : height + 15);
        canvas.roundRect(background, x, y, width, height, height / 2);
        canvas.text(label, x + 9, y + (height - 9) / 2, 8, textColor, Font.PLAIN, theme.fontFamily());
        if (removeAction != null) {
            float iconSize = Math.min(10, height * .42F);
            UiIcon.CLOSE.draw(canvas, textColor, x + width - height / 2 - iconSize / 2,
                y + height / 2 - iconSize / 2, iconSize, 1.2F);
            hit(id, removeAction, x, y, width, height);
        }
        return width;
    }

    /** Native determinate progress bar. */
    public void progress(float x, float y, float width, float height, double value) {
        float amount = (float) Math.max(0, Math.min(1, value));
        canvas.roundRect(theme.raisedSurface(), x, y, width, height, height / 2);
        if (amount > 0) canvas.roundRect(accent, x, y, Math.max(height, width * amount), height, height / 2);
    }

    public void slider(String id, String key, float x, float y, float width, double fallback) {
        slider(id, key, x, y, width, 0, 1, fallback);
    }

    /** Slider storing a value in an arbitrary numeric range. */
    public double slider(String id, String key, float x, float y, float width,
                         double minimum, double maximum, double fallback) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || !Double.isFinite(maximum - minimum) || maximum <= minimum) {
            throw new IllegalArgumentException("maximum must be greater than minimum");
        }
        double current = numberValue(key, fallback);
        double stored = Double.isFinite(current) ? Math.max(minimum, Math.min(maximum, current)) : minimum;
        state.putIfAbsent(key, stored);
        float normalized = (float) ((stored - minimum) / (maximum - minimum));
        paintSlider(id, key, x, y, width, normalized, minimum, maximum);
        return stored;
    }

    private void paintSlider(String id, String key, float x, float y, float width, float value,
                             double minimum, double maximum) {
        canvas.roundRect(theme.raisedSurface(), x, y + 6, width, 4, 2);
        canvas.roundRect(accent, x, y + 6, width * value, 4, 2);
        canvas.circle(theme.text(), x + width * value, y + 8, 6.5F);
        canvas.circle(accent, x + width * value, y + 8, 4.5F);
        if (focused(id)) drawFocusRing(accent, x - 3, y - 3, width + 6, 22, 11);
        // The grab area is padded, but the value must still map to the visible track.
        hitTracked(id, internalAction("slider:" + key + ":" + minimum + ":" + maximum),
            x - 8, y - 2, width + 16, 20, x, width);
    }

    /** A native, keyboard-editable single-line text field. */
    public String textField(String id, String key, String placeholder, float x, float y, float width,
                            String fallback) {
        return textField(id, key, placeholder, x, y, width, 30, fallback, 256);
    }

    /** Single-line text field with the default height and an explicit user-insertion length limit. */
    public String textField(String id, String key, String placeholder, float x, float y, float width,
                            String fallback, int maxLength) {
        return textField(id, key, placeholder, x, y, width, 30, fallback, maxLength);
    }

    /**
     * Paints a button that opens a native action menu anchored directly beneath it.
     * Selecting an item emits its normal action string through the screen event listener.
     */
    public void menuButton(String id, String label, float x, float y, float width, float height,
                           ButtonStyle style, Shape shape, List<UiMenuItem> items) {
        menuButton(id, label, x, y, width, height, style, shape, items, UiMenuStyle.defaults());
    }

    /** Menu trigger with an independently styled popup. */
    public void menuButton(String id, String label, float x, float y, float width, float height,
                           ButtonStyle style, Shape shape, List<UiMenuItem> items, UiMenuStyle menuStyle) {
        java.util.Objects.requireNonNull(menuStyle, "menuStyle");
        if (id == null || id.isBlank() || items == null || items.isEmpty()) return;
        button(id, label, internalAction("menu:" + id), x, y, width, height, style, shape);
        registerMenu(id, x, y, width, height, items, null, menuStyle);
    }

    /** Styled menu trigger; popup rows have their own independent style. */
    public void menuButton(String id, String label, float x, float y, float width, float height,
                           UiButtonStyle triggerStyle, List<UiMenuItem> items, UiMenuStyle menuStyle) {
        menuButton(id, label, x, y, width, height, triggerStyle, true, items, menuStyle);
    }

    /** Styled menu trigger with normal focus, semantic label and disabled handling. */
    public void menuButton(String id, String label, float x, float y, float width, float height,
                           UiButtonStyle triggerStyle, boolean enabled, List<UiMenuItem> items, UiMenuStyle menuStyle) {
        java.util.Objects.requireNonNull(menuStyle, "menuStyle");
        boolean available = enabled && items != null && !items.isEmpty();
        recordButtonState(id, label, available);
        button(id, label, internalAction("menu:" + id), x, y, width, height, triggerStyle, available);
        if (available) {
            tooltip(id, label);
            registerMenu(id, x, y, width, height, items, null, menuStyle);
        }
    }

    /**
     * Custom menu trigger using the same painter/state, automatic focus ring and disabled opacity
     * as paintedButton. The painter draws only the trigger; PaneLib owns popup input and placement.
     */
    public void paintedMenuButton(String id, String label, float x, float y, float width, float height,
                                  float radius, boolean enabled, List<UiMenuItem> items, UiMenuStyle menuStyle,
                                  BiConsumer<UiCanvas, UiButtonState> painter) {
        java.util.Objects.requireNonNull(menuStyle, "menuStyle");
        boolean available = enabled && items != null && !items.isEmpty();
        paintedButton(id, label, internalAction("menu:" + id), x, y, width, height, radius, available, painter);
        if (available) registerMenu(id, x, y, width, height, items, null, menuStyle);
    }

    private void registerMenu(String id, float x, float y, float width, float height,
                              List<UiMenuItem> items, String selectedItemId, UiMenuStyle menuStyle) {
        UiRect anchor = new UiRect(x + offsetX, y + offsetY, width, height);
        if (inputClip != null) anchor = intersect(anchor, inputClip);
        if (anchor.width() > 0 && anchor.height() > 0) menus.add(new UiMenu(id, anchor, items, selectedItemId, menuStyle));
    }

    /**
     * Paints a native single-value selector. Choosing an option writes {@code key}, emits a
     * normal {@code value} event, and closes the popup.
     */
    public String dropdown(String id, String key, float x, float y, float width, float height,
                           String fallback, List<UiSelectOption> options) {
        return dropdown(id, key, x, y, width, height, fallback, options, UiMenuStyle.defaults());
    }

    /** Single-value selector with independently styled popup rows. */
    public String dropdown(String id, String key, float x, float y, float width, float height,
                           String fallback, List<UiSelectOption> options, UiMenuStyle menuStyle) {
        return dropdown(id, key, x, y, width, height, fallback, options, DEFAULT_BUTTON_STYLE, true, menuStyle);
    }

    /** Styled selected-value trigger. Unset colors/font inherit the selector's theme defaults. */
    public String dropdown(String id, String key, float x, float y, float width, float height,
                           String fallback, List<UiSelectOption> options, UiButtonStyle triggerStyle, UiMenuStyle menuStyle) {
        return dropdown(id, key, x, y, width, height, fallback, options, triggerStyle, true, menuStyle);
    }

    /** Styled selector with an explicit enabled state; labels fit before the arrow and are clipped. */
    public String dropdown(String id, String key, float x, float y, float width, float height,
                           String fallback, List<UiSelectOption> options, UiButtonStyle triggerStyle,
                           boolean enabled, UiMenuStyle menuStyle) {
        java.util.Objects.requireNonNull(menuStyle, "menuStyle");
        if (id == null || id.isBlank() || key == null || key.isBlank() || options == null || options.isEmpty())
            return fallback == null ? "" : fallback;
        Selection selection = selection(key, fallback, options);
        recordButtonState(id, selection.label().isBlank() ? id : selection.label(), enabled);
        paintStyledButton(id, selection.label(), x, y, width, height, triggerStyle, enabled, true);
        if (enabled) {
            internalHit(id, "menu:" + id, x, y, width, height);
            tooltip(id, selection.label());
            registerMenu(id, x, y, width, height, selection.items(), selection.itemId(), menuStyle);
        }
        return selection.value();
    }

    /**
     * Custom selected-value trigger. The painter state's label is the current option's display
     * label (or unmatched value); binding, focus, disabled opacity and popup navigation stay native.
     */
    public String paintedDropdown(String id, String key, float x, float y, float width, float height,
                                  String fallback, List<UiSelectOption> options, float radius, boolean enabled,
                                  UiMenuStyle menuStyle, BiConsumer<UiCanvas, UiButtonState> painter) {
        java.util.Objects.requireNonNull(menuStyle, "menuStyle");
        java.util.Objects.requireNonNull(painter, "painter");
        if (id == null || id.isBlank() || key == null || key.isBlank() || options == null || options.isEmpty())
            return fallback == null ? "" : fallback;
        Selection selection = selection(key, fallback, options);
        paintedButton(id, selection.label().isBlank() ? id : selection.label(), internalAction("menu:" + id),
            x, y, width, height, radius, enabled, painter);
        if (enabled) registerMenu(id, x, y, width, height, selection.items(), selection.itemId(), menuStyle);
        return selection.value();
    }

    private record Selection(String value, String label, String itemId, List<UiMenuItem> items) { }

    private Selection selection(String key, String fallback, List<UiSelectOption> options) {
        String selected = stringValue(key, fallback == null ? options.getFirst().value() : fallback);
        state.putIfAbsent(key, selected);
        String label = selected;
        String selectedItemId = null;
        List<UiMenuItem> items = new java.util.ArrayList<>(options.size());
        for (int i = 0; i < options.size(); i++) {
            UiSelectOption option = options.get(i);
            if (selectedItemId == null && option.value().equals(selected)) {
                label = option.label();
                selectedItemId = "option-" + i;
            }
            items.add(new UiMenuItem("option-" + i, option.label(), internalAction("dropdown:" + UiValueKey.encode(key) + "=" + option.value())));
        }
        return new Selection(selected, label, selectedItemId, items);
    }

    /** Paints a button that opens a native modal confirmation dialog. */
    public void dialogButton(String id, String label, float x, float y, float width, float height,
                             ButtonStyle style, Shape shape, UiDialog dialog) {
        if (id == null || id.isBlank() || dialog == null) return;
        button(id, label, internalAction("dialog:" + id), x, y, width, height, style, shape);
        dialogs.add(new UiDialogRequest(id, dialog));
    }

    /** Styled confirmation trigger; dialog appearance remains independently configured on the dialog. */
    public void dialogButton(String id, String label, float x, float y, float width, float height,
                             UiButtonStyle triggerStyle, UiDialog dialog) {
        dialogButton(id, label, x, y, width, height, triggerStyle, true, dialog);
    }

    /** Styled confirmation trigger with an explicit enabled state. */
    public void dialogButton(String id, String label, float x, float y, float width, float height,
                             UiButtonStyle triggerStyle, boolean enabled, UiDialog dialog) {
        java.util.Objects.requireNonNull(dialog, "dialog");
        recordButtonState(id, label, enabled);
        button(id, label, internalAction("dialog:" + id), x, y, width, height, triggerStyle, enabled);
        if (enabled) {
            tooltip(id, label);
            dialogs.add(new UiDialogRequest(id, dialog));
        }
    }

    /** Custom confirmation trigger with standard button state, focus ring and disabled opacity. */
    public void paintedDialogButton(String id, String label, float x, float y, float width, float height,
                                    float radius, boolean enabled, UiDialog dialog,
                                    BiConsumer<UiCanvas, UiButtonState> painter) {
        java.util.Objects.requireNonNull(dialog, "dialog");
        paintedButton(id, label, internalAction("dialog:" + id), x, y, width, height, radius, enabled, painter);
        if (enabled) dialogs.add(new UiDialogRequest(id, dialog));
    }

    /** A native, keyboard-editable single-line text field with an explicit height and limit. */
    public String textField(String id, String key, String placeholder, float x, float y, float width, float height,
                            String fallback, int maxLength) {
        return paintTextField(id, key, placeholder, x, y, width, height, fallback, maxLength, false, UiInputStyle.defaults());
    }

    /** Password field with the same native editing behavior but masked paint output. */
    public String passwordField(String id, String key, String placeholder, float x, float y, float width,
                                String fallback) {
        return paintTextField(id, key, placeholder, x, y, width, 30, fallback, 256, true, UiInputStyle.defaults());
    }

    /** Password field with the default height and an explicit user-insertion length limit. */
    public String passwordField(String id, String key, String placeholder, float x, float y, float width,
                                String fallback, int maxLength) {
        return paintTextField(id, key, placeholder, x, y, width, 30, fallback, maxLength, true, UiInputStyle.defaults());
    }

    /** Password field with an explicit height and user-insertion length limit. */
    public String passwordField(String id, String key, String placeholder, float x, float y, float width,
                                float height, String fallback, int maxLength) {
        return paintTextField(id, key, placeholder, x, y, width, height, fallback, maxLength, true, UiInputStyle.defaults());
    }

    /** Explicit typography shared by paint, caret, selection, clipping, and pointer placement. */
    public String textField(String id, String key, String placeholder, float x, float y, float width, float height,
                            String fallback, int maxLength, UiInputStyle style) {
        return paintTextField(id, key, placeholder, x, y, width, height, fallback, maxLength, false, style);
    }

    public String passwordField(String id, String key, String placeholder, float x, float y, float width, float height,
                                String fallback, int maxLength, UiInputStyle style) {
        return paintTextField(id, key, placeholder, x, y, width, height, fallback, maxLength, true, style);
    }

    private void paintInputSurface(UiInputAppearance paint, boolean focused,
                                   float x, float y, float width, float height) {
        float radius = Math.min(UiInputAppearance.metric(paint.radius(), theme.smallRadius()), Math.max(0, height / 2));
        canvas.roundRect(UiInputAppearance.color(paint.background(), theme.raisedSurface()), x, y, width, height, radius);
        float stroke = focused ? UiInputAppearance.metric(paint.focusWidth(), 1.2F) : UiInputAppearance.metric(paint.borderWidth(), .8F);
        if (stroke > 0) canvas.strokeRoundRect(focused ? UiInputAppearance.color(paint.focus(), accent)
            : UiInputAppearance.color(paint.border(), theme.border()), stroke, x, y, width, height, radius);
    }

    /** Custom painting with normal pointer/keyboard activation, focus ring and disabled handling.
     * The label is exposed as semantic metadata and a tooltip; automatic narrator output is not implemented. */
    public void paintedButton(String id, String label, String action, float x, float y, float width, float height,
                              float radius, boolean enabled, java.util.function.BiConsumer<UiCanvas, UiButtonState> painter) {
        java.util.Objects.requireNonNull(painter, "painter");
        if (!Float.isFinite(radius) || radius < 0) throw new IllegalArgumentException("Invalid button radius");
        var state = recordButtonState(id, label, enabled);
        canvas.withOpacity(enabled ? 1 : .42F, () -> painter.accept(canvas, state));
        if (state.focused()) drawFocusRing(accent, x - 2, y - 2, width + 4, height + 4, radius + 2);
        if (enabled) {
            hit(id, action, x, y, width, height);
            tooltip(id, label);
        }
    }

    private UiButtonState recordButtonState(String id, String label, boolean enabled) {
        requireConsumerInteractionId(id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A button needs a descriptive label");
        var state = new UiButtonState(label, enabled, enabled && hovered(id), enabled && pressed(id), enabled && focused(id));
        interactions.buttons.put(id, state);
        return state;
    }

    private String paintTextField(String id, String key, String placeholder, float x, float y, float width,
                                  float height, String fallback, int maxLength, boolean masked, UiInputStyle style) {
        java.util.Objects.requireNonNull(style);
        UiInputAppearance paint = style.appearance();
        String family = style.fontFamily() == null ? theme.fontFamily() : style.fontFamily();
        float size = style.fontSize(); int fontStyle = style.fontStyle();
        float paddingX = Math.min(style.paddingX(), Math.max(0, width / 2));
        float paddingY = Math.min(style.paddingY(), Math.max(0, height / 2));
        String initial = fallback == null ? "" : fallback;
        String value = stringValue(key, initial);
        String displayValue = masked ? "•".repeat(value.length()) : value;
        state.putIfAbsent(key, value);
        boolean focused = focused(id);
        UiTextBoundary.Index boundaries = UiTextBoundary.index(value);
        int cursor = focused && textInput != null ? boundaries.floor(textInput.cursor()) : value.length();
        int selectionStart = focused && textInput != null ? boundaries.floor(textInput.selectionStart()) : cursor;
        int selectionEnd = focused && textInput != null ? boundaries.floor(textInput.selectionEnd()) : cursor;
        paintInputSurface(paint, focused, x, y, width, height);

        float textHeight = canvas.textHeight(size, fontStyle, family);
        float textX = x + paddingX, textY = y + (height - textHeight) / 2;
        float innerWidth = Math.max(0, width - paddingX * 2);
        UiShapedText shaped=canvas.shapeText(displayValue,size,fontStyle,family,focused?interactions.textLayouts:null);
        float cursorX = shaped.x(cursor);
        float textOffset = Math.max(0, cursorX - innerWidth + 3);
        canvas.withClip(textX, y + paddingY, innerWidth, Math.max(0, height - paddingY * 2), () -> {
            if (!value.isEmpty()) {
                if (selectionEnd > selectionStart) {
                    canvas.shapedSelection(shaped,selectionStart,selectionEnd,textX-textOffset,textY,textHeight,UiInputAppearance.color(paint.selection(), UiCanvas.fade(accent,.45F)));
                }
                canvas.shapedText(shaped,textX-textOffset,textY,UiInputAppearance.color(paint.text(), theme.text()));
            } else if (!focused && placeholder != null && !placeholder.isEmpty()) {
                canvas.shapedText(canvas.shapeText(placeholder,size,fontStyle,family),textX,textY,UiInputAppearance.color(paint.placeholder(), theme.mutedText()));
            }
            if (focused) {
                float caretX = textX + cursorX - textOffset;
                canvas.line(UiInputAppearance.color(paint.caret(), accent), 1, caretX, textY, caretX, textY + textHeight);
            }
        });
        internalHit(id, "input:" + key, x, y, width, height);
        float[] carets = new float[value.length() + 1];
        java.util.Arrays.fill(carets, Float.NaN);
        // Masked display positions are indexed using the original value's grapheme boundaries.
        for (int i = 0; i <= value.length();) {
            carets[i] = textX - textOffset + shaped.x(i);
            if (i == value.length()) break;
            i = boundaries.next(i);
        }
        float[] caretYs = new float[carets.length];
        java.util.Arrays.fill(caretYs, y + height / 2);
        addInput(id, key, x, y, width, height, maxLength, false, carets, caretYs, List.of(), masked);
        return value;
    }

    /** Native multiline editor with soft wrapping, selection, clipboard, and pointer caret placement. */
    public String textArea(String id, String key, String placeholder, float x, float y, float width,
                           float height, String fallback) {
        return textArea(id, key, placeholder, x, y, width, height, fallback, 4096);
    }

    /** Native multiline editor with an explicit user-insertion limit (UTF-16 units).
     * Existing programmatic/fallback values are not truncated. */
    public String textArea(String id, String key, String placeholder, float x, float y, float width,
                           float height, String fallback, int maxLength) {
        return textArea(id, key, placeholder, x, y, width, height, fallback, maxLength, UiInputStyle.defaults());
    }

    /** Multiline input with retained wheel browsing and caret reveal on editing/navigation. */
    public String textArea(String id, String key, String placeholder, float x, float y, float width,
                           float height, String fallback, int maxLength, UiInputStyle style) {
        java.util.Objects.requireNonNull(style);
        UiInputAppearance paint = style.appearance();
        String family = style.fontFamily() == null ? theme.fontFamily() : style.fontFamily();
        float size = style.fontSize(); int fontStyle = style.fontStyle();
        float paddingX = Math.min(style.paddingX(), Math.max(0, width / 2));
        float paddingY = Math.min(style.paddingY(), Math.max(0, height / 2));
        float textHeight = canvas.textHeight(size, fontStyle, family);
        String value = UiTextBoundary.normalizeLines(stringValue(key, fallback == null ? "" : fallback));
        // Normalize text, but a painter must never silently replace consumer state of another type.
        Object stored = state.get(key);
        if (stored == null || stored instanceof String && !value.equals(stored)) state.put(key, value);
        boolean focused = focused(id);
        UiTextBoundary.Index boundaries = UiTextBoundary.index(value);
        int cursor = focused && textInput != null ? boundaries.floor(textInput.cursor()) : value.length();
        int selectionStart = focused && textInput != null
            ? boundaries.floor(textInput.selectionStart()) : cursor;
        int selectionEnd = focused && textInput != null
            ? boundaries.floor(textInput.selectionEnd()) : cursor;
        paintInputSurface(paint, focused, x, y, width, height);

        float textX = x + paddingX;
        float firstBaseline = y + paddingY;
        float innerWidth = Math.max(0, width - paddingX * 2);
        float lineHeight = Math.max(textHeight, style.lineHeight());
        List<UiShapedText.Line> lines = canvas.wrapShapedText(value,innerWidth,size,fontStyle,family,focused?interactions.textLayouts:null);
        List<UiTextLine> visualLines = new java.util.ArrayList<>();
        float[] caretXs = new float[value.length() + 1];
        float[] caretYs = new float[value.length() + 1];
        java.util.Arrays.fill(caretXs, Float.NaN);
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            UiShapedText.Line line = lines.get(lineIndex);
            float baseline = firstBaseline + lineIndex * lineHeight;
            float[] lineXs = new float[line.end() - line.start() + 1];
            java.util.Arrays.fill(lineXs, Float.NaN);
            for (int index = line.start(); index <= line.end();) {
                caretXs[index] = textX + Math.min(innerWidth, line.text().x(index-line.start()));
                caretYs[index] = baseline + textHeight / 2;
                lineXs[index - line.start()] = caretXs[index];
                if (index == line.end()) break;
                index = boundaries.next(index);
            }
            visualLines.add(new UiTextLine(line.start(), line.end(), lineXs, baseline + textHeight / 2));
        }
        if (focused && textInput != null && textInput.upstream()) {
            for (UiTextLine line : visualLines) if (cursor == line.end()) {
                caretXs[cursor] = line.x(cursor); caretYs[cursor] = line.y(); break;
            }
        }

        float innerHeight = Math.max(0, height - paddingY * 2);
        UiEditorViewport editor=interactions.editors.computeIfAbsent(id,ignored->new UiEditorViewport());
        editor.viewport=new UiRect(textX+offsetX,firstBaseline+offsetY,innerWidth,innerHeight);
        float offset=editor.layout(value,cursor,textInput!=null&&textInput.upstream(),focused,innerWidth,innerHeight,
            (lines.size()-1)*lineHeight+textHeight,caretYs[cursor]-textHeight/2-firstBaseline,textHeight,lineHeight);
        for (int i = 0; i < caretYs.length; i++) caretYs[i] -= offset;
        canvas.withClip(textX, y + paddingY, innerWidth, innerHeight, () -> {
            if (value.isEmpty() && !focused && placeholder != null && !placeholder.isEmpty()) {
                canvas.shapedText(canvas.shapeText(placeholder,size,fontStyle,family),textX,firstBaseline,UiInputAppearance.color(paint.placeholder(), theme.mutedText()));
            }
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                UiShapedText.Line line = lines.get(lineIndex);
                float baseline = firstBaseline + lineIndex * lineHeight - offset;
                int selectedFrom = Math.max(selectionStart, line.start());
                int selectedTo = Math.min(selectionEnd, line.end());
                if (selectedTo > selectedFrom) {
                    canvas.shapedSelection(line.text(),selectedFrom-line.start(),selectedTo-line.start(),textX,baseline,textHeight,UiInputAppearance.color(paint.selection(), UiCanvas.fade(accent,.45F)));
                }
                if(line.start()==line.end() && selectionStart<=line.start() && selectionEnd>line.end())
                    canvas.fill(UiInputAppearance.color(paint.selection(), UiCanvas.fade(accent,.45F)),textX,baseline,1,textHeight);
                if(baseline+textHeight>=y+paddingY&&baseline<y+height-paddingY)
                    canvas.shapedText(line.text(),textX,baseline,UiInputAppearance.color(paint.text(), theme.text()));
            }
            if (focused) canvas.line(UiInputAppearance.color(paint.caret(), accent), 1, caretXs[cursor], caretYs[cursor] - textHeight / 2,
                caretXs[cursor], caretYs[cursor] + textHeight / 2);
        });
        internalHit(id, "input:" + key, x, y, width, height);
        addInput(id, key, x, y, width, height, maxLength, true, caretXs, caretYs,
            visualLines.stream().map(line -> line.translate(0, -offset)).toList());
        return value;
    }

    /** Makes a custom-painted region switch to another page without reserving an action name. */
    public void pageLink(String id, String pageId, float x, float y, float width, float height) {
        internalHit(id, "page:" + pageId, x, y, width, height);
    }

    private void drawDefaultButton(String id, String label, float x, float y, float width, float height,
                                   ButtonStyle style, Shape shape) {
        boolean hover = hovered(id);
        boolean down = pressed(id);
        UiRect repaintBounds = controlRepaintBounds(x, y, width, height, 4);
        float hoverAmount = animate("button:" + id + ":hover", hover, 120, UiEasing.PAPER, repaintBounds);
        float pressAmount = animate("button:" + id + ":press", down, 90, UiEasing.PAPER, repaintBounds);
        float radius = switch (shape) {
            case SQUARE -> 2;
            case ROUNDED -> theme.smallRadius();
            case SOFT -> theme.mediumRadius();
            case PILL -> height / 2;
        };
        int fill = switch (style) {
            case PRIMARY -> accent;
            case SECONDARY -> theme.raisedSurface();
            case GHOST -> UiCanvas.alpha(theme.background(), 0);
            case DANGER -> 0xFF3A1D20;
        };
        int border = switch (style) {
            case PRIMARY -> UiCanvas.mix(accent, 0xFFFFFFFF, .15F);
            case DANGER -> 0xFF7B3439;
            default -> theme.border();
        };
        fill = UiCanvas.mix(fill, 0xFFFFFFFF, (style == ButtonStyle.GHOST ? .05F : .08F) * hoverAmount);
        fill = UiCanvas.mix(fill, 0xFF000000, .12F * pressAmount);
        canvas.roundRect(fill, x, y, width, height, radius);
        canvas.strokeRoundRect(border, .8F, x, y, width, height, radius);
        if (focused(id)) drawFocusRing(accent, x - 2, y - 2, width + 4, height + 4, radius + 2);
        int textColor = style == ButtonStyle.PRIMARY ? theme.accentText()
            : style == ButtonStyle.DANGER ? 0xFFFFB9BD : theme.text();
        canvas.centeredText(label, x + width / 2, y + height / 2, 9, textColor, Font.BOLD, theme.fontFamily());
    }

    /**
     * Adds a delayed native tooltip for a control already painted with {@code targetId}.
     * Call this after the control so Pane can use its exact hit bounds as the anchor.
     */
    public void tooltip(String targetId, String text) {
        if (targetId == null || targetId.isBlank() || text == null || text.isBlank()) return;
        for (int i = hitRegions.size() - 1; i >= 0; i--) {
            UiHitRegion region = hitRegions.get(i);
            if (targetId.equals(region.id())) {
                tooltips.add(new UiTooltip(targetId, text, new UiRect(region.x(), region.y(),
                    region.width(), region.height())));
                return;
            }
        }
    }

    /**
     * Adds a delayed native tooltip for a custom painted rectangle. The rectangle becomes
     * hoverable but remains non-clickable and is not added to keyboard focus traversal.
     */
    public void tooltip(String id, String text, float x, float y, float width, float height) {
        requireConsumerInteractionId(id);
        if (id == null || id.isBlank() || text == null || text.isBlank() || width <= 0 || height <= 0) return;
        UiRect anchor = new UiRect(x + offsetX, y + offsetY, width, height);
        if (inputClip != null) anchor = intersect(anchor, inputClip);
        if (anchor.width() <= 0 || anchor.height() <= 0) return;
        tooltips.add(new UiTooltip(id, text, anchor));
    }

    private static int clampIndex(int index, int length) { return Math.max(0, Math.min(length, index)); }
}
