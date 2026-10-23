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

    /** Starts a style with every value unset, so each uses the default. */
    public static Builder builder() { return new Builder(); }
    /** The background colour as ARGB, or null when unset, which uses the default. */
    public Integer background() { return background; }
    /** The hover background colour as ARGB, or null when unset, which uses the default. */
    public Integer hoverBackground() { return hoverBackground; }
    /** The pressed background colour as ARGB, or null when unset, which uses the default. */
    public Integer pressedBackground() { return pressedBackground; }
    /** The border colour as ARGB, or null when unset, which uses the default. */
    public Integer border() { return border; }
    /** The text colour as ARGB, or null when unset, which uses the default. */
    public Integer text() { return text; }
    /** The focus colour as ARGB, or null when unset, which uses the default. */
    public Integer focus() { return focus; }
    /** The shadow colour as ARGB, or null when unset, which uses the default. */
    public Integer shadow() { return shadow; }
    /** The radius, or null when unset, which uses the default. */
    public Float radius() { return radius; }
    /** The border width, or null when unset, which uses the default. */
    public Float borderWidth() { return borderWidth; }
    /** The text size, or null when unset, which uses the default. */
    public Float textSize() { return textSize; }
    /** The shadow spread, or null when unset, which uses the default. */
    public Float shadowSpread() { return shadowSpread; }
    /** The shadow offset x, or null when unset, which uses the default. */
    public Float shadowOffsetX() { return shadowOffsetX; }
    /** The shadow offset y, or null when unset, which uses the default. */
    public Float shadowOffsetY() { return shadowOffsetY; }
    /** The font style, or null when unset, which uses the default. */
    public Integer fontStyle() { return fontStyle; }
    /** The font family, or null when unset, which uses the default. */
    public String fontFamily() { return fontFamily; }

    /** Collects values for one style; any left unset uses the default. */
    public static final class Builder {
        /** A builder with every value unset; {@link #build()} fills in the defaults. Usually reached through {@code builder()}. */
        public Builder() { }

        private Integer background, hoverBackground, pressedBackground, border, text, focus, shadow;
        private Float radius, borderWidth, textSize, shadowSpread, shadowOffsetX, shadowOffsetY;
        private Integer fontStyle;
        private String fontFamily;

        /** Sets the background colour, as ARGB. */
        public Builder background(int value) { background = value; return this; }
        /** Sets the hover background colour, as ARGB. */
        public Builder hoverBackground(int value) { hoverBackground = value; return this; }
        /** Sets the pressed background colour, as ARGB. */
        public Builder pressedBackground(int value) { pressedBackground = value; return this; }
        /** Sets the border colour, as ARGB. */
        public Builder border(int value) { border = value; return this; }
        /** Sets the text colour, as ARGB. */
        public Builder text(int value) { text = value; return this; }
        /** Sets the focus colour, as ARGB. */
        public Builder focus(int value) { focus = value; return this; }
        /** Sets the radius. */
        public Builder radius(float value) { radius = Math.max(0, value); return this; }
        /** Sets the border width. */
        public Builder borderWidth(float value) { borderWidth = Math.max(0, value); return this; }
        /** Sets the text size. */
        public Builder textSize(float value) { textSize = Math.max(1, value); return this; }
        /** Sets the font style. */
        public Builder fontStyle(int value) { fontStyle = value; return this; }
        /** Sets the font family. */
        public Builder fontFamily(String value) { fontFamily = value; return this; }

        /** Adds a retained raster shadow. A zero spread disables it. */
        public Builder shadow(int color, float spread, float offsetX, float offsetY) {
            shadow = color;
            shadowSpread = Math.max(0, spread);
            shadowOffsetX = offsetX;
            shadowOffsetY = offsetY;
            return this;
        }

        /** The finished, immutable style. */
        public UiButtonStyle build() { return new UiButtonStyle(this); }
    }
}
