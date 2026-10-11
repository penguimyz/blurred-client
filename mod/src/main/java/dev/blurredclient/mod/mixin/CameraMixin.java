package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.fx.ContainerView;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moves the camera for the in-chest view, after vanilla has placed it.
 * {@code update}'s first parameter changed type in 1.21.11 (BlockView to
 * World), so the injection matches by name only; {@code setPos(Vec3d)} and
 * {@code setRotation} are the same everywhere (javap-checked). Only the
 * camera moves — the player's own rotation, which the server sees, doesn't.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void blurred$containerView(CallbackInfo ci) {
        ContainerView.INSTANCE.applyCamera((Camera) (Object) this, (pos, yaw, pitch) -> {
            setRotation(yaw, pitch);
            setPos(pos);
        });
    }
}
