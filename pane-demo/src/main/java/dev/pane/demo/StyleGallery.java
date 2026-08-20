package dev.pane.demo;

import dev.pane.ui.UiCanvas;
import dev.pane.ui.UiContext;
import dev.pane.ui.UiDocument;
import dev.pane.ui.UiPage;
import dev.pane.ui.UiScreen;
import dev.pane.ui.UiShell;
import dev.pane.ui.UiTheme;
import java.awt.Font;

/**
 * Five screens that share one library and look nothing alike.
 * <p>
 * Every page uses {@link UiShell#NONE}, so each owns the whole surface and paints its own
 * background, chrome, navigation and typography. Nothing here is a theme preset: the styles
 * differ because the painters differ.
 */
public final class StyleGallery {
    private static final String[][] STYLES = {
        {"terminal", "TERMINAL"},
        {"editorial", "EDITORIAL"},
        {"aurora", "AURORA"},
        {"blueprint", "BLUEPRINT"},
        {"brutalist", "BRUTALIST"},
    };

    private StyleGallery() {}

    public static UiScreen createScreen() {
        return new UiScreen(UiDocument.builder("Pane Style Gallery")
            .brand("Pane")
            .theme(UiTheme.dark())
            .shell(UiShell.NONE)
            .pageShortcuts(true)
            .page(new UiPage("terminal", "Terminal", "", 0xFF35F58A, StyleGallery::terminal))
            .page(new UiPage("editorial", "Editorial", "", 0xFF9A3B2A, StyleGallery::editorial))
            .page(new UiPage("aurora", "Aurora", "", 0xFF6FE3D0, StyleGallery::aurora))
            .page(new UiPage("blueprint", "Blueprint", "", 0xFF4FD8E8, StyleGallery::blueprint))
            .page(new UiPage("brutalist", "Brutalist", "", 0xFFE24E1B, StyleGallery::brutalist))
            .build());
    }

    // ================================================================ 1 · terminal

    private static void terminal(UiContext ui) {
        UiCanvas g = ui.canvas();
        int bg = 0xFF05070A, ink = 0xFF35F58A, dim = 0xFF1B7048;
        float w = ui.width(), h = ui.height();
        g.fill(bg, 0, 0, w, h);
        g.grid(0xFF0C1A14, .6F, 22, 0, 0, w, h);
        g.scanlines(0x18000000, 1, 2, 0, 0, w, h);

        float x = 46, y = 40 + (1 - ui.motion()) * 6;
        g.trackedText("PANE // NATIVE RENDER SHELL", x, y, 8, dim, Font.PLAIN, UiCanvas.MONO, 2.4F);
        g.text("root@pane:~$ status --verbose", x, y + 20, 17, ink, Font.BOLD, UiCanvas.MONO);
        g.line(dim, 1, x, y + 50, w - 46, y + 50);

        String[][] rows = {
            {"surface", "cached", "0.00ms", "98"},
            {"upload", "idle", "0.00ms", "0"},
            {"hit-regions", "ready", "0.01ms", "42"},
            {"overlay", "stratum", "0.14ms", "11"},
            {"animation", "stopped", "0.00ms", "0"},
        };
        float rowY = y + 68;
        for (int i = 0; i < rows.length; i++) {
            boolean hover = ui.hovered("term-" + i);
            float ry = rowY + i * 30;
            if (hover) g.fill(0xFF0E1F17, x - 10, ry - 6, w - 72, 26);
            g.text("[" + (hover ? "*" : "+") + "]", x, ry, 10, hover ? ink : dim, Font.PLAIN, UiCanvas.MONO);
            g.text(rows[i][0], x + 34, ry, 10, ink, Font.PLAIN, UiCanvas.MONO);
            g.text(rows[i][1], x + 190, ry, 10, dim, Font.PLAIN, UiCanvas.MONO);
            g.text(rows[i][2], x + 300, ry, 10, dim, Font.PLAIN, UiCanvas.MONO);
            float barW = 120, pct = Float.parseFloat(rows[i][3]) / 100F;
            g.fill(0xFF0F231A, x + 380, ry + 3, barW, 7);
            g.fill(ink, x + 380, ry + 3, barW * pct, 7);
            ui.hit("term-" + i, "term-" + rows[i][0], x - 10, ry - 6, w - 72, 26);
        }

        float boxY = rowY + rows.length * 30 + 18;
        g.strokeRoundRect(dim, 1, x, boxY, Math.min(430, w - 92), 62, 0);
        g.text("> retained surface holds until state changes", x + 14, boxY + 14, 9, ink, Font.PLAIN, UiCanvas.MONO);
        g.text("> no per-frame rasterisation", x + 14, boxY + 30, 9, dim, Font.PLAIN, UiCanvas.MONO);
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        g.text("> _", x + 14, boxY + 46, 9, blink ? ink : dim, Font.PLAIN, UiCanvas.MONO);

        switcher(ui, ink, dim, bg, UiCanvas.MONO, 0);
    }

