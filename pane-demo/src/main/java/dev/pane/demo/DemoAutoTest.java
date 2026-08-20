package dev.pane.demo;

import dev.pane.ui.UiRect;
import dev.pane.ui.UiScreen;
import java.io.File;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Development-only harness. Enabled with {@code -Dpane.demo.autotest=true}, it loads a
 * throwaway singleplayer world, opens the showcase, and captures each page so the native
 * player and ItemStack overlays can be verified where they actually run: inside a world.
 * <p>
 * Nothing in this class is referenced unless the system property is set, and it lives in the
 * demo source set rather than the API, so consuming mods never see it.
 */
final class DemoAutoTest {
    private static final String LEVEL_ID = "pane_autotest";
    private static final String[] CAPTURE_PAGES = {"loadout", "discover", "waypoints", "performance", "settings"};

    private enum Stage { WAIT_FOR_TITLE, LOADING_WORLD, SETTLING, CAPTURING, INTERACTION, HOVER_JUMP, ALT_SHELL, RESIZE, IDLE_CHECK, DONE }

    private Stage stage = Stage.WAIT_FOR_TITLE;
    private int ticks;
    private int captureIndex;
    private int idlePaintCount = -1;
    private double idleMouseX;
    private double idleMouseY;
    private int resizeStep;

    private static final int[][] RESIZE_SIZES = {{900, 560}, {430, 300}, {1024, 640}, {1600, 1000}};
    private int hoverStep;
    private int beforeHoverPaints;
    private float hoverBaseline;

    static boolean enabled() {
        return Boolean.getBoolean("pane.demo.autotest");
    }

