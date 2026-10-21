package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UiInteractionStateTest {
    @Test void nestedRevealHandlesSeveralFocusChangesBeforeNextPaint() {
        var state = new UiInteractionState();
        var outer = new UiScrollRegion("outer", new UiRect(100,10,200,100), 600);
        var inner = new UiScrollRegion("inner", new UiRect(110,210,180,80), 300);
        state.inScroll(outer, () -> {
            state.register(new UiHitRegion("first","first",110,15,60,20));
            state.inScroll(inner, () -> state.register(new UiHitRegion("last","last",120,450,60,20)));
        });
        assertEquals(java.util.Set.of("inner","outer"),state.reveal("last"));
        assertEquals(184, state.offsets.get("inner"));
        assertEquals(180, state.offsets.get("outer"));
        assertTrue(state.reveal("last").isEmpty());
        state.reveal("first");
        assertEquals(1, state.offsets.get("outer"));
        state.reveal("last");
        assertEquals(180, state.offsets.get("outer"));
    }
    @Test void deepInnerContentCanRevealWithinAShorterOuterContentExtent() {
        var state = new UiInteractionState();
        state.inScroll(new UiScrollRegion("outer",new UiRect(0,0,200,100),250), () ->
            state.inScroll(new UiScrollRegion("inner",new UiRect(0,150,100,60),1000), () ->
                state.register(new UiHitRegion("deep","deep",5,1050,50,20))));
        assertTrue(state.targets.containsKey("deep"));
        state.reveal("deep");
        assertEquals(864,state.offsets.get("inner"));
        assertEquals(110,state.offsets.get("outer"));
    }

    @Test void declaredExtentRejectsTargetsThatCannotBeRevealed() {
        var state = new UiInteractionState();
        state.inScroll(new UiScrollRegion("s",new UiRect(10,10,100,50),100), () -> {
            state.register(new UiHitRegion("outside","go",20,150,20,20));
            state.register(new UiHitRegion("inside","go",20,80,20,20));
        });
        assertFalse(state.targets.containsKey("outside"));
        assertTrue(state.targets.containsKey("inside"));
    }

    @Test void scrolledInnerContentAboveOuterViewportRemainsReachable() {
        var state = new UiInteractionState();
        state.offsets.put("outer", 10D);
        state.offsets.put("inner", 195D);
        state.inScroll(new UiScrollRegion("outer", new UiRect(300,28,260,220),700), () ->
            state.inScroll(new UiScrollRegion("inner", new UiRect(310,148,220,120),400), () ->
                state.register(new UiHitRegion("first","first",320,-42,120,30))));
        assertTrue(state.targets.containsKey("first"));
        state.reveal("first");
        assertEquals(1, state.offsets.get("inner"));
        assertEquals(10, state.offsets.get("outer"));
    }
}
