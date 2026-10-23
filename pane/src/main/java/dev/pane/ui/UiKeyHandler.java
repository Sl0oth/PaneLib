package dev.pane.ui;

import net.minecraft.client.input.KeyEvent;

/** Consumer-owned raw keyboard handling after text editing and modal dismissal have had priority. */
@FunctionalInterface
public interface UiKeyHandler {
    /** Handles no keys. */
    UiKeyHandler NONE = (screen, event) -> false;

    /** Return true when the event was handled and PaneLib should stop dispatching it. */
    boolean keyPressed(UiScreen screen, KeyEvent event);
}
