package dev.pane.ui;

/**
 * Resolves placement on the client render thread when the logical viewport changes or a policy
 * is installed. Return a non-null placement; keep this callback free of rendering and HUD mutations.
 * Capture immutable settings and reinstall the policy when those settings change. This does not
 * automatically avoid other HUDs; consumers can share a responsive recipe between their widgets.
 */
@FunctionalInterface
public interface UiHudPlacementPolicy {
    /** The placement to use for the current viewport. */
    UiHudPlacement resolve(UiHudViewport viewport);
}
