package dev.clientui.demo;

import dev.clientui.api.NativeOverlayContext;
import dev.clientui.api.UiCanvas;
import dev.clientui.api.UiContext;
import dev.clientui.api.UiDocument;
import dev.clientui.api.UiPage;
import dev.clientui.api.UiScreen;
import dev.clientui.api.UiSidebarShell;
import dev.clientui.api.UiTheme;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static dev.clientui.api.UiContext.ButtonStyle.GHOST;
import static dev.clientui.api.UiContext.ButtonStyle.PRIMARY;
import static dev.clientui.api.UiContext.ButtonStyle.SECONDARY;
import static dev.clientui.api.UiContext.Shape.PILL;
import static dev.clientui.api.UiContext.Shape.ROUNDED;
import static dev.clientui.api.UiContext.Shape.SOFT;

/** Five practical screens rendered by the lightweight native backend. */
public final class NativeDemoShowcase {
    private NativeDemoShowcase() {}

    public static UiScreen createScreen() {
        return new UiScreen(document(), NativeDemoShowcase::renderNativeLayer,
            (type, key, value) -> System.out.println("[Client UI native demo] " + type + " " + key + " " + value));
    }

    private static UiDocument document() {
        String initial = System.getProperty("clientui.demo.page", "discover");
        return UiDocument.builder("Client UI Native Lab")
            .brand("Client UI Lab")
            .theme(UiTheme.dark())
            .shell(UiSidebarShell.create()
                .caption("CLIENT UI LIBRARY")
                .sectionLabel("SHOWCASE")
                .footnote("H \u00b7 open")
                .statusIndicator(true))
            .pageShortcuts(true)
            .page(new UiPage("discover", "Discover", "⌕", 0xFF1BD96A, NativeDemoShowcase::discover))
            .page(new UiPage("loadout", "Loadout", "◇", 0xFFF0B64D, NativeDemoShowcase::loadout))
            .page(new UiPage("waypoints", "Waypoints", "+", 0xFF5AA7FF, NativeDemoShowcase::waypoints))
            .page(new UiPage("performance", "Performance", "~", 0xFF6DE6AA, NativeDemoShowcase::performance))
            .page(new UiPage("settings", "Settings", "○", 0xFF8290FF, NativeDemoShowcase::settings))
            .initialPage(initial)
            .build();
    }

