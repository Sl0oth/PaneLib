package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class UiVirtualPlacementTest {
    private static final float SCALE = 2.6380367F;

    @Test
    void everyRowKeepsTheSameSpacingThroughoutFractionalScroll() {
        UiVirtualListRequest request = request();
        float expectedStride = request.stride() / SCALE;
        for (float offset = 0; offset < request.stride(); offset += .37F) {
            float offsetGui = offset / SCALE;
            float previous = UiScreen.virtualRowGuiRect(request, 0, SCALE).y() - offsetGui;
            for (int index = 1; index < 40; index++) {
                float current = UiScreen.virtualRowGuiRect(request, index, SCALE).y() - offsetGui;
                assertEquals(expectedStride, current - previous, .0001F,
                    "adjacent row spacing must not change with scroll phase");
                previous = current;
            }
        }
    }

    @Test
    void oneSharedFloatOffsetAvoidsTheOldIndependentRoundingWave() {
        UiVirtualListRequest request = request();
        Set<Integer> legacyRoundedSpacings = new HashSet<>();
        float offset = 13.45F;
        for (int index = 1; index < 40; index++) {
            int previous = Math.round((request.viewport.y() + (index - 1) * request.stride() - offset) / SCALE);
            int current = Math.round((request.viewport.y() + index * request.stride() - offset) / SCALE);
            legacyRoundedSpacings.add(current - previous);
        }
        assertTrue(legacyRoundedSpacings.size() > 1,
            "the former per-row rounding path should reproduce alternating row spacing");

        float first = UiScreen.virtualRowGuiRect(request, 0, SCALE).y() - offset / SCALE;
        float last = UiScreen.virtualRowGuiRect(request, 39, SCALE).y() - offset / SCALE;
        assertEquals(39 * request.stride() / SCALE, last - first, .001F);
    }

    private static UiVirtualListRequest request() {
        return new UiVirtualListRequest("rows", new UiRect(31.25F, 79.4F, 700.5F, 380.25F),
            10_000, 47, 1, 479_999, UiTheme.dark(), 1000, 650, 0xFFFFFFFF,
            new UiRect(0, 0, 1000, 650), new HashMap<>(), 1, 0, 0, (ui, item) -> { });
    }
}
