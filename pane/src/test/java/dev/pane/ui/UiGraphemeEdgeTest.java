package dev.pane.ui;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Boundary and wrapping cases beyond the pinned Unicode conformance corpus. */
final class UiGraphemeEdgeTest {
    private record Span(int start, int end) { }
    private record LinesCase(String source, List<Span> spans) { }
    private static final List<String> CLUSTERS = List.of(
        "e\u0301", "👍🏽", "🇺🇸", "👩‍🚀", "👨‍👩‍👧‍👦", "1️⃣", "\u0915\u094D\u0937");
    private static final List<LinesCase> LINE_CASES = List.of(
        new LinesCase("", List.of(new Span(0, 0))),
        new LinesCase("\n", List.of(new Span(0, 0), new Span(1, 1))),
        new LinesCase("\r", List.of(new Span(0, 0), new Span(1, 1))),
        new LinesCase("\r\n", List.of(new Span(0, 0), new Span(2, 2))),
        new LinesCase("A\r\nB", List.of(new Span(0, 1), new Span(3, 4))),
        new LinesCase("A\rB\r", List.of(new Span(0, 1), new Span(2, 3), new Span(4, 4))),
        new LinesCase("A\r\n\r\n", List.of(new Span(0, 1), new Span(3, 3), new Span(5, 5))),
        new LinesCase("\rA\n\r\nB\r", List.of(
            new Span(0, 0), new Span(1, 2), new Span(3, 3), new Span(5, 6), new Span(7, 7))),
        new LinesCase("e\u0301\r\n👩‍🚀\r", List.of(new Span(0, 2), new Span(4, 9), new Span(10, 10))));

    @Test void unshapedWrappingPreservesOriginalOffsetsAcrossEveryLineEnding() {
        for (var sample : LINE_CASES) {
            var measured = UiTextWrap.lines(sample.source(), 10_000, (start, end) -> end - start);
            assertEquals(sample.spans(), measured.stream().map(line -> new Span(line.start(), line.end())).toList(),
                "measured wrapping: " + printable(sample.source()));
            float[] advances = new float[sample.source().length()];
            java.util.Arrays.fill(advances, 1);
            var advanced = UiTextWrap.lines(sample.source(), 10_000, advances);
            assertEquals(sample.spans(), advanced.stream().map(line -> new Span(line.start(), line.end())).toList(),
                "advance-array wrapping: " + printable(sample.source()));
        }
    }

