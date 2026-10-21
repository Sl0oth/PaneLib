package dev.pane.demo;

import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Owns only the currently selected recipe. World creation and inventory remain Minecraft's job. */
final class ExampleHudController {
    private static WayfarerHudShowcase wayfarer;
    private static UiHud vanilla;
    private static String selected = "none";

    static void select(String name) {
        if (!name.equals("wayfarer-hud") && !name.equals("vanilla-hud")) name = "none";
        if (selected.equals(name)) return;
        if (wayfarer != null) { wayfarer.close(); wayfarer = null; }
        if (vanilla != null) { vanilla.close(); vanilla = null; }
        selected = name;
        if (name.equals("wayfarer-hud")) {
            wayfarer = new WayfarerHudShowcase();
            wayfarer.init();
        } else if (name.equals("vanilla-hud")) {
            vanilla = VanillaHudExample.create();
            vanilla.setPlacementPolicy(viewport -> new UiHudPlacement(UiHudPlacement.Anchor.TOP_LEFT, 12, 12, 1));
            vanilla.attach(Identifier.fromNamespaceAndPath("pane_demo", "vanilla_recipe"));
        }
    }

    static boolean active() { return !selected.equals("none"); }
    static String selected() { return selected; }
    static void tick(Minecraft client) { if (wayfarer != null) wayfarer.tickLive(client); }
    static void toggle() {
        if (wayfarer != null) wayfarer.toggle();
        if (vanilla != null) vanilla.setEnabled(!vanilla.enabled());
    }
    static WayfarerHudShowcase wayfarer() { return wayfarer; }
    static UiHud vanilla() { return vanilla; }
}
