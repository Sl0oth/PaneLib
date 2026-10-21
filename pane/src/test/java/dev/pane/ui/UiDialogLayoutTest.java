package dev.pane.ui;

import java.awt.Font;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UiDialogLayoutTest {
    @Test void longCopyAndActionsRemainBoundedAcrossViewportSizes() {
        var image=new BufferedImage(1000,800,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
        try {
            var canvas=new UiCanvas(graphics,image);
            var dialog=new UiDialog("Long title ".repeat(20),"🔧unbreakable".repeat(100)+"\nfinal line",
                "Bestätigung für alle Änderungen ohne weitere Rückfrage","yes","Zur vorherigen Ansicht zurückkehren","no");
            var shortLayout=UiDialogLayout.measure(canvas,dialog,UiCanvas.SANS,120,90);
            assertTrue(shortLayout.cancel().height()>=canvas.textHeight(8,Font.BOLD,UiCanvas.SANS)+4);
            assertTrue(shortLayout.confirm().height()>=canvas.textHeight(8,Font.BOLD,UiCanvas.SANS)+4);
            for(int width:new int[]{1,8,40,120,240,320,1000})for(int height:new int[]{1,20,100,260,800}) {
                var layout=UiDialogLayout.measure(canvas,dialog,UiCanvas.SANS,width,height);
                inside(new UiRect(0,0,width,height),layout.frame());inside(layout.frame(),layout.cancel());inside(layout.frame(),layout.confirm());inside(layout.frame(),layout.content());
                assertTrue(layout.cancel().bottom()<=layout.confirm().y()+.001F||layout.cancel().right()<=layout.confirm().x()+.001F);
                assertTrue(canvas.textWidth(layout.confirmLabel(),8,Font.BOLD,UiCanvas.SANS)<=Math.max(0,layout.confirm().width()-12)+.001F);
                assertEquals(layout.maxOffset(),layout.clamp(Float.MAX_VALUE));
                for(var line:layout.lines())if(!line.text().isEmpty())assertFalse(Character.isLowSurrogate(line.text().charAt(0)));
                assertTrue(layout.lines().stream().anyMatch(line->line.text().contains("final")||width<120));
            }
        }finally{graphics.dispose();}
    }
    @Test void customizationIsValueBasedAndRejectsNonfiniteDimensions() {
        var a=UiDialogStyle.builder().maxWidth(480).padding(20).maxHeightFraction(.8F).build();
        var b=UiDialogStyle.builder().maxWidth(480).padding(20).maxHeightFraction(.8F).build();
        assertEquals(40F,UiDialogStyle.builder().radius(Float.POSITIVE_INFINITY).build().radius());
        assertEquals(a,b);assertEquals(a.hashCode(),b.hashCode());
        assertNotEquals(a,UiDialogStyle.builder().maxWidth(481).padding(20).maxHeightFraction(.8F).build());
        assertThrows(IllegalArgumentException.class,()->UiDialogStyle.builder().maxWidth(Float.NaN).build());
        assertThrows(IllegalArgumentException.class,()->UiDialogStyle.builder().padding(-1).build());
        assertThrows(IllegalArgumentException.class,()->UiDialogStyle.builder().maxHeightFraction(1.1F).build());
        assertThrows(IllegalArgumentException.class,()->UiDialogStyle.builder().radius(Float.NaN).build());
    }
    private void inside(UiRect outer,UiRect inner) {
        assertTrue(inner.width()>0&&inner.height()>0,inner.toString());
        assertTrue(inner.x()>=outer.x()-.001F&&inner.y()>=outer.y()-.001F&&inner.right()<=outer.right()+.001F&&inner.bottom()<=outer.bottom()+.001F,inner+" inside "+outer);
    }
}
