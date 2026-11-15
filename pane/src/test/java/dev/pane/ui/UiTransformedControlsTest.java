package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Sliders and text inputs inside rotated or scaled groups map the pointer into the group. */
class UiTransformedControlsTest {
    private static final class Paint implements AutoCloseable {
        final BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = image.createGraphics();
        final UiInteractionState interactions = new UiInteractionState();
        final List<UiHitRegion> hits = new ArrayList<>();
        final List<UiInputRegion> inputs = new ArrayList<>();
        final Map<String, Object> state = new HashMap<>();
        UiContext ui() {
            hits.clear(); inputs.clear();
            interactions.beginPaint();
            return new UiContext(new UiCanvas(g, image), UiTheme.dark(), 400, 300, 0xFFFFAA00, new UiRect(0, 0, 400, 300), null, null,
                1, 1, false, 0, state, hits, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new ArrayList<>(), inputs, new ArrayList<>(), new UiTextInputState(null, 0, 0), new ArrayList<>(), new ArrayList<>(), null, 1, null)
                .withInteractions(interactions);
        }
        UiHitRegion hit(String id) { return hits.stream().filter(h -> h.id().equals(id)).findFirst().orElseThrow(); }
        public void close() { g.dispose(); }
    }

    @Test void aSliderInAScaledGroupFollowsThePointerAlongItsOwnTrack() {
        try (var p = new Paint()) {
            p.ui().slider("volume", "volume", 50, 50, 100, 0);
            UiHitRegion plain = p.hit("volume");
            // Doubled about the origin, the same track runs from twice as far to twice as wide.
            p.ui().withTransform(UiTransform.identity().scale(2), 0, 0, group -> group.slider("volume", "volume", 50, 50, 100, 0));
            UiHitRegion scaled = p.hit("volume");
            assertEquals(plain.width() * 2, scaled.width(), .01);
            double pageX = (plain.trackX() + plain.trackWidth() * .25) * 2;
            assertEquals(.25, scaled.fractionAt(pageX, 110), .001);
            assertEquals(0, scaled.fractionAt(0, 110));
            assertEquals(1, scaled.fractionAt(399, 110));
        }
    }

    @Test void aSliderRotatedAQuarterTurnIsDraggedVertically() {
        try (var p = new Paint()) {
            p.ui().slider("volume", "volume", 0, 0, 100, 0);
            UiHitRegion plain = p.hit("volume");
            // Rotating 90 degrees about (150, 100) and moving there sends local +x to page +y.
            p.ui().withTransform(UiTransform.identity().translate(150, 100).rotate(90), 0, 0,
                group -> group.slider("volume", "volume", 0, 0, 100, 0));
            UiHitRegion turned = p.hit("volume");
            assertTrue(turned.height() > turned.width(), "the hit box stands upright");
            float x = turned.x() + turned.width() / 2;
            double low = turned.fractionAt(x, 100 + plain.trackX() + plain.trackWidth() * .2);
            double high = turned.fractionAt(x, 100 + plain.trackX() + plain.trackWidth() * .8);
            assertEquals(.2, low, .01);
            assertEquals(.8, high, .01);
            assertEquals(low, turned.fractionAt(x + 3, 100 + plain.trackX() + plain.trackWidth() * .2), .001, "moving across the track changes nothing");
        }
    }

    @Test void aTextFieldInAScaledGroupPlacesTheCaretUnderThePointer() {
        try (var p = new Paint()) {
            p.state.put("name", "Hello world");
            p.ui().textField("name", "name", "", 20, 20, 200, 24, "", 40);
            UiInputRegion plain = p.inputs.getFirst();
            p.ui().withTransform(UiTransform.identity().scale(2), 0, 0, group -> group.textField("name", "name", "", 20, 20, 200, 24, "", 40));
            UiInputRegion scaled = p.inputs.getFirst();
            assertEquals(new UiRect(40, 40, 400, 48), scaled.bounds(), "bounds are the box covered on the page");
            assertEquals(scaled.bounds(), p.interactions.inputs.get("name").bounds(), "the keyboard sees the same input");
            for (int caret : new int[]{0, 3, 5, 11}) {
                float x = plain.caretX(caret), y = plain.bounds().centerY();
                assertEquals(caret, scaled.caretPositionAt(x * 2, y * 2).index(), "caret " + caret);
                var page = scaled.page(x, y);
                assertEquals(x * 2, page.x, .01);
                assertEquals(y * 2, page.y, .01);
            }
        }
    }

    @Test void nestedGroupsComposeAndDocumentStylesReachTheGroup() {
        try (var p = new Paint()) {
            p.ui().slider("volume", "volume", 10, 10, 100, 0);
            UiHitRegion plain = p.hit("volume");
            p.ui().withTransform(UiTransform.identity().scale(2), 0, 0, outer ->
                outer.withTransform(UiTransform.identity().translate(30, 0), 0, 0, inner -> inner.slider("volume", "volume", 10, 10, 100, 0)));
            double pageX = (plain.trackX() + plain.trackWidth() * .5 + 30) * 2;
            assertEquals(.5, p.hit("volume").fractionAt(pageX, 30), .001);

            p.interactions.inputStyle = UiInputStyle.defaults().padding(30, 6);
            p.state.put("name", "Hi");
            p.ui().textField("name", "", 20, 60, 200, "");
            float plainCaret = p.inputs.getFirst().caretX(0);
            p.ui().withTransform(UiTransform.identity(), 0, 0, group -> group.textField("name", "", 20, 60, 200, ""));
            assertEquals(plainCaret, p.inputs.getFirst().caretX(0), .01, "the document's input style applies inside a group");
            assertTrue(plainCaret >= 50, "the 30-point padding is in effect");
        }
    }

    @Test void scrollViewportsStillBelongOutside() {
        try (var p = new Paint()) {
            assertThrows(IllegalArgumentException.class, () -> p.ui().withTransform(UiTransform.identity().rotate(10), 0, 0,
                group -> group.scroll("list", new UiRect(0, 0, 100, 80), 300, inner -> { })));
        }
    }
}
