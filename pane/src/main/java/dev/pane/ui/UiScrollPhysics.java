package dev.pane.ui;

/** Internal frame-rate-independent target follower used by smooth wheel and trackpad scrolling. */
final class UiScrollPhysics {
    private static final double SETTLE_DISTANCE = 0.20;
    private static final long MAX_FRAME_NANOS = 64_000_000L;

    private UiScrollPhysics() { }

    static Motion retarget(Motion active, double target, long now, long responseNanos) {
        long lastFrame = active == null ? now : active.lastFrame();
        return new Motion(target, lastFrame, now, Math.max(1, responseNanos));
    }

    static Frame advance(double displayed, Motion motion, long now) {
        long elapsed = Math.max(0, Math.min(MAX_FRAME_NANOS, now - motion.lastFrame()));
        // Three time constants fit inside the consumer's configured smoothing duration. Unlike
        // restarting an easing curve, this follows a moving target continuously while input is
        // arriving and therefore cannot build up a hidden post-input jump.
        double alpha = elapsed <= 0 ? 0
            : 1 - Math.exp(-3D * elapsed / motion.responseNanos());
        double next = displayed + (motion.target() - displayed) * alpha;
        boolean settled = Math.abs(motion.target() - next) <= SETTLE_DISTANCE;
        if (settled) next = motion.target();
        return new Frame(next,
            new Motion(motion.target(), now, motion.lastInput(), motion.responseNanos()), settled);
    }

    record Motion(double target, long lastFrame, long lastInput, long responseNanos) { }
    record Frame(double displayed, Motion motion, boolean settled) { }
}
