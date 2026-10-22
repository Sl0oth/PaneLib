package dev.pane.ui;

import static java.util.Objects.requireNonNullElse;

import com.mojang.blaze3d.textures.GpuSampler;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Toast stacks in their own cropped texture, so fades and expiry never repaint the page. */
final class UiToastOverlay {
    static final String DISMISS_PREFIX = "toast-dismiss:";
    private static final long FRAME_NANOS = 16_000_000L;
    private static final UiToastStyle NO_STYLE = UiToastStyle.builder().build();

    private record Active(UiToast toast, long shownAt) {
        long expiresAt() { return shownAt + toast.durationMillis() * 1_000_000L; }
    }

    private final List<Active> toasts = new ArrayList<>();
    private final Map<String, UiRect> bounds = new HashMap<>();
    private UiRetainedTexture surface;
    private UiRect layoutBounds;
    private long lastPaint;
    private List<UiHitRegion> hits = List.of();
    /** Set when content, placement or the protected control may have changed. */
    boolean dirty = true;
    long uploadPixels, allocations;

    /** Supplying an id already shown replaces that toast; the oldest leave beyond {@code maxVisible}. */
    void show(UiToast toast, int maxVisible, long now) {
        toasts.removeIf(candidate -> candidate.toast().id().equals(toast.id()));
        while (toasts.size() >= maxVisible) toasts.removeFirst();
        toasts.add(new Active(toast, now));
        dirty = true;
    }

    boolean dismiss(String id) {
        boolean removed = toasts.removeIf(candidate -> candidate.toast().id().equals(id));
        dirty |= removed;
        return removed;
    }

    boolean prune(long now) {
        boolean removed = toasts.removeIf(candidate -> candidate.expiresAt() <= now);
        dirty |= removed;
        return removed;
    }

    boolean isEmpty() { return toasts.isEmpty(); }
    int count() { return toasts.size(); }
    Optional<UiRect> bounds(String id) { return Optional.ofNullable(bounds.get(id)); }
    List<UiHitRegion> hits() { return hits; }

    boolean hasHit(String id) {
        for (UiHitRegion hit : hits) if (hit.id().equals(id)) return true;
        return false;
    }

    /** Marks a repaint at most once per frame while any toast's fade differs from what is painted. */
    void refreshFades(long now, long fadeNanos) {
        if (now - lastPaint < FRAME_NANOS) return;
        for (Active toast : toasts) {
            // Compare with the last painted opacity, not just the current animation window.
            // This paints the final fully opaque frame even if a slow frame skips the boundary.
            if (UiToastTiming.opacity(now, toast.shownAt(), toast.expiresAt(), fadeNanos)
                    != UiToastTiming.opacity(lastPaint, toast.shownAt(), toast.expiresAt(), fadeNanos)) {
                dirty = true;
                return;
            }
        }
    }

