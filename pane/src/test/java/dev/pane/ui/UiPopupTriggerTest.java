package dev.pane.ui;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiPopupTriggerTest {
    private static final List<UiMenuItem> ITEMS=List.of(new UiMenuItem("go","Open","go"));
    private static final UiDialog DIALOG=new UiDialog("Launch?","A local example.","Yes","yes","No","no");
    private static final List<UiSelectOption> OPTIONS=List.of(new UiSelectOption("a","Alpha"),new UiSelectOption("b","Beta"));
    private static final class Paint implements AutoCloseable {
        final BufferedImage image=new BufferedImage(300,200,BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g=image.createGraphics();
        final UiCanvas canvas=new UiCanvas(g,image);
        final UiInteractionState interactions=new UiInteractionState();
        final List<UiHitRegion> hits=new ArrayList<>();
        final List<UiMenu> menus=new ArrayList<>();
        final List<UiDialogRequest> dialogs=new ArrayList<>();
        final Map<String,Object> values=new HashMap<>();
        UiContext ui(String hover,String press,String focus) {
            return new UiContext(canvas,UiTheme.dark(),300,200,0xFFFFAA00,new UiRect(0,0,300,200),hover,press,
                1,1,false,0,values,hits,new HashMap<>(),new HashMap<>(),new HashMap<>(),new ArrayList<>(),
                new ArrayList<>(),new ArrayList<>(),new UiTextInputState(focus,0,0),menus,dialogs,null,1,null)
                .withInteractions(interactions);
        }
        public void close(){g.dispose();}
    }
    @Test void customMenuSharesStateSemanticLabelAndInternalActivation() {
        try(var p=new Paint()) {
            p.ui("menu","menu","menu").paintedMenuButton("menu","Server options",20,20,100,30,0,true,ITEMS,
                UiMenuStyle.defaults(),(g,state)->{
                    assertTrue(state.hovered()&&state.pressed()&&state.focused());assertEquals("Server options",state.label());
                    g.fill(0xFF778899,20,20,100,30);
                });
            assertEquals(UiContext.internalAction("menu:menu"),p.hits.getFirst().action());
            assertEquals("Server options",p.interactions.buttons.get("menu").label());
            assertEquals(0xFF778899,p.image.getRGB(25,25));
            assertNotEquals(0,p.image.getRGB(50,18),"automatic focus ring drawn after custom painter");
            assertEquals(new UiRect(20,20,100,30),p.menus.getFirst().anchor());
        }
    }
    @Test void disabledAndEmptyMenusPaintButRegisterNoPopupOrInput() {
        try(var p=new Paint()) {
            var ui=p.ui("off","off","off");
            ui.paintedMenuButton("off","Disabled",20,20,100,30,0,false,ITEMS,UiMenuStyle.defaults(),(g,state)->{
                assertFalse(state.enabled()||state.hovered()||state.pressed()||state.focused());g.fill(0xFFFFFFFF,20,20,100,30);
            });
            ui.menuButton("empty","Empty",20,70,100,30,UiButtonStyle.builder().build(),true,List.of(),UiMenuStyle.defaults());
            assertTrue(p.hits.isEmpty()&&p.menus.isEmpty());
            assertFalse(p.interactions.buttons.get("empty").enabled());
            assertEquals(107,p.image.getRGB(25,25)>>>24,1,"disabled alpha applied once");
        }
    }
    @Test void dialogStylesAndCustomPainterUseSameNativeRequest() {
        try(var p=new Paint()) {
            var ui=p.ui(null,null,null);
            ui.dialogButton("styled","Launch",10,10,100,30,UiButtonStyle.builder().background(0xFF225577).borderWidth(0).build(),DIALOG);
            ui.paintedDialogButton("custom","Launch with crew",10,60,100,30,0,true,DIALOG,(g,state)->{});
            ui.paintedDialogButton("off","Unavailable",10,110,100,30,0,false,DIALOG,(g,state)->{});
            assertEquals(2,p.dialogs.size());assertSame(DIALOG,p.dialogs.getFirst().dialog());
            assertEquals(2,p.hits.size());assertEquals(UiContext.internalAction("dialog:custom"),p.hits.get(1).action());
            assertEquals(0xFF225577,p.image.getRGB(15,15));
        }
    }
    @Test void styledSelectorKeepsDisplayLabelBindingAndPopupStyleSeparate() {
        try(var p=new Paint()) {
            var popup=UiMenuStyle.builder().textSize(22).rowHeight(40).build();
            String value=p.ui(null,null,null).dropdown("role","binding:key",10,10,130,36,"b",OPTIONS,
                UiButtonStyle.builder().fontFamily(Font.MONOSPACED).textSize(16).background(0xFF665522).borderWidth(0).build(),popup);
            assertEquals("b",value);assertEquals("Beta",p.interactions.buttons.get("role").label());
            assertEquals("b",p.values.get("binding:key"));assertEquals("option-1",p.menus.getFirst().selectedItemId());
            assertSame(popup,p.menus.getFirst().style());assertEquals(0xFF665522,p.image.getRGB(15,15));
            assertTrue(p.menus.getFirst().items().get(1).action().startsWith(UiContext.internalAction("dropdown:")));
        }
    }
    @Test void selectorTypographyUsesSuppliedFontAndCannotPaintPastTrigger() {
        try(var p=new Paint();var expected=new Paint()) {
            var style=UiButtonStyle.builder().background(0).borderWidth(0).radius(0).text(0xFFFFFFFF)
                .fontFamily(Font.MONOSPACED).fontStyle(Font.BOLD).textSize(19).build();
            var label="An extremely long selected label";
            p.ui(null,null,null).dropdown("long","key",20,20,100,38,label,List.of(new UiSelectOption(label,label)),style,UiMenuStyle.defaults());
            String clipped=expected.canvas.ellipsize(label,66,19,Font.BOLD,Font.MONOSPACED);
            expected.canvas.withClip(30,20,66,38,()->expected.canvas.text(clipped,30,20+(38-expected.canvas.textHeight(19,Font.BOLD,Font.MONOSPACED))/2,
                19,0xFFFFFFFF,Font.BOLD,Font.MONOSPACED));
            for(int y=0;y<200;y++)for(int x=0;x<300;x++) {
                if(x<100)assertEquals(expected.image.getRGB(x,y),p.image.getRGB(x,y),"text pixel "+x+","+y);
                if(x>=120||y<20||y>=58)assertEquals(0,p.image.getRGB(x,y),"outside trigger");
            }
        }
    }
    @Test void paintedSelectorReceivesCurrentDisplayLabelAndDisabledBindingRemainsIntact() {
        try(var p=new Paint()) {
            p.values.put("key","b");
            assertEquals("b",p.ui(null,null,null).paintedDropdown("select","key",10,10,120,30,"a",OPTIONS,0,false,
                UiMenuStyle.defaults(),(g,state)->{assertEquals("Beta",state.label());assertFalse(state.enabled());}));
            assertTrue(p.hits.isEmpty()&&p.menus.isEmpty());
            assertEquals("b",p.values.get("key"));
        }
    }
    @Test void transformedAndClippedTriggersShareAnchorsAndHits() {
        try(var p=new Paint()) {
            var ui=p.ui(null,null,null);p.interactions.offsets.put("scroll",20D);
            ui.withTranslation(10,8,moved->moved.scroll("scroll",new UiRect(20,20,100,60),150,row->
                row.paintedMenuButton("scrolled","Options",20,30,90,30,0,true,ITEMS,UiMenuStyle.defaults(),(g,state)->{})));
            var anchor=p.menus.getFirst().anchor();var hit=p.hits.getFirst();
            assertEquals(new UiRect(30,28,90,20),anchor);
            assertEquals(anchor,new UiRect(hit.x(),hit.y(),hit.width(),hit.height()));
        }
    }
    @Test void customTriggersRejectBlankLabelsAndReservedIds() {
        try(var p=new Paint()) {
            var ui=p.ui(null,null,null);
            assertThrows(IllegalArgumentException.class,()->ui.paintedMenuButton("m","",0,0,50,20,0,true,ITEMS,UiMenuStyle.defaults(),(g,state)->{}));
            assertThrows(IllegalArgumentException.class,()->ui.dialogButton("dialog-confirm","Bad id",0,0,50,20,UiButtonStyle.builder().build(),DIALOG));
            assertThrows(IllegalArgumentException.class,()->ui.paintedDialogButton("ok","Label",0,0,50,20,Float.NaN,true,DIALOG,(g,state)->{}));
            assertTrue(p.hits.isEmpty()&&p.menus.isEmpty()&&p.dialogs.isEmpty());
        }
    }
}
