package dev.pane.ui;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiHudReplacementsTest {
    private static final Identifier HOTBAR = Identifier.fromNamespaceAndPath("minecraft", "hotbar");
    private static final Identifier HEALTH = Identifier.fromNamespaceAndPath("minecraft", "health_bar");
    // Fabric composes functions, applying them to the live vanilla callback at extraction time.
    private static final class Registry {
        final Map<Identifier, Function<HudElement,HudElement>> wrappers = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        int installs;
        Registry(){wrappers.put(HOTBAR,Function.identity());wrappers.put(HEALTH,Function.identity());}
        void install(Identifier id, Function<HudElement,HudElement> wrapper){
            if(!wrappers.containsKey(id))throw new IllegalArgumentException("Missing element");
            wrappers.put(id,wrappers.get(id).andThen(wrapper));installs++;
        }
        void draw(Identifier id){wrappers.get(id).apply((g,d)->calls.add("vanilla")).extractRenderState(null,null);}
    }
    @Test void overlappingOwnersAndRepeatedTogglesDoNotStackWrappersOrRestoreTooEarly(){
        var registry=new Registry();var claims=new UiHudReplacements(registry::install);
        Object a=new Object(),b=new Object();var first=new AtomicBoolean(true);var second=new AtomicBoolean(true);
        claims.replace(a,Set.of(HOTBAR),first::get);claims.replace(b,Set.of(HOTBAR),second::get);
        registry.draw(HOTBAR);assertTrue(registry.calls.isEmpty());
        claims.remove(a);registry.draw(HOTBAR);assertTrue(registry.calls.isEmpty());
        for(int i=0;i<100;i++){second.set(false);registry.draw(HOTBAR);second.set(true);registry.draw(HOTBAR);}
        assertEquals(100,registry.calls.size());assertEquals(1,registry.installs);
        claims.remove(b);registry.draw(HOTBAR);assertEquals(101,registry.calls.size());
    }
    @Test void preservesEarlierAndLaterModWrappersWithoutReinstallingRegistry(){
        var registry=new Registry();var claims=new UiHudReplacements(registry::install);Object owner=new Object();
        registry.install(HOTBAR, original->(g,d)->{registry.calls.add("before");original.extractRenderState(g,d);});
        claims.replace(owner,Set.of(HOTBAR),()->true);
        registry.install(HOTBAR, original->(g,d)->{registry.calls.add("after");original.extractRenderState(g,d);});
        registry.draw(HOTBAR);assertEquals(List.of("after"),registry.calls);
        registry.calls.clear();claims.remove(owner);registry.draw(HOTBAR);
        assertEquals(List.of("after","before","vanilla"),registry.calls);assertEquals(3,registry.installs);
    }
    @Test void updatingDeclarationsReleasesOldClaimsAndFailedUpdatePreservesThem(){
        var registry=new Registry();var claims=new UiHudReplacements(registry::install);Object owner=new Object();
        claims.replace(owner,Set.of(HOTBAR),()->true);
        claims.replace(owner,Set.of(HEALTH),()->true);
        registry.draw(HOTBAR);registry.draw(HEALTH);assertEquals(List.of("vanilla"),registry.calls);
        assertThrows(IllegalArgumentException.class,()->claims.replace(owner,new LinkedHashSet<>(List.of(HOTBAR,
            Identifier.fromNamespaceAndPath("missing","element"))),()->true));
        registry.calls.clear();registry.draw(HOTBAR);registry.draw(HEALTH);assertEquals(List.of("vanilla"),registry.calls);
        claims.replace(owner,Set.of(),()->true);registry.draw(HEALTH);assertEquals(2,registry.calls.size());
    }
    @Test void clearingOwnersLeavesOnlyPassThroughCallbacks(){
        var registry=new Registry();var claims=new UiHudReplacements(registry::install);var evaluations=new AtomicInteger();
        claims.replace(new Object(),Set.of(HOTBAR,HEALTH),()->{evaluations.incrementAndGet();return true;});
        claims.clear();registry.draw(HOTBAR);registry.draw(HEALTH);
        assertEquals(0,evaluations.get());assertEquals(2,registry.calls.size());
    }
}