    /**
     * Lays out and paints every stack. {@code pageHits} supplies the pressed or focused control
     * that automatic placement avoids; it is not modified.
     */
    void rebuild(long now, BufferedImage measureImage, float quality, int layoutWidth, int layoutHeight,
                 UiDocument document, long fadeNanos, String protectedId, List<UiHitRegion> pageHits) {
        dirty = false;
        lastPaint = now;
        hits = List.of();
        if (toasts.isEmpty() || measureImage == null) { layoutBounds = null; return; }
        UiRect protectedControl = null;
        if (protectedId != null) for (UiHitRegion candidate : pageHits)
            if (protectedId.equals(candidate.id())) {
                protectedControl = new UiRect(candidate.x(), candidate.y(), candidate.width(), candidate.height());
                break;
            }
        // Layout uses text metrics and protected-control bounds, not a full-page paint.
        Graphics2D measure = measureImage.createGraphics();
        try {
            measure.setClip(0, 0, 0, 0);
            draw(new UiCanvas(measure), new ArrayList<>(), protectedControl, layoutWidth, layoutHeight, document, fadeNanos, now);
        } finally { measure.dispose(); }
        UiRect union = null;
        for (UiRect rect : bounds.values()) union = union == null ? rect : union.union(rect);
        if (union == null) return;
        UiRect area = union.expandAndClamp(layoutWidth, layoutHeight, 14);
        int w = Math.max(1, (int) Math.ceil(area.width() * quality));
        int h = Math.max(1, (int) Math.ceil(area.height() * quality));
        if (surface == null || !surface.matches(w, h)) {
            closeSurface();
            surface = new UiRetainedTexture("pane-toast-overlay", w, h);
            allocations++;
        } else surface.clear();
        var painted = new ArrayList<UiHitRegion>();
        Graphics2D painter = surface.image.createGraphics();
        try {
            painter.scale(quality, quality);
            painter.translate(-area.x(), -area.y());
            draw(new UiCanvas(painter, surface.image), painted, protectedControl, layoutWidth, layoutHeight, document, fadeNanos, now);
        } finally { painter.dispose(); }
        hits = List.copyOf(painted);
        surface.upload();
        uploadPixels += (long) w * h;
        layoutBounds = new UiRect(area.x(), area.y(), w / quality, h / quality);
    }

    void render(GuiGraphicsExtractor extractor, GpuSampler sampler, float layoutScale) {
        if (toasts.isEmpty() || surface == null || layoutBounds == null) return;
        extractor.nextStratum();
        extractor.blit(surface.texture.getTextureView(), sampler,
            Math.round(layoutBounds.x() / layoutScale), Math.round(layoutBounds.y() / layoutScale),
            Math.round(layoutBounds.right() / layoutScale), Math.round(layoutBounds.bottom() / layoutScale), 0, 1, 0, 1);
    }

    private void draw(UiCanvas canvas, List<UiHitRegion> painted, UiRect protectedControl, int layoutWidth, int layoutHeight,
                      UiDocument document, long fadeNanos, long now) {
        bounds.clear();
        UiToastSettings settings = document.toastSettings();
        Map<UiToastPlacement, List<Active>> stacks = new EnumMap<>(UiToastPlacement.class);
        for (Active active : toasts) {
            UiToastPlacement placement = active.toast().placement();
            stacks.computeIfAbsent(placement == null ? settings.placement() : placement, ignored -> new ArrayList<>()).add(active);
        }
        stacks.forEach((placement, stack) -> drawStack(canvas, painted, stack, placement, protectedControl,
            layoutWidth, layoutHeight, settings, document.theme(), fadeNanos, now));
    }

