package dev.pane.demo;

import dev.pane.ui.UiScrollSnapshot;

import dev.pane.ui.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Real-client exercise of the three designs. Uses only the title panorama, never a saved world. */
final class DesignLabTest {
    private static DesignLabTest active;
    private static final String[] PAGES = {"voxel", "sky", "tactical"};
    private UiScreen screen;
    private int ticks, page, scrollBasePaints, idleBasePaints;
    private double scrollX, scrollY;
    private boolean failed;
    private long rowPixels, toastPixels;
    private final ArrayList<Long> extractTimes = new ArrayList<>();
    private final ArrayList<Long> frameTimes = new ArrayList<>();
    private long previousFrame;

    DesignLabTest() { active = this; }

    static void observe(long start) {
        if (active == null) return;
        active.extractTimes.add(System.nanoTime() - start);
        if (active.previousFrame != 0) active.frameTimes.add(start - active.previousFrame);
        active.previousFrame = start;
    }

    void tick(Minecraft client) {
        if (screen == null) {
            if (!(client.gui.screen() instanceof TitleScreen)) return;
            screen = DesignLabShowcase.createScreen(client.gui.screen());
            client.gui.setScreen(screen);
            return;
        }
        if (screen.paintCount() == 0) return;
        ticks++;
        if (ticks == 25) {
            capture(client, "wide");
            check(screen.virtualCompositionActive(), "virtual atlas ready");
            click("route-1");
            check((int) screen.numberValue("selected", -1) == 1, "route selection");
            screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, 0));
            check(screen.focusedId().isPresent(), "keyboard focus");
        }
        if (ticks == 27 || ticks == 29) click("details"); // Reverse a still-running fade/slide.
        if (ticks == 30) {
            click("brief-notice");
            check(screen.activeToastCount() > 0, "moving group input aligned");
        }
        if (ticks == 40) {
            check(screen.booleanValue("details", false), "interrupted briefing reopened");
            screen.toast(new UiToast("overlay-test", "Route updated", "Scroll keeps its atlas while this fades.", 4500));
        }
        if (ticks == 52) {
            scrollBasePaints = screen.paintCount();
            rowPixels = screen.virtualRowUploadPixels();
            toastPixels = screen.toastUploadPixels();
            UiRect row = screen.regionBounds("route-0").orElseThrow();
            scrollX = (row.right() + 2) / screen.layoutScale();
            scrollY = row.centerY() / screen.layoutScale();
            screen.mouseMoved(scrollX, scrollY);
            extractTimes.clear(); frameTimes.clear(); previousFrame = 0;
        }
        if (ticks >= 53 && ticks <= 72) screen.mouseScrolled(scrollX, scrollY, 0, -.5);
        if (ticks == 85) {
            check(screen.virtualCompositionActive(), "toast preserves atlas");
            check(screen.paintCount() == scrollBasePaints, "toast plus scrolling keeps page cached");
            check(screen.scrollSnapshot("routes").map(UiScrollSnapshot::offset).orElse(0D) > 0, "scroll moved");
            report("scroll-with-toast");
            capture(client, "overlay");
            // Verify moving/recycled row targets after scrolling.
            int visible = screen.virtualFirstVisible("routes") + 1;
            click("route-" + visible);
            check((int) screen.numberValue("selected", -1) == visible, "scrolled target aligned");
            click("deploy");
            check(screen.booleanValue("ready", false), "ready state");
        }
        if (ticks == 88) {
            int before = screen.activeToastCount();
            click("toast-dismiss:ready");
            check(screen.activeToastCount() == before - 1, "independent toast dismiss target");
        }
        if (ticks == 92) {
            click("reduced");
            check(screen.motionScale() == 0, "runtime reduced motion");
            click("details");
        }
        if (ticks == 96) {
            check(screen.regionBounds("brief-notice").isEmpty(), "hidden group has no input targets");
            click("details");
            client.getWindow().setWindowed(560, 390);
        }
        if (ticks == 115) {
            capture(client, "compact");
            check(screen.surfacePixelWidth() > 0 && screen.width == client.getWindow().getGuiScaledWidth(), "resize synchronized");
            client.getWindow().setWindowed(854, 480);
        }
        if (ticks == 124) click("glass");
        if (ticks == 132) {
            check(screen.booleanValue("glass", false), "optional retained blur enabled");
            System.out.printf(Locale.ROOT, "[DesignLab] %s glass lastRasterMs=%.3f lastUploadMs=%.3f%n",
                PAGES[page], screen.performanceSnapshot().lastRasterNanos() / 1e6,
                screen.performanceSnapshot().lastUploadNanos() / 1e6);
            capture(client, "glass");
            SurfaceGpuCheck.verify(screen, result -> {
                failed |= result.contains("FAIL");
                System.out.println("[DesignLab] " + screen.pageId() + " " + result);
            });
            click("glass");
        }
        if (ticks == 160) idleBasePaints = screen.paintCount();
        if (ticks == 200) {
            check(screen.paintCount() == idleBasePaints, "settled idle stays cached");
            if (++page == PAGES.length) {
                System.out.println("[DesignLab] complete " + (failed ? "FAIL" : "PASS"));
                active = null;
                client.stop();
            } else {
                screen.showPage(PAGES[page]);
                screen.setMotionScale(1);
                screen.setValue("reduced", false);
                screen.setValue("selected", 0);
                screen.setValue("ready", false);
                screen.setValue("details", true);
                screen.scrollTo("routes", 0D);
                ticks = 0;
            }
        }
    }

    private void click(String id) {
        var bounds = screen.regionBounds(id);
        if (bounds.isEmpty()) { check(false, "target exists: " + id); return; }
        UiRect b = bounds.get();
        double x = b.centerX() / screen.layoutScale(), y = b.centerY() / screen.layoutScale();
        screen.mouseMoved(x, y);
        var event = new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private void capture(Minecraft client, String suffix) {
        Screenshot.grab(client.gameDirectory, "pane-design-" + PAGES[page] + "-" + suffix + ".png",
            client.gameRenderer.mainRenderTarget(), 1, message -> { });
    }

    private void check(boolean ok, String label) {
        failed |= !ok;
        System.out.println("[DesignLab] " + PAGES[page] + " " + label + ": " + (ok ? "PASS" : "FAIL"));
    }

    private void report(String phase) {
        System.out.printf(Locale.ROOT, "[DesignLab] %s %s pagePaints=%d rowUpload=%d toastUpload=%d extractP95Ms=%.3f extractP99Ms=%.3f frameP95Ms=%.3f%n",
            PAGES[page], phase, screen.paintCount() - scrollBasePaints,
            screen.virtualRowUploadPixels() - rowPixels, screen.toastUploadPixels() - toastPixels,
            percentile(extractTimes, .95), percentile(extractTimes, .99), percentile(frameTimes, .95));
    }

    private static double percentile(ArrayList<Long> values, double p) {
        if (values.isEmpty()) return 0;
        Collections.sort(values);
        return values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * p) - 1)) / 1e6;
    }
}
