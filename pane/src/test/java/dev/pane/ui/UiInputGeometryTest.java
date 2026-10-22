package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Editor geometry keeps its defensive-copy contract across ordinary and clipped public painters. */
final class UiInputGeometryTest {
    private static final String TEXT = "Ae\u0301👩‍🚀Z";
    private static final float DX = 13.25F, DY = -7.5F, SCROLL = 17.5F;
    private enum Kind { FIELD, PASSWORD, AREA }
    private enum Placement { ZERO, TRANSLATED, SCROLLED, NESTED_SCROLL }
    private record Painted(UiInputRegion pointer, UiInputRegion registered) { }

    @Test void visualLineOwnsItsCoordinatesAcrossZeroAndFractionalTranslations() {
        float[] supplied = {10, Float.NaN, 31};
        var original = new UiTextLine(4, 6, supplied, 20);
        supplied[0] = -100;
        original.xs()[2] = -200;
        assertArrayEquals(new float[]{10, Float.NaN, 31}, original.xs());
        for (float[] shift : new float[][]{{0, 0}, {0, DY}, {DX, DY}}) {
            var translated = original.translate(shift[0], shift[1]);
            assertEquals(4, translated.start()); assertEquals(6, translated.end());
            assertEquals(20 + shift[1], translated.y());
            assertArrayEquals(new float[]{10 + shift[0], Float.NaN, 31 + shift[0]}, translated.xs());
            Arrays.fill(translated.xs(), 999);
            assertEquals(10 + shift[0], translated.x(4));
            assertTrue(Float.isNaN(translated.x(5)));
            assertEquals(31 + shift[0], translated.x(6));
            assertArrayEquals(new float[]{10, Float.NaN, 31}, original.xs());
        }
    }

    @Test void inputRegionOwnsBothArraysAndItsLineList() {
        float[] xs = {10, Float.NaN, 30}, ys = {20, 20, 20};
        var lines = new ArrayList<>(List.of(new UiTextLine(0, 2, xs, 20)));
        var input = new UiInputRegion("field", "value", new UiRect(0, 0, 100, 40), 100,
            true, xs, ys, lines, false);
        Arrays.fill(xs, 888); Arrays.fill(ys, 888); lines.clear();
        assertArrayEquals(new float[]{10, Float.NaN, 30}, input.caretXs());
        assertArrayEquals(new float[]{20, 20, 20}, input.caretYs());
        assertEquals(1, input.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> input.lines().clear());
        Arrays.fill(input.caretXs(), 999); Arrays.fill(input.caretYs(), 999);
        Arrays.fill(input.lines().getFirst().xs(), 999);
        assertEquals(10, input.caretX(0)); assertEquals(30, input.caretX(2, false));
        assertEquals(new UiInputRegion.Caret(0, false), input.caretPositionAt(10, 20));
        assertEquals(new UiInputRegion.Caret(2, true), input.caretPositionAt(30, 20));
        assertArrayEquals(new float[]{20, 20, 20}, input.caretYs());
    }

