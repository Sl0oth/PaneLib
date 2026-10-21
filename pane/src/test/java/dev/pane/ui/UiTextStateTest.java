package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UiTextStateTest {
    @Test void paintingDoesNotReplaceConsumerValuesOfAnotherType() {
        var state = new HashMap<String,Object>(); state.put("value",42);
        paint(state);
        assertEquals(42,state.get("value"));
    }
    @Test void normalizesStringValuesAndFallbacksOnly() {
        var state = new HashMap<String,Object>(); state.put("value","a\r\nb\rc");
        paint(state); assertEquals("a\nb\nc",state.get("value"));
        state.clear();paint(state);assertEquals("fallback\nline",state.get("value"));
    }
    private void paint(Map<String,Object> state) {
        var image=new BufferedImage(200,100,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
        try {
            var ui=new UiContext(new UiCanvas(graphics,image),UiTheme.dark(),200,100,0xFF55AAFF,
                new UiRect(0,0,200,100),null,null,1,1,false,0,state,new ArrayList<>(),new LinkedHashMap<>(),
                new LinkedHashMap<>(),new HashMap<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),
                new UiTextInputState(null,0,0),new ArrayList<>(),new ArrayList<>(),null,1,null);
            ui.textArea("area","value","",0,0,180,80,"fallback\r\nline",1000);
        } finally {graphics.dispose();}
    }
}
