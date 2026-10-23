package dev.pane.ui;

import java.awt.Font;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UiTextLayoutCacheTest {
    @Test void reusesOnlyMatchingGeometryAndKeepsOneBoundedEditor() {
        var image=new BufferedImage(200,80,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
        try {
            var canvas=new UiCanvas(graphics,image);var cache=new UiTextLayoutCache();
            var first=canvas.shapeText("abc",12,Font.PLAIN,UiCanvas.SANS,cache);
            assertSame(first,canvas.shapeText("abc",12,Font.PLAIN,UiCanvas.SANS,cache));
            graphics.translate(0,-100);
            assertSame(first,canvas.shapeText("abc",12,Font.PLAIN,UiCanvas.SANS,cache));
            graphics.translate(0,100);
            assertNotSame(first,canvas.shapeText("abc",18,Font.PLAIN,UiCanvas.SANS,cache));
            var lines=canvas.wrapShapedText("abc def",20,12,Font.PLAIN,UiCanvas.SANS,cache);
            assertSame(lines,canvas.wrapShapedText("abc def",20,12,Font.PLAIN,UiCanvas.SANS,cache));
            assertNotSame(lines,canvas.wrapShapedText("abc def",40,12,Font.PLAIN,UiCanvas.SANS,cache));
            assertEquals(7,cache.retainedCharacters());
            cache.get("oversized",65537,Object::new);assertEquals(0,cache.retainedCharacters());
            canvas.shapeText("abc",12,Font.PLAIN,UiCanvas.SANS,cache);cache.clear();assertEquals(0,cache.retainedCharacters());
        }finally{graphics.dispose();}
    }

    @Test void restingInputsKeepSeveralLayoutsWithinTheirBounds() {
        var cache=new UiTextLayoutCache(3);
        Object a=cache.get("a",10,Object::new),b=cache.get("b",10,Object::new),c=cache.get("c",10,Object::new);
        assertSame(a,cache.get("a",10,Object::new),"several layouts are kept at once");
        cache.get("d",10,Object::new);
        assertSame(a,cache.get("a",10,Object::new),"the most recently used layout stays");
        assertNotSame(b,cache.get("b",10,Object::new),"the least recently used layout goes first");
        assertEquals(30,cache.retainedCharacters());
        cache.get("long",65_000,Object::new);
        assertTrue(cache.retainedCharacters()<=65_536,"retained text stays within its bound");
        assertNotSame(c,cache.get("c",10,Object::new));
    }
}
