package dev.pane.demo;

import dev.pane.ui.UiBackdrop;
import dev.pane.ui.UiCanvas;
import dev.pane.ui.UiDocument;
import dev.pane.ui.UiPage;
import dev.pane.ui.UiScreen;
import dev.pane.ui.UiScreenOptions;
import dev.pane.ui.UiShell;
import dev.pane.ui.UiTheme;
import java.awt.Font;
import net.minecraft.client.gui.screens.Screen;

/** Transparent reference used to verify real title-panorama and parent restoration integration. */
final class BackdropShowcase {
    private BackdropShowcase() {}

    static UiScreen createScreen(Screen parent) {
        UiTheme theme = new UiTheme(
            0x00000000, 0xC9121519, 0xE51B2026, 0x704E5A66,
            0xFFF4F7FA, 0xFFABB5C0, 0xFF64B5FF, 0xFF07111A,
            3, 6, 9, 1, 1, UiCanvas.SANS
        );
        UiPage page = new UiPage("backdrop", "Backdrop", "", theme.accent(), ui -> {
            UiCanvas g = ui.canvas();
            float w = ui.width(), h = ui.height();
            g.fill(0x7603080D, 0, 0, w, h);
            g.vignette(0xA8000000, 0, 0, w, h);
            g.grain(0x10000000, .055F, 0, 0, w, h);

            float x = Math.max(22, w * .065F), y = Math.max(24, h * .12F);
            float panelW = Math.min(540, w - x * 2), panelH = Math.min(250, h - y * 2);
            g.shadow(0x8A000000, 16, 0, 7, x, y, panelW, panelH, 7);
            g.roundRect(0xD90C1117, x, y, panelW, panelH, 7);
            g.strokeRoundRect(0x785F7182, .8F, x, y, panelW, panelH, 7);
            g.roundRect(ui.accent(), x, y, 4, panelH, 2, 0, 0, 2);

            g.trackedText("MINECRAFT CONTEXT LAYER", x + 24, y + 22, 7, 0xFF91A3B4,
                Font.BOLD, ui.theme().fontFamily(), 1.1F);
            g.text("The world stays part of the interface.", x + 24, y + 48, 22,
                ui.theme().text(), Font.BOLD, ui.theme().fontFamily());
            g.wrappedText("PaneLib now composes transparent retained geometry over the real title panorama, "
                + "keeps raw input available, and returns to the exact parent on Escape.",
                x + 24, y + 88, panelW - 48, 9, 17, ui.theme().mutedText(), Font.PLAIN,
                ui.theme().fontFamily());

            float ruleY = y + panelH - 62;
            g.line(0x554E5A66, .8F, x + 24, ruleY, x + panelW - 24, ruleY);
            g.circle(ui.accent(), x + 29, ruleY + 30, 3);
            g.text("PANORAMA LIVE", x + 41, ruleY + 23, 7, ui.theme().text(), Font.BOLD,
                ui.theme().fontFamily());
            g.text("ESC  BACK", x + panelW - 88, ruleY + 23, 7, ui.theme().mutedText(), Font.BOLD,
                ui.theme().fontFamily());
        });
        UiDocument document = UiDocument.builder("PaneLib backdrop")
            .brand("")
            .theme(theme)
            .shell(UiShell.NONE)
            .page(page)
            .build();
        UiScreenOptions options = UiScreenOptions.builder()
            .parent(parent)
            .backdrop(UiBackdrop.PARENT_BACKGROUND)
            .build();
        return new UiScreen(document, dev.pane.ui.NativeOverlayRenderer.NONE,
            dev.pane.ui.UiEventHandler.NONE, options);
    }
}
