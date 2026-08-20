package dev.pane.demo;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Opens the lightweight native UI showcase with H. */
public final class PaneDemoClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("pane", "demo"));
    private static final KeyMapping OPEN_DEMO = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.pane_demo.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
    private static final KeyMapping OPEN_GALLERY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.pane_demo.gallery", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));
    private final DemoAutoTest autoTest = DemoAutoTest.enabled() ? new DemoAutoTest() : null;
    private int startupTicks;
    private boolean demoShown;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (autoTest != null) {
                autoTest.tick(client);
            } else if (!demoShown && ++startupTicks >= 20 && client.gui.screen() instanceof TitleScreen) {
                client.gui.setScreen(createDemoScreen());
                demoShown = true;
            }
            while (OPEN_DEMO.consumeClick()) {
                client.gui.setScreen(createDemoScreen());
            }
            while (OPEN_GALLERY.consumeClick()) {
                client.gui.setScreen(StyleGallery.createScreen());
            }
        });
    }

    private static Screen createDemoScreen() {
        return NativeDemoShowcase.createScreen();
    }
}
