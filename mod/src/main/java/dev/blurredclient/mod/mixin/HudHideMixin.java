package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.fx.ContainerView;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No hotbar, hearts or crosshair over the in-chest view. */
@Mixin(InGameHud.class)
public class HudHideMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void blurred$hideInChest(CallbackInfo ci) {
        if (ContainerView.INSTANCE.amount() > 0.02f) {
            ci.cancel();
        }
    }
}
