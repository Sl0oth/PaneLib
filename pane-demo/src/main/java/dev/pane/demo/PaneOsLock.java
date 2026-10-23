package dev.pane.demo;

import dev.pane.demo.PaneOsArt.Look;
import dev.pane.ui.*;
import java.awt.Font;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Start-up chime screen and the lock screen in front of the desktop. */
final class PaneOsLock {
    static final long BOOT_MILLIS = 1900;

    private PaneOsLock() { }

    /** Black screen, the mark and a filling progress bar, as a Mac shows while starting. */
    static void boot(UiContext ui, float w, float h, long elapsed) {
        UiCanvas g = ui.canvas();
        g.fill(0xFF000000, 0, 0, w, h);
        float fade = ui.animateFrom("boot:mark", 0, 1, 500, 0, UiEasing.EASE_OUT, new UiRect(w / 2 - 60, h / 2 - 110, 120, 120));
        PaneOsArt.mark(g, w / 2, h / 2 - 50, 86, UiCanvas.alpha(0xFFFFFFFF, Math.round(fade * 255)));
        float bw = 220, bx = (w - bw) / 2, by = h / 2 + 60;
        UiRect bar = new UiRect(bx - 2, by - 2, bw + 4, 10);
        float progress = ui.animateFrom("boot:progress", 0, 1, (int) BOOT_MILLIS - 400, 250, UiEasing.EASE_IN_OUT, bar);
        g.roundRect(0xFF3A3A3C, bx, by, bw, 5, 2.5F);
        g.roundRect(0xFFE5E5EA, bx, by, Math.max(5, bw * progress), 5, 2.5F);
        ui.refreshAfter(Math.max(1, BOOT_MILLIS - elapsed), null);
    }

    /** Blurred wallpaper, the time large at the top, the account and a glass password field at the bottom. */
    static void lock(UiContext ui, UiLayer cache, UiLayer wallpaper, UiLayer art, Object wallRevision, int kind, Look look, float w, float h) {
        UiCanvas g = ui.canvas();
        g.cachedLayer(cache, List.of(wallRevision, "lock"), new UiRect(0, 0, w, h), c -> {
            c.cachedLayer(wallpaper, wallRevision, new UiRect(0, 0, w, h), wc -> PaneOsArt.wallpaper(wc, art, kind, look.dark(), w, h));
            c.backdropBlur(24, 0, 0, w, h);
            c.backdropBlur(24, 0, 0, w, h);
            c.fill(0x1F000000, 0, 0, w, h);
        });
        float rise = ui.animateFrom("lock:in", 0, 1, 700, 0, UiEasing.EASE_OUT, new UiRect(0, 0, w, h));
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH));
        String time = now.format(DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH));
        float top = h * .1F + (1 - rise) * 20;
        int white = UiCanvas.alpha(0xFFFFFFFF, Math.round(235 * rise));
        g.centeredText(date, w / 2, top, 22, white, Font.BOLD, PaneOsArt.FONT);
        g.centeredText(time, w / 2, top + 88, 132, UiCanvas.alpha(0xFFFFFFFF, Math.round(215 * rise)), Font.BOLD, PaneOsArt.FONT);
        ui.refreshAfter(60_000 - System.currentTimeMillis() % 60_000 + 20, new UiRect(w / 2 - 260, top - 20, 520, 190));
        PaneOsArt.symbol(g, "battery", w - 40, 18, 22, white);
        PaneOsArt.symbol(g, "wifi", w - 76, 17, 16, white);

        float cy = h - 170, ax = w / 2;
        ui.paintedButton("lock:avatar", "Unlock", "unlock", ax - 34, cy - 34, 68, 68, 34, true, (c, s) -> {
            c.shadow(0x40000000, 10, 0, 4, ax - 32, cy - 32, 64, 64, 32);
            c.gradientRect(UiCanvas.lighten(look.accent(), .3F), look.accent(), 90, ax - 32, cy - 32, 64, 64, 32);
            c.centeredText("S", ax, cy, 30, 0xFFFFFFFF, Font.BOLD, PaneOsArt.FONT);
        });
        g.centeredText("Sl0oth", ax, cy + 52, 16, white, Font.BOLD, PaneOsArt.FONT);
        float fw = 220, fx = ax - fw / 2, fy = cy + 72;
        g.liquidGlass(fx, fy, fw, 34, 17, look.clear().withTint(0x33FFFFFF).withShadow(0));
        var style = UiInputStyle.defaults().font(PaneOsArt.FONT, 13, Font.PLAIN).padding(14, 6)
            .appearance(UiInputAppearance.defaults().background(0x00000000).border(0x00000000).focus(0x00000000)
                .text(0xFFFFFFFF).placeholder(0xB3FFFFFF).caret(0xFFFFFFFF).selection(0x66FFFFFF));
        String typed = ui.passwordField("lock:field", "lock:pw", "Enter Password", fx, fy, fw - 34, 34, "", 64, style);
        float arrowX = fx + fw - 18;
        ui.paintedButton("lock:go", "Unlock", "unlock", arrowX - 13, fy + 4, 26, 26, 13, true, (c, s) -> {
            c.circle(typed.isEmpty() ? 0x40FFFFFF : 0xE6FFFFFF, arrowX, fy + 17, 11);
            int ink = typed.isEmpty() ? 0xCCFFFFFF : 0xFF1D1D1F;
            c.line(ink, 1.6F, arrowX - 4.5F, fy + 17, arrowX + 4.5F, fy + 17);
            c.path().moveTo(arrowX + .5F, fy + 12.5F).lineTo(arrowX + 4.8F, fy + 17).lineTo(arrowX + .5F, fy + 21.5F).stroke(ink, 1.6F);
        });
        g.centeredText("Press Return or click to unlock", ax, fy + 56, 12, UiCanvas.alpha(0xFFFFFFFF, 170), Font.PLAIN, PaneOsArt.FONT);
    }
}
