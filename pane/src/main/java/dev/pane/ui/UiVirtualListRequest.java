package dev.pane.ui;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Internal retained description of one fixed-height virtual list. */
final class UiVirtualListRequest {
    final String id;
    final UiRect viewport;
    final int itemCount;
    final float rowHeight;
    final float gap;
    final float contentHeight;
    final UiTheme theme;
    final int width;
    final int height;
    final int accent;
    final UiRect content;
    final Map<String, Object> state;
    final double nativeScale;
    final float offsetX;
    final float offsetY;
    final BiConsumer<UiContext, UiVirtualItem> painter;

    UiVirtualListRequest(String id, UiRect viewport, int itemCount, float rowHeight, float gap,
                         float contentHeight, UiTheme theme, int width, int height, int accent,
                         UiRect content, Map<String, Object> state, double nativeScale,
                         float offsetX, float offsetY,
                         BiConsumer<UiContext, UiVirtualItem> painter) {
        this.id = id;
        this.viewport = viewport.translate(offsetX, offsetY);
        this.itemCount = itemCount;
        this.rowHeight = rowHeight;
        this.gap = gap;
        this.contentHeight = contentHeight;
        this.theme = theme;
        this.width = width;
        this.height = height;
        this.accent = accent;
        this.content = content;
        this.state = state;
        this.nativeScale = nativeScale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.painter = painter;
    }

    float stride() { return rowHeight + gap; }

    float maxOffset() { return Math.max(0, contentHeight - viewport.height()); }

    int firstVisible(double offset) {
        return Math.max(0, (int) Math.floor(offset / stride()));
    }

    int lastVisible(double offset) {
        return Math.min(itemCount, (int) Math.ceil((offset + viewport.height()) / stride()));
    }

    void paintRow(UiCanvas canvas, int index, DynamicState dynamic, RowOutput output) {
        UiRect rawBounds = new UiRect(viewport.x() - offsetX,
            viewport.y() - offsetY + index * stride(), viewport.width(), rowHeight);
        UiContext row = new UiContext(canvas, theme, width, height, accent, content,
            dynamic.hoveredId(), dynamic.pressedId(), dynamic.motion(), dynamic.interactionMotion(),
            dynamic.animating(), dynamic.paintCount(), state, output.hits(), output.nativeBounds(), output.nativeOverlayLayers(), null,
            output.scrollRegions(), output.inputs(), dynamic.textInput(), output.tooltips(),
            output.menus(), output.dialogs(), dynamic.animationClock(), nativeScale,
            offsetX, offsetY, null, null);
        canvas.withTranslation(offsetX, offsetY,
            () -> painter.accept(row, new UiVirtualItem(index, rawBounds)));
    }

    record DynamicState(String hoveredId, String pressedId, float motion, float interactionMotion,
                        boolean animating, int paintCount, UiTextInputState textInput,
                        UiAnimationClock animationClock) { }

    record RowOutput(List<UiHitRegion> hits, Map<String, NativeBounds> nativeBounds,
                     Map<String, UiRect> nativeOverlayLayers,
                     List<UiScrollRegion> scrollRegions, List<UiInputRegion> inputs,
                     List<UiTooltip> tooltips, List<UiMenu> menus,
                     List<UiDialogRequest> dialogs) { }
}
