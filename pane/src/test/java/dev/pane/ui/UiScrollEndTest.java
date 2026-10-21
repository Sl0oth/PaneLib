package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiScrollEndTest {
    @Test void explicitIntentUsesNewExtentAndIsConsumedOnce() {
        var end=new UiScrollEndState();
        assertEquals(0,end.resolve("chat",0,100,UiScrollFollow.NONE));
        end.request("chat");end.request("chat");
        assertEquals(300,end.resolve("chat",0,300,UiScrollFollow.NONE));
        assertEquals(Set.of("chat"),end.takeResolved());
        assertEquals(300,end.resolve("chat",300,450,UiScrollFollow.NONE));
        assertTrue(end.takeResolved().isEmpty());
    }
    @Test void followsGrowthAndResizeOnlyWhenPreviouslyAtEnd() {
        var end=new UiScrollEndState();
        assertEquals(0,end.resolve("chat",0,100,UiScrollFollow.WHEN_AT_END));
        assertEquals(200,end.resolve("chat",100,200,UiScrollFollow.WHEN_AT_END));
        assertEquals(250,end.resolve("chat",200,250,UiScrollFollow.WHEN_AT_END));
        assertEquals(60,end.resolve("chat",60,400,UiScrollFollow.WHEN_AT_END));
        assertEquals(20,end.resolve("chat",60,20,UiScrollFollow.WHEN_AT_END));
        assertEquals(80,end.resolve("chat",20,80,UiScrollFollow.WHEN_AT_END));
    }
    @Test void wheelAwayCancelsPendingAndDoesNotReattachBeforeMotionMoves() {
        var end=new UiScrollEndState();end.resolve("chat",100,100,UiScrollFollow.WHEN_AT_END);
        end.request("chat");end.navigate("chat",50);
        assertEquals(100,end.resolve("chat",100,200,UiScrollFollow.WHEN_AT_END));
        assertEquals(75,end.resolve("chat",75,250,UiScrollFollow.WHEN_AT_END));
        assertTrue(end.takeResolved().isEmpty());
        end.navigate("chat",250);
        assertEquals(300,end.resolve("chat",100,300,UiScrollFollow.WHEN_AT_END));
        assertEquals(Set.of("chat"),end.takeResolved());
    }
    @Test void filteringCancelsAnOutOfRangeMotionTarget() {
        var end=new UiScrollEndState();end.resolve("chat",0,500,UiScrollFollow.WHEN_AT_END);
        end.navigate("chat",300);
        assertEquals(40,end.resolve("chat",90,40,UiScrollFollow.WHEN_AT_END));
        assertEquals(Set.of("chat"),end.takeResolved());
        assertEquals(40,end.resolve("chat",40,40,UiScrollFollow.WHEN_AT_END));
        assertTrue(end.takeResolved().isEmpty());
    }
    @Test void absentViewportAndConversationIdsAreIndependentAndBoundaryCancelsIntent() {
        var end=new UiScrollEndState();end.request("chat:A");
        assertEquals(0,end.resolve("chat:B",0,100,UiScrollFollow.WHEN_AT_END));
        assertEquals(800,end.resolve("chat:A",0,800,UiScrollFollow.NONE));
        end.request("chat:B");end.cancelPending();
        assertEquals(0,end.resolve("chat:B",0,200,UiScrollFollow.NONE));
        end.request("chat:A");end.navigate("chat:A",12);
        assertEquals(12,end.resolve("chat:A",12,900,UiScrollFollow.NONE));
    }
    @Test void emptyThenPopulatedFollowAndSettledLayoutsDoNotRepeatedlyResolve() {
        var end=new UiScrollEndState();end.request("rows");
        assertEquals(0,end.resolve("rows",0,0,UiScrollFollow.WHEN_AT_END));end.takeResolved();
        assertEquals(9900,end.resolve("rows",0,9900,UiScrollFollow.WHEN_AT_END));end.takeResolved();
        for(int i=0;i<100;i++)assertEquals(9900,end.resolve("rows",9900,9900,UiScrollFollow.WHEN_AT_END));
        assertTrue(end.takeResolved().isEmpty());
    }
    @Test void resolvesBeforePaintingHitRegistrationAndNativeSlotPlacement() {
        var image=new BufferedImage(200,140,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {
            var state=new UiInteractionState();state.scrollEnds.request("chat");
            var hits=new ArrayList<UiHitRegion>();var nativeBounds=new HashMap<String,NativeBounds>();
            var context=context(image,g,state,hits,nativeBounds);
            context.scroll("chat",new UiRect(10,10,100,80),300,sc->{
                sc.canvas().fill(0xFFFF8800,20,280,30,20);
                sc.hit("last","last",20,280,30,20);sc.nativeSlot("last-item",20,280,18,18);
            });
            assertEquals(220,state.offsets.get("chat"));
            assertEquals(0xFFFF8800,image.getRGB(25,65));
            assertEquals(60,hits.getFirst().y());
            assertEquals(60,nativeBounds.get("last-item").y());
        } finally {g.dispose();}
    }
    @Test void virtualFallbackUsesNewEndForVisibleWindowIncludingEmptyTransition() {
        var image=new BufferedImage(200,140,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {
            var state=new UiInteractionState();var context=context(image,g,state,new ArrayList<>(),new HashMap<>());
            var painted=new ArrayList<Integer>();state.scrollEnds.request("rows");
            context.virtualList("rows",new UiRect(0,0,100,80),0,20,0,UiScrollFollow.WHEN_AT_END,(row,item)->fail());
            assertEquals(0,state.offsets.get("rows"));
            context.virtualList("rows",new UiRect(0,0,100,80),1000,20,0,UiScrollFollow.WHEN_AT_END,(row,item)->painted.add(item.index()));
            assertEquals(19920,state.offsets.get("rows"));assertTrue(painted.contains(999));assertTrue(painted.size()<=6);
        } finally {g.dispose();}
    }
    private UiContext context(BufferedImage image,java.awt.Graphics2D g,UiInteractionState state,
                              List<UiHitRegion> hits,Map<String,NativeBounds> bounds) {
        return new UiContext(new UiCanvas(g,image),UiTheme.dark(),200,140,0xFF55AAFF,
            new UiRect(0,0,200,140),null,null,1,1,false,0,new HashMap<>(),hits,bounds,
            new HashMap<>(),new HashMap<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),
            new UiTextInputState(null,0,0),new ArrayList<>(),new ArrayList<>(),null,1,null).withInteractions(state);
    }
}
