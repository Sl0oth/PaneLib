package dev.pane.demo;
import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Consumer acceptance checks against real Minecraft rendering and input dispatch. */
final class DiscordAuditTest {
    private UiScreen screen;private DiscordShowcase.Model model;private int stage,checks,paints,count;private long deadline;private boolean failed,done;
    void tick(Minecraft client){
        if(done)return;
        try{
            if(screen==null){if(!(client.gui.screen() instanceof TitleScreen)||client.gui.overlay()!=null)return;
                client.getWindow().setWindowed(1440,907);model=new DiscordShowcase.Model();screen=DiscordShowcase.createScreen(client.gui.screen(),model);client.gui.setScreen(screen);waitFor(1300);return;}
            if(System.nanoTime()<deadline||client.gui.overlay()!=null)return;
            switch(stage++){
                case 0->{System.out.println("[GatherQuality] "+screen.rasterQuality());check(!screen.rasterQuality().upscaled(),"surface renders without upscaling");capture(client,"discord-server");check(screen.regionBounds("channel:general").isPresent(),"native channel input exists");paints=screen.paintCount();waitFor(900);return;}
                case 1->{check(screen.paintCount()==paints,"server has zero idle repaints");click("channel:showcase");}
                case 2->{check(model.conversation.equals("showcase"),"channel switching");click("react:0");check(model.current().getFirst().likes==7,"reaction increments");}
                case 3->{click("react:0");check(model.current().getFirst().likes==6,"reaction toggles off");click("home");}
                case 4->{check(model.dm&&model.friends,"home opens Friends view");capture(client,"discord-friends");click("dm:Maya");}
                case 5->{capture(client,"discord-dms");count=model.current().size();screen.focus("composer");"Testing native Enter to send".codePoints().forEach(cp->screen.charTyped(new CharacterEvent(cp)));screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));}
                case 6->{check(model.current().size()==count+1,"Enter submits to the current DM");check(screen.stringValue("draft:dm:Maya","bad").isEmpty(),"send clears composer");screen.setValue("draft:dm:Maya","Keep this draft");click("dm:Alex");}
                case 7->{check(screen.stringValue("draft:dm:Alex","").isEmpty(),"DM drafts are isolated");click("dm:Maya");}
                case 8->{check(screen.stringValue("draft:dm:Maya","").equals("Keep this draft"),"draft survives conversation switching");screen.setValue("search","cozy");}
                case 9->{check(screen.numberValue("visible-message-count",0)==1,"search filters the rendered conversation");capture(client,"discord-search");screen.setValue("search","");click("server");}
                case 10->{click("voice");check(model.voice,"local voice preview toggles");click("appearance");}
                case 11->{check(model.settings&&model.setting.equals("Appearance"),"user gear opens appearance");click("compact");screen.setValue("text-size",17);click("save");}
                case 12->{check(model.compact&&model.textSize==17,"appearance changes save");click("setting:Overview");}
                case 13->{screen.setValue("server-name","Everlight Studio");screen.setValue("description","A native Discord-style community demo.");capture(client,"discord-settings");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0));check(model.settings,"unsaved changes guard Escape");}
                case 14->{click("reset");check(screen.stringValue("server-name","").equals("Trailside"),"Reset restores saved settings");screen.setValue("server-name","");click("save");check(model.serverName.equals("Trailside"),"blank server name cannot save");}
                case 15->{screen.setValue("server-name","Everlight Studio");click("save");check(model.serverName.equals("Everlight Studio"),"server rename saves");click("setting:Roles");}
                case 16->{capture(client,"discord-roles");click("links");click("save");check(!model.links,"role permission toggle saves");click("setting:Members");}
                case 17->{capture(client,"discord-members");check(screen.regionBounds("role").isPresent(),"native role dropdown exists");click("role");}
                case 18->{screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_DOWN,0,0));screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));}
                case 19->{check(screen.stringValue("role","").equals("Moderator"),"role dropdown keyboard selection");click("save");check(model.role.equals("Moderator"),"role assignment saves");click("setting:Notifications");}
                case 20->{click("notify");click("mentions");click("save");check(!model.notify&&!model.mentions,"notification settings save");click("close-settings");}
                case 21->{check(!model.settings,"saved settings close");click("members");check(!model.members,"member pane toggles");click("channel:general");}
                case 22->{screen.focus("composer");screen.setValue("draft:general","A long message "+"with wrapping and enough words to check the native scroll layout. ".repeat(40));screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));}
                case 23->{check(screen.scrollSnapshot(model.historyId()).orElseThrow().offset()>0,"new message follows scroll tail after layout");capture(client,"discord-long-message");client.getWindow().setWindowed(860,650);}
                case 24->{capture(client,"discord-compact");var b=screen.regionBounds("composer").orElseThrow();check(b.right()<=screen.width*screen.layoutScale()&&b.bottom()<=screen.height*screen.layoutScale(),"compact composer stays in bounds");client.getWindow().setWindowed(1440,907);}
                case 25->{click("server-menu");}
                case 26->{var row=screen.regionBounds("menu:server-menu:settings").orElseThrow();float scale=Math.min(screen.width*screen.layoutScale()/1000F,screen.height*screen.layoutScale()/630F);check(Math.abs(row.height()-23*scale)<1,"styled menu row height matches consumer scale");capture(client,"discord-server-menu");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_HOME,0,0));screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));}
                case 27->{check(model.settings&&model.setting.equals("Overview"),"server menu opens server settings");client.getWindow().setWindowed(860,650);}
                case 28->{capture(client,"discord-settings-compact");var b=screen.regionBounds("save").orElseThrow();check(b.right()<=screen.width*screen.layoutScale(),"compact settings save remains visible");client.getWindow().setWindowed(1440,907);}
                case 29->{model=new DiscordShowcase.Model();screen=DiscordShowcase.createScreen(null,model);client.gui.setScreen(screen);}
                case 30->{capture(client,"discord-server-final");click("home");}
                case 31->{capture(client,"discord-friends-final");screen.setValue("friend-name","sample_friend");click("add-friend");check(screen.stringValue("friend-status","").contains("sample_friend"),"Add Friend stores a local pending request");}
                case 32->{click("friend-tab:Pending");}
                case 33->{check(model.friendTab.equals("Pending"),"Friends tabs switch");screen.invalidate();}
                case 34->{click("friend-tab:Add Friend");}
                case 35->{capture(client,"discord-friends-redraw");screen.setValue("friend-name","");click("add-friend");check(screen.stringValue("friend-status","").equals("Enter a username first."),"empty friend request is rejected");client.getWindow().setWindowed(860,650);}
                case 36->{capture(client,"discord-friends-compact");client.getWindow().setWindowed(1440,907);}
                case 37->{capture(client,"discord-friends-restored");click("server");}
                default->{capture(client,"discord-server-final");System.out.println("[DiscordTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);done=true;client.stop();}
            }
            waitFor(400);
        }catch(Throwable e){e.printStackTrace();System.out.println("[DiscordTest] complete FAIL "+e);done=true;client.stop();}
    }
    private void waitFor(long ms){deadline=System.nanoTime()+ms*1_000_000;}
    private void capture(Minecraft c,String name){Screenshot.grab(c.gameDirectory,name+".png",c.gameRenderer.mainRenderTarget(),1,message->{});}
    private void check(boolean ok,String msg){checks++;failed|=!ok;System.out.println("[DiscordTest] "+msg+": "+(ok?"PASS":"FAIL"));}
    private void click(String id){var b=screen.regionBounds(id).orElseThrow();var e=new MouseButtonEvent(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseClicked(e,false);screen.mouseReleased(e);}
}
