package dev.pane.demo;

import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Consumer-only acceptance and screenshots. Uses an existing isolated fixture world if requested. */
final class CastleAuditTest {
    private UiScreen screen;
    private int stage,checks,paints,oldWidth,oldHeight;
    private long deadline,started;
    private boolean failed,finished;
    void tick(Minecraft client) {
        if(finished)return;
        try {
            if(screen==null) {
                if(!(client.gui.screen() instanceof TitleScreen)||client.gui.overlay()!=null)return;
                oldWidth=client.getWindow().getScreenWidth();oldHeight=client.getWindow().getScreenHeight();
                client.getWindow().setWindowed(1100,704);
                screen=CastleShowcase.createScreen(client.gui.screen());client.gui.setScreen(screen);
                started=System.nanoTime();waitFor(900);return;
            }
            if(System.nanoTime()-started>120_000_000_000L)throw new IllegalStateException("Castle fixture timed out");
            if(System.nanoTime()<deadline||client.gui.overlay()!=null)return;
            if(stage==0) {
                capture(client,"castle-title");
                check(screen.performanceSnapshot().nativeEntityCount()==0,"title state does not invent a local player");
                check(screen.regionBounds("enter").isPresent(),"primary carved button registered");
                paints=screen.paintCount();stage++;waitFor(700);return;
            }
            if(stage==1) {
                check(screen.paintCount()==paints,"static scenery causes zero idle repaints");
                screen.focus("settings");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));
                check(screen.pageId().equals("settings"),"keyboard Enter opens settings");stage++;waitFor(300);return;
            }
            if(stage==2) {
                capture(client,"castle-settings");click("ivy");check(!screen.booleanValue("ivy",true),"ivy customization changes state");
                click("light");check(!screen.booleanValue("light",true),"torchlight customization changes state");
                stage++;waitFor(250);return;
            }
            if(stage==3) {
                capture(client,"castle-bare-stone");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0));
                check(screen.pageId().equals("gate")&&client.gui.screen()==screen,"Escape returns from settings to the gate");stage++;waitFor(300);return;
            }
            if(stage==4) {
                client.getWindow().setWindowed(640,440);stage++;waitFor(500);return;
            }
            if(stage==5) {
                capture(client,"castle-compact");
                for(String id:new String[]{"enter","chronicle","settings","back"}) {
                    var b=screen.regionBounds(id).orElseThrow();
                    check(b.x()>=0&&b.y()>=0&&b.right()<=screen.width*screen.layoutScale()&&b.bottom()<=screen.height*screen.layoutScale(),"compact button stays in viewport: "+id);
                }
                client.getWindow().setWindowed(1100,704);stage=55;waitFor(400);return;
            }
            if(stage==55) {click("chronicle");stage=6;waitFor(300);return;}
            if(stage==6) {
                check(screen.pageId().equals("chronicle"),"chronicle navigation works");capture(client,"castle-chronicle");click("gate");stage++;waitFor(300);return;
            }
            if(stage==7) {
                click("enter");check(client.gui.screen() instanceof SelectWorldScreen,"Enter opens real local world picker");
                client.gui.screen().onClose();check(client.gui.screen()==screen,"world picker returns to the castle menu");
                screen.setValue("ivy",true);screen.setValue("light",true);
                if(Boolean.getBoolean("pane.demo.castleWorldTest")) {
                    client.options.renderDistance().set(2);client.options.simulationDistance().set(5);
                    String id="pane_studio_autotest_20261016";
                    if(!client.getLevelSource().levelExists(id))throw new IllegalStateException("Missing isolated fixture world "+id);
                    client.createWorldOpenFlows().openWorld(id,()->{throw new IllegalStateException("Fixture world cancelled");});
                    stage=8;waitFor(300);return;
                }
                finish(client);return;
            }
            if(stage==8) {
                if(client.player==null||client.level==null)return;
                screen=CastleShowcase.createScreen(null);client.gui.setScreen(screen);
                GLFW.glfwSetCursorPos(client.getWindow().handle(),client.getWindow().getScreenWidth()*.74,client.getWindow().getScreenHeight()*.586);
                stage++;waitFor(1400);return;
            }
            if(stage==9) {
                check(screen.performanceSnapshot().nativeEntityCount()==1,"existing native API renders the real local player");
                capture(client,"castle-player");paints=screen.paintCount();stage++;waitFor(700);return;
            }
            if(stage==10) {
                check(screen.paintCount()==paints,"live player preview does not reraster static scenery");
                System.out.println("[CastleTest] "+screen.performanceSnapshot().summary());
                click("enter");check(client.gui.screen()==null,"in-world primary button resumes play");
                client.disconnect(new TitleScreen(),false);stage++;waitFor(300);return;
            }
            finish(client);
        }catch(Throwable error){finished=true;error.printStackTrace();System.out.println("[CastleTest] complete FAIL "+error);client.stop();}
    }
    private void finish(Minecraft client){
        client.getWindow().setWindowed(oldWidth,oldHeight);finished=true;
        System.out.println("[CastleTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);client.stop();
    }
    private void waitFor(long ms){deadline=System.nanoTime()+ms*1_000_000L;}
    private void capture(Minecraft client,String name){Screenshot.grab(client.gameDirectory,name+".png",client.gameRenderer.mainRenderTarget(),1,message->{});}
    private void click(String id){var b=screen.regionBounds(id).orElseThrow();var e=new MouseButtonEvent(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseClicked(e,false);screen.mouseReleased(e);}
    private void check(boolean passed,String message){checks++;failed|=!passed;System.out.println("[CastleTest] "+message+": "+(passed?"PASS":"FAIL"));}
}
