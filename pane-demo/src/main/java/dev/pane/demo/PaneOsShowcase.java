package dev.pane.demo;

import dev.pane.demo.PaneOsArt.Look;
import dev.pane.ui.*;
import java.awt.Font;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * PaneOS: a desktop in the spirit of a modern Mac, drawn entirely from code. Liquid glass dock,
 * widgets, Control Center and Spotlight refract the procedural wallpaper and whatever window
 * sits behind them. Windows open, minimize into the dock, drag and stack; the apps work.
 */
public final class PaneOsShowcase {
    static final float MENU_H = 28;

    /** Window apps and their default size; Mail and Wayfarer open their own showcases. */
    record App(String id, String name, float width, float height, float centerX, float centerY) { }
    static final List<App> WINDOWS = List.of(
        new App("files", "Files", 820, 500, .5F, .44F),
        new App("compass", "Compass", 980, 600, .52F, .47F),
        new App("messages", "Messages", 780, 520, .56F, .5F),
        new App("maps", "Maps", 920, 580, .5F, .48F),
        new App("photos", "Photos", 920, 580, .48F, .47F),
        new App("calendar", "Calendar", 880, 570, .5F, .48F),
        new App("notes", "Notes", 840, 520, .46F, .46F),
        new App("music", "Music", 920, 580, .54F, .49F),
        new App("calculator", "Calculator", 232, 372, .8F, .42F),
        new App("terminal", "Terminal", 680, 420, .4F, .6F),
        new App("settings", "System Settings", 780, 560, .5F, .47F),
        new App("welcome", "Welcome to PaneOS", 580, 676, .5F, .5F));
    static final String[] DOCK = {"files", "launch", "compass", "messages", "mail", "maps", "photos", "calendar", "notes",
        "music", "calculator", "terminal", "settings", "wayfarer", "|", "downloads", "trash"};
    static final String[] DOCK_NAMES = {"Files", "Apps", "Compass", "Messages", "Mail", "Maps", "Photos", "Calendar", "Notes",
        "Music", "Calculator", "Terminal", "System Settings", "Wayfarer", "", "Downloads", "Trash"};

    private PaneOsShowcase() { }

    static App app(String id) {
        for (App app : WINDOWS) if (app.id().equals(id)) return app;
        return null;
    }

    static String displayName(String id) {
        for (int i = 0; i < DOCK.length; i++) if (DOCK[i].equals(id)) return DOCK_NAMES[i];
        App app = app(id);
        return app == null ? "Files" : id.equals("welcome") ? "PaneOS" : app.name();
    }

    /** Caches that outlive individual paints. */
    static final class Caches {
        final UiLayer wallpaper = new UiLayer(), wallpaperArt = new UiLayer(), desk = new UiLayer(), photos = new UiLayer(), map = new UiLayer(), lock = new UiLayer();
        final UiLayer[] wallpaperThumbs = {new UiLayer(), new UiLayer(), new UiLayer()};
    }

    /** Builds the screen object; the performance probe substitutes a measuring subclass. */
    interface ScreenMaker { UiScreen make(UiDocument document, UiScreenEventHandler handler, UiScreenOptions options); }

    public static UiScreen createScreen(Screen parent) {
        return createScreen(parent, (document, handler, options) -> UiScreen.create(document, NativeOverlayRenderer.NONE, handler, options));
    }

    static UiScreen createScreen(Screen parent, ScreenMaker maker) {
        var caches = new Caches();
        var doc = UiDocument.builder("PaneOS").shell(UiShell.NONE)
            .theme(new UiTheme(0xFF0B1B3A, 0xFFFFFFFF, 0xFFF2F2F7, 0x1F000000, 0xFF1D1D1F, 0xA63C3C43,
                PaneOsArt.BLUE, 0xFFFFFFFF, 6, 10, 16, 1, 1, PaneOsArt.FONT))
            .scaling(UiScaling.fixed().reference(1440, 900).adaptivity(1).range(.25F, 8))
            .renderSettings(UiRenderSettings.typography())
            .tooltipDelayMillis(350)
            .localFocusRepaint(true)
            .tooltipStyle(UiTooltipStyle.defaults().background(0xE6F2F2F5).border(0x1F000000).text(0xFF1D1D1F)
                .font(12, Font.PLAIN).shape(26, 8).preferAbove(true))
            .page(new UiPage("desktop", "PaneOS", "", PaneOsArt.BLUE, ui -> paint(ui, caches))).build();
        UiScreen[] self = new UiScreen[1];
        var options = UiScreenOptions.builder().parent(parent).backdrop(UiBackdrop.NONE)
            .onKey(PaneOsShowcase::key)
            .onClick((screen, click) -> { pointerDown(screen, click.targetId(), click.doubleClick()); return false; })
            .onClose(PaneOsShowcase::escape).build();
        self[0] = maker.make(doc, PaneOsEvents::handle, options);
        self[0].setValue("boot:at", (double) System.currentTimeMillis());
        self[0].setValue("locked", true);
        self[0].requestFocus("lock:field");
        return self[0];
    }

    // ------------------------------------------------------------------ input

    /** A press anywhere in a window raises it; a press outside a popover closes it. */
    private static void pointerDown(UiScreen screen, String target, boolean doubleClick) {
        if (doubleClick && target != null && target.startsWith("desk:item")) PaneOsEvents.openApp(screen, "files");
        String owner = target == null ? "" : target.substring(0, Math.max(0, target.indexOf(':')));
        // Double-clicking a title bar zooms the window, as on a Mac.
        if (doubleClick && target != null && target.endsWith(":bar") && app(owner) != null) PaneOsEvents.handleAction(screen, "zoom:" + owner);
        if (screen.booleanValue("nc", false) && !owner.equals("nc") && !"mb:clock".equals(target)) screen.setValue("nc", false);
        if (app(owner) != null && screen.booleanValue("open:" + owner, false)) PaneOsEvents.raise(screen, owner);
        if (screen.booleanValue("cc", false) && !owner.equals("cc") && !"mb:cc".equals(target)) screen.setValue("cc", false);
        if (screen.booleanValue("spot", false) && !owner.equals("spot") && !"mb:spot".equals(target)) screen.setValue("spot", false);
    }

