package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/** PaneOS artwork drawn from geometry: wallpapers, app icons and line symbols. No bitmaps. */
final class PaneOsArt {
    static final String FONT = pickFont();
    static final String MONO = pickMono();
    static final int RED = 0xFFFF453A, ORANGE = 0xFFFF9F0A, YELLOW = 0xFFFFD60A, GREEN = 0xFF30D158,
        MINT = 0xFF63E6E2, TEAL = 0xFF40C8E0, BLUE = 0xFF0A84FF, INDIGO = 0xFF5E5CE6, PURPLE = 0xFFBF5AF2,
        GRAPHITE = 0xFF8E8E93;
    static final int[] ACCENTS = {BLUE, PURPLE, RED, ORANGE, YELLOW, GREEN, GRAPHITE};
    static final String[] ACCENT_NAMES = {"Blue", "Purple", "Red", "Orange", "Yellow", "Green", "Graphite"};
    static final String[] WALLPAPERS = {"Tahoe", "Sequoia", "Ivory"};
    /** Whether the menu bar sits on a light area of each wallpaper. */
    static final boolean[] LIGHT_TOP = {false, false, true};

    private PaneOsArt() { }

    private static String pickFont() {
        var installed = java.util.Set.of(java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String family : new String[] {"SF Pro Text", "SF Pro", ".AppleSystemUIFont", "Helvetica Neue", "Inter", "Segoe UI Variable Text", "Segoe UI"})
            if (installed.contains(family)) return family;
        return ExampleFonts.FAMILY;
    }

    private static String pickMono() {
        var installed = java.util.Set.of(java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String family : new String[] {"SF Mono", "Menlo", "Cascadia Mono", "Consolas"})
            if (installed.contains(family)) return family;
        return UiCanvas.MONO;
    }

    /** Appearance resolved once per paint from the user's settings. */
    record Look(boolean dark, int accent, int text, int secondary, int tertiary, int window, int content,
                int separator, int control, int hover, int sidebarTint, UiGlass glass, UiGlass clear) {
        static Look of(boolean dark, int accentIndex, double refraction, double frost) {
            int accent = ACCENTS[Math.floorMod(accentIndex, ACCENTS.length)];
            float bend = (float) refraction, blur = (float) frost;
            UiGlass glass = new UiGlass(bend, 26, blur, dark ? 0x40202024 : 0x40FFFFFF, .5F, dark ? .55F : .8F, 0x30000000);
            UiGlass clear = new UiGlass(bend * .7F, 16, Math.max(.8F, blur * .25F), dark ? 0x28000000 : 0x22FFFFFF, .45F, .85F, 0x24000000);
            return dark
                ? new Look(true, accent, 0xFFF5F5F7, 0xB3EBEBF5, 0x66EBEBF5, 0xF0232326, 0xFF1C1C1E, 0x24FFFFFF,
                    0x1FFFFFFF, 0x14FFFFFF, 0x9E2A2A2E, glass, clear)
                : new Look(false, accent, 0xFF1D1D1F, 0xA63C3C43, 0x553C3C43, 0xF5FBFBFD, 0xFFFFFFFF, 0x1A000000,
                    0x0F000000, 0x0A000000, 0x9EF2F2F5, glass, clear);
        }
    }

    // ------------------------------------------------------------------ wallpapers

    static void wallpaper(UiCanvas g, int kind, boolean dark, float w, float h) {
        art(g, kind, w, h);
        finish(g, kind, dark, w, h);
    }

    /** The wallpaper with its painting kept in {@code art}, so switching appearance only re-tints it. */
    static void wallpaper(UiCanvas g, UiLayer art, int kind, boolean dark, float w, float h) {
        g.cachedLayer(art, List.of(Math.round(w), Math.round(h), kind), new UiRect(0, 0, w, h), c -> art(c, kind, w, h));
        finish(g, kind, dark, w, h);
    }

    private static void art(UiCanvas g, int kind, float w, float h) {
        switch (kind) {
            case 1 -> sequoia(g, w, h);
            case 2 -> ivory(g, w, h);
            default -> tahoe(g, w, h);
        }
    }

    private static void finish(UiCanvas g, int kind, boolean dark, float w, float h) {
        if (dark) g.fill(kind == 2 ? 0x8A101114 : 0x6E000814, 0, 0, w, h);
        g.grain(0x09FFFFFF, .3F, 0, 0, w, h);
    }

