package dev.pane.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Minecraft adapter kept out of consumer code. States are per submission: extraction retains them. */
final class PlayerPreviewRenderer {
    private PlayerPreviewRenderer() {}

    record Frame(int x, int y, int width, int height, float size, float offsetX, float offsetY,
                 float headYaw, float headPitch) {}

    static Frame frame(NativeBounds box, double guiScale, UiPlayerPreview options,
                       double mouseX, double mouseY, boolean pointerBlocked) {
        if (box == null) return null;
        int x = box.logicalX(guiScale), y = box.logicalY(guiScale);
        int width = box.logicalWidth(guiScale), height = box.logicalHeight(guiScale);
        if (width <= 0 || height <= 0) return null;
        float size = Math.min(height * options.heightFraction() / 1.8F, width / 1.1F);
        float yaw = options.headYaw(), pitch = options.headPitch();
        if (options.followPointer() && !pointerBlocked) {
            yaw += (float) Math.toDegrees(Math.atan((x + width * options.feetX() - mouseX) / size)) * .5F;
            pitch += (float) Math.toDegrees(Math.atan((mouseY - (y + height * options.feetY() - size * 1.62F)) / size)) * .5F;
        }
        return new Frame(x, y, width, height, size,
            width * (options.feetX() - .5F) / size, height * (options.feetY() - .5F) / size,
            Math.clamp(yaw, -90, 90), Math.clamp(pitch, -90, 90));
    }

    static void render(GuiGraphicsExtractor graphics, Frame frame, PlayerSkin skin, UiPlayerPreview options) {
        // Explicit standing state: no live entity, entity mutation, world access or animation clock.
        var state = new AvatarRenderState();
        state.entityType = EntityTypes.PLAYER;
        state.boundingBoxWidth = .6F;
        state.boundingBoxHeight = 1.8F;
        state.eyeHeight = 1.62F;
        state.lightCoords = 0x00F000F0;
        state.skin = skin == null ? DefaultPlayerSkin.getDefaultSkin() : skin;
        state.pose = Pose.STANDING;
        state.bodyRot = options.bodyYaw();
        state.yRot = frame.headYaw();
        state.xRot = frame.headPitch();
        state.scale = 1;
        state.ageScale = 1;
        state.showHat = state.showJacket = state.showLeftSleeve = state.showRightSleeve = true;
        state.showLeftPants = state.showRightPants = true;
        state.showCape = false;
        state.rightArmPose = state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        // Other fresh-state fields remain neutral: no name, shadow, fire, equipment or movement.
        var camera = new Quaternionf().rotateX((float) Math.toRadians(options.cameraPitch()));
        graphics.entity(state, frame.size(), new Vector3f(frame.offsetX(), frame.offsetY(), 0),
            new Quaternionf().rotateZ((float) Math.PI).mul(camera), camera,
            frame.x(), frame.y(), frame.x() + frame.width(), frame.y() + frame.height());
    }
}
