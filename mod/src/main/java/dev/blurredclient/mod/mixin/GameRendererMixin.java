package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zoom (field of view) and No hurt cam (the screen shake when you're hit).
 *
 * <p>{@code getFov} returns a double on 1.21.1 and a float from 1.21.4
 * (checked with javap), hence the branch; {@code tiltViewWhenHurt} is the
 * same on every version.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    //? if >=1.21.4 {
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private float blurred$zoom(float fov, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) boolean changingFov) {
        float out = fov * Modules.ZOOM.fovMultiplier() * Modules.CINEMATIC_CONTAINERS.fovMultiplier();
        if (changingFov) {
            dev.blurredclient.mod.fx.ScreenProject.recordFov(out);
        }
        return out;
    }
    //?} else {
    /*@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private double blurred$zoom(double fov, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) boolean changingFov) {
        double out = fov * Modules.ZOOM.fovMultiplier() * Modules.CINEMATIC_CONTAINERS.fovMultiplier();
        if (changingFov) {
            dev.blurredclient.mod.fx.ScreenProject.recordFov((float) out);
        }
        return out;
    }
    *///?}

    //? if >=1.21.11 {
    /** Liquid glass: where a glass menu marked its background done, copy the frame instead of blurring it. */
    @Inject(method = "renderBlur", at = @At("HEAD"), cancellable = true)
    private void blurred$glassInsteadOfBlur(CallbackInfo ci) {
        if (dev.blurredclient.mod.ui.kit.LiquidGlass.takeBlur()) {
            ci.cancel();
        }
    }

    /** Liquid glass: copy the world just before the menu layer (and its blur) is drawn. */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void blurred$glassCopy(CallbackInfo ci) {
        dev.blurredclient.mod.ui.kit.LiquidGlass.beforeGui();
    }
    //?}

    /** No camera bob: skip the walking sway. */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void blurred$noBob(CallbackInfo ci) {
        // No bob while looking into a chest either: the walk's sway would shake the grid.
        if (Modules.NO_BOB.isActive() || dev.blurredclient.mod.fx.ContainerView.INSTANCE.showing()) {
            ci.cancel();
        }
    }

    /** No hand floating in front of the camera while it's looking into a chest. */
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void blurred$noHandInChest(CallbackInfo ci) {
        if (dev.blurredclient.mod.fx.ContainerView.INSTANCE.amount() > 0.02f) {
            ci.cancel();
        }
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void blurred$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (Modules.NO_HURT_CAM.isActive()) {
            ci.cancel();
        }
    }
}
