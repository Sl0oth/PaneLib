package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UiTextWrapTest {
    private java.util.List<UiTextWrap.Line> wrap(String text, int width) {
        float[] widths = new float[text.length()];
        for (int i = 0; i < text.length(); i = UiTextBoundary.next(text, i)) widths[i] = 1;
        return UiTextWrap.lines(text, width, widths);
    }
    @Test void ordinaryWordsStayWholeAndOffsetsPreserveWhitespace() {
        String text = "Alpha bravo charlie delta echo foxtrot hotel india quebec";
        var lines = wrap(text, 13);
        StringBuilder joined = new StringBuilder();
        for (var line : lines) {
            joined.append(text, line.start(), line.end());
            if (line.end() < text.length()) assertTrue(Character.isWhitespace(text.charAt(line.end() - 1)));
        }
        assertEquals(text, joined.toString());
    }
    @Test void oversizedWordsSplitSafelyAndExplicitBlankLinesSurvive() {
        String text = "😀😀😀😀\n\nx";
        assertEquals(java.util.List.of(new UiTextWrap.Line(0,4), new UiTextWrap.Line(4,8),
            new UiTextWrap.Line(9,9),new UiTextWrap.Line(10,11)), wrap(text,2));
        assertEquals(java.util.List.of(new UiTextWrap.Line(0,0)), wrap("",0));
    }
    @Test void sharedBoundaryHasTwoCaretPositions() {
        var first = new UiTextLine(0,3,new float[]{0,10,20,30},10);
        var second = new UiTextLine(3,6,new float[]{0,10,20,30},30);
        var input = new UiInputRegion("a","a",new UiRect(0,0,100,60),100,true,
            new float[]{0,10,20,0,10,20,30},new float[]{10,10,10,30,30,30,30},java.util.List.of(first,second));
        assertEquals(new UiInputRegion.Caret(3,true), input.lineEdge(0,false,true));
        assertEquals(new UiInputRegion.Caret(0,false), input.lineEdge(3,true,false));
        assertEquals(new UiInputRegion.Caret(3,false), input.lineEdge(3,false,false));
        assertEquals(new UiInputRegion.Caret(3,true), input.caretPositionAt(90,10));
        assertEquals(new UiInputRegion.Caret(3,false), input.caretPositionAt(0,30));
        assertEquals(new UiInputRegion.Caret(6,true), input.verticalCaret(3,true,1,30));
    }
}
