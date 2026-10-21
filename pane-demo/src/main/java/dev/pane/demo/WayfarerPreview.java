package dev.pane.demo;

import dev.pane.ui.UiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/** Opens the showcase directly from startup without loading a world. */
final class WayfarerPreview {
    private static boolean opened;
    static UiScreen openMenu(Minecraft c){
        var screen=WayfarerShowcase.createScreen(c.gui.screen());
        c.gui.setScreen(screen);
        return screen;
    }
    static boolean tick(Minecraft c){
        if(!opened&&c.gui.screen() instanceof TitleScreen&&c.gui.overlay()==null){
            openMenu(c);opened=true;
        }
        return true;
    }
}