    // ================================================================ 2 · editorial

    private static void editorial(UiContext ui) {
        UiCanvas g = ui.canvas();
        int paper = 0xFFF6F1E7, ink = 0xFF1A1712, rust = 0xFF9A3B2A, soft = 0xFF7C7367;
        float w = ui.width(), h = ui.height();
        g.fill(paper, 0, 0, w, h);
        g.grain(0x0E000000, .16F, 0, 0, w, h);

        float x = Math.max(58, w * .10F);
        float right = w - x;
        float y = 52 + (1 - ui.motion()) * 6;
        g.trackedText("ISSUE 04", x, y, 7.5F, rust, Font.BOLD, UiCanvas.SANS, 3F);
        g.trackedText("ON DRAWING INTERFACES", right - g.trackedTextWidth("ON DRAWING INTERFACES", 7.5F, Font.PLAIN, UiCanvas.SANS, 3F),
            y, 7.5F, soft, Font.PLAIN, UiCanvas.SANS, 3F);
        g.line(ink, 1.2F, x, y + 18, right, y + 18);

        g.text("The Quiet", x, y + 34, 44, ink, Font.PLAIN, UiCanvas.SERIF);
        g.text("Renderer", x, y + 82, 44, rust, Font.ITALIC, UiCanvas.SERIF);
        g.line(ink, .7F, x, y + 142, right, y + 142);

        // Drop cap and two columns of rules standing in for body copy.
        float colY = y + 158;
        g.text("A", x, colY - 6, 40, rust, Font.PLAIN, UiCanvas.SERIF);
        float colW = (right - x - 34) / 2;
        g.text("retained surface is painted once and", x + 38, colY + 2, 9.5F, ink, Font.PLAIN, UiCanvas.SERIF);
        g.text("then left alone. The screen holds still", x + 38, colY + 18, 9.5F, ink, Font.PLAIN, UiCanvas.SERIF);
        for (int i = 2; i < 9; i++) {
            float lw = i == 8 ? colW * .55F : colW;
            g.fill(0x22000000, x, colY + 6 + i * 16, lw, 1.5F);
        }
        for (int i = 0; i < 9; i++) {
            float lw = i == 8 ? colW * .38F : colW;
            g.fill(0x22000000, x + colW + 34, colY + 6 + i * 16, lw, 1.5F);
        }

        float pullY = colY + 176;
        g.line(rust, 2.5F, x, pullY, x, pullY + 54);
        g.text("“Nothing repaints until", x + 18, pullY, 15, ink, Font.ITALIC, UiCanvas.SERIF);
        g.text("something actually changes.”", x + 18, pullY + 24, 15, ink, Font.ITALIC, UiCanvas.SERIF);

        switcher(ui, rust, soft, paper, UiCanvas.SANS, 1);
    }

    // ================================================================ 3 · aurora

