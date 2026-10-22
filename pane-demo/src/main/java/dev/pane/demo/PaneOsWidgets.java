package dev.pane.demo;

import dev.pane.demo.PaneOsArt.Look;
import dev.pane.ui.*;
import java.awt.Font;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Desktop widgets. Glass and everything that changes rarely are painted into the desktop cache;
 * the clock hands and pointer targets are painted live over it, so a second tick repaints one dial.
 */
final class PaneOsWidgets {
    static final float S = 164, GAP = 16, X = 20, Y = PaneOsShowcase.MENU_H + 18;
    static final String[] REMINDERS = {"Pick up oat milk", "Call Sam about Saturday", "Ship PaneOS"};

    private PaneOsWidgets() { }

    static String reminderKey(UiContext ui) {
        var key = new StringBuilder();
        for (int i = 0; i < REMINDERS.length; i++) key.append(ui.booleanValue("rem:" + i, i == 2) ? '1' : '0');
        return key.toString();
    }

    private static boolean fits(int row, float h) { return Y + row * (S + GAP) + S <= h - 110; }

    private static UiRect cell(int column, int row, int span) {
        return new UiRect(X + column * (S + GAP), Y + row * (S + GAP), span * S + (span - 1) * GAP, S);
    }

    private static int ink(Look look) { return look.text(); }

    private static void plate(UiCanvas g, Look look, UiRect r) {
        g.liquidGlass(r.x(), r.y(), r.width(), r.height(), 24,
            look.glass().withTint(look.dark() ? 0x6B1C1C1E : 0x6BFFFFFF).withShadow(0x26000000));
    }

    static void paintStatic(UiCanvas g, UiContext ui, Look look, float h) {
        if (fits(0, h)) { clockFace(g, look, cell(0, 0, 1)); calendar(g, look, cell(1, 0, 1)); }
        if (fits(1, h)) weather(g, look, cell(0, 1, 2));
        if (fits(2, h)) { reminders(g, ui, look, cell(0, 2, 1)); nowPlaying(g, ui, look, cell(1, 2, 1)); }
        if (fits(3, h)) batteries(g, look, cell(0, 3, 2));
    }

    static void paintLive(UiContext ui, Look look, float h) {
        if (fits(0, h)) {
            UiRect clock = cell(0, 0, 1);
            hands(ui.canvas(), look, clock);
            ui.refreshAfter(1000 - System.currentTimeMillis() % 1000 + 5, clock.inset(14));
            UiRect calendar = cell(1, 0, 1);
            ui.paintedButton("w:calendar", "Calendar", "open:calendar", calendar.x(), calendar.y(), calendar.width(), calendar.height(), 24, true, (c, s) -> { });
        }
        if (fits(1, h)) {
            UiRect weather = cell(0, 1, 2);
            ui.tooltip("w:weather", "Lake Tahoe · Partly Cloudy", weather.x(), weather.y(), weather.width(), weather.height());
        }
        if (fits(2, h)) {
            UiRect r = cell(0, 2, 1);
            for (int i = 0; i < REMINDERS.length; i++) {
                float rowY = r.y() + 46 + i * 36;
                ui.paintedButton("w:rem:" + i, REMINDERS[i], "rem:" + i, r.x() + 8, rowY - 4, r.width() - 16, 32, 8, true, (c, s) -> {
                    if (s.hovered()) c.roundRect(look.hover(), r.x() + 8, rowY - 4, r.width() - 16, 32, 8);
                });
            }
            UiRect m = cell(1, 2, 1);
            boolean playing = ui.booleanValue("music:playing", false);
            ui.paintedButton("w:music:play", playing ? "Pause" : "Play", "music:play", m.right() - 50, m.bottom() - 50, 36, 36, 18, true, (c, s) -> {
                if (s.hovered()) c.circle(look.hover(), m.right() - 32, m.bottom() - 32, 18);
            });
            ui.paintedButton("w:music:open", "Music", "open:music", m.x() + 14, m.y() + 14, 64, 64, 12, true, (c, s) -> { });
        }
    }

