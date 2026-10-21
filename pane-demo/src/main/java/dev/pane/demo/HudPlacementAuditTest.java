package dev.pane.demo;

import dev.pane.ui.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;

/** Actual viewport, native-item and retained-placement checks in the copied showcase world. */
final class HudPlacementAuditTest {
    private final WayfarerHudShowcase owner;
    private UiHud probe;
    private int stage,checks,calls,originalGui;
    private long start,next,paints,uploads,allocations,frames;
    private int previousCalls;
    private boolean failed;
    HudPlacementAuditTest(WayfarerHudShowcase owner){this.owner=owner;}
    void tick(Minecraft c){
        try {
            if(start==0){start=System.nanoTime();originalGui=c.options.guiScale().get();}
            if(System.nanoTime()-start>120_000_000_000L)throw new IllegalStateException("placement audit timeout at "+stage);
            if(System.nanoTime()<next)return;
            switch(stage++) {
                case 0->{
                    check(c.level!=null&&c.gui.screen()==null,"fixture renders the real HUD in its copied world");
                    probe=new UiHud(80,40,UiTheme.dark(),ui->ui.canvas().fill(0xFFCCAE67,0,0,80,40))
                        .attach(Identifier.fromNamespaceAndPath("pane_demo","placement_probe"));
                    probe.setMotionScale(0);
                    policy(UiHudPlacement.Anchor.CENTER_LEFT,1);
                    owner.setHudScale(1);c.options.guiScale().set(2);c.resizeGui();
                    c.getWindow().setWindowed(1440,900);waitMs(900);return;
                }
                case 1->{checkLayout(c,"reference");checkAnchor(c,UiHudPlacement.Anchor.CENTER_LEFT);baseline();waitMs(650);return;}
                case 2->{idle("stable reference");check(calls==previousCalls,"policy is not called on unchanged frames");
                    c.getWindow().setWindowed(640,440);waitMs(900);return;}
                case 3->{checkLayout(c,"compact 640x440");check(calls>previousCalls,"resize resolves responsive policy");
                    check(probe.snapshot().paints()==paints&&probe.snapshot().uploadedPixels()==uploads,"viewport placement change reuses pixels");
                    capture(c,"hud-placement-compact");owner.setHudScale(1.5F);waitMs(650);return;}
                case 4->{checkLayout(c,"compact requested 1.5x");
                    check(owner.belt().placement().scale()<1.5F,"compact recipe bounds requested scale to available area");
                    c.getWindow().setWindowed(1440,900);waitMs(900);return;}
                case 5->{checkLayout(c,"reference requested 1.5x");capture(c,"hud-placement-reference-large");
                    policy(UiHudPlacement.Anchor.CENTER_RIGHT,1.5F);waitMs(400);return;}
                case 6->{checkAnchor(c,UiHudPlacement.Anchor.CENTER_RIGHT);baseline();
                    c.options.guiScale().set(3);c.resizeGui();waitMs(750);return;}
                case 7->{checkLayout(c,"GUI scale 3");checkAnchor(c,UiHudPlacement.Anchor.CENTER_RIGHT);
                    check(probe.snapshot().paints()==paints&&probe.snapshot().uploadedPixels()==uploads,"GUI scale change reuses retained pixels");
                    c.getWindow().setWindowed(1600,500);owner.setHudScale(1);waitMs(900);return;}
                case 8->{checkLayout(c,"ultrawide 1600x500");capture(c,"hud-placement-ultrawide");
                    policy(UiHudPlacement.Anchor.TOP_CENTER,2);waitMs(450);return;}
                case 9->{checkAnchor(c,UiHudPlacement.Anchor.TOP_CENTER);baseline();
                    policy(UiHudPlacement.Anchor.BOTTOM_CENTER,.5F);waitMs(450);return;}
                case 10->{checkAnchor(c,UiHudPlacement.Anchor.BOTTOM_CENTER);
                    check(calls==previousCalls+1,"reinstalling policy refreshes settings exactly once");
                    check(probe.rasterQuality().pixelWidth()==80&&probe.rasterQuality().pixelHeight()==40,"placement shrink releases oversized raster under quality policy");
                    previousCalls=calls;probe.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.CENTER,0,0,1));
                    c.getWindow().setWindowed(900,600);waitMs(750);return;}
                case 11->{check(calls==previousCalls,"fixed placement removes policy across resize");checkAnchor(c,UiHudPlacement.Anchor.CENTER);
                    probe.setEnabled(false);policy(UiHudPlacement.Anchor.TOP_CENTER,1);previousCalls=calls;waitMs(400);return;}
                case 12->{check(calls==previousCalls&&!probe.snapshot().hasSurface(),"disabled HUD does not evaluate policy or retain surface");
                    probe.setEnabled(true);waitMs(450);return;}
                case 13->{check(calls==previousCalls+1,"reenable resolves pending placement policy");
                    probe.setVisible(false);waitMs(350);return;}
                case 14->{check(!probe.snapshot().hasSurface(),"hidden HUD releases retained surface");previousCalls=calls;
                    c.getWindow().setWindowed(640,440);waitMs(650);return;}
                case 15->{check(calls==previousCalls,"hidden HUD does not evaluate responsive policy");probe.setVisible(true);waitMs(500);return;}
                case 16->{check(calls==previousCalls+1,"show resolves changed viewport");checkLayout(c,"compact after show");
                    probe.close();previousCalls=calls;frames=probe.snapshot().frames();
                    c.getWindow().setWindowed(1440,900);owner.setHudScale(1);c.options.guiScale().set(originalGui);c.resizeGui();waitMs(900);return;}
                case 17->{check(calls==previousCalls&&probe.snapshot().frames()==frames&&!probe.snapshot().hasSurface(),"closed HUD stops placement callbacks and rendering");
                    checkLayout(c,"restored reference");capture(c,"wayfarer-hud-responsive-final");
                    c.disconnect(new TitleScreen(),false);waitMs(750);return;}
                default->{if(c.level!=null){stage--;return;}
                    check(owner.huds().stream().noneMatch(h->h.snapshot().hasSurface()),"world unload releases showcase surfaces");
                    System.out.println("[HudPlacementTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);c.stop();}
            }
        }catch(Throwable e){e.printStackTrace();System.out.println("[HudPlacementTest] complete FAIL "+e);stage=999;c.stop();}
    }
    private void policy(UiHudPlacement.Anchor anchor,float scale){probe.setPlacementPolicy(v->{calls++;return new UiHudPlacement(anchor,18,22,scale);});}
    private float unit(Minecraft c){return (float)c.getWindow().getGuiScale()/Math.max(1,(float)c.getWindow().getWidth()/c.getWindow().getScreenWidth());}
    private void checkLayout(Minecraft c,String label){
        var list=owner.huds();float w=c.getWindow().getGuiScaledWidth(),h=c.getWindow().getGuiScaledHeight();
        boolean bounded=true,overlaps=false,matched=true;
        for(var hud:list){var b=hud.snapshot().bounds();bounded&=b.x()>=0&&b.y()>=0&&b.right()<=w+.01&&b.bottom()<=h+.01;}
        for(int i=0;i<list.size();i++)for(int j=i+1;j<list.size();j++)overlaps|=overlap(list.get(i).snapshot().bounds(),list.get(j).snapshot().bounds());
        check(bounded,label+" all seven widgets bounded");check(!overlaps,label+" all seven widgets non-overlapping");
        check(Math.abs(owner.belt().snapshot().bounds().centerX()-w/2)<.1&&Math.abs(owner.compassHud().snapshot().bounds().centerX()-w/2)<.1,label+" belt and compass centered");
        check(owner.belt().nativeItemCount()==9,label+" nine native equipment items visible");
        System.out.println("[HudPlacementTest] "+label+" viewport="+w+"x"+h+" unit="+unit(c)+" bounds="+list.stream().map(hud->hud.snapshot().bounds()).toList());
    }
    private boolean overlap(UiRect a,UiRect b){return a.x()<b.right()-.01&&a.right()>b.x()+.01&&a.y()<b.bottom()-.01&&a.bottom()>b.y()+.01;}
    private void checkAnchor(Minecraft c,UiHudPlacement.Anchor anchor){
        var b=probe.snapshot().bounds();float w=c.getWindow().getGuiScaledWidth(),h=c.getWindow().getGuiScaledHeight(),u=unit(c);
        boolean ok=switch(anchor){
            case TOP_CENTER->Math.abs(b.centerX()-w/2)<.1&&Math.abs(b.y()-22/u)<.1;
            case BOTTOM_CENTER->Math.abs(b.centerX()-w/2)<.1&&Math.abs(b.bottom()-(h-22/u))<.1;
            case CENTER_LEFT->Math.abs(b.centerY()-h/2)<.1&&Math.abs(b.x()-18/u)<.1;
            case CENTER_RIGHT->Math.abs(b.centerY()-h/2)<.1&&Math.abs(b.right()-(w-18/u))<.1;
            case CENTER->Math.abs(b.centerX()-w/2)<.1&&Math.abs(b.centerY()-h/2)<.1;
            default->false;};check(ok,"live "+anchor+" anchor tracks scaled dimensions");
    }
    private void baseline(){var s=probe.snapshot();paints=s.paints();uploads=s.uploadedPixels();allocations=s.allocations();previousCalls=calls;}
    private void idle(String name){var s=probe.snapshot();check(s.paints()==paints&&s.uploadedPixels()==uploads&&s.allocations()==allocations,name+" zero extra paints/uploads/allocations");}
    private void check(boolean ok,String name){checks++;failed|=!ok;System.out.println("[HudPlacementTest] "+name+": "+(ok?"PASS":"FAIL"));}
    private void waitMs(long ms){next=System.nanoTime()+ms*1_000_000L;}
    private void capture(Minecraft c,String name){Screenshot.grab(c.gameDirectory,name+".png",c.gameRenderer.mainRenderTarget(),1,m->{});}
}
