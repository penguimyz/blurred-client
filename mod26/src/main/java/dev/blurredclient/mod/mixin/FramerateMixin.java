package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.blurredclient.mod.module.feature.PerformanceModules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Smooth menus and Dynamic FPS: adjusts the frame-rate limit Minecraft applies.
 *
 * <p>The limit moved in 1.21.4, from {@code MinecraftClient#getFramerateLimit}
 * into {@code InactivityFpsLimiter#update} (checked with javap; both hold
 * menus at 60). One mixin class, retargeted per version.
 */
@Mixin(com.mojang.blaze3d.platform.FramerateLimitTracker.class)
public class FramerateMixin {

    @ModifyReturnValue(method = "getFramerateLimit", at = @At("RETURN"))
    private int blurred$framerate(int limit) {
        return PerformanceModules.adjustFramerate(limit);
    }
}
