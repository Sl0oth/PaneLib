package dev.pane.demo;

import dev.pane.ui.*;
import dev.pane.ui.internal.NativeOpacity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;

/** Runs only in the copied Wayfarer fixture world. */
final class HudNativeAuditTest {
    private final WayfarerHudShowcase owner;
    private UiHud probe;
    private int stage, checks, frames, originalGui;
    private long next, start, idlePaints, idleFrames, probeFrames;
    private boolean failed, sawFade;
    private float opacity;
    private final List<Float> bearings=new ArrayList<>();
    HudNativeAuditTest(WayfarerHudShowcase owner){this.owner=owner;}
    void heading(float degrees){if(stage==12||stage==13)bearings.add(degrees);}
    void tick(Minecraft c){
        try {
            if(start==0){start=System.nanoTime();originalGui=c.options.guiScale().get();}
            if(System.nanoTime()-start>90_000_000_000L)throw new IllegalStateException("HUD audit timeout");
            if(System.nanoTime()<next)return;
            var belt=owner.belt();var compass=owner.compassHud();
            switch(stage){
                case 0->{
                    check(belt.nativeItemCount()==9,"nine hotbar items rendered through supported HUD slots");
                    probe=new UiHud(210,220,UiTheme.dark(),ui->{
                        ui.canvas().fill(0xFF17232B,0,0,210,220);
                        ui.canvas().text("NATIVE HUD FADE",12,10,12,0xFFFFFFFF);
                        ui.nativeSlot("item",15,55,45,45);ui.nativeSlot("avatar",75,30,120,175);
                    },overlay->{opacity=NativeOpacity.current();overlay.item("item",new ItemStack(Items.DIAMOND));overlay.playerPreview("avatar",null);overlay.item("absent",new ItemStack(Items.APPLE));})
                        .attach(Identifier.fromNamespaceAndPath("pane_demo","hud_native_audit"));
                    probe.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.TOP_LEFT,30,180,1));
                    stage++;waitMs(650);
                }
                case 1->{check(probe.nativeItemCount()==1&&probe.nativeEntityCount()==1,"HUD supports item and world-free avatar; missing slot skipped");capture(c,"hud-native-full");probe.setVisible(false);belt.setMotionScale(1);belt.setVisible(false);stage++;}
                case 2->{
                    if(opacity>.1F&&opacity<.9F&&!sawFade){sawFade=true;check(probe.nativeItemCount()==1&&probe.nativeEntityCount()==1,"native draws participate in partial-opacity exit");System.out.println("[HudNativeTest] fade opacity="+opacity+" bounds="+probe.snapshot().bounds());capture(c,"hud-native-fade");}
                    if(!probe.snapshot().hasSurface()){check(sawFade,"observed an intermediate fade frame");check(probe.nativeItemCount()==0&&probe.nativeEntityCount()==0&&belt.nativeItemCount()==0,"hidden HUD clears surfaces and native counts");capture(c,"hud-native-hidden");probe.setVisible(true);belt.setVisible(true);stage++;waitMs(650);}
                }
                case 3->{check(belt.nativeItemCount()==9&&probe.nativeEntityCount()==1,"show resumes native slots");c.gui.hud.toggle();idleFrames=belt.snapshot().frames();stage++;waitMs(300);}
                case 4->{check(belt.snapshot().frames()==idleFrames&&belt.nativeItemCount()==0,"F1 suppresses native HUD rendering");c.gui.hud.toggle();c.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(false));stage++;waitMs(300);}
                case 5->{check(belt.nativeItemCount()==0&&probe.nativeEntityCount()==0,"ordinary screen suppresses native overlays");probe.setShowOnScreens(true);stage++;waitMs(250);}
                case 6->{check(probe.nativeEntityCount()==1&&belt.nativeItemCount()==0,"showOnScreens applies to native slots too");c.gui.setScreen(null);c.options.guiScale().set(3);c.resizeGui();belt.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.BOTTOM_LEFT,100,22,1.25F));probe.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.TOP_LEFT,30,180,1.4F));stage++;waitMs(650);}
                case 7->{check(belt.nativeItemCount()==9&&probe.nativeEntityCount()==1,"native items/entities survive GUI and placement scale changes");capture(c,"hud-native-scaled");probeFrames=probe.snapshot().frames();probe.close();stage++;waitMs(250);}
                case 8->{check(!probe.snapshot().hasSurface()&&probe.nativeItemCount()==0&&probe.nativeEntityCount()==0&&probe.snapshot().frames()==probeFrames,"close releases native HUD and stops callbacks");c.options.guiScale().set(originalGui);c.resizeGui();c.player.setYRot(179);compass.setMotionScale(2);stage++;waitMs(500);}
                case 9->{idlePaints=compass.snapshot().paints();idleFrames=belt.snapshot().frames();stage++;waitMs(400);}
                case 10->{check(compass.snapshot().paints()==idlePaints,"stationary compass settles to zero idle paints");check(belt.snapshot().frames()>idleFrames&&belt.nativeItemCount()==9,"retained HUD keeps native items live while idle");c.player.setYRot(179);stage=11;waitMs(250);}
                case 11->{bearings.clear();c.player.setYRot(181);stage=12;waitMs(300);}
                case 12->{
                    check(bearings.stream().anyMatch(v->v>359.01F||v<.99F),"north crossing includes intermediate animated headings");
                    check(bearings.stream().allMatch(v->v>358.9F||v<1.1F),"359 to 1 crosses north without a long spin");
                    bearings.clear();stage=13;frames=0;
                }
                case 13->{
                    // Actual camera yaw follows a continuous sweep. Capture the real retained HUD at 20Hz.
                    if(frames<40){capture(c,String.format(Locale.ROOT,"compass-scroll-%02d",frames));c.player.setYRot(181+frames*4);frames++;waitMs(50);}
                    else{check(bearings.size()>25,"compass produces intermediate render frames during continuous turning");System.out.println("[HudNativeTest] animated headings="+bearings);compass.setMotionScale(0);c.player.setYRot(-90);stage++;waitMs(200);}
                }
                case 14->{check(Math.abs(owner.displayedHeading-90)<.01,"reduced motion adopts final bearing directly");compass.setMotionScale(1);capture(c,"wayfarer-hud-native-final");c.disconnect(new TitleScreen(),false);stage++;waitMs(500);}
                case 15->{if(c.level!=null)return;check(!belt.snapshot().hasSurface()&&belt.nativeItemCount()==0,"world unload releases native HUD state");System.out.println("[HudNativeTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);stage++;c.stop();}
            }
        }catch(Throwable e){failed=true;e.printStackTrace();System.out.println("[HudNativeTest] complete FAIL "+e);stage=99;c.stop();}
    }
    private void waitMs(int ms){next=System.nanoTime()+ms*1_000_000L;}
    private void check(boolean ok,String name){checks++;failed|=!ok;System.out.println("[HudNativeTest] "+name+": "+(ok?"PASS":"FAIL"));}
    private void capture(Minecraft c,String name){Screenshot.grab(c.gameDirectory,name+".png",c.gameRenderer.mainRenderTarget(),1,m->{});}
}
