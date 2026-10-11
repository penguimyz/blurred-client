package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lowers the first-person fire overlay (the Low fire module).
 *
 * <p>{@code renderFireOverlay}'s parameters differ in every era (a
 * MinecraftClient in 1.21.1, a Sprite added in 1.21.10), so the stack is
 * found by type with MixinExtras' {@code @Local} instead of being listed as
 * parameters — one injector for all five versions. It translates right after
 * the method's own {@code push()} (one call in every version, checked with
 * javap), so the shift is undone by its matching {@code pop()}.
 */
@Mixin(ScreenEffectRenderer.class)
public class LowFireMixin {

    // 26.x submits the fire overlay with the caller's pose: lower it for the submit, then put it back.
    @Inject(method = "submitFire", at = @At("HEAD"))
    private static void blurred$lowFire(CallbackInfo ci, @Local(argsOnly = true) PoseStack matrices) {
        matrices.pushPose();
        if (Modules.LOW_FIRE.isActive()) {
            matrices.translate(0f, -Modules.LOW_FIRE.drop(), 0f);
        }
    }

    @Inject(method = "submitFire", at = @At("RETURN"))
    private static void blurred$lowFireEnd(CallbackInfo ci, @Local(argsOnly = true) PoseStack matrices) {
        matrices.popPose();
    }
}
