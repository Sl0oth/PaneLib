package dev.pane.ui;

import java.util.*;
import dev.pane.ui.internal.NativeOpacity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiHudNativeTest {
    @Test void placementIncludesScaleTranslationAndGuiScaleExactlyOnce() {
        var slots=Map.of("item",new NativeBounds(20,55,36,36));
        var mapped=UiHudNativePlacement.map(slots,new UiRect(100,200,870,228),580,152,2);
        assertEquals(new NativeBounds(260,565,108,108),mapped.get("item"));
        assertEquals(new NativeBounds(20,55,36,36),slots.get("item"));
        assertTrue(UiHudNativePlacement.map(Map.of(),new UiRect(0,0,1,1),1,1,1).isEmpty());
    }
    @Test void opacityIsScopedAndPremultipliedIncludingExceptions() {
        assertEquals(0x80808080,NativeOpacity.tint(0xFFFFFFFF,.5F));
        assertEquals(0x40201008,NativeOpacity.tint(0x80402010,.5F));
        assertEquals(1,NativeOpacity.current());
        NativeOpacity.with(.5F,()->{
            assertEquals(.5F,NativeOpacity.current());
            assertThrows(IllegalStateException.class,()->NativeOpacity.with(.5F,()->{
                assertEquals(.25F,NativeOpacity.current());throw new IllegalStateException();
            }));
            assertEquals(.5F,NativeOpacity.current());
        });
        assertEquals(1,NativeOpacity.current());
    }
    @Test void headingCrossesNorthInBothDirectionsAndRetargetsContinuously() {
        var states=new HashMap<String,UiAnimationClock.State>();
        assertEquals(359,new UiAnimationClock(states,0,1).angle("heading",359,100,UiEasing.LINEAR));
        assertEquals(359,new UiAnimationClock(states,0,1).angle("heading",1,100,UiEasing.LINEAR));
        assertEquals(0,new UiAnimationClock(states,50_000_000,1).angle("heading",1,100,UiEasing.LINEAR),.001);
        assertEquals(0,new UiAnimationClock(states,50_000_000,1).angle("heading",358,100,UiEasing.LINEAR),.001);
        assertEquals(359,new UiAnimationClock(states,100_000_000,1).angle("heading",358,100,UiEasing.LINEAR),.001);
        var settled=new UiAnimationClock(states,150_000_000,1);
        assertEquals(358,settled.angle("heading",358,100,UiEasing.LINEAR),.001);assertFalse(settled.active());
    }
    @Test void headingHandlesLargeAnglesReducedMotionAndSettles() {
        var states=new HashMap<String,UiAnimationClock.State>();
        assertEquals(1,new UiAnimationClock(states,0,1).angle("a",36001,90,UiEasing.EASE_OUT));
        var reduced=new UiAnimationClock(states,1,0);
        assertEquals(359,reduced.angle("a",-1,90,UiEasing.EASE_OUT));assertFalse(reduced.active());
        assertThrows(IllegalArgumentException.class,()->reduced.angle("a",Float.NaN,90,UiEasing.LINEAR));
    }
}
