package dev.pane.ui;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class UiControlCustomizationTest {
    @Test void overridesPaintAllEditorTypesWithoutChangingCaretGeometry() {
        for (int kind=0;kind<3;kind++) {
            var plain=renderField(kind,UiInputStyle.defaults(),false);
            var style=UiInputStyle.defaults().appearance(UiInputAppearance.defaults()
                .background(0xFFFFFFFF).borderWidth(0).focusWidth(0).radius(0).text(0xFF000000));
            var custom=renderField(kind,style,false);
            assertArrayEquals(plain.carets,custom.carets);
            assertEquals(0xFFFFFFFF,custom.image.getRGB(11,11));
            assertNotEquals(plain.image.getRGB(11,11),custom.image.getRGB(11,11));
            assertEquals(style.appearance(),style.padding(1,2).lineHeight(20).font("Dialog",12,0).appearance());
        }
    }
    @Test void zeroFocusWidthAndExplicitFocusColorWork() {
        var style=UiInputStyle.defaults().appearance(UiInputAppearance.defaults().background(0xFFFFFFFF)
            .radius(0).focus(0xFFFF0000).focusWidth(2));
        assertEquals(0xFFFF0000,renderField(0,style,true).image.getRGB(50,10));
        assertEquals(0xFFFFFFFF,renderField(0,style.appearance(style.appearance().focusWidth(0)),true).image.getRGB(50,10));
        assertThrows(IllegalArgumentException.class,()->UiInputAppearance.defaults().radius(Float.NaN));
        assertThrows(IllegalArgumentException.class,()->UiInputAppearance.defaults().borderWidth(-1));
    }
    @Test void paintedButtonOwnsEnabledHitsFocusAndLabelsIncludingTranslations() {
        var image=new BufferedImage(200,150,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {
            var hits=new ArrayList<UiHitRegion>();var tips=new ArrayList<UiTooltip>();var interactions=new UiInteractionState();
            var ui=context(new UiCanvas(g,image),hits,new ArrayList<>(),tips,true).withInteractions(interactions);
            ui.withTranslation(20,10,child -> child.paintedButton("field","Archive","archive",10,10,70,30,4,true,(c,s)->{
                assertTrue(s.focused());assertTrue(s.hovered());assertTrue(s.pressed());c.fill(0xFFFFFFFF,10,10,70,30);
            }));
            assertEquals(30,hits.getFirst().x());assertEquals("archive",hits.getFirst().action());
            assertEquals("Archive",tips.getFirst().text());assertEquals("Archive",interactions.buttons.get("field").label());
            ui.paintedButton("off","Disabled","off",100,10,70,30,4,false,(c,s)->{
                assertFalse(s.hovered());assertFalse(s.pressed());assertFalse(s.focused());c.fill(0xFFFFFFFF,100,10,70,30);
            });
            assertEquals(1,hits.size());assertFalse(interactions.targets.containsKey("off"));
            assertEquals(107,image.getRGB(110,20)>>>24,1);
            interactions.beginPaint();assertTrue(interactions.buttons.isEmpty());
        } finally {g.dispose();}
    }
    @Test void symbolsDrawAndRestoreTransformsAtDifferentSizes() {
        var image=new BufferedImage(220,160,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {
            var c=new UiCanvas(g,image);var transform=g.getTransform();
            for(var icon:UiIcon.values()) {
                Arrays.fill(((java.awt.image.DataBufferInt)image.getRaster().getDataBuffer()).getData(),0);icon.draw(c,0xFF000000,10,10,24,1.5F);
                assertTrue(Arrays.stream(((java.awt.image.DataBufferInt)image.getRaster().getDataBuffer()).getData()).anyMatch(p->p!=0),icon.name());
                assertEquals(transform,g.getTransform());
            }
            UiSymbol.vector(20,10,(canvas,color,stroke)->canvas.fill(color,0,0,20,10)).draw(c,0xFFFF0000,50,50,40,2);
            assertEquals(0xFFFF0000,image.getRGB(51,61));assertEquals(0,image.getRGB(51,51));
            assertEquals(transform,g.getTransform());
            assertThrows(IllegalArgumentException.class,()->UiSymbol.vector(0,10,(canvas,color,stroke)->{}));
        } finally {g.dispose();}
    }
    @Test void retainedRowsAndAffineGroupsPreserveButtonMetadata() {
        var image=new BufferedImage(200,150,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {
            var c=new UiCanvas(g,image);var interactions=new UiInteractionState();
            var ui=context(c,new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),false).withInteractions(interactions);
            ui.withTransform(UiTransform.identity().rotate(10),0,0,child ->
                child.paintedButton("rotate","Rotated action","rotate",20,20,80,30,4,true,(canvas,state)->{}));
            assertEquals("Rotated action",interactions.buttons.get("rotate").label());
            var request=new UiVirtualListRequest("rows",new UiRect(0,0,180,120),4,30,0,120,
                UiTheme.dark(),200,150,0xFF55AAFF,new UiRect(0,0,200,150),new HashMap<>(),1,0,0,
                (row,item)->row.paintedButton("row"+item.index(),"Row action","row",0,item.index()*30,100,30,4,true,(canvas,state)->{}));
            var output=new UiVirtualListRequest.RowOutput(new ArrayList<>(),new HashMap<>(),new HashMap<>(),
                new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
            request.paintRow(c,2,new UiVirtualListRequest.DynamicState(null,null,1,1,false,0,
                new UiTextInputState(null,0,0),null),output);
            assertEquals("Row action",output.buttons().get("row2").label());
            assertEquals("row2",output.hits().getFirst().id());
        } finally {g.dispose();}
    }
    private record Rendered(BufferedImage image,float[] carets) {}
    private Rendered renderField(int kind,UiInputStyle style,boolean focus) {
        var image=new BufferedImage(200,150,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {
            var inputs=new ArrayList<UiInputRegion>();var ui=context(new UiCanvas(g,image),new ArrayList<>(),inputs,new ArrayList<>(),focus);
            if(kind==0)ui.textField("field","value","",10,10,160,40,"abc",30,style);
            else if(kind==1)ui.passwordField("field","value","",10,10,160,40,"abc",30,style);
            else ui.textArea("field","value","",10,10,160,80,"abc",30,style);
            return new Rendered(image,inputs.getFirst().caretXs());
        } finally {g.dispose();}
    }
    private UiContext context(UiCanvas c,List<UiHitRegion> hits,List<UiInputRegion> inputs,List<UiTooltip> tips,boolean focus) {
        return new UiContext(c,UiTheme.dark(),200,150,0xFF55AAFF,new UiRect(0,0,200,150),"field","field",1,1,false,0,
            new HashMap<>(),hits,new LinkedHashMap<>(),new LinkedHashMap<>(),new HashMap<>(),
            new ArrayList<>(),inputs,tips,new UiTextInputState(focus?"field":null,0,0),new ArrayList<>(),new ArrayList<>(),null,1,null);
    }
}
