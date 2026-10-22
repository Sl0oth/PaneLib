package dev.pane.demo;

import dev.pane.ui.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;

/** Isolated retained-renderer benchmark: actual showcase painter, no world or native entities. */
final class DemoPerformanceTest {
    private Probe screen;
    private long started;
    private int stage;

    void tick(Minecraft client) {
        long now = System.nanoTime();
        if (screen == null) {
            if (!(client.gui.screen() instanceof TitleScreen)) return;
            screen = new Probe();
            client.gui.setScreen(screen);
            started = now;
            return;
        }
        if (stage == 6) screen.invalidate();
        double seconds = (now - started) / 1e9;
        if (stage == 0 && seconds >= 3) {
            screen.reset();
            stage++;
        } else if (stage == 1 && seconds >= 6) {
            screen.report("idle-3s");
            screen.toast(new UiToast("perf", "Saved", "Retained toast benchmark", 6000));
            stage++;
        } else if (stage == 2 && seconds >= 7) {
            screen.reset();
            stage++;
        } else if (stage == 3 && seconds >= 10) {
            screen.report("steady-toast-3s");
            net.minecraft.client.Screenshot.grab(client.gameDirectory, "pane-perf-toast.png",
                client.gameRenderer.mainRenderTarget(), 1, message -> { });
            screen.reset();
            stage++;
        } else if (stage == 4 && seconds >= 13) {
            screen.report("toast-expiry-3s");
            screen.check(screen.activeToastCount() == 0, "toast expired");
            screen.reset();
            stage++;
        } else if (stage == 5 && seconds >= 16) {
            screen.report("post-toast-idle-3s");
            screen.reset();
            stage++;
        } else if (stage == 6 && seconds >= 19) {
            screen.report("forced-repaint-3s");
            stage++;
            System.out.println("[PanePerf] complete " + (screen.failed ? "FAIL" : "PASS"));
            client.stop();
        }
    }

    private static final class Probe extends UiScreen {
        private final ArrayList<Long> frames = new ArrayList<>();
        private final ArrayList<Long> extracts = new ArrayList<>();
        private long previousFrame, raster, upload, pixels, measurementStart, toastPixels;
        private boolean failed;
        private int paints;
        private final int[] callbacks;

        Probe() { this(new int[1]); }

        Probe(int[] callbacks) {
            super(GmailShowcase.createScreen(null).document(), NativeOverlayRenderer.NONE,
                UiEventListener.NONE, UiScreenOptions.builder()
                    .performanceDiagnostics(sample -> callbacks[0]++).build());
            this.callbacks = callbacks;
        }

        @Override public void mouseMoved(double x, double y) { }

        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
            long start = System.nanoTime();
            int before = paintCount();
            // Keep real cursor movement out of this controlled measurement.
            super.extractRenderState(graphics, -100, -100, delta);
            extracts.add(System.nanoTime() - start);
            if (previousFrame != 0) frames.add(start - previousFrame);
            previousFrame = start;
            if (paintCount() != before) {
                var sample = performanceSnapshot();
                paints++;
                raster += sample.lastRasterNanos();
                upload += sample.lastUploadNanos();
                pixels += lastUploadPixelCount();
            }
        }

        void reset() {
            frames.clear(); extracts.clear(); previousFrame = 0;
            measurementStart = System.nanoTime();
            toastPixels = toastUploadPixels();
            paints = callbacks[0] = 0; raster = upload = pixels = 0;
        }

        void report(String name) {
            check(frames.size() >= 30, name + " rendered enough frames");
            if (name.contains("idle") || name.startsWith("steady"))
                check(paints == 0 && pixels == 0, name + " retained surface stayed cached");
            if (name.startsWith("forced")) check(paints >= 10, "forced invalidation painted");
            long callbackLimit = (System.nanoTime() - measurementStart) / 250_000_000L + 2;
            check(callbacks[0] <= callbackLimit, name + " diagnostics bounded");
            var sample = performanceSnapshot();
            System.out.printf(Locale.ROOT,
                "[PanePerf] %s surfacePixels=%d frames=%d paints=%d callbacks=%d uploadPixels=%d toastUploadPixels=%d rasterMs=%.3f uploadMs=%.3f extractP95Ms=%.3f extractP99Ms=%.3f frameP95Ms=%.3f frameP99Ms=%.3f%n",
                name, sample.surfacePixels(), frames.size(), paints, callbacks[0], pixels, toastUploadPixels() - toastPixels, raster / 1e6, upload / 1e6,
                percentile(extracts, .95), percentile(extracts, .99), percentile(frames, .95), percentile(frames, .99));
        }

        void check(boolean condition, String label) {
            failed |= !condition;
            System.out.println("[PanePerf] " + label + ": " + (condition ? "PASS" : "FAIL"));
        }

        private static double percentile(ArrayList<Long> values, double fraction) {
            if (values.isEmpty()) return 0;
            Collections.sort(values);
            return values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * fraction) - 1)) / 1e6;
        }
    }
}
