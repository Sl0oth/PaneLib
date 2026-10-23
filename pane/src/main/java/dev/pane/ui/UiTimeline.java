package dev.pane.ui;

import java.util.ArrayList;
import java.util.List;

/** Immutable finite scalar choreography. Combine named tracks for position, scale, rotation, or color. */
public final class UiTimeline {
    private record Step(float target, int millis, UiEasing easing) { }
    private final float from;
    private final List<Step> steps;
    private final int duration;

    private UiTimeline(float from, List<Step> steps) {
        if (!Float.isFinite(from)) throw new IllegalArgumentException("Timeline values must be finite");
        this.from = from; this.steps = List.copyOf(steps);
        duration = steps.stream().mapToInt(Step::millis).sum();
        if (duration > 60_000 || steps.size() > 64) throw new IllegalArgumentException("Timeline exceeds finite motion limits");
    }

    /** A timeline that starts at {@code value}. */
    public static UiTimeline starting(float value) { return new UiTimeline(value, List.of()); }
    /** A copy that then moves to {@code target} over {@code millis} with {@code easing}. */
    public UiTimeline to(float target, int millis, UiEasing easing) {
        if (!Float.isFinite(target)) throw new IllegalArgumentException("Timeline values must be finite");
        var next = new ArrayList<>(steps);
        next.add(new Step(target, Math.max(0, Math.min(60_000, millis)), easing == null ? UiEasing.EASE_OUT : easing));
        return new UiTimeline(from, next);
    }
    /** A copy that then holds its last value for {@code millis}. */
    public UiTimeline hold(int millis) { return to(endValue(), millis, UiEasing.LINEAR); }
    /** Total length in milliseconds. */
    public int durationMillis() { return duration; }
    /** The value at the end. */
    public float endValue() { return steps.isEmpty() ? from : steps.getLast().target; }
    float startValue() { return from; }
    UiTimeline from(float value) { return new UiTimeline(value, steps); }

    /** Deterministic sampling, useful for authoring/previewing without a client clock. */
    public float valueAt(double elapsedMillis) {
        if (elapsedMillis < 0) return from;
        float previous = from;
        double elapsed = elapsedMillis;
        for (Step step : steps) {
            if (step.millis > 0 && elapsed < step.millis)
                return previous + (step.target - previous) * step.easing.apply((float) (elapsed / step.millis));
            elapsed -= step.millis;
            previous = step.target;
        }
        return previous;
    }
}
