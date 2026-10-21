package dev.pane.demo;

import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

/** Isolated editor viewport acceptance checks, using no world or personal data. */
final class EditorAuditTest {
    private UiScreen screen;
    private int tick,checks;
    private boolean failed,finished,hidePlain;
    private double browsed;
    private int idlePaints, originalWidth, originalHeight;
    void tick(Minecraft client) {
        if(finished)return;
        try {
            if(screen==null) {
                if(!(client.gui.screen() instanceof TitleScreen))return;
                originalWidth=client.getWindow().getScreenWidth();originalHeight=client.getWindow().getScreenHeight();
                screen=new UiScreen(UiDocument.builder("Editor audit").scaling(UiScaling.fixed())
                    .scrollSettings(UiScrollSettings.immediate())
                    .page(new UiPage("main","Main","",0xFF88AA55,ui -> {
                        ui.scroll("parent",new UiRect(20,20,300,180),600,inside ->
                            inside.textArea("area","text","",30,30,260,110,"Line of text 😀\n".repeat(80),10000));
                        if(!hidePlain)ui.textField("plain","plain","",30,250,260,"abcdef",100);
                        ui.virtualList("rows",new UiRect(350,20,260,150),10,120,4,(row,item)->
                            row.textArea("row-"+item.index(),"row-text-"+item.index(),"",item.bounds().x()+5,item.bounds().y()+5,
                                240,100,"Virtual editor line\n".repeat(50),10000));
                    })).build());
                client.gui.setScreen(screen);return;
            }
            if(screen.paintCount()==0)return;
            tick++;
            if(tick==10) {
                screen.focus("area");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_HOME,0,GLFW.GLFW_MOD_CONTROL));
            }
            if(tick==15) {
                var b=screen.regionBounds("area").orElseThrow();
                check(screen.mouseScrolled(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),0,-3),"textarea handles wheel");
            }
            if(tick==20) {
                check(screen.scrollSnapshot("parent").orElseThrow().offset()==0,"textarea wheel does not scroll parent");
                browsed=screen.editorScrollSnapshot("area").orElseThrow().offset();
                check(browsed>0,"textarea wheel advances independent viewport");
                screen.invalidate();
            }
            if(tick==25) {
                check(screen.editorScrollSnapshot("area").orElseThrow().offset()==browsed,"ordinary repaint does not snap browsing back to caret");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_HOME,0,GLFW.GLFW_MOD_CONTROL));
            }
            if(tick==30) {
                check(screen.editorScrollSnapshot("area").orElseThrow().offset()==0,"Home reveals unchanged caret after wheel browsing");
                var b=screen.regionBounds("area").orElseThrow();
                screen.mouseScrolled(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),0,-1000);
                screen.mouseScrolled(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),0,-1000);
            }
            if(tick==35) {
                var view=screen.editorScrollSnapshot("area").orElseThrow();
                check(view.offset()==view.maximumOffset(),"repeated wheel before repaint clamps to document end");
                check(screen.scrollSnapshot("parent").orElseThrow().offset()==0,"editor end stop does not move parent");
                idlePaints=screen.paintCount();
                var b=screen.regionBounds("area").orElseThrow();
                screen.mouseScrolled(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),0,-1);
            }
            if(tick==40) {
                check(screen.paintCount()==idlePaints,"editor wheel end stop causes no extra paint");
                screen.setValue("text","short");
            }
            if(tick==45) {
                check(screen.editorScrollSnapshot("area").orElseThrow().offset()==0,"shrinking value clamps editor viewport");
                var b=screen.regionBounds("area").orElseThrow();
                screen.mouseScrolled(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),0,-1);
                check(screen.scrollSnapshot("parent").orElseThrow().offset()>0,"short editor passes wheel to parent");
                screen.scrollTo("parent",0);screen.setValue("text","Line of text 😀\n".repeat(80));
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_HOME,0,GLFW.GLFW_MOD_CONTROL));
            }
            if(tick==50) {
                var b=screen.regionBounds("area").orElseThrow();
                screen.mouseScrolled(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),0,-3);
            }
            if(tick==55) {
                click(40,42);screen.charTyped(new CharacterEvent('!'));
                check(screen.stringValue("text","").indexOf('!')=="Line of text 😀\n".length()*9,"pointer insertion matches scrolled text line");
                screen.focus("row-0");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_HOME,0,GLFW.GLFW_MOD_CONTROL));
            }
            if(tick==60) {
                var b=screen.regionBounds("row-0").orElseThrow();
                screen.mouseScrolled(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),0,-2);
            }
            if(tick==65) {
                net.minecraft.client.Screenshot.grab(client.gameDirectory,"audit-editor-viewport.png",client.gameRenderer.mainRenderTarget(),1,m->{});
                check(screen.editorScrollSnapshot("row-0").orElseThrow().offset()>0,"virtual row textarea keeps independent wheel position");
                check(screen.scrollSnapshot("rows").orElseThrow().offset()==0,"virtual editor wheel leaves list stationary");
                browsed=screen.editorScrollSnapshot("row-0").orElseThrow().offset();screen.invalidateVirtualItem("rows",0);
            }
            if(tick==70) {
                check(screen.editorScrollSnapshot("row-0").orElseThrow().offset()==browsed,"virtual row reraster preserves browsing state");
                var b=screen.regionBounds("row-0").orElseThrow();
                click(b.x()+12,b.y()+15);
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_END,0,GLFW.GLFW_MOD_CONTROL));
            }
            if(tick==75) {
                var view=screen.editorScrollSnapshot("row-0").orElseThrow();
                check(view.offset()==view.maximumOffset(),"same-batch click then End reveals keyboard caret in virtual row");
                check(view.viewport().height()==88,"editor snapshot reports inner text viewport excluding padding");
                browsed=view.offset();
                screen.focus("plain");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_HOME,0,0));
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_RIGHT,0,GLFW.GLFW_MOD_SHIFT));
                screen.resize(screen.width-1,screen.height);
                click(280,265);
                client.getWindow().setWindowed(Math.max(640,originalWidth-40),originalHeight);
            }
            if(tick==85) {
                check(screen.focusedId().orElse("").equals("plain"),"window resize preserves field focus");
                screen.charTyped(new CharacterEvent('X'));
                check(screen.stringValue("plain","").equals("Xbcdef"),"window resize preserves selection for replacement");
                check(screen.editorScrollSnapshot("row-0").orElseThrow().offset()==browsed,"virtual-layer resize preserves unfocused editor browsing");
                hidePlain=true;
                client.getWindow().setWindowed(originalWidth,originalHeight);
            }
            if(tick==95) {
                check(screen.focusedId().isEmpty(),"resize clears focus when responsive field disappears");
                screen.charTyped(new CharacterEvent('Y'));
                check(screen.stringValue("plain","").equals("Xbcdef"),"removed field cannot receive further typing");
                screen.focus("row-0");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_HOME,0,GLFW.GLFW_MOD_CONTROL));
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_RIGHT,0,GLFW.GLFW_MOD_SHIFT));
                client.getWindow().setWindowed(Math.max(640,originalWidth-60),originalHeight);
            }
            if(tick==105) {
                check(screen.focusedId().orElse("").equals("row-0"),"virtual editor keeps focus through layer rebuild");
                screen.charTyped(new CharacterEvent('Z'));
                check(screen.stringValue("row-text-0","").startsWith("Zirtual"),"virtual editor keeps selection through layer rebuild");
                screen.pushFocusScope("pending",java.util.List.of("area"),"area");
                client.getWindow().setWindowed(originalWidth,originalHeight);
                screen.resize(screen.width-1,screen.height);
            }
            if(tick==115) {
                check(screen.focusedId().orElse("").equals("area"),"resize preserves focus queued by a new custom scope");
                screen.popFocusScope("pending");
                finished=true;System.out.println("[EditorTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);client.stop();
            }
        }catch(Throwable error){finished=true;error.printStackTrace();System.out.println("[EditorTest] complete FAIL "+error);client.stop();}
    }
    private void click(float x,float y) {
        var event=new MouseButtonEvent(x/screen.layoutScale(),y/screen.layoutScale(),new MouseButtonInfo(0,0));
        screen.mouseClicked(event,false);screen.mouseReleased(event);
    }
    private void check(boolean passed,String message){checks++;failed|=!passed;System.out.println("[EditorTest] "+message+": "+(passed?"PASS":"FAIL"));}
}
