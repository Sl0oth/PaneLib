package dev.pane.demo;

import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

/** Tests the public launcher/gallery and live HUD owner in a newly generated disposable world. */
final class ExamplesAuditTest {
    private UiScreen gallery;
    private TitleScreen parent;
    private int stage, checks, paints;
    private long deadline, settleStarted;
    private boolean done;
    private String inventory;
    private WayfarerHudShowcase previous;

    void tick(Minecraft c) {
        if(done||System.nanoTime()<deadline||c.gui.overlay()!=null)return;
        try {
            if(stage==0){
                if(!(c.gui.screen() instanceof TitleScreen))return;
                parent=(TitleScreen)c.gui.screen();c.getWindow().setWindowed(1280,850);
                gallery=ExampleGallery.createScreen(parent);c.gui.setScreen(gallery);advance(1300);return;
            }
            switch(stage){
                case 1 -> {
                    // A cold first frame can outlast the opening transition, which then paints its last frame next; idle starts once that is done.
                    if(settleStarted==0)settleStarted=System.nanoTime();
                    if(!gallery.settled()&&System.nanoTime()-settleStarted<5_000_000_000L)return;
                    check(gallery.settled(),"gallery settles after opening"+(gallery.settled()?"":" ("+pending(gallery)+")"));capture(c,"example-shelf");check(c.level==null,"gallery needs no world");check(gallery.buttonState("wayfarer").orElseThrow().label().equals("Open Wayfarer menu"),"gallery exposes named keyboard targets");paints=gallery.paintCount();advance(900); }
                case 2 -> {check(gallery.paintCount()==paints,"idle gallery does not repaint");click(gallery,"wayfarer");advance(1200);}
                case 3 -> {var s=current(c);check(s.regionBounds("deploy").isPresent(),"Wayfarer opens from gallery");check(c.level==null,"player menu stays world-free");capture(c,"wayfarer-current");escape(s);advance(600);}
                case 4 -> {check(c.gui.screen()==gallery,"Wayfarer restores gallery parent");click(gallery,"paneos");advance(2600);}
                case 5 -> {var s=current(c);check(s.regionBounds("lock:avatar").isPresent(),"PaneOS boots to its lock screen from gallery");capture(c,"paneos-current");escape(s);advance(500);}
                case 6 -> {check(c.gui.screen()==gallery,"PaneOS restores gallery parent");click(gallery,"mail");advance(900);}
                case 7 -> {var s=current(c);check(s.regionBounds("compose").isPresent(),"Pane Mail opens from gallery");capture(c,"mail-current");escape(s);advance(500);}
                case 8 -> {check(c.gui.screen()==gallery,"Pane Mail restores gallery parent");c.getWindow().setWindowed(860,600);advance(800);}
                case 9 -> {for(String id:new String[]{"paneos","wayfarer","mail","wayfarer-hud","vanilla-hud","back"}){var r=gallery.regionBounds(id).orElseThrow();check(r.x()>=0&&r.y()>=0&&r.right()<=gallery.width*gallery.layoutScale()&&r.bottom()<=gallery.height*gallery.layoutScale(),id+" fits compact window");}capture(c,"example-shelf-compact");click(gallery,"wayfarer-hud");advance(500);}
                case 10 -> {check(ExampleHudController.selected().equals("wayfarer-hud"),"HUD selection creates live owner");check(c.level==null&&c.gui.screen()==gallery,"HUD selection does not load a world");click(gallery,"back");advance(500);}
                case 11 -> {
                    check(c.gui.screen()==parent,"back restores Minecraft title");
                    String id="examples-audit-"+System.currentTimeMillis();
                    c.createWorldOpenFlows().createFreshLevel(id,new LevelSettings("Disposable example audit",GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(9824L,false,false),WorldPresets::createNormalWorldDimensions,null);
                    c.options.renderDistance().set(5);c.options.simulationDistance().set(5);c.options.pauseOnLostFocus=false;
                    advance(1200);
                }
                case 12 -> {if(c.player==null||c.level==null)return;c.gui.setScreen(null);inventory=inventory(c);advance(3500);}
                case 13 -> {previous=ExampleHudController.wayfarer();check(previous.belt().snapshot().hasSurface(),"Wayfarer HUD renders in a newly created world");int occupied=0;for(int i=0;i<9;i++)if(!c.player.getInventory().getItem(i).isEmpty())occupied++;check(previous.belt().nativeItemCount()==occupied,"live equipment reflects actual occupied slots");check(inventory.equals(inventory(c)),"live HUD leaves inventory untouched");check(c.gameMode.getPlayerMode()==GameType.CREATIVE,"live HUD preserves game mode");capture(c,"wayfarer-live-world");net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));advance(500);}
                case 14 -> {check(previous.huds().stream().noneMatch(UiHud::enabled),"toggle disables all Wayfarer widgets");capture(c,"vanilla-restored");net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));advance(600);}
                case 15 -> {check(previous.huds().stream().allMatch(UiHud::enabled),"toggle restores selected HUD");ExampleHudController.select("vanilla-hud");advance(900);}
                case 16 -> {check(previous.huds().stream().noneMatch(h->h.snapshot().hasSurface()),"switching HUD closes old surfaces");var h=ExampleHudController.vanilla();check(h.nativeTextCount()==3&&h.nativeItemCount()==4,"vanilla recipe uses native text and items");capture(c,"vanilla-live-world");net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));advance(400);}
                case 17 -> {check(!ExampleHudController.vanilla().enabled(),"vanilla recipe toggles off");ExampleHudController.select("wayfarer-hud");advance(600);}
                case 18 -> {check(ExampleHudController.wayfarer().belt().snapshot().hasSurface(),"closed attachment IDs can be reused");check(inventory.equals(inventory(c)),"switching recipes preserves inventory");net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H));advance(600);}
                case 19 -> {gallery=current(c);check(gallery.regionBounds("wayfarer").isPresent(),"H opens the public shelf during gameplay");click(gallery,"disable-hud");advance(400);}
                case 20 -> {check(!ExampleHudController.active(),"HUD off removes selected owner through gallery input");click(gallery,"field-kit");advance(900);}
                case 21 -> {var s=current(c);check(s!=gallery&&s.regionBounds("slot-hit0").isPresent()&&s.regionBounds("tabs-1").isPresent(),"Field kit opens over the world from the gallery");capture(c,"field-kit-live-world");escape(s);advance(500);}
                case 22 -> {check(c.gui.screen()==null,"Escape closes the field kit back to the game");c.disconnect(new TitleScreen(),false);advance(600);}
                default -> {if(c.level!=null)return;check(!ExampleHudController.active(),"disconnect after removing recipe leaves no active owner");System.out.println("[ExamplesTest] complete PASS checks="+checks);done=true;c.stop();}
            }
        } catch(Throwable error) {error.printStackTrace();System.out.println("[ExamplesTest] FAIL "+error);done=true;c.stop();}
    }
    private String inventory(Minecraft c){var out=new StringBuilder();for(int i=0;i<36;i++)out.append(c.player.getInventory().getItem(i)).append(';');return out.toString();}
    private UiScreen current(Minecraft c){return (UiScreen)c.gui.screen();}
    private void check(boolean valid,String label){checks++;if(!valid)throw new IllegalStateException(label);System.out.println("[ExamplesTest] PASS "+label);}
    /** The screen's own state that keeps it from settling, read by reflection for the failure message. */
    private static String pending(UiScreen s){
        var out=new StringBuilder();
        for(String name:new String[]{"paintCount","dirty","transitionEnd","interactionEnd","customAnimationsActive","scrollMotions","toastOverlay","tooltipRevealAt","hoveredId"}){
            try{var f=UiScreen.class.getDeclaredField(name);f.setAccessible(true);out.append(name).append('=').append(f.get(s)).append(' ');}catch(ReflectiveOperationException e){out.append(name).append("=? ");}
        }
        return out.append("now=").append(System.nanoTime()).toString().trim();
    }
    private void advance(long millis){stage++;deadline=System.nanoTime()+millis*1_000_000;}
    private void capture(Minecraft c,String name){Screenshot.grab(c.gameDirectory,name+".png",c.gameRenderer.mainRenderTarget(),1,m->{});}
    private void escape(UiScreen s){s.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0));}
    private void click(UiScreen s,String id){var b=s.regionBounds(id).orElseThrow();var e=new MouseButtonEvent(b.centerX()/s.layoutScale(),b.centerY()/s.layoutScale(),new MouseButtonInfo(0,0));s.mouseClicked(e,false);s.mouseReleased(e);}
}
