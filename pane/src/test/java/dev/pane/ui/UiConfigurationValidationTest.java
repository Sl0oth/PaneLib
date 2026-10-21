package dev.pane.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UiConfigurationValidationTest {
    @Test void duplicatePagesFailWithAnActionableId() {
        var first=new UiPage("settings","Settings","",0,ui->{});
        var second=new UiPage("settings","Other","",0,ui->{});
        var error=assertThrows(IllegalArgumentException.class,()->UiDocument.builder("Test").page(first).page(second).build());
        assertTrue(error.getMessage().contains("settings"));
        assertEquals(first,UiDocument.builder("Test").page(first).build().page("settings"));
    }
    @Test void nonFiniteQualityAndScalingAreRejected() {
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->new UiRenderSettings(1000,800,bad));
            for(int field=0;field<5;field++) {
                float[] values={860,480,.6F,.8F,2.5F};values[field]=bad;
                assertThrows(IllegalArgumentException.class,()->new UiScaling(values[0],values[1],values[2],values[3],values[4]));
            }
        }
    }
    @Test void finiteValuesKeepExistingClamps() {
        assertEquals(new UiRenderSettings(512,8192,4),new UiRenderSettings(-10,100000,10));
        var scaling=new UiScaling(-20,-30,2,0,100);
        assertEquals(120,scaling.referenceWidth());assertEquals(90,scaling.referenceHeight());
        assertEquals(1,scaling.adaptivity());assertEquals(.25F,scaling.minScale());assertEquals(8,scaling.maxScale());
    }
}
