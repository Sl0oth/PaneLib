package dev.pane.backport;

import dev.pane.ui.UiRect;
import dev.pane.ui.UiScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** A separate client mod exercises the installed library through a consumer's public API. */
public final class ScreenSmoke implements ClientModInitializer {
    private UiScreen screen;
    private int firstPaint;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (screen == null) {
            if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
            screen = UiScreen.open("PaneLib port check", ui ->
                ui.form("settings", new UiRect(12, 12, 220, 100),
                    form -> form.toggle("Enabled", "enabled", false)));
            screen.setMotionScale(0);
            return;
        }
        if (screen.paintCount() == 0) return;
        if (firstPaint == 0) {
            UiRect toggle = screen.regionBounds("enabled").orElseThrow();
            var event = new MouseButtonEvent(toggle.centerX() / screen.layoutScale(),
                toggle.centerY() / screen.layoutScale(), new MouseButtonInfo(0, 0));
            screen.mouseClicked(event, false);
            screen.mouseReleased(event);
            if (!Boolean.TRUE.equals(screen.value("enabled"))) throw new AssertionError("Toggle click failed");
            firstPaint = screen.paintCount();
            return;
        }
        if (screen.paintCount() <= firstPaint) return;
        screen.onClose();
        if (!(client.screen instanceof TitleScreen)) throw new AssertionError("Title screen was not restored");
        try {
            Files.writeString(Path.of("panelib-smoke.pass"), "Form rendered, toggle clicked, repainted, parent restored.\n");
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        client.stop();
    }
}
