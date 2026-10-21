package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Isolated client input, composition and retained-cost checks. No player world or network service. */
final class PopupTriggerAuditTest {
    private static final UiButtonStyle STYLE=UiButtonStyle.builder().fontFamily(Font.MONOSPACED).fontStyle(Font.BOLD)
        .textSize(16).background(0xFF304A3F).hoverBackground(0xFF406453).pressedBackground(0xFF22362B)
        .text(0xFFF4E6BB).border(0xFF8EAD82).focus(0xFFFFCC44).radius(2).build();
    private static final UiMenuStyle MENU=UiMenuStyle.builder().fontFamily(Font.MONOSPACED).textSize(14).rowHeight(32).build();
    private static final List<UiMenuItem> ITEMS=List.of(new UiMenuItem("first","First action","first"),new UiMenuItem("last","Last action","last"));
    private static final UiDialog DIALOG=new UiDialog("Continue?","This is a local popup API test.","Confirm","confirm","Cancel","cancel");
    private static final List<UiSelectOption> OPTIONS=List.of(new UiSelectOption("a","Alpha"),new UiSelectOption("b","Beta"));
    private final List<UiMenuItem> many=new ArrayList<>();
    private final List<String> events=new ArrayList<>();
    private UiScreen screen;
    private boolean enabled=true,failed,done;
    private int stage,checks,paints;
    private long start,deadline,uploads,allocations,rowUploads,rowRasters;
    private MouseButtonEvent held;
    private UiRect anchor;
    PopupTriggerAuditTest(){for(int i=0;i<80;i++)many.add(new UiMenuItem("item-"+i,"Menu item "+i,"item-"+i));}
    private UiScreen create(){return new UiScreen(UiDocument.builder("Popup trigger audit").shell(UiShell.NONE)
        .scaling(UiScaling.fixed()).tooltipDelayMillis(60000)
        .page(new UiPage("main","Main","",0xFFFFCC44,this::paint)).build(),null,(kind,key,value)->events.add(kind+":"+key+":"+value));}
    private void paint(UiContext ui){
        var g=ui.canvas();g.fill(0xFF151A17,0,0,ui.width(),ui.height());g.text("POPUP TRIGGER API",24,15,20,0xFFF4E6BB);
        ui.menuButton("styled","Styled menu",24,55,220,42,STYLE,enabled,many,MENU);
        ui.paintedMenuButton("custom","Custom server menu",24,120,220,42,2,true,ITEMS,MENU,(c,state)->custom(c,state,24,120,220,42));
        ui.dropdown("select","choice",24,185,220,42,"a",OPTIONS,STYLE,enabled,MENU);
        ui.paintedDropdown("paint-select","paint-choice",24,250,220,42,"b",OPTIONS,2,true,MENU,(c,state)->custom(c,state,24,250,220,42));
        ui.dialogButton("styled-dialog","Styled dialog",24,315,220,42,STYLE,enabled,DIALOG);
        ui.paintedDialogButton("custom-dialog","Custom dialog",24,380,220,42,2,true,DIALOG,(c,state)->custom(c,state,24,380,220,42));
        ui.paintedMenuButton("disabled","Disabled menu",24,445,220,42,2,false,ITEMS,MENU,(c,state)->custom(c,state,24,445,220,42));
        ui.withTransform(new UiTransform(330,90,1.2F,1.2F,8,1),0,0,moved->
            moved.paintedMenuButton("transformed","Transformed menu",0,0,180,36,2,true,ITEMS,MENU,(c,state)->custom(c,state,0,0,180,36)));
        ui.scroll("clipped",new UiRect(330,185,220,90),200,sc->sc.menuButton("clip-menu","Scrolled menu",330,205,210,42,STYLE,ITEMS,MENU));
        ui.virtualList("rows",new UiRect(330,315,240,150),10000,40,2,(row,item)->{
            var b=item.bounds();row.paintedMenuButton("row-menu-"+item.index(),"Row "+item.index(),b.x(),b.y(),220,36,2,true,ITEMS,MENU,
                (c,state)->custom(c,state,b.x(),b.y(),220,36));
        });
    }
    private static void custom(UiCanvas c,UiButtonState state,float x,float y,float w,float h){
        c.fill(state.pressed()?0xFF67532C:state.hovered()?0xFF94753C:0xFF806436,x,y,w,h);
        c.text(state.label(),x+12,y+12,14,0xFFFFEDB9,Font.BOLD,Font.MONOSPACED);
        c.line(0xFFC6A75B,2,x,y+h-2,x+w,y+h-2);
    }
    void tick(Minecraft c){
        if(done)return;
        try{
            if(start==0)start=System.nanoTime();
            if(System.nanoTime()-start>180_000_000_000L)throw new IllegalStateException("timeout at stage "+stage);
            if(screen==null){if(!(c.gui.screen() instanceof TitleScreen)||c.gui.overlay()!=null)return;
                c.getWindow().setWindowed(1000,700);screen=create();c.gui.setScreen(screen);pointer(c,800,560);waitMs(1200);return;}
            if(System.nanoTime()<deadline||c.gui.overlay()!=null)return;
            switch(stage++){
                case 0->{check(c.level==null&&c.player==null,"isolated test requires no world/player");
                    check(screen.buttonState("styled").orElseThrow().label().equals("Styled menu"),"styled trigger exposes semantic label");
                    check(!screen.buttonState("disabled").orElseThrow().enabled()&&screen.regionBounds("disabled").isEmpty()&&!screen.focus("disabled"),"disabled custom trigger has metadata but no hit or keyboard target");
                    screen.focus("custom");pointerTo(c,"custom");}
                case 1->{check(screen.buttonState("custom").orElseThrow().focused()&&screen.buttonState("custom").orElseThrow().hovered(),"custom painter receives focus and hover state");
                    held=event("custom");screen.mouseClicked(held,false);}
                case 2->{check(screen.buttonState("custom").orElseThrow().pressed(),"custom painter receives held press");capture(c,"popup-custom-pressed");screen.mouseReleased(held);}
                case 3->{check(screen.openMenuId().orElse("").equals("custom"),"custom menu opens on pointer release");
                    check(Math.abs(screen.regionBounds("menu:custom:first").orElseThrow().height()-32)<.1,"custom trigger keeps independent popup row style");
                    key(GLFW.GLFW_KEY_END);key(GLFW.GLFW_KEY_ENTER);check(events.contains("action:last:"),"custom menu keyboard selection dispatches consumer action");}
                case 4->{check(screen.focusedId().orElse("").equals("custom")&&screen.popupRetainedPixels()==0,"selection restores trigger focus and releases popup");
                    screen.focus("styled");key(GLFW.GLFW_KEY_SPACE);}
                case 5->{check(screen.openMenuId().orElse("").equals("styled"),"styled trigger opens from Space");key(GLFW.GLFW_KEY_ESCAPE);}
                case 6->{check(screen.focusedId().orElse("").equals("styled"),"Escape restores styled trigger focus");
                    screen.focus("select");key(GLFW.GLFW_KEY_ENTER);}
                case 7->{check(screen.focusedId().orElse("").equals("menu:select:option-0"),"styled dropdown starts on selected value");key(GLFW.GLFW_KEY_END);key(GLFW.GLFW_KEY_ENTER);}
                case 8->{check(screen.stringValue("choice","").equals("b")&&screen.buttonState("select").orElseThrow().label().equals("Beta"),"dropdown updates binding and displayed semantic label");
                    check(events.contains("value:choice:b"),"dropdown emits one normal value event");click("paint-select");}
                case 9->{key(GLFW.GLFW_KEY_HOME);key(GLFW.GLFW_KEY_ENTER);}
                case 10->{check(screen.stringValue("paint-choice","").equals("a")&&screen.buttonState("paint-select").orElseThrow().label().equals("Alpha"),"custom dropdown uses current display label and binding");
                    screen.focus("custom-dialog");key(GLFW.GLFW_KEY_ENTER);}
                case 11->{check(screen.openDialogId().orElse("").equals("custom-dialog")&&screen.focusedId().orElse("").equals("dialog-cancel"),"custom dialog opens with safe cancel focus");key(GLFW.GLFW_KEY_ESCAPE);}
                case 12->{check(screen.openDialogId().isEmpty()&&screen.focusedId().orElse("").equals("custom-dialog"),"dialog Escape restores custom trigger focus");click("styled-dialog");}
                case 13->{click("dialog-confirm");check(events.contains("action:confirm:"),"styled dialog confirmation dispatches action");}
                case 14->{anchor=screen.regionBounds("transformed").orElseThrow();click("transformed");}
                case 15->{var b=screen.regionBounds("menu-body:transformed").orElseThrow();
                    check(Math.abs(b.x()-anchor.x())<1&&b.y()>=anchor.bottom(),"rotated/scaled trigger positions popup at transformed anchor");
                    check(screen.buttonState("transformed").orElseThrow().label().equals("Transformed menu"),"affine trigger preserves semantic metadata");key(GLFW.GLFW_KEY_ESCAPE);screen.scrollTo("clipped",35);}
                case 16->{anchor=screen.regionBounds("clip-menu").orElseThrow();check(anchor.height()<42,"scroll clips trigger hit area");click("clip-menu");}
                case 17->{var b=screen.regionBounds("menu-body:clip-menu").orElseThrow();check(Math.abs(b.x()-anchor.x())<1&&b.y()>=anchor.bottom(),"scrolled trigger popup uses visible anchor");key(GLFW.GLFW_KEY_ESCAPE);click("row-menu-0");}
                case 18->{check(screen.openMenuId().orElse("").equals("row-menu-0")&&screen.regionBounds("menu:row-menu-0:first").isPresent(),"custom virtual-row trigger opens native popup");
                    check(screen.buttonState("row-menu-0").orElseThrow().label().equals("Row 0"),"retained virtual row preserves semantic label");key(GLFW.GLFW_KEY_ESCAPE);}
                case 19->{rowRasters=screen.virtualRowRasterCount();screen.scrollVirtualItemIntoView("rows",9999);}
                case 20->{long jumpRows=screen.virtualRowRasterCount()-rowRasters;System.out.println("[PopupTriggerCost] jumpRows="+jumpRows+" cachedRows="+screen.virtualCachedRows("rows"));
                    check(jumpRows<=20&&screen.virtualCachedRows("rows")<=12,"10000-row end jump rasterizes and retains a bounded row window");click("row-menu-9999");}
                case 21->{check(screen.openMenuId().orElse("").equals("row-menu-9999"),"recycled virtual row has working popup trigger");key(GLFW.GLFW_KEY_ESCAPE);click("styled");}
                case 22->{enabled=false;screen.invalidate();}
                case 23->{check(screen.openMenuId().isEmpty()&&screen.regionBounds("styled").isEmpty()&&screen.popupRetainedPixels()==0,"disabling open trigger closes popup and removes targets");
                    check(!screen.focus("select")&&!screen.focus("styled-dialog"),"disabled styled selectors and dialogs cannot focus");enabled=true;screen.invalidate();}
                case 24->{click("styled-dialog");}
                case 25->{enabled=false;screen.invalidate();}
                case 26->{check(screen.openDialogId().isEmpty()&&screen.popupRetainedPixels()==0,"disabling active dialog trigger cancels and releases backing");enabled=true;screen.invalidate();pointer(c,800,560);}
                case 27->{capture(c,"popup-triggers-styled");baseline();waitMs(800);return;}
                case 28->{idle("settled triggers");screen.focus("styled");key(GLFW.GLFW_KEY_ENTER);pointer(c,800,560);}
                case 29->{check(screen.popupRetainedPixels()>0&&screen.popupRetainedPixels()<(long)screen.rasterQuality().pixelWidth()*screen.rasterQuality().pixelHeight(),"styled popup keeps cropped backing");baseline();waitMs(800);return;}
                case 30->{idle("open styled menu");var b=screen.regionBounds("menu-body:styled").orElseThrow();screen.mouseScrolled(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),0,-8);}
                case 31->{check(screen.paintCount()==paints&&screen.popupUploadPixels()>uploads,"popup wheel updates only cropped popup, not page");
                    check(screen.virtualRowRasterCount()==rowRasters&&screen.virtualRowUploadPixels()==rowUploads,"popup wheel does not rerasterize virtual rows");
                    c.getWindow().setWindowed(850,620);waitMs(800);return;}
                case 32->{var b=screen.regionBounds("menu-body:styled").orElseThrow();check(b.x()>=0&&b.y()>=0&&b.right()<=screen.rasterQuality().layoutWidth()&&b.bottom()<=screen.rasterQuality().layoutHeight(),"styled popup remains bounded after resize");
                    key(GLFW.GLFW_KEY_END);key(GLFW.GLFW_KEY_ENTER);check(events.contains("action:item-79:"),"resized styled menu selects last item with keyboard");pointer(c,800,560);}
                default->{System.out.println("[PopupTriggerTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);done=true;c.stop();return;}
            }
            waitMs(400);
        }catch(Throwable e){e.printStackTrace();System.out.println("[PopupTriggerTest] complete FAIL "+e);done=true;c.stop();}
    }
    private void baseline(){paints=screen.paintCount();uploads=screen.popupUploadPixels();allocations=screen.performanceSnapshot().retainedTextureAllocations();rowUploads=screen.virtualRowUploadPixels();rowRasters=screen.virtualRowRasterCount();}
    private void idle(String label){check(screen.paintCount()==paints&&screen.popupUploadPixels()==uploads&&screen.performanceSnapshot().retainedTextureAllocations()==allocations&&screen.virtualRowUploadPixels()==rowUploads&&screen.virtualRowRasterCount()==rowRasters,label+" causes zero extra paints/uploads/texture allocations");}
    private void check(boolean ok,String label){checks++;failed|=!ok;System.out.println("[PopupTriggerTest] "+label+": "+(ok?"PASS":"FAIL"));}
    private void waitMs(long ms){deadline=System.nanoTime()+ms*1_000_000L;}
    private void key(int key){screen.keyPressed(new KeyEvent(key,0,0));}
    private MouseButtonEvent event(String id){var b=screen.regionBounds(id).orElseThrow(()->new IllegalStateException("Missing "+id));return new MouseButtonEvent(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),new MouseButtonInfo(0,0));}
    private void click(String id){var e=event(id);screen.mouseClicked(e,false);screen.mouseReleased(e);}
    private void pointerTo(Minecraft c,String id)throws Exception{var b=screen.regionBounds(id).orElseThrow();pointer(c,b.centerX()/screen.rasterQuality().layoutWidth()*c.getWindow().getScreenWidth(),b.centerY()/screen.rasterQuality().layoutHeight()*c.getWindow().getScreenHeight());}
    private void pointer(Minecraft c,double x,double y)throws Exception{var method=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);method.setAccessible(true);method.invoke(c.mouseHandler,c.getWindow().handle(),x,y);}
    private void capture(Minecraft c,String name){Screenshot.grab(c.gameDirectory,name+".png",c.gameRenderer.mainRenderTarget(),1,m->{});}
}
