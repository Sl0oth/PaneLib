package dev.pane.ui;

/** ARGB colour helpers: hex text such as {@code #58A6FF}, and hue, saturation and brightness. */
public final class UiColor {
    private UiColor() { }

    /**
     * The colour written as {@code #RRGGBB}, {@code #AARRGGBB}, {@code #RGB} or the same without the
     * hash; six and three digit forms are opaque. Returns {@code fallback} for anything else.
     */
    public static int hex(String text, int fallback) {
        Integer parsed = parse(text);
        return parsed == null ? fallback : parsed;
    }

    /** {@code color} as {@code #AARRGGBB}, or {@code #RRGGBB} when it is fully opaque. */
    public static String hex(int color) {
        return (color >>> 24) == 0xFF ? String.format("#%06X", color & 0xFFFFFF) : String.format("#%08X", color);
    }

    /** A colour from hue in degrees, saturation, brightness and opacity, the last three from 0 to 1. */
    public static int hsv(float hue, float saturation, float brightness, float opacity) {
        float h = ((hue % 360) + 360) % 360 / 60F, s = clamp(saturation), v = clamp(brightness);
        int sector = (int) Math.floor(h) % 6;
        float fraction = h - (float) Math.floor(h);
        float p = v * (1 - s), q = v * (1 - s * fraction), t = v * (1 - s * (1 - fraction));
        float r, g, b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return Math.round(clamp(opacity) * 255) << 24 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    /** Hue in degrees from 0 up to 360, then saturation and brightness from 0 to 1, of {@code color}. */
    public static float[] toHsv(int color) {
        float r = (color >> 16 & 0xFF) / 255F, g = (color >> 8 & 0xFF) / 255F, b = (color & 0xFF) / 255F;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        float hue = 0;
        if (delta > 0) {
            if (max == r) hue = 60 * (((g - b) / delta) % 6);
            else if (max == g) hue = 60 * ((b - r) / delta + 2);
            else hue = 60 * ((r - g) / delta + 4);
            if (hue < 0) hue += 360;
        }
        return new float[] {hue, max == 0 ? 0 : delta / max, max};
    }

    static Integer parse(String text) {
        if (text == null) return null;
        String digits = text.trim();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (digits.length() != 3 && digits.length() != 6 && digits.length() != 8) return null;
        for (int i = 0; i < digits.length(); i++) if (Character.digit(digits.charAt(i), 16) < 0) return null;
        if (digits.length() == 3) {
            var wide = new StringBuilder();
            for (int i = 0; i < 3; i++) wide.append(digits.charAt(i)).append(digits.charAt(i));
            digits = wide.toString();
        }
        long value = Long.parseLong(digits, 16);
        return digits.length() == 8 ? (int) value : (int) (0xFF000000L | value);
    }

    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
}
