package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Public-API acceptance in an isolated title client; never opens a saved world. */
final class ApiImprovementsTest {
    private UiScreen screen;
    private int ticks, checks, rawKeys, submits, backgroundActions;
    private boolean failed;
    private int sliderEvents;
    private double scrollBefore;
    private final List<String> actions = new ArrayList<>();
    private static final UiInputStyle LARGE = UiInputStyle.defaults().font(UiCanvas.MONO, 16, Font.BOLD).padding(12, 6);

    private UiScreen create(boolean lowMemory) {
        var doc = UiDocument.builder("PaneLib / Input and focus")
            // Force the low-memory probe above its 1.5x ceiling even on a 1x CI display.
            .scaling(lowMemory ? UiScaling.fixed().range(2, 2) : UiScaling.fixed()).pageShortcuts(true)
            .renderSettings(lowMemory ? UiRenderSettings.lowMemory() : UiRenderSettings.typography())
            .page(new UiPage("main", "Inputs", "", 0xFF64D4FF, ui -> {
                var c = ui.canvas(); c.fill(0xFF0B1826,0,0,ui.width(),ui.height());
                c.text("Inputs that fit the application",20,18,21,0xFFF0F9FF,Font.BOLD);
                c.text("Shared text metrics / bounded undo / modal focus",20,49,10,0xFFABC3D7);
                float w = Math.min(340,ui.width()-40);
                ui.textField("field","draft","Write here",20,78,w,44,"",64,LARGE);
                ui.textArea("area","notes","",20,135,w,125,"First line\nSecond 😀 line\nThird line\nFourth line\nFifth line",1000,
                    LARGE.font(UiCanvas.MONO,14,Font.PLAIN).lineHeight(23));
                ui.textField("limit","limit","Three UTF-16 units",20,274,w/2-5,32,"",3,UiInputStyle.defaults().font(UiCanvas.SANS,11,Font.PLAIN));
                ui.passwordField("password","secret","Password",30+w/2,274,w/2-5,32,"masked",64,UiInputStyle.defaults().font(UiCanvas.MONO,11,Font.PLAIN));
                ui.button("open","Custom modal","open",20,322,150,30,UiContext.ButtonStyle.PRIMARY,UiContext.Shape.ROUNDED);
                ui.button("background","Background action","background",180,322,180,30,UiContext.ButtonStyle.GHOST,UiContext.Shape.ROUNDED);
                if (ui.width()>640) ui.scroll("list",new UiRect(410,78,ui.width()-430,220),800,list -> {
                    for(int i=0;i<24;i++) list.canvas().text("Diagnostic row "+i,420,85+i*30,12,0xFFABC3D7);
                });
                ui.dialogButton("builtin","Built-in dialog",20,366,150,28,UiContext.ButtonStyle.GHOST,UiContext.Shape.ROUNDED,
                    new UiDialog("Confirm choice","Background input stays isolated.","Allow","confirm","Cancel","cancel"));
                ui.dropdown("current-option", "choice", 500,366,140,28,"beta", List.of(
                    new UiSelectOption("alpha","Alpha"), new UiSelectOption("beta","Beta"), new UiSelectOption("gamma","Gamma")));
                ui.menuButton("keyboard-menu", "Menu", 380, 366, 100, 28, UiContext.ButtonStyle.SECONDARY,
                    UiContext.Shape.ROUNDED, List.of(new UiMenuItem("one", "One", "one"),
                    new UiMenuItem("two", "Two", "two"), new UiMenuItem("three", "Three", "three")));
                ui.withTranslation(400, 300, shifted -> shifted.scroll("shifted", new UiRect(0,0,160,50), 150,
                    content -> content.button("shifted-button", "Translated", "translated", 5,5,100,25,
                        UiContext.ButtonStyle.SECONDARY, UiContext.Shape.ROUNDED)));
                ui.slider("keyboard-slider","audio:volume",20,410,180,-50,50,10);
                ui.slider("normalized-slider","ratio",220,410,140,.6);
                if(ui.booleanValue("modal",false)) {
                    c.fill(0xB0000000,0,0,ui.width(),ui.height());
                    float x=Math.max(12,(ui.width()-350)/2);
                    c.roundRect(0xFF153149,x,95,350,210,14);
                    c.text("Custom focus scope",x+20,112,17,0xFFF0F9FF,Font.BOLD);
                    ui.textField("modal-input","modal-text","Modal text",x+20,149,310,38,"",64,LARGE);
                    ui.button("deny","Deny","close",x+20,213,90,30,UiContext.ButtonStyle.GHOST,UiContext.Shape.ROUNDED);
                    ui.button("allow","Allow","close",x+120,213,90,30,UiContext.ButtonStyle.PRIMARY,UiContext.Shape.ROUNDED);
                    ui.button("nest","Nested","nest",x+220,213,90,30,UiContext.ButtonStyle.GHOST,UiContext.Shape.ROUNDED);
                    if(ui.booleanValue("inner",false)) {
                        c.roundRect(0xFF204A65,x+15,110,320,160,12);
                        c.text("Nested scope",x+35,133,16,0xFFFFFFFF,Font.BOLD);
                        ui.button("inner-cancel","Back","inner-close",x+35,190,250,32,UiContext.ButtonStyle.PRIMARY,UiContext.Shape.ROUNDED);
                    }
                }
            })).page(new UiPage("other","Other","",0xFF64D4FF,ui -> {})).build();
        return new UiScreen(doc,NativeOverlayRenderer.NONE,(UiEventListener)(kind,key,value)->{
            if(kind.equals("submit")) submits++;
            if(kind.equals("value") && key.equals("audio:volume")) sliderEvents++;
            if(!kind.equals("action")) return;
            actions.add(key);
            switch(key) {
                case "open" -> {
                    screen.setValue("modal",true);
                    screen.pushFocusScope("sheet",List.of("modal-input","deny","allow","nest"),"deny",this::close);
                }
                case "close" -> close();
                case "background" -> backgroundActions++;
                case "nest" -> {
                    screen.setValue("inner",true);
                    screen.pushFocusScope("inner",List.of("inner-cancel"),"inner-cancel",this::closeInner);
                }
                case "inner-close" -> closeInner();
            }
        },UiScreenOptions.builder().onKey((s,e)->{rawKeys++;return false;}).build());
    }
    private void close(){screen.setValue("modal",false);screen.popFocusScope("sheet");}
    private void closeInner(){screen.setValue("inner",false);screen.popFocusScope("inner");}
    void tick(Minecraft client) {
        try {
            if(screen==null) {
                if(!(client.gui.screen() instanceof TitleScreen))return;
                screen=create(false);client.gui.setScreen(screen);return;
            }
            if(screen.paintCount()==0)return;
            ticks++;
            if(ticks==15) {
                check(screen.focus("field"),"public focus accepts painted input");
                type("A😀B"); key(GLFW.GLFW_KEY_LEFT,0);key(GLFW.GLFW_KEY_BACKSPACE,0);
                check(value().equals("AB"),"backspace removes entire supplementary codepoint");
                screen.setValue("draft",value());
                key(GLFW.GLFW_KEY_Z,GLFW.GLFW_MOD_CONTROL);check(value().equals("A😀B"),"undo survives identical programmatic synchronization");
                key(GLFW.GLFW_KEY_DELETE,0);check(value().equals("A😀"),"undo restores pre-delete caret without synthetic selection");
                key(GLFW.GLFW_KEY_Z,GLFW.GLFW_MOD_CONTROL);
                key(GLFW.GLFW_KEY_Z,GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SHIFT);check(value().equals("A😀"),"redo edit");
                key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);type("😀");key(GLFW.GLFW_KEY_Z,GLFW.GLFW_MOD_CONTROL);
                check(value().equals("A😀"),"undo replacement restores selected text");
                screen.setValue("draft","Draft");key(GLFW.GLFW_KEY_Z,GLFW.GLFW_MOD_CONTROL);
                check(value().equals("Draft"),"programmatic replacement invalidates old history");
                key(GLFW.GLFW_KEY_2,0);type("2");check(screen.pageId().equals("main"),"typing digits does not trigger page shortcut");
                check(!screen.focus("missing"),"unknown focus rejected");
                screen.focus("limit");type("A😀B");check(screen.stringValue("limit","").equals("A😀"),"length limit never splits emoji");
                key(GLFW.GLFW_KEY_HOME,0);key(GLFW.GLFW_KEY_DELETE,0);key(GLFW.GLFW_KEY_DELETE,0);
                check(screen.stringValue("limit","").isEmpty(),"forward delete uses codepoint boundaries");
                screen.focus("area");key(GLFW.GLFW_KEY_END,0);type("\nVisible end");
            }
            if(ticks==22) {
                screen.focus("keyboard-slider");key(GLFW.GLFW_KEY_RIGHT,0);
                check(screen.numberValue("audio:volume",0)==11,"slider arrow changes ranged value by one percent");
                key(GLFW.GLFW_KEY_PAGE_DOWN,0);check(screen.numberValue("audio:volume",0)==1,"slider Page Down changes by ten percent");
                key(GLFW.GLFW_KEY_END,0);int before=sliderEvents;key(GLFW.GLFW_KEY_UP,0);
                check(screen.numberValue("audio:volume",0)==50 && sliderEvents==before,"slider clamps and emits no duplicate boundary change");
                key(GLFW.GLFW_KEY_HOME,0);check(screen.numberValue("audio:volume",0)==-50,"slider Home selects minimum");
                key(GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_MOD_CONTROL);check(screen.numberValue("audio:volume",0)==-50,"modified shortcut does not alter slider");
                screen.focus("normalized-slider");key(GLFW.GLFW_KEY_LEFT,0);
                check(Math.abs(screen.numberValue("ratio",0)-.59)<1e-9,"normalized slider keyboard starts from painted fallback");
                screen.pushFocusScope("slider-guard",List.of("field"),"field");key(GLFW.GLFW_KEY_END,0);
                check(Math.abs(screen.numberValue("ratio",0)-.59)<1e-9,"focus scope blocks background slider keys immediately");
                screen.popFocusScope("slider-guard");
            }
            if(ticks==24) {
                screen.setValue("notes", "abcd\nx\nabcd"); screen.focus("area");
                screen.focus("keyboard-menu"); key(GLFW.GLFW_KEY_ENTER,0);
            }
            if(ticks==26) {
                key(GLFW.GLFW_KEY_DOWN,0); check(screen.focusedId().orElse("").equals("menu:keyboard-menu:one"),"menu Down selects first item");
                key(GLFW.GLFW_KEY_UP,0); check(screen.focusedId().orElse("").equals("menu:keyboard-menu:three"),"menu Up wraps");
                key(GLFW.GLFW_KEY_HOME,0);key(GLFW.GLFW_KEY_DOWN,0);key(GLFW.GLFW_KEY_ENTER,0);
                check(actions.contains("two") && screen.focusedId().orElse("").equals("keyboard-menu"),"menu Enter selects and restores trigger");
                click("shifted-button");check(actions.contains("translated"),"closed menu cannot intercept next pointer event before repaint");
                screen.focus("area"); key(GLFW.GLFW_KEY_HOME,GLFW.GLFW_MOD_CONTROL);key(GLFW.GLFW_KEY_RIGHT,0);
                key(GLFW.GLFW_KEY_RIGHT,0);key(GLFW.GLFW_KEY_RIGHT,0);key(GLFW.GLFW_KEY_DOWN,0);key(GLFW.GLFW_KEY_DOWN,0);type("!");
                check(screen.stringValue("notes","").equals("abcd\nx\nabc!d"),"textarea Down preserves column across short line");

            }
            if(ticks==28) {
                click("shifted-button");check(actions.contains("translated"),"translated scroll button receives actual pointer");
                var shifted=screen.scrollViewport("shifted").orElseThrow();wheel(shifted,-2);
                check(screen.scrollSnapshot("shifted").orElseThrow().targetOffset()>0,"translated scroll receives wheel at painted position");
                screen.focus("field");screen.focus("area");key(GLFW.GLFW_KEY_HOME,0);type("^");
                check(screen.stringValue("notes","").equals("abcd\nx\n^abc!d"),"textarea Home moves to current visual line start");
            }
            if(ticks==30) {
                capture(client,"styled-inputs");check(!screen.rasterQuality().upscaled(),"typography raster meets physical target");
                check(screen.scrollViewport("list").isPresent(),"public scroll viewport exists");
                var r=screen.scrollViewport("list").orElseThrow();wheel(r,-3);
                check(screen.scrollSnapshot("list").orElseThrow().targetOffset()>0,"public scroll target reflects actual wheel");
                screen.setValue("draft","Draft");screen.focus("field");key(GLFW.GLFW_KEY_HOME,0);key(GLFW.GLFW_KEY_RIGHT,0);
                // Open without changing the underlying selection; callers can open custom sheets from application events.
                screen.setValue("modal",true);screen.pushFocusScope("sheet",List.of("modal-input","deny","allow","nest"),"deny",this::close);
                int oldRaw=rawKeys;type("x");key(GLFW.GLFW_KEY_ENTER,0);key(GLFW.GLFW_KEY_L,0);click("background");
                check(value().equals("Draft")&&submits==0,"modal blocks background typing and submit before first paint");
                check(rawKeys==oldRaw&&backgroundActions==0,"modal blocks raw shortcuts and background pointer actions");
                check(!screen.focus("field"),"scope rejects background programmatic focus");
                scrollBefore=screen.scrollSnapshot("list").orElseThrow().targetOffset();wheel(r,-3);
                check(screen.scrollSnapshot("list").orElseThrow().targetOffset()==scrollBefore,"modal blocks background wheel");
            }
            if(ticks==45) {
                check(screen.focusedId().orElse("").equals("deny"),"modal defaults to safe Deny");
                key(GLFW.GLFW_KEY_TAB,0);check(screen.focusedId().orElse("").equals("allow"),"scope Tab moves among modal controls");
                key(GLFW.GLFW_KEY_TAB,GLFW.GLFW_MOD_SHIFT);check(screen.focusedId().orElse("").equals("deny"),"scope Shift Tab reverses");
                capture(client,"custom-modal");screen.focus("modal-input");type("Q");
                check(screen.stringValue("modal-text","").equals("Q"),"modal input edits only modal state");
                key(GLFW.GLFW_KEY_ESCAPE,0);check(screen.focusScopeId().isEmpty(),"Escape closes custom scope through callback");
                check(screen.focusedId().orElse("").equals("field"),"scope restores prior focus");type("!");
                check(value().equals("D!raft"),"scope restores prior caret position");
            }
            if(ticks==47) { screen.focus("current-option");key(GLFW.GLFW_KEY_ENTER,0); }
            if(ticks==49) {
                check(screen.focusedId().orElse("").equals("menu:current-option:option-1"),"dropdown opens focused on current value");
                key(GLFW.GLFW_KEY_DOWN,0);key(GLFW.GLFW_KEY_ENTER,0);
                check(screen.stringValue("choice","").equals("gamma"),"dropdown Down advances from current value");
                check(screen.focusedId().orElse("").equals("current-option"),"dropdown selection restores trigger focus");
            }
            if(ticks==51) { screen.focus("current-option");key(GLFW.GLFW_KEY_ENTER,0); }
            if(ticks==53) {
                key(GLFW.GLFW_KEY_UP,0);key(GLFW.GLFW_KEY_ENTER,0);
                check(screen.stringValue("choice","").equals("beta"),"reopened dropdown Up moves from saved value");
            }
            if(ticks==55)click("open");
            if(ticks==65)click("nest");
            if(ticks==75) {
                check(screen.focusScopeId().orElse("").equals("inner"),"nested scope active");
                boolean rejected=false;try{screen.popFocusScope("sheet");}catch(IllegalStateException expected){rejected=true;}
                check(rejected,"out-of-order scope close is rejected");
                key(GLFW.GLFW_KEY_ESCAPE,0);check(screen.focusScopeId().orElse("").equals("sheet"),"nested Escape restores outer scope");
                check(screen.focusedId().orElse("").equals("nest"),"nested scope restores invoking control");
                key(GLFW.GLFW_KEY_ESCAPE,0);
            }
            if(ticks==80) {
                screen.focus("field");key(GLFW.GLFW_KEY_HOME,0);key(GLFW.GLFW_KEY_RIGHT,0);key(GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_MOD_SHIFT);
                screen.setValue("modal",true);screen.pushFocusScope("sheet",List.of("deny","allow"),"deny",this::close);
            }
            if(ticks==85) {
                key(GLFW.GLFW_KEY_ESCAPE,0);type("?");check(value().equals("D?raft"),"scope restores prior selection range");
                key(GLFW.GLFW_KEY_Z,GLFW.GLFW_MOD_CONTROL);check(value().equals("D!raft"),"undo restores modal-return edit");
            }
            if(ticks==90) {
                screen.focus("field");click("builtin");type("x");key(GLFW.GLFW_KEY_ENTER,0);
                check(value().equals("D!raft")&&!actions.contains("confirm"),"built-in dialog blocks stale focused input immediately");
            }
            if(ticks==105) {
                check(screen.focusedId().orElse("").equals("dialog-cancel"),"built-in dialog defaults to Cancel");
                key(GLFW.GLFW_KEY_ENTER,0);check(actions.contains("cancel"),"built-in Enter activates safe default");
                check(screen.focusedId().orElse("").equals("builtin"),"built-in dialog restores trigger focus");
                screen.clearFocus();check(screen.focusedId().isEmpty(),"public clearFocus");
                client.getWindow().setWindowed(560,430);
            }
            if(ticks==120)capture(client,"compact-inputs");
            if(ticks==130)client.getWindow().setWindowed(1280,800);
            if(ticks==145) {
                capture(client,"large-inputs");screen=create(true);client.gui.setScreen(screen);
            }
            if(ticks==160) {
                var quality=screen.rasterQuality();
                float ceiling=UiRenderSettings.lowMemory().maximumScale();
                check(quality.desiredScale()>ceiling,"low-memory fixture requests scale above its ceiling");
                check(quality.actualScale()<=ceiling,"low-memory raster obeys configured scale ceiling");
                check(quality.upscaled(),"low-memory cap reports physical upscaling");
                client.getWindow().setWindowed(854,480);
                screen.pushFocusScope("leaving",List.of("field"),"field");screen.showPage("other");
                check(screen.focusScopeId().isEmpty()&&screen.focusedId().isEmpty(),"page change releases scopes and focus");
            }
            if(ticks==175) {
                System.out.println("[ApiTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);
                client.stop();
            }
        } catch(Throwable e) {failed=true;System.out.println("[ApiTest] exception FAIL "+e);e.printStackTrace();client.stop();}
    }
    private String value(){return screen.stringValue("draft","");}
    private void key(int key,int modifiers){screen.keyPressed(new KeyEvent(key,0,modifiers));}
    private void type(String text){text.codePoints().forEach(cp->{if(cp=='\n')key(GLFW.GLFW_KEY_ENTER,0);else screen.charTyped(new CharacterEvent(cp));});}
    private void click(String id) {
        var b=screen.regionBounds(id).orElseThrow();double x=b.centerX()/screen.layoutScale(),y=b.centerY()/screen.layoutScale();
        var e=new MouseButtonEvent(x,y,new MouseButtonInfo(0,0));screen.mouseMoved(x,y);screen.mouseClicked(e,false);screen.mouseReleased(e);
    }
    private void wheel(UiRect r,double amount){screen.mouseScrolled(r.centerX()/screen.layoutScale(),r.centerY()/screen.layoutScale(),0,amount);}
    private void capture(Minecraft client,String name){Screenshot.grab(client.gameDirectory,"api-"+name+".png",client.gameRenderer.mainRenderTarget(),1,m->{});}
    private void check(boolean ok,String text){checks++;failed|=!ok;System.out.println("[ApiTest] "+text+": "+(ok?"PASS":"FAIL"));}
}
