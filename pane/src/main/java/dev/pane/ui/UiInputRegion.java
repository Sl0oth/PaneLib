package dev.pane.ui;

/** A text input registered during a page paint, including exact painted caret positions. */
record UiInputRegion(String id, String key, UiRect bounds, int maxLength, boolean multiline,
                     float[] caretXs, float[] caretYs) {
    UiInputRegion {
        maxLength = Math.max(0, maxLength);
        caretXs = caretXs == null ? new float[]{bounds.x()} : caretXs.clone();
        caretYs = caretYs == null || caretYs.length != caretXs.length
            ? new float[caretXs.length] : caretYs.clone();
    }

    @Override public float[] caretXs() { return caretXs.clone(); }
    @Override public float[] caretYs() { return caretYs.clone(); }

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
