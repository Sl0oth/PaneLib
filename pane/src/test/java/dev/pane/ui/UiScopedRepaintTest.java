package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Repaint scopes, GPU transitions and drag layers as they behave without a GPU compositor. */
class UiScopedRepaintTest {
    private final List<UiHitRegion> hits = new ArrayList<>();
    private final UiInteractionState interactions = new UiInteractionState();

    private void paint(Consumer<UiContext> painter) {
        hits.clear();
        interactions.beginPaint();
        var image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            painter.accept(new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), 400, 300, 0xFF5B9BE3, new UiRect(0, 0, 400, 300),
                null, null, 1, 1, false, 0, new HashMap<>(), hits, new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new UiTextInputState(null, 0, 0), new ArrayList<>(), new ArrayList<>(),
                null, 1, null).withInteractions(interactions));
        } finally { graphics.dispose(); }
    }

    @Test
    void scopesCombineAndFollowTranslation() {
        paint(ui -> {
            ui.repaintScope("calc:", new UiRect(0, 0, 50, 50));
            ui.withTranslation(100, 0, moved -> moved.repaintScope("calc:", new UiRect(0, 0, 50, 50)));
            ui.repaintScope("", new UiRect(0, 0, 10, 10));
        });
        assertEquals(new UiRect(0, 0, 150, 50), interactions.live.scopes.get("calc:"));
        assertFalse(interactions.live.scopes.containsKey(""), "an empty prefix would scope every key");
        paint(ui -> { });
        assertTrue(interactions.live.scopes.isEmpty(), "scopes are declared per paint");
    }

    @Test
    void transitionPaintsSettledGroupsNormallyAndPresentsChanges() {
        var calls = new ArrayList<Boolean>();
        Consumer<UiContext> panel = ui -> { calls.add(true); ui.hit("panel:button", "go", 10, 10, 40, 20); };
        // Half opacity: without a GPU compositor the group is presented at its target, and a transparent target paints nothing.
        UiTransform hidden = new UiTransform(0, -10, .9F, .9F, 0, .5F);
        paint(ui -> ui.gpuTransition("panel", new UiRect(0, 0, 100, 100), 1, true, hidden, 200, UiEasing.EASE_OUT, panel));
        assertEquals(1, calls.size());
        assertEquals(1, hits.size(), "a first, settled paint is interactive");
        paint(ui -> ui.gpuTransition("panel", new UiRect(0, 0, 100, 100), 1, false, hidden, 200, UiEasing.EASE_OUT, panel));
        assertEquals(2, calls.size(), "hiding presents the group while it animates");
        assertTrue(hits.isEmpty(), "a presented group takes no input");
        assertFalse(interactions.live.refreshes.isEmpty(), "the end of the transition is scheduled");
        interactions.transitions.get("panel").changedAt -= 1_000_000_000L;
        paint(ui -> ui.gpuTransition("panel", new UiRect(0, 0, 100, 100), 1, false, hidden, 200, UiEasing.EASE_OUT, panel));
        assertEquals(2, calls.size(), "a settled hidden group paints nothing");
    }

    @Test
    void dragLayerPaintsInPlaceUnlessItsDragMoves() {
        var calls = new ArrayList<Boolean>();
        paint(ui -> ui.dragLayer("win", new UiRect(0, 0, 100, 100), 1, inner -> {
            calls.add(true);
            assertFalse(inner.dragging("win"));
            inner.dragArea("bar", "win", 0, 0, 100, 20);
        }));
        assertEquals(1, calls.size());
        assertEquals(1, hits.size());
        interactions.live.draggingKey = "win";
        interactions.live.dragMoved = true;
        paint(ui -> assertTrue(ui.dragging("win")));
        interactions.live.dragMoved = false;
        paint(ui -> assertFalse(ui.dragging("win"), "a pressed but unmoved drag is not dragging"));
    }

    @Test
    void largeBackdropHashesAreStableAndSensitive() {
        int[] pixels = new int[300_000];
        for (int i = 0; i < pixels.length; i++) pixels[i] = i * 2654435761L > 0 ? i : -i;
        long first = UiGlassCache.hash(pixels), again = UiGlassCache.hash(pixels.clone());
        assertEquals(first, again, "chunked hashing is deterministic");
        pixels[123_457] ^= 1;
        assertNotEquals(first, UiGlassCache.hash(pixels), "one changed bit changes the hash");
    }

    @Test
    void localFocusRepaintIsOptIn() {
        var page = new UiPage("p", "P", "", 0xFF5B9BE3, ui -> { });
        assertFalse(UiDocument.builder("A").page(page).build().localFocusRepaint());
        assertTrue(UiDocument.builder("B").localFocusRepaint(true).page(page).build().localFocusRepaint());
    }
}