    /** A deep lake-blue swell with one bright, glassy crest sweeping across the frame. */
    private static void tahoe(UiCanvas g, float w, float h) {
        g.gradientRect(0xFFBFDDF8, 0xFF2E6FD8, 90, 0, 0, w, h * .62F, 0);
        g.gradientRect(0xFF2E6FD8, 0xFF06225F, 90, 0, h * .62F - 1, w, h * .38F + 1, 0);
        g.radialRect(0xCCFFFFFF, 0x00FFFFFF, w * .18F, -h * .05F, w * .5F, 0, 0, w, h, 0);
        // Back swell, then the main wave with its crest, then the trough that folds beneath it.
        g.path().moveTo(0, h * .58F).curveTo(w * .28F, h * .40F, w * .55F, h * .50F, w * .78F, h * .34F)
            .curveTo(w * .88F, h * .27F, w * .95F, h * .22F, w, h * .2F).lineTo(w, h).lineTo(0, h).close()
            .fillGradient(0xFF5C9DFF, 0xFF0A2C86, 90);
        g.path().moveTo(0, h * .74F).curveTo(w * .22F, h * .60F, w * .46F, h * .56F, w * .66F, h * .62F)
            .curveTo(w * .82F, h * .67F, w * .92F, h * .58F, w, h * .5F).lineTo(w, h).lineTo(0, h).close()
            .fillGradient(0xFF1D5FE0, 0xFF041B52, 90);
        g.path().moveTo(0, h * .9F).curveTo(w * .3F, h * .78F, w * .6F, h * .86F, w, h * .76F).lineTo(w, h).lineTo(0, h).close()
            .fillGradient(0xCC0B3A9C, 0xFF020F33, 90);
        g.backdropBlur(16, 0, 0, w, h);
        // Gloss just under the main crest gives the swell its rounded, glassy volume.
        g.path().moveTo(0, h * .58F).curveTo(w * .28F, h * .40F, w * .55F, h * .50F, w * .78F, h * .34F)
            .curveTo(w * .88F, h * .27F, w * .95F, h * .22F, w, h * .2F).lineTo(w, h * .34F)
            .curveTo(w * .9F, h * .38F, w * .8F, h * .46F, w * .7F, h * .5F).curveTo(w * .5F, h * .6F, w * .25F, h * .52F, 0, h * .7F)
            .close().fillGradient(0x5CFFFFFF, 0x00FFFFFF, 100);
        crest(g, w, h, 0, h * .58F, w * .28F, h * .40F, w * .55F, h * .50F, w * .78F, h * .34F, w, h * .2F, 0xFFFFFFFF);
        crest(g, w, h, 0, h * .74F, w * .22F, h * .60F, w * .46F, h * .56F, w * .66F, h * .62F, w, h * .5F, 0xFFAEDBFF);
    }

    /** Glow then a fine bright line along a wave edge: two cubic spans meeting at (mx, my). */
    private static void crest(UiCanvas g, float w, float h, float ax, float ay, float c1x, float c1y, float c2x, float c2y,
                              float mx, float my, float ex, float ey, int color) {
        float[] widths = {34, 18, 8, 3, 1.2F};
        int[] alphas = {14, 26, 52, 120, 230};
        for (int i = 0; i < widths.length; i++) {
            var path = g.path().moveTo(ax, ay).curveTo(c1x, c1y, c2x, c2y, mx, my);
            path.curveTo(mx + (ex - mx) * .45F, my + (ey - my) * .5F, mx + (ex - mx) * .75F, my + (ey - my) * .9F, ex, ey);
            path.stroke(UiCanvas.alpha(color, alphas[i]), widths[i]);
        }
    }

    /** Warm and cool light rays fanning up from below the frame. */
    private static void sequoia(UiCanvas g, float w, float h) {
        g.gradientRect(0xFF0B1638, 0xFF050913, 90, 0, 0, w, h, 0);
        float ox = w * .16F, oy = h * 1.18F, reach = Math.max(w, h) * 1.6F;
        int[][] rays = {{0xFFFF7A2F, 0x00FF7A2F}, {0xFF3B6CFF, 0x003B6CFF}, {0xFFFFB04A, 0x00FFB04A},
            {0xFF2848D8, 0x002848D8}, {0xFFFF8C42, 0x00FF8C42}, {0xFF4E8BFF, 0x004E8BFF}, {0xFFFFC266, 0x00FFC266}};
        for (int i = 0; i < rays.length; i++) {
            double a0 = Math.toRadians(-88 + i * 9.5), a1 = Math.toRadians(-88 + i * 9.5 + 6.5);
            var ray = g.path().moveTo(ox, oy)
                .lineTo(ox + (float) Math.cos(a0) * reach, oy + (float) Math.sin(a0) * reach)
                .lineTo(ox + (float) Math.cos(a1) * reach, oy + (float) Math.sin(a1) * reach).close();
            ray.fillGradient(UiCanvas.alpha(rays[i][0], 210), rays[i][1], 300);
        }
        g.radialRect(0x66FFB45C, 0x00FFB45C, ox, h, w * .45F, 0, 0, w, h, 0);
        g.backdropBlur(20, 0, 0, w, h);
        g.radialRect(0x33FFFFFF, 0x00FFFFFF, ox, h * 1.02F, w * .2F, 0, 0, w, h, 0);
    }

