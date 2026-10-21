package dev.pane.demo;

import dev.pane.ui.NativeOverlayContext;
import dev.pane.ui.UiContext;
import dev.pane.ui.UiPlayerPreview;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.core.ClientAsset.ResourceTexture;
import net.minecraft.resources.Identifier;

/** World-independent avatar snapshot, rendered with Minecraft's player renderer. */
final class WayfarerPlayerPreview {
    static final PlayerSkin EXPLORER_SKIN=new PlayerSkin(new ResourceTexture(
        Identifier.fromNamespaceAndPath("pane_demo","wayfarer/explorer-skin")),
        null,null,PlayerModelType.WIDE,true);
    private static final UiPlayerPreview PRESENTATION = UiPlayerPreview.standing()
        .bodyYaw(210).headRotation(-12, 0).cameraPitch(-8)
        .heightFraction(.81F).feet(.5F, 524F / 540F);
    private WayfarerPlayerPreview() {}

    static void reserve(UiContext ui,dev.pane.ui.UiImagePlacement image) {
        // Share the exact mapping used to draw the background, including its crop and alignment.
        float scale=image.imageBounds().width()/1672F*.90F;
        float footX=image.mapX(image.sourceWidth()*890F/1280F);
        float footY=image.mapY(image.sourceHeight()*492F/720F);
        var g=ui.canvas();
        // Sun is behind and left of the player. Project the shadow toward the camera/right.
        // All points share the feet's image-space anchor, so resizing cannot detach it.
        g.withTranslation(footX,footY,()->g.withScale(scale,scale,0,0,()->{
            for(int i=5;i>=0;i--){
                final int spread=i;
                g.path().moveTo(-54-spread,1).lineTo(54+spread,1)
                    .lineTo(65+spread,55).lineTo(57+spread,98)
                    .curveTo(75+spread,129,20-spread,138,20-spread,105)
                    .lineTo(-10-spread,66).close().fill(0x10030A12);
            }
        }));
        g.withScale(1,.18F,footX,footY,()->{
            for(int i=10;i>=1;i--)g.circle(0x09030B14,footX,footY,(45+i*3)*scale);
        });
        // Tight, separately placed contact patches match the two yawed boot soles.
        for(int side=-1;side<=1;side+=2){
            float bootX=footX+side*25*scale,bootY=footY+(side*2+2)*scale;
            g.withScale(1,.22F,bootX,bootY,()->{
                for(int i=7;i>=1;i--)g.circle(0x19020810,bootX,bootY,(24+i*1.8F)*scale);
            });
        }
        ui.nativeSlot("explorer",footX-140*scale,footY-524*scale,280*scale,540*scale);
    }

    static void render(NativeOverlayContext ui) {
        ui.playerPreview("explorer", EXPLORER_SKIN, PRESENTATION);
    }
}
