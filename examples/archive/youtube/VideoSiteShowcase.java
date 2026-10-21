package dev.pane.demo;

import dev.pane.ui.UiCanvas;
import dev.pane.ui.UiContext;
import dev.pane.ui.UiDialog;
import dev.pane.ui.UiDialogStyle;
import dev.pane.ui.UiDocument;
import dev.pane.ui.UiMenuItem;
import dev.pane.ui.UiMenuStyle;
import dev.pane.ui.UiIcon;
import dev.pane.ui.UiPage;
import dev.pane.ui.UiRect;
import dev.pane.ui.UiScreen;
import dev.pane.ui.UiShell;
import dev.pane.ui.UiTheme;
import dev.pane.ui.UiToast;
import dev.pane.ui.UiToastPlacement;
import dev.pane.ui.UiToastSettings;
import dev.pane.ui.UiToastStyle;
import java.awt.Font;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A native consumer-page stress test inspired by the desktop shape of YouTube.
 * It deliberately uses local sample data and drawn thumbnail art: this validates Pane's
 * customization and interaction model without embedding a browser or copying a live website.
 */
public final class VideoSiteShowcase {
    private static final int BACKGROUND = 0xFF0F0F0F;
    private static final int RAISED = 0xFF272727;
    private static final int TEXT = 0xFFF1F1F1;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int RED = 0xFFFF0033;
    private static final String FONT = "pane-video-roboto";
    private static final String[] TOPICS = {
        "All", "Gaming", "Music", "Coding", "Minecraft", "Live", "Design", "Mixes", "News"
    };
    private static final String[] NAV = {"Home", "Shorts", "Subscriptions", "You", "History"};
    private static final Video[] VIDEOS = {
        new Video("Building a tiny UI library from scratch", "Pane Labs", "18K views", "2 days ago", "Coding", "16:42", 0xFF3478F6, 0xFF0D2047),
        new Video("I survived 100 days with one button", "Block Theory", "421K views", "1 week ago", "Minecraft", "22:08", 0xFF39A76A, 0xFF103224),
        new Video("A quiet room to think in", "mira makes", "96K views", "3 days ago", "Design", "12:31", 0xFFF29B65, 0xFF51241C),
        new Video("The playlist that makes code feel easy", "lowlight radio", "1.3M views", "5 months ago", "Music", "1:02:09", 0xFF9A67E5, 0xFF2F184D),
        new Video("What native UI gets right", "dev field notes", "38K views", "1 day ago", "Coding", "9:48", 0xFF57B8CD, 0xFF103745),
        new Video("This redstone door should not work", "Block Theory", "713K views", "4 days ago", "Minecraft", "18:04", 0xFFE35B3C, 0xFF482015),
        new Video("LATE NIGHT // deep focus mix", "lowlight radio", "2.1M views", "2 weeks ago", "Music", "47:15", 0xFF386DCF, 0xFF141B42),
        new Video("Making a feed feel intentional", "mira makes", "82K views", "6 days ago", "Design", "14:22", 0xFFE4B64D, 0xFF4F3917),
        new Video("A speedrun but every rule is backwards", "pixel sprint", "287K views", "8 hours ago", "Gaming", "11:06", 0xFFD55478, 0xFF4E152C),
        new Video("How I plan a new mod", "Pane Labs", "29K views", "3 weeks ago", "Coding", "20:11", 0xFF63A7F5, 0xFF122942),
        new Video("Live: fixing a village one idea at a time", "Block Theory", "8.4K watching", "Streamed 1 hour ago", "Live", "LIVE", 0xFFDD3D45, 0xFF47151C),
        new Video("Small worlds, big atmosphere", "mira makes", "140K views", "2 months ago", "Design", "10:17", 0xFF67B790, 0xFF163E2A)
    };

    private VideoSiteShowcase() {}

