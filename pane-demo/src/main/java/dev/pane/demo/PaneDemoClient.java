package dev.pane.demo;

import dev.pane.ui.ResourceAuditTest;

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
    private static final KeyMapping OPEN_DISCORD = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.pane_demo.discord", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY));
    private static final KeyMapping OPEN_GMAIL = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.pane_demo.gmail", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));
    private static final KeyMapping TOGGLE_HUD = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.pane_demo.hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));
    private final StudioAutoTest studioTest = StudioAutoTest.enabled() ? new StudioAutoTest() : null;
    private final DemoAutoTest autoTest = DemoAutoTest.enabled() ? new DemoAutoTest() : null;
    private final DemoPerformanceTest performanceTest = Boolean.getBoolean("pane.demo.perftest") ? new DemoPerformanceTest() : null;
    private final DesignLabTest designTest = Boolean.getBoolean("pane.demo.designTest") ? new DesignLabTest() : null;
    private final CastleAuditTest castleTest = Boolean.getBoolean("pane.demo.castleTest") ? new CastleAuditTest() : null;
    private final ResourceAuditTest resourceTest = Boolean.getBoolean("pane.demo.resourceTest") ? new ResourceAuditTest() : null;
    private final EditingAuditTest editingTest = Boolean.getBoolean("pane.demo.editingTest") ? new EditingAuditTest() : null;
    private final AnimationAuditTest animationTest = Boolean.getBoolean("pane.demo.animationTest") ? new AnimationAuditTest() : null;
    private final EditorAuditTest editorTest = Boolean.getBoolean("pane.demo.editorTest") ? new EditorAuditTest() : null;
    private final DialogTypographyAuditTest dialogTypographyTest = Boolean.getBoolean("pane.demo.dialogTypographyTest") ? new DialogTypographyAuditTest() : null;
    private final DialogAuditTest dialogTest = Boolean.getBoolean("pane.demo.dialogTest") ? new DialogAuditTest() : null;
    private final PopupTriggerAuditTest popupTriggerTest = Boolean.getBoolean("pane.demo.popupTriggerTest") ? new PopupTriggerAuditTest() : null;
    private final PopupAuditTest popupTest = Boolean.getBoolean("pane.demo.popupTest") ? new PopupAuditTest() : null;
    private final ReviewFollowupTest reviewTest = Boolean.getBoolean("pane.demo.reviewTest") ? new ReviewFollowupTest() : null;
    private final ApiImprovementsTest apiTest = Boolean.getBoolean("pane.demo.apiTest") ? new ApiImprovementsTest() : null;
    private final PlayerPreviewAuditTest playerPreviewTest = Boolean.getBoolean("pane.demo.playerPreviewTest") ? new PlayerPreviewAuditTest() : null;
    private final WayfarerAuditTest wayfarerTest = Boolean.getBoolean("pane.demo.wayfarerTest") ? new WayfarerAuditTest() : null;
    private final GmailAuditTest gmailTest = Boolean.getBoolean("pane.demo.gmailTest") ? new GmailAuditTest() : null;
    private final DiscordAuditTest discordTest = Boolean.getBoolean("pane.demo.discordTest") ? new DiscordAuditTest() : null;
    private final ScrollEndAuditTest scrollEndTest = Boolean.getBoolean("pane.demo.scrollEndTest") ? new ScrollEndAuditTest() : null;
    private final WayfarerHudShowcase wayfarerHud = Boolean.getBoolean("pane.demo.wayfarerHud") ? new WayfarerHudShowcase() : null;
    private final BrambleHudShowcase bramble = Boolean.getBoolean("pane.demo.bramble") ? new BrambleHudShowcase() : null;
    private final ExamplesAuditTest examplesTest = Boolean.getBoolean("pane.demo.exampleTest") ? new ExamplesAuditTest() : null;
    private final AdoptionAuditTest adoptionTest = Boolean.getBoolean("pane.demo.adoptionTest") ? new AdoptionAuditTest() : null;
    private int startupTicks;
    private boolean demoShown;

    @Override
    public void onInitializeClient() {
        java.util.concurrent.CompletableFuture.runAsync(() -> dev.pane.ui.UiCanvas.prewarmFonts());
        FieldHudDemo.init();
        if (!StudioAutoTest.enabled()) FieldHudDemo.setEnabled(false);
        ExampleHudController.select(System.getProperty("pane.demo.example", "gallery"));
        AtlasBenchmark.installHudHooks();
        if(bramble!=null)bramble.init();
        if(wayfarerHud!=null)wayfarerHud.init();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (adoptionTest != null) { adoptionTest.tick(client); return; }
            if(wayfarerHud!=null){while(TOGGLE_HUD.consumeClick())if(client.gui.screen()==null)wayfarerHud.toggle();wayfarerHud.tick(client);return;}
            if(bramble!=null){while(TOGGLE_HUD.consumeClick())if(client.gui.screen()==null)bramble.toggle();bramble.tick(client);return;}
            if (discordTest != null) { discordTest.tick(client); return; }
            if (scrollEndTest != null) { scrollEndTest.tick(client); return; }
            if (playerPreviewTest != null) { playerPreviewTest.tick(client); return; }
            if (wayfarerTest != null) { wayfarerTest.tick(client); return; }
            if (Boolean.getBoolean("pane.demo.wayfarer") && WayfarerPreview.tick(client)) return;
            if (gmailTest != null) { gmailTest.tick(client); return; }
            if (castleTest != null) { castleTest.tick(client); return; }
            if (resourceTest != null) { resourceTest.tick(client); return; }
            if (editingTest != null) { editingTest.tick(client); return; }
            if (animationTest != null) { animationTest.tick(client); return; }
            if (editorTest != null) { editorTest.tick(client); return; }
            if (dialogTypographyTest != null) { dialogTypographyTest.tick(client); return; }
            if (dialogTest != null) { dialogTest.tick(client); return; }
            if (popupTriggerTest != null) { popupTriggerTest.tick(client); return; }
            if (popupTest != null) { popupTest.tick(client); return; }
            if (reviewTest != null) { reviewTest.tick(client); return; }
            if (apiTest != null) { apiTest.tick(client); return; }
            FieldHudDemo.tick(client);
            ExampleHudController.tick(client);
            while (OPEN_DEMO.consumeClick()) {
                client.gui.setScreen(createDemoScreen());
            }
            while (OPEN_DISCORD.consumeClick()) { client.gui.setScreen(DiscordShowcase.createScreen(client.gui.screen())); }
            while (OPEN_GMAIL.consumeClick()) {
                client.gui.setScreen(GmailShowcase.createScreen(client.gui.screen()));
            }
            while (TOGGLE_HUD.consumeClick()) {
                if (StudioAutoTest.enabled()) FieldHudDemo.toggle(); else ExampleHudController.toggle();
            }
            if (examplesTest != null) { examplesTest.tick(client); return; }
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
                if (!ExampleHudController.active()) client.gui.setScreen(createDemoScreen());
                demoShown = true;
            }

        });
    }

    private static Screen createDemoScreen() {
        var parent = net.minecraft.client.Minecraft.getInstance().gui.screen();
        switch (System.getProperty("pane.demo.example", "gallery")) {
            case "wayfarer": return WayfarerShowcase.createScreen(parent);
            case "gather": return DiscordShowcase.createScreen(parent);
            case "mail": return GmailShowcase.createScreen(parent);
        }
        if (Boolean.getBoolean("pane.demo.wayfarer")) return WayfarerShowcase.createScreen(net.minecraft.client.Minecraft.getInstance().gui.screen());
        if (Boolean.getBoolean("pane.demo.discord")) return DiscordShowcase.createScreen(net.minecraft.client.Minecraft.getInstance().gui.screen());
        if (Boolean.getBoolean("pane.demo.gmail")) return GmailShowcase.createScreen(net.minecraft.client.Minecraft.getInstance().gui.screen());
        if (Boolean.getBoolean("pane.demo.castle")) return CastleShowcase.createScreen(net.minecraft.client.Minecraft.getInstance().gui.screen());
        return Boolean.getBoolean("pane.demo.designLab")
            ? DesignLabShowcase.createScreen(net.minecraft.client.Minecraft.getInstance().gui.screen())
            : StudioAutoTest.enabled() ? ShowcaseHub.createScreen(parent) : ExampleGallery.createScreen(parent);
    }
}
