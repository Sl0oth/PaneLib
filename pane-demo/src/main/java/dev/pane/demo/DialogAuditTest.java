package dev.pane.demo;

import dev.pane.ui.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Isolated responsive-dialog checks; no world or consumer actions. */
final class DialogAuditTest {
    private UiScreen screen;
    private int tick, checks, originalWidth, originalHeight;
    private boolean failed;
    private long beforeScroll;
    private int beforePaint;
    private final List<String> actions=new ArrayList<>();
    private static final UiDialog DELETE=new UiDialog("Delete script?","notes.py will be deleted. This cannot be undone.",
        "Delete","delete","Keep","keep",UiDialogStyle.builder().confirmBackground(0xFF8C3A33).confirmText(0xFFFFFFFF).build());
    private UiScreen create() {
        return new UiScreen(UiDocument.builder("Dialog audit").scaling(UiScaling.fixed())
            .page(new UiPage("main","Main","",0xFF55AAFF,ui -> {
                ui.canvas().fill(ui.theme().background(),0,0,ui.width(),ui.height());
                ui.dialogButton("open","Open",20,20,90,28,UiContext.ButtonStyle.PRIMARY,UiContext.Shape.ROUNDED,
                    new UiDialog("Review this localized confirmation before continuing — ".repeat(4),
                        "Please review all details. "+"LongUnbreakableToken".repeat(20)+"\n"+"More detailed explanation.\n".repeat(50),
                        "Änderungen endgültig bestätigen und dauerhaft übernehmen", "confirm",
                        "Abbrechen und zur vorherigen Ansicht zurückkehren", "cancel"));
            })).build(),NativeOverlayRenderer.NONE,event -> { if(event.type()==UiEventType.ACTION) actions.add(event.key()); });
    }
    void tick(Minecraft client) {
        try {
            if(screen==null) {
                if(!(client.gui.screen() instanceof TitleScreen))return;
                originalWidth=client.getWindow().getScreenWidth();originalHeight=client.getWindow().getScreenHeight();
                client.getWindow().setWindowed(320,260);screen=create();client.gui.setScreen(screen);return;
            }
            if(screen.paintCount()==0)return;
            tick++;
            if(tick==10)click("open");
            if(tick==15) {
                var body=screen.regionBounds("dialog-body").orElseThrow();
                var cancel=screen.regionBounds("dialog-cancel").orElseThrow();
                var confirm=screen.regionBounds("dialog-confirm").orElseThrow();
                check(inside(body,cancel)&&inside(body,confirm),"localized actions stay inside dialog");
                check(cancel.right()<=confirm.x()||cancel.bottom()<=confirm.y(),"action buttons do not overlap");
                check(body.x()>=0&&body.y()>=0&&body.right()<=screen.rasterQuality().layoutWidth()&&body.bottom()<=screen.rasterQuality().layoutHeight(),"dialog stays inside viewport");
                Screenshot.grab(client.gameDirectory,"audit-responsive-dialog.png",client.gameRenderer.mainRenderTarget(),1,m->{});
                check(screen.focusedId().orElse("").equals("dialog-cancel"),"dialog defaults keyboard focus to Cancel");
                beforePaint=screen.paintCount();beforeScroll=screen.popupUploadPixels();
                screen.mouseScrolled(body.centerX()/screen.layoutScale(),body.centerY()/screen.layoutScale(),0,-3);
            }
            if(tick==20) {
                check(screen.popupUploadPixels()>beforeScroll,"wheel reveals overflowing dialog copy");
                check(screen.dialogScrollSnapshot().orElseThrow().offset()>0,"dialog scroll offset advances");
                check(screen.paintCount()==beforePaint,"scrolling dialog copy does not repaint page");
                key(GLFW.GLFW_KEY_END,0);
                var scroll=screen.dialogScrollSnapshot().orElseThrow();
                check(scroll.offset()==scroll.maximumOffset(),"End reaches all dialog copy");
                key(GLFW.GLFW_KEY_HOME,0);check(screen.dialogScrollSnapshot().orElseThrow().offset()==0,"Home returns to title");
                key(GLFW.GLFW_KEY_PAGE_DOWN,0);check(screen.dialogScrollSnapshot().orElseThrow().offset()>0,"PageDown browses copy");
                key(GLFW.GLFW_KEY_TAB,0);check(screen.focusedId().orElse("").equals("dialog-confirm"),"Tab follows visible action order");
            }
            if(tick==25) {
                Screenshot.grab(client.gameDirectory,"audit-dialog-focus.png",client.gameRenderer.mainRenderTarget(),1,m->{});
                click("dialog-body");check(screen.openDialogId().isPresent(),"copy area clicks keep dialog open");
                key(GLFW.GLFW_KEY_TAB,0);check(screen.focusedId().orElse("").equals("dialog-cancel"),"Tab wraps to Cancel");
                client.getWindow().setWindowed(240,180);
            }
            if(tick==30) {
                check(screen.focusedId().orElse("").equals("dialog-cancel"),"resize preserves dialog keyboard focus");
                var body=screen.regionBounds("dialog-body").orElseThrow();
                check(inside(body,screen.regionBounds("dialog-confirm").orElseThrow()),"narrow resize keeps actions inside card");
                key(GLFW.GLFW_KEY_ESCAPE,0);check(Collections.frequency(actions,"cancel")==1,"Escape cancels once");
                click("open");
            }
            if(tick==35) {
                check(screen.dialogScrollSnapshot().orElseThrow().offset()==0,"reopening resets dialog scroll");
                var confirm=screen.regionBounds("dialog-confirm").orElseThrow();
                click("dialog-confirm");clickAt(confirm.centerX(),confirm.centerY());
                check(Collections.frequency(actions,"confirm")==1,"Confirm fires once before repaint");
            }
            if(tick==40) {
                check(screen.dialogScrollSnapshot().isEmpty()&&screen.popupRetainedPixels()==0,"closing clears dialog layout and surface");
                click("open");
            }
            if(tick==42||tick==60) {
                var b=screen.regionBounds("dialog-cancel").orElseThrow();screen.mouseMoved(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale());
            }
            if(tick==45) { key(GLFW.GLFW_KEY_ESCAPE,0);key(GLFW.GLFW_KEY_ENTER,0); }
            if(tick==50) {
                var b=screen.regionBounds("dialog-cancel").orElseThrow();
                var e=eventAt(b.centerX(),b.centerY());screen.mouseClicked(e,false);screen.mouseReleased(e);
                check(screen.openDialogId().isEmpty(),"same-frame reopen cache hit restores pointer actions");
            }
            if(tick==55)click("open");
            if(tick==65) {
                beforeScroll=screen.popupUploadPixels();beforePaint=screen.paintCount();
                var b=screen.regionBounds("dialog-cancel").orElseThrow();screen.mouseClicked(eventAt(b.centerX(),b.centerY()),false);
            }
            if(tick==70) {
                check(screen.popupUploadPixels()>beforeScroll,"pressed visual state invalidates popup cache");
                check(screen.paintCount()==beforePaint,"dialog press does not repaint background");
                var b=screen.dialogScrollSnapshot().orElseThrow().viewport();screen.mouseMoved(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale());
            }
            if(tick==75) {
                beforeScroll=screen.popupUploadPixels();var b=screen.dialogScrollSnapshot().orElseThrow().viewport();
                screen.mouseReleased(eventAt(b.centerX(),b.centerY()));
            }
            if(tick==80) {
                check(screen.popupUploadPixels()>beforeScroll,"release outside clears cached pressed appearance");
                check(screen.openDialogId().isPresent(),"dragging off an action cancels activation");
                key(GLFW.GLFW_KEY_ESCAPE,0);
                // A dialog opened from code, as for a delete chosen from a menu.
                screen.showDialog("shown",DELETE);
            }
            if(tick==85) {
                check(screen.openDialogId().orElse("").equals("shown"),"showDialog opens without a trigger");
                check(screen.focusedId().orElse("").equals("dialog-cancel"),"shown dialog focuses Cancel");
                screen.showDialog("second",DELETE);
                check(screen.openDialogId().orElse("").equals("shown"),"showDialog leaves an open dialog alone");
                key(GLFW.GLFW_KEY_ESCAPE,0);
                check(Collections.frequency(actions,"keep")==1&&screen.openDialogId().isEmpty(),"Escape cancels a shown dialog once");
                screen.showDialog("shown",DELETE);
            }
            if(tick==90) {
                click("dialog-confirm");
                check(Collections.frequency(actions,"delete")==1&&screen.openDialogId().isEmpty(),"shown dialog confirms once and closes");
                Thread worker=new Thread(()->screen.showDialog("worker",DELETE));worker.start();
            }
            if(tick==95) {
                check(screen.openDialogId().orElse("").equals("worker"),"showDialog from a worker thread opens on the client thread");
                key(GLFW.GLFW_KEY_ESCAPE,0);
                client.getWindow().setWindowed(originalWidth,originalHeight);
                System.out.println("[DialogTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);client.stop();
            }
        }catch(Throwable error){System.out.println("[DialogTest] complete FAIL "+error);error.printStackTrace();client.getWindow().setWindowed(originalWidth,originalHeight);client.stop();}
    }
    private boolean inside(UiRect outer,UiRect inner){return inner.x()>=outer.x()&&inner.y()>=outer.y()&&inner.right()<=outer.right()&&inner.bottom()<=outer.bottom();}
    private void click(String id){var b=screen.regionBounds(id).orElseThrow();var e=new MouseButtonEvent(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseMoved(e.x(),e.y());screen.mouseClicked(e,false);screen.mouseReleased(e);}
    private MouseButtonEvent eventAt(float x,float y){return new MouseButtonEvent(x/screen.layoutScale(),y/screen.layoutScale(),new MouseButtonInfo(0,0));}
    private void clickAt(float x,float y){var e=new MouseButtonEvent(x/screen.layoutScale(),y/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseMoved(e.x(),e.y());screen.mouseClicked(e,false);screen.mouseReleased(e);}
    private void key(int key,int mods){screen.keyPressed(new KeyEvent(key,0,mods));}
    private void check(boolean ok,String label){checks++;failed|=!ok;System.out.println("[DialogTest] "+label+": "+(ok?"PASS":"FAIL"));}
}
