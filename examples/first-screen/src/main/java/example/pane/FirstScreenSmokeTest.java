package example.pane;

import dev.pane.ui.UiScreen;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/** Optional verification only; a consuming mod can delete this class and its two call sites. */
final class FirstScreenSmokeTest {
    private final Path config;
    private int startupTicks, ticks;

    FirstScreenSmokeTest(Path config) { this.config = config; }

    void tick(Minecraft client, UiScreen screen, BiConsumer<Minecraft, Screen> reopen) {
        try {
            if (screen == null || screen.paintCount() == 0) {
                if (++startupTicks > 600) throw new AssertionError("Startup blocked by " + client.gui.screen());
                return;
            }
            if (++ticks == 10) {
                var origin = UiScreen.class.getProtectionDomain().getCodeSource().getLocation();
                if (!origin.getPath().endsWith(".jar")) throw new AssertionError("Library did not load from a JAR: " + origin);
                System.out.println("[ConsumerTest] library JAR: " + origin);
                if (!screen.isPauseScreen()) throw new AssertionError("Settings screen must request single-player pause");
                if (!screen.focus("volume")) throw new AssertionError("Volume not focusable");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_HOME, 0, 0));
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_RIGHT, 0, 0));
                if (screen.numberValue("volume", -1) != 1) throw new AssertionError("Keyboard slider failed");
                screen.setValue("name", ""); screen.focus("name");
                "New user".codePoints().forEach(cp -> screen.charTyped(new CharacterEvent(cp)));
                screen.focus("save"); screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                Properties saved = new Properties();
                try (InputStream in = Files.newInputStream(config)) { saved.load(in); }
                if (!saved.getProperty("name", "").equals("New user") || !saved.getProperty("volume", "").equals("1.0"))
                    throw new AssertionError("Settings not persisted");
                System.out.println("[ConsumerTest] input, slider, keyboard Save and disk persistence PASS");
                client.getWindow().setWindowed(800, 600);
            }
            if (ticks == 30) {
                Screenshot.grab(client.gameDirectory, "consumer-settings.png", client.gameRenderer.mainRenderTarget(), 1, m -> {});
                // Cancel must discard the screen's draft while keeping the last successful Save.
                screen.setValue("name", "Unsaved edit");
                screen.focus("cancel"); screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                if (!(client.gui.screen() instanceof TitleScreen)) throw new AssertionError("Cancel did not restore parent");
                reopen.accept(client, client.gui.screen());
            }
            if (ticks == 40) {
                if (!screen.stringValue("name", "").equals("New user")) throw new AssertionError("Cancel committed unsaved edit");
                screen.setValue("name", "Another unsaved edit");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                if (!(client.gui.screen() instanceof TitleScreen)) throw new AssertionError("Escape did not restore parent");
                reopen.accept(client, client.gui.screen());
            }
            if (ticks == 50) {
                if (!screen.stringValue("name", "").equals("New user")) throw new AssertionError("Escape committed unsaved edit");
                System.out.println("[ConsumerTest] resize, Cancel/Escape draft discard, parent restore and reopen PASS");
                System.out.println("[ConsumerTest] complete PASS"); client.stop();
            }
        } catch (Throwable error) {
            System.out.println("[ConsumerTest] complete FAIL " + error); error.printStackTrace(); client.stop();
        }
    }
}
