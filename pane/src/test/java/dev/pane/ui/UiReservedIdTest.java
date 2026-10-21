package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UiReservedIdTest {
    @Test void rejectsModalAndToastNamesIncludingClippedTargets() {
        withContext(ui -> {
            for (String id : List.of("dialog-confirm", "dialog-cancel", "dialog-body", "dialog-backdrop",
                    "menu:large:option-1", "menu-body:large", "toast-dismiss:notice")) {
                assertThrows(IllegalArgumentException.class, () -> ui.hit(id,"consumer",0,0,20,20), id);
                assertThrows(IllegalArgumentException.class, () -> ui.hit(id,"consumer",0,0,0,0), id);
                assertThrows(IllegalArgumentException.class, () -> ui.tooltip(id,"tip",0,0,20,20), id);
            }
        });
    }
    @Test void derivedControlsAndChildContextsCannotBypassValidation() {
        withContext(ui -> {
            assertThrows(IllegalArgumentException.class, () -> ui.textField("dialog-confirm","key","",0,0,100,"",20));
            assertThrows(IllegalArgumentException.class, () -> ui.slider("dialog-body","number",0,0,100,0));
            assertThrows(IllegalArgumentException.class, () -> ui.withTransform(UiTransform.identity().rotate(10),0,0,
                child -> child.hit("dialog-cancel","action",0,0,20,20)));
            assertThrows(IllegalArgumentException.class, () -> ui.withTranslation(10,10, child -> child.hit("menu:x:y","action",0,0,20,20)));
            assertThrows(IllegalArgumentException.class, () -> ui.virtualList("list",new UiRect(0,0,100,80),2,30,0,
                (row,item) -> row.hit("toast-dismiss:x","action",0,item.bounds().y(),20,20)));
        });
    }
    @Test void preservesOrdinaryIdsAndArbitraryActionNames() {
        withContext(ui -> {
            for (String id : List.of("confirm", "dialog-confirm-custom", "my:menu", "menu:settings", "toast", ""))
                assertDoesNotThrow(() -> ui.hit(id,"dialog-confirm",0,0,20,20));
        });
    }
    private void withContext(java.util.function.Consumer<UiContext> painter) {
        var image=new BufferedImage(200,100,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
        try {
            var ui=new UiContext(new UiCanvas(graphics,image),UiTheme.dark(),200,100,0xFF55AAFF,
                new UiRect(0,0,200,100),null,null,1,1,false,0,new HashMap<>(),new ArrayList<>(),new LinkedHashMap<>(),
                new LinkedHashMap<>(),new HashMap<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),
                new UiTextInputState(null,0,0),new ArrayList<>(),new ArrayList<>(),null,1,null);
            painter.accept(ui);
        } finally {graphics.dispose();}
    }
}
