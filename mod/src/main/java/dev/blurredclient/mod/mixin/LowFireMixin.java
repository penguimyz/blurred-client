package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
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
@Mixin(InGameOverlayRenderer.class)
public class LowFireMixin {

    @Inject(method = "renderFireOverlay",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
    private static void blurred$lowFire(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices) {
        if (Modules.LOW_FIRE.isActive()) {
            matrices.translate(0f, -Modules.LOW_FIRE.drop(), 0f);
        }
    }
}
