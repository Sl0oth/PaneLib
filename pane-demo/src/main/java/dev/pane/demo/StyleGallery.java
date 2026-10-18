package dev.pane.demo;

import dev.pane.ui.UiCanvas;
import dev.pane.ui.UiContext;
import dev.pane.ui.UiDialog;
import dev.pane.ui.UiDialogStyle;
import dev.pane.ui.UiDocument;
import dev.pane.ui.UiMenuItem;
import dev.pane.ui.UiPage;
import dev.pane.ui.UiRect;
import dev.pane.ui.UiScreen;
import dev.pane.ui.UiSelectOption;
import dev.pane.ui.UiShell;
import dev.pane.ui.UiTheme;
import dev.pane.ui.UiToast;
import dev.pane.ui.UiToastPlacement;
import dev.pane.ui.UiToastStyle;
import java.awt.Font;
import net.minecraft.client.Minecraft;

/**
 * Nine screens that share one library and look nothing alike.
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
        {"harbor", "HARBOR"},
        {"pixelport", "PIXELPORT"},
        {"fieldnotes", "FIELD NOTES"},
        {"orbit", "ORBIT"},
    };

    private StyleGallery() {}

    public static UiScreen createScreen() {
        UiScreen[] holder = new UiScreen[1];
        UiScreen screen = new UiScreen(UiDocument.builder("Pane Style Gallery")
            .brand("Pane")
            .theme(UiTheme.light())
            .shell(UiShell.NONE)
            .pageShortcuts(true)
            .page(new UiPage("terminal", "Terminal", "", 0xFF35F58A, StyleGallery::terminal))
            .page(new UiPage("editorial", "Editorial", "", 0xFF9A3B2A, StyleGallery::editorial))
            .page(new UiPage("aurora", "Aurora", "", 0xFF6FE3D0, StyleGallery::aurora))
            .page(new UiPage("blueprint", "Blueprint", "", 0xFF4FD8E8, StyleGallery::blueprint))
            .page(new UiPage("brutalist", "Brutalist", "", 0xFFE24E1B, StyleGallery::brutalist))
            .page(new UiPage("harbor", "Harbor", "", 0xFF1771C6, StyleGallery::harbor))
            .page(new UiPage("pixelport", "Pixelport", "", 0xFF4DCC82, StyleGallery::pixelport))
            .page(new UiPage("fieldnotes", "Field Notes", "", 0xFF637A43, StyleGallery::fieldnotes))
            .page(new UiPage("orbit", "Orbit", "", 0xFF6CC7E8, StyleGallery::orbit))
            .build(), null, (type, key, value) -> handleEvent(holder[0], type, key, value));
        holder[0] = screen;
        return screen;
    }

    private static void handleEvent(UiScreen screen, String type, String key, String value) {
        if (screen == null || !"action".equals(type)) return;
        String title = switch (key) {
            case "harbor-invite" -> "Invite sent";
            case "harbor-archive" -> "Board archived";
            case "pixel-launch" -> "Launch queue ready";
            case "pixel-buy" -> "Pack added";
            case "field-save" -> "Note saved";
            case "field-discard" -> "Draft discarded";
            case "orbit-calibrate" -> "Calibration started";
            case "orbit-abort" -> "Sequence held";
            default -> "Action complete";
        };
        String page = screen.pageId();
        screen.toast(UiToast.builder("gallery-" + key, title)
            .message("The " + screen.pageId() + " example updated its local state.")
            .icon(page.equals("pixelport") ? "+" : page.equals("fieldnotes") ? "•" : "✓")
            .placement(UiToastPlacement.AUTO).style(galleryToastStyle(page))
            .build());
    }

    private static UiToastStyle galleryToastStyle(String page) {
        return switch (page) {
            case "harbor" -> UiToastStyle.builder().background(0xFFE8F5FF).border(0xFF5EA8DF)
                .titleColor(0xFF17354D).messageColor(0xFF54728B).iconBackground(0xFF1771C6).iconColor(0xFFFFFFFF)
                .closeBackground(0xFFD6EAF9).closeColor(0xFF17354D).radius(16).build();
            case "pixelport" -> UiToastStyle.builder().background(0xFF182641).border(0xFF4DCC82)
                .titleColor(0xFFF1F5FF).messageColor(0xFFB7C8E3).iconBackground(0xFF4DCC82).iconColor(0xFF101522)
                .closeBackground(0xFF263A5F).closeColor(0xFFF1F5FF).radius(0).build();
            case "fieldnotes" -> UiToastStyle.builder().background(0xFFFFFDF5).border(0xFF9BA77C)
                .titleColor(0xFF303425).messageColor(0xFF737562).iconBackground(0xFF637A43).iconColor(0xFFFFFFFF)
                .closeBackground(0xFFE6E4D6).closeColor(0xFF303425).radius(3).build();
            case "orbit" -> UiToastStyle.builder().background(0xFF0B1D29).border(0xFF5BAFCB)
                .titleColor(0xFFD8F4FF).messageColor(0xFF8AB4C3).iconBackground(0xFF1F6986).iconColor(0xFFFFFFFF)
                .closeBackground(0xFF153344).closeColor(0xFFD8F4FF).radius(2).build();
            default -> UiToastStyle.builder().background(0xFF14293A).border(0xFF3E8EBB)
                .titleColor(0xFFE8F6FF).messageColor(0xFFA9D6ED).iconBackground(0xFF1E6693)
                .iconColor(0xFFFFFFFF).radius(12).build();
        };
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

    // ================================================================ 6 · harbor

    /** Bright, calm productivity UI: text input, menus, selectors, confirmation, and toasts. */
    private static void harbor(UiContext ui) {
        UiCanvas g = ui.canvas();
        float w = ui.width(), h = ui.height();
        int sky = 0xFFF5FAFF, blue = 0xFF1771C6, ink = 0xFF16324A, muted = 0xFF668197;
        g.fill(sky, 0, 0, w, h);
        g.radialRect(0x284DB8FA, 0x00F5FAFF, w * .92F, h * .08F, w * .55F, 0, 0, w, h, 0);
        float x = 34, y = 28 + (1 - ui.motion()) * 7;
        g.text("Harbor", x, y, 28, ink, Font.BOLD, UiCanvas.SANS);
        g.text("a soft, useful workspace", x, y + 33, 9, muted, Font.PLAIN, UiCanvas.SANS);
        ui.textField("harbor-search", "harbor-search", "Search workspaces", Math.max(x + 190, w - 390), y,
            Math.max(100, Math.min(210, w - 520)), "");
        ui.menuButton("harbor-actions", "Invite", w - 196, y, 72, 30, UiContext.ButtonStyle.SECONDARY,
            UiContext.Shape.PILL, java.util.List.of(new UiMenuItem("invite", "Invite teammate", "harbor-invite"),
                new UiMenuItem("copy", "Copy board link", "harbor-invite")));
        ui.dialogButton("harbor-archive", "Archive", w - 112, y, 78, 30, UiContext.ButtonStyle.GHOST,
            UiContext.Shape.PILL, new UiDialog("Archive this board?", "You can restore it later from the archive.",
                "Archive", "harbor-archive", "Keep it", "harbor-archive", UiDialogStyle.builder()
                    .backdrop(0x663D79A5).surface(0xFFF9FCFF).border(0xFF77B5E4).titleColor(0xFF17354D)
                    .messageColor(0xFF58758B).cancelBackground(0xFFE7F2FB).cancelBorder(0xFF9DC6E5)
                    .cancelText(0xFF17354D).confirmBackground(0xFF1771C6).confirmText(0xFFFFFFFF).radius(18).build()));
        ui.tooltip("harbor-archive", "A confirmation dialog protects this board.");

        float navY = y + 74, navW = 150;
        g.roundRect(0xFFFFFFFF, x, navY, navW, h - navY - 72, 18);
        g.strokeRoundRect(0xFFD7E6F1, .8F, x, navY, navW, h - navY - 72, 18);
        String[] nav = {"Today", "Projects", "People", "Archive"};
        for (int i = 0; i < nav.length; i++) {
            float ny = navY + 20 + i * 34;
            boolean selected = ui.stringValue("harbor-nav", "Today").equals(nav[i]);
            if (selected) g.roundRect(0xFFE4F2FF, x + 10, ny - 5, navW - 20, 26, 9);
            g.text(nav[i], x + 24, ny + 2, 9, selected ? blue : muted, selected ? Font.BOLD : Font.PLAIN, UiCanvas.SANS);
            ui.selectable("harbor-nav-" + i, "harbor-nav", nav[i], x + 10, ny - 5, navW - 20, 26);
        }
        float boardX = x + navW + 18, boardW = w - boardX - 34, boardY = navY;
        g.roundRect(0xFFFFFFFF, boardX, boardY, boardW, h - boardY - 72, 18);
        g.strokeRoundRect(0xFFD7E6F1, .8F, boardX, boardY, boardW, h - boardY - 72, 18);
        String section = ui.stringValue("harbor-nav", "Today");
        ui.setValue("harbor-nav", section);
        String heading = switch (section) {
            case "Projects" -> "Project harbor";
            case "People" -> "People aboard";
            case "Archive" -> "Quiet archive";
            default -> "Today’s tide";
        };
        String subheading = switch (section) {
            case "Projects" -> "Three projects are moving with the tide.";
            case "People" -> "A small team, all moving together.";
            case "Archive" -> "Old boards stay safe and out of the way.";
            default -> "Small steps, clear water.";
        };
        g.text(heading, boardX + 22, boardY + 20, 16, ink, Font.BOLD, UiCanvas.SANS);
        g.text(subheading, boardX + 22, boardY + 43, 8.5F, muted, Font.PLAIN, UiCanvas.SANS);
        String mode = ui.dropdown("harbor-view", "harbor-view", boardX + boardW - 142, boardY + 18, 118, 28, "Board",
            java.util.List.of(new UiSelectOption("Board", "Board"), new UiSelectOption("Timeline", "Timeline"),
                new UiSelectOption("Calendar", "Calendar")));
        String[] cards = switch (section) {
            case "Projects" -> new String[] {"Lantern release", "Board refresh", "Open docs"};
            case "People" -> new String[] {"Maya is reviewing", "Ben joined the board", "Two invites pending"};
            case "Archive" -> new String[] {"Spring planning", "Old launch notes", "Research shelf"};
            default -> new String[] {"Outline launch", "Review the path", "Send the note"};
        };
        for (int i = 0; i < cards.length; i++) {
            float cy = boardY + 78 + i * 62;
            int color = i == 0 ? 0xFFE5F3FF : 0xFFF6FAFD;
            g.roundRect(color, boardX + 20, cy, boardW - 40, 50, 13);
            g.circle(i == 0 ? blue : 0xFF9DB5C7, boardX + 42, cy + 25, 6);
            g.text(cards[i], boardX + 58, cy + 11, 10, ink, Font.BOLD, UiCanvas.SANS);
            g.text(i == 0 ? "Ready to share" : "A quiet next step", boardX + 58, cy + 27, 8, muted, Font.PLAIN, UiCanvas.SANS);
        }
        g.text(section + " / " + mode + " view", boardX + 22, boardY + h - boardY - 104, 8, muted, Font.PLAIN, UiCanvas.SANS);
        switcher(ui, blue, muted, sky, UiCanvas.SANS, 5);
    }

    // ================================================================ 7 · pixelport

    /** Chunky pixel arcade UI: selectable state, slider, tooltips, actions, and a hard-edged dialog. */
    private static void pixelport(UiContext ui) {
        UiCanvas g = ui.canvas();
        float w = ui.width(), h = ui.height();
        int bg = 0xFF172033, mint = 0xFF4DCC82, yellow = 0xFFFFD45A, pink = 0xFFFF7B8E, ink = 0xFF101522;
        g.fill(bg, 0, 0, w, h);
        g.grid(0xFF263553, .65F, 12, 0, 0, w, h);
        float x = 34, y = 30 + (1 - ui.motion()) * 7;
        g.fill(mint, x, y, 11, 11); g.fill(yellow, x + 14, y, 11, 11); g.fill(pink, x + 28, y, 11, 11);
        g.text("PIXELPORT", x + 52, y - 5, 24, 0xFFF1F5FF, Font.BOLD, UiCanvas.MONO);
        g.text("LOCAL ARCADE CATALOG", x + 52, y + 24, 8, 0xFF9CB1D6, Font.PLAIN, UiCanvas.MONO);
        float cabinetX = x, cabinetY = y + 65, cabinetW = Math.min(278, w * .42F), cabinetH = h - cabinetY - 76;
        g.fill(ink, cabinetX + 8, cabinetY + 8, cabinetW, cabinetH);
        g.fill(0xFF273C61, cabinetX, cabinetY, cabinetW, cabinetH);
        g.strokeRoundRect(mint, 2, cabinetX, cabinetY, cabinetW, cabinetH, 0);
        g.fill(0xFF0B101A, cabinetX + 18, cabinetY + 22, cabinetW - 36, Math.min(154, cabinetH * .48F));
        g.grid(0xFF14253C, .5F, 10, cabinetX + 18, cabinetY + 22, cabinetW - 36, Math.min(154, cabinetH * .48F));
        String mode = ui.stringValue("pixel-mode", "CO-OP");
        ui.setValue("pixel-mode", mode);
        int modeColor = mode.equals("VERSUS") ? pink : mode.equals("SOLO") ? yellow : mint;
        g.text(mode + " READY", cabinetX + 34, cabinetY + 48, 10, modeColor, Font.BOLD, UiCanvas.MONO);
        g.text("01:42", cabinetX + 34, cabinetY + 78, 31, yellow, Font.BOLD, UiCanvas.MONO);
        String[] modes = {"CO-OP", "SOLO", "VERSUS"};
        for (int i = 0; i < modes.length; i++) {
            float mx = cabinetX + 20 + i * ((cabinetW - 40) / 3F), my = cabinetY + cabinetH - 74;
            boolean on = mode.equals(modes[i]);
            g.fill(on ? mint : 0xFF16223A, mx, my, (cabinetW - 48) / 3F, 28);
            g.text(modes[i], mx + 7, my + 9, 7, on ? ink : 0xFFB7C8E3, Font.BOLD, UiCanvas.MONO);
            ui.selectable("pixel-mode-" + i, "pixel-mode", modes[i], mx, my, (cabinetW - 48) / 3F, 28);
        }
        float panelX = cabinetX + cabinetW + 28, panelW = w - panelX - 34;
        g.text("GAME SETTINGS", panelX, cabinetY, 10, mint, Font.BOLD, UiCanvas.MONO);
        g.text("Cabinet volume", panelX, cabinetY + 36, 9, 0xFFCAD7ED, Font.PLAIN, UiCanvas.MONO);
        ui.slider("pixel-volume", "pixel-volume", panelX, cabinetY + 54, Math.min(190, panelW - 12), .72);
        ui.tooltip("pixel-volume", "Drag to change the cabinet volume.");
        g.text("Difficulty", panelX, cabinetY + 94, 9, 0xFFCAD7ED, Font.PLAIN, UiCanvas.MONO);
        ui.dropdown("pixel-difficulty", "pixel-difficulty", panelX, cabinetY + 110, Math.min(180, panelW - 12), 28, "Normal",
            java.util.List.of(new UiSelectOption("Easy", "Easy"), new UiSelectOption("Normal", "Normal"),
                new UiSelectOption("Hard", "Hard")));
        ui.button("pixel-launch", "INSERT COIN", "pixel-launch", panelX, cabinetY + 164, Math.min(172, panelW - 12), 34,
            UiContext.ButtonStyle.PRIMARY, UiContext.Shape.SQUARE);
        ui.dialogButton("pixel-buy", "BUY PACK", panelX, cabinetY + 210, Math.min(172, panelW - 12), 30,
            UiContext.ButtonStyle.SECONDARY, UiContext.Shape.SQUARE,
            new UiDialog("Buy pixel pack?", "This is a pretend purchase inside the showcase.", "Buy", "pixel-buy", "Back", "pixel-buy",
                UiDialogStyle.builder().backdrop(0xBB07101D).surface(0xFF182641).border(0xFF4DCC82)
                    .titleColor(0xFFF1F5FF).messageColor(0xFFB7C8E3).cancelBackground(0xFF263A5F).cancelBorder(0xFF4A628D)
                    .cancelText(0xFFF1F5FF).confirmBackground(0xFF4DCC82).confirmText(0xFF101522).radius(0).build()));
        g.text("mode: " + mode + " / " + ui.stringValue("pixel-difficulty", "Normal"), panelX, cabinetY + 270, 8, modeColor, Font.PLAIN, UiCanvas.MONO);
        switcher(ui, mint, 0xFFB7C8E3, bg, UiCanvas.MONO, 6);
    }

    // ================================================================ 8 · field notes

    /** Paper-and-ink field journal: editable note, filter, scrolling log, and save/discard actions. */
    private static void fieldnotes(UiContext ui) {
        UiCanvas g = ui.canvas();
        float w = ui.width(), h = ui.height();
        int paper = 0xFFF3F0E3, green = 0xFF637A43, ink = 0xFF303425, muted = 0xFF7E806F;
        g.fill(paper, 0, 0, w, h); g.grain(0x0C2E2513, .12F, 0, 0, w, h);
        float x = Math.max(34, w * .07F), y = 28 + (1 - ui.motion()) * 6;
        g.trackedText("FIELD NOTES / 26.2", x, y, 8, green, Font.BOLD, UiCanvas.SANS, 2F);
        g.text("A small place to notice things.", x, y + 24, 25, ink, Font.PLAIN, UiCanvas.SERIF);
        float leftW = Math.min(290, w * .43F), pageY = y + 65, pageH = h - pageY - 74;
        g.roundRect(0xFFFFFDF5, x, pageY, leftW, pageH, 3); g.strokeRoundRect(0xFFD5D1C0, .8F, x, pageY, leftW, pageH, 3);
        g.text("NEW OBSERVATION", x + 18, pageY + 18, 8, green, Font.BOLD, UiCanvas.SANS);
        ui.textField("field-title", "field-title", "Title this note", x + 18, pageY + 38, leftW - 36, "");
        ui.dropdown("field-weather", "field-weather", x + 18, pageY + 78, leftW - 36, 28, "Clear",
            java.util.List.of(new UiSelectOption("Clear", "Clear"), new UiSelectOption("Rain", "Rain"), new UiSelectOption("Wind", "Wind")));
        g.line(0xFFD9D5C2, .8F, x + 18, pageY + 124, x + leftW - 18, pageY + 124);
        g.text("The creek sounded different after rain.", x + 18, pageY + 144, 9, ink, Font.PLAIN, UiCanvas.SERIF);
        g.text("Add a thought with the title field above.", x + 18, pageY + 164, 8, muted, Font.ITALIC, UiCanvas.SERIF);
        ui.button("field-save", "Save note", "field-save", x + 18, pageY + pageH - 50, 92, 30,
            UiContext.ButtonStyle.PRIMARY, UiContext.Shape.ROUNDED);
        ui.dialogButton("field-discard", "Discard", x + 120, pageY + pageH - 50, 82, 30,
            UiContext.ButtonStyle.GHOST, UiContext.Shape.ROUNDED,
            new UiDialog("Discard this draft?", "The field note will not be saved.", "Discard", "field-discard", "Keep", "field-discard",
                UiDialogStyle.builder().backdrop(0x884A5032).surface(0xFFFFFDF5).border(0xFF9BA77C)
                    .titleColor(0xFF303425).messageColor(0xFF737562).cancelBackground(0xFFF0EEDF).cancelBorder(0xFFC8C6B2)
                    .cancelText(0xFF303425).confirmBackground(0xFF637A43).confirmText(0xFFFFFFFF).radius(3).build()));
        float logX = x + leftW + 28, logW = w - logX - x;
        g.text("RECENT TRAIL", logX, pageY, 8, green, Font.BOLD, UiCanvas.SANS);
        UiRect viewport = new UiRect(logX, pageY + 20, logW, pageH - 20);
        ui.scroll("field-log", viewport, 460, page -> {
            UiCanvas c = page.canvas();
            String[] notes = {"09:14  cedar waxwing near the east fence", "08:47  fog stayed low over the water",
                "Yesterday  moss brightened after the storm", "Yesterday  a new trail marker at the bend",
                "Monday  three crows sharing one branch", "Sunday  first cold wind from the north"};
            for (int i = 0; i < notes.length; i++) {
                float ny = pageY + 20 + i * 68;
                c.roundRect(0xFFFFFDF5, logX, ny, logW, 54, 3); c.strokeRoundRect(0xFFD5D1C0, .7F, logX, ny, logW, 54, 3);
                c.text(notes[i], logX + 14, ny + 13, 8.5F, ink, Font.PLAIN, UiCanvas.SERIF);
                c.text("weather: " + ui.stringValue("field-weather", "Clear"), logX + 14, ny + 31, 7.5F, muted, Font.PLAIN, UiCanvas.SANS);
            }
        });
        switcher(ui, green, muted, paper, UiCanvas.SANS, 7);
    }

    // ================================================================ 9 · orbit

    /** Dense orbital console: toggles, sliders, selection, confirmation, and live numeric state. */
    private static void orbit(UiContext ui) {
        UiCanvas g = ui.canvas();
        float w = ui.width(), h = ui.height();
        int bg = 0xFF061018, cyan = 0xFF6CC7E8, pale = 0xFFD8F4FF, dim = 0xFF6D9CAC;
        g.fill(bg, 0, 0, w, h); g.grid(0xFF102834, .5F, 18, 0, 0, w, h); g.vignette(0x99000000, 0, 0, w, h);
        float x = 34, y = 28 + (1 - ui.motion()) * 6;
        g.trackedText("ORBITAL CONTROL / LINK STABLE", x, y, 8, cyan, Font.BOLD, UiCanvas.MONO, 2F);
        g.text("ORBIT", x, y + 23, 31, pale, Font.BOLD, UiCanvas.MONO);
        g.text("local guidance console", x + 118, y + 36, 8, dim, Font.PLAIN, UiCanvas.MONO);
        float railY = y + 78, railW = Math.min(240, w * .34F);
        g.strokeRoundRect(0xFF28566B, 1, x, railY, railW, h - railY - 75, 3);
        String[] systems = {"NAVIGATION", "COMMS", "THERMAL"};
        for (int i = 0; i < systems.length; i++) {
            float sy = railY + 24 + i * 44;
            boolean on = ui.stringValue("orbit-system", "NAVIGATION").equals(systems[i]);
            if (on) g.fill(0xFF123242, x + 12, sy - 7, railW - 24, 30);
            g.text(systems[i], x + 24, sy + 1, 9, on ? cyan : dim, Font.BOLD, UiCanvas.MONO);
            ui.selectable("orbit-system-" + i, "orbit-system", systems[i], x + 12, sy - 7, railW - 24, 30);
        }
        float mainX = x + railW + 28, mainW = w - mainX - 34;
        g.strokeRoundRect(0xFF28566B, 1, mainX, railY, mainW, h - railY - 75, 3);
        String system = ui.stringValue("orbit-system", "NAVIGATION");
        ui.setValue("orbit-system", system);
        String readout = switch (system) {
            case "COMMS" -> "UPLINK ARRAY";
            case "THERMAL" -> "THERMAL BIAS";
            default -> "GUIDANCE VECTOR";
        };
        String allocation = switch (system) {
            case "COMMS" -> "Signal allocation";
            case "THERMAL" -> "Cooling allocation";
            default -> "Thrust allocation";
        };
        g.text(readout, mainX + 20, railY + 18, 10, pale, Font.BOLD, UiCanvas.MONO);
        g.text(allocation, mainX + 20, railY + 54, 8, dim, Font.PLAIN, UiCanvas.MONO);
        ui.slider("orbit-thrust", "orbit-thrust", mainX + 20, railY + 72, Math.min(250, mainW - 44), .58);
        g.text("Telemetry uplink", mainX + 20, railY + 116, 8, dim, Font.PLAIN, UiCanvas.MONO);
        ui.toggle("orbit-uplink", "orbit-uplink", mainX + 166, railY + 108, true);
        g.text("Auto-correct", mainX + 20, railY + 156, 8, dim, Font.PLAIN, UiCanvas.MONO);
        ui.toggle("orbit-correct", "orbit-correct", mainX + 166, railY + 148, false);
        double thrust = ui.numberValue("orbit-thrust", .58) * 100;
        g.text(String.format(java.util.Locale.ROOT, "THRUST %05.1f%%", thrust), mainX + 20, railY + 205, 18, cyan, Font.BOLD, UiCanvas.MONO);
        ui.button("orbit-calibrate", "CALIBRATE", "orbit-calibrate", mainX + 20, railY + 242, 116, 30,
            UiContext.ButtonStyle.PRIMARY, UiContext.Shape.SQUARE);
        ui.dialogButton("orbit-abort", "HOLD", mainX + 146, railY + 242, 76, 30, UiContext.ButtonStyle.DANGER,
            UiContext.Shape.SQUARE, new UiDialog("Hold guidance sequence?", "The virtual craft will keep its current vector.",
                "Hold", "orbit-abort", "Continue", "orbit-abort", UiDialogStyle.builder()
                    .backdrop(0xBB02080D).surface(0xFF0B1D29).border(0xFF5BAFCB).titleColor(0xFFD8F4FF)
                    .messageColor(0xFF8AB4C3).cancelBackground(0xFF153344).cancelBorder(0xFF35667B)
                    .cancelText(0xFFD8F4FF).confirmBackground(0xFFB9414F).confirmText(0xFFFFFFFF).radius(2).build()));
        ui.tooltip("orbit-thrust", "This slider uses normal Pane state and input mapping.");
        switcher(ui, cyan, dim, bg, UiCanvas.MONO, 8);
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
        float segment = w / STYLES.length;
        for (int i = 0; i < STYLES.length; i++) {
            String id = "style-" + STYLES[i][0];
            String label = STYLES[i][1];
            float size = Math.max(5.2F, Math.min(7.5F, segment / Math.max(6F, label.length() * 1.25F)));
            float labelW = g.trackedTextWidth(label, size, Font.BOLD, family, 1F);
            float cursor = segment * i + (segment - labelW) / 2F;
            boolean on = i == active;
            boolean hover = ui.hovered(id);
            if (on) g.fill(accent, segment * i + 5, barY + 20, Math.max(1, segment - 10), 2);
            g.trackedText(label, cursor, barY + 4, size, on ? accent : hover ? muted : UiCanvas.fade(muted, .6F),
                Font.BOLD, family, 1F);
            ui.pageLink(id, STYLES[i][0], segment * i, barY - 2, segment, 26);
        }
        g.trackedText("1-9 · ESC", w - 70, barY - 14, 6, UiCanvas.fade(muted, .5F), Font.PLAIN, family, 1F);
    }
}
