package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class UiTextHistoryTest {
    private UiTextHistory.Snapshot s(String value, int cursor, int anchor) { return new UiTextHistory.Snapshot(value, cursor, anchor); }
    @Test void undoRedoPreservesSelectionAndNewEditInvalidatesRedo() {
        var h = new UiTextHistory();
        var before = s("alpha beta", 10, 6); var after = s("alpha B", 7, 7);
        h.record(before, after);
        assertEquals(before, h.undo(after).orElseThrow());
        assertEquals(after, h.redo(before).orElseThrow());
        assertEquals(before, h.undo(after).orElseThrow());
        h.record(before, s("alpha C", 7, 7));
        assertTrue(h.redo(s("alpha C", 7, 7)).isEmpty());
    }
    @Test void boundsHistoryAndDoesNotUndoOverExternalChanges() {
        var h = new UiTextHistory(2, 10);
        h.record(s("a",1,1),s("ab",2,2)); h.record(s("ab",2,2),s("abc",3,3)); h.record(s("abc",3,3),s("abcd",4,4));
        assertEquals("abc",h.undo(s("abcd",4,4)).orElseThrow().value());
        assertEquals("ab",h.undo(s("abc",3,3)).orElseThrow().value());
        assertTrue(h.undo(s("ab",2,2)).isEmpty());
        h.record(s("ab",2,2),s("new",3,3));
        assertTrue(h.undo(s("external",8,8)).isEmpty());
        assertTrue(h.redo(s("external",8,8)).isEmpty());
        var small = new UiTextHistory(32,3);
        small.record(s("oversized",9,9), s("x",1,1)); assertTrue(small.undo(s("x",1,1)).isEmpty());
    }
    @Test void codepointBoundariesNeverSplitSurrogatePairs() {
        String v = "A😀B";
        assertEquals(1,UiTextBoundary.floor(v,2));
        assertEquals(1,UiTextBoundary.previous(v,3)); assertEquals(3,UiTextBoundary.next(v,1));
        assertEquals("A",UiTextBoundary.truncate(v,2)); assertEquals("A😀",UiTextBoundary.truncate(v,3));
        assertEquals(1,s(v,2,2).cursor());
    }

    @Test void continuedEditsUndoAsOneStep() {
        var history=new UiTextHistory();
        var empty=new UiTextHistory.Snapshot("",0,0);
        var one=new UiTextHistory.Snapshot("日",1,1);
        var two=new UiTextHistory.Snapshot("日本",2,2);
        var three=new UiTextHistory.Snapshot("日本語",3,3);
        history.record(empty,one);
        history.extend(one,two);
        history.extend(two,three);
        assertEquals(empty,history.undo(three).orElseThrow(),"a committed word undoes in one step");
        assertEquals(three,history.redo(empty).orElseThrow());
        var other=new UiTextHistory.Snapshot("x",1,1);
        history.extend(other,new UiTextHistory.Snapshot("xy",2,2));
        assertEquals(other,history.undo(new UiTextHistory.Snapshot("xy",2,2)).orElseThrow(),"an extend that does not continue starts its own step");
    }
}
