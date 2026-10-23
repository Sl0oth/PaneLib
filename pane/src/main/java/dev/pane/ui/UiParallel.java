package dev.pane.ui;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.function.IntConsumer;

/**
 * PaneLib's own raster threads. Java2D keeps each thread's rasterizer state, with its grown scratch
 * arrays, in a thread local; the common pool's workers may discard thread locals between tasks,
 * which rebuilt that state for every banded draw. These threads keep it for as long as they live.
 * They are daemons, so they never hold the game open.
 */
final class UiParallel {
    /** Worker threads besides the caller, which always does a share itself. */
    static final int THREADS = Math.max(0, Runtime.getRuntime().availableProcessors() - 1);
    private static final ForkJoinPool POOL = THREADS == 0 ? null : new ForkJoinPool(THREADS, pool -> {
        var thread = new ForkJoinWorkerThread(pool) { };
        thread.setName("PaneLib raster " + thread.getPoolIndex());
        thread.setDaemon(true);
        return thread;
    }, null, false);

    private UiParallel() { }

    /**
     * Runs {@code task} for 0 to {@code count - 1}, shared out in contiguous runs between the caller
     * and the raster threads, and returns once every run has finished. Tasks must be independent.
     */
    static void forEach(int count, IntConsumer task) {
        int runs = POOL == null ? 1 : Math.min(count, THREADS + 1);
        if (runs <= 1) {
            for (int i = 0; i < count; i++) task.accept(i);
            return;
        }
        ForkJoinTask<?>[] others = new ForkJoinTask<?>[runs - 1];
        // The first failure is kept as thrown; joining would rethrow a copy.
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        for (int run = 1; run < runs; run++) {
            int from = (int) ((long) count * run / runs), to = (int) ((long) count * (run + 1) / runs);
            others[run - 1] = POOL.submit(() -> {
                try { for (int i = from; i < to; i++) task.accept(i); }
                catch (Throwable error) { failure.compareAndSet(null, error); }
            });
        }
        try {
            for (int i = 0, end = count / runs; i < end; i++) task.accept(i);
        } catch (Throwable error) {
            failure.compareAndSet(null, error);
        }
        // Every run finishes before the caller goes on, even when one failed.
        for (ForkJoinTask<?> other : others) other.join();
        Throwable error = failure.get();
        if (error instanceof RuntimeException runtime) throw runtime;
        if (error instanceof Error fatal) throw fatal;
        if (error != null) throw new IllegalStateException(error);
    }
}
