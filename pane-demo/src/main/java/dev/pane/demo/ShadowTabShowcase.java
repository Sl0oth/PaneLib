package dev.pane.demo;

import dev.pane.ui.UiCanvas;
import dev.pane.ui.UiContext;
import dev.pane.ui.UiDocument;
import dev.pane.ui.UiPage;
import dev.pane.ui.UiScreen;
import dev.pane.ui.UiShell;
import dev.pane.ui.UiTheme;
import java.awt.Font;

/** Minimal retained-surface reference for short paper-tab shadows and partial hover repaints. */
public final class ShadowTabShowcase {
    private ShadowTabShowcase() { }

    public static UiScreen createScreen() {
        return new UiScreen(UiDocument.builder("Shadowed Paper Tabs")
            .brand("Pane").theme(UiTheme.light()).shell(UiShell.NONE)
            .page(new UiPage("tabs", "Tabs", "", 0xFF8C5237, ShadowTabShowcase::paint)).build(), null,
            (type, key, value) -> { });
    }

    private static void paint(UiContext ui) {
        UiCanvas g = ui.canvas();
        float w = ui.width(), h = ui.height();
        float railX = Math.max(44, w * .11F), railY = Math.max(54, h * .15F);
        float railW = Math.min(278, w - railX - 44), railH = Math.min(282, h - railY - 42);
        g.fill(0xFFF4F0E4, 0, 0, w, h);
        g.roundRect(0xFFE1D7C5, railX - 12, railY - 12, railW + 24, railH + 24, 5);
        g.text("SHADOW BOUNDS / PARTIAL REPAINT", railX, railY - 34, 9, 0xFF523A2D, Font.BOLD, UiCanvas.SANS);
        for (int i = 0; i < 4; i++) {
            String id = "paper-tab-" + i;
            float tabX = railX + 14 + i * 3, tabY = railY + 16 + i * 50, tabW = railW - 28;
            boolean hover = ui.hovered(id);
            // Exact public geometry used by the downstream reproduction: a 34px rounded paper tab,
            // spread 5, vertical offset 3. A hover only changes this one tab's retained pixels.
            g.shadow(0x7D1C1008, 5, 0, 3, tabX + 2, tabY, tabW - 4, 34, 2);
            g.roundRect(hover ? 0xFFFFF9EA : 0xFFFFFDF5, tabX + 2, tabY, tabW - 4, 34, 2);
            g.strokeRoundRect(0xFFB9A78E, .8F, tabX + 2, tabY, tabW - 4, 34, 2);
            g.text("paper tab " + (i + 1), tabX + 16, tabY + 10, 9, 0xFF49372B, Font.BOLD, UiCanvas.SERIF);
            ui.hit(id, null, tabX + 2, tabY, tabW - 4, 34);
        }
    }
}
