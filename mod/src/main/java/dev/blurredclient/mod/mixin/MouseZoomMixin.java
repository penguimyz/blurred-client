package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While zoomed, the mouse wheel zooms further instead of changing the hotbar
 * slot. {@code onMouseScroll(long, double, double)} is the same on every
 * supported version.
 */
@Mixin(Mouse.class)
public class MouseZoomMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void blurred$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (dev.blurredclient.mod.fx.Hologram.INSTANCE.onScroll(vertical) || Modules.ZOOM.onScroll(vertical)) {
            ci.cancel();
        }
    }
}
