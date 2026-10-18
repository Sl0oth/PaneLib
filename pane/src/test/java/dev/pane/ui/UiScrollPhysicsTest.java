package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UiScrollPhysicsTest {
    private static final long FRAME = 16_000_000L;
    private static final long RESPONSE = 105_000_000L;

    @Test
    void continuousInputAdvancesDuringTheEventStreamWithoutRestarting() {
        long now = 0;
        double displayed = 0;
        UiScrollPhysics.Motion motion = null;
        double previous = 0;
        for (int event = 1; event <= 60; event++) {
            motion = UiScrollPhysics.retarget(motion, event * 4.6, now, RESPONSE);
            now += FRAME;
            UiScrollPhysics.Frame frame = UiScrollPhysics.advance(displayed, motion, now);
            displayed = frame.displayed();
            motion = frame.motion();
            assertTrue(displayed > previous, "the visible offset must move during continuous input");
            previous = displayed;
        }
        assertTrue(displayed > 240, "the viewport must remain close to a continuously moving target");
    }

    @Test
    void directionReversalChangesDirectionOnTheNextFrame() {
        UiScrollPhysics.Motion motion = UiScrollPhysics.retarget(null, 120, 0, RESPONSE);
        UiScrollPhysics.Frame forward = UiScrollPhysics.advance(0, motion, FRAME);
        motion = UiScrollPhysics.retarget(forward.motion(), 0, FRAME, RESPONSE);
        UiScrollPhysics.Frame reversed = UiScrollPhysics.advance(forward.displayed(), motion, FRAME * 2);
        assertTrue(reversed.displayed() < forward.displayed());
    }

    @Test
    void settlesExactlyAtTargetInFiniteFrames() {
        long now = 0;
        double displayed = 0;
        UiScrollPhysics.Motion motion = UiScrollPhysics.retarget(null, 46, now, RESPONSE);
        UiScrollPhysics.Frame frame = null;
        for (int index = 0; index < 30; index++) {
            now += FRAME;
            frame = UiScrollPhysics.advance(displayed, motion, now);
            displayed = frame.displayed();
            motion = frame.motion();
            if (frame.settled()) break;
        }
        assertTrue(frame != null && frame.settled());
        assertEquals(46, displayed);
    }

    @Test
    void longFrameIsCappedInsteadOfJumpingStraightToTheTarget() {
        UiScrollPhysics.Motion motion = UiScrollPhysics.retarget(null, 1000, 0, RESPONSE);
        UiScrollPhysics.Frame frame = UiScrollPhysics.advance(0, motion, 1_000_000_000L);
        assertTrue(frame.displayed() > 0);
        assertTrue(frame.displayed() < 1000);
    }

    @Test
    void thirtySecondsOfFastContinuousInputKeepsMovingAndSettlesWithoutAReleaseJump() {
        long now = 0;
        double displayed = 0;
        double target = 0;
        UiScrollPhysics.Motion motion = null;
        double largestStreamingStep = 0;
        int frames = (int) (30_000_000_000L / FRAME);
        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            target += 9.2;
            motion = UiScrollPhysics.retarget(motion, target, now, RESPONSE);
            now += FRAME;
            UiScrollPhysics.Frame frame = UiScrollPhysics.advance(displayed, motion, now);
            double step = frame.displayed() - displayed;
            assertTrue(step > 0, "visible content must advance throughout the 30-second stream");
            largestStreamingStep = Math.max(largestStreamingStep, step);
            displayed = frame.displayed();
            motion = frame.motion();
        }

        now += FRAME;
        UiScrollPhysics.Frame afterRelease = UiScrollPhysics.advance(displayed, motion, now);
        assertTrue(afterRelease.displayed() - displayed <= largestStreamingStep * 1.25,
            "release must not apply a large accumulated invisible jump");
        for (int frameIndex = 0; frameIndex < 60 && !afterRelease.settled(); frameIndex++) {
            displayed = afterRelease.displayed();
            motion = afterRelease.motion();
            now += FRAME;
            afterRelease = UiScrollPhysics.advance(displayed, motion, now);
        }
        assertTrue(afterRelease.settled());
        assertEquals(target, afterRelease.displayed());
    }
}
