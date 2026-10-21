package dev.pane.ui;

import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/** Per-paint view over a screen's retained animation states. */
final class UiAnimationClock {
    private final Map<String, State> states;
    private final long now;
    private final float motionScale;
    private boolean active;
    private final Set<String> activeIds = new LinkedHashSet<>();
    private final Set<String> usedIds = new LinkedHashSet<>();

    UiAnimationClock(Map<String, State> states, long now, float motionScale) {
        this.states = states;
        this.now = now;
        this.motionScale = Math.max(0, motionScale);
    }

    float value(String id, float target, int durationMillis, UiEasing easing) {
        return value(id, target, target, durationMillis, 0, easing);
    }

    float value(String id, float initial, float target, int durationMillis, int delayMillis, UiEasing easing) {
        if (id == null || id.isBlank()) return target;
        usedIds.add(id);
        UiEasing curve = easing == null ? UiEasing.EASE_OUT : easing;
        long duration = Math.max(0L, Math.round(Math.max(0, durationMillis) * motionScale * 1_000_000D));
        State state = states.get(id);
        float current = state == null ? initial : state.value(now);
        if (state == null) {
            long delay = (long) (Math.max(0, delayMillis) * motionScale * 1_000_000D);
            state = duration == 0 || Float.compare(initial, target) == 0 ? new State(target, target, now, now, curve)
                : new State(initial, target, now + delay, now + delay + duration, curve);
            states.put(id, state);
        }
        if (duration == 0 || Float.compare(state.target, target) != 0) {
            state = duration == 0
                ? new State(target, target, now, now, curve)
                : new State(current, target, now, now + duration, curve);
            states.put(id, state);
        }
        current = state.value(now);
        if (now < state.end) {
            active = true;
            activeIds.add(id);
        }
        return current;
    }

    float angle(String id, float targetDegrees, int durationMillis, UiEasing easing) {
        if (!Float.isFinite(targetDegrees)) throw new IllegalArgumentException("Angle must be finite");
        float target = ((targetDegrees % 360) + 360) % 360;
        State previous = states.get(id);
        if (previous != null) {
            // Keep bounded coordinates and the same current trajectory across repeated revolutions.
            float shift = (float) Math.floor(previous.target / 360) * 360;
            if (shift != 0) {
                previous = new State(previous.from - shift, previous.target - shift,
                    previous.start, previous.end, previous.easing);
                states.put(id, previous);
            }
            float delta = ((target - previous.target + 540) % 360) - 180;
            target = previous.target + delta;
        }
        float result = value(id, target, durationMillis, easing);
        return ((result % 360) + 360) % 360;
    }

    float sequence(String id, long revision, UiTimeline timeline) {
        if(id==null||id.isBlank())return timeline.endValue();
        usedIds.add(id);
        State state = states.get(id);
        if (state == null || state.timeline == null || state.revision != revision) {
            float from = state == null ? timeline.startValue() : state.value(now);
            long duration = (long) (timeline.durationMillis() * motionScale * 1_000_000D);
            state = new State(from, timeline.endValue(), now, now + duration, UiEasing.LINEAR,
                timeline.from(from), revision);
            states.put(id, state);
        } else if (motionScale == 0) {
            state = new State(state.target, state.target, now, now, UiEasing.LINEAR, state.timeline, revision);
            states.put(id, state);
        }
        if (now < state.end) { active = true; activeIds.add(id); }
        return state.value(now);
    }

    boolean active() { return active; }
    Set<String> activeIds() { return Set.copyOf(activeIds); }
    Set<String> usedIds() { return Set.copyOf(usedIds); }

    record State(float from, float target, long start, long end, UiEasing easing, UiTimeline timeline, long revision) {
        State(float from, float target, long start, long end, UiEasing easing) {
            this(from, target, start, end, easing, null, 0);
        }
        float value(long now) {
            if (end <= start || now >= end) return target;
            if (now <= start) return from;
            float linear = (float) (now - start) / (end - start);
            if (timeline != null) return timeline.valueAt(linear * timeline.durationMillis());
            return from + (target - from) * easing.apply(linear);
        }
    }
}