    public static UiScreen createScreen() {
        registerRoboto();
        UiScreen[] holder = new UiScreen[1];
        UiTheme theme = new UiTheme(BACKGROUND, BACKGROUND, RAISED, 0xFF303030, TEXT, MUTED, RED,
            0xFFFFFFFF, 14, 16, 20, 1, 1, FONT);
        UiScreen screen = new UiScreen(UiDocument.builder("Native Video Site Test")
            .brand("Pane")
            .theme(theme)
            .shell(UiShell.NONE)
            .tooltipDelayMillis(550)
            .toastSettings(new UiToastSettings(UiToastPlacement.BOTTOM_RIGHT, 3, 20, 8, 180))
            .page(new UiPage("home", "Home", "", RED, VideoSiteShowcase::paint))
            .build(), null, (type, key, value) -> handleEvent(holder[0], type, key, value));
        holder[0] = screen;
        return screen;
    }

    private static void registerRoboto() {
        InputStream source = VideoSiteShowcase.class.getResourceAsStream("/assets/pane_demo/fonts/Roboto-Variable.ttf");
        if (source != null) UiCanvas.registerFont(FONT, source);
    }

    private static void handleEvent(UiScreen screen, String type, String key, String value) {
        if (screen == null || !"action".equals(type)) return;
        if (key.startsWith("video-open:")) {
            String id = key.substring("video-open:".length());
            screen.setValue("video-selected", id);
            Video video = video(id);
            screen.toast(toast("Playing " + (video == null ? "video" : video.title), "Demo interaction only — no browser involved."));
            return;
        }
        if (key.startsWith("video-menu:")) {
            String action = key.substring("video-menu:".length());
            String title = action.startsWith("save") ? "Saved to Watch later"
                : action.startsWith("queue") ? "Added to queue" : "Not interested";
            screen.toast(toast(title, "This card menu is a normal Pane menu."));
            return;
        }
        if (key.equals("clear-history")) {
            screen.setValue("history-cleared", true);
            screen.toast(toast("History cleared", "The local sample history is now empty."));
        }
    }

    private static UiToast toast(String title, String message) {
        return UiToast.builder("video-site-action", title).message(message).icon("▶")
            .style(UiToastStyle.builder().background(0xFF272727).border(0xFF454545).titleColor(TEXT)
                .messageColor(MUTED).iconBackground(RED).iconColor(0xFFFFFFFF).closeBackground(0xFF3C3C3C)
                .closeColor(TEXT).radius(14).build()).build();
    }

    private static void paint(UiContext ui) {
        UiCanvas g = ui.canvas();
        float w = ui.width(), h = ui.height();
        g.fill(BACKGROUND, 0, 0, w, h);
        float headerH = 54;
        float sidebarW = w < 580 ? 68 : 174;
        header(ui, sidebarW, headerH);
        sidebar(ui, sidebarW, headerH);
        float contentX = sidebarW + 16;
        float contentW = Math.max(180, w - contentX - 18);
        chips(ui, contentX, headerH + 10, contentW);
        feed(ui, contentX, headerH + 56, contentW, Math.max(80, h - headerH - 62));
    }

