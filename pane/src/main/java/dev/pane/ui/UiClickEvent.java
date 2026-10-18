package dev.pane.ui;

import net.minecraft.client.input.MouseButtonEvent;

/** A raw pointer press enriched with PaneLib's target id and coordinate conversion. */
public record UiClickEvent(String targetId, MouseButtonEvent nativeEvent, boolean doubleClick,
                           float layoutX, float layoutY) {
    public boolean hasTarget(String id) { return id != null && id.equals(targetId); }
}
