package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Beacon: a deliberately maximal expedition planner. Every control is drawn from code: hexagon
 * rail, slanted tabs, chamfered and circular actions, chevron route steps, a radial loadout ring
 * and notched region cards, all with shape-exact pointer targets. Motion is finite and bounded,
 * so the screen goes fully idle; the region sheet slides on the GPU.
 */
public final class BeaconShowcase {
    private static final int NIGHT = 0xFF060A11, PANEL = 0xF20B121D, RAISED = 0xFF121C2B, LINE = 0xFF203049,
        TEXT = 0xFFEAF0F8, MUTED = 0xFF8D9BB4, FAINT = 0xFF56637B, AMBER = 0xFFFFB347, EMBER = 0xFFE8742C;
    private static final String FONT = ExampleFonts.FAMILY;

    private record Region(String name, String tagline, String lore, int skyTop, int skyBottom, int far, int mid,
                          int near, int aurora, int sun, int difficulty, int altitude, int temperature, float distance,
                          List<String> rewards) { }

    private static final List<Region> REGIONS = List.of(
        new Region("Shattered Peaks", "A beacon still burns above the cloudline.",
            "Climbers report a light that never moves with the wind. Rope teams leave at moonrise, when the ice is quiet and the aurora shows the old switchback trail.",
            0xFF071226, 0xFF1C3354, 0xFF22385A, 0xFF162842, 0xFF0B1626, 0xFF3FE0C5, 0xFFFFE3A6, 4, 2940, -18, 14.2F,
            List.of("Beacon shard", "Frost-forged pick", "Summit banner")),
        new Region("Deep Dark", "Something below answers every footstep.",
            "Sculk veins pulse under the ancient city. Move in silence, carry wool, and keep the lantern low so the warden never learns your shape.",
            0xFF02070B, 0xFF07222A, 0xFF0B2C33, 0xFF082028, 0xFF041218, 0xFF1FD3E8, 0xFF9FF7FF, 5, -52, 11, 6.8F,
            List.of("Echo shard", "Swift sneak tome", "Ancient debris")),
        new Region("Badlands", "Red mesas, gold veins and a long dusk.",
            "The mineshafts here run deeper than any map. Caravans cross at dusk, when the terracotta cools and the gold veins catch the last of the light.",
            0xFF1B0B12, 0xFF6B2A1E, 0xFF7A3522, 0xFF552416, 0xFF2A120D, 0xFFFFA45C, 0xFFFFD08A, 2, 84, 31, 9.5F,
            List.of("Gold cache", "Mesa armour trim", "Cartographer map")),
        new Region("Frozen Archipelago", "Islands of packed ice under a pale dawn.",
            "Boats thread between bergs where polar bears keep watch. Strays haunt the shoreline, so the crossing is made at first light, quickly and together.",
            0xFF0B1A2E, 0xFF5B86A8, 0xFF5D7F9C, 0xFF3D5C78, 0xFF1C3045, 0xFFB4F1FF, 0xFFFFF4DA, 3, 12, -9, 21.7F,
            List.of("Blue ice", "Trident", "Heart of the sea")));
    private static final String[] TABS = {"Peaks", "Deep Dark", "Badlands", "Archipelago"};
    private static final String[] NAV = {"Overview", "World map", "Crew", "Crafting", "Journal"};
    private static final String[] CREW = {"Mira", "Theo", "Jules", "You"};
    private static final boolean[] CREW_READY = {true, true, false, true};
    private static final String[] STEPS = {"Gather", "Craft", "Travel", "Summit"};
    private static final String[] STEP_DETAIL = {"Supplies & crew", "Gear & potions", "Route & camps", "Claim the beacon"};
    private static final String[] ITEMS = {"Pickaxe", "Sword", "Bow", "Shield", "Lantern", "Compass"};
    private static final int[][] ITEM_STATS = {{78, 40, 20}, {64, 35, 18}, {52, 30, 24}, {92, 52, 12}, {38, 70, 96}, {46, 44, 58}};

    private BeaconShowcase() { }

    public static UiScreen createScreen(Screen parent) {
        var backdrop = new UiLayer();
        var hero = new UiLayer[REGIONS.size()];
        var art = new UiLayer[REGIONS.size()];
        for (int i = 0; i < art.length; i++) { hero[i] = new UiLayer(); art[i] = new UiLayer(); }
        var doc = UiDocument.builder("Beacon").shell(UiShell.NONE)
            .theme(new UiTheme(NIGHT, PANEL, RAISED, LINE, TEXT, MUTED, AMBER, NIGHT, 8, 14, 20, 1, 1, FONT))
            .scaling(UiScaling.fixed().reference(1240, 760).adaptivity(1).range(.25F, 8))
            .renderSettings(UiRenderSettings.typography())
            .page(new UiPage("beacon", "Beacon", "", AMBER, ui -> paint(ui, backdrop, hero, art))).build();
        return UiScreen.create(doc, NativeOverlayRenderer.NONE, (screen, event) -> {
            if (event.type() != UiEventType.ACTION) return;
            String key = event.key();
            int split = key.indexOf(':');
            String verb = split < 0 ? key : key.substring(0, split);
            int index = split < 0 ? -1 : Integer.parseInt(key.substring(split + 1));
            switch (verb) {
                case "region" -> screen.setValue("region", index);
                case "nav" -> screen.setValue("nav", index);
                case "step" -> screen.setValue("step", index);
                case "item" -> screen.setValue("item", index);
                case "ready" -> screen.setValue("ready-" + index, !screen.booleanValue("ready-" + index, CREW_READY[index]));
                case "sheet-open" -> screen.setValue("sheet", true);
                case "sheet-close" -> screen.setValue("sheet", false);
                case "embark" -> embark(screen);
                case "bell" -> screen.toast(toast("crew", "Crew update", "Mira packed extra torches for the climb."));
                case "profile" -> screen.toast(toast("profile", "Sl0oth · Level 42", "1,920 XP to the next level."));
                case "back" -> screen.onClose();
                default -> { }
            }
        }, UiScreenOptions.builder().parent(parent).backdrop(UiBackdrop.NONE).build());
    }

    private static void embark(UiScreen screen) {
        int step = (int) screen.numberValue("step", 0);
        Region region = REGIONS.get((int) screen.numberValue("region", 0));
        screen.setValue("emeralds", screen.numberValue("emeralds", 1284) - 120);
        screen.setValue("launches", screen.numberValue("launches", 0) + 1);
        screen.setValue("step", Math.min(STEPS.length - 1, step + 1));
        screen.toast(toast("embark", "Expedition launched", region.name() + " · " + STEPS[Math.min(STEPS.length - 1, step + 1)]));
    }

    private static UiToast toast(String id, String title, String message) {
        return UiToast.builder(id, title).message(message).icon("B").durationMillis(3200)
            .style(UiToastStyle.builder().background(0xF4101826).border(0x66FFB347).titleColor(TEXT)
                .messageColor(MUTED).iconBackground(0x33FFB347).iconColor(AMBER).radius(14).build()).build();
    }

