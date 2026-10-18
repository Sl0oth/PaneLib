package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class UiInputRegionTest {
    @Test
    void choosesNearestPaintedCaret() {
        UiInputRegion input = new UiInputRegion("field", "value", new UiRect(10, 10, 100, 24), 50,
            false, new float[]{20, 27, 35, 44}, new float[]{22, 22, 22, 22});
        assertEquals(0, input.caretAt(18, 22));
        assertEquals(1, input.caretAt(28, 22));
        assertEquals(3, input.caretAt(80, 22));
    }

    @Test
    void choosesCaretFromNearestPaintedLine() {
        UiInputRegion input = new UiInputRegion("area", "value", new UiRect(10, 10, 100, 60), 100,
            true, new float[]{20, 30, 20, 30}, new float[]{20, 20, 36, 36});
        assertEquals(1, input.caretAt(29, 20));
        assertEquals(3, input.caretAt(29, 36));
    }
}