    private static boolean key(UiScreen screen, net.minecraft.client.input.KeyEvent event) {
        boolean command = (event.modifiers() & (GLFW.GLFW_MOD_SUPER | GLFW.GLFW_MOD_CONTROL)) != 0;
        if (command && event.key() == GLFW.GLFW_KEY_SPACE) { PaneOsEvents.toggleSpotlight(screen); return true; }
        if (command && event.key() == GLFW.GLFW_KEY_W) { PaneOsEvents.handleAction(screen, "close:" + frontApp(screen)); return true; }
        if (command && event.key() == GLFW.GLFW_KEY_M) { PaneOsEvents.handleAction(screen, "min:" + frontApp(screen)); return true; }
        if (command && event.key() == GLFW.GLFW_KEY_COMMA) { PaneOsEvents.openApp(screen, "settings"); return true; }
        if (command && event.key() == GLFW.GLFW_KEY_N && !frontApp(screen).isEmpty()) { PaneOsEvents.openApp(screen, frontApp(screen)); return true; }
        boolean controlCommand = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 && (event.modifiers() & GLFW.GLFW_MOD_SUPER) != 0;
        if (controlCommand && event.key() == GLFW.GLFW_KEY_Q) { PaneOsEvents.handleAction(screen, "lock"); return true; }
        return false;
    }

    private static boolean escape(UiScreen screen) {
        if (screen.booleanValue("spot", false)) { screen.setValue("spot", false); return true; }
        if (screen.booleanValue("cc", false)) { screen.setValue("cc", false); return true; }
        if (screen.booleanValue("nc", false)) { screen.setValue("nc", false); return true; }
        if (screen.booleanValue("apps", false)) { screen.setValue("apps", false); return true; }
        return false;
    }

    static List<String> stack(String z) {
        var list = new ArrayList<String>();
        if (z != null) for (String id : z.split(",")) if (!id.isBlank()) list.add(id);
        return list;
    }

    static String frontApp(UiScreen screen) {
        var z = stack(screen.stringValue("z", ""));
        for (int i = z.size() - 1; i >= 0; i--)
            if (screen.booleanValue("open:" + z.get(i), false) && !screen.booleanValue("min:" + z.get(i), false)) return z.get(i);
        return "";
    }

    private static String frontApp(UiContext ui) {
        var z = stack(ui.stringValue("z", ""));
        for (int i = z.size() - 1; i >= 0; i--)
            if (ui.booleanValue("open:" + z.get(i), false) && !ui.booleanValue("min:" + z.get(i), false)) return z.get(i);
        return "";
    }

    static Look look(UiContext ui) {
        return Look.of(ui.booleanValue("dark", false), (int) ui.numberValue("accent", 0),
            ui.numberValue("refraction", 14), ui.numberValue("frost", 6));
    }

    // ------------------------------------------------------------------ page

    private static void paint(UiContext ui, Caches caches) {
        UiCanvas g = ui.canvas();
        float w = ui.width(), h = ui.height();
        Look look = look(ui);
        int wallpaper = (int) ui.numberValue("wallpaper", 0);
        boolean widgets = ui.booleanValue("widgets", true);
        long day = java.time.LocalDate.now().toEpochDay();
        String reminders = PaneOsWidgets.reminderKey(ui);
        Object wallRevision = List.of(Math.round(w), Math.round(h), wallpaper, look.dark());
        Object deskRevision = List.of(wallRevision, widgets, day, reminders, look.accent(),
            Math.round(ui.numberValue("refraction", 14) * 10), Math.round(ui.numberValue("frost", 6) * 10),
            ui.booleanValue("music:playing", false), (int) ui.numberValue("music:track", 0));
        long sinceBoot = System.currentTimeMillis() - (long) ui.numberValue("boot:at", 0);
        if (sinceBoot < PaneOsLock.BOOT_MILLIS) { PaneOsLock.boot(ui, w, h, sinceBoot); return; }
        boolean locked = ui.booleanValue("locked", true);
        if (locked) { PaneOsLock.lock(ui, caches.lock, caches.wallpaper, caches.wallpaperArt, wallRevision, wallpaper, look, w, h); return; }
        long sinceUnlock = System.currentTimeMillis() - (long) ui.numberValue("unlockedAt", 0);

        g.cachedLayer(caches.desk, deskRevision, new UiRect(0, 0, w, h), c -> {
            c.cachedLayer(caches.wallpaper, wallRevision, new UiRect(0, 0, w, h), wc -> PaneOsArt.wallpaper(wc, caches.wallpaperArt, wallpaper, look.dark(), w, h));
            if (widgets) PaneOsWidgets.paintStatic(c, ui, look, h);
        });
        ui.hit("desk:bg", "desk:none", 0, MENU_H, w, h - MENU_H);
        ui.contextMenu("desk:bg", List.of(
            new UiMenuItem("folder", "New Folder", "desk:newfolder").shortcut("⇧⌘N"),
            new UiMenuItem("info", "Get Info", "desk:info:desktop").shortcut("⌘I").startsGroup(),
            new UiMenuItem("wallpaper", "Change Wallpaper…", "desk:wallpaper"),
            new UiMenuItem("widgets", widgets ? "Hide Widgets" : "Show Widgets", "widgets"),
            new UiMenuItem("view", "Show View Options", "desk:view").shortcut("⌘J").enabled(false).startsGroup(),
            new UiMenuItem("sort", "Sort By", "desk:sort").enabled(false)), menuStyle(look));
        if (widgets) PaneOsWidgets.paintLive(ui, look, h);
        PaneOsWidgets.desktopItems(ui, look, w, h);
        ui.repaintScope("desk:", new UiRect(w - 124, MENU_H, 124, h - MENU_H - 100));
        ui.repaintScope("rem:", new UiRect(0, MENU_H, PaneOsWidgets.X + 2 * PaneOsWidgets.S + PaneOsWidgets.GAP + 30, h - MENU_H - 100));

        DockLayout dock = DockLayout.base(ui, w, h);
        var z = stack(ui.stringValue("z", ""));
        String front = frontApp(ui);
        for (String id : z) {
            App app = app(id);
            if (app != null) window(ui, caches, look, app, id.equals(front), dock, w, h);
        }
        dock(ui, look, dock, w, h);
        menuBar(ui, look, w, wallpaper, front);
        controlCenter(ui, look, w);
        spotlight(ui, look, w, h);
        notification(ui, look, w);
        PaneOsOverlays.notificationCenter(ui, look, w, h);
        PaneOsOverlays.appsGrid(ui, look, w, h);
        if (sinceUnlock < UNLOCK_MILLIS) {
            // The lock screen lifts away on the GPU while the desktop beneath stays still.
            ui.gpuGroup("unlock", new UiRect(0, 0, w, h), wallRevision, UiTransform.identity(), new UiTransform(0, 0, 1.08F, 1.08F, 0, 0),
                UNLOCK_MILLIS, UiEasing.EASE_IN_OUT, lock -> PaneOsLock.lock(lock, caches.lock, caches.wallpaper, caches.wallpaperArt, wallRevision, wallpaper, look, w, h));
            ui.refreshAfter(UNLOCK_MILLIS - sinceUnlock + 20, new UiRect(0, 0, w, h));
        }
        long shots = (long) ui.numberValue("shot:count", 0);
        float flash = shots == 0 ? 0 : ui.sequence("shot:flash", shots,
            UiTimeline.starting(0).to(.85F, 60, UiEasing.EASE_OUT).to(0, 420, UiEasing.EASE_IN), new UiRect(0, 0, w, h));
        if (flash > 0) g.fill(UiCanvas.alpha(0xFFFFFFFF, Math.round(flash * 255)), 0, 0, w, h);
        float brightness = (float) ui.numberValue("brightness", 1);
        if (brightness < 1) g.fill(UiCanvas.alpha(0xFF000000, Math.round((1 - brightness) * 170)), 0, 0, w, h);
    }

