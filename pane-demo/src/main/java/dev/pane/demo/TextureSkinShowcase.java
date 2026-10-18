package dev.pane.demo;

import dev.pane.ui.UiCanvas;
import dev.pane.ui.UiContext;
import dev.pane.ui.UiDocument;
import dev.pane.ui.UiPage;
import dev.pane.ui.UiScreen;
import dev.pane.ui.UiShell;
import dev.pane.ui.UiTheme;
import java.awt.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** A small reference page for Minecraft resource textures and real player-skin crops. */
public final class TextureSkinShowcase {
    private static final Identifier GRASS = Identifier.withDefaultNamespace("textures/block/grass_block_top.png");
    private static final Identifier DIAMOND_ORE = Identifier.withDefaultNamespace("textures/block/diamond_ore.png");
    private static final Identifier OAK = Identifier.withDefaultNamespace("textures/block/oak_planks.png");
    private static final String[] MATERIALS = {"Grass", "Diamond ore", "Oak planks"};

    private TextureSkinShowcase() {}

    public static UiScreen createScreen() {
        return new UiScreen(UiDocument.builder("Texture and Skin Fills")
            .brand("Pane")
            .theme(UiTheme.dark())
            .shell(UiShell.NONE)
            .tooltipDelayMillis(500)
            .page(new UiPage("fills", "Texture fills", "", 0xFF55BEE8, TextureSkinShowcase::paint))
            .build(), null, (type, key, value) -> { });
    }

