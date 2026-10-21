package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class UiInputRegionTest {
    @Test void pageNavigationUsesPaintedLinesAndKeepsPreferredColumn() {
        var lines = new java.util.ArrayList<UiTextLine>();
        for (int i=0;i<10;i++) lines.add(new UiTextLine(i*3,i*3+2,new float[]{0,10,20},i*20));
        var input = new UiInputRegion("area","value",new UiRect(0,0,100,100),100,true,
            new float[]{0},new float[]{0},lines);
        assertEquals(14,input.pageCaret(2,false,1,20,100).index());
        assertEquals(2,input.pageCaret(14,false,-1,20,100).index());
        assertEquals(29,input.pageCaret(26,false,1,20,100).index());
        assertEquals(0,input.pageCaret(0,false,-1,20,100).index());
        assertEquals(5,input.pageCaret(2,false,1,20,5).index());
    }

    @Test void verticalNavigationKeepsColumnAcrossShortLinesAndSkipsInvalidCarets() {
        var input = new UiInputRegion("area", "value", new UiRect(0, 0, 100, 60), 100, true,
            new float[]{0, 10, 20, 30, 0, 10, Float.NaN, 0, 10, 20, 30},
            new float[]{0, 0, 0, 0, 20, 20, 20, 40, 40, 40, 40});
        assertEquals(5, input.verticalCaret(3, 1, 30));
        assertEquals(10, input.verticalCaret(5, 1, 30));
        assertEquals(3, input.verticalCaret(5, -1, 30));
        assertEquals(0, input.lineEdge(3, false));
        assertEquals(5, input.lineEdge(4, true));
        assertEquals(10, input.verticalCaret(10, 1, 30));
    }

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
