package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom crosshair (skip vanilla's) and smooth hotbar (move the selection
 * frame). The selection frame is the second {@code drawGuiTexture} in
 * {@code renderHotbar} on every supported version (javap-checked); the call's
 * signature gained a render-type argument in 1.21.4 and a pipeline in 1.21.8,
 * so the x argument's index moves.
 */
@Mixin(InGameHud.class)
public class HudTweaksMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void blurred$customCrosshair(CallbackInfo ci) {
        if (Modules.CROSSHAIR.replacesVanilla()) {
            ci.cancel();
        }
    }

    //? if >=1.21.8 {
    @ModifyArg(method = "renderHotbar", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"),
            index = 2)
    //?} elif >=1.21.4 {
    /*@ModifyArg(method = "renderHotbar", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V"),
            index = 2)
    *///?} else {
    /*@ModifyArg(method = "renderHotbar", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"),
            index = 1)
    *///?}
    private int blurred$smoothSelection(int x) {
        return Modules.SMOOTH_HOTBAR.smooth(x);
    }
}