    @Test void shapedWrappingPreservesEmptyAndTrailingLinesWithoutPaintingSeparators() {
        var image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var canvas = new UiCanvas(graphics, image);
            for (var sample : LINE_CASES) {
                var lines = canvas.wrapShapedText(sample.source(), 10_000, 16, Font.PLAIN, UiCanvas.SANS);
                assertEquals(sample.spans(), lines.stream().map(line -> new Span(line.start(), line.end())).toList(),
                    printable(sample.source()));
                for (var line : lines) {
                    String visible = sample.source().substring(line.start(), line.end());
                    assertFalse(visible.contains("\r") || visible.contains("\n"));
                    assertTrue(Float.isFinite(line.text().x(0)), "line start remains a usable caret");
                    assertTrue(Float.isFinite(line.text().x(line.end() - line.start())), "line end remains a usable caret");
                    if (visible.isEmpty()) assertEquals(0, line.text().width());
                }
            }
        } finally {
            graphics.dispose();
        }
    }

    @Test void emptyTextAndClampedOffsetsAlwaysResolveToZero() {
        var index = UiTextBoundary.index("");
        for (int offset : new int[]{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}) {
            assertEquals(0, index.floor(offset));
            assertEquals(0, index.ceil(offset));
            assertEquals(0, index.previous(offset));
            assertEquals(0, index.next(offset));
            assertEquals("", UiTextBoundary.truncate("", offset));
        }
        assertTrue(index.contains(0));
        assertFalse(index.contains(-1));
        assertFalse(index.contains(1));
        assertEquals(new UiTextBoundary.Selection(0, 0), UiTextBoundary.wordAt("", 0));
    }

    @Test void ceilMovesForwardFromEveryInteriorUtf16OffsetAndKeepsExactEdges() {
        for (String cluster : CLUSTERS) {
            String value = "A" + cluster + "B";
            var index = UiTextBoundary.index(value);
            assertEquals(0, index.ceil(Integer.MIN_VALUE));
            assertEquals(value.length(), index.ceil(Integer.MAX_VALUE));
            for (int edge : new int[]{0, 1, 1 + cluster.length(), value.length()}) {
                assertEquals(edge, index.ceil(edge));
                assertEquals(edge, UiTextBoundary.ceil(value, edge));
            }
            for (int at = 2; at < 1 + cluster.length(); at++) {
                assertEquals(1 + cluster.length(), index.ceil(at), cluster + " offset " + at);
                assertEquals(1 + cluster.length(), UiTextBoundary.ceil(value, at));
                assertEquals(1, index.floor(at));
                assertFalse(index.contains(at));
            }
        }
    }

    @Test void concatenationRecomputesCombiningZwjAndSurrogateSeams() {
        for (String[] parts : List.of(new String[]{"e", "\u0301"},
                new String[]{"👩\u200D", "🚀"}, new String[]{"\uD83D", "\uDE80"},
                new String[]{"🇺", "🇸"})) {
            var left = UiTextBoundary.index(parts[0]);
            var right = UiTextBoundary.index(parts[1]);
            String joined = parts[0] + parts[1];
            var combined = UiTextBoundary.index(joined);
            int seam = parts[0].length();
            assertTrue(left.contains(seam));
            assertTrue(right.contains(0));
            assertFalse(combined.contains(seam), "concatenation must remove the former boundary");
            assertEquals(0, combined.floor(seam));
            assertEquals(joined.length(), combined.ceil(seam));
            assertEquals(joined.length(), combined.next(0));
            assertTrue(left.contains(seam), "previously acquired indices remain immutable");
        }
    }

    @Test void prependingRegionalIndicatorChangesPairingBeyondTheJoin() {
        String original = "🇺🇸🇨";
        var before = UiTextBoundary.index(original);
        String prefixed = "🇦" + original;
        var after = UiTextBoundary.index(prefixed);
        assertTrue(before.contains(4));
        assertEquals(6, before.next(4));
        assertEquals(4, after.next(0));
        assertEquals(8, after.next(4));
        assertFalse(after.contains(6), "old boundary shifted by prefix is no longer valid");
        assertEquals(4, after.floor(6));
        assertEquals(8, after.ceil(6));
        assertTrue(before.contains(4));
    }

    @Test void isolatedSurrogatesRemainAddressableWithoutSplittingValidPairs() {
        String value = "\uD83Dx🚀y\uDC00";
        var index = UiTextBoundary.index(value);
        var expected = List.of(0, 1, 2, 4, 5, 6);
        assertEquals(expected, positions(index));
        assertEquals(2, index.floor(3));
        assertEquals(4, index.ceil(3));
        assertEquals("\uD83D", UiTextBoundary.truncate(value, 1));
        assertEquals("\uD83Dx", UiTextBoundary.truncate(value, 3));
        assertEquals(List.of(0, 1, 2), positions(UiTextBoundary.index("\uDC00\uD83D")));
    }

    @Test void narrowWrappingPreservesMalformedUtf16AndWholeValidPairs() {
        String value = "\uD83Dx🚀y\uDC00";
        var valid = List.of(0, 1, 2, 4, 5, 6);
        var image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var canvas = new UiCanvas(graphics, image);
            for (float width : new float[]{.01F, 10_000}) {
                var simple = UiTextWrap.lines(value, width, (start, end) -> end - start);
                assertReassembles(value, simple.stream().map(line -> new Span(line.start(), line.end())).toList(), valid);
                var shaped = canvas.wrapShapedText(value, width, 16, Font.PLAIN, UiCanvas.SANS);
                assertReassembles(value, shaped.stream().map(line -> new Span(line.start(), line.end())).toList(), valid);
            }
        } finally {
            graphics.dispose();
        }
    }

    @Test void offsetsBeyondCacheBudgetRetainFullUtf16Range() {
        int prefix = 65_536;
        String value = "x".repeat(prefix) + "e\u0301👩‍🚀Z";
        var index = UiTextBoundary.index(value);
        assertEquals(prefix + 8, index.length);
        assertEquals(prefix, index.floor(prefix + 1));
        assertEquals(prefix + 2, index.ceil(prefix + 1));
        assertEquals(prefix + 2, index.floor(prefix + 5));
        assertEquals(prefix + 7, index.ceil(prefix + 5));
        assertEquals(prefix + 7, index.next(prefix + 2));
        assertEquals(prefix + 2, index.previous(prefix + 7));
        assertEquals(value.length(), index.next(value.length()));
        assertEquals(value.substring(0, prefix + 2), UiTextBoundary.truncate(value, prefix + 6));
        assertEquals(value, UiTextBoundary.truncate(value, Integer.MAX_VALUE));
    }

    @Test void longCombiningSequenceRemainsOneClusterAcrossCacheBudget() {
        String cluster = "e" + "\u0301".repeat(70_000);
        var index = UiTextBoundary.index(cluster);
        assertEquals(List.of(0, cluster.length()), positions(index));
        for (int inside : new int[]{1, 32_768, 65_536, cluster.length() - 1}) {
            assertEquals(0, index.floor(inside));
            assertEquals(cluster.length(), index.ceil(inside));
            assertFalse(index.contains(inside));
        }
        assertEquals("", UiTextBoundary.truncate(cluster, 65_536));
        assertEquals(cluster, UiTextBoundary.truncate(cluster, cluster.length()));
        String value = cluster + "Z";
        var lines = UiTextWrap.lines(value, .01F, (start, end) -> end - start);
        assertEquals(List.of(new UiTextWrap.Line(0, cluster.length()),
            new UiTextWrap.Line(cluster.length(), value.length())), lines);
    }

    @Test void utf16LimitsKeepZeroExactAndInteriorClusterBudgetsDistinct() {
        for (String cluster : CLUSTERS) {
            String value = cluster + "Z";
            assertEquals("", UiTextBoundary.truncate(value, -1));
            assertEquals("", UiTextBoundary.truncate(value, 0));
            for (int limit = 1; limit < cluster.length(); limit++) {
                assertEquals("", UiTextBoundary.truncate(value, limit), cluster + " limit " + limit);
                assertEquals("A", UiTextBoundary.truncate("A" + value, limit + 1));
            }
            assertEquals(cluster, UiTextBoundary.truncate(value, cluster.length()));
            assertEquals(value, UiTextBoundary.truncate(value, value.length()));
            assertEquals(value, UiTextBoundary.truncate(value, Integer.MAX_VALUE));
        }
        assertEquals("A", UiTextBoundary.truncate("A\r\nB", 2), "a CRLF pair is one grapheme");
        assertEquals("A\r\n", UiTextBoundary.truncate("A\r\nB", 3));
    }

    private static List<Integer> positions(UiTextBoundary.Index index) {
        var result = new ArrayList<Integer>();
        result.add(0);
        for (int at = 0; at < index.length;) {
            int next = index.next(at);
            assertTrue(next > at && next <= index.length, "walking boundaries must progress");
            result.add(next);
            at = next;
        }
        return result;
    }

    private static void assertReassembles(String value, List<Span> spans, List<Integer> boundaries) {
        var joined = new StringBuilder();
        int previous = 0;
        for (var span : spans) {
            assertEquals(previous, span.start(), "wrapping must not drop or duplicate code units");
            assertTrue(span.end() > span.start());
            assertTrue(boundaries.contains(span.start()) && boundaries.contains(span.end()));
            joined.append(value, span.start(), span.end());
            previous = span.end();
        }
        assertEquals(value.length(), previous);
        assertEquals(value, joined.toString());
    }

    private static String printable(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
