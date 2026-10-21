package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

final class UiContextTransformTest {
    @Test void focusRingRemainsVisibleAtAllFourHardScrollEdges() throws Exception {
        var image = new BufferedImage(110, 60, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var context = new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), 110, 60,
                0xFF55AAFF, new UiRect(0, 0, 110, 60), null, null, 1, 1, false, 0,
                new HashMap<>(), new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new UiTextInputState("edge", 0, 0),
                new ArrayList<>(), new ArrayList<>(), null, 1, null);
            context.withTranslation(5, 5, moved -> moved.scroll("s", new UiRect(10,10,80,30),30,
                inner -> inner.button("edge", "", "edge",10,10,80,30,
                    UiContext.ButtonStyle.SECONDARY, UiContext.Shape.ROUNDED)));
            for (int[] point : new int[][]{{55,16},{55,43},{16,30},{93,30}}) {
                int rgb = image.getRGB(point[0], point[1]);
                org.junit.jupiter.api.Assertions.assertTrue((rgb & 255) - ((rgb >>> 16) & 255) > 70,
                    "focus accent missing at " + java.util.Arrays.toString(point));
            }
            assertEquals(0, image.getRGB(55,14), "focus ring must not escape the viewport");
            javax.imageio.ImageIO.write(image, "png", new java.io.File("build/focus-ring-edges.png"));
        } finally { graphics.dispose(); }
    }

    @Test void affineFocusRingsRespectLogicalClipAndIgnoreDirtyPaintClip() {
        UiTransform[] transforms = {UiTransform.identity(), UiTransform.identity().scale(2),
            UiTransform.identity().rotate(90), UiTransform.identity().rotate(180)};
        for (int i = 0; i < transforms.length; i++) {
            var image = new BufferedImage(110,60,BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try {
                var context = new UiContext(new UiCanvas(graphics,image), UiTheme.dark(),110,60,
                    0xFF55AAFF,new UiRect(0,0,110,60),null,null,1,1,false,0,
                    new HashMap<>(),new ArrayList<>(),new LinkedHashMap<>(),new LinkedHashMap<>(),new HashMap<>(),
                    new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new UiTextInputState("edge",0,0),
                    new ArrayList<>(),new ArrayList<>(),null,1,null);
                UiTransform transform = transforms[i];
                UiRect b = i == 1 ? new UiRect(30,17.5F,40,15)
                    : i == 2 ? new UiRect(35,-15,30,80) : new UiRect(10,10,80,30);
                java.util.function.Consumer<UiContext> paint = c -> c.scroll("s",new UiRect(10,10,80,30),30,
                    inner -> inner.withTransform(transform,50,25,
                        group -> group.withTransform(UiTransform.identity(),0,0,
                            nested -> nested.button("edge","","edge",b.x(),b.y(),b.width(),b.height(),
                                UiContext.ButtonStyle.SECONDARY,UiContext.Shape.ROUNDED))));
                paint.accept(context);
                for (int[] point : new int[][]{{50,11},{50,38},{11,25},{88,25}}) {
                    int rgb=image.getRGB(point[0],point[1]);
                    org.junit.jupiter.api.Assertions.assertTrue((rgb & 255)-((rgb >>> 16)&255)>70,
                        "ring missing for transform " + transform + " at " + java.util.Arrays.toString(point));
                }
                int[] before = image.getRGB(0,0,110,60,null,0,110);
                graphics.setClip(35,10,30,30); // A repaint boundary must not draw a new outline across the button.
                graphics.setComposite(java.awt.AlphaComposite.Clear);
                graphics.fillRect(35,10,30,30);
                graphics.setComposite(java.awt.AlphaComposite.SrcOver);
                paint.accept(context);
                org.junit.jupiter.api.Assertions.assertArrayEquals(before,image.getRGB(0,0,110,60,null,0,110));
                assertEquals(0,image.getRGB(50,9));
            } finally { graphics.dispose(); }
        }
    }

    @Test void translatedNestedScrollKeepsPixelsTargetsAndClippingAligned() {
        var image = new BufferedImage(240, 180, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var hits = new ArrayList<UiHitRegion>();
            var scrolls = new ArrayList<UiScrollRegion>();
            var context = new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), 240, 180,
                0xFF55AAFF, new UiRect(0, 0, 240, 180), null, null, 1, 1, false, 0,
                new HashMap<>(), hits, new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>(),
                scrolls, new ArrayList<>(), new ArrayList<>(), new UiTextInputState(null, 0, 0),
                new ArrayList<>(), new ArrayList<>(), null, 1, null);
            context.withTranslation(100, 0, moved -> moved.scroll("outer", new UiRect(0, 0, 80, 80), 160, content -> {
                content.canvas().fill(0xFFFFFFFF, 5, 5, 40, 20);
                content.hit("button", "go", 5, 5, 40, 20);
                content.scroll("inner", new UiRect(40, 40, 80, 80), 200,
                    inner -> inner.hit("clipped", "go", 60, 60, 40, 40));
            }));
            assertEquals(0xFFFFFFFF, image.getRGB(110, 10));
            assertEquals(105, hits.getFirst().x());
            assertEquals(2, hits.size());
            assertEquals(20, hits.getLast().width());
            assertEquals(new UiRect(100, 0, 80, 80), scrolls.getFirst().viewport());
            org.junit.jupiter.api.Assertions.assertTrue(scrolls.getFirst().contains(110, 10));
            org.junit.jupiter.api.Assertions.assertFalse(scrolls.getFirst().contains(10, 10));
            org.junit.jupiter.api.Assertions.assertFalse(scrolls.getLast().contains(190, 90));
            assertEquals(120, scrolls.getLast().maxOffset()); // Clipping must not change scroll range.
        } finally { graphics.dispose(); }
    }

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
            var internal = new UiInteractionState();
            internal.offsets.put("list", 40D);
            UiContext context = new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), 240, 180,
                0xFF55AAFF, new UiRect(0, 0, 240, 180), null, null, 1, 1, false, 0,
                state, new ArrayList<>(), new LinkedHashMap<>(), layers, new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new UiTextInputState(null, 0, 0),
                new ArrayList<>(), new ArrayList<>(), null, 4, null);

            context.withInteractions(internal);
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
