package dev.pane.ui;

import java.util.*;
import java.util.function.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.resources.Identifier;

/** One delegating wrapper per element, with removable owner claims. Client-thread only. */
final class UiHudReplacements {
    private final BiConsumer<Identifier, Function<HudElement, HudElement>> install;
    private final Map<Identifier, Map<Object, BooleanSupplier>> claims = new LinkedHashMap<>();

    UiHudReplacements(BiConsumer<Identifier, Function<HudElement, HudElement>> install) {
        this.install = install;
    }

    void replace(Object owner, Set<Identifier> elements, BooleanSupplier active) {
        // Install all requested wrappers before changing ownership. A bad ID cannot leave half
        // an owner's new claim active. Successfully installed empty wrappers just delegate.
        for (Identifier id : elements) {
            if (claims.containsKey(id)) continue;
            Map<Object, BooleanSupplier> owners = new IdentityHashMap<>();
            install.accept(id, original -> (graphics, delta) -> {
                for (BooleanSupplier claim : owners.values()) if (claim.getAsBoolean()) return;
                original.extractRenderState(graphics, delta);
            });
            claims.put(id, owners);
        }
        remove(owner);
        for (Identifier id : elements) claims.get(id).put(owner, active);
    }

    void remove(Object owner) { for (var owners : claims.values()) owners.remove(owner); }
    void clear() { for (var owners : claims.values()) owners.clear(); }
}
