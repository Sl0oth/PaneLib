package dev.pane.ui;

import net.minecraft.client.Minecraft;

/**
 * How long a retained surface waits between animation repaints. The wait follows the display, so a
 * 144 Hz monitor animates at 144 frames a second while an uncapped game still repaints at most once
 * per displayed frame.
 */
final class UiFrameRate {
    private static final long REFRESH_NANOS = 1_000_000_000L;
    private static int displayHz = 60;
    private static long displayReadAt;
    private static boolean displayRead;

    private UiFrameRate() { }

    /**
     * Nanoseconds between repaints on a display refreshing {@code displayHz} times a second, repainting
     * at most {@code maximumFrameRate} times a second; zero or less follows the display. A capped rate
     * is a whole number of display frames, so its frames stay evenly spaced. The wait is a tenth short
     * of the frame, which keeps a frame that arrives slightly early from being skipped.
     */
    static long intervalNanos(int displayHz, int maximumFrameRate) {
        int hz = displayHz <= 0 ? 60 : Math.max(24, Math.min(480, displayHz));
        int frames = maximumFrameRate <= 0 || maximumFrameRate >= hz ? 1
            : (int) Math.ceil(hz / (double) Math.max(1, maximumFrameRate) - .01);
        return (long) (1_000_000_000D / hz * (frames - .1));
    }

    /** The wait for the window's current display; call on the render thread. */
    static long intervalNanos(int maximumFrameRate, long now) {
        if (!displayRead || now - displayReadAt >= REFRESH_NANOS) {
            displayRead = true;
            displayReadAt = now;
            try {
                displayHz = Minecraft.getInstance().getWindow().getRefreshRate();
            } catch (RuntimeException unknown) {
                displayHz = 60;
            }
        }
        return intervalNanos(displayHz, maximumFrameRate);
    }
}
