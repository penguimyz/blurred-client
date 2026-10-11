package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.module.feature.ViewModelModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the View model module's offset, rotation and scale.
 *
 * <p>Hooks the end of {@code applyEquipOffset} rather than
 * {@code renderFirstPersonItem}: its signature is identical in every
 * supported version (checked with javap on 1.21.1–1.21.11), while
 * {@code renderFirstPersonItem}'s last parameter changed type in 1.21.10. It
 * runs inside the per-hand push/pop, so the extra transform can't leak into
 * anything drawn afterwards.
 */
@Mixin(ItemInHandRenderer.class)
public class HeldItemViewModelMixin {

    /**
     * Note which item is being drawn, so the per-item setup can be chosen.
     * The stack is found by type with MixinExtras' {@code @Local}, because
     * this method's last parameter changed type in 1.21.10.
     */
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void blurred$noteItem(CallbackInfo ci, @Local(argsOnly = true) ItemStack stack) {
        ViewModelModule.setCurrent(stack);
    }

    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void blurred$viewModel(PoseStack matrices, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
        boolean main = arm == net.minecraft.client.Minecraft.getInstance().player.getMainArm();
        if (main) {
            ViewModelModule.MAIN_TRANSFORM.identity();
        }
        ViewModelModule vm = Modules.VIEW_MODEL;
        if (!vm.isActive()) {
            return;
        }
        org.joml.Matrix4f before = new org.joml.Matrix4f(matrices.last().pose());
        ViewModelModule.Setup s = vm.setup();
        // Inspect plays from vanilla's pose: ease the custom offsets out while it runs.
        float keep = arm == net.minecraft.client.Minecraft.getInstance().player.getMainArm()
                ? 1f - Modules.INSPECT.takeover() : 1f;
        if (keep <= 0.001f) {
            return;
        }
        // Mirror sideways movement and the side-dependent rotations for the
        // left arm, so both hands stay symmetrical.
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        matrices.translate(side * s.x() * keep, s.y() * keep, s.z() * keep);
        matrices.mulPose(Axis.XP.rotationDegrees(s.rotX() * keep));
        matrices.mulPose(Axis.YP.rotationDegrees(side * s.rotY() * keep));
        matrices.mulPose(Axis.ZP.rotationDegrees(side * s.rotZ() * keep));
        float k = 1f + (s.scale() - 1f) * keep;
        matrices.scale(k, k, k);
        if (main) {
            // Remember exactly what was applied, so the drawn hand can take it back out.
            ViewModelModule.MAIN_TRANSFORM.set(before.invert().mul(matrices.last().pose()));
        }
    }
}