    // ------------------------------------------------------------------ windows

    static UiRect frame(UiContext ui, App app, float w, float h) {
        float top = MENU_H + 8, bottom = h - 96;
        if (ui.booleanValue("max:" + app.id(), false)) return new UiRect(8, top, w - 16, bottom - top);
        float baseW = Math.min(app.width(), w - 40), baseH = Math.min(app.height(), bottom - top);
        // Resizing grows from the top-left corner, so the window's position comes from its default size.
        String id = app.id();
        float width = Math.max(resizable(app) ? 460 : baseW, Math.min(w - 40, baseW
            + (float) (ui.numberValue("size:" + id + ":x", 0) + ui.numberValue("sizew:" + id + ":x", 0))));
        float height = Math.max(resizable(app) ? 320 : baseH, Math.min(bottom - top, baseH
            + (float) (ui.numberValue("size:" + id + ":y", 0) + ui.numberValue("sizeh:" + id + ":y", 0))));
        float x = w * app.centerX() - baseW / 2 + (float) ui.numberValue("win:" + id + ":x", 0);
        float y = top + (bottom - top) * app.centerY() - baseH / 2 + (float) ui.numberValue("win:" + id + ":y", 0);
        x = Math.max(80 - width, Math.min(w - 80, x));
        y = Math.max(top, Math.min(h - 60, y));
        return new UiRect(x, y, width, height);
    }

    static float radius(App app) { return app.id().equals("calculator") ? 22 : 18; }

    static boolean resizable(App app) { return !app.id().equals("calculator") && !app.id().equals("welcome"); }

    static final int UNLOCK_MILLIS = 620;
    static final int OPEN_MILLIS = 480, CLOSE_MILLIS = 200, MINIMIZE_MILLIS = 420;

    /**
     * A settled window paints normally and lifts onto the GPU while dragged. Opening, closing and
     * minimizing paint the window once into a GPU layer that the GPU animates with no page repaints.
     */
    private static void window(UiContext ui, Caches caches, Look look, App app, boolean active, DockLayout dock, float w, float h) {
        String id = app.id();
        boolean open = ui.booleanValue("open:" + id, false), minimized = ui.booleanValue("min:" + id, false);
        UiRect f = frame(ui, app, w, h);
        UiRect bounds = f.inset(-80);
        UiRect icon = dock.icon(id);
        long now = System.currentTimeMillis();
        long shown = now - (long) ui.numberValue("win:" + id + ":shownAt", 0), hidden = now - (long) ui.numberValue("win:" + id + ":hiddenAt", 0);
        UiTransform genie = icon == null ? new UiTransform(0, 0, .1F, .1F, 0, 0)
            : new UiTransform(icon.centerX() - f.centerX(), icon.centerY() - f.centerY(), .1F, .1F, 0, .3F);
        UiTransform settled = UiTransform.identity();
        Object revision = List.of(look, f.width(), f.height(), active);
        if (!open || minimized) {
            if (hidden >= (open ? MINIMIZE_MILLIS : CLOSE_MILLIS)) return;
            UiTransform away = open ? genie : new UiTransform(0, 0, .92F, .92F, 0, 0);
            ui.gpuGroup("window:" + id, bounds, revision, settled, away, open ? MINIMIZE_MILLIS : CLOSE_MILLIS,
                open ? UiEasing.EASE_IN_OUT : UiEasing.EASE_IN, inner -> chrome(inner, caches, look, app, f, active, false));
            ui.refreshAfter((open ? MINIMIZE_MILLIS : CLOSE_MILLIS) - hidden + 20, bounds);
            return;
        }
        if (shown < 0) { ui.refreshAfter(-shown, bounds); return; }
        if (shown < OPEN_MILLIS) {
            boolean fromDock = ui.booleanValue("win:" + id + ":fromDock", false);
            ui.gpuGroup("window:" + id, bounds, revision, fromDock ? genie : new UiTransform(0, 0, .88F, .88F, 0, 0), settled,
                OPEN_MILLIS, fromDock ? UiEasing.EASE_OUT : UiEasing.SPRING, inner -> chrome(inner, caches, look, app, f, active, false));
            ui.refreshAfter(OPEN_MILLIS - shown + 20, bounds);
            return;
        }
        // A window's own selections and fields repaint only the window.
        for (String prefix : SCOPES.getOrDefault(id, List.of())) ui.repaintScope(prefix, f.inset(-6));
        ui.dragLayer("win:" + id, bounds, revision, inner -> chrome(inner, caches, look, app, f, active, true));
    }

