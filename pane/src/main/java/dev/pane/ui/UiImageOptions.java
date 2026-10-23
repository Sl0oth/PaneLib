package dev.pane.ui;
import java.util.Objects;
/** Image fitting and filtering. Alignment selects which edges retain excess image/letterbox space. */
public record UiImageOptions(Fit fit, Sampling sampling, float alignmentX, float alignmentY) {
 /** How an image fills its viewport: cropped to cover it, shrunk to fit inside it, or stretched to it. */
 public enum Fit {
  /** Fills the viewport, cropping what overflows. */
  COVER,
  /** Fits wholly inside the viewport, leaving bars where the shape differs. */
  CONTAIN,
  /** Stretches to the viewport, ignoring the image's shape. */
  STRETCH
 }
 /** How image pixels are sampled when scaled. */
 public enum Sampling {
  /** Crisp pixels, for pixel art. */
  NEAREST,
  /** Smooth, for photographs. */
  BILINEAR,
  /** Smoother still, at more cost. */
  BICUBIC
 }
 /** Requires a fit, a sampling mode, and both alignments within 0 to 1. */
 public UiImageOptions {
  Objects.requireNonNull(fit,"fit");Objects.requireNonNull(sampling,"sampling");
  if(!Float.isFinite(alignmentX)||!Float.isFinite(alignmentY)||alignmentX<0||alignmentX>1||alignmentY<0||alignmentY>1)
   throw new IllegalArgumentException("Image alignment must be finite and in 0..1");
 }
 /** Covers the viewport with crisp nearest-pixel sampling, for pixel art. */
 public static UiImageOptions pixelArt(){return new UiImageOptions(Fit.COVER,Sampling.NEAREST,.5F,.5F);}
 /** Covers the viewport with smooth sampling, for photographs. */
 public static UiImageOptions photo(){return new UiImageOptions(Fit.COVER,Sampling.BILINEAR,.5F,.5F);}
}
