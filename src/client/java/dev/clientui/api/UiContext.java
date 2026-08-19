package dev.clientui.api;

import java.awt.Font;
import java.util.List;
import java.util.Map;

/** Drawing, state, hit testing, and native-overlay registration for one page paint. */
public final class UiContext {
    public enum ButtonStyle { PRIMARY, SECONDARY, GHOST, DANGER }
    public enum Shape { SQUARE, ROUNDED, SOFT, PILL }

    private final UiCanvas canvas;
    private final UiTheme theme;
    private final int width;
    private final int height;
    private final int accent;
    private final UiRect content;
    private final String hoveredId;
    private final String pressedId;
    private final float motion;
    private final boolean animating;
    private final int paintCount;
    private final Map<String, Object> state;
    private final List<UiHitRegion> hitRegions;
    private final Map<String, NativeBounds> nativeBounds;
    private final double nativeScale;

    UiContext(UiCanvas canvas, UiTheme theme, int width, int height, int accent, UiRect content,
              String hoveredId, String pressedId, float motion, boolean animating, int paintCount,
              Map<String, Object> state, List<UiHitRegion> hitRegions,
              Map<String, NativeBounds> nativeBounds, double nativeScale) {
        this.canvas = canvas;
        this.theme = theme;
        this.width = width;
        this.height = height;
        this.accent = accent;
        this.content = content;
        this.hoveredId = hoveredId;
        this.pressedId = pressedId;
        this.motion = motion;
        this.animating = animating;
        this.paintCount = paintCount;
        this.state = state;
        this.hitRegions = hitRegions;
        this.nativeBounds = nativeBounds;
        this.nativeScale = nativeScale;
    }

    /** Same drawing surface and state, re-aimed at a different content rectangle. */
    UiContext withContent(UiRect newContent) {
        return new UiContext(canvas, theme, width, height, accent, newContent, hoveredId, pressedId,
            motion, animating, paintCount, state, hitRegions, nativeBounds, nativeScale);
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

    /** Eased 0..1 progress of the current transition, or 1 when nothing is animating. */
    public float motion() { return motion; }

    /** True while a finite animation is still scheduling repaints. */
    public boolean animating() { return animating; }

    /** How many times this screen has rasterized its surface. A diagnostic, not a frame count. */
    public int paintCount() { return paintCount; }

    public boolean hovered(String id) { return id != null && id.equals(hoveredId); }
    public boolean pressed(String id) { return id != null && id.equals(pressedId); }
    public Object value(String key) { return state.get(key); }

    public boolean booleanValue(String key, boolean fallback) {
        Object value = state.get(key);
        return value instanceof Boolean bool ? bool : fallback;
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

    public void hit(String id, String action, float x, float y, float width, float height) {
        hitRegions.add(new UiHitRegion(id, action, x, y, width, height));
    }

    void hitTracked(String id, String action, float x, float y, float width, float height,
                    float trackX, float trackWidth) {
        hitRegions.add(new UiHitRegion(id, action, x, y, width, height, trackX, trackWidth));
    }

    /**
     * Reserves a rectangle for Minecraft's own renderer. The bounds are converted to the
     * physical pixels a {@link NativeOverlayRenderer} needs and published under {@code id}.
     */
    public void nativeSlot(String id, float x, float y, float width, float height) {
        nativeBounds.put(id, new NativeBounds(x * nativeScale, y * nativeScale,
            width * nativeScale, height * nativeScale));
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
        if (hover) fill = UiCanvas.mix(fill, 0xFFFFFFFF, style == ButtonStyle.GHOST ? .05F : .08F * motion);
        if (down) fill = UiCanvas.mix(fill, 0xFF000000, .12F);
        canvas.roundRect(fill, x, y, width, height, radius);
        canvas.strokeRoundRect(border, .8F, x, y, width, height, radius);
        int textColor = style == ButtonStyle.PRIMARY ? theme.accentText()
            : style == ButtonStyle.DANGER ? 0xFFFFB9BD : theme.text();
        canvas.centeredText(label, x + width / 2, y + height / 2, 9, textColor, Font.BOLD);
        hit(id, action, x, y, width, height);
    }

    public void toggle(String id, String key, float x, float y, boolean fallback) {
        boolean enabled = booleanValue(key, fallback);
        canvas.roundRect(enabled ? accent : 0xFF343941, x, y, 31, 18, 9);
        canvas.circle(0xFFF0F3F7, x + (enabled ? 22 : 9), y + 9, 6.5F);
        hit(id, "toggle:" + key, x, y, 31, 18);
    }

    public void slider(String id, String key, float x, float y, float width, double fallback) {
        float value = (float) Math.max(0, Math.min(1, numberValue(key, fallback)));
        canvas.roundRect(0xFF343941, x, y + 6, width, 4, 2);
        canvas.roundRect(accent, x, y + 6, width * value, 4, 2);
        canvas.circle(0xFFF2F4F7, x + width * value, y + 8, 6.5F);
        canvas.circle(accent, x + width * value, y + 8, 4.5F);
        // The grab area is padded, but the value must still map to the visible track.
        hitTracked(id, "slider:" + key, x - 8, y - 2, width + 16, 20, x, width);
    }
}
