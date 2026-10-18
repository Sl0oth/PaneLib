package dev.pane.ui;

/** What Minecraft-native content PaneLib extracts beneath its transparent retained surface. */
public enum UiBackdrop {
    /** PaneLib paints no native background. The document is responsible for covering the screen. */
    NONE,
    /** Minecraft's current panorama/menu or in-world background treatment. */
    MINECRAFT,
    /** Only the parent screen's background, such as the title panorama. */
    PARENT_BACKGROUND,
    /** The parent screen's background and visible UI, useful beneath a modal PaneLib screen. */
    PARENT_SCREEN
}
