package dev.pane.demo;

import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import org.lwjgl.glfw.GLFW;

/** Isolated W01 client fixture. Never opens a level or changes an account skin. */
final class PlayerPreviewAuditTest {
    private UiScreen screen;
    private int stage, checks, paints, originalGuiScale, lastWidth, lastHeight;
    private long until, started, stableSince, frames;
    private double leftPointerX;
    private int stablePaint = -1;
    private boolean failed, done, modalBlocked;
    private java.util.concurrent.CompletableFuture<Void> reload;
    private static final UiPlayerPreview FIXED = UiPlayerPreview.standing().bodyYaw(210).headRotation(-12,0).cameraPitch(-8);
    private static final PlayerSkin SLIM = new PlayerSkin(WayfarerPlayerPreview.EXPLORER_SKIN.body(), null, null, PlayerModelType.SLIM, true);

    private UiScreen previews() {
        return new UiScreen(UiDocument.builder("W01 player preview validation")
            .shell(UiShell.NONE).scaling(UiScaling.fixed()).renderSettings(UiRenderSettings.typography())
            .page(new UiPage("previews", "Previews", "", 0xFFE8BC62, ui -> {
                ui.canvas().fill(0xFF25303C, 0, 0, ui.width(), ui.height());
                String[] ids={"wide", "slim", "default", "tracking"};
                for(int i=0;i<4;i++) {
                    float w=ui.width()/4F, x=i*w+20, y=70, h=ui.height()-115;
                    ui.canvas().text(ids[i], x, 28, 18, 0xFFFFFFFF);
                    ui.canvas().fill(0xFF364656,x,y,w-40,h);
                    ui.canvas().fill(0xFFFFD357,x,y+h*.95F,w-40,1);
                    if(!ui.booleanValue("hidden",false))ui.nativeSlot(ids[i],x,y,w-40,h);
                }
                ui.dialogButton("modal","Preview coverage",20,ui.height()-35,160,26,UiContext.ButtonStyle.PRIMARY,UiContext.Shape.SQUARE,
                    new UiDialog("Preview coverage", "Native previews must remain behind this dialog.", "OK", "unused", "Cancel", "cancel"));
            })).build(), overlay -> {
                modalBlocked=overlay.pointerBlocked();
                overlay.playerPreview("wide",WayfarerPlayerPreview.EXPLORER_SKIN,FIXED);
                overlay.playerPreview("slim",SLIM,FIXED);
                overlay.playerPreview("default",null,FIXED);
                overlay.playerPreview("tracking",WayfarerPlayerPreview.EXPLORER_SKIN,FIXED.followPointer(true));
                overlay.playerPreview("not-reserved",null);
            }, event -> {}, UiScreenOptions.builder().backdrop(UiBackdrop.NONE).build());
    }

