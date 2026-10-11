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
//? if >=1.21.4 {
@Mixin(net.minecraft.client.option.InactivityFpsLimiter.class)
//?} else {
/*@Mixin(net.minecraft.client.MinecraftClient.class)
*///?}
public class FramerateMixin {

    //? if >=1.21.4 {
    @ModifyReturnValue(method = "update", at = @At("RETURN"))
    //?} else {
    /*@ModifyReturnValue(method = "getFramerateLimit", at = @At("RETURN"))
    *///?}
    private int blurred$framerate(int limit) {
        return PerformanceModules.adjustFramerate(limit);
    }
}