    private static void discover(UiContext ui) {
        UiCanvas g = ui.canvas();
        UiTheme t = ui.theme();
        float x = ui.contentX() + 20, y = ui.contentY() + 17 + (1 - ui.motion()) * 5;
        float w = ui.contentWidth() - 40, h = ui.contentHeight() - 34;
        kicker(g, "MOD LIBRARY", x, y, ui.accent());
        title(g, "Discover", x, y + 14, t);
        muted(g, "Find, install and update client mods without leaving the game.", x, y + 43, t);

        float updateWidth = Math.min(156, w * .24F);
        float mainWidth = w - updateWidth - 9;
        float searchWidth = Math.min(180, mainWidth * .34F);
        search(g, x + mainWidth - searchWidth - 75, y + 1, searchWidth, 28, t);
        ui.button("update-all", "Update all", "update-all", x + mainWidth - 67, y + 1, 67, 28, PRIMARY, PILL);

        float bodyY = y + 64;
        float bodyH = h - 64;
        ui.card(x, bodyY, mainWidth, 88, t.mediumRadius(), 0xFF171A18);
        projectIcon(g, "S", x + 13, bodyY + 20, 42, 0xFF1D3323, 0xFFC0E6CA, t);
        badge(g, "FEATURED", x + 66, bodyY + 12, ui.accent(), 0xFF153520);
        g.textMedium("Sodium", x + 66, bodyY + 31, 14, t.text());
        muted(g, "A fast rendering engine, ready for this version.", x + 66, bodyY + 49, t);
        g.text("41.2M downloads    Fabric 26.2    Client", x + 66, bodyY + 65, 6.5F, 0xFF748078);
        ui.button("install-sodium", "Install", "install-sodium", x + mainWidth - 63, bodyY + 30, 50, 27, PRIMARY, ROUNDED);

        g.textMedium("Recommended for you", x + 2, bodyY + 99, 9, t.text());
        g.textMedium("View all  →", x + mainWidth - 56, bodyY + 99, 7, ui.accent());
        float gridY = bodyY + 115;
        float gap = 7;
        float cardW = (mainWidth - gap) / 2;
        projectCard(ui, "iris", "I", "Iris Shaders", "Shaders compatible with Sodium.", "Graphics · 18.7M",
            x, gridY, cardW, 58, 0xFF242B42, 0xFFB7CAFF, true);
        projectCard(ui, "zoomify", "Z", "Zoomify", "Configurable zoom controls.", "Utility · 3.1M",
            x + cardW + gap, gridY, cardW, 58, 0xFF37301D, 0xFFFFE19C, false);
        projectCard(ui, "mod-menu", "M", "Mod Menu", "Manage installed mods.", "Management · 22M",
            x, gridY + 65, cardW, 58, 0xFF1F3732, 0xFFB9E7DB, true);
        projectCard(ui, "lights", "L", "LambDynamicLights", "Dynamic item and entity lighting.", "Graphics · 14M",
            x + cardW + gap, gridY + 65, cardW, 58, 0xFF37341E, 0xFFFFF1A8, false);

        float sideX = x + mainWidth + 9;
        ui.card(sideX, bodyY, updateWidth, bodyH, t.mediumRadius(), 0xFF151815);
        g.textMedium("Updates", sideX + 11, bodyY + 12, 9, t.text());
        badge(g, "3 READY", sideX + updateWidth - 51, bodyY + 9, t.mutedText(), t.raisedSurface());
        updateRow(g, "I", "Iris", "1.9.4 → 1.9.5", sideX + 11, bodyY + 37, updateWidth - 22, 0xFF242B42, t);
        updateRow(g, "M", "Mod Menu", "18.0 → 18.1", sideX + 11, bodyY + 76, updateWidth - 22, 0xFF1F3732, t);
        updateRow(g, "Z", "Zoomify", "3.0.2 → 3.1", sideX + 11, bodyY + 115, updateWidth - 22, 0xFF37301D, t);
        g.text("Verified for this profile", sideX + 11, bodyY + bodyH - 20, 6.5F, 0xFF657068);
    }

    private static void loadout(UiContext ui) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        float x = ui.contentX() + 20, y = ui.contentY() + 17 + (1 - ui.motion()) * 5;
        float w = ui.contentWidth() - 40, h = ui.contentHeight() - 34;
        kicker(g, "EQUIPMENT MANAGER", x, y, ui.accent());
        title(g, "Adventure loadout", x, y + 14, t);
        muted(g, "Native items and your live player over a lightweight retained layout.", x, y + 43, t);
        ui.button("save-loadout", "Save loadout", "save-loadout", x + w - 150, y + 1, 83, 29, PRIMARY, ROUNDED);
        ui.button("equip-best", "Equip best", "equip-best", x + w - 60, y + 1, 60, 29, SECONDARY, ROUNDED);

        float bodyY = y + 64;
        float playerW = Math.min(245, w * .38F);
        float topH = h - 130;
        ui.card(x, bodyY, playerW, topH);
        float playerBoxX = x + 12, playerBoxY = bodyY + 12, playerBoxW = playerW - 24, playerBoxH = topH - 73;
        g.roundRect(0xFF181B20, playerBoxX, playerBoxY, playerBoxW, playerBoxH, 12);
        g.strokeRoundRect(0xFF353A43, .8F, playerBoxX, playerBoxY, playerBoxW, playerBoxH, 12);
        ui.nativeSlot("player", playerBoxX, playerBoxY, playerBoxW, playerBoxH);
        if (net.minecraft.client.Minecraft.getInstance().player == null) {
            g.centeredText("Join a world to render your live player", playerBoxX + playerBoxW / 2,
                playerBoxY + playerBoxH / 2, 7, 0xFF69717D, Font.PLAIN);
        }
        g.textMedium("Sl0oth", x + 14, bodyY + topH - 49, 10, t.text());
        badge(g, "READY", x + playerW - 55, bodyY + topH - 53, 0xFF72D699, 0xFF14291E);
        muted(g, "Exploration preset", x + 14, bodyY + topH - 34, t);
        g.text("Armor  18     Toughness  8     Speed  100%", x + 14, bodyY + topH - 17, 7, t.mutedText());

