package dev.pane.ui;

/**
 * Quality, memory and frame-rate limits for the retained raster surface. {@code maximumFrameRate} is
 * the most animation repaints a second; zero follows the display, so a 144 Hz monitor animates at 144.
 */
public record UiRenderSettings(int maximumWidth, int maximumHeight, float maximumScale, int maximumFrameRate) {
    /**
     * Requires a finite scale; keeps each surface side between 512 and 8192 pixels, the scale between
     * 1 and 4, and a frame-rate ceiling between 15 and 480, with zero or less meaning the display's rate.
     */
    public UiRenderSettings {
        if (!Float.isFinite(maximumScale)) throw new IllegalArgumentException("maximumScale must be finite");
        maximumWidth = Math.max(512, Math.min(8192, maximumWidth));
        maximumHeight = Math.max(512, Math.min(8192, maximumHeight));
        maximumScale = Math.max(1, Math.min(4, maximumScale));
        maximumFrameRate = maximumFrameRate <= 0 ? 0 : Math.max(15, Math.min(480, maximumFrameRate));
    }

    /** Limits that animate at the display's refresh rate. */
    public UiRenderSettings(int maximumWidth, int maximumHeight, float maximumScale) {
        this(maximumWidth, maximumHeight, maximumScale, 0);
    }

    /** A copy that repaints animation at most {@code framesPerSecond} times a second; zero follows the display. */
    public UiRenderSettings withMaximumFrameRate(int framesPerSecond) {
        return new UiRenderSettings(maximumWidth, maximumHeight, maximumScale, framesPerSecond);
    }

    /** Sharp on normal and HiDPI displays while keeping the cached texture under about 15MB. */
    public static UiRenderSettings balanced() { return new UiRenderSettings(2560, 1440, 2); }

    /** Higher ceiling for typography-heavy fullscreen interfaces on modern GPUs. */
    public static UiRenderSettings highQuality() { return new UiRenderSettings(4096, 2304, 3); }

    /** Opt-in full-screen typography ceiling, up to 62.5 MiB per RGBA surface; not a low-memory preset. */
    public static UiRenderSettings typography() { return new UiRenderSettings(5120, 3200, 4); }

    /** Conservative ceiling for integrated and older GPUs; animation repaints at most 60 times a second. */
    public static UiRenderSettings lowMemory() { return new UiRenderSettings(1920, 1080, 1.5F, 60); }

    /** Legacy HUD memory/scale ceilings: at most 2048x2048 (16 MiB per RGBA surface), 2x. */
    public static UiRenderSettings hud() { return new UiRenderSettings(2048, 2048, 2); }
}
