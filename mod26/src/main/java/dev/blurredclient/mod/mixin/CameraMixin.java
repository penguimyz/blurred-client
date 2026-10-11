package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.fx.ContainerView;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
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
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    /** Zoom and the chest view's FOV. 26.x works the world FOV out here (the hand has its own). */
    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float blurred$zoom(float fov) {
        float out = fov * dev.blurredclient.mod.module.Modules.ZOOM.fovMultiplier()
                * dev.blurredclient.mod.module.Modules.CINEMATIC_CONTAINERS.fovMultiplier();
        dev.blurredclient.mod.fx.ScreenProject.recordFov(out);
        return out;
    }

    // 26.x: the camera follows its entity in update(DeltaTracker).
    @Inject(method = "update", at = @At("TAIL"))
    private void blurred$containerView(CallbackInfo ci) {
        ContainerView.INSTANCE.applyCamera((Camera) (Object) this, (pos, yaw, pitch) -> {
            setRotation(yaw, pitch);
            setPosition(pos);
        });
    }
}
