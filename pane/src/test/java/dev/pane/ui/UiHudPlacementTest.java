package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class UiHudPlacementTest {
    @Test void edgeCentersUseScaledSizeAndIgnoreCenteredAxisMargin() {
        for(float scale:new float[]{.5F,1,1.5F,2})for(float unit:new float[]{.5F,1,2,3}) {
            float w=900/unit,h=600/unit;
            var top=new UiHudPlacement(UiHudPlacement.Anchor.TOP_CENTER,99,20,scale).bounds(w,h,200,80,unit);
            var bottom=new UiHudPlacement(UiHudPlacement.Anchor.BOTTOM_CENTER,99,20,scale).bounds(w,h,200,80,unit);
            var left=new UiHudPlacement(UiHudPlacement.Anchor.CENTER_LEFT,30,99,scale).bounds(w,h,200,80,unit);
            var right=new UiHudPlacement(UiHudPlacement.Anchor.CENTER_RIGHT,30,99,scale).bounds(w,h,200,80,unit);
            assertEquals(w/2,top.centerX(),.001);assertEquals(w/2,bottom.centerX(),.001);
            assertEquals(20/unit,top.y(),.001);assertEquals(h-20/unit,bottom.bottom(),.001);
            assertEquals(h/2,left.centerY(),.001);assertEquals(h/2,right.centerY(),.001);
            assertEquals(30/unit,left.x(),.001);assertEquals(w-30/unit,right.right(),.001);
        }
    }
    @Test void allAnchorsStayBoundedWhenViewportIsSmallerThanContent() {
        for(var anchor:UiHudPlacement.Anchor.values())for(float scale:new float[]{.5F,1.5F,2}) {
            var b=new UiHudPlacement(anchor,1000,1000,scale).bounds(90,40,240,100,1);
            assertTrue(b.x()>=0&&b.y()>=0&&b.right()<=90.001&&b.bottom()<=40.001);
            if(anchor==UiHudPlacement.Anchor.TOP_CENTER||anchor==UiHudPlacement.Anchor.BOTTOM_CENTER)
                assertEquals(45,b.centerX(),.001);
            if(anchor==UiHudPlacement.Anchor.CENTER_LEFT||anchor==UiHudPlacement.Anchor.CENTER_RIGHT)
                assertEquals(20,b.centerY(),.001);
        }
    }
    @Test void responsivePolicyCachesAndFixedPlacementRemovesIt() {
        var r=new UiHudPlacementResolver();int[] calls={0};
        UiHudPlacementPolicy p=v->{calls[0]++;return new UiHudPlacement(UiHudPlacement.Anchor.BOTTOM_CENTER,0,12,v.width()<700?.5F:1);};
        r.setPolicy(p);assertEquals(0,calls[0]);
        assertEquals(1,r.resolve(900,600).scale());
        for(int i=0;i<10000;i++)r.resolve(900,600);
        assertEquals(1,calls[0]);
        assertEquals(.5F,r.resolve(640,440).scale());assertEquals(2,calls[0]);
        r.resolve(640,500);assertEquals(3,calls[0]);
        r.setPolicy(p);r.resolve(640,500);assertEquals(4,calls[0]);
        var fixed=UiHudPlacement.topLeft();r.setPlacement(fixed);
        assertSame(fixed,r.resolve(1000,800));assertEquals(4,calls[0]);
        r.setPolicy(p);r.resolve(1000,800);assertEquals(5,calls[0]);
    }
    @Test void invalidPolicyResultsNeverReplaceLastValidPlacement() {
        var r=new UiHudPlacementResolver();var original=r.placement();
        assertThrows(NullPointerException.class,()->r.setPolicy(null));
        assertThrows(NullPointerException.class,()->r.setPlacement(null));
        r.setPolicy(v->null);assertThrows(NullPointerException.class,()->r.resolve(800,600));
        assertSame(original,r.placement());
        r.setPolicy(v->original);
        for(float invalid:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->r.resolve(invalid,600));
        assertSame(original,r.resolve(800,600));
    }
    @Test void anchorsFitAndKeepPhysicalPointSizeAcrossGuiScales() {
        var placement = new UiHudPlacement(UiHudPlacement.Anchor.BOTTOM_RIGHT, 18, 22, 1);
        UiRect first = placement.bounds(800, 600, 240, 100, 1);
        assertEquals(new UiRect(542, 478, 240, 100), first);
        UiRect scaled = placement.bounds(400, 300, 240, 100, 2);
        assertEquals(first.width(), scaled.width() * 2);
        assertEquals(first.x(), scaled.x() * 2);
        UiRect tiny = placement.bounds(90, 40, 240, 100, 1);
        assertTrue(tiny.x() >= 0 && tiny.y() >= 0 && tiny.right() <= 90 && tiny.bottom() <= 40);
        assertThrows(IllegalArgumentException.class, () -> new UiHudPlacement(null, Float.NaN, 0, 1));
    }
}