    /** Pale ivory with soft silver folds. */
    private static void ivory(UiCanvas g, float w, float h) {
        g.gradientRect(0xFFF7F3EA, 0xFFDAD8D3, 100, 0, 0, w, h, 0);
        for (int i = 0; i < 5; i++) {
            float base = h * (.25F + i * .17F), lift = h * (.12F + i * .02F);
            g.path().moveTo(0, base).curveTo(w * .3F, base - lift, w * .62F, base + lift * .6F, w, base - lift * .8F)
                .lineTo(w, h).lineTo(0, h).close().fillGradient(UiCanvas.alpha(0xFFFFFFFF, 120 - i * 12), UiCanvas.alpha(0xFFB9BCC4, 70 + i * 14), 90);
        }
        g.backdropBlur(18, 0, 0, w, h);
        for (int i = 0; i < 5; i++) {
            float base = h * (.25F + i * .17F), lift = h * (.12F + i * .02F);
            g.path().moveTo(0, base).curveTo(w * .3F, base - lift, w * .62F, base + lift * .6F, w, base - lift * .8F)
                .stroke(0x8CFFFFFF, 1.2F);
        }
    }

    // ------------------------------------------------------------------ icons

    /** The continuous-corner app tile: a superellipse, which reads softer than a rounded rect. */
    static UiPath squircle(UiCanvas g, float x, float y, float size) {
        var path = g.path();
        float r = size / 2, cx = x + r, cy = y + r;
        for (int i = 0; i <= 64; i++) {
            double t = i / 64.0 * Math.PI * 2, c = Math.cos(t), s = Math.sin(t);
            float px = cx + (float) (Math.signum(c) * Math.pow(Math.abs(c), .4) * r);
            float py = cy + (float) (Math.signum(s) * Math.pow(Math.abs(s), .4) * r);
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        return path.close();
    }

    static void icon(UiCanvas g, String app, float x, float y, float s) {
        float cx = x + s / 2, cy = y + s / 2;
        g.shadow(0x40000000, s * .08F, 0, s * .04F, x + s * .06F, y + s * .08F, s * .88F, s * .86F, s * .24F);
        switch (app) {
            case "files" -> {
                squircle(g, x, y, s).fillGradient(0xFF6FD0FF, 0xFF1E8BF0, 90);
                g.path().moveTo(cx, y + s * .02F).curveTo(cx - s * .08F, cy - s * .1F, cx + s * .06F, cy + s * .12F, cx - s * .02F, y + s * .98F)
                    .stroke(0x55FFFFFF, s * .02F);
                g.withClip(squircle(g, x, y, s), () -> g.fill(0x22FFFFFF, cx, y, s / 2, s));
                g.circle(0xFF0B2F66, x + s * .32F, y + s * .4F, s * .045F);
                g.circle(0xFF0B2F66, x + s * .68F, y + s * .4F, s * .045F);
                g.path().moveTo(x + s * .26F, y + s * .66F).curveTo(cx, y + s * .82F, cx, y + s * .82F, x + s * .74F, y + s * .66F)
                    .stroke(0xFF0B2F66, s * .035F);
            }
            case "launch" -> {
                squircle(g, x, y, s).fillGradient(0xFFEDEDF2, 0xFFC7C7CF, 90);
                int[] dots = {RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, INDIGO, PURPLE, GRAPHITE};
                for (int i = 0; i < 9; i++) g.roundRect(dots[i], x + s * (.2F + i % 3 * .22F), y + s * (.2F + i / 3 * .22F), s * .16F, s * .16F, s * .05F);
            }
            case "compass" -> {
                squircle(g, x, y, s).fillGradient(0xFFFFFFFF, 0xFFE3E6EC, 90);
                g.circle(0xFF1A8CFF, cx, cy, s * .38F);
                g.radialRect(0xFF5CC2FF, 0xFF0A6CE0, cx, cy - s * .1F, s * .42F, cx - s * .38F, cy - s * .38F, s * .76F, s * .76F, s * .38F);
                for (int i = 0; i < 24; i++) {
                    double a = Math.toRadians(i * 15);
                    float inner = i % 6 == 0 ? .29F : .33F;
                    g.line(0xCCFFFFFF, s * .012F, cx + (float) Math.cos(a) * s * inner, cy + (float) Math.sin(a) * s * inner,
                        cx + (float) Math.cos(a) * s * .36F, cy + (float) Math.sin(a) * s * .36F);
                }
                g.polygon(0xFFFF3B30, cx + s * .2F, cy - s * .2F, cx - s * .04F, cy - s * .04F, cx + s * .04F, cy + s * .04F);
                g.polygon(0xFFFFFFFF, cx - s * .2F, cy + s * .2F, cx - s * .04F, cy - s * .04F, cx + s * .04F, cy + s * .04F);
            }
            case "messages" -> {
                squircle(g, x, y, s).fillGradient(0xFF6CF08A, 0xFF16B83E, 90);
                bubble(g, cx, cy - s * .02F, s * .58F, s * .46F, 0xFFFFFFFF);
            }
            case "mail" -> {
                squircle(g, x, y, s).fillGradient(0xFF5DB8FF, 0xFF1162E8, 90);
                float ex = x + s * .2F, ey = y + s * .3F, ew = s * .6F, eh = s * .42F;
                g.roundRect(0xFFFFFFFF, ex, ey, ew, eh, s * .04F);
                g.path().moveTo(ex + s * .02F, ey + s * .03F).lineTo(cx, ey + eh * .58F).lineTo(ex + ew - s * .02F, ey + s * .03F)
                    .stroke(0xFF5AA7F5, s * .03F);
            }
            case "maps" -> {
                g.withClip(squircle(g, x, y, s), () -> {
                    g.gradientRect(0xFFD8F0C9, 0xFFB8E3A3, 90, x, y, s, s, 0);
                    g.path().moveTo(x, y + s * .7F).curveTo(x + s * .3F, y + s * .55F, x + s * .5F, y + s * .9F, x + s, y + s * .75F)
                        .lineTo(x + s, y + s).lineTo(x, y + s).close().fill(0xFF7CC4FF);
                    g.line(0xFFFFFFFF, s * .09F, x - s * .1F, y + s * .28F, x + s * 1.1F, y + s * .52F);
                    g.line(0xFFFFD36B, s * .05F, x + s * .55F, y - s * .1F, x + s * .38F, y + s * 1.1F);
                    g.line(0xFFFFFFFF, s * .018F, x + s * .55F, y - s * .1F, x + s * .38F, y + s * 1.1F);
                });
                pin(g, x + s * .62F, y + s * .28F, s * .34F, RED);
            }
            case "photos" -> {
                squircle(g, x, y, s).fill(0xFFFFFFFF);
                int[] petals = {ORANGE, YELLOW, 0xFFA6E04C, GREEN, TEAL, BLUE, INDIGO, PURPLE};
                for (int i = 0; i < petals.length; i++) {
                    double a = Math.toRadians(i * 45 - 90);
                    float px = cx + (float) Math.cos(a) * s * .17F, py = cy + (float) Math.sin(a) * s * .17F;
                    int c = petals[i];
                    g.withOpacity(.82F, () -> g.roundRect(c, px - s * .1F, py - s * .16F, s * .2F, s * .32F, s * .1F));
                }
            }
            case "calendar" -> {
                squircle(g, x, y, s).fill(0xFFFFFFFF);
                LocalDate today = LocalDate.now();
                g.centeredText(today.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ENGLISH),
                    cx, y + s * .22F, s * .16F, 0xFFFF3B30, Font.BOLD, FONT);
                g.centeredText(Integer.toString(today.getDayOfMonth()), cx, y + s * .6F, s * .5F, 0xFF1D1D1F, Font.PLAIN, FONT);
            }
            case "notes" -> {
                squircle(g, x, y, s).fill(0xFFFFFFFF);
                g.withClip(squircle(g, x, y, s), () -> {
                    g.gradientRect(0xFFFFE27A, 0xFFFFCC2E, 90, x, y, s, s * .27F, 0);
                    g.dots(0x33000000, s * .012F, s * .05F, x, y + s * .27F, s, s * .02F);
                    for (int i = 0; i < 4; i++) g.line(0xFFD6D6DB, s * .015F, x + s * .14F, y + s * (.45F + i * .13F), x + s * .86F, y + s * (.45F + i * .13F));
                });
            }
            case "music" -> {
                squircle(g, x, y, s).fillGradient(0xFFFF6A5C, 0xFFF0263B, 90);
                note(g, cx, cy, s * .52F, 0xFFFFFFFF);
            }
            case "calculator" -> {
                squircle(g, x, y, s).fillGradient(0xFF4A4A4E, 0xFF1F1F22, 90);
                for (int i = 0; i < 4; i++) {
                    for (int j = 0; j < 4; j++) {
                        int c = j == 3 ? ORANGE : i == 0 ? 0xFFA5A5AA : 0xFF5E5E63;
                        g.circle(c, x + s * (.24F + j * .174F), y + s * (.26F + i * .165F), s * .066F);
                    }
                }
            }
            case "terminal" -> {
                squircle(g, x, y, s).fillGradient(0xFF3A3A3E, 0xFF111113, 90);
                g.roundRect(0xFF0A0A0B, x + s * .12F, y + s * .14F, s * .76F, s * .7F, s * .06F);
                g.path().moveTo(x + s * .24F, y + s * .34F).lineTo(x + s * .36F, y + s * .44F).lineTo(x + s * .24F, y + s * .54F)
                    .stroke(0xFFF2F2F7, s * .045F);
                g.line(0xFFF2F2F7, s * .045F, x + s * .42F, y + s * .56F, x + s * .6F, y + s * .56F);
            }
            case "settings" -> {
                squircle(g, x, y, s).fillGradient(0xFFD5D5DA, 0xFF8E8E93, 90);
                gear(g, cx, cy, s * .36F, 0xFF4A4A4E, 0xFFDCDCE0);
            }
            case "wayfarer" -> {
                g.withClip(squircle(g, x, y, s), () -> {
                    g.gradientRect(0xFF7EC8FF, 0xFFCFEBFF, 90, x, y, s, s, 0);
                    g.circle(0xFFFFE29A, x + s * .72F, y + s * .3F, s * .12F);
                    g.polygon(0xFF4C7A3A, x - s * .05F, y + s * .78F, x + s * .32F, y + s * .42F, x + s * .62F, y + s * .78F);
                    g.polygon(0xFF3A6630, x + s * .35F, y + s * .8F, x + s * .7F, y + s * .5F, x + s * 1.05F, y + s * .8F);
                    g.fill(0xFF6BAE45, x, y + s * .72F, s, s * .1F);
                    g.fill(0xFF8A5A35, x, y + s * .82F, s, s * .2F);
                    g.dots(0x22000000, s * .02F, s * .08F, x, y + s * .84F, s, s * .16F);
                });
            }
            case "downloads" -> {
                squircle(g, x, y, s).fillGradient(0xFF79C6FF, 0xFF2A86F0, 90);
                g.circle(0x33FFFFFF, cx, cy, s * .3F);
                g.line(0xFFFFFFFF, s * .06F, cx, cy - s * .18F, cx, cy + s * .12F);
                g.path().moveTo(cx - s * .13F, cy).lineTo(cx, cy + s * .14F).lineTo(cx + s * .13F, cy).stroke(0xFFFFFFFF, s * .06F);
            }
            case "trash" -> {
                float bx = x + s * .2F, bw = s * .6F;
                g.withOpacity(.9F, () -> {
                    g.path().moveTo(bx, y + s * .16F).lineTo(bx + bw, y + s * .16F).lineTo(bx + bw - s * .05F, y + s * .94F)
                        .lineTo(bx + s * .05F, y + s * .94F).close().fillGradient(0xEFF4F6F9, 0xCFC9CED6, 90);
                });
                for (int i = 0; i < 5; i++) {
                    float lx = bx + s * (.1F + i * .1F);
                    g.line(0x33000000, s * .012F, lx, y + s * .24F, lx + (i - 2) * s * .01F, y + s * .88F);
                }
                g.roundRect(0xFFE5E7EB, bx - s * .03F, y + s * .1F, bw + s * .06F, s * .08F, s * .03F);
            }
            default -> squircle(g, x, y, s).fill(GRAPHITE);
        }
        // Light catches the top rim of every tile.
        squircle(g, x + s * .004F, y + s * .004F, s * .992F).stroke(0x26FFFFFF, Math.max(.6F, s * .012F));
    }

