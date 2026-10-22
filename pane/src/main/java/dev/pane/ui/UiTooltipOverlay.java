package dev.pane.ui;

import com.mojang.blaze3d.textures.GpuSampler;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Hover tooltip in a small independent texture. Baking one into the full page used to switch
 * virtual rows out of their atlas and back through the page rasterizer, whose pixel rounding
 * differed by one or two pixels and made every line of text appear to jump.
 */
final class UiTooltipOverlay {
    private static final float OUTER_PAD = 7, PAD_X = 9, HEIGHT = 23;
    private UiRetainedTexture surface;
    private UiRect bounds;
    /** Set when the tooltip text, anchor or theme may have changed. */
    boolean dirty = true;
    long allocations;

    /** Composites the tooltip, repainting it first when dirty. Returns false when nothing is shown. */
    boolean render(GuiGraphicsExtractor graphics, GpuSampler sampler, UiTooltip tooltip, BufferedImage measureImage,
                   float quality, int layoutWidth, int layoutHeight, float layoutScale, UiTheme theme) {
        if (dirty || surface == null) {
            if (measureImage == null) bounds = null;
            else rebuild(tooltip, measureImage, quality, layoutWidth, layoutHeight, theme);
        }
        if (surface == null || bounds == null) return false;
        graphics.nextStratum();
        graphics.blit(surface.texture.getTextureView(), sampler,
            Math.round(bounds.x() / layoutScale), Math.round(bounds.y() / layoutScale),
            Math.round(bounds.right() / layoutScale), Math.round(bounds.bottom() / layoutScale), 0, 1, 0, 1);
        return true;
    }

    private void rebuild(UiTooltip tooltip, BufferedImage measureImage, float quality,
                         int layoutWidth, int layoutHeight, UiTheme theme) {
        // Do not leave stale bounds drawable if measuring or allocating the replacement fails.
        bounds = null;
        float width;
        Graphics2D measure = measureImage.createGraphics();
        try {
            measure.scale(quality, quality);
            width = Math.min(Math.max(54, new UiCanvas(measure, measureImage).textWidth(tooltip.text(), 8,
                Font.PLAIN, theme.fontFamily()) + PAD_X * 2), Math.max(54, layoutWidth - 12));
        } finally {
            measure.dispose();
        }
        UiRect anchor = tooltip.anchor();
        float x = Math.max(6, Math.min(anchor.x() + anchor.width() / 2 - width / 2, layoutWidth - width - 6));
        float below = anchor.bottom() + 7;
        float above = anchor.y() - HEIGHT - 7;
        float y = below + HEIGHT <= layoutHeight - 6 ? below : Math.max(6, above);
        float textureWidth = width + OUTER_PAD * 2;
        float textureHeight = HEIGHT + OUTER_PAD * 2;
        int pixelWidth = Math.max(1, Math.round(textureWidth * quality));
        int pixelHeight = Math.max(1, Math.round(textureHeight * quality));
        if (surface == null || !surface.matches(pixelWidth, pixelHeight)) {
            close();
            surface = new UiRetainedTexture("pane-tooltip-surface", pixelWidth, pixelHeight);
            allocations++;
        } else surface.clear();
        Graphics2D painter = surface.image.createGraphics();
        try {
            painter.scale(quality, quality);
            UiCanvas canvas = new UiCanvas(painter, surface.image);
            canvas.shadow(0x72000000, 5, 0, 2, OUTER_PAD, OUTER_PAD, width, HEIGHT, 6);
            canvas.roundRect(0xFF111419, OUTER_PAD, OUTER_PAD, width, HEIGHT, 6);
            canvas.strokeRoundRect(theme.border(), .8F, OUTER_PAD, OUTER_PAD, width, HEIGHT, 6);
            canvas.centeredText(tooltip.text(), OUTER_PAD + width / 2, OUTER_PAD + HEIGHT / 2,
                8, theme.text(), Font.PLAIN, theme.fontFamily());
        } finally {
            painter.dispose();
        }
        surface.upload();
        bounds = new UiRect(x - OUTER_PAD, y - OUTER_PAD, textureWidth, textureHeight);
        dirty = false;
    }

    void close() {
        if (surface != null) surface.close();
        surface = null;
        bounds = null;
        dirty = true;
    }
}
