package dev.pane.demo;
import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Real-client checks for the original lobby, including native input and modal focus. */
final class WayfarerAuditTest {
    private UiScreen screen;private int stage,checks,paints,lastWidth,lastHeight;private long until,settledAt,started;private int settlePaint=-1;private boolean failed,done;
    void tick(Minecraft c){
        if(done)return;
        try{
            if(started==0)started=System.nanoTime();
            if(System.nanoTime()-started>180_000_000_000L)throw new IllegalStateException("Fixture timeout");
            if(screen==null){
                if(!(c.gui.screen() instanceof TitleScreen)||c.gui.overlay()!=null)return;
                c.getWindow().setWindowed(1440,810);screen=WayfarerPreview.openMenu(c);
                GLFW.glfwSetCursorPos(c.getWindow().handle(),10,100);
                waitFor(1800);return;
            }
            int width=c.getWindow().getWidth(),height=c.getWindow().getHeight();
            if(width!=lastWidth||height!=lastHeight){lastWidth=width;lastHeight=height;waitFor(1200);return;}
            if(System.nanoTime()<until||c.gui.overlay()!=null)return;
            if(stage==0||stage==17){
                if(screen.paintCount()!=settlePaint){settlePaint=screen.paintCount();settledAt=System.nanoTime();return;}
                if(System.nanoTime()-settledAt<1_000_000_000L)return;
            }
            switch(stage++){
                case 0->{check(c.level==null,"main menu has no loaded world");check(c.player==null,"avatar renders without a live player");check(c.getSingleplayerServer()==null,"no integrated server running");check(c.getResourceManager().getResource(WayfarerPlayerPreview.EXPLORER_SKIN.body().texturePath()).isPresent(),"custom explorer skin resolves to packaged texture");capture(c,"wayfarer-lobby");System.out.println("[WayfarerQuality] "+screen.rasterQuality());check(!screen.rasterQuality().upscaled(),"full-resolution surface");check(screen.regionBounds("deploy").isPresent(),"native deploy target");check(screen.performanceSnapshot().nativeEntityCount()==1,"native Minecraft avatar rendered in main menu");paints=screen.paintCount();waitFor(900);return;}
                case 1->{System.out.println("[WayfarerIdle] before="+paints+" after="+screen.paintCount());check(screen.paintCount()==paints,"lobby settles to zero idle repaints");
                    if(Boolean.getBoolean("pane.demo.wayfarerMenuOnly")){
                        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0));
                        check(c.gui.screen() instanceof TitleScreen,"Escape returns to vanilla title screen");
                        screen=WayfarerPreview.openMenu(c);
                        check(c.gui.screen()==screen&&c.level==null&&c.getSingleplayerServer()==null,"main menu reopens without a world");
                        System.out.println("[WayfarerTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks+" scope=main-menu");done=true;c.stop();return;
                    }
                    click("mission:1");}
                case 2->{check(screen.numberValue("mission",0)==1,"mission selection updates");click("tab:LOADOUT");check(screen.stringValue("tab","").equals("LOADOUT"),"tab pointer activates loadout");}
                case 3->{capture(c,"wayfarer-loadout");click("gear:2");}
                case 4->{check(screen.numberValue("gear",0)==2,"equipment selection retained");check(screen.performanceSnapshot().nativeEntityCount()==1,"player remains visible in loadout");capture(c,"wayfarer-equipped");click("tab:JOURNAL");}
                case 5->{capture(c,"wayfarer-journal");click("claim");}
                case 6->{check(screen.performanceSnapshot().nativeEntityCount()==0,"player hidden outside character tabs");check(screen.booleanValue("claimed",false)&&screen.numberValue("xp",0)==1670,"journal reward updates XP");check(!screen.buttonState("claim").orElseThrow().enabled(),"claimed reward cannot be repeated");click("tab:OPTIONS");}
                case 7->{screen.focus("callsign");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_END,0,0));screen.charTyped(new CharacterEvent(33));check(screen.stringValue("callsign","").equals("Rowan!"),"styled callsign accepts native typing");click("reduce-motion");}
                case 8->{check(screen.booleanValue("reduce-motion",false),"reduced motion toggle");capture(c,"wayfarer-options");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0));}
                case 9->{check(screen.stringValue("tab","").equals("PLAY"),"Escape returns to lobby");click("party");}
                case 10->{check(screen.booleanValue("party",false),"local crew privacy toggle");click("deploy");}
                case 11->{check(screen.openDialogId().isPresent(),"deploy opens native confirmation");check(screen.focusedId().orElse("").equals("dialog-cancel"),"confirmation defaults to cancel");capture(c,"wayfarer-confirmation");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0));}
                case 12->{check(screen.openDialogId().isEmpty()&&!screen.booleanValue("launched",false),"Escape safely cancels launch");click("deploy");}
                case 13->{click("dialog-confirm");}
                case 14->{check(screen.booleanValue("launched",false),"confirm opens local expedition briefing");capture(c,"wayfarer-briefing");click("return");}
                case 15->{check(!screen.booleanValue("launched",false),"return restores lobby");c.getWindow().setWindowed(960,600);}
                case 16->{capture(c,"wayfarer-compact");var b=screen.regionBounds("mission:2").orElseThrow();check(b.right()<=screen.width*screen.layoutScale()&&b.bottom()<=screen.height*screen.layoutScale(),"compact mission cards remain reachable");c.getWindow().setWindowed(1440,810);screen=WayfarerShowcase.createScreen(null);c.gui.setScreen(screen);settlePaint=-1;waitFor(1500);return;}
                case 17->{check(screen.performanceSnapshot().nativeEntityCount()==1,"final live player preview");capture(c,"wayfarer-final");paints=screen.paintCount();waitFor(700);return;}
                case 18->{check(screen.paintCount()==paints,"final lobby settles after animations");GLFW.glfwSetCursorPos(c.getWindow().handle(),10,100);}
                case 19->{capture(c,"wayfarer-pointer-left");GLFW.glfwSetCursorPos(c.getWindow().handle(),c.getWindow().getScreenWidth()-10,100);}
                case 20->{capture(c,"wayfarer-pointer-right");check(screen.performanceSnapshot().nativeEntityCount()==1,"fixed stance still uses live native renderer");}
                default->{System.out.println("[WayfarerTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);done=true;c.stop();}
            }
            waitFor(550);
        }catch(Throwable e){e.printStackTrace();System.out.println("[WayfarerTest] complete FAIL "+e);done=true;c.stop();}
    }
    private void waitFor(long ms){until=System.nanoTime()+ms*1_000_000;}
    private void capture(Minecraft c,String n){Screenshot.grab(c.gameDirectory,n+".png",c.gameRenderer.mainRenderTarget(),1,m->{});}
    private void check(boolean ok,String name){checks++;failed|=!ok;System.out.println("[WayfarerTest] "+name+": "+(ok?"PASS":"FAIL"));}
    private void click(String id){var b=screen.regionBounds(id).orElseThrow(()->new IllegalStateException("Missing hit target: "+id+"; tab="+screen.stringValue("tab","PLAY")));var e=new MouseButtonEvent(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseClicked(e,false);screen.mouseReleased(e);}
}