    static void bubble(UiCanvas g, float cx, float cy, float w, float h, int color) {
        g.roundRect(color, cx - w / 2, cy - h / 2, w, h, h / 2);
        g.polygon(color, cx - w * .3F, cy + h * .3F, cx - w * .42F, cy + h * .58F, cx - w * .1F, cy + h * .44F);
    }

    static void note(UiCanvas g, float cx, float cy, float size, int color) {
        float stem = size * .07F;
        g.circle(color, cx - size * .26F, cy + size * .28F, size * .14F);
        g.circle(color, cx + size * .2F, cy + size * .2F, size * .14F);
        g.fill(color, cx - size * .14F, cy - size * .36F, stem, size * .64F);
        g.fill(color, cx + size * .32F, cy - size * .44F, stem, size * .64F);
        g.polygon(color, cx - size * .14F, cy - size * .36F, cx + size * .32F + stem, cy - size * .44F,
            cx + size * .32F + stem, cy - size * .3F, cx - size * .14F, cy - size * .22F);
    }

    static void pin(UiCanvas g, float cx, float top, float size, int color) {
        float r = size * .32F;
        g.path().moveTo(cx, top + size).curveTo(cx - r * 1.3F, top + r * 1.6F, cx - r, top + r * .2F, cx, top + r * .02F)
            .curveTo(cx + r, top + r * .2F, cx + r * 1.3F, top + r * 1.6F, cx, top + size).close().fill(color);
        g.circle(0xFFFFFFFF, cx, top + r * 1.05F, r * .4F);
    }

