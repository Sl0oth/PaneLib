package dev.pane.ui;
import java.util.Objects;
/** Exact source-to-layout mapping used when painting an image; cover output clips to viewport. */
public record UiImagePlacement(float sourceWidth,float sourceHeight,UiRect viewport,UiRect imageBounds) {
 /** Requires positive source dimensions. */
 public UiImagePlacement {
  Objects.requireNonNull(viewport);Objects.requireNonNull(imageBounds);
  positive(sourceWidth);positive(sourceHeight);valid(viewport);valid(imageBounds);
 }
 private static void positive(float n){if(!Float.isFinite(n)||n<=0)throw new IllegalArgumentException("Image size must be positive and finite");}
 private static void valid(UiRect r){positive(r.width());positive(r.height());if(!Float.isFinite(r.x())||!Float.isFinite(r.y())||!Float.isFinite(r.right())||!Float.isFinite(r.bottom()))throw new IllegalArgumentException("Image bounds must be finite");}
 /** Where an image of the given size lands in {@code viewport} under {@code options}. */
 public static UiImagePlacement fit(float sourceWidth,float sourceHeight,UiRect viewport,UiImageOptions options){
  positive(sourceWidth);positive(sourceHeight);valid(viewport);Objects.requireNonNull(options);
  float sx=viewport.width()/sourceWidth,sy=viewport.height()/sourceHeight;
  float scale=options.fit()==UiImageOptions.Fit.CONTAIN?Math.min(sx,sy):Math.max(sx,sy);
  float w=options.fit()==UiImageOptions.Fit.STRETCH?viewport.width():sourceWidth*scale;
  float h=options.fit()==UiImageOptions.Fit.STRETCH?viewport.height():sourceHeight*scale;
  return new UiImagePlacement(sourceWidth,sourceHeight,viewport,new UiRect(viewport.x()+(viewport.width()-w)*options.alignmentX(),viewport.y()+(viewport.height()-h)*options.alignmentY(),w,h));
 }
 /** The layout x of source pixel column {@code sourceX}. */
 public float mapX(float sourceX){return imageBounds.x()+sourceX/sourceWidth*imageBounds.width();}
 /** The layout y of source pixel row {@code sourceY}. */
 public float mapY(float sourceY){return imageBounds.y()+sourceY/sourceHeight*imageBounds.height();}
}
