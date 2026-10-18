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
    private static final KeyMapping OPEN_VIDEO_TEST = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.pane_demo.video_test", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY));
    private static final KeyMapping OPEN_TEXTURE_TEST = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.pane_demo.texture_test", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_T, CATEGORY));
    private static final KeyMapping TOGGLE_HUD = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.pane_demo.hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));
    private final StudioAutoTest studioTest = StudioAutoTest.enabled() ? new StudioAutoTest() : null;
    private final DemoAutoTest autoTest = DemoAutoTest.enabled() ? new DemoAutoTest() : null;
    private final DemoPerformanceTest performanceTest = Boolean.getBoolean("pane.demo.perftest") ? new DemoPerformanceTest() : null;
    private final DesignLabTest designTest = Boolean.getBoolean("pane.demo.designTest") ? new DesignLabTest() : null;
    private final ApiImprovementsTest apiTest = Boolean.getBoolean("pane.demo.apiTest") ? new ApiImprovementsTest() : null;
    private int startupTicks;
    private boolean demoShown;

    @Override
    public void onInitializeClient() {
        java.util.concurrent.CompletableFuture.runAsync(() -> dev.pane.ui.UiCanvas.prewarmFonts());
        FieldHudDemo.init();
        AtlasBenchmark.installHudHooks();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (apiTest != null) { apiTest.tick(client); return; }
            FieldHudDemo.tick(client);
            while (OPEN_DEMO.consumeClick()) {
                client.gui.setScreen(createDemoScreen());
            }
            while (OPEN_GALLERY.consumeClick()) {
                client.gui.setScreen(StyleGallery.createScreen());
            }
            while (OPEN_VIDEO_TEST.consumeClick()) {
                client.gui.setScreen(VideoSiteShowcase.createScreen());
            }
            while (OPEN_TEXTURE_TEST.consumeClick()) {
                client.gui.setScreen(TextureSkinShowcase.createScreen());
            }
            while (TOGGLE_HUD.consumeClick()) FieldHudDemo.toggle();
            if (studioTest != null && studioTest.running()) { studioTest.tick(client); return; }
            if (designTest != null) {
                designTest.tick(client);
                return;
            }
            if (performanceTest != null) {
                performanceTest.tick(client);
                return;
            }
            if (autoTest != null) {
                autoTest.tick(client);
            } else if (!demoShown && ++startupTicks >= 20 && client.gui.screen() instanceof TitleScreen) {
                client.gui.setScreen(createDemoScreen());
                demoShown = true;
            }

        });
    }

    private static Screen createDemoScreen() {
        return Boolean.getBoolean("pane.demo.designLab")
            ? DesignLabShowcase.createScreen(net.minecraft.client.Minecraft.getInstance().gui.screen())
            : ShowcaseHub.createScreen(net.minecraft.client.Minecraft.getInstance().gui.screen());
    }
}