    private static void aurora(UiContext ui) {
        UiCanvas g = ui.canvas();
        int bg = 0xFF090B14, teal = 0xFF6FE3D0, indigo = 0xFF6C7BF4, text = 0xFFEDF1FA, muted = 0xFF8E97B5;
        float w = ui.width(), h = ui.height();
        g.fill(bg, 0, 0, w, h);
        g.radialRect(0x556C7BF4, 0x00090B14, w * .22F, h * .12F, Math.max(w, h) * .55F, 0, 0, w, h, 0);
        g.radialRect(0x446FE3D0, 0x00090B14, w * .86F, h * .82F, Math.max(w, h) * .5F, 0, 0, w, h, 0);

        // A curved ribbon, the kind of shape a rectangle-only canvas cannot express.
        g.path().moveTo(0, h * .68F)
            .curveTo(w * .28F, h * .52F, w * .52F, h * .86F, w, h * .60F)
            .lineTo(w, h).lineTo(0, h).close()
            .fillGradient(0x2A6C7BF4, 0x0C6FE3D0, 90);

        float x = 54, y = 46 + (1 - ui.motion()) * 8;
        g.trackedText("LIVE", x + 12, y + 7, 7, teal, Font.BOLD, UiCanvas.SANS, 2F);
        g.strokeRoundRect(UiCanvas.fade(teal, .5F), 1, x, y, 46, 20, 10);
        g.text("Session overview", x, y + 34, 30, text, Font.BOLD, UiCanvas.SANS);
        g.text("Gradients, glow, curves and translucency — all drawn from geometry.",
            x, y + 74, 9, muted, Font.PLAIN, UiCanvas.SANS);

        float cardY = y + 106;
        float cardW = Math.min(210, (w - 108 - 28) / 3F);
        String[][] cards = {{"Frame", "6.9", "ms"}, {"Memory", "412", "mb"}, {"Draws", "1", "call"}};
        for (int i = 0; i < cards.length; i++) {
            float cx = x + i * (cardW + 14);
            boolean hover = ui.hovered("aur-" + i);
            int tint = i == 1 ? indigo : teal;
            if (hover) g.glow(UiCanvas.fade(tint, .30F), 14, cx, cardY, cardW, 104, 20);
            g.gradientRect(0xFF161A2B, 0xFF10131F, 90, cx, cardY, cardW, 104, 20);
            g.strokeRoundRect(hover ? UiCanvas.fade(tint, .55F) : 0xFF232840, 1, cx, cardY, cardW, 104, 20);
            g.circle(UiCanvas.fade(tint, .18F), cx + 30, cardY + 34, 15);
            g.circle(tint, cx + 30, cardY + 34, 4.5F);
            g.text(cards[i][0], cx + 20, cardY + 58, 8, muted, Font.PLAIN, UiCanvas.SANS);
            g.text(cards[i][1], cx + 20, cardY + 70, 22, text, Font.BOLD, UiCanvas.SANS);
            g.text(cards[i][2], cx + 20 + g.textWidth(cards[i][1], 22, Font.BOLD) + 6, cardY + 82, 8, muted, Font.PLAIN, UiCanvas.SANS);
            ui.hit("aur-" + i, "aurora-card-" + i, cx, cardY, cardW, 104);
        }

        // Rounded clipping: the ribbon is masked to the panel instead of overflowing it.
        float panelY = cardY + 124;
        float panelH = Math.max(60, h - panelY - 76);
        g.withRoundedClip(x, panelY, w - 108, panelH, 22, () -> {
            g.gradientRect(0xFF141828, 0xFF0D101B, 90, x, panelY, w - 108, panelH, 22);
            g.path().moveTo(x, panelY + panelH * .74F)
                .curveTo(x + (w - 108) * .3F, panelY + panelH * .3F,
                         x + (w - 108) * .62F, panelY + panelH * .95F,
                         x + w - 108, panelY + panelH * .34F)
                .stroke(teal, 2.5F);
        });
        g.strokeRoundRect(0xFF232840, 1, x, panelY, w - 108, panelH, 22);
        g.text("Throughput", x + 22, panelY + 18, 10, text, Font.BOLD, UiCanvas.SANS);

        switcher(ui, teal, muted, bg, UiCanvas.SANS, 2);
    }

    // ================================================================ 4 · blueprint

