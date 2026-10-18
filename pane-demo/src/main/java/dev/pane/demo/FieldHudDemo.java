package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Consumer-owned live data sampling; the library only owns retained drawing/lifecycle. */
final class FieldHudDemo {
    static final int BLUE = 0xFF64D4FF, WHITE = 0xFFF0F9FF;
    private static UiHud vitals, navigation;
    private static Object world;
    private static Telemetry telemetry;
    private static int ticks;
    private static boolean enabled = true, reduced, previewing;
    private static float scale = 1, unwrappedHeading;
    private static UiHudPlacement.Anchor anchor = UiHudPlacement.Anchor.TOP_LEFT;

    record Telemetry(int x, int y, int z, int health, int maxHealth, int food, int armor, int heading, int experience, String dimension) { }
    static void init() {
        if (vitals != null) return;
        vitals = new UiHud(244, 112, UiTheme.dark().withAccent(BLUE), FieldHudDemo::paintVitals)
            .attach(Identifier.fromNamespaceAndPath("pane_demo", "field_vitals"));
        navigation = new UiHud(194, 75, UiTheme.dark().withAccent(BLUE), FieldHudDemo::paintNavigation)
            .attach(Identifier.fromNamespaceAndPath("pane_demo", "field_navigation"));
        applyPlacement();
    }
    static UiHud vitals() { return vitals; }
    static UiHud navigation() { return navigation; }
    static Telemetry telemetry() { return telemetry; }
    static boolean enabled() { return enabled; }
    static UiHudPlacement.Anchor anchor() { return anchor; }
    static float scale() { return scale; }
    static boolean reduced() { return reduced; }
    static void toggle() { setEnabled(!enabled); }
    static void setEnabled(boolean value) { enabled = value; vitals.setVisible(value); navigation.setVisible(value); }
    static void setReduced(boolean value) {
        reduced = value; vitals.setMotionScale(value ? 0 : 1); navigation.setMotionScale(value ? 0 : 1);
    }
    static void setScale(float value) { scale = Math.max(.65F, Math.min(1.5F, value)); applyPlacement(); }
    static void cycleAnchor() {
        var anchors = UiHudPlacement.Anchor.values(); anchor = anchors[(anchor.ordinal() + 1) % anchors.length]; applyPlacement();
    }
    static void preview(boolean value) { previewing = value; applyPlacement(); vitals.setShowOnScreens(value); navigation.setShowOnScreens(value); }
    private static void applyPlacement() {
        vitals.setPlacement(new UiHudPlacement(previewing ? UiHudPlacement.Anchor.TOP_RIGHT : anchor, 18, previewing ? 166 : 18, scale));
        navigation.setPlacement(new UiHudPlacement(anchor == UiHudPlacement.Anchor.BOTTOM_RIGHT
            ? UiHudPlacement.Anchor.BOTTOM_LEFT : UiHudPlacement.Anchor.BOTTOM_RIGHT, 18, previewing ? 76 : 22, scale));
    }

    static void tick(Minecraft client) {
        if (client.level != world) { world = client.level; telemetry = null; }
        if (telemetry == null || ++ticks % 5 == 0) sample(client);
    }
    static void sample(Minecraft client) {
        if (client.player == null || client.level == null) return;
        var player = client.player;
        var position = player.blockPosition();
        var next = new Telemetry(position.getX(), position.getY(), position.getZ(), Math.round(player.getHealth()),
            Math.round(player.getMaxHealth()), player.getFoodData().getFoodLevel(), player.getArmorValue(),
            Math.floorMod(Math.round(player.getYRot() / 5) * 5, 360), player.experienceLevel,
            client.level.dimension().identifier().getPath());
        if (next.equals(telemetry)) return;
        unwrappedHeading = telemetry == null ? next.heading
            : unwrappedHeading + Math.floorMod(next.heading - telemetry.heading + 540, 360) - 180;
        telemetry = next;
        navigation.setValue("unwrapped-heading", (double) unwrappedHeading);
        vitals.setValue("live", next); navigation.setValue("live", next);
    }