    /** State key prefixes each window alone reads. Keys shown anywhere else stay unscoped. */
    private static final java.util.Map<String, List<String>> SCOPES = java.util.Map.ofEntries(
        java.util.Map.entry("files", List.of("files:")), java.util.Map.entry("notes", List.of("notes:", "note:")),
        java.util.Map.entry("calculator", List.of("calc:")), java.util.Map.entry("terminal", List.of("terminal:")),
        java.util.Map.entry("settings", List.of("settings:pane", "wifi", "bt")), java.util.Map.entry("music", List.of("music:side")),
        java.util.Map.entry("photos", List.of("photos:")), java.util.Map.entry("messages", List.of("msg:")),
        java.util.Map.entry("maps", List.of("maps:")), java.util.Map.entry("compass", List.of("compass:")),
        java.util.Map.entry("calendar", List.of("cal:")));

    private static UiRect union(UiRect a, UiRect b) {
        float x = Math.min(a.x(), b.x()), y = Math.min(a.y(), b.y());
        return new UiRect(x, y, Math.max(a.right(), b.right()) - x, Math.max(a.bottom(), b.bottom()) - y);
    }

    private static void chrome(UiContext ui, Caches caches, Look look, App app, UiRect f, boolean active, boolean interactive) {
        UiCanvas g = ui.canvas();
        String id = app.id();
        float r = radius(app);
        g.shadowOutside(active ? 0x5C000000 : 0x30000000, active ? 38 : 24, 0, active ? 20 : 10, f.x(), f.y(), f.width(), f.height(), r);
        if (interactive) {
            ui.hit(id + ":bg", "front:" + id, f.x(), f.y(), f.width(), f.height());
            if (!ui.booleanValue("max:" + id, false))
                ui.dragArea(id + ":bar", "win:" + id, f.x(), f.y(), f.width(), 52, f.inset(-80));
        }
        PaneOsApps.paint(ui, caches, look, app, f, active);
        trafficLights(ui, look, id, f.x() + 20, f.y() + (id.equals("calculator") ? 16 : 22), active);
        g.strokeRoundRect(look.dark() ? 0x40FFFFFF : 0x24000000, .8F, f.x(), f.y(), f.width(), f.height(), r);
        if (interactive && resizable(app) && !ui.booleanValue("max:" + id, false)) {
            // Edges and the corner straddle the outline, as a Mac's resize zones do.
            UiRect moves = f.inset(-80);
            ui.dragArea(id + ":resize-right", "sizew:" + id, f.right() - 4, f.y() + 24, 8, f.height() - 44, moves);
            ui.dragArea(id + ":resize-bottom", "sizeh:" + id, f.x() + 24, f.bottom() - 4, f.width() - 44, 8, moves);
            ui.dragArea(id + ":resize", "size:" + id, f.right() - 16, f.bottom() - 16, 22, 22, moves);
        }
    }

    private static void trafficLights(UiContext ui, Look look, String id, float x, float cy, boolean active) {
        String[] verbs = {"close", "min", "zoom"};
        String[] labels = {"Close", "Minimize", "Zoom"};
        int[] colors = {0xFFFF5F57, 0xFFFEBC2E, 0xFF28C840};
        boolean group = false;
        for (int i = 0; i < 3; i++) group |= ui.hovered(id + ":" + verbs[i]);
        for (int i = 0; i < 3; i++) {
            float cx = x + i * 20, d = 13;
            int color = active || group ? colors[i] : look.dark() ? 0xFF4A4A4E : 0xFFD1D1D6;
            boolean show = group;
            int index = i;
            UiPath outline = ui.canvas().path().arc(cx, cy, d / 2 + 1, 0, 360).close();
            ui.shapedButton(id + ":" + verbs[i], labels[i], verbs[i] + ":" + id, outline, true, (c, state) -> {
                c.circle(color, cx, cy, d / 2);
                c.strokeCircle(0x1F000000, .6F, cx, cy, d / 2 - .3F);
                if (state.pressed()) c.circle(0x33000000, cx, cy, d / 2);
                if (!show) return;
                int glyph = 0x99000000;
                switch (index) {
                    case 0 -> { c.line(glyph, 1.3F, cx - 2.6F, cy - 2.6F, cx + 2.6F, cy + 2.6F); c.line(glyph, 1.3F, cx - 2.6F, cy + 2.6F, cx + 2.6F, cy - 2.6F); }
                    case 1 -> c.line(glyph, 1.4F, cx - 3, cy, cx + 3, cy);
                    default -> { c.polygon(glyph, cx - 3, cy - 3, cx + 1.2F, cy - 3, cx - 3, cy + 1.2F); c.polygon(glyph, cx + 3, cy + 3, cx - 1.2F, cy + 3, cx + 3, cy - 1.2F); }
                }
            });
        }
    }

    // ------------------------------------------------------------------ dock

    /** Unmagnified dock geometry, also the target windows minimize into. */
    record DockLayout(float x, float y, float width, float height, float size, float[] centers) {
        static DockLayout base(UiContext ui, float w, float h) {
            float size = (float) ui.numberValue("dockSize", 54), gap = size * .16F, pad = size * .2F;
            float[] centers = new float[DOCK.length];
            float cursor = 0;
            for (int i = 0; i < DOCK.length; i++) {
                float span = DOCK[i].equals("|") ? gap * 2 + 1 : size;
                centers[i] = cursor + span / 2;
                cursor += span + (i < DOCK.length - 1 ? gap : 0);
            }
            float width = cursor + pad * 2, height = size + pad * 2, x = (w - width) / 2;
            for (int i = 0; i < centers.length; i++) centers[i] += x + pad;
            return new DockLayout(x, h - height - 8, width, height, size, centers);
        }

        UiRect icon(String id) {
            for (int i = 0; i < DOCK.length; i++) if (DOCK[i].equals(id))
                return new UiRect(centers[i] - size / 2, y + (height - size) / 2, size, size);
            return null;
        }
    }