    private static void blueprint(UiContext ui) {
        UiCanvas g = ui.canvas();
        int bg = 0xFF0C2233, cyan = 0xFF4FD8E8, line = 0xFF1D4256, pale = 0xFFB6D9E4;
        float w = ui.width(), h = ui.height();
        g.fill(bg, 0, 0, w, h);
        g.grid(line, .55F, 20, 0, 0, w, h);
        g.grid(UiCanvas.fade(cyan, .18F), .7F, 100, 0, 0, w, h);
        g.vignette(0x66000000, 0, 0, w, h);

        float x = 48, y = 38 + (1 - ui.motion()) * 6;
        g.strokeRoundRect(cyan, 1.2F, x, y, w - 96, h - y - 68, 0);
        g.trackedText("PANE / SHEET 01 / RETAINED SURFACE", x + 16, y + 14, 8, cyan, Font.PLAIN, UiCanvas.MONO, 2F);
        g.line(line, 1, x, y + 36, x + w - 96, y + 36);

        // Technical drawing: an exploded view of the render path with dimension lines.
        float dx = x + 60, dy = y + 76;
        float boxW = Math.min(190, (w - 220) / 3F), boxH = 78;
        String[] stages = {"PAINTER", "SURFACE", "GPU BLIT"};
        for (int i = 0; i < stages.length; i++) {
            float bx = dx + i * (boxW + 56);
            boolean hover = ui.hovered("bp-" + i);
            if (hover) g.fill(UiCanvas.fade(cyan, .12F), bx, dy, boxW, boxH);
            g.strokeRoundRect(hover ? cyan : pale, 1.1F, bx, dy, boxW, boxH, 0);
            g.line(line, .7F, bx, dy + 22, bx + boxW, dy + 22);
            g.trackedText(stages[i], bx + 12, dy + 7, 8, hover ? cyan : pale, Font.BOLD, UiCanvas.MONO, 1.6F);
            g.text("n = " + (i + 1), bx + 12, dy + 32, 8, pale, Font.PLAIN, UiCanvas.MONO);
            g.text(i == 2 ? "per frame" : "on change", bx + 12, dy + 48, 8, UiCanvas.fade(pale, .7F), Font.PLAIN, UiCanvas.MONO);
            ui.hit("bp-" + i, "blueprint-" + stages[i], bx, dy, boxW, boxH);
            if (i < stages.length - 1) {
                g.dashedLine(cyan, 1, 4, bx + boxW + 6, dy + boxH / 2, bx + boxW + 50, dy + boxH / 2);
                g.polygon(cyan, bx + boxW + 50, dy + boxH / 2, bx + boxW + 44, dy + boxH / 2 - 4,
                    bx + boxW + 44, dy + boxH / 2 + 4);
            }
        }

        // Dimension line beneath the whole assembly.
        float dimY = dy + boxH + 34;
        float dimRight = dx + stages.length * (boxW + 56) - 56;
        g.line(pale, .8F, dx, dimY, dimRight, dimY);
        g.line(pale, .8F, dx, dimY - 5, dx, dimY + 5);
        g.line(pale, .8F, dimRight, dimY - 5, dimRight, dimY + 5);
        g.fill(bg, (dx + dimRight) / 2 - 44, dimY - 7, 88, 14);
        g.centeredText("1 TEXTURE", (dx + dimRight) / 2, dimY, 8, cyan, Font.PLAIN, UiCanvas.MONO);

        // Legend, anchored low so the sheet reads as a drawing rather than a header.
        float legendY = Math.max(dimY + 40, h - 190);
        g.strokeRoundRect(pale, .9F, x + 18, legendY, Math.min(300, w - 140), 96, 0);
        g.line(pale, .9F, x + 18, legendY + 24, x + 18 + Math.min(300, w - 140), legendY + 24);
        g.trackedText("NOTES", x + 30, legendY + 8, 7.5F, cyan, Font.BOLD, UiCanvas.MONO, 2F);
        String[] notes = {"01  surface rebuilt on invalidation only",
                          "02  one texture bound per visible frame",
                          "03  entities drawn in a later stratum"};
        for (int i = 0; i < notes.length; i++) {
            g.text(notes[i], x + 30, legendY + 34 + i * 18, 8, pale, Font.PLAIN, UiCanvas.MONO);
        }
        float stampX = Math.min(w - 210, x + 360);
        if (stampX > x + 340) {
            g.strokeRoundRect(UiCanvas.fade(cyan, .8F), 1.4F, stampX, legendY + 14, 150, 62, 0);
            g.trackedText("VERIFIED", stampX + 18, legendY + 30, 11, cyan, Font.BOLD, UiCanvas.MONO, 3F);
            g.text("no per-frame raster", stampX + 18, legendY + 50, 7, UiCanvas.fade(pale, .75F), Font.PLAIN, UiCanvas.MONO);
        }

        // Rotated annotation, only possible with a transform.
        g.withRotation(-90, x + 26, y + h * .5F, () ->
            g.trackedText("SCALE 1:1", x + 26, y + h * .5F, 7.5F, UiCanvas.fade(pale, .7F), Font.PLAIN, UiCanvas.MONO, 2F));

        switcher(ui, cyan, pale, bg, UiCanvas.MONO, 3);
    }

    // ================================================================ 5 · brutalist

