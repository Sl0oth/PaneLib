package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;

final class UiMotionTest {
    @Test void initialStaggerAndRetargetAreTimeBased() {
        var states = new HashMap<String, UiAnimationClock.State>();
        var first = new UiAnimationClock(states, 0, 1);
        assertEquals(0, first.value("card", 0, 1, 100, 50, UiEasing.LINEAR));
        assertTrue(first.active());
        assertEquals(0, new UiAnimationClock(states, 25_000_000, 1).value("card", 0, 1, 100, 50, UiEasing.LINEAR));
        assertEquals(.5F, new UiAnimationClock(states, 100_000_000, 1).value("card", 0, 1, 100, 50, UiEasing.LINEAR), .001F);
        assertEquals(.5F, new UiAnimationClock(states, 100_000_000, 1).value("card", 0, 0, 100, 50, UiEasing.LINEAR), .001F);
        assertEquals(.25F, new UiAnimationClock(states, 150_000_000, 1).value("card", 0, 0, 100, 50, UiEasing.LINEAR), .001F);
    }

    @Test void sequentialTracksHoldReplayAndRespectReducedMotion() {
        UiTimeline timeline = UiTimeline.starting(0).hold(50).to(1, 100, UiEasing.LINEAR).hold(30).to(.5F, 100, UiEasing.EASE_OUT);
        assertEquals(280, timeline.durationMillis());
        assertEquals(0, timeline.valueAt(30));
        assertEquals(.5F, timeline.valueAt(100), .001F);
        assertEquals(1, timeline.valueAt(160));
        assertEquals(.5F, timeline.valueAt(280));
        var states = new HashMap<String, UiAnimationClock.State>();
        new UiAnimationClock(states, 0, 1).sequence("pulse", 0, timeline);
        float current = new UiAnimationClock(states, 100_000_000, 1).sequence("pulse", 0, timeline);
        assertEquals(current, new UiAnimationClock(states, 100_000_000, 1).sequence("pulse", 1, timeline), .001F);
        var reduced = new UiAnimationClock(states, 100_000_001, 0);
        assertEquals(.5F, reduced.sequence("pulse", 1, timeline));
        assertFalse(reduced.active());
        assertEquals(0, UiEasing.SPRING.apply(0));
        assertEquals(1, UiEasing.SPRING.apply(1));
        assertTrue(UiEasing.SPRING.apply(.3F) > 1 && UiEasing.SPRING.apply(.3F) < 1.25F);
    }

    @Test void transformedButtonHasExactPolygonRatherThanBoundingBoxInput() {
        var image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var hits = new ArrayList<UiHitRegion>();
            UiContext ui = new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), 100, 100,
                0xFF55AAFF, new UiRect(0, 0, 100, 100), null, null, 1, 1, false, 0,
                new HashMap<>(), hits, new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new UiTextInputState(null, 0, 0),
                new ArrayList<>(), new ArrayList<>(), null, 1, null);
            ui.withTransform(UiTransform.identity().translate(10, 5).rotate(45).scale(1.2F), 30, 30, group -> {
                group.canvas().fill(0xFFFFFFFF, 20, 20, 20, 20);
                group.hit("card", "open", 20, 20, 20, 20);
            });
            var hit = hits.getFirst();
            assertTrue(hit.contains(40, 35));
            assertFalse(hit.contains(hit.x() + .1, hit.y() + .1));
            assertEquals(0xFFFFFFFF, image.getRGB(40, 35));
            assertThrows(IllegalArgumentException.class, () -> ui.withTransform(UiTransform.identity(), 0, 0,
                group -> group.textField("field", "name", "Name", 0, 0, 30, 20, "", 10)));
        } finally { graphics.dispose(); }
    }
}
