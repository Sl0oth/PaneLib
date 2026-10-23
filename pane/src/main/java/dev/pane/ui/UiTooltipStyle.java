package dev.pane.ui;

/**
 * Appearance of native hover tooltips for a document. A null background is a near-black plate and a
 * null border is the theme border. Null text is the theme text when it reads on the plate, otherwise
 * near-white or near-black, whichever contrasts more, so light themes stay legible on the dark plate.
 * {@code preferAbove} places the tip over its control when it fits, as for a dock or bottom toolbar.
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

    private static final int PLATE = 0xFF111419, LIGHT_TEXT = 0xFFF2F4F7;

    public static UiTooltipStyle defaults() { return DEFAULT; }
    /** The plate colour drawn for this style. */
    public int plate() { return background == null ? PLATE : background; }
    /** The text colour drawn on {@link #plate()} for a theme whose text colour is {@code themeText}. */
    public int textOn(int themeText) {
        if (text != null) return text;
        int plate = plate();
        if (contrast(themeText, plate) >= 4.5) return themeText;
        return contrast(LIGHT_TEXT, plate) >= contrast(PLATE, plate) ? LIGHT_TEXT : PLATE;
    }

    /** WCAG contrast ratio between two opaque sRGB colours. */
    private static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }
    private static double luminance(int argb) {
        return .2126 * linear(argb >> 16 & 0xFF) + .7152 * linear(argb >> 8 & 0xFF) + .0722 * linear(argb & 0xFF);
    }
    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
    }
    public UiTooltipStyle background(int value) { return new UiTooltipStyle(value, border, text, textSize, fontStyle, height, radius, preferAbove); }
    public UiTooltipStyle border(int value) { return new UiTooltipStyle(background, value, text, textSize, fontStyle, height, radius, preferAbove); }
    public UiTooltipStyle text(int value) { return new UiTooltipStyle(background, border, value, textSize, fontStyle, height, radius, preferAbove); }
    public UiTooltipStyle font(float size, int style) { return new UiTooltipStyle(background, border, text, size, style, Math.max(height, size + 10), radius, preferAbove); }
    public UiTooltipStyle shape(float plateHeight, float cornerRadius) { return new UiTooltipStyle(background, border, text, textSize, fontStyle, plateHeight, cornerRadius, preferAbove); }
    public UiTooltipStyle preferAbove(boolean value) { return new UiTooltipStyle(background, border, text, textSize, fontStyle, height, radius, value); }
}
