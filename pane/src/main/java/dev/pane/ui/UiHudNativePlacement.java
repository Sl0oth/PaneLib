package dev.pane.ui;

import java.util.LinkedHashMap;
import java.util.Map;

/** Convert retained HUD-local slots to the same physical-pixel contract used by UiScreen. */
final class UiHudNativePlacement {
    private UiHudNativePlacement() {}
    static Map<String, NativeBounds> map(Map<String, NativeBounds> local, UiRect target,
                                        float width, float height, double guiScale) {
        var result = new LinkedHashMap<String, NativeBounds>();
        double sx = target.width() / width, sy = target.height() / height;
        local.forEach((id, b) -> result.put(id, new NativeBounds(
            (target.x() + b.x()*sx)*guiScale, (target.y() + b.y()*sy)*guiScale,
            b.width()*sx*guiScale, b.height()*sy*guiScale)));
        return result;
    }
}