    // ------------------------------------------------------------------ page

    private static void paint(UiContext ui, UiLayer backdrop, UiLayer[] heroCache, UiLayer[] cardArt) {
        UiCanvas g = ui.canvas();
        float w = ui.width(), h = ui.height(), m = 18;
        g.cachedLayer(backdrop, Math.round(w) * 10_000 + Math.round(h), new UiRect(0, 0, w, h), c -> backdrop(c, w, h));

        int region = (int) ui.numberValue("region", 0);
        float barH = 56, bodyY = m + barH + 14, bodyB = h - m;
        float railW = 70, contentX = m + railW + 16, contentR = w - m;
        float sideW = Math.max(270, Math.min(330, (contentR - contentX) * .29F)), sideX = contentR - sideW;
        float mainW = sideX - 16 - contentX;
        float heroH = (bodyB - bodyY) * .53F, routeY = bodyY + heroH + 14, routeH = 60, cardsY = routeY + routeH + 14;

        enter(ui, "bar", 0, new UiRect(m, m, w - 2 * m, barH), bar -> topBar(bar, m, m, w - 2 * m, barH, region));
        enter(ui, "rail", 90, new UiRect(m, bodyY, railW, bodyB - bodyY), rail -> rail(rail, m, bodyY, railW, bodyB - bodyY));
        UiRect heroBounds = new UiRect(contentX, bodyY, mainW, heroH);
        enter(ui, "hero", 160, heroBounds, inner -> hero(inner, heroCache, heroBounds, region));
        enter(ui, "route", 240, new UiRect(contentX, routeY, mainW, routeH), route -> route(route, contentX, routeY, mainW, routeH));
        cards(ui, cardArt, contentX, cardsY, mainW, bodyB - cardsY, region);
        enter(ui, "side", 200, new UiRect(sideX, bodyY, sideW, bodyB - bodyY), side -> loadout(side, sideX, bodyY, sideW, bodyB - bodyY, region));
        sheet(ui, new UiRect(sideX, bodyY, sideW, bodyB - bodyY), region);
    }

    /** Rises and fades in once, staggered; afterwards it paints untransformed. */
    private static void enter(UiContext ui, String id, int delay, UiRect bounds, Consumer<UiContext> painter) {
        float t = ui.animateFrom("enter:" + id, 0, 1, 760, delay, UiEasing.EASE_OUT, bounds.inset(-30));
        if (t >= 1) { painter.accept(ui); return; }
        ui.withTransform(new UiTransform(0, (1 - t) * 28, 1, 1, 0, t), bounds.centerX(), bounds.centerY(), painter);
    }

    private static void backdrop(UiCanvas g, float w, float h) {
        g.gradientRect(0xFF081120, NIGHT, 75, 0, 0, w, h, 0);
        g.radialRect(0x33FFB347, 0x00FFB347, 60, 30, Math.max(w, h) * .55F, 0, 0, w, h, 0);
        g.radialRect(0x1A3FE0C5, 0x003FE0C5, w * .92F, h * 1.05F, Math.max(w, h) * .5F, 0, 0, w, h, 0);
        // Topographic contours: irregular closed loops around two summits.
        var random = new Random(7);
        for (int ring = 0; ring < 14; ring++) {
            for (int hill = 0; hill < 2; hill++) {
                float cx = hill == 0 ? w * .63F : w * .18F, cy = hill == 0 ? h * .78F : h * .2F, base = 40 + ring * 34;
                UiPath path = g.path();
                for (int step = 0; step <= 48; step++) {
                    double a = step / 48.0 * Math.PI * 2;
                    float wobble = (float) (Math.sin(a * 3 + ring * .7 + hill) * 9 + Math.sin(a * 5 + hill * 2) * 5);
                    path.lineTo(cx + (float) Math.cos(a) * (base + wobble) * 1.5F, cy + (float) Math.sin(a) * (base + wobble));
                }
                path.close().stroke(ring % 4 == 0 ? 0x1C8FA6C8 : 0x0E8FA6C8, ring % 4 == 0 ? .9F : .6F);
            }
        }
        for (int i = 0; i < 70; i++) g.circle(0x22FFFFFF, random.nextFloat() * w, random.nextFloat() * h * .6F, random.nextFloat() * .9F + .2F);
        g.grain(0x09FFFFFF, .22F, 0, 0, w, h);
    }

    // ------------------------------------------------------------------ top bar