    private static void dock(UiContext ui, Look look, DockLayout base, float w, float h) {
        UiCanvas g = ui.canvas();
        float size = base.size(), gap = size * .16F, pad = size * .2F;
        float maxScale = ui.booleanValue("magnify", true) ? 1.72F : 1;
        UiRect area = new UiRect(base.x() - size * 2, base.y() - size * .9F, base.width() + size * 4, h - base.y() + size * .9F);
        UiPointer pointer = ui.pointer(area.x(), area.y(), area.width(), area.height());
        if (pointer.inside()) ui.setValue("dock:px", (double) pointer.x());
        float hover = ui.animate("dock:hover", pointer.inside() && pointer.y() > base.y() - 6 ? 1F : 0F, 200, UiEasing.EASE_OUT, area);
        float px = (float) ui.numberValue("dock:px", w / 2);
        float[] scales = new float[DOCK.length];
        float total = pad * 2;
        for (int i = 0; i < DOCK.length; i++) {
            float distance = Math.abs(px - base.centers()[i]) / (size * 3.2F);
            float bump = distance >= 1 ? 0 : (float) (Math.cos(distance * Math.PI) + 1) / 2;
            scales[i] = DOCK[i].equals("|") ? 1 : 1 + (maxScale - 1) * hover * bump;
            total += (DOCK[i].equals("|") ? gap * 2 + 1 : size * scales[i]) + (i < DOCK.length - 1 ? gap : 0);
        }
        float x = (w - total) / 2, y = base.y(), height = base.height();
        ui.canvas().liquidGlass(x, y, total, height, Math.min(26, height / 2 - 2), look.glass().withShadow(0x34000000));
        float cursor = x + pad;
        for (int i = 0; i < DOCK.length; i++) {
            String id = DOCK[i];
            if (id.equals("|")) {
                g.line(look.dark() ? 0x40FFFFFF : 0x33000000, 1, cursor + gap, y + pad, cursor + gap, y + height - pad);
                cursor += gap * 2 + 1 + gap;
                continue;
            }
            float s = size * scales[i];
            long launches = (long) ui.numberValue("launch:" + id, 0);
            float bounce = launches == 0 ? 0 : ui.sequence("dock:bounce:" + id, launches,
                UiTimeline.starting(0).to(1, 260, UiEasing.EASE_OUT).to(0, 260, UiEasing.EASE_IN).to(.45F, 200, UiEasing.EASE_OUT)
                    .to(0, 200, UiEasing.EASE_IN), new UiRect(cursor - 4, y - size * 1.8F, s + 8, size * 1.8F + height));
            float iy = y + height - pad - s - bounce * size * .55F, ix = cursor;
            String name = DOCK_NAMES[i];
            float iconSize = s;
            ui.paintedButton("dock:" + id, name, "open:" + id, ix, iy, s, s, s * .22F, true, (c, state) -> {
                PaneOsArt.icon(c, id, ix, iy, iconSize);
                if (state.pressed()) PaneOsArt.squircle(c, ix, iy, iconSize).fill(0x40000000);
            });
            boolean running = id.equals("mail") || id.equals("wayfarer") ? false : ui.booleanValue("open:" + id, false);
            var options = new ArrayList<UiMenuItem>();
            if (running) options.add(new UiMenuItem("window", name, "restore:" + id));
            options.add(new UiMenuItem("open", running ? "New Window" : "Open", "open:" + id).startsGroup());
            options.add(new UiMenuItem("keep", "Keep in Dock", "dock").enabled(false).startsGroup());
            options.add(new UiMenuItem("show", "Show in Files", "go:applications"));
            if (running) options.add(new UiMenuItem("quit", "Quit", "close:" + id).startsGroup());
            if (!id.equals("trash") && !id.equals("downloads")) ui.contextMenu("dock:" + id, options, menuStyle(look));
            if (running) g.circle(look.dark() ? 0xE6FFFFFF : 0xCC1D1D1F, cursor + s / 2, y + height - pad * .42F, 2.2F);
            cursor += s + gap;
        }
    }

    // ------------------------------------------------------------------ menu bar

