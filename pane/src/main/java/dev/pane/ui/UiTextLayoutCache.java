package dev.pane.ui;

/**
 * Text layouts kept between paints, bounded by entry count and by text length. The focused editor
 * keeps one entry, so no old edit versions pile up while typing; inputs at rest keep a few, so
 * unfocused fields and text areas are not shaped again on every paint.
 */
final class UiTextLayoutCache {
    private static final int MAX_CHARACTERS = 65_536;
    private final int entries;
    private final java.util.LinkedHashMap<Object, Object> values = new java.util.LinkedHashMap<>(16, .75F, true);
    private final java.util.Map<Object, Integer> lengths = new java.util.HashMap<>();
    private int characters;

    UiTextLayoutCache() { this(1); }

    UiTextLayoutCache(int entries) { this.entries = Math.max(1, entries); }

    @SuppressWarnings("unchecked")
    <T> T get(Object nextKey,int length,java.util.function.Supplier<T> factory) {
        Object cached = values.get(nextKey);
        if (cached != null) return (T) cached;
        T result=factory.get();
        if (length > MAX_CHARACTERS) { clear(); return result; }
        values.put(nextKey, result); lengths.put(nextKey, length); characters += length;
        // Least recently used layouts go first.
        var oldest = values.keySet().iterator();
        while ((values.size() > entries || characters > MAX_CHARACTERS) && oldest.hasNext()) {
            Object key = oldest.next();
            if (key.equals(nextKey)) continue;
            characters -= lengths.remove(key);
            oldest.remove();
        }
        return result;
    }
    void clear(){values.clear();lengths.clear();characters=0;}
    int retainedCharacters(){return characters;}
}