    // ------------------------------------------------------------------ clock

    private static void clockFace(UiCanvas g, Look look, UiRect r) {
        plate(g, look, r);
        float cx = r.centerX(), cy = r.centerY(), radius = r.width() / 2 - 14;
        g.circle(look.dark() ? 0xFF1C1C1E : 0xFFFFFFFF, cx, cy, radius);
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6 - 90);
            boolean hour = i % 5 == 0;
            float inner = radius - (hour ? 9 : 5);
            g.line(hour ? ink(look) : look.tertiary(), hour ? 1.6F : .8F, cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner,
                cx + (float) Math.cos(a) * (radius - 3), cy + (float) Math.sin(a) * (radius - 3));
        }
        for (int i = 1; i <= 12; i++) {
            double a = Math.toRadians(i * 30 - 90);
            g.centeredText(Integer.toString(i), cx + (float) Math.cos(a) * (radius - 21), cy + (float) Math.sin(a) * (radius - 21),
                13, ink(look), Font.PLAIN, PaneOsArt.FONT);
        }
        g.centeredText("PANE", cx, cy + radius * .42F, 9, look.tertiary(), Font.BOLD, PaneOsArt.FONT);
    }

    private static void hands(UiCanvas g, Look look, UiRect r) {
        float cx = r.centerX(), cy = r.centerY(), radius = r.width() / 2 - 14;
        LocalTime now = LocalTime.now();
        double seconds = now.getSecond(), minutes = now.getMinute() + seconds / 60, hours = now.getHour() % 12 + minutes / 60;
        hand(g, cx, cy, hours * 30, radius * .5F, 4.2F, ink(look));
        hand(g, cx, cy, minutes * 6, radius * .78F, 3, ink(look));
        double a = Math.toRadians(seconds * 6 - 90);
        g.line(PaneOsArt.ORANGE, 1.2F, cx - (float) Math.cos(a) * 12, cy - (float) Math.sin(a) * 12,
            cx + (float) Math.cos(a) * (radius - 6), cy + (float) Math.sin(a) * (radius - 6));
        g.circle(PaneOsArt.ORANGE, cx, cy, 3.4F);
        g.circle(look.dark() ? 0xFF1C1C1E : 0xFFFFFFFF, cx, cy, 1.4F);
    }

    private static void hand(UiCanvas g, float cx, float cy, double degrees, float length, float width, int color) {
        double a = Math.toRadians(degrees - 90);
        g.line(color, width, cx, cy, cx + (float) Math.cos(a) * length, cy + (float) Math.sin(a) * length);
    }

    // ------------------------------------------------------------------ calendar, weather

    static void calendar(UiCanvas g, Look look, UiRect r) {
        plate(g, look, r);
        LocalDate today = LocalDate.now();
        float x = r.x() + 16;
        g.text(today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ENGLISH), x, r.y() + 14, 11,
            0xFFFF3B30, Font.BOLD, PaneOsArt.FONT);
        g.text(Integer.toString(today.getDayOfMonth()), x - 2, r.y() + 26, 40, ink(look), Font.PLAIN, PaneOsArt.FONT);
        g.roundRect(PaneOsArt.INDIGO, x, r.y() + 90, 3.5F, 30, 2);
        g.text("Design review", x + 10, r.y() + 89, 12.5F, ink(look), Font.BOLD, PaneOsArt.FONT);
        g.text("10:00 – 11:00 AM", x + 10, r.y() + 106, 11.5F, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        g.roundRect(PaneOsArt.ORANGE, x, r.y() + 128, 3.5F, 22, 2);
        g.text("Lunch with Ana", x + 10, r.y() + 131, 12.5F, ink(look), Font.BOLD, PaneOsArt.FONT);
    }

    static void weather(UiCanvas g, Look look, UiRect r) {
        plate(g, look, r);
        float x = r.x() + 18;
        g.text("Lake Tahoe", x, r.y() + 14, 14, ink(look), Font.BOLD, PaneOsArt.FONT);
        float ax = x + g.textWidth("Lake Tahoe", 14, Font.BOLD, PaneOsArt.FONT) + 6, ay = r.y() + 17;
        g.polygon(ink(look), ax, ay + 6, ax + 10, ay, ax + 6.5F, ay + 10, ax + 5, ay + 6.5F);
        g.text("18°", x - 2, r.y() + 30, 44, ink(look), Font.PLAIN, PaneOsArt.FONT);
        PaneOsArt.symbol(g, "sun-cloud", r.right() - 40, r.y() + 32, 34, look.dark() ? 0xFFE5E5EA : 0xFFFFFFFF);
        float right = r.right() - 18;
        String condition = "Partly Cloudy", range = "H:21°  L:9°";
        g.text(condition, right - g.textWidth(condition, 12.5F, Font.BOLD, PaneOsArt.FONT), r.y() + 56, 12.5F, ink(look), Font.BOLD, PaneOsArt.FONT);
        g.text(range, right - g.textWidth(range, 12.5F, Font.PLAIN, PaneOsArt.FONT), r.y() + 73, 12.5F, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        String[] hours = {"Now", "11AM", "12PM", "1PM", "2PM", "3PM"};
        String[] icons = {"sun-cloud", "sun", "sun", "sun-cloud", "cloud", "cloud"};
        int[] temps = {18, 19, 21, 21, 20, 18};
        float step = (r.width() - 36) / hours.length;
        for (int i = 0; i < hours.length; i++) {
            float cx = x + step * i + step / 2;
            g.centeredText(hours[i], cx, r.y() + 106, 11, look.secondary(), Font.BOLD, PaneOsArt.FONT);
            int tone = icons[i].equals("sun") ? PaneOsArt.YELLOW : look.dark() ? 0xFFE5E5EA : 0xFFFFFFFF;
            PaneOsArt.symbol(g, icons[i], cx, r.y() + 126, 20, tone);
            g.centeredText(temps[i] + "°", cx, r.y() + 147, 13, ink(look), Font.PLAIN, PaneOsArt.FONT);
        }
    }

    // ------------------------------------------------------------------ reminders, music, batteries

    private static void reminders(UiCanvas g, UiContext ui, Look look, UiRect r) {
        plate(g, look, r);
        int open = 0;
        for (int i = 0; i < REMINDERS.length; i++) if (!ui.booleanValue("rem:" + i, i == 2)) open++;
        g.circle(look.accent(), r.x() + 26, r.y() + 26, 11);
        PaneOsArt.symbol(g, "list", r.x() + 26, r.y() + 26, 13, 0xFFFFFFFF);
        g.text(Integer.toString(open), r.right() - 16 - g.textWidth(Integer.toString(open), 24, Font.BOLD, PaneOsArt.FONT), r.y() + 10, 24,
            look.accent(), Font.BOLD, PaneOsArt.FONT);
        for (int i = 0; i < REMINDERS.length; i++) {
            boolean done = ui.booleanValue("rem:" + i, i == 2);
            float rowY = r.y() + 46 + i * 36, cx = r.x() + 26, cy = rowY + 12;
            if (done) {
                g.circle(look.accent(), cx, cy, 8.5F);
                PaneOsArt.symbol(g, "check", cx, cy, 11, 0xFFFFFFFF);
            } else g.strokeCircle(look.tertiary(), 1.4F, cx, cy, 8);
            String label = g.ellipsize(REMINDERS[i], r.width() - 62, 12.5F, Font.PLAIN, PaneOsArt.FONT);
            g.text(label, r.x() + 42, cy - 8, 12.5F, done ? look.tertiary() : ink(look), Font.PLAIN, PaneOsArt.FONT);
            if (i < REMINDERS.length - 1) g.line(look.separator(), .7F, r.x() + 42, rowY + 30, r.right() - 14, rowY + 30);
        }
    }

    private static void nowPlaying(UiCanvas g, UiContext ui, Look look, UiRect r) {
        plate(g, look, r);
        int track = (int) ui.numberValue("music:track", 0);
        var song = PaneOsApps.TRACKS[track];
        float ax = r.x() + 14, ay = r.y() + 14;
        PaneOsApps.albumArt(g, ax, ay, 64, track);
        g.text(g.ellipsize(song[0], r.width() - 28, 13, Font.BOLD, PaneOsArt.FONT), ax, ay + 76, 13, ink(look), Font.BOLD, PaneOsArt.FONT);
        g.text(g.ellipsize(song[1], r.width() - 70, 12, Font.PLAIN, PaneOsArt.FONT), ax, ay + 94, 12, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        boolean playing = ui.booleanValue("music:playing", false);
        PaneOsArt.symbol(g, playing ? "pause" : "play", r.right() - 32, r.bottom() - 32, 20, ink(look));
        if (playing) for (int i = 0; i < 4; i++) {
            float bar = 6 + (i * 7 % 11);
            g.roundRect(PaneOsArt.RED, r.right() - 44 + i * 6, r.y() + 30 - bar, 3.5F, bar, 1.5F);
        }
    }

    private static void batteries(UiCanvas g, Look look, UiRect r) { batteries(g, look, r, 4); }

    /** The first {@code count} devices' charge rings. */
    static void batteries(UiCanvas g, Look look, UiRect r, int count) {
        plate(g, look, r);
        String[] names = {"PaneBook", "Headphones", "Keyboard", "Mouse"};
        int[] levels = {84, 62, 91, 18};
        String[] glyphs = {"battery", "note", "grid", "person"};
        float step = r.width() / count;
        for (int i = 0; i < count; i++) {
            float cx = r.x() + step * i + step / 2, cy = r.y() + 66, radius = Math.min(30, step / 2 - 12);
            int color = levels[i] <= 20 ? 0xFFFF453A : 0xFF30D158;
            g.strokeCircle(look.dark() ? 0x33FFFFFF : 0x1F000000, 5.5F, cx, cy, radius);
            g.path().arc(cx, cy, radius, -90, 360 * levels[i] / 100F).stroke(color, 5.5F);
            PaneOsArt.symbol(g, glyphs[i], cx, cy, 22, ink(look));
            g.centeredText(levels[i] + "%", cx, r.y() + 118, 13.5F, ink(look), Font.BOLD, PaneOsArt.FONT);
            g.centeredText(names[i], cx, r.y() + 138, 11, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        }
    }

    // ------------------------------------------------------------------ desktop items

    /** Desktop icons in a column down the right edge; each has its own context menu. */
    static void desktopItems(UiContext ui, Look look, float w, float h) {
        var keys = new java.util.ArrayList<String>();
        var names = new java.util.ArrayList<String>();
        var kinds = new java.util.ArrayList<String>();
        keys.add("projects"); names.add("Projects"); kinds.add("folder");
        keys.add("notes"); names.add("Field Notes"); kinds.add("doc");
        String shot = ui.stringValue("desk:shot", "");
        if (!shot.isEmpty()) { keys.add("shot"); names.add(shot); kinds.add("photo"); }
        int folders = (int) ui.numberValue("desk:folders", 0);
        for (int i = 1; i <= folders; i++) { keys.add("folder" + i); names.add(i == 1 ? "untitled folder" : "untitled folder " + i); kinds.add("folder"); }
        String selected = ui.stringValue("desk:sel", "");
        var style = PaneOsShowcase.menuStyle(look);
        int slot = 0;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i), kind = kinds.get(i), name = names.get(i), id = "desk:item:" + key;
            if (ui.booleanValue("desk:trashed:" + key, false)) continue;
            float x = w - 112, y = PaneOsShowcase.MENU_H + 18 + slot++ * 106;
            if (y + 96 > h - 110) break;
            boolean isSelected = selected.equals(key);
            ui.paintedButton(id, name, "desk:" + key, x, y, 96, 96, 10, true, (c, s) -> {
                if (isSelected) c.roundRect(0x33000000, x + 18, y + 2, 60, 60, 10);
                desktopIcon(c, kind, x + 22, y + 6, 52);
                String label = c.ellipsize(name, 92, 12, Font.BOLD, PaneOsArt.FONT);
                float tw = c.textWidth(label, 12, Font.BOLD, PaneOsArt.FONT);
                if (isSelected) c.roundRect(look.accent(), x + 48 - tw / 2 - 5, y + 66, tw + 10, 18, 5);
                c.text(label, x + 48 - tw / 2 + .6F, y + 67.6F, 12, 0x66000000, Font.BOLD, PaneOsArt.FONT);
                c.text(label, x + 48 - tw / 2, y + 67, 12, 0xFFFFFFFF, Font.BOLD, PaneOsArt.FONT);
            });
            ui.contextMenu(id, java.util.List.of(
                new UiMenuItem("open", "Open", "desk:open:" + key).shortcut("⌘O"),
                new UiMenuItem("info", "Get Info", "desk:info:" + key).shortcut("⌘I"),
                new UiMenuItem("rename", "Rename", "desk:rename").enabled(false),
                new UiMenuItem("trash", "Move to Trash", "desk:trash:" + key).shortcut("⌘⌫").startsGroup()), style);
        }
    }

    static void desktopIcon(UiCanvas g, String kind, float x, float y, float s) {
        switch (kind) {
            case "folder" -> {
                g.shadow(0x33000000, 4, 0, 2, x, y + s * .16F, s, s * .7F, s * .08F);
                g.roundRect(0xFF5AB4F5, x, y + s * .1F, s * .42F, s * .2F, s * .06F);
                g.gradientRect(0xFF8FD0FF, 0xFF4AA8F0, 90, x, y + s * .18F, s, s * .66F, s * .08F);
                g.line(0x66FFFFFF, 1, x + s * .04F, y + s * .22F, x + s * .96F, y + s * .22F);
            }
            case "photo" -> {
                g.shadow(0x33000000, 4, 0, 2, x + s * .1F, y + s * .14F, s * .8F, s * .7F, 3);
                g.fill(0xFFFFFFFF, x + s * .08F, y + s * .12F, s * .84F, s * .72F);
                g.gradientRect(0xFF6FB6FF, 0xFF1C4FB8, 90, x + s * .12F, y + s * .16F, s * .76F, s * .64F, 0);
                g.path().moveTo(x + s * .12F, y + s * .6F).curveTo(x + s * .4F, y + s * .44F, x + s * .6F, y + s * .62F, x + s * .88F, y + s * .5F)
                    .stroke(0xCCFFFFFF, 1.4F);
            }
            default -> {
                g.shadow(0x33000000, 4, 0, 2, x + s * .18F, y + s * .04F, s * .64F, s * .86F, 3);
                g.path().moveTo(x + s * .18F, y + s * .04F).lineTo(x + s * .64F, y + s * .04F).lineTo(x + s * .82F, y + s * .22F)
                    .lineTo(x + s * .82F, y + s * .9F).lineTo(x + s * .18F, y + s * .9F).close().fill(0xFFFFFFFF);
                g.polygon(0xFFDADDE3, x + s * .64F, y + s * .04F, x + s * .82F, y + s * .22F, x + s * .64F, y + s * .22F);
                for (int i = 0; i < 5; i++) g.line(0xFFB8BEC8, 1.2F, x + s * .28F, y + s * (.36F + i * .1F), x + s * (i == 4 ? .55F : .72F), y + s * (.36F + i * .1F));
            }
        }
    }
}
