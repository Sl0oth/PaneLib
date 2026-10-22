package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Pointer tracking, timed refresh, bounded drags, presentation groups, painted sliders and tooltip style. */
class UiLiveInputTest {
    private final List<UiHitRegion> hits = new ArrayList<>();
    private final List<UiInputRegion> inputs = new ArrayList<>();
    private final UiInteractionState interactions = new UiInteractionState();

    private void paint(Consumer<UiContext> painter) {
        var image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var ui = new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), 400, 300, 0xFF5B9BE3, new UiRect(0, 0, 400, 300),
                null, null, 1, 1, false, 0, new HashMap<>(), hits, new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>(),
                new ArrayList<>(), inputs, new ArrayList<>(), new UiTextInputState(null, 0, 0), new ArrayList<>(), new ArrayList<>(),
                null, 1, null).withInteractions(interactions);
            painter.accept(ui);
        } finally { graphics.dispose(); }
    }

    @Test
    void pointerIsLocalThroughTranslationsAndTransforms() {
        interactions.live.pointerX = 150;
        interactions.live.pointerY = 120;
        var seen = new ArrayList<UiPointer>();
        paint(ui -> {
            seen.add(ui.pointer(100, 100, 100, 50));
            ui.withTranslation(100, 100, moved -> seen.add(moved.pointer(0, 0, 100, 50)));
            ui.withTransform(new UiTransform(0, 0, 2, 2, 0, 1), 0, 0, scaled -> seen.add(scaled.pointer(50, 50, 50, 25)));
            seen.add(ui.pointer(0, 0, 50, 50));
        });
        assertEquals(new UiPointer(true, 150, 120), seen.get(0));
        assertEquals(new UiPointer(true, 50, 20), seen.get(1));
        assertEquals(new UiPointer(true, 75, 60), seen.get(2), "a 2x group sees the pointer at half the document distance");
        assertSame(UiPointer.OUTSIDE, seen.get(3));
        var areas = interactions.live.pointerAreas;
        assertEquals(4, areas.size(), "every tracked region is registered, inside or not");
        assertEquals(new UiRect(100, 100, 100, 50), areas.get(1), "areas are stored in document coordinates");
        assertEquals(new UiRect(100, 100, 100, 50), areas.get(2));
    }

    @Test
    void eachRefreshKeepsItsOwnDeadlineAndBounds() {
        paint(ui -> {
            ui.refreshAfter(5000, new UiRect(0, 0, 10, 10));
            ui.withTranslation(50, 50, moved -> moved.refreshAfter(1000, new UiRect(0, 0, 10, 10)));
            ui.refreshAfter(10, null);
        });
        var refreshes = interactions.live.refreshes;
        assertEquals(3, refreshes.size());
        long first = (refreshes.get(0).atNanos() - System.nanoTime()) / 1_000_000, second = (refreshes.get(1).atNanos() - System.nanoTime()) / 1_000_000;
        assertTrue(first > 4500 && first <= 5000, "first deadline " + first);
        assertTrue(second > 500 && second <= 1000, "second deadline " + second);
        assertEquals(new UiRect(0, 0, 10, 10), refreshes.get(0).bounds());
        assertEquals(new UiRect(50, 50, 10, 10), refreshes.get(1).bounds(), "bounds follow translation");
        assertNull(refreshes.get(2).bounds(), "null bounds asks for the page");
    }

    @Test
    void dragAreaRecordsWhatTheDragMovesInDocumentCoordinates() {
        interactions.beginPaint();
        paint(ui -> ui.withTranslation(20, 30, moved ->
            moved.dragArea("bar", "win", 0, 0, 200, 40, new UiRect(-10, -10, 220, 160))));
        assertEquals(new UiRect(10, 20, 220, 160), interactions.live.dragBounds.get("bar"));
        assertEquals("\u0000pane:drag:" + UiValueKey.encode("win"), hits.getFirst().action());
    }

    @Test
    void presentationPaintsEditorsButTakesNoInput() {
        paint(ui -> ui.withPresentation(new UiTransform(10, 0, .9F, .9F, 0, .5F), 100, 100, group -> {
            group.textField("field", "text", "Type", 20, 20, 160, 30, "hello", 64);
            group.paintedButton("go", "Go", "go", 20, 60, 80, 30, 4, true, (c, s) -> { });
            group.slider("vol", "volume", 20, 100, 100, .5);
        }));
        assertTrue(hits.isEmpty(), "no pointer targets while presented");
        assertTrue(inputs.isEmpty(), "no text input while presented");
        assertThrows(IllegalArgumentException.class, () -> paint(ui -> ui.withTransform(UiTransform.identity().scale(.9F), 0, 0,
            group -> group.textField("field", "text", "Type", 20, 20, 160, 30, "hello", 64))), "withTransform still refuses editors");
    }

    @Test
    void paintedSliderUsesItsWholeRectangleAsTheTrack() {
        var states = new ArrayList<UiSliderState>();
        paint(ui -> ui.paintedSlider("bright", "brightness", 40, 10, 200, 26, 0, 10, 2.5, (canvas, state) -> states.add(state)));
        assertEquals(.25F, states.getFirst().position(), 1e-6);
        var hit = hits.getFirst();
        assertEquals(40, hit.x());
        assertEquals(200, hit.width());
        assertTrue(hit.action().contains("slider:brightness:0.0:10.0"));
        assertThrows(IllegalArgumentException.class, () -> paint(ui -> ui.paintedSlider("s", "k", 0, 0, 10, 10, 1, 1, 0, (c, s) -> { })));
    }

    @Test
    void tooltipStyleClampsItsMetrics() {
        var style = UiTooltipStyle.defaults().font(60, java.awt.Font.BOLD).shape(4, 99).preferAbove(true);
        assertEquals(48, style.textSize());
        assertTrue(style.height() >= style.textSize() + 4);
        assertEquals(style.height() / 2, style.radius());
        assertTrue(style.preferAbove());
        assertThrows(IllegalArgumentException.class, () -> new UiTooltipStyle(null, null, null, Float.NaN, 0, 20, 4, false));
    }
}
