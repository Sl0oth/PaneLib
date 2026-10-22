package dev.pane.demo;
import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Real-client consumer checks; every action waits for rebuilt input regions. */
final class GmailAuditTest {
    private UiScreen screen;private GmailShowcase.Model model;private int stage,checks,paints;private long deadline;private boolean failed,done;
    void tick(Minecraft client){
        if(done)return;
        try{
            if(screen==null){if(!(client.gui.screen() instanceof TitleScreen)||client.gui.overlay()!=null)return;
                client.getWindow().setWindowed(1440,900);model=new GmailShowcase.Model();screen=GmailShowcase.createScreen(client.gui.screen(),model);client.gui.setScreen(screen);waitFor(1500);return;}
            if(System.nanoTime()<deadline||client.gui.overlay()!=null)return;
            if(Boolean.getBoolean("pane.demo.gmailCapture")){
                if(stage++==0){
                    capture(client,"pane-mail-inbox");
                    System.out.println("[PaneMailCapture] "+screen.rasterQuality());
                    waitFor(700);return;
                }
                System.out.println("[PaneMailCapture] complete; screenshot only, no functional checks run");
                done=true;client.stop();return;
            }
            switch(stage++){
                case 0->{capture(client,"gmail-inbox");check(screen.regionBounds("open:0").isPresent(),"inbox row has real hit target");paints=screen.paintCount();waitFor(900);}
                case 1->{check(screen.paintCount()==paints,"idle inbox has zero repaints");click("star:0");check(model.mail.get(0).star,"star toggles");}
                case 2->{click("folder:Starred");}
                case 3->{check(model.visible("").size()==3,"Starred shows three messages");click("folder:Inbox");}
                case 4->{screen.setValue("query","Maya");}
                case 5->{check(screen.regionBounds("open:2").isPresent()&&screen.regionBounds("open:0").isEmpty(),"search filters real rows");capture(client,"gmail-search");screen.setValue("query","");}
                case 6->{click("open:0");check(!model.mail.get(0).unread,"opening marks message read");}
                case 7->{capture(client,"gmail-message");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0));check(model.opened==-1,"Escape returns to inbox");}
                case 8->{click("select:1");}
                case 9->{click("archive");check(model.mail.get(1).archived,"selected message archives locally");}
                case 10->{check(screen.focus("compose"),"custom button accepts keyboard focus");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));check(model.compose,"Enter activates custom-painted composer button");}
                case 11->{check(screen.buttonState("close-compose").orElseThrow().label().equals("Close composer"),"custom button exposes descriptive label");screen.setValue("to","alex@example.com");screen.setValue("subject","A native inbox in Minecraft");screen.setValue("body","Hey Alex,\n\nThis is rendered entirely with PaneLib.\nSearch, folders, message rows and composing all work.\n\nSl0oth");}
                case 12->{capture(client,"gmail-compose");click("subject");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_END,0,0));screen.charTyped(new CharacterEvent(33));check(screen.stringValue("subject","").endsWith("!"),"styled composer accepts native text input");click("send");check(model.mail.getLast().sent&&!model.compose,"send saves local sample to Sent");}
                case 13->{click("folder:Sent");}
                case 14->{check(screen.regionBounds("open:21").isPresent(),"Sent contains composed message");click("folder:Inbox");screen.setValue("notice","");client.getWindow().setWindowed(860,650);}
                case 15->{capture(client,"gmail-compact");var b=screen.regionBounds("search").orElseThrow();check(b.right()<=screen.width*screen.layoutScale(),"compact search stays on screen");client.getWindow().setWindowed(1440,900);}
                case 16->{click("tab:Promotions");}
                case 17->{check(screen.regionBounds("open:18").isPresent()&&screen.regionBounds("open:0").isEmpty(),"Promotions changes rows");click("tab:Primary");}
                case 18->{client.getWindow().setWindowed(860,450);}
                case 19->{screen.scrollTo("messages",500);waitFor(400);return;}
                case 20->{check(screen.scrollSnapshot("messages").orElseThrow().offset()>0,"mail list scrolls");model=new GmailShowcase.Model();screen=GmailShowcase.createScreen(null,model);client.gui.setScreen(screen);client.getWindow().setWindowed(1440,900);}
                case 21->{
                    capture(client,"gmail-inbox-final");
                    var doc=UiDocument.builder("Custom button regression").shell(UiShell.NONE)
                        .page(new UiPage("test","Test","",0xFF5588CC,ui->{
                            ui.paintedButton("enabled","Enabled action","activate",20,20,140,40,6,ui.booleanValue("enabled",true),
                                (g,state)->g.roundRect(state.hovered()?0xFF557799:0xFF335577,20,20,140,40,6));
                            ui.paintedButton("disabled","Disabled action","forbidden",20,80,140,40,6,false,
                                (g,state)->g.roundRect(0xFF335577,20,80,140,40,6));
                        })).build();
                    screen=new UiScreen(doc,NativeOverlayRenderer.NONE,(UiEventListener)(type,key,value)->{
                        if(type.equals("action"))screen.setValue("lastAction",key);
                    },UiScreenOptions.builder().backdrop(UiBackdrop.NONE).build());
                    client.gui.setScreen(screen);
                }
                case 22->{
                    check(screen.regionBounds("disabled").isEmpty()&&!screen.focus("disabled"),"disabled custom button has no pointer or keyboard target");
                    check(!screen.buttonState("disabled").orElseThrow().enabled(),"disabled semantic state retained");
                    check(screen.focus("enabled"),"enabled custom button accepts focus");
                    screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE,0,0));
                    check(screen.stringValue("lastAction","").equals("activate"),"Space activates custom button");
                    screen.setValue("enabled",false);
                }
                case 23->{
                    check(screen.regionBounds("enabled").isEmpty()&&!screen.focus("enabled"),"disabling removes previously active target");
                    screen.setValue("lastAction","");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));
                    check(screen.stringValue("lastAction","").isEmpty(),"Enter cannot activate newly disabled control");

                }
                case 24->{paints=screen.paintCount();}
                case 25->{check(screen.paintCount()==paints,"idle custom buttons have zero repaints");}
                default->{System.out.println("[GmailTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);done=true;if(!Boolean.getBoolean("pane.demo.gmailKeepOpen"))client.stop();}
            }
            if(stage!=2)waitFor(350);
        }catch(Throwable e){e.printStackTrace();System.out.println("[GmailTest] complete FAIL "+e);done=true;client.stop();}
    }
    private void waitFor(long ms){deadline=System.nanoTime()+ms*1_000_000;}
    private void capture(Minecraft c,String name){Screenshot.grab(c.gameDirectory,name+".png",c.gameRenderer.mainRenderTarget(),1,message->{});}
    private void check(boolean ok,String msg){checks++;failed|=!ok;System.out.println("[GmailTest] "+msg+": "+(ok?"PASS":"FAIL"));}
    private void click(String id){var b=screen.regionBounds(id).orElseThrow();var e=new MouseButtonEvent(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseClicked(e,false);screen.mouseReleased(e);}
}
