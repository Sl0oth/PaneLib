package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import net.minecraft.client.gui.screens.Screen;

/** Three original visual systems, one small expedition-planning model. No game artwork or I/O. */
public final class DesignLabShowcase {
    private static final String[] NAMES = {"Pine Ridge", "Cloudbreak", "Silent Harbor", "Copper Canyon", "Northwatch", "Sunken Relay"};
    private static final int WHITE = 0xFFF5FAFF;
    private DesignLabShowcase() { }

    public static UiScreen createScreen(Screen parent) {
        UiScreen[] holder = new UiScreen[1];
        UiLayer detailLayer = new UiLayer();
        var document = UiDocument.builder("PaneLib • Design Lab")
            .theme(UiTheme.dark().withAccent(0xFF62C8FF))
            .pageShortcuts(true)
            .page(new UiPage("voxel", "Trailcraft", "", 0xFF91D17B, ui -> voxel(ui, detailLayer)))
            .page(new UiPage("sky", "Skybound", "", 0xFF55CFFF, ui -> sky(ui, detailLayer)))
            .page(new UiPage("tactical", "Blackline", "", 0xFFBADEA2, ui -> tactical(ui, detailLayer)))
            .build();
        holder[0] = new UiScreen(document, NativeOverlayRenderer.NONE, (UiEventListener) (type, key, value) -> {
            UiScreen screen = holder[0];
            if (type.equals("value") && key.equals("reduced")) screen.setMotionScale(Boolean.parseBoolean(value) ? 0 : 1);
            if (!type.equals("action")) return;
            if (key.startsWith("page:")) screen.showPage(key.substring(5));
            else if (key.startsWith("route:")) {
                screen.setValue("selected", Integer.parseInt(key.substring(6)));
                screen.setValue("details", true);
            } else if (key.equals("details")) screen.setValue("details", !screen.booleanValue("details", !screen.pageId().equals("voxel")));
            else if (key.equals("deploy")) {
                screen.setValue("ready", !screen.booleanValue("ready", false));
                screen.toast(new UiToast("ready", screen.booleanValue("ready", false) ? "Expedition ready" : "Back in planning",
                    "Local demo state • no server action", 4000));
            } else if (key.equals("notice")) screen.toast(new UiToast("notice", "Route updated", "Scroll the list while this stays visible.", 6000));
        }, UiScreenOptions.builder().parent(parent).backdrop(UiBackdrop.PARENT_BACKGROUND).build()) {
            @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int x, int y, float delta) {
                long start = System.nanoTime();
                super.extractRenderState(graphics, x, y, delta);
                DesignLabTest.observe(start);
            }
        };
        return holder[0];
    }

    private static UiButtonStyle style(int fill, int accent, float radius, String font) {
        return UiButtonStyle.builder().background(fill).hoverBackground(UiCanvas.mix(fill, accent, .22F))
            .pressedBackground(UiCanvas.mix(fill, accent, .4F)).text(WHITE).border(UiCanvas.fade(accent, .65F))
            .focus(accent).radius(radius).textSize(9).fontFamily(font).build();
    }

    private static void navigation(UiContext ui, int active, int accent, boolean square) {
        String[] labels = {"1  TRAILCRAFT", "2  SKYBOUND", "3  BLACKLINE"};
        String[] pages = {"voxel", "sky", "tactical"};
        float w = Math.min(126, (ui.width() - 48) / 3F);
        for (int i = 0; i < 3; i++) ui.button("design-" + i, labels[i], "page:" + pages[i], 20 + i * (w + 4), 14, w, 27,
            style(i == active ? UiCanvas.fade(accent, .3F) : 0x60101720, accent, square ? 0 : 8, UiCanvas.MONO));
        ui.checkbox("reduced", "reduced", "Reduce motion", Math.max(20, ui.width() - 145), ui.height() - 28, 125, false);
    }

    private static void voxel(UiContext ui, UiLayer detailLayer) {
        var g = ui.canvas();
        int accent = 0xFF91D17B;
        g.fill(0x28111E23, 0, 0, ui.width(), ui.height());
        navigation(ui, 0, accent, true);
        float x = Math.max(20, (ui.width() - 690) / 2), y = 70;
        float w = Math.min(690, ui.width() - 40), h = ui.height() - 118;
        // Block-built landscape floats over the live panorama rather than covering the whole screen.
        for (int i = 0; i < 18; i++) {
            float blockX = x + i * w / 18;
            float top = y + h * .5F + (int) (Math.sin(i * .8) * 3) * 9;
            g.fill(i % 2 == 0 ? 0x70699567 : 0x705A8259, blockX, top, w / 18 + 1, h * .5F);
            g.fill(0x70543F2F, blockX, top + 12, w / 18 + 1, h * .5F - 12);
        }
        g.text("TRAILCRAFT", x, y, 25, WHITE, Font.BOLD, UiCanvas.MONO);
        g.text("Pack light. Take the long way home.", x, y + 34, 10, 0xFFE0ECD9);
        float journalW = w < 540 ? w : 210;
        float inventoryW = w - journalW - 18;
        UiRect list = new UiRect(x + (w < 540 ? 0 : inventoryW + 18), y + 69, journalW, h - 127);
        if (w >= 540) {
            g.text("ROUTE INVENTORY", x, y + 68, 8, accent, Font.BOLD, UiCanvas.MONO);
            float cellW = (inventoryW - 12) / 3, cellH = Math.min(87, (h - 152) / 2);
            for (int i = 0; i < 6; i++) {
                float sx = x + (i % 3) * (cellW + 6), sy = y + 88 + (i / 3) * (cellH + 6);
                boolean selected = (int) ui.numberValue("selected", 0) == i;
                g.fill(selected ? 0xB7547446 : 0xB51B2528, sx, sy, cellW, cellH);
                g.strokeRoundRect(selected ? accent : 0x9067766C, 1.5F, sx, sy, cellW, cellH, 0);
                float iconX = sx + cellW / 2, iconY = sy + cellH * .42F;
                g.fill(0xFF725442, iconX - 3, iconY, 6, 14);
                g.fill(i % 2 == 0 ? 0xFF91B86A : 0xFF68A2A8, iconX - 15, iconY - 15, 30, 22);
                g.fill(0xFFB6D68A, iconX - 9, iconY - 22, 18, 8);
                g.centeredText(NAMES[i], iconX, sy + cellH - 12, 7, WHITE, Font.PLAIN, UiCanvas.MONO);
                ui.hit("slot-" + i, "route:" + i, sx, sy, cellW, cellH);
            }
            details(ui, detailLayer, new UiRect(x, y + 78, inventoryW, h - 136), 0, accent);
        }
        panel(ui, list.inset(-5), 0xB51B2528, accent, 0);
        if (ui.booleanValue("glass", false)) g.backdropBlur(2, list.x(), list.y(), list.width(), list.height());
        routes(ui, list, 0, accent);
        footer(ui, x, y + h - 42, w, accent, 0);
    }

    private static void sky(UiContext ui, UiLayer detailLayer) {
        var g = ui.canvas();
        int accent = 0xFF55CFFF;
        g.fill(0x303177BD, 0, 0, ui.width(), ui.height());
        // Large graphic shapes, open space, and floating translucent cards: a different silhouette.
        g.circle(0x5540CCFF, ui.width() * .77F, ui.height() * .46F, ui.height() * .3F);
        g.circle(0x40F6D25D, ui.width() * .82F, ui.height() * .32F, ui.height() * .17F);
        navigation(ui, 1, accent, false);
        float x = 34, y = 76, w = ui.width() - 68, h = ui.height() - 125;
        g.path().moveTo(x - 10, y + 7).lineTo(x + 208, y - 7).lineTo(x + 195, y + 25).lineTo(x - 16, y + 31).close().fill(0xFFF4D95C);
        g.text("YOUR NEXT BIG THING", x, y + 3, 11, 0xFF133B61, Font.BOLD, UiCanvas.SANS);
        g.text("GO SOMEWHERE", x, y + 48, Math.min(32, w / 17), WHITE, Font.BOLD, UiCanvas.SANS);
        g.text("WILD.", x, y + 88, 43, WHITE, Font.BOLD, UiCanvas.SANS);
        g.text("A fresh route. Your crew. One very good detour.", x, y + 145, 10, WHITE);
        float listW = Math.min(300, w < 570 ? w : w * .48F);
        UiRect list = new UiRect(x, y + 177, listW, Math.max(75, h - 227));
        routes(ui, list, 1, accent);
        if (w >= 570) details(ui, detailLayer, new UiRect(x + w * .57F, y + 52, w * .43F, h - 110), 1, accent);
        footer(ui, x, y + h - 34, w, 0xFFF4D95C, 1);
    }

    private static void tactical(UiContext ui, UiLayer detailLayer) {
        var g = ui.canvas();
        int accent = 0xFFBADEA2;
        g.fill(0x65060D10, 0, 0, ui.width(), ui.height());
        // Sparse topographic linework and a split command layout; no opaque central window.
        for (int i = 0; i < 11; i++) {
            float yy = 55 + i * 33;
            g.path().moveTo(ui.width() * .38F, yy).curveTo(ui.width() * .6F, yy - 80, ui.width() * .8F, yy + 80, ui.width(), yy - 12)
                .stroke(0x244E8873, .7F);
        }
        navigation(ui, 2, accent, true);
        float x = 26, y = 78, w = ui.width() - 52, h = ui.height() - 126;
        g.text("B L A C K L I N E", x, y, 11, accent, Font.BOLD, UiCanvas.MONO);
        g.text("OPERATIONS", x, y + 25, 29, WHITE, Font.BOLD, UiCanvas.SANS);
        g.line(0x906B8175, 1, x, y + 72, x + w, y + 72);
        float rail = w >= 620 ? 125 : 0;
        if (rail > 0) {
            g.text("BRIEFING / 07", x, y + 94, 9, accent, Font.BOLD, UiCanvas.MONO);
            g.text("ROUTE PLANNING", x, y + 120, 7, WHITE, Font.PLAIN, UiCanvas.MONO);
            g.text("TEAM 04", x, y + 145, 9, WHITE, Font.BOLD, UiCanvas.MONO);
            g.text("SIGNAL SECURE", x, y + h - 80, 7, accent, Font.PLAIN, UiCanvas.MONO);
        }
        float listX = x + rail, listW = w < 530 ? w : (w - rail) * .54F;
        UiRect list = new UiRect(listX, y + 87, listW, h - 143);
        routes(ui, list, 2, accent);
        if (w >= 530) details(ui, detailLayer, new UiRect(listX + listW + 22, y + 87, w - rail - listW - 22, h - 143), 2, accent);
        footer(ui, listX, y + h - 36, w - rail, accent, 2);
    }

    private static void routes(UiContext ui, UiRect viewport, int look, int accent) {
        float rowHeight = look == 0 ? 51 : look == 1 ? 64 : 48;
        ui.virtualList("routes", viewport, 60, rowHeight, look == 2 ? 1 : 6, (row, item) -> {
            UiRect b = item.bounds();
            int i = item.index();
            boolean selected = (int) row.numberValue("selected", 0) == i;
            float hover = row.animate("route-hover:" + i, row.hovered("route-" + i), 140, b);
            int fill = look == 0 ? 0xB4212C2B : look == 1 ? 0xB2144D88 : 0x9010181A;
            if (selected) fill = UiCanvas.mix(fill, UiCanvas.fade(accent, .8F), .24F);
            var g = row.canvas();
            float radius = look == 1 ? 15 : 0;
            g.roundRect(UiCanvas.mix(fill, accent, hover * .13F), b.x(), b.y(), b.width() - 5, b.height(), radius);
            if (selected) g.fill(accent, b.x(), b.y(), look == 0 ? 4 : 2, b.height());
            float tile = look == 1 ? 39 : 28, ty = b.y() + (b.height() - tile) / 2;
            g.roundRect(UiCanvas.fade(accent, .24F), b.x() + 12, ty, tile, tile, radius / 2);
            g.centeredText(String.format(java.util.Locale.ROOT, "%02d", i + 1), b.x() + 12 + tile / 2, ty + tile / 2, 9, accent, Font.BOLD, UiCanvas.MONO);
            g.text(NAMES[i % NAMES.length], b.x() + tile + 24, b.y() + 12, 10, WHITE, Font.BOLD, look == 0 ? UiCanvas.MONO : UiCanvas.SANS);
            g.text((i % 3 == 0 ? "EXPLORE" : i % 3 == 1 ? "RECOVER" : "SURVEY") + "  /  " + (8 + i) + " MIN",
                b.x() + tile + 24, b.y() + 29, 7, 0xFFCCDCD9, Font.PLAIN, UiCanvas.MONO);
            row.hit("route-" + i, "route:" + i, b.x(), b.y(), b.width() - 5, b.height());
            row.tooltip("route-" + i, "Select this expedition");
        });
    }

    private static void panel(UiContext ui, UiRect b, int color, int accent, float radius) {
        ui.canvas().roundRect(color, b.x(), b.y(), b.width(), b.height(), radius);
        ui.canvas().strokeRoundRect(UiCanvas.fade(accent, .4F), .8F, b.x(), b.y(), b.width(), b.height(), radius);
    }

    private static void details(UiContext ui, UiLayer layer, UiRect bounds, int look, int accent) {
        float amount = ui.animate("details-open", ui.booleanValue("details", look != 0), 220, UiEasing.EASE_IN_OUT, bounds.inset(-20));
        if (amount == 1 && ui.booleanValue("glass", false)) ui.canvas().backdropBlur(2, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        ui.withLayer(layer, amount, bounds.inset(-16), group -> group.withTranslation((1 - amount) * 14, 0, panel -> {
            var g = panel.canvas();
            float x = bounds.x(), y = bounds.y(), w = bounds.width(), h = bounds.height();
            panel(panel, bounds, look == 1 ? 0xA51D5586 : 0xB0111D22, accent, look == 1 ? 22 : 0);
            int selected = (int) panel.numberValue("selected", 0);
            g.text(look == 2 ? "OPERATION BRIEF" : "THE PLAN", x + 16, y + 17, 8, accent, Font.BOLD, UiCanvas.MONO);
            g.text(g.ellipsize(NAMES[selected % NAMES.length], w - 32, 19, Font.BOLD, UiCanvas.SANS), x + 16, y + 43, 19, WHITE, Font.BOLD, UiCanvas.SANS);
            float cx = x + w / 2, cy = y + Math.min(133, h * .48F);
            if (h > 190) {
                g.path().moveTo(cx - 54, cy + 26).lineTo(cx - 12, cy - 31).lineTo(cx + 10, cy - 5)
                    .lineTo(cx + 30, cy - 21).lineTo(cx + 65, cy + 26).close().fill(UiCanvas.fade(accent, .4F));
                g.path().moveTo(cx - 47, cy + 22).lineTo(cx - 12, cy - 23).lineTo(cx + 12, cy + 22).close().fill(UiCanvas.fade(accent, .45F));
                g.line(accent, 1, cx - 66, cy + 33, cx + 66, cy + 33);
            }
            if (h > 140) {
                g.text("4 explorers  /  " + (8 + selected) + " minutes", x + 16, y + h - 69, 8, WHITE);
                g.text("Supplies checked. Route saved locally.", x + 16, y + h - 48, 7, 0xFFBFD2DC);
            }
            panel.button("brief-notice", "Send update", "notice", x + 14, y + h - 31, w - 28, 23,
                style(0x503C687C, accent, look == 1 ? 9 : 0, UiCanvas.SANS));
        }));
    }

    private static void footer(UiContext ui, float x, float y, float w, int accent, int look) {
        float primaryW = Math.min(180, w * .4F);
        ui.button("deploy", ui.booleanValue("ready", false) ? "READY • CANCEL" : look == 2 ? "CONFIRM ROUTE" : "LET'S GO", "deploy",
            x, y, primaryW, 31, style(UiCanvas.fade(accent, .7F), accent, look == 1 ? 14 : 0, UiCanvas.SANS));
        ui.button("details", "Briefing", "details", x + primaryW + 8, y, 80, 31,
            style(0x70303C48, accent, look == 1 ? 14 : 0, UiCanvas.SANS));
        if (w > 360) ui.toggle("glass", "glass", x + w - 34, y + 7, false);
        if (w > 420) ui.canvas().text("Soft glass", x + w - 100, y + 11, 8, WHITE);
    }
}
