package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class UiScrollSettingsTest {
    @Test
    void clampsConsumerValuesToSafeBounds() {
        UiScrollSettings settings = new UiScrollSettings(900, -20);
        assertEquals(400, settings.step());
        assertEquals(0, settings.smoothingMillis());
    }

    @Test
    void smoothDefaultsStayFinite() {
        UiScrollSettings settings = UiScrollSettings.smooth();
        assertEquals(46, settings.step());
        assertEquals(105, settings.smoothingMillis());
    }
}