    private static void menuBar(UiContext ui, Look look, float w, int wallpaper, String front) {
        UiCanvas g = ui.canvas();
        boolean lightTop = PaneOsArt.LIGHT_TOP[wallpaper] && !look.dark();
        int ink = lightTop ? 0xFF1D1D1F : 0xFFFFFFFF;
        int hover = lightTop ? 0x1A000000 : 0x2EFFFFFF;
        g.gradientRect(lightTop ? 0x14FFFFFF : 0x1F000000, 0x00000000, 90, 0, 0, w, MENU_H + 8, 0);
        var menuStyle = menuStyle(look);
        String appName = front.isEmpty() ? "Files" : displayName(front);
        String target = front.isEmpty() ? "files" : front;
        boolean hasWindow = !front.isEmpty();
        float x = 12;
        x = menu(ui, "mb:mark", "PaneOS", x, 34, ink, hover, menuStyle, List.of(
            new UiMenuItem("about", "About PaneOS", "about"),
            new UiMenuItem("settings", "System Settings…", "open:settings").shortcut("⌘,").startsGroup(),
            new UiMenuItem("apps", "App Store…", "apps"),
            new UiMenuItem("recent", "Recent Items", "recent").enabled(false).startsGroup(),
            new UiMenuItem("force", "Force Quit…", "force").shortcut("⌥⌘⎋").enabled(false).startsGroup(),
            new UiMenuItem("sleep", "Sleep", "lock").startsGroup(),
            new UiMenuItem("restart", "Restart…", "restart"),
            new UiMenuItem("shutdown", "Shut Down…", "leave"),
            new UiMenuItem("lock", "Lock Screen", "lock").shortcut("⌃⌘Q").startsGroup(),
            new UiMenuItem("logout", "Log Out Sl0oth…", "lock").shortcut("⇧⌘Q")), (c, cx, cy) -> PaneOsArt.mark(c, cx, cy, 14, ink));
        float nameWidth = g.textWidth(appName, 13.5F, Font.BOLD, PaneOsArt.FONT) + 20;
        x = menu(ui, "mb:app", appName, x, nameWidth, ink, hover, menuStyle, List.of(
            new UiMenuItem("about", "About " + appName, "about:" + target),
            new UiMenuItem("settings", "Settings…", "open:settings").shortcut("⌘,").startsGroup(),
            new UiMenuItem("hide", "Hide " + appName, "min:" + target).enabled(hasWindow).startsGroup(),
            new UiMenuItem("others", "Hide Others", "hideothers:" + target).enabled(hasWindow),
            new UiMenuItem("quit", "Quit " + appName, "close:" + target).enabled(hasWindow).startsGroup()),
            (c, cx, cy) -> c.centeredText(appName, cx, cy, 13.5F, ink, Font.BOLD, PaneOsArt.FONT));
        var windows = new ArrayList<UiMenuItem>(List.of(
            new UiMenuItem("min", "Minimize", "min:" + target).enabled(hasWindow),
            new UiMenuItem("zoom", "Zoom", "zoom:" + target).enabled(hasWindow),
            new UiMenuItem("front", "Bring All to Front", "front:" + target).enabled(hasWindow).startsGroup()));
        boolean first = true;
        for (String id : stack(ui.stringValue("z", ""))) {
            if (!ui.booleanValue("open:" + id, false) || app(id) == null) continue;
            var item = new UiMenuItem("w-" + id, (id.equals(front) ? "✓  " : "     ") + app(id).name(), "restore:" + id);
            windows.add(first ? item.startsGroup() : item);
            first = false;
        }
        var menus = List.of(
            List.of(new UiMenuItem("new", "New Window", "open:" + target).shortcut("⌘N"),
                new UiMenuItem("tab", "New Tab", "compass:").shortcut("⌘T").enabled(front.equals("compass")),
                new UiMenuItem("open", "Open…", "open:files").shortcut("⌘O"),
                new UiMenuItem("close", "Close Window", "close:" + target).shortcut("⌘W").enabled(hasWindow).startsGroup()),
            List.of(new UiMenuItem("undo", "Undo", "edit").shortcut("⌘Z").enabled(false),
                new UiMenuItem("redo", "Redo", "edit").shortcut("⇧⌘Z").enabled(false),
                new UiMenuItem("cut", "Cut", "edit").shortcut("⌘X").enabled(false).startsGroup(),
                new UiMenuItem("copy", "Copy", "edit").shortcut("⌘C").enabled(false),
                new UiMenuItem("paste", "Paste", "edit").shortcut("⌘V").enabled(false),
                new UiMenuItem("all", "Select All", "edit").shortcut("⌘A").enabled(false),
                new UiMenuItem("emoji", "Emoji & Symbols", "edit").shortcut("fn E").enabled(false).startsGroup()),
            List.of(new UiMenuItem("full", "Enter Full Screen", "zoom:" + target).shortcut("⌃⌘F").enabled(hasWindow),
                new UiMenuItem("widgets", ui.booleanValue("widgets", true) ? "Hide Widgets" : "Show Widgets", "widgets").startsGroup()),
            List.of(new UiMenuItem("recents", "Recents", "go:recents").shortcut("⇧⌘F"),
                new UiMenuItem("documents", "Documents", "go:documents").shortcut("⇧⌘O"),
                new UiMenuItem("desktop", "Desktop", "go:desktop").shortcut("⇧⌘D"),
                new UiMenuItem("downloads", "Downloads", "open:downloads").shortcut("⌥⌘L"),
                new UiMenuItem("applications", "Applications", "apps").shortcut("⇧⌘A"),
                new UiMenuItem("folder", "Go to Folder…", "go").shortcut("⇧⌘G").enabled(false).startsGroup()),
            windows,
            List.of(new UiMenuItem("help", "PaneOS Help", "help")));
        String[] titles = {"File", "Edit", "View", "Go", "Window", "Help"};
        for (int i = 0; i < titles.length; i++) {
            String label = titles[i];
            float width = g.textWidth(label, 13.5F, Font.PLAIN, PaneOsArt.FONT) + 20;
            x = menu(ui, "mb:" + label.toLowerCase(Locale.ROOT), label, x, width, ink, hover, menuStyle, menus.get(i),
                (c, cx, cy) -> c.centeredText(label, cx, cy, 13.5F, ink, Font.PLAIN, PaneOsArt.FONT));
        }

        // Status items, right to left.
        LocalDateTime now = LocalDateTime.now();
        String clock = now.format(DateTimeFormatter.ofPattern("EEE MMM d  h:mm a", Locale.ENGLISH));
        float clockWidth = g.textWidth(clock, 13.5F, Font.PLAIN, PaneOsArt.FONT) + 16;
        float right = w - 10 - clockWidth;
        UiRect clockRect = new UiRect(right, 3, clockWidth, MENU_H - 6);
        boolean centerOpen = ui.booleanValue("nc", false);
        ui.paintedButton("mb:clock", "Notification Center", "nc", clockRect.x(), clockRect.y(), clockRect.width(), clockRect.height(), 6, true, (c, s) -> {
            if (centerOpen || s.hovered() || s.pressed()) c.roundRect(centerOpen ? UiCanvas.alpha(hover, 90) : hover, clockRect.x(), clockRect.y(), clockRect.width(), clockRect.height(), 6);
            c.centeredText(clock, clockRect.centerX(), MENU_H / 2, 13.5F, ink, Font.PLAIN, PaneOsArt.FONT);
        });
        ui.refreshAfter(60_000 - (System.currentTimeMillis() % 60_000) + 20, clockRect);
        right = status(ui, "mb:cc", "Control Center", "cc", right - 34, ink, hover, ui.booleanValue("cc", false), (c, cx, cy) -> PaneOsArt.symbol(c, "control", cx, cy, 15, ink));
        right = status(ui, "mb:spot", "Spotlight", "spot", right - 32, ink, hover, ui.booleanValue("spot", false), (c, cx, cy) -> PaneOsArt.symbol(c, "search", cx, cy, 15, ink));
        boolean wifi = ui.booleanValue("wifi", true);
        String network = ui.stringValue("wifi:network", "Home");
        ui.repaintScope("wifi", new UiRect(w / 2, 0, w / 2, MENU_H));
        var networks = new ArrayList<UiMenuItem>();
        networks.add(new UiMenuItem("toggle", wifi ? "Turn Wi-Fi Off" : "Turn Wi-Fi On", "wifi"));
        if (wifi) for (String name : new String[] {"Home", "Lakeside Cabin", "Pane Labs 5G", "Coffee Bar Guest"}) {
            var item = new UiMenuItem("n-" + name, (name.equals(network) ? "✓  " : "     ") + name, "network:" + name);
            networks.add(networks.size() == 1 ? item.startsGroup() : item);
        }
        networks.add(new UiMenuItem("settings", "Wi-Fi Settings…", "settings:0").startsGroup());
        right -= 32;
        menu(ui, "mb:wifi", wifi ? "Wi-Fi: " + network : "Wi-Fi: Off", right, 30, ink, hover, menuStyle, networks,
            (c, cx, cy) -> PaneOsArt.symbol(c, "wifi", cx, cy - 1, 16, wifi ? ink : UiCanvas.alpha(ink, 90)));
        right -= 34;
        menu(ui, "mb:battery", "Battery 84%", right, 30, ink, hover, menuStyle, List.of(
            new UiMenuItem("level", "Battery  84%", "settings:2").enabled(false),
            new UiMenuItem("source", "Power Source: Battery", "settings:2").enabled(false),
            new UiMenuItem("settings", "Battery Settings…", "settings:2").startsGroup()),
            (c, cx, cy) -> PaneOsArt.symbol(c, "battery", cx, cy, 22, ink));
        long timerEnd = (long) ui.numberValue("timer:end", 0);
        long remaining = timerEnd - System.currentTimeMillis();
        if (remaining > 0) {
            String label = String.format(Locale.ROOT, "%d:%02d", remaining / 60_000, remaining / 1000 % 60);
            float tw = g.textWidth(label, 13.5F, Font.PLAIN, PaneOsArt.FONT) + 30;
            UiRect rect = new UiRect(right - tw - 4, 2, tw, MENU_H - 4);
            PaneOsArt.symbol(g, "timer", rect.x() + 10, MENU_H / 2, 15, PaneOsArt.ORANGE);
            g.text(label, rect.x() + 22, MENU_H / 2 - g.textHeight(13.5F, Font.PLAIN, PaneOsArt.FONT) / 2, 13.5F, ink, Font.PLAIN, PaneOsArt.FONT);
            ui.refreshAfter(remaining % 1000 + 5, rect);
        }
    }

