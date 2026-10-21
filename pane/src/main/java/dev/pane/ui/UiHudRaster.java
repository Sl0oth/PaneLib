package dev.pane.ui;

/** Uniform physical-resolution target with explicit per-axis and scale ceilings. */
final class UiHudRaster {
    static UiRasterQuality resolve(int width,int height,float desired,UiRenderSettings settings) {
        if(width<1||height<1||!Float.isFinite(desired)||desired<=0)
            throw new IllegalArgumentException("Raster dimensions and desired scale must be positive and finite");
        double scale=Math.min(desired,Math.min(settings.maximumScale(),
            Math.min(settings.maximumWidth()/(double)width,settings.maximumHeight()/(double)height)));
        int pw=Math.min(settings.maximumWidth(),Math.max(1,(int)Math.ceil(width*scale)));
        int ph=Math.min(settings.maximumHeight(),Math.max(1,(int)Math.ceil(height*scale)));
        return new UiRasterQuality(width,height,pw,ph,desired,Math.min(pw/(float)width,ph/(float)height));
    }
}
