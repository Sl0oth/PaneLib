package dev.pane.ui;

import java.awt.Font;

/** Shared text metrics and padding for fields, password fields, and multiline editors. */
public record UiInputStyle(float fontSize, int fontStyle, String fontFamily,
                           float paddingX, float paddingY, float lineHeight) {
    public UiInputStyle {
        if (!Float.isFinite(fontSize) || fontSize <= 0 || !Float.isFinite(lineHeight) || lineHeight <= 0
                || !Float.isFinite(paddingX) || !Float.isFinite(paddingY) || paddingX < 0 || paddingY < 0)
            throw new IllegalArgumentException("Input metrics must be finite and positive; padding may be zero");
        if ((fontStyle & ~(Font.BOLD | Font.ITALIC)) != 0) throw new IllegalArgumentException("Invalid font style");
    }
    private static final UiInputStyle DEFAULT = new UiInputStyle(9, Font.PLAIN, null, 10, 6, 14);

    /** Null family inherits the document theme. Existing overloads retain these metrics. */
    public static UiInputStyle defaults() { return DEFAULT; }
    public UiInputStyle font(String family, float size, int style) {
        return new UiInputStyle(size, style, family, paddingX, paddingY, Math.max(lineHeight, size * 1.5F));
    }
    public UiInputStyle padding(float horizontal, float vertical) {
        return new UiInputStyle(fontSize, fontStyle, fontFamily, horizontal, vertical, lineHeight);
    }
    public UiInputStyle lineHeight(float height) {
        return new UiInputStyle(fontSize, fontStyle, fontFamily, paddingX, paddingY, height);
    }
}
