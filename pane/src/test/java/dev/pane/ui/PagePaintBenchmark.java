package dev.pane.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;

/**
 * Explicit full-page CPU repaint workload, outside normal tests: common controls, cards, shadows,
 * text and a scroll view on a 2x surface. Prints a pixel fingerprint so refactors can prove parity.
 */
public final class PagePaintBenchmark {
    private static final int LAYOUT_W = 854, LAYOUT_H = 480;
    private static final float QUALITY = 2;

    public static void main(String[] args) throws Exception {
        int warmup = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        int measured = args.length > 1 ? Integer.parseInt(args[1]) : 60;
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        var image = new BufferedImage((int) (LAYOUT_W * QUALITY), (int) (LAYOUT_H * QUALITY), BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        var state = new HashMap<String, Object>();
        var retained = new UiInteractionState();
        String fingerprint = null;
        long[] times = new long[measured], allocated = new long[measured];
        for (int round = -warmup; round < measured; round++) {
            long bytes = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
            Arrays.fill(pixels, 0);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.scale(QUALITY, QUALITY);
                retained.beginPaint();
                var ui = new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), LAYOUT_W, LAYOUT_H, 0xFF5B9BE3,
                    new UiRect(0, 0, LAYOUT_W, LAYOUT_H), "secondary", null, 1, 1, false, 0, state,
                    new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>(), new ArrayList<>(),
                    new ArrayList<>(), new ArrayList<>(), new UiTextInputState("field", 4, 9), new ArrayList<>(),
                    new ArrayList<>(), null, 1, null).withInteractions(retained);
                page(ui);
            } finally { graphics.dispose(); }
            long duration = System.nanoTime() - start;
            if (round >= 0) {
                times[round] = duration;
                allocated[round] = bean.getThreadAllocatedBytes(thread) - bytes;
            }
            String next = hash(pixels);
            if (fingerprint != null && !fingerprint.equals(next)) throw new AssertionError("Repaint is not deterministic");
            fingerprint = next;
        }
        Arrays.sort(times); Arrays.sort(allocated);
        System.out.printf(Locale.ROOT, "{\"name\":\"page\",\"median_ms\":%.3f,\"p95_ms\":%.3f,\"allocated_bytes\":%d,\"sha256\":\"%s\"}%n",
            times[measured / 2] / 1e6, times[(int) Math.ceil(measured * .95) - 1] / 1e6, allocated[measured / 2], fingerprint);
    }

    static void page(UiContext ui) {
        var canvas = ui.canvas();
        var theme = ui.theme();
        canvas.gradientRect(0xFF14181E, 0xFF0D1014, 90, 0, 0, LAYOUT_W, LAYOUT_H, 0);
        canvas.fill(0xFF101318, 0, 0, 180, LAYOUT_H);
        for (int i = 0; i < 8; i++) {
            ui.button("nav-" + i, "Section " + i, "page:" + i, 12, 60 + i * 34, 156, 28,
                i == 0 ? UiContext.ButtonStyle.PRIMARY : UiContext.ButtonStyle.GHOST, UiContext.Shape.ROUNDED);
        }
        canvas.text("Settings overview", 200, 18, 18, theme.text(), Font.BOLD, theme.fontFamily());
        canvas.wrappedText("Every control here is drawn from code and cached on the GPU between changes. "
            + "This paragraph wraps across the available width to exercise measurement.", 200, 44, 420, 9, 13,
            theme.mutedText(), Font.PLAIN, theme.fontFamily());
        for (int i = 0; i < 6; i++) {
            float x = 200 + (i % 3) * 212, y = 90 + (i / 3) * 128;
            canvas.shadow(0x50000000, 10, 0, 3, x, y, 200, 116, 10);
            ui.card(x, y, 200, 116);
            canvas.text("Card " + i, x + 14, y + 12, 11, theme.text(), Font.BOLD, theme.fontFamily());
            canvas.text(canvas.ellipsize("A longer description that will need to be shortened", 172, 8, Font.PLAIN,
                theme.fontFamily()), x + 14, y + 30, 8, theme.mutedText(), Font.PLAIN, theme.fontFamily());
            ui.toggle("toggle-" + i, "toggle-" + i, x + 14, y + 52, i % 2 == 0);
            ui.badge("Beta", x + 56, y + 53, 16, UiCanvas.fade(ui.accent(), .2F), ui.accent());
            ui.progress(x + 14, y + 82, 172, 6, i / 5.0);
            UiIcon.values()[i].draw(canvas, theme.text(), x + 172, y + 12, 14, 1.4F);
        }
        ui.button("primary", "Save changes", "save", 200, 360, 120, 30, UiContext.ButtonStyle.PRIMARY, UiContext.Shape.PILL);
        ui.button("secondary", "Cancel", "cancel", 330, 360, 90, 30, UiContext.ButtonStyle.SECONDARY, UiContext.Shape.SOFT);
        ui.iconButton("search", UiIcon.SEARCH, "search", 430, 360, 30, UiContext.ButtonStyle.SECONDARY, UiContext.Shape.ROUNDED);
        ui.checkbox("check", "check", "Remember this choice", 480, 366, 160, true);
        ui.radio("radio-a", "radio", "a", "Option A", 650, 366, 80, true);
        ui.radio("radio-b", "radio", "b", "Option B", 740, 366, 80, false);
        ui.tabs("tabs", "tabs", List.of(new UiSelectOption("one", "Overview"), new UiSelectOption("two", "Activity"),
            new UiSelectOption("three", "Members")), 200, 404, 300, 28, "two");
        ui.slider("slider", "slider", 520, 410, 140, .35);
        ui.textField("field", "field", "Search", 680, 402, 150, "Waypoint café");
        ui.dropdown("drop", "drop", 200, 440, 150, 28, "b", List.of(new UiSelectOption("a", "Alpha"),
            new UiSelectOption("b", "Bravo"), new UiSelectOption("c", "Charlie")));
        ui.tag("tag", "minecraft:stone", "remove", 360, 444, 20, theme.raisedSurface(), theme.text());
        canvas.formattedText(UiRichText.legacy("§aGreen §lbold §r§cred §nunderlined"), 500, 446, 9, theme.text());
        ui.scroll("recent", new UiRect(12, 340, 156, 130), 20 * 24, inner -> {
            for (int i = 0; i < 20; i++) {
                inner.canvas().roundRect(theme.surface(), 12, 340 + i * 24, 156, 20, 4);
                inner.canvas().text("Recent item " + i, 20, 344 + i * 24, 8, theme.text(), Font.PLAIN, theme.fontFamily());
            }
        });
    }

    private static String hash(int[] pixels) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        var bytes = new byte[pixels.length * 4];
        for (int i = 0, j = 0; i < pixels.length; i++) {
            int p = pixels[i];
            bytes[j++] = (byte) (p >>> 24); bytes[j++] = (byte) (p >>> 16); bytes[j++] = (byte) (p >>> 8); bytes[j++] = (byte) p;
        }
        return HexFormat.of().formatHex(digest.digest(bytes));
    }
}
