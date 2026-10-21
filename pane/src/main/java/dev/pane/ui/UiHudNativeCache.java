package dev.pane.ui;
import java.util.Map;
/** Reuses transformed slot geometry until source slots, placement, opacity offset or GUI scale change. */
final class UiHudNativeCache {
    private UiRect target;
    private double guiScale;
    private Map<String,NativeBounds> mapped=Map.of();
    private long builds;
    Map<String,NativeBounds> resolve(Map<String,NativeBounds> local,UiRect next,float width,float height,double scale){
        if(!next.equals(target)||scale!=guiScale){
            mapped=UiHudNativePlacement.map(local,next,width,height,scale);target=next;guiScale=scale;builds++;
        }
        return mapped;
    }
    void clear(){target=null;mapped=Map.of();}
    long builds(){return builds;}
}
