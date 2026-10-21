package dev.pane.ui;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class UiImagePlacementTest {
 @Test void coverContainAndStretchMapTheSameSourcePointsTheyDraw(){
  var box=new UiRect(10,20,100,100);
  var cover=UiImagePlacement.fit(200,100,box,UiImageOptions.photo());
  assertEquals(new UiRect(-40,20,200,100),cover.imageBounds());assertEquals(60,cover.mapX(100));assertEquals(120,cover.mapY(100));
  var contain=UiImagePlacement.fit(200,100,box,new UiImageOptions(UiImageOptions.Fit.CONTAIN,UiImageOptions.Sampling.BILINEAR,.5F,1));
  assertEquals(new UiRect(10,70,100,50),contain.imageBounds());assertEquals(110,contain.mapX(200));
  assertEquals(box,UiImagePlacement.fit(200,100,box,new UiImageOptions(UiImageOptions.Fit.STRETCH,UiImageOptions.Sampling.NEAREST,0,0)).imageBounds());
 }
 @Test void cropAlignmentKeepsChosenEdge(){
  var b=new UiRect(0,0,100,100);
  assertEquals(0,UiImagePlacement.fit(200,100,b,new UiImageOptions(UiImageOptions.Fit.COVER,UiImageOptions.Sampling.NEAREST,0,.5F)).mapX(0));
  assertEquals(100,UiImagePlacement.fit(200,100,b,new UiImageOptions(UiImageOptions.Fit.COVER,UiImageOptions.Sampling.NEAREST,1,.5F)).mapX(200));
 }
 @Test void explicitSamplingChangesPixelsWithoutLeakingClipOrHints(){
  var src=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);src.setRGB(0,0,0xFF000000);src.setRGB(1,0,0xFFFFFFFF);
  var dst=new BufferedImage(24,12,BufferedImage.TYPE_INT_ARGB);var g=dst.createGraphics();
  try{var c=new UiCanvas(g,dst);Object hint=g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
   c.image(src,new UiRect(0,0,8,4),new UiImageOptions(UiImageOptions.Fit.STRETCH,UiImageOptions.Sampling.NEAREST,0,0));
   c.image(src,new UiRect(8,0,8,4),new UiImageOptions(UiImageOptions.Fit.STRETCH,UiImageOptions.Sampling.BILINEAR,0,0));
   assertEquals(0xFF000000,dst.getRGB(3,2));assertNotEquals(dst.getRGB(3,2),dst.getRGB(11,2));
   assertEquals(hint,g.getRenderingHint(RenderingHints.KEY_INTERPOLATION));assertNull(g.getClip());
   c.image(src,new UiRect(4,6,4,4),UiImageOptions.pixelArt());assertEquals(0,dst.getRGB(3,7));assertEquals(0,dst.getRGB(8,7));
   c.fill(0xFFFF0000,20,0,2,2);assertEquals(0xFFFF0000,dst.getRGB(20,0));
  }finally{g.dispose();}
 }
 @Test void mappingTracksResizeAndRejectsInvalidGeometry(){
  for(float w:new float[]{320,640,1440,2560}){
   var p=UiImagePlacement.fit(1280,720,new UiRect(0,0,w,900),UiImageOptions.photo());
   float s=Math.max(w/1280,900F/720);
   assertEquals((w-1280*s)/2+890*s,p.mapX(890),.001);
  }
  assertThrows(IllegalArgumentException.class,()->UiImagePlacement.fit(0,10,new UiRect(0,0,10,10),UiImageOptions.photo()));
  assertThrows(IllegalArgumentException.class,()->new UiImageOptions(UiImageOptions.Fit.COVER,UiImageOptions.Sampling.NEAREST,Float.NaN,0));
  assertThrows(IllegalArgumentException.class,()->UiImagePlacement.fit(10,10,new UiRect(0,0,-1,10),UiImageOptions.photo()));
 }
}
