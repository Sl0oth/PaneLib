package dev.pane.ui;

/**
 * Appearance of native hover tooltips for a document. Null colours follow the theme: a near-black
 * plate, the theme border and the theme text. {@code preferAbove} places the tip over its control
 * when it fits, as for a dock or bottom toolbar.
 */
public record UiTooltipStyle(Integer background, Integer border, Integer text, float textSize, int fontStyle,
                             float height, float radius, boolean preferAbove) {
    private static final UiTooltipStyle DEFAULT = new UiTooltipStyle(0xFF111419, null, null, 8, java.awt.Font.PLAIN, 23, 6, false);

    public UiTooltipStyle {
        if (!Float.isFinite(textSize) || !Float.isFinite(height) || !Float.isFinite(radius))
            throw new IllegalArgumentException("Tooltip metrics must be finite");
        textSize = Math.max(4, Math.min(48, textSize));
        height = Math.max(textSize + 4, Math.min(96, height));
        radius = Math.max(0, Math.min(height / 2, radius));
    }

    public static UiTooltipStyle defaults() { return DEFAULT; }
    public UiTooltipStyle background(int value) { return new UiTooltipStyle(value, border, text, textSize, fontStyle, height, radius, preferAbove); }
    public UiTooltipStyle border(int value) { return new UiTooltipStyle(background, value, text, textSize, fontStyle, height, radius, preferAbove); }
    public UiTooltipStyle text(int value) { return new UiTooltipStyle(background, border, value, textSize, fontStyle, height, radius, preferAbove); }
    public UiTooltipStyle font(float size, int style) { return new UiTooltipStyle(background, border, text, size, style, Math.max(height, size + 10), radius, preferAbove); }
    public UiTooltipStyle shape(float plateHeight, float cornerRadius) { return new UiTooltipStyle(background, border, text, textSize, fontStyle, plateHeight, cornerRadius, preferAbove); }
    public UiTooltipStyle preferAbove(boolean value) { return new UiTooltipStyle(background, border, text, textSize, fontStyle, height, radius, value); }
}
