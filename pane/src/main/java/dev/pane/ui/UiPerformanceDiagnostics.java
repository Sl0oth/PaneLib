package dev.pane.ui;

/**
 * Opt-in retained-renderer diagnostics. PaneLib does not allocate listener snapshots or invoke callbacks
 * when this is {@link #NONE}; consumers can install a listener while profiling a specific screen.
 */
@FunctionalInterface
public interface UiPerformanceDiagnostics {
    /** Disabled default with no per-frame allocations or callbacks. */
    UiPerformanceDiagnostics NONE = snapshot -> { };

    /** Receives a bounded-rate snapshot on the Minecraft render thread. Keep this callback cheap. */
    void onSample(UiPerformanceSnapshot snapshot);
}
