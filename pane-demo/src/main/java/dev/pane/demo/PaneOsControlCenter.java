package dev.pane.demo;

import dev.pane.demo.PaneOsArt.Look;
import dev.pane.ui.*;
import java.awt.Font;

/** Control Center: separate glass modules, every toggle and slider live. */
final class PaneOsControlCenter {
    private PaneOsControlCenter() { }

    private static UiGlass module(Look look) {
        return look.glass().withTint(look.dark() ? 0x59262628 : 0x52FFFFFF).withShadow(0x2E000000);
    }

    static void paint(UiContext ui, Look look, UiRect panel) {
        UiCanvas g = ui.canvas();
        // Every place that shows a toggle declares it, so a change repaints just those places.
        UiRect area = panel.inset(-30);
        for (String key : new String[] {"bt", "airdrop", "wifi"}) ui.repaintScope(key, area);
        float x = panel.x(), y = panel.y(), half = (panel.width() - 12) / 2;
        // Connectivity: Wi-Fi, Bluetooth, AirDrop.
        g.liquidGlass(x, y, half, 156, 26, module(look));
        String[][] rows = {{"wifi", "Wi-Fi", "Home", "Off"}, {"bt", "Bluetooth", "On", "Off"}, {"airdrop", "AirDrop", "Contacts Only", "Off"}};
        String[] glyphs = {"wifi", "bluetooth", "airdrop"};
        for (int i = 0; i < rows.length; i++) {
            String key = rows[i][0];
            boolean on = ui.booleanValue(key, true);
            float ry = y + 14 + i * 46;
            String glyph = glyphs[i], title = rows[i][1], subtitle = on ? rows[i][2] : rows[i][3];
            ui.paintedButton("cc:" + key, title, key, x + 10, ry, half - 20, 40, 20, true, (c, s) -> {
                toggleCircle(c, look, x + 30, ry + 20, 16, on, s.pressed(), glyph);
                c.text(title, x + 54, ry + 4, 13, look.text(), Font.BOLD, PaneOsArt.FONT);
                c.text(c.ellipsize(subtitle, half - 70, 11.5F, Font.PLAIN, PaneOsArt.FONT), x + 54, ry + 21, 11.5F, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
            });
        }
        // Now Playing.
        float nx = x + half + 12;
        g.liquidGlass(nx, y, half, 156, 26, module(look));
        int track = (int) ui.numberValue("music:track", 0);
        String[] song = PaneOsApps.TRACKS[track];
        PaneOsApps.albumArt(g, nx + 14, y + 14, 48, track);
        g.text(g.ellipsize(song[0], half - 28, 13, Font.BOLD, PaneOsArt.FONT), nx + 14, y + 72, 13, look.text(), Font.BOLD, PaneOsArt.FONT);
        g.text(g.ellipsize(song[1], half - 28, 11.5F, Font.PLAIN, PaneOsArt.FONT), nx + 14, y + 89, 11.5F, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        boolean playing = ui.booleanValue("music:playing", false);
        String[] controls = {"prev", playing ? "pause" : "play", "next"};
        String[] verbs = {"prev", "play", "next"};
        for (int i = 0; i < 3; i++) {
            float cx = nx + half / 2 + (i - 1) * 42, cy = y + 128;
            String glyph = controls[i];
            float glyphSize = i == 1 ? 22 : 18;
            ui.paintedButton("cc:music:" + verbs[i], verbs[i].equals("play") ? (playing ? "Pause" : "Play") : verbs[i].equals("next") ? "Next" : "Previous",
                "music:" + verbs[i], cx - 16, cy - 16, 32, 32, 16, true, (c, s) -> {
                    if (s.hovered()) c.circle(look.hover(), cx, cy, 16);
                    PaneOsArt.symbol(c, glyph, cx, cy, glyphSize, look.text());
                });
        }
        // Focus.
        float fy = y + 168;
        boolean focus = ui.booleanValue("focus", false);
        g.liquidGlass(x, fy, panel.width(), 58, 29, module(look));
        ui.paintedButton("cc:focus", "Focus", "focus", x + 8, fy + 6, panel.width() - 16, 46, 23, true, (c, s) -> {
            toggleCircle(c, look, x + 30, fy + 29, 17, focus, s.pressed(), "moon", PaneOsArt.INDIGO);
            c.text("Focus", x + 56, fy + 12, 13, look.text(), Font.BOLD, PaneOsArt.FONT);
            c.text(focus ? "Do Not Disturb" : "Off", x + 56, fy + 29, 11.5F, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        });
        // Display and Sound.
        slider(ui, look, "Display", "brightness", "sun", x, fy + 70, panel.width(), .25, 1, 1);
        slider(ui, look, "Sound", "volume", "speaker", x, fy + 150, panel.width(), 0, 1, .6);
        // Bottom row of circular controls.
        String[][] bottom = {{"darkmode", "Dark Mode", "darkmode"}, {"calculator", "Calculator", "open:calculator"},
            {"timer", "Timer", "timer"}, {"screenshot", "Screenshot", "shot"}};
        float step = (panel.width() - 3 * 12) / 4, by = fy + 230;
        for (int i = 0; i < bottom.length; i++) {
            float bx = x + i * (step + 12);
            String glyph = bottom[i][0], label = bottom[i][1], action = bottom[i][2];
            boolean active = glyph.equals("darkmode") ? look.dark() : glyph.equals("timer") && ui.numberValue("timer:end", 0) > System.currentTimeMillis();
            g.liquidGlass(bx, by, step, step, step / 2, module(look));
            ui.shapedButton("cc:" + glyph, label, action, g.path().arc(bx + step / 2, by + step / 2, step / 2, 0, 360).close(), true, (c, s) -> {
                if (active) c.circle(look.accent(), bx + step / 2, by + step / 2, step / 2 - 4);
                else if (s.hovered()) c.circle(look.hover(), bx + step / 2, by + step / 2, step / 2 - 4);
                PaneOsArt.symbol(c, glyph, bx + step / 2, by + step / 2, 26, active ? 0xFFFFFFFF : look.text());
            });
        }
    }

    private static void toggleCircle(UiCanvas c, Look look, float cx, float cy, float r, boolean on, boolean pressed, String glyph) {
        toggleCircle(c, look, cx, cy, r, on, pressed, glyph, look.accent());
    }

    private static void toggleCircle(UiCanvas c, Look look, float cx, float cy, float r, boolean on, boolean pressed, String glyph, int tint) {
        c.circle(on ? tint : look.dark() ? 0x33FFFFFF : 0x24000000, cx, cy, r);
        if (pressed) c.circle(0x26000000, cx, cy, r);
        PaneOsArt.symbol(c, glyph, cx, cy, r * 1.05F, on ? 0xFFFFFFFF : look.text());
    }

    private static void slider(UiContext ui, Look look, String title, String key, String glyph, float x, float y, float width,
                               double min, double max, double fallback) {
        UiCanvas g = ui.canvas();
        g.liquidGlass(x, y, width, 70, 24, module(look));
        g.text(title, x + 16, y + 11, 13, look.text(), Font.BOLD, PaneOsArt.FONT);
        float tx = x + 14, tw = width - 28, ty = y + 34, th = 26;
        ui.paintedSlider("cc:" + key, key, tx, ty, tw, th, min, max, fallback, (c, s) -> {
            c.roundRect(look.dark() ? 0x40FFFFFF : 0x2E000000, tx, ty, tw, th, th / 2);
            float fill = Math.max(th, tw * s.position());
            c.roundRect(0xFFFFFFFF, tx, ty, fill, th, th / 2);
            c.shadow(0x24000000, 4, 0, 1, tx + fill - th, ty, th, th, th / 2);
            c.circle(0xFFFFFFFF, tx + fill - th / 2, ty + th / 2, th / 2);
            PaneOsArt.symbol(c, glyph, tx + 15, ty + th / 2, 16, 0xFF6E6E73);
        });
    }
}
