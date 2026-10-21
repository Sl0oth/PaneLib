package dev.pane.demo;

import dev.pane.ui.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Isolated real-client checks for the independent review findings. */
final class ReviewFollowupTest {
    private UiScreen screen;
    private int tick, checks;
    private boolean failed;
    private boolean injectRawText;
    private boolean overlayPointerBlocked, animateBackdrop, finished;
    private long dialogUploads, blurAllocations, idleDialogOpenedAt, idleDialogMeasuredAt, animationStartedAt, animationMeasuredAt;
    private int dialogPaints;
    private final Set<String> rejectedIds = new HashSet<>();
    private String previousClipboard;
    private final List<String> actions = new ArrayList<>(), values = new ArrayList<>();
    private UiScreen create() {
        var doc = UiDocument.builder("Review fixes").scaling(UiScaling.fixed()).scrollSettings(UiScrollSettings.immediate())
            .page(new UiPage("main","Main","",0xFF58A6FF,ui -> {
                ui.canvas().fill(ui.theme().background(),0,0,ui.width(),ui.height());
                float motion=ui.animate("background-motion",animateBackdrop ? 1 : 0,animateBackdrop ? 5000 : 0,UiEasing.LINEAR,new UiRect(580,350,50,20));
                ui.canvas().fill(0xFF4488AA,580,350,10+motion*40,20);
                for (String id : List.of("dialog-confirm", "dialog-body", "menu:choice:option-0", "toast-dismiss:notice")) {
                    try { ui.hit(id,"collision",580,20,20,20); }
                    catch (IllegalArgumentException expected) { rejectedIds.add(id); }
                }
                ui.textArea("notes","notes","Notes",20,20,260,115,"Alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima mike november oscar papa quebec romeo sierra tango uniform victor whiskey xray yankee zulu",4000);
                if (injectRawText) { ui.setValue("notes", "a\r\nb"); injectRawText=false; }
                ui.dialogButton("confirm-test","Confirm",20,250,100,28,UiContext.ButtonStyle.SECONDARY,UiContext.Shape.ROUNDED,
                    new UiDialog("Confirm action","Clicking this body must not cancel.","Yes","audit-confirm","No","audit-cancel"));
                ui.dropdown("key-choice","a=b",20,300,150,28,"first",List.of(new UiSelectOption("first","First"),new UiSelectOption("value=two","Second")));
                ui.passwordField("secret","secret","Password",20,205,200,"secret-value",100);
                ui.dropdown("choice","choice",20,160,150,28,"beta",List.of(new UiSelectOption("alpha","Alpha"),new UiSelectOption("beta","Beta"),new UiSelectOption("gamma","Gamma")));
                ui.withTranslation(320,20, moved -> moved.scroll("outer",new UiRect(0,0,240,120),650,outer -> {
                    for(int i=0;i<12;i++) outer.button("b"+i,"Button "+i,"b"+i,5,i*32+5,100,25,UiContext.ButtonStyle.SECONDARY,UiContext.Shape.ROUNDED);
                    outer.scroll("inner",new UiRect(120,250,110,80),250,inner -> {
                        inner.textField("deep","scroll:outer","Deep",125,430,95,"",60);
                        inner.button("deep-button","Deep","deep-button",125,465,95,25,UiContext.ButtonStyle.PRIMARY,UiContext.Shape.ROUNDED);
                    });
                    outer.button("unreachable","Hidden","unreachable",5,800,100,25,UiContext.ButtonStyle.PRIMARY,UiContext.Shape.ROUNDED);
                }));
                ui.virtualList("password-list",new UiRect(320,200,240,65),10,32,2,(row,item) ->
                    row.passwordField("virtual-secret-"+item.index(),"virtual-secret-"+item.index(),"Secret",
                        item.bounds().x()+5,item.bounds().y()+2,200,"row-secret",100));
            })).build();
        return new UiScreen(doc,overlay -> {
            overlayPointerBlocked=overlay.pointerBlocked();
            overlay.graphics().fill(screen.width/2-30,screen.height/2-30,screen.width/2+30,screen.height/2+30,0xFF00FF00);
        },event -> {
            if(event.type()==UiEventType.ACTION) actions.add(event.key());
            if(event.type()==UiEventType.VALUE) values.add(event.key());
        });
    }
    void tick(Minecraft client) {
        if(finished)return;
        try {
            if(screen==null) {
                if(!(client.gui.screen() instanceof TitleScreen)) return;
                previousClipboard=client.keyboardHandler.getClipboard();screen=create();screen.scrollTo("outer",-10);client.gui.setScreen(screen);return;
            }
            if(screen.paintCount()==0)return;
            tick++;
            if(tick==51 && System.nanoTime()-animationStartedAt<350_000_000L){tick--;return;}
            if(tick==56 && System.nanoTime()-animationMeasuredAt<200_000_000L){tick--;return;}
            if(tick==15) {
                check(rejectedIds.size()==4,"consumer modal/menu/toast IDs rejected with diagnostics");
                check(screen.regionBounds("dialog-confirm").isEmpty(),"rejected ID leaves no consumer pointer target");
                check(screen.scrollSnapshot("outer").orElseThrow().offset()==0,"prepaint scrollTo clamps negative offset");
                check(screen.regionBounds("b11").isEmpty(),"offscreen control has no pointer target");
                screen.focus("b0");
                for(int i=1;i<12;i++) { key(GLFW.GLFW_KEY_TAB,0);check(screen.focusedId().orElse("").equals("b"+i),"Tab reaches b"+i); }
                key(GLFW.GLFW_KEY_ENTER,0);check(actions.contains("b11"),"revealed button activates before repaint");
                key(GLFW.GLFW_KEY_TAB,0);check(screen.focusedId().orElse("").equals("deep"),"Tab enters nested offscreen input");
                type("consumer");check(screen.stringValue("scroll:outer","").equals("consumer"),"typing before reveal paint uses consumer key independently");
                key(GLFW.GLFW_KEY_TAB,0);key(GLFW.GLFW_KEY_ENTER,0);
                check(actions.contains("deep-button"),"nested revealed button activates before repaint");
                check(!screen.focus("unreachable"),"outside content extent cannot focus");
            }
            if(tick==20) {
                var b=screen.regionBounds("deep-button").orElseThrow();
                var outer=screen.scrollViewport("outer").orElseThrow();
                check(b.y()>=outer.y() && b.bottom()<=outer.bottom(),"nested reveal exposes whole target");
                key(GLFW.GLFW_KEY_TAB,GLFW.GLFW_MOD_SHIFT);type("!");
                check(screen.stringValue("scroll:outer","").equals("consumer!"),"reverse Tab reaches text input");
                int count=values.size();screen.scrollTo("outer",9999);
                check(screen.scrollSnapshot("outer").orElseThrow().offset()==530,"scrollTo clamps maximum");
                check(values.size()==count,"scrollTo emits no consumer value event");
                screen.scrollTo("outer",0);screen.scrollTo("inner",0);
            }
            if(tick==25) {
                var r=screen.scrollViewport("outer").orElseThrow();int count=values.size();
                screen.mouseScrolled((r.x()+10)/screen.layoutScale(),(r.y()+10)/screen.layoutScale(),0,-1);
                check(values.size()==count && screen.stringValue("scroll:outer","").equals("consumer!"),"wheel emits no values and preserves colliding consumer key");
                screen.setValue("scroll:outer",123D);
                check(screen.scrollSnapshot("outer").orElseThrow().offset()==46,"consumer scroll key cannot move internal viewport");
                screen.pushFocusScope("guard",List.of("choice"),"choice");
                double old=screen.scrollSnapshot("outer").orElseThrow().offset();
                check(!screen.focus("b11") && screen.scrollSnapshot("outer").orElseThrow().offset()==old,"modal rejects background reveal");
                screen.popFocusScope("guard");
                click("choice");
            }
            if(tick==30) {
                key(GLFW.GLFW_KEY_DOWN,0);key(GLFW.GLFW_KEY_ENTER,0);
                check(screen.stringValue("choice","").equals("gamma"),"mouse-open dropdown Down starts from current value");
                screen.focus("choice");key(GLFW.GLFW_KEY_ENTER,0);
            }
            if(tick==35) {
                int count=values.size();key(GLFW.GLFW_KEY_ENTER,0);
                check(values.size()==count,"accepting current dropdown choice emits no change");
                screen.focus("notes");key(GLFW.GLFW_KEY_HOME,GLFW.GLFW_MOD_CONTROL);key(GLFW.GLFW_KEY_END,0);
                Screenshot.grab(client.gameDirectory,"review-word-wrap.png",client.gameRenderer.mainRenderTarget(),1,m->{});
                key(GLFW.GLFW_KEY_HOME,GLFW.GLFW_MOD_SHIFT);type("REPLACED ");
                check(screen.stringValue("notes","").startsWith("REPLACED "),"Shift Home from visual End selects complete first line");
            }
            if(tick==40) {
                screen.setValue("notes","a\r\nb");
            }
            if(tick==45) {
                check(screen.stringValue("notes", "").equals("a\nb"),"seeded CRLF matches stored caret text");
                screen.focus("notes");key(GLFW.GLFW_KEY_HOME,GLFW.GLFW_MOD_CONTROL);
                key(GLFW.GLFW_KEY_RIGHT,0);key(GLFW.GLFW_KEY_RIGHT,0);type("X");
                check(screen.stringValue("notes", "").equals("a\nXb"),"typing after normalized newline uses correct offset");
                key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);key(GLFW.GLFW_KEY_LEFT,0);type("L");
                check(screen.stringValue("notes", "").equals("La\nXb"),"Left collapses selection to start");
                key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);key(GLFW.GLFW_KEY_RIGHT,0);type("R");
                check(screen.stringValue("notes", "").equals("La\nXbR"),"Right collapses selection to end");
                check(screen.focus("virtual-secret-0"),"virtual-row password can focus");
                key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);
                client.keyboardHandler.setClipboard("virtual-sentinel");
                key(GLFW.GLFW_KEY_C,GLFW.GLFW_MOD_CONTROL);key(GLFW.GLFW_KEY_X,GLFW.GLFW_MOD_CONTROL);
                check(client.keyboardHandler.getClipboard().equals("virtual-sentinel") && screen.stringValue("virtual-secret-0", "").equals("row-secret"),"virtual-row password keeps masked clipboard policy");
                screen.focus("secret");key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);
                client.keyboardHandler.setClipboard("pane-audit-sentinel");
                key(GLFW.GLFW_KEY_C,GLFW.GLFW_MOD_CONTROL);
                check(client.keyboardHandler.getClipboard().equals("pane-audit-sentinel"),"password copy preserves clipboard");
                key(GLFW.GLFW_KEY_X,GLFW.GLFW_MOD_CONTROL);
                check(client.keyboardHandler.getClipboard().equals("pane-audit-sentinel") && screen.stringValue("secret", "").equals("secret-value"),"password cut preserves clipboard and value");
                key(GLFW.GLFW_KEY_V,GLFW.GLFW_MOD_CONTROL);
                check(screen.stringValue("secret", "").equals("pane-audit-sentinel"),"password paste remains available");
            }
            if(tick==50) {
                animateBackdrop=true;
                animationStartedAt=System.nanoTime();
                click("confirm-test");
            }
            if(tick==55) { dialogUploads=screen.popupUploadPixels(); dialogPaints=screen.paintCount(); blurAllocations=screen.dialogBlurAllocations(); animationMeasuredAt=System.nanoTime(); }
            if(tick==57) {
                check(screen.paintCount()>dialogPaints,"background animation repaints behind dialog");
                check(screen.popupUploadPixels()==dialogUploads,"unchanged dialog uploads no pixels during background animation");
                check(screen.dialogBlurAllocations()==blurAllocations,"partial animation blur reuses its buffers");
                check(screen.dialogBlurRetainedPixels()<(long)screen.rasterQuality().pixelWidth()*screen.rasterQuality().pixelHeight(),"partial animation blur stores less than one full-screen image");
                Screenshot.grab(client.gameDirectory,"audit-dialog-overlay.png",client.gameRenderer.mainRenderTarget(),1,m->{});
                check(overlayPointerBlocked,"native overlay knows dialog blocks pointer");
            }
            if(tick==58) {
                animateBackdrop=false;
                click("dialog-body");
                check(screen.openDialogId().isPresent() && !actions.contains("audit-cancel"),"clicking dialog body keeps it open");
                var confirm=screen.regionBounds("dialog-confirm").orElseThrow();
                click("dialog-confirm");
                clickAt(confirm.centerX(),confirm.centerY());
                check(Collections.frequency(actions,"audit-confirm")==1,"confirm fires once across clicks before repaint");
                check(screen.regionBounds("dialog-backdrop").isEmpty(),"closing dialog clears stale modal targets");
                check(screen.dialogBlurRetainedPixels()==0,"closing dialog releases blur buffers");
            }
            if(tick==60) { click("key-choice"); }
            if(tick==65) {
                key(GLFW.GLFW_KEY_DOWN,0);key(GLFW.GLFW_KEY_ENTER,0);
                check(screen.stringValue("a=b","").equals("value=two") && screen.stringValue("a", "missing").equals("missing"),"dropdown preserves delimiter-bearing key and value");
            }
            if(tick==70) {
                screen.clearFocus();screen.setValue("notes","a\r\nb");screen.focus("notes");type("Z");
                check(screen.stringValue("notes","").equals("a\nbZ"),"CRLF setValue then typing before paint uses canonical text");
                key(GLFW.GLFW_KEY_Z,GLFW.GLFW_MOD_CONTROL);
                check(screen.stringValue("notes","").equals("a\nb"),"undo never restores raw line endings");
                screen.setValue("notes","a\r\nb");key(GLFW.GLFW_KEY_HOME,GLFW.GLFW_MOD_CONTROL);
                key(GLFW.GLFW_KEY_RIGHT,0);key(GLFW.GLFW_KEY_RIGHT,0);type("X");
                check(screen.stringValue("notes","").equals("a\nXb"),"same-batch navigation after CRLF replacement uses canonical indices");
                injectRawText=true;screen.clearFocus();screen.invalidate();
            }
            if(tick==75) {
                check(screen.stringValue("notes","").equals("a\r\nb"),"fixture injected raw text after field paint");
                screen.focus("notes");type("Z");
                check(screen.stringValue("notes","").equals("a\nbZ"),"edit boundary normalizes painter-injected raw text without setter help");
                key(GLFW.GLFW_KEY_Z,GLFW.GLFW_MOD_CONTROL);
                check(screen.stringValue("notes","").equals("a\nb"),"painter-injected raw text undo stays canonical");
                client.keyboardHandler.setClipboard(previousClipboard);
            }
            if(tick==78) {
                String longText="word 😀 café\n".repeat(250);
                screen.setValue("notes",longText);screen.focus("notes");
                key(GLFW.GLFW_KEY_END,GLFW.GLFW_MOD_CONTROL);type("Z");
                check(screen.stringValue("notes","").equals(longText+"Z"),"long shaped textarea inserts at document end");
                key(GLFW.GLFW_KEY_Z,GLFW.GLFW_MOD_CONTROL);
                check(screen.stringValue("notes","").equals(longText),"long shaped textarea undo preserves Unicode and newlines");
            }
            if(tick==80) { click("confirm-test"); idleDialogOpenedAt=System.nanoTime(); }
            // Client ticks can catch up in a batch; they are not a wall-clock idle interval.
            if(tick>=85 && idleDialogMeasuredAt==0 && System.nanoTime()-idleDialogOpenedAt>=750_000_000L) {
                dialogUploads=screen.popupUploadPixels(); dialogPaints=screen.paintCount();
                idleDialogMeasuredAt=System.nanoTime();
            }
            if(idleDialogMeasuredAt!=0 && System.nanoTime()-idleDialogMeasuredAt>=750_000_000L) {
                check(screen.dialogBlurRetainedPixels()==0,"idle dialog releases temporary blur storage");
                check(screen.popupUploadPixels()==dialogUploads,"idle scratch release uploads no popup pixels");
                check(screen.paintCount()==dialogPaints,"idle scratch release causes no base repaint");
                finished=true;
                System.out.println("[ReviewFix] complete "+(failed?"FAIL":"PASS")+" checks="+checks);client.stop();
            }
        } catch(Throwable e) {if(previousClipboard!=null)client.keyboardHandler.setClipboard(previousClipboard);failed=true;System.out.println("[ReviewFix] complete FAIL "+e);e.printStackTrace();client.stop();}
    }
    private void key(int key,int mods){screen.keyPressed(new KeyEvent(key,0,mods));}
    private void type(String text){text.codePoints().forEach(cp->screen.charTyped(new CharacterEvent(cp)));}
    private void click(String id){var b=screen.regionBounds(id).orElseThrow();var e=new MouseButtonEvent(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseMoved(e.x(),e.y());screen.mouseClicked(e,false);screen.mouseReleased(e);}
    private void clickAt(float x,float y){var e=new MouseButtonEvent(x/screen.layoutScale(),y/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseMoved(e.x(),e.y());screen.mouseClicked(e,false);screen.mouseReleased(e);}
    private void check(boolean ok,String label){checks++;failed|=!ok;System.out.println("[ReviewFix] "+label+": "+(ok?"PASS":"FAIL"));}
}
