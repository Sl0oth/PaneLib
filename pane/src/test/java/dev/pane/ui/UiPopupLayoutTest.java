package dev.pane.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class UiPopupLayoutTest {
    @Test void largeMenusFitAndRevealEitherEnd() {
        var layout=UiPopupLayout.menu(new UiRect(380,380,100,28),900,80,400,400);
        assertTrue(layout.frame().right()<=400 && layout.frame().bottom()<=400);
        assertTrue(layout.frame().x()>=0 && layout.frame().y()>=0);
        float end=layout.reveal(0,79);
        assertEquals(layout.maxOffset(),end);
        assertEquals(27,layout.row(79,end).height());
        assertEquals(0,layout.row(0,end).height());
        assertEquals(0,layout.reveal(end,0));
        assertEquals(end,layout.clamp(Float.MAX_VALUE));
    }
    @Test void usesRoomBesideTriggerBeforeCoveringIt() {
        var top = UiPopupLayout.menu(new UiRect(20,20,100,28),150,100,400,400);
        assertTrue(top.frame().y() >= 48);
        var bottom = UiPopupLayout.menu(new UiRect(20,350,100,28),150,500,400,400);
        assertTrue(bottom.frame().bottom() <= 350);
        var negative = UiPopupLayout.menu(new UiRect(-100,-100,100,28),150,20,400,400);
        assertTrue(negative.frame().x() >= 0 && negative.frame().y() >= 0);
    }
    @Test void tinyWindowsDoNotProduceNegativeOrOffscreenFrames() {
        for(int size:new int[]{1,8,20,40}) {
            var layout=UiPopupLayout.menu(new UiRect(50,50,100,28),300,80,size,size);
            assertTrue(layout.frame().right()<=size && layout.frame().bottom()<=size);
            assertTrue(layout.viewport().height()>0);
        }
    }
    @Test void customRowsPaddingAndRevealShareGeometry() {
        var style=UiMenuStyle.builder().textSize(16).rowHeight(42).padding(20,9).build();
        var layout=UiPopupLayout.menu(new UiRect(20,20,100,28),220,80,400,400,style);
        assertEquals(42,layout.rowHeight());assertEquals(3360,layout.contentHeight());
        assertEquals(9,layout.viewport().y()-layout.frame().y());
        float end=layout.reveal(0,79);
        assertEquals(layout.maxOffset(),end);assertEquals(42,layout.row(79,end).height());
        assertEquals(0,layout.reveal(end,0));
        for(int size:new int[]{1,8,20,40}) {
            var small=UiPopupLayout.menu(new UiRect(50,50,100,28),300,80,size,size,style);
            assertTrue(small.frame().bottom()<=size);assertTrue(small.viewport().height()>0);
        }
    }
    @Test void stylesCompareByValueAndRejectInvalidMetrics() {
        assertEquals(UiMenuStyle.defaults(),UiMenuStyle.builder().build());
        assertThrows(IllegalArgumentException.class,()->UiMenuStyle.builder().rowHeight(0).build());
        assertThrows(IllegalArgumentException.class,()->UiMenuStyle.builder().textSize(Float.NaN).build());
        assertThrows(IllegalArgumentException.class,()->UiMenuStyle.builder().padding(-1,5).build());
    }
}
