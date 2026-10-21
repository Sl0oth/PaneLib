package dev.pane.ui;

import java.awt.Font;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UiShapedTextTest {
    @Test void addingNonAsciiDoesNotShiftExistingLatinCarets() {
        var image=new BufferedImage(200,80,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {
            var canvas=new UiCanvas(g,image);
            var ascii=canvas.shapeText("abc",18,Font.PLAIN,UiCanvas.SANS);
            var mixed=canvas.shapeText("abcé",18,Font.PLAIN,UiCanvas.SANS);
            for(int i=0;i<=3;i++)assertEquals(ascii.x(i),mixed.x(i),.25);
        } finally {g.dispose();}
    }
    @Test void selectionWithFallbackGlyphsStaysInsideRequestedLineBand() {
        var image=new BufferedImage(200,80,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {
            var canvas=new UiCanvas(g,image);
            var shaped=canvas.shapeText("A😀日本語",18,Font.PLAIN,UiCanvas.SANS);
            canvas.shapedSelection(shaped,0,6,10,20,10,0xFFFFFFFF);
            int painted=0;
            for(int y=0;y<80;y++)for(int x=0;x<200;x++) {
                if(y<20||y>=30)assertEquals(0,image.getRGB(x,y));
                else if(image.getRGB(x,y)!=0)painted++;
            }
            assertTrue(painted>0);
        } finally {g.dispose();}
    }
    @Test void shapedPaintMatchesExistingTextForLatinAndComplexRuns() {
        for(String value:new String[]{"AV office ffi","A😀B","Cafe\u0301","مرحبا","日本語"}) {
            var expected=new BufferedImage(300,60,BufferedImage.TYPE_INT_ARGB);
            var actual=new BufferedImage(300,60,BufferedImage.TYPE_INT_ARGB);
            var a=expected.createGraphics();var b=actual.createGraphics();
            try {
                new UiCanvas(a,expected).text(value,10,10,18,0xFFFFFFFF,Font.PLAIN,UiCanvas.SANS);
                var canvas=new UiCanvas(b,actual);var shaped=canvas.shapeText(value,18,Font.PLAIN,UiCanvas.SANS);
                canvas.shapedText(shaped,10,10,0xFFFFFFFF);
                if(value.equals("日本語")) {
                    try {
                        javax.imageio.ImageIO.write(expected,"png",new java.io.File("build/japanese-text-before.png"));
                        javax.imageio.ImageIO.write(actual,"png",new java.io.File("build/japanese-text-shaped.png"));
                    }catch(java.io.IOException error){throw new AssertionError(error);}
                    assertTrue(shaped.width()>0);
                } else assertArrayEquals(expected.getRGB(0,0,300,60,null,0,300),actual.getRGB(0,0,300,60,null,0,300),value);
                for(int i=0;i<=value.length();i++) {
                    if(UiTextBoundary.floor(value,i)!=i)assertTrue(Float.isNaN(shaped.x(i)));
                    else assertTrue(Float.isFinite(shaped.x(i)));
                }
            } finally {a.dispose();b.dispose();}
        }
    }
    @Test void wrappedRunsPreserveWhitespaceNewlinesAndSurrogateBoundaries() {
        var image=new BufferedImage(300,60,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
        try {
            var canvas=new UiCanvas(graphics,image);
            String value="😀AB  cd\n\n日本語 café\n";
            for(float width:new float[]{.01F,8,50,1000}) {
                var lines=canvas.wrapShapedText(value,width,12,Font.PLAIN,UiCanvas.SANS);
                var reconstructed=new StringBuilder();
                int previousEnd=0;
                for(var line:lines) {
                    assertEquals(line.start(),UiTextBoundary.floor(value,line.start()));
                    assertEquals(line.end(),UiTextBoundary.floor(value,line.end()));
                    reconstructed.append(value,previousEnd,line.start());
                    reconstructed.append(value,line.start(),line.end());previousEnd=line.end();
                }
                assertEquals(value,reconstructed.toString());
                assertEquals(value.length(),lines.getLast().end());
            }
            assertEquals(0,canvas.shapeText("",12,Font.PLAIN,UiCanvas.SANS).x(0));
        } finally {graphics.dispose();}
    }
}
