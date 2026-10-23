package dev.pane.ui;

/** Document-wide defaults for native toast notifications. */
public record UiToastSettings(UiToastPlacement placement, int maxVisible, float margin, float gap,
                              int fadeMillis, UiToastStyle defaultStyle) {
    /** A missing placement means automatic; keeps 1 to 8 visible, the margin 4-80, the gap 0-40 and the fade up to 2 seconds. */
    public UiToastSettings {
        placement = placement == null ? UiToastPlacement.AUTO : placement;
        maxVisible = Math.max(1, Math.min(8, maxVisible));
        margin = Math.max(4, Math.min(80, margin));
        gap = Math.max(0, Math.min(40, gap));
        fadeMillis = Math.max(0, Math.min(2_000, fadeMillis));
    }

    /** Settings with the default style. */
    public UiToastSettings(UiToastPlacement placement, int maxVisible, float margin, float gap, int fadeMillis) {
        this(placement, maxVisible, margin, gap, fadeMillis, null);
    }

    /** The default toast behaviour. */
    public static UiToastSettings defaults() {
        return new UiToastSettings(UiToastPlacement.AUTO, 4, 16, 8, 180, null);
    }
}
