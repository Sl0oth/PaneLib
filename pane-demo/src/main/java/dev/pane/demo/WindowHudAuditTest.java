package dev.pane.demo;

import com.mojang.blaze3d.platform.InputConstants;
import dev.pane.ui.*;
import java.lang.reflect.Method;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

/**
 * Window and world acceptance: maximize, restore, fullscreen and GUI scale keep the launcher sized to
 * the display with clicks landing through Minecraft's own mouse handler; in a fresh survival world a
 * HUD hides only the vanilla elements it replaces and gives them back when toggled, hidden or removed.
 */
final class WindowHudAuditTest {
    private static final List<Identifier> WATCHED = List.of(VanillaHudElements.HOTBAR, VanillaHudElements.HEALTH_BAR,
        VanillaHudElements.FOOD_BAR, VanillaHudElements.CROSSHAIR);
    private final Map<Identifier, Long> drawn = new HashMap<>();
    private final Map<Identifier, Long> baseline = new HashMap<>();
    private UiScreen gallery;
    private TitleScreen parent;
    private int stage, checks, windowWidth, windowHeight;
    private long deadline, frames, pendingWait;
    private boolean failed, done;
    private String pendingClick;
    private Runnable pendingNext;

    /** Counts each watched vanilla element's own renderer; a HUD replacing it wraps this from outside. */
    void install() {
        for (var id : WATCHED)
            HudElementRegistry.replaceElement(id, original -> (graphics, delta) -> {
                drawn.merge(id, 1L, Long::sum);
                original.extractRenderState(graphics, delta);
            });
    }

