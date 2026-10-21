package dev.pane.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UiEditorViewportTest {
    @Test void pointerSelectionDoesNotRevealFocusAtEndButNextKeyDoes() {
        var view=new UiEditorViewport();
        view.layout("long",0,false,false,200,100,1000,0,12,14);
        view.scroll(-3);float browsed=view.offset;
        view.pointerPlacement=true;view.reveal=true;
        assertEquals(browsed,view.layout("long",90,false,true,200,100,1000,900,12,14));
        view.reveal=true;
        assertEquals(812,view.layout("long",90,false,true,200,100,1000,900,12,14));
    }
    @Test void browsingSurvivesPaintUntilNavigationOrEditing() {
        var view=new UiEditorViewport();
        assertEquals(0,view.layout("long",0,false,true,200,100,1000,0,12,14));
        assertTrue(view.scroll(-3));float browsed=view.offset;
        assertTrue(browsed>0);
        assertEquals(browsed,view.layout("long",0,false,true,200,100,1000,0,12,14));
        view.reveal=true;
        assertEquals(0,view.layout("long",0,false,true,200,100,1000,0,12,14));
        assertEquals(812,view.layout("long",30,false,true,200,100,1000,900,12,14));
        view.scroll(-1000);assertEquals(900,view.offset);assertFalse(view.scroll(-1));
        assertEquals(0,view.layout("short",1,false,true,200,100,12,0,12,14));
    }
    @Test void unfocusedBrowsingAndResizeStayBounded() {
        var view=new UiEditorViewport();
        view.layout("long",30,false,false,200,100,1000,900,12,14);
        view.scroll(-2);float offset=view.offset;
        assertEquals(offset,view.layout("long",30,false,false,200,100,1000,900,12,14));
        assertEquals(812,view.layout("long",30,false,true,200,100,1000,900,12,14));
        assertEquals(0,view.layout("long",30,false,true,200,1200,1000,900,12,14));
    }
}
