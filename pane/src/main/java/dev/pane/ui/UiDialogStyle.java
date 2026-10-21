package dev.pane.ui;

/** Optional visual overrides for one native confirmation dialog. Unset values inherit the document theme. */
public final class UiDialogStyle {
    private final Integer backdrop, shadow, surface, border, titleColor, messageColor;
    private final Integer cancelBackground, cancelBorder, cancelText, confirmBackground, confirmText;
    private final Float radius;
    private final String fontFamily, titleFontFamily, messageFontFamily, buttonFontFamily;
    private final float titleSize, messageSize, buttonSize, titleLineSpacing, messageLineSpacing;
    private final int titleFontStyle, messageFontStyle, buttonFontStyle;
    private final float maxWidth, padding, maxHeightFraction;

    private UiDialogStyle(Builder builder) {
        backdrop = builder.backdrop; shadow = builder.shadow; surface = builder.surface; border = builder.border;
        titleColor = builder.titleColor; messageColor = builder.messageColor;
        cancelBackground = builder.cancelBackground; cancelBorder = builder.cancelBorder; cancelText = builder.cancelText;
        confirmBackground = builder.confirmBackground; confirmText = builder.confirmText;
        fontFamily=family(builder.fontFamily); titleFontFamily=family(builder.titleFontFamily);
        messageFontFamily=family(builder.messageFontFamily); buttonFontFamily=family(builder.buttonFontFamily);
        titleSize=finite(builder.titleSize,1,128,"titleSize");
        messageSize=finite(builder.messageSize,1,128,"messageSize");
        buttonSize=finite(builder.buttonSize,1,128,"buttonSize");
        titleLineSpacing=finite(builder.titleLineSpacing,0,128,"titleLineSpacing");
        messageLineSpacing=finite(builder.messageLineSpacing,0,128,"messageLineSpacing");
        titleFontStyle=fontStyle(builder.titleFontStyle); messageFontStyle=fontStyle(builder.messageFontStyle);
        buttonFontStyle=fontStyle(builder.buttonFontStyle);
        maxWidth=finite(builder.maxWidth,80,2000,"maxWidth");
        padding=finite(builder.padding,0,64,"padding");
        maxHeightFraction=finite(builder.maxHeightFraction,.2F,1,"maxHeightFraction");
        if(builder.radius!=null&&Float.isNaN(builder.radius))throw new IllegalArgumentException("radius must not be NaN");
        radius = builder.radius == null ? null : Math.max(0, Math.min(40, builder.radius));
    }

    private static String family(String value) {
        if(value!=null&&value.isBlank())throw new IllegalArgumentException("Font family must be nonblank or null to inherit");
        return value;
    }
    private static int fontStyle(int value) {
        if(value<0||value>3)throw new IllegalArgumentException("Font style must be PLAIN, BOLD, ITALIC or BOLD | ITALIC");
        return value;
    }
    /** Shared font alias/family, falling back to the document theme when unset. */
    public String fontFamily(){return fontFamily;}
    public String titleFontFamily(){return titleFontFamily;}
    public String messageFontFamily(){return messageFontFamily;}
    public String buttonFontFamily(){return buttonFontFamily;}
    public float titleSize(){return titleSize;}
    public float messageSize(){return messageSize;}
    public float buttonSize(){return buttonSize;}
    public int titleFontStyle(){return titleFontStyle;}
    public int messageFontStyle(){return messageFontStyle;}
    public int buttonFontStyle(){return buttonFontStyle;}
    public float titleLineSpacing(){return titleLineSpacing;}
    public float messageLineSpacing(){return messageLineSpacing;}