    private static void header(UiContext ui, float sidebarW, float headerH) {
        UiCanvas g = ui.canvas();
        float w = ui.width();
        g.fill(BACKGROUND, 0, 0, w, headerH);
        // Hamburger, play mark, and wordmark are drawn primitives so the whole header remains native.
        g.line(TEXT, 1.4F, 18, 19, 34, 19); g.line(TEXT, 1.4F, 18, 25, 34, 25); g.line(TEXT, 1.4F, 18, 31, 34, 31);
        ui.hit("video-menu-toggle", "video-menu-toggle", 12, 11, 30, 30);
        g.roundRect(RED, 50, 17, 25, 18, 5);
        g.polygon(0xFFFFFFFF, 60, 21, 60, 31, 68, 26);
        g.text("YouTube", 80, 19, 17, TEXT, Font.BOLD, FONT);
        g.text("NATIVE TEST", 81, 36, 5.5F, MUTED, Font.BOLD, FONT);

        float searchW = Math.max(120, Math.min(460, w * .42F));
        float searchX = Math.max(sidebarW + 14, (w - searchW) * .5F - 24);
        ui.textField("video-search", "video-search", "Search", searchX, 12, searchW, 30, "", 90);
        g.roundRect(RAISED, searchX + searchW + 4, 12, 42, 30, 15);
        g.circle(TEXT, searchX + searchW + 21, 25, 5);
        g.line(TEXT, 1.4F, searchX + searchW + 25, 29, searchX + searchW + 30, 34);
        ui.hit("video-search-button", "video-search-button", searchX + searchW + 4, 12, 42, 30);
        ui.tooltip("video-search-button", "Results update while you type.");

        float rightX = w - 76;
        ui.paintedMenuButton("video-account", "Account options", rightX, 12, 30, 30, 15, true,
            List.of(new UiMenuItem("account", "This is a local demo", "video-menu:account")), UiMenuStyle.defaults(),
            (canvas,state)->{
                canvas.roundRect(state.pressed()?0xFF404040:state.hovered()?0xFF353535:RAISED,rightX,12,30,30,15);
                UiIcon.MORE_VERTICAL.draw(canvas,TEXT,rightX+7,19,16,1.3F);
            });
        g.circle(0xFF7A91B8, w - 25, 27, 15); g.centeredText("P", w - 25, 27, 12, 0xFFFFFFFF, Font.BOLD, FONT);
    }

    private static void sidebar(UiContext ui, float sidebarW, float headerH) {
        UiCanvas g = ui.canvas();
        float h = ui.height();
        g.fill(BACKGROUND, 0, headerH, sidebarW, h - headerH);
        String selected = ui.stringValue("video-nav", "Home");
        for (int i = 0; i < NAV.length; i++) {
            String item = NAV[i];
            float y = headerH + 14 + i * (sidebarW < 100 ? 58 : 43);
            boolean on = selected.equals(item);
            if (on) g.roundRect(0xFF272727, 8, y, sidebarW - 16, sidebarW < 100 ? 48 : 34, 10);
            String icon = switch (item) { case "Home" -> "⌂"; case "Shorts" -> "S"; case "Subscriptions" -> "▣"; case "You" -> "◉"; default -> "◷"; };
            if (sidebarW < 100) {
                g.centeredText(icon, sidebarW / 2, y + 15, 14, TEXT, on ? Font.BOLD : Font.PLAIN, FONT);
                g.centeredText(item, sidebarW / 2, y + 31, 6.5F, TEXT, on ? Font.BOLD : Font.PLAIN, FONT);
            } else {
                g.text(icon, 22, y + 10, 13, TEXT, on ? Font.BOLD : Font.PLAIN, FONT);
                g.text(item, 48, y + 11, 9, TEXT, on ? Font.BOLD : Font.PLAIN, FONT);
            }
            ui.selectable("video-nav-" + i, "video-nav", item, 8, y, sidebarW - 16, sidebarW < 100 ? 48 : 34);
        }
        if (sidebarW >= 100) {
            g.line(0xFF303030, .7F, 12, headerH + 245, sidebarW - 12, headerH + 245);
            g.text("Subscriptions", 18, headerH + 265, 9, TEXT, Font.BOLD, FONT);
            g.circle(0xFFAA7467, 24, headerH + 286, 7); g.text("Block Theory", 38, headerH + 280, 8, TEXT, Font.PLAIN, FONT);
            g.circle(0xFF6388B4, 24, headerH + 309, 7); g.text("Pane Labs", 38, headerH + 303, 8, TEXT, Font.PLAIN, FONT);
        }
    }

    private static void chips(UiContext ui, float x, float y, float width) {
        UiCanvas g = ui.canvas();
        String selected = ui.stringValue("video-topic", "All");
        float cursor = x;
        for (int i = 0; i < TOPICS.length; i++) {
            String topic = TOPICS[i];
            float chipW = Math.max(32, g.textWidth(topic, 8.5F, Font.BOLD, FONT) + 20);
            if (cursor + chipW > x + width) break;
            boolean on = selected.equals(topic);
            g.roundRect(on ? TEXT : RAISED, cursor, y, chipW, 28, 8);
            g.centeredText(topic, cursor + chipW / 2, y + 14, 8.5F, on ? 0xFF0F0F0F : TEXT, Font.BOLD, FONT);
            ui.selectable("video-topic-" + i, "video-topic", topic, cursor, y, chipW, 28);
            cursor += chipW + 8;
        }
    }

