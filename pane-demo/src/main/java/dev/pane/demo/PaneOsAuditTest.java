package dev.pane.demo;

import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

/** PaneOS acceptance: windows, drag cost, Control Center, Spotlight, apps, dock magnification and idle cost. */
final class PaneOsAuditTest {
    private UiScreen screen;
    private int tick, checks, paints, partials;
    private boolean failed, finished, warm;
    private long fullRaster, warmRaster;
    private float width;
    private UiRect bar;

    void tick(Minecraft client) {
        if (finished) return;
        try {
            if (screen == null) {
                if (!(client.gui.screen() instanceof TitleScreen) || client.gui.overlay() != null) return;
                screen = PaneOsShowcase.createScreen(client.gui.screen());
                client.gui.setScreen(screen);
                return;
            }
            if (screen.paintCount() == 0) return;
            tick++;
            if (tick <= 100) { startUp(client); return; }
            int t = tick - 100;
            switch (t) {
                case 50 -> {
                    capture(client, "paneos-desktop");
                    check(screen.regionBounds("dock:files").isPresent() && screen.regionBounds("mb:cc").isPresent(), "dock and menu bar are live");
                    click("dock:files");
                    check(screen.booleanValue("open:files", false), "dock opens Files");
                }
                case 56 -> capture(client, "paneos-files-opening");
                case 80 -> {
                    capture(client, "paneos-files");
                    check(screen.regionBounds("files:bar").isPresent(), "settled window takes input");
                    bar = screen.regionBounds("files:bar").orElseThrow();
                    paints = screen.paintCount(); partials = screen.partialPaintCount();
                    press(bar.centerX(), bar.y() + 20);
                }
                case 81 -> drag(bar.centerX() + 120, bar.y() + 50);
                case 82 -> {
                    drag(bar.centerX() + 250, bar.y() + 40);
                    check(screen.regionBounds("files:bar").isEmpty(), "a moving window is lifted onto the GPU, off the page");
                }
                case 83 -> {
                    release(bar.centerX() + 250, bar.y() + 40);
                    int full = (screen.paintCount() - paints) - (screen.partialPaintCount() - partials);
                    check(screen.numberValue("win:files:x", 0) > 200, "title bar drags the window (" + Math.round(screen.numberValue("win:files:x", 0)) + ")");
                    check(full == 0, "window drag does no full repaints (" + full + ")");
                }
                case 84 -> check(screen.regionBounds("files:bar").isPresent(), "a dropped window is back on the page and takes input");
                case 85 -> { var corner = screen.regionBounds("files:resize").orElseThrow(); width = screen.regionBounds("files:bg").orElseThrow().width(); press(corner.centerX(), corner.centerY()); }
                case 86 -> { var corner = screen.regionBounds("files:resize").orElseThrow(); drag(corner.centerX() + 100, corner.centerY() + 40); }
                case 87 -> { var corner = screen.regionBounds("files:resize").orElseThrow(); release(corner.centerX(), corner.centerY()); }
                case 89 -> {
                    float grown = screen.regionBounds("files:bg").orElseThrow().width() - width;
                    check(Math.abs(grown - 100) < 2, "corner drag resizes the window (" + Math.round(grown) + ")");
                }
                case 90 -> click("files:side:5");
                case 96 -> {
                    check(screen.numberValue("files:side", 0) == 5, "sidebar selects Downloads");
                    click("dock:settings");
                }
                case 130 -> {
                    click("settings:side:5");
                    check(screen.numberValue("settings:pane", 0) == 5, "settings shows Liquid Glass");
                }
                case 140 -> { capture(client, "paneos-settings-glass"); click("mb:cc"); }
                case 160 -> {
                    capture(client, "paneos-control-center");
                    check(screen.booleanValue("cc", false), "menu bar opens Control Center");
                    click("cc:darkmode");
                    check(screen.booleanValue("dark", false), "Control Center switches to dark mode");
                }
                case 180 -> {
                    capture(client, "paneos-dark");
                    click("cc:wifi");
                    check(!screen.booleanValue("wifi", true), "Wi-Fi module toggles");
                    screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                    check(!screen.booleanValue("cc", true), "Escape closes Control Center first");
                    check(client.gui.screen() == screen, "Escape with a popover open stays in PaneOS");
                }
                case 190 -> screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, GLFW.GLFW_MOD_CONTROL));
                case 200 -> {
                    check(screen.booleanValue("spot", false), "Ctrl+Space opens Spotlight");
                    check(screen.focusedId().orElse("").equals("spot:field"), "Spotlight field takes focus once it is painted");
                    for (char ch : "calc".toCharArray()) screen.charTyped(new net.minecraft.client.input.CharacterEvent(ch));
                }
                case 206 -> {
                    capture(client, "paneos-spotlight");
                    check(screen.stringValue("spot:q", "").equals("calc"), "typing filters Spotlight");
                    screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                    check(screen.booleanValue("open:calculator", false) && !screen.booleanValue("spot", true), "Enter opens the first result");
                }
                // Key ids are row then column: 6 is 22, × is 13, 7 is 10 and = is 43.
                case 240 -> { for (String key : new String[] {"22", "13", "10", "43"}) click("calculator:" + key); }
                case 244 -> {
                    check(screen.stringValue("calc:display", "").equals("42"), "calculator computes 6 × 7 (" + screen.stringValue("calc:display", "") + ")");
                    capture(client, "paneos-calculator");
                    click("calculator:min");
                    check(screen.booleanValue("min:calculator", false), "yellow light minimizes the front window");
                }
                case 252 -> capture(client, "paneos-minimizing");
                case 280 -> {
                    var dock = screen.regionBounds("dock:photos").orElseThrow();
                    pointerAt(client, dock.centerX(), dock.centerY());
                }
                case 292 -> {
                    capture(client, "paneos-dock-magnify");
                    var dock = screen.regionBounds("dock:photos").orElseThrow();
                    check(dock.width() > 70, "dock magnifies toward the pointer (" + Math.round(dock.width()) + ")");
                    pointerAt(client, 700, 420);
                }
                case 300 -> {
                    rightClick(400, 780);
                    check(screen.openMenuId().orElse("").equals("context:desk:bg"), "right-clicking the desktop opens its menu");
                }
                case 304 -> {
                    capture(client, "paneos-context-menu");
                    click("menu:context:desk:bg:folder");
                    check(screen.numberValue("desk:folders", 0) == 1, "New Folder adds a desktop folder");
                    click("mb:clock");
                    check(screen.booleanValue("nc", false), "clock opens Notification Center");
                }
                case 316 -> {
                    capture(client, "paneos-notification-center");
                    screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                    check(!screen.booleanValue("nc", true), "Escape closes Notification Center");
                    click("dock:launch");
                    check(screen.booleanValue("apps", false), "Apps opens the app grid");
                }
                case 328 -> {
                    capture(client, "paneos-apps");
                    click("apps:maps");
                    check(screen.booleanValue("open:maps", false) && !screen.booleanValue("apps", true), "a grid icon opens its app");
                    PaneOsEvents.handleAction(screen, "close:maps");
                }
                case 360 -> { paints = screen.paintCount(); partials = screen.partialPaintCount(); }
                case 390 -> {
                    int full = (screen.paintCount() - paints) - (screen.partialPaintCount() - partials);
                    check(full == 0, "settled desktop does no full repaints; only the clock ticks (" + full + " full, "
                        + (screen.partialPaintCount() - partials) + " partial)");
                    SurfaceGpuCheck.verify(screen, result -> check(result.contains("PASS"), result));
                    paints = screen.paintCount(); partials = screen.partialPaintCount();
                    screen.invalidate();
                }
                case 391, 392, 393, 394, 395, 396, 397, 398 -> {
                    // Time a full repaint itself: a paint with no partial paint alongside it.
                    boolean clean = screen.paintCount() == paints + 1 && screen.partialPaintCount() == partials;
                    if (clean && fullRaster == 0) fullRaster = screen.performanceSnapshot().lastRasterNanos();
                    else if (clean) warmRaster = screen.performanceSnapshot().lastRasterNanos();
                    if (warmRaster == 0) { paints = screen.paintCount(); partials = screen.partialPaintCount(); screen.invalidate(); }
                }
                case 399 -> System.out.printf("[PaneOsTest] full repaint first=%.1fms again=%.1fms%n", fullRaster / 1e6, warmRaster / 1e6);
                case 400 -> {
                    for (String id : new String[] {"files", "settings", "calculator"}) PaneOsEvents.handleAction(screen, "close:" + id);
                    click("dock:mail");
                    check(client.gui.screen() != screen && client.gui.screen() instanceof UiScreen, "Mail opens its own screen from the dock");
                }
                case 430 -> {
                    client.gui.screen().keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                    check(client.gui.screen() == screen, "Escape from Mail returns to PaneOS");
                }
                default -> {
                    int step = (t - 440) / 34, phase = (t - 440) % 34;
                    if (t >= 440 && step < TOUR.length) {
                        if (phase == 0) {
                            if (step > 0) PaneOsEvents.handleAction(screen, "close:" + TOUR[step - 1]);
                            PaneOsEvents.openApp(screen, TOUR[step]);
                        }
                        if (phase == 30) capture(client, "paneos-app-" + TOUR[step]);
                    }
                    if (t == 440 + TOUR.length * 34) finish(client);
                }
            }
        } catch (Throwable error) {
            finished = true;
            error.printStackTrace();
            System.out.println("[PaneOsTest] complete FAIL " + error);
            client.stop();
        }
    }

    private void startUp(Minecraft client) {
        switch (tick) {
            case 12 -> capture(client, "paneos-boot");
            case 60 -> {
                check(screen.booleanValue("locked", false), "PaneOS starts locked after the boot screen");
                check(screen.focusedId().orElse("").equals("lock:field"), "password field takes focus once it appears");
                for (char ch : "pane".toCharArray()) screen.charTyped(new net.minecraft.client.input.CharacterEvent(ch));
            }
            case 64 -> {
                capture(client, "paneos-lock");
                check(screen.stringValue("lock:pw", "").equals("pane"), "typing reaches the password field");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                check(!screen.booleanValue("locked", true), "Return unlocks");
            }
            case 69 -> capture(client, "paneos-unlocking");
            case 92 -> {
                capture(client, "paneos-welcome");
                check(screen.booleanValue("open:welcome", false) && screen.regionBounds("welcome:continue").isPresent(),
                    "the first unlock opens the welcome window");
            }
            case 96 -> {
                click("welcome:continue");
                check(!screen.booleanValue("open:welcome", true), "Continue closes the welcome window");
            }
            default -> { }
        }
    }

    private void rightClick(float x, float y) {
        var event = new MouseButtonEvent(x / screen.layoutScale(), y / screen.layoutScale(), new MouseButtonInfo(1, 0));
        screen.mouseMoved(event.x(), event.y());
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static final String[] TOUR = {"notes", "music", "photos", "messages", "maps", "compass", "calendar", "terminal"};

    private void finish(Minecraft client) {
        var sample = screen.performanceSnapshot();
        System.out.printf("[PaneOsTest] lenses reused=%d rendered=%d, last paint rasterMs=%.3f uploadMs=%.3f%n",
            screen.glassLensReuseCount(), screen.glassLensRenderCount(), sample.lastRasterNanos() / 1e6, sample.lastUploadNanos() / 1e6);
        finished = true;
        System.out.println("[PaneOsTest] complete " + (failed ? "FAIL" : "PASS") + " checks=" + checks);
        if (!Boolean.getBoolean("pane.demo.paneOsKeepOpen")) client.stop();
    }

    private void pointerAt(Minecraft client, float layoutX, float layoutY) throws Exception {
        var quality = screen.rasterQuality();
        var move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
        move.setAccessible(true);
        move.invoke(client.mouseHandler, client.getWindow().handle(),
            layoutX / quality.layoutWidth() * client.getWindow().getScreenWidth(),
            layoutY / quality.layoutHeight() * client.getWindow().getScreenHeight());
    }

    private MouseButtonEvent mouse(float x, float y) {
        return new MouseButtonEvent(x / screen.layoutScale(), y / screen.layoutScale(), new MouseButtonInfo(0, 0));
    }

    private void press(float x, float y) { screen.mouseMoved(x / screen.layoutScale(), y / screen.layoutScale()); screen.mouseClicked(mouse(x, y), false); }
    private void drag(float x, float y) { screen.mouseDragged(mouse(x, y), 1, 0); }
    private void release(float x, float y) { screen.mouseReleased(mouse(x, y)); }

    private void click(String id) {
        var r = screen.regionBounds(id).orElseThrow(() -> new IllegalStateException("missing " + id));
        press(r.centerX(), r.centerY());
        release(r.centerX(), r.centerY());
    }

    private void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> { });
    }

    private void check(boolean passed, String message) {
        checks++;
        failed |= !passed;
        System.out.println("[PaneOsTest] " + message + ": " + (passed ? "PASS" : "FAIL"));
    }
}
