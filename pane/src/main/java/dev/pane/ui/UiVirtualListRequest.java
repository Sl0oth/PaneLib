package dev.pane.ui;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Internal retained description of one fixed-height virtual list. */
final class UiVirtualListRequest {
    private Map<String, Double> scrollOffsets = new java.util.HashMap<>();
    private UiInteractionState editorOwner;
    UiVirtualListRequest withEditors(UiInteractionState owner){editorOwner=owner;return this;}
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

    UiVirtualListRequest(String id, UiRect viewport, int itemCount, float rowHeight, float gap,
                         float contentHeight, UiTheme theme, int width, int height, int accent,
                         UiRect content, Map<String, Object> state, double nativeScale,
                         float offsetX, float offsetY, BiConsumer<UiContext, UiVirtualItem> painter,
                         Map<String, Double> scrollOffsets) {
        this(id, viewport, itemCount, rowHeight, gap, contentHeight, theme, width, height, accent,
            content, state, nativeScale, offsetX, offsetY, painter);
        this.scrollOffsets = scrollOffsets;
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
        if(editorOwner==null)editorOwner=new UiInteractionState(scrollOffsets);
        UiRect rawBounds = new UiRect(viewport.x() - offsetX,
            viewport.y() - offsetY + index * stride(), viewport.width(), rowHeight);
        UiInteractionState rowInteractions = new UiInteractionState(scrollOffsets,editorOwner.editors,editorOwner.textLayouts,editorOwner.scrollEnds);
        UiContext row = new UiContext(canvas, theme, width, height, accent, content,
            dynamic.hoveredId(), dynamic.pressedId(), dynamic.motion(), dynamic.interactionMotion(),
            dynamic.animating(), dynamic.paintCount(), state, output.hits(), output.nativeBounds(), output.nativeOverlayLayers(), null,
            output.scrollRegions(), output.inputs(), dynamic.textInput(), output.tooltips(),
            output.menus(), output.dialogs(), dynamic.animationClock(), nativeScale,
            offsetX, offsetY, null, null).withInteractions(rowInteractions);
        canvas.withTranslation(offsetX, offsetY,
            () -> painter.accept(row, new UiVirtualItem(index, rawBounds)));
        output.buttons().putAll(rowInteractions.buttons);
        output.narrations().putAll(rowInteractions.live.narrations);
        output.narrations().putAll(rowInteractions.live.namedNarrations);
    }

    record DynamicState(String hoveredId, String pressedId, float motion, float interactionMotion,
                        boolean animating, int paintCount, UiTextInputState textInput,
                        UiAnimationClock animationClock) { }

    record RowOutput(List<UiHitRegion> hits, Map<String, NativeBounds> nativeBounds,
                     Map<String, UiRect> nativeOverlayLayers,
                     List<UiScrollRegion> scrollRegions, List<UiInputRegion> inputs,
                     List<UiTooltip> tooltips, List<UiMenu> menus,
                     List<UiDialogRequest> dialogs, Map<String, UiButtonState> buttons, Map<String, UiNarration> narrations) {
        RowOutput(List<UiHitRegion> hits, Map<String, NativeBounds> nativeBounds,
                  Map<String, UiRect> nativeOverlayLayers, List<UiScrollRegion> scrollRegions,
                  List<UiInputRegion> inputs, List<UiTooltip> tooltips, List<UiMenu> menus,
                  List<UiDialogRequest> dialogs) {
            this(hits, nativeBounds, nativeOverlayLayers, scrollRegions, inputs, tooltips, menus, dialogs, new java.util.HashMap<>(), new java.util.HashMap<>());
        }
        RowOutput(List<UiHitRegion> hits, Map<String, NativeBounds> nativeBounds,
                  Map<String, UiRect> nativeOverlayLayers, List<UiScrollRegion> scrollRegions,
                  List<UiInputRegion> inputs, List<UiTooltip> tooltips, List<UiMenu> menus,
                  List<UiDialogRequest> dialogs, Map<String, UiButtonState> buttons) {
            this(hits, nativeBounds, nativeOverlayLayers, scrollRegions, inputs, tooltips, menus, dialogs, buttons, new java.util.HashMap<>());
        }
    }
}
