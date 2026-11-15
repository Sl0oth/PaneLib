package dev.pane.ui;

import java.awt.Font;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Slanted and cut-corner shapes, skew, sunburst rays and display-text effects. */
class UiGameStyleDrawingTest {
    private static final int RED = 0xFFFF0000, BLUE = 0xFF0000FF, WHITE = 0xFFFFFFFF;
    private final BufferedImage image = new BufferedImage(200, 120, BufferedImage.TYPE_INT_ARGB);

    private void paint(java.util.function.Consumer<UiCanvas> painter) {
        var g = image.createGraphics();
        try {
            g.setComposite(java.awt.AlphaComposite.Clear); g.fillRect(0, 0, 200, 120);
            g.setComposite(java.awt.AlphaComposite.SrcOver);
            painter.accept(new UiCanvas(g, image));
        } finally { g.dispose(); }
    }
    private boolean painted(int x, int y) { return image.getRGB(x, y) >>> 24 > 200; }
    private int count() { int n = 0; for (int y = 0; y < 120; y++) for (int x = 0; x < 200; x++) if (painted(x, y)) n++; return n; }

    @Test void aSlantedRectLeansRightAndStaysInsideItsBounds() {
        paint(c -> c.slantRect(RED, 20, 20, 100, 40, 20));
        assertFalse(painted(24, 24), "the top-left corner is cut away");
        assertTrue(painted(24, 56), "the bottom-left corner is filled");
        assertTrue(painted(116, 24), "the top-right corner is filled");
        assertFalse(painted(116, 56), "the bottom-right corner is cut away");
        assertFalse(painted(18, 40)); assertFalse(painted(122, 40));
        paint(c -> c.slantRect(RED, 20, 20, 100, 40, -20));
        assertTrue(painted(24, 24), "a negative slant leans the other way");
        assertFalse(painted(24, 56));
    }

    @Test void aSlantedOutlineIsTheButtonsPointerTarget() {
        var state = new java.util.HashMap<String, Object>();
        var hits = new java.util.ArrayList<UiHitRegion>();
        var g = image.createGraphics();
        try {
            var theme = UiTheme.dark();
            var ui = new UiContext(new UiCanvas(g, image), theme, 200, 120, theme.accent(), new UiRect(0, 0, 200, 120), null, null,
                1, 1, false, 0, state, hits, new java.util.HashMap<>(), new java.util.HashMap<>(), new java.util.HashMap<>(), new java.util.ArrayList<>(),
                new java.util.ArrayList<>(), new java.util.ArrayList<>(), new UiTextInputState(null, 0, 0), new java.util.ArrayList<>(), new java.util.ArrayList<>(), null, 1, null)
                .withInteractions(new UiInteractionState());
            ui.shapedButton("play", "Play", "play", ui.canvas().slantPath(20, 20, 100, 40, 20), true, (c, s) -> { });
        } finally { g.dispose(); }
        var hit = hits.stream().filter(h -> h.id().equals("play")).findFirst().orElseThrow();
        assertTrue(hit.contains(70, 40));
        assertFalse(hit.contains(23, 23), "the cut-away corner takes no clicks");
        assertTrue(hit.contains(23, 57));
    }

