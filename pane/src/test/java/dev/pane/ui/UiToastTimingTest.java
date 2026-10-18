package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class UiToastTimingTest {
    @Test void plateauHasNoChangesButFadeOutDoes() {
        assertEquals(0, opacity(0, 180));
        assertEquals(.5F, opacity(90, 180));
        assertEquals(1, opacity(180, 180));
        assertEquals(opacity(200, 180), opacity(2000, 180));
        assertEquals(.5F, opacity(2910, 180));
        assertEquals(0, opacity(3000, 180));
    }

    @Test void skippedFadeBoundaryStillNeedsFinalOpaqueFrame() {
        assertNotEquals(opacity(170, 180), opacity(230, 180));
        assertEquals(opacity(230, 180), opacity(2600, 180));
    }

    @Test void disabledMotionHasNoOpacityAnimation() {
        assertEquals(1, opacity(0, 0));
        assertEquals(1, opacity(1500, 0));
        assertEquals(1, opacity(2999, 0));
    }

    @Test void overlappingFadesStayContinuousAndBounded() {
        assertEquals(.75F, opacity(1500, 2000));
        assertEquals(opacity(1490, 2000), opacity(1510, 2000));
        assertEquals(0, opacity(-100, 180));
        assertEquals(0, opacity(3100, 180));
    }

    private static float opacity(long millis, long fadeMillis) {
        return UiToastTiming.opacity(millis * 1_000_000L, 0, 3_000_000_000L, fadeMillis * 1_000_000L);
    }
}
