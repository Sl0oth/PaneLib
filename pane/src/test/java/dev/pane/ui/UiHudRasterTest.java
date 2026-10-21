package dev.pane.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class UiHudRasterTest {
 @Test void physicalScaleIncludesMagnificationAndCanShrinkBelowOne(){
  var sharp=UiHudRaster.resolve(580,152,3,UiRenderSettings.highQuality());
  assertEquals(1740,sharp.pixelWidth());assertEquals(456,sharp.pixelHeight());assertFalse(sharp.upscaled());
  var small=UiHudRaster.resolve(580,152,.5F,UiRenderSettings.hud());
  assertEquals(290,small.pixelWidth());assertEquals(76,small.pixelHeight());assertFalse(small.upscaled());
 }
 @Test void defaultsKeepLegacyCeilingAndReportUpscaling(){
  var limited=UiHudRaster.resolve(580,152,3,UiRenderSettings.hud());
  assertEquals(1160,limited.pixelWidth());assertTrue(limited.upscaled());
  assertEquals(new UiRenderSettings(2048,2048,2),UiRenderSettings.hud());
 }
 @Test void axisBudgetsBoundWideTallAndLargeSurfaces(){
  var settings=new UiRenderSettings(512,768,4);
  for(int w:new int[]{1,57,580,1024})for(int h:new int[]{1,71,152,1024})for(float q:new float[]{.01F,.75F,1.3F,3,7}){
   var r=UiHudRaster.resolve(w,h,q,settings);
   assertTrue(r.pixelWidth()>=1&&r.pixelWidth()<=512&&r.pixelHeight()>=1&&r.pixelHeight()<=768);
   assertTrue((long)r.pixelWidth()*r.pixelHeight()*4<=512L*768*4);
  }
 }
 @Test void rejectsInvalidTargets(){for(float q:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})
  assertThrows(IllegalArgumentException.class,()->UiHudRaster.resolve(100,80,q,UiRenderSettings.hud()));}
}
