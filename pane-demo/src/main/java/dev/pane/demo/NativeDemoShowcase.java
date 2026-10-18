package dev.pane.demo;

import dev.pane.ui.NativeOverlayContext;
import dev.pane.ui.UiCanvas;
import dev.pane.ui.UiBackdrop;
import dev.pane.ui.UiContext;
import dev.pane.ui.UiDialog;
import dev.pane.ui.UiDocument;
import dev.pane.ui.UiLayout;
import dev.pane.ui.UiMenuItem;
import dev.pane.ui.UiPage;
import dev.pane.ui.UiRect;
import dev.pane.ui.UiRichText;
import dev.pane.ui.UiScreen;
import dev.pane.ui.UiScreenOptions;
import dev.pane.ui.UiSelectOption;
import dev.pane.ui.UiSidebarShell;
import dev.pane.ui.UiTheme;
import dev.pane.ui.UiToast;
import dev.pane.ui.UiToastPlacement;
import dev.pane.ui.UiToastSettings;
import dev.pane.ui.UiToastStyle;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static dev.pane.ui.UiContext.ButtonStyle.GHOST;
import static dev.pane.ui.UiContext.ButtonStyle.PRIMARY;
import static dev.pane.ui.UiContext.ButtonStyle.SECONDARY;
import static dev.pane.ui.UiContext.Shape.PILL;
import static dev.pane.ui.UiContext.Shape.ROUNDED;
import static dev.pane.ui.UiContext.Shape.SOFT;

/** Five practical screens rendered by the lightweight native backend. */
public final class NativeDemoShowcase {
    private static final int DIRECTORY_PROJECTS = 10_000;
    private static final String[] DIRECTORY_NAMES = {
        "Sodium", "Iris Shaders", "Mod Menu", "Zoomify", "LambDynamicLights", "ImmediatelyFast",
        "FerriteCore", "Entity Culling", "AppleSkin", "Xaero's Minimap", "Continuity", "Indium",
        "BetterF3", "Jade", "Dynamic FPS", "No Chat Reports", "Mouse Tweaks", "Inventory Profiles Next"
    };
    private static final String[] DIRECTORY_PREFIXES = {
        "Copper", "Quiet", "Wanderer's", "Northern", "Tiny", "Practical", "Cobalt", "Lantern"
    };
    private static final String[] DIRECTORY_SUFFIXES = {
        "Tools", "Tweaks", "Library", "HUD", "Utilities", "Performance", "Companion", "Overlays"
    };
    private static final String[] DIRECTORY_CATEGORIES = {
        "Optimization", "Graphics", "Management", "Utility", "Adventure", "World", "Storage", "Social"
    };
    private static final String[][] SEARCH_OPTIONS = {
        {"Sodium", "Fast rendering for Minecraft"},
        {"Iris Shaders", "Shaders compatible with Sodium"},
        {"Zoomify", "Configurable zoom controls"},
        {"Mod Menu", "Manage installed mods"},
        {"LambDynamicLights", "Dynamic item and entity lighting"},
    };
    private static final UiRichText FORMATTED_DIRECTORY_MOTD = UiRichText.legacy(
        "§bFast rendering §7for §lmodern Minecraft §r§o— smooth, native, and ready ✦");
    private static final UiToastStyle COPY_TOAST = UiToastStyle.builder()
        .background(0xFF10263C).border(0xFF3A82BD).titleColor(0xFFE4F3FF).messageColor(0xFF9AC9ED)
        .iconBackground(0xFF1F5C8B).iconColor(0xFFFFFFFF).closeBackground(0xFF234A69).closeColor(0xFFE4F3FF)
        .radius(14).build();
    private static final UiToastStyle SHARE_TOAST = UiToastStyle.builder()
        .background(0xFF13291F).border(0xFF4EBF83).titleColor(0xFFE0F8E9).messageColor(0xFF9ACCAA)
        .iconBackground(0xFF256842).iconColor(0xFFFFFFFF).radius(4).build();
    private static final UiToastStyle REMOVE_TOAST = UiToastStyle.builder()
        .background(0xFF321A1E).border(0xFFD46770).titleColor(0xFFFFE6E8).messageColor(0xFFE3A7AD)
        .iconBackground(0xFF8D3742).iconColor(0xFFFFFFFF).closeBackground(0xFF5D2830).closeColor(0xFFFFE6E8)
        .radius(18).build();

    private NativeDemoShowcase() {}

    public static UiScreen createScreen() {
        return createScreen(Minecraft.getInstance().gui.screen());
    }

