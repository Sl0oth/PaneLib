package dev.pane.ui;

/** UTF-16 offsets at Unicode extended grapheme boundaries (Java 25 / Unicode 16). */
final class UiTextBoundary {
    private UiTextBoundary() { }

    // Keep only bounded position data, never a strong reference to editor/password contents.
    private static final int CACHE_CHARACTERS = 65_536;
    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);
    private record Entry(java.lang.ref.WeakReference<String> source, Index index) { }
    private static final class Cache {
        final java.util.ArrayDeque<Entry> entries = new java.util.ArrayDeque<>();
        int characters;
        Index get(String text) {
            for (var entry : entries) if (entry.source().get() == text) return entry.index();
            var result = new Index(text);
            if (text.length() <= CACHE_CHARACTERS) {
                while (!entries.isEmpty() && (entries.size() >= 4 || characters + text.length() > CACHE_CHARACTERS))
                    characters -= entries.removeFirst().index().length;
                entries.addLast(new Entry(new java.lang.ref.WeakReference<>(text), result));
                characters += text.length();
            }
            return result;
        }
    }

    /** Immutable offset map; callers walking many characters should acquire it once. */
    static final class Index {
        final int length;
        private final java.util.BitSet boundaries;
        private Index(String text) {
            length = text.length();
            boundaries = new java.util.BitSet();
            var iterator = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT);
            iterator.setText(text);
            for (int at = iterator.first(); at != java.text.BreakIterator.DONE; at = iterator.next())
                boundaries.set(at);
        }
        int floor(int index) { return boundaries.previousSetBit(Math.clamp(index, 0, length)); }
        int ceil(int index) { return boundaries.nextSetBit(Math.clamp(index, 0, length)); }
        int previous(int index) { int at = floor(index); return at == 0 ? 0 : boundaries.previousSetBit(at - 1); }
        int next(int index) { int at = floor(index); return at == length ? length : boundaries.nextSetBit(at + 1); }
        boolean contains(int index) { return index >= 0 && index <= length && boundaries.get(index); }
    }
    static Index index(String text) { return CACHE.get().get(text); }
    static int floor(String text, int index) { return index(text).floor(index); }
    static int ceil(String text, int index) { return index(text).ceil(index); }
    static int previous(String text, int index) { return index(text).previous(index); }
    static int next(String text, int index) { return index(text).next(index); }
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
            if (!blank(text, start, end)) return ceil(text, end);
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
        return new Selection(floor(text,start), ceil(text,end));
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
