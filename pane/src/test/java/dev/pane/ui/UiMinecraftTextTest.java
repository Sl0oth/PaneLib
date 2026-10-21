package dev.pane.ui;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UiMinecraftTextTest {
    @Test void styleIsImmutableWithExplicitArgbAndOverflow() {
        var base = UiMinecraftTextStyle.hud();
        var changed = base.color(0x8033AAFF).shadow(false).alignment(UiMinecraftTextStyle.Alignment.RIGHT)
            .overflow(UiMinecraftTextStyle.Overflow.CLIP);
        assertEquals(0xFFFFFFFF, base.color()); assertTrue(base.shadow());
        assertEquals(0x8033AAFF, changed.color()); assertFalse(changed.shadow());
        assertEquals(UiMinecraftTextStyle.Alignment.RIGHT, changed.alignment());
        assertSame(base, UiMinecraftTextStyle.hud());
        assertThrows(NullPointerException.class, () -> base.alignment(null));
        assertThrows(NullPointerException.class, () -> base.overflow(null));
    }

    @Test void physicalGlyphScaleAndBudgetAreIndependentOfGuiScale() {
        var bounds = new NativeBounds(31, 49, 240, 36);
        for (double guiScale : new double[]{1, 1.5, 2, 3, 4}) {
            var f = NativeTextRenderer.frame(bounds, guiScale, 9, true);
            assertEquals(31, f.x() * guiScale, .0001);
            assertEquals(49, f.y() * guiScale, .0001);
            assertEquals(4, f.scale() * guiScale, .0001);
            assertEquals(59, f.available());
        }
        assertEquals(60, NativeTextRenderer.frame(bounds, 2, 9, false).available());
    }

    @Test void fontFadeChangesAlphaOnlyAndComposesWithConsumerAlpha() {
        assertEquals(0x4033AAFF, NativeTextRenderer.fadedColor(0x8033AAFF, .5F));
        assertEquals(0x0033AAFF, NativeTextRenderer.fadedColor(0x8033AAFF, 0));
        assertEquals(0x8033AAFF, NativeTextRenderer.fadedColor(0x8033AAFF, 1));
    }

    @Test void absentTinyInvalidAndTransparentSlotsNeedNoMinecraft() {
        var context = new NativeOverlayContext(null, "test", Map.of(
            "empty", new NativeBounds(0,0,0,9), "tiny", new NativeBounds(0,0,.1,.1),
            "invalid", new NativeBounds(Double.NaN,0,20,9), "valid", new NativeBounds(0,0,20,9)), 2, 0, 0);
        for (String id : new String[]{"missing", "empty", "tiny", "invalid"}) context.text(id, "Safe");
        context.text("valid", "Invisible", UiMinecraftTextStyle.hud().color(0x00FFFFFF));
        assertEquals(0, context.renderedTextCount());
        assertThrows(NullPointerException.class, () -> context.text("missing", "text", null));
        assertThrows(NullPointerException.class, () -> context.text("missing", (String) null));
    }
}
