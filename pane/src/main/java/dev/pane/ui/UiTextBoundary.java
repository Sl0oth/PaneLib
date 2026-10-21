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
    /** Word segments use the JDK's locale-neutral Unicode boundaries, skipping whitespace. */
    static int previousWord(String text, int index) {
        int end = floor(text, index);
        if (end == 0) return 0;
        var words = java.text.BreakIterator.getWordInstance(java.util.Locale.ROOT);
        words.setText(text);
        int start = words.preceding(end);
        while (start != java.text.BreakIterator.DONE) {
            if (!blank(text, start, end)) return floor(text, start);
            end = start;
            start = words.previous();
        }
        return 0;
    }
    static int nextWord(String text, int index) {
        int start = floor(text, index);
        if (start == text.length()) return start;
        var words = java.text.BreakIterator.getWordInstance(java.util.Locale.ROOT);
        words.setText(text);
        int end = words.following(start);
        while (end != java.text.BreakIterator.DONE) {
            if (!blank(text, start, end)) return floor(text, end);
            start = end;
            end = words.next();
        }
        return text.length();
    }
    record Selection(int start, int end) { }
    static Selection wordAt(String text, int index) {
        if (text.isEmpty()) return new Selection(0,0);
        int at = floor(text,index);
        if (at == text.length()) at = previous(text,at);
        var words = java.text.BreakIterator.getWordInstance(java.util.Locale.ROOT);
        words.setText(text);
        int end = words.following(at);
        int start = words.previous();
        return new Selection(floor(text,start), floor(text,end));
    }
    private static boolean blank(String text, int start, int end) {
        for (int i = start; i < end;) {
            int cp = text.codePointAt(i);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) return false;
            i += Character.charCount(cp);
        }
        return true;
    }
    static String normalizeLines(String text) {
        return text.indexOf('\r') < 0 ? text : text.replace("\r\n", "\n").replace('\r', '\n');
    }
    static String truncate(String text, int maximum) { return text.substring(0, floor(text, maximum)); }
}