    void tick(Minecraft client) {
        ticks++;
        switch (stage) {
            case WAIT_FOR_TITLE -> {
                if (ticks > 60 && client.gui.screen() instanceof TitleScreen) {
                    log("loading test world");
                    loadWorld(client);
                    stage = Stage.LOADING_WORLD;
                    ticks = 0;
                }
            }
            case LOADING_WORLD -> {
                if (client.player != null && client.level != null) {
                    log("world ready, settling");
                    stage = Stage.SETTLING;
                    ticks = 0;
                } else if (ticks > 20 * 90) {
                    log("FAILED: world did not load within 90s");
                    stage = Stage.DONE;
                }
            }
            case SETTLING -> {
                if (ticks > 60) {
                    client.gui.setScreen(NativeDemoShowcase.createScreen());
                    stage = Stage.CAPTURING;
                    captureIndex = 0;
                    ticks = 0;
                }
            }
            case CAPTURING -> {
                if (ticks < 25) return;
                ticks = 0;
                if (client.gui.screen() instanceof UiScreen screen) {
                    if (captureIndex > 0) {
                        capture(client, "pane-live-" + CAPTURE_PAGES[captureIndex - 1]);
                        log("captured " + CAPTURE_PAGES[captureIndex - 1] + " paints=" + screen.paintCount());
                    }
                    if (captureIndex >= CAPTURE_PAGES.length) {
                        screen.showPage("settings");
                        stage = Stage.INTERACTION;
                        return;
                    }
                    screen.showPage(CAPTURE_PAGES[captureIndex]);
                    captureIndex++;
                } else {
                    log("FAILED: showcase screen was replaced");
                    stage = Stage.DONE;
                }
            }
            case INTERACTION -> {
                if (ticks < 30) return;
                ticks = 0;
                if (client.gui.screen() instanceof UiScreen screen) {
                    checkSlider(screen);
                    checkToggle(screen);
                    screen.showPage("discover");
                }
                hoverStep = 0;
                stage = Stage.HOVER_JUMP;
                return;
            }
            // Hovering a control must not move page content. This is the regression guard for
            // the shared animation clock, where every hover replayed the page entrance and the
            // whole layout visibly jumped.
            case HOVER_JUMP -> {
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    stage = Stage.DONE;
                    return;
                }
                if (hoverStep == 0) {
                    if (ticks < 30) return;
                    Optional<UiRect> anchor = screen.regionBounds("install-sodium");
                    if (anchor.isEmpty()) {
                        log("FAILED: install-sodium was not painted");
                        stage = Stage.ALT_SHELL;
                        return;
                    }
                    hoverBaseline = anchor.get().y();
                    screen.mouseMoved(0, 0);
                    hoverStep = 1;
                    ticks = 0;
                    return;
                }
                if (hoverStep == 1) {
                    if (ticks < 12) return;
                    Optional<UiRect> nav = screen.regionBounds("nav-loadout");
                    if (nav.isEmpty()) {
                        log("FAILED: nav-loadout was not painted");
                        stage = Stage.ALT_SHELL;
                        return;
                    }
                    UiRect box = nav.get();
                    float s = screen.layoutScale();
                    beforeHoverPaints = screen.paintCount();
                    screen.mouseMoved((box.x() + box.width() / 2) / s, (box.y() + box.height() / 2) / s);
                    hoverStep = 2;
                    ticks = 0;
                    return;
                }
                if (ticks < 1) return;
                float after = screen.regionBounds("install-sodium").map(UiRect::y).orElse(Float.NaN);
                boolean repainted = screen.paintCount() > beforeHoverPaints;
                float drift = Math.abs(after - hoverBaseline);
                log("hover page drift: " + drift + " layout units"
                    + (!repainted ? " (INCONCLUSIVE: hover did not repaint)"
                       : drift < 0.01F ? " (PASS)" : " (FAIL)"));
                stage = Stage.ALT_SHELL;
                ticks = 0;
                // A second, unrelated consumer proves nothing showcase-specific is baked into
                // the renderer: different shell, different theme, different layout.
                client.gui.setScreen(AltShellShowcase.createScreen());
                stage = Stage.ALT_SHELL;
            }
            case ALT_SHELL -> {
                if (ticks < 30) return;
                ticks = 0;
                capture(client, "pane-live-altshell");
                log("captured alt shell consumer");
                client.gui.setScreen(NativeDemoShowcase.createScreen());
                resizeStep = 0;
                stage = Stage.RESIZE;
            }
            // Resizing frees and reallocates the surface texture and its backing image. Cycle
            // through several sizes, including a very small one, to shake out lifetime bugs.
            case RESIZE -> {
                if (ticks < 12) return;
                ticks = 0;
                if (resizeStep < RESIZE_SIZES.length) {
                    int[] size = RESIZE_SIZES[resizeStep++];
                    client.getWindow().setWindowed(size[0], size[1]);
                    return;
                }
                if (resizeStep == RESIZE_SIZES.length && client.gui.screen() instanceof UiScreen s) {
                    log("scale curve: points=" + client.getWindow().getScreenWidth()
                        + "x" + client.getWindow().getScreenHeight()
                        + " guiScale=" + client.getWindow().getGuiScale()
                        + " layoutScale=" + s.layoutScale()
                        + " layout=" + Math.round(client.getWindow().getGuiScaledWidth() * s.layoutScale())
                        + "x" + Math.round(client.getWindow().getGuiScaledHeight() * s.layoutScale()));
                }
                if (client.gui.screen() instanceof UiScreen screen) {
                    log("survived " + RESIZE_SIZES.length + " resizes, paints=" + screen.paintCount()
                        + " window=" + client.getWindow().getGuiScaledWidth()
                        + "x" + client.getWindow().getGuiScaledHeight()
                        + " layoutScale=" + screen.layoutScale());
                    // Native slots are placed in layout units and consumed in GUI units, so a
                    // resize that changes the scale is where that conversion would break.
                    if (!"loadout".equals(screen.pageId())) {
                        screen.showPage("loadout");
                        return;
                    }
                    capture(client, "pane-live-resized");
                } else {
                    log("FAILED: screen lost during resize");
                }
                idlePaintCount = -1;
                stage = Stage.IDLE_CHECK;
            }
            case IDLE_CHECK -> {
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    stage = Stage.DONE;
                    return;
                }
                if (idlePaintCount < 0) {
                    if (ticks < 40) return;
                    idlePaintCount = screen.paintCount();
                    idleMouseX = client.mouseHandler.xpos();
                    idleMouseY = client.mouseHandler.ypos();
                    ticks = 0;
                    return;
                }
                if (ticks < 100) return;
                int delta = screen.paintCount() - idlePaintCount;
                // Hover changes legitimately repaint, so a moving cursor is not a failure. Report
                // it separately instead of letting real input masquerade as a stuck repaint loop.
                boolean mouseMoved = client.mouseHandler.xpos() != idleMouseX
                    || client.mouseHandler.ypos() != idleMouseY;
                String verdict = delta == 0 ? " (PASS)"
                    : mouseMoved ? " (INCONCLUSIVE: cursor moved during the check)" : " (FAIL)";
                log("idle repaints over 5s: " + delta + verdict);
                capture(client, "pane-live-idle");
                log("autotest complete");
                stage = Stage.DONE;
            }
            case DONE -> { }
        }
    }

    /**
     * Drags the motion slider to known points on its visible track and checks the value that
     * comes back. This is the regression guard for the grab-padding offset: the hit rectangle is
     * wider than the rail, so mapping the value against the rectangle used to make the ends
     * unreachable and left the knob trailing the cursor.
     */
    private void checkSlider(UiScreen screen) {
        Optional<UiRect> track = screen.trackBounds("motion-slider");
        if (track.isEmpty()) {
            log("FAILED: motion-slider was not painted");
            return;
        }
        UiRect rail = track.get();
        float scale = screen.layoutScale();
        double grabX = (rail.x() + rail.width() / 2) / scale;
        double grabY = (rail.y() + rail.height() / 2) / scale;
        press(screen, grabX, grabY);
        boolean ok = true;
        ok &= expectSlider(screen, rail, scale, 0.0);
        ok &= expectSlider(screen, rail, scale, 1.0);
        ok &= expectSlider(screen, rail, scale, 0.25);
        ok &= expectSlider(screen, rail, scale, 0.5);
        release(screen, grabX, grabY);
        log("slider track mapping: " + (ok ? "PASS" : "FAIL"));
    }

    private boolean expectSlider(UiScreen screen, UiRect rail, float scale, double fraction) {
        double layoutX = rail.x() + rail.width() * fraction;
        drag(screen, layoutX / scale, (rail.y() + rail.height() / 2) / scale);
        double actual = screen.numberValue("motion-intensity", -1);
        // One layout pixel of tolerance, since the drag goes through GUI-space coordinates.
        double tolerance = Math.max(0.01, 1.0 / Math.max(1, rail.width()));
        boolean ok = Math.abs(actual - fraction) <= tolerance;
        if (!ok) log("  expected " + fraction + " got " + actual);
        return ok;
    }

    private void checkToggle(UiScreen screen) {
        Optional<UiRect> bounds = screen.regionBounds("reduce-toggle");
        if (bounds.isEmpty()) {
            log("FAILED: reduce-toggle was not painted");
            return;
        }
        UiRect box = bounds.get();
        float scale = screen.layoutScale();
        double x = (box.x() + box.width() / 2) / scale;
        double y = (box.y() + box.height() / 2) / scale;
        boolean before = screen.booleanValue("reduced-motion", false);
        press(screen, x, y);
        release(screen, x, y);
        boolean after = screen.booleanValue("reduced-motion", false);
        log("toggle click: " + (after != before ? "PASS" : "FAIL"));
    }

    private static MouseButtonEvent at(double x, double y) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
    }

    private static void press(UiScreen screen, double x, double y) {
        screen.mouseMoved(x, y);
        screen.mouseClicked(at(x, y), false);
    }

    private static void drag(UiScreen screen, double x, double y) {
        screen.mouseDragged(at(x, y), 0, 0);
    }

    private static void release(UiScreen screen, double x, double y) {
        screen.mouseReleased(at(x, y));
    }

    private void loadWorld(Minecraft client) {
        try {
            if (client.getLevelSource().levelExists(LEVEL_ID)) {
                client.createWorldOpenFlows().openWorld(LEVEL_ID, () -> log("world open cancelled"));
                return;
            }
            LevelSettings settings = new LevelSettings(
                "Pane Autotest",
                GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                true,
                WorldDataConfiguration.DEFAULT
            );
            client.createWorldOpenFlows().createFreshLevel(
                LEVEL_ID, settings, new WorldOptions(1234L, false, false),
                WorldPresets::createNormalWorldDimensions, null
            );
        } catch (RuntimeException e) {
            log("FAILED: could not load world: " + e);
            stage = Stage.DONE;
        }
    }

    private static void capture(Minecraft client, String name) {
        // Screenshot.grab only creates one directory level under workDir, so anchor at the game
        // directory (run/screenshots) rather than a nested folder that does not exist yet.
        Screenshot.grab(client.gameDirectory, name + ".png",
            client.gameRenderer.mainRenderTarget(), 1, message -> { });
    }

    private static void log(String message) {
        System.out.println("[pane-autotest] " + message);
    }
}
