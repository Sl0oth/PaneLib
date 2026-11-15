package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;

/** Colour and key helpers, the stepper, key binding and colour picker, and form layout. */
class UiFormControlsTest {
    private static final class Paint implements AutoCloseable {
        final BufferedImage image = new BufferedImage(420, 600, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = image.createGraphics();
        final UiCanvas canvas = new UiCanvas(g, image);
        final UiInteractionState interactions = new UiInteractionState();
        final List<UiHitRegion> hits = new ArrayList<>();
        final List<UiScrollRegion> scrolls = new ArrayList<>();
        final List<UiInputRegion> inputs = new ArrayList<>();
        final Map<String, Object> values = new HashMap<>();
        UiContext ui() {
            hits.clear(); scrolls.clear(); inputs.clear();
            interactions.beginPaint();
            return new UiContext(canvas, UiTheme.dark(), 420, 600, 0xFFFFAA00, new UiRect(0, 0, 420, 600), null, null,
                1, 1, false, 0, values, hits, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                scrolls, inputs, new ArrayList<>(), new UiTextInputState(null, 0, 0), new ArrayList<>(), new ArrayList<>(), null, 1, null)
                .withInteractions(interactions);
        }
        Optional<UiHitRegion> hit(String id) { return hits.stream().filter(h -> h.id().equals(id)).findFirst(); }
        boolean inked(int x0, int y0, int x1, int y1) {
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if ((image.getRGB(x, y) >>> 24) > 0) return true;
            return false;
        }
        public void close() { g.dispose(); }
    }

    @Test void hexTextParsesEveryFormAndRejectsTheRest() {
        assertEquals(0xFF58A6FF, UiColor.hex("#58A6FF", 0));
        assertEquals(0xFF58A6FF, UiColor.hex("58a6ff", 0));
        assertEquals(0x8058A6FF, UiColor.hex("#8058A6FF", 0));
        assertEquals(0xFFFFAA00, UiColor.hex(" #FA0 ", 0));
        for (String bad : new String[]{null, "", "#", "#12", "#12345", "#1234567", "#GGGGGG", "red", "#123456789"})
            assertEquals(7, UiColor.hex(bad, 7), String.valueOf(bad));
        assertEquals("#58A6FF", UiColor.hex(0xFF58A6FF));
        assertEquals("#8058A6FF", UiColor.hex(0x8058A6FF));
        assertEquals("#00000000", UiColor.hex(0));
    }

    @Test void hueSaturationAndBrightnessRoundTripEveryChannel() {
        assertEquals(0xFFFF0000, UiColor.hsv(0, 1, 1, 1));
        assertEquals(0xFF00FF00, UiColor.hsv(120, 1, 1, 1));
        assertEquals(0xFF0000FF, UiColor.hsv(240, 1, 1, 1));
        assertEquals(0xFFFF0000, UiColor.hsv(360, 1, 1, 1));
        assertEquals(0xFFFF0000, UiColor.hsv(-360, 1, 1, 1));
        assertEquals(0x80FFFFFF, UiColor.hsv(77, 0, 1, .5F));
        var random = new Random(11);
        for (int i = 0; i < 4000; i++) {
            int color = random.nextInt() | 0xFF000000;
            float[] hsv = UiColor.toHsv(color);
            assertTrue(hsv[0] >= 0 && hsv[0] < 360 && hsv[1] >= 0 && hsv[1] <= 1 && hsv[2] >= 0 && hsv[2] <= 1);
            int back = UiColor.hsv(hsv[0], hsv[1], hsv[2], 1);
            for (int shift = 0; shift <= 16; shift += 8)
                assertTrue(Math.abs((color >> shift & 0xFF) - (back >> shift & 0xFF)) <= 1, Integer.toHexString(color) + " -> " + Integer.toHexString(back));
        }
    }

    @Test void keysHaveReadableNames() {
        assertEquals("H", UiKeyNames.name(GLFW.GLFW_KEY_H));
        assertEquals("7", UiKeyNames.name(GLFW.GLFW_KEY_7));
        assertEquals("F5", UiKeyNames.name(GLFW.GLFW_KEY_F5));
        assertEquals("Left Shift", UiKeyNames.name(GLFW.GLFW_KEY_LEFT_SHIFT));
        assertEquals("Keypad 3", UiKeyNames.name(GLFW.GLFW_KEY_KP_3));
        assertEquals("Space", UiKeyNames.name(GLFW.GLFW_KEY_SPACE));
        assertEquals("Not bound", UiKeyNames.name(UiKeyNames.UNBOUND));
        assertEquals("Key 999", UiKeyNames.name(999));
    }

