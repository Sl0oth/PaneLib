package dev.pane.ui;

/** Shared opacity calculation for painting and deciding whether a retained toast has changed. */
final class UiToastTiming {
    private UiToastTiming() { }

    static float opacity(long now, long shownAt, long expiresAt, long fadeNanos) {
        if (fadeNanos <= 0) return 1;
        long edgeDistance = Math.max(0, Math.min(now - shownAt, expiresAt - now));
        return Math.min(1, (float) edgeDistance / fadeNanos);
    }
}
