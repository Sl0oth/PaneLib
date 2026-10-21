package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Atlas: an interactive motion workbench and a practical entry point for all existing examples. */
public final class ShowcaseHub {
    private static final int BLUE = 0xFF64D4FF, WHITE = 0xFFF0F9FF, MUTED = 0xFFABC3D7;
    private static final UiTimeline DEPLOY = UiTimeline.starting(0).to(1, 270, UiEasing.SPRING)
        .hold(130).to(.25F, 260, UiEasing.EASE_IN_OUT).hold(80).to(0, 350, UiEasing.EASE_OUT);
    private ShowcaseHub() { }

    public static UiScreen createScreen(Screen parent) {
        FieldHudDemo.init();
        UiScreen[] holder = new UiScreen[1];
        UiLayer textCache = new UiLayer(), detailCache = new UiLayer(), panelCache = new UiLayer();
        var doc = UiDocument.builder("Atlas • PaneLib Studio")
            .theme(UiTheme.dark().withAccent(BLUE))
            .page(new UiPage("studio", "Motion Studio", "", BLUE, ui -> studio(ui, textCache, detailCache, panelCache)))
            .page(new UiPage("hud", "Field HUD", "", BLUE, ShowcaseHub::hud))
            .page(new UiPage("library", "Examples", "", BLUE, ShowcaseHub::library))
            .build();
        holder[0] = new UiScreen(doc, NativeOverlayRenderer.NONE, (UiEventListener) (type, key, value) -> {
            UiScreen screen = holder[0];
            if (type.equals("value")) {
                if (key.equals("motion-off")) {
                    screen.setMotionScale(Boolean.parseBoolean(value) ? 0 : 1);
                    FieldHudDemo.setReduced(Boolean.parseBoolean(value));
                }
                if (key.equals("hud-scale")) FieldHudDemo.setScale(Float.parseFloat(value));
                return;
            }
            if (!type.equals("action")) return;
            if (key.startsWith("page:")) {
                FieldHudDemo.preview(key.equals("page:hud"));
                screen.showPage(key.substring(5));
            } else if (key.startsWith("mode:")) screen.setValue("mode", Integer.parseInt(key.substring(5)));
            else if (key.equals("morph")) screen.setValue("expanded", !screen.booleanValue("expanded", false));
            else if (key.equals("retarget")) screen.setValue("heading", screen.numberValue("heading", 0) + 90);
            else if (key.equals("replay")) screen.setValue("revision", screen.numberValue("revision", 0) + 1);
            else if (key.equals("hud-toggle")) { FieldHudDemo.toggle(); screen.invalidate(); }
            else if (key.equals("hud-anchor")) { FieldHudDemo.cycleAnchor(); screen.invalidate(); }
            else if (key.equals("resume")) { FieldHudDemo.preview(false); Minecraft.getInstance().gui.setScreen(null); }
            else if (key.equals("wayfarer")) Minecraft.getInstance().gui.setScreen(WayfarerShowcase.createScreen(screen));
            else if (key.equals("discord")) Minecraft.getInstance().gui.setScreen(DiscordShowcase.createScreen(screen));
            else if (key.equals("gmail")) Minecraft.getInstance().gui.setScreen(GmailShowcase.createScreen(screen));
            else if (key.equals("castle")) Minecraft.getInstance().gui.setScreen(CastleShowcase.createScreen(screen));
            else if (key.equals("designs")) Minecraft.getInstance().gui.setScreen(DesignLabShowcase.createScreen(screen));
            else if (key.equals("classic")) Minecraft.getInstance().gui.setScreen(NativeDemoShowcase.createScreen(screen));
            else if (key.equals("gallery")) Minecraft.getInstance().gui.setScreen(StyleGallery.createScreen());
            else if (key.equals("notice")) screen.toast(new UiToast("studio", "Track armed", "Replay or retarget while the instrument is moving.", 3000));
        }, UiScreenOptions.builder().parent(parent)
            .backdrop(parent == null ? UiBackdrop.MINECRAFT : UiBackdrop.PARENT_BACKGROUND)
            .onClose(screen -> { FieldHudDemo.preview(false); return false; }).build()) {
            @Override protected void init() {
                super.init();
                FieldHudDemo.preview(pageId().equals("hud"));
            }
            @Override public void removed() {
                FieldHudDemo.preview(false);
                super.removed();
            }
            @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int x, int y, float delta) {
                long started = System.nanoTime();
                long allocation = AtlasBenchmark.allocated();
                super.extractRenderState(graphics, x, y, delta);
                AtlasBenchmark.observe(this, started, allocation);
                StudioAutoTest.observe(started);
            }
        };
        holder[0].setValue("hud-scale", (double) FieldHudDemo.scale());
        holder[0].setValue("motion-off", FieldHudDemo.reduced());
        holder[0].setMotionScale(FieldHudDemo.reduced() ? 0 : 1);
        return holder[0];
    }

    private static UiButtonStyle button(boolean primary) {
        return UiButtonStyle.builder().background(primary ? 0xD346A5D2 : 0x80152D40)
            .hoverBackground(primary ? 0xEF64CFF2 : 0xC127506C).pressedBackground(0xFF1B7EA9)
            .border(0x7076D6FF).focus(BLUE).text(WHITE).radius(8).textSize(9).build();
    }

    private static void chrome(UiContext ui, String active, String title, String subtitle) {
        var g = ui.canvas();
        g.fill(active.equals("hud") ? 0x3805101D : 0x8205101D, 0, 0, ui.width(), ui.height());
        g.text("A T L A S", 28, 21, 12, BLUE, Font.BOLD, UiCanvas.MONO);
        g.text("PANELIB / FIELD CONSOLE", 28, 39, 6, MUTED, Font.PLAIN, UiCanvas.MONO);
        String[] ids = {"studio", "hud", "library"}, labels = {"Motion studio", "Field HUD", "Examples"};
        for (int i = 0; i < 3; i++) ui.button("nav-" + ids[i], labels[i], "page:" + ids[i],
            ui.width() - 326 + i * 99, 20, 92, 28, button(ids[i].equals(active)));
        g.text(title, 28, 85, 28, WHITE, Font.BOLD, UiCanvas.SANS);
        g.text(subtitle, 29, 124, 9, MUTED);
        ui.checkbox("motion-off", "motion-off", "Reduce motion", ui.width() - 154, ui.height() - 29, 130, false);
        g.text("H  CONSOLE     J  HUD", 28, ui.height() - 24, 7, MUTED, Font.PLAIN, UiCanvas.MONO);
    }

    private static void studio(UiContext ui, UiLayer textCache, UiLayer detailCache, UiLayer panelCache) {
        chrome(ui, "studio", "Make it move.", "Open the instrument, retarget its spring, or replay a short choreography.");
        var g = ui.canvas();
        UiRect stage = new UiRect(24, 157, ui.width() - 48, Math.max(180, ui.height() - 218));
        g.roundRect(0x680B1B2B, stage.x(), stage.y(), stage.width(), stage.height(), 18);
        g.strokeRoundRect(0x4064D4FF, .8F, stage.x(), stage.y(), stage.width(), stage.height(), 18);
        int mode = (int) ui.numberValue("mode", 0);
        float side = Math.min(185, stage.width() * .28F);
        String[] titles = {"01 / MORPH", "02 / RETARGET", "03 / SEQUENCE"};
        String[] descriptions = {"A card becomes a workspace", "A spring follows your next intent", "Three beats, one timeline"};
        float rowHeight = Math.min(78, (stage.height() - 26) / 3);
        float indicator = ui.animate("mode-indicator", mode * rowHeight, 340, UiEasing.SPRING, stage);
        g.roundRect(BLUE, stage.x() + 13, stage.y() + 17 + indicator, 3, rowHeight - 11, 1);
        for (int i = 0; i < 3; i++) {
            float progress = ui.animateFrom("track-enter-" + i, 0, 1, 440, i * 75, UiEasing.SPRING, stage);
            final int index = i;
            float x = stage.x() + 25, y = stage.y() + 14 + i * rowHeight;
            ui.withTransform(UiTransform.identity().translate((1 - progress) * -24, 0).opacity(Math.min(1, progress)),
                x, y, track -> {
                    track.canvas().roundRect(index == mode ? 0x902B536C : 0x281C3547, x, y, side - 17, rowHeight - 10, 8);
                    track.canvas().text(titles[index], x + 11, y + 12, 8, index == mode ? BLUE : WHITE, Font.BOLD, UiCanvas.MONO);
                    if (side > 155) track.canvas().text(descriptions[index], x + 11, y + 32, 6.5F, MUTED);
                    track.hit("track-" + index, "mode:" + index, x, y, side - 17, rowHeight - 10);
                });
        }
        float open = ui.animate("instrument-open", ui.booleanValue("expanded", false) ? 1 : 0, 440, UiEasing.SPRING, stage);
        float hover = ui.animate("instrument-hover", ui.hovered("instrument"), 170, UiEasing.EASE_OUT, stage);
        float turn = ui.animate("instrument-turn", (float) ui.numberValue("heading", 0), 650, UiEasing.SPRING, stage);
        float pulse = ui.sequence("instrument-sequence", (long) ui.numberValue("revision", 0), DEPLOY, stage);
        float available = stage.width() - side - 36;
        float w = Math.min(available - 12, 205 + open * 95), h = Math.min(stage.height() - 64, 137 + open * 42);
        float x = stage.x() + side + 20 + (available - w) / 2, y = stage.y() + (stage.height() - h) / 2 - 12;
        float rotation = mode == 0 ? -3 + open * 3 : mode == 1 ? turn * .035F : pulse * -5;
        float scale = 1 + hover * .018F + (mode == 2 ? pulse * .035F : 0);
        ui.withTransform(UiTransform.identity().rotate(rotation).scale(scale), x + w / 2, y + h / 2, card -> {
            var c = card.canvas();
            c.withTransform(new UiTransform(x, y, w / 205, h / 137, 0, 1), 0, 0, () ->
                c.cachedLayer(panelCache, 0, new UiRect(-14, -12, 233, 167), panel -> {
                    panel.shadow(0x70000000, 9, 0, 5, 0, 0, 205, 137, 14);
                    panel.roundRect(0xC3163D57, 0, 0, 205, 137, 14);
                    panel.strokeRoundRect(0xC064D4FF, 1, 0, 0, 205, 137, 14);
                }));
            card.hit("instrument", "morph", x, y, w, h);

            float cx = x + 48, cy = y + 73;
            c.strokeCircle(0x5064D4FF, 1, cx, cy, 27 + pulse * 10);
            c.withTransform(UiTransform.identity().rotate(turn + pulse * 90), cx, cy, () -> {
                c.polygon(BLUE, cx, cy - 23, cx - 12, cy + 15, cx, cy + 9, cx + 12, cy + 15);
            });
            c.cachedLayer(textCache, mode * 2 + (ui.booleanValue("expanded", false) ? 1 : 0), new UiRect(x, y, 330, 100), text -> {
                text.text("FIELD INSTRUMENT / 07", x + 16, y + 14, 7, BLUE, Font.BOLD, UiCanvas.MONO);
                text.text(ui.booleanValue("expanded", false) ? "Workspace open" : "Click to expand", x + 89, y + 47, 12, WHITE, Font.BOLD);
                text.text(mode == 0 ? "POSITION + SIZE" : mode == 1 ? "INTERRUPTIBLE SPRING" : "EXPAND / HOLD / SETTLE", x + 89, y + 68, 6, MUTED, Font.PLAIN, UiCanvas.MONO);
            });
            if (open > .3F) c.withOpacity(Math.min(1, open), () -> c.cachedLayer(detailCache, 0,
                new UiRect(x + 15, y + 103, 260, 20), text -> text.text("Layout, drawing and input move together.", x + 17, y + 105, 8, WHITE)));
            card.button("instrument-action", mode == 1 ? "Retarget +90°" : "Replay choreography", mode == 1 ? "retarget" : "replay",
                x + 13, y + h - 32, w - 26, 23, button(false));
        });
        ui.menuButton("motion-menu", "Motion options", stage.right() - 119, stage.bottom() - 30, 103, 23,
            UiContext.ButtonStyle.GHOST, UiContext.Shape.ROUNDED,
            List.of(new UiMenuItem("notice", "Arm track", "notice"), new UiMenuItem("replay", "Replay sequence", "replay")));
        g.text("CLICK CARD TO EXPAND  /  INTERRUPT ANY TRANSITION", stage.x() + side + 22, stage.bottom() - 17, 6, MUTED, Font.PLAIN, UiCanvas.MONO);
    }

    private static void hud(UiContext ui) {
        chrome(ui, "hud", "Your world. In view.", "A quiet field HUD, driven by your actual health, supplies, heading and position.");
        var g = ui.canvas();
        float x = 28, y = 163, w = Math.min(340, ui.width() - 56), h = ui.height() - 221;
        g.roundRect(0xC00B2032, x, y, w, h, 15);
        g.text("FIELD HUD", x + 20, y + 19, 9, BLUE, Font.BOLD, UiCanvas.MONO);
        g.text(Minecraft.getInstance().level == null ? "Join a world to see live telemetry." : "Preview is live behind this editor.", x + 20, y + 42, 9, WHITE);
        ui.button("hud-toggle", FieldHudDemo.enabled() ? "HUD enabled • turn off" : "HUD hidden • turn on", "hud-toggle",
            x + 18, y + 73, w - 36, 30, button(FieldHudDemo.enabled()));
        ui.button("hud-anchor", "Anchor: " + FieldHudDemo.anchor().name().replace('_', ' '), "hud-anchor",
            x + 18, y + 114, w - 36, 28, button(false));
        g.text("Scale", x + 20, y + 161, 8, MUTED);
        ui.slider("hud-scale", "hud-scale", x + 72, y + 160, w - 130, .65, 1.5, FieldHudDemo.scale());
        g.text(Math.round(FieldHudDemo.scale() * 100) + "%", x + w - 49, y + 158, 8, WHITE);
        if (h > 230) g.text("H opens this console. J toggles the HUD.", x + 20, y + 192, 8, MUTED);
        ui.button("resume", "Return to the world", "resume", x + 18, y + h - 41, w - 36, 27, button(true));
        if (ui.width() > 720) {
            g.text("NO INPUT CAPTURE", x + w + 34, y + h - 58, 8, BLUE, Font.BOLD, UiCanvas.MONO);
            g.text("Move, look and click normally while playing.", x + w + 34, y + h - 37, 8, MUTED);
        }
    }

    private static void library(UiContext ui) {
        chrome(ui, "library", "Different worlds. Same library.", "Working examples, from a route inventory to a dense native control surface.");
        ui.button("wayfarer-example", "Wayfarer", "wayfarer", ui.width() - 542, 118, 100, 29, button(true));
        ui.button("discord-example", "Gather", "discord", ui.width() - 435, 118, 100, 29, button(true));
        ui.button("gmail-example", "Gmail inbox", "gmail", ui.width() - 328, 118, 140, 29, button(true));
        ui.button("castle-example", "Briarkeep castle", "castle", ui.width() - 176, 118, 148, 29, button(true));
        String[] titles = {"Design Lab", "Core controls", "Style gallery"};
        String[] copy = {"Three distinct translucent visual systems", "Lists, forms, dialogs, tooltips and native items", "Nine separate approaches to native UI"};
        String[] actions = {"designs", "classic", "gallery"};
        float gap = 12, w = (ui.width() - 56 - gap * 2) / 3, y = 173, h = ui.height() - 242;
        for (int i = 0; i < 3; i++) {
            float progress = ui.animateFrom("example-enter-" + i, 0, 1, 430, i * 85, UiEasing.SPRING,
                new UiRect(24, 150, ui.width() - 48, ui.height() - 190));
            float x = 28 + i * (w + gap);
            final int index = i;
            ui.withTransform(UiTransform.identity().translate(0, (1 - progress) * 26).rotate((1 - progress) * -4)
                .opacity(Math.min(1, progress)), x + w / 2, y + h, card -> {
                    var g = card.canvas();
                    g.roundRect(0xB0153047, x, y, w, h, 15);
                    g.strokeRoundRect(0x8064D4FF, .8F, x, y, w, h, 15);
                    g.circle(0x5064D4FF, x + 37, y + 38, 17);
                    g.centeredText(Integer.toString(index + 1), x + 37, y + 38, 12, BLUE, Font.BOLD, UiCanvas.MONO);
                    g.text(titles[index], x + 18, y + 75, 16, WHITE, Font.BOLD);
                    List<String> lines = g.wrapText(copy[index], w - 36, 8, Font.PLAIN, UiCanvas.SANS);
                    for (int line = 0; line < lines.size(); line++)
                        g.text(lines.get(line), x + 18, y + 105 + line * 14, 8, MUTED);
                    card.button("example-" + index, "Open", actions[index], x + 15, y + h - 42, w - 30, 27, button(true));
                });
        }
    }
}
