package dev.pane.demo;

import com.mojang.blaze3d.platform.InputConstants;
import dev.pane.ui.*;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import org.lwjgl.glfw.GLFW;

/** Opt-in lifecycle fixture. Runs in the copied Wayfarer save; never selects a personal world. */
final class HudRestorationAuditTest {
    private final WayfarerHudShowcase owner;
    private final Map<Identifier,Long> originals = new HashMap<>();
    private long afterCalls, savedAfter, savedHotbar, savedHealth, savedFrames, start, next;
    private int stage, checks;
    private boolean failed, settlingToggle;
    private UiHud first, second, styled;
    HudRestorationAuditTest(WayfarerHudShowcase owner){this.owner=owner;}
    void installBefore(){
        for(var id:List.of(VanillaHudElements.HOTBAR,VanillaHudElements.ARMOR_BAR,VanillaHudElements.HEALTH_BAR,
            VanillaHudElements.FOOD_BAR,VanillaHudElements.AIR_BAR,VanillaHudElements.INFO_BAR,
            VanillaHudElements.EXPERIENCE_LEVEL,VanillaHudElements.HELD_ITEM_TOOLTIP,VanillaHudElements.CROSSHAIR)) {
            HudElementRegistry.replaceElement(id, original -> (g,d) -> {
                originals.merge(id,1L,Long::sum);original.extractRenderState(g,d);
            });
        }
    }
    private long hotbar(){return originals.getOrDefault(VanillaHudElements.HOTBAR,0L);}
    private long health(){return originals.getOrDefault(VanillaHudElements.HEALTH_BAR,0L);}
    private void baseline(){savedHotbar=hotbar();savedHealth=health();savedAfter=afterCalls;savedFrames=owner.belt().snapshot().frames();}
    void tick(Minecraft c){
        try {
            if(start==0)start=System.nanoTime();
            if(System.nanoTime()-start>150_000_000_000L)throw new IllegalStateException("restoration test timeout at "+stage);
            if(System.nanoTime()<next)return;
            var belt=owner.belt();
            switch(stage){
                case 0->{
                    check(belt.nativeItemCount()==9&&hotbar()==0&&health()==0,"enabled replacement suppresses original HUD callbacks");
                    HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR,original -> (g,d)->{afterCalls++;original.extractRenderState(g,d);});
                    capture(c,"replacement-enabled");baseline();toggleKey();advance(350);
                }
                case 1->{
                    check(!belt.enabled()&&owner.huds().stream().noneMatch(h->h.snapshot().hasSurface()),"J toggle disables all custom HUD surfaces immediately");
                    check(hotbar()>savedHotbar&&health()>savedHealth,"disabled replacement calls preserved vanilla renderers");
                    check(afterCalls>savedAfter,"later mod wrapper survives deactivation");
                    capture(c,"replacement-disabled-vanilla");baseline();toggleKey();advance(450);
                }
                case 2->{
                    // KeyMapping.click queues input for the next client tick. Vanilla is expected
                    // to draw until that input is consumed; start the no-vanilla baseline afterward.
                    if(!settlingToggle){
                        check(belt.enabled(),"J toggle re-enables the custom HUD");
                        settlingToggle=true;baseline();next=System.nanoTime()+300_000_000L;return;
                    }
                    check(belt.nativeItemCount()==9&&hotbar()==savedHotbar,"re-enabled custom HUD suppresses vanilla again");
                    check(afterCalls>savedAfter,"later mod wrapper remains active outside our suppression wrapper");
                    belt.setMotionScale(1);baseline();belt.setVisible(false);advance(60);
                }
                case 3->{
                    check(belt.snapshot().hasSurface()&&hotbar()==savedHotbar,"hide keeps original suppressed through exit fade");advance(400);
                }
                case 4->{
                    check(!belt.snapshot().hasSurface()&&hotbar()>savedHotbar,"hide restores vanilla after fade finishes");
                    check(health()==savedHealth,"hiding belt does not release another widget's health claim");
                    capture(c,"replacement-belt-hidden");belt.setVisible(true);advance(450);
                }
                case 5->{
                    check(belt.nativeItemCount()==9,"show recreates replacement content");baseline();
                    c.gui.setScreen(new PauseScreen(false));advance(300);
                }
                case 6->{
                    check(hotbar()>savedHotbar&&health()>savedHealth&&belt.nativeItemCount()==0,"ordinary screen restores vanilla while custom HUD is suppressed");
                    owner.huds().forEach(h->h.setShowOnScreens(true));baseline();advance(300);
                }
                case 7->{
                    check(hotbar()==savedHotbar&&health()==savedHealth&&belt.nativeItemCount()==9,"showOnScreens keeps matching replacement claims active");
                    c.gui.setScreen(null);owner.huds().forEach(h->h.setShowOnScreens(false));c.gui.hud.toggle();baseline();advance(300);
                }
                case 8->{
                    check(hotbar()==savedHotbar&&health()==savedHealth&&belt.snapshot().frames()==savedFrames,"F1 preserves vanilla visibility gates and hides custom HUD");
                    c.gui.hud.toggle();advance(300);
                }
                case 9->{
                    check(belt.nativeItemCount()==9,"F1 release resumes replacement");
                    first=claim("first");second=claim("second");owner.setEnabled(false);baseline();advance(300);
                }
                case 10->{
                    check(hotbar()==savedHotbar&&health()>savedHealth,"overlapping owners suppress only their declared vanilla element");first.close();baseline();advance(250);
                }
                case 11->{
                    check(hotbar()==savedHotbar,"closing one owner does not restore through another active owner");second.setEnabled(false);baseline();advance(250);
                }
                case 12->{
                    check(hotbar()>savedHotbar,"disabling final active owner restores original");second.setEnabled(true);baseline();advance(250);
                }
                case 13->{
                    check(hotbar()==savedHotbar,"overlap owner can reactivate without reinstalling callbacks");second.close();baseline();advance(250);
                }
                case 14->{
                    check(hotbar()>savedHotbar,"closing final owner restores original");
                    styled=vanillaStyled();advance(350);
                }
                case 15->{
                    check(styled.nativeTextCount()==3&&styled.nativeItemCount()==4,"vanilla-styled supplementary HUD renders native labels and items");
                    capture(c,"vanilla-styled-hud");styled.close();baseline();c.disconnect(new TitleScreen(),false);advance(600);
                }
                case 16->{
                    if(c.level!=null||!(c.gui.screen() instanceof TitleScreen)||c.gui.overlay()!=null)return;
                    check(owner.huds().stream().noneMatch(h->h.snapshot().hasSurface()),"disconnect releases custom surfaces");
                    c.createWorldOpenFlows().openWorld("wayfarerHud_preview",()->{});advance(250);
                }
                case 17->{if(c.player==null||c.level==null||c.gui.overlay()!=null)return;c.gui.setScreen(null);baseline();advance(500);}
                case 18->{
                    check(!belt.enabled()&&hotbar()>savedHotbar&&health()>savedHealth,"disabled preference survives rejoin and vanilla renders");owner.setEnabled(true);baseline();advance(500);
                }
                case 19->{
                    check(belt.nativeItemCount()==9&&hotbar()==savedHotbar,"replacement reactivates after rejoin");
                    c.disconnect(new TitleScreen(),false);advance(600);
                }
                case 20->{
                    if(c.level!=null||!(c.gui.screen() instanceof TitleScreen)||c.gui.overlay()!=null)return;
                    check(belt.enabled()&&!belt.snapshot().hasSurface(),"enabled preference survives unload without retaining world surfaces");
                    c.createWorldOpenFlows().openWorld("wayfarerHud_preview",()->{});advance(250);
                }
                case 21->{if(c.player==null||c.level==null||c.gui.overlay()!=null)return;c.gui.setScreen(null);baseline();advance(500);}
                case 22->{
                    check(belt.nativeItemCount()==9&&hotbar()==savedHotbar,"enabled replacement resumes automatically on rejoin");
                    var old=belt.replacedVanillaElements();boolean rejected=false;
                    try{belt.replaceVanilla(VanillaHudElements.HOTBAR,null);}catch(NullPointerException expected){rejected=true;}
                    check(rejected&&old.equals(belt.replacedVanillaElements()),"invalid declaration preserves existing claims");
                    belt.replaceVanilla();baseline();advance(250);
                }
                case 23->{
                    check(hotbar()>savedHotbar&&belt.nativeItemCount()==9,"empty declaration releases originals without disabling custom drawing");
                    owner.close();baseline();advance(300);
                }
                case 24->{
                    check(hotbar()>savedHotbar&&health()>savedHealth&&afterCalls>savedAfter,"closing showcase restores vanilla and both mod wrapper chains");
                    check(owner.huds().stream().allMatch(h->!h.enabled()&&!h.snapshot().hasSurface()&&h.nativeItemCount()==0),"close clears all owned surfaces and native counts");
                    boolean rejected=false;try{belt.replaceVanilla(VanillaHudElements.HOTBAR);}catch(IllegalStateException expected){rejected=true;}
                    belt.setEnabled(true);check(rejected&&!belt.enabled(),"closed HUD cannot re-register or reactivate claims");
                    first=new UiHud(40,24,UiTheme.dark(),ui->ui.canvas().fill(0xFFDDCC77,0,0,40,24))
                        .replaceVanilla(VanillaHudElements.HOTBAR).attach(Identifier.fromNamespaceAndPath("pane_demo","wayfarer_hud_belt"));
                    baseline();advance(300);
                }
                case 25->{check(hotbar()==savedHotbar,"new HUD can reuse a closed HUD's ID and claim");first.close();baseline();advance(250);}
                case 26->{
                    check(hotbar()>savedHotbar,"replacement recreated then closed still restores original");capture(c,"replacement-closed-vanilla");
                    System.out.println("[HudRestorationTest] original callback totals="+originals);
                    System.out.println("[HudRestorationTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);stage=99;c.stop();
                }
            }
        }catch(Throwable e){failed=true;e.printStackTrace();System.out.println("[HudRestorationTest] complete FAIL "+e);stage=99;c.stop();}
    }
    private UiHud claim(String id){
        var hud=new UiHud(40,24,UiTheme.dark(),ui->ui.canvas().fill(0xFFDDCC77,0,0,40,24))
            .attach(Identifier.fromNamespaceAndPath("pane_demo","restoration_"+id));
        hud.setMotionScale(0);return hud.replaceVanilla(VanillaHudElements.HOTBAR);
    }
    private UiHud vanillaStyled(){
        var hud=VanillaHudExample.create()
            .attach(Identifier.fromNamespaceAndPath("pane_demo","vanilla_style_probe"));
        hud.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.TOP_RIGHT,24,24,1));hud.setMotionScale(0);return hud;
    }
    private void toggleKey(){KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));}
    private void advance(int ms){stage++;next=System.nanoTime()+ms*1_000_000L;}
    private void check(boolean ok,String name){checks++;failed|=!ok;System.out.println("[HudRestorationTest] "+name+": "+(ok?"PASS":"FAIL"));}
    private void capture(Minecraft c,String name){Screenshot.grab(c.gameDirectory,name+".png",c.gameRenderer.mainRenderTarget(),1,m->{});}
}