    private void drawStack(UiCanvas canvas, List<UiHitRegion> painted, List<Active> stack, UiToastPlacement requested,
                           UiRect protectedControl, int layoutWidth, int layoutHeight, UiToastSettings settings,
                           UiTheme theme, long fadeNanos, long now) {
        float width = Math.min(286, Math.max(170, layoutWidth - settings.margin() * 2));
        float height = 54, blockHeight = stack.size() * height + (stack.size() - 1) * settings.gap();
        UiToastPlacement placement = requested == UiToastPlacement.AUTO
            ? autoPlacement(width, blockHeight, protectedControl, layoutWidth, layoutHeight, settings) : requested;
        boolean left = placement == UiToastPlacement.TOP_LEFT || placement == UiToastPlacement.BOTTOM_LEFT;
        boolean top = placement == UiToastPlacement.TOP_LEFT || placement == UiToastPlacement.TOP_RIGHT;
        float x = left ? settings.margin() : layoutWidth - settings.margin() - width;
        float y = top ? settings.margin() : layoutHeight - settings.margin() - blockHeight;
        String family = theme.fontFamily();
        for (Active active : stack) {
            UiToast toast = active.toast();
            float opacity = UiToastTiming.opacity(now, active.shownAt(), active.expiresAt(), fadeNanos);
            float toastY = y;
            bounds.put(toast.id(), new UiRect(x, toastY, width, height));
            UiToastStyle style = requireNonNullElse(toast.style() == null ? settings.defaultStyle() : toast.style(), NO_STYLE);
            int background = requireNonNullElse(style.background(), theme.raisedSurface());
            int border = requireNonNullElse(style.border(), theme.border());
            int title = requireNonNullElse(style.titleColor(), theme.text());
            int message = requireNonNullElse(style.messageColor(), theme.mutedText());
            int iconBackground = requireNonNullElse(style.iconBackground(), UiCanvas.fade(theme.accent(), .22F));
            int iconColor = requireNonNullElse(style.iconColor(), theme.accent());
            int closeBackground = requireNonNullElse(style.closeBackground(), UiCanvas.fade(theme.border(), .6F));
            int closeColor = requireNonNullElse(style.closeColor(), theme.text());
            float radius = requireNonNullElse(style.radius(), 10F);
            canvas.withOpacity(opacity, () -> {
                canvas.shadow(0x8A000000, 8, 0, 3, x, toastY, width, height, 10);
                canvas.roundRect(background, x, toastY, width, height, radius);
                canvas.strokeRoundRect(border, .8F, x, toastY, width, height, radius);
                float textX = x + 13;
                if (!toast.icon().isBlank()) {
                    canvas.circle(iconBackground, x + 23, toastY + height / 2, 11);
                    canvas.centeredText(canvas.ellipsize(toast.icon(), 15, 8, Font.BOLD, family),
                        x + 23, toastY + height / 2, 8, iconColor, Font.BOLD, family);
                    textX += 28;
                }
                float textWidth = width - (textX - x) - (toast.dismissible() ? 32 : 13);
                canvas.text(canvas.ellipsize(toast.title(), textWidth, 9, Font.BOLD, family),
                    textX, toastY + 9, 9, title, Font.BOLD, family);
                if (!toast.message().isBlank()) {
                    canvas.text(canvas.ellipsize(toast.message(), textWidth, 8, Font.PLAIN, family),
                        textX, toastY + 27, 8, message, Font.PLAIN, family);
                }
                if (toast.dismissible()) {
                    float closeX = x + width - 26, closeY = toastY + 11;
                    canvas.roundRect(closeBackground, closeX, closeY, 16, 16, 5);
                    canvas.centeredText("×", closeX + 8, closeY + 8, 10, closeColor, Font.PLAIN);
                    painted.add(new UiHitRegion(DISMISS_PREFIX + toast.id(), UiContext.internalAction(DISMISS_PREFIX + toast.id()),
                        closeX, closeY, 16, 16));
                }
            });
            y += height + settings.gap();
        }
    }

    private static UiToastPlacement autoPlacement(float width, float height, UiRect protectedControl,
                                                  int layoutWidth, int layoutHeight, UiToastSettings settings) {
        for (UiToastPlacement candidate : new UiToastPlacement[] {UiToastPlacement.BOTTOM_RIGHT, UiToastPlacement.TOP_RIGHT,
                UiToastPlacement.BOTTOM_LEFT, UiToastPlacement.TOP_LEFT}) {
            boolean left = candidate == UiToastPlacement.TOP_LEFT || candidate == UiToastPlacement.BOTTOM_LEFT;
            boolean top = candidate == UiToastPlacement.TOP_LEFT || candidate == UiToastPlacement.TOP_RIGHT;
            float x = left ? settings.margin() : layoutWidth - settings.margin() - width;
            float y = top ? settings.margin() : layoutHeight - settings.margin() - height;
            if (protectedControl == null || !new UiRect(x, y, width, height).intersects(protectedControl)) return candidate;
        }
        return UiToastPlacement.BOTTOM_RIGHT;
    }

    private void closeSurface() {
        if (surface != null) surface.close();
        surface = null;
        layoutBounds = null;
    }

    /** Releases the texture; the toasts themselves stay queued and repaint on the next frame. */
    void close() {
        closeSurface();
        hits = List.of();
        dirty = true;
    }
}
