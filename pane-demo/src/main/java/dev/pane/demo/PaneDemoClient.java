package dev.pane.demo;

import dev.pane.ui.ResourceAuditTest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Opens the PaneLib showcase launcher with H; J toggles the selected example HUD. */
public final class PaneDemoClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("pane", "demo"));
    private static final KeyMapping OPEN_DEMO = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.pane_demo.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
    private static final KeyMapping TOGGLE_HUD = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.pane_demo.hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));
    private final DemoPerformanceTest performanceTest = Boolean.getBoolean("pane.demo.perftest") ? new DemoPerformanceTest() : null;
    private final ResourceAuditTest resourceTest = Boolean.getBoolean("pane.demo.resourceTest") ? new ResourceAuditTest() : null;
    private final EditingAuditTest editingTest = Boolean.getBoolean("pane.demo.editingTest") ? new EditingAuditTest() : null;
    private final AnimationAuditTest animationTest = Boolean.getBoolean("pane.demo.animationTest") ? new AnimationAuditTest() : null;
    private final EditorAuditTest editorTest = Boolean.getBoolean("pane.demo.editorTest") ? new EditorAuditTest() : null;
    private final GpuLayerAuditTest gpuLayerTest = Boolean.getBoolean("pane.demo.gpuLayerTest") ? new GpuLayerAuditTest() : null;
    private final PaneOsPerfTest paneOsPerf = Boolean.getBoolean("pane.demo.paneOsPerf") ? new PaneOsPerfTest() : null;
    private final PaneOsAuditTest paneOsTest = Boolean.getBoolean("pane.demo.paneOsTest") ? new PaneOsAuditTest() : null;
    private final DialogTypographyAuditTest dialogTypographyTest = Boolean.getBoolean("pane.demo.dialogTypographyTest") ? new DialogTypographyAuditTest() : null;
    private final DialogAuditTest dialogTest = Boolean.getBoolean("pane.demo.dialogTest") ? new DialogAuditTest() : null;
    private final PopupTriggerAuditTest popupTriggerTest = Boolean.getBoolean("pane.demo.popupTriggerTest") ? new PopupTriggerAuditTest() : null;
    private final PopupAuditTest popupTest = Boolean.getBoolean("pane.demo.popupTest") ? new PopupAuditTest() : null;
    private final ReviewFollowupTest reviewTest = Boolean.getBoolean("pane.demo.reviewTest") ? new ReviewFollowupTest() : null;
    private final ApiImprovementsTest apiTest = Boolean.getBoolean("pane.demo.apiTest") ? new ApiImprovementsTest() : null;
    private final PlayerPreviewAuditTest playerPreviewTest = Boolean.getBoolean("pane.demo.playerPreviewTest") ? new PlayerPreviewAuditTest() : null;
    private final WayfarerAuditTest wayfarerTest = Boolean.getBoolean("pane.demo.wayfarerTest") ? new WayfarerAuditTest() : null;
    private final GmailAuditTest gmailTest = Boolean.getBoolean("pane.demo.gmailTest") ? new GmailAuditTest() : null;
    private final WayfarerHudShowcase wayfarerHud = Boolean.getBoolean("pane.demo.wayfarerHud") ? new WayfarerHudShowcase() : null;
    private final ExamplesAuditTest examplesTest = Boolean.getBoolean("pane.demo.exampleTest") ? new ExamplesAuditTest() : null;
    private final WindowHudAuditTest windowHudTest = Boolean.getBoolean("pane.demo.windowHudTest") ? new WindowHudAuditTest() : null;
    private final AdoptionAuditTest adoptionTest = Boolean.getBoolean("pane.demo.adoptionTest") ? new AdoptionAuditTest() : null;
    private int startupTicks;
    private boolean demoShown;

    @Override
    public void onInitializeClient() {
        java.util.concurrent.CompletableFuture.runAsync(() -> dev.pane.ui.UiCanvas.prewarmFonts());
        ExampleHudController.select(System.getProperty("pane.demo.example", "gallery"));
        if(wayfarerHud!=null)wayfarerHud.init();
        if (windowHudTest != null) windowHudTest.install();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (adoptionTest != null) { adoptionTest.tick(client); return; }
            if(wayfarerHud!=null){while(TOGGLE_HUD.consumeClick())if(client.gui.screen()==null)wayfarerHud.toggle();wayfarerHud.tick(client);return;}
            if (playerPreviewTest != null) { playerPreviewTest.tick(client); return; }
            if (wayfarerTest != null) { wayfarerTest.tick(client); return; }
            if (Boolean.getBoolean("pane.demo.wayfarer") && WayfarerPreview.tick(client)) return;
            if (gmailTest != null) { gmailTest.tick(client); return; }
            if (resourceTest != null) { resourceTest.tick(client); return; }
            if (editingTest != null) { editingTest.tick(client); return; }
            if (animationTest != null) { animationTest.tick(client); return; }
            if (editorTest != null) { editorTest.tick(client); return; }
            if (gpuLayerTest != null) { gpuLayerTest.tick(client); return; }
            if (paneOsTest != null) { paneOsTest.tick(client); return; }
            if (paneOsPerf != null) { paneOsPerf.tick(client); return; }
            if (dialogTypographyTest != null) { dialogTypographyTest.tick(client); return; }
            if (dialogTest != null) { dialogTest.tick(client); return; }
            if (popupTriggerTest != null) { popupTriggerTest.tick(client); return; }
            if (popupTest != null) { popupTest.tick(client); return; }
            if (reviewTest != null) { reviewTest.tick(client); return; }
            if (apiTest != null) { apiTest.tick(client); return; }
            ExampleHudController.tick(client);
            while (OPEN_DEMO.consumeClick()) client.gui.setScreen(ExampleGallery.createScreen(client.gui.screen()));
            while (TOGGLE_HUD.consumeClick()) ExampleHudController.toggle();
            if (examplesTest != null) { examplesTest.tick(client); return; }
            if (windowHudTest != null) { windowHudTest.tick(client); return; }
            if (performanceTest != null) { performanceTest.tick(client); return; }
            if (!demoShown && ++startupTicks >= 20 && client.gui.screen() instanceof TitleScreen) {
                if (!ExampleHudController.active()) client.gui.setScreen(ExampleGallery.createScreen(client.gui.screen()));
                demoShown = true;
            }

        });
    }
}