    private static void paint(UiContext ui) {
        UiCanvas g = ui.canvas();
        float w = ui.width(), h = ui.height();
        int bg = 0xFF0D1216, surface = 0xFF151D22, border = 0xFF31424C, text = 0xFFF2F7F9, muted = 0xFF93A7B2;
        g.fill(bg, 0, 0, w, h);
        g.radialRect(0x1F3BA2C9, 0x000D1216, w * .82F, h * .05F, Math.max(w, h) * .60F, 0, 0, w, h, 0);
        float x = Math.max(30, w * .07F), y = 34 + (1 - ui.motion()) * 6;
        g.trackedText("PANE REFERENCE / MINECRAFT PIXELS", x, y, 8, 0xFF71D2F1, Font.BOLD, UiCanvas.SANS, 2F);
        g.text("Textures that stay inside the shape.", x, y + 22, 24, text, Font.BOLD);
        g.text("Minecraft resources and your real skin, rasterized into Pane's retained surface.", x, y + 53, 9, muted, Font.PLAIN);

        float cardY = y + 88;
        float leftW = Math.min(330, w * .42F);
        float gap = 18;
        float rightX = x + leftW + gap;
        float rightW = Math.max(150, w - rightX - x);
        float cardH = Math.max(220, Math.min(h - cardY - 38, 350));

        g.roundRect(surface, x, cardY, leftW, cardH, 18);
        g.strokeRoundRect(border, 1, x, cardY, leftW, cardH, 18);
        g.text("Minecraft texture fill", x + 18, cardY + 17, 12, text, Font.BOLD);
        g.text("Click a material; it is read from Minecraft, not bundled UI art.", x + 18, cardY + 38, 8, muted, Font.PLAIN);
        String material = ui.stringValue("texture-material", "Grass");
        float chipX = x + 18, chipY = cardY + 58;
        for (int i = 0; i < MATERIALS.length; i++) {
            String option = MATERIALS[i];
            float chipW = i == 1 ? 74 : 62;
            boolean selected = material.equals(option);
            g.roundRect(selected ? 0xFF24586B : 0xFF202A30, chipX, chipY, chipW, 25, 8);
            g.centeredText(option, chipX + chipW / 2, chipY + 12.5F, 7, selected ? 0xFFE6FAFF : muted, Font.BOLD);
            ui.selectable("texture-material-" + i, "texture-material", option, chipX, chipY, chipW, 25);
            chipX += chipW + 7;
        }
        Identifier block = switch (material) {
            case "Diamond ore" -> DIAMOND_ORE;
            case "Oak planks" -> OAK;
            default -> GRASS;
        };
        float fillX = x + 18, fillY = cardY + 102, fillW = leftW - 36, fillH = cardH - 124;
        g.withRoundedClip(fillX, fillY, fillW, fillH, 13, () -> {
            if (!g.minecraftTexture(block, fillX, fillY, fillW, fillH)) {
                g.fill(0xFF243139, fillX, fillY, fillW, fillH);
                g.centeredText("Waiting for Minecraft texture", fillX + fillW / 2, fillY + fillH / 2, 8, muted, Font.PLAIN);
            }
        });
        g.strokeRoundRect(0xFF557887, 1, fillX, fillY, fillW, fillH, 13);
        g.roundRect(0xD911171B, fillX + 9, fillY + 9, 108, 21, 6);
        g.text(material, fillX + 17, fillY + 15, 8, text, Font.BOLD);

        g.roundRect(surface, rightX, cardY, rightW, cardH, 18);
        g.strokeRoundRect(border, 1, rightX, cardY, rightW, cardH, 18);
        g.text("Your player skin", rightX + 18, cardY + 17, 12, text, Font.BOLD);
        g.text("The face crop is clipped to a circle before it reaches the GPU surface.", rightX + 18, cardY + 38, 8, muted, Font.PLAIN);
        float avatarRadius = Math.min(76, Math.min(rightW * .26F, cardH * .27F));
        float avatarX = rightX + 22 + avatarRadius, avatarY = cardY + 83 + avatarRadius;
        Identifier skin = liveSkin();
        g.circle(0xFF20313A, avatarX, avatarY, avatarRadius + 7);
        g.withCircleClip(avatarX, avatarY, avatarRadius, () -> {
            if (skin == null || !g.minecraftTextureRegion(skin, 8, 8, 8, 8,
                avatarX - avatarRadius, avatarY - avatarRadius, avatarRadius * 2, avatarRadius * 2)) {
                g.gradientRect(0xFF4B7991, 0xFF1A3544, 135, avatarX - avatarRadius, avatarY - avatarRadius,
                    avatarRadius * 2, avatarRadius * 2, avatarRadius);
                g.centeredText("?", avatarX, avatarY, 26, 0xFFE8F7FC, Font.BOLD);
            } else {
                // The optional hat layer sits on top of the same clipped face, just like Minecraft.
                g.minecraftTextureRegion(skin, 40, 8, 8, 8, avatarX - avatarRadius, avatarY - avatarRadius,
                    avatarRadius * 2, avatarRadius * 2);
            }
        });
        g.strokeRoundRect(0xFF79D8F5, 1.2F, avatarX - avatarRadius, avatarY - avatarRadius,
            avatarRadius * 2, avatarRadius * 2, avatarRadius);
        float infoX = avatarX + avatarRadius + 20;
        g.text(Minecraft.getInstance().player == null ? "Local player" : Minecraft.getInstance().player.getName().getString(),
            infoX, avatarY - 24, 13, text, Font.BOLD);
        g.text(skin == null ? "Skin is still loading" : "Live Minecraft skin", infoX, avatarY + 1, 8, 0xFF7ED7A0, Font.BOLD);
        g.text("Base head: 8, 8, 8, 8", infoX, avatarY + 22, 8, muted, Font.PLAIN);
        g.text("Hat layer: 40, 8, 8, 8", infoX, avatarY + 38, 8, muted, Font.PLAIN);

        float noteY = cardY + cardH - 67;
        g.roundRect(0xFF1B2A31, rightX + 18, noteY, rightW - 36, 47, 11);
        g.text("The same API works in rounded cards, circles, panels, and custom clips.", rightX + 31, noteY + 10, 8, text, Font.BOLD);
        g.text("Nearest-neighbor sampling keeps Minecraft pixels clean at any UI size.", rightX + 31, noteY + 27, 7.5F, muted, Font.PLAIN);
    }

    private static Identifier liveSkin() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : player.getSkin().body().texturePath();
    }
}
