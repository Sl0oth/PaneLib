package dev.pane.ui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/** Library lifecycle hook; installs no screen, key binding, timer or demo content. */
public final class PaneClientResources implements ClientModInitializer {
    @Override public void onInitializeClient() {
        var loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        var id = Identifier.fromNamespaceAndPath("panelib", "decoded_textures");
        loader.registerReloadListener(id, new SimpleReloadListener<Void>() {
            @Override protected Void prepare(PreparableReloadListener.SharedState state) { return null; }
            @Override protected void apply(Void unused, PreparableReloadListener.SharedState state) {
                UiCanvas.clearMinecraftTextureCache();
            }
        });
        loader.addListenerOrdering(ResourceReloaderKeys.Client.TEXTURES, id);
    }
}
