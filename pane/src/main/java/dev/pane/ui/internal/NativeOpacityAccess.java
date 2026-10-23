package dev.pane.ui.internal;

/**
 * Internal metadata on a deferred Minecraft draw state; never retained globally.
 * <p><b>Internal:</b> public only so PaneLib's mixins can implement it; not part of PaneLib's API.
 */
public interface NativeOpacityAccess {
    /** The opacity this draw state was recorded with. */
    float panelibOpacity();
}
