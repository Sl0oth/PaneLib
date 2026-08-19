package dev.pane.ui;

import java.util.Map;
import java.util.Optional;
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

    NativeOverlayContext(GuiGraphicsExtractor graphics, String pageId, Map<String, NativeBounds> bounds,
                         double guiScale, int mouseX, int mouseY) {
        this.graphics = graphics;
        this.pageId = pageId;
        this.bounds = bounds;
        this.guiScale = guiScale <= 0 ? 1 : guiScale;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    /** Id of the page currently painted, so an overlay can skip work for other pages. */
    public String pageId() { return pageId; }

    public GuiGraphicsExtractor graphics() { return graphics; }

    /** Slot rectangle in GUI space, if the active page reserved one under this id. */
    public Optional<NativeBounds> bounds(String id) { return Optional.ofNullable(bounds.get(id)); }

    /** Ids of every slot the active page reserved. */
    public Iterable<String> slotIds() { return bounds.keySet(); }

    /** Draws a stack centred in the named slot, scaled to fill it. */
    public void item(String slotId, ItemStack stack) {
        NativeBounds box = bounds.get(slotId);
        if (box == null || stack == null || stack.isEmpty()) return;
        int x = box.logicalX(guiScale);
        int y = box.logicalY(guiScale);
        int w = box.logicalWidth(guiScale);
        int h = box.logicalHeight(guiScale);
        if (w <= 0 || h <= 0) return;
        // A vanilla item is 16x16; 18 leaves the slot padding vanilla containers use.
        float itemScale = Math.max(.35F, Math.min(w, h) / 18.0F);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + w / 2.0F, y + h / 2.0F);
        graphics.pose().scale(itemScale, itemScale);
        graphics.item(stack, -8, -8);
        graphics.pose().popMatrix();
    }

    /** Draws a living entity in the named slot, sized to its bounding box and tracking the cursor. */
    public void player(String slotId, LivingEntity entity) {
        NativeBounds box = bounds.get(slotId);
        if (box == null || entity == null) return;
        int x1 = box.logicalX(guiScale);
        int y1 = box.logicalY(guiScale);
        int width = box.logicalWidth(guiScale);
        int height = box.logicalHeight(guiScale);
        if (width <= 0 || height <= 0) return;
        // Fit the entity's real bounding box rather than guessing from the slot's short edge, so
        // tall slots do not leave a small model floating and wide entities do not overflow.
        float boxWidth = Math.max(.1F, entity.getBbWidth());
        float boxHeight = Math.max(.1F, entity.getBbHeight());
        int size = (int) Math.max(1, Math.min(width / (boxWidth * 2.2F), height / (boxHeight * 1.3F)));
        InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics, x1, y1, x1 + width, y1 + height, size, 0.0F, mouseX, mouseY, entity
        );
    }

    /** The local player, or null outside a world. */
    public LivingEntity localPlayer() { return Minecraft.getInstance().player; }
}