    @Test void stepperClampsItsValueAndDisablesTheButtonAtEachLimit() {
        try (var p = new Paint()) {
            assertEquals(5, p.ui().stepper("count", 10, 10, 130, 0, 10, 1, 5));
            assertEquals(5D, p.values.get("count"));
            assertTrue(p.hit("count:down").isPresent() && p.hit("count:up").isPresent());
            assertTrue(p.hit("count:up").orElseThrow().action().endsWith("step:1.0:0.0:10.0:count"));
            assertTrue(p.hit("count:down").orElseThrow().action().endsWith("step:-1.0:0.0:10.0:count"));
            p.values.put("count", 99D);
            assertEquals(10, p.ui().stepper("count", 10, 10, 130, 0, 10, 1, 5));
            assertTrue(p.hit("count:up").isEmpty(), "no increase at the maximum");
            assertTrue(p.hit("count:down").isPresent());
            p.values.put("count", 0D);
            p.ui().stepper("count", 10, 10, 130, 0, 10, 1, 5);
            assertTrue(p.hit("count:down").isEmpty(), "no decrease at the minimum");
            assertThrows(IllegalArgumentException.class, () -> p.ui().stepper("bad", 0, 0, 100, 0, 10, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> p.ui().stepper("bad", 0, 0, 100, 5, 5, 1, 1));
        }
    }

    @Test void numbersFormatWithoutNeedlessDecimals() {
        assertEquals("5", UiContext.formatNumber(5, 1));
        assertEquals("-3", UiContext.formatNumber(-3, 1));
        assertEquals("0.25", UiContext.formatNumber(.25, .05));
        assertEquals("2", UiContext.formatNumber(2, .5));
        assertEquals("2.5", UiContext.formatNumber(2.5, .5));
        assertEquals("0.1235", UiContext.formatNumber(.12345, .0001));
    }

    @Test void keyBindingStoresItsFallbackAndShowsWhenItIsListening() {
        try (var resting = new Paint(); var listening = new Paint()) {
            assertEquals(GLFW.GLFW_KEY_H, resting.ui().keybind("open", 10, 10, 120, 28, GLFW.GLFW_KEY_H));
            assertEquals(GLFW.GLFW_KEY_H, resting.values.get("open"));
            assertTrue(resting.hit("open").orElseThrow().action().endsWith("keybind:open"));
            listening.interactions.live.capturingKey = "open";
            listening.ui().keybind("open", 10, 10, 120, 28, GLFW.GLFW_KEY_H);
            assertFalse(Arrays.equals(resting.image.getRGB(0, 0, 420, 60, null, 0, 420), listening.image.getRGB(0, 0, 420, 60, null, 0, 420)),
                "a listening binding looks different");
            resting.values.put("open", UiKeyNames.UNBOUND);
            assertEquals(UiKeyNames.UNBOUND, resting.ui().keybind("open", 10, 10, 120, 28, GLFW.GLFW_KEY_H), "an unbound key stays unbound");
        }
    }

    @Test void colourPickerFollowsItsSlidersItsHexTextAndOutsideChanges() {
        try (var p = new Paint()) {
            assertEquals(0xFF58A6FF, p.ui().colorPicker("tint", 10, 10, 220, 0xFF58A6FF, false));
            assertEquals("#58A6FF", p.values.get("tint"));
            float[] hsv = UiColor.toHsv(0xFF58A6FF);
            assertEquals(hsv[0], ((Number) p.values.get("tint:hue")).floatValue(), .001F);
            for (String id : List.of("tint:hue", "tint:saturation", "tint:brightness")) assertTrue(p.hit(id).isPresent(), id);
            assertTrue(p.hit("tint:opacity").isEmpty());
            assertTrue(p.inputs.stream().anyMatch(i -> i.key().equals("tint")), "hex field edits the colour key");
            // Painting again without touching anything must not drift.
            assertEquals(0xFF58A6FF, p.ui().colorPicker("tint", 10, 10, 220, 0xFF58A6FF, false));
            assertEquals("#58A6FF", p.values.get("tint"));

            p.values.put("tint:hue", 0D); p.values.put("tint:saturation", 1D); p.values.put("tint:brightness", 1D);
            assertEquals(0xFFFF0000, p.ui().colorPicker("tint", 10, 10, 220, 0xFF58A6FF, false), "a moved slider sets the colour");
            assertEquals("#FF0000", p.values.get("tint"));

            p.values.put("tint", "#00FF00");
            assertEquals(0xFF00FF00, p.ui().colorPicker("tint", 10, 10, 220, 0xFF58A6FF, false), "typed hex sets the colour");
            assertEquals(120D, ((Number) p.values.get("tint:hue")).doubleValue(), .001);

            p.values.put("tint", "#00F");
            assertEquals(0xFF0000FF, p.ui().colorPicker("tint", 10, 10, 220, 0xFF58A6FF, false));
            p.values.put("tint", "#00");
            assertEquals(0xFF0000FF, p.ui().colorPicker("tint", 10, 10, 220, 0xFF58A6FF, false), "half-typed hex keeps the last colour");
            assertEquals("#00", p.values.get("tint"), "and is not overwritten while typing");

            p.values.put("tint", "#808080");
            p.ui().colorPicker("tint", 10, 10, 220, 0xFF58A6FF, false);
            assertEquals(240D, ((Number) p.values.get("tint:hue")).doubleValue(), .001, "a grey keeps the hue slider where it was");
        }
    }

    @Test void colourPickerWithOpacityKeepsAlpha() {
        try (var p = new Paint()) {
            assertEquals(0x8058A6FF, p.ui().colorPicker("glass", 10, 10, 220, 0x8058A6FF, true));
            assertEquals("#8058A6FF", p.values.get("glass"));
            assertTrue(p.hit("glass:opacity").isPresent());
            p.values.put("glass:opacity", 1D);
            assertEquals(0xFF, p.ui().colorPicker("glass", 10, 10, 220, 0x8058A6FF, true) >>> 24, "opacity slider sets alpha");
            assertEquals(0xFF, UiColor.hex((String) p.values.get("glass"), 0) >>> 24);
            assertEquals(UiContext.colorPickerHeight(false) + 24, UiContext.colorPickerHeight(true));
        }
    }

    private static void rows(UiForm form) {
        form.section("General")
            .toggle("Show hints", "hints", true)
            .slider("Volume", "volume", 0, 100, 50)
            .text("Display name", "name", "Name", "Player")
            .dropdown("Mode", "mode", "a", List.of(new UiSelectOption("a", "Alpha"), new UiSelectOption("b", "Beta")))
            .stepper("Columns", "columns", 1, 9, 1, 3)
            .keybind("Open menu", "open", GLFW.GLFW_KEY_H)
            .color("Accent", "accent", 0xFF58A6FF, false)
            .note("Changes apply when you save.")
            .button("Save", "save");
    }

    @Test void formLaysOutEveryRowAndRegistersItsControlsUnderTheirKeys() {
        try (var p = new Paint()) {
            p.ui().form("settings", new UiRect(10, 10, 400, 580), UiFormControlsTest::rows);
            assertTrue(p.scrolls.isEmpty(), "a form that fits does not scroll");
            for (String id : List.of("hints", "volume", "mode", "columns:up", "columns:down", "open", "accent:hue", "save"))
                assertTrue(p.hit(id).isPresent(), id);
            assertTrue(p.inputs.stream().anyMatch(i -> i.key().equals("name")));
            assertEquals("save", p.hit("save").orElseThrow().action());
            assertEquals(Boolean.TRUE, p.values.get("hints"));
            assertEquals(50D, p.values.get("volume"));
            assertEquals(3D, p.values.get("columns"));
            assertEquals("#58A6FF", p.values.get("accent"));
            // Rows run top to bottom without overlapping, each control inside the form.
            float last = -1;
            for (String id : List.of("hints", "volume", "mode", "columns:up", "open", "accent:hue", "save")) {
                UiHitRegion hit = p.hit(id).orElseThrow();
                assertTrue(hit.y() > last, id + " is below the row before it");
                assertTrue(hit.x() >= 10 && hit.x() + hit.width() <= 410.5F, id + " stays inside the form");
                last = hit.y();
            }
            assertTrue(p.inked(10, 10, 200, 40), "section heading painted");
        }
    }

    @Test void formScrollsWhenItsRowsAreTallerThanItsBounds() {
        try (var p = new Paint()) {
            p.ui().form("settings", new UiRect(10, 10, 400, 200), UiFormControlsTest::rows);
            assertEquals(1, p.scrolls.size());
            var region = p.scrolls.getFirst();
            assertEquals("settings", region.id());
            assertTrue(region.contentHeight() > 400, "content height is the measured rows: " + region.contentHeight());
            assertFalse(p.inked(0, 215, 420, 600), "nothing is painted below the form's bounds");
        }
    }

    @Test void measuringAFormDrawsAndRegistersNothing() {
        try (var p = new Paint()) {
            var ui = p.ui();
            var measure = new UiForm(ui, false, 10, 10, 400);
            rows(measure);
            assertTrue(measure.height() > 300);
            assertFalse(measure.painting());
            assertTrue(p.hits.isEmpty() && p.inputs.isEmpty() && p.values.isEmpty());
            assertFalse(p.inked(0, 0, 420, 600));
            var custom = new UiForm(ui, true, 10, 10, 400);
            assertEquals(new UiRect(10, 10, 400, 64), custom.row(64));
            assertEquals(64, custom.height());
            assertSame(ui, custom.ui());
        }
    }
}