    private static void topBar(UiContext ui, float x, float y, float w, float h, int region) {
        UiCanvas g = ui.canvas();
        g.shadow(0x55000000, 12, 0, 4, x, y, w, h, 16);
        g.roundRect(PANEL, x, y, w, h, 16);
        g.strokeRoundRect(0x30FFFFFF, .8F, x, y, w, h, 16);
        // Faceted beacon crystal.
        float cx = x + 30, cy = y + h / 2;
        g.glow(0x55FFB347, 10, cx - 9, cy - 13, 18, 26, 9);
        g.path().moveTo(cx, cy - 15).lineTo(cx + 10, cy - 3).lineTo(cx, cy + 15).lineTo(cx - 10, cy - 3).close().fillGradient(0xFFFFE0A0, EMBER, 90);
        g.path().moveTo(cx, cy - 15).lineTo(cx + 10, cy - 3).lineTo(cx, cy + 1).close().fill(0x55FFFFFF);
        g.trackedText("BEACON", x + 50, y + 12, 15, TEXT, Font.BOLD, FONT, 3.2F);
        g.trackedText("EXPEDITION PLANNER", x + 51, y + 33, 7.5F, FAINT, Font.BOLD, FONT, 1.6F);

        // Slanted region tabs with a sliding, springy highlight.
        float tabW = 128, tabH = 34, slant = 12, gap = 6, tabX = x + 232, tabY = y + (h - tabH) / 2;
        UiRect strip = new UiRect(tabX - 4, tabY - 4, REGIONS.size() * (tabW + gap) + 8, tabH + 8);
        float position = ui.animate("tab-position", region, 420, UiEasing.SPRING, strip);
        g.roundRect(0x66060A11, strip.x(), strip.y(), strip.width(), strip.height(), 10);
        float indicatorX = tabX + position * (tabW + gap);
        g.glow(0x40FFB347, 9, indicatorX + 6, tabY + 4, tabW - 12, tabH - 8, 8);
        parallelogram(g, indicatorX, tabY, tabW, tabH, slant).fillGradient(0xFFFFC56B, EMBER, 0);
        for (int i = 0; i < REGIONS.size(); i++) {
            float tx = tabX + i * (tabW + gap);
            float near = Math.max(0, 1 - Math.abs(position - i));
            UiPath outline = parallelogram(g, tx, tabY, tabW, tabH, slant);
            int index = i;
            ui.shapedButton("tab-" + i, REGIONS.get(i).name(), "region:" + i, outline, true, (c, state) -> {
                if (state.hovered() && near < .5F) outline.fill(0x14FFFFFF);
                if (near < .5F) outline.stroke(state.hovered() ? 0x55FFB347 : 0x22FFFFFF, .8F);
                c.centeredText(TABS[index], tx + tabW / 2, tabY + tabH / 2,
                    10, UiCanvas.mix(state.hovered() ? TEXT : MUTED, NIGHT, near), Font.BOLD, FONT);
            });
        }

        // Resources with count-up numbers, a notification bell and a profile ring.
        float right = x + w - 16;
        float avatarR = 18, avatarX = right - avatarR, avatarY = y + h / 2;
        UiPath avatar = g.path().arc(avatarX, avatarY, avatarR, 0, 360).close();
        ui.shapedButton("profile", "Profile", "profile", avatar, true, (c, state) -> {
            c.strokeCircle(0x33FFFFFF, 2.4F, avatarX, avatarY, avatarR + 3);
            c.path().arc(avatarX, avatarY, avatarR + 3, -90, 252).stroke(AMBER, 2.4F);
            avatar.fillGradient(0xFF2B3D5C, 0xFF152033, 120);
            c.centeredText("SL", avatarX, avatarY, 11, state.hovered() ? AMBER : TEXT, Font.BOLD, FONT);
        });
        float bellX = avatarX - avatarR - 34, bellR = 15;
        UiPath bell = g.path().arc(bellX, avatarY, bellR, 0, 360).close();
        int launches = (int) ui.numberValue("launches", 0);
        ui.shapedButton("bell", "Crew updates", "bell", bell, true, (c, state) -> {
            bell.fill(state.hovered() ? 0x24FFFFFF : 0x12FFFFFF);
            c.path().moveTo(bellX - 6, avatarY + 4).curveTo(bellX - 6, avatarY - 9, bellX + 6, avatarY - 9, bellX + 6, avatarY + 4)
                .lineTo(bellX - 6, avatarY + 4).stroke(TEXT, 1.4F);
            c.circle(TEXT, bellX, avatarY + 6.5F, 1.6F);
            float pulse = ui.sequence("bell-pulse", launches, UiTimeline.starting(0).to(1, 160, UiEasing.EASE_OUT)
                .to(0, 520, UiEasing.EASE_IN_OUT), new UiRect(bellX - 24, avatarY - 24, 48, 48));
            if (pulse > .01F) c.glow(UiCanvas.fade(AMBER, .7F * pulse), 8 * pulse, bellX + 4, avatarY - 11, 7, 7, 4);
            c.circle(AMBER, bellX + 7.5F, avatarY - 7.5F, 3.4F);
        });
        float emeralds = ui.animate("emeralds", (float) ui.numberValue("emeralds", 1284), 900, UiEasing.EASE_OUT,
            new UiRect(bellX - 200, y, 170, h));
        pill(g, bellX - 150, avatarY - 13, 104, 26, String.format("%,d", Math.round(emeralds)), 0xFF4FE3A1);
        pill(g, bellX - 258, avatarY - 13, 98, 26, "LV 42", AMBER);
    }

    private static void pill(UiCanvas g, float x, float y, float w, float h, String value, int gem) {
        g.roundRect(0x55060A11, x, y, w, h, h / 2);
        g.strokeRoundRect(0x26FFFFFF, .8F, x, y, w, h, h / 2);
        float gx = x + 15, gy = y + h / 2;
        g.path().moveTo(gx, gy - 6).lineTo(gx + 6, gy).lineTo(gx, gy + 6).lineTo(gx - 6, gy).close().fillGradient(UiCanvas.lighten(gem, .35F), gem, 90);
        g.text(value, x + 28, y + (h - g.textHeight(11, Font.BOLD, FONT)) / 2, 11, TEXT, Font.BOLD, FONT);
    }

    // ------------------------------------------------------------------ hexagon rail

    private static void rail(UiContext ui, float x, float y, float w, float h) {
        UiCanvas g = ui.canvas();
        g.roundRect(PANEL, x, y, w, h, 18);
        g.strokeRoundRect(0x26FFFFFF, .8F, x, y, w, h, 18);
        int selected = (int) ui.numberValue("nav", 0);
        float radius = 23, step = 62, top = y + 44;
        UiRect railBounds = new UiRect(x, y, w, h);
        float indicator = ui.animate("nav-indicator", selected, 380, UiEasing.SPRING, railBounds);
        g.roundRect(AMBER, x + 2, top + indicator * step - 14, 3, 28, 1.5F);
        g.glow(0x55FFB347, 6, x + 2, top + indicator * step - 14, 3, 28, 1.5F);
        UiIcon[] icons = {UiIcon.HOME, UiIcon.COLUMNS, UiIcon.PEOPLE, UiIcon.TUNE, UiIcon.BOOKMARK};
        for (int i = 0; i < NAV.length; i++) {
            float cx = x + w / 2, cy = top + i * step;
            boolean active = i == selected;
            UiPath hex = hexagon(g, cx, cy, radius);
            UiIcon icon = icons[i];
            float hover = ui.animate("nav-hover:" + i, ui.hovered("nav-" + i), 140, new UiRect(cx - 34, cy - 34, 68, 68));
            ui.shapedButton("nav-" + i, NAV[i], "nav:" + i, hex, true, (c, state) -> {
                if (active) {
                    c.glow(0x50FFB347, 10, cx - radius * .8F, cy - radius * .8F, radius * 1.6F, radius * 1.6F, radius * .8F);
                    hex.fillGradient(0xFFFFC56B, EMBER, 90);
                } else {
                    hex.fill(UiCanvas.mix(RAISED, 0xFF1D2A3F, hover));
                    hex.stroke(UiCanvas.mix(0x30FFFFFF, AMBER, hover * .8F), 1F);
                }
                icon.draw(c, active ? NIGHT : UiCanvas.mix(MUTED, TEXT, hover), cx - 9, cy - 9, 18, 1.7F);
            });
        }
        UiPath back = hexagon(g, x + w / 2, y + h - 36, 18);
        ui.shapedButton("back", "Back to Minecraft", "back", back, true, (c, state) -> {
            back.stroke(state.hovered() ? AMBER : 0x40FFFFFF, 1);
            UiIcon.CHEVRON_LEFT.draw(c, state.hovered() ? AMBER : MUTED, x + w / 2 - 7, y + h - 43, 14, 1.6F);
        });
    }

    // ------------------------------------------------------------------ hero