        float equipmentX = x + playerW + 9;
        float equipmentW = w - playerW - 9;
        ui.card(equipmentX, bodyY, equipmentW, topH);
        g.textMedium("Equipment", equipmentX + 14, bodyY + 13, 10, t.text());
        g.text("Native item renderer", equipmentX + equipmentW - 91, bodyY + 14, 6.5F, t.mutedText());
        float slotGap = 7, slotW = (equipmentW - 28 - slotGap * 2) / 3, slotH = 62;
        nativeItemSlot(ui, "item-helmet", "Helmet", equipmentX + 14, bodyY + 36, slotW, slotH, false);
        nativeItemSlot(ui, "item-chest", "Chest", equipmentX + 14 + slotW + slotGap, bodyY + 36, slotW, slotH, false);
        nativeItemSlot(ui, "item-legs", "Legs", equipmentX + 14 + (slotW + slotGap) * 2, bodyY + 36, slotW, slotH, false);
        nativeItemSlot(ui, "item-boots", "Boots", equipmentX + 14, bodyY + 36 + slotH + 7, slotW, slotH, false);
        nativeItemSlot(ui, "item-sword", "Main hand", equipmentX + 14 + slotW + slotGap, bodyY + 36 + slotH + 7, slotW, slotH, true);
        nativeItemSlot(ui, "item-totem", "Off hand", equipmentX + 14 + (slotW + slotGap) * 2, bodyY + 36 + slotH + 7, slotW, slotH, false);
        g.roundRect(0xFF1B1E24, equipmentX + 14, bodyY + 36 + (slotH + 7) * 2, equipmentW - 28, 38, 9);
        g.polygon(ui.accent(), equipmentX + 24, bodyY + 36 + (slotH + 7) * 2 + 19,
            equipmentX + 31, bodyY + 36 + (slotH + 7) * 2 + 12,
            equipmentX + 38, bodyY + 36 + (slotH + 7) * 2 + 19,
            equipmentX + 31, bodyY + 36 + (slotH + 7) * 2 + 26);
        g.textMedium("Native bridge", equipmentX + 45, bodyY + 36 + (slotH + 7) * 2 + 10, 8, ui.accent());
        muted(g, "Items keep glint, durability and model overrides.", equipmentX + 45,
            bodyY + 36 + (slotH + 7) * 2 + 22, t);