    @TestFactory Stream<DynamicTest> publicEditorPaintersPreserveGeometryAndClusterBoundaries() {
        var tests = new ArrayList<DynamicTest>();
        for (Kind kind : Kind.values()) for (Placement placement : Placement.values()) {
            tests.add(DynamicTest.dynamicTest(kind + " " + placement, () -> {
                var baseline = paint(kind, Placement.ZERO);
                var actual = paint(kind, placement);
                float dx = placement == Placement.TRANSLATED || placement == Placement.NESTED_SCROLL ? DX : 0;
                float dy = placement == Placement.TRANSLATED || placement == Placement.NESTED_SCROLL ? DY : 0;
                boolean clipped = placement == Placement.SCROLLED || placement == Placement.NESTED_SCROLL;
                if (clipped) dy -= SCROLL;
                var originalBounds = baseline.registered.bounds();
                assertEquals(originalBounds.translate(dx, dy), actual.registered.bounds(),
                    "keyboard registration retains the full translated control");
                UiRect expectedPointer = actual.registered.bounds();
                if (clipped) {
                    float viewportX = 35 + dx, viewportY = 40 + dy + SCROLL;
                    float left = Math.max(expectedPointer.x(), viewportX);
                    float top = Math.max(expectedPointer.y(), viewportY);
                    expectedPointer = new UiRect(left, top,
                        Math.min(expectedPointer.right(), viewportX + 100) - left,
                        Math.min(expectedPointer.bottom(), viewportY + 40) - top);
                    assertNotEquals(expectedPointer, actual.registered.bounds(),
                        "clipping must not shrink keyboard/focus registration");
                }
                assertEquals(expectedPointer, actual.pointer.bounds());
                assertShifted(baseline.registered, actual.registered, dx, dy);
                assertArrayEquals(actual.registered.caretXs(), actual.pointer.caretXs());
                assertArrayEquals(actual.registered.caretYs(), actual.pointer.caretYs());
                Set<Integer> boundaries = kind == Kind.AREA
                    ? Set.of(0, 1, 3, 8, 9, 10, 11, 13, 18, 19) : Set.of(0, 1, 3, 8, 9);
                float[] expectedXs = actual.registered.caretXs(), expectedYs = actual.registered.caretYs();
                for (int i = 0; i < expectedXs.length; i++)
                    assertEquals(boundaries.contains(i), Float.isFinite(expectedXs[i]), "grapheme edge " + i);
                for (int y = 0; y <= 170; y += 17) for (int x = 0; x <= 350; x += 19) {
                    var before = baseline.registered.caretPositionAt(x, y);
                    var moved = actual.pointer.caretPositionAt(x + dx, y + dy);
                    assertEquals(before, moved, "translation preserves logical pointer placement");
                    assertTrue(boundaries.contains(moved.index()), "masked and ordinary pointers exclude cluster interiors");
                }
                // A consumer mutating any returned array must not affect either registration.
                Arrays.fill(actual.pointer.caretXs(), 999); Arrays.fill(actual.pointer.caretYs(), 999);
                for (var line : actual.pointer.lines()) Arrays.fill(line.xs(), 999);
                assertArrayEquals(expectedXs, actual.pointer.caretXs());
                assertArrayEquals(expectedYs, actual.pointer.caretYs());
                assertArrayEquals(expectedXs, actual.registered.caretXs());
                assertArrayEquals(expectedYs, actual.registered.caretYs());
                assertShifted(baseline.registered, actual.registered, dx, dy);
            }));
        }
        return tests.stream();
    }

    private static void assertShifted(UiInputRegion before, UiInputRegion after, float dx, float dy) {
        shifted(before.caretXs(), after.caretXs(), dx);
        shifted(before.caretYs(), after.caretYs(), dy);
        assertEquals(before.masked(), after.masked()); assertEquals(before.multiline(), after.multiline());
        assertEquals(before.maxLength(), after.maxLength()); assertEquals(before.lines().size(), after.lines().size());
        for (int i = 0; i < before.lines().size(); i++) {
            var first = before.lines().get(i); var second = after.lines().get(i);
            assertEquals(first.start(), second.start()); assertEquals(first.end(), second.end());
            assertEquals(first.y() + dy, second.y(), .0001F);
            shifted(first.xs(), second.xs(), dx);
        }
    }

    private static void shifted(float[] before, float[] after, float delta) {
        assertEquals(before.length, after.length);
        for (int i = 0; i < before.length; i++) {
            if (Float.isNaN(before[i])) assertTrue(Float.isNaN(after[i]), "interior caret " + i);
            else assertEquals(before[i] + delta, after[i], .0001F, "coordinate " + i);
        }
    }

    private static Painted paint(Kind kind, Placement placement) {
        var image = new BufferedImage(400, 240, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var retained = new UiInteractionState(); retained.offsets.put("parent", (double) SCROLL);
            var inputs = new ArrayList<UiInputRegion>();
            var ui = new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), 400, 240, 0xFFE3B85B,
                new UiRect(0, 0, 400, 240), null, null, 1, 1, false, 0, new HashMap<>(),
                new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>(),
                new ArrayList<>(), inputs, new ArrayList<>(), new UiTextInputState("editor", 0, 0),
                new ArrayList<>(), new ArrayList<>(), null, 1, null).withInteractions(retained);
            java.util.function.Consumer<UiContext> editor = context -> {
                if (kind == Kind.AREA) context.textArea("editor", "value", "", 20, 50, 140, 95, TEXT + "\n" + TEXT, 100);
                else if (kind == Kind.PASSWORD) context.passwordField("editor", "value", "", 20, 50, 260, 40, TEXT, 100, UiInputStyle.defaults());
                else context.textField("editor", "value", "", 20, 50, 260, 40, TEXT, 100, UiInputStyle.defaults());
            };
            switch (placement) {
                case ZERO -> editor.accept(ui);
                case TRANSLATED -> ui.withTranslation(DX, DY, editor);
                case SCROLLED -> ui.scroll("parent", new UiRect(35, 40, 100, 40), 200, editor);
                case NESTED_SCROLL -> ui.withTranslation(DX, DY,
                    child -> child.scroll("parent", new UiRect(35, 40, 100, 40), 200, editor));
            }
            assertEquals(1, inputs.size()); assertEquals(1, retained.inputs.size());
            return new Painted(inputs.getFirst(), retained.inputs.get("editor"));
        } finally { graphics.dispose(); }
    }
}
