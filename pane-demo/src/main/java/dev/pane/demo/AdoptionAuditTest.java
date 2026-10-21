package dev.pane.demo;

import dev.pane.ui.*;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

/** Public-API adoption regressions; owns only a newly generated disposable world/profile. */
final class AdoptionAuditTest {
    private static final int COMMAND = System.getProperty("os.name", "").startsWith("Mac")
        ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL;
    private final ArrayList<String> mutable = new ArrayList<>();
    private TitleScreen parent;
    private UiScreen screen, paused;
    private int stage, checks, failures, paints, actions, values, pages, submits;
    private enum MutablePreference { INSTANCE; int revision; }
    private int sliderMode; // 0 live, 1 absent, 2 enabled button replacement with the same id
    private float sliderX = 40;
    private double before;
    private long deadline, worldTime;
    private boolean done;
    private CompletableFuture<Void> worker;

    void tick(Minecraft c) {
        if (done || System.nanoTime() < deadline || c.gui.overlay() != null) return;
        try {
            if (stage == 0) {
                if (!(c.gui.screen() instanceof TitleScreen)) return;
                parent = (TitleScreen)c.gui.screen();
                c.getWindow().setWindowed(1100, 760);
                c.options.pauseOnLostFocus = false;
                screen = create(false);
                screen.setMotionScale(0);
                screen.setValue("scalar", 7);
                screen.setValue("nullable", null);
                screen.setValue("mutable", mutable);
                screen.setValue("enum", MutablePreference.INSTANCE);
                c.gui.setScreen(screen);
                next(900); return;
            }
            switch (stage) {
                case 1 -> {
                    check(c.level == null, "adoption screen opens without a world");
                    check(!screen.isPauseScreen(), "default screen remains non-pausing");
                    check(UiScreenOptions.builder().parent(parent).parent(null).build().backdrop() == UiBackdrop.NONE,
                        "clearing parent clears its implicit backdrop");
                    check(UiScreenOptions.builder().parent(parent).backdrop(UiBackdrop.MINECRAFT).parent(null).build().backdrop() == UiBackdrop.MINECRAFT,
                        "clearing parent preserves an explicit backdrop");
                    click("owner");
                    check(actions == 1 && screen.booleanValue("owned", false), "typed owner callback updates its own screen");
                    next(500);
                }
                case 2 -> { paints = screen.paintCount(); next(600); }
                case 3 -> {
                    check(screen.paintCount() == paints, "settled screen is idle");
                    for (int i = 0; i < 100; i++) { screen.setValue("scalar", 7); screen.setValue("nullable", null); }
                    next(400);
                }
                case 4 -> {
                    check(screen.paintCount() == paints, "equal scalar and null assignments do not repaint");
                    screen.setValue("scalar", 8); next(400);
                }
                case 5 -> {
                    check(screen.paintCount() > paints && screen.numberValue("scalar", 0) == 8, "changed scalar repaints");
                    paints = screen.paintCount(); mutable.add("changed"); screen.setValue("mutable", mutable); next(400);
                }
                case 6 -> {
                    check(screen.paintCount() > paints && screen.value("mutable") == mutable,
                        "same mutable object remains an explicit update notification");
                    paints = screen.paintCount(); screen.setValue("scalar", "eight"); next(400);
                }
                case 7 -> {
                    check(screen.paintCount() > paints && screen.stringValue("scalar", "").equals("eight"), "state type replacement repaints");
                    screen.setValue("scalar", null); next(400);
                }
                case 8 -> {
                    check(screen.value("scalar") == null, "state accepts a null replacement");
                    screen.focus("area"); key(GLFW.GLFW_KEY_END, COMMAND); type('!');
                    check(screen.stringValue("lines", "").equals("alpha\nbeta!"), "textarea accepts ordinary editing");
                    screen.setValue("lines", "alpha\r\nbeta!");
                    key(GLFW.GLFW_KEY_Z, COMMAND);
                    check(screen.stringValue("lines", "").equals("alpha\nbeta"), "equal normalized textarea assignment preserves undo");
                    screen.clearFocus(); next(500);
                }
                case 9 -> {
                    worker = CompletableFuture.runAsync(() -> {
                        screen.setValue("async", "first"); screen.setValue("async", "second");
                        screen.setValue("async", "second"); screen.setValue("async", "third");
                    }); next(500);
                }
                case 10 -> {
                    if (!worker.isDone()) return;
                    worker.join();
                    check(screen.stringValue("async", "").equals("third"), "worker mutations retain submission order");
                    paints = screen.paintCount();
                    worker = CompletableFuture.runAsync(() -> screen.setValue("async", "third")); next(500);
                }
                case 11 -> {
                    if (!worker.isDone()) return;
                    worker.join(); check(screen.paintCount() == paints, "equal worker completion leaves retained screen idle");
                    pressSlider(.25F); before = slider();
                    key(GLFW.GLFW_KEY_ESCAPE, 0);
                    check(c.gui.screen() == parent, "Escape restores parent during an interrupted slider drag");
                    next(350);
                }
                case 12 -> { c.gui.setScreen(screen); next(500); }
                case 13 -> {
                    press(800, 500); drag(950, 500);
                    check(close(slider(), before), "reopening a screen cancels the old slider drag");
                    release(950, 500); next(350);
                }
                case 14 -> {
                    pressSlider(.3F); before = slider(); sliderMode = 1; screen.invalidate(); next(400);
                }
                case 15 -> {
                    drag(350, 78);
                    check(close(slider(), before), "disappearing slider cancels captured drag");
                    sliderMode = 0; screen.invalidate(); next(400);
                }
                case 16 -> {
                    drag(350, 78);
                    check(close(slider(), before), "reappearing slider requires a fresh pointer press");
                    release(350, 78); pressSlider(.2F); sliderX = 400; screen.invalidate(); next(400);
                }
                case 17 -> {
                    drag(sliderX + 150, 78);
                    check(close(slider(), .5), "valid drag follows the slider's current track geometry");
                    release(sliderX + 150, 78); pressSlider(.2F); before = slider();
                    sliderMode = 2; screen.invalidate(); next(400);
                }
                case 18 -> {
                    drag(sliderX + 290, 78);
                    check(close(slider(), before), "enabled same-id button cannot inherit an old slider action");
                    release(sliderX + 290, 78); sliderMode = 0; sliderX = 40; screen.invalidate(); next(400);
                }
                case 19 -> {
                    pressSlider(.4F); release(sliderX + 120, 78);
                    check(close(slider(), .4), "slider remains usable after interrupted ownership");
                    check(values > 0, "typed callback receives ordinary control value events");
                    capture(c, "adoption-controls");
                    c.gui.setScreen(parent);
                    c.options.renderDistance().set(5); c.options.simulationDistance().set(5);
                    c.createWorldOpenFlows().createFreshLevel("adoption-audit-" + System.currentTimeMillis(),
                        new LevelSettings("Disposable adoption audit", GameType.CREATIVE,
                            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(94513L, false, false), WorldPresets::createNormalWorldDimensions, null);
                    next(1000);
                }
                case 20 -> {
                    if (c.level == null || c.player == null) return;
                    c.gui.setScreen(null); next(1200);
                }
                case 21 -> {
                    paused = create(true); paused.setMotionScale(0); c.gui.setScreen(paused); next(1000);
                }
                case 22 -> {
                    check(paused.isPauseScreen(), "pauseGame option reaches Minecraft's Screen contract");
                    worldTime = c.level.getGameTime(); next(1000);
                }
                case 23 -> {
                    check(c.level.getGameTime() == worldTime, "pausing PaneLib screen stops the actual single-player world clock");
                    capture(c, "adoption-paused-world"); c.gui.setScreen(screen); next(800);
                }
                case 24 -> { worldTime = c.level.getGameTime(); next(1000); }
                case 25 -> {
                    check(c.level.getGameTime() > worldTime, "non-pausing PaneLib screen resumes the actual single-player world clock");
                    c.disconnect(new TitleScreen(), false); next(600);
                }
                case 26 -> {
                    if (c.level != null) return;
                    c.gui.setScreen(screen); next(500);
                }
                case 27 -> {
                    pressSlider(.25F); before = slider(); sliderMode = 1; screen.invalidate(); next(400);
                }
                case 28 -> {
                    // Deliberately no pointer event while absent: paint-time ownership must cancel capture.
                    sliderMode = 0; screen.invalidate(); next(400);
                }
                case 29 -> {
                    drag(sliderX + 285, 78);
                    check(close(slider(), before), "absence during repaint cancels drag before same-id reappearance without pointer movement");
                    release(sliderX + 285, 78);
                    check(screen.focus("submit"), "single-line field accepts programmatic focus");
                    key(GLFW.GLFW_KEY_ENTER, 0);
                    check(submits == 1 && screen.stringValue("submitted", "").equals("draft"),
                        "owner-aware callback receives SUBMIT payload and updates its screen");
                    screen.showPage("other");
                    check(pages == 1 && screen.stringValue("visited", "").equals("other"),
                        "owner-aware callback receives PAGE payload and updates its screen");
                    screen.showPage("main");
                    check(pages == 2 && screen.stringValue("visited", "").equals("main"), "owner-aware page events preserve return navigation");
                    legacyCallbacks(); next(600);
                }
                case 30 -> {
                    paints = screen.paintCount(); MutablePreference.INSTANCE.revision++;
                    screen.setValue("enum", MutablePreference.INSTANCE); next(500);
                }
                case 31 -> {
                    check(screen.paintCount() > paints, "mutable enum same-instance assignment remains an update notification");
                    c.gui.setScreen(parent); next(400);
                }
                default -> {
                    if (c.level != null) return;
                    check(c.level == null, "disposable world disconnects cleanly");
                    done = true;
                    System.out.println("[AdoptionTest] complete " + (failures == 0 ? "PASS" : "FAIL") + " checks=" + checks + " failures=" + failures);
                    c.stop();
                }
            }
        } catch (Throwable error) {
            error.printStackTrace(); done = true;
            System.out.println("[AdoptionTest] complete FAIL " + error); c.stop();
        }
    }

    private UiScreen create(boolean pause) {
        var document = UiDocument.builder("PaneLib adoption audit").scaling(UiScaling.fixed())
            .page(new UiPage("main", "Main", "", 0xFFD8A641, ui -> {
                var g = ui.canvas(); g.fill(0xFFF2EBDD, 0, 0, ui.width(), ui.height());
                g.text("A mod settings screen", 40, 24, 22, 0xFF28231D);
                if (sliderMode == 0) ui.slider("slider", "volume", sliderX, 70, 300, 0, 1, .5);
                else if (sliderMode == 2) ui.button("slider", "Switch mode", "switch-mode", sliderX, 66, 300, 28,
                    UiButtonStyle.builder().build(), true);
                ui.textArea("area", "lines", "Notes", 40, 120, 340, 110, "alpha\nbeta", 1000);
                ui.textField("submit", "draft", "Draft", 420, 120, 250, "draft");
                ui.button("owner", "Save settings", "save", 40, 260, 150, 34,
                    UiContext.ButtonStyle.PRIMARY, UiContext.Shape.ROUNDED);
                g.text("State: " + ui.value("scalar"), 40, 320, 14, 0xFF28231D);
                g.text("Updates: " + mutable.size(), 40, 345, 14, 0xFF28231D);
                g.text("Preference revision: " + MutablePreference.INSTANCE.revision, 40, 370, 14, 0xFF28231D);
            })).page(new UiPage("other", "Other", "", 0xFFD8A641,
                ui -> ui.canvas().text("Second settings page", 40, 40, 20, 0xFF28231D))).build();
        return UiScreen.create(document, NativeOverlayRenderer.NONE, (owner, event) -> {
            if (event.type() == UiEventType.ACTION && event.key().equals("save")) {
                actions++; check(owner == screen, "owner-aware event carries the exact screen instance"); owner.setValue("owned", true);
            }
            if (event.type() == UiEventType.VALUE) values++;
            if (event.type() == UiEventType.SUBMIT) {
                submits++; check(owner == screen, "submit dispatch retains the exact owner"); owner.setValue("submitted", event.value());
            }
            if (event.type() == UiEventType.PAGE) {
                pages++; check(owner == screen, "page dispatch retains the exact owner"); owner.setValue("visited", event.key());
            }
        }, UiScreenOptions.builder().parent(pause ? null : parent).pauseGame(pause).build());
    }

    private void legacyCallbacks() {
        int[] typed = {0}, strings = {0};
        var typedScreen = new UiScreen(screen.document(), NativeOverlayRenderer.NONE, (UiEventHandler) event -> {
            if (event.type() == UiEventType.PAGE && event.key().equals("other")) typed[0]++;
        });
        typedScreen.showPage("other");
        check(typed[0] == 1, "existing one-argument typed event constructor remains compatible");
        var stringScreen = new UiScreen(screen.document(), NativeOverlayRenderer.NONE, (type, key, value) -> {
            if (type.equals("page") && key.equals("other")) strings[0]++;
        });
        stringScreen.showPage("other");
        check(strings[0] == 1, "existing three-string event constructor remains compatible");
        var noHandler = UiScreen.create(screen.document(), NativeOverlayRenderer.NONE, null);
        noHandler.showPage("other");
        check(noHandler.pageId().equals("other"), "null owner-aware handler is a safe no-op");
    }

    private void next(long millis) { stage++; deadline = System.nanoTime() + millis * 1_000_000L; }
    private void check(boolean ok, String message) { checks++; if (!ok) failures++; System.out.println("[AdoptionTest] " + (ok ? "PASS " : "FAIL ") + message); }
    private double slider() { return screen.numberValue("volume", -1); }
    private boolean close(double a, double b) { return Math.abs(a - b) < .005; }
    private MouseButtonEvent mouse(float x, float y) { return new MouseButtonEvent(x / screen.layoutScale(), y / screen.layoutScale(), new MouseButtonInfo(0, 0)); }
    private void press(float x, float y) { screen.mouseClicked(mouse(x, y), false); }
    private void drag(float x, float y) { screen.mouseDragged(mouse(x, y), 1, 0); }
    private void release(float x, float y) { screen.mouseReleased(mouse(x, y)); }
    private void pressSlider(float value) { press(sliderX + value * 300, 78); }
    private void click(String id) { var r = screen.regionBounds(id).orElseThrow(); press(r.centerX(), r.centerY()); release(r.centerX(), r.centerY()); }
    private void key(int key, int modifiers) { screen.keyPressed(new KeyEvent(key, 0, modifiers)); }
    private void type(char value) { screen.charTyped(new CharacterEvent(value)); }
    private void capture(Minecraft c, String name) { Screenshot.grab(c.gameDirectory, name + ".png", c.gameRenderer.mainRenderTarget(), 1, message -> {}); }
}
