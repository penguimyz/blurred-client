package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.fx.Hologram;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Clicks aimed at the 3D menu go to the menu, not the world: no block is
 * placed or hit behind the panel. {@code doItemUse()} and {@code doAttack()}
 * are the same on every supported version (javap-checked).
 */
@Mixin(MinecraftClient.class)
public class WorldClickMixin {
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void blurred$hologramUse(CallbackInfo ci) {
        if (Hologram.INSTANCE.onUse()) {
            ci.cancel();
            return;
        }
        // Remember which block was clicked: by the time a chest's screen
        // opens you may have walked on and be looking at something else.
        if (MinecraftClient.getInstance().crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit
                && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            dev.blurredclient.mod.fx.ContainerView.INSTANCE.noteUse(hit.getBlockPos());
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void blurred$hologramAttack(CallbackInfoReturnable<Boolean> cir) {
        if (Hologram.INSTANCE.onAttack()) {
            cir.setReturnValue(false);
        }
    }
}
