package dev.pane.demo;

import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Focused real-client scroll layout/input checks; no world or remote chat service. */
final class ScrollEndAuditTest {
    private UiScreen screen;
    private DiscordShowcase.Model model;
    private int stage,checks,count=100,paints;
    private long start,deadline,rowUploads;
    private double savedOffset;
    private boolean failed,done,hidden,virtual;
    private UiScrollFollow follow=UiScrollFollow.WHEN_AT_END;
    void tick(Minecraft c){
        if(done)return;
        try {
            if(start==0)start=System.nanoTime();
            if(System.nanoTime()-start>150_000_000_000L)throw new IllegalStateException("scroll test timeout at "+stage);
            if(screen==null){
                if(!(c.gui.screen() instanceof TitleScreen)||c.gui.overlay()!=null)return;
                c.getWindow().setWindowed(1440,907);
                screen=new UiScreen(UiDocument.builder("Scroll end audit").shell(UiShell.NONE)
                    .theme(UiTheme.dark().withMotionScale(0)).scaling(UiScaling.fixed())
                    .page(new UiPage("main","Main","",0xFFCCAA66,this::paint))
                    .page(new UiPage("other","Other","",0xFFCCAA66,ui->ui.scroll("feed",new UiRect(24,50,500,250),6000,sc->sc.canvas().text("Other page",28,54,14,0xFFFFFFFF))))
                    .build());
                screen.scrollToEnd("feed");c.gui.setScreen(screen);waitMs(900);return;
            }
            if(System.nanoTime()<deadline||c.gui.overlay()!=null)return;
            switch(stage++){
                case 0->{check(atEnd("feed"),"request before first paint resolves against registered height");check(lastVisible("row:"+(count-1),"feed"),"last row hit geometry uses resolved offset in the same paint");paints=screen.paintCount();count+=20;screen.invalidate();screen.scrollToEnd("feed");}
                case 1->{check(atEnd("feed"),"append then end request reaches new content extent");check(screen.paintCount()==paints+1,"append-to-end requires one paint, without a retry paint");capture(c,"scroll-end-regular");screen.scrollToEnd("absent");}
                case 2->{paints=screen.paintCount();rowUploads=screen.virtualRowUploadPixels();waitMs(650);return;}
                case 3->{check(screen.paintCount()==paints&&screen.virtualRowUploadPixels()==rowUploads,"absent viewport intent schedules no idle paints or uploads");hidden=true;screen.scrollToEnd("feed");screen.invalidate();}
                case 4->{check(screen.scrollSnapshot("feed").isEmpty(),"hidden viewport is absent from layout");hidden=false;count+=10;screen.invalidate();}
                case 5->{check(atEnd("feed"),"pending end resolves when hidden viewport returns");count+=5;screen.invalidate();}
                case 6->{check(atEnd("feed"),"incoming append follows when already at end");screen.setMotionScale(1);savedOffset=snap("feed").maximumOffset()-138;wheel("feed",3);count+=10;screen.invalidate();}
                case 7->{check(Math.abs(snap("feed").offset()-savedOffset)<1&&!atEnd("feed"),"wheel away before first motion frame prevents incoming append from following");savedOffset=snap("feed").offset();count+=10;screen.invalidate();}
                case 8->{check(Math.abs(snap("feed").offset()-savedOffset)<1,"incoming append preserves reader position above end");c.getWindow().setWindowed(900,650);waitMs(650);return;}
                case 9->{check(Math.abs(snap("feed").offset()-savedOffset)<1,"resize preserves an unpinned reader's numeric offset");screen.scrollToEnd("feed");}
                case 10->{check(atEnd("feed"),"explicit end works after resize");c.getWindow().setWindowed(1440,907);waitMs(650);return;}
                case 11->{check(atEnd("feed"),"resizing a pinned viewport preserves end");count=3;screen.invalidate();}
                case 12->{check(snap("feed").maximumOffset()==0&&snap("feed").offset()==0,"filter shrink clamps to zero extent");count=100;screen.invalidate();}
                case 13->{check(atEnd("feed"),"fitting content follows when it grows beyond viewport");follow=UiScrollFollow.NONE;savedOffset=snap("feed").offset();count+=10;screen.invalidate();}
                case 14->{check(Math.abs(snap("feed").offset()-savedOffset)<1&&!atEnd("feed"),"follow NONE retains existing offset on append");follow=UiScrollFollow.WHEN_AT_END;virtual=true;count=10000;screen.scrollToEnd("rows");screen.invalidate();}
                case 15->{check(atEnd("rows")&&lastVisible("virtual:"+(count-1),"rows"),"virtual atlas draws and registers the new last row");check(screen.virtualRowRasterCount()<100,"end jump rasterizes a bounded virtual row window");count+=5;screen.invalidate();}
                case 16->{check(atEnd("rows")&&lastVisible("virtual:"+(count-1),"rows"),"virtual list follows appended rows");check(screen.scrollVirtualItemIntoView("rows",4),"explicit virtual-row reveal accepted");}
                case 17->{check(!atEnd("rows"),"virtual reveal detaches from end");savedOffset=snap("rows").offset();count+=5;screen.invalidate();}
                case 18->{check(Math.abs(snap("rows").offset()-savedOffset)<1,"virtual append preserves reader position");count=0;screen.invalidate();}
                case 19->{check(snap("rows").maximumOffset()==0&&snap("rows").offset()==0,"empty virtual list registers and resets its extent");count=10000;screen.invalidate();}
                case 20->{check(atEnd("rows"),"empty-to-populated virtual list follows end");wheel("rows",3);count=2;screen.invalidate();}
                case 21->{check(snap("rows").offset()==0&&snap("rows").targetOffset()==0,"filtering cancels wheel target outside the new virtual extent");screen.scrollTo("feed",0);screen.scrollToEnd("feed");screen.showPage("other");}
                case 22->{check(snap("feed").offset()==0,"page change cancels unresolved end commands");screen.scrollToEnd("feed");c.gui.setScreen(new TitleScreen());}
                case 23->{c.gui.setScreen(screen);}
                case 24->{check(snap("feed").offset()==0,"removal cancels pending command before screen reuse");
                    model=new DiscordShowcase.Model();for(int i=0;i<40;i++)model.current().add(DiscordShowcase.Model.msg(1,"Remote message "+i+" with some text to wrap as the window changes.","Now"));
                    screen=DiscordShowcase.createScreen(null,model);screen.scrollToEnd(model.historyId());c.gui.setScreen(screen);waitMs(700);return;}
                case 25->{check(atEnd(model.historyId()),"Gather opens with end intent on actual wrapped message layout");
                    count=model.current().size();screen.setValue("draft:"+model.key(),"Sent through Enter: "+"a long message with wrapping. ".repeat(70));screen.focus("composer");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));}
                case 26->{check(model.current().size()==count+1&&atEnd(model.historyId()),"Gather Enter send reaches newly laid-out last message");capture(c,"gather-scroll-end-send");screen.scrollTo(model.historyId(),0);}
                case 27->{model.current().add(DiscordShowcase.Model.msg(2,"Another incoming message while reading older content.","Now"));screen.invalidate();}
                case 28->{check(snap(model.historyId()).offset()==0,"Gather remote append does not yank older-message reader");capture(c,"gather-scroll-end-reading");wheel(model.historyId(),-100000);}
                case 29->{check(atEnd(model.historyId()),"scrolling back to bottom resumes follow eligibility");model.current().add(DiscordShowcase.Model.msg(2,"Incoming message after returning to the bottom.","Now"));screen.invalidate();}
                case 30->{check(atEnd(model.historyId()),"Gather follows remote append after scrolling back down");screen.scrollToEnd(model.historyId());click("channel:showcase");}
                case 31->{check(model.conversation.equals("showcase")&&snap(model.historyId()).offset()==0,"queued end for previous conversation does not affect new channel");click("channel:general");}
                case 32->{check(snap(model.historyId()).offset()==0,"explicit channel navigation cancels its older pending end intent");c.getWindow().setWindowed(860,650);screen.scrollToEnd(model.historyId());waitMs(700);return;}
                case 33->{check(atEnd(model.historyId()),"Gather resize and wrapping resolve end against new height");screen.setValue("search","zz-no-match-audit-zz");}
                case 34->{check(snap(model.historyId()).maximumOffset()==0&&snap(model.historyId()).offset()==0,"Gather search with no matches clamps its history");screen.setValue("search","");}
                case 35->{check(atEnd(model.historyId()),"Gather clearing fitting search results follows restored content");c.getWindow().setWindowed(1440,907);waitMs(700);return;}
                case 36->{check(atEnd(model.historyId()),"Gather restored window remains pinned after rewrap");paints=screen.paintCount();rowUploads=screen.virtualRowUploadPixels();waitMs(750);return;}
                default->{check(screen.paintCount()==paints&&screen.virtualRowUploadPixels()==rowUploads,"settled Gather has zero extra idle paints or virtual uploads");capture(c,"gather-scroll-end-final");
                    System.out.println("[ScrollEndTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);done=true;c.stop();return;}
            }
            waitMs(300);
        }catch(Throwable e){e.printStackTrace();System.out.println("[ScrollEndTest] complete FAIL "+e);done=true;c.stop();}
    }
    private void paint(UiContext ui){
        var g=ui.canvas();g.fill(0xFF191E22,0,0,ui.width(),ui.height());g.text("SCROLL LAYOUT CHECK",24,16,18,0xFFF0D9A0);
        if(hidden)return;var viewport=new UiRect(24,50,Math.min(700,ui.width()-48),ui.height()-110);
        if(virtual)ui.virtualList("rows",viewport,count,24,2,follow,(row,item)->{
            row.canvas().fill(item.index()%2==0?0xFF303A3F:0xFF252D32,item.bounds().x(),item.bounds().y(),item.bounds().width(),24);
            row.canvas().text("Row "+item.index(),item.bounds().x()+8,item.bounds().y()+3,13,0xFFEAE5DB);
            row.hit("virtual:"+item.index(),"noop",item.bounds().x(),item.bounds().y(),200,24);
        });
        else ui.scroll("feed",viewport,count*28,follow,sc->{for(int i=0;i<count;i++){
            float y=viewport.y()+i*28;sc.canvas().fill(i%2==0?0xFF303A3F:0xFF252D32,24,y,viewport.width(),26);
            sc.canvas().text("Message "+i,32,y+4,14,0xFFEAE5DB);sc.hit("row:"+i,"noop",24,y,200,26);
        }});
    }
    private UiScrollSnapshot snap(String id){return screen.scrollSnapshot(id).orElseThrow();}
    private boolean atEnd(String id){var s=snap(id);return Math.abs(s.offset()-s.maximumOffset())<1&&Math.abs(s.targetOffset()-s.maximumOffset())<1;}
    private boolean lastVisible(String hit,String scroll){var b=screen.regionBounds(hit);var v=snap(scroll).viewport();return b.isPresent()&&b.get().y()>=v.y()-1&&b.get().bottom()<=v.bottom()+1;}
    private void wheel(String id,double amount){var v=snap(id).viewport();screen.mouseScrolled(v.centerX()/screen.layoutScale(),v.centerY()/screen.layoutScale(),0,amount);}
    private void click(String id){var b=screen.regionBounds(id).orElseThrow();var e=new MouseButtonEvent(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseClicked(e,false);screen.mouseReleased(e);}
    private void waitMs(long ms){deadline=System.nanoTime()+ms*1_000_000L;}
    private void check(boolean ok,String message){checks++;failed|=!ok;System.out.println("[ScrollEndTest] "+message+": "+(ok?"PASS":"FAIL"));}
    private void capture(Minecraft c,String name){Screenshot.grab(c.gameDirectory,name+".png",c.gameRenderer.mainRenderTarget(),1,m->{});}
}
