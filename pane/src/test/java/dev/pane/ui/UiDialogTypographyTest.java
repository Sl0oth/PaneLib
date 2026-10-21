package dev.pane.ui;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiDialogTypographyTest {
    private static UiDialog dialog(UiDialogStyle style){return new UiDialog("Review expedition details", "First line\nSecond line\n"+"More survey notes to review. ".repeat(20),"Confirm expedition","yes","Return to loadout","no",style);}
    private static void paint(Consumer<UiCanvas> test){var image=new BufferedImage(1200,900,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();try{test.accept(new UiCanvas(g,image));}finally{g.dispose();}}
    @Test void unsetTypographyKeepsDefaultsAndInheritsTheme() {
        paint(c->{
            var a=UiDialogLayout.measure(c,dialog(null),UiCanvas.MONO,800,600);
            var b=UiDialogLayout.measure(c,dialog(UiDialogStyle.builder().build()),UiCanvas.MONO,800,600);
            assertEquals(a,b);
            assertEquals(new UiDialogLayout.Typography(UiCanvas.MONO,12,Font.BOLD,2),a.titleText());
            assertEquals(new UiDialogLayout.Typography(UiCanvas.MONO,8,Font.PLAIN,2),a.messageText());
            assertEquals(new UiDialogLayout.Typography(UiCanvas.MONO,8,Font.BOLD,0),a.buttonText());
            assertEquals(28,a.cancel().height());
        });
    }
    @Test void explicitFamiliesOverrideSharedFamilyIndependently() {
        paint(c->{
            var style=UiDialogStyle.builder().fontFamily(UiCanvas.SERIF).titleFontFamily(UiCanvas.MONO)
                .buttonFontFamily(UiCanvas.SANS).titleSize(23).messageSize(15).buttonSize(17)
                .titleFontStyle(Font.ITALIC).messageFontStyle(Font.BOLD).buttonFontStyle(Font.PLAIN).build();
            var l=UiDialogLayout.measure(c,dialog(style),"unrelated-theme",900,700);
            assertEquals(new UiDialogLayout.Typography(UiCanvas.MONO,23,Font.ITALIC,2),l.titleText());
            assertEquals(new UiDialogLayout.Typography(UiCanvas.SERIF,15,Font.BOLD,2),l.messageText());
            assertEquals(new UiDialogLayout.Typography(UiCanvas.SANS,17,Font.PLAIN,0),l.buttonText());
            var shared=UiDialogLayout.measure(c,dialog(UiDialogStyle.builder().fontFamily(UiCanvas.MONO).build()),UiCanvas.SERIF,900,700);
            assertEquals(UiCanvas.MONO,shared.titleText().family());assertEquals(UiCanvas.MONO,shared.messageText().family());assertEquals(UiCanvas.MONO,shared.buttonText().family());
        });
    }
    @Test void wrappingLineSpacingAndScrollingShareExactFontMetrics() {
        paint(c->{
            var l=UiDialogLayout.measure(c,dialog(UiDialogStyle.builder().titleSize(28).messageSize(18)
                .titleFontFamily(UiCanvas.SERIF).messageFontFamily(UiCanvas.MONO).titleLineSpacing(7).messageLineSpacing(9).build()),UiCanvas.SANS,420,500);
            for(int i=0;i<l.lines().size();i++) {
                var line=l.lines().get(i);var typography=line.title()?l.titleText():l.messageText();
                assertEquals(typography.height(c)+typography.lineSpacing(),line.height());
                assertEquals(line.height(),l.scrollStep(line.top()));
                if(i+1<l.lines().size()&&line.title()==l.lines().get(i+1).title())assertEquals(line.top()+line.height(),l.lines().get(i+1).top());
                String text=line.text().stripTrailing();
                assertTrue(typography.width(c,text)<=l.content().width()-6+.01F||text.codePointCount(0,text.length())<=1);
            }
            assertEquals(l.lines().getLast().top()+l.lines().getLast().height(),l.contentHeight());
            assertTrue(l.maxOffset()>0);assertEquals(l.maxOffset(),l.clamp(Float.MAX_VALUE));
        });
    }
    @Test void actionButtonsGrowAndEllipsizeUsingTheirOwnTypography() {
        paint(c->{
            var style=UiDialogStyle.builder().maxWidth(220).buttonFontFamily(UiCanvas.MONO).buttonSize(26).buttonFontStyle(Font.ITALIC).build();
            var l=UiDialogLayout.measure(c,dialog(style),UiCanvas.SANS,800,700);
            assertEquals(c.textHeight(26,Font.ITALIC,UiCanvas.MONO)+12,l.cancel().height());
            assertTrue(l.cancel().bottom()<l.confirm().y(),"wide labels stack");
            assertTrue(l.buttonText().width(c,l.cancelLabel())<=l.cancel().width()-12);
            assertTrue(l.buttonText().width(c,l.confirmLabel())<=l.confirm().width()-12);
            assertTrue(l.cancelLabel().endsWith("…"));
        });
    }
    @Test void largeTypographyStaysBoundedAcrossSmallAndLargeWindows() {
        paint(c->{
            var d=new UiDialog("Title 🔧".repeat(5),"Surrogate 🔧 and LongUnbrokenWord".repeat(25),"Bestätigen 🔧 vollständig","yes","Änderungen verwerfen 🔧","no",
                UiDialogStyle.builder().fontFamily(UiCanvas.SERIF).titleSize(42).messageSize(24).buttonSize(22).messageLineSpacing(12).build());
            for(int w:new int[]{1,8,80,160,320,900})for(int h:new int[]{1,20,90,180,400,800}){
                var l=UiDialogLayout.measure(c,d,UiCanvas.SANS,w,h);
                inside(new UiRect(0,0,w,h),l.frame());inside(l.frame(),l.content());inside(l.frame(),l.cancel());inside(l.frame(),l.confirm());
                assertTrue(l.content().bottom()<=l.cancel().y()+.01F);
                assertTrue(l.cancel().bottom()<=l.confirm().y()+.01F||l.cancel().right()<=l.confirm().x()+.01F);
                assertTrue(l.buttonText().width(c,l.confirmLabel())<=Math.max(0,l.confirm().width()-12)+.01F);
                for(var line:l.lines())if(!line.text().isEmpty())assertFalse(Character.isLowSurrogate(line.text().charAt(0)));
            }
        });
    }
    @Test void allTypographyFieldsParticipateInStyleValueEquality() {
        var zero=UiDialogStyle.builder().titleLineSpacing(0).messageLineSpacing(0).build();
        var negativeZero=UiDialogStyle.builder().titleLineSpacing(-0F).messageLineSpacing(-0F).build();
        assertEquals(zero,negativeZero);assertEquals(zero.hashCode(),negativeZero.hashCode());
        var baseline=UiDialogStyle.builder().build();assertEquals(baseline,UiDialogStyle.builder().build());assertEquals(baseline.hashCode(),UiDialogStyle.builder().build().hashCode());
        List<Consumer<UiDialogStyle.Builder>> changes=List.of(b->b.fontFamily("custom"),b->b.titleFontFamily("custom"),b->b.messageFontFamily("custom"),b->b.buttonFontFamily("custom"),
            b->b.titleSize(20),b->b.messageSize(15),b->b.buttonSize(14),b->b.titleFontStyle(Font.PLAIN),b->b.messageFontStyle(Font.BOLD),b->b.buttonFontStyle(Font.ITALIC),b->b.titleLineSpacing(6),b->b.messageLineSpacing(7));
        for(var change:changes){var a=UiDialogStyle.builder();var b=UiDialogStyle.builder();change.accept(a);change.accept(b);assertEquals(a.build(),b.build());assertEquals(a.build().hashCode(),b.build().hashCode());assertNotEquals(baseline,a.build());}
    }
    @Test void typographyRejectsInvalidMetricsAndStyles() {
        for(float v:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,0,129}){
            assertThrows(IllegalArgumentException.class,()->UiDialogStyle.builder().titleSize(v).build());
            assertThrows(IllegalArgumentException.class,()->UiDialogStyle.builder().messageSize(v).build());
            assertThrows(IllegalArgumentException.class,()->UiDialogStyle.builder().buttonSize(v).build());
        }
        for(float v:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,-1,129}){
            assertThrows(IllegalArgumentException.class,()->UiDialogStyle.builder().titleLineSpacing(v).build());
            assertThrows(IllegalArgumentException.class,()->UiDialogStyle.builder().messageLineSpacing(v).build());
        }
        assertThrows(IllegalArgumentException.class,()->UiDialogStyle.builder().fontFamily(" ").build());
        assertThrows(IllegalArgumentException.class,()->UiDialogStyle.builder().titleFontStyle(4).build());
        assertThrows(IllegalArgumentException.class,()->UiDialogStyle.builder().messageFontStyle(-1).build());
        assertThrows(IllegalArgumentException.class,()->UiDialogStyle.builder().buttonFontStyle(8).build());
        assertDoesNotThrow(()->UiDialogStyle.builder().fontFamily(null).titleFontStyle(Font.BOLD|Font.ITALIC).messageLineSpacing(0).build());
    }
    private static void inside(UiRect a,UiRect b){assertTrue(b.width()>0&&b.height()>0&&b.x()>=a.x()-.01&&b.y()>=a.y()-.01&&b.right()<=a.right()+.01&&b.bottom()<=a.bottom()+.01,b+" inside "+a);}
}
