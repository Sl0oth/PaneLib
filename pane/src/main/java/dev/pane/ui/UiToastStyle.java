package dev.pane.ui;

/** Optional visual overrides for a toast. Unset values inherit the document theme. */
public final class UiToastStyle {
    private final Integer background, border, titleColor, messageColor;
    private final Integer iconBackground, iconColor, closeBackground, closeColor;
    private final Float radius;

    private UiToastStyle(Builder builder) {
        background = builder.background;
        border = builder.border;
        titleColor = builder.titleColor;
        messageColor = builder.messageColor;
        iconBackground = builder.iconBackground;
        iconColor = builder.iconColor;
        closeBackground = builder.closeBackground;
        closeColor = builder.closeColor;
        radius = builder.radius == null ? null : Math.max(0, Math.min(40, builder.radius));
    }

    /** Starts a style with every value unset, so each uses the default. */
    public static Builder builder() { return new Builder(); }
    /** The background colour as ARGB, or null when unset, which uses the default. */
    public Integer background() { return background; }
    /** The border colour as ARGB, or null when unset, which uses the default. */
    public Integer border() { return border; }
    /** The title color colour as ARGB, or null when unset, which uses the default. */
    public Integer titleColor() { return titleColor; }
    /** The message color colour as ARGB, or null when unset, which uses the default. */
    public Integer messageColor() { return messageColor; }
    /** The icon background colour as ARGB, or null when unset, which uses the default. */
    public Integer iconBackground() { return iconBackground; }
    /** The icon color colour as ARGB, or null when unset, which uses the default. */
    public Integer iconColor() { return iconColor; }
    /** The close background colour as ARGB, or null when unset, which uses the default. */
    public Integer closeBackground() { return closeBackground; }
    /** The close color colour as ARGB, or null when unset, which uses the default. */
    public Integer closeColor() { return closeColor; }
    /** The radius, or null when unset, which uses the default. */
    public Float radius() { return radius; }

    /** Collects values for one style; any left unset uses the default. */
    public static final class Builder {
        /** A builder with every value unset; {@link #build()} fills in the defaults. Usually reached through {@code builder()}. */
        public Builder() { }

        private Integer background, border, titleColor, messageColor;
        private Integer iconBackground, iconColor, closeBackground, closeColor;
        private Float radius;

        /** Sets the background. */
        public Builder background(int color) { background = color; return this; }
        /** Sets the border. */
        public Builder border(int color) { border = color; return this; }
        /** Sets the title color. */
        public Builder titleColor(int color) { titleColor = color; return this; }
        /** Sets the message color. */
        public Builder messageColor(int color) { messageColor = color; return this; }
        /** Sets the icon background. */
        public Builder iconBackground(int color) { iconBackground = color; return this; }
        /** Sets the icon color. */
        public Builder iconColor(int color) { iconColor = color; return this; }
        /** Sets the close background. */
        public Builder closeBackground(int color) { closeBackground = color; return this; }
        /** Sets the close color. */
        public Builder closeColor(int color) { closeColor = color; return this; }
        /** Sets the radius. */
        public Builder radius(float value) { radius = value; return this; }
        /** The finished, immutable style. */
        public UiToastStyle build() { return new UiToastStyle(this); }
    }
}
