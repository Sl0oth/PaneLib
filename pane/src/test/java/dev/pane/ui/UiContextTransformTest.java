package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

final class UiContextTransformTest {
    @Test void fadedLayerKeepsTranslatedInputClippedAndHiddenLayerHasNoTargets() {
        var image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var hits = new ArrayList<UiHitRegion>();
            var context = new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), 80, 80,
                0xFF55AAFF, new UiRect(0, 0, 80, 80), null, null, 1, 1, false, 0,
                new HashMap<>(), hits, new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new UiTextInputState(null, 0, 0),
                new ArrayList<>(), new ArrayList<>(), null, 1, null);
            context.withTranslation(10, 5, moved -> moved.withLayer(new UiLayer(), .5F,
                new UiRect(0, 0, 20, 20), group -> {
                    group.canvas().fill(0xFFFFFFFF, 5, 4, 10, 10);
                    group.hit("inside", "open", 5, 4, 10, 10);
                    group.hit("outside", "open", 50, 50, 10, 10);
                }));
            context.withLayer(new UiLayer(), 0, new UiRect(0, 0, 40, 40),
                group -> group.hit("hidden", "open", 0, 0, 10, 10));
            assertEquals(1, hits.size());
            assertEquals(15, hits.getFirst().x());
            assertEquals(9, hits.getFirst().y());
            assertEquals(128, image.getRGB(16, 10) >>> 24, 1);
        } finally { graphics.dispose(); }
    }
    @Test
    void translationMovesDrawingHitTooltipAndNativeSlotTogether() {
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            var hits = new ArrayList<UiHitRegion>();
            var nativeBounds = new LinkedHashMap<String, NativeBounds>();
            var nativeOverlayLayers = new LinkedHashMap<String, UiRect>();
            var tooltips = new ArrayList<UiTooltip>();
            UiContext context = new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), 80, 80,
                0xFF55AAFF, new UiRect(0, 0, 80, 80), null, null, 1, 1, false, 0,
                new HashMap<>(), hits, nativeBounds, nativeOverlayLayers, new HashMap<>(), new ArrayList<>(), new ArrayList<>(), tooltips,
                new UiTextInputState(null, 0, 0), new ArrayList<>(), new ArrayList<>(), null, 1, null);

            context.withTranslation(11, 7, moved -> {
                moved.canvas().fill(0xFFFFFFFF, 2, 3, 4, 5);
                moved.hit("card", "open", 2, 3, 4, 5);
                moved.nativeSlot("item", 2, 3, 4, 5);
                moved.nativeOverlayLayer("sheet", 1, 2, 8, 9);
                moved.tooltip("card", "Open card");
            });

            assertEquals(0xFFFFFFFF, image.getRGB(13, 10));
            assertEquals(new UiHitRegion("card", "open", 13, 10, 4, 5, 13, 4), hits.getFirst());
            assertEquals(new NativeBounds(13, 10, 4, 5), nativeBounds.get("item"));
            assertEquals(new UiRect(12, 9, 8, 9), nativeOverlayLayers.get("sheet"));
            assertEquals(new UiTooltip("card", "Open card", new UiRect(13, 10, 4, 5)), tooltips.getFirst());
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void namedNativeOverlayLayerReplacesItsPreviousBoundsDuringTheSamePaint() {
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            var layers = new LinkedHashMap<String, UiRect>();
            UiContext context = new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), 80, 80,
                0xFF55AAFF, new UiRect(0, 0, 80, 80), null, null, 1, 1, false, 0,
                new HashMap<>(), new ArrayList<>(), new LinkedHashMap<>(), layers, new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new UiTextInputState(null, 0, 0),
                new ArrayList<>(), new ArrayList<>(), null, 1, null);

            context.nativeOverlayLayer("details", 2, 3, 20, 22);
            context.nativeOverlayLayer("details", 7, 11, 32, 34);

            assertEquals(1, layers.size());
            assertEquals(new UiRect(7, 11, 32, 34), layers.get("details"));
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void overlayLayerKeepsPaneCoordinatesAtNonUnitNativeScale() {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            var layers = new LinkedHashMap<String, UiRect>();
            UiContext context = new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), 400, 300,
                0xFF55AAFF, new UiRect(0, 0, 400, 300), null, null, 1, 1, false, 0,
                new HashMap<>(), new ArrayList<>(), new LinkedHashMap<>(), layers, new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new UiTextInputState(null, 0, 0),
                new ArrayList<>(), new ArrayList<>(), null, 4, null);

            context.withTranslation(11.5F, 7.25F,
                moved -> moved.nativeOverlayLayer("details", 220, 42, 96.5F, 80));

            assertEquals(new UiRect(231.5F, 49.25F, 96.5F, 80), layers.get("details"));
            // A cover uses the exact Pane rectangle; it must not inherit the native overlay's
            // backing-pixel scale. Floor/ceil keep every retained pixel at the declared edge.
            assertEquals(new UiRect(154, 32, 65, 55),
                UiScreen.nativeOverlayGuiBounds(layers.get("details"), 1.5F, 400, 300));
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void overlayLayerTracksScrollTranslationAndClipsInPaneCoordinates() {
        BufferedImage image = new BufferedImage(240, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            var layers = new LinkedHashMap<String, UiRect>();
            var state = new HashMap<String, Object>();
            state.put("scroll:list", 40D);
            UiContext context = new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), 240, 180,
                0xFF55AAFF, new UiRect(0, 0, 240, 180), null, null, 1, 1, false, 0,
                state, new ArrayList<>(), new LinkedHashMap<>(), layers, new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new UiTextInputState(null, 0, 0),
                new ArrayList<>(), new ArrayList<>(), null, 4, null);

            context.scroll("list", new UiRect(30, 20, 100, 70), 180,
                scrolled -> scrolled.nativeOverlayLayer("sheet", 44, 50, 60, 55));

            assertEquals(new UiRect(44, 20, 60, 45), layers.get("sheet"));
            assertEquals(new UiRect(29, 13, 41, 31),
                UiScreen.nativeOverlayGuiBounds(layers.get("sheet"), 1.5F, 240, 180));
        } finally {
            graphics.dispose();
        }
    }
}