    private static void plate(UiCanvas g, float width, float height) {
        g.roundRect(0xB0081B2B, 1, 1, width - 2, height - 2, 13);
        g.strokeRoundRect(0x7076D6FF, .8F, 1, 1, width - 2, height - 2, 13);
        g.fill(0xB064D4FF, 14, 13, 3, 10);
    }
    private static void paintVitals(UiContext ui) {
        var g = ui.canvas();
        plate(g, ui.width(), ui.height());
        Telemetry data = ui.value("live") instanceof Telemetry live ? live : new Telemetry(0, 0, 0, 20, 20, 20, 0, 0, 0, "syncing");
        float health = ui.animate("meter-health", data.health, 360, UiEasing.EASE_OUT);
        float food = ui.animate("meter-food", data.food, 300, UiEasing.EASE_OUT);
        float altitude = ui.animate("counter-altitude", data.y, 380, UiEasing.EASE_OUT);
        g.text("ATLAS / FIELD LINK", 23, 13, 8, BLUE, Font.BOLD, UiCanvas.MONO);
        g.circle(0xFF72E5AB, 222, 18, 2.5F);
        ring(g, 44, 66, 28, health / Math.max(1, data.maxHealth), health < 8 ? 0xFFFFA58A : BLUE);
        g.centeredText(Integer.toString(Math.round(health)), 44, 65, 17, WHITE, Font.BOLD);
        g.text("VITALS", 82, 40, 7, 0xFFAFC8DB, Font.BOLD, UiCanvas.MONO);
        g.text("ALT", 82, 57, 7, 0xFFAFC8DB, Font.PLAIN, UiCanvas.MONO);
        g.text(Integer.toString(Math.round(altitude)), 108, 51, 18, WHITE, Font.BOLD, UiCanvas.MONO);
        g.text("m", 164, 59, 8, 0xFFAFC8DB);
        g.text("LV " + data.experience, 190, 57, 9, BLUE, Font.BOLD, UiCanvas.MONO);
        g.roundRect(0x5036657D, 82, 83, 143, 5, 2);
        g.roundRect(0xFFCDEBC0, 82, 83, 143 * Math.max(0, Math.min(1, food / 20)), 5, 2);
        g.text("SUPPLIES", 82, 94, 6, 0xFFAFC8DB, Font.PLAIN, UiCanvas.MONO);
        g.text(data.food + "/20", 196, 94, 6, WHITE, Font.PLAIN, UiCanvas.MONO);
    }
    private static void paintNavigation(UiContext ui) {
        var g = ui.canvas();
        plate(g, ui.width(), ui.height());
        if (!(ui.value("live") instanceof Telemetry data)) return;
        // Shortest-path rotation is supplied as a stable unwrapped heading track.
        float angle = ui.animate("heading", (float) ui.numberValue("unwrapped-heading", data.heading), 280, UiEasing.EASE_OUT);
        g.text("SURVEY / " + data.dimension.toUpperCase(Locale.ROOT), 23, 13, 7, BLUE, Font.BOLD, UiCanvas.MONO);
        g.withTransform(UiTransform.identity().rotate(angle), 31, 49, () -> {
            g.polygon(BLUE, 31, 34, 23, 58, 31, 53, 39, 58);
        });
        g.text(data.x + "  /  " + data.y + "  /  " + data.z, 54, 33, 9, WHITE, Font.BOLD, UiCanvas.MONO);
        g.text(String.format(Locale.ROOT, "%03d°  •  J TO TOGGLE", data.heading), 54, 52, 7, 0xFFAFC8DB, Font.PLAIN, UiCanvas.MONO);
    }
    static void ring(UiCanvas g, float x, float y, float radius, float fraction, int color) {
        g.strokeCircle(0x5036657D, 4, x, y, radius);
        int segments = 48;
        float amount = Math.max(0, Math.min(1, fraction));
        if (amount <= 0) return;
        var path = g.path();
        for (int i = 0; i <= segments; i++) {
            double angle = Math.toRadians(-90 + 360 * amount * i / segments);
            float px = x + (float) Math.cos(angle) * radius, py = y + (float) Math.sin(angle) * radius;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.stroke(color, 4);
    }
}
