package dev.pane.ui;

/** Optional per-button paint overrides. Unset values inherit sensible theme defaults. */
public final class UiButtonStyle {
    private final Integer background, hoverBackground, pressedBackground, border, text, focus, shadow;
    private final Float radius, borderWidth, textSize, shadowSpread, shadowOffsetX, shadowOffsetY;
    private final Integer fontStyle;
    private final String fontFamily;

    private UiButtonStyle(Builder b) {
        background = b.background;
        hoverBackground = b.hoverBackground;
        pressedBackground = b.pressedBackground;
        border = b.border;
        text = b.text;
        focus = b.focus;
        shadow = b.shadow;
        radius = b.radius;
        borderWidth = b.borderWidth;
        textSize = b.textSize;
        shadowSpread = b.shadowSpread;
        shadowOffsetX = b.shadowOffsetX;
        shadowOffsetY = b.shadowOffsetY;
        fontStyle = b.fontStyle;
        fontFamily = b.fontFamily;
    }

    public static Builder builder() { return new Builder(); }
    public Integer background() { return background; }
    public Integer hoverBackground() { return hoverBackground; }
    public Integer pressedBackground() { return pressedBackground; }
    public Integer border() { return border; }
    public Integer text() { return text; }
    public Integer focus() { return focus; }
    public Integer shadow() { return shadow; }
    public Float radius() { return radius; }
    public Float borderWidth() { return borderWidth; }
    public Float textSize() { return textSize; }
    public Float shadowSpread() { return shadowSpread; }
    public Float shadowOffsetX() { return shadowOffsetX; }
    public Float shadowOffsetY() { return shadowOffsetY; }
    public Integer fontStyle() { return fontStyle; }
    public String fontFamily() { return fontFamily; }

    public static final class Builder {
        private Integer background, hoverBackground, pressedBackground, border, text, focus, shadow;
        private Float radius, borderWidth, textSize, shadowSpread, shadowOffsetX, shadowOffsetY;
        private Integer fontStyle;
        private String fontFamily;

        public Builder background(int value) { background = value; return this; }
        public Builder hoverBackground(int value) { hoverBackground = value; return this; }
        public Builder pressedBackground(int value) { pressedBackground = value; return this; }
        public Builder border(int value) { border = value; return this; }
        public Builder text(int value) { text = value; return this; }
        public Builder focus(int value) { focus = value; return this; }
        public Builder radius(float value) { radius = Math.max(0, value); return this; }
        public Builder borderWidth(float value) { borderWidth = Math.max(0, value); return this; }
        public Builder textSize(float value) { textSize = Math.max(1, value); return this; }
        public Builder fontStyle(int value) { fontStyle = value; return this; }
        public Builder fontFamily(String value) { fontFamily = value; return this; }

        /** Adds a retained raster shadow. A zero spread disables it. */
        public Builder shadow(int color, float spread, float offsetX, float offsetY) {
            shadow = color;
            shadowSpread = Math.max(0, spread);
            shadowOffsetX = offsetX;
            shadowOffsetY = offsetY;
            return this;
        }

        public UiButtonStyle build() { return new UiButtonStyle(this); }
    }
}
