package dev.pane.ui;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UiPlayerPreviewTest {
    @Test void optionsAreImmutableAndRejectInvalidGeometry() {
        var defaults = UiPlayerPreview.standing();
        var posed = defaults.bodyYaw(570).headRotation(-12, 5).cameraPitch(-8).feet(.4F, .9F);
        assertEquals(180, defaults.bodyYaw());
        assertEquals(210, posed.bodyYaw());
        assertFalse(defaults.followPointer());
        assertThrows(IllegalArgumentException.class, () -> defaults.bodyYaw(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> defaults.cameraPitch(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> defaults.headRotation(91, 0));
        assertThrows(IllegalArgumentException.class, () -> defaults.cameraPitch(-46));
        assertThrows(IllegalArgumentException.class, () -> defaults.heightFraction(0));
        assertThrows(IllegalArgumentException.class, () -> defaults.feet(-.1F, 1));
    }
    @Test void wayfarerKeepsItsExistingCameraSizeAndFootAnchor() {
        var pose = UiPlayerPreview.standing().heightFraction(.81F).feet(.5F, 524F/540F);
        var f = PlayerPreviewRenderer.frame(new NativeBounds(100, 200, 560, 1080), 2, pose, 0, 0, false);
        assertEquals(243, f.size(), .001);
        assertEquals(50, f.x()); assertEquals(100, f.y());
        assertEquals(524, f.height()/2F + f.offsetY()*f.size(), .001);
        assertEquals(140, f.width()/2F + f.offsetX()*f.size(), .001);
    }
    @Test void physicalFootAnchorSurvivesGuiScaleChanges() {
        var box = new NativeBounds(60, 90, 600, 1200);
        var pose = UiPlayerPreview.standing().feet(.3F, .9F);
        for (double scale : new double[]{1, 1.5, 2, 3}) {
            var f = PlayerPreviewRenderer.frame(box, scale, pose, 0, 0, false);
            assertEquals(240, (f.x()+f.width()/2F+f.offsetX()*f.size())*scale, .001);
            assertEquals(1170, (f.y()+f.height()/2F+f.offsetY()*f.size())*scale, .001);
        }
    }
    @Test void narrowSlotsConstrainStandingFrameWithoutChangingFootAnchor() {
        var f = PlayerPreviewRenderer.frame(new NativeBounds(0, 0, 20, 500), 1,
            UiPlayerPreview.standing(), 0, 0, false);
        assertEquals(20/1.1F, f.size(), .001);
        assertEquals(475, 250+f.offsetY()*f.size(), .001);
    }
    @Test void fixedPoseIgnoresPointerAndTrackingRespectsModalBlocking() {
        var box = new NativeBounds(0, 0, 200, 400);
        var fixed = UiPlayerPreview.standing().headRotation(10, 5);
        var left = PlayerPreviewRenderer.frame(box, 1, fixed, -10000, 0, false);
        assertEquals(left, PlayerPreviewRenderer.frame(box, 1, fixed, 10000, 900, false));
        assertEquals(left, PlayerPreviewRenderer.frame(box, 1, fixed.followPointer(true), 10000, 900, true));
        var tracked = PlayerPreviewRenderer.frame(box, 1, fixed.followPointer(true), 10000, 900, false);
        assertNotEquals(left.headYaw(), tracked.headYaw());
        assertTrue(Math.abs(tracked.headYaw()) <= 90 && Math.abs(tracked.headPitch()) <= 90);
    }
    @Test void absentHiddenAndSubpixelSlotsDoNotSubmitOrNeedMinecraft() {
        var context = new NativeOverlayContext(null, "test", Map.of(
            "empty", new NativeBounds(0,0,0,200), "tiny", new NativeBounds(0,0,.1,.1)), 2, 0, 0);
        assertDoesNotThrow(() -> context.playerPreview("missing", null));
        assertDoesNotThrow(() -> context.playerPreview("empty", null));
        assertDoesNotThrow(() -> context.playerPreview("tiny", null));
        assertEquals(0, context.renderedEntityCount());
        assertThrows(NullPointerException.class, () -> context.playerPreview("missing", null, null));
    }
}