    public static UiScreen createScreen(Screen parent) {
        UiScreen[] holder = new UiScreen[1];
        UiScreenOptions options = UiScreenOptions.builder()
            .parent(parent)
            .backdrop(parent == null ? UiBackdrop.MINECRAFT : UiBackdrop.PARENT_BACKGROUND)
            .onKey((screen, event) -> {
                if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_F6) {
                    screen.setValue("raw-key-received", true);
                    return true;
                }
                if (!"discover".equals(screen.pageId())
                        || (event.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN
                        && event.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_UP)) return false;
                int current = (int) screen.numberValue("directory-key-selected", -1);
                if (current < 0) current = Math.max(0, screen.virtualFirstVisible("discover-directory"));
                int direction = event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ? 1 : -1;
                int selected = Math.max(0, Math.min(DIRECTORY_PROJECTS - 1, current + direction));
                screen.setValue("directory-key-selected", (double) selected);
                screen.scrollVirtualItemIntoView("discover-directory", selected);
                return true;
            })
            .onClick((screen, event) -> {
                if (!event.doubleClick()) return false;
                if (event.hasTarget("preview-secondary")) {
                    screen.setValue("double-click-received", true);
                    return true;
                }
                String target = event.targetId();
                if (target == null || !target.startsWith("directory-row-")) return false;
                try {
                    int index = Integer.parseInt(target.substring("directory-row-".length()));
                    if (index < 0 || index >= DIRECTORY_PROJECTS) return false;
                    screen.setValue("directory-double-clicked-index", (double) index);
                    return true;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            })
            .build();
        UiScreen screen = new UiScreen(document(), NativeDemoShowcase::renderNativeLayer,
            (type, key, value) -> handleEvent(holder[0], type, key, value), options);
        holder[0] = screen;
        return screen;
    }

    private static void handleEvent(UiScreen screen, String type, String key, String value) {
        if (type.equals("action") && key.equals("open-style-gallery")) {
            Minecraft.getInstance().gui.setScreen(StyleGallery.createScreen());
            return;
        }
        if (type.equals("action") && key.startsWith("open-directory-project:")) {
            try {
                screen.setValue("directory-opened-index", Integer.parseInt(key.substring(key.indexOf(':') + 1)));
            } catch (NumberFormatException ignored) { }
        }
        if (type.equals("action") && key.equals("toggle:consumer-owned")) {
            screen.setValue("reserved-action-received", true);
        }
        if (type.equals("action") && key.equals("remove-client-tag")) {
            screen.setValue("client-tag-removed", true);
        }
        if (type.equals("action") && key.equals("copy-waypoint")) {
            Minecraft.getInstance().keyboardHandler.setClipboard("124, 68, -340");
            screen.setValue("waypoint-menu-result", "Coordinates copied to clipboard");
            screen.toast(UiToast.builder("coordinates-copied", "Coordinates copied")
                .message("124, 68, -340 is on your clipboard").icon("✓").placement(UiToastPlacement.TOP_RIGHT)
                .style(COPY_TOAST).build());
        } else if (type.equals("action") && key.equals("share-waypoint")) {
            screen.setValue("waypoint-menu-result", "Waypoint shared with your party");
            screen.toast(UiToast.builder("waypoint-shared", "Waypoint shared")
                .message("Your party can see this route now").icon("→").durationMillis(5_000)
                .placement(UiToastPlacement.TOP_LEFT).style(SHARE_TOAST).dismissible(false).build());
        } else if (type.equals("action") && key.equals("remove-waypoint")) {
            screen.setValue("waypoint-menu-result", "Waypoint removed (demo only)");
            screen.toast(UiToast.builder("waypoint-removed", "Waypoint removed")
                .message("Demo data was not changed").icon("!").durationMillis(4_000)
                .placement(UiToastPlacement.BOTTOM_LEFT).style(REMOVE_TOAST).build());
        } else if (type.equals("action") && key.equals("reset-routes")) {
            screen.setValue("waypoint-menu-result", "Routes reset (demo only)");
            screen.toast(UiToast.builder("routes-reset", "Routes reset")
                .message("Demo route plan cleared").icon("↺").build());
        } else if (type.equals("action") && key.equals("cancel-reset-routes")) {
            screen.setValue("waypoint-menu-result", "Reset cancelled");
        }
        System.out.println("[Pane demo] " + type + " " + key + " " + value);
    }

