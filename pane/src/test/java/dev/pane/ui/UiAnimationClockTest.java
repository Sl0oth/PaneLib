package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class UiAnimationClockTest {
    @Test void reversalStartsAtCurrentValueAndReducedMotionCanInterrupt() {
        Map<String, UiAnimationClock.State> states = new HashMap<>();
        new UiAnimationClock(states, 0, 1).value("slide", 0, 100, UiEasing.LINEAR);
        new UiAnimationClock(states, 1, 1).value("slide", 1, 100, UiEasing.LINEAR);
        float before = new UiAnimationClock(states, 40_000_001, 1).value("slide", 1, 100, UiEasing.LINEAR);
        assertEquals(before, new UiAnimationClock(states, 40_000_001, 1).value("slide", 0, 100, UiEasing.LINEAR));
        assertEquals(.2F, new UiAnimationClock(states, 90_000_001, 1).value("slide", 0, 100, UiEasing.LINEAR), .001F);
        var disabled = new UiAnimationClock(states, 90_000_002, 0);
        assertEquals(0, disabled.value("slide", 0, 100, UiEasing.LINEAR));
        assertFalse(disabled.active());
    }
    @Test
    void adoptsFirstValueThenAnimatesOnlyAfterTargetChanges() {
        Map<String, UiAnimationClock.State> states = new HashMap<>();
        UiAnimationClock initial = new UiAnimationClock(states, 1_000, 1);
        assertEquals(0, initial.value("hover", 0, 100, UiEasing.LINEAR));
        assertFalse(initial.active());

        UiAnimationClock changed = new UiAnimationClock(states, 2_000, 1);
        assertEquals(0, changed.value("hover", 1, 100, UiEasing.LINEAR));
        assertTrue(changed.active());
        assertEquals(java.util.Set.of("hover"), changed.activeIds());

        UiAnimationClock halfway = new UiAnimationClock(states, 50_002_000, 1);
        assertEquals(.5F, halfway.value("hover", 1, 100, UiEasing.LINEAR), .001F);
        assertTrue(halfway.active());

        UiAnimationClock finished = new UiAnimationClock(states, 100_002_000, 1);
        assertEquals(1, finished.value("hover", 1, 100, UiEasing.LINEAR));
        assertFalse(finished.active());
        assertTrue(finished.activeIds().isEmpty());
        assertEquals(java.util.Set.of("hover"),finished.usedIds(),"completed but present controls retain their target state");
    }

    @Test
    void reducedMotionSnapsImmediately() {
        Map<String, UiAnimationClock.State> states = new HashMap<>();
        new UiAnimationClock(states, 1, 0).value("state", 0, 500, UiEasing.PAPER);
        UiAnimationClock changed = new UiAnimationClock(states, 2, 0);
        assertEquals(1, changed.value("state", 1, 500, UiEasing.PAPER));
        assertFalse(changed.active());
    }
}
