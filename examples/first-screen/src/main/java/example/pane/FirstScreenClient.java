package example.pane;

import com.mojang.blaze3d.platform.InputConstants;
import dev.pane.ui.*;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** A complete consumer with real persistence, no demo classes or library source dependency. */
public final class FirstScreenClient implements ClientModInitializer {
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("pane-example.properties");
    private static final boolean SMOKE = Boolean.getBoolean("pane.example.smokeTest");
    private UiScreen screen;
    private boolean opened;
    private final FirstScreenSmokeTest smoke = SMOKE ? new FirstScreenSmokeTest(CONFIG) : null;

    @Override
    public void onInitializeClient() {
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("pane_example", "settings"));
        var open = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.pane_example.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Open once at the title screen so trying the example needs no world.
            if (!opened && client.gui.screen() instanceof TitleScreen) {
                opened = true; show(client, client.gui.screen());
            }
            while (open.consumeClick()) show(client, client.gui.screen());
            if (smoke != null) smoke.tick(client, screen, this::show);
        });
    }

    private void show(Minecraft client, Screen parent) {
        var document = UiDocument.builder("Example settings")
            .theme(UiTheme.dark()).scaling(UiScaling.fixed())
            .page(UiPage.of("settings", "Settings", ui -> {
                var g = ui.canvas();
                g.fill(ui.theme().background(), 0, 0, ui.width(), ui.height());
                float x = 24, width = Math.max(120, Math.min(440, ui.width() - 48));
                g.textMedium("Your first PaneLib screen", x, 24, 22, ui.theme().text());
                g.text("Display name", x, 68, 12, ui.theme().mutedText());
                ui.textField("name", "Display name", x, 90, width, "Player");
                g.text("Show hints", x, 140, 12, ui.theme().text());
                ui.toggle("hints", x + width - 48, 136, true);
                g.text("Volume (arrows adjust; Home/End select limits)", x, 184, 11, ui.theme().mutedText());
                ui.slider("volume", x, 209, width, 0, 100, 50);
                ui.button("save", "Save", "save", new UiRect(x, 252, 100, 32), UiContext.ButtonStyle.PRIMARY);
                ui.button("cancel", "Cancel", "cancel", new UiRect(x + 112, 252, 100, 32), UiContext.ButtonStyle.GHOST);
                g.text(ui.stringValue("status", "Tab moves focus. Escape returns to the previous screen."),
                    x, 308, 11, ui.theme().mutedText());
            })).build();
        // settings() loads the saved values now; the screen edits a draft until Save writes it.
        screen = UiScreen.create(document, UiScreenOptions.builder().parent(parent).pauseGame(true).build())
            .settings(CONFIG, Map.of("name", "Player", "hints", true, "volume", 50))
            .onAction("cancel", UiScreen::onClose)
            .onAction("save", owner -> owner.setValue("status", owner.saveSettings()
                ? "Saved. Close and reopen with H to load these settings."
                : "Could not save the settings file."));
        double volume = screen.numberValue("volume", 50);
        screen.setValue("volume", Math.clamp(volume, 0, 100));
        screen.open();
    }

}
