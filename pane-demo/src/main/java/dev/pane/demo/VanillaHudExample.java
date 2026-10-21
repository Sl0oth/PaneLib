package dev.pane.demo;

import dev.pane.ui.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Copyable supplementary HUD recipe: vanilla bevels, textures, live items and native font labels. */
public final class VanillaHudExample {
    private static final Component TITLE = Component.translatable("container.inventory");
    private static final List<ItemStack> ITEMS = List.of(new ItemStack(Items.DIAMOND),
        new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD), new ItemStack(Items.TORCH));
    private static final Identifier HEART = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/full.png");
    private static final UiMinecraftTextStyle LEVEL = UiMinecraftTextStyle.label().color(0xFF256B13)
        .alignment(UiMinecraftTextStyle.Alignment.RIGHT);

    private VanillaHudExample() {}

    /** Caller owns attachment and close. Does not hide any vanilla element or alter the player. */
    public static UiHud create() {
        return new UiHud(228,104,UiTheme.dark(),ui->{
            var g=ui.canvas();g.fill(0xFF080808,0,0,228,104);g.fill(0xFFC6C6C6,2,2,224,100);
            g.fill(0xFFFFFFFF,2,2,224,2);g.fill(0xFFFFFFFF,2,2,2,100);
            g.fill(0xFF555555,2,100,224,2);g.fill(0xFF555555,224,2,2,100);
            ui.nativeSlot("title",12,10,204,12);
            for(int i=0;i<5;i++)g.minecraftTexture(HEART,12+i*18,30,18,18);
            for(int i=0;i<4;i++){
                g.fill(0xFF373737,11+i*52,54,46,30);g.fill(0xFF8B8B8B,13+i*52,56,42,26);
                ui.nativeSlot("item"+i,20+i*52,54,26,26);
            }
            ui.nativeSlot("level",110,34,104,9);
            ui.nativeSlot("caption",12,90,204,9);
        },overlay->{
            overlay.text("title",TITLE,UiMinecraftTextStyle.label());
            var player=Minecraft.getInstance().player;
            overlay.text("level","Level "+(player==null?0:player.experienceLevel),LEVEL);
            overlay.text("caption","PaneLib / native Minecraft text",UiMinecraftTextStyle.label());
            for(int i=0;i<ITEMS.size();i++)overlay.item("item"+i,ITEMS.get(i));
        });
    }
}
