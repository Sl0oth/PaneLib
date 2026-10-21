package dev.pane.demo;

import dev.pane.ui.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Disposable real-client popup regressions; never opens a player world. */
final class PopupAuditTest {
    private UiScreen screen;
    private int tick, checks, rawKeys, rawClicks, originalWidth, originalHeight;
    private boolean failed;
    private int idlePaints;
    private long idleAllocations, idleUploads, wheelUploads;
    private int wheelPaints;
    private boolean overlayPointerBlocked;
    private UiScreen create() {
        var options = new ArrayList<UiSelectOption>();
        for (int i=0;i<80;i++) options.add(new UiSelectOption("v"+i,"Option "+i));
        return new UiScreen(UiDocument.builder("Popup audit").scaling(UiScaling.fixed()).pageShortcuts(true).tooltipDelayMillis(0)
            .page(new UiPage("main","Main","",0xFF9DA86C,ui -> {
                ui.canvas().fill(ui.theme().background(),0,0,ui.width(),ui.height());
                ui.tooltip("background-tip","Background must stay hidden",250,80,80,40);
                ui.nativeSlot("behind-popup",45,85,90,90);
                ui.canvas().fill(0xFF00FFFF,45,200,90,30);
                ui.nativeOverlayLayer("cover-behind-popup",45,200,90,30);
                ui.dropdown("large","choice",20,20,150,28,"v75",options,UiMenuStyle.builder().textSize(14).rowHeight(36).padding(18,7).build());
                ui.virtualList("popup-rows",new UiRect(350,20,150,70),2,30,0,(row,item) ->
                    row.dropdown("row-choice-"+item.index(),"row-value-"+item.index(),item.bounds().x(),item.bounds().y(),140,26,"a",
                        List.of(new UiSelectOption("a","Row A"),new UiSelectOption("b","Row B")),UiMenuStyle.builder().textSize(12).rowHeight(31).build()));
            })).page(new UiPage("other","Other","",0xFF9DA86C,ui -> {})).build(),overlay -> {
                overlayPointerBlocked=overlay.pointerBlocked();
                float scale=screen.layoutScale();
                overlay.graphics().fill(Math.round(45/scale),Math.round(85/scale),Math.round(135/scale),Math.round(175/scale),0xFF00FF00);
            },event -> {},
            UiScreenOptions.builder().onKey((s,e) -> {rawKeys++;return false;})
                .onClick((s,e) -> {rawClicks++;return false;}).build());
    }
    void tick(Minecraft client) {
        try {
            if(screen==null) {
                if(!(client.gui.screen() instanceof TitleScreen)) return;
                originalWidth=client.getWindow().getScreenWidth();originalHeight=client.getWindow().getScreenHeight();
                screen=create();client.gui.setScreen(screen);return;
            }
            if(screen.paintCount()==0)return;
            tick++;
            if(tick==10) click("large");
            if(tick==15) {
                check(overlayPointerBlocked,"native overlay knows popup blocks background pointer");
                check(screen.popupRetainedPixels()>0 && screen.popupRetainedPixels() < (long) screen.rasterQuality().pixelWidth()*screen.rasterQuality().pixelHeight(),"popup texture is cropped below full page size");
                check(visible("menu:large:option-75"),"opening reveals selected option within viewport");
                check(screen.regionBounds("menu:large:option-0").isEmpty(),"offscreen options have no pointer hit target");
                key(GLFW.GLFW_KEY_HOME,0);
            }
            if(tick==20) {
                check(visible("menu:large:option-0"),"Home reveals first option");
                key(GLFW.GLFW_KEY_END,0);key(GLFW.GLFW_KEY_ENTER,0);
                check(screen.stringValue("choice","").equals("v79"),"End then Enter selects last item before repaint");
            }
            if(tick==25) {click("large");}
            if(tick==30) {
                key(GLFW.GLFW_KEY_END,0);key(GLFW.GLFW_KEY_TAB,0);
                check(screen.focusedId().orElse("").equals("menu:large:option-0"),"Tab wraps through full popup model");
            }
            if(tick==35) {
                check(visible("menu:large:option-0"),"Tab reveals focused option");
                var b=screen.regionBounds("menu:large:option-0").orElseThrow();
                screen.mouseScrolled(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),0,-100);
            }
            if(tick==40) {
                check(visible("menu:large:option-79"),"wheel reaches last item");
                click("menu:large:option-79");
                check(screen.openMenuId().isEmpty(),"pointer selects visible final item and closes");
                check(screen.regionBounds("menu:large:option-79").isEmpty(),"close removes stale popup targets immediately");
            }
            if(tick==45) click("large");
            if(tick==50) {
                check(visible("menu:large:option-79"),"reopen reveals current selection");
                client.getWindow().setWindowed(520,320);
            }
            if(tick==65) {
                check(visible("menu:large:option-79"),"resize keeps focused selection visible");
                Screenshot.grab(client.gameDirectory,"audit-bounded-popup.png",client.gameRenderer.mainRenderTarget(),1,m->{});
                key(GLFW.GLFW_KEY_UP,0);key(GLFW.GLFW_KEY_UP,0);key(GLFW.GLFW_KEY_ENTER,0);
                check(screen.stringValue("choice","").equals("v77"),"repeated arrows and Enter work before repaint");
                click("large");
                key(GLFW.GLFW_KEY_ESCAPE,0);
                check(screen.openMenuId().isEmpty(),"Escape closes popup");
            }
            if(tick==70) {
                click("large");int keys=rawKeys;
                key(GLFW.GLFW_KEY_2,0);
                check(rawKeys==keys && screen.pageId().equals("main"),"popup blocks background shortcuts and raw key callbacks");
            }
            if(tick==75) {
                screen.mouseMoved(260/screen.layoutScale(),90/screen.layoutScale());
                var b=screen.regionBounds("menu:large:option-77").orElseThrow();
                screen.resize(screen.width-1,screen.height-1);
                int clicks=rawClicks;
                var e=new MouseButtonEvent(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),new MouseButtonInfo(0,0));
                screen.mouseClicked(e,false);screen.mouseReleased(e);
                check(screen.openMenuId().isPresent() && rawClicks==clicks,"stale resize click is consumed without selection or background callback");
            }
            if(tick==80) {
                check(screen.visibleTooltipId().isEmpty(),"background tooltip blocked while popup is open");
                key(GLFW.GLFW_KEY_HOME,0);key(GLFW.GLFW_KEY_PAGE_DOWN,0);
                check(!screen.focusedId().orElse("").equals("menu:large:option-0"),"PageDown navigates model beyond first item");
                key(GLFW.GLFW_KEY_ESCAPE,0);
                click("large");
            }
            if(tick==85) key(GLFW.GLFW_KEY_HOME,0);
            if(tick==86) {
                var b=screen.regionBounds("menu:large:option-0").orElseThrow();
                screen.mouseMoved(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale());
            }
            if(tick==90) {
                wheelPaints=screen.paintCount();wheelUploads=screen.popupUploadPixels();
                var b=screen.regionBounds("menu:large:option-0").orElseThrow();
                screen.mouseScrolled(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),0,-100);
            }
            if(tick==95) {
                check(screen.paintCount()==wheelPaints,"wheel over popup rows does not repaint page chrome");
                check(screen.popupUploadPixels()>wheelUploads,"wheel over popup rows updates cropped texture");
                key(GLFW.GLFW_KEY_HOME,0);
            }
            if(tick==100) {
                check(visible("menu:large:option-0"),"Home re-reveals an already-focused row after wheel scrolling");
                key(GLFW.GLFW_KEY_END,0);
            }
            if(tick==105) {
                var b=screen.regionBounds("menu:large:option-79").orElseThrow();
                for(int i=0;i<20;i++)screen.mouseScrolled(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),0,-1);
                click("menu:large:option-79");
                check(screen.openMenuId().isEmpty(),"end-stop wheel events do not invalidate clickable rows");
            }
            if(tick==110) click("large");
            if(tick==115) {
                var b=screen.regionBounds("menu-body:large").orElseThrow();
                clickAt(b.x()+1,b.y()+1);
                check(screen.openMenuId().isPresent(),"popup padding is inert");
                clickAt(b.right()-2,b.centerY());
                check(screen.openMenuId().isPresent(),"scroll indicator gutter does not select a row");
            }
            if(tick==125) {
                idleUploads=screen.popupUploadPixels();
                idlePaints=screen.paintCount();idleAllocations=screen.performanceSnapshot().retainedTextureAllocations();
                var b=screen.regionBounds("menu-body:large").orElseThrow();
                for(int i=0;i<100;i++)screen.mouseScrolled(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),0,-1);
            }
            if(tick==150) {
                check(screen.paintCount()==idlePaints,"idle popup and end-stop wheel generate no extra paints");
                check(screen.performanceSnapshot().retainedTextureAllocations()==idleAllocations,"idle popup allocates no extra textures");
                check(screen.popupUploadPixels()==idleUploads,"idle popup uploads no pixels");
                key(GLFW.GLFW_KEY_ESCAPE,0);
            }
            if(tick==155) {
                check(screen.popupRetainedPixels()==0,"closing popup releases cropped backing");
                check(!overlayPointerBlocked,"native overlay pointer state restored on close");
                click("row-choice-0");
            }
            if(tick==160) {
                check(screen.regionBounds("menu:row-choice-0:option-0").isPresent(),"virtual-row menu survives transition into popup composition");
                check(Math.abs(screen.regionBounds("menu:row-choice-0:option-0").orElseThrow().height()-31)<.1F,"virtual-row menu retains styled row geometry");
                key(GLFW.GLFW_KEY_END,0);key(GLFW.GLFW_KEY_ENTER,0);
                check(screen.stringValue("row-value-0","").equals("b"),"virtual-row popup selection updates consumer state");
            }
            if(tick==165) {
                check(screen.popupRetainedPixels()==0,"virtual-row popup backing released after selection");
                client.getWindow().setWindowed(originalWidth,originalHeight);
                System.out.println("[PopupTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);client.stop();
            }
        } catch(Throwable error) {System.out.println("[PopupTest] complete FAIL "+error);error.printStackTrace();client.stop();}
    }
    private boolean visible(String id) {
        var b=screen.regionBounds(id);
        return b.isPresent() && b.get().height()>=35.99F && b.get().x()>=0 && b.get().y()>=0 && b.get().right()<=screen.rasterQuality().layoutWidth()
            && b.get().bottom()<=screen.rasterQuality().layoutHeight();
    }
    private void key(int key,int mods){screen.keyPressed(new KeyEvent(key,0,mods));}
    private void click(String id){var b=screen.regionBounds(id).orElseThrow();var e=new MouseButtonEvent(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseMoved(e.x(),e.y());screen.mouseClicked(e,false);screen.mouseReleased(e);}
    private void clickAt(float x,float y){var e=new MouseButtonEvent(x/screen.layoutScale(),y/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseMoved(e.x(),e.y());screen.mouseClicked(e,false);screen.mouseReleased(e);}
    private void check(boolean ok,String label){checks++;failed|=!ok;System.out.println("[PopupTest] "+label+": "+(ok?"PASS":"FAIL"));}
}
