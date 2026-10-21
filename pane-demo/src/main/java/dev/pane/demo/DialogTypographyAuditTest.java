package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Isolated custom-font dialog layout/input/cache checks. Does not load a world or remote service. */
final class DialogTypographyAuditTest {
    private static final String FAMILY="dialog-audit-bundled";
    private final List<String> actions=new ArrayList<>();
    private UiScreen screen;
    private int stage,checks,paints;
    private long started,deadline,layouts,uploads,allocations;
    private float contentHeight,buttonHeight;
    private boolean failed,done,large,overrides,shortCopy,legacy;
    private UiDialogStyle style(){
        if(legacy)return null;
        return UiDialogStyle.builder().fontFamily(FAMILY).titleSize(large?38:28).messageSize(large?24:18).buttonSize(large?22:16)
            .titleLineSpacing(5).messageLineSpacing(6).maxWidth(440).padding(20)
            .titleFontFamily(overrides?UiCanvas.SERIF:null).messageFontFamily(overrides?UiCanvas.MONO:null).buttonFontFamily(overrides?UiCanvas.SANS:null)
            .titleFontStyle(Font.BOLD|Font.ITALIC).messageFontStyle(Font.PLAIN).buttonFontStyle(Font.BOLD)
            .surface(0xFF26382F).border(0xFF93B087).titleColor(0xFFF6E3AA).messageColor(0xFFE5E9DA)
            .confirmBackground(0xFFF1D080).confirmText(0xFF18221B).radius(3).build();
    }
    private UiScreen create(){return new UiScreen(UiDocument.builder("Dialog typography audit").shell(UiShell.NONE).scaling(UiScaling.fixed())
        .renderSettings(UiRenderSettings.typography()).tooltipDelayMillis(60000)
        .page(new UiPage("main","Main","",0xFFDABB75,ui->{
            ui.canvas().fill(0xFF101A14,0,0,ui.width(),ui.height());ui.canvas().text("CUSTOM FONT DIALOG TEST",24,20,20,0xFFF6E3AA);
            ui.dialogButton("open","Review expedition",24,70,220,42,UiButtonStyle.builder().textSize(16).build(),
                new UiDialog("Review expedition details",shortCopy?"Ready to continue?":"AVW survey notes: narrow paths, wide skies, and 0123456789. ".repeat(30)+"\nFinal checkpoint.",
                    legacy?"Confirm":"Änderungen vollständig bestätigen","confirm",legacy?"Cancel":"Return to expedition planning","cancel",style()));
        })).build(),NativeOverlayRenderer.NONE,event->{if(event.type()==UiEventType.ACTION)actions.add(event.key());});}
    void tick(Minecraft c){
        if(done)return;
        try {
            if(started==0)started=System.nanoTime();
            if(System.nanoTime()-started>150_000_000_000L)throw new IllegalStateException("timeout at stage "+stage);
            if(screen==null){if(!(c.gui.screen() instanceof TitleScreen)||c.gui.overlay()!=null)return;
                c.getWindow().setWindowed(1000,700);screen=create();c.gui.setScreen(screen);pointer(c,950,30);waitMs(1000);return;}
            if(System.nanoTime()<deadline||c.gui.overlay()!=null)return;
            switch(stage++) {
                case 0->{check(c.level==null&&c.player==null,"font fixture uses no world/player");screen.focus("open");key(GLFW.GLFW_KEY_ENTER);}
                case 1->{check(screen.openDialogId().isPresent()&&screen.focusedId().orElse("").equals("dialog-cancel"),"custom typography dialog opens with Cancel focused");
                    bounded();contentHeight=snap().contentHeight();buttonHeight=box("dialog-confirm").height();layouts=screen.dialogLayoutCount();
                    capture(c,"dialog-font-before-registration");
                    check(UiCanvas.registerFont(FAMILY,DialogTypographyAuditTest.class.getResourceAsStream("/assets/pane_demo/fonts/Roboto-Variable.ttf")),"bundled TTF registers under consumer-owned alias");screen.invalidate();}
                case 2->{check(screen.dialogLayoutCount()==layouts+1,"late font registration plus invalidate remeasures exactly once");
                    check(snap().contentHeight()!=contentHeight,"registered font changes wrapping/metrics from missing-family fallback");
                    check(box("dialog-confirm").height()>28,"custom button font increases action height");
                    check(box("dialog-cancel").bottom()<=box("dialog-confirm").y(),"long custom-font action labels stack");
                    capture(c,"dialog-font-registered");baseline();waitMs(800);return;}
                case 3->{idle("open custom-font dialog");var v=snap().viewport();screen.mouseScrolled(v.centerX()/screen.layoutScale(),v.centerY()/screen.layoutScale(),0,-3);}
                case 4->{check(snap().offset()>0&&screen.popupUploadPixels()>uploads,"wheel scrolls custom-font copy and updates popup");
                    check(screen.paintCount()==paints&&screen.dialogLayoutCount()==layouts,"scroll reuses wrapped lines and leaves page unchanged");
                    key(GLFW.GLFW_KEY_END);check(snap().offset()==snap().maximumOffset(),"End reaches final custom-font line");
                    key(GLFW.GLFW_KEY_HOME);check(snap().offset()==0,"Home returns to title");
                    key(GLFW.GLFW_KEY_DOWN);check(snap().offset()>18,"Down advances using actual title line metrics");key(GLFW.GLFW_KEY_HOME);
                    key(GLFW.GLFW_KEY_TAB);check(screen.focusedId().orElse("").equals("dialog-confirm"),"Tab reaches Confirm with custom action typography");}
                case 5->{check(screen.dialogLayoutCount()==layouts&&screen.paintCount()==paints,"keyboard navigation/focus do not rewrap or repaint page");baseline();screen.invalidate();}
                case 6->{check(screen.dialogLayoutCount()==layouts&&screen.popupUploadPixels()==uploads,"equal rebuilt styles reuse layout and popup pixels");
                    check(!UiCanvas.registerFont(FAMILY,new ByteArrayInputStream(new byte[]{1,2,3})),"invalid font returns false without discarding registered font");screen.invalidate();}
                case 7->{check(screen.dialogLayoutCount()==layouts&&screen.popupUploadPixels()==uploads,"failed font registration leaves measured/pixel caches valid");
                    contentHeight=snap().contentHeight();buttonHeight=box("dialog-confirm").height();large=true;screen.invalidate();}
                case 8->{check(screen.dialogLayoutCount()==layouts+1&&snap().contentHeight()>contentHeight,"larger typography invalidates layout and rewraps copy");
                    check(box("dialog-confirm").height()>buttonHeight,"action height follows larger button text");bounded();layouts=screen.dialogLayoutCount();overrides=true;screen.invalidate();}
                case 9->{check(screen.dialogLayoutCount()==layouts+1,"per-part font overrides invalidate measured layout");capture(c,"dialog-font-overrides");key(GLFW.GLFW_KEY_END);shortCopy=true;screen.invalidate();}
                case 10->{check(snap().offset()<=snap().maximumOffset(),"shorter copy clamps old scroll offset");key(GLFW.GLFW_KEY_HOME);c.getWindow().setWindowed(320,260);waitMs(800);return;}
                case 11->{bounded();check(screen.focusedId().orElse("").equals("dialog-confirm"),"compact resize preserves keyboard focus");
                    capture(c,"dialog-typography-compact");key(GLFW.GLFW_KEY_TAB);check(screen.focusedId().orElse("").equals("dialog-cancel"),"compact dialog cycles focus between actions");
                    key(GLFW.GLFW_KEY_ESCAPE);check(Collections.frequency(actions,"cancel")==1,"Escape cancels once");}
                case 12->{check(screen.openDialogId().isEmpty()&&screen.popupRetainedPixels()==0,"close releases custom-font popup backing");
                    check(screen.focusedId().orElse("").equals("open"),"close restores trigger keyboard focus");key(GLFW.GLFW_KEY_ENTER);}
                case 13->{check(snap().offset()==0,"reopening resets custom dialog scroll");key(GLFW.GLFW_KEY_TAB);key(GLFW.GLFW_KEY_ENTER);
                    check(Collections.frequency(actions,"confirm")==1,"keyboard confirm emits one consumer action");}
                case 14->{check(screen.openDialogId().isEmpty(),"confirmation closes dialog");legacy=true;screen.invalidate();}
                case 15->{click("open");}
                case 16->{check(box("dialog-confirm").height()==28,"legacy dialog keeps default action height");bounded();click("dialog-confirm");check(Collections.frequency(actions,"confirm")==2,"legacy pointer confirmation still works");}
                case 17->{c.getWindow().setWindowed(1000,700);legacy=false;large=false;overrides=false;shortCopy=false;screen.invalidate();waitMs(800);return;}
                case 18->{click("open");}
                case 19->{bounded();key(GLFW.GLFW_KEY_PAGE_DOWN);check(snap().offset()>0,"PageDown browses custom copy");key(GLFW.GLFW_KEY_END);baseline();}
                case 20->{var v=snap().viewport();for(int i=0;i<100;i++)screen.mouseScrolled(v.centerX()/screen.layoutScale(),v.centerY()/screen.layoutScale(),0,-1);baseline();waitMs(750);return;}
                case 21->{idle("settled end-stop scrolling");key(GLFW.GLFW_KEY_ESCAPE);}
                default->{check(screen.popupRetainedPixels()==0,"final close releases popup");System.out.println("[DialogTypographyTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);done=true;c.stop();return;}
            }
            waitMs(450);
        }catch(Throwable e){e.printStackTrace();System.out.println("[DialogTypographyTest] complete FAIL "+e);done=true;c.stop();}
    }
    private UiScrollSnapshot snap(){return screen.dialogScrollSnapshot().orElseThrow();}
    private UiRect box(String id){return screen.regionBounds(id).orElseThrow();}
    private void bounded(){var b=box("dialog-body");var a=box("dialog-cancel");var z=box("dialog-confirm");
        check(b.x()>=0&&b.y()>=0&&b.right()<=screen.rasterQuality().layoutWidth()&&b.bottom()<=screen.rasterQuality().layoutHeight()&&inside(b,a)&&inside(b,z),"frame and actions fit viewport at stage "+stage);
        check(a.right()<=z.x()||a.bottom()<=z.y(),"action hit regions do not overlap at stage "+stage);}
    private boolean inside(UiRect a,UiRect b){return b.x()>=a.x()&&b.y()>=a.y()&&b.right()<=a.right()&&b.bottom()<=a.bottom();}
    private void baseline(){paints=screen.paintCount();layouts=screen.dialogLayoutCount();uploads=screen.popupUploadPixels();allocations=screen.performanceSnapshot().retainedTextureAllocations();}
    private void idle(String label){check(screen.paintCount()==paints&&screen.dialogLayoutCount()==layouts&&screen.popupUploadPixels()==uploads&&screen.performanceSnapshot().retainedTextureAllocations()==allocations,label+" has zero idle paints/layouts/uploads/texture allocations");}
    private void key(int k){screen.keyPressed(new KeyEvent(k,0,0));}
    private void click(String id){var b=box(id);var e=new MouseButtonEvent(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseClicked(e,false);screen.mouseReleased(e);}
    private void check(boolean ok,String label){checks++;failed|=!ok;System.out.println("[DialogTypographyTest] "+label+": "+(ok?"PASS":"FAIL"));}
    private void waitMs(long ms){deadline=System.nanoTime()+ms*1_000_000L;}
    private void pointer(Minecraft c,double x,double y)throws Exception{var m=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);m.setAccessible(true);m.invoke(c.mouseHandler,c.getWindow().handle(),x,y);}
    private void capture(Minecraft c,String n){Screenshot.grab(c.gameDirectory,n+".png",c.gameRenderer.mainRenderTarget(),1,m->{});}
}
