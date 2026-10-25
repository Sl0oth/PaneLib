package dev.pane.demo;

import dev.pane.ui.*;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

/**
 * Captures the README and gallery screenshots in a fresh survival world: the example HUDs over
 * the landscape, the field kit open beside it, and PaneOS with the world as its wallpaper. {@code -PscreenshotTour}, optional
 * {@code -PtourSeed=<seed>}; files land in the run directory's {@code screenshots/}.
 */
final class ScreenshotTour {
    private int stage;
    private long deadline;
    private boolean done;
    private UiScreen kit;

    void tick(Minecraft c) {
        if (done || System.nanoTime() < deadline || c.gui.overlay() != null) return;
        try {
            switch (stage) {
                case 0 -> {
                    if (!(c.gui.screen() instanceof TitleScreen)) return;
                    c.getWindow().setWindowed(1600, 900);
                    c.options.tutorialStep = TutorialSteps.NONE;
                    c.getTutorial().setStep(TutorialSteps.NONE);
                    c.options.renderDistance().set(12);
                    c.options.guiScale().set(3);
                    c.resizeGui();
                    c.options.pauseOnLostFocus = false;
                    long seed = Long.getLong("pane.demo.tourSeed", 2048L);
                    c.createWorldOpenFlows().createFreshLevel("panelib-screenshots-" + System.currentTimeMillis(),
                        new LevelSettings("PaneLib screenshots", GameType.SURVIVAL,
                            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(seed, true, false), WorldPresets::createNormalWorldDimensions, null);
                    advance(1000);
                }
                case 1 -> {
                    if (c.player == null || c.level == null) return;
                    ExampleHudController.select("none");
                    command(c, "time set 1500");
                    command(c, "weather clear 100000");
                    for (String rule : new String[] {"doDaylightCycle", "advance_time"}) command(c, "gamerule " + rule + " false");
                    String[] hotbar = {"diamond_sword", "iron_pickaxe", "bow", "torch 48", "cooked_beef 12", "oak_planks 64", "compass", "shield"};
                    for (String item : hotbar) command(c, "give @s " + item);
                    // Let the chunks around spawn load before choosing a viewpoint.
                    advance(9000);
                }
                case 2 -> { viewpoint(c); advance(2500); }
                case 3 -> {
                    // Clear the plants at the player's feet so the view opens onto the landscape.
                    for (String plant : new String[] {"short_grass", "tall_grass", "fern", "large_fern", "short_dry_grass", "tall_dry_grass", "bush"})
                        { command(c, "fill ~-12 ~-6 ~-12 ~12 ~3 ~12 air replace " + plant); }
                    c.player.getInventory().setSelectedSlot(3);
                    advance(5000);
                }
                case 4 -> { capture(c, "world-plain"); ExampleHudController.select("vanilla-hud"); advance(2500); }
                case 5 -> { capture(c, "hud-vanilla"); ExampleHudController.select("wayfarer-hud"); advance(3000); }
                case 6 -> { capture(c, "hud-wayfarer"); ExampleHudController.select("none"); kit = InWorldShowcase.createScreen(); c.gui.setScreen(kit); advance(1500); }
                case 7 -> { move(c, "slot-hit0"); advance(1400); }
                case 8 -> { capture(c, "kit-loadout"); click(c, tab("waypoints")); advance(900); }
                case 9 -> { click(c, "sort"); advance(1200); }
                case 10 -> {
                    capture(c, "kit-waypoints"); c.gui.setScreen(null);
                    // Tip the view down so the blocky ground, not the sky, fills the desktop.
                    command(c, "tp @s ~ ~ ~ ~ 16");
                    // F1: the held item would sit in the wallpaper. Screens still draw with the HUD hidden.
                    if (!c.gui.hud.isHidden()) c.gui.hud.toggle();
                    advance(1500);
                }
                // PaneOS over the same world: it captures the frame behind it as its wallpaper.
                case 11 -> { kit = PaneOsShowcase.createScreen(null); c.gui.setScreen(kit); advance(PaneOsLock.BOOT_MILLIS + 2200); }
                case 12 -> { capture(c, "paneos-lock"); type("pane"); kit.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)); advance(1800); }
                case 13 -> { click(c, "welcome:continue"); advance(500); }
                case 14 -> {
                    PaneOsEvents.handleAction(kit, "music:1");
                    kit.setValue("size:music:x", -250D); kit.setValue("size:music:y", -150D);
                    open("music", 40, -40);
                    open("calculator", 90, 20);
                    // The first-unlock tip would cover the corner of the world.
                    kit.setValue("notice:at", 0D);
                    advance(1600);
                }
                case 15 -> advance(1500);
                case 16 -> {
                    capture(c, "paneos-desktop");
                    // Dark appearance with Control Center open, so its glass sits on the world itself.
                    PaneOsEvents.handleAction(kit, "close:calculator");
                    PaneOsEvents.handleAction(kit, "darkmode");
                    PaneOsEvents.handleAction(kit, "cc");
                    advance(900);
                }
                case 17 -> { move(c, "cc:wifi"); advance(900); }
                case 18 -> { capture(c, "paneos-control"); c.gui.setScreen(null); if (c.gui.hud.isHidden()) c.gui.hud.toggle(); advance(600); }
                case 19 -> { c.disconnect(new TitleScreen(), false); advance(1500); }
                default -> {
                    if (c.level != null) return;
                    System.out.println("[ScreenshotTour] complete PASS");
                    done = true;
                    c.stop();
                }
            }
        } catch (Throwable error) {
            error.printStackTrace();
            System.out.println("[ScreenshotTour] complete FAIL " + error);
            done = true;
            c.stop();
        }
    }

    /** The highest loaded ground near spawn, looking toward the lowest, most open direction. */
    private void viewpoint(Minecraft c) {
        var level = c.level;
        var origin = c.player.blockPosition();
        int bestX = origin.getX(), bestZ = origin.getZ(), bestY = Integer.MIN_VALUE;
        for (int dx = -96; dx <= 96; dx += 4) for (int dz = -96; dz <= 96; dz += 4) {
            int x = origin.getX() + dx, z = origin.getZ() + dz;
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y > bestY && level.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z)).getFluidState().isEmpty()) {
                bestY = y; bestX = x; bestZ = z;
            }
        }
        float bestYaw = 0; double lowest = Double.MAX_VALUE;
        for (int angle = 0; angle < 360; angle += 15) {
            double rad = Math.toRadians(angle), sum = 0; int n = 0;
            for (int d = 24; d <= 72; d += 8) {
                int x = bestX + (int) Math.round(-Math.sin(rad) * d), z = bestZ + (int) Math.round(Math.cos(rad) * d);
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                sum += level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z); n++;
            }
            if (n > 0 && sum / n < lowest) { lowest = sum / n; bestYaw = angle; }
        }
        System.out.println("[ScreenshotTour] viewpoint " + bestX + " " + bestY + " " + bestZ + " yaw " + bestYaw + " ground ahead " + Math.round(lowest));
        command(c, "tp @s " + (bestX + .5) + " " + bestY + " " + (bestZ + .5) + " " + bestYaw + " 3");
    }

    private void command(Minecraft c, String command) {
        if (c.player != null) c.player.connection.sendCommand(command);
    }

    private String tab(String value) {
        // Tabs register each option as "<id>-<index>".
        return switch (value) { case "waypoints" -> "tabs-1"; case "settings" -> "tabs-2"; default -> "tabs-0"; };
    }

    private double[] point(Minecraft c, String id) {
        var r = kit.regionBounds(id).orElseThrow(() -> new IllegalStateException("no region " + id));
        var window = c.getWindow();
        return new double[] {r.centerX() / kit.layoutScale() * window.getScreenWidth() / window.getGuiScaledWidth(),
            r.centerY() / kit.layoutScale() * window.getScreenHeight() / window.getGuiScaledHeight()};
    }

    private void move(Minecraft c, String id) throws Exception {
        double[] p = point(c, id);
        Method move = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
        move.setAccessible(true);
        move.invoke(c.mouseHandler, c.getWindow().handle(), p[0], p[1]);
    }

    private void click(Minecraft c, String id) throws Exception {
        move(c, id);
        Method button = MouseHandler.class.getDeclaredMethod("onButton", long.class, MouseButtonInfo.class, int.class);
        button.setAccessible(true);
        var left = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
        button.invoke(c.mouseHandler, c.getWindow().handle(), left, GLFW.GLFW_PRESS);
        button.invoke(c.mouseHandler, c.getWindow().handle(), left, GLFW.GLFW_RELEASE);
    }

    /** Opens a PaneOS window moved from its default place by {@code dx, dy} layout units. */
    private void open(String app, double dx, double dy) {
        kit.setValue("win:" + app + ":x", dx);
        kit.setValue("win:" + app + ":y", dy);
        PaneOsEvents.openApp(kit, app);
    }

    private void type(String text) {
        for (char ch : text.toCharArray()) kit.charTyped(new CharacterEvent(ch));
    }

    private void capture(Minecraft c, String name) {
        c.gui.hud.getChat().clearMessages(false);
        Screenshot.grab(c.gameDirectory, name + ".png", c.gameRenderer.mainRenderTarget(), 1, message -> { });
    }

    private void advance(long millis) { stage++; deadline = System.nanoTime() + millis * 1_000_000; }
}
