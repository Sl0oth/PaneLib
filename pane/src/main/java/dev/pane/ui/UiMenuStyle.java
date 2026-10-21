package dev.pane.ui;

/** Immutable popup styling. Metrics use layout units; null colors/font inherit the theme. */
public record UiMenuStyle(float textSize, int fontStyle, String fontFamily, float rowHeight, float paddingX, float paddingY, float radius, float rowRadius, float rowInset, float borderWidth, Integer background, Integer text, Integer border, Integer highlight) {
    public UiMenuStyle {
        if (!Float.isFinite(textSize) || textSize <= 0) throw new IllegalArgumentException("Invalid textSize");
        if (!Float.isFinite(rowHeight) || rowHeight <= 0) throw new IllegalArgumentException("Invalid rowHeight");
        if (!Float.isFinite(paddingX) || paddingX < 0) throw new IllegalArgumentException("Invalid paddingX");
        if (!Float.isFinite(paddingY) || paddingY < 0) throw new IllegalArgumentException("Invalid paddingY");
        if (!Float.isFinite(radius) || radius < 0) throw new IllegalArgumentException("Invalid radius");
        if (!Float.isFinite(rowRadius) || rowRadius < 0) throw new IllegalArgumentException("Invalid rowRadius");
        if (!Float.isFinite(rowInset) || rowInset < 0) throw new IllegalArgumentException("Invalid rowInset");
        if (!Float.isFinite(borderWidth) || borderWidth < 0) throw new IllegalArgumentException("Invalid borderWidth");
        if ((fontStyle & ~(java.awt.Font.BOLD | java.awt.Font.ITALIC)) != 0) throw new IllegalArgumentException("Invalid font style");
    }
    private static final UiMenuStyle DEFAULT = builder().build();
    public static UiMenuStyle defaults() { return DEFAULT; }
    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private float textSize = 8;
        private int fontStyle = java.awt.Font.PLAIN;
        private String fontFamily = null;
        private float rowHeight = 27;
        private float paddingX = 12;
        private float paddingY = 5;
        private float radius = 8;
        private float rowRadius = 5;
        private float rowInset = 3;
        private float borderWidth = .8F;
        private Integer background = null;
        private Integer text = null;
        private Integer border = null;
        private Integer highlight = null;
        public Builder textSize(float value) { textSize = value; return this; }
        public Builder fontStyle(int value) { fontStyle = value; return this; }
        public Builder fontFamily(String value) { fontFamily = value; return this; }
        public Builder rowHeight(float value) { rowHeight = value; return this; }
        public Builder paddingX(float value) { paddingX = value; return this; }
        public Builder paddingY(float value) { paddingY = value; return this; }
        public Builder radius(float value) { radius = value; return this; }
        public Builder rowRadius(float value) { rowRadius = value; return this; }
        public Builder rowInset(float value) { rowInset = value; return this; }
        public Builder borderWidth(float value) { borderWidth = value; return this; }
        public Builder background(Integer value) { background = value; return this; }
        public Builder text(Integer value) { text = value; return this; }
        public Builder border(Integer value) { border = value; return this; }
        public Builder highlight(Integer value) { highlight = value; return this; }
        public Builder padding(float horizontal, float vertical) { paddingX = horizontal; paddingY = vertical; return this; }
        public UiMenuStyle build() { return new UiMenuStyle(textSize, fontStyle, fontFamily, rowHeight, paddingX, paddingY, radius, rowRadius, rowInset, borderWidth, background, text, border, highlight); }
    }
}