    interface Glyph { void draw(UiCanvas canvas, float centerX, float centerY); }

    /** Menus, context menus and status menus share the system menu look. */
    static UiMenuStyle menuStyle(Look look) {
        return UiMenuStyle.builder().textSize(13).fontFamily(PaneOsArt.FONT).rowHeight(23).padding(12, 5)
            .radius(12).rowRadius(6).rowInset(5).borderWidth(.6F)
            .background(look.dark() ? 0xF02A2A2D : 0xF2F6F6F8).text(look.text()).border(look.dark() ? 0x33FFFFFF : 0x1F000000)
            .highlight(look.accent()).build();
    }

    private static float menu(UiContext ui, String id, String label, float x, float width, int ink, int hover,
                              UiMenuStyle style, List<UiMenuItem> items, Glyph glyph) {
        ui.paintedMenuButton(id, label, x, 3, width, MENU_H - 6, 6, true, items, style, (c, state) -> {
            if (state.hovered() || state.pressed() || state.focused()) c.roundRect(hover, x, 3, width, MENU_H - 6, 6);
            glyph.draw(c, x + width / 2, MENU_H / 2);
        });
        return x + width;
    }

    private static float status(UiContext ui, String id, String label, String action, float x, int ink, int hover,
                                boolean active, Glyph glyph) {
        float width = 30;
        ui.paintedButton(id, label, action, x, 3, width, MENU_H - 6, 6, true, (c, state) -> {
            if (active || state.hovered() || state.pressed()) c.roundRect(active ? UiCanvas.alpha(hover, 90) : hover, x, 3, width, MENU_H - 6, 6);
            glyph.draw(c, x + width / 2, MENU_H / 2);
        });
        return x;
    }

    // ------------------------------------------------------------------ control center

    private static void controlCenter(UiContext ui, Look look, float w) {
        boolean open = ui.booleanValue("cc", false);
        float pw = 336, px = w - 12 - pw, py = MENU_H + 6, ph = 492;
        UiRect panel = new UiRect(px, py, pw, ph);
        ui.repaintScope("cc", new UiRect(w / 2, 0, w / 2, panel.bottom() + 40));
        ui.gpuTransition("cc", panel.inset(-40), look, open, new UiTransform(12, -14, .92F, .92F, 0, 0), 260, UiEasing.EASE_OUT,
            inner -> PaneOsControlCenter.paint(inner, look, panel));
    }

    // ------------------------------------------------------------------ spotlight

    record Result(String id, String title, String kind) { }

    static List<Result> results(String query) {
        var list = new ArrayList<Result>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return list;
        for (int i = 0; i < DOCK.length; i++) {
            if (DOCK[i].equals("|")) continue;
            if (DOCK_NAMES[i].toLowerCase(Locale.ROOT).contains(q) || DOCK[i].contains(q)) list.add(new Result(DOCK[i], DOCK_NAMES[i], "Application"));
        }
        String[][] extra = {{"settings", "Wallpaper", "System Settings"}, {"settings", "Liquid Glass", "System Settings"},
            {"settings", "Appearance", "System Settings"}, {"notes", "Shopping list", "Note"}, {"files", "Projects", "Folder"}};
        for (String[] item : extra) if (item[1].toLowerCase(Locale.ROOT).contains(q)) list.add(new Result(item[0], item[1], item[2]));
        if (list.size() > 6) return list.subList(0, 6);
        list.add(new Result("compass", "Search the web for “" + query.trim() + "”", "Compass"));
        return list;
    }

    private static void spotlight(UiContext ui, Look look, float w, float h) {
        boolean open = ui.booleanValue("spot", false);
        float sw = Math.min(680, w - 80), sx = (w - sw) / 2, sy = h * .2F;
        UiRect panel = new UiRect(sx, sy, sw, 56 + 6 * 46 + 24);
        ui.repaintScope("spot", panel.inset(-50));
        ui.repaintScope("spot", new UiRect(w / 2, 0, w / 2, MENU_H));
        ui.gpuTransition("spot", panel.inset(-50), look, open, new UiTransform(0, -18, .94F, .94F, 0, 0), 220, UiEasing.EASE_OUT,
            inner -> spotlightPanel(inner, look, sx, sy, sw));
    }