    private static float finite(float value,float min,float max,String name) {
        if(!Float.isFinite(value)||value<min||value>max)throw new IllegalArgumentException(name+" must be finite and between "+min+" and "+max);
        return value == 0 ? 0 : value;
    }
    public float maxWidth(){return maxWidth;}
    public float padding(){return padding;}
    public float maxHeightFraction(){return maxHeightFraction;}

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

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UiDialogStyle style)) return false;
        return maxWidth==style.maxWidth && padding==style.padding && maxHeightFraction==style.maxHeightFraction
            && titleSize==style.titleSize && messageSize==style.messageSize && buttonSize==style.buttonSize
            && titleFontStyle==style.titleFontStyle && messageFontStyle==style.messageFontStyle && buttonFontStyle==style.buttonFontStyle
            && titleLineSpacing==style.titleLineSpacing && messageLineSpacing==style.messageLineSpacing
            && java.util.Objects.equals(fontFamily,style.fontFamily)
            && java.util.Objects.equals(titleFontFamily,style.titleFontFamily)
            && java.util.Objects.equals(messageFontFamily,style.messageFontFamily)
            && java.util.Objects.equals(buttonFontFamily,style.buttonFontFamily)
            && java.util.Objects.equals(backdrop, style.backdrop)
            && java.util.Objects.equals(shadow, style.shadow)
            && java.util.Objects.equals(surface, style.surface)
            && java.util.Objects.equals(border, style.border)
            && java.util.Objects.equals(titleColor, style.titleColor)
            && java.util.Objects.equals(messageColor, style.messageColor)
            && java.util.Objects.equals(cancelBackground, style.cancelBackground)
            && java.util.Objects.equals(cancelBorder, style.cancelBorder)
            && java.util.Objects.equals(cancelText, style.cancelText)
            && java.util.Objects.equals(confirmBackground, style.confirmBackground)
            && java.util.Objects.equals(confirmText, style.confirmText)
            && java.util.Objects.equals(radius, style.radius);
    }
    @Override public int hashCode() { return java.util.Objects.hash(backdrop, shadow, surface, border, titleColor, messageColor, cancelBackground, cancelBorder, cancelText, confirmBackground, confirmText, radius, maxWidth, padding, maxHeightFraction, fontFamily, titleFontFamily, messageFontFamily, buttonFontFamily, titleSize, messageSize, buttonSize, titleFontStyle, messageFontStyle, buttonFontStyle, titleLineSpacing, messageLineSpacing); }

    public static final class Builder {
        private Integer backdrop, shadow, surface, border, titleColor, messageColor;
        private Integer cancelBackground, cancelBorder, cancelText, confirmBackground, confirmText;
        private Float radius;
        private String fontFamily, titleFontFamily, messageFontFamily, buttonFontFamily;
        private float titleSize=12, messageSize=8, buttonSize=8, titleLineSpacing=2, messageLineSpacing=2;
        private int titleFontStyle=java.awt.Font.BOLD, messageFontStyle=java.awt.Font.PLAIN, buttonFontStyle=java.awt.Font.BOLD;
        /** Font family or UiCanvas.registerFont alias for all dialog text; null inherits the theme. */
        public Builder fontFamily(String value){fontFamily=value;return this;}
        /** Title-only override; null inherits the shared dialog family, then the theme. */
        public Builder titleFontFamily(String value){titleFontFamily=value;return this;}
        /** Message-only override; null inherits the shared dialog family, then the theme. */
        public Builder messageFontFamily(String value){messageFontFamily=value;return this;}
        /** Font for both action buttons; null inherits the shared dialog family, then the theme. */
        public Builder buttonFontFamily(String value){buttonFontFamily=value;return this;}
        /** Title size in layout units, 1..128. Default 12. */
        public Builder titleSize(float value){titleSize=value;return this;}
        /** Message size in layout units, 1..128. Default 8. */
        public Builder messageSize(float value){messageSize=value;return this;}
        /** Action-label size in layout units, 1..128. Default 8; buttons grow to fit when space allows. */
        public Builder buttonSize(float value){buttonSize=value;return this;}
        public Builder titleFontStyle(int value){titleFontStyle=value;return this;}
        public Builder messageFontStyle(int value){messageFontStyle=value;return this;}
        public Builder buttonFontStyle(int value){buttonFontStyle=value;return this;}
        /** Additional space after each title line in layout units, 0..128. Default 2. */
        public Builder titleLineSpacing(float value){titleLineSpacing=value;return this;}
        /** Additional space after each message line in layout units, 0..128. Default 2. */
        public Builder messageLineSpacing(float value){messageLineSpacing=value;return this;}
        private float maxWidth=330, padding=16, maxHeightFraction=.9F;
        /** Preferred maximum layout width, 80..2000. Always clamped to the viewport. */
        public Builder maxWidth(float value){maxWidth=value;return this;}
        /** Interior spacing in layout units, 0..64; compressed in very small viewports. */
        public Builder padding(float value){padding=value;return this;}
        /** Maximum fraction of viewport height, .2..1. Overflowing copy scrolls. */
        public Builder maxHeightFraction(float value){maxHeightFraction=value;return this;}
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
