package dev.pane.ui;

import java.awt.image.BufferedImage;

/**
 * The game frame captured beneath a screen opened with {@link UiScreenOptions.Builder#worldSnapshot(boolean)}:
 * the rendered world without any GUI, in physical window pixels. {@code revision} changes with each
 * new capture, so it can key a {@link UiLayer} cache. Treat the image as read-only.
 */
public record UiWorldSnapshot(BufferedImage image, long revision) { }
