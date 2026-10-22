package dev.pane.demo;

import dev.pane.demo.PaneOsArt.Look;
import dev.pane.demo.PaneOsShowcase.App;
import dev.pane.demo.PaneOsShowcase.Caches;
import dev.pane.ui.*;
import java.awt.Font;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** The contents of every PaneOS window. Ids are prefixed with the app id so a press raises its window. */
final class PaneOsApps {
    static final String[][] TRACKS = {
        {"Glass Harbor", "Northbound", "3:42"}, {"Silver Line", "Mara Quell", "4:05"}, {"Low Sun Over Water", "The Tidewells", "2:58"},
        {"Pine & Static", "Northbound", "3:31"}, {"Afterlight", "Juniper Vale", "5:12"}, {"Open Sky", "Mara Quell", "3:19"}};
    private static final int[][] ART = {{0xFF0E7BF2, 0xFF63E6E2}, {0xFF5E5CE6, 0xFFBF5AF2}, {0xFFFF9F0A, 0xFFFF453A},
        {0xFF1E6B4A, 0xFF9BE15D}, {0xFF2C2C54, 0xFFFFB86B}, {0xFF40C8E0, 0xFF0A84FF}};
    private static final float SIDEBAR = 196;

    private PaneOsApps() { }

    static void paint(UiContext ui, Caches caches, Look look, App app, UiRect f, boolean active) {
        switch (app.id()) {
            case "files" -> files(ui, look, f);
            case "notes" -> notes(ui, look, f);
            case "calculator" -> calculator(ui, look, f);
            case "terminal" -> terminal(ui, look, f);
            case "settings" -> settings(ui, caches, look, f);
            case "music" -> music(ui, look, f);
            case "photos" -> photos(ui, caches, look, f);
            case "messages" -> messages(ui, look, f);
            case "maps" -> maps(ui, caches, look, f);
            case "compass" -> compass(ui, look, f);
            case "calendar" -> calendar(ui, look, f);
            default -> body(ui.canvas(), look, f, 18, 0);
        }
    }

    // ------------------------------------------------------------------ shared chrome

    /** Window material. With a sidebar, the gutter frosts the desktop and the sidebar floats on it as glass. */
    static void body(UiCanvas g, Look look, UiRect f, float r, float sidebar) {
        if (sidebar <= 0) { g.roundRect(look.window(), f.x(), f.y(), f.width(), f.height(), r); return; }
        float split = f.x() + sidebar + 16;
        g.withRoundedClip(f.x(), f.y(), f.width(), f.height(), r, () -> g.backdropBlur(14, f.x(), f.y(), split - f.x(), f.height()));
        g.roundRect(look.sidebarTint(), f.x(), f.y(), split - f.x() + r, f.height(), r);
        g.roundRect(look.content(), split, f.y(), f.right() - split, f.height(), 0, r, r, 0);
        g.liquidGlass(f.x() + 8, f.y() + 8, sidebar, f.height() - 16, r - 6,
            look.clear().withTint(look.dark() ? 0x332C2C2E : 0x4DFFFFFF).withShadow(0x1A000000));
    }

    static float contentX(UiRect f) { return f.x() + SIDEBAR + 16; }

    static void sectionLabel(UiCanvas g, Look look, String label, float x, float y) {
        g.text(label, x, y, 11, look.tertiary(), Font.BOLD, PaneOsArt.FONT);
    }

    static void sideItem(UiContext ui, Look look, String id, String label, String glyph, int glyphColor, float x, float y, float w,
                         boolean selected, String action) {
        ui.paintedButton(id, label, action, x, y, w, 28, 8, true, (c, s) -> {
            if (selected) c.roundRect(look.dark() ? 0x2EFFFFFF : 0x17000000, x, y, w, 28, 8);
            else if (s.hovered()) c.roundRect(look.hover(), x, y, w, 28, 8);
            PaneOsArt.symbol(c, glyph, x + 16, y + 14, 15, glyphColor);
            c.text(label, x + 32, y + 6, 13, look.text(), Font.PLAIN, PaneOsArt.FONT);
        });
    }