    private static void feed(UiContext ui, float x, float y, float width, float height) {
        UiCanvas g = ui.canvas();
        String nav = ui.stringValue("video-nav", "Home");
        String query = ui.stringValue("video-search", "").trim();
        String topic = ui.stringValue("video-topic", "All");
        if (nav.equals("History") && ui.booleanValue("history-cleared", false)) {
            g.text("Your watch history is empty", x, y + 20, 20, TEXT, Font.BOLD, FONT);
            g.text("Videos you watch will appear here.", x, y + 49, 10, MUTED, Font.PLAIN, FONT);
            return;
        }
        String heading = query.isEmpty() ? (nav.equals("Home") ? "Recommended" : nav) : "Search results";
        g.text(heading, x, y + 2, 15, TEXT, Font.BOLD, FONT);
        if (!query.isEmpty()) g.text("for “" + ellipsize(query, 36) + "”", x + g.textWidth(heading, 15, Font.BOLD, FONT) + 7, y + 5, 9, MUTED, Font.PLAIN, FONT);
        if (nav.equals("History")) {
            ui.dialogButton("video-clear-history", "Clear all watch history", x + width - 158, y - 8, 158, 27,
                UiContext.ButtonStyle.GHOST, UiContext.Shape.PILL,
                new UiDialog("Clear watch history?", "This only clears the local sample history in this Pane reference screen.",
                    "Clear history", "clear-history", "Cancel", "clear-history-cancel", UiDialogStyle.builder()
                        .backdrop(0xB8000000).surface(0xFF212121).border(0xFF454545).titleColor(TEXT).messageColor(MUTED)
                        .cancelBackground(0xFF383838).cancelBorder(0xFF505050).cancelText(TEXT).confirmBackground(RED)
                        .confirmText(0xFFFFFFFF).radius(14).build()));
        }

        List<Video> visible = filtered(nav, topic, query);
        if (visible.isEmpty()) {
            g.text("No videos found", x, y + 54, 18, TEXT, Font.BOLD, FONT);
            g.text("Try a different search or topic.", x, y + 78, 9, MUTED, Font.PLAIN, FONT);
            return;
        }
        int columns = width >= 950 ? 4 : width >= 650 ? 3 : width >= 380 ? 2 : 1;
        float gap = 14;
        float cardW = (width - gap * (columns - 1)) / columns;
        float thumbH = Math.max(72, cardW * .55F);
        float cardH = thumbH + 64;
        int rows = (visible.size() + columns - 1) / columns;
        UiRect viewport = new UiRect(x, y + 28, width, Math.max(42, height - 28));
        ui.virtualList("video-feed", viewport, rows, cardH, 18, (page, item) -> {
            int row = item.index();
            for (int col = 0; col < columns; col++) {
                int i = row * columns + col;
                if (i >= visible.size()) break;
                Video video = visible.get(i);
                float cx = x + col * (cardW + gap);
                float cy = item.bounds().y();
                videoCard(page, video, i, cx, cy, cardW, thumbH);
            }
        });
    }