    @Test void aDocumentCanTurnOffTooltipsThatRepeatALabel() {
        for (boolean show : new boolean[] {true, false}) {
            var tips = new java.util.ArrayList<UiTooltip>();
            var g = image.createGraphics();
            try {
                var theme = UiTheme.dark();
                var interactions = new UiInteractionState();
                interactions.labelTooltips = show;
                var ui = new UiContext(new UiCanvas(g, image), theme, 200, 120, theme.accent(), new UiRect(0, 0, 200, 120), null, null,
                    1, 1, false, 0, new java.util.HashMap<>(), new java.util.ArrayList<>(), new java.util.HashMap<>(), new java.util.HashMap<>(), new java.util.HashMap<>(), new java.util.ArrayList<>(),
                    new java.util.ArrayList<>(), tips, new UiTextInputState(null, 0, 0), new java.util.ArrayList<>(), new java.util.ArrayList<>(), null, 1, null)
                    .withInteractions(interactions);
                ui.paintedButton("play", "Play", "play", 10, 10, 80, 30, 0, true, (c, s) -> { });
                ui.shapedButton("quit", "Quit", "quit", ui.canvas().slantPath(10, 50, 80, 30, 8), true, (c, s) -> { });
                assertEquals(show ? 2 : 0, tips.size(), "label tooltips " + (show ? "on" : "off"));
                ui.iconButton("gear", "Settings", UiIcon.SETTINGS, "settings", 100, 10, 30, null, true);
                ui.tooltip("play", "Start a match");
                assertEquals(show ? 4 : 2, tips.size(), "icon buttons and tooltips asked for by name always show");
            } finally { g.dispose(); }
        }
        assertTrue(UiDocument.builder("t").page(UiPage.of("p", "P", ui -> { })).build().labelTooltips());
        assertFalse(UiDocument.builder("t").labelTooltips(false).page(UiPage.of("p", "P", ui -> { })).build().labelTooltips());
    }

    @Test void chamferedCornersAreCutIndependently() {
        paint(c -> c.chamferRect(RED, 20, 20, 100, 60, 16));
        assertFalse(painted(22, 22)); assertFalse(painted(117, 22)); assertFalse(painted(22, 77)); assertFalse(painted(117, 77));
        assertTrue(painted(70, 22)); assertTrue(painted(22, 50));
        paint(c -> c.chamferRect(RED, 20, 20, 100, 60, 16, 0, 16, 0));
        assertFalse(painted(22, 22), "top left is cut");
        assertTrue(painted(117, 22), "top right stays square");
        assertFalse(painted(117, 77), "bottom right is cut");
        assertTrue(painted(22, 77), "bottom left stays square");
    }

    @Test void skewLeansDrawingForwardAboutItsPivot() {
        paint(c -> c.withSkew(45, 0, 80, () -> c.fill(RED, 40, 20, 20, 60)));
        assertTrue(painted(50, 78), "points level with the pivot stay put");
        assertFalse(painted(50, 24), "the top has moved");
        assertTrue(painted(106, 24), "45 degrees moves a point 56 above the pivot 56 to the right");
        paint(c -> c.withSkew(0, 0, 80, () -> c.fill(RED, 40, 20, 20, 60)));
        assertTrue(painted(50, 24));
        assertThrows(IllegalArgumentException.class, () -> paint(c -> c.withSkew(90, 0, 0, () -> { })));
    }

    @Test void raysFillHalfTheirBoundsAndTurn() {
        paint(c -> c.rays(RED, 12, 0, 100, 60, 40, 20, 120, 80));
        int filled = count();
        assertTrue(Math.abs(filled - 120 * 80 / 2) < 500, "wedges and gaps are equal: " + filled);
        assertFalse(painted(30, 60), "nothing is drawn outside the bounds");
        assertTrue(painted(150, 63), "the first wedge starts pointing east");
        assertFalse(painted(150, 57));
        paint(c -> c.rays(RED, 12, 15, 100, 60, 40, 20, 120, 80));
        assertFalse(painted(150, 63), "turning by one wedge swaps wedges and gaps");
        assertTrue(painted(150, 57));
    }

    @Test void anOutlineSurroundsTheFillAndKeepsItsAdvance() {
        paint(c -> c.text("H", 40, 20, 60, WHITE, Font.BOLD, UiCanvas.SANS));
        int plain = 0, plainArea = count();
        for (int y = 0; y < 120; y++) for (int x = 0; x < 200; x++) if (image.getRGB(x, y) == WHITE) plain++;
        paint(c -> c.text("H", 40, 20, 60, WHITE, Font.BOLD, UiCanvas.SANS, UiTextEffect.outline(BLUE, 4)));
        int white = 0, blue = 0;
        for (int y = 0; y < 120; y++) for (int x = 0; x < 200; x++) {
            int p = image.getRGB(x, y);
            if (p == WHITE) white++; else if (p == BLUE) blue++;
        }
        assertTrue(blue > 400, "the rim is drawn: " + blue);
        assertTrue(Math.abs(white - plain) < plain * .12F, "the fill keeps the plain glyph's weight: " + white + " against " + plain);
        paint(c -> c.text("H", 40, 20, 60, WHITE, Font.BOLD, UiCanvas.SANS, null));
        assertEquals(plainArea, count(), "a null effect is plain text");
    }