    void tick(Minecraft c) {
        if(done)return;
        try {
            if(started==0)started=System.nanoTime();
            if(System.nanoTime()-started>180_000_000_000L)throw new IllegalStateException("Preview test timeout");
            if(screen==null) {
                if(!(c.gui.screen() instanceof TitleScreen)||c.gui.overlay()!=null)return;
                originalGuiScale=c.options.guiScale().get();
                c.getWindow().setWindowed(1440,810);
                screen=WayfarerPreview.openMenu(c); pointer(c,10); waitFor(1800);return;
            }
            int w=c.getWindow().getWidth(),h=c.getWindow().getHeight();
            if(w!=lastWidth||h!=lastHeight){lastWidth=w;lastHeight=h;waitFor(1200);return;}
            if(System.nanoTime()<until||c.gui.overlay()!=null)return;
            if(screen.paintCount()!=stablePaint){stablePaint=screen.paintCount();stableSince=System.nanoTime();return;}
            if(System.nanoTime()-stableSince<600_000_000L)return;
            switch(stage++) {
                case 0 -> {
                    check(c.level==null&&c.player==null&&c.getSingleplayerServer()==null,"Wayfarer opens without world/player/server");
                    check(screen.performanceSnapshot().nativeEntityCount()==1,"Wayfarer uses supported native entity counter");
                    capture(c,"wayfarer-api-left");paints=screen.paintCount();frames=screen.performanceSnapshot().frameCount();pointer(c,c.getWindow().getScreenWidth()-10);
                }
                case 1 -> {
                    check(screen.paintCount()==paints,"pointer movement does not repaint retained Wayfarer art");
                    check(screen.performanceSnapshot().frameCount()>frames&&screen.performanceSnapshot().nativeEntityCount()==1,"native preview continues while UI stays idle");
                    capture(c,"wayfarer-api-right"); c.options.guiScale().set(3);c.resizeGui();
                }
                case 2 -> {check(screen.performanceSnapshot().nativeEntityCount()==1,"preview submits at GUI scale 3");capture(c,"wayfarer-api-scale3");c.getWindow().setWindowed(960,600);}
                case 3 -> {check(screen.performanceSnapshot().nativeEntityCount()==1,"preview submits after compact resize");capture(c,"wayfarer-api-compact");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0));check(c.gui.screen() instanceof TitleScreen,"Escape returns to title");screen=WayfarerPreview.openMenu(c);}
                case 4 -> {check(screen.performanceSnapshot().nativeEntityCount()==1&&c.level==null,"reopening submits a world-free preview");c.getWindow().setWindowed(1440,810);c.options.guiScale().set(originalGuiScale);c.resizeGui();screen=previews();c.gui.setScreen(screen);pointer(c,10);}
                case 5 -> {check(screen.performanceSnapshot().nativeEntityCount()==4,"wide/slim/default/tracking render; absent slot submits nothing");capture(c,"preview-api-left");leftPointerX=c.mouseHandler.xpos();paints=screen.paintCount();pointer(c,c.getWindow().getScreenWidth()-10);}
                case 6 -> {check(Math.abs(c.mouseHandler.xpos()-leftPointerX)>100,"Minecraft receives changed pointer coordinates");check(screen.paintCount()==paints,"tracking needs no retained surface repaint");capture(c,"preview-api-right");click("modal");}
                case 7 -> {check(screen.openDialogId().isPresent()&&modalBlocked,"modal reports blocked pointer to previews");check(screen.performanceSnapshot().nativeEntityCount()==4,"native previews still submit behind modal");capture(c,"preview-api-modal");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0));screen.setValue("hidden",true);}
                case 8 -> {check(screen.performanceSnapshot().nativeEntityCount()==0,"removing slots stops all preview submissions");capture(c,"preview-api-hidden");screen.setValue("hidden",false);}
                case 9 -> {check(screen.performanceSnapshot().nativeEntityCount()==4,"restoring slots resumes previews");c.options.guiScale().set(2);c.resizeGui();}
                case 10 -> {check(screen.performanceSnapshot().nativeEntityCount()==4,"all skin models render at GUI scale 2");capture(c,"preview-api-scale2");c.options.guiScale().set(originalGuiScale);c.resizeGui();screen=WayfarerPreview.openMenu(c);pointer(c,10);}
                case 11 -> {check(screen.performanceSnapshot().nativeEntityCount()==1&&c.level==null&&c.player==null&&c.getSingleplayerServer()==null,"final migrated menu still world-free");capture(c,"wayfarer-player-api-final");reload=c.reloadResourcePacks();}
                case 12 -> {if(!reload.isDone()){stage--;waitFor(200);return;}reload.join();check(screen.performanceSnapshot().nativeEntityCount()==1,"preview survives resource reload");capture(c,"wayfarer-api-reloaded");finish(c);return;}
                default -> throw new IllegalStateException("Unexpected stage");
            }
            waitFor(800);
        }catch(Throwable e){failed=true;e.printStackTrace();finish(c);}
    }
    private void finish(Minecraft c){done=true;c.options.guiScale().set(originalGuiScale);System.out.println("[PlayerPreviewTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);c.stop();}
    private void check(boolean ok,String label){checks++;failed|=!ok;System.out.println("[PlayerPreviewTest] "+label+": "+(ok?"PASS":"FAIL"));}
    private void waitFor(long ms){until=System.nanoTime()+ms*1_000_000L;}
    private void pointer(Minecraft c,double x) throws ReflectiveOperationException {
        // Fixture-only native input injection: macOS ignores GLFW cursor warps for this background
        // client. Deliver the same MouseHandler callback; do not alter production preview code.
        var move=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);
        move.setAccessible(true);
        move.invoke(c.mouseHandler,c.getWindow().handle(),x,100D);
    }
    private void capture(Minecraft c,String name){Screenshot.grab(c.gameDirectory,name+".png",c.gameRenderer.mainRenderTarget(),1,m->{});}
    private void click(String id){var b=screen.regionBounds(id).orElseThrow();var e=new MouseButtonEvent(b.centerX()/screen.layoutScale(),b.centerY()/screen.layoutScale(),new MouseButtonInfo(0,0));screen.mouseClicked(e,false);screen.mouseReleased(e);}
}