    private static UiDocument document() {
        String initial = System.getProperty("pane.demo.page", "discover");
        return UiDocument.builder("Pane Showcase")
            .brand("Pane Lab")
            .theme(UiTheme.dark())
            .shell(UiSidebarShell.create()
                .caption("PANE UI LIBRARY")
                .sectionLabel("SHOWCASE")
                .footnote("H \u00b7 open")
                .statusIndicator(true))
            .pageShortcuts(true)
            .tooltipDelayMillis(650)
            .toastSettings(new UiToastSettings(UiToastPlacement.BOTTOM_RIGHT, 4, 16, 8, 240,
                UiToastStyle.builder().radius(10).build()))
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
        float galleryWidth = 64;
        float searchX = x + mainWidth - searchWidth - galleryWidth - 82;
        ui.textField("project-search", "project-search", "Search projects", searchX, y + 1, searchWidth, 28, "", 64);
        ui.tooltip("project-search", "Search projects; close matches are suggested.");
        ui.button("style-gallery", "Styles", "open-style-gallery", x + mainWidth - galleryWidth - 75, y + 1,
            galleryWidth, 28, SECONDARY, PILL);
        ui.tooltip("style-gallery", "Open five completely different native UI styles.");
        ui.button("update-all", "Update all", "update-all", x + mainWidth - 67, y + 1, 67, 28, PRIMARY, PILL);
        ui.tooltip("update-all", "Install all verified updates.");

        float bodyY = y + 64;
        float bodyH = h - 64;
        g.text("PROJECT", x + 11, bodyY + 7, 6.5F, t.mutedText(), Font.BOLD);
        g.text("CATEGORY", x + mainWidth - 153, bodyY + 7, 6.5F, t.mutedText(), Font.BOLD);
        g.text("10,000 RESULTS", x + mainWidth - 76, bodyY + 7, 6.5F, ui.accent(), Font.BOLD);
        float listY = bodyY + 22;
        float listH = bodyH - 22;
        ui.setValue("directory-painted-rows", 0D);
        ui.setValue("directory-first-visible", -1D);
        ui.setValue("directory-last-visible", -1D);
        ui.virtualList("discover-directory", new UiRect(x, listY, mainWidth, listH),
            DIRECTORY_PROJECTS, 47, 1, (list, item) -> {
                int painted = (int) list.numberValue("directory-painted-rows", 0);
                list.setValue("directory-painted-rows", (double) painted + 1);
                if (painted == 0) list.setValue("directory-first-visible", (double) item.index());
                list.setValue("directory-last-visible", (double) item.index());
                directoryRow(list, item.index(), item.bounds());
            });

        float sideX = x + mainWidth + 9;
        ui.card(sideX, bodyY, updateWidth, bodyH, t.mediumRadius(), 0xFF151815);
        g.textMedium("Updates", sideX + 11, bodyY + 12, 9, t.text());
        badge(g, "3 READY", sideX + updateWidth - 51, bodyY + 9, t.mutedText(), t.raisedSurface());
        updateRow(g, "I", "Iris", "1.9.4 → 1.9.5", sideX + 11, bodyY + 37, updateWidth - 22, 0xFF242B42, t);
        updateRow(g, "M", "Mod Menu", "18.0 → 18.1", sideX + 11, bodyY + 76, updateWidth - 22, 0xFF1F3732, t);
        updateRow(g, "Z", "Zoomify", "3.0.2 → 3.1", sideX + 11, bodyY + 115, updateWidth - 22, 0xFF37301D, t);
        g.text("Verified for this profile", sideX + 11, bodyY + bodyH - 20, 6.5F, 0xFF657068);
        searchSuggestions(ui, ui.stringValue("project-search", ""), searchX, y + 34, searchWidth);
    }

