package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;

final class UiScreenOptionsTest {
    @Test
    void defaultsOwnTheWholeSurfaceWithoutConsumerHooks() {
        UiScreenOptions options = UiScreenOptions.defaults();
        assertEquals(UiBackdrop.NONE, options.backdrop());
        assertSame(UiPerformanceDiagnostics.NONE, options.performanceDiagnostics());
        assertFalse(options.keyHandler().keyPressed(null, new KeyEvent(0, 0, 0)));
        assertFalse(options.closeHandler().onClose(null));
    }

    @Test
    void builderKeepsExplicitIntegrationHooks() {
        UiKeyHandler keys = (screen, event) -> true;
        UiClickHandler clicks = (screen, event) -> event.doubleClick();
        UiCloseHandler close = screen -> true;
        UiScreenOptions options = UiScreenOptions.builder()
            .backdrop(UiBackdrop.MINECRAFT)
            .onKey(keys)
            .onClick(clicks)
            .onClose(close)
            .build();

        assertEquals(UiBackdrop.MINECRAFT, options.backdrop());
        assertSame(keys, options.keyHandler());
        assertSame(clicks, options.clickHandler());
        assertSame(close, options.closeHandler());
        assertTrue(options.clickHandler().mouseClicked(null,
            new UiClickEvent("row", null, true, 12, 18)));
    }

    @Test
    void diagnosticsAreStrictlyOptIn() {
        UiPerformanceDiagnostics diagnostics = snapshot -> { };
        assertSame(diagnostics, UiScreenOptions.builder().performanceDiagnostics(diagnostics).build().performanceDiagnostics());
        assertSame(UiPerformanceDiagnostics.NONE,
            UiScreenOptions.builder().performanceDiagnostics(null).build().performanceDiagnostics());
    }
}
