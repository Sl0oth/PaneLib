package dev.pane.demo;

import dev.pane.ui.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import jdk.jfr.Event;
import jdk.jfr.Name;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Controlled dev-only profiling. No screenshot capture or boxed per-frame samples. */
final class AtlasBenchmark {
    static final boolean ENABLED = Boolean.getBoolean("pane.demo.benchmark");
    private static final com.sun.management.ThreadMXBean THREADS = ENABLED
        ? (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean() : null;
    private static final Samples UI = ENABLED ? new Samples("ui") : null;
    private static final Samples HUD = ENABLED ? new Samples("hud") : null;
    private static boolean measuring, hudMeasuring;
    private static long previousUi, previousHud, hudStart, hudAllocated;
    private static long previousHudRaster, previousHudUpload, previousHudPixels;
    private static int previousPaints;
    private static Phase event;

    @Name("dev.pane.AtlasPhase")
    static final class Phase extends Event { String phase; int cycle; }

    static long allocated() {
        return ENABLED ? THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId()) : 0;
    }
    static void beginCycle(UiScreen screen, int cycle) {
        if (!ENABLED) return;
        measuring = cycle >= 2;
        previousUi = 0; previousPaints = screen.paintCount();
        event = new Phase(); event.phase = measuring ? "ui-measured" : "ui-warmup"; event.cycle = cycle;
        event.begin();
        System.out.println("[AtlasBench] begin " + event.phase + " cycle=" + cycle);
    }
    static void endCycle() {
        if (!ENABLED) return;
        measuring = false; event.end(); event.commit();
    }
    static void observe(UiScreen screen, long start, long allocationBefore) {
        if (!ENABLED || !measuring) return;
        long end = System.nanoTime(), bytes = allocated() - allocationBefore;
        var sample = screen.performanceSnapshot();
        boolean painted = screen.paintCount() != previousPaints;
        UI.add(previousUi == 0 ? 0 : start - previousUi, end - start,
            painted ? sample.lastRasterNanos() : 0, painted ? sample.lastUploadNanos() : 0,
            painted ? screen.lastUploadPixelCount() : 0, bytes);
        previousUi = start; previousPaints = screen.paintCount();
    }
    static void installHudHooks() {
        if (!ENABLED) return;
        if (!THREADS.isThreadAllocatedMemoryEnabled()) THREADS.setThreadAllocatedMemoryEnabled(true);
        var target = Identifier.fromNamespaceAndPath("panelib", "retained_huds");
        HudElementRegistry.attachElementBefore(target, Identifier.fromNamespaceAndPath("pane_demo", "benchmark_begin"), (g, d) -> {
            if (hudMeasuring) { hudStart = System.nanoTime(); hudAllocated = allocated(); }
        });
        HudElementRegistry.attachElementAfter(target, Identifier.fromNamespaceAndPath("pane_demo", "benchmark_end"), (g, d) -> {
            if (!hudMeasuring) return;
            long end = System.nanoTime(), bytes = allocated() - hudAllocated;
            var a = FieldHudDemo.vitals().snapshot(); var b = FieldHudDemo.navigation().snapshot();
            long raster = a.totalRasterNanos() + b.totalRasterNanos(), upload = a.totalUploadNanos() + b.totalUploadNanos();
            long pixels = a.uploadedPixels() + b.uploadedPixels();
            HUD.add(previousHud == 0 ? 0 : hudStart - previousHud, end - hudStart,
                raster - previousHudRaster, upload - previousHudUpload, pixels - previousHudPixels, bytes);
            previousHud = hudStart; previousHudRaster = raster; previousHudUpload = upload; previousHudPixels = pixels;
        });
    }
    static void beginHud() {
        previousHud = 0;
        var a = FieldHudDemo.vitals().snapshot(); var b = FieldHudDemo.navigation().snapshot();
        previousHudRaster = a.totalRasterNanos() + b.totalRasterNanos();
        previousHudUpload = a.totalUploadNanos() + b.totalUploadNanos();
        previousHudPixels = a.uploadedPixels() + b.uploadedPixels();
        event = new Phase(); event.phase = "hud-measured"; event.cycle = 0; event.begin();
        hudMeasuring = true;
    }
    static void finish(Minecraft client) {
        hudMeasuring = false; event.end(); event.commit();
        try {
            String label = System.getProperty("pane.demo.benchLabel", "sample").replaceAll("[^a-zA-Z0-9_-]", "_");
            UI.write(client.gameDirectory.toPath(), label); HUD.write(client.gameDirectory.toPath(), label);
            var runtime = Runtime.getRuntime();
            long gcCount = ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum();
            long gcMillis = ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionTime())).sum();
            System.out.println("[AtlasBench] memory heapUsed=" + (runtime.totalMemory() - runtime.freeMemory())
                + " heapCommitted=" + runtime.totalMemory() + " heapMax=" + runtime.maxMemory()
                + " gcCount=" + gcCount + " gcMillis=" + gcMillis);
            System.out.println("[AtlasBench] config window=" + client.getWindow().getScreenWidth() + "x" + client.getWindow().getScreenHeight()
                + " framebuffer=" + client.getWindow().getWidth() + "x" + client.getWindow().getHeight()
                + " guiScale=" + client.getWindow().getGuiScale() + " fpsCap=" + client.options.framerateLimit().get()
                + " vsync=" + client.options.enableVsync().get() + " renderDistance=" + client.options.renderDistance().get());
            System.out.println("[AtlasBench] complete");
        } catch (java.io.IOException error) { throw new RuntimeException(error); }
    }

    private static final class Samples {
        final String name;
        final long[][] rows = new long[12_000][6];
        int count;
        long peakHeap;
        Samples(String name) { this.name = name; }
        void add(long interval, long cpu, long raster, long upload, long pixels, long allocated) {
            if (count >= rows.length) throw new IllegalStateException("Benchmark sample capacity exceeded");
            long[] row = rows[count++];
            row[0] = interval; row[1] = cpu; row[2] = raster; row[3] = upload; row[4] = pixels; row[5] = allocated;
            var runtime = Runtime.getRuntime(); peakHeap = Math.max(peakHeap, runtime.totalMemory() - runtime.freeMemory());
        }
        void write(Path directory, String label) throws java.io.IOException {
            var csv = new StringBuilder("frameIntervalNanos,extractNanos,rasterNanos,uploadNanos,uploadPixels,allocatedBytes\n");
            long pixels = 0, allocation = 0; int paints = 0;
            for (int i = 0; i < count; i++) {
                for (int j = 0; j < 6; j++) { if (j > 0) csv.append(','); csv.append(rows[i][j]); }
                csv.append('\n'); pixels += rows[i][4]; allocation += rows[i][5]; if (rows[i][2] > 0) paints++;
            }
            Files.writeString(directory.resolve(label + "-" + name + ".csv"), csv);
            System.out.printf(Locale.ROOT, "[AtlasBench] %s frames=%d paints=%d frameP50Ms=%.3f frameP95Ms=%.3f frameP99Ms=%.3f cpuP50Ms=%.3f cpuP95Ms=%.3f cpuP99Ms=%.3f paintRasterP95Ms=%.3f uploadPixels=%d allocatedBytes=%d allocatedPerFrame=%d peakHeap=%d%n",
                name, count, paints, percentile(0,.5,false), percentile(0,.95,false), percentile(0,.99,false),
                percentile(1,.5,false), percentile(1,.95,false), percentile(1,.99,false), percentile(2,.95,true),
                pixels, allocation, count == 0 ? 0 : allocation/count, peakHeap);
        }
        double percentile(int column, double q, boolean positiveOnly) {
            long[] values = new long[count]; int size = 0;
            for (int i = 0; i < count; i++) if ((!positiveOnly && column != 0) || rows[i][column] > 0) values[size++] = rows[i][column];
            if (size == 0) return 0;
            Arrays.sort(values, 0, size); return values[Math.min(size-1, (int)Math.ceil(size*q)-1)] / 1e6;
        }
    }
}
