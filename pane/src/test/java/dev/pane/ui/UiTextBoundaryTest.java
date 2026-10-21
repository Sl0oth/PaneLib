package dev.pane.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UiTextBoundaryTest {
    @Test void wordMovementSkipsWhitespaceAndStopsAtWordEdges() {
        String text="alpha  beta";
        assertEquals(7, UiTextBoundary.previousWord(text,11));
        assertEquals(0, UiTextBoundary.previousWord(text,7));
        assertEquals(5, UiTextBoundary.nextWord(text,0));
        assertEquals(11, UiTextBoundary.nextWord(text,5));
        assertEquals(0, UiTextBoundary.previousWord(text,-20));
        assertEquals(11, UiTextBoundary.nextWord(text,200));
        assertEquals(0, UiTextBoundary.previousWord("",0));
        assertEquals(0, UiTextBoundary.nextWord("",0));
    }
    @Test void unicodeWordMovementMakesProgressWithoutSplittingSurrogates() {
        for(String text:new String[]{"hello, world!", "a\u0301 b", "😀 🚀 test", "中文 العربية", "a\u00a0b", "one\n\ttwo"}) {
            for(int i=0;i<=text.length();i++) {
                int at=UiTextBoundary.floor(text,i), previous=UiTextBoundary.previousWord(text,i), next=UiTextBoundary.nextWord(text,i);
                assertEquals(previous,UiTextBoundary.floor(text,previous));
                assertEquals(next,UiTextBoundary.floor(text,next));
                assertTrue(at==0 ? previous==0 : previous<at, text+" previous "+i);
                assertTrue(at==text.length() ? next==at : next>at, text+" next "+i);
            }
        }
    }
    @Test void doubleClickSelectsOnlyThePointedSegment() {
        assertEquals(new UiTextBoundary.Selection(0,5),UiTextBoundary.wordAt("alpha beta",2));
        assertEquals(new UiTextBoundary.Selection(6,10),UiTextBoundary.wordAt("alpha beta",10));
        assertEquals(new UiTextBoundary.Selection(5,6),UiTextBoundary.wordAt("alpha beta",5));
        assertEquals(new UiTextBoundary.Selection(0,2),UiTextBoundary.wordAt("😀 text",1));
        assertEquals(new UiTextBoundary.Selection(0,0),UiTextBoundary.wordAt("",0));
    }
    @Test void combiningMarkStaysWithItsWord() {
        assertEquals(0,UiTextBoundary.previousWord("cafe\u0301",5));
        assertEquals(5,UiTextBoundary.nextWord("cafe\u0301",0));
    }
}
