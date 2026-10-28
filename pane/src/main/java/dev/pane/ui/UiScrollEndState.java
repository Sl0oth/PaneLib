package dev.pane.ui;

import java.util.*;

/** Layout-time scroll intents, independent of consumer values and paint scheduling. */
final class UiScrollEndState {
    private static final double END_TOLERANCE = .5;
    private static final class Entry {
        double maximum = Double.NaN;
        Double navigationTarget;
        boolean pending;
    }
    private final Map<String, Entry> entries = new HashMap<>();
    private final Set<String> resolved = new HashSet<>();

    void request(String id) { entries.computeIfAbsent(id, key -> new Entry()).pending = true; }
    void navigate(String id, double target) {
        Entry entry = entries.computeIfAbsent(id, key -> new Entry());
        entry.pending = false;
        entry.navigationTarget = target;
    }
    /** True when {@code offset} is at the last resolved maximum, or nothing has been resolved yet. */
    boolean atEnd(String id, double offset) {
        Entry entry = entries.get(id);
        return entry == null || !Double.isFinite(entry.maximum) || offset >= entry.maximum - END_TOLERANCE;
    }

    double resolve(String id, double offset, double maximum, UiScrollFollow follow) {
        Entry entry = entries.computeIfAbsent(id, key -> new Entry());
        double priorPosition = entry.navigationTarget == null ? offset : entry.navigationTarget;
        boolean wasAtEnd = Double.isFinite(entry.maximum) && priorPosition >= entry.maximum - END_TOLERANCE;
        boolean jump = entry.pending || follow == UiScrollFollow.WHEN_AT_END
            && wasAtEnd && Double.compare(entry.maximum, maximum) != 0;
        entry.maximum = maximum;
        entry.pending = false;
        if (jump) {
            entry.navigationTarget = null;
            resolved.add(id);
            return maximum;
        }
        if (entry.navigationTarget != null && entry.navigationTarget > maximum) {
            // Filtering/shrinking can invalidate a wheel motion's target. Do not let its next
            // frame move the offset back outside the new range or revive a stale follow intent.
            entry.navigationTarget = null;
            resolved.add(id);
        }
        if (entry.navigationTarget != null && Math.abs(offset - entry.navigationTarget) < .001)
            entry.navigationTarget = null;
        return Math.clamp(offset, 0, maximum);
    }
    Set<String> takeResolved() { Set<String> result = Set.copyOf(resolved); resolved.clear(); return result; }
    /** Page/removal boundaries cancel unfinished commands but retain each ID's offset semantics. */
    void cancelPending() {
        for (Entry entry : entries.values()) { entry.pending = false; entry.navigationTarget = null; }
        resolved.clear();
    }
}
