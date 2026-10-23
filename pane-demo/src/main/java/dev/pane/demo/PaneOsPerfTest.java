package dev.pane.demo;

import dev.pane.ui.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * Where PaneOS spends its time in a large window: every paint's raster time is recorded per phase
 * (full repaint, a click that changes state, a window drag, dock magnification, a window opening).
 * The real cursor is ignored; the probe moves its own pointer.
 */
final class PaneOsPerfTest {
    private Probe screen;
    private int tick;
    private final List<String> report = new ArrayList<>();
    private UiRect bar;

    void tick(Minecraft client) {
        if (screen == null) {
            if (!(client.gui.screen() instanceof TitleScreen) || client.gui.overlay() != null) return;
            String[] size = System.getProperty("pane.demo.size", "1512x945").split("x");
            client.getWindow().setWindowed(Integer.parseInt(size[0]), Integer.parseInt(size[1]));
            screen = (Probe) PaneOsShowcase.createScreen(client.gui.screen(), Probe::new);
            screen.setValue("boot:at", 0D);
            screen.setValue("locked", false);
            screen.setValue("welcomed", true);
            client.gui.setScreen(screen);
            return;
        }
        if (screen.paintCount() == 0) return;
        tick++;
        try { step(client); } catch (Throwable error) { error.printStackTrace(); System.out.println("[PaneOsPerf] complete FAIL " + error); client.stop(); }
    }

    private void step(Minecraft client) {
        if (Boolean.getBoolean("pane.demo.perfClicks")) {
            // Profiling aid: open Notes, then click its list for a few seconds.
            if (tick == 10) PaneOsEvents.openApp(screen, "notes");
            if (tick == 40) screen.begin("click: select a note");
            if (tick > 40 && tick < 200) click("notes:item:" + (tick % 3));
            if (tick == 200) { end(); report.forEach(line -> System.out.println("[PaneOsPerf] " + line)); System.out.println("[PaneOsPerf] complete PASS"); client.stop(); }
            return;
        }
        if (Boolean.getBoolean("pane.demo.perfCompare")) { compare(client); return; }
        if (Boolean.getBoolean("pane.demo.perfLoop")) {
            // Profiling aid: long runs of one kind of paint, so a sampling profiler sees enough of each.
            int loop = 150;
            if (tick == 10) for (String id : new String[] {"files", "settings", "notes"}) PaneOsEvents.openApp(screen, id);
            if (tick == 40) screen.begin("loop: full repaint");
            if (tick > 40 && tick <= 40 + loop) screen.invalidate();
            if (tick == 42 + loop) { end(); PaneOsEvents.raise(screen, "notes"); screen.begin("loop: tiny repaint"); }
            if (tick > 42 + loop && tick <= 42 + loop * 2) screen.repaintArea(new UiRect(700, 400, 8, 8));
            if (tick == 44 + loop * 2) { end(); screen.begin("loop: click a note"); }
            if (tick > 44 + loop * 2 && tick <= 44 + loop * 3) click("notes:item:" + (tick % 3));
            if (tick == 46 + loop * 3) { end(); report.forEach(line -> System.out.println("[PaneOsPerf] " + line)); System.out.println("[PaneOsPerf] complete PASS"); client.stop(); }
            return;
        }
        switch (tick) {
            case 10 -> { for (String id : new String[] {"files", "settings", "notes"}) PaneOsEvents.openApp(screen, id); }
            case 40 -> screen.begin("full repaint");
            case 41, 42, 43, 44, 45, 46, 47, 48, 49, 50 -> screen.invalidate();
            case 52 -> { end(); PaneOsEvents.raise(screen, "notes"); screen.begin("click: select a note"); }
            case 53, 54, 55, 56, 57, 58, 59, 60 -> click("notes:item:" + (tick % 3));
            case 62 -> { end(); PaneOsEvents.raise(screen, "files"); }
            case 66 -> { bar = screen.regionBounds("files:bar").orElseThrow(); screen.press(bar.centerX(), bar.y() + 20); screen.begin("drag a window"); }
            case 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86 -> {
                screen.drag(bar.centerX() + (tick - 66) * 12, bar.y() + 20 + (tick % 2 == 0 ? 6 : -6));
            }
            case 87 -> { screen.release(bar.centerX() + 21 * 12, bar.y() + 20); }
            case 90 -> end();
            case 92 -> { var dock = screen.regionBounds("dock:files").orElseThrow(); screen.pointer(dock.centerX(), dock.centerY()); screen.begin("dock magnify sweep"); }
            case 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112 -> {
                var start = screen.regionBounds("dock:files").orElseThrow();
                screen.pointer(start.centerX() + (tick - 92) * 30, start.centerY());
            }
            case 113 -> { end(); screen.pointer(700, 400); }
            case 125 -> { screen.begin("open a window"); PaneOsEvents.openApp(screen, "music"); }
            case 140 -> { end(); screen.begin("close a window"); PaneOsEvents.handleAction(screen, "close:music"); }
            case 152 -> { end(); screen.begin("toggle dark mode"); PaneOsEvents.handleAction(screen, "darkmode"); }
            case 156 -> { end(); screen.begin("Control Center in/out"); PaneOsEvents.handleAction(screen, "cc"); }
            case 166 -> PaneOsEvents.handleAction(screen, "cc");
            case 176 -> { end(); screen.begin("Apps grid in/out"); PaneOsEvents.handleAction(screen, "apps"); }
            case 186 -> PaneOsEvents.handleAction(screen, "apps");
            case 196 -> { end(); screen.begin("Spotlight in"); PaneOsEvents.toggleSpotlight(screen); }
            case 206 -> { end(); PaneOsEvents.toggleSpotlight(screen); }
            case 212 -> { screen.setValue("locked", true); }
            case 220 -> { screen.begin("unlock"); PaneOsEvents.handleAction(screen, "unlock"); }
            case 236 -> end();
            case 240 -> {
                System.out.printf(Locale.ROOT, "[PaneOsPerf] surface %dx%d px (layout %d wide, scale %.2f)%n", screen.surfacePixelWidth(),
                    screen.surfacePixelHeight(), screen.rasterQuality().layoutWidth(), screen.surfacePixelWidth() / (double) screen.rasterQuality().layoutWidth());
                report.forEach(line -> System.out.println("[PaneOsPerf] " + line));
                System.out.println("[PaneOsPerf] complete PASS");
                client.stop();
            }
            default -> { }
        }
    }

