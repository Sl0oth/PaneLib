package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicIntegerArray;
import org.junit.jupiter.api.Test;

class UiParallelTest {
    @Test
    void everyIndexRunsExactlyOnceBeforeReturning() {
        for (int count : new int[] {0, 1, 2, 7, 64, 1000}) {
            var runs = new AtomicIntegerArray(count);
            UiParallel.forEach(count, i -> { runs.incrementAndGet(i); });
            for (int i = 0; i < count; i++) assertEquals(1, runs.get(i), "index " + i + " of " + count);
        }
    }

    @Test
    void workersAreDaemonsThatKeepTheirName() {
        var caller = Thread.currentThread();
        var workers = java.util.concurrent.ConcurrentHashMap.<Thread>newKeySet();
        UiParallel.forEach(64, i -> { if (Thread.currentThread() != caller) workers.add(Thread.currentThread()); });
        for (Thread worker : workers) {
            assertTrue(worker.getName().startsWith("PaneLib raster "), worker.getName());
            assertTrue(worker.isDaemon(), worker.getName() + " never holds the game open");
        }
    }

    @Test
    void aFailureSurfacesOnlyAfterEveryRunFinished() {
        var runs = new AtomicIntegerArray(200);
        var error = assertThrows(IllegalStateException.class, () -> UiParallel.forEach(200, i -> {
            runs.incrementAndGet(i);
            if (i == 150) throw new IllegalStateException("boom");
        }));
        assertEquals("boom", error.getMessage());
        int ran = 0;
        for (int i = 0; i < 200; i++) ran += runs.get(i);
        assertTrue(ran >= 151, "runs before the failure completed");
    }
}
