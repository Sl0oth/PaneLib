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

    public static Builder builder() { return new Builder(); }
    public Integer background() { return background; }
    public Integer border() { return border; }
    public Integer titleColor() { return titleColor; }
    public Integer messageColor() { return messageColor; }
    public Integer iconBackground() { return iconBackground; }
    public Integer iconColor() { return iconColor; }
    public Integer closeBackground() { return closeBackground; }
    public Integer closeColor() { return closeColor; }
    public Float radius() { return radius; }

    public static final class Builder {
        private Integer background, border, titleColor, messageColor;
        private Integer iconBackground, iconColor, closeBackground, closeColor;
        private Float radius;

        public Builder background(int color) { background = color; return this; }
        public Builder border(int color) { border = color; return this; }
        public Builder titleColor(int color) { titleColor = color; return this; }
        public Builder messageColor(int color) { messageColor = color; return this; }
        public Builder iconBackground(int color) { iconBackground = color; return this; }
        public Builder iconColor(int color) { iconColor = color; return this; }
        public Builder closeBackground(int color) { closeBackground = color; return this; }
        public Builder closeColor(int color) { closeColor = color; return this; }
        public Builder radius(float value) { radius = value; return this; }
        public UiToastStyle build() { return new UiToastStyle(this); }
    }
}
