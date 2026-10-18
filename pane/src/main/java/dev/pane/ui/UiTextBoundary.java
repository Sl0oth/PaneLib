package dev.pane.ui;

/** UTF-16 indices at Unicode codepoint boundaries. Does not claim grapheme-cluster editing. */
final class UiTextBoundary {
    private UiTextBoundary() { }
    static int floor(String text, int index) {
        int i = Math.max(0, Math.min(text.length(), index));
        return i > 0 && i < text.length() && Character.isHighSurrogate(text.charAt(i - 1))
            && Character.isLowSurrogate(text.charAt(i)) ? i - 1 : i;
    }
    static int previous(String text, int index) {
        int i = floor(text, index); return i == 0 ? 0 : text.offsetByCodePoints(i, -1);
    }
    static int next(String text, int index) {
        int i = floor(text, index); return i == text.length() ? i : text.offsetByCodePoints(i, 1);
    }
    static String truncate(String text, int maximum) { return text.substring(0, floor(text, maximum)); }
}
