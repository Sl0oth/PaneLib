package dev.pane.ui;
import java.util.Objects;
/** Image fitting and filtering. Alignment selects which edges retain excess image/letterbox space. */
public record UiImageOptions(Fit fit, Sampling sampling, float alignmentX, float alignmentY) {
 public enum Fit { COVER, CONTAIN, STRETCH }
 public enum Sampling { NEAREST, BILINEAR, BICUBIC }
 public UiImageOptions {
  Objects.requireNonNull(fit,"fit");Objects.requireNonNull(sampling,"sampling");
  if(!Float.isFinite(alignmentX)||!Float.isFinite(alignmentY)||alignmentX<0||alignmentX>1||alignmentY<0||alignmentY>1)
   throw new IllegalArgumentException("Image alignment must be finite and in 0..1");
 }
 public static UiImageOptions pixelArt(){return new UiImageOptions(Fit.COVER,Sampling.NEAREST,.5F,.5F);}
 public static UiImageOptions photo(){return new UiImageOptions(Fit.COVER,Sampling.BILINEAR,.5F,.5F);}
}
