package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Actual resource-manager reload plus a stable-ID dynamic texture. No world required. */
public final class ResourceAuditTest {
    private static final Identifier IMAGE=Identifier.fromNamespaceAndPath("pane_demo","audit_dynamic");
    private UiScreen screen;
    private DynamicTexture texture;
    private int stage,checks,sampled,paints;
    private long deadline,reloadRevision,reloadStarted;
    private boolean failed,finished;
    private boolean photoAvailable,missingEmpty;
    private UiImagePlacement photoPlacement;
    private CompletableFuture<Void> reload;
    public void tick(Minecraft client) {
        if(finished)return;
        try {
            if(screen==null) {
                if(!(client.gui.screen() instanceof TitleScreen))return;
                texture=new DynamicTexture(()->"pane-resource-audit",2,2,true);
                color(0xFFFF0000);client.getTextureManager().register(IMAGE,texture);
                screen=new UiScreen(UiDocument.builder("Resource audit").scaling(UiScaling.fixed())
                    .page(new UiPage("main","Main","",0xFF88AA55,ui->{
                        var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);
                        var graphics=image.createGraphics();
                        try { new UiCanvas(graphics).minecraftImage(IMAGE,new UiRect(0,0,2,2),UiImageOptions.pixelArt()); }
                        finally { graphics.dispose(); }
                        sampled=image.getRGB(0,0);
                        ui.canvas().minecraftTexture(IMAGE,30,30,100,100);
                        var art=Identifier.fromNamespaceAndPath("pane_demo","textures/wayfarer/mountain-lookout.png");
                        photoAvailable=ui.canvas().minecraftImage(art,new UiRect(30,160,260,160),UiImageOptions.photo()).isPresent();
                        photoPlacement=ui.canvas().minecraftImage(art,new UiRect(320,160,260,160),
                            new UiImageOptions(UiImageOptions.Fit.CONTAIN,UiImageOptions.Sampling.BICUBIC,.5F,.5F)).orElse(null);
                        missingEmpty=ui.canvas().minecraftImage(Identifier.fromNamespaceAndPath("pane_demo","missing-audit-image"),
                            new UiRect(600,160,20,20),UiImageOptions.photo()).isEmpty();
                    })).build());
                client.gui.setScreen(screen);deadline=System.nanoTime()+700_000_000L;return;
            }
            if(screen.paintCount()==0 || System.nanoTime()<deadline || (stage==0&&client.gui.overlay()!=null))return;
            if(stage==0) {
                check(sampled==0xFFFF0000,"initial dynamic pixels are decoded");
                check(photoAvailable&&photoPlacement!=null,"resource photos draw through cover and contain filters");
                check(photoPlacement!=null&&Math.abs(photoPlacement.mapX(photoPlacement.sourceWidth()/2)-450)<.01,"source-point mapping matches painted image center");
                check(missingEmpty,"missing image returns empty without inventing geometry");
                color(0xFF00FF00);UiCanvas.clearMinecraftTextureCache();
                stage++;deadline=System.nanoTime()+250_000_000L;return;
            }
            if(stage==1) {
                net.minecraft.client.Screenshot.grab(client.gameDirectory,"image-fit-filters.png",client.gameRenderer.mainRenderTarget(),1,m->{});

                check(sampled==0xFF00FF00,"cache clearing automatically repaints retained screen");
                color(0xFF0000FF);reloadRevision=UiCanvas.minecraftTextureRevision();reloadStarted=System.nanoTime();reload=client.reloadResourcePacks();
                stage++;deadline=System.nanoTime()+300_000_000L;return;
            }
            if(stage==2) {
                if(System.nanoTime()-reloadStarted>45_000_000_000L)throw new IllegalStateException("Reload did not finish within 45 seconds");
                if(!reload.isDone() || client.gui.overlay()!=null)return;
                reload.join();stage++;deadline=System.nanoTime()+700_000_000L;return;
            }
            if(stage==3) {
                check(UiCanvas.minecraftTextureRevision()>reloadRevision,"automatic reload listener advances image revision");
                System.out.println("[ResourceTest] sampled="+Integer.toHexString(sampled)+" paints="+screen.paintCount());
                check(sampled==0xFF0000FF,"actual resource reload refreshes cached pixels and screen");
                color(0xFFFFFF00);UiCanvas.invalidateMinecraftTexture(IMAGE);stage++;deadline=System.nanoTime()+250_000_000L;return;
            }
            if(stage==4) {
                check(sampled==0xFFFFFF00,"explicit same-ID texture update refreshes decoded pixels");
                paints=screen.paintCount();stage++;deadline=System.nanoTime()+300_000_000L;return;
            }
            check(screen.paintCount()==paints,"resource refresh does not create an idle repaint loop");
            client.getTextureManager().release(IMAGE);
            finished=true;System.out.println("[ResourceTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);client.stop();
        }catch(Throwable error){finished=true;error.printStackTrace();System.out.println("[ResourceTest] complete FAIL "+error);client.stop();}
    }
    private void color(int argb){for(int y=0;y<2;y++)for(int x=0;x<2;x++)texture.getPixels().setPixel(x,y,argb);texture.upload();}
    private void check(boolean passed,String message){checks++;failed|=!passed;System.out.println("[ResourceTest] "+message+": "+(passed?"PASS":"FAIL"));}
}