    private void end() { report.add(screen.summary()); }

    // ------------------------------------------------------------ raster shortcut comparison

    /** Scenes painted with PaneLib's raster shortcuts on and off; every pixel must match. */
    private static final String[] SCENES = {"desktop", "control center", "dark mode", "apps grid", "spotlight", "after a click"};
    private int scene = -1, step, waitPaints, failures, compared;
    private int[] first, plain, again;

    private void compare(Minecraft client) {
        if (tick == 10) for (String id : new String[] {"files", "settings", "notes"}) PaneOsEvents.openApp(screen, id);
        if (tick < 40) return;
        if (waitPaints > 0) {
            if (screen.paintCount() >= waitPaints) waitPaints = 0;
            else return;
        }
        if (scene < 0 || step == 4) {
            if (step == 4 && !finishScene()) { step = 0; return; }
            scene++;
            step = 0;
            if (scene == SCENES.length) {
                System.out.println("[PaneOsPerf] compare " + compared + " scenes, " + failures + " differing");
                System.out.println("[PaneOsPerf] complete " + (failures == 0 && compared == SCENES.length ? "PASS" : "FAIL"));
                client.stop();
                return;
            }
            switch (SCENES[scene]) {
                case "control center" -> PaneOsEvents.handleAction(screen, "cc");
                case "dark mode" -> { PaneOsEvents.handleAction(screen, "cc"); PaneOsEvents.handleAction(screen, "darkmode"); }
                case "apps grid" -> PaneOsEvents.handleAction(screen, "apps");
                case "spotlight" -> { PaneOsEvents.handleAction(screen, "apps"); PaneOsEvents.toggleSpotlight(screen); }
                case "after a click" -> { PaneOsEvents.toggleSpotlight(screen); PaneOsEvents.raise(screen, "notes"); }
                default -> { }
            }
            // Let transitions settle before comparing.
            waitPaints = screen.paintCount() + 1;
            settleUntil = tick + 30;
            return;
        }
        if (tick < settleUntil) return;
        switch (step) {
            case 0 -> { shortcuts(true); if (SCENES[scene].equals("after a click")) click("notes:item:1"); else screen.invalidate(); waitPaints = screen.paintCount() + 1; step = 1; }
            case 1 -> { first = pixels(); shortcuts(false); screen.invalidate(); waitPaints = screen.paintCount() + 1; step = 2; }
            case 2 -> { plain = pixels(); shortcuts(true); screen.invalidate(); waitPaints = screen.paintCount() + 1; step = 3; }
            case 3 -> { again = pixels(); step = 4; }
            default -> { }
        }
    }
    private int settleUntil, retries;

