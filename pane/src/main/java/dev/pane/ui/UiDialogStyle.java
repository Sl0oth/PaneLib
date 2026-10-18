package dev.pane.ui;

/** Optional visual overrides for one native confirmation dialog. Unset values inherit the document theme. */
public final class UiDialogStyle {
    private final Integer backdrop, shadow, surface, border, titleColor, messageColor;
    private final Integer cancelBackground, cancelBorder, cancelText, confirmBackground, confirmText;
    private final Float radius;

    private UiDialogStyle(Builder builder) {
        backdrop = builder.backdrop; shadow = builder.shadow; surface = builder.surface; border = builder.border;
        titleColor = builder.titleColor; messageColor = builder.messageColor;
        cancelBackground = builder.cancelBackground; cancelBorder = builder.cancelBorder; cancelText = builder.cancelText;
        confirmBackground = builder.confirmBackground; confirmText = builder.confirmText;
        radius = builder.radius == null ? null : Math.max(0, Math.min(40, builder.radius));
    }

    public static Builder builder() { return new Builder(); }
    public Integer backdrop() { return backdrop; }
    public Integer shadow() { return shadow; }
    public Integer surface() { return surface; }
    public Integer border() { return border; }
    public Integer titleColor() { return titleColor; }
    public Integer messageColor() { return messageColor; }
    public Integer cancelBackground() { return cancelBackground; }
    public Integer cancelBorder() { return cancelBorder; }
    public Integer cancelText() { return cancelText; }
    public Integer confirmBackground() { return confirmBackground; }
    public Integer confirmText() { return confirmText; }
    public Float radius() { return radius; }

    public static final class Builder {
        private Integer backdrop, shadow, surface, border, titleColor, messageColor;
        private Integer cancelBackground, cancelBorder, cancelText, confirmBackground, confirmText;
        private Float radius;
        public Builder backdrop(int color) { backdrop = color; return this; }
        public Builder shadow(int color) { shadow = color; return this; }
        public Builder surface(int color) { surface = color; return this; }
        public Builder border(int color) { border = color; return this; }
        public Builder titleColor(int color) { titleColor = color; return this; }
        public Builder messageColor(int color) { messageColor = color; return this; }
        public Builder cancelBackground(int color) { cancelBackground = color; return this; }
        public Builder cancelBorder(int color) { cancelBorder = color; return this; }
        public Builder cancelText(int color) { cancelText = color; return this; }
        public Builder confirmBackground(int color) { confirmBackground = color; return this; }
        public Builder confirmText(int color) { confirmText = color; return this; }
        public Builder radius(float value) { radius = value; return this; }
        public UiDialogStyle build() { return new UiDialogStyle(this); }
    }
}
