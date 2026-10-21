package dev.pane.ui;

/**
 * Immutable presentation of a world-free, standing player. Angles are degrees; body yaw 180
 * faces the viewer, head angles are relative to the body, and negative camera pitch looks down.
 * Height is the unprojected 1.8-block body height as a fraction of the slot, additionally limited
 * by a 1.1-block-wide standing frame. Feet are normalized slot coordinates (0..1).
 * Extreme rotations or anchors can clip against the rectangular slot; these are framing controls,
 * not a promise of pixel-tight bounds. Capes, equipment and animated poses are not rendered.
 */
public record UiPlayerPreview(float bodyYaw, float headYaw, float headPitch, float cameraPitch,
                              float heightFraction, float feetX, float feetY, boolean followPointer) {
    private static final UiPlayerPreview STANDING = new UiPlayerPreview(180, 0, 0, 0, .8F, .5F, .95F, false);

    public UiPlayerPreview {
        finite(bodyYaw, "bodyYaw");
        range(headYaw, -90, 90, "headYaw");
        range(headPitch, -90, 90, "headPitch");
        range(cameraPitch, -45, 45, "cameraPitch");
        range(heightFraction, .01F, 1, "heightFraction");
        range(feetX, 0, 1, "feetX");
        range(feetY, 0, 1, "feetY");
        bodyYaw %= 360;
    }

    public static UiPlayerPreview standing() { return STANDING; }
    public UiPlayerPreview bodyYaw(float degrees) {
        return new UiPlayerPreview(degrees, headYaw, headPitch, cameraPitch, heightFraction, feetX, feetY, followPointer);
    }
    public UiPlayerPreview headRotation(float yaw, float pitch) {
        return new UiPlayerPreview(bodyYaw, yaw, pitch, cameraPitch, heightFraction, feetX, feetY, followPointer);
    }
    public UiPlayerPreview cameraPitch(float degrees) {
        return new UiPlayerPreview(bodyYaw, headYaw, headPitch, degrees, heightFraction, feetX, feetY, followPointer);
    }
    public UiPlayerPreview heightFraction(float fraction) {
        return new UiPlayerPreview(bodyYaw, headYaw, headPitch, cameraPitch, fraction, feetX, feetY, followPointer);
    }
    public UiPlayerPreview feet(float x, float y) {
        return new UiPlayerPreview(bodyYaw, headYaw, headPitch, cameraPitch, heightFraction, x, y, followPointer);
    }
    /** Adds bounded head tracking; a blocking popup/focus scope uses the configured fixed rotation. */
    public UiPlayerPreview followPointer(boolean enabled) {
        return new UiPlayerPreview(bodyYaw, headYaw, headPitch, cameraPitch, heightFraction, feetX, feetY, enabled);
    }
    private static void finite(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
    private static void range(float value, float min, float max, String name) {
        finite(value, name);
        if (value < min || value > max) throw new IllegalArgumentException(name + " must be in [" + min + ", " + max + "]");
    }
}