    static void title(UiCanvas g, Look look, String title, String subtitle, float x, float y) {
        g.text(title, x, y, 15, look.text(), Font.BOLD, PaneOsArt.FONT);
        if (subtitle != null) g.text(subtitle, x, y + 19, 11.5F, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
    }

    /** A capsule toolbar control on clear glass. */
    static void toolButton(UiContext ui, Look look, String id, String label, String glyph, float x, float y, float w, String action) {
        UiCanvas g = ui.canvas();
        g.liquidGlass(x, y, w, 32, 16, look.clear().withTint(look.dark() ? 0x1FFFFFFF : 0x99FFFFFF).withShadow(0x14000000));
        ui.paintedButton(id, label, action, x, y, w, 32, 16, true, (c, s) -> {
            if (s.hovered() || s.pressed()) c.roundRect(s.pressed() ? 0x1F000000 : look.hover(), x, y, w, 32, 16);
            PaneOsArt.symbol(c, glyph, x + w / 2, y + 16, 15, look.text());
        });
    }

    static void searchPill(UiCanvas g, Look look, float x, float y, float w, String placeholder) {
        g.liquidGlass(x, y, w, 32, 16, look.clear().withTint(look.dark() ? 0x1FFFFFFF : 0x99FFFFFF).withShadow(0x14000000));
        PaneOsArt.symbol(g, "search", x + 16, y + 16, 13, look.secondary());
        g.text(placeholder, x + 30, y + 8, 13, look.tertiary(), Font.PLAIN, PaneOsArt.FONT);
    }

    static UiInputStyle input(Look look, float size, boolean mono) {
        return UiInputStyle.defaults().font(mono ? PaneOsArt.MONO : PaneOsArt.FONT, size, Font.PLAIN).padding(12, 7).lineHeight(size * 1.45F)
            .appearance(UiInputAppearance.defaults().background(0x00000000).border(0x00000000).focus(0x00000000)
                .text(look.text()).placeholder(look.tertiary()).caret(look.accent()).selection(UiCanvas.alpha(look.accent(), 80)));
    }

    /** Accent switch in the system style, animating its knob. */
    static void switchControl(UiContext ui, Look look, String id, String key, boolean fallback, float x, float y) {
        boolean on = ui.booleanValue(key, fallback);
        float t = ui.animate(id + ":knob", on ? 1F : 0F, 200, UiEasing.EASE_OUT, new UiRect(x - 4, y - 4, 48, 30));
        ui.paintedButton(id, on ? "On" : "Off", "toggle:" + key + "|" + fallback, x, y, 40, 22, 11, true, (c, s) -> {
            c.roundRect(UiCanvas.mix(look.dark() ? 0xFF48484A : 0xFFE5E5EA, look.accent(), t), x, y, 40, 22, 11);
            c.shadow(0x33000000, 3, 0, 1, x + 2 + t * 18, y + 2, 18, 18, 9);
            c.circle(0xFFFFFFFF, x + 11 + t * 18, y + 11, 9);
        });
    }

    /** Thin system slider: a track, the accent fill and a white knob. */
    static double systemSlider(UiContext ui, Look look, String id, String key, float x, float y, float w, double min, double max, double fallback) {
        return ui.paintedSlider(id, key, x, y, w, 22, min, max, fallback, (c, s) -> {
            c.roundRect(look.dark() ? 0x40FFFFFF : 0x1F000000, x, y + 9, w, 4, 2);
            c.roundRect(look.accent(), x, y + 9, w * s.position(), 4, 2);
            float kx = x + Math.max(10, Math.min(w - 10, w * s.position()));
            c.shadow(0x40000000, 4, 0, 1.5F, kx - 10, y + 1, 20, 20, 10);
            c.circle(s.pressed() ? 0xFFF2F2F7 : 0xFFFFFFFF, kx, y + 11, 10);
        });
    }

    static void group(UiCanvas g, Look look, float x, float y, float w, float h) {
        g.roundRect(look.dark() ? 0x0FFFFFFF : 0xFFFFFFFF, x, y, w, h, 12);
        g.strokeRoundRect(look.separator(), .7F, x, y, w, h, 12);
    }

    static void row(UiCanvas g, Look look, String label, String value, float x, float y, float w, boolean divider) {
        g.text(label, x + 14, y + 11, 13, look.text(), Font.PLAIN, PaneOsArt.FONT);
        if (value != null) g.text(value, x + w - 14 - g.textWidth(value, 13, Font.PLAIN, PaneOsArt.FONT), y + 11, 13, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        if (divider) g.line(look.separator(), .7F, x + 14, y + 38, x + w - 14, y + 38);
    }

    // ------------------------------------------------------------------ actions

    static void action(UiScreen screen, String verb, String arg) {
        switch (verb) {
            case "toggle" -> {
                int bar = arg.lastIndexOf('|');
                String key = arg.substring(0, bar);
                screen.setValue(key, !screen.booleanValue(key, Boolean.parseBoolean(arg.substring(bar + 1))));
            }
            case "select" -> {
                int eq = arg.indexOf('=');
                screen.setValue(arg.substring(0, eq), Double.parseDouble(arg.substring(eq + 1)));
            }
            case "calc" -> calculate(screen, arg);
            case "photos" -> screen.setValue("photos:sel", Double.parseDouble(arg));
            case "msg" -> screen.setValue("msg:sel", Double.parseDouble(arg));
            case "compass" -> screen.setValue("compass:page", arg);
            case "place" -> screen.setValue("maps:sel", Double.parseDouble(arg));
            case "newnote" -> {
                int count = (int) screen.numberValue("notes:count", NOTES.length);
                screen.setValue("note:" + count, "New Note\n");
                screen.setValue("notes:count", count + 1D);
                screen.setValue("notes:sel", (double) count);
                screen.requestFocus("notes:editor");
            }
            default -> { }
        }
    }

    static void submit(UiScreen screen, String key, String value) {
        if (key.equals("terminal:input")) terminalCommand(screen, value);
        if (key.startsWith("msg:draft")) {
            if (value.isBlank()) return;
            int chat = (int) screen.numberValue("msg:sel", 0);
            String log = screen.stringValue("msg:sent:" + chat, "");
            screen.setValue("msg:sent:" + chat, log + value.strip().replace('\n', ' ') + "\n");
            screen.setValue(key, "");
            screen.setValue("msg:reply:" + chat, (double) System.currentTimeMillis() + 1800);
        }
    }

    // ------------------------------------------------------------------ files

    private static final String[] FILE_SIDE = {"AirDrop", "Recents", "Applications", "Desktop", "Documents", "Downloads",
        "PaneOS HD", "Network", "Red", "Trash"};
    private static final String[] FILE_GLYPHS = {"airdrop", "clock", "grid", "sidebar", "doc", "folder", "lock", "globe", "heart", "trash"};

    private static void files(UiContext ui, Look look, UiRect f) {
        UiCanvas g = ui.canvas();
        body(g, look, f, 18, SIDEBAR);
        int side = (int) ui.numberValue("files:side", 4);
        float sx = f.x() + 16, sy = f.y() + 52, sw = SIDEBAR - 16;
        sectionLabel(g, look, "Favourites", sx + 8, sy);
        for (int i = 0; i < FILE_SIDE.length; i++) {
            float y = sy + 18 + i * 30 + (i >= 6 ? 26 : 0) + (i >= 8 ? 26 : 0);
            if (i == 6) sectionLabel(g, look, "Locations", sx + 8, y - 22);
            if (i == 8) sectionLabel(g, look, "Tags", sx + 8, y - 22);
            int tint = i == 8 ? PaneOsArt.RED : look.accent();
            sideItem(ui, look, "files:side:" + i, FILE_SIDE[i], FILE_GLYPHS[i], tint, sx, y, sw, side == i, "select:files:side=" + i);
        }
        float cx = contentX(f), cw = f.right() - cx;
        toolButton(ui, look, "files:back", "Back", "chevron-left", cx + 12, f.y() + 12, 36, "select:files:side=" + Math.max(0, side - 1));
        toolButton(ui, look, "files:fwd", "Forward", "chevron-right", cx + 52, f.y() + 12, 36, "select:files:side=" + Math.min(9, side + 1));
        title(g, look, FILE_SIDE[side], null, cx + 102, f.y() + 18);
        boolean list = ui.booleanValue("files:list", false);
        toolButton(ui, look, "files:view", list ? "Show as Icons" : "Show as List", list ? "grid" : "list", f.right() - 252, f.y() + 12, 40,
            "toggle:files:list|false");
        searchPill(g, look, f.right() - 200, f.y() + 12, 186, "Search");
        String[][] items = fileItems(side);
        int selected = (int) ui.numberValue("files:sel", -1);
        float top = f.y() + 62;
        if (list) {
            g.text("Name", cx + 20, top, 11.5F, look.secondary(), Font.BOLD, PaneOsArt.FONT);
            g.text("Kind", cx + cw * .62F, top, 11.5F, look.secondary(), Font.BOLD, PaneOsArt.FONT);
            for (int i = 0; i < items.length; i++) {
                float y = top + 20 + i * 28;
                if (y + 28 > f.bottom() - 30) break;
                int index = i;
                String[] item = items[i];
                ui.paintedButton("files:item:" + i, item[0], "select:files:sel=" + i, cx + 10, y, cw - 20, 26, 6, true, (c, s) -> {
                    if (selected == index) c.roundRect(look.accent(), cx + 10, y, cw - 20, 26, 6);
                    else if (index % 2 == 1) c.roundRect(look.hover(), cx + 10, y, cw - 20, 26, 6);
                    PaneOsWidgets.desktopIcon(c, item[1], cx + 18, y + 3, 20);
                    int ink = selected == index ? 0xFFFFFFFF : look.text();
                    c.text(item[0], cx + 46, y + 5, 13, ink, Font.PLAIN, PaneOsArt.FONT);
                    c.text(item[2], cx + cw * .62F, y + 5, 13, selected == index ? 0xD9FFFFFF : look.secondary(), Font.PLAIN, PaneOsArt.FONT);
                });
            }
        } else {
            int columns = Math.max(1, (int) ((cw - 24) / 104));
            for (int i = 0; i < items.length; i++) {
                float x = cx + 16 + i % columns * 104, y = top + i / columns * 112;
                if (y + 104 > f.bottom() - 30) break;
                int index = i;
                String[] item = items[i];
                ui.paintedButton("files:item:" + i, item[0], "select:files:sel=" + i, x, y, 96, 104, 10, true, (c, s) -> {
                    if (selected == index) c.roundRect(look.dark() ? 0x2EFFFFFF : 0x17000000, x + 14, y, 68, 68, 10);
                    PaneOsWidgets.desktopIcon(c, item[1], x + 20, y + 6, 56);
                    String label = c.ellipsize(item[0], 92, 12, Font.PLAIN, PaneOsArt.FONT);
                    float tw = c.textWidth(label, 12, Font.PLAIN, PaneOsArt.FONT);
                    if (selected == index) c.roundRect(look.accent(), x + 48 - tw / 2 - 5, y + 74, tw + 10, 18, 5);
                    c.text(label, x + 48 - tw / 2, y + 75, 12, selected == index ? 0xFFFFFFFF : look.text(), Font.PLAIN, PaneOsArt.FONT);
                });
            }
        }
        g.line(look.separator(), .7F, cx, f.bottom() - 28, f.right(), f.bottom() - 28);
        String status = items.length + " items, 248.6 GB available";
        g.centeredText(status, cx + cw / 2, f.bottom() - 14, 11.5F, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
    }

    private static String[][] fileItems(int side) {
        return switch (side) {
            case 2 -> new String[][] {{"Calculator", "doc", "Application"}, {"Compass", "doc", "Application"}, {"Maps", "doc", "Application"},
                {"Music", "doc", "Application"}, {"Notes", "doc", "Application"}, {"Photos", "doc", "Application"}, {"Terminal", "doc", "Application"}};
            case 3 -> new String[][] {{"Projects", "folder", "Folder"}, {"Field Notes", "doc", "Text Document"}};
            case 5 -> new String[][] {{"pane-26.2.jar", "doc", "Java Archive"}, {"Roboto.ttf", "doc", "Font"}, {"tahoe-shore.png", "photo", "PNG image"},
                {"Invoice-0410.pdf", "doc", "PDF Document"}};
            case 9 -> new String[][] {{"old-draft.txt", "doc", "Text Document"}};
            default -> new String[][] {{"Projects", "folder", "Folder"}, {"Receipts", "folder", "Folder"}, {"Field Notes", "doc", "Text Document"},
                {"Itinerary.pdf", "doc", "PDF Document"}, {"Shoreline.png", "photo", "PNG image"}, {"Budget 2026", "doc", "Spreadsheet"},
                {"Sketches", "folder", "Folder"}, {"Ridge at dusk.png", "photo", "PNG image"}, {"Letters", "folder", "Folder"},
                {"Reading list", "doc", "Text Document"}, {"Pane talk.key", "doc", "Presentation"}};
        };
    }

    // ------------------------------------------------------------------ notes

    private static final String[] NOTES = {
        "Shopping list\nOat milk\nCoffee beans\nBasil\nLemons\nSourdough",
        "Trip to the lake\nLeave Friday 7am. Pack the blue kayak, two paddles and the small cooler.\n\nCampsite 14 is booked until Sunday.",
        "PaneOS ideas\n• Live widgets on the desktop\n• Glass that bends what is behind it\n• Windows that minimise into the dock",
        "Book notes\nThe best interfaces disappear. You only notice them when they fight you."};

    private static void notes(UiContext ui, Look look, UiRect f) {
        UiCanvas g = ui.canvas();
        body(g, look, f, 18, SIDEBAR);
        float sx = f.x() + 16, sy = f.y() + 52, sw = SIDEBAR - 16;
        sectionLabel(g, look, "iCloud", sx + 8, sy);
        String[] folders = {"All Notes", "Notes", "Ideas", "Recently Deleted"};
        String[] glyphs = {"folder", "folder", "folder", "trash"};
        int folder = (int) ui.numberValue("notes:folder", 0);
        for (int i = 0; i < folders.length; i++)
            sideItem(ui, look, "notes:folder:" + i, folders[i], glyphs[i], PaneOsArt.YELLOW, sx, sy + 18 + i * 30, sw, folder == i, "select:notes:folder=" + i);
        float lx = contentX(f), lw = 236;
        int count = (int) ui.numberValue("notes:count", NOTES.length), selected = (int) ui.numberValue("notes:sel", 0);
        toolButton(ui, look, "notes:new", "New Note", "compose", lx + lw - 46, f.y() + 12, 36, "newnote");
        title(g, look, "Notes", count + " notes", lx + 16, f.y() + 14);
        g.line(look.separator(), .7F, lx + lw, f.y(), lx + lw, f.bottom());
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("d/M/yy"));
        for (int i = 0; i < count; i++) {
            float y = f.y() + 62 + i * 66;
            if (y + 62 > f.bottom()) break;
            String text = ui.stringValue("note:" + i, i < NOTES.length ? NOTES[i] : "New Note\n");
            String[] lines = text.split("\n", 3);
            String preview = lines.length > 1 ? lines[1] : "No additional text";
            int index = i;
            ui.paintedButton("notes:item:" + i, lines[0], "select:notes:sel=" + i, lx + 8, y, lw - 16, 60, 10, true, (c, s) -> {
                if (selected == index) c.roundRect(UiCanvas.alpha(PaneOsArt.YELLOW, 150), lx + 8, y, lw - 16, 60, 10);
                c.text(c.ellipsize(lines[0], lw - 40, 13.5F, Font.BOLD, PaneOsArt.FONT), lx + 20, y + 9, 13.5F, look.text(), Font.BOLD, PaneOsArt.FONT);
                c.text(date, lx + 20, y + 31, 12, look.text(), Font.PLAIN, PaneOsArt.FONT);
                float px = lx + 28 + c.textWidth(date, 12, Font.PLAIN, PaneOsArt.FONT);
                c.text(c.ellipsize(preview, lx + lw - 20 - px, 12, Font.PLAIN, PaneOsArt.FONT), px, y + 31, 12, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
            });
            if (selected != i && selected != i + 1) g.line(look.separator(), .7F, lx + 20, y + 63, lx + lw - 20, y + 63);
        }
        float ex = lx + lw + 1, ew = f.right() - ex;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy 'at' h:mm a", Locale.ENGLISH));
        g.centeredText(stamp, ex + ew / 2, f.y() + 26, 11.5F, look.tertiary(), Font.PLAIN, PaneOsArt.FONT);
        ui.textArea("notes:editor", "note:" + selected, "Start typing", ex + 18, f.y() + 46, ew - 36, f.height() - 60,
            selected < NOTES.length ? NOTES[selected] : "New Note\n", 4096, input(look, 15, false));
    }

    // ------------------------------------------------------------------ calculator

    private static void calculator(UiContext ui, Look look, UiRect f) {
        UiCanvas g = ui.canvas();
        g.withRoundedClip(f.x(), f.y(), f.width(), f.height(), 22, () -> g.backdropBlur(18, f.x(), f.y(), f.width(), f.height()));
        g.roundRect(0xD91C1C1E, f.x(), f.y(), f.width(), f.height(), 22);
        String display = ui.stringValue("calc:display", "0");
        float size = display.length() > 9 ? 34 : 46;
        g.text(display, f.right() - 18 - g.textWidth(display, size, Font.PLAIN, PaneOsArt.FONT), f.y() + 96 - size, size, 0xFFFFFFFF, Font.PLAIN, PaneOsArt.FONT);
        String[][] keys = {{"⌫", "±", "%", "÷"}, {"7", "8", "9", "×"}, {"4", "5", "6", "−"}, {"1", "2", "3", "+"}, {"AC", "0", ".", "="}};
        float d = 46, gap = 10, gx = f.x() + (f.width() - 4 * d - 3 * gap) / 2, gy = f.y() + 108;
        String op = ui.stringValue("calc:op", "");
        boolean fresh = ui.booleanValue("calc:fresh", false);
        for (int r = 0; r < keys.length; r++) for (int c = 0; c < 4; c++) {
            String key = keys[r][c];
            float cx = gx + c * (d + gap) + d / 2, cy = gy + r * (d + gap) + d / 2;
            boolean operator = c == 3, function = r == 0 && c < 3;
            boolean lit = operator && key.equals(op) && fresh;
            int fill = operator ? (lit ? 0xFFFFFFFF : PaneOsArt.ORANGE) : function ? 0xFF5C5C5F : 0xFF3A3A3C;
            int ink = lit ? PaneOsArt.ORANGE : 0xFFFFFFFF;
            ui.shapedButton("calculator:" + r + c, key, "calc:" + key, g.path().arc(cx, cy, d / 2, 0, 360).close(), true, (canvas, s) -> {
                canvas.circle(s.pressed() ? UiCanvas.lighten(fill, .3F) : s.hovered() ? UiCanvas.lighten(fill, .08F) : fill, cx, cy, d / 2);
                canvas.centeredText(key, cx, cy, key.length() > 1 ? 16 : 21, ink, Font.PLAIN, PaneOsArt.FONT);
            });
        }
    }

    private static void calculate(UiScreen screen, String key) {
        String display = screen.stringValue("calc:display", "0");
        boolean fresh = screen.booleanValue("calc:fresh", false);
        switch (key) {
            case "AC" -> { screen.setValue("calc:display", "0"); screen.setValue("calc:op", ""); screen.setValue("calc:acc", 0D); }
            case "⌫" -> screen.setValue("calc:display", fresh || display.length() <= 1 ? "0" : display.substring(0, display.length() - 1));
            case "±" -> screen.setValue("calc:display", format(-parse(display)));
            case "%" -> screen.setValue("calc:display", format(parse(display) / 100));
            case "+", "−", "×", "÷", "=" -> {
                String op = screen.stringValue("calc:op", "");
                double value = parse(display), acc = screen.numberValue("calc:acc", 0);
                double result = op.isEmpty() || fresh && !key.equals("=") ? value : switch (op) {
                    case "+" -> acc + value; case "−" -> acc - value; case "×" -> acc * value;
                    default -> value == 0 ? Double.NaN : acc / value;
                };
                screen.setValue("calc:display", format(result));
                screen.setValue("calc:acc", result);
                screen.setValue("calc:op", key.equals("=") ? "" : key);
                screen.setValue("calc:fresh", true);
                return;
            }
            case "." -> { if (fresh) screen.setValue("calc:display", "0."); else if (!display.contains(".")) screen.setValue("calc:display", display + "."); }
            default -> {
                String next = fresh || display.equals("0") ? key : display.length() < 12 ? display + key : display;
                screen.setValue("calc:display", next);
            }
        }
        screen.setValue("calc:fresh", false);
    }

    private static double parse(String text) {
        try { return Double.parseDouble(text.replace(",", "")); } catch (NumberFormatException ignored) { return 0; }
    }

    private static String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "Error";
        if (value == Math.rint(value) && Math.abs(value) < 1e12) return Long.toString((long) value);
        String text = String.format(Locale.ROOT, "%.10f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return text.length() > 12 ? String.format(Locale.ROOT, "%.6g", value) : text;
    }

    // ------------------------------------------------------------------ terminal

    private static void terminal(UiContext ui, Look look, UiRect f) {
        UiCanvas g = ui.canvas();
        g.withRoundedClip(f.x(), f.y(), f.width(), f.height(), 18, () -> g.backdropBlur(16, f.x(), f.y(), f.width(), f.height()));
        g.roundRect(0xE01B1B1D, f.x(), f.y(), f.width(), f.height(), 18);
        g.centeredText("sl0oth — -zsh — 80×24", f.centerX(), f.y() + 22, 13, 0xB3EBEBF5, Font.BOLD, PaneOsArt.FONT);
        g.line(0x1FFFFFFF, .7F, f.x(), f.y() + 44, f.right(), f.y() + 44);
        String log = ui.stringValue("terminal:log", "Last login: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE MMM d HH:mm", Locale.ENGLISH))
            + " on ttys001\nType help to see what PaneOS can do.\n");
        ui.setValue("terminal:log", log);
        String[] lines = log.split("\n", -1);
        float lineHeight = 19, top = f.y() + 56, bottomLimit = f.bottom() - 46;
        int fit = (int) ((bottomLimit - top) / lineHeight);
        int start = Math.max(0, lines.length - 1 - fit);
        float y = top;
        for (int i = start; i < lines.length - 1; i++, y += lineHeight) {
            String line = lines[i];
            int color = line.startsWith("sl0oth@") ? 0xFF7EE787 : line.startsWith("  ") ? 0xFF79C0FF : 0xFFE6EDF3;
            g.text(line, f.x() + 16, y, 13, color, Font.PLAIN, PaneOsArt.MONO);
        }
        String prompt = "sl0oth@paneos ~ %";
        g.text(prompt, f.x() + 16, y, 13, 0xFF7EE787, Font.PLAIN, PaneOsArt.MONO);
        float px = f.x() + 16 + g.textWidth(prompt, 13, Font.PLAIN, PaneOsArt.MONO) + 2;
        var style = UiInputStyle.defaults().font(PaneOsArt.MONO, 13, Font.PLAIN).padding(6, 0)
            .appearance(UiInputAppearance.defaults().background(0x00000000).border(0x00000000).focus(0x00000000)
                .text(0xFFE6EDF3).placeholder(0x66E6EDF3).caret(0xFFE6EDF3).selection(0x5579C0FF));
        ui.textField("terminal:input", "terminal:input", "", px, y - 2, f.right() - px - 16, 20, "", 200, style);
    }

    private static void terminalCommand(UiScreen screen, String raw) {
        String command = raw.strip(), log = screen.stringValue("terminal:log", "");
        var out = new StringBuilder(log).append("sl0oth@paneos ~ % ").append(command).append('\n');
        String[] parts = command.split("\\s+", 2);
        switch (parts[0]) {
            case "" -> { }
            case "help" -> out.append("  help  ls  date  whoami  uptime  panefetch  open <app>  theme dark|light  echo <text>  clear\n");
            case "ls" -> out.append("  Applications  Desktop  Documents  Downloads  Music  Pictures\n");
            case "date" -> out.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH))).append('\n');
            case "whoami" -> out.append("sl0oth\n");
            case "uptime" -> out.append("up ").append(java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 60_000).append(" mins, 1 user\n");
            case "echo" -> out.append(parts.length > 1 ? parts[1] : "").append('\n');
            case "clear" -> { screen.setValue("terminal:log", ""); screen.setValue("terminal:input", ""); return; }
            case "theme" -> {
                boolean dark = parts.length > 1 && parts[1].equals("dark");
                screen.setValue("dark", dark);
                out.append("Appearance set to ").append(dark ? "dark" : "light").append('\n');
            }
            case "open" -> {
                String target = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
                if (PaneOsShowcase.app(target) != null || target.equals("mail") || target.equals("wayfarer")) {
                    out.append("Opening ").append(target).append("…\n");
                    screen.setValue("terminal:log", out.toString());
                    screen.setValue("terminal:input", "");
                    PaneOsEvents.openApp(screen, target);
                    return;
                }
                out.append("open: no such app: ").append(target).append('\n');
            }
            case "panefetch" -> {
                var runtime = Runtime.getRuntime();
                long used = (runtime.totalMemory() - runtime.freeMemory()) >> 20, max = runtime.maxMemory() >> 20;
                out.append("  ▄▄ ▄▄    sl0oth@paneos\n");
                out.append("  ██ ▀▀    OS: PaneOS 26.2 (Tahoe Pane)\n");
                out.append("  ▄▄ ██    Shell: zsh 5.9\n");
                out.append("  ██ ██    Renderer: Pane retained raster + GPU layers\n");
                out.append("           Java: ").append(Runtime.version()).append('\n');
                out.append("           Memory: ").append(used).append(" MiB / ").append(max).append(" MiB\n");
            }
            default -> out.append("zsh: command not found: ").append(parts[0]).append('\n');
        }
        String text = out.toString();
        String[] lines = text.split("\n", -1);
        if (lines.length > 200) text = String.join("\n", java.util.Arrays.copyOfRange(lines, lines.length - 200, lines.length));
        screen.setValue("terminal:log", text);
        screen.setValue("terminal:input", "");
    }

    // ------------------------------------------------------------------ settings

    private static final String[] SETTINGS = {"Wi-Fi", "Bluetooth", "Battery", "Appearance", "Wallpaper", "Liquid Glass",
        "Dock & Desktop", "Displays", "Sound", "About"};
    private static final String[] SETTINGS_GLYPHS = {"wifi", "bluetooth", "battery", "darkmode", "photo", "globe", "grid", "sun", "speaker", "lock"};
    private static final int[] SETTINGS_TINTS = {PaneOsArt.BLUE, PaneOsArt.BLUE, PaneOsArt.GREEN, 0xFF1C1C1E, PaneOsArt.TEAL,
        PaneOsArt.INDIGO, 0xFF1C1C1E, PaneOsArt.BLUE, PaneOsArt.RED, PaneOsArt.GRAPHITE};

    private static void settings(UiContext ui, Caches caches, Look look, UiRect f) {
        UiCanvas g = ui.canvas();
        body(g, look, f, 18, SIDEBAR + 20);
        int pane = (int) ui.numberValue("settings:pane", 3);
        float sx = f.x() + 16, sy = f.y() + 48, sw = SIDEBAR + 4;
        searchPill(g, look, sx, sy, sw, "Search");
        g.circle(look.accent(), sx + 22, sy + 66, 18);
        g.centeredText("S", sx + 22, sy + 66, 17, 0xFFFFFFFF, Font.BOLD, PaneOsArt.FONT);
        g.text("Sl0oth", sx + 48, sy + 50, 13.5F, look.text(), Font.BOLD, PaneOsArt.FONT);
        g.text("Pane Account", sx + 48, sy + 67, 11.5F, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        for (int i = 0; i < SETTINGS.length; i++) {
            float y = sy + 98 + i * 30 + (i >= 3 ? 12 : 0) + (i >= 9 ? 12 : 0);
            if (y + 28 > f.bottom() - 8) break;
            int tint = SETTINGS_TINTS[i], index = i;
            String label = SETTINGS[i], glyph = SETTINGS_GLYPHS[i];
            ui.paintedButton("settings:side:" + i, label, "select:settings:pane=" + i, sx, y, sw, 28, 8, true, (c, s) -> {
                if (pane == index) c.roundRect(look.accent(), sx, y, sw, 28, 8);
                else if (s.hovered()) c.roundRect(look.hover(), sx, y, sw, 28, 8);
                c.roundRect(tint, sx + 6, y + 4, 20, 20, 5);
                PaneOsArt.symbol(c, glyph, sx + 16, y + 14, 13, 0xFFFFFFFF);
                c.text(label, sx + 34, y + 6, 13, pane == index ? 0xFFFFFFFF : look.text(), Font.PLAIN, PaneOsArt.FONT);
            });
        }
        float cx = f.x() + SIDEBAR + 36 + 18, cw = f.right() - cx - 22, header = f.y() + 18, y = header + 50;
        toolButton(ui, look, "settings:back", "Back", "chevron-left", cx - 6, header - 4, 36, "select:settings:pane=" + Math.max(0, pane - 1));
        g.text(SETTINGS[pane], cx + 42, header + 3, 15, look.text(), Font.BOLD, PaneOsArt.FONT);
        switch (pane) {
            case 0 -> {
                group(g, look, cx, y, cw, 50);
                g.roundRect(PaneOsArt.BLUE, cx + 14, y + 12, 26, 26, 7);
                PaneOsArt.symbol(g, "wifi", cx + 27, y + 24, 16, 0xFFFFFFFF);
                g.text("Wi-Fi", cx + 50, y + 16, 13, look.text(), Font.BOLD, PaneOsArt.FONT);
                switchControl(ui, look, "settings:wifi", "wifi", true, cx + cw - 54, y + 14);
                if (ui.booleanValue("wifi", true)) {
                    sectionLabel(g, look, "Known Networks", cx + 4, y + 66);
                    String[] nets = {"Home", "Lakeside Cabin", "Pane Labs 5G", "Coffee Bar Guest"};
                    group(g, look, cx, y + 84, cw, nets.length * 40);
                    for (int i = 0; i < nets.length; i++) {
                        row(g, look, nets[i], i == 0 ? "Connected" : null, cx, y + 84 + i * 40, cw, i < nets.length - 1);
                        PaneOsArt.symbol(g, "wifi", cx + cw - (i == 0 ? 100 : 30), y + 104 + i * 40, 14, look.secondary());
                    }
                }
            }
            case 1 -> {
                group(g, look, cx, y, cw, 50);
                g.text("Bluetooth", cx + 14, y + 16, 13, look.text(), Font.BOLD, PaneOsArt.FONT);
                switchControl(ui, look, "settings:bt", "bt", true, cx + cw - 54, y + 14);
                sectionLabel(g, look, "My Devices", cx + 4, y + 66);
                String[][] devices = {{"Pane Headphones", "Connected"}, {"Magic Keys", "Connected"}, {"Trackpad", "Not Connected"}};
                group(g, look, cx, y + 84, cw, devices.length * 40);
                for (int i = 0; i < devices.length; i++) row(g, look, devices[i][0], ui.booleanValue("bt", true) ? devices[i][1] : "Off", cx, y + 84 + i * 40, cw, i < devices.length - 1);
            }
            case 2 -> {
                group(g, look, cx, y, cw, 110);
                g.text("Battery Level", cx + 14, y + 14, 13, look.text(), Font.BOLD, PaneOsArt.FONT);
                g.text("84%", cx + cw - 50, y + 14, 13, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
                for (int i = 0; i < 24; i++) {
                    float bar = 14 + (float) Math.abs(Math.sin(i * .5)) * 30;
                    g.roundRect(i > 20 ? PaneOsArt.GREEN : UiCanvas.alpha(PaneOsArt.GREEN, 150), cx + 14 + i * (cw - 28) / 24, y + 96 - bar, (cw - 28) / 24 - 3, bar, 2);
                }
            }
            case 3 -> {
                group(g, look, cx, y, cw, 150);
                g.text("Appearance", cx + 14, y + 14, 13, look.text(), Font.PLAIN, PaneOsArt.FONT);
                String[] modes = {"Light", "Dark"};
                for (int i = 0; i < 2; i++) {
                    float tx = cx + cw - 230 + i * 110, ty = y + 14;
                    boolean chosen = look.dark() == (i == 1);
                    String mode = modes[i];
                    int shade = i;
                    ui.paintedButton("settings:mode:" + i, mode, "appearance:" + mode.toLowerCase(Locale.ROOT), tx, ty, 96, 110, 10, true, (c, s) -> {
                        if (chosen) c.strokeRoundRect(look.accent(), 3, tx - 3, ty - 3, 102, 72, 12);
                        c.gradientRect(shade == 0 ? 0xFFBFDDF8 : 0xFF0B2A6B, shade == 0 ? 0xFF2E6FD8 : 0xFF02081C, 90, tx, ty, 96, 66, 9);
                        c.roundRect(shade == 0 ? 0xF0FFFFFF : 0xF02C2C2E, tx + 14, ty + 14, 68, 40, 6);
                        c.roundRect(shade == 0 ? 0x33000000 : 0x40FFFFFF, tx + 20, ty + 22, 40, 5, 2);
                        c.roundRect(look.accent(), tx + 20, ty + 32, 26, 5, 2);
                        c.centeredText(mode, tx + 48, ty + 86, 12, chosen ? look.text() : look.secondary(), chosen ? Font.BOLD : Font.PLAIN, PaneOsArt.FONT);
                    });
                }
                group(g, look, cx, y + 166, cw, 52);
                g.text("Accent colour", cx + 14, y + 183, 13, look.text(), Font.PLAIN, PaneOsArt.FONT);
                int accent = (int) ui.numberValue("accent", 0);
                for (int i = 0; i < PaneOsArt.ACCENTS.length; i++) {
                    float dx = cx + cw - 24 - (PaneOsArt.ACCENTS.length - 1 - i) * 28, dy = y + 192;
                    int color = PaneOsArt.ACCENTS[i];
                    boolean chosen = accent == i;
                    ui.shapedButton("settings:accent:" + i, PaneOsArt.ACCENT_NAMES[i], "accent:" + i, g.path().arc(dx, dy, 11, 0, 360).close(), true, (c, s) -> {
                        c.circle(color, dx, dy, 9);
                        if (chosen) { c.strokeCircle(UiCanvas.alpha(color, 160), 2, dx, dy, 12); c.circle(0xFFFFFFFF, dx, dy, 3); }
                    });
                }
            }
            case 4 -> {
                float tw = (cw - 24) / 3, th = tw * .62F;
                int current = (int) ui.numberValue("wallpaper", 0);
                for (int i = 0; i < 3; i++) {
                    float tx = cx + i * (tw + 12);
                    int kind = i;
                    boolean chosen = current == i;
                    UiLayer thumb = caches.wallpaperThumbs[i];
                    ui.paintedButton("settings:wall:" + i, PaneOsArt.WALLPAPERS[i], "wallpaper:" + i, tx, y, tw, th + 26, 12, true, (c, s) -> {
                        if (chosen) c.strokeRoundRect(look.accent(), 3, tx - 3, y - 3, tw + 6, th + 6, 13);
                        c.withRoundedClip(tx, y, tw, th, 10, () -> c.withTranslation(tx, y, () ->
                            c.cachedLayer(thumb, List.of(kind, Math.round(tw), look.dark()), new UiRect(0, 0, tw, th),
                                l -> PaneOsArt.wallpaper(l, kind, look.dark(), tw, th))));
                        c.centeredText(PaneOsArt.WALLPAPERS[kind], tx + tw / 2, y + th + 15, 12.5F, chosen ? look.text() : look.secondary(),
                            chosen ? Font.BOLD : Font.PLAIN, PaneOsArt.FONT);
                    });
                }
                g.text("Wallpapers are generated from code at the exact pixel size of your window.", cx, y + th + 48, 12, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
            }
            case 5 -> {
                float pw = cw, ph = 150;
                g.withRoundedClip(cx, y, pw, ph, 14, () -> {
                    g.gradientRect(0xFFFF9F0A, 0xFF0A84FF, 0, cx, y, pw, ph, 0);
                    g.stripes(0x66FFFFFF, 6, 10, 60, cx, y, pw, ph);
                    for (int i = 0; i < 7; i++) g.circle(PaneOsArt.ACCENTS[i], cx + 40 + i * (pw - 80) / 6, y + 40 + (i % 2) * 70, 16);
                });
                g.liquidGlass(cx + pw / 2 - 120, y + 40, 240, 70, 35, look.glass());
                g.centeredText("Liquid Glass", cx + pw / 2, y + 75, 17, look.text(), Font.BOLD, PaneOsArt.FONT);
                group(g, look, cx, y + 166, cw, 112);
                g.text("Refraction", cx + 14, y + 180, 13, look.text(), Font.PLAIN, PaneOsArt.FONT);
                systemSlider(ui, look, "settings:refraction", "refraction", cx + 150, y + 178, cw - 170, 0, 30, 14);
                g.line(look.separator(), .7F, cx + 14, y + 222, cx + cw - 14, y + 222);
                g.text("Frost", cx + 14, y + 236, 13, look.text(), Font.PLAIN, PaneOsArt.FONT);
                systemSlider(ui, look, "settings:frost", "frost", cx + 150, y + 234, cw - 170, 0, 14, 6);
            }
            case 6 -> {
                group(g, look, cx, y, cw, 160);
                g.text("Size", cx + 14, y + 14, 13, look.text(), Font.PLAIN, PaneOsArt.FONT);
                systemSlider(ui, look, "settings:dock", "dockSize", cx + 150, y + 12, cw - 170, 40, 70, 54);
                g.line(look.separator(), .7F, cx + 14, y + 52, cx + cw - 14, y + 52);
                g.text("Magnification", cx + 14, y + 66, 13, look.text(), Font.PLAIN, PaneOsArt.FONT);
                switchControl(ui, look, "settings:magnify", "magnify", true, cx + cw - 54, y + 64);
                g.line(look.separator(), .7F, cx + 14, y + 104, cx + cw - 14, y + 104);
                g.text("Show widgets on desktop", cx + 14, y + 118, 13, look.text(), Font.PLAIN, PaneOsArt.FONT);
                switchControl(ui, look, "settings:widgets", "widgets", true, cx + cw - 54, y + 116);
            }
            case 7 -> {
                group(g, look, cx, y, cw, 56);
                g.text("Brightness", cx + 14, y + 20, 13, look.text(), Font.PLAIN, PaneOsArt.FONT);
                systemSlider(ui, look, "settings:brightness", "brightness", cx + 150, y + 18, cw - 170, .25, 1, 1);
            }
            case 8 -> {
                group(g, look, cx, y, cw, 56);
                g.text("Output volume", cx + 14, y + 20, 13, look.text(), Font.PLAIN, PaneOsArt.FONT);
                systemSlider(ui, look, "settings:volume", "volume", cx + 150, y + 18, cw - 170, 0, 1, .6);
            }
            default -> {
                PaneOsArt.mark(g, cx + cw / 2, y + 50, 80, look.accent());
                g.centeredText("PaneOS", cx + cw / 2, y + 122, 26, look.text(), Font.BOLD, PaneOsArt.FONT);
                g.centeredText("Version 26.2 (Tahoe Pane)", cx + cw / 2, y + 150, 12.5F, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
                String[][] rows = {{"Renderer", "Pane retained raster + GPU layers"}, {"Runtime", "Java " + Runtime.version().feature() + " · Fabric"},
                    {"Memory", (Runtime.getRuntime().maxMemory() >> 20) + " MiB"}, {"Serial", "PANE-0042"}};
                group(g, look, cx, y + 176, cw, rows.length * 40);
                for (int i = 0; i < rows.length; i++) row(g, look, rows[i][0], rows[i][1], cx, y + 176 + i * 40, cw, i < rows.length - 1);
            }
        }
    }

    // ------------------------------------------------------------------ music

    static void albumArt(UiCanvas g, float x, float y, float s, int index) {
        int[] colors = ART[Math.floorMod(index, ART.length)];
        g.shadow(0x33000000, s * .08F, 0, s * .03F, x, y, s, s, s * .1F);
        g.withRoundedClip(x, y, s, s, s * .1F, () -> {
            g.gradientRect(colors[0], colors[1], 45, x, y, s, s, 0);
            var random = new Random(index * 31L + 7);
            for (int i = 0; i < 4; i++) {
                float r = s * (.2F + random.nextFloat() * .4F);
                g.circle(UiCanvas.alpha(0xFFFFFFFF, 30 + random.nextInt(50)), x + random.nextFloat() * s, y + random.nextFloat() * s, r);
            }
            g.path().moveTo(x, y + s * .72F).curveTo(x + s * .35F, y + s * .55F, x + s * .65F, y + s * .9F, x + s, y + s * .7F)
                .stroke(0x99FFFFFF, Math.max(1, s * .025F));
        });
    }

    private static void music(UiContext ui, Look look, UiRect f) {
        UiCanvas g = ui.canvas();
        body(g, look, f, 18, SIDEBAR);
        float sx = f.x() + 16, sy = f.y() + 52, sw = SIDEBAR - 16;
        int side = (int) ui.numberValue("music:side", 0);
        String[] items = {"Home", "New", "Radio", "Recently Added", "Artists", "Albums", "Songs"};
        String[] glyphs = {"note", "grid", "wifi", "clock", "person", "photo", "list"};
        for (int i = 0; i < items.length; i++) {
            float y = sy + i * 30 + (i >= 3 ? 30 : 0);
            if (i == 3) sectionLabel(g, look, "Library", sx + 8, y - 22);
            sideItem(ui, look, "music:side:" + i, items[i], glyphs[i], PaneOsArt.RED, sx, y, sw, side == i, "select:music:side=" + i);
        }
        int track = (int) ui.numberValue("music:track", 0);
        boolean playing = ui.booleanValue("music:playing", false);
        float cx = contentX(f) + 24, cw = f.right() - cx - 24, y = f.y() + 28;
        albumArt(g, cx, y, 168, 0);
        g.text("Glass Harbor", cx + 192, y + 52, 26, look.text(), Font.BOLD, PaneOsArt.FONT);
        g.text("Northbound", cx + 192, y + 86, 18, PaneOsArt.RED, Font.PLAIN, PaneOsArt.FONT);
        g.text("Alternative · 2026 · Lossless", cx + 192, y + 112, 12, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        float bx = cx + 192, by = y + 134;
        ui.paintedButton("music:playall", "Play", "music:" + (playing ? "play" : "0"), bx, by, 110, 32, 9, true, (c, s) -> {
            c.roundRect(s.hovered() ? UiCanvas.lighten(PaneOsArt.RED, .1F) : PaneOsArt.RED, bx, by, 110, 32, 9);
            PaneOsArt.symbol(c, playing ? "pause" : "play", bx + 30, by + 16, 14, 0xFFFFFFFF);
            c.text(playing ? "Pause" : "Play", bx + 46, by + 8, 13.5F, 0xFFFFFFFF, Font.BOLD, PaneOsArt.FONT);
        });
        float ly = y + 196;
        for (int i = 0; i < TRACKS.length; i++) {
            float ry = ly + i * 36;
            if (ry + 36 > f.bottom() - 74) break;
            int index = i;
            String[] song = TRACKS[i];
            ui.paintedButton("music:track:" + i, song[0], "music:" + i, cx - 8, ry, cw + 16, 34, 8, true, (c, s) -> {
                if (index % 2 == 0) c.roundRect(look.hover(), cx - 8, ry, cw + 16, 34, 8);
                if (s.hovered()) c.roundRect(look.hover(), cx - 8, ry, cw + 16, 34, 8);
                boolean current = track == index;
                if (current && playing) PaneOsArt.symbol(c, "speaker", cx + 8, ry + 17, 13, PaneOsArt.RED);
                else c.centeredText(Integer.toString(index + 1), cx + 8, ry + 17, 12.5F, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
                c.text(song[0], cx + 30, ry + 9, 13, current ? PaneOsArt.RED : look.text(), Font.PLAIN, PaneOsArt.FONT);
                c.text(song[1], cx + cw * .55F, ry + 9, 13, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
                c.text(song[2], cx + cw - 30, ry + 9, 13, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
            });
        }
        // Floating glass player.
        float px = contentX(f) + 14, pw = f.right() - px - 14, py = f.bottom() - 64;
        g.liquidGlass(px, py, pw, 50, 25, look.glass().withTint(look.dark() ? 0x55262628 : 0x8CFFFFFF));
        String[] song = TRACKS[track];
        albumArt(g, px + 10, py + 7, 36, track);
        g.text(song[0], px + 56, py + 9, 13, look.text(), Font.BOLD, PaneOsArt.FONT);
        g.text(song[1], px + 56, py + 26, 11.5F, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        String[] verbs = {"prev", "play", "next"};
        for (int i = 0; i < 3; i++) {
            float bcx = px + pw / 2 + (i - 1) * 44, bcy = py + 25;
            String glyph = i == 1 ? (playing ? "pause" : "play") : verbs[i];
            int index = i;
            ui.paintedButton("music:ctl:" + verbs[i], verbs[i], "music:" + verbs[i], bcx - 16, bcy - 16, 32, 32, 16, true, (c, s) -> {
                if (s.hovered()) c.circle(look.hover(), bcx, bcy, 16);
                PaneOsArt.symbol(c, glyph, bcx, bcy, index == 1 ? 20 : 16, look.text());
            });
        }
        float progressX = px + pw - 250, progressW = 200;
        double elapsed = playing ? (System.currentTimeMillis() - ui.numberValue("music:started", System.currentTimeMillis())) / 1000.0 : 0;
        String[] length = song[2].split(":");
        double total = Integer.parseInt(length[0]) * 60 + Integer.parseInt(length[1]);
        float progress = (float) Math.min(1, (elapsed % total) / total);
        g.roundRect(look.dark() ? 0x33FFFFFF : 0x1F000000, progressX, py + 23, progressW, 4, 2);
        g.roundRect(look.secondary(), progressX, py + 23, progressW * progress, 4, 2);
        if (playing) ui.refreshAfter(1000, new UiRect(progressX - 2, py + 18, progressW + 4, 14));
    }

    // ------------------------------------------------------------------ photos

    private static void photos(UiContext ui, Caches caches, Look look, UiRect f) {
        UiCanvas g = ui.canvas();
        body(g, look, f, 18, SIDEBAR);
        float sx = f.x() + 16, sy = f.y() + 52, sw = SIDEBAR - 16;
        String[] items = {"Library", "Memories", "Favourites", "Recently Saved", "Map"};
        String[] glyphs = {"photo", "clock", "heart", "folder", "pin"};
        int side = (int) ui.numberValue("photos:side", 0);
        for (int i = 0; i < items.length; i++)
            sideItem(ui, look, "photos:side:" + i, items[i], glyphs[i], look.accent(), sx, sy + i * 30, sw, side == i, "select:photos:side=" + i);
        float cx = contentX(f) + 12, cw = f.right() - cx - 12, top = f.y() + 58;
        title(g, look, "Library", LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + LocalDate.now().getYear(), cx + 4, f.y() + 14);
        int columns = Math.max(2, (int) (cw / 120));
        float cell = (cw - (columns - 1) * 4) / columns;
        int rows = (int) ((f.bottom() - 12 - top) / (cell + 4));
        int count = columns * Math.max(1, rows);
        float gridH = rows * (cell + 4);
        g.withRoundedClip(cx, top, cw, gridH, 6, () -> g.withTranslation(cx, top, () ->
            g.cachedLayer(caches.photos, List.of(columns, Math.round(cell), rows), new UiRect(0, 0, cw, gridH), c -> {
                for (int i = 0; i < count; i++) photo(c, i % columns * (cell + 4), i / columns * (cell + 4), cell, i);
            })));
        int selected = (int) ui.numberValue("photos:sel", -1);
        for (int i = 0; i < count; i++) {
            float x = cx + i % columns * (cell + 4), y = top + i / columns * (cell + 4);
            int index = i;
            ui.paintedButton("photos:item:" + i, "Photo " + (i + 1), "photos:" + i, x, y, cell, cell, 2, true, (c, s) -> {
                if (selected == index) c.strokeRoundRect(look.accent(), 3, x + 1.5F, y + 1.5F, cell - 3, cell - 3, 3);
            });
        }
    }

    private static void photo(UiCanvas g, float x, float y, float s, int seed) {
        var random = new Random(seed * 977L + 13);
        int[][] skies = {{0xFF8EC5FF, 0xFFE0F0FF}, {0xFFFF9A5C, 0xFFFFD39A}, {0xFF20325A, 0xFF7A5C9E}, {0xFF6FD3C8, 0xFFE8FFF7}, {0xFFFFC371, 0xFFFF5F6D}};
        int[] sky = skies[random.nextInt(skies.length)];
        g.withClip(x, y, s, s, () -> {
            g.gradientRect(sky[0], sky[1], 90, x, y, s, s, 0);
            if (random.nextBoolean()) g.circle(0xCCFFF6D5, x + s * (.2F + random.nextFloat() * .6F), y + s * (.2F + random.nextFloat() * .25F), s * .09F);
            for (int layer = 0; layer < 3; layer++) {
                float base = y + s * (.55F + layer * .14F);
                var path = g.path().moveTo(x, base);
                for (int i = 1; i <= 6; i++) path.lineTo(x + s * i / 6F, base - random.nextFloat() * s * (.25F - layer * .06F));
                int tone = UiCanvas.mix(sky[0], 0xFF0B1B2E, .45F + layer * .2F);
                path.lineTo(x + s, y + s).lineTo(x, y + s).close().fill(tone);
            }
        });
    }

    // ------------------------------------------------------------------ messages

    private static final String[][] CHATS = {
        {"Ana", "Lunch tomorrow? There's a new place by the lake.", "9:12", "That sounds perfect"},
        {"Sam", "Did you see the glass dock?? It bends the wallpaper", "Yesterday", "I can't stop moving windows under it"},
        {"Pane Team", "Build 26.2 is green on every platform.", "Tuesday", "Ship it"},
        {"Jules", "Photos from the ridge are up", "Monday", "The dusk ones are unreal"}};

    private static void messages(UiContext ui, Look look, UiRect f) {
        UiCanvas g = ui.canvas();
        float side = 250;
        body(g, look, f, 18, side);
        int chat = (int) ui.numberValue("msg:sel", 0);
        float sx = f.x() + 16, sw = side - 16;
        searchPill(g, look, sx, f.y() + 48, sw, "Search");
        for (int i = 0; i < CHATS.length; i++) {
            float y = f.y() + 92 + i * 64;
            int index = i;
            String[] c0 = CHATS[i];
            String sent = ui.stringValue("msg:sent:" + i, "");
            String preview = sent.isEmpty() ? c0[1] : sent.strip().substring(sent.strip().lastIndexOf('\n') + 1);
            ui.paintedButton("messages:chat:" + i, c0[0], "msg:" + i, sx, y, sw, 60, 10, true, (c, s) -> {
                if (chat == index) c.roundRect(look.accent(), sx, y, sw, 60, 10);
                else if (s.hovered()) c.roundRect(look.hover(), sx, y, sw, 60, 10);
                int avatar = PaneOsArt.ACCENTS[(index * 2 + 1) % PaneOsArt.ACCENTS.length];
                c.gradientRect(UiCanvas.lighten(avatar, .25F), avatar, 90, sx + 10, y + 10, 40, 40, 20);
                c.centeredText(c0[0].substring(0, 1), sx + 30, y + 30, 17, 0xFFFFFFFF, Font.BOLD, PaneOsArt.FONT);
                int ink = chat == index ? 0xFFFFFFFF : look.text(), sub = chat == index ? 0xD9FFFFFF : look.secondary();
                c.text(c0[0], sx + 60, y + 11, 13.5F, ink, Font.BOLD, PaneOsArt.FONT);
                c.text(c0[2], sx + sw - 10 - c.textWidth(c0[2], 11.5F, Font.PLAIN, PaneOsArt.FONT), y + 12, 11.5F, sub, Font.PLAIN, PaneOsArt.FONT);
                c.text(c.ellipsize(preview, sw - 74, 12.5F, Font.PLAIN, PaneOsArt.FONT), sx + 60, y + 31, 12.5F, sub, Font.PLAIN, PaneOsArt.FONT);
            });
        }
        float cx = f.x() + side + 16, cw = f.right() - cx;
        String[] current = CHATS[chat];
        g.text(current[0], cx + cw / 2 - g.textWidth(current[0], 13.5F, Font.BOLD, PaneOsArt.FONT) / 2, f.y() + 18, 13.5F, look.text(), Font.BOLD, PaneOsArt.FONT);
        g.line(look.separator(), .7F, cx, f.y() + 50, f.right(), f.y() + 50);
        var bubbles = new ArrayList<String[]>();
        bubbles.add(new String[] {"them", current[1]});
        bubbles.add(new String[] {"me", current[3]});
        long now = System.currentTimeMillis(), replyAt = (long) ui.numberValue("msg:reply:" + chat, 0);
        for (String line : ui.stringValue("msg:sent:" + chat, "").split("\n")) if (!line.isBlank()) bubbles.add(new String[] {"me", line});
        boolean typing = replyAt > now;
        if (replyAt != 0 && !typing) bubbles.add(new String[] {"them", "Ha, love it. Talk soon!"});
        if (typing) ui.refreshAfter(replyAt - now, new UiRect(cx, f.y() + 50, cw, f.height() - 50));
        float y = f.bottom() - 70;
        float maxW = cw * .62F;
        var layout = new ArrayList<float[]>();
        for (int i = bubbles.size() - 1; i >= 0; i--) {
            var lines = g.wrapText(bubbles.get(i)[1], maxW - 24, 13.5F, Font.PLAIN, PaneOsArt.FONT);
            float bh = lines.size() * 18 + 16;
            y -= bh + 6;
            if (y < f.y() + 60) break;
            layout.add(0, new float[] {i, y, bh});
        }
        if (typing) {
            float ty = f.bottom() - 70 - 6;
            g.roundRect(look.dark() ? 0xFF3A3A3C : 0xFFE9E9EB, cx + 18, ty - 34, 62, 34, 17);
            for (int d = 0; d < 3; d++) g.circle(look.secondary(), cx + 36 + d * 13, ty - 17, 3.6F);
        }
        for (float[] entry : layout) {
            String[] bubble = bubbles.get((int) entry[0]);
            boolean mine = bubble[0].equals("me");
            var lines = g.wrapText(bubble[1], maxW - 24, 13.5F, Font.PLAIN, PaneOsArt.FONT);
            float bw = 0;
            for (String line : lines) bw = Math.max(bw, g.textWidth(line, 13.5F, Font.PLAIN, PaneOsArt.FONT));
            bw += 24;
            float bx = mine ? f.right() - 18 - bw : cx + 18, by = entry[1] - (typing ? 40 : 0);
            int fill = mine ? look.accent() : look.dark() ? 0xFF3A3A3C : 0xFFE9E9EB;
            g.roundRect(fill, bx, by, bw, entry[2], 18);
            for (int l = 0; l < lines.size(); l++)
                g.text(lines.get(l), bx + 12, by + 8 + l * 18, 13.5F, mine ? 0xFFFFFFFF : look.text(), Font.PLAIN, PaneOsArt.FONT);
        }
        float ix = cx + 16, iw = cw - 32, iy = f.bottom() - 52;
        g.roundRect(look.dark() ? 0x14FFFFFF : 0x0A000000, ix, iy, iw, 36, 18);
        g.strokeRoundRect(look.separator(), .8F, ix, iy, iw, 36, 18);
        ui.textField("messages:draft", "msg:draft:" + chat, "Message", ix + 4, iy, iw - 8, 36, "", 400, input(look, 13.5F, false));
    }

    // ------------------------------------------------------------------ maps

    private static final String[][] PLACES = {{"Emerald Bay", "State Park · 4.8 ★"}, {"Sand Harbor", "Beach · 4.7 ★"},
        {"Glass House Café", "Coffee · Open until 6 PM"}, {"Ridge Trailhead", "Trail · 6.2 km loop"}};
    private static final float[][] PLACE_AT = {{.38F, .34F}, {.7F, .3F}, {.56F, .62F}, {.82F, .7F}};

    private static void maps(UiContext ui, Caches caches, Look look, UiRect f) {
        UiCanvas g = ui.canvas();
        g.withRoundedClip(f.x(), f.y(), f.width(), f.height(), 18, () -> g.withTranslation(f.x(), f.y(), () ->
            g.cachedLayer(caches.map, List.of(Math.round(f.width()), Math.round(f.height()), look.dark()), new UiRect(0, 0, f.width(), f.height()),
                c -> mapArt(c, f.width(), f.height(), look.dark()))));
        int selected = (int) ui.numberValue("maps:sel", 0);
        for (int i = 0; i < PLACES.length; i++) {
            float px = f.x() + f.width() * PLACE_AT[i][0], py = f.y() + f.height() * PLACE_AT[i][1];
            int index = i;
            ui.paintedButton("maps:pin:" + i, PLACES[i][0], "place:" + i, px - 14, py - 36, 28, 38, 8, true, (c, s) -> {
                c.shadow(0x40000000, 5, 0, 2, px - 6, py - 2, 12, 6, 3);
                PaneOsArt.pin(c, px, py - 36, 36, selected == index ? PaneOsArt.RED : PaneOsArt.ORANGE);
            });
            if (selected == i) {
                float bw = g.textWidth(PLACES[i][0], 13, Font.BOLD, PaneOsArt.FONT) + 28;
                g.liquidGlass(px - bw / 2, py - 76, bw, 32, 16, look.clear().withTint(look.dark() ? 0x80262628 : 0xB3FFFFFF));
                g.centeredText(PLACES[i][0], px, py - 60, 13, look.text(), Font.BOLD, PaneOsArt.FONT);
            }
        }
        // Floating glass sidebar.
        float sx = f.x() + 10, sy = f.y() + 10, sw = 250, sh = Math.min(f.height() - 20, 92 + PLACES.length * 56);
        g.liquidGlass(sx, sy, sw, sh, 14, look.glass().withTint(look.dark() ? 0x73202022 : 0x99FFFFFF));
        searchPill(g, look, sx + 12, sy + 40, sw - 24, "Search Maps");
        for (int i = 0; i < PLACES.length; i++) {
            float y = sy + 86 + i * 56;
            if (y + 52 > sy + sh) break;
            int index = i;
            String[] place = PLACES[i];
            ui.paintedButton("maps:place:" + i, place[0], "place:" + i, sx + 8, y, sw - 16, 52, 10, true, (c, s) -> {
                if (selected == index) c.roundRect(look.dark() ? 0x26FFFFFF : 0x14000000, sx + 8, y, sw - 16, 52, 10);
                c.circle(index == 2 ? PaneOsArt.ORANGE : index == 3 ? PaneOsArt.GREEN : PaneOsArt.BLUE, sx + 30, y + 26, 14);
                PaneOsArt.symbol(c, index == 2 ? "note" : "pin", sx + 30, y + 26, 14, 0xFFFFFFFF);
                c.text(place[0], sx + 52, y + 9, 13, look.text(), Font.BOLD, PaneOsArt.FONT);
                c.text(place[1], sx + 52, y + 28, 11.5F, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
            });
        }
        for (int i = 0; i < 2; i++) {
            float bx = f.right() - 52, by = f.bottom() - 98 + i * 42;
            g.liquidGlass(bx, by, 38, 38, 12, look.clear().withTint(look.dark() ? 0x80262628 : 0xB3FFFFFF));
            PaneOsArt.symbol(g, i == 0 ? "plus" : "chevron-down", bx + 19, by + 19, 16, look.text());
        }
    }

    private static void mapArt(UiCanvas g, float w, float h, boolean dark) {
        int land = dark ? 0xFF2B2E33 : 0xFFF1EFE8, park = dark ? 0xFF24402F : 0xFFCDE8C0, water = dark ? 0xFF1A3557 : 0xFFA9D3F5;
        int road = dark ? 0xFF4A4E55 : 0xFFFFFFFF, highway = dark ? 0xFF8A6B2E : 0xFFFFD98A;
        g.fill(land, 0, 0, w, h);
        var random = new Random(42);
        for (int i = 0; i < 9; i++)
            g.roundRect(park, random.nextFloat() * w, random.nextFloat() * h, 60 + random.nextFloat() * 160, 40 + random.nextFloat() * 110, 30);
        g.path().moveTo(w * .2F, -20).curveTo(w * .05F, h * .3F, w * .36F, h * .5F, w * .18F, h * 1.1F)
            .lineTo(-20, h * 1.1F).lineTo(-20, -20).close().fill(water);
        g.path().moveTo(w * .44F, h * .14F).curveTo(w * .62F, h * .02F, w * .82F, h * .18F, w * .74F, h * .42F)
            .curveTo(w * .66F, h * .6F, w * .46F, h * .48F, w * .44F, h * .14F).close().fill(water);
        for (int i = 0; i < 14; i++) {
            float y = random.nextFloat() * h;
            g.line(road, 5, -10, y, w + 10, y + (random.nextFloat() - .5F) * 140);
            float x = random.nextFloat() * w;
            g.line(road, 5, x, -10, x + (random.nextFloat() - .5F) * 140, h + 10);
        }
        g.path().moveTo(-10, h * .82F).curveTo(w * .3F, h * .7F, w * .6F, h * .95F, w + 10, h * .58F).stroke(highway, 9);
        g.path().moveTo(-10, h * .82F).curveTo(w * .3F, h * .7F, w * .6F, h * .95F, w + 10, h * .58F).stroke(dark ? 0x33000000 : 0x26C08A1F, 1);
        g.centeredText("LAKE TAHOE", w * .6F, h * .27F, 13, dark ? 0x99A9D3F5 : 0x995B8DB8, Font.BOLD, PaneOsArt.FONT);
    }

    // ------------------------------------------------------------------ compass

    private static void compass(UiContext ui, Look look, UiRect f) {
        UiCanvas g = ui.canvas();
        body(g, look, f, 18, 0);
        String page = ui.stringValue("compass:page", "");
        float tx = f.x() + 96, ty = f.y() + 12;
        toolButton(ui, look, "compass:back", "Back", "chevron-left", tx, ty, 36, "compass:");
        toolButton(ui, look, "compass:sidebar", "Sidebar", "sidebar", tx + 40, ty, 36, "compass:");
        float aw = Math.min(520, f.width() - 340), ax = f.centerX() - aw / 2;
        g.liquidGlass(ax, ty, aw, 32, 16, look.clear().withTint(look.dark() ? 0x1FFFFFFF : 0xB3FFFFFF).withShadow(0x14000000));
        String address = page.isEmpty() ? "Search or enter website name" : page.toLowerCase(Locale.ROOT).replace(' ', '-') + ".pane";
        PaneOsArt.symbol(g, page.isEmpty() ? "search" : "lock", ax + 18, ty + 16, 12, look.secondary());
        g.centeredText(address, ax + aw / 2, ty + 16, 13, page.isEmpty() ? look.tertiary() : look.text(), Font.PLAIN, PaneOsArt.FONT);
        toolButton(ui, look, "compass:share", "Share", "share", f.right() - 92, ty, 36, "compass:" + page);
        toolButton(ui, look, "compass:new", "New Tab", "plus", f.right() - 52, ty, 36, "compass:");
        float cy = f.y() + 56, cx = f.x() + 40, cw = f.width() - 80;
        g.withRoundedClip(f.x(), cy, f.width(), f.bottom() - cy, 18, () -> {
            if (page.isEmpty()) g.gradientRect(look.dark() ? 0xFF1E2230 : 0xFFF4F1FA, look.dark() ? 0xFF151517 : 0xFFE9F1FB, 120, f.x(), cy, f.width(), f.bottom() - cy, 0);
            else g.fill(look.content(), f.x(), cy, f.width(), f.bottom() - cy);
        });
        if (!page.isEmpty()) { article(g, look, page, cx + cw * .12F, cy + 34, cw * .76F); return; }
        g.text("Favourites", cx, cy + 36, 19, look.text(), Font.BOLD, PaneOsArt.FONT);
        String[] sites = {"Pane Docs", "Fabric", "Weather", "News", "Trails", "Recipes", "Studio", "Maps"};
        int[] tints = {PaneOsArt.BLUE, 0xFFDBD0B4, PaneOsArt.TEAL, PaneOsArt.RED, PaneOsArt.GREEN, PaneOsArt.ORANGE, PaneOsArt.INDIGO, PaneOsArt.MINT};
        for (int i = 0; i < sites.length; i++) {
            float x = cx + i * 92, y = cy + 72;
            if (x + 80 > f.right() - 20) break;
            String site = sites[i];
            int tint = tints[i];
            ui.paintedButton("compass:site:" + i, site, "compass:" + site, x, y, 80, 100, 14, true, (c, s) -> {
                c.shadow(0x26000000, 6, 0, 2, x + 8, y, 64, 64, 14);
                c.roundRect(s.hovered() ? UiCanvas.lighten(tint, .08F) : tint, x + 8, y, 64, 64, 14);
                c.centeredText(site.substring(0, 1), x + 40, y + 32, 26, 0xFFFFFFFF, Font.BOLD, PaneOsArt.FONT);
                c.centeredText(site, x + 40, y + 82, 12, look.text(), Font.PLAIN, PaneOsArt.FONT);
            });
        }
        float py = cy + 204;
        g.text("Privacy Report", cx, py, 19, look.text(), Font.BOLD, PaneOsArt.FONT);
        g.roundRect(look.dark() ? 0x14FFFFFF : 0xB3FFFFFF, cx, py + 34, Math.min(cw, 520), 76, 16);
        PaneOsArt.symbol(g, "lock", cx + 36, py + 72, 26, look.accent());
        g.text("24", cx + 66, py + 50, 26, look.text(), Font.BOLD, PaneOsArt.FONT);
        g.text("trackers were kept from profiling you in the last seven days.", cx + 104, py + 60, 13, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
    }

    private static void article(UiCanvas g, Look look, String page, float x, float y, float w) {
        g.text(page, x, y, 32, look.text(), Font.BOLD, PaneOsArt.FONT);
        g.text("A page rendered by Pane: text, layout and art drawn from code.", x, y + 46, 15, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        g.gradientRect(0xFF0A84FF, 0xFF63E6E2, 30, x, y + 82, w, 150, 16);
        String body = "Retained rendering means this page is rasterised once and reused until something changes. "
            + "Hovering a link repaints only that link, and the glass toolbar above re-reads the pixels behind it whenever they move. "
            + "Nothing here is an image: the hero is a gradient, the type is shaped at the exact pixel size of your window.";
        g.wrappedText(body, x, y + 256, w, 15, 24, look.text(), Font.PLAIN, PaneOsArt.FONT);
    }

    // ------------------------------------------------------------------ calendar

    private static void calendar(UiContext ui, Look look, UiRect f) {
        UiCanvas g = ui.canvas();
        body(g, look, f, 18, SIDEBAR - 20);
        float sx = f.x() + 16, sy = f.y() + 52;
        String[] cals = {"Home", "Work", "Family", "Trips"};
        int[] tints = {PaneOsArt.BLUE, PaneOsArt.INDIGO, PaneOsArt.ORANGE, PaneOsArt.GREEN};
        sectionLabel(g, look, "iCloud", sx + 8, sy);
        for (int i = 0; i < cals.length; i++) {
            float y = sy + 20 + i * 28;
            String key = "cal:" + i;
            boolean on = ui.booleanValue(key, true);
            int tint = tints[i];
            String label = cals[i];
            ui.paintedButton("calendar:cal:" + i, label, "toggle:" + key + "|true", sx, y, SIDEBAR - 36, 26, 6, true, (c, s) -> {
                if (s.hovered()) c.roundRect(look.hover(), sx, y, SIDEBAR - 36, 26, 6);
                if (on) { c.roundRect(tint, sx + 8, y + 6, 14, 14, 4); PaneOsArt.symbol(c, "check", sx + 15, y + 13, 10, 0xFFFFFFFF); }
                else c.strokeRoundRect(tint, 1.5F, sx + 8, y + 6, 14, 14, 4);
                c.text(label, sx + 30, y + 5, 13, look.text(), Font.PLAIN, PaneOsArt.FONT);
            });
        }
        LocalDate today = LocalDate.now(), first = today.withDayOfMonth(1);
        float cx = f.x() + SIDEBAR + 8, cw = f.right() - cx - 16, top = f.y() + 60;
        g.text(today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH), cx, f.y() + 14, 22, look.text(), Font.BOLD, PaneOsArt.FONT);
        g.text(Integer.toString(today.getYear()), cx + g.textWidth(today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH), 22, Font.BOLD, PaneOsArt.FONT) + 8,
            f.y() + 14, 22, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        float cellW = cw / 7, cellH = (f.bottom() - 12 - top - 22) / 6;
        for (int i = 0; i < 7; i++) g.text(days[i], cx + i * cellW + cellW - 34, top, 12, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        int offset = first.getDayOfWeek().getValue() - 1;
        String[][] events = {{"3", "Dentist", "0"}, {"8", "Ridge hike", "3"}, {"12", "Team offsite", "1"}, {"17", "Ana's birthday", "2"},
            {Integer.toString(today.getDayOfMonth()), "Design review", "1"}, {"26", "Flight to Reno", "3"}, {"29", "Launch PaneOS", "1"}};
        for (int cell = 0; cell < 42; cell++) {
            LocalDate date = first.plusDays(cell - offset);
            float x = cx + cell % 7 * cellW, y = top + 22 + cell / 7 * cellH;
            g.line(look.separator(), .7F, x, y, x + cellW, y);
            if (cell % 7 > 0) g.line(look.separator(), .7F, x, y, x, y + cellH);
            boolean inMonth = date.getMonth() == today.getMonth(), isToday = date.equals(today);
            String number = Integer.toString(date.getDayOfMonth());
            float nx = x + cellW - 12 - g.textWidth(number, 13, Font.PLAIN, PaneOsArt.FONT);
            if (isToday) g.circle(0xFFFF3B30, nx + g.textWidth(number, 13, Font.PLAIN, PaneOsArt.FONT) / 2, y + 15, 12);
            g.text(number, nx, y + 6, 13, isToday ? 0xFFFFFFFF : inMonth ? look.text() : look.tertiary(), isToday ? Font.BOLD : Font.PLAIN, PaneOsArt.FONT);
            if (!inMonth) continue;
            for (String[] event : events) {
                int calendarIndex = Integer.parseInt(event[2]);
                if (!event[0].equals(number) || !ui.booleanValue("cal:" + calendarIndex, true)) continue;
                int tint = tints[calendarIndex];
                g.roundRect(UiCanvas.alpha(tint, 46), x + 4, y + 30, cellW - 8, 18, 4);
                g.roundRect(tint, x + 4, y + 30, 3, 18, 1.5F);
                g.text(g.ellipsize(event[1], cellW - 20, 11.5F, Font.PLAIN, PaneOsArt.FONT), x + 11, y + 32, 11.5F, look.dark() ? UiCanvas.lighten(tint, .3F) : UiCanvas.darken(tint, .25F), Font.PLAIN, PaneOsArt.FONT);
            }
        }
    }
}
