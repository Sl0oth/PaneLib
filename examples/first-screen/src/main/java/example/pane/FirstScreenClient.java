package example.pane;

import com.mojang.blaze3d.platform.InputConstants;
import dev.pane.ui.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
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
    private final Properties config = new Properties();
    private UiScreen screen;
    private boolean opened;
    private final FirstScreenSmokeTest smoke = SMOKE ? new FirstScreenSmokeTest(CONFIG) : null;

    @Override
    public void onInitializeClient() {
        if (Files.exists(CONFIG)) {
            try (InputStream in = Files.newInputStream(CONFIG)) { config.load(in); }
            catch (IOException error) { System.err.println("Could not read example settings: " + error); }
        }
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
            .page(new UiPage("settings", "Settings", "", 0xFF58A6FF, ui -> {
                var g = ui.canvas();
                g.fill(ui.theme().background(), 0, 0, ui.width(), ui.height());
                float x = 24, width = Math.max(120, Math.min(440, ui.width() - 48));
                g.textMedium("Your first PaneLib screen", x, 24, 22, ui.theme().text());
                g.text("Display name", x, 68, 12, ui.theme().mutedText());
                ui.textField("name", "name", "Display name", x, 90, width, "Player");
                g.text("Show hints", x, 140, 12, ui.theme().text());
                ui.toggle("hints", "hints", x + width - 48, 136, true);
                g.text("Volume (arrows adjust; Home/End select limits)", x, 184, 11, ui.theme().mutedText());
                ui.slider("volume", "volume", x, 209, width, 0, 100, 50);
                ui.button("save", "Save", "save", x, 252, 100, 32, UiContext.ButtonStyle.PRIMARY, UiContext.Shape.ROUNDED);
                ui.button("cancel", "Cancel", "cancel", x + 112, 252, 100, 32, UiContext.ButtonStyle.GHOST, UiContext.Shape.ROUNDED);
                g.text(ui.stringValue("status", "Tab moves focus. Escape returns to the previous screen."),
                    x, 308, 11, ui.theme().mutedText());
            })).build();
        screen = UiScreen.create(document, NativeOverlayRenderer.NONE, (owner, event) -> {
            if (event.type() != UiEventType.ACTION) return;
            if (event.key().equals("cancel")) owner.onClose();
            if (event.key().equals("save")) save(owner);
        }, UiScreenOptions.builder().parent(parent).pauseGame(true).build());
        screen.setValue("name", config.getProperty("name", "Player"));
        screen.setValue("hints", Boolean.parseBoolean(config.getProperty("hints", "true")));
        double volume;
        try { volume = Double.parseDouble(config.getProperty("volume", "50")); }
        catch (NumberFormatException invalid) { volume = 50; }
        screen.setValue("volume", Double.isFinite(volume) ? Math.clamp(volume, 0, 100) : 50);
        client.gui.setScreen(screen);
    }

    private void save(UiScreen owner) {
        Properties updated = new Properties();
        updated.setProperty("name", owner.stringValue("name", "Player"));
        updated.setProperty("hints", Boolean.toString(owner.booleanValue("hints", true)));
        updated.setProperty("volume", Double.toString(owner.numberValue("volume", 50)));
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG)) { updated.store(out, "PaneLib example settings"); }
            config.clear(); config.putAll(updated);
            owner.setValue("status", "Saved. Close and reopen with H to load these settings.");
        } catch (IOException error) {
            owner.setValue("status", "Could not save: " + error.getClass().getSimpleName());
        }
    }

}
