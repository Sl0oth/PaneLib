package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiHorizontalScrollTest {
    private static final class Paint implements AutoCloseable {
        final BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = image.createGraphics();
        final UiCanvas canvas = new UiCanvas(g, image);
        final UiInteractionState interactions = new UiInteractionState();
        final List<UiHitRegion> hits = new ArrayList<>();
        final List<UiScrollRegion> scrolls = new ArrayList<>();
        UiContext ui() {
            hits.clear(); scrolls.clear();
            java.util.Arrays.fill(((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData(), 0);
            interactions.beginPaint();
            return new UiContext(canvas, UiTheme.dark(), 400, 300, 0xFFFFAA00, new UiRect(0, 0, 400, 300), null, null,
                1, 1, false, 0, new HashMap<>(), hits, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                scrolls, new ArrayList<>(), new ArrayList<>(), new UiTextInputState(null, 0, 0), new ArrayList<>(), new ArrayList<>(), null, 1, null)
                .withInteractions(interactions);
        }
        UiHitRegion hit(String id) { return hits.stream().filter(h -> h.id().equals(id)).findFirst().orElseThrow(); }
        public void close() { g.dispose(); }
    }

    /** Ten 100-wide cards in a 200-wide strip at (50, 40). */
    private static void strip(UiContext ui) {
        ui.scrollHorizontal("strip", new UiRect(50, 40, 200, 60), 1000, inner -> {
            for (int i = 0; i < 10; i++) {
                inner.canvas().fill(0xFF000000 | (i + 1) * 20, 50 + i * 100, 40, 100, 50);
                inner.hit("card-" + i, "open-" + i, 50 + i * 100 + 10, 50, 80, 30);
            }
        });
    }

    @Test void contentMovesSidewaysWithTheOffsetAndIsClippedToTheViewport() {
        try (var p = new Paint()) {
            strip(p.ui());
            var region = p.scrolls.getFirst();
            assertTrue(region.horizontal());
            assertEquals(800, region.maxOffset());
            assertEquals(0xFF000000 | 20, p.image.getRGB(60, 50), "first card at the left edge");
            assertEquals(0, p.image.getRGB(260, 50) >>> 24, "nothing right of the viewport");
            assertEquals(0, p.image.getRGB(40, 50) >>> 24, "nothing left of the viewport");
            assertEquals(60, p.hit("card-0").x());

            p.interactions.offsets.put("strip", 250D);
            strip(p.ui());
            assertEquals(0xFF000000 | 60, p.image.getRGB(60, 50), "third card under the left edge after scrolling 250");
            assertEquals(60 + 300 - 250, p.hit("card-3").x(), "hit regions move with the content");
            assertEquals(50, p.hit("card-3").y(), "and keep their row");

            p.interactions.offsets.put("strip", 5000D);
            strip(p.ui());
            assertEquals(800D, p.interactions.offsets.get("strip"), "offset clamps to the content");
        }
    }

    @Test void fittingContentDoesNotScrollOrDrawABar() {
        try (var p = new Paint()) {
            p.ui().scrollHorizontal("fits", new UiRect(10, 10, 300, 40), 200, inner -> { });
            assertEquals(0, p.scrolls.getFirst().maxOffset());
            for (int x = 10; x < 310; x++) assertEquals(0, p.image.getRGB(x, 47) >>> 24);
        }
    }

    @Test void keyboardFocusRevealsAControlScrolledOffEitherSide() {
        try (var p = new Paint()) {
            strip(p.ui());
            assertEquals(Set.of("strip"), p.interactions.reveal("card-7"));
            double offset = p.interactions.offsets.get("strip");
            // Card 7's hit spans 760..840 on the page and the viewport ends at 250: 840 + 4 of focus padding - 250.
            assertEquals(594, offset, .001);
            assertTrue(offset > 0, "scrolled just far enough right: " + offset);
            strip(p.ui());
            UiHitRegion card = p.hit("card-7");
            assertTrue(card.x() >= 50 && card.x() + card.width() <= 250, "the revealed card is inside the viewport");
            assertEquals(Set.of("strip"), p.interactions.reveal("card-0"));
            assertTrue(p.interactions.offsets.get("strip") <= 10, "and back left for the first card");
            strip(p.ui());
            assertEquals(Set.of(), p.interactions.reveal("card-1"), "a visible card needs no scrolling");
        }
    }

    @Test void sidewaysAndVerticalRegionsNestAndRevealOnTheirOwnAxes() {
        try (var p = new Paint()) {
            Runnable paint = () -> p.ui().scroll("page", new UiRect(0, 0, 400, 200), 900, page ->
                page.scrollHorizontal("row", new UiRect(20, 600, 300, 60), 1200, row ->
                    row.hit("far", "far", 20 + 1000, 610, 80, 30)));
            paint.run();
            assertEquals(2, p.scrolls.size());
            assertFalse(p.scrolls.get(0).horizontal());
            assertTrue(p.scrolls.get(1).horizontal());
            assertEquals(Set.of("page", "row"), p.interactions.reveal("far"));
            paint.run();
            UiHitRegion far = p.hit("far");
            assertTrue(far.y() >= 0 && far.y() + far.height() <= 200, "revealed vertically: " + far.y());
            assertTrue(far.x() >= 20 && far.x() + far.width() <= 320, "revealed sideways: " + far.x());
        }
    }

    @Test void snapshotsReportTheSidewaysExtent() {
        var wide = new UiScrollSnapshot("strip", new UiRect(0, 0, 200, 60), 1000, 0, 0, true);
        assertEquals(800, wide.maximumOffset());
        var tall = new UiScrollSnapshot("page", new UiRect(0, 0, 200, 60), 1000, 0, 0);
        assertFalse(tall.horizontal());
        assertEquals(940, tall.maximumOffset());
    }

    @Test void variableListPaintsTheRowsInViewAndOnePastEachEdgeAtTheirOwnHeights() {
        try (var p = new Paint()) {
            // Row i is 20 + 10 * (i % 4) tall with a gap of 5: tops 0, 25, 60, 105, 160, 185, ...
            java.util.function.IntToDoubleFunction heights = i -> 20 + 10 * (i % 4);
            var painted = new ArrayList<UiVirtualItem>();
            int[] asked = {0};
            Runnable paint = () -> {
                painted.clear();
                p.ui().variableList("feed", new UiRect(10, 20, 300, 100), 1000, i -> { asked[0]++; return heights.applyAsDouble(i); }, 5,
                    (row, item) -> { painted.add(item); row.hit("row-" + item.index(), "open", item.bounds().x(), item.bounds().y(), 50, item.bounds().height()); });
            };
            paint.run();
            assertEquals(List.of(0, 1, 2, 3), painted.stream().map(UiVirtualItem::index).toList(), "three in view and the next");
            assertTrue(p.hits.stream().noneMatch(h -> h.id().equals("row-3")), "the row past the edge takes no clicks");
            assertTrue(p.interactions.targets.containsKey("row-3"), "but Tab can reach it");
            assertEquals(Set.of("feed"), p.interactions.reveal("row-3"), "and focusing it scrolls it in");
            p.interactions.offsets.put("feed", 0D);
            paint.run();
            assertEquals(new UiRect(10, 20, 300, 20), painted.get(0).bounds());
            assertEquals(new UiRect(10, 45, 300, 30), painted.get(1).bounds());
            assertEquals(new UiRect(10, 80, 300, 40), painted.get(2).bounds());
            var region = p.scrolls.getFirst();
            assertEquals("feed", region.id());
            // 250 groups of (20+30+40+50) plus 999 gaps.
            assertEquals(250 * 140 + 999 * 5, region.contentHeight(), .01F);

            p.interactions.offsets.put("feed", 100D);
            paint.run();
            assertEquals(List.of(2, 3, 4, 5, 6), painted.stream().map(UiVirtualItem::index).toList(), "rows 3 to 5 cross 100..200; one more each side");
            assertEquals(20 + 105 - 100, p.hit("row-3").y(), "hit regions scroll with their rows");

            p.interactions.offsets.put("feed", 1e9);
            paint.run();
            assertEquals(999, painted.getLast().index(), "the end of the list is reachable");
            assertTrue(painted.size() <= 7);
        }
    }

    @Test void variableListHandlesEmptyAndOddHeights() {
        try (var p = new Paint()) {
            p.ui().variableList("none", new UiRect(0, 0, 100, 100), 0, i -> 10, 4, (row, item) -> fail("no rows"));
            assertEquals(0, p.scrolls.getFirst().contentHeight());
            float[] tops = UiContext.variableTops(4, i -> i == 1 ? Double.NaN : i == 2 ? -5 : 10, 2);
            assertArrayEquals(new float[]{0, 12, 14, 16, 28}, tops);
            assertEquals(0, UiContext.firstVariableRow(tops, 4, 2, 0));
            assertEquals(0, UiContext.firstVariableRow(tops, 4, 2, 9.9));
            assertEquals(1, UiContext.firstVariableRow(tops, 4, 2, 10), "a row ending exactly at the offset is out of view");
            assertEquals(3, UiContext.firstVariableRow(tops, 4, 2, 16));
            assertEquals(4, UiContext.firstVariableRow(tops, 4, 2, 500));
        }
    }
}
