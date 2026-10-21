package dev.pane.ui;

import java.util.Objects;

/** Placement-only cache; changing placement never dirties retained widget pixels. */
final class UiHudPlacementResolver {
    private UiHudPlacement placement = UiHudPlacement.topLeft();
    private UiHudPlacementPolicy policy;
    private float lastWidth = -1, lastHeight = -1;

    UiHudPlacement placement() { return placement; }
    void setPlacement(UiHudPlacement value) {
        placement = Objects.requireNonNull(value, "placement");
        policy = null;
        lastWidth = lastHeight = -1;
    }
    void setPolicy(UiHudPlacementPolicy value) {
        policy = Objects.requireNonNull(value, "policy");
        lastWidth = lastHeight = -1;
    }
    UiHudPlacement resolve(float width, float height) {
        if (policy != null && (width != lastWidth || height != lastHeight)) {
            UiHudPlacement next = Objects.requireNonNull(policy.resolve(new UiHudViewport(width, height)), "policy placement");
            placement = next;
            lastWidth = width; lastHeight = height;
        }
        return placement;
    }
}
