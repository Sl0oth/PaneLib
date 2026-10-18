package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class UiHudPlacementTest {
    @Test void anchorsFitAndKeepPhysicalPointSizeAcrossGuiScales() {
        var placement = new UiHudPlacement(UiHudPlacement.Anchor.BOTTOM_RIGHT, 18, 22, 1);
        UiRect first = placement.bounds(800, 600, 240, 100, 1);
        assertEquals(new UiRect(542, 478, 240, 100), first);
        UiRect scaled = placement.bounds(400, 300, 240, 100, 2);
        assertEquals(first.width(), scaled.width() * 2);
        assertEquals(first.x(), scaled.x() * 2);
        UiRect tiny = placement.bounds(90, 40, 240, 100, 1);
        assertTrue(tiny.x() >= 0 && tiny.y() >= 0 && tiny.right() <= 90 && tiny.bottom() <= 40);
        assertThrows(IllegalArgumentException.class, () -> new UiHudPlacement(null, Float.NaN, 0, 1));
    }
}
