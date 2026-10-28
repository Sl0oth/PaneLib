package dev.pane.ui;

/** A text input registered during a page paint, including exact painted caret positions. */
record UiInputRegion(String id, String key, UiRect bounds, int maxLength, boolean multiline,
                     float[] caretXs, float[] caretYs, java.util.List<UiTextLine> lines, boolean masked, boolean readOnly) {
    UiInputRegion(String id, String key, UiRect bounds, int maxLength, boolean multiline,
                  float[] caretXs, float[] caretYs, java.util.List<UiTextLine> lines, boolean masked) {
        this(id, key, bounds, maxLength, multiline, caretXs, caretYs, lines, masked, false);
    }
    UiInputRegion(String id, String key, UiRect bounds, int maxLength, boolean multiline,
                  float[] caretXs, float[] caretYs, java.util.List<UiTextLine> lines) {
        this(id, key, bounds, maxLength, multiline, caretXs, caretYs, lines, false);
    }
    UiInputRegion(String id, String key, UiRect bounds, int maxLength, boolean multiline,
                  float[] caretXs, float[] caretYs) {
        this(id, key, bounds, maxLength, multiline, caretXs, caretYs, java.util.List.of());
    }
    UiInputRegion {
        maxLength = Math.max(0, maxLength);
        lines = java.util.List.copyOf(lines);
        caretXs = caretXs == null ? new float[]{bounds.x()} : caretXs.clone();
        caretYs = caretYs == null || caretYs.length != caretXs.length
            ? new float[caretXs.length] : caretYs.clone();
    }

    @Override public float[] caretXs() { return caretXs.clone(); }
    @Override public float[] caretYs() { return caretYs.clone(); }

    record Caret(int index, boolean upstream) { }
    private int visualLine(int cursor, boolean upstream) {
        int result = 0;
        for (int i = 0; i < lines.size(); i++) {
            UiTextLine line = lines.get(i);
            if (cursor >= line.start() && cursor <= line.end()) {
                result = i;
                if (upstream || cursor < line.end()) break;
            }
        }
        return result;
    }
    float caretX(int cursor, boolean upstream) {
        return lines.isEmpty() ? caretX(cursor) : lines.get(visualLine(cursor, upstream)).x(cursor);
    }
    Caret lineEdge(int cursor, boolean upstream, boolean end) {
        if (lines.isEmpty()) return new Caret(lineEdge(cursor, end), false);
        UiTextLine line = lines.get(visualLine(cursor, upstream));
        return new Caret(end ? line.end() : line.start(), end);
    }
    Caret verticalCaret(int cursor, boolean upstream, int direction, float preferredX) {
        if (lines.isEmpty()) return new Caret(verticalCaret(cursor, direction, preferredX), false);
        int current = visualLine(cursor, upstream);
        int next = Math.clamp(current + direction, 0, lines.size() - 1);
        return next == current ? new Caret(cursor, upstream) : nearest(lines.get(next), preferredX);
    }
    Caret pageCaret(int cursor, boolean upstream, int direction, float preferredX, float viewportHeight) {
        if (lines.isEmpty()) return verticalCaret(cursor, upstream, direction, preferredX);
        int current = visualLine(cursor, upstream);
        float lineHeight = lines.size() > 1 ? Math.abs(lines.get(1).y() - lines.getFirst().y()) : viewportHeight;
        float distance = Math.max(lineHeight, viewportHeight - lineHeight);
        float targetY = lines.get(current).y() + direction * distance;
        int best = current;
        for (int i = 0; i < lines.size(); i++) {
            if (direction < 0 ? i >= current : i <= current) continue;
            if (best == current || Math.abs(lines.get(i).y() - targetY) < Math.abs(lines.get(best).y() - targetY)) best = i;
        }
        return best == current ? new Caret(cursor, upstream) : nearest(lines.get(best), preferredX);
    }
    private static Caret nearest(UiTextLine line, float x) {
        int best = line.start(); float distance = Float.MAX_VALUE;
        for (int i = line.start(); i <= line.end(); i++) {
            float next = Math.abs(line.x(i) - x);
            if (Float.isFinite(next) && next < distance) { best = i; distance = next; }
        }
        return new Caret(best, best == line.end());
    }
    Caret caretPositionAt(float x, float y) {
        if (lines.isEmpty()) return new Caret(caretAt(x, y), false);
        UiTextLine best = lines.getFirst();
        for (UiTextLine line : lines) if (Math.abs(line.y() - y) < Math.abs(best.y() - y)) best = line;
        return nearest(best, x);
    }

    float caretX(int cursor) { return caretXs[Math.clamp(cursor, 0, caretXs.length - 1)]; }

    int lineEdge(int cursor, boolean end) {
        cursor = Math.clamp(cursor, 0, caretYs.length - 1);
        int result = cursor;
        for (int i = 0; i < caretYs.length; i++) {
            if (Float.isFinite(caretXs[i]) && caretYs[i] == caretYs[cursor]
                    && (end ? i > result : i < result)) result = i;
        }
        return result;
    }

    int verticalCaret(int cursor, int direction, float preferredX) {
        cursor = Math.clamp(cursor, 0, caretYs.length - 1);
        float y = caretYs[cursor], nextY = direction < 0 ? -Float.MAX_VALUE : Float.MAX_VALUE;
        for (int i = 0; i < caretYs.length; i++) {
            if (!Float.isFinite(caretXs[i])) continue;
            float candidate = caretYs[i];
            if (direction < 0 ? candidate < y && candidate > nextY : candidate > y && candidate < nextY)
                nextY = candidate;
        }
        if (Math.abs(nextY) == Float.MAX_VALUE) return cursor;
        int nearest = cursor;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < caretYs.length; i++) {
            float dx = Math.abs(caretXs[i] - preferredX);
            if (caretYs[i] == nextY && Float.isFinite(dx) && dx < distance) { nearest = i; distance = dx; }
        }
        return nearest;
    }

    int caretAt(float x, float y) {
        if (caretXs.length <= 1) return 0;
        int nearest = 0;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < caretXs.length; i++) {
            float dx = x - caretXs[i];
            float dy = multiline ? (y - caretYs[i]) * 2F : 0;
            float next = dx * dx + dy * dy;
            if (next < distance) {
                nearest = i;
                distance = next;
            }
        }
        return nearest;
    }
}
