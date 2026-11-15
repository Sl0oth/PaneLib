package dev.pane.demo;
import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Real-client checks for the two game-menu showcases: slanted targets, looping motion, shop, garage and settings. */
final class GameMenusAuditTest {
    private UiScreen screen;private int stage,checks,paints;private long until,started;private boolean failed,done;
    void tick(Minecraft c){
        if(done)return;
        try{
            if(started==0)started=System.nanoTime();
            if(System.nanoTime()-started>200_000_000_000L)throw new IllegalStateException("Fixture timeout");
            if(screen==null){
                if(!(c.gui.screen() instanceof TitleScreen)||c.gui.overlay()!=null)return;
                c.getWindow().setWindowed(1440,810);screen=SkydropShowcase.createScreen(null);c.gui.setScreen(screen);
                GLFW.glfwSetCursorPos(c.getWindow().handle(),4,4);waitFor(2000);return;
            }
            if(System.nanoTime()<until||c.gui.overlay()!=null)return;
            switch(stage++){
                case 0->{capture(c,"skydrop-lobby");check(!screen.rasterQuality().upscaled(),"full-resolution surface");check(screen.performanceSnapshot().nativeEntityCount()==1,"player preview is drawn");paints=screen.paintCount();waitFor(700);return;}
                case 1->{check(screen.paintCount()>paints+10,"the pulsing Play button and turning rays keep repainting: "+(screen.paintCount()-paints));
                    var tab=screen.regionBounds("tab:SHOP").orElseThrow();clickAt(tab.x()+3,tab.y()+3);}
                case 2->{check(screen.stringValue("tab","LOBBY").equals("LOBBY"),"a slanted tab's cut-away corner takes no click");click("play");}
                case 3->{check(screen.booleanValue("queued",false),"Play starts matchmaking");check(!screen.buttonState("mode").orElseThrow().enabled(),"the mode cannot change while queued");waitFor(1300);return;}
                case 4->{capture(c,"skydrop-queued");click("play");}
                case 5->{check(!screen.booleanValue("queued",true),"Cancel leaves the queue");click("invite:0");click("claim:0");}
                case 6->{check(screen.booleanValue("party0",false),"a round party slot invites a friend");check(screen.numberValue("xp",0)==4300,"a finished quest pays its XP");check(!screen.buttonState("claim:2").orElseThrow().enabled(),"an unfinished quest cannot be claimed");
                    screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_E,0,0));}
                case 7->{check(screen.stringValue("tab","").equals("PASS"),"E moves to the next tab");capture(c,"skydrop-pass");check(screen.regionBounds("upgrade").isPresent(),"pass upgrade button");click("upgrade");}
                case 8->{check(screen.booleanValue("premium",false),"the pass unlocks");click("tab:LOCKER");}
                case 9->{click("slot:GLIDER");}
                case 10->{check(screen.stringValue("slot","").equals("GLIDER"),"a locker slot selects");click("equip:GLIDER:5");}
                case 11->{check(screen.stringValue("equip:GLIDER","").equals("5"),"a style equips");capture(c,"skydrop-locker");click("tab:SHOP");}
                case 12->{capture(c,"skydrop-shop");click("buy:ember");}
                case 13->{check(screen.openDialogId().isPresent(),"a shop card inside a scaled group opens its dialog");capture(c,"skydrop-buy");click("dialog-confirm");}
                case 14->{check(screen.booleanValue("owned:ember",false)&&screen.numberValue("bolts",0)==1950,"buying deducts bolts and marks the item owned");click("buy:aurora");}
                case 15->{click("dialog-confirm");}
                case 16->{check(!screen.booleanValue("owned:aurora",false),"an item that costs too much is not bought");click("tab:CAREER");waitFor(1500);return;}
                case 17->{capture(c,"skydrop-career");click("settings");}
                case 18->{check(screen.booleanValue("settings",false),"the gear opens settings");capture(c,"skydrop-settings");screen.setValue("accent","#3DDC97");screen.setValue("reduce-motion",true);screen.setMotionScale(0);click("settings-done");}
                case 19->{check(!screen.booleanValue("settings",true),"Done closes settings");click("tab:LOBBY");waitFor(900);return;}
                case 20->{capture(c,"skydrop-green");paints=screen.paintCount();waitFor(900);return;}
                case 21->{check(screen.paintCount()-paints<=2,"with motion off only the season clock repaints the lobby: "+(screen.paintCount()-paints));c.getWindow().setWindowed(960,600);waitFor(1200);return;}
                case 22->{capture(c,"skydrop-compact");var b=screen.regionBounds("play").orElseThrow();check(b.right()<=screen.width*screen.layoutScale()+1&&b.bottom()<=screen.height*screen.layoutScale()+1,"Play stays on screen in a small window");
                    c.getWindow().setWindowed(1440,810);screen=OverdriveShowcase.createScreen(null);c.gui.setScreen(screen);GLFW.glfwSetCursorPos(c.getWindow().handle(),4,4);waitFor(2200);return;}
                case 23->{capture(c,"overdrive-menu");check(!screen.rasterQuality().upscaled(),"arena menu at full resolution");paints=screen.paintCount();waitFor(800);return;}
                case 24->{check(screen.paintCount()==paints,"the arena main menu settles to zero idle repaints: "+(screen.paintCount()-paints));
                    screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_DOWN,0,0));}
                case 25->{check(screen.stringValue("menu","").equals("GARAGE"),"Down moves the menu selection");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));}
                case 26->{check(screen.stringValue("view","").equals("GARAGE"),"Enter opens the selection");capture(c,"overdrive-garage");screen.setValue("paint","#FF5A1F");click("team:ORANGE");}
                case 27->{check(screen.stringValue("team","").equals("ORANGE"),"team colour switches");click("part:WHEELS");}
                case 28->{click("pick:WHEELS:3");}
                case 29->{check(screen.stringValue("pick:WHEELS","").equals("3"),"a wheel from the sideways strip equips");capture(c,"overdrive-garage-orange");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0));}
                case 30->{check(screen.stringValue("view","").equals("MENU"),"Escape returns to the main menu");click("menu:PLAY");}
                case 31->{check(screen.stringValue("view","").equals("PLAY"),"clicking a menu row opens it");capture(c,"overdrive-play");click("playlist:1");}
                case 32->{check(screen.numberValue("playlist",-1)==1,"a playlist card selects");click("queue");waitFor(1300);return;}
                case 33->{check(screen.booleanValue("searching",false),"the queue starts");capture(c,"overdrive-searching");click("queue");}
                case 34->{check(!screen.booleanValue("searching",true),"and cancels");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0));}
                case 35->{click("menu:SETTINGS");}
                case 36->{check(screen.stringValue("view","").equals("SETTINGS"),"settings opens");capture(c,"overdrive-settings");click("menu-back");}
                case 37->{click("menu:PROFILE");waitFor(1400);return;}
                case 38->{capture(c,"overdrive-profile");c.getWindow().setWindowed(960,600);waitFor(1200);return;}
                case 39->{capture(c,"overdrive-compact");check(screen.regionBounds("menu-back").isPresent(),"back stays reachable in a small window");}
                default->{System.out.println("[MenuTest] "+checks+" checks");System.out.println("[MenuTest] complete "+(failed?"FAIL":"PASS"));done=true;c.stop();}
            }
            waitFor(450);
        }catch(Throwable e){e.printStackTrace();System.out.println("[MenuTest] complete FAIL "+e);done=true;c.stop();}
    }
    private void waitFor(long ms){until=System.nanoTime()+ms*1_000_000;}
    private void capture(Minecraft c,String n){Screenshot.grab(c.gameDirectory,n+".png",c.gameRenderer.mainRenderTarget(),1,m->{});}
    private void check(boolean ok,String name){checks++;failed|=!ok;System.out.println("[MenuTest] "+name+": "+(ok?"PASS":"FAIL"));}
    private void click(String id){var b=screen.regionBounds(id).orElseThrow(()->new IllegalStateException("Missing hit target: "+id));clickAt(b.centerX(),b.centerY());}
    private void clickAt(float x,float y){var e=new MouseButtonEvent(x/screen.layoutScale(),y/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseClicked(e,false);screen.mouseReleased(e);}
}