    private static void brutalist(UiContext ui) {
        UiCanvas g = ui.canvas();
        int paper = 0xFFEDEDE8, ink = 0xFF111111, orange = 0xFFE24E1B;
        float w = ui.width(), h = ui.height();
        g.fill(paper, 0, 0, w, h);

        float x = 42, y = 34 + (1 - ui.motion()) * 7;
        g.fill(ink, x, y, w - 84, 4);
        g.trackedText("PANE / NO ORNAMENT", x, y + 14, 8, ink, Font.BOLD, UiCanvas.SANS, 3.5F);

        g.text("BUILT", x - 4, y + 34, 62, ink, Font.BOLD, UiCanvas.SANS);
        g.text("FROM", x - 4, y + 92, 62, ink, Font.BOLD, UiCanvas.SANS);
        g.text("CODE", x - 4, y + 150, 62, orange, Font.BOLD, UiCanvas.SANS);

        float stripeX = Math.max(x + 300, w * .52F);
        float stripeW = w - stripeX - 42;
        if (stripeW > 60) {
            g.fill(ink, stripeX, y + 34, stripeW, 84);
            g.stripes(orange, 5, 7, 45, stripeX, y + 34, stripeW, 84);
            g.fill(paper, stripeX + 12, y + 58, stripeW - 24, 36);
            g.centeredText("ZERO ASSETS", stripeX + stripeW / 2, y + 76, 13, ink, Font.BOLD, UiCanvas.SANS);
        }

        // Hard offset shadow: a solid block behind, no blur. Very much the idiom.
        float cardY = y + 224;
        float cardW = Math.min(250, (w - 84 - 24) / 2F);
        String[][] blocks = {{"01", "RETAINED", "Paints once, holds."}, {"02", "NATIVE", "No browser, no assets."}};
        for (int i = 0; i < blocks.length; i++) {
            float bx = x + i * (cardW + 24);
            boolean hover = ui.hovered("bru-" + i);
            float shift = hover ? 3 : 0;
            g.fill(ink, bx + 7, cardY + 7, cardW, 96);
            g.fill(i == 0 ? orange : paper, bx + shift, cardY + shift, cardW, 96);
            g.strokeRoundRect(ink, 2.5F, bx + shift, cardY + shift, cardW, 96, 0);
            int fg = i == 0 ? paper : ink;
            g.text(blocks[i][0], bx + 14 + shift, cardY + 10 + shift, 26, fg, Font.BOLD, UiCanvas.SANS);
            g.trackedText(blocks[i][1], bx + 14 + shift, cardY + 48 + shift, 10, fg, Font.BOLD, UiCanvas.SANS, 2F);
            g.text(blocks[i][2], bx + 14 + shift, cardY + 68 + shift, 8, fg, Font.PLAIN, UiCanvas.SANS);
            ui.hit("bru-" + i, "brutalist-" + blocks[i][1], bx, cardY, cardW + 7, 103);
        }

        switcher(ui, orange, ink, paper, UiCanvas.SANS, 4);
    }

    // ================================================================ shared switcher

    /**
     * Each page draws its own navigation in its own idiom. There is no shell here; this is
     * ordinary painter code, which is exactly why the five look nothing alike.
     */
    private static void switcher(UiContext ui, int accent, int muted, int background, String family, int active) {
        UiCanvas g = ui.canvas();
        float w = ui.width(), h = ui.height();
        float barY = h - 44;
        g.fill(UiCanvas.fade(background, .92F), 0, barY - 8, w, 52);
        g.line(UiCanvas.fade(muted, .35F), .8F, 0, barY - 8, w, barY - 8);
        float cursor = 44;
        for (int i = 0; i < STYLES.length; i++) {
            String id = "style-" + STYLES[i][0];
            String label = STYLES[i][1];
            float labelW = g.trackedTextWidth(label, 7.5F, Font.BOLD, family, 2F);
            boolean on = i == active;
            boolean hover = ui.hovered(id);
            if (on) g.fill(accent, cursor - 8, barY + 20, labelW + 16, 2);
            g.trackedText(label, cursor, barY + 4, 7.5F, on ? accent : hover ? muted : UiCanvas.fade(muted, .6F),
                Font.BOLD, family, 2F);
            ui.hit(id, "page:" + STYLES[i][0], cursor - 8, barY - 2, labelW + 16, 26);
            cursor += labelW + 34;
        }
        g.trackedText("1-5 · ESC", w - 100, barY + 4, 7, UiCanvas.fade(muted, .5F), Font.PLAIN, family, 1.5F);
    }
}