    private static void directoryRow(UiContext ui, int index, UiRect row) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        String name = directoryName(index);
        String id = "directory-row-" + index;
        boolean hovered = ui.hovered(id);
        if (hovered) g.roundRect(0xFF1A211D, row.x(), row.y(), row.width() - 5, row.height(), 7);
        g.line(hovered ? 0xFF33433A : 0xFF262C28, .65F,
            row.x(), row.y() + row.height(), row.x() + row.width() - 5, row.y() + row.height());
        int hue = index % 4;
        int[] backgrounds = {0xFF1D3323, 0xFF242B42, 0xFF37301D, 0xFF253035};
        int[] foregrounds = {0xFFC0E6CA, 0xFFB7CAFF, 0xFFFFE19C, 0xFFB9E7DB};
        projectIcon(g, name.substring(0, 1).toUpperCase(Locale.ROOT), row.x() + 8, row.y() + 6,
            35, backgrounds[hue], foregrounds[hue], t);
        g.textMedium(name, row.x() + 54, row.y() + 6, 8.5F, t.text());
        if (index == 0) {
            g.formattedText(FORMATTED_DIRECTORY_MOTD, row.x() + 54, row.y() + 20,
                6.3F, t.mutedText(), t.fontFamily());
        } else {
            g.text("A lightweight client mod built for modern Fabric profiles.",
                row.x() + 54, row.y() + 20, 6.3F, t.mutedText());
        }
        g.text(directoryDownloads(index) + " downloads  ·  Fabric 26.2  ·  Client",
            row.x() + 54, row.y() + 33, 6F, 0xFF718078);
        g.text(DIRECTORY_CATEGORIES[index % DIRECTORY_CATEGORIES.length],
            row.x() + row.width() - 153, row.y() + 18, 6.5F, t.mutedText());
        ui.hit(id, "open-directory-project:" + index, row.x(), row.y(), row.width() - 58, row.height());
        ui.tooltip(id, "Select this project");
        boolean installed = index == 2 || index == 6 || index == 8;
        String buttonId = index == 0 ? "install-sodium" : "directory-install-" + index;
        ui.button(buttonId, installed ? "Installed" : "Install", "install-directory:" + index,
            row.x() + row.width() - 53, row.y() + 11, 46, 25,
            installed ? SECONDARY : PRIMARY, ROUNDED);
    }

    private static String directoryName(int index) {
        if (index < DIRECTORY_NAMES.length) return DIRECTORY_NAMES[index];
        String prefix = DIRECTORY_PREFIXES[index % DIRECTORY_PREFIXES.length];
        String suffix = DIRECTORY_SUFFIXES[(index / DIRECTORY_PREFIXES.length) % DIRECTORY_SUFFIXES.length];
        return prefix + " " + suffix + " " + (index + 1);
    }

    private static String directoryDownloads(int index) {
        if (index == 0) return "41.2M";
        if (index < 12) return (18 - index) + "." + ((index * 7) % 10) + "M";
        long thousands = 120 + (index * 7919L) % 980_000L;
        if (thousands < 1_000) return thousands + "K";
        return (thousands / 1_000) + "." + ((thousands % 1_000) / 100) + "M";
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
        ui.tooltip("live-player-preview", "Minecraft renders this live player preview.",
            playerBoxX, playerBoxY, playerBoxW, playerBoxH);
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
        float slotGap = 7, slotW = (equipmentW - 28 - slotGap * 2) / 3;
        float slotsTop = bodyY + 36;
        float bridgeH = 38;
        float bridgeY = bodyY + topH - 14 - bridgeH;
        // Let the two slot rows take the room the card actually has, then pin the summary strip
        // to the bottom edge so a tall window does not leave the card looking half finished.
        float slotH = Math.max(55, Math.min(104, (bridgeY - 12 - slotsTop - slotGap) / 2));
        float row2 = slotsTop + slotH + slotGap;
        nativeItemSlot(ui, "item-helmet", "Helmet", equipmentX + 14, slotsTop, slotW, slotH, false);
        nativeItemSlot(ui, "item-chest", "Chest", equipmentX + 14 + slotW + slotGap, slotsTop, slotW, slotH, false);
        nativeItemSlot(ui, "item-legs", "Legs", equipmentX + 14 + (slotW + slotGap) * 2, slotsTop, slotW, slotH, false);
        nativeItemSlot(ui, "item-boots", "Boots", equipmentX + 14, row2, slotW, slotH, false);
        nativeItemSlot(ui, "item-sword", "Main hand", equipmentX + 14 + slotW + slotGap, row2, slotW, slotH, true);
        nativeItemSlot(ui, "item-totem", "Off hand", equipmentX + 14 + (slotW + slotGap) * 2, row2, slotW, slotH, false);
        g.roundRect(0xFF1B1E24, equipmentX + 14, bridgeY, equipmentW - 28, bridgeH, 9);
        g.polygon(ui.accent(), equipmentX + 24, bridgeY + 19, equipmentX + 31, bridgeY + 12,
            equipmentX + 38, bridgeY + 19, equipmentX + 31, bridgeY + 26);
        g.textMedium("Native bridge", equipmentX + 45, bridgeY + 10, 8, ui.accent());
        muted(g, "Items keep glint, durability and model overrides.", equipmentX + 45, bridgeY + 22, t);

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
        ui.menuButton("waypoint-actions", "Actions", x + w - 184, y + 1, 73, 29, SECONDARY, PILL,
            List.of(
                new UiMenuItem("copy", "Copy coordinates", "copy-waypoint"),
                new UiMenuItem("share", "Share with party", "share-waypoint"),
                new UiMenuItem("remove", "Remove waypoint", "remove-waypoint")
            ));
        ui.dialogButton("reset-routes", "Reset", x + w - 238, y + 1, 47, 29, GHOST, PILL,
            new UiDialog("Reset all routes?", "This clears the current route plan in the demo.",
                "Reset", "reset-routes", "Cancel", "cancel-reset-routes"));
        ui.button("new-waypoint", "+  New waypoint", "new-waypoint", x + w - 103, y + 1, 103, 29, PRIMARY, PILL);
        String menuResult = ui.stringValue("waypoint-menu-result", "");
        if (!menuResult.isEmpty()) muted(g, menuResult, x, y + 52, t);
        float bodyY = y + 64, listW = Math.min(215, w * .35F), bodyH = h - 64;
        ui.card(x, bodyY, listW, bodyH);
        g.roundRect(0xFF1B1E23, x + 11, bodyY + 11, listW - 22, 28, 8);
        filter(ui, "all", "All", x + 14, bodyY + 14, (listW - 28) / 3);
        filter(ui, "overworld", "Overworld", x + 14 + (listW - 28) / 3, bodyY + 14, (listW - 28) / 3);
        filter(ui, "nether", "Nether", x + 14 + (listW - 28) * 2 / 3, bodyY + 14, (listW - 28) / 3);
        String zone = ui.stringValue("waypoint-filter", "all");
        String[][] marks = {
            {"base", "overworld", "Main base", "124, 68, -340 · 182m"},
            {"portal", "nether", "Nether portal", "18, 64, -42 · 36m"},
            {"mine", "overworld", "Deep mine", "201, -42, -511 · 390m"},
            {"trading", "overworld", "Trading hall", "-88, 71, 640 · 712m"},
        };
        int[] colors = {ui.accent(), 0xFFA779EF, 0xFFD6A858, 0xFF72CC99};
        float rowY = bodyY + 49;
        int shown = 0;
        for (int i = 0; i < marks.length; i++) {
            if (!zone.equals("all") && !zone.equals(marks[i][1])) continue;
            waypointRow(ui, marks[i][0], marks[i][2], marks[i][3], x + 11, rowY, listW - 22, colors[i]);
            rowY += 49;
            shown++;
        }
        if (shown == 0) {
            g.centeredText("No waypoints in this dimension", x + listW / 2, bodyY + 90, 7, t.mutedText(), Font.PLAIN);
        }

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
        costRow(g, "Pane UI", .05F, "0.1", processX + 13, chartY + 141, processW - 26, ui.accent(), t);
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
        ui.dropdown("accent-preset", "accent-preset", x + w - 225, y + 1, 130, 29, "system", List.of(
            new UiSelectOption("system", "System accent"),
            new UiSelectOption("ocean", "Ocean blue"),
            new UiSelectOption("forest", "Forest green")
        ));
        ui.button("save-settings", "Save changes", "save-settings", x + w - 88, y + 1, 88, 29, PRIMARY, PILL);
        float bodyY = y + 64, indexW = Math.min(155, w * .23F), bodyH = h - 64;
        ui.card(x, bodyY, indexW, Math.min(164, bodyH));
        String[] indexes = {"Appearance", "Motion", "Accessibility", "Input", "Advanced"};
        for (int i = 0; i < indexes.length; i++) {
            String key = indexes[i].toLowerCase(java.util.Locale.ROOT);
            boolean active = key.equals(ui.stringValue("settings-section", "appearance"));
            float rowY = bodyY + 8 + i * 30;
            if (active || ui.hovered("section-" + key)) {
                g.roundRect(active ? 0xFF232733 : 0xFF1D2028, x + 8, rowY, indexW - 16, 27, 8);
            }
            g.textMedium(indexes[i], x + 17, rowY + 9, 8, active ? t.text() : t.mutedText());
            ui.selectable("section-" + key, "settings-section", key, x + 8, rowY, indexW - 16, 27);
        }
        float contentX = x + indexW + 9, contentW = w - indexW - 9;
        UiRect viewport = new UiRect(contentX, bodyY, contentW, bodyH);
        UiLayout form = ui.column(new UiRect(contentX, bodyY, contentW, 0)).gap(8);
        UiRect shapeCard = form.next(103);
        UiRect motionCard = form.next(166);
        UiRect previewCard = form.next(112);
        UiRect displayCard = form.next(112);
        UiRect inputCard = form.next(122);
        UiRect notesCard = form.next(152);

        ui.scroll("settings-content", viewport, form.contentHeight(), page -> {
            UiCanvas pageCanvas = page.canvas();
            page.card(shapeCard.x(), shapeCard.y(), shapeCard.width(), shapeCard.height());
            pageCanvas.textMedium("Shape language", shapeCard.x() + 13, shapeCard.y() + 12, 10, t.text());
            muted(pageCanvas, "Choose globally or override per component.", shapeCard.x() + 13, shapeCard.y() + 27, t);
            float shapeY = shapeCard.y() + 44, shapeGap = 7, shapeW = (contentW - 26 - shapeGap * 3) / 4;
            shapeChoice(page, "square", "Square", contentX + 13, shapeY, shapeW, 48, 1);
            shapeChoice(page, "rounded", "Rounded", contentX + 13 + (shapeW + shapeGap), shapeY, shapeW, 48, 7);
            shapeChoice(page, "soft", "Soft", contentX + 13 + (shapeW + shapeGap) * 2, shapeY, shapeW, 48, 13);
            shapeChoice(page, "pill", "Pill", contentX + 13 + (shapeW + shapeGap) * 3, shapeY, shapeW, 48, 24);

            page.card(motionCard.x(), motionCard.y(), motionCard.width(), motionCard.height());
            pageCanvas.textMedium("Motion", contentX + 13, motionCard.y() + 12, 10, t.text());
            muted(pageCanvas, "Finite, event-driven animations that stop when complete.", contentX + 13, motionCard.y() + 27, t);
            settingDivider(pageCanvas, contentX + 13, motionCard.y() + 45, contentW - 26, t);
            settingLabel(pageCanvas, "Interface motion", "Page, hover and state transitions", contentX + 13, motionCard.y() + 54, t);
            page.toggle("motion-toggle", "interface-motion", contentX + contentW - 45, motionCard.y() + 57, true);
            settingDivider(pageCanvas, contentX + 13, motionCard.y() + 88, contentW - 26, t);
            settingLabel(pageCanvas, "Motion intensity", "Scales duration and distance", contentX + 13, motionCard.y() + 98, t);
            page.slider("motion-slider", "motion-intensity", contentX + contentW - 166,
                motionCard.y() + 103, 122, 0, 100, 65);
            pageCanvas.text(Math.round(page.numberValue("motion-intensity", 65)) + "%",
                contentX + contentW - 36, motionCard.y() + 103, 7, t.mutedText());
            settingDivider(pageCanvas, contentX + 13, motionCard.y() + 130, contentW - 26, t);
            settingLabel(pageCanvas, "Reduced motion fallback", "Honors the operating system preference", contentX + 13, motionCard.y() + 139, t);
            page.toggle("reduce-toggle", "reduced-motion", contentX + contentW - 45, motionCard.y() + 136, false);

            page.card(previewCard.x(), previewCard.y(), previewCard.width(), previewCard.height());
            pageCanvas.textMedium("Component preview", contentX + 13, previewCard.y() + 12, 10, t.text());
            muted(pageCanvas, "Styles can coexist within the same document.", contentX + 13, previewCard.y() + 27, t);
            UiContext.Shape shape = chosenShape(page);
            page.button("preview-primary", "Primary", "toggle:consumer-owned", contentX + 13, previewCard.y() + 43, 63, 29, PRIMARY, shape);
            page.button("preview-secondary", "Secondary", "preview-secondary", contentX + 83, previewCard.y() + 43, 72, 29, SECONDARY, shape);
            page.button("preview-ghost", "Ghost", "preview-ghost", contentX + 162, previewCard.y() + 43, 70, 29, GHOST, shape);
            badge(pageCanvas, "ONLINE", contentX + 240, previewCard.y() + 48, 0xFF75D69C, 0xFF152A20);
            page.slider("preview-slider", "preview-slider", contentX + contentW - 116, previewCard.y() + 50, 96, .48);

            page.card(displayCard.x(), displayCard.y(), displayCard.width(), displayCard.height());
            pageCanvas.textMedium("Display", contentX + 13, displayCard.y() + 12, 10, t.text());
            muted(pageCanvas, "A real long-form setting group — scroll to keep going.", contentX + 13, displayCard.y() + 27, t);
            settingDivider(pageCanvas, contentX + 13, displayCard.y() + 45, contentW - 26, t);
            settingLabel(pageCanvas, "Compact density", "Tighter spacing for smaller windows", contentX + 13, displayCard.y() + 54, t);
            page.toggle("density-toggle", "compact-density", contentX + contentW - 45, displayCard.y() + 57, false);
            settingDivider(pageCanvas, contentX + 13, displayCard.y() + 88, contentW - 26, t);
            settingLabel(pageCanvas, "Contrast", "Strength of borders and dividers", contentX + 13, displayCard.y() + 96, t);
            page.slider("contrast-slider", "contrast", contentX + contentW - 166, displayCard.y() + 96, 122, .5);

            page.card(inputCard.x(), inputCard.y(), inputCard.width(), inputCard.height());
            pageCanvas.textMedium("Input", contentX + 13, inputCard.y() + 12, 10, t.text());
            muted(pageCanvas, "Scrolling, pointer editing, and keyboard focus share the same layout coordinates.", contentX + 13, inputCard.y() + 27, t);
            settingDivider(pageCanvas, contentX + 13, inputCard.y() + 46, contentW - 26, t);
            settingLabel(pageCanvas, "Scroll speed", "How far each wheel step moves", contentX + 13, inputCard.y() + 56, t);
            page.slider("scroll-speed", "scroll-speed", contentX + contentW - 166, inputCard.y() + 59, 122, .5);
            settingDivider(pageCanvas, contentX + 13, inputCard.y() + 91, contentW - 26, t);
            settingLabel(pageCanvas, "Developer tools", "Show layout bounds in a later pass", contentX + 13, inputCard.y() + 100, t);
            page.toggle("dev-tools", "developer-tools", contentX + contentW - 45, inputCard.y() + 101, false);

            page.card(notesCard.x(), notesCard.y(), notesCard.width(), notesCard.height());
            pageCanvas.textMedium("Project notes", contentX + 13, notesCard.y() + 12, 10, t.text());
            muted(pageCanvas, "A real soft-wrapped native editor, not a painted mockup.",
                contentX + 13, notesCard.y() + 27, t);
            page.textArea("project-notes", "project-notes", "Write a short release note...",
                contentX + 13, notesCard.y() + 45, contentW - 26, 66, "", 1000);
            float tagX = contentX + 13;
            if (!page.booleanValue("client-tag-removed", false)) {
                tagX += page.tag("tag-client", "client", "remove-client-tag", tagX,
                    notesCard.y() + 119, 21, 0xFF222C29, 0xFF8BD7B0) + 6;
            }
            page.tag("tag-performance", "performance", "remove-performance-tag", tagX,
                notesCard.y() + 119, 21, 0xFF22272D, t.mutedText());
        });
    }

    private static void projectCard(UiContext ui, String id, String icon, String name, String subtitle, String meta,
                                    float x, float y, float w, float h, int iconBg, int iconFg, boolean installed) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        int fill = ui.hovered(id) ? UiCanvas.mix(t.surface(), t.raisedSurface(), .45F * ui.interactionMotion()) : t.surface();
        ui.card(x, y, w, h, t.mediumRadius(), fill);
        float mid = y + h / 2;
        projectIcon(g, icon, x + 9, mid - 18, 37, iconBg, iconFg, t);
        g.textMedium(name, x + 55, mid - 18, 8.5F, t.text());
        g.text(subtitle, x + 55, mid - 3, 6.5F, t.mutedText());
        g.text(meta, x + 55, mid + 11, 6, 0xFF68716B);
        boolean on = ui.booleanValue("installed-" + id, installed);
        ui.toggleButton(id + "-button", "installed-" + id, "Install", "Installed",
            x + w - 55, mid - 12, 46, 25, installed, PRIMARY, SECONDARY, ROUNDED);
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

    private static void searchSuggestions(UiContext ui, String query, float x, float y, float width) {
        if (!ui.focused("project-search") || query == null || query.isBlank()) return;
        List<SearchMatch> matches = searchMatches(query);
        if (matches.isEmpty()) return;
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        float rowHeight = 34, height = 22 + matches.size() * rowHeight + 6;
        g.shadow(0x66000000, 8, 0, 3, x, y, width, height, t.smallRadius());
        g.roundRect(0xFF1A1E24, x, y, width, height, t.smallRadius());
        g.strokeRoundRect(t.border(), .8F, x, y, width, height, t.smallRadius());
        SearchMatch first = matches.getFirst();
        g.text(first.correction() ? "DID YOU MEAN" : "SUGGESTIONS", x + 10, y + 7, 6.5F,
            first.correction() ? ui.accent() : t.mutedText(), Font.BOLD);
        for (int i = 0; i < matches.size(); i++) {
            SearchMatch match = matches.get(i);
            float rowY = y + 22 + i * rowHeight;
            String id = "search-suggestion-" + match.label().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            if (ui.hovered(id) || ui.focused(id)) g.roundRect(0xFF242B34, x + 4, rowY, width - 8, rowHeight - 2, 6);
            g.text(match.label(), x + 10, rowY + 5, 8, t.text(), Font.BOLD);
            g.text(match.description(), x + 10, rowY + 18, 6.5F, t.mutedText());
            ui.selectable(id, "project-search", match.label(), x + 4, rowY, width - 8, rowHeight - 2);
        }
    }

    private static List<SearchMatch> searchMatches(String query) {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<SearchMatch> matches = new ArrayList<>();
        for (String[] option : SEARCH_OPTIONS) {
            String name = option[0].toLowerCase(Locale.ROOT);
            String description = option[1].toLowerCase(Locale.ROOT);
            int distance = editDistance(needle, name);
            int score;
            if (name.startsWith(needle)) score = 0;
            else if (name.contains(needle) || description.contains(needle)) score = 10;
            else if (distance <= Math.max(2, needle.length() / 3)) score = 20 + distance;
            else continue;
            matches.add(new SearchMatch(option[0], option[1], score, score >= 20));
        }
        matches.sort(Comparator.comparingInt(SearchMatch::score).thenComparing(SearchMatch::label));
        return matches.size() <= 3 ? matches : matches.subList(0, 3);
    }

    private static int editDistance(String first, String second) {
        int[] previous = new int[second.length() + 1];
        for (int j = 0; j <= second.length(); j++) previous[j] = j;
        for (int i = 1; i <= first.length(); i++) {
            int[] current = new int[second.length() + 1];
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int replacement = previous[j - 1] + (first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), replacement);
            }
            previous = current;
        }
        return previous[second.length()];
    }

    private record SearchMatch(String label, String description, int score, boolean correction) {}

    private static void nativeItemSlot(UiContext ui, String id, String label, float x, float y, float w, float h, boolean selected) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        g.roundRect(0xFF1C2026, x, y, w, h, 9);
        g.strokeRoundRect(selected ? ui.accent() : 0xFF343942, selected ? 1.2F : .8F, x, y, w, h, 9);
        if (!label.isEmpty()) g.text(label, x + 7, y + h - 14, 6.5F, t.mutedText());
        ui.nativeSlot(id, x + 4, y + 4, w - 8, Math.max(16, h - (label.isEmpty() ? 8 : 18)));
    }

    private static void filter(UiContext ui, String value, String label, float x, float y, float w) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        boolean active = value.equals(ui.stringValue("waypoint-filter", "all"));
        if (active) g.roundRect(0xFF292E35, x, y, w, 22, 6);
        else if (ui.hovered("filter-" + value)) g.roundRect(0xFF212530, x, y, w, 22, 6);
        g.centeredText(label, x + w / 2, y + 11, 6.5F, active ? t.text() : t.mutedText(), Font.PLAIN);
        ui.selectable("filter-" + value, "waypoint-filter", value, x, y, w, 22);
    }

    private static void waypointRow(UiContext ui, String id, String title, String subtitle, float x, float y, float w, int color) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        boolean selected = id.equals(ui.stringValue("waypoint", "base"));
        if (selected) {
            g.roundRect(0xFF172230, x, y, w, 46, 9);
            g.strokeRoundRect(0xFF2F455D, .8F, x, y, w, 46, 9);
        } else if (ui.hovered("wp-" + id)) {
            g.roundRect(0xFF161A20, x, y, w, 46, 9);
        }
        ui.selectable("wp-" + id, "waypoint", id, x, y, w, 46);
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

    private static void shapeChoice(UiContext ui, String value, String label, float x, float y, float w, float h, float radius) {
        UiCanvas g = ui.canvas(); UiTheme t = ui.theme();
        boolean active = value.equals(ui.stringValue("shape", "rounded"));
        boolean hover = ui.hovered("shape-" + value);
        g.roundRect(hover && !active ? 0xFF20242B : 0xFF1A1D23, x, y, w, h, radius);
        g.strokeRoundRect(active ? ui.accent() : t.border(), active ? 1.1F : .8F, x, y, w, h, radius);
        g.strokeRoundRect(active ? ui.accent() : t.mutedText(), 1.7F, x + w / 2 - 15, y + 10, 30, 13, radius);
        g.centeredText(label, x + w / 2, y + 35, 6.5F, active ? ui.accent() : t.mutedText(), Font.PLAIN);
        ui.selectable("shape-" + value, "shape", value, x, y, w, h);
    }

    /** The shape family the Settings page is currently demonstrating. */
    private static UiContext.Shape chosenShape(UiContext ui) {
        return switch (ui.stringValue("shape", "rounded")) {
            case "square" -> UiContext.Shape.SQUARE;
            case "soft" -> SOFT;
            case "pill" -> PILL;
            default -> ROUNDED;
        };
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