        float hotY = bodyY + topH + 9;
        ui.card(x, hotY, w, h - (hotY - y));
        g.textMedium("Hotbar preset", x + 14, hotY + 14, 9, t.text());
        muted(g, "Real ItemStacks can be placed into any native slot.", x + 14, hotY + 29, t);
        float hotSlot = 34, hotGap = 5, hotX = x + w - hotSlot * 9 - hotGap * 8 - 14;
        String[] ids = {"hotbar-pick", "hotbar-food", "hotbar-pearl", "hotbar-compass", "h5", "h6", "h7", "h8", "h9"};
        for (int i = 0; i < ids.length; i++) nativeItemSlot(ui, ids[i], "", hotX + i * (hotSlot + hotGap), hotY + 12, hotSlot, hotSlot, i == 0);
    }

    private static void waypoints(UiContext ui) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        float x = ui.contentX() + 20, y = ui.contentY() + 17 + (1 - ui.motion()) * 5;
        float w = ui.contentWidth() - 40, h = ui.contentHeight() - 34;
        kicker(g, "WORLD NAVIGATION", x, y, ui.accent());
        title(g, "Waypoints", x, y + 14, t);
        muted(g, "Plan routes, group locations and share exact coordinates.", x, y + 43, t);
        ui.button("new-waypoint", "+  New waypoint", "new-waypoint", x + w - 103, y + 1, 103, 29, PRIMARY, PILL);
        float bodyY = y + 64, listW = Math.min(215, w * .35F), bodyH = h - 64;
        ui.card(x, bodyY, listW, bodyH);
        g.roundRect(0xFF1B1E23, x + 11, bodyY + 11, listW - 22, 28, 8);
        filter(g, "All", x + 14, bodyY + 14, (listW - 28) / 3, true, t);
        filter(g, "Overworld", x + 14 + (listW - 28) / 3, bodyY + 14, (listW - 28) / 3, false, t);
        filter(g, "Nether", x + 14 + (listW - 28) * 2 / 3, bodyY + 14, (listW - 28) / 3, false, t);
        waypointRow(ui, "Main base", "124, 68, -340 · 182m", x + 11, bodyY + 49, listW - 22, ui.accent(), true);
        waypointRow(ui, "Nether portal", "18, 64, -42 · 36m", x + 11, bodyY + 98, listW - 22, 0xFFA779EF, false);
        waypointRow(ui, "Deep mine", "201, -42, -511 · 390m", x + 11, bodyY + 147, listW - 22, 0xFFD6A858, false);
        waypointRow(ui, "Trading hall", "-88, 71, 640 · 712m", x + 11, bodyY + 196, listW - 22, 0xFF72CC99, false);

        float mapX = x + listW + 9, mapW = w - listW - 9;
        ui.card(mapX, bodyY, mapW, bodyH, t.mediumRadius(), 0xFF14191F);
        g.withClip(mapX + 1, bodyY + 1, mapW - 2, bodyH - 2, () -> {
            for (float gx = mapX + 28; gx < mapX + mapW; gx += 28) g.line(0xFF222B34, .6F, gx, bodyY, gx, bodyY + bodyH);
            for (float gy = bodyY + 28; gy < bodyY + bodyH; gy += 28) g.line(0xFF222B34, .6F, mapX, gy, mapX + mapW, gy);
            g.polygon(0xFF192820, mapX + mapW * .09F, bodyY + bodyH * .29F, mapX + mapW * .25F, bodyY + bodyH * .11F,
                mapX + mapW * .49F, bodyY + bodyH * .14F, mapX + mapW * .60F, bodyY + bodyH * .40F,
                mapX + mapW * .51F, bodyY + bodyH * .68F, mapX + mapW * .22F, bodyY + bodyH * .76F,
                mapX + mapW * .08F, bodyY + bodyH * .58F);
            g.polygon(0xFF1A2631, mapX + mapW * .62F, bodyY + bodyH * .58F, mapX + mapW * .79F, bodyY + bodyH * .51F,
                mapX + mapW * .93F, bodyY + bodyH * .66F, mapX + mapW * .84F, bodyY + bodyH * .91F,
                mapX + mapW * .66F, bodyY + bodyH * .86F);
            g.dashedLine(ui.accent(), 2, 5, mapX + mapW * .43F, bodyY + bodyH * .48F,
                mapX + mapW * .72F, bodyY + bodyH * .36F);
            g.dashedLine(ui.accent(), 2, 5, mapX + mapW * .72F, bodyY + bodyH * .36F,
                mapX + mapW * .76F, bodyY + bodyH * .64F);
        });
        marker(g, mapX + mapW * .48F, bodyY + bodyH * .62F, ui.accent(), "◆", 0xFFFFFFFF);
        marker(g, mapX + mapW * .73F, bodyY + bodyH * .34F, 0xFF202731, "⌂", ui.accent());
        marker(g, mapX + mapW * .29F, bodyY + bodyH * .27F, 0xFF202731, "◇", 0xFFA779EF);
        mapControl(g, "+", mapX + mapW - 35, bodyY + 12, t);
        mapControl(g, "−", mapX + mapW - 35, bodyY + 43, t);
        mapControl(g, "+", mapX + mapW - 35, bodyY + 74, t);
        g.roundRect(0xFF171D24, mapX + mapW - 116, bodyY + bodyH - 27, 104, 17, 6);
        g.text("X 124     Z -340     1:4", mapX + mapW - 108, bodyY + bodyH - 23, 6.5F, 0xFF8794A3);
    }

    private static void performance(UiContext ui) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        float x = ui.contentX() + 20, y = ui.contentY() + 17 + (1 - ui.motion()) * 5;
        float w = ui.contentWidth() - 40, h = ui.contentHeight() - 34;
        kicker(g, "PERFORMANCE TELEMETRY", x, y, ui.accent());
        title(g, "Frame analyzer", x, y + 14, t);
        muted(g, "Measure first. Change settings second.", x, y + 43, t);
        ui.button("run-sample", "Run 3-second sample", "run-sample", x + w - 155, y + 1, 116, 29, PRIMARY, PILL);
        ui.button("export", "Export", "export", x + w - 34, y + 1, 34, 29, GHOST, ROUNDED);
        float metricY = y + 64, gap = 7, metricW = (w - gap * 3) / 4;
        metric(ui, "Average FPS", "143", "Example sampler output", x, metricY, metricW);
        metric(ui, "Frame time", "6.9 ms", "99th percentile 11.2ms", x + (metricW + gap), metricY, metricW);
        metric(ui, "UI renderer", "Idle", "Cached texture only", x + (metricW + gap) * 2, metricY, metricW);
        metric(ui, "Surface paints", Integer.toString(ui.paintCount() + 1),
            "Since this screen opened", x + (metricW + gap) * 3, metricY, metricW);
        float chartY = metricY + 73, bodyH = h - (chartY - y), processW = Math.min(195, w * .30F), chartW = w - processW - 9;
        ui.card(x, chartY, chartW, bodyH);
        g.textMedium("Frame time", x + 13, chartY + 13, 9, t.text());
        muted(g, "Example 60-second sample", x + 13, chartY + 27, t);
        float left = x + 14, right = x + chartW - 14, top = chartY + 55, bottom = chartY + bodyH - 32;
        for (int i = 0; i < 4; i++) g.line(0xFF25292E, .7F, left, top + (bottom - top) * i / 3, right, top + (bottom - top) * i / 3);
        g.dashedLine(0xFF59616B, 1.2F, 4, left, top + (bottom - top) * .68F, right, top + (bottom - top) * .68F);
        float previousX = left, previousY = bottom - 18;
        for (int i = 1; i <= 48; i++) {
            float px = left + (right - left) * i / 48;
            float wave = (float) (Math.sin(i * .45) * 13 + Math.sin(i * .13) * 11);
            float py = bottom - 28 - wave - i * .25F;
            g.line(ui.accent(), 2, previousX, previousY, px, py);
            previousX = px; previousY = py;
        }
        g.text("60s", left, bottom + 11, 6, t.mutedText());
        g.text("30s", (left + right) / 2, bottom + 11, 6, t.mutedText());
        g.text("now", right - 16, bottom + 11, 6, t.mutedText());
        float processX = x + chartW + 9;
        ui.card(processX, chartY, processW, bodyH);
        g.textMedium("Cost by system", processX + 13, chartY + 13, 9, t.text());
        costRow(g, "World render", .72F, "4.1", processX + 13, chartY + 48, processW - 26, ui.accent(), t);
        costRow(g, "Entities", .31F, "1.2", processX + 13, chartY + 79, processW - 26, ui.accent(), t);
        costRow(g, "Particles", .18F, "0.7", processX + 13, chartY + 110, processW - 26, ui.accent(), t);
        costRow(g, "Client UI", .05F, "0.1", processX + 13, chartY + 141, processW - 26, ui.accent(), t);
        g.roundRect(0xFF151B18, processX + 13, chartY + bodyH - 54, processW - 26, 40, 9);
        g.strokeRoundRect(0xFF2C3531, .8F, processX + 13, chartY + bodyH - 54, processW - 26, 40, 9);
        g.circle(0xFF48D994, processX + 24, chartY + bodyH - 34, 3);
        g.textMedium("UI is idle", processX + 35, chartY + bodyH - 45, 8, t.text());
        g.text("No queued surface rebuild", processX + 35, chartY + bodyH - 31, 6, t.mutedText());
    }

    private static void settings(UiContext ui) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        float x = ui.contentX() + 20, y = ui.contentY() + 17 + (1 - ui.motion()) * 5;
        float w = ui.contentWidth() - 40, h = ui.contentHeight() - 34;
        kicker(g, "PREFERENCES", x, y, ui.accent());
        title(g, "Interface settings", x, y + 14, t);
        muted(g, "Every native token and component can be overridden by the consuming mod.", x, y + 43, t);
        ui.button("save-settings", "Save changes", "save-settings", x + w - 88, y + 1, 88, 29, PRIMARY, PILL);
        float bodyY = y + 64, indexW = Math.min(155, w * .23F), bodyH = h - 64;
        ui.card(x, bodyY, indexW, 164);
        String[] indexes = {"Appearance", "Motion", "Accessibility", "Input", "Advanced"};
        for (int i = 0; i < indexes.length; i++) {
            if (i == 0) g.roundRect(0xFF232733, x + 8, bodyY + 8 + i * 30, indexW - 16, 27, 8);
            g.textMedium(indexes[i], x + 17, bodyY + 17 + i * 30, 8, i == 0 ? t.text() : t.mutedText());
        }
        float contentX = x + indexW + 9, contentW = w - indexW - 9;
        ui.card(contentX, bodyY, contentW, 103);
        g.textMedium("Shape language", contentX + 13, bodyY + 12, 10, t.text());
        muted(g, "Choose globally or override per component.", contentX + 13, bodyY + 27, t);
        float shapeY = bodyY + 44, shapeGap = 7, shapeW = (contentW - 26 - shapeGap * 3) / 4;
        shapeChoice(ui, "shape-square", "Square", contentX + 13, shapeY, shapeW, 48, 1, false);
        shapeChoice(ui, "shape-rounded", "Rounded", contentX + 13 + (shapeW + shapeGap), shapeY, shapeW, 48, 7, true);
        shapeChoice(ui, "shape-soft", "Soft", contentX + 13 + (shapeW + shapeGap) * 2, shapeY, shapeW, 48, 13, false);
        shapeChoice(ui, "shape-pill", "Pill", contentX + 13 + (shapeW + shapeGap) * 3, shapeY, shapeW, 48, 24, false);
        float motionY = bodyY + 112;
        ui.card(contentX, motionY, contentW, 166);
        g.textMedium("Motion", contentX + 13, motionY + 12, 10, t.text());
        muted(g, "Finite, event-driven animations that stop when complete.", contentX + 13, motionY + 27, t);
        settingDivider(g, contentX + 13, motionY + 45, contentW - 26, t);
        settingLabel(g, "Interface motion", "Page, hover and state transitions", contentX + 13, motionY + 54, t);
        ui.toggle("motion-toggle", "interface-motion", contentX + contentW - 45, motionY + 57, true);
        settingDivider(g, contentX + 13, motionY + 88, contentW - 26, t);
        settingLabel(g, "Motion intensity", "Scales duration and distance", contentX + 13, motionY + 98, t);
        ui.slider("motion-slider", "motion-intensity", contentX + contentW - 166, motionY + 103, 122, .65);
        g.text(Math.round(ui.numberValue("motion-intensity", .65) * 100) + "%",
            contentX + contentW - 36, motionY + 103, 7, t.mutedText());
        settingDivider(g, contentX + 13, motionY + 130, contentW - 26, t);
        settingLabel(g, "Reduced motion fallback", "Honors the operating system preference", contentX + 13, motionY + 139, t);
        ui.toggle("reduce-toggle", "reduced-motion", contentX + contentW - 45, motionY + 136, false);
        float previewY = motionY + 175;
        ui.card(contentX, previewY, contentW, bodyH - (previewY - bodyY));
        g.textMedium("Component preview", contentX + 13, previewY + 12, 10, t.text());
        muted(g, "Styles can coexist within the same document.", contentX + 13, previewY + 27, t);
        ui.button("preview-primary", "Primary", "preview-primary", contentX + 13, previewY + 43, 63, 29, PRIMARY, ROUNDED);
        ui.button("preview-secondary", "Secondary", "preview-secondary", contentX + 83, previewY + 43, 72, 29, SECONDARY, SOFT);
        ui.button("preview-ghost", "Ghost pill", "preview-ghost", contentX + 162, previewY + 43, 70, 29, GHOST, PILL);
        badge(g, "ONLINE", contentX + 240, previewY + 48, 0xFF75D69C, 0xFF152A20);
        ui.slider("preview-slider", "preview-slider", contentX + contentW - 116, previewY + 50, 96, .48);
    }

    private static void projectCard(UiContext ui, String id, String icon, String name, String subtitle, String meta,
                                    float x, float y, float w, float h, int iconBg, int iconFg, boolean installed) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        int fill = ui.hovered(id) ? UiCanvas.mix(t.surface(), t.raisedSurface(), .45F * ui.motion()) : t.surface();
        ui.card(x, y, w, h, t.mediumRadius(), fill);
        projectIcon(g, icon, x + 9, y + 10, 37, iconBg, iconFg, t);
        g.textMedium(name, x + 55, y + 11, 8.5F, t.text());
        g.text(subtitle, x + 55, y + 26, 6.5F, t.mutedText());
        g.text(meta, x + 55, y + 40, 6, 0xFF68716B);
        ui.button(id + "-button", installed ? "Installed" : "Install", installed ? "installed" : "install-" + id,
            x + w - 55, y + 16, 46, 25, installed ? SECONDARY : PRIMARY, ROUNDED);
        ui.hit(id, "open-" + id, x, y, w - 60, h);
    }

    private static void projectIcon(UiCanvas g, String letter, float x, float y, float size, int bg, int fg, UiTheme t) {
        g.roundRect(bg, x, y, size, size, Math.min(10, size / 4));
        g.strokeRoundRect(UiCanvas.mix(bg, t.border(), .5F), .7F, x, y, size, size, Math.min(10, size / 4));
        g.centeredText(letter, x + size / 2, y + size / 2, size * .34F, fg, Font.BOLD);
    }

    private static void updateRow(UiCanvas g, String icon, String name, String version, float x, float y, float w, int bg, UiTheme t) {
        g.line(0xFF252925, .7F, x, y - 5, x + w, y - 5);
        projectIcon(g, icon, x, y, 25, bg, t.text(), t);
        g.textMedium(name, x + 34, y + 2, 7.5F, t.text());
        g.text(version, x + 34, y + 14, 6, t.mutedText());
    }

    private static void search(UiCanvas g, float x, float y, float w, float h, UiTheme t) {
        g.roundRect(0xFF171A18, x, y, w, h, t.smallRadius());
        g.strokeRoundRect(t.border(), .7F, x, y, w, h, t.smallRadius());
        g.strokeCircle(t.mutedText(), 1, x + 12, y + 13, 3.5F);
        g.line(t.mutedText(), 1, x + 15, y + 16, x + 18, y + 19);
        g.text("Search projects", x + 24, y + 8, 7, t.mutedText());
    }

    private static void nativeItemSlot(UiContext ui, String id, String label, float x, float y, float w, float h, boolean selected) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        g.roundRect(0xFF1C2026, x, y, w, h, 9);
        g.strokeRoundRect(selected ? ui.accent() : 0xFF343942, selected ? 1.2F : .8F, x, y, w, h, 9);
        if (!label.isEmpty()) g.text(label, x + 7, y + h - 14, 6.5F, t.mutedText());
        ui.nativeSlot(id, x + 4, y + 4, w - 8, Math.max(16, h - (label.isEmpty() ? 8 : 18)));
    }

    private static void filter(UiCanvas g, String label, float x, float y, float w, boolean active, UiTheme t) {
        if (active) g.roundRect(0xFF292E35, x, y, w, 22, 6);
        g.centeredText(label, x + w / 2, y + 11, 6.5F, active ? t.text() : t.mutedText(), Font.PLAIN);
    }

    private static void waypointRow(UiContext ui, String title, String subtitle, float x, float y, float w, int color, boolean selected) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        if (selected) {
            g.roundRect(0xFF172230, x, y, w, 46, 9);
            g.strokeRoundRect(0xFF2F455D, .8F, x, y, w, 46, 9);
        }
        g.circle(color, x + 12, y + 21, 3.5F);
        g.circle(0xFF172230, x + 12, y + 21, 1.3F);
        g.textMedium(title, x + 28, y + 11, 8, t.text());
        g.text(subtitle, x + 28, y + 25, 6.5F, t.mutedText());
        g.text("~", x + w - 17, y + 16, 9, t.mutedText());
    }

    private static void marker(UiCanvas g, float x, float y, int bg, String symbol, int fg) {
        g.roundRect(bg, x - 13, y - 13, 26, 26, 8);
        g.strokeRoundRect(0xFF465363, .8F, x - 13, y - 13, 26, 26, 8);
        g.centeredText(symbol, x, y, 10, fg, Font.BOLD);
    }

    private static void mapControl(UiCanvas g, String symbol, float x, float y, UiTheme t) {
        g.roundRect(0xFF1B2128, x, y, 25, 25, 7);
        g.strokeRoundRect(0xFF343D47, .8F, x, y, 25, 25, 7);
        g.centeredText(symbol, x + 12.5F, y + 12.5F, 12, t.text(), Font.PLAIN);
    }

    private static void metric(UiContext ui, String label, String value, String sub, float x, float y, float w) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        ui.card(x, y, w, 64);
        g.text(label, x + 11, y + 10, 6.5F, t.mutedText());
        g.textMedium(value, x + 11, y + 24, 18, t.text());
        g.text(sub, x + 11, y + 49, 5.8F, 0xFF66706C);
    }

    private static void costRow(UiCanvas g, String label, float amount, String value, float x, float y, float w, int accent, UiTheme t) {
        g.text(label, x, y, 7, t.text());
        float barX = x + 64, barW = w - 84;
        g.roundRect(0xFF2B2F35, barX, y + 3, barW, 4, 2);
        g.roundRect(accent, barX, y + 3, barW * amount, 4, 2);
        g.text(value, x + w - 15, y, 6.5F, t.mutedText());
    }

    private static void shapeChoice(UiContext ui, String id, String label, float x, float y, float w, float h, float radius, boolean active) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        g.roundRect(0xFF1A1D23, x, y, w, h, radius);
        g.strokeRoundRect(active ? ui.accent() : t.border(), active ? 1.1F : .8F, x, y, w, h, radius);
        g.strokeRoundRect(active ? ui.accent() : t.mutedText(), 1.7F, x + w / 2 - 15, y + 10, 30, 13, radius);
        g.centeredText(label, x + w / 2, y + 35, 6.5F, active ? ui.accent() : t.mutedText(), Font.PLAIN);
        ui.hit(id, id, x, y, w, h);
    }

    private static void settingDivider(UiCanvas g, float x, float y, float w, UiTheme t) {
        g.line(t.border(), .7F, x, y, x + w, y);
    }

    private static void settingLabel(UiCanvas g, String title, String sub, float x, float y, UiTheme t) {
        g.textMedium(title, x, y, 7.5F, t.text());
        g.text(sub, x, y + 13, 6.5F, t.mutedText());
    }

    private static void badge(UiCanvas g, String value, float x, float y, int fg, int bg) {
        float width = g.textWidth(value, 6, Font.BOLD) + 12;
        g.roundRect(bg, x, y, width, 16, 8);
        g.strokeRoundRect(UiCanvas.mix(bg, fg, .35F), .7F, x, y, width, 16, 8);
        g.centeredText(value, x + width / 2, y + 8, 6, fg, Font.BOLD);
    }

    private static void kicker(UiCanvas g, String value, float x, float y, int accent) {
        g.textMedium(value, x, y, 7, accent);
    }

    private static void title(UiCanvas g, String value, float x, float y, UiTheme theme) {
        g.textMedium(value, x, y, 24, theme.text());
    }

    private static void muted(UiCanvas g, String value, float x, float y, UiTheme theme) {
        g.text(value, x, y, 7.5F, theme.mutedText());
    }

    /**
     * Item.getDefaultInstance() reads registry components, which are only bound once a world is
     * loaded, and allocating ten stacks per frame on the render thread is pure waste. Build them
     * once, lazily, and only while a player exists.
     */
    private static Map<String, ItemStack> demoStacks;

    private static Map<String, ItemStack> demoStacks() {
        Map<String, ItemStack> cached = demoStacks;
        if (cached != null) return cached;
        Map<String, ItemStack> built = new LinkedHashMap<>();
        try {
            put(built, "item-helmet", Items.DIAMOND_HELMET);
            put(built, "item-chest", Items.ELYTRA);
            put(built, "item-legs", Items.DIAMOND_LEGGINGS);
            put(built, "item-boots", Items.DIAMOND_BOOTS);
            put(built, "item-sword", Items.DIAMOND_SWORD);
            put(built, "item-totem", Items.TOTEM_OF_UNDYING);
            put(built, "hotbar-pick", Items.DIAMOND_PICKAXE);
            put(built, "hotbar-food", Items.GOLDEN_APPLE);
            put(built, "hotbar-pearl", Items.ENDER_PEARL);
            put(built, "hotbar-compass", Items.RECOVERY_COMPASS);
        } catch (RuntimeException e) {
            return Map.of();
        }
        demoStacks = built;
        return built;
    }

    private static void put(Map<String, ItemStack> target, String slot, Item item) {
        target.put(slot, item.getDefaultInstance());
    }

    private static void renderNativeLayer(NativeOverlayContext context) {
        if (!"loadout".equals(context.pageId())) return;
        var player = context.localPlayer();
        if (player == null) return;
        context.player("player", player);
        demoStacks().forEach(context::item);
    }
}