    @Test void aShadowRepeatsTheSilhouetteAndAGradientRampsDownTheLine() {
        paint(c -> c.text("I", 40, 10, 80, WHITE, Font.BOLD, UiCanvas.SANS, UiTextEffect.shadow(BLUE, 30, 0)));
        int white = 0, blue = 0;
        for (int y = 0; y < 120; y++) for (int x = 0; x < 200; x++) {
            int p = image.getRGB(x, y);
            if (p == WHITE) white++; else if (p == BLUE) blue++;
        }
        assertTrue(white > 200 && Math.abs(white - blue) < white * .1F, "the shadow is the glyph again: " + white + " and " + blue);
        paint(c -> c.text("I", 40, 10, 80, RED, Font.BOLD, UiCanvas.SANS, UiTextEffect.gradient(BLUE)));
        int top = 0, bottom = 0;
        for (int x = 0; x < 200; x++) { if (painted(x, 38)) top = image.getRGB(x, 38); if (painted(x, 82)) bottom = image.getRGB(x, 82); }
        assertTrue((top >> 16 & 0xFF) > (top & 0xFF), "red at the top: " + Integer.toHexString(top));
        assertTrue((bottom & 0xFF) > (bottom >> 16 & 0xFF), "blue at the bottom: " + Integer.toHexString(bottom));
    }

    @Test void leaningTextKeepsItsFeetAndMovesItsHead() {
        paint(c -> c.text("I", 60, 10, 80, WHITE, Font.BOLD, UiCanvas.SANS));
        int plainTop = -1, plainFoot = -1;
        for (int x = 0; x < 200; x++) { if (plainTop < 0 && painted(x, 36)) plainTop = x; if (plainFoot < 0 && painted(x, 82)) plainFoot = x; }
        paint(c -> c.text("I", 60, 10, 80, WHITE, Font.BOLD, UiCanvas.SANS, UiTextEffect.lean(20)));
        int top = -1, foot = -1;
        for (int x = 0; x < 200; x++) { if (top < 0 && painted(x, 36)) top = x; if (foot < 0 && painted(x, 82)) foot = x; }
        assertTrue(top > plainTop + 12, "the top of the letter moves right: " + plainTop + " to " + top);
        assertTrue(Math.abs(foot - plainFoot) <= 3, "the foot stays near the baseline position: " + plainFoot + " to " + foot);
        assertThrows(IllegalArgumentException.class, () -> UiTextEffect.lean(90));
    }

    @Test void aNumberStoredAsTextReadsAsANumber() {
        assertEquals(3, UiContext.number("3", 0), "the text a selectable choice wrote");
        assertEquals(2.5, UiContext.number(" 2.5 ", 0));
        assertEquals(7, UiContext.number(7, 0));
        assertEquals(9, UiContext.number("GLIDER", 9), "other text keeps the fallback");
        assertEquals(9, UiContext.number("NaN", 9));
        assertEquals(9, UiContext.number(null, 9));
    }

    @Test void longTextShrinksToFit() {
        paint(c -> {
            float wide = c.textWidth("A very long player name", 20, Font.BOLD, UiCanvas.SANS);
            assertEquals(20, c.fitTextSize("Short", wide, 20, Font.BOLD, UiCanvas.SANS));
            float size = c.fitTextSize("A very long player name", wide / 2, 20, Font.BOLD, UiCanvas.SANS);
            assertTrue(size < 20 && size >= 9, "about half: " + size);
            assertTrue(c.textWidth("A very long player name", size, Font.BOLD, UiCanvas.SANS) <= wide / 2 + .5F);
            assertEquals(1, c.fitTextSize("x", 0, 20, Font.PLAIN, UiCanvas.SANS));
        });
    }
}
