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

    void tick(Minecraft client) {
        if (finished) return;
        try {
            if (screen == null) {
                if (!(client.gui.screen() instanceof TitleScreen)) return;
                screen = PaneOsShowcase.createScreen(client.gui.screen());
                client.gui.setScreen(screen);
                return;
            }
            if (screen.paintCount() == 0) return;
            tick++;
            switch (tick) {
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
                    var bar = screen.regionBounds("files:bar").orElseThrow();
                    paints = screen.paintCount(); partials = screen.partialPaintCount();
                    press(bar.centerX(), bar.y() + 20);
                }
                case 81 -> drag(screen.regionBounds("files:bar").orElseThrow().centerX() + 120, screen.regionBounds("files:bar").orElseThrow().y() + 50);
                case 82 -> drag(screen.regionBounds("files:bar").orElseThrow().centerX() + 120, screen.regionBounds("files:bar").orElseThrow().y() + 40);
                case 83 -> {
                    var bar = screen.regionBounds("files:bar").orElseThrow();
                    release(bar.centerX(), bar.y() + 20);
                    int full = (screen.paintCount() - paints) - (screen.partialPaintCount() - partials);
                    check(screen.numberValue("win:files:x", 0) > 200, "title bar drags the window (" + Math.round(screen.numberValue("win:files:x", 0)) + ")");
                    check(full <= 1, "window drag repaints only where the window was and goes (" + full + " full paints)");
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
                case 330 -> { paints = screen.paintCount(); partials = screen.partialPaintCount(); }
                case 390 -> {
                    int full = (screen.paintCount() - paints) - (screen.partialPaintCount() - partials);
                    check(full == 0, "settled desktop does no full repaints; only the clock ticks (" + full + " full, "
                        + (screen.partialPaintCount() - partials) + " partial)");
                    SurfaceGpuCheck.verify(screen, result -> check(result.contains("PASS"), result));
                    paints = screen.paintCount(); partials = screen.partialPaintCount();
                    screen.invalidate();
                }
                case 391, 392, 393 -> {
                    // Time the full repaint itself, not a later clock tick.
                    if (fullRaster == 0 && screen.paintCount() > paints && screen.partialPaintCount() == partials)
                        fullRaster = screen.performanceSnapshot().lastRasterNanos();
                    if (tick == 392 && fullRaster != 0) { paints = screen.paintCount(); partials = screen.partialPaintCount(); screen.invalidate(); warm = true; }
                    if (tick == 393 && warm && screen.paintCount() > paints && screen.partialPaintCount() == partials)
                        warmRaster = screen.performanceSnapshot().lastRasterNanos();
                }
                case 394 -> System.out.printf("[PaneOsTest] full repaint first=%.1fms again=%.1fms%n", fullRaster / 1e6, warmRaster / 1e6);
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
                    int step = (tick - 440) / 34, phase = (tick - 440) % 34;
                    if (tick >= 440 && step < TOUR.length) {
                        if (phase == 0) {
                            if (step > 0) PaneOsEvents.handleAction(screen, "close:" + TOUR[step - 1]);
                            PaneOsEvents.openApp(screen, TOUR[step]);
                        }
                        if (phase == 30) capture(client, "paneos-app-" + TOUR[step]);
                    }
                    if (tick == 440 + TOUR.length * 34) finish(client);
                }
            }
        } catch (Throwable error) {
            finished = true;
            error.printStackTrace();
            System.out.println("[PaneOsTest] complete FAIL " + error);
            client.stop();
        }
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
