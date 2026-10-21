package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;

final class UiInputStyleTest {
    @Test void selectedBlankLineHasVisibleHighlight() {
        var image=new BufferedImage(420,250,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {
            var ui=context(new UiCanvas(g,image),new ArrayList<>(),new UiTextInputState("area",3,0));
            ui.textArea("area","key","",10,10,200,100,"A\n\nB",100);
            assertNotEquals(image.getRGB(22,32),image.getRGB(20,32));
        } finally {g.dispose();}
    }
    @Test void maskedEmojiPointerGeometryUsesOriginalCodepointBoundaries() {
        var image=new BufferedImage(420,250,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {
            var inputs=new ArrayList<UiInputRegion>();
            var ui=context(new UiCanvas(g,image),inputs,new UiTextInputState("field",4,4));
            ui.passwordField("field","key","",10,10,300,42,"A😀B",50,UiInputStyle.defaults());
            var input=inputs.getFirst();
            assertTrue(Float.isNaN(input.caretXs()[2]));
            for(int i:new int[]{0,1,3,4})assertEquals(i,input.caretAt(input.caretXs()[i],30));
        } finally {g.dispose();}
    }
    @Test void styledFieldUsesIdenticalFontMetricsForPaintAndPointerCarets() {
        var image = new BufferedImage(420,250,BufferedImage.TYPE_INT_ARGB); var g=image.createGraphics();
        try {
            var inputs=new ArrayList<UiInputRegion>(); var canvas=new UiCanvas(g,image);
            var ui=context(canvas,inputs,new UiTextInputState("field",4,4));
            var style=UiInputStyle.defaults().font(UiCanvas.MONO,18,Font.BOLD).padding(14,5);
            ui.textField("field","key","",10,10,300,42,"A😀B",50,style);
            var input=inputs.getFirst();float[] x=input.caretXs();
            assertEquals(24,x[0],.01);assertTrue(Float.isNaN(x[2]));
            var expected=new java.awt.font.TextLayout("A😀B",new Font(UiCanvas.MONO,Font.BOLD,18),g.getFontRenderContext());
            assertEquals(24+expected.getCaretInfo(java.awt.font.TextHitInfo.leading(3))[0],x[3],.01);
            assertEquals(3,input.caretAt(x[3],30));
        } finally {g.dispose();}
    }
    @Test void styledMultilineWrapsAtCodepointsAndScrollsCaretIntoView() {
        var image = new BufferedImage(420,250,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {
            var inputs=new ArrayList<UiInputRegion>();String v="One 😀 two three\nfour\nfive\nsix\nseven";
            var ui=context(new UiCanvas(g,image),inputs,new UiTextInputState("area",v.length(),v.length()));
            ui.textArea("area","key","",10,10,150,70,v,100,UiInputStyle.defaults().font(UiCanvas.MONO,16,Font.PLAIN).lineHeight(25));
            var input=inputs.getFirst(); assertTrue(Float.isNaN(input.caretXs()[5]));
            assertTrue(input.caretYs()[v.length()]<=74);
            assertEquals(v.length(), input.caretAt(input.caretXs()[v.length()],input.caretYs()[v.length()]));
        } finally {g.dispose();}
    }
    @Test void excessivePaddingCannotPaintPlaceholderOutsideNarrowControl() {
        var image=new BufferedImage(100,60,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {
            var ui=context(new UiCanvas(g,image),new ArrayList<>(),new UiTextInputState(null,0,0));
            ui.textField("tiny","tiny","WWWW",10,10,8,24,"",50,
                UiInputStyle.defaults().font(UiCanvas.MONO,16,Font.BOLD).padding(20,0));
            for(int y=0;y<60;y++) for(int x=24;x<100;x++) assertEquals(0,image.getRGB(x,y));
        } finally {g.dispose();}
    }
    @Test void rejectsInvalidMetricsAndReportsQualityCaps() {
        assertThrows(IllegalArgumentException.class,()->new UiInputStyle(Float.NaN,0,null,1,1,14));
        assertThrows(IllegalArgumentException.class,()->UiInputStyle.defaults().padding(-1,2));
        assertTrue(new UiRasterQuality(100,100,150,150,2,1.5F).upscaled());
        assertFalse(new UiRasterQuality(100,100,200,200,2,2).upscaled());
        assertEquals(4,UiRenderSettings.typography().maximumScale());
    }
    private UiContext context(UiCanvas c,List<UiInputRegion> inputs,UiTextInputState text) {
        return new UiContext(c,UiTheme.dark(),420,250,0xFF55AAFF,new UiRect(0,0,420,250),null,null,1,1,false,0,
            new HashMap<>(),new ArrayList<>(),new LinkedHashMap<>(),new LinkedHashMap<>(),new HashMap<>(),
            new ArrayList<>(),inputs,new ArrayList<>(),text,new ArrayList<>(),new ArrayList<>(),null,1,null);
    }
}