    static void gear(UiCanvas g, float cx, float cy, float r, int color, int hole) {
        var path = g.path();
        for (int i = 0; i <= 96; i++) {
            double a = i / 96.0 * Math.PI * 2;
            double tooth = (Math.cos(a * 12) > .2) ? 1 : .82;
            float px = cx + (float) (Math.cos(a) * r * tooth), py = cy + (float) (Math.sin(a) * r * tooth);
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close().fill(color);
        g.circle(hole, cx, cy, r * .42F);
        g.strokeCircle(color, r * .08F, cx, cy, r * .24F);
    }

    // ------------------------------------------------------------------ symbols

    /** Line symbols in the spirit of a system symbol set, centred on (cx, cy) within {@code s}. */
    static void symbol(UiCanvas g, String name, float cx, float cy, float s, int color) {
        float w = Math.max(1, s * .1F);
        switch (name) {
            case "wifi" -> {
                for (int i = 0; i < 3; i++) {
                    float r = s * (.18F + i * .17F);
                    g.path().arc(cx, cy + s * .3F, r, 225, 90).stroke(color, w);
                }
                g.circle(color, cx, cy + s * .3F, w * .9F);
            }
            case "search" -> {
                g.strokeCircle(color, w, cx - s * .08F, cy - s * .08F, s * .28F);
                g.line(color, w * 1.2F, cx + s * .13F, cy + s * .13F, cx + s * .36F, cy + s * .36F);
            }
            case "control" -> {
                g.strokeRoundRect(color, w * .85F, cx - s * .4F, cy - s * .34F, s * .8F, s * .3F, s * .15F);
                g.circle(color, cx + s * .23F, cy - s * .19F, s * .09F);
                g.strokeRoundRect(color, w * .85F, cx - s * .4F, cy + s * .06F, s * .8F, s * .3F, s * .15F);
                g.circle(color, cx - s * .23F, cy + s * .21F, s * .09F);
            }
            case "battery" -> {
                g.strokeRoundRect(UiCanvas.alpha(color, 150), w * .8F, cx - s * .5F, cy - s * .22F, s * .9F, s * .44F, s * .12F);
                g.roundRect(color, cx - s * .44F, cy - s * .16F, s * .62F, s * .32F, s * .07F);
                g.roundRect(UiCanvas.alpha(color, 150), cx + s * .43F, cy - s * .08F, s * .06F, s * .16F, s * .03F);
            }
            case "bluetooth" -> g.path().moveTo(cx - s * .2F, cy - s * .16F).lineTo(cx + s * .2F, cy + s * .18F).lineTo(cx, cy + s * .36F)
                .lineTo(cx, cy - s * .36F).lineTo(cx + s * .2F, cy - s * .18F).lineTo(cx - s * .2F, cy + s * .16F).stroke(color, w);
            case "airdrop" -> {
                for (int i = 0; i < 3; i++) g.path().arc(cx, cy + s * .12F, s * (.12F + i * .13F), 200, 140).stroke(color, w * .9F);
                g.polygon(color, cx, cy + s * .02F, cx - s * .1F, cy + s * .36F, cx + s * .1F, cy + s * .36F);
            }
            case "moon" -> crescent(g, cx, cy, s * .36F, color);
            case "sun" -> {
                g.circle(color, cx, cy, s * .17F);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    g.line(color, w, cx + (float) Math.cos(a) * s * .28F, cy + (float) Math.sin(a) * s * .28F,
                        cx + (float) Math.cos(a) * s * .4F, cy + (float) Math.sin(a) * s * .4F);
                }
            }
            case "speaker" -> {
                g.polygon(color, cx - s * .38F, cy - s * .12F, cx - s * .2F, cy - s * .12F, cx, cy - s * .32F, cx, cy + s * .32F,
                    cx - s * .2F, cy + s * .12F, cx - s * .38F, cy + s * .12F);
                g.path().arc(cx + s * .02F, cy, s * .18F, -50, 100).stroke(color, w * .9F);
                g.path().arc(cx + s * .02F, cy, s * .34F, -50, 100).stroke(color, w * .9F);
            }
            case "darkmode" -> {
                g.strokeCircle(color, w, cx, cy, s * .34F);
                g.path().arc(cx, cy, s * .34F, 90, 180).close().fill(color);
            }
            case "calculator" -> {
                g.strokeRoundRect(color, w * .9F, cx - s * .28F, cy - s * .38F, s * .56F, s * .76F, s * .1F);
                g.roundRect(color, cx - s * .18F, cy - s * .28F, s * .36F, s * .14F, s * .03F);
                for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
                    g.circle(color, cx - s * .14F + j * s * .14F, cy + s * .02F + i * s * .12F, s * .035F);
            }
            case "timer" -> {
                g.strokeCircle(color, w, cx, cy + s * .04F, s * .32F);
                g.line(color, w, cx, cy + s * .04F, cx + s * .14F, cy - s * .1F);
                g.line(color, w, cx - s * .1F, cy - s * .38F, cx + s * .1F, cy - s * .38F);
            }
            case "screenshot" -> {
                float a = s * .36F, b = s * .14F;
                g.path().moveTo(cx - a, cy - a + b).lineTo(cx - a, cy - a).lineTo(cx - a + b, cy - a).stroke(color, w);
                g.path().moveTo(cx + a - b, cy - a).lineTo(cx + a, cy - a).lineTo(cx + a, cy - a + b).stroke(color, w);
                g.path().moveTo(cx - a, cy + a - b).lineTo(cx - a, cy + a).lineTo(cx - a + b, cy + a).stroke(color, w);
                g.path().moveTo(cx + a - b, cy + a).lineTo(cx + a, cy + a).lineTo(cx + a, cy + a - b).stroke(color, w);
                g.circle(color, cx, cy, s * .1F);
            }
            case "play" -> g.polygon(color, cx - s * .22F, cy - s * .3F, cx + s * .3F, cy, cx - s * .22F, cy + s * .3F);
            case "pause" -> { g.roundRect(color, cx - s * .24F, cy - s * .3F, s * .16F, s * .6F, s * .04F); g.roundRect(color, cx + s * .08F, cy - s * .3F, s * .16F, s * .6F, s * .04F); }
            case "next" -> { g.polygon(color, cx - s * .34F, cy - s * .24F, cx, cy, cx - s * .34F, cy + s * .24F); g.polygon(color, cx, cy - s * .24F, cx + s * .34F, cy, cx, cy + s * .24F); }
            case "prev" -> { g.polygon(color, cx + s * .34F, cy - s * .24F, cx, cy, cx + s * .34F, cy + s * .24F); g.polygon(color, cx, cy - s * .24F, cx - s * .34F, cy, cx, cy + s * .24F); }
            case "chevron-left" -> g.path().moveTo(cx + s * .1F, cy - s * .26F).lineTo(cx - s * .12F, cy).lineTo(cx + s * .1F, cy + s * .26F).stroke(color, w);
            case "chevron-right" -> g.path().moveTo(cx - s * .1F, cy - s * .26F).lineTo(cx + s * .12F, cy).lineTo(cx - s * .1F, cy + s * .26F).stroke(color, w);
            case "chevron-down" -> g.path().moveTo(cx - s * .22F, cy - s * .08F).lineTo(cx, cy + s * .12F).lineTo(cx + s * .22F, cy - s * .08F).stroke(color, w);
            case "grid" -> { for (int i = 0; i < 4; i++) g.roundRect(color, cx - s * .3F + i % 2 * s * .34F, cy - s * .3F + i / 2 * s * .34F, s * .26F, s * .26F, s * .06F); }
            case "list" -> { for (int i = 0; i < 3; i++) { g.circle(color, cx - s * .28F, cy - s * .22F + i * s * .22F, s * .045F); g.line(color, w * .9F, cx - s * .14F, cy - s * .22F + i * s * .22F, cx + s * .34F, cy - s * .22F + i * s * .22F); } }
            case "folder" -> {
                g.roundRect(UiCanvas.alpha(color, 190), cx - s * .42F, cy - s * .3F, s * .38F, s * .2F, s * .06F);
                g.roundRect(color, cx - s * .42F, cy - s * .2F, s * .84F, s * .52F, s * .07F);
            }
            case "doc" -> {
                g.strokeRoundRect(color, w * .85F, cx - s * .26F, cy - s * .36F, s * .52F, s * .72F, s * .06F);
                for (int i = 0; i < 3; i++) g.line(color, w * .7F, cx - s * .14F, cy - s * .12F + i * s * .14F, cx + s * .14F, cy - s * .12F + i * s * .14F);
            }
            case "clock" -> { g.strokeCircle(color, w, cx, cy, s * .36F); g.line(color, w, cx, cy, cx, cy - s * .22F); g.line(color, w, cx, cy, cx + s * .15F, cy + s * .08F); }
            case "cloud" -> cloud(g, cx, cy, s, color);
            case "sun-cloud" -> {
                g.circle(0xFFFFD60A, cx + s * .12F, cy - s * .12F, s * .2F);
                cloud(g, cx - s * .04F, cy + s * .08F, s * .9F, color);
            }
            case "plus" -> { g.line(color, w, cx - s * .3F, cy, cx + s * .3F, cy); g.line(color, w, cx, cy - s * .3F, cx, cy + s * .3F); }
            case "share" -> {
                g.path().moveTo(cx - s * .2F, cy - s * .08F).lineTo(cx - s * .28F, cy - s * .08F).lineTo(cx - s * .28F, cy + s * .38F)
                    .lineTo(cx + s * .28F, cy + s * .38F).lineTo(cx + s * .28F, cy - s * .08F).lineTo(cx + s * .2F, cy - s * .08F).stroke(color, w * .85F);
                g.line(color, w * .85F, cx, cy - s * .38F, cx, cy + s * .12F);
                g.path().moveTo(cx - s * .13F, cy - s * .25F).lineTo(cx, cy - s * .38F).lineTo(cx + s * .13F, cy - s * .25F).stroke(color, w * .85F);
            }
            case "sidebar" -> {
                g.strokeRoundRect(color, w * .85F, cx - s * .4F, cy - s * .3F, s * .8F, s * .6F, s * .1F);
                g.line(color, w * .85F, cx - s * .12F, cy - s * .3F, cx - s * .12F, cy + s * .3F);
            }
            case "trash" -> {
                g.line(color, w, cx - s * .32F, cy - s * .24F, cx + s * .32F, cy - s * .24F);
                g.path().moveTo(cx - s * .24F, cy - s * .2F).lineTo(cx - s * .2F, cy + s * .36F).lineTo(cx + s * .2F, cy + s * .36F)
                    .lineTo(cx + s * .24F, cy - s * .2F).stroke(color, w);
                g.line(color, w, cx - s * .1F, cy - s * .36F, cx + s * .1F, cy - s * .36F);
            }
            case "note" -> note(g, cx, cy, s * .8F, color);
            case "pin" -> pin(g, cx, cy - s * .4F, s * .8F, color);
            case "check" -> g.path().moveTo(cx - s * .26F, cy).lineTo(cx - s * .06F, cy + s * .2F).lineTo(cx + s * .28F, cy - s * .22F).stroke(color, w * 1.2F);
            case "compose" -> {
                g.strokeRoundRect(color, w * .85F, cx - s * .34F, cy - s * .26F, s * .58F, s * .6F, s * .1F);
                g.line(color, w * 1.1F, cx - s * .06F, cy + s * .06F, cx + s * .34F, cy - s * .34F);
            }
            case "photo" -> {
                g.strokeRoundRect(color, w * .85F, cx - s * .38F, cy - s * .3F, s * .76F, s * .6F, s * .08F);
                g.polygon(color, cx - s * .3F, cy + s * .22F, cx - s * .08F, cy - s * .04F, cx + s * .08F, cy + s * .1F, cx + s * .16F, cy + s * .02F, cx + s * .3F, cy + s * .22F);
                g.circle(color, cx + s * .16F, cy - s * .14F, s * .06F);
            }
            case "heart" -> {
                g.circle(color, cx - s * .13F, cy - s * .08F, s * .16F);
                g.circle(color, cx + s * .13F, cy - s * .08F, s * .16F);
                g.polygon(color, cx - s * .28F, cy - s * .02F, cx + s * .28F, cy - s * .02F, cx, cy + s * .32F);
            }
            case "person" -> {
                g.circle(color, cx, cy - s * .14F, s * .15F);
                g.path().moveTo(cx - s * .3F, cy + s * .34F).curveTo(cx - s * .28F, cy + s * .04F, cx + s * .28F, cy + s * .04F, cx + s * .3F, cy + s * .34F).close().fill(color);
            }
            case "globe" -> {
                g.strokeCircle(color, w * .85F, cx, cy, s * .36F);
                g.line(color, w * .7F, cx - s * .36F, cy, cx + s * .36F, cy);
                g.path().moveTo(cx, cy - s * .36F).curveTo(cx - s * .24F, cy - s * .1F, cx - s * .24F, cy + s * .1F, cx, cy + s * .36F).stroke(color, w * .7F);
                g.path().moveTo(cx, cy - s * .36F).curveTo(cx + s * .24F, cy - s * .1F, cx + s * .24F, cy + s * .1F, cx, cy + s * .36F).stroke(color, w * .7F);
            }
            case "lock" -> {
                g.roundRect(color, cx - s * .26F, cy - s * .04F, s * .52F, s * .4F, s * .08F);
                g.path().arc(cx, cy - s * .06F, s * .17F, 180, 180).stroke(color, w);
            }
            case "bell" -> {
                g.path().moveTo(cx - s * .3F, cy + s * .2F).curveTo(cx - s * .2F, cy + s * .08F, cx - s * .26F, cy - s * .34F, cx, cy - s * .34F)
                    .curveTo(cx + s * .26F, cy - s * .34F, cx + s * .2F, cy + s * .08F, cx + s * .3F, cy + s * .2F).close().fill(color);
                g.circle(color, cx, cy + s * .3F, s * .07F);
            }
            default -> g.circle(color, cx, cy, s * .2F);
        }
    }