    /** Returns false to repeat the scene when time-driven content changed between the two shortcut paints. */
    private boolean finishScene() {
        if (!java.util.Arrays.equals(first, again) && !SCENES[scene].equals("after a click")) {
            if (++retries < 4) return false;
            System.out.println("[PaneOsPerf] compare " + SCENES[scene] + ": the scene would not hold still");
            failures++;
            return true;
        }
        retries = 0;
        compared++;
        int differing = 0, worst = 0;
        int[] reference = SCENES[scene].equals("after a click") ? again : first;
        for (int i = 0; i < plain.length; i++) if (plain[i] != reference[i]) {
            differing++;
            for (int shift = 0; shift < 32; shift += 8) worst = Math.max(worst, Math.abs((plain[i] >>> shift & 255) - (reference[i] >>> shift & 255)));
        }
        if (SCENES[scene].equals("after a click")) {
            // The click repainted part of the surface; the plain full repaint must agree with it too.
            int partial = 0;
            for (int i = 0; i < plain.length; i++) if (plain[i] != first[i]) partial++;
            System.out.printf(Locale.ROOT, "[PaneOsPerf] compare partial repaint vs plain full repaint: %d differing pixels%n", partial);
        }
        System.out.printf(Locale.ROOT, "[PaneOsPerf] compare %-15s %d of %d pixels differ, worst channel %d%n", SCENES[scene], differing, plain.length, worst);
        if (differing != 0) failures++;
        return true;
    }

    private static void shortcuts(boolean on) {
        try {
            var field = Class.forName("dev.pane.ui.UiBands").getDeclaredField("shortcuts");
            field.setAccessible(true);
            field.setBoolean(null, on);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private int[] pixels() {
        try {
            var field = UiScreen.class.getDeclaredField("surface");
            field.setAccessible(true);
            Object surface = field.get(screen);
            var pixels = surface.getClass().getDeclaredField("pixels");
            pixels.setAccessible(true);
            return ((int[]) pixels.get(surface)).clone();
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private void click(String id) {
        var r = screen.regionBounds(id).orElseThrow();
        screen.press(r.centerX(), r.centerY());
        screen.release(r.centerX(), r.centerY());
    }

    static final class Probe extends UiScreen {
        private final ArrayList<Long> rasters = new ArrayList<>();
        private long uploadNanos, dirtyPixels, surfacePixels;
        private String phase;
        private int full, partial, startPaints, startPartials;
        private int pointerX = -100, pointerY = -100;
        private long phaseStartedMillis;

        Probe(UiDocument document, UiScreenEventHandler handler, UiScreenOptions options) {
            this(document, handler, options, new UiScreen[1]);
        }

        private Probe(UiDocument document, UiScreenEventHandler handler, UiScreenOptions options, UiScreen[] self) {
            super(document, NativeOverlayRenderer.NONE, event -> handler.onEvent(self[0], event), options);
            self[0] = this;
        }

        @Override public void mouseMoved(double x, double y) { }

        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
            int before = paintCount(), partialBefore = partialPaintCount();
            super.extractRenderState(graphics, pointerX, pointerY, delta);
            if (phase != null && paintCount() != before) {
                var snapshot = performanceSnapshot();
                rasters.add(snapshot.lastRasterNanos());
                uploadNanos += snapshot.lastUploadNanos();
                dirtyPixels += snapshot.lastDirtyPixels();
                surfacePixels += snapshot.surfacePixels();
            }
        }

        /** Queues a partial repaint of one layout rectangle, as a small state change would. */
        void repaintArea(UiRect area) {
            try {
                var method = UiScreen.class.getDeclaredMethod("requestPartialRepaint", UiRect.class);
                method.setAccessible(true);
                method.invoke(this, area);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }

        void begin(String name) { phase = name; phaseStartedMillis = System.currentTimeMillis(); rasters.clear(); uploadNanos = dirtyPixels = surfacePixels = 0; startPaints = paintCount(); startPartials = partialPaintCount(); }

        String summary() {
            var sorted = new ArrayList<>(rasters);
            Collections.sort(sorted);
            int paints = paintCount() - startPaints, partials = partialPaintCount() - startPartials;
            double average = sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
            double worst = sorted.isEmpty() ? 0 : sorted.getLast() / 1e6;
            String line = String.format(Locale.ROOT, "%-22s paints=%3d (full %3d) avg=%6.1fms max=%6.1fms upload=%5.1fms dirty=%3.0f%%", phase, paints, paints - partials,
                average, worst, rasters.isEmpty() ? 0 : uploadNanos / 1e6 / rasters.size(), surfacePixels == 0 ? 0 : dirtyPixels * 100D / surfacePixels);
            // Wall-clock bounds let a profiler recording be cut to one phase.
            System.out.printf(Locale.ROOT, "[PaneOsPerf] window %d %d %s%n", phaseStartedMillis, System.currentTimeMillis(), phase);
            phase = null;
            return line;
        }

        private float scale() { return layoutScale(); }
        void pointer(float x, float y) { pointerX = Math.round(x / scale()); pointerY = Math.round(y / scale()); super.mouseMoved(x / scale(), y / scale()); }
        private MouseButtonEvent at(float x, float y) { return new MouseButtonEvent(x / scale(), y / scale(), new MouseButtonInfo(0, 0)); }
        void press(float x, float y) { pointer(x, y); mouseClicked(at(x, y), false); }
        void drag(float x, float y) { pointer(x, y); mouseDragged(at(x, y), 0, 0); }
        void release(float x, float y) { mouseReleased(at(x, y)); }
    }
}
