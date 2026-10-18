package dev.pane.ui;

/** Document-wide defaults for native toast notifications. */
public record UiToastSettings(UiToastPlacement placement, int maxVisible, float margin, float gap,
                              int fadeMillis, UiToastStyle defaultStyle) {
    public UiToastSettings {
        placement = placement == null ? UiToastPlacement.AUTO : placement;
        maxVisible = Math.max(1, Math.min(8, maxVisible));
        margin = Math.max(4, Math.min(80, margin));
        gap = Math.max(0, Math.min(40, gap));
        fadeMillis = Math.max(0, Math.min(2_000, fadeMillis));
    }

    public UiToastSettings(UiToastPlacement placement, int maxVisible, float margin, float gap, int fadeMillis) {
        this(placement, maxVisible, margin, gap, fadeMillis, null);
    }

    public static UiToastSettings defaults() {
        return new UiToastSettings(UiToastPlacement.AUTO, 4, 16, 8, 180, null);
    }
}
