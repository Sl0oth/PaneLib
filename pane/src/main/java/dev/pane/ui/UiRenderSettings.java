package dev.pane.ui;

/** Quality and memory limits for the retained raster surface. */
public record UiRenderSettings(int maximumWidth, int maximumHeight, float maximumScale) {
    public UiRenderSettings {
        maximumWidth = Math.max(512, Math.min(8192, maximumWidth));
        maximumHeight = Math.max(512, Math.min(8192, maximumHeight));
        maximumScale = Math.max(1, Math.min(4, maximumScale));
    }

    /** Sharp on normal and HiDPI displays while keeping the cached texture under about 15MB. */
    public static UiRenderSettings balanced() { return new UiRenderSettings(2560, 1440, 2); }

    /** Higher ceiling for typography-heavy fullscreen interfaces on modern GPUs. */
    public static UiRenderSettings highQuality() { return new UiRenderSettings(4096, 2304, 3); }

    /** Opt-in full-screen typography ceiling, up to 62.5 MiB per RGBA surface; not a low-memory preset. */
    public static UiRenderSettings typography() { return new UiRenderSettings(5120, 3200, 4); }

    /** Conservative ceiling for integrated and older GPUs. */
    public static UiRenderSettings lowMemory() { return new UiRenderSettings(1920, 1080, 1.5F); }
}
