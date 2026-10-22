package dev.pane.demo;

import dev.pane.demo.PaneOsArt.Look;
import dev.pane.ui.*;
import java.awt.Font;

/** Notification Center beside the clock and the full-screen Apps grid. */
final class PaneOsOverlays {
    private PaneOsOverlays() { }

    // ------------------------------------------------------------------ notification center

    static void notificationCenter(UiContext ui, Look look, float w, float h) {
        boolean open = ui.booleanValue("nc", false);
        float pw = 360, px = w - pw - 10, py = PaneOsShowcase.MENU_H + 6;
        UiRect bounds = new UiRect(px - 40, py - 10, pw + 50, h - py);
        ui.repaintScope("nc", new UiRect(w / 2, 0, w / 2, h));
        ui.gpuTransition("nc", bounds, java.util.List.of(look, ui.stringValue("notice:history", "")), open,
            new UiTransform(pw + 20, 0, 1, 1, 0, 0), 340, UiEasing.EASE_OUT, inner -> centerPanel(inner, look, px, py, pw, h));
    }

    private static void centerPanel(UiContext ui, Look look, float x, float y, float width, float h) {
        UiCanvas g = ui.canvas();
        String history = ui.stringValue("notice:history", "");
        String[] lines = history.isEmpty() ? new String[0] : history.split("\n");
        int shown = 0;
        float cy = y;
        g.text("Notifications", x + 6, cy + 4, 15, 0xFFFFFFFF, Font.BOLD, PaneOsArt.FONT);
        if (lines.length > 0) {
            float bx = x + width - 64, by = cy;
            ui.paintedButton("nc:clear", "Clear", "nc:clear", bx, by, 64, 24, 12, true, (c, s) -> {
                c.liquidGlass(bx, by, 64, 24, 12, look.clear().withTint(look.dark() ? 0x33262628 : 0x66FFFFFF).withShadow(0));
                c.centeredText("Clear", bx + 32, by + 12, 12, look.text(), Font.BOLD, PaneOsArt.FONT);
            });
        }
        cy += 34;
        for (int i = lines.length - 1; i >= 0 && shown < 4; i--) {
            String[] parts = lines[i].split("\t");
            if (parts.length < 4) continue;
            g.liquidGlass(x, cy, width, 70, 20, look.glass().withTint(look.dark() ? 0x80262628 : 0x99FFFFFF).withShadow(0x2A000000));
            PaneOsArt.icon(g, parts[2], x + 14, cy + 15, 40);
            g.text(g.ellipsize(parts[0], width - 140, 13.5F, Font.BOLD, PaneOsArt.FONT), x + 66, cy + 15, 13.5F, look.text(), Font.BOLD, PaneOsArt.FONT);
            String age = age(Long.parseLong(parts[3]));
            g.text(age, x + width - 14 - g.textWidth(age, 12, Font.PLAIN, PaneOsArt.FONT), cy + 16, 12, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
            g.text(g.ellipsize(parts[1], width - 82, 12.5F, Font.PLAIN, PaneOsArt.FONT), x + 66, cy + 36, 12.5F, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
            cy += 80;
            shown++;
        }
        if (shown == 0) {
            g.liquidGlass(x, cy, width, 56, 20, look.glass().withTint(look.dark() ? 0x80262628 : 0x99FFFFFF).withShadow(0x2A000000));
            g.centeredText("No Notifications", x + width / 2, cy + 28, 13.5F, look.secondary(), Font.BOLD, PaneOsArt.FONT);
            cy += 66;
        }
        ui.refreshAfter(60_000, new UiRect(x, y, width, cy - y));
        cy += 10;
        g.text("Today", x + 6, cy, 15, 0xFFFFFFFF, Font.BOLD, PaneOsArt.FONT);
        cy += 28;
        if (cy + 164 < h - 100) PaneOsWidgets.weather(g, look, new UiRect(x + 8, cy, width - 16, 164));
        cy += 180;
        if (cy + 164 < h - 100) {
            PaneOsWidgets.calendar(g, look, new UiRect(x + 8, cy, 164, 164));
            PaneOsWidgets.batteries(g, look, new UiRect(x + 188, cy, 164, 164), 2);
        }
    }

    private static String age(long at) {
        long minutes = (System.currentTimeMillis() - at) / 60_000;
        return minutes < 1 ? "now" : minutes < 60 ? minutes + "m ago" : minutes / 60 + "h ago";
    }

    // ------------------------------------------------------------------ apps grid

    static void appsGrid(UiContext ui, Look look, float w, float h) {
        boolean open = ui.booleanValue("apps", false);
        ui.gpuTransition("apps", new UiRect(0, 0, w, h), look, open, new UiTransform(0, 0, 1.06F, 1.06F, 0, 0), 300, UiEasing.EASE_OUT, inner -> {
            UiCanvas g = inner.canvas();
            g.backdropBlur(22, 0, 0, w, h);
            g.fill(0x40000000, 0, 0, w, h);
            grid(inner, look, w, h);
        });
    }

    private static void grid(UiContext ui, Look look, float w, float h) {
        UiCanvas g = ui.canvas();
        ui.hit("apps:bg", "apps", 0, 0, w, h);
        float sw = 260, sx = (w - sw) / 2, sy = PaneOsShowcase.MENU_H + 40;
        g.liquidGlass(sx, sy, sw, 32, 16, look.clear().withTint(0x26FFFFFF).withShadow(0));
        PaneOsArt.symbol(g, "search", sx + 18, sy + 16, 13, 0xCCFFFFFF);
        g.text("Search", sx + 32, sy + 8, 13, 0xB3FFFFFF, Font.PLAIN, PaneOsArt.FONT);
        var ids = new java.util.ArrayList<String>();
        var names = new java.util.ArrayList<String>();
        for (int i = 0; i < PaneOsShowcase.DOCK.length; i++) {
            String id = PaneOsShowcase.DOCK[i];
            if (id.equals("|") || id.equals("launch") || id.equals("trash") || id.equals("downloads")) continue;
            ids.add(id); names.add(PaneOsShowcase.DOCK_NAMES[i]);
        }
        int columns = 7;
        float cell = Math.min(150, (w - 240) / columns), icon = Math.min(88, cell * .62F);
        float gx = (w - columns * cell) / 2, gy = sy + 90;
        for (int i = 0; i < ids.size(); i++) {
            float cx = gx + i % columns * cell + cell / 2, top = gy + i / columns * (icon + 64);
            String id = ids.get(i), name = names.get(i);
            ui.paintedButton("apps:" + id, name, "open:" + id, cx - icon / 2 - 6, top - 6, icon + 12, icon + 40, 16, true, (c, s) -> {
                PaneOsArt.icon(c, id, cx - icon / 2, top, icon);
                if (s.pressed()) PaneOsArt.squircle(c, cx - icon / 2, top, icon).fill(0x40000000);
                c.centeredText(name, cx + .6F, top + icon + 17.6F, 13, 0x66000000, Font.PLAIN, PaneOsArt.FONT);
                c.centeredText(name, cx, top + icon + 17, 13, 0xFFFFFFFF, Font.PLAIN, PaneOsArt.FONT);
            });
        }
    }
}
