package dev.pane.demo;

import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Beacon showcase acceptance: shape-exact targets, motion, GPU sheet and idle cost. No world or personal data. */
final class BeaconAuditTest {
    private UiScreen screen;
    private int tick, checks, paints, partials;
    private long rasters;
    private boolean failed, finished;

    void tick(Minecraft client) {
        if (finished) return;
        try {
            if (screen == null) {
                if (!(client.gui.screen() instanceof TitleScreen)) return;
                screen = BeaconShowcase.createScreen(client.gui.screen());
                client.gui.setScreen(screen);
                return;
            }
            if (screen.paintCount() == 0) return;
            tick++;
            switch (tick) {
                case 40 -> {
                    capture(client, "beacon-overview");
                    var hex = screen.regionBounds("nav-2").orElseThrow();
                    clickAt(hex.x() + 2, hex.y() + 2);
                    check(number("nav") == 0, "hexagon corner outside the outline does not activate");
                    clickAt(hex.centerX(), hex.centerY());
                    check(number("nav") == 2, "hexagon centre activates its rail item");
                }
                case 44 -> {
                    var tab = screen.regionBounds("tab-2").orElseThrow();
                    clickAt(tab.x() + 2, tab.y() + 2);
                    check(number("region") == 0, "slanted tab ignores the empty corner");
                    clickAt(tab.centerX(), tab.centerY());
                    check(number("region") == 2, "slanted tab selects the Badlands");
                }
                case 70 -> {
                    capture(client, "beacon-badlands");
                    UiRect ring = screen.regionBounds("item-0").orElseThrow();
                    for (int i = 1; i < 6; i++) ring = union(ring, screen.regionBounds("item-" + i).orElseThrow());
                    float cx = ring.centerX(), cy = ring.centerY(), radius = ring.width() / 2 * .75F;
                    clickAt(cx, cy);
                    check(number("item") == 0, "ring hole is not a target");
                    double mid = Math.toRadians(-120 + 4 * 60 + 2 + 28);
                    clickAt(cx + (float) Math.cos(mid) * radius, cy + (float) Math.sin(mid) * radius);
                    check(number("item") == 4, "ring segment selects the lantern");
                    pointerTo(client, "embark");
                }
                case 76 -> capture(client, "beacon-embark-hover");
                case 82 -> pointerTo(client, "card-1");
                case 87 -> capture(client, "beacon-card-hover");
                case 90 -> {
                    var embark = screen.regionBounds("embark").orElseThrow();
                    clickAt(embark.x() + 2, embark.y() + 2);
                    check(number("launches") == 0, "chamfered corner is not part of the embark target");
                    clickAt(embark.centerX(), embark.centerY());
                    check(number("launches") == 1 && number("step") == 1, "embark launches and advances the route");
                    check(screen.activeToastCount() == 1, "launch toast shown");
                }
                case 104 -> {
                    capture(client, "beacon-launched");
                    rasters = screen.gpuLayerRasterCount();
                    var preview = screen.regionBounds("preview").orElseThrow();
                    clickAt(preview.centerX(), preview.centerY());
                    check(screen.booleanValue("sheet", false), "round preview opens the region sheet");
                }
                case 108 -> {
                    capture(client, "beacon-sheet-sliding");
                    check(screen.gpuLayerRasterCount() == rasters, "opening the sheet reuses its rasterized content");
                }
                case 120 -> {
                    capture(client, "beacon-sheet");
                    var close = screen.regionBounds("sheet-close").orElseThrow();
                    clickAt(close.centerX(), close.centerY());
                    check(!screen.booleanValue("sheet", true), "sheet closes from its own close target");
                }
                case 140 -> pointerAt(client, 5, 5);
                case 160 -> {
                    // Pointer parked on empty space: only the sheet itself can cause work now.
                    paints = screen.paintCount();
                    partials = screen.partialPaintCount();
                    screen.setValue("sheet", true);
                }
                case 180 -> {
                    // Partial repaints come from hover under a real cursor; the slide itself adds exactly one full paint.
                    int full = (screen.paintCount() - paints) - (screen.partialPaintCount() - partials);
                    check(full == 1, "a full sheet slide costs one full page paint (" + full + ")");
                    screen.setValue("sheet", false);
                }
                case 200 -> {
                    paints = screen.paintCount();
                    SurfaceGpuCheck.verify(screen, result -> check(result.contains("PASS"), result));
                }
                case 260 -> {
                    check(screen.paintCount() == paints, "settled showcase stays idle (" + (screen.paintCount() - paints) + " paints)");
                    screen.invalidate();
                }
                case 264 -> {
                    var sample = screen.performanceSnapshot();
                    System.out.printf("[BeaconTest] full repaint surface=%dx%d rasterMs=%.3f uploadMs=%.3f%n",
                        screen.surfacePixelWidth(), screen.surfacePixelHeight(), sample.lastRasterNanos() / 1e6,
                        sample.lastUploadNanos() / 1e6);
                    finished = true;
                    System.out.println("[BeaconTest] complete " + (failed ? "FAIL" : "PASS") + " checks=" + checks);
                    if (!Boolean.getBoolean("pane.demo.beaconKeepOpen")) client.stop();
                }
                default -> { }
            }
        } catch (Throwable error) {
            finished = true;
            error.printStackTrace();
            System.out.println("[BeaconTest] complete FAIL " + error);
            client.stop();
        }
    }

    private double number(String key) { return screen.numberValue(key, 0); }

    /** Moves the real cursor; each frame the screen re-reads it, so synthetic hover would not stick. */
    private void pointerTo(Minecraft client, String id) throws Exception {
        var bounds = screen.regionBounds(id).orElseThrow();
        pointerAt(client, bounds.centerX(), bounds.centerY());
    }

    private void pointerAt(Minecraft client, float layoutX, float layoutY) throws Exception {
        var quality = screen.rasterQuality();
        var move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
        move.setAccessible(true);
        move.invoke(client.mouseHandler, client.getWindow().handle(),
            layoutX / quality.layoutWidth() * client.getWindow().getScreenWidth(),
            layoutY / quality.layoutHeight() * client.getWindow().getScreenHeight());
    }

    private static UiRect union(UiRect a, UiRect b) {
        float x = Math.min(a.x(), b.x()), y = Math.min(a.y(), b.y());
        return new UiRect(x, y, Math.max(a.right(), b.right()) - x, Math.max(a.bottom(), b.bottom()) - y);
    }

    private void clickAt(float x, float y) {
        var event = new MouseButtonEvent(x / screen.layoutScale(), y / screen.layoutScale(), new MouseButtonInfo(0, 0));
        screen.mouseMoved(event.x(), event.y());
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> { });
    }

    private void check(boolean passed, String message) {
        checks++;
        failed |= !passed;
        System.out.println("[BeaconTest] " + message + ": " + (passed ? "PASS" : "FAIL"));
    }
}
