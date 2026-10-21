package dev.pane.ui;

/** One visual line; a soft-wrap boundary has a position on both adjacent lines. */
record UiTextLine(int start, int end, float[] xs, float y) {
    UiTextLine { xs = xs.clone(); }
    @Override public float[] xs() { return xs.clone(); }
    float x(int index) { return xs[Math.clamp(index - start, 0, xs.length - 1)]; }
    UiTextLine translate(float dx, float dy) {
        float[] moved = xs.clone();
        for (int i = 0; i < moved.length; i++) moved[i] += dx;
        return new UiTextLine(start, end, moved, y + dy);
    }
}
