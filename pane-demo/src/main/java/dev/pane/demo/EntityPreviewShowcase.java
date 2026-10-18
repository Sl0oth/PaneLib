package dev.pane.demo;

import dev.pane.ui.NativeOverlayContext;
import dev.pane.ui.NativeOverlayRenderer;
import dev.pane.ui.UiBackdrop;
import dev.pane.ui.UiContext;
import dev.pane.ui.UiDocument;
import dev.pane.ui.UiPage;
import dev.pane.ui.UiRect;
import dev.pane.ui.UiScreen;
import dev.pane.ui.UiScreenOptions;
import dev.pane.ui.UiTheme;
import java.awt.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** Real-client reference for generic living-entity card previews. */
public final class EntityPreviewShowcase {
    private static ClientLevel previewLevel;
    private static LivingEntity ironGolem;
    private static LivingEntity villager;
    private static int nextPreviewId = -2_000_000;

    private EntityPreviewShowcase() {}

    public static UiScreen createScreen() {
        UiDocument document = UiDocument.builder("Entity Card Previews")
            .brand("Pane")
            .theme(UiTheme.dark().withAccent(0xFF6BC7E8))
            .page(new UiPage("entities", "Entity previews", "", 0xFF6BC7E8, EntityPreviewShowcase::paint))
            .build();
        UiScreenOptions options = UiScreenOptions.builder().backdrop(UiBackdrop.MINECRAFT).build();
        return new UiScreen(document, EntityPreviewShowcase::renderEntities, event -> { }, options);
    }

    private static void paint(UiContext ui) {
        var g = ui.canvas();
        float w = ui.width(), h = ui.height();
        float margin = Math.max(28, w * .075F);
        float gap = Math.max(16, w * .025F);
        float cardW = Math.max(180, (w - margin * 2 - gap) / 2F);
        float cardH = Math.max(250, Math.min(h - 146, 400));
        float y = Math.max(82, (h - cardH) / 2F + 30);
        g.fill(0xB80A1118, 0, 0, w, h);
        g.text("PANE REFERENCE / NATIVE ENTITIES", margin, y - 54, 8, 0xFF72D9F6, Font.BOLD, ui.theme().fontFamily());
        g.text("Living entity card previews", margin, y - 48, 24, 0xFFF2F8FB, Font.BOLD, ui.theme().fontFamily());
        g.text("Vanilla GUI lighting, live pose, and cursor-following orientation.", margin, y - 17, 9,
            0xFF9BAFBA, Font.PLAIN, ui.theme().fontFamily());
        card(ui, "iron-golem", "Iron golem", "A tall entity fits its own real bounds.", margin, y, cardW, cardH, 0xFF85BCD0);
        card(ui, "villager", "Villager", "The exact same public call works for NPCs.", margin + cardW + gap, y,
            cardW, cardH, 0xFFE2B56C);
        // Deliberately paint and register from a translated context. The bright right-edge guide
        // is the exact declared rectangle, so a native golem must stop on this line at every GUI
        // scale; no padded shadow or consumer-side offset is hidden in this reference.
        ui.withTranslation(margin, y, translated -> {
            var layer = translated.canvas();
            UiRect sheet = new UiRect(cardW * .14F, cardH * .18F,
                Math.min(170, Math.max(126, cardW * .27F)), Math.min(230, Math.max(174, cardH * .64F)));
            layer.shadow(0x90000000, 8, 0, 3, sheet.x(), sheet.y(), sheet.width(), sheet.height(), 12);
            layer.roundRect(0xFF20333F, sheet.x(), sheet.y(), sheet.width(), sheet.height(), 12);
            layer.strokeRoundRect(0xFF7BC9E5, .8F, sheet.x(), sheet.y(), sheet.width(), sheet.height(), 12);
            layer.line(0xFF76E3FF, 1.5F, sheet.right(), sheet.y() + 10, sheet.right(), sheet.bottom() - 10);
            layer.text("Pane detail sheet", sheet.x() + 13, sheet.y() + 14, 8, 0xFFF2F8FB, Font.BOLD, ui.theme().fontFamily());
            layer.wrappedText("The cyan line is the exact registered right edge. It must cut the golem here.",
                sheet.x() + 13, sheet.y() + 36, sheet.width() - 26, 9, 7, 0xFFBAD0DA, Font.PLAIN, ui.theme().fontFamily());
            translated.nativeOverlayLayer("entity-detail-sheet", sheet.x(), sheet.y(), sheet.width(), sheet.height());
        });
    }

    private static void card(UiContext ui, String slot, String title, String note, float x, float y, float w,
                             float h, int accent) {
        var g = ui.canvas();
        g.shadow(0x78000000, 10, 0, 4, x, y, w, h, 16);
        g.roundRect(0xF0142028, x, y, w, h, 16);
        g.strokeRoundRect(accent, 1, x, y, w, h, 16);
        float previewX = x + 18, previewY = y + 51, previewW = w - 36, previewH = h - 118;
        g.roundRect(0xFF182731, previewX, previewY, previewW, previewH, 12);
        g.strokeRoundRect(0xFF3E5C6D, .8F, previewX, previewY, previewW, previewH, 12);
        // This preview has no retained hover artwork or click action. Registering it as a
        // passive tooltip target keeps the live entity responsive to the cursor without
        // scheduling a retained-surface repaint for the whole preview rectangle.
        ui.nativeSlot(slot, previewX + 8, previewY + 8, previewW - 16, previewH - 16);
        ui.tooltip(slot, "This native slot is rectangular; leave card padding around it.", previewX, previewY,
            previewW, previewH);
        g.text(title, x + 20, y + 19, 14, 0xFFF2F8FB, Font.BOLD, ui.theme().fontFamily());
        g.text(note, x + 20, y + h - 39, 8, 0xFF9BAFBA, Font.PLAIN, ui.theme().fontFamily());
        g.text("LIVE MINECRAFT ENTITY", x + 20, y + h - 20, 6.5F, accent, Font.BOLD, ui.theme().fontFamily());
    }

    private static void renderEntities(NativeOverlayContext nativeUi) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level != previewLevel) {
            previewLevel = level;
            ironGolem = preview(level, "iron_golem");
            villager = preview(level, "villager");
        }
        nativeUi.entity("iron-golem", ironGolem);
        nativeUi.entity("villager", villager);
    }

    private static LivingEntity preview(ClientLevel level, String id) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(id));
        Entity entity = type == null ? null : type.create(level, EntitySpawnReason.LOAD);
        if (entity != null) entity.setId(nextPreviewId++);
        return entity instanceof LivingEntity living ? living : null;
    }
}
