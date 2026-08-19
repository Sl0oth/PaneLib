package dev.pane.demo;

import dev.pane.ui.UiCanvas;
import dev.pane.ui.UiContext;
import dev.pane.ui.UiDocument;
import dev.pane.ui.UiPage;
import dev.pane.ui.UiRect;
import dev.pane.ui.UiScreen;
import dev.pane.ui.UiShell;
import dev.pane.ui.UiTheme;
import java.awt.Font;

import static dev.pane.ui.UiContext.ButtonStyle.PRIMARY;
import static dev.pane.ui.UiContext.ButtonStyle.SECONDARY;
import static dev.pane.ui.UiContext.Shape.ROUNDED;

/**
 * A second consumer of the same library, deliberately sharing nothing with the main showcase:
 * light theme, its own top-bar chrome instead of the sidebar, its own accent and layout.
 * <p>
 * It exists to keep the library honest. If anything from the showcase's own design leaks into
 * {@link UiScreen} rather than living in a {@link UiShell} or a painter, it shows up here.
 */
public final class AltShellShowcase {
    private static final int ACCENT = 0xFF2F6FEB;

    private AltShellShowcase() {}

    public static UiScreen createScreen() {
        return new UiScreen(UiDocument.builder("Server Browser")
            .brand("Relay")
            .theme(UiTheme.light())
            .shell(AltShellShowcase::topBar)
            .page(new UiPage("servers", "Servers", "", ACCENT, AltShellShowcase::servers))
            .build());
    }

    /** Custom chrome: a full-bleed page with a top bar rather than a navigation rail. */
    private static UiRect topBar(UiContext ui, UiDocument document, UiPage page) {
        UiCanvas g = ui.canvas();
        UiTheme t = ui.theme();
        float barHeight = 46;
        g.fill(t.background(), 0, 0, ui.width(), ui.height());
        g.fill(t.surface(), 0, 0, ui.width(), barHeight);
        g.line(t.border(), 1, 0, barHeight, ui.width(), barHeight);
        g.roundRect(ACCENT, 18, 14, 18, 18, 5);
        g.centeredText("R", 27, 23, 10, 0xFFFFFFFF, Font.BOLD);
        g.textMedium(document.brand(), 44, 15, 12, t.text());
        g.text("Multiplayer", 44, 30, 7, t.mutedText());
        ui.button("bar-refresh", "Refresh", "refresh", ui.width() - 168, 11, 66, 25, SECONDARY, ROUNDED);
        ui.button("bar-add", "Add server", "add", ui.width() - 96, 11, 78, 25, PRIMARY, ROUNDED);
        return new UiRect(0, barHeight, ui.width(), ui.height() - barHeight);
    }

    private static void servers(UiContext ui) {
        UiCanvas g = ui.canvas();
        UiTheme t = ui.theme();
        float x = ui.contentX() + 22;
        float y = ui.contentY() + 20 + (1 - ui.motion()) * 6;
        float w = ui.contentWidth() - 44;
        g.textMedium("Saved servers", x, y, 15, t.text());
        g.text("4 reachable of 5", x, y + 22, 7.5F, t.mutedText());
        float rowY = y + 46;
        row(ui, "srv-a", "Hypixel", "mc.hypixel.net", "18 ms", 92, x, rowY, w, true);
        row(ui, "srv-b", "Creative Plots", "plots.example.net", "34 ms", 61, x, rowY + 56, w, true);
        row(ui, "srv-c", "SMP Season 4", "smp.example.org", "77 ms", 24, x, rowY + 112, w, true);
        row(ui, "srv-d", "Modded Testing", "10.0.0.14", "offline", 0, x, rowY + 168, w, false);
    }

    private static void row(UiContext ui, String id, String name, String address, String ping,
                            int players, float x, float y, float w, boolean online) {
        UiCanvas g = ui.canvas();
        UiTheme t = ui.theme();
        int fill = ui.hovered(id) ? UiCanvas.mix(t.surface(), t.raisedSurface(), .9F) : t.surface();
        ui.card(x, y, w, 48, t.mediumRadius(), fill);
        g.roundRect(t.raisedSurface(), x + 10, y + 9, 30, 30, 7);
        g.centeredText(name.substring(0, 1), x + 25, y + 24, 12, t.mutedText(), Font.BOLD);
        g.textMedium(name, x + 50, y + 10, 9, t.text());
        g.text(address, x + 50, y + 26, 7, t.mutedText());
        g.circle(online ? 0xFF2FA85E : 0xFFC2C8D0, x + w - 184, y + 24, 3);
        g.text(online ? players + " players" : "unreachable", x + w - 176, y + 19, 7, t.mutedText());
        g.text(ping, x + w - 102, y + 19, 7, t.mutedText());
        ui.button(id, online ? "Join" : "Retry", id, x + w - 56, y + 11, 46, 26,
            online ? PRIMARY : SECONDARY, ROUNDED);
        ui.hit(id + "-row", id + "-row", x, y, w, 48);
    }
}
