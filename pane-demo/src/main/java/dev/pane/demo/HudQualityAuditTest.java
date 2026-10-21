package dev.pane.demo;
import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;
final class HudQualityAuditTest {
 private UiHud hud;private int stage,checks;private long next,paints,allocations,uploads;private boolean failed;
 void tick(Minecraft c){try{
  if(System.nanoTime()<next)return;
  switch(stage++){
   case 0->{hud=new UiHud(320,100,UiTheme.dark(),ui->{var g=ui.canvas();g.fill(0xFF142323,0,0,320,100);g.text("Raster quality / 0123456789",12,12,19,0xFFFFFFFF);g.line(0xFFFFDD66,.5F,12,48,300,48);ui.nativeSlot("item",12,55,32,32);},o->o.item("item",c.player.getMainHandItem())).attach(Identifier.fromNamespaceAndPath("pane_demo","quality_probe"));hud.setMotionScale(0);place(1.5F);}
   case 1->{check(hud.rasterQuality().upscaled(),"default ceiling reports magnified HiDPI upscaling");check(hud.rasterQuality().actualScale()<=2.01,"legacy default scale ceiling preserved");capture(c,"hud-quality-default");hud.setRenderSettings(UiRenderSettings.highQuality());}
   case 2->{var r=hud.rasterQuality();check(!r.upscaled()&&r.pixelWidth()==960&&r.pixelHeight()==300,"opt-in quality follows physical 3x target");check(hud.nativeItemCount()==1,"native item aligns with quality-aware HUD");capture(c,"hud-quality-sharp");baseline();}
   case 3->{idle("settled sharp raster");hud.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.CENTER_RIGHT,24,0,1.5F));}
   case 4->{idle("position-only change");place(.5F);}
   case 5->{check(hud.rasterQuality().pixelWidth()==320&&hud.rasterQuality().pixelHeight()==100,"shrinking placement shrinks actual texture");check(hud.rasterBytes()==128000,"RGBA byte diagnostics match retained dimensions");baseline();hud.setRenderSettings(new UiRenderSettings(4096,2304,3));}
   case 6->{idle("equivalent settings");hud.setRenderSettings(new UiRenderSettings(512,512,4));place(2);}
   case 7->{check(hud.rasterQuality().pixelWidth()==512&&hud.rasterQuality().upscaled(),"width budget caps physical target and reports limit");check(hud.rasterBytes()<=512L*512*4,"configured per-surface memory bound holds");baseline();c.options.guiScale().set(3);c.resizeGui();}
   case 8->{idle("GUI scale does not change physical target");hud.setRenderSettings(UiRenderSettings.highQuality());place(1.5F);c.getWindow().setWindowed(320,240);}
   case 9->{var r=hud.rasterQuality();check(r.desiredScale()<3&&!r.upscaled(),"viewport fitting reduces physical raster target");baseline();}
   case 10->{idle("fitted raster");hud.setVisible(false);}
   case 11->{check(hud.rasterBytes()==0&&hud.rasterQuality().pixelWidth()==0&&!hud.snapshot().hasSurface(),"hide releases raster and clears size diagnostics");hud.setVisible(true);}
   case 12->{check(hud.rasterBytes()>0&&hud.nativeItemCount()==1,"show rebuilds fitted raster and native slot");c.gui.hud.toggle();baseline();}
   case 13->{idle("F1 suppressed HUD");c.gui.hud.toggle();hud.setEnabled(false);}
   case 14->{check(hud.rasterBytes()==0&&!hud.snapshot().hasSurface(),"disable releases surface");hud.close();check(hud.rasterBytes()==0,"close retains no raster");System.out.println("[HudQualityTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);c.stop();}
  }next=System.nanoTime()+650_000_000L;
 }catch(Throwable e){e.printStackTrace();System.out.println("[HudQualityTest] complete FAIL "+e);c.stop();}}
 private void place(float s){hud.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.CENTER,0,0,s));}
 private void baseline(){var s=hud.snapshot();paints=s.paints();allocations=s.allocations();uploads=s.uploadedPixels();}
 private void idle(String label){var s=hud.snapshot();check(s.paints()==paints&&s.allocations()==allocations&&s.uploadedPixels()==uploads,label+" has no extra paints/allocations/uploads");}
 private void check(boolean ok,String label){checks++;failed|=!ok;System.out.println("[HudQualityTest] "+label+": "+(ok?"PASS":"FAIL"));}
 private void capture(Minecraft c,String n){Screenshot.grab(c.gameDirectory,n+".png",c.gameRenderer.mainRenderTarget(),1,m->{});}
}