    private static void videoCard(UiContext ui, Video video, int index, float x, float y, float width, float thumbH) {
        UiCanvas g = ui.canvas();
        boolean hover = ui.hovered("video-card-" + video.id());
        float radius = 11;
        g.withRoundedClip(x, y, width, thumbH, radius, () -> {
            g.gradientRect(video.dark, video.color, 24, x, y, width, thumbH, radius);
            float sx = x + width * (.12F + (index % 3) * .08F);
            float sy = y + thumbH * (.18F + (index % 2) * .12F);
            g.circle(UiCanvas.fade(0xFFFFFFFF, .18F), sx, sy, Math.min(width, thumbH) * .27F);
            g.roundRect(UiCanvas.fade(0xFF000000, .22F), x + width * .06F, y + thumbH * .68F, width * .70F, thumbH * .12F, 4);
            g.text(video.topic.toUpperCase(Locale.ROOT), x + width * .09F, y + thumbH * .70F, Math.max(5.5F, thumbH * .075F), 0xFFFFFFFF, Font.BOLD, FONT);
            g.polygon(UiCanvas.fade(0xFFFFFFFF, .92F), x + width * .53F, y + thumbH * .36F, x + width * .53F, y + thumbH * .62F, x + width * .72F, y + thumbH * .49F);
        });
        if (hover) g.strokeRoundRect(0xFFFFFFFF, 1.2F, x, y, width, thumbH, radius);
        float durationW = Math.max(25, g.textWidth(video.duration, 7, Font.BOLD, FONT) + 9);
        g.roundRect(0xDD000000, x + width - durationW - 5, y + thumbH - 19, durationW, 14, 3);
        g.centeredText(video.duration, x + width - durationW / 2 - 5, y + thumbH - 12, 7, 0xFFFFFFFF, Font.BOLD, FONT);
        g.circle(video.color, x + 17, y + thumbH + 22, 14);
        g.centeredText(video.channel.substring(0, 1).toUpperCase(Locale.ROOT), x + 17, y + thumbH + 22, 10, 0xFFFFFFFF, Font.BOLD, FONT);
        float textX = x + 39;
        g.text(ellipsize(video.title, Math.max(18, (int) (width / 6.8F))), textX, y + thumbH + 10, 9, TEXT, Font.BOLD, FONT);
        g.text(video.channel, textX, y + thumbH + 27, 8, MUTED, Font.PLAIN, FONT);
        g.text(video.views + " · " + video.when, textX, y + thumbH + 40, 8, MUTED, Font.PLAIN, FONT);
        ui.hit("video-card-" + video.id(), "video-open:" + video.id(), x, y, width - 25, thumbH + 57);
        ui.paintedMenuButton("video-more-" + video.id(), "Options for " + video.title(), x + width - 25, y + thumbH, 25, 27,
            12, true,
            List.of(new UiMenuItem("queue", "Add to queue", "video-menu:queue-" + video.id()),
                new UiMenuItem("save", "Save to Watch later", "video-menu:save-" + video.id()),
                new UiMenuItem("hide", "Not interested", "video-menu:hide-" + video.id())), UiMenuStyle.defaults(),
            (canvas,state)->{
                if(state.hovered()||state.pressed())canvas.roundRect(state.pressed()?0xFF404040:RAISED,x+width-25,y+thumbH,25,27,12);
                UiIcon.MORE_VERTICAL.draw(canvas,TEXT,x+width-19,y+thumbH+6,14,1.3F);
            });
    }

    private static List<Video> filtered(String nav, String topic, String query) {
        String lowered = query.toLowerCase(Locale.ROOT);
        List<Video> result = new ArrayList<>();
        for (Video video : VIDEOS) {
            if (!topic.equals("All") && !video.topic.equals(topic)) continue;
            if (nav.equals("Shorts") && video.duration.length() > 5) continue;
            if (nav.equals("Subscriptions") && !(video.channel.equals("Block Theory") || video.channel.equals("Pane Labs"))) continue;
            if (!lowered.isEmpty() && !(video.title + " " + video.channel + " " + video.topic).toLowerCase(Locale.ROOT).contains(lowered)) continue;
            result.add(video);
        }
        return result;
    }

    private static Video video(String id) {
        for (Video candidate : VIDEOS) if (candidate.id().equals(id)) return candidate;
        return null;
    }

    private static String ellipsize(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, Math.max(1, limit - 1)) + "…";
    }

    private record Video(String title, String channel, String views, String when, String topic, String duration, int color, int dark) {
        String id() { return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""); }
    }
}