    private static void spotlightPanel(UiContext ui, Look look, float x, float y, float width) {
        UiCanvas g = ui.canvas();
        ui.repaintScope("spot:q", new UiRect(x - 24, y - 10, width + 48, 64 + 6 * 46 + 60));
        g.liquidGlass(x, y, width, 56, 28, look.glass().withTint(look.dark() ? 0x66202024 : 0x73FFFFFF).withShadow(0x40000000));
        PaneOsArt.symbol(g, "search", x + 30, y + 28, 22, look.secondary());
        var style = UiInputStyle.defaults().font(PaneOsArt.FONT, 22, Font.PLAIN).padding(0, 8)
            .appearance(UiInputAppearance.defaults().background(0x00000000).border(0x00000000).focus(0x00000000)
                .text(look.text()).placeholder(look.tertiary()).caret(look.accent()).selection(UiCanvas.alpha(look.accent(), 90)));
        String query = ui.textField("spot:field", "spot:q", "Spotlight Search", x + 52, y + 8, width - 70, 40, "", 80, style);
        var results = results(query);
        if (results.isEmpty()) return;
        float listY = y + 64, listH = results.size() * 46 + 12;
        g.liquidGlass(x, listY, width, listH, 22, look.glass().withTint(look.dark() ? 0x80202024 : 0x99FFFFFF).withShadow(0x38000000));
        for (int i = 0; i < results.size(); i++) {
            Result result = results.get(i);
            float ry = listY + 6 + i * 46;
            boolean first = i == 0;
            String id = "spot:result:" + i;
            ui.paintedButton(id, result.title(), "open:" + result.id(), x + 6, ry, width - 12, 46, 12, true, (c, state) -> {
                if (first || state.hovered()) c.roundRect(first ? look.accent() : look.hover(), x + 6, ry, width - 12, 46, 12);
                PaneOsArt.icon(c, result.id(), x + 16, ry + 7, 32);
                c.text(result.title(), x + 60, ry + 6, 15, first ? 0xFFFFFFFF : look.text(), Font.PLAIN, PaneOsArt.FONT);
                c.text(result.kind(), x + 60, ry + 25, 12, first ? 0xCCFFFFFF : look.secondary(), Font.PLAIN, PaneOsArt.FONT);
            });
        }
    }

    // ------------------------------------------------------------------ notifications

    private static void notification(UiContext ui, Look look, float w) {
        long at = (long) ui.numberValue("notice:at", 0), now = System.currentTimeMillis();
        if (at == 0 || now > at + 6000) return;
        float nw = 356, nh = 76, nx = w - nw - 14, ny = MENU_H + 10;
        UiRect bounds = new UiRect(nx - 40, ny - 20, nw + 80, nh + 60);
        boolean visible = now >= at && now < at + 5200;
        if (now < at) ui.refreshAfter(at - now, bounds);
        else if (visible) ui.refreshAfter(at + 5200 - now, bounds);
        String app = ui.stringValue("notice:app", "settings");
        String title = ui.stringValue("notice:title", ""), body = ui.stringValue("notice:body", "");
        ui.gpuTransition("notice", bounds, at, visible, new UiTransform(nw + 30, 0, 1, 1, 0, 0), 420, UiEasing.EASE_OUT, inner -> {
            UiCanvas g = inner.canvas();
            g.liquidGlass(nx, ny, nw, nh, 22, look.glass().withTint(look.dark() ? 0x80262628 : 0x99FFFFFF).withShadow(0x3A000000));
            PaneOsArt.icon(g, app, nx + 14, ny + 18, 40);
            g.text(title, nx + 66, ny + 17, 14, look.text(), Font.BOLD, PaneOsArt.FONT);
            g.text("now", nx + nw - 16 - g.textWidth("now", 12, Font.PLAIN, PaneOsArt.FONT), ny + 18, 12, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
            g.text(g.ellipsize(body, nw - 84, 13, Font.PLAIN, PaneOsArt.FONT), nx + 66, ny + 38, 13, look.secondary(), Font.PLAIN, PaneOsArt.FONT);
        });
    }

    static void notify(UiScreen screen, String app, String title, String body) {
        String history = screen.stringValue("notice:history", "");
        String line = title.replace('\t', ' ').replace('\n', ' ') + "\t" + body.replace('\t', ' ').replace('\n', ' ') + "\t" + app + "\t" + System.currentTimeMillis();
        var lines = new ArrayList<>(history.isEmpty() ? List.<String>of() : List.of(history.split("\n")));
        lines.add(line);
        while (lines.size() > 8) lines.removeFirst();
        screen.setValue("notice:history", String.join("\n", lines));
        screen.setValue("notice:app", app);
        screen.setValue("notice:title", title);
        screen.setValue("notice:body", body);
        screen.setValue("notice:at", (double) System.currentTimeMillis());
    }

    static Minecraft client() { return Minecraft.getInstance(); }

    /** Launcher card art: the Tahoe wallpaper under a glass dock and a sample window, cached per size. */
    static void paintPreview(UiCanvas g, UiLayer cache, UiRect r) {
        g.cachedLayer(cache, List.of(Math.round(r.width()), Math.round(r.height())), r, c -> c.withTranslation(r.x(), r.y(), () -> {
            float w = r.width(), h = r.height();
            PaneOsArt.wallpaper(c, 0, false, w, h);
            Look look = Look.of(false, 0, 14, 6);
            float ww = w * .5F, wh = h * .42F, wx = w * .4F, wy = h * .14F;
            c.shadow(0x50000000, 28, 0, 14, wx, wy, ww, wh, 14);
            PaneOsApps.body(c, look, new UiRect(wx, wy, ww, wh), 14, ww * .3F);
            for (int i = 0; i < 3; i++) c.circle(new int[] {0xFFFF5F57, 0xFFFEBC2E, 0xFF28C840}[i], wx + 18 + i * 16, wy + 18, 5.5F);
            for (int i = 0; i < 5; i++) c.roundRect(0x22000000, wx + 16, wy + 40 + i * 20, ww * .3F - 28, 9, 4);
            for (int i = 0; i < 4; i++) c.roundRect(0x14000000, wx + ww * .34F + 16, wy + 30 + i * 24, ww * .5F - i * 20, 10, 5);
            String[] icons = {"files", "compass", "messages", "mail", "photos", "music", "settings"};
            float size = Math.min(40, w / 14), pad = size * .22F, dw = icons.length * (size + pad) + pad, dx = (w - dw) / 2, dy = h - size - pad * 2 - 10;
            c.liquidGlass(dx, dy, dw, size + pad * 2, (size + pad * 2) / 2 - 2, look.glass());
            for (int i = 0; i < icons.length; i++) PaneOsArt.icon(c, icons[i], dx + pad + i * (size + pad), dy + pad, size);
            c.gradientRect(0x22000000, 0x00000000, 90, 0, 0, w, 22, 0);
            PaneOsArt.mark(c, 16, 11, 10, 0xFFFFFFFF);
            c.text("Files", 30, 4, 11, 0xFFFFFFFF, Font.BOLD, PaneOsArt.FONT);
        }));
    }
}
