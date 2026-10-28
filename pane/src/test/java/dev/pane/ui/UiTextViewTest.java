package dev.pane.ui;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiTextViewTest {
    private static final UiInputStyle MONO=UiInputStyle.defaults().font(Font.MONOSPACED,12,Font.PLAIN).lineHeight(16);
    private static final class Paint implements AutoCloseable {
        final BufferedImage image=new BufferedImage(320,240,BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g=image.createGraphics();
        final UiCanvas canvas=new UiCanvas(g,image);
        final UiInteractionState interactions=new UiInteractionState();
        final List<UiInputRegion> inputs=new ArrayList<>();
        final Map<String,Object> values=new HashMap<>();
        UiContext ui() {
            return new UiContext(canvas,UiTheme.dark(),320,240,0xFFFFAA00,new UiRect(0,0,320,240),null,null,
                1,1,false,0,values,new ArrayList<>(),new HashMap<>(),new HashMap<>(),new HashMap<>(),
                new ArrayList<>(),inputs,new ArrayList<>(),new UiTextInputState(null,0,0),new ArrayList<>(),new ArrayList<>(),null,1,null)
                .withInteractions(interactions);
        }
        int inkedPixels(int x0,int y0,int x1,int y1,int background){
            int n=0;for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){int p=image.getRGB(x,y);if((p>>>24)>0&&p!=background)n++;}return n;
        }
        int pixelsLike(int x0,int y0,int x1,int y1,java.util.function.IntPredicate match){
            int n=0;for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)if(match.test(image.getRGB(x,y)))n++;return n;
        }
        public void close(){g.dispose();}
    }

    @Test void viewRegistersAReadOnlyMultilineInputShowingItsText() {
        try(var p=new Paint()) {
            p.ui().textView("log","first\nsecond",10,10,200,80);
            var input=p.inputs.getFirst();
            assertTrue(input.readOnly()&&input.multiline());
            assertEquals("first\nsecond",p.values.get(input.key()));
        }
    }

    @Test void theCallersTextWinsOverAnythingStoredForTheView() {
        try(var p=new Paint()) {
            p.ui().textView("log","one",10,10,200,80);
            p.values.put(p.inputs.getFirst().key(),"edited elsewhere");
            p.inputs.clear();
            p.ui().textView("log","two",10,10,200,80);
            assertEquals("two",p.values.get(p.inputs.getFirst().key()));
        }
    }

    @Test void editableAreasStayEditable() {
        try(var p=new Paint()) {
            p.ui().textArea("notes","notes","",10,10,200,80,"hi");
            assertFalse(p.inputs.getFirst().readOnly());
        }
    }

    @Test void lineNumbersMoveTheTextRightAndNumberOnlyFirstRows() {
        String wrapped="x\n"+"word ".repeat(30);
        float plainStart,numberedStart;
        try(var p=new Paint()) {
            p.ui().textView("code",wrapped,10,10,160,200,MONO,false);
            plainStart=p.inputs.getFirst().caretX(0);
        }
        try(var p=new Paint()) {
            p.ui().textView("code",wrapped,10,10,160,200,MONO,true);
            numberedStart=p.inputs.getFirst().caretX(0);
            assertTrue(numberedStart>plainStart+6,"the gutter pushes the text right");
            int background=p.image.getRGB(12,200);
            float top=10+UiInputStyle.defaults().paddingY();
            int gutterLeft=(int)(10+UiInputStyle.defaults().paddingX()),gutterRight=(int)numberedStart-2;
            assertTrue(p.inkedPixels(gutterLeft,(int)top,gutterRight,(int)top+16,background)>0,"line 1 is numbered");
            assertTrue(p.inkedPixels(gutterLeft,(int)top+16,gutterRight,(int)top+32,background)>0,"line 2 is numbered");
            assertEquals(0,p.inkedPixels(gutterLeft,(int)top+33,gutterRight,(int)top+46,background),"a wrapped continuation row has no number");
        }
    }

    private static boolean reddish(int p){return ((p>>16)&255)>170&&((p>>8)&255)<90&&(p&255)<90;}
    private static boolean greenish(int p){return ((p>>8)&255)>170&&((p>>16)&255)<90&&(p&255)<90;}
    private static boolean amber(int p){return ((p>>16)&255)>200&&((p>>8)&255)>120&&((p>>8)&255)<200&&(p&255)<60;}

    @Test void richRunsKeepTheirOwnColours() {
        var red=new UiRichText.Span("ERROR bad\n",0xFFFF2020,false,false,false,false,false);
        var green=new UiRichText.Span("DONE ok",0xFF20FF20,false,false,false,false,false);
        try(var p=new Paint()) {
            p.ui().textView("log",UiRichText.of(List.of(red,green)),10,10,300,120,MONO,false,UiScrollFollow.NONE);
            assertEquals("ERROR bad\nDONE ok",p.values.get(p.inputs.getFirst().key()));
            float top=10+UiInputStyle.defaults().paddingY();
            assertTrue(p.pixelsLike(10,(int)top,310,(int)top+16,UiTextViewTest::reddish)>0,"the first line is red");
            assertEquals(0,p.pixelsLike(10,(int)top,310,(int)top+16,UiTextViewTest::greenish),"no green on the red line");
            assertTrue(p.pixelsLike(10,(int)top+16,310,(int)top+32,UiTextViewTest::greenish)>0,"the second line is green");
        }
    }

    @Test void linksAreDrawnInTheAccentAndUnderlined() {
        try(var p=new Paint()) {
            p.ui().textView("log","see https://example.com/docs now",10,10,300,60,MONO,false,UiScrollFollow.NONE);
            var input=p.inputs.getFirst();
            float top=10+UiInputStyle.defaults().paddingY();
            int linkX0=(int)input.caretX(4),linkX1=(int)input.caretX(28);
            assertTrue(p.pixelsLike(linkX0,(int)top,linkX1,(int)top+16,UiTextViewTest::amber)>20,"the link uses the accent");
            assertEquals(0,p.pixelsLike(10,(int)top,linkX0-1,(int)top+16,UiTextViewTest::amber),"text before the link does not");
            assertTrue(p.pixelsLike(linkX0,(int)top+13,linkX1,(int)top+17,UiTextViewTest::amber)>(linkX1-linkX0)/2,"the link is underlined");
        }
    }

    @Test void linksStopBeforeSentencePunctuation() {
        assertEquals(List.of("https://a.dev/x","http://b.io/y"),
            UiTextLinks.find("Go to https://a.dev/x. Or (http://b.io/y), or not http:// at all.").stream().map(UiTextLinks.Link::url).toList());
        assertEquals("https://en.wikipedia.org/wiki/Mod_(video_games)",
            UiTextLinks.find("read https://en.wikipedia.org/wiki/Mod_(video_games).").getFirst().url());
        var link=UiTextLinks.at("x https://a.dev y",5);
        assertNotNull(link);
        assertEquals(2,link.start());
        assertNull(UiTextLinks.at("x https://a.dev y",16));
    }

    @Test void aFollowingViewKeepsShowingNewLines() {
        try(var p=new Paint()) {
            p.ui().textView("log","one",10,10,200,60,MONO,false,UiScrollFollow.WHEN_AT_END);
            String many="line\n".repeat(30)+"last";
            p.inputs.clear();
            p.ui().textView("log",many,10,10,200,60,MONO,false,UiScrollFollow.WHEN_AT_END);
            var editor=p.interactions.editors.get("log");
            assertTrue(editor.maximum>0);
            assertEquals(editor.maximum,editor.offset,.01,"it followed to the end");
            assertTrue(p.ui().scrollAtEnd("log"));
            // A reader who scrolled up stays where they are.
            editor.scroll(2);
            p.inputs.clear();
            p.ui().textView("log",many,10,10,200,60,MONO,false,UiScrollFollow.WHEN_AT_END);
            float kept=editor.offset;
            p.inputs.clear();
            p.ui().textView("log",many+"\nmore",10,10,200,60,MONO,false,UiScrollFollow.WHEN_AT_END);
            assertEquals(kept,editor.offset,.01);
            assertFalse(p.ui().scrollAtEnd("log"));
            assertEquals(kept,p.ui().scrollOffset("log"),.01);
        }
    }

    @Test void aPlainViewDoesNotFollow() {
        try(var p=new Paint()) {
            p.ui().textView("code","one",10,10,200,60,MONO,false);
            p.inputs.clear();
            p.ui().textView("code","line\n".repeat(30),10,10,200,60,MONO,false);
            assertEquals(0,p.interactions.editors.get("code").offset,.01);
        }
    }
}
