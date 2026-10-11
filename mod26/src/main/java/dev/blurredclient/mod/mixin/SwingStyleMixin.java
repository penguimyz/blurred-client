package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swing animation styles for the item in your hand.
 *
 * <p>{@code applySwingOffset(MatrixStack, Arm, float)} is the same on every
 * supported version (javap-checked) and is always called straight after
 * vanilla's swing translate, with only translations in between. So a style
 * can undo that translate exactly and put its own motion in place of both,
 * without touching the version-specific code around it.
 *
 * <p>"No cooldown dip" stops the item sinking after each hit: the equip
 * animation reads the attack cooldown, renamed {@code getHandEquippingProgress}
 * in 1.21.11.
 */
@Mixin(ItemInHandRenderer.class)
public class SwingStyleMixin {

    /** Inspect moves the main-hand item first; the swing (which cancels it) goes on top. */
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"))
    private void blurred$inspect(PoseStack matrices, HumanoidArm arm, float progress, CallbackInfo ci) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && arm == player.getMainArm()) {
            Modules.INSPECT.apply(matrices, arm == HumanoidArm.RIGHT ? 1 : -1);
        }
    }

    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void blurred$swingStyle(PoseStack matrices, HumanoidArm arm, float progress, CallbackInfo ci) {
        if (Modules.SWING.customStyle()) {
            Modules.SWING.apply(matrices, arm == HumanoidArm.RIGHT ? 1 : -1, progress);
            ci.cancel();
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
    private float blurred$noDip(LocalPlayer player, float delta, Operation<Float> op) {
        return Modules.SWING.noCooldownDip() ? 1f : op.call(player, delta);
    }
}