    private static void hero(UiContext ui, UiLayer[] cache, UiRect b, int region) {
        UiCanvas g = ui.canvas();
        float blend = ui.animate("region-blend", region, 720, UiEasing.EASE_IN_OUT, b.inset(-24));
        g.shadow(0x70000000, 18, 0, 8, b.x(), b.y(), b.width(), b.height(), 22);
        // Each landscape is rasterized once; a region change crossfades two cached images.
        int from = (int) Math.floor(blend), to = Math.min(REGIONS.size() - 1, from + 1);
        float mix = blend - from;
        long size = Math.round(b.width()) * 10_000L + Math.round(b.height());
        g.withRoundedClip(b.x(), b.y(), b.width(), b.height(), 22, () -> {
            heroArt(g, cache[from], size, b, from);
            if (mix > .004F) g.withOpacity(mix, () -> heroArt(g, cache[to], size, b, to));
        });
        g.strokeRoundRect(0x33FFFFFF, .9F, b.x(), b.y(), b.width(), b.height(), 22);

        Region current = REGIONS.get(region);
        float copy = ui.sequence("hero-copy", region, UiTimeline.starting(0).to(1, 520, UiEasing.EASE_OUT),
            new UiRect(b.x(), b.y(), b.width() * .62F, b.height()));
        float left = b.x() + 32, top = b.y() + 30;
        g.withOpacity(copy, () -> {
            float slide = (1 - copy) * 18;
            g.trackedText("REGION 0" + (region + 1), left + slide, top, 8.5F, AMBER, Font.BOLD, FONT, 2.4F);
            for (int i = 0; i < 5; i++) g.path().moveTo(left + 98 + slide + i * 12, top + 1).lineTo(left + 103 + slide + i * 12, top + 6)
                .lineTo(left + 98 + slide + i * 12, top + 11).lineTo(left + 93 + slide + i * 12, top + 6).close()
                .fill(i < current.difficulty() ? AMBER : 0x30FFFFFF);
            g.text(current.name(), left + slide * 1.4F, top + 20, 34, TEXT, Font.BOLD, FONT);
            g.text(current.tagline(), left + slide * 1.8F, top + 66, 13, 0xFFC5D0E2, Font.PLAIN, FONT);
        });

        // Count-up telemetry.
        float statsY = top + 104;
        UiRect statsBounds = new UiRect(left, statsY, 380, 46);
        float altitude = ui.animate("altitude", current.altitude(), 900, UiEasing.EASE_OUT, statsBounds);
        float temperature = ui.animate("temperature", current.temperature(), 900, UiEasing.EASE_OUT, statsBounds);
        float distance = ui.animate("distance", current.distance(), 900, UiEasing.EASE_OUT, statsBounds);
        stat(g, left, statsY, "ALTITUDE", String.format("%,d m", Math.round(altitude)));
        stat(g, left + 128, statsY, "TEMPERATURE", Math.round(temperature) + "°");
        stat(g, left + 256, statsY, "DISTANCE", String.format("%.1f km", distance));

        // Chamfered embark action with a hover sheen and a warm glow.
        float bw = 204, bh = 54, bx = left, by = b.bottom() - bh - 30, cut = 15;
        UiPath embark = chamfer(g, bx, by, bw, bh, cut);
        UiRect embarkBounds = new UiRect(bx - 30, by - 30, bw + 60, bh + 60);
        float hover = ui.animate("embark-hover", ui.hovered("embark"), 180, embarkBounds);
        float press = ui.animate("embark-press", ui.pressed("embark"), 90, embarkBounds);
        float sheen = ui.sequence("embark-sheen", ui.hovered("embark") ? 1 : 0,
            UiTimeline.starting(0).to(1, 760, UiEasing.EASE_IN_OUT), embarkBounds);
        ui.shapedButton("embark", "Embark on the expedition", "embark", embark, true, (c, state) -> {
            c.shadow(UiCanvas.fade(EMBER, .35F + .4F * hover), 10 + 10 * hover, 0, 4 - 3 * press, bx, by, bw, bh, 10);
            embark.fillGradient(UiCanvas.mix(0xFFFFC56B, 0xFFFFE0A0, hover * .5F), UiCanvas.mix(EMBER, 0xFFB8551E, press), 90);
            c.withClip(embark, () -> {
                c.gradientRect(0x55FFFFFF, 0x00FFFFFF, 90, bx, by, bw, bh * .45F, 0);
                if (ui.hovered("embark") && sheen > 0 && sheen < 1) {
                    float sx = bx - 60 + (bw + 120) * sheen;
                    c.polygon(0x5AFFFFFF, sx, by, sx + 26, by, sx - 4, by + bh, sx - 30, by + bh);
                }
            });
            embark.stroke(0x66FFFFFF, .8F);
            c.trackedText("EMBARK", bx + 26, by + bh / 2 - 9, 15, NIGHT, Font.BOLD, FONT, 3.4F);
            float ax = bx + bw - 42 + 4 * hover, ay = by + bh / 2;
            c.path().moveTo(ax, ay - 7).lineTo(ax + 9, ay).lineTo(ax, ay + 7).stroke(NIGHT, 2.4F);
            c.line(NIGHT, 2.4F, ax - 10, ay, ax + 8, ay);
        });

        // Round glass preview action.
        float pr = 26, pcx = bx + bw + 22 + pr, pcy = by + bh / 2;
        UiPath preview = g.path().arc(pcx, pcy, pr, 0, 360).close();
        float previewHover = ui.animate("preview-hover", ui.hovered("preview"), 160, new UiRect(pcx - 40, pcy - 40, 80, 80));
        ui.shapedButton("preview", "Region details", "sheet-open", preview, true, (c, state) -> {
            preview.fill(UiCanvas.mix(0x1CFFFFFF, 0x30FFFFFF, previewHover));
            preview.stroke(UiCanvas.mix(0x40FFFFFF, AMBER, previewHover), 1.2F);
            c.strokeCircle(UiCanvas.fade(AMBER, .6F * previewHover), 1, pcx, pcy, pr + 5 + 3 * previewHover);
            c.polygon(TEXT, pcx - 5, pcy - 8, pcx + 9, pcy, pcx - 5, pcy + 8);
        });
        g.text("Region details", pcx + pr + 12, pcy - 7, 11, MUTED, Font.BOLD, FONT);

        // Wind compass, upper right.
        float ccx = b.right() - 62, ccy = b.y() + 62;
        g.circle(0x40060A11, ccx, ccy, 34);
        g.strokeCircle(0x40FFFFFF, .8F, ccx, ccy, 34);
        g.strokeCircle(0x20FFFFFF, .8F, ccx, ccy, 24);
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5);
            float inner = i % 4 == 0 ? 26 : 30;
            g.line(0x50FFFFFF, i % 4 == 0 ? 1.1F : .6F, ccx + (float) Math.cos(a) * inner, ccy + (float) Math.sin(a) * inner,
                ccx + (float) Math.cos(a) * 33, ccy + (float) Math.sin(a) * 33);
        }
        float heading = ui.animateAngle("wind-heading", 40 + region * 77, 1100, UiEasing.SPRING);
        g.withRotation(heading, ccx, ccy, () -> {
            g.polygon(AMBER, ccx, ccy - 22, ccx + 5, ccy, ccx - 5, ccy);
            g.polygon(0x80FFFFFF, ccx, ccy + 22, ccx + 5, ccy, ccx - 5, ccy);
        });
        g.circle(TEXT, ccx, ccy, 2.2F);
        g.centeredText("WIND", ccx, ccy + 48, 7.5F, MUTED, Font.BOLD, FONT);
    }

    private static void heroArt(UiCanvas g, UiLayer cache, long size, UiRect b, int region) {
        g.cachedLayer(cache, size, b, art -> {
            regionArt(art, b, region, true);
            art.gradientRect(0xEE060A11, 0x00060A11, 0, b.x(), b.y(), b.width() * .62F, b.height(), 0);
        });
    }

    /** The Shattered Peaks landscape alone, for the showcase launcher card. */
    static void paintPreview(UiCanvas g, UiRect bounds) { regionArt(g, bounds, 0, true); }

    private static void stat(UiCanvas g, float x, float y, String label, String value) {
        g.fill(0x40FFB347, x, y + 2, 2, 38);
        g.trackedText(label, x + 12, y + 2, 7.5F, MUTED, Font.BOLD, FONT, 1.4F);
        g.text(value, x + 12, y + 16, 18, TEXT, Font.BOLD, FONT);
    }

    /** Procedural landscape, blended between neighbouring regions by a fractional index. */
    private static void regionArt(UiCanvas g, UiRect b, float blend, boolean detailed) {
        int from = Math.max(0, Math.min(REGIONS.size() - 1, (int) Math.floor(blend)));
        int to = Math.min(REGIONS.size() - 1, from + 1);
        float t = blend - from;
        Region a = REGIONS.get(from), z = REGIONS.get(to);
        int skyTop = UiCanvas.mix(a.skyTop(), z.skyTop(), t), skyBottom = UiCanvas.mix(a.skyBottom(), z.skyBottom(), t);
        int far = UiCanvas.mix(a.far(), z.far(), t), mid = UiCanvas.mix(a.mid(), z.mid(), t), near = UiCanvas.mix(a.near(), z.near(), t);
        int aurora = UiCanvas.mix(a.aurora(), z.aurora(), t), sun = UiCanvas.mix(a.sun(), z.sun(), t);
        float x = b.x(), y = b.y(), w = b.width(), h = b.height();
        g.gradientRect(skyTop, skyBottom, 90, x, y, w, h, 0);
        var random = new Random(31);
        int stars = detailed ? 140 : 24;
        for (int i = 0; i < stars; i++) {
            float sx = x + random.nextFloat() * w, sy = y + random.nextFloat() * h * .55F, size = random.nextFloat();
            g.circle(UiCanvas.fade(0xFFFFFFFF, .25F + size * .6F), sx, sy, .4F + size * (detailed ? 1.1F : .7F));
        }
        // Aurora ribbons.
        for (int band = 0; band < (detailed ? 3 : 1); band++) {
            UiPath ribbon = g.path();
            float base = y + h * (.16F + band * .07F), thick = h * (.12F - band * .025F);
            for (int s = 0; s <= 40; s++) {
                float px = x + w * s / 40F;
                ribbon.lineTo(px, base + (float) Math.sin(s * .32 + band * 1.7) * h * .045F);
            }
            for (int s = 40; s >= 0; s--) {
                float px = x + w * s / 40F;
                ribbon.lineTo(px, base + thick + (float) Math.sin(s * .27 + band * 1.1 + 1) * h * .06F);
            }
            ribbon.close().fillGradient(UiCanvas.fade(aurora, .42F - band * .1F), UiCanvas.fade(aurora, 0), 90);
        }
        // Moon or sun with a soft halo.
        float sunX = x + w * .76F, sunY = y + h * .26F, sunR = Math.min(w, h) * .055F;
        g.radialRect(UiCanvas.fade(sun, .45F), UiCanvas.fade(sun, 0), sunX, sunY, sunR * 5, x, y, w, h, 0);
        g.circle(sun, sunX, sunY, sunR);
        ridge(g, x, y + h * .34F, w, h * .66F, 0.82F, far, 11, detailed);
        ridge(g, x, y + h * .46F, w, h * .54F, 0.7F, mid, 23, detailed);
        // The beacon: a beam rising from the tallest near peak.
        float peakX = x + w * .7F, peakY = y + h * .55F;
        g.gradientRect(UiCanvas.fade(AMBER, 0), UiCanvas.fade(AMBER, .55F), 90, peakX - 3, y, 6, peakY - y, 3);
        g.gradientRect(UiCanvas.fade(AMBER, 0), UiCanvas.fade(AMBER, .16F), 90, peakX - 16, y + h * .2F, 32, peakY - y - h * .2F, 16);
        ridge(g, x, y + h * .6F, w, h * .4F, 0.6F, near, 41, detailed);
        g.radialRect(UiCanvas.fade(AMBER, .9F), UiCanvas.fade(AMBER, 0), peakX, peakY, 24, peakX - 30, peakY - 30, 60, 60, 0);
        g.circle(0xFFFFF1CF, peakX, peakY, 2.6F);
        g.gradientRect(0x00060A11, 0xC0060A11, 90, x, y + h * .7F, w, h * .3F, 0);
    }

    private static void ridge(UiCanvas g, float x, float y, float w, float h, float roughness, int color, long seed, boolean detailed) {
        var random = new Random(seed);
        int points = detailed ? 18 : 9;
        float[] xs = new float[points + 1], ys = new float[points + 1];
        for (int i = 0; i <= points; i++) {
            xs[i] = x + w * i / points + (i == 0 || i == points ? 0 : (random.nextFloat() - .5F) * w / points * .6F);
            ys[i] = y + h * (i % 2 == 0 ? .04F + random.nextFloat() * .2F : .22F + random.nextFloat() * roughness * .3F);
        }
        UiPath path = g.path().moveTo(x, y + h);
        for (int i = 0; i <= points; i++) path.lineTo(xs[i], ys[i]);
        path.lineTo(x + w, y + h).close().fillGradient(UiCanvas.lighten(color, .12F), UiCanvas.darken(color, .55F), 90);
        // A lit edge along each crest gives the layers depth.
        UiPath crest = g.path();
        for (int i = 0; i <= points; i++) crest.lineTo(xs[i], ys[i]);
        crest.stroke(UiCanvas.fade(UiCanvas.lighten(color, .45F), .55F), detailed ? 1.1F : .8F);
    }

    // ------------------------------------------------------------------ route stepper

    private static void route(UiContext ui, float x, float y, float w, float h) {
        UiCanvas g = ui.canvas();
        int step = (int) ui.numberValue("step", 0);
        float overlap = 14, width = (w + overlap * (STEPS.length - 1)) / STEPS.length;
        float progress = ui.animate("route-progress", step, 620, UiEasing.EASE_OUT, new UiRect(x - 10, y - 10, w + 20, h + 20));
        for (int i = 0; i < STEPS.length; i++) {
            float cx = x + i * (width - overlap);
            UiPath chevron = chevron(g, cx, y, width, h, overlap, i == 0, i == STEPS.length - 1);
            float fill = Math.max(0, Math.min(1, progress - i + 1));
            boolean current = i == step;
            int index = i;
            float hover = ui.animate("step-hover:" + i, ui.hovered("step-" + i), 140, new UiRect(cx - 6, y - 6, width + 12, h + 12));
            ui.shapedButton("step-" + i, STEPS[i] + " step", "step:" + i, chevron, true, (c, state) -> {
                chevron.fill(UiCanvas.mix(RAISED, 0xFF1A2639, hover));
                if (fill > 0) c.withClip(chevron, () -> {
                    if (current) c.gradientRect(0xFFFFC56B, EMBER, 0, cx, y, width * fill, h, 0);
                    else c.fill(0x2EFFB347, cx, y, width * fill, h);
                });
                chevron.stroke(current ? 0x99FFE0A0 : UiCanvas.mix(0x28FFFFFF, 0x80FFB347, hover), current ? 1.2F : .8F);
                boolean done = index < step;
                int ink = current && fill > .55F ? NIGHT : done ? AMBER : UiCanvas.mix(MUTED, TEXT, hover);
                float tx = cx + (index == 0 ? 22 : overlap + 22);
                g.trackedText("0" + (index + 1), tx, y + 13, 8, ink, Font.BOLD, FONT, 1.6F);
                g.text(STEPS[index], tx + 24, y + 9, 14, ink, Font.BOLD, FONT);
                g.text(done ? "Complete" : STEP_DETAIL[index], tx, y + 33, 9, current && fill > .55F ? 0xB3060A11 : FAINT, Font.PLAIN, FONT);
                if (done) UiIcon.CHECK.draw(c, AMBER, cx + width - overlap - 34, y + h / 2 - 8, 16, 2);
            });
        }
    }

    // ------------------------------------------------------------------ region cards

    private static void cards(UiContext ui, UiLayer[] art, float x, float y, float w, float h, int region) {
        float gap = 14, cw = (w - gap * (REGIONS.size() - 1)) / REGIONS.size();
        for (int i = 0; i < REGIONS.size(); i++) {
            float cx = x + i * (cw + gap);
            int index = i;
            UiRect bounds = new UiRect(cx, y, cw, h);
            enter(ui, "card-" + i, 320 + i * 90, bounds, inner -> card(inner, art[index], index, cx, y, cw, h, index == region));
        }
    }

    private static void card(UiContext ui, UiLayer art, int index, float x, float y0, float w, float h, boolean selected) {
        UiCanvas g = ui.canvas();
        UiRect motion = new UiRect(x - 26, y0 - 26, w + 52, h + 52);
        float lift = ui.animate("card-lift:" + index, ui.hovered("card-" + index), 200, UiEasing.PAPER, motion);
        float chosen = ui.animate("card-selected:" + index, selected, 260, motion);
        float y = y0 - 6 * lift, notch = 20;
        Region region = REGIONS.get(index);
        UiPath outline = notched(g, x, y, w, h, notch);
        ui.shapedButton("card-" + index, region.name(), "region:" + index, outline, true, (c, state) -> {
            c.shadow(UiCanvas.mix(0x60000000, 0x66E8742C, chosen * .6F), 10 + 8 * lift, 0, 4 + 5 * lift, x, y, w, h, 14);
            // The landscape is cached per card; lifting only moves the cached pixels.
            c.withClip(outline, () -> c.cachedLayer(art, Math.round(w) * 10_000 + Math.round(h), new UiRect(x, y, w, h), card -> {
                regionArt(card, new UiRect(x, y, w, h * .62F), index, false);
                card.gradientRect(0xFF0D1522, 0xFF0A1019, 90, x, y + h * .62F, w, h * .38F, 0);
            }));
            outline.stroke(UiCanvas.mix(0x2CFFFFFF, AMBER, Math.max(chosen, lift * .5F)), 1 + chosen * .6F);
            // The notch carries the region number.
            c.trackedText("0" + (index + 1), x + w - notch - 2, y + 4, 8, chosen > .5F ? AMBER : MUTED, Font.BOLD, FONT, 1);
            float ty = y + h * .62F + 12;
            c.text(c.ellipsize(region.name(), w - 28, 12.5F, Font.BOLD, FONT), x + 14, ty, 12.5F, TEXT, Font.BOLD, FONT);
            for (int d = 0; d < 5; d++) c.roundRect(d < region.difficulty() ? AMBER : 0x26FFFFFF, x + 14 + d * 13, ty + 24, 9, 4, 2);
            c.text(region.distance() + " km", x + w - 14 - c.textWidth(region.distance() + " km", 9, Font.PLAIN, FONT), ty + 20, 9, MUTED, Font.PLAIN, FONT);
            if (chosen > .01F) {
                c.withOpacity(chosen, () -> {
                    c.roundRect(AMBER, x + 12, y + 12, 64, 18, 9);
                    c.centeredText("SELECTED", x + 44, y + 21, 7, NIGHT, Font.BOLD, FONT);
                });
            }
        });
    }

    // ------------------------------------------------------------------ loadout ring

    private static void loadout(UiContext ui, float x, float y, float w, float h, int region) {
        UiCanvas g = ui.canvas();
        g.shadow(0x60000000, 14, 0, 6, x, y, w, h, 20);
        g.roundRect(PANEL, x, y, w, h, 20);
        g.strokeRoundRect(0x26FFFFFF, .8F, x, y, w, h, 20);
        g.trackedText("LOADOUT", x + 22, y + 20, 9, AMBER, Font.BOLD, FONT, 2.6F);
        g.text("Six slots · tap a wedge", x + 22, y + 36, 10, FAINT, Font.PLAIN, FONT);
        int item = (int) ui.numberValue("item", 0);
        float cx = x + w / 2, cy = y + 62 + 104, outer = Math.min(104, w / 2 - 22), inner = outer * .5F;
        UiRect ring = new UiRect(cx - outer - 16, cy - outer - 16, outer * 2 + 32, outer * 2 + 32);
        float angle = ui.animateAngle("loadout-angle", item * 60, 560, UiEasing.SPRING);
        g.circle(0x40060A11, cx, cy, outer + 10);
        g.strokeCircle(0x16FFFFFF, .8F, cx, cy, outer + 10);
        for (int i = 0; i < ITEMS.length; i++) {
            float start = -120 + i * 60 + 2, sweep = 56;
            UiPath wedge = g.path().arc(cx, cy, outer, start, sweep).arc(cx, cy, inner, start + sweep, -sweep).close();
            boolean active = i == item;
            int index = i;
            float hover = ui.animate("wedge-hover:" + i, ui.hovered("item-" + i), 150, ring);
            ui.shapedButton("item-" + i, ITEMS[i], "item:" + i, wedge, true, (c, state) -> {
                wedge.fill(active ? 0x30FFB347 : UiCanvas.mix(RAISED, 0xFF1D2A3F, hover));
                wedge.stroke(active ? 0x90FFB347 : UiCanvas.mix(0x20FFFFFF, 0x70FFB347, hover), active ? 1.1F : .8F);
                double mid = Math.toRadians(start + sweep / 2);
                float r = (outer + inner) / 2;
                glyph(c, index, cx + (float) Math.cos(mid) * r, cy + (float) Math.sin(mid) * r,
                    active ? AMBER : UiCanvas.mix(MUTED, TEXT, hover));
            });
        }
        // The selector glides around the ring on the shortest path.
        g.withRotation(angle, cx, cy, () -> {
            g.path().arc(cx, cy, outer + 6, -118, 52).stroke(AMBER, 3);
            g.glow(0x30FFB347, 6, cx - 6, cy - outer - 10, 12, 8, 4);
        });
        g.circle(0xFF0E1724, cx, cy, inner - 6);
        g.strokeCircle(0x50FFB347, 1, cx, cy, inner - 6);
        g.centeredText(ITEMS[item], cx, cy - 4, 13, TEXT, Font.BOLD, FONT);
        g.centeredText("Tier " + (3 + item % 3), cx, cy + 13, 8.5F, MUTED, Font.PLAIN, FONT);

        // Animated stat bars, tuned by item and region.
        float barsY = cy + outer + 30;
        String[] labels = {"Endurance", "Warmth", "Light"};
        int[] stats = ITEM_STATS[item];
        int climate = REGIONS.get(region).temperature();
        for (int i = 0; i < labels.length; i++) {
            float target = Math.max(6, Math.min(100, stats[i] + (i == 1 ? Math.round(climate * -.8F) : 0) + (i == 2 && region == 1 ? -24 : 0)));
            float by = barsY + i * 34, bw = w - 44;
            float value = ui.animate("stat-" + i, target, 700, UiEasing.EASE_OUT, new UiRect(x, by - 4, w, 30));
            g.text(labels[i], x + 22, by, 10, MUTED, Font.BOLD, FONT);
            String number = Math.round(value) + "";
            g.text(number, x + w - 22 - g.textWidth(number, 10, Font.BOLD, FONT), by, 10, TEXT, Font.BOLD, FONT);
            g.roundRect(0xFF0E1724, x + 22, by + 17, bw, 6, 3);
            g.gradientRect(EMBER, 0xFFFFC56B, 0, x + 22, by + 17, bw * value / 100, 6, 3);
            g.circle(0xFFFFE0A0, x + 22 + bw * value / 100, by + 20, 3.6F);
        }

        // Expedition toggles and a styled difficulty picker.
        float optionsY = barsY + 3 * 34 + 10;
        g.fill(0x18FFFFFF, x + 22, optionsY - 8, w - 44, 1);
        g.text("Night vision", x + 22, optionsY + 2, 11, TEXT, Font.PLAIN, FONT);
        ui.toggle("night-vision", "night-vision", x + w - 22 - 31, optionsY, region == 1);
        g.text("Auto-torch", x + 22, optionsY + 32, 11, TEXT, Font.PLAIN, FONT);
        ui.toggle("auto-torch", "auto-torch", x + w - 22 - 31, optionsY + 30, true);
        float pickY = optionsY + 64;
        if (pickY + 34 < y + h - 12) {
            ui.dropdown("difficulty", "difficulty", x + 22, pickY, w - 44, 32, "veteran", List.of(
                    new UiSelectOption("scout", "Scout · relaxed"), new UiSelectOption("veteran", "Veteran · fair"),
                    new UiSelectOption("legend", "Legend · one life")),
                UiButtonStyle.builder().background(0xFF101A28).hoverBackground(0xFF16233A).border(0x40FFB347)
                    .text(TEXT).radius(10).textSize(10.5F).fontFamily(FONT).build(),
                UiMenuStyle.builder().background(0xF8101A28).border(0x50FFB347).highlight(0x33FFB347).text(TEXT)
                    .radius(12).rowRadius(8).fontFamily(FONT).build());
        }
        float crewY = pickY + 54;
        if (crewY + 70 < y + h - 8) crew(ui, x, crewY, w);
    }

    /** Readiness avatars: each ring fills when that member is ready. */
    private static void crew(UiContext ui, float x, float y, float w) {
        UiCanvas g = ui.canvas();
        int ready = 0;
        for (int i = 0; i < CREW.length; i++) if (ui.booleanValue("ready-" + i, CREW_READY[i])) ready++;
        g.fill(0x18FFFFFF, x + 22, y - 10, w - 44, 1);
        g.trackedText("CREW", x + 22, y, 8.5F, AMBER, Font.BOLD, FONT, 2.4F);
        String status = ready == CREW.length ? "ALL SET" : ready + " / " + CREW.length + " READY";
        float statusW = g.trackedTextWidth(status, 8, Font.BOLD, FONT, 1.4F);
        g.trackedText(status, x + w - 22 - statusW, y + 1, 8, ready == CREW.length ? AMBER : MUTED, Font.BOLD, FONT, 1.4F);
        float r = 17, spacing = (w - 44 - r * 2) / (CREW.length - 1);
        for (int i = 0; i < CREW.length; i++) {
            float cx = x + 22 + r + i * spacing, cy = y + 40;
            boolean isReady = ui.booleanValue("ready-" + i, CREW_READY[i]);
            int index = i;
            UiRect bounds = new UiRect(cx - r - 8, cy - r - 8, r * 2 + 16, r * 2 + 30);
            float fill = ui.animate("crew-ring:" + i, isReady, 520, UiEasing.EASE_OUT, bounds);
            UiPath avatar = g.path().arc(cx, cy, r, 0, 360).close();
            ui.shapedButton("crew-" + i, CREW[i] + (isReady ? " is ready" : " is not ready"), "ready:" + i, avatar, true, (c, state) -> {
                avatar.fillGradient(state.hovered() ? 0xFF33486B : 0xFF223450, 0xFF121C2B, 120);
                c.strokeCircle(0x22FFFFFF, 2.2F, cx, cy, r + 3.5F);
                if (fill > .01F) c.path().arc(cx, cy, r + 3.5F, -90, 360 * fill).stroke(AMBER, 2.2F);
                c.centeredText(CREW[index].substring(0, 1), cx, cy, 12, TEXT, Font.BOLD, FONT);
                c.circle(PANEL, cx + r * .72F, cy + r * .72F, 5);
                c.circle(UiCanvas.mix(0xFF56637B, 0xFF4FE3A1, fill), cx + r * .72F, cy + r * .72F, 3.4F);
                c.centeredText(CREW[index], cx, cy + r + 12, 8.5F, MUTED, Font.PLAIN, FONT);
            });
        }
    }

    // ------------------------------------------------------------------ GPU details sheet

    private static void sheet(UiContext ui, UiRect b, int region) {
        boolean open = ui.booleanValue("sheet", false);
        Region current = REGIONS.get(region);
        float pad = 18;
        UiRect layer = b.inset(-pad);
        float closeX = b.right() - 34, closeY = b.y() + 32;
        boolean closeHover = open && ui.hovered("sheet-close");
        ui.gpuLayer("sheet", layer, region * 2 + (closeHover ? 1 : 0),
            open ? UiTransform.identity() : new UiTransform(46, 0, .98F, .98F, 0, 0), 420, UiEasing.EASE_OUT, g -> {
                g.shadow(0x90000000, 16, 0, 8, b.x(), b.y(), b.width(), b.height(), 20);
                g.roundRect(0xFF0D1623, b.x(), b.y(), b.width(), b.height(), 20);
                g.withRoundedClip(b.x(), b.y(), b.width(), 150, 20, () -> regionArt(g, new UiRect(b.x(), b.y(), b.width(), 150), region, false));
                g.gradientRect(0x000D1623, 0xFF0D1623, 90, b.x(), b.y() + 60, b.width(), 90, 0);
                g.strokeRoundRect(0x50FFB347, 1, b.x(), b.y(), b.width(), b.height(), 20);
                g.circle(closeHover ? AMBER : 0x88060A11, closeX, closeY, 13);
                UiIcon.CLOSE.draw(g, closeHover ? NIGHT : TEXT, closeX - 6, closeY - 6, 12, 1.8F);
                float left = b.x() + 22, top = b.y() + 132;
                g.trackedText("FIELD NOTES", left, top, 8.5F, AMBER, Font.BOLD, FONT, 2.4F);
                g.text(current.name(), left, top + 16, 21, TEXT, Font.BOLD, FONT);
                float used = g.wrappedText(current.lore(), left, top + 50, b.width() - 44, 11, 17, 0xFFC5D0E2, Font.PLAIN, FONT);
                float rewardsY = top + 64 + used;
                g.trackedText("REWARDS", left, rewardsY, 8, MUTED, Font.BOLD, FONT, 2);
                for (int i = 0; i < current.rewards().size(); i++) {
                    float ry = rewardsY + 22 + i * 30;
                    g.roundRect(0xFF121D2D, left, ry, b.width() - 44, 24, 8);
                    float gx = left + 14, gy = ry + 12;
                    g.path().moveTo(gx, gy - 6).lineTo(gx + 6, gy).lineTo(gx, gy + 6).lineTo(gx - 6, gy).close().fillGradient(0xFFFFE0A0, EMBER, 90);
                    g.text(current.rewards().get(i), left + 30, ry + 6, 11, TEXT, Font.PLAIN, FONT);
                }
            });
        if (open) {
            // The layer is drawing-only: claim its area so the ring beneath stays inert, then the close target.
            ui.hit("sheet-surface", "sheet-noop", b.x(), b.y(), b.width(), b.height());
            ui.hit("sheet-close", "sheet-close", closeX - 14, closeY - 14, 28, 28);
        }
    }

    // ------------------------------------------------------------------ shapes and glyphs

    private static UiPath hexagon(UiCanvas g, float cx, float cy, float r) {
        UiPath path = g.path();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i - 90);
            path.lineTo(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
        }
        return path.close();
    }

    private static UiPath parallelogram(UiCanvas g, float x, float y, float w, float h, float slant) {
        return g.path().moveTo(x + slant, y).lineTo(x + w, y).lineTo(x + w - slant, y + h).lineTo(x, y + h).close();
    }

    private static UiPath chamfer(UiCanvas g, float x, float y, float w, float h, float cut) {
        return g.path().moveTo(x + cut, y).lineTo(x + w, y).lineTo(x + w, y + h - cut).lineTo(x + w - cut, y + h)
            .lineTo(x, y + h).lineTo(x, y + cut).close();
    }

    private static UiPath chevron(UiCanvas g, float x, float y, float w, float h, float point, boolean first, boolean last) {
        UiPath path = g.path().moveTo(x, y).lineTo(x + w - (last ? 0 : point), y);
        if (last) path.lineTo(x + w, y).lineTo(x + w, y + h);
        else path.lineTo(x + w, y + h / 2).lineTo(x + w - point, y + h);
        path.lineTo(x, y + h);
        if (!first) path.lineTo(x + point, y + h / 2);
        return path.close();
    }

    /** Rounded card with its top-right corner cut away. */
    private static UiPath notched(UiCanvas g, float x, float y, float w, float h, float notch) {
        float r = 14;
        return g.path().moveTo(x + r, y).lineTo(x + w - notch - 22, y).lineTo(x + w - notch - 10, y + notch)
            .lineTo(x + w, y + notch).lineTo(x + w, y + h - r).curveTo(x + w, y + h, x + w - r, y + h)
            .lineTo(x + r, y + h).curveTo(x, y + h, x, y + h - r).lineTo(x, y + r).curveTo(x, y, x + r, y).close();
    }

    /** Small code-drawn item glyphs, centred on (cx, cy). */
    private static void glyph(UiCanvas g, int item, float cx, float cy, int color) {
        switch (item) {
            case 0 -> { // pickaxe: a curved head across a diagonal haft
                g.line(color, 2.2F, cx - 8, cy + 9, cx + 4, cy - 3);
                g.path().arc(cx + 7, cy + 6, 13, -168, 78).stroke(color, 2.6F);
            }
            case 1 -> { // sword
                g.line(color, 2.4F, cx - 8, cy + 8, cx + 8, cy - 8);
                g.line(color, 2.2F, cx - 9, cy + 2, cx - 2, cy + 9);
                g.circle(color, cx - 9, cy + 9, 1.8F);
            }
            case 2 -> { // bow
                g.path().moveTo(cx - 4, cy - 11).curveTo(cx + 9, cy, cx - 4, cy + 11).stroke(color, 2.2F);
                g.line(color, 1, cx - 4, cy - 11, cx - 4, cy + 11);
                g.line(color, 1.6F, cx - 10, cy, cx + 7, cy);
            }
            case 3 -> // shield
                g.path().moveTo(cx - 8, cy - 9).lineTo(cx + 8, cy - 9).lineTo(cx + 8, cy - 1)
                    .curveTo(cx + 8, cy + 6, cx, cy + 11).curveTo(cx - 8, cy + 6, cx - 8, cy - 1).close().stroke(color, 2);
            case 4 -> { // lantern: handle, cap, glass and flame
                g.path().arc(cx, cy - 7, 4, 180, 180).stroke(color, 1.5F);
                g.line(color, 2, cx - 6, cy - 6, cx + 6, cy - 6);
                g.strokeRoundRect(color, 1.6F, cx - 5, cy - 6, 10, 14, 2.5F);
                g.path().moveTo(cx, cy - 2).curveTo(cx + 3.5F, cy + 2, cx, cy + 5).curveTo(cx - 3.5F, cy + 2, cx, cy - 2).fill(color);
            }
            default -> { // compass
                g.strokeCircle(color, 1.8F, cx, cy, 9);
                g.polygon(color, cx + 5, cy - 5, cx + 1.5F, cy + 1.5F, cx - 1.5F, cy - 1.5F);
                g.polygon(UiCanvas.fade(color, .5F), cx - 5, cy + 5, cx + 1.5F, cy + 1.5F, cx - 1.5F, cy - 1.5F);
            }
        }
    }
}
