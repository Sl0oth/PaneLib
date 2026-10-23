package dev.pane.ui;

import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft's own renderer, drawn into slots the retained surface reserved with
 * {@link UiContext#nativeSlot}. This runs on the render thread once per visible frame, after
 * the cached surface has been blitted, so keep the work here to real draw calls.
 */
public final class NativeOverlayContext {
    private final GuiGraphicsExtractor graphics;
    private final String pageId;
    private final Map<String, NativeBounds> bounds;
    private final double guiScale;
    private final int mouseX;
    private final int mouseY;
    private final boolean pointerBlocked;
    private int renderedItemCount;
    private int renderedEntityCount;
    private int renderedTextCount;
    private final NativeTextRenderer textRenderer;

    NativeOverlayContext(GuiGraphicsExtractor graphics, String pageId, Map<String, NativeBounds> bounds,
                         double guiScale, int mouseX, int mouseY) {
        this(graphics, pageId, bounds, guiScale, mouseX, mouseY, false);
    }

    NativeOverlayContext(GuiGraphicsExtractor graphics, String pageId, Map<String, NativeBounds> bounds,
                         double guiScale, int mouseX, int mouseY, boolean pointerBlocked) {
        this(graphics, pageId, bounds, guiScale, mouseX, mouseY, pointerBlocked, new NativeTextRenderer());
    }

    NativeOverlayContext(GuiGraphicsExtractor graphics, String pageId, Map<String, NativeBounds> bounds,
                         double guiScale, int mouseX, int mouseY, boolean pointerBlocked, NativeTextRenderer textRenderer) {
        this.textRenderer = textRenderer;
        this.pointerBlocked = pointerBlocked;
        this.graphics = graphics;
        this.pageId = pageId;
        this.bounds = bounds;
        this.guiScale = guiScale <= 0 ? 1 : guiScale;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    /** Id of the page currently painted, so an overlay can skip work for other pages. */
    public String pageId() { return pageId; }

    /** True when a menu, dialog or custom focus scope blocks background pointer interaction.
     * Consumers should suppress their hover tooltips; entity previews still receive the real pointer. */
    public boolean pointerBlocked() { return pointerBlocked; }

    /** Minecraft's graphics for this frame, for drawing anything the helpers here do not cover. */
    public GuiGraphicsExtractor graphics() { return graphics; }

    /** Slot rectangle in physical backing pixels. Helpers perform the GUI-scale conversion internally. */
    public Optional<NativeBounds> bounds(String id) { return Optional.ofNullable(bounds.get(id)); }

    /** Ids of every slot the active page reserved. */
    public Iterable<String> slotIds() { return bounds.keySet(); }

    /** Draw a single line of white, shadowed vanilla text, ellipsized to fit its native slot. */
    public void text(String slotId, String text) { text(slotId, text, UiMinecraftTextStyle.hud()); }
    /** Draws Minecraft text in slot {@code slotId} with the HUD text style. */
    public void text(String slotId, net.minecraft.network.chat.Component text) { text(slotId, text, UiMinecraftTextStyle.hud()); }

    /**
     * Draw a single line using Minecraft's font renderer. Slot height is the line height; width
     * constrains clipping/ellipsis. Translation, component colors and resource fonts are preserved.
     * This inherits HUD fading and the normal native-slot visibility, rectangular clipping and
     * popup ordering rules. No bitmap is copied into the retained CPU surface.
     */
    public void text(String slotId, net.minecraft.network.chat.Component text, UiMinecraftTextStyle style) {
        drawText(slotId, text, style);
    }

    /** Plain-string equivalent of the component overload. Repeated equal strings reuse layout. */
    public void text(String slotId, String text, UiMinecraftTextStyle style) { drawText(slotId, text, style); }

    private void drawText(String slotId, Object text, UiMinecraftTextStyle style) {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(style, "style");
        if (textRenderer.render(graphics, slotId, bounds.get(slotId), guiScale, text, style)) renderedTextCount++;
    }

    /** Draws a stack centred in the named slot, scaled to fill it. */
    public void item(String slotId, ItemStack stack) {
        NativeBounds box = bounds.get(slotId);
        if (box == null || stack == null || stack.isEmpty()) return;
        int x = box.logicalX(guiScale);
        int y = box.logicalY(guiScale);
        int w = box.logicalWidth(guiScale);
        int h = box.logicalHeight(guiScale);
        if (w <= 0 || h <= 0) return;
        renderedItemCount++;
        // A vanilla item is 16x16; 18 leaves the slot padding vanilla containers use.
        float itemScale = Math.max(.35F, Math.min(w, h) / 18.0F);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + w / 2.0F, y + h / 2.0F);
        graphics.pose().scale(itemScale, itemScale);
        graphics.item(stack, -8, -8);
        graphics.pose().popMatrix();
    }

    /**
     * Draws any living Minecraft entity in the named slot, sized to its real bounding box and
     * oriented with the cursor using Minecraft's inventory-preview camera.
     *
     * <p>The slot is rectangular in the native renderer. Pane hides it when a scroll viewport
     * would only partially contain it, but native entities cannot inherit rounded or circular
     * {@link UiCanvas} clips. Reserve padding in the slot when it sits inside a rounded card.</p>
     *
     * <p>This reads the entity's current pose but does not add it to a level or mutate it. Supply
     * a live client-world entity, or a client-only preview entity created for the active level.</p>
     */
    public void entity(String slotId, LivingEntity entity) {
        NativeBounds box = bounds.get(slotId);
        if (box == null || entity == null) return;
        int x1 = box.logicalX(guiScale);
        int y1 = box.logicalY(guiScale);
        int width = box.logicalWidth(guiScale);
        int height = box.logicalHeight(guiScale);
        if (width <= 0 || height <= 0) return;
        renderedEntityCount++;
        // Fit the entity's real bounding box rather than guessing from the slot's short edge, so
        // tall slots do not leave a small model floating and wide entities do not overflow.
        float boxWidth = Math.max(.1F, entity.getBbWidth());
        float boxHeight = Math.max(.1F, entity.getBbHeight());
        int size = (int) Math.max(1, Math.min(width / (boxWidth * 2.2F), height / (boxHeight * 1.3F)));
        InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics, x1, y1, x1 + width, y1 + height, size, 0.0F, mouseX, mouseY, entity
        );
    }

    /**
     * Compatibility alias for {@link #entity(String, LivingEntity)}. Use {@code entity} for
     * villagers, mobs, and player previews in new consumer code.
     */
    public void player(String slotId, LivingEntity entity) {
        entity(slotId, entity);
    }

    /** Draws a world-free standing avatar using the default presentation. See the options overload. */
    public void playerPreview(String slotId, PlayerSkin skin) {
        playerPreview(slotId, skin, UiPlayerPreview.standing());
    }

    /**
     * Draws a world-free standing player from a resolved skin, with no player entity or level.
     * Null skin selects Minecraft's bundled default; this method does not fetch profiles or skins.
     * Custom texture assets must be registered/available to Minecraft (missing assets use its
     * missing texture). Both wide and slim models are supported. The account skin is never changed.
     *
     * <p>Missing, hidden or zero-sized slots submit nothing. The existing rectangular native-slot
     * clipping rules in {@link #entity} apply. This counts as one native entity submission per draw.
     * Settings must be non-null, even if the slot is absent.</p>
     */
    public void playerPreview(String slotId, PlayerSkin skin, UiPlayerPreview options) {
        Objects.requireNonNull(options, "options");
        var frame = PlayerPreviewRenderer.frame(bounds.get(slotId), guiScale, options, mouseX, mouseY, pointerBlocked);
        if (frame == null) return;
        PlayerPreviewRenderer.render(graphics, frame, skin, options);
        renderedEntityCount++;
    }

    /** The local player, or null outside a world. */
    public LivingEntity localPlayer() { return Minecraft.getInstance().player; }

    int renderedItemCount() { return renderedItemCount; }
    int renderedEntityCount() { return renderedEntityCount; }
    int renderedTextCount() { return renderedTextCount; }
}
