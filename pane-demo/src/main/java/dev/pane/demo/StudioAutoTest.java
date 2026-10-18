package dev.pane.demo;

import dev.pane.ui.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

/** Isolated full-flow console and HUD verification; hands-on -Pstudio uses its own scratch world. */
final class StudioAutoTest {
    private enum Stage { TITLE, LOAD, SETTLE, UI, PLAY, HIDE, RESIZE, UNLOAD, RELOAD, BENCH_HUD, DONE }
    private static StudioAutoTest active;
    private Stage stage = Stage.TITLE;
    private int ticks, originalGuiScale, benchmarkCycle;
    private int sequenceSwitchPaint = -1;
    private boolean failed, sawSwing;
    private UiScreen screen;
    private long idlePaints, hiddenPaints;
    private double startX, startZ;
    private UiHud.Snapshot hudBefore;
    private final ArrayList<Long> uiTimes = new ArrayList<>();
    private final ArrayList<Long> frameTimes = new ArrayList<>();
    private long previousFrame;
    static boolean enabled() { return Boolean.getBoolean("pane.demo.studio") || Boolean.getBoolean("pane.demo.studioTest"); }
    static boolean testing() { return Boolean.getBoolean("pane.demo.studioTest"); }
    boolean running() { return stage != Stage.DONE || testing(); }
    StudioAutoTest() { active = this; }
    static void observe(long start) {
        if (active == null || !testing()) return;
        active.uiTimes.add(System.nanoTime() - start);
        if (active.previousFrame != 0) active.frameTimes.add(start - active.previousFrame);
        active.previousFrame = start;
    }
    void tick(Minecraft client) {
        ticks++;
        if (ticks > 1800 && stage != Stage.DONE) { check(false, "stage timeout " + stage); finish(client); return; }
        switch (stage) {
            case TITLE -> {
                if (!(client.gui.screen() instanceof TitleScreen) || ticks < 45) return;
                originalGuiScale = client.options.guiScale().get();
                load(client); next(Stage.LOAD);
            }
            case LOAD -> { if (client.level != null && client.player != null) next(Stage.SETTLE); }
            case SETTLE -> {
                if (ticks == 10) {
                    client.player.connection.sendCommand("time set day");
                    client.player.connection.sendCommand("weather clear");
                    client.player.connection.sendCommand("fill -12 61 -12 12 95 12 air");
                    client.player.connection.sendCommand("fill -12 60 -12 12 60 12 grass_block");
                    client.player.connection.sendCommand("tp @s 0 61 -2 170 -15");
                }
                if (ticks < 70) return;
                client.gui.hud.getChat().clearMessages(true);
                screen = ShowcaseHub.createScreen(null);
                client.gui.setScreen(screen);
                if (!testing()) { next(Stage.DONE); log("ready: H console, J HUD; choose Return to world to play"); return; }
                next(Stage.UI);
            }
            case UI -> ui(client);
            case PLAY -> play(client);
            case HIDE -> hide(client);
            case RESIZE -> resize(client);
            case UNLOAD -> {
                if (client.level != null || ticks < 20) return;
                check(!FieldHudDemo.vitals().snapshot().hasSurface() && !FieldHudDemo.navigation().snapshot().hasSurface(), "world unload releases HUD textures");
                load(client); next(Stage.RELOAD);
            }
            case RELOAD -> {
                if (client.player == null || client.level == null || ticks < 70) return;
                client.gui.setScreen(null);
                FieldHudDemo.sample(client);
                if (!FieldHudDemo.vitals().snapshot().hasSurface()) return;
                check(FieldHudDemo.telemetry() != null, "world reload refreshes HUD data");
                check(FieldHudDemo.vitals().snapshot().hasSurface(), "HUD recreates after reload");
                capture(client, "pane-hud-reloaded");
                finish(client);
            }
            case BENCH_HUD -> {
                if (ticks == 1) { client.gui.setScreen(null); client.player.connection.sendCommand("gamemode survival"); }
                if (ticks == 10 || ticks == 70 || ticks == 130) client.player.connection.sendCommand("damage @s 3 minecraft:generic");
                if (ticks == 20) AtlasBenchmark.beginHud();
                if (ticks == 180) { AtlasBenchmark.finish(client); finish(client); }
            }
            case DONE -> { }
        }
    }
    private void ui(Minecraft client) {
        if (screen.paintCount() == 0) { ticks = 0; return; }
        if (ticks == 25) {
            client.gui.hud.getChat().clearMessages(true);
            if (AtlasBenchmark.ENABLED) AtlasBenchmark.beginCycle(screen, benchmarkCycle);
            uiTimes.clear(); frameTimes.clear(); previousFrame = 0;
            click("instrument");
        }
        if (ticks == 27) click("instrument");
        if (ticks == 29) click("instrument");
        if (ticks == 32) {
            click("instrument-action");
            capture(client, "pane-studio-in-motion");
        }
        if (ticks == 45) {
            check(screen.booleanValue("expanded", false), "card expansion survives repeated reversal");
            check(screen.numberValue("revision", 0) > 0, "moving transformed action target works");
            click("track-1");
        }
        if (ticks == 51 || ticks == 55) {
            double headingBefore = screen.numberValue("heading", 0);
            click("instrument-action");
            if (screen.numberValue("heading", 0) != headingBefore + 90)
                log("retarget diagnostic cycle=" + benchmarkCycle + " tick=" + ticks + " before=" + headingBefore
                    + " after=" + screen.numberValue("heading", 0) + " mode=" + screen.numberValue("mode", 0));
        }
        if (ticks == 76) {
            check(screen.numberValue("heading", 0) == 180, "spring retargets while active");
            click("track-2");
            sequenceSwitchPaint = screen.paintCount();
        }
        // A mode switch changes the button's action on the next paint. Do not click the
        // previous mode's retained target in the same tick and accidentally retarget again.
        if (ticks >= 78 && ticks < 90 && sequenceSwitchPaint >= 0 && screen.paintCount() > sequenceSwitchPaint) {
            double revisionBefore = screen.numberValue("revision", 0);
            click("instrument-action");
            check(screen.numberValue("revision", 0) == revisionBefore + 1, "sequence button uses freshly painted action");
            sequenceSwitchPaint = -1;
        }
        if (ticks == 90) {
            check(sequenceSwitchPaint < 0, "sequence action delivered after mode paint");
            String before = screen.focusedId().orElse("");
            screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, 0));
            screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT));
            check(screen.focusedId().orElse("").equals(before), "keyboard focus survives active transforms");
        }
        if (ticks == 95) { click("motion-menu"); }
        if (ticks == 99) {
            check(screen.openMenuId().isPresent(), "menu works during motion");
            screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        }
        if (ticks == 115 && AtlasBenchmark.ENABLED) {
            AtlasBenchmark.endCycle();
            if (++benchmarkCycle < 6) {
                screen.showPage("library"); screen.showPage("studio");
                screen.setValue("mode", 0); screen.setValue("expanded", false);
                screen.setValue("heading", 0D); screen.setValue("revision", 0D);
                ticks = 0;
            } else next(Stage.BENCH_HUD);
            return;
        }
        if (ticks == 115) {
            reportMotion(); capture(client, "pane-studio-console");
            click("nav-library");
        }
        if (ticks == 116 && Boolean.getBoolean("pane.demo.studioPreview")) {
            log("motion preview captured; gameplay/lifecycle gates were verified in the full run");
            finish(client); return;
        }
        if (ticks == 130) {
            check(screen.pageId().equals("library"), "example launcher opens");
            capture(client, "pane-studio-examples");
            click("example-0");
        }
        if (ticks == 140) {
            check(client.gui.screen() instanceof UiScreen child && child.pageId().equals("voxel"), "launcher opens existing design lab");
            client.gui.screen().keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
            check(client.gui.screen() == screen, "example Escape restores exact console");
        }
        if (ticks == 145) click("nav-hud");
        if (ticks == 158) {
            check(screen.pageId().equals("hud"), "HUD editor opens");
            click("hud-anchor");
        }
        if (ticks == 170) capture(client, "pane-hud-editor");
        if (ticks == 185) {
            var track = screen.trackBounds("hud-scale");
            if (track.isEmpty()) { check(false, "HUD scale track exists"); finish(client); return; }
            UiRect b = track.get();
            double x = (b.x() + b.width() * .7) / screen.layoutScale(), y = b.centerY() / screen.layoutScale();
            var event = at(x, y);
            screen.mouseClicked(event, false); screen.mouseDragged(event, 0, 0); screen.mouseReleased(event);
            check(FieldHudDemo.scale() > 1, "HUD editor scale control");
        }
        if (ticks == 200) { click("motion-off"); check(screen.motionScale() == 0, "console reduced motion"); }
        if (ticks == 210) {
            click("resume");
            check(client.gui.screen() == null, "HUD resumes without a screen");
            FieldHudDemo.setScale(1); FieldHudDemo.setReduced(false);
            client.player.connection.sendCommand("gamemode creative");
            client.player.connection.sendCommand("tp @s 0 61 -2 170 -15");
            next(Stage.PLAY);
        }
        if (Boolean.getBoolean("pane.demo.studioRecord") && ticks >= 25 && ticks <= 115)
            capture(client, String.format(Locale.ROOT, "atlas-motion-%03d", ticks));
    }
    private void play(Minecraft client) {
        if (ticks == 30) {
            startX = client.player.getX(); startZ = client.player.getZ();
            client.mouseHandler.grabMouse(); client.options.keyUp.setDown(true);
        }
        if (ticks >= 31 && ticks <= 50) sawSwing |= client.player.swinging;
        if (ticks == 35) {
            client.options.keyAttack.setDown(true);
            net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0));
        }
        if (ticks == 51) {
            client.options.keyUp.setDown(false); client.options.keyAttack.setDown(false);
            check(Math.hypot(client.player.getX() - startX, client.player.getZ() - startZ) > .3, "movement passes through HUD");
            check(sawSwing, "attack input passes through HUD");
            check(client.gui.screen() == null, "HUD never creates a gameplay screen");
            client.player.connection.sendCommand("gamemode survival");
        }
        if (ticks == 60) client.player.connection.sendCommand("damage @s 6 minecraft:generic");
        if (ticks == 68) {
            FieldHudDemo.sample(client);
            check(FieldHudDemo.telemetry().health() < FieldHudDemo.telemetry().maxHealth(), "HUD reads actual changed player health");
            hudBefore = FieldHudDemo.vitals().snapshot();
        }
        if (ticks == 85) client.gui.hud.getChat().clearMessages(true);
        if (ticks == 90) {
            capture(client, "pane-hud-live");
            var after = FieldHudDemo.vitals().snapshot();
            log(String.format(Locale.ROOT, "HUD live paints=%d avgRasterMs=%.3f avgUploadMs=%.3f maxRasterMs=%.3f",
                after.paints() - hudBefore.paints(), (after.totalRasterNanos() - hudBefore.totalRasterNanos()) / 1e6 / Math.max(1, after.paints() - hudBefore.paints()),
                (after.totalUploadNanos() - hudBefore.totalUploadNanos()) / 1e6 / Math.max(1, after.paints() - hudBefore.paints()), after.maximumRasterNanos() / 1e6));
            client.player.connection.sendCommand("gamemode creative");
        }
        if (ticks == 100) net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H));
        if (ticks == 105) {
            check(client.gui.screen() instanceof UiScreen opened && opened.document().title().contains("Atlas"), "H shortcut reopens console during gameplay");
            if (client.gui.screen() != null) client.gui.screen().keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        }
        if (ticks == 110) net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));
        if (ticks == 120) { check(!FieldHudDemo.enabled(), "J shortcut toggles HUD"); next(Stage.HIDE); }
        if (Boolean.getBoolean("pane.demo.studioRecord") && ticks >= 58 && ticks <= 95)
            capture(client, String.format(Locale.ROOT, "atlas-hud-%03d", ticks));
    }
    private void hide(Minecraft client) {
        if (ticks == 15) {
            check(!FieldHudDemo.vitals().snapshot().hasSurface(), "toggle-off finishes animation and releases texture");
            hiddenPaints = FieldHudDemo.vitals().snapshot().paints();
        }
        if (ticks == 35) {
            check(FieldHudDemo.vitals().snapshot().paints() == hiddenPaints, "hidden HUD has no redraws");
            FieldHudDemo.setEnabled(true); FieldHudDemo.setReduced(true);
        }
        if (ticks == 55) {
            check(FieldHudDemo.vitals().snapshot().hasSurface(), "toggle-on restores HUD");
            client.gui.hud.toggle(); hiddenPaints = FieldHudDemo.vitals().snapshot().frames();
        }
        if (ticks == 70) {
            check(FieldHudDemo.vitals().snapshot().frames() == hiddenPaints, "vanilla F1 condition hides custom HUD");
            client.gui.hud.toggle();
            next(Stage.RESIZE);
        }
    }
    private void resize(Minecraft client) {
        if (ticks == 1) client.getWindow().setWindowed(560, 390);
        if (ticks == 20) {
            client.options.guiScale().set(2); client.resizeGui();
        }
        if (ticks == 40) {
            var b = FieldHudDemo.vitals().snapshot().bounds();
            check(b.x() >= 0 && b.y() >= 0 && b.right() <= client.getWindow().getGuiScaledWidth() + 1
                && b.bottom() <= client.getWindow().getGuiScaledHeight() + 1, "HUD anchor stays in viewport after resize/GUI scale");
            capture(client, "pane-hud-resized");
            client.getWindow().setWindowed(854, 480); client.options.guiScale().set(originalGuiScale); client.resizeGui();
        }
        if (ticks == 65) {
            screen = ShowcaseHub.createScreen(null); client.gui.setScreen(screen);
            hiddenPaints = FieldHudDemo.vitals().snapshot().frames();
        }
        if (ticks == 85) {
            check(FieldHudDemo.vitals().snapshot().frames() == hiddenPaints, "HUD hides beneath ordinary screens");
            client.gui.setScreen(null);
            idlePaints = FieldHudDemo.vitals().snapshot().paints();
        }
        if (ticks == 125) {
            check(FieldHudDemo.vitals().snapshot().paints() == idlePaints, "stationary HUD has zero idle repaints over 2s");
            client.disconnect(new TitleScreen(), false); next(Stage.UNLOAD);
        }
    }
    private void load(Minecraft client) {
        String id = testing() ? "pane_studio_autotest_20261016" : "pane_studio_scratch";
        if (client.getLevelSource().levelExists(id)) client.createWorldOpenFlows().openWorld(id, () -> log("world open cancelled"));
        else client.createWorldOpenFlows().createFreshLevel(id, new LevelSettings("Pane Studio Scratch", GameType.CREATIVE,
            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
            new WorldOptions(2784L, false, false), WorldPresets::createNormalWorldDimensions, null);
    }
    private void next(Stage stage) { this.stage = stage; ticks = 0; }
    private void click(String id) {
        var bounds = screen.regionBounds(id);
        if (bounds.isEmpty()) { check(false, "target exists " + id); return; }
        UiRect b = bounds.get(); double x = b.centerX() / screen.layoutScale(), y = b.centerY() / screen.layoutScale();
        var event = at(x, y); screen.mouseMoved(x, y);
        boolean handled = screen.mouseClicked(event, false);
        if (id.equals("instrument-action") && (!handled || !screen.focusedId().orElse("").equals(id)))
            log("click diagnostic cycle=" + benchmarkCycle + " tick=" + ticks + " handled=" + handled
                + " focused=" + screen.focusedId().orElse("") + " bounds=" + b);
        screen.mouseReleased(event);
    }
    private static MouseButtonEvent at(double x, double y) { return new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)); }
    private void check(boolean ok, String label) { failed |= !ok; log(label + ": " + (ok ? "PASS" : "FAIL")); }
    private static void log(String text) { System.out.println("[StudioTest] " + text); }
    private void reportMotion() {
        log(String.format(Locale.ROOT, "active motion extractP95Ms=%.3f extractP99Ms=%.3f frameP95Ms=%.3f frames=%d",
            percentile(uiTimes, .95), percentile(uiTimes, .99), percentile(frameTimes, .95), uiTimes.size()));
    }
    private static double percentile(ArrayList<Long> values, double p) {
        if (values.isEmpty()) return 0; Collections.sort(values);
        return values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * p) - 1)) / 1e6;
    }
    private static void capture(Minecraft client, String name) {
        if (AtlasBenchmark.ENABLED) return;
        Screenshot.grab(client.gameDirectory, name + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> { });
    }
    private void finish(Minecraft client) {
        client.options.keyUp.setDown(false); client.options.keyAttack.setDown(false);
        client.options.guiScale().set(originalGuiScale); client.resizeGui();
        log("complete " + (failed ? "FAIL" : "PASS")); next(Stage.DONE); active = null; client.stop();
    }
}
