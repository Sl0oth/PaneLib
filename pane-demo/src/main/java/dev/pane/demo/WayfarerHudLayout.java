package dev.pane.demo;

import dev.pane.ui.*;

/** Authored responsive recipe, not an automatic collision system. Designed for 640x440 and up. */
final class WayfarerHudLayout {
    static UiHudPlacement placement(String widget, UiHudViewport viewport, float requestedScale) {
        boolean compact=viewport.width()<1376*requestedScale||viewport.height()<500*requestedScale;
        float scale=compact?Math.max(.5F,Math.min(requestedScale,
            Math.min((viewport.width()-64)/600,(viewport.height()-64)/620))):requestedScale;
        float top=compact?24+80*scale:24;
        float bottom=compact?24+152*scale:25;
        return switch(widget) {
            case "location"->new UiHudPlacement(UiHudPlacement.Anchor.TOP_LEFT,24,top,scale);
            case "journal"->new UiHudPlacement(UiHudPlacement.Anchor.TOP_RIGHT,24,top,scale);
            case "vitals"->new UiHudPlacement(UiHudPlacement.Anchor.BOTTOM_LEFT,24,bottom,scale);
            case "badge"->new UiHudPlacement(UiHudPlacement.Anchor.BOTTOM_RIGHT,24,compact?bottom:28,scale);
            case "belt"->new UiHudPlacement(UiHudPlacement.Anchor.BOTTOM_CENTER,0,compact?16:22,scale);
            case "compass"->new UiHudPlacement(UiHudPlacement.Anchor.TOP_CENTER,0,compact?16:22,scale);
            case "crosshair"->new UiHudPlacement(UiHudPlacement.Anchor.CENTER,0,0,1);
            default->throw new IllegalArgumentException("Unknown Wayfarer HUD widget: "+widget);
        };
    }
}
