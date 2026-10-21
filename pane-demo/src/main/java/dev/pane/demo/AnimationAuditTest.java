package dev.pane.demo;

import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/** Retained animation ownership under changing controls and recycled virtual rows. */
final class AnimationAuditTest {
    private UiScreen screen;
    private int stage,generation,checks,paints,bulkPeak;
    private boolean bulkPainted, hideRows;
    private long deadline;
    private long idleUploads;
    private boolean failed,finished;
    private float entrance;
    private boolean pulse;
    private float sampledPulse, sampledInteraction;
    void tick(Minecraft client) {
        if(finished)return;
        try {
            if(screen==null) {
                if(!(client.gui.screen() instanceof TitleScreen))return;
                screen=new UiScreen(UiDocument.builder("Animation audit").scaling(UiScaling.fixed())
                    .page(new UiPage("main","Main","",0xFF88AA55,ui->{
                        if(!bulkPainted) {
                            for(int i=0;i<10000;i++)ui.animate("discard-"+i,1,0,UiEasing.LINEAR);
                            bulkPeak=screen.animationStateCount();bulkPainted=true;
                        }
                        ui.animate("changing-"+generation,1,0,UiEasing.LINEAR);
                        entrance=ui.animateFrom("persistent",0,1,300,0,UiEasing.LINEAR,new UiRect(350,80,100,20));
                        ui.hit("main","noop",350,20,100,40);
                        sampledInteraction=ui.interactionMotion();
                        ui.canvas().fill(0xFF889955,350,20,20+80*sampledInteraction,40);
                        if (!hideRows) ui.virtualList("rows",new UiRect(20,20,250,180),10000,30,2,(row,item)->{
                            row.animate("row-motion-"+item.index(),1,0,UiEasing.LINEAR);
                            float progress=row.animate("row-pulse-"+item.index(),pulse?1:0,800,UiEasing.LINEAR);
                            if(item.index()==2000)sampledPulse=progress;
                            row.hit("row-"+item.index(),"noop",item.bounds().x(),item.bounds().y(),200,25);
                        });
                    })).build());
                client.gui.setScreen(screen);deadline=System.nanoTime()+1_000_000_000L;return;
            }
            if(screen.paintCount()==0||System.nanoTime()<deadline)return;
            if(stage<20) {
                if(stage==0) {
                    check(bulkPeak>=10000,"fixture registered ten thousand completed animation states");
                    screen.invalidate();
                } else check(screen.animationStateCount()==2+2*screen.virtualCachedRows("rows"),"retention equals live page/row owners at window "+generation);
                generation++;
                screen.scrollVirtualItemIntoView("rows",generation*100);
                if(generation<=10)screen.invalidate();
                if (generation == 20) screen.invalidate();
                stage++;deadline=System.nanoTime()+80_000_000L;return;
            }
            if(stage==20) {
                check(entrance==1,"persistent entrance does not replay during pruning");
                screen.focus("row-"+(generation*100));screen.focus("main");
                paints=screen.paintCount();
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));
                stage++;deadline=System.nanoTime()+400_000_000L;return;
            }
            if(stage==21) {
                check(screen.paintCount()>paints+1,"main interaction keeps repainting after virtual-row focus");
                screen.setMotionScale(2);pulse=true;
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));
                paints=screen.paintCount();sampledInteraction=-1;
                screen.focus("row-1999");screen.invalidateVirtualItem("rows",2000);
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));
                stage++;deadline=System.nanoTime()+100_000_000L;return;
            }
            if(stage==22) {
                check(sampledInteraction>0 && sampledInteraction<1 && screen.paintCount()>paints+1,
                    "base animation keeps progressing when interrupted by a row interaction");
                check(sampledPulse>0,"custom row animation advances alongside another row interaction");
                screen.setMotionScale(0);stage++;deadline=System.nanoTime()+200_000_000L;return;
            }
            if(stage==23) {
                check(sampledPulse==1,"reduced motion snaps custom row to its final value");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));
                stage++;deadline=System.nanoTime()+250_000_000L;return;
            }
            if(stage==24) {
                idleUploads=screen.virtualRowUploadPixels();paints=screen.paintCount();
                stage++;deadline=System.nanoTime()+250_000_000L;return;
            }
            if(stage==25) check(screen.virtualRowUploadPixels()==idleUploads && screen.paintCount()==paints,"zero-duration interactions leave no idle row uploads or base paints");
            if (stage==25) {
                hideRows=true;screen.invalidate();stage++;deadline=System.nanoTime()+150_000_000L;return;
            }
            check(screen.animationStateCount()==2 && screen.virtualCachedRows("rows")==0, "removing final list releases row animation owners");
            check(!screen.scrollVirtualItemIntoView("rows",2000),"removed list is no longer addressable");
            screen.removed();check(screen.animationStateCount()==0,"screen removal releases animation states");
            finished=true;System.out.println("[AnimationTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);client.stop();
        }catch(Throwable error){finished=true;error.printStackTrace();System.out.println("[AnimationTest] complete FAIL "+error);client.stop();}
    }
    private void check(boolean passed,String message){checks++;failed|=!passed;System.out.println("[AnimationTest] "+message+": "+(passed?"PASS":"FAIL"));}
}
