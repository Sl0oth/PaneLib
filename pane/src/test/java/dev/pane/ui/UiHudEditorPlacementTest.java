package dev.pane.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiHudEditorPlacementTest {
    private static final float W = 480, H = 270;

    @Test void offsetsMoveAHudFromTheCentreInEitherDirection() {
        var below = new UiHudPlacement(UiHudPlacement.Anchor.CENTER, 0, 0, 1, 0, 20).bounds(W, H, 40, 10, 1);
        assertEquals((W - 40) / 2, below.x(), .01);
        assertEquals((H - 10) / 2 + 20, below.y(), .01);
        var left = new UiHudPlacement(UiHudPlacement.Anchor.TOP_CENTER, 0, 6, 1).offset(-30, 0).bounds(W, H, 40, 10, 1);
        assertEquals((W - 40) / 2 - 30, left.x(), .01);
        assertEquals(6, left.y(), .01);
    }

    @Test void offsetsAreInPlacementUnits() {
        var r = new UiHudPlacement(UiHudPlacement.Anchor.TOP_LEFT, 10, 10, 1, 8, 4).bounds(W, H, 40, 10, 2);
        assertEquals(5 + 4, r.x(), .01);
        assertEquals(5 + 2, r.y(), .01);
    }

    @Test void theOldFourArgumentPlacementHasNoOffset() {
        var p = new UiHudPlacement(UiHudPlacement.Anchor.BOTTOM_RIGHT, 12, 14, 1);
        assertEquals(0, p.offsetX()); assertEquals(0, p.offsetY());
    }

    @Test void droppingPicksTheNearestAnchorAndDrawsExactlyWhereItWasDropped() {
        float[][] spots = {{8, 8}, {220, 4}, {430, 12}, {6, 130}, {200, 120}, {440, 128}, {10, 250}, {230, 255}, {436, 252}};
        UiHudPlacement.Anchor[] expected = UiHudPlacement.Anchor.values();
        java.util.Set<UiHudPlacement.Anchor> seen = java.util.EnumSet.noneOf(UiHudPlacement.Anchor.class);
        for (float[] spot : spots) {
            for (float unit : new float[]{1, 1.5F, 2}) {
                var dropped = new UiRect(spot[0], spot[1], 36, 12);
                var placement = UiHudEditor.placementFor(dropped, W, H, unit, 1);
                var drawn = placement.bounds(W, H, 36 * unit, 12 * unit, unit);
                assertEquals(dropped.x(), drawn.x(), .01, placement.toString());
                assertEquals(dropped.y(), drawn.y(), .01, placement.toString());
                seen.add(placement.anchor());
            }
        }
        assertEquals(java.util.EnumSet.allOf(UiHudPlacement.Anchor.class), seen, "all nine anchors are reachable");
        assertEquals(9, expected.length);
    }

    @Test void aHudNearTheRightEdgeStaysAgainstItWhenTheWindowWidens() {
        var placement = UiHudEditor.placementFor(new UiRect(430, 12, 40, 10), W, H, 1, 1);
        assertEquals(UiHudPlacement.Anchor.TOP_RIGHT, placement.anchor());
        var wider = placement.bounds(W + 200, H, 40, 10, 1);
        assertEquals(W + 200 - 40 - 10, wider.x(), .01);
    }
}