    void tick(Minecraft c) {
        if (done || System.nanoTime() < deadline || c.gui.overlay() != null) return;
        try {
            var window = c.getWindow();
            if (pendingClick != null) { finishClick(c); return; }
            switch (stage) {
                case 0 -> {
                    if (!(c.gui.screen() instanceof TitleScreen title)) return;
                    parent = title;
                    window.setWindowed(1280, 800);
                    gallery = ExampleGallery.createScreen(parent);
                    c.gui.setScreen(gallery);
                    advance(1500);
                }
                case 1 -> {
                    windowWidth = window.getScreenWidth(); windowHeight = window.getScreenHeight();
                    fits(c, "windowed");
                    clickThen(c, "windowed", 1800, () -> GLFW.glfwMaximizeWindow(window.handle()));
                }
                case 2 -> {
                    check(GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE
                        && window.getScreenWidth() > windowWidth, "window maximizes (" + window.getScreenWidth() + "x" + window.getScreenHeight() + ")");
                    fits(c, "maximized");
                    capture(c, "window-maximized");
                    clickThen(c, "maximized", 1800, () -> GLFW.glfwRestoreWindow(window.handle()));
                }
                case 3 -> {
                    check(Math.abs(window.getScreenWidth() - windowWidth) <= 2 && Math.abs(window.getScreenHeight() - windowHeight) <= 2,
                        "restore returns to the windowed size (" + window.getScreenWidth() + "x" + window.getScreenHeight() + ")");
                    fits(c, "restored");
                    clickThen(c, "restored", 1200, () -> { c.options.guiScale().set(2); c.resizeGui(); });
                }
                case 4 -> {
                    check(window.getGuiScale() == 2, "GUI scale 2 applies");
                    fits(c, "GUI scale 2");
                    clickThen(c, "GUI scale 2", 1200, () -> { c.options.guiScale().set(0); c.resizeGui(); });
                }
                case 5 -> {
                    fits(c, "automatic GUI scale");
                    window.toggleFullScreen();
                    c.options.fullscreen().set(window.isFullscreen());
                    advance(2500);
                }
                case 6 -> {
                    check(window.isFullscreen(), "fullscreen engages (" + window.getScreenWidth() + "x" + window.getScreenHeight() + ")");
                    fits(c, "fullscreen");
                    capture(c, "window-fullscreen");
                    clickThen(c, "fullscreen", 2500, () -> { window.toggleFullScreen(); c.options.fullscreen().set(window.isFullscreen()); });
                }
                case 7 -> {
                    check(!window.isFullscreen(), "fullscreen exits");
                    window.setWindowed(1280, 800);
                    advance(1200);
                }
                case 8 -> {
                    fits(c, "back to windowed");
                    clickThen(c, "back to windowed", 300, () -> { });
                }
                case 9 -> {
                    c.gui.setScreen(parent);
                    String id = "window-hud-audit-" + System.currentTimeMillis();
                    c.createWorldOpenFlows().createFreshLevel(id, new LevelSettings("Disposable window and HUD audit", GameType.SURVIVAL,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(9824L, false, false), WorldPresets::createNormalWorldDimensions, null);
                    c.options.renderDistance().set(5); c.options.simulationDistance().set(5); c.options.pauseOnLostFocus = false;
                    advance(1200);
                }
                case 10 -> {
                    if (c.player == null || c.level == null) return;
                    c.gui.setScreen(null);
                    ExampleHudController.select("none");
                    advance(3000);
                }
                case 11 -> { mark(); advance(1000); }
                case 12 -> {
                    check(allDrew(WATCHED), "vanilla hotbar, hearts, food and crosshair draw without a PaneLib HUD");
                    ExampleHudController.select("wayfarer-hud");
                    advance(2500);
                }
                case 13 -> { mark(); frames = belt().snapshot().frames(); advance(1000); }
                case 14 -> {
                    check(belt().snapshot().hasSurface() && belt().snapshot().frames() > frames, "Wayfarer HUD draws in the world");
                    check(noneDrew(WATCHED), "it hides exactly the vanilla elements it replaces");
                    capture(c, "world-hud-on");
                    toggleKey();
                    advance(900);
                }
                case 15 -> { mark(); advance(1000); }
                case 16 -> {
                    check(ExampleHudController.wayfarer().huds().stream().noneMatch(h -> h.snapshot().hasSurface()), "J toggles every HUD surface off");
                    check(allDrew(WATCHED), "toggled off, vanilla HUD comes back");
                    capture(c, "world-hud-off");
                    toggleKey();
                    advance(900);
                }
                case 17 -> {
                    check(belt().enabled(), "J toggles the HUD back on");
                    c.options.guiScale().set(2);
                    c.resizeGui();
                    advance(1500);
                }
                case 18 -> { mark(); frames = belt().snapshot().frames(); advance(1000); }
                case 19 -> {
                    var bounds = belt().snapshot().bounds();
                    check(belt().snapshot().frames() > frames && bounds != null && bounds.x() >= 0 && bounds.y() >= 0
                        && bounds.right() <= window.getGuiScaledWidth() + 1 && bounds.bottom() <= window.getGuiScaledHeight() + 1,
                        "after a GUI scale change the HUD stays on screen (" + bounds + " in " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + ")");
                    check(noneDrew(WATCHED), "replaced vanilla elements stay hidden across the scale change");
                    capture(c, "world-hud-scale2");
                    c.options.guiScale().set(0);
                    c.resizeGui();
                    c.gui.hud.toggle();
                    advance(600);
                }
                case 20 -> { mark(); frames = belt().snapshot().frames(); advance(800); }
                case 21 -> {
                    check(c.gui.hud.isHidden() && belt().snapshot().frames() == frames && noneDrew(WATCHED), "F1 hides the PaneLib HUD with vanilla's");
                    c.gui.hud.toggle();
                    advance(600);
                }
                case 22 -> { frames = belt().snapshot().frames(); advance(800); }
                case 23 -> {
                    check(belt().snapshot().frames() > frames, "showing the GUI again resumes the HUD");
                    ExampleHudController.select("none");
                    advance(900);
                }
                case 24 -> { mark(); advance(1000); }
                case 25 -> {
                    check(allDrew(WATCHED), "removing the HUD restores vanilla");
                    c.disconnect(new TitleScreen(), false);
                    advance(800);
                }
                default -> {
                    if (c.level != null) return;
                    check(true, "the disposable world disconnects");
                    finish(c);
                }
            }
        } catch (Throwable error) {
            error.printStackTrace();
            check(false, "unexpected " + error);
            finish(c);
        }
    }

    /** The launcher's surface matches the display and every target lies inside the window. */
    private void fits(Minecraft c, String label) {
        var window = c.getWindow();
        var quality = gallery.rasterQuality();
        check(gallery.width == window.getGuiScaledWidth() && gallery.height == window.getGuiScaledHeight(),
            label + ": screen follows the scaled window (" + gallery.width + "x" + gallery.height + ")");
        // Minecraft rounds the GUI up to whole units, so its area can overhang the framebuffer slightly.
        int areaWidth = window.getGuiScaledWidth() * window.getGuiScale(), areaHeight = window.getGuiScaledHeight() * window.getGuiScale();
        check(Math.abs(quality.pixelWidth() - areaWidth) <= 2 && Math.abs(quality.pixelHeight() - areaHeight) <= 2 && !quality.upscaled(),
            label + ": surface is drawn at full resolution (" + quality.pixelWidth() + "x" + quality.pixelHeight()
                + " for a " + areaWidth + "x" + areaHeight + " GUI area)");
        for (String id : new String[] {"paneos", "wayfarer", "mail", "back"}) {
            var r = gallery.regionBounds(id).orElse(null);
            check(r != null && r.x() >= 0 && r.y() >= 0 && r.right() <= gallery.width * gallery.layoutScale() + 1
                && r.bottom() <= gallery.height * gallery.layoutScale() + 1, label + ": " + id + " is on screen");
        }
    }

    /** Clicks Pane Mail through Minecraft's mouse handler; the launcher opens it on a later tick. */
    private void clickThen(Minecraft c, String label, long wait, Runnable next) throws Exception {
        var r = gallery.regionBounds("mail").orElseThrow();
        var window = c.getWindow();
        double x = r.centerX() / gallery.layoutScale() * window.getScreenWidth() / window.getGuiScaledWidth();
        double y = r.centerY() / gallery.layoutScale() * window.getScreenHeight() / window.getGuiScaledHeight();
        Method move = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
        Method button = MouseHandler.class.getDeclaredMethod("onButton", long.class, MouseButtonInfo.class, int.class);
        move.setAccessible(true); button.setAccessible(true);
        move.invoke(c.mouseHandler, window.handle(), x, y);
        var left = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
        button.invoke(c.mouseHandler, window.handle(), left, GLFW.GLFW_PRESS);
        button.invoke(c.mouseHandler, window.handle(), left, GLFW.GLFW_RELEASE);
        pendingClick = label; pendingNext = next; pendingWait = wait;
        advance(900);
    }

    /** The click opened Pane Mail and Escape returns; then the stage's next window change runs. */
    private void finishClick(Minecraft c) {
        String label = pendingClick;
        pendingClick = null;
        var opened = c.gui.screen();
        check(opened instanceof UiScreen mail && mail != gallery && mail.regionBounds("compose").isPresent(),
            label + ": a real click on Pane Mail opens it");
        if (opened instanceof UiScreen mail && mail != gallery) mail.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        check(c.gui.screen() == gallery, label + ": Escape returns to the launcher");
        pendingNext.run();
        deadline = System.nanoTime() + pendingWait * 1_000_000;
    }

    private UiHud belt() { return ExampleHudController.wayfarer().belt(); }
    private void mark() { baseline.clear(); baseline.putAll(drawn); }
    private long since(Identifier id) { return drawn.getOrDefault(id, 0L) - baseline.getOrDefault(id, 0L); }
    private boolean allDrew(List<Identifier> ids) { return ids.stream().allMatch(id -> since(id) > 0); }
    private boolean noneDrew(List<Identifier> ids) { return ids.stream().allMatch(id -> since(id) == 0); }
    private void toggleKey() { KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J)); }
    private void advance(long millis) { stage++; deadline = System.nanoTime() + millis * 1_000_000; }

    private void capture(Minecraft c, String name) {
        Screenshot.grab(c.gameDirectory, name + ".png", c.gameRenderer.mainRenderTarget(), 1, message -> { });
    }

    private void check(boolean passed, String message) {
        checks++;
        failed |= !passed;
        System.out.println("[WindowHudTest] " + message + ": " + (passed ? "PASS" : "FAIL"));
    }

    private void finish(Minecraft c) {
        done = true;
        System.out.println("[WindowHudTest] complete " + (failed ? "FAIL" : "PASS") + " checks=" + checks);
        c.stop();
    }
}
