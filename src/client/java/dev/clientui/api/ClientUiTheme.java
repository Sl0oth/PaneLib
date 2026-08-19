package dev.clientui.api;

/** Immutable design tokens. Every value becomes a CSS custom property in the document. */
public record ClientUiTheme(
    String background,
    String surface,
    String elevatedSurface,
    String border,
    String text,
    String mutedText,
    String accent,
    String accentText,
    int smallRadius,
    int mediumRadius,
    int largeRadius,
    double density,
    double motionScale
) {
    public ClientUiTheme {
        background = cssValue(background);
        surface = cssValue(surface);
        elevatedSurface = cssValue(elevatedSurface);
        border = cssValue(border);
        text = cssValue(text);
        mutedText = cssValue(mutedText);
        accent = cssValue(accent);
        accentText = cssValue(accentText);
        smallRadius = clamp(smallRadius, 0, 40);
        mediumRadius = clamp(mediumRadius, 0, 48);
        largeRadius = clamp(largeRadius, 0, 64);
        density = Math.clamp(density, 0.72, 1.35);
        motionScale = Math.clamp(motionScale, 0.0, 2.0);
    }

    public static ClientUiTheme dark() {
        return new ClientUiTheme(
            "#0d0f11", "#15171a", "#1b1e22", "#2d3137",
            "#f2f4f7", "#8d96a6", "#58a6ff", "#07111d",
            8, 13, 18, 1.0, 1.0
        );
    }

    String cssVariables() {
        return "--ui-bg:" + background + ";--ui-surface:" + surface
            + ";--ui-elevated:" + elevatedSurface + ";--ui-border:" + border
            + ";--ui-text:" + text + ";--ui-muted:" + mutedText
            + ";--ui-accent:" + accent + ";--ui-accent-text:" + accentText
            + ";--ui-r-sm:" + smallRadius + "px;--ui-r-md:" + mediumRadius
            + "px;--ui-r-lg:" + largeRadius + "px;--ui-density:" + density
            + ";--ui-motion:" + motionScale + ";";
    }

    private static String cssValue(String value) {
        if (value == null || value.isBlank() || value.matches(".*[;{}<>].*")) {
            throw new IllegalArgumentException("Unsafe or empty CSS token");
        }
        return value.strip();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
