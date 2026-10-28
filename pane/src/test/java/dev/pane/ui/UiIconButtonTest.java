package dev.pane.ui;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiIconButtonTest {
    private static final List<UiMenuItem> ITEMS=List.of(new UiMenuItem("go","Open","go"));
    private static final UiButtonStyle STYLE=UiButtonStyle.builder().background(0xFF202020).border(0xFF202020).borderWidth(0)
        .radius(0).text(0xFFFFFFFF).textSize(12).fontStyle(Font.PLAIN).build();
    private static final class Paint implements AutoCloseable {
        final BufferedImage image=new BufferedImage(320,200,BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g=image.createGraphics();
        final UiCanvas canvas=new UiCanvas(g,image);
        final UiInteractionState interactions=new UiInteractionState();
        final List<UiHitRegion> hits=new ArrayList<>();
        final List<UiMenu> menus=new ArrayList<>();
        final List<UiTooltip> tips=new ArrayList<>();
        final Map<String,Object> values=new HashMap<>();
        UiContext ui() {
            return new UiContext(canvas,UiTheme.dark(),320,200,0xFFFFAA00,new UiRect(0,0,320,200),null,null,
                1,1,false,0,values,hits,new HashMap<>(),new HashMap<>(),new HashMap<>(),
                new ArrayList<>(),new ArrayList<>(),tips,new UiTextInputState(null,0,0),menus,new ArrayList<>(),null,1,null)
                .withInteractions(interactions);
        }
        /** True when any pixel in the rectangle differs from the button's own fill. */
        boolean inked(int x0,int y0,int x1,int y1){
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){int p=image.getRGB(x,y);if((p>>>24)>0&&p!=0xFF202020)return true;}
            return false;
        }
        public void close(){g.dispose();}
    }

    @Test void iconButtonDrawsIconBeforeItsLabelAndActsLikeAButton() {
        try(var p=new Paint()) {
            var ui=p.ui();
            float w=ui.buttonWidth("Run",UiIcon.PLAY,STYLE);
            assertTrue(w>ui.buttonWidth("Run",null,STYLE),"the icon adds width");
            ui.button("run","Run",UiIcon.PLAY,"run",10,10,w,30,STYLE,true);
            assertEquals("run",p.hits.getFirst().action());
            float iconLeft=10+(w-(w-2*Math.max(8,12)))/2; // content starts at the side padding
            assertTrue(p.inked((int)iconLeft,14,(int)iconLeft+12,36),"icon painted at the start of the content");
            assertTrue(p.inked((int)(10+w/2),14,(int)(10+w-8),36),"label painted after the icon");
            assertFalse(p.inked((int)(10+w)+1,10,319,40),"nothing painted past the button");
        }
    }

    @Test void narrowIconButtonShortensItsLabelInsideTheButton() {
        try(var p=new Paint()) {
            p.ui().button("long","A very long label that cannot fit",UiIcon.FILE,"go",10,10,90,30,STYLE,true);
            assertFalse(p.inked(101,10,319,40),"label is shortened, not painted past the edge");
        }
    }

    @Test void disabledIconButtonRegistersNoInput() {
        try(var p=new Paint()) {
            p.ui().button("off","Off",UiIcon.STOP,"off",10,10,80,30,STYLE,false);
            assertTrue(p.hits.isEmpty());
        }
    }

    @Test void menuTriggerAddsAnArrowAndOpensItsMenu() {
        try(var p=new Paint()) {
            var ui=p.ui();
            float w=ui.menuButtonWidth("More",null,STYLE);
            assertTrue(w>ui.buttonWidth("More",null,STYLE),"room for the arrow");
            ui.menuButton("more","More",null,10,10,w,30,STYLE,true,ITEMS);
            assertEquals(UiContext.internalAction("menu:more"),p.hits.getFirst().action());
            assertEquals(new UiRect(10,10,w,30),p.menus.getFirst().anchor());
            assertEquals("More",p.interactions.buttons.get("more").label());
            float arrowLeft=10+w-Math.max(8,12)-Math.round(12*.8F);
            assertTrue(p.inked((int)arrowLeft,14,(int)(10+w-6),36),"arrow painted at the trailing edge");
        }
    }

    @Test void emptyMenuTriggerIsDisabled() {
        try(var p=new Paint()) {
            p.ui().menuButton("none","New",UiIcon.PLUS,10,10,80,30,STYLE,true,List.of());
            assertTrue(p.hits.isEmpty()&&p.menus.isEmpty());
            assertFalse(p.interactions.buttons.get("none").enabled());
        }
    }

    @Test void iconOnlyButtonNeedsALabelAndShowsItAsATooltip() {
        try(var p=new Paint()) {
            var ui=p.ui();
            assertThrows(IllegalArgumentException.class,()->ui.iconButton("x"," ",UiIcon.REFRESH,"r",10,10,30,STYLE,true));
            ui.iconButton("refresh","Reload this folder",UiIcon.REFRESH,"refresh",10,10,30,STYLE,true);
            assertEquals("refresh",p.hits.getFirst().action());
            assertTrue(p.tips.stream().anyMatch(t->t.id().equals("refresh")&&t.text().equals("Reload this folder")));
            assertTrue(p.inked(17,17,33,33),"icon centred in the square");
        }
    }

    @Test void everyIconStaysInsideItsSquare() {
        for(UiIcon icon:UiIcon.values()) {
            var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
            var g=image.createGraphics();
            try {
                icon.draw(new UiCanvas(g,image),0xFFFFFFFF,16,16,32,2);
                boolean drawn=false;
                for(int y=0;y<64;y++)for(int x=0;x<64;x++){
                    if((image.getRGB(x,y)>>>24)==0)continue;
                    drawn=true;
                    assertTrue(x>=14&&x<50&&y>=14&&y<50,icon+" painted outside its square at "+x+","+y);
                }
                assertTrue(drawn,icon+" draws something");
            } finally { g.dispose(); }
        }
    }

    @Test void scrollAtEndTracksTheLastResolvedExtent() {
        var end=new UiScrollEndState();
        assertTrue(end.atEnd("log",0),"nothing resolved yet");
        end.resolve("log",0,200,UiScrollFollow.WHEN_AT_END);
        assertFalse(end.atEnd("log",120));
        assertTrue(end.atEnd("log",200));
        assertTrue(end.atEnd("log",199.6),"half-pixel tolerance");
    }
}