    /** The part of a disc not covered by a second disc offset toward the upper right. */
    private static void crescent(UiCanvas g, float cx, float cy, float r, int color) {
        float ix = cx + r * .42F, iy = cy - r * .34F, ir = r * .82F;
        var path = g.path();
        boolean started = false;
        for (int i = 0; i <= 120; i++) {
            double a = i / 120.0 * Math.PI * 2;
            float px = cx + (float) Math.cos(a) * r, py = cy + (float) Math.sin(a) * r;
            if (Math.hypot(px - ix, py - iy) < ir) continue;
            if (!started) { path.moveTo(px, py); started = true; } else path.lineTo(px, py);
        }
        // Back along the inner disc, through the points that lie inside the outer one.
        double start = Math.atan2(cy - iy, cx - ix);
        for (int i = 0; i <= 120; i++) {
            double a = start + Math.PI - i / 120.0 * Math.PI * 2;
            float px = ix + (float) Math.cos(a) * ir, py = iy + (float) Math.sin(a) * ir;
            if (Math.hypot(px - cx, py - cy) <= r) path.lineTo(px, py);
        }
        path.close().fill(color);
    }

    private static void cloud(UiCanvas g, float cx, float cy, float s, int color) {
        g.circle(color, cx - s * .16F, cy + s * .04F, s * .17F);
        g.circle(color, cx + s * .04F, cy - s * .06F, s * .22F);
        g.circle(color, cx + s * .22F, cy + s * .06F, s * .15F);
        g.roundRect(color, cx - s * .32F, cy + s * .02F, s * .68F, s * .2F, s * .1F);
    }

    /** The PaneOS mark: four rounded panes, the top-right one lifted. Not any company's logo. */
    static void mark(UiCanvas g, float cx, float cy, float s, int color) {
        float u = s * .42F, gap = s * .1F;
        g.roundRect(color, cx - u - gap / 2, cy - u - gap / 2, u, u, u * .3F);
        g.roundRect(UiCanvas.alpha(color, 200), cx + gap / 2, cy - u - gap / 2 - s * .06F, u, u, u * .3F);
        g.roundRect(UiCanvas.alpha(color, 200), cx - u - gap / 2, cy + gap / 2, u, u, u * .3F);
        g.roundRect(color, cx + gap / 2, cy + gap / 2, u, u, u * .3F);
    }
}
