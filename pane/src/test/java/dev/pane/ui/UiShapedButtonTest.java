package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiShapedButtonTest {
    @Test
    void onlyTheOutlineTakesThePointer() {
        var hits = new ArrayList<UiHitRegion>();
        var image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var ui = context(new UiCanvas(graphics, image), hits, null);
            ui.shapedButton("hex", "Hexagon", "go", hexagon(ui.canvas(), 50, 50, 30), true, (canvas, state) -> { });
            ui.withTranslation(100, 100, moved -> moved.shapedButton("moved", "Moved", "go",
                hexagon(moved.canvas(), 20, 20, 15), true, (canvas, state) -> { }));
        } finally { graphics.dispose(); }
        assertEquals(2, hits.size());
        var hex = hits.get(0);
        assertTrue(hex.contains(50, 50), "centre");
        assertTrue(hex.contains(50, 21), "near the top vertex");
        assertFalse(hex.contains(24, 23), "empty bounding-box corner");
        var moved = hits.get(1);
        assertTrue(moved.contains(120, 120), "translated centre");
        assertFalse(moved.contains(106, 106), "translated corner");
    }

    @Test
    void ringSegmentsAndFocusFollowTheShape() {
        var hits = new ArrayList<UiHitRegion>();
        var image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var ui = context(new UiCanvas(graphics, image), hits, "wedge");
            UiPath wedge = ui.canvas().path().arc(100, 100, 80, -30, 60).arc(100, 100, 40, 30, -60).close();
            ui.shapedButton("wedge", "Wedge", "pick", wedge, true, (canvas, state) -> assertTrue(state.focused()));
        } finally { graphics.dispose(); }
        var wedge = hits.getFirst();
        assertTrue(wedge.contains(160, 100), "inside the ring segment, due east");
        assertFalse(wedge.contains(120, 100), "inside the hole");
        assertFalse(wedge.contains(100, 40), "outside the swept angle");
        // The focus ring is painted just outside the outer arc, never across the hole.
        assertNotEquals(0, image.getRGB(182, 100) >>> 24, "focus ring outside the outer edge");
        assertEquals(0, image.getRGB(130, 100) >>> 24, "nothing painted inside the segment by the ring");
    }

    private static UiPath hexagon(UiCanvas canvas, float cx, float cy, float r) {
        UiPath path = canvas.path();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i - 90);
            path.lineTo(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
        }
        return path.close();
    }

    private static UiContext context(UiCanvas canvas, List<UiHitRegion> hits, String focused) {
        return new UiContext(canvas, UiTheme.dark(), 200, 200, 0xFF5B9BE3, new UiRect(0, 0, 200, 200), null, null,
            1, 1, false, 0, new HashMap<>(), hits, new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>(),
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new UiTextInputState(focused, 0, 0),
            new ArrayList<>(), new ArrayList<>(), null, 1, null).withInteractions(new UiInteractionState());
    }
}
